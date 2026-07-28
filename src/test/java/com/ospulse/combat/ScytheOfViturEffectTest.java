package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Scythe of Vitur (and Holy/Sanguine reskins) — hits 1-3x by target size,
 * each hit's max hit decaying to 50% (rounded down) of the previous, per the
 * OSRS Wiki; see {@link ScytheCascade}. Previously modelled as an ordinary
 * single-hit weapon, ranking the scythe far below its true DPS at exactly
 * the large (2x2/3x3+) bosses it is BiS at — the bug this stage fixes.
 */
public class ScytheOfViturEffectTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /** +80 astab/aslash/acrush, +100 str, speed 4; scytheOfVitur flag set by the caller. */
    private static EquipmentStats.Builder gear() {
        return EquipmentStats.builder()
                .add(80, 80, 80, 0, 0, 0, 0, 0, 0, 0, 100, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    private static Monster monster(int size) {
        return Monster.builder()
                .name("Test")
                .hitpoints(1200)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .size(size)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear, Monster target) {
        return DpsCalculator.compute(gear, player(), CombatStyle.SLASH, target, 0);
    }

    @Test
    public void oneByOneTarget_matchesPlainSingleHitWeapon() {
        // A 1x1 target degenerates to exactly one hit - byte-identical to a
        // plain weapon of the same maxHit/accuracy.
        DpsResult plain = compute(gear().build(), monster(1));
        DpsResult scythe = compute(gear().scytheOfVitur(true).build(), monster(1));
        assertEquals(plain.maxHit(), scythe.maxHit());
        assertEquals(plain.accuracy(), scythe.accuracy(), 1e-12);
        assertEquals(plain.avgHit(), scythe.avgHit(), 1e-9);
        assertEquals(plain.dps(), scythe.dps(), 1e-9);
        assertEquals(plain.ttkSeconds(), scythe.ttkSeconds(), 1e-9);
    }

    @Test
    public void threeByThreeOrLargerTarget_dealsMoreThanTripleTheSingleHitAverage() {
        // Symptom check: at a large boss, the cascade must clearly beat a
        // single hit (roughly 1 + 0.5 + 0.25 = 1.75x a single roll's average,
        // NOT 1x) - the exact ranking bug this stage exists to fix.
        DpsResult plain = compute(gear().build(), monster(5));
        DpsResult scythe = compute(gear().scytheOfVitur(true).build(), monster(5));
        assertTrue("cascade avgHit must be well above the single-hit average",
                scythe.avgHit() > plain.avgHit() * 1.5);
    }

    @Test
    public void threeHitCascade_endToEnd_matchesScytheCascadeFormulaDirectly() {
        EquipmentStats scytheGear = gear().scytheOfVitur(true).build();
        Monster target = monster(5); // 3x3+ -> 3 hits
        DpsResult result = compute(scytheGear, target);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 0, 8, 1.0); // AGGRESSIVE gives no attack-side style bonus
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 100, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 80, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        DpsResult expected = ScytheCascade.finish(maxHit, -1, MonsterCombatRequirement.CapMode.CLAMP, 5,
                attackRoll, defenceRoll, 4, target.hitpoints());

        assertEquals(expected.maxHit(), result.maxHit());
        assertEquals(expected.accuracy(), result.accuracy(), 1e-12);
        assertEquals(expected.avgHit(), result.avgHit(), 1e-9);
        assertEquals(expected.dps(), result.dps(), 1e-9);
        assertEquals(expected.ttkSeconds(), result.ttkSeconds(), 1e-9);
        assertEquals(expected.overkillPerKill(), result.overkillPerKill(), 1e-9);
    }

    @Test
    public void twoByTwoTarget_getsExactlyTwoHits() {
        EquipmentStats scytheGear = gear().scytheOfVitur(true).build();
        Monster target = monster(2);
        DpsResult result = compute(scytheGear, target);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 0, 8, 1.0); // AGGRESSIVE gives no attack-side style bonus
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 100, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 80, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        DpsResult expected = ScytheCascade.finish(maxHit, -1, MonsterCombatRequirement.CapMode.CLAMP, 2,
                attackRoll, defenceRoll, 4, target.hitpoints());

        assertEquals(expected.avgHit(), result.avgHit(), 1e-9);
    }

    @Test
    public void unaffectedLoadout_regression_targetSizeNeverChangesDpsWithoutScythe() {
        EquipmentStats plain = gear().build();
        DpsResult size1 = compute(plain, monster(1));
        DpsResult size3 = compute(plain, monster(3));
        DpsResult size7 = compute(plain, monster(7));
        assertEquals(size1.maxHit(), size3.maxHit());
        assertEquals(size1.dps(), size3.dps(), 1e-9);
        assertEquals(size1.dps(), size7.dps(), 1e-9);
    }

    /**
     * The exact real-world interaction the director flagged: Scythe of
     * Vitur (a common Verzik phase 1 weapon) at a 5x5-sized (3-hit) target
     * under Verzik P1's shipped, REROLL-mode melee damage cap of 10 (see
     * {@code VerzikDamageCapTest}) — a prior P1-class defect in this
     * codebase came from exactly this kind of size+cap interaction being
     * modelled on only one of the two axes. Uses the REAL, shipped {@link
     * MonsterCombatRequirementRepository} lookup (the no-explicit-requirement
     * {@code compute} overload), not a hand-built requirement.
     */
    @Test
    public void verzikPhase1_rerollCap_appliesToEveryCascadeHit() {
        Monster verzikP1 = Monster.builder()
                .name("Verzik Vitur (Normal mode, Phase 1)")
                .hitpoints(2000)
                .defenceLevel(150)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(200)
                .size(5)
                .build();
        EquipmentStats scytheGear = gear().scytheOfVitur(true).build();
        DpsResult result = DpsCalculator.compute(scytheGear, player(), CombatStyle.SLASH, verzikP1, 0);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 0, 8, 1.0); // AGGRESSIVE gives no attack-side style bonus
        int uncappedMaxHit = CombatMath.meleeOrRangedMaxHit(effStr, 100, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 80, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(150, 50);
        assertTrue("fixture must actually exceed the cap for this test to mean anything", uncappedMaxHit > 10);

        DpsResult expected = ScytheCascade.finish(uncappedMaxHit, 10, MonsterCombatRequirement.CapMode.REROLL, 5,
                attackRoll, defenceRoll, 4, verzikP1.hitpoints());

        assertEquals("cap must actually bind (visible max hit == 10)", 10, result.maxHit());
        assertEquals(expected.avgHit(), result.avgHit(), 1e-9);
        assertEquals(expected.overkillPerKill(), result.overkillPerKill(), 1e-9);
        assertEquals(expected.dps(), result.dps(), 1e-9);

        // And this must NOT equal what a naive "cap folded into a single roll"
        // model would give: 3 uncapped cascade hits summed then clamped is a
        // different (wrong) shape from 3 independently re-rolled hits.
        double naiveWrong = ScytheCascade.averageDamage(expected.accuracy(), uncappedMaxHit, 3);
        assertTrue(Math.abs(naiveWrong - result.avgHit()) > 1e-6);
    }
}
