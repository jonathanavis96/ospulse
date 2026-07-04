package com.ospulse.combat;

/**
 * Entry point: computes max hit / accuracy / DPS for one gear+player+style
 * setup against one {@link Monster}, per the OSRS Wiki DPS calculator
 * formulas (see per-method Javadoc in {@link CombatMath} for citations).
 * <p>
 * Tier-A effects modelled (each isolated below, with an "applies when"
 * predicate, per the design doc): offensive prayers, best-potion boosts,
 * Slayer helm(i)/black mask(i) on-task bonus, Salve amulet variants vs
 * Undead, Void/Elite Void. Tier-B effects: demonbane weapons (melee sword
 * line + Burning claws + ranged Scorching bow; accuracy/damage split, applied
 * as the weapon's own step stacking with the salve/slayer slot, with the
 * ranged max hit folding additively into an on-task imbued helm — see
 * computeRanged; a per-monster demonbane RESISTANCE, e.g. Duke Sucellus's
 * 30%, scales the bonus's excess-over-1 down — see
 * resistedDemonbaneFraction), Dragon
 * Hunter lance/crossbow/wand vs dragons and the Twisted bow's vs-target-magic
 * scaling (separate multiplicative steps — they stack with the target
 * slot, with the same ranged-max-hit additive fold for the DHCB),
 * powered-staff built-in spells and real {@link Spell} max hits for
 * autocast, and elemental weakness (a monster's {@code weaknessElement}/
 * {@code weaknessSeverity} adds straight into the magic-damage percent when
 * the cast {@link Spell}'s {@link Spell.Element} matches - see
 * computeMagic), and Osmumten's fang's two STAB-only passives (double
 * accuracy roll + compressed 15%-85%-of-max-hit damage roll — its own
 * hitChance/avgDamage formulas replace the generic ones entirely rather than
 * being a multiplicative step on maxHit/attackRoll — see computeMelee /
 * finishFang / {@link CombatMath#fangHitChance} /
 * {@link CombatMath#fangAverageDamagePerAttack}). Unknown/unmodelled effects
 * (scythe multi-hit, special
 * attacks, Avarice, Tomes, Tumeken's 3x gear multiplier, ...) are simply not
 * applied — extend this class (and {@link CombatMath}) to add them, gated
 * behind their own "applies when" predicate.
 */
public final class DpsCalculator {
    private DpsCalculator() {
    }

    /**
     * Legacy/simple entry point: magic uses {@code baseSpellMaxHit} verbatim
     * with the weapon's own attack speed (no powered-staff or spell logic).
     * Prefer {@link #compute(EquipmentStats, PlayerCombat, CombatStyle, Monster, Spell)}
     * for magic with a real spell, which also auto-detects powered staves.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, int baseSpellMaxHit) {
        if (style == CombatStyle.MAGIC) {
            return computeMagic(gear, player, target, baseSpellMaxHit, gear.weaponSpeedTicks(), false, null);
        }
        return computeNonMagic(gear, player, style, target);
    }

    /**
     * Spell-aware entry point. For {@link CombatStyle#MAGIC}: a worn powered
     * staff (detected on {@code gear}) takes precedence — its built-in spell's
     * max hit is derived from the (boosted) Magic level at the weapon's own
     * speed; otherwise the given {@link Spell}'s base max hit is cast at the
     * fixed {@link Spell#CAST_SPEED_TICKS} autocast speed. A {@code null}
     * spell with no powered staff yields a zero-damage result rather than a
     * guess.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, Spell spell) {
        if (style != CombatStyle.MAGIC) {
            return computeNonMagic(gear, player, style, target);
        }
        if (gear.poweredStaff().applies()) {
            // Base max hit derives from the boosted level inside computeMagic.
            // Powered staves cast their own built-in "spell" (Magic Dart etc.),
            // which is not a real Spell and carries no element - no weakness bonus.
            return computeMagic(gear, player, target, POWERED_STAFF_SENTINEL, gear.weaponSpeedTicks(),
                    gear.poweredStaff().approximate(), null);
        }
        int baseMaxHit = spell == null ? 0 : spell.baseMaxHit();
        Spell.Element element = spell == null ? null : spell.element();
        return computeMagic(gear, player, target, baseMaxHit, Spell.CAST_SPEED_TICKS, false, element);
    }

    private static DpsResult computeNonMagic(EquipmentStats gear, PlayerCombat player, CombatStyle style, Monster target) {
        if (style == CombatStyle.RANGED) {
            return computeRanged(gear, player, target);
        }
        return computeMelee(gear, player, style, target);
    }

    // ---- Melee (STAB/SLASH/CRUSH) -----------------------------------------------------

    private static DpsResult computeMelee(EquipmentStats gear, PlayerCombat player, CombatStyle style, Monster target) {
        int boostedStr = player.assumeBestPotion()
                ? PotionBoosts.bestMeleeBoostedLevel(player.baseStrength())
                : player.boostedStrength();
        int boostedAtt = player.assumeBestPotion()
                ? PotionBoosts.bestMeleeBoostedLevel(player.baseAttack())
                : player.boostedAttack();

        double prayerStrMult = player.assumeBestPrayer() ? OffensivePrayer.PIETY.meleeStrengthMult()
                : maxOf(player, OffensivePrayer::meleeStrengthMult);
        double prayerAttMult = player.assumeBestPrayer() ? OffensivePrayer.PIETY.meleeAttackMult()
                : maxOf(player, OffensivePrayer::meleeAttackMult);

        int styleBonusStr = player.stance() == Stance.AGGRESSIVE ? 3 : player.stance() == Stance.CONTROLLED ? 1 : 0;
        int styleBonusAtt = player.stance() == Stance.ACCURATE ? 3 : player.stance() == Stance.CONTROLLED ? 1 : 0;

        double voidMult = gear.voidSet().meleeMultiplier();

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(boostedStr, prayerStrMult, styleBonusStr, 8, voidMult);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(boostedAtt, prayerAttMult, styleBonusAtt, 8, voidMult);

        TargetBonus targetGearBonus = salveOrSlayerTargetBonus(style, gear, target, player);

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.str(), targetGearBonus.damage);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.attackBonus(style), targetGearBonus.accuracy);

        // Melee demonbane (Silverlight line): the weapon's own vs-demon passive —
        // a SEPARATE multiplicative floor step AFTER the salve/slayer slot, so it
        // STACKS with the on-task slayer helm (per the wiki DPS calc). Some
        // demons (Duke Sucellus) partially resist the bonus — see
        // resistedDemonbaneFraction.
        DemonbaneWeapon demonbane = gear.demonbaneWeapon();
        if (target.isDemon() && demonbane.appliesTo(style)) {
            int resist = target.demonbaneResistPercent();
            maxHit = (int) resistedDemonbaneFraction(demonbane.damageMult(), resist).applyFloor(maxHit);
            attackRoll = (int) resistedDemonbaneFraction(demonbane.accuracyMult(), resist).applyFloor(attackRoll);
        }

        // Dragon Hunter lance: the weapon's own vs-dragon passive — a SEPARATE
        // multiplicative floor step, stacking with the salve/slayer slot.
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        if (target.isDragon() && dh.appliesTo(style)) {
            maxHit = (int) dh.damageMult().applyFloor(maxHit);
            attackRoll = (int) dh.accuracyMult().applyFloor(attackRoll);
        }

        int defenceRoll = CombatMath.npcDefenceRoll(target.defenceLevel(), target.defenceBonus(style));

        // Osmumten's fang (and re-skins/cosmetics): two passives, STAB style
        // only (the double-accuracy-roll passive was restricted to Stab by the
        // 17 Jan 2024 update — see CombatMath.fangHitChance/fangAverageDamagePerAttack
        // for the exact formulas + citation). Neither passive changes maxHit or
        // attackRoll themselves — they change how hitChance/avgDamage are
        // derived from them, so this bypasses the generic finish() path.
        boolean fangApplies = gear.osmumtensFang() && style == CombatStyle.STAB;
        if (fangApplies) {
            return finishFang(maxHit, attackRoll, defenceRoll, gear.weaponSpeedTicks(), target.hitpoints());
        }

        return finish(maxHit, attackRoll, defenceRoll, gear.weaponSpeedTicks(), target.hitpoints(), false);
    }

    // ---- Ranged -----------------------------------------------------------------------

    private static DpsResult computeRanged(EquipmentStats gear, PlayerCombat player, Monster target) {
        int boostedRanged = player.assumeBestPotion()
                ? PotionBoosts.bestRangedBoostedLevel(player.baseRanged())
                : player.boostedRanged();

        double prayerAttMult = player.assumeBestPrayer() ? OffensivePrayer.RIGOUR.rangedAttackMult()
                : maxOf(player, OffensivePrayer::rangedAttackMult);
        double prayerStrMult = player.assumeBestPrayer() ? OffensivePrayer.RIGOUR.rangedStrengthMult()
                : maxOf(player, OffensivePrayer::rangedStrengthMult);

        // Ranged has only one meaningful style bonus (Accurate, +3), applied identically
        // to both the effective-attack and effective-strength calculations.
        int styleBonus = player.stance() == Stance.ACCURATE ? 3 : 0;

        // Elite ranged void boosts max hit (+12.5%) more than accuracy (+10%),
        // so the strength and attack sides take DIFFERENT void multipliers.
        int effStr = CombatMath.effectiveMeleeOrRangedLevel(boostedRanged, prayerStrMult, styleBonus, 8,
                gear.voidSet().rangedStrengthMultiplier());
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(boostedRanged, prayerAttMult, styleBonus, 8,
                gear.voidSet().rangedAccuracyMultiplier());

        TargetBonus targetGearBonus = salveOrSlayerTargetBonus(CombatStyle.RANGED, gear, target, player);

        DemonbaneWeapon demonbane = gear.demonbaneWeapon();
        boolean demonbaneApplies = target.isDemon() && demonbane.appliesTo(CombatStyle.RANGED);
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        boolean dragonbaneApplies = target.isDragon() && dh.appliesTo(CombatStyle.RANGED);

        // MAX HIT ONLY (per the wiki DPS calc's "these are additive with slayer
        // only"): when the on-task IMBUED slayer helm/black mask wins the target
        // slot, the ranged demonbane (Scorching bow +30% -> +6/20) and dragonbane
        // (DHCB +25% -> +5/20) damage bonuses fold ADDITIVELY into its 23/20 step
        // — ONE floor of (23+5+6)/20 — instead of applying as separate steps.
        // E.g. Cerberus on-task with a Scorching bow: floor(40 * 29/20) = 58
        // (the wiki's "total damage boost of 45%"), not floor(46 * 13/10) = 59.
        Fraction damageSlot = targetGearBonus.damage;
        boolean foldIntoSlayer = targetGearBonus.imbuedSlayer && (demonbaneApplies || dragonbaneApplies);
        if (foldIntoSlayer) {
            long numerator = 23
                    + (dragonbaneApplies ? additiveTwentieths(dh.damageMult()) : 0)
                    + (demonbaneApplies ? additiveTwentieths(demonbane.damageMult()) : 0);
            damageSlot = new Fraction(numerator, 20);
        }

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.rstr(), damageSlot);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.arange(), targetGearBonus.accuracy);

        // Dragon Hunter crossbow — the accuracy side is ALWAYS its own separate
        // multiplicative step; the damage side only when not folded above.
        if (dragonbaneApplies) {
            if (!foldIntoSlayer) {
                maxHit = (int) dh.damageMult().applyFloor(maxHit);
            }
            attackRoll = (int) dh.accuracyMult().applyFloor(attackRoll);
        }

        // Scorching bow (ranged demonbane) — same shape as dragonbane above.
        // Per-monster demonbane resistance (Duke Sucellus) is applied to the
        // separate-step accuracy roll and to the damage step when it is NOT
        // folded into the slayer-helm slot above; a resisted value folded
        // additively into that slot is not documented by the wiki calc, so we
        // do not attempt to fold a resisted bonus (out of scope — no known
        // demon combines on-task-imbued-helm ranged demonbane with resistance).
        if (demonbaneApplies) {
            int resist = target.demonbaneResistPercent();
            if (!foldIntoSlayer) {
                maxHit = (int) resistedDemonbaneFraction(demonbane.damageMult(), resist).applyFloor(maxHit);
            }
            attackRoll = (int) resistedDemonbaneFraction(demonbane.accuracyMult(), resist).applyFloor(attackRoll);
        }

        // Twisted bow: the weapon's own scaling with the TARGET's magic level —
        // also its own multiplicative step (stacks with everything above).
        if (gear.twistedBow()) {
            int accPct = CombatMath.twistedBowAccuracyPercent(target.magicLevel());
            int dmgPct = CombatMath.twistedBowDamagePercent(target.magicLevel());
            attackRoll = (int) new Fraction(accPct, 100).applyFloor(attackRoll);
            maxHit = (int) new Fraction(dmgPct, 100).applyFloor(maxHit);
        }

        int defenceRoll = CombatMath.npcDefenceRoll(target.defenceLevel(), target.drange());

        // Rapid attack style reduces the weapon's attack speed by 1 tick.
        int weaponSpeedTicks = gear.weaponSpeedTicks() - (player.stance() == Stance.RAPID ? 1 : 0);
        weaponSpeedTicks = Math.max(1, weaponSpeedTicks);

        return finish(maxHit, attackRoll, defenceRoll, weaponSpeedTicks, target.hitpoints(), false);
    }

    // ---- Magic --------------------------------------------------------------------------

    /** Marker for "derive the base max hit from the worn powered staff at the boosted Magic level". */
    private static final int POWERED_STAFF_SENTINEL = -1;

    /**
     * The Magic-level boost {@link PlayerCombat#assumeBestPotion()} applies for
     * the given {@link PlayerCombat#magicPotionVariant()} — see
     * {@link CombatIcons.BoostPotion} / {@link PotionBoosts}. Only the three
     * magic-boosting variants are meaningful here; a non-magic variant (or
     * {@code null}) falls back to the Imbued heart formula.
     */
    private static int magicPotionBoostedLevel(CombatIcons.BoostPotion variant, int baseLevel) {
        if (variant == CombatIcons.BoostPotion.SATURATED_HEART) {
            return PotionBoosts.saturatedHeartBoostedLevel(baseLevel);
        }
        if (variant == CombatIcons.BoostPotion.ANCIENT_BREW) {
            return PotionBoosts.ancientBrewBoostedLevel(baseLevel);
        }
        return PotionBoosts.imbuedHeartBoostedLevel(baseLevel);
    }

    private static DpsResult computeMagic(EquipmentStats gear, PlayerCombat player, Monster target,
                                          int baseSpellMaxHit, int castSpeedTicks, boolean approximate,
                                          Spell.Element spellElement) {
        int boostedMagic = player.assumeBestPotion()
                ? magicPotionBoostedLevel(player.magicPotionVariant(), player.baseMagic())
                : player.boostedMagic();

        if (baseSpellMaxHit == POWERED_STAFF_SENTINEL) {
            // Powered staff: built-in spell max hit scales with the boosted level.
            baseSpellMaxHit = gear.poweredStaff().maxHitAt(boostedMagic);
        }

        double prayerAccMult = player.assumeBestPrayer() ? OffensivePrayer.AUGURY.magicAccuracyMult()
                : maxOf(player, OffensivePrayer::magicAccuracyMult);
        double prayerDamagePercent = player.assumeBestPrayer() ? OffensivePrayer.AUGURY.magicDamagePercent()
                : maxOf(player, OffensivePrayer::magicDamagePercent);

        int styleBonus = player.stance() == Stance.ACCURATE ? 3 : player.stance() == Stance.LONGRANGE ? 1 : 0;
        double voidMult = gear.voidSet().magicMultiplier();

        int effMagic = CombatMath.effectiveMagicLevel(boostedMagic, prayerAccMult, styleBonus, voidMult);

        boolean salveVsUndead = target.isUndead() && gear.salveType() != SalveType.NONE;
        boolean slayerOnTaskImbued = player.onSlayerTask() && gear.slayerHeadgear().isImbued();
        boolean magicAccuracyBonusApplies = (salveVsUndead && gear.salveType().isImbued()) || slayerOnTaskImbued;
        Fraction accuracyTargetBonus = magicAccuracyBonusApplies ? new Fraction(23, 20) : Fraction.ONE;

        int accuracyRoll = CombatMath.magicAccuracyRoll(effMagic, gear.amagic(), accuracyTargetBonus);
        int defenceRoll = CombatMath.npcDefenceRoll(target.magicLevel(), target.dmagic());

        // Elemental weakness (e.g. frost dragons vs Fire spells): a per-monster
        // percent, added straight into the magic-damage percent when the cast
        // spell's element matches the monster's weakness element - matching
        // weirdgloop/osrs-dps-calc semantics. Spells with no element (Iban
        // Blast, Magic Dart/powered staves, god spells, all Ancient Magicks -
        // spellElement is null for those) never get this bonus, and a monster
        // with no weakness (weaknessElement() null) never grants it.
        // It is NOT folded into the magic-damage percent below; it is a separate
        // additive term applied last (see the elemental-weakness step at the end).
        boolean weaknessApplies = spellElement != null
                && target.weaknessElement() != null
                && target.weaknessElement().equals(spellElement.name());

        double totalDamagePercent = gear.mdmg()
                + gear.voidSet().eliteMagicDamageBonusPercent()
                + (target.isUndead() ? gear.salveType().magicDamagePercent() : 0.0)
                + prayerDamagePercent;
        int primaryDamage = CombatMath.magicPrimaryDamage(baseSpellMaxHit, totalDamagePercent);

        // Slayer on-task +15% never stacks with an active Salve bonus (per wiki).
        boolean slayerPreHitRollApplies = slayerOnTaskImbued && !salveVsUndead;
        int maxHit = CombatMath.magicPreHitRoll(primaryDamage, slayerPreHitRollApplies);

        // Dragon hunter wand — the weapon's own vs-dragon passive, a separate
        // multiplicative floor step stacking with the salve/slayer slot (see computeRanged).
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        if (target.isDragon() && dh.appliesTo(CombatStyle.MAGIC)) {
            maxHit = (int) dh.damageMult().applyFloor(maxHit);
            accuracyRoll = (int) dh.accuracyMult().applyFloor(accuracyRoll);
        }

        // Elemental weakness — the FINAL damage modifier: a separate additive term,
        // floor(baseSpellMaxHit * severity%), computed from the base spell max hit
        // and floored independently, then added on. This is weirdgloop/osrs-dps-calc's
        // exact stage (maxHit += trunc(baseMax * severity/100)) and the OSRS Wiki
        // "Maximum magic hit" (two floors, not one — folding it into the damage
        // percent above over-/under-shoots by 1 in ~12% of gear/level combos).
        if (weaknessApplies) {
            maxHit += CombatMath.elementalWeaknessBonus(baseSpellMaxHit, target.weaknessSeverity());
        }

        // Charged elemental tome (fire/water/earth): +10% damage vs NPCs to the
        // matching-element standard-spellbook spell. weirdgloop's MAX_HIT_TOME
        // step (floor(maxHit * 11/10)) is the FINAL magic-damage modifier —
        // applied AFTER the elemental-weakness bonus above, as its own floored
        // multiplicative step. spellElement is null for powered staves / Ancient
        // / non-elemental casts, so those never receive it.
        if (spellElement != null && gear.tome().boosts(spellElement)) {
            maxHit = (int) new Fraction(11, 10).applyFloor(maxHit);
        }

        return finish(maxHit, accuracyRoll, defenceRoll, castSpeedTicks, target.hitpoints(), approximate);
    }

    // ---- Shared helpers -----------------------------------------------------------------

    /**
     * The single non-stacking salve/slayer target-bonus slot, split into its
     * accuracy and damage components. {@code imbuedSlayer} records that the
     * WINNER is the on-task imbued slayer helm/black mask — the only case the
     * ranged max hit folds demonbane/dragonbane additively into (see
     * computeRanged).
     */
    private static final class TargetBonus {
        static final TargetBonus NONE = new TargetBonus(Fraction.ONE, Fraction.ONE, false);

        final Fraction accuracy;
        final Fraction damage;
        final boolean imbuedSlayer;

        TargetBonus(Fraction accuracy, Fraction damage, boolean imbuedSlayer) {
            this.accuracy = accuracy;
            this.damage = damage;
            this.imbuedSlayer = imbuedSlayer;
        }
    }

    /**
     * Target-specific gear-bonus slot shared by max hit and attack roll for
     * melee/ranged. ONE non-stacking slot holding Salve (vs undead) and the
     * on-task Slayer helm/black mask — the strongest effect wins (matching the
     * wiki DPS calc's if/else chain, where Salve takes precedence over the
     * helm; Salve's fractions also dominate numerically, so highest-wins and
     * the documented "salve wins over slayer" coincide).
     *
     * <p>Demonbane, Dragon Hunter weapons and the Twisted bow do NOT live in
     * this slot — each is the weapon's own passive, applied as a separate
     * multiplicative floor step in computeMelee/computeRanged (stacking with
     * this slot), with one exception: the ranged MAX HIT folds demonbane/
     * dragonbane damage additively into an on-task imbued helm's 23/20 (see
     * computeRanged; wiki calc: "these are additive with slayer only").
     */
    private static TargetBonus salveOrSlayerTargetBonus(CombatStyle style, EquipmentStats gear, Monster target, PlayerCombat player) {
        TargetBonus best = TargetBonus.NONE;

        if (target.isUndead()) {
            Fraction salve = style.isMelee() ? gear.salveType().meleeBonus() : gear.salveType().rangedBonus();
            best = stronger(best, new TargetBonus(salve, salve, false));
        }
        if (player.onSlayerTask() && gear.slayerHeadgear().wornAtAll()) {
            boolean imbued = gear.slayerHeadgear().isImbued();
            Fraction slayer = style.isMelee()
                    ? new Fraction(7, 6)
                    : (imbued ? new Fraction(23, 20) : Fraction.ONE); // ranged: imbued only
            best = stronger(best, new TargetBonus(slayer, slayer, imbued && !slayer.isOne()));
        }
        return best;
    }

    /**
     * A bonus fraction's additive contribution to the imbued slayer helm's
     * numerator, in twentieths: e.g. the Scorching bow's 13/10 (+30%) is +6,
     * the DHCB's 5/4 (+25%) is +5 — exactly the wiki calc's
     * {@code numerator += 5/6} constants on top of the helm's 23/20. Exact for
     * every wired bonus (both divide evenly); revisit if a bonus whose percent
     * is not a multiple of 5 ever needs folding.
     */
    private static long additiveTwentieths(Fraction bonus) {
        return (bonus.numerator - bonus.denominator) * 20 / bonus.denominator;
    }

    /**
     * Scales a demonbane bonus fraction down for a monster's demonbane
     * resistance: the bonus's excess-over-1 (its "BONUS portion", e.g.
     * Emberlight's 17/10 is 1 + 7/10) is reduced by {@code resistPercent}%,
     * so the final multiplier is {@code 1 + excess * (1 - resistPercent/100)}.
     * Duke Sucellus (30% resistance): Emberlight's documented +70% becomes
     * +49% (0.7 * 0.7 = 0.49), i.e. 149/100 instead of 17/10 — per the OSRS
     * Wiki ("an emberlight would only have 49% increased damage and accuracy
     * against him rather than 70%"). {@code resistPercent} of 0 (the default
     * for almost every demon) returns the bonus unchanged.
     */
    private static Fraction resistedDemonbaneFraction(Fraction bonus, int resistPercent) {
        if (resistPercent <= 0 || bonus.isOne()) {
            return bonus;
        }
        // excess = bonus - 1, as a fraction with the same denominator as bonus.
        long excessNumerator = bonus.numerator - bonus.denominator;
        // scaledExcess = excess * (100 - resistPercent) / 100
        long numerator = bonus.denominator * 100L + excessNumerator * (100L - resistPercent);
        long denominator = bonus.denominator * 100L;
        return new Fraction(numerator, denominator);
    }

    /** The stronger of two slot candidates: higher damage multiplier wins, accuracy breaks ties. */
    private static TargetBonus stronger(TargetBonus a, TargetBonus b) {
        double da = a.damage.asDouble();
        double db = b.damage.asDouble();
        if (db > da) {
            return b;
        }
        if (db == da && b.accuracy.asDouble() > a.accuracy.asDouble()) {
            return b;
        }
        return a;
    }

    private static double maxOf(PlayerCombat player, java.util.function.ToDoubleFunction<OffensivePrayer> extractor) {
        double best = 1.0;
        for (OffensivePrayer prayer : player.activePrayers()) {
            best = Math.max(best, extractor.applyAsDouble(prayer));
        }
        return best;
    }

    private static DpsResult finish(int maxHit, int attackRoll, int defenceRoll, int weaponSpeedTicks,
                                    int targetHitpoints, boolean baseEstimate) {
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double avgDamage = CombatMath.averageDamagePerAttack(hitChance, maxHit);
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        // avgDamage is the expected damage per attack (misses included) — GearScape's "Avg Hit".
        double overkill = CombatMath.expectedOverkill(maxHit, targetHitpoints);
        // TTK must account for overkill: the killing blow rolls past 0 HP, wasting
        // `overkill` HP of damage, so effective damage per kill is HP + overkill.
        // By Wald's identity E[TTK] = (HP + E[overkill]) / DPS exactly — the naive
        // HP/DPS understates it (e.g. Cerberus 57.3s vs GearScape's overkill-aware 59s).
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(maxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, baseEstimate);
    }

    /**
     * Osmumten's fang variant of {@link #finish}: uses the fang's own
     * double-accuracy-roll hit chance and compressed-damage-roll average
     * damage (see {@link CombatMath#fangHitChance} /
     * {@link CombatMath#fangAverageDamagePerAttack} for the formulas +
     * citation). {@code maxHit} in the returned {@link DpsResult} is left as
     * the TRUE (unshrunk) max hit, matching how the wiki/GearScape display it
     * — only the roll range feeding avgDamage is compressed.
     *
     * <p>Overkill/TTK reuse the generic uniform-0..maxHit overkill model on
     * the true max hit (not the shrunk range) as an approximation — modelling
     * overkill exactly for the fang's compressed distribution is out of scope
     * here (Tier B, not requested); this only matters for the very last hit
     * of a kill and the effect is small.
     */
    private static DpsResult finishFang(int maxHit, int attackRoll, int defenceRoll, int weaponSpeedTicks,
                                        int targetHitpoints) {
        double hitChance = CombatMath.fangHitChance(attackRoll, defenceRoll);
        double avgDamage = CombatMath.fangAverageDamagePerAttack(hitChance, maxHit);
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        double overkill = CombatMath.expectedOverkill(maxHit, targetHitpoints);
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(maxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, false);
    }
}
