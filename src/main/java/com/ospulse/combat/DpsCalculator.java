package com.ospulse.combat;

/**
 * Entry point: computes max hit / accuracy / DPS for one gear+player+style
 * setup against one {@link Monster}, per the OSRS Wiki DPS calculator
 * formulas (see per-method Javadoc in {@link CombatMath} for citations).
 * <p>
 * Tier-A effects modelled (each isolated below, with an "applies when"
 * predicate, per the design doc): offensive prayers, best-potion boosts,
 * Slayer helm(i)/black mask(i) on-task bonus, Salve amulet variants vs
 * Undead, Void/Elite Void. Tier-B effects: demonbane melee weapons
 * (accuracy/damage split, sharing the highest-wins target slot), Dragon
 * Hunter lance/crossbow vs dragons and the Twisted bow's vs-target-magic
 * scaling (both separate multiplicative steps — they stack with the target
 * slot), powered-staff built-in spells and real {@link Spell} max hits for
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

        TargetBonus targetGearBonus = meleeOrRangedTargetGearBonus(style, gear, target, player);

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.str(), targetGearBonus.damage);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.attackBonus(style), targetGearBonus.accuracy);

        // Dragon Hunter lance: the weapon's own vs-dragon passive — a SEPARATE
        // multiplicative floor step, stacking with the salve/slayer/demonbane slot.
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

        TargetBonus targetGearBonus = meleeOrRangedTargetGearBonus(CombatStyle.RANGED, gear, target, player);

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.rstr(), targetGearBonus.damage);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.arange(), targetGearBonus.accuracy);

        // Dragon Hunter crossbow — separate multiplicative step (see computeMelee).
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        if (target.isDragon() && dh.appliesTo(CombatStyle.RANGED)) {
            maxHit = (int) dh.damageMult().applyFloor(maxHit);
            attackRoll = (int) dh.accuracyMult().applyFloor(attackRoll);
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

    /** The single highest-wins target-specific gear-bonus slot, split into its accuracy and damage components. */
    private static final class TargetBonus {
        static final TargetBonus NONE = new TargetBonus(Fraction.ONE, Fraction.ONE);

        final Fraction accuracy;
        final Fraction damage;

        TargetBonus(Fraction accuracy, Fraction damage) {
            this.accuracy = accuracy;
            this.damage = damage;
        }
    }

    /**
     * Target-specific gear-bonus slot shared by max hit and attack roll for melee/ranged.
     * ONE "target-specific gear bonus" slot: Salve (vs undead), the on-task Slayer
     * helm/black mask, and melee demonbane (vs demons) all live here and DO NOT
     * stack — the strongest effect wins and contributes BOTH its accuracy and its
     * damage component (matching GearScape / the wiki DPS calc; e.g. an Emberlight
     * (1.7/1.7) on a demon overrides the slayer-helm 7/6 rather than multiplying on
     * top of it; Salve already dominates the slayer value numerically, preserving
     * the documented "salve wins over slayer").
     *
     * <p>"Strongest" = highest damage multiplier (accuracy breaks ties). For the
     * damage-only Silverlight/Darklight (1.0 acc / 1.6 dmg) worn on-task against
     * an undead demon this means the sword's damage bonus is taken INSTEAD of the
     * slayer helm's symmetric 7/6 — the wiki doesn't document that corner
     * explicitly; GearScape-parity TODO before trusting it to the last percent.
     *
     * <p>Dragon Hunter weapons and the Twisted bow deliberately do NOT live in
     * this slot — they're the weapon's own passive, applied as a separate
     * multiplicative step in computeMelee/computeRanged (they stack with this
     * slot). The ranged demonbane Scorching bow is not wired yet: its bonus is
     * documented as stacking ADDITIVELY with the slayer helm (i), which fits
     * neither this slot nor a separate multiplicative step (see GearVariants).
     */
    private static TargetBonus meleeOrRangedTargetGearBonus(CombatStyle style, EquipmentStats gear, Monster target, PlayerCombat player) {
        TargetBonus best = TargetBonus.NONE;

        if (target.isUndead()) {
            Fraction salve = style.isMelee() ? gear.salveType().meleeBonus() : gear.salveType().rangedBonus();
            best = stronger(best, new TargetBonus(salve, salve));
        }
        if (player.onSlayerTask() && gear.slayerHeadgear().wornAtAll()) {
            Fraction slayer = style.isMelee()
                    ? new Fraction(7, 6)
                    : (gear.slayerHeadgear().isImbued() ? new Fraction(23, 20) : Fraction.ONE); // ranged: imbued only
            best = stronger(best, new TargetBonus(slayer, slayer));
        }
        if (style.isMelee() && target.isDemon() && gear.demonbaneWeapon().applies()) {
            best = stronger(best, new TargetBonus(
                    gear.demonbaneWeapon().accuracyMult(), gear.demonbaneWeapon().damageMult()));
        }
        return best;
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
