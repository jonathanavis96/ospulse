package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Crystal armour set (full ACTIVE helm+body+legs) + an ACTIVE Crystal bow /
 * Bow of Faerdhinen: +15% damage/+30% accuracy, per the OSRS Wiki. A
 * conditional weapon+armour combo, not baked into per-piece stats — modelled
 * via {@link EquipmentStats#crystalSetBonusActive()}, resolved once in
 * {@code GearMapper} (see {@code com.ospulse.session.GearVariants}).
 *
 * <p><b>The critical regression this stage exists to prevent</b> (per the
 * design spec's explicit warning): a prior P1 defect in this repo came from
 * crediting fully-statted ACTIVE crystal armour when the player owned the
 * zero-stat INACTIVE pieces. Every test below that constructs gear directly
 * via {@link EquipmentStats.Builder#crystalSetBonusActive} is exercising the
 * DPS-side half of that contract; {@code GearVariantsTest} exercises the
 * item-id resolution half (the inactive-id exclusion) directly.
 */
public class CrystalSetEffectTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /** +100 arange, +100 rstr, speed 5; crystalSetBonusActive set by the caller. */
    private static EquipmentStats.Builder gear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 100, 0.0, 0)
                .weaponSpeedTicks(5);
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.ACCURATE)
                .build();
    }

    private static Monster monster() {
        return Monster.builder()
                .name("Test")
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear) {
        return DpsCalculator.compute(gear, player(), CombatStyle.RANGED, monster(), 0);
    }

    @Test
    public void fullActiveSetAndBow_grantsFifteenPercentDamageAndThirtyPercentAccuracy() {
        DpsResult base = compute(gear().build());
        DpsResult withSet = compute(gear().crystalSetBonusActive(true).build());

        // Hard-coded 15%/30%, independent of DpsCalculator's own Fraction literals.
        int expectedMaxHit = (int) Math.floor(base.maxHit() * 115.0 / 100.0);
        assertEquals(expectedMaxHit, withSet.maxHit());
        assertTrue("accuracy must improve with the full set + bow", withSet.accuracy() > base.accuracy());
    }

    @Test
    public void withoutTheFlag_hasNoEffect_regression() {
        // The flag is the whole story - a loadout that never sets it must
        // produce exactly the plain (pre-this-stage) formula, byte-identical
        // to what DpsCalculator computed before crystalSetBonusActive existed.
        EquipmentStats plain = gear().build();
        DpsResult result = compute(plain);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 100, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 100, Fraction.ONE);
        assertEquals(maxHit, result.maxHit());
        double expectedHitChance = CombatMath.hitChance(attackRoll, CombatMath.npcDefenceRoll(100, 50));
        assertEquals(expectedHitChance, result.accuracy(), 1e-12);
    }

    @Test
    public void endToEnd_matchesDirectFormulaComputation() {
        EquipmentStats withSet = gear().crystalSetBonusActive(true).build();
        DpsResult result = compute(withSet);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int baseMaxHit = CombatMath.meleeOrRangedMaxHit(effStr, 100, Fraction.ONE);
        int baseAttackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 100, Fraction.ONE);
        int maxHit = (int) new Fraction(23, 20).applyFloor(baseMaxHit);
        int attackRoll = (int) new Fraction(13, 10).applyFloor(baseAttackRoll);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double avg = DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
        double dps = CombatMath.dps(avg, 5);

        assertEquals(maxHit, result.maxHit());
        assertEquals(hitChance, result.accuracy(), 1e-12);
        assertEquals(avg, result.avgHit(), 1e-9);
        assertEquals(dps, result.dps(), 1e-9);
    }
}
