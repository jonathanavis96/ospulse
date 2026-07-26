package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Charged Tonalztics of Ralos (item id 28922) — two full, independent damage
 * rolls per attack, per the OSRS Wiki ("the weapon will hit twice, with two
 * independent damage rolls"); see {@link TonalzticsDualHit}. The uncharged
 * variant (28919) is an ordinary single-roll ranged weapon and must produce
 * byte-identical DPS to a plain ranged weapon of the same stats — the
 * regression this test proves is that {@code tonalzticsOfRalosCharged() ==
 * false} (the default, and the only state {@link
 * com.ospulse.session.GearVariants#isTonalzticsOfRalosCharged} ever returns
 * for anything other than id 28922) never touches {@link DpsCalculator}'s
 * generic path at all.
 */
public class TonalzticsRalosEffectTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /** +100 arange, +150 rstr, speed 4; charged flag set by the caller. */
    private static EquipmentStats.Builder gear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 150, 0.0, 0)
                .weaponSpeedTicks(4);
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

    private static DpsResult compute(EquipmentStats gear, Monster target) {
        return DpsCalculator.compute(gear, player(), CombatStyle.RANGED, target, 0);
    }

    @Test
    public void charged_doublesAverageDamageVersusUncharged() {
        DpsResult uncharged = compute(gear().build(), monster());
        DpsResult charged = compute(gear().tonalzticsOfRalosCharged(true).build(), monster());

        // Same accuracy/maxHit (the passive changes NOTHING about the roll
        // itself, only how many independent rolls happen) - only avgHit/dps
        // double.
        assertEquals(uncharged.maxHit(), charged.maxHit());
        assertEquals(uncharged.accuracy(), charged.accuracy(), 1e-12);
        assertEquals(2.0 * uncharged.avgHit(), charged.avgHit(), 1e-9);
        assertEquals(2.0 * uncharged.dps(), charged.dps(), 1e-9);
    }

    @Test
    public void charged_endToEnd_matchesTonalzticsDualHitFormulaDirectly() {
        EquipmentStats chargedGear = gear().tonalzticsOfRalosCharged(true).build();
        Monster target = monster();
        DpsResult result = compute(chargedGear, target);

        // Recompute independently via the same formula this stage adds,
        // using the plain (uncapped, no target-damage-rule) inputs this
        // fixture is built to exercise, to prove the wiring - not just the
        // isolated math - is correct.
        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 150, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 100, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        DpsResult expected = TonalzticsDualHit.finish(maxHit, -1, MonsterCombatRequirement.CapMode.CLAMP,
                attackRoll, defenceRoll, 4, target.hitpoints());

        assertEquals(expected.maxHit(), result.maxHit());
        assertEquals(expected.accuracy(), result.accuracy(), 1e-12);
        assertEquals(expected.avgHit(), result.avgHit(), 1e-9);
        assertEquals(expected.dps(), result.dps(), 1e-9);
        assertEquals(expected.ttkSeconds(), result.ttkSeconds(), 1e-9);
        assertEquals(expected.overkillPerKill(), result.overkillPerKill(), 1e-9);
    }

    @Test
    public void unrelatedWeapon_regression_dpsUnchangedFromPlainGenericPath() {
        // A loadout that never sets tonalzticsOfRalosCharged must go through
        // the ordinary finish() path untouched - i.e. produce the exact same
        // result as before this stage existed. Cross-checked directly
        // against DamageDistribution.averageDamagePerAttack/expectedOverkill.
        EquipmentStats plain = gear().build();
        Monster target = monster();
        DpsResult result = compute(plain, target);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 150, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 100, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double avg = DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
        double overkill = DamageDistribution.expectedOverkill(maxHit, target.hitpoints());
        double dps = CombatMath.dps(avg, 4);

        assertEquals(maxHit, result.maxHit());
        assertEquals(hitChance, result.accuracy(), 1e-12);
        assertEquals(avg, result.avgHit(), 1e-9);
        assertEquals(dps, result.dps(), 1e-9);
        assertEquals(overkill, result.overkillPerKill(), 1e-9);
    }
}
