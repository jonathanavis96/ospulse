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
 * line + ranged Scorching bow; accuracy/damage split, applied as the weapon's
 * own step stacking with the salve/slayer slot, with the ranged max hit
 * folding additively into an on-task imbued helm — see computeRanged), Dragon
 * Hunter lance/crossbow/wand vs dragons and the Twisted bow's vs-target-magic
 * scaling (separate multiplicative steps — they stack with the target
 * slot, with the same ranged-max-hit additive fold for the DHCB),
 * powered-staff built-in spells and real {@link Spell} max hits for
 * autocast. Unknown/unmodelled effects (scythe multi-hit, special attacks,
 * elemental weakness, Avarice, Tomes, Tumeken's 3x gear multiplier, ...)
 * are simply not applied — extend this class (and {@link CombatMath}) to add
 * them, gated behind their own "applies when" predicate.
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
            return computeMagic(gear, player, target, baseSpellMaxHit, gear.weaponSpeedTicks(), false);
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
            return computeMagic(gear, player, target, POWERED_STAFF_SENTINEL, gear.weaponSpeedTicks(),
                    gear.poweredStaff().approximate());
        }
        int baseMaxHit = spell == null ? 0 : spell.baseMaxHit();
        return computeMagic(gear, player, target, baseMaxHit, Spell.CAST_SPEED_TICKS, false);
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
        // STACKS with the on-task slayer helm (per the wiki DPS calc).
        DemonbaneWeapon demonbane = gear.demonbaneWeapon();
        if (target.isDemon() && demonbane.appliesTo(style)) {
            maxHit = (int) demonbane.damageMult().applyFloor(maxHit);
            attackRoll = (int) demonbane.accuracyMult().applyFloor(attackRoll);
        }

        // Dragon Hunter lance: the weapon's own vs-dragon passive — a SEPARATE
        // multiplicative floor step, stacking with the salve/slayer slot.
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        if (target.isDragon() && dh.appliesTo(style)) {
            maxHit = (int) dh.damageMult().applyFloor(maxHit);
            attackRoll = (int) dh.accuracyMult().applyFloor(attackRoll);
        }

        int defenceRoll = CombatMath.npcDefenceRoll(target.defenceLevel(), target.defenceBonus(style));

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

        double voidMult = gear.voidSet().rangedMultiplier();

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(boostedRanged, prayerStrMult, styleBonus, 8, voidMult);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(boostedRanged, prayerAttMult, styleBonus, 8, voidMult);

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
        if (demonbaneApplies) {
            if (!foldIntoSlayer) {
                maxHit = (int) demonbane.damageMult().applyFloor(maxHit);
            }
            attackRoll = (int) demonbane.accuracyMult().applyFloor(attackRoll);
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

    private static DpsResult computeMagic(EquipmentStats gear, PlayerCombat player, Monster target,
                                          int baseSpellMaxHit, int castSpeedTicks, boolean approximate) {
        int boostedMagic = player.assumeBestPotion()
                ? PotionBoosts.bestMagicBoostedLevel(player.baseMagic())
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
        double ttkSeconds = dps > 0 ? targetHitpoints / dps : 0.0;
        double overkill = CombatMath.expectedOverkill(maxHit, targetHitpoints);
        return new DpsResult(maxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, baseEstimate);
    }
}
