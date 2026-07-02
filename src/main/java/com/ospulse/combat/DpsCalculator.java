package com.ospulse.combat;

/**
 * Entry point: computes max hit / accuracy / DPS for one gear+player+style
 * setup against one {@link Monster}, per the OSRS Wiki DPS calculator
 * formulas (see per-method Javadoc in {@link CombatMath} for citations).
 * <p>
 * Tier-A effects modelled (each isolated below, with an "applies when"
 * predicate, per the design doc): offensive prayers, best-potion boosts,
 * Slayer helm(i)/black mask(i) on-task bonus, Salve amulet variants vs
 * Undead, Void/Elite Void. Unknown/unmodelled effects (Tier B/C: dragon
 * hunter gear, twisted bow scaling, scythe multi-hit, special attacks,
 * elemental weakness, Avarice, Tomes, ...) are simply not applied — extend
 * this class (and {@link CombatMath}) to add them, gated behind their own
 * "applies when" predicate, without touching the Tier-A paths.
 */
public final class DpsCalculator {
    private DpsCalculator() {
    }

    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, int baseSpellMaxHit) {
        if (style == CombatStyle.MAGIC) {
            return computeMagic(gear, player, target, baseSpellMaxHit);
        }
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

        Fraction targetGearBonus = meleeOrRangedTargetGearBonus(style, gear, target, player);

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.str(), targetGearBonus);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.attackBonus(style), targetGearBonus);
        int defenceRoll = CombatMath.npcDefenceRoll(target.defenceLevel(), target.defenceBonus(style));

        return finish(maxHit, attackRoll, defenceRoll, gear.weaponSpeedTicks(), target.hitpoints());
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

        Fraction targetGearBonus = meleeOrRangedTargetGearBonus(CombatStyle.RANGED, gear, target, player);

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.rstr(), targetGearBonus);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.arange(), targetGearBonus);
        int defenceRoll = CombatMath.npcDefenceRoll(target.defenceLevel(), target.drange());

        // Rapid attack style reduces the weapon's attack speed by 1 tick.
        int weaponSpeedTicks = gear.weaponSpeedTicks() - (player.stance() == Stance.RAPID ? 1 : 0);
        weaponSpeedTicks = Math.max(1, weaponSpeedTicks);

        return finish(maxHit, attackRoll, defenceRoll, weaponSpeedTicks, target.hitpoints());
    }

    // ---- Magic --------------------------------------------------------------------------

    private static DpsResult computeMagic(EquipmentStats gear, PlayerCombat player, Monster target, int baseSpellMaxHit) {
        int boostedMagic = player.assumeBestPotion()
                ? PotionBoosts.bestMagicBoostedLevel(player.baseMagic())
                : player.boostedMagic();

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

        return finish(maxHit, accuracyRoll, defenceRoll, gear.weaponSpeedTicks(), target.hitpoints());
    }

    // ---- Shared helpers -----------------------------------------------------------------

    /**
     * Target-specific gear-bonus fraction shared by max hit and attack roll for melee/ranged
     * (per the wiki: "Gear bonus is the same as in Step Two/Two"). Salve (vs Undead) and the
     * on-task Slayer helm(i)/black mask(i) bonus never stack; Salve wins when both would apply.
     */
    private static Fraction meleeOrRangedTargetGearBonus(CombatStyle style, EquipmentStats gear, Monster target, PlayerCombat player) {
        if (target.isUndead()) {
            Fraction salveFraction = style.isMelee() ? gear.salveType().meleeBonus() : gear.salveType().rangedBonus();
            if (!salveFraction.isOne()) {
                return salveFraction;
            }
        }
        if (player.onSlayerTask() && gear.slayerHeadgear().wornAtAll()) {
            if (style.isMelee()) {
                return new Fraction(7, 6);
            }
            // Ranged: only the imbued variant extends the bonus.
            return gear.slayerHeadgear().isImbued() ? new Fraction(23, 20) : Fraction.ONE;
        }
        return Fraction.ONE;
    }

    private static double maxOf(PlayerCombat player, java.util.function.ToDoubleFunction<OffensivePrayer> extractor) {
        double best = 1.0;
        for (OffensivePrayer prayer : player.activePrayers()) {
            best = Math.max(best, extractor.applyAsDouble(prayer));
        }
        return best;
    }

    private static DpsResult finish(int maxHit, int attackRoll, int defenceRoll, int weaponSpeedTicks, int targetHitpoints) {
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double avgDamage = CombatMath.averageDamagePerAttack(hitChance, maxHit);
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        // avgDamage is the expected damage per attack (misses included) — GearScape's "Avg Hit".
        double ttkSeconds = dps > 0 ? targetHitpoints / dps : 0.0;
        // Tier-A only: no unmodelled-effect detection signal exists in the current input
        // model, so baseEstimate is always false for now. Future tiers that add gear
        // signals for e.g. twisted bow / scythe / special attacks should set this true
        // whenever such a signal is present but not (yet) accounted for above.
        return new DpsResult(maxHit, hitChance, dps, avgDamage, ttkSeconds, false);
    }
}
