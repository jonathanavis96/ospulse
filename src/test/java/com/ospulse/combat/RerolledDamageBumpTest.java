package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The generic {@code REROLL} damage cap's mean is NOT the same as feeding the
 * cap through the ordinary {@link DamageDistribution#averageDamage}/{@link
 * DamageDistribution#expectedOverkill}, even though the RE-ROLLED distribution's
 * SHAPE is exactly a uniform {@code 0..cap} roll (proved in {@link
 * CombatMathRerollEquivalenceTest}).
 *
 * <p>Those two ordinary methods bake in the game's "rolled 0 becomes 1"
 * convention as if the roll being fed in were the ORIGINAL {@code 0..maxHit}
 * weapon roll. But a re-rolled hitsplat is not that roll — the bump already
 * happened, once, to the original {@code 0..M} roll; the monster then
 * re-rolls anything still above the cap uniformly into {@code 0..cap}, and
 * THAT re-roll can and does produce a genuine, un-bumped zero. Applying the
 * bump a second time (by calling {@code averageDamage(hitChance,
 * cap)}) overstates the mean — at Verzik's ranged/magic cap of 3 that reports
 * 1.75 instead of the true {@code 1.5 + 1/(M+1)}, a real loadout's true M
 * putting that around 1.524 — this is exactly the mistake the fang's
 * re-rolled formulas were built to avoid one review cycle earlier, just not
 * yet caught on the generic path.
 *
 * <p>{@link DamageDistribution#rerolledAverageDamage}/{@code
 * rerolledExpectedOverkill} are the exact, zero-aware replacements, wired
 * into {@link DpsCalculator#finish}'s {@code REROLL} branch.
 */
public class RerolledDamageBumpTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    // ---- The identity: C/2 + 1/(M+1), NOT C/2 + 1/(C+1) -----------------------------------

    /**
     * Direct enumeration from first principles — NOT the closed form under
     * test: the "rolled 0 becomes 1" bump is part of the ORIGINAL {@code
     * 0..M} roll (applied first, to every raw outcome equally), and only
     * THEN does a result still above the cap re-roll uniformly into
     * {@code 0..cap} (contributing its expectation, {@code cap/2}, since a
     * re-roll is exactly uniform and carries no bump of its own).
     */
    private static double bruteForceRerolledMean(int uncappedMaxHit, int cap) {
        double total = 0.0;
        for (int raw = 0; raw <= uncappedMaxHit; raw++) {
            int bumped = raw <= 1 ? 1 : raw;
            total += (bumped > cap) ? (cap / 2.0) : bumped;
        }
        return total / (uncappedMaxHit + 1);
    }

    @Test
    public void identityMatchesABruteForceEnumeration_forSeveralMaxCapPairs() {
        int[][] pairs = {{40, 10}, {40, 3}, {25, 3}, {99, 10}, {20, 1}, {7, 5}};
        for (int[] pair : pairs) {
            int m = pair[0];
            int cap = pair[1];
            assertEquals("M=" + m + " cap=" + cap,
                bruteForceRerolledMean(m, cap),
                DamageDistribution.rerolledAverageDamage(1.0, m, cap) , 1e-12);
        }
    }

    @Test
    public void closedFormIsExactlyHalfCapPlusOneOverMPlusOne() {
        int[][] pairs = {{40, 10}, {40, 3}, {25, 3}, {99, 10}};
        for (int[] pair : pairs) {
            int m = pair[0];
            int cap = pair[1];
            double expected = cap / 2.0 + 1.0 / (m + 1.0);
            assertEquals("M=" + m + " cap=" + cap,
                expected, DamageDistribution.rerolledAverageDamage(1.0, m, cap), 1e-12);
        }
    }

    /** The naive "just feed the cap through the ordinary formula" bug this method replaces. */
    @Test
    public void isNotTheNaiveOrdinaryFormulaFedTheCap() {
        double correct = DamageDistribution.rerolledAverageDamage(1.0, 40, 3);
        double naive = DamageDistribution.averageDamage(1.0, 3); // the bug: bumps the re-roll's own zero
        assertNotEquals(naive, correct, 1e-9);
        assertTrue("the naive formula overstates the mean by double-applying the bump", naive > correct);
    }

    // ---- Verzik's ranged/magic case specifically: must NOT be 1.75 ------------------------

    @Test
    public void verzikRangedMagicCapOfThree_mustNotReportOnePointSevenFive() {
        for (int m : new int[]{20, 40, 99, 250}) {
            double result = DamageDistribution.rerolledAverageDamage(1.0, m, 3);
            assertNotEquals("M=" + m, 1.75, result, 1e-9);
            assertEquals("M=" + m, 1.5 + 1.0 / (m + 1.0), result, 1e-12);
        }
    }

    // ---- P(0) > 0 must actually change the overkill, not just the average -----------------

    @Test
    public void overkillDiffersFromTheNaiveOrdinaryFormula_becauseP0IsPositive() {
        double correct = DamageDistribution.rerolledExpectedOverkill(40, 3, 60);
        double naive = DamageDistribution.expectedOverkill(3, 60); // bakes in the bump, assumes P(0) == 0
        assertNotEquals("a re-rolled hitsplat of 0 is genuine, not folded into 1 the way the ordinary "
                + "distribution assumes — this must change the overkill, not just the average",
            naive, correct, 1e-6);
    }

    @Test
    public void overkillCapAtOrAboveMaxHit_reducesToThePlainUncappedOverkill() {
        assertEquals(DamageDistribution.expectedOverkill(40, 60), DamageDistribution.rerolledExpectedOverkill(40, 40, 60), 1e-12);
        assertEquals(DamageDistribution.expectedOverkill(40, 60), DamageDistribution.rerolledExpectedOverkill(40, 100, 60), 1e-12);
    }

    // ---- End-to-end: DpsCalculator's generic REROLL branch uses the new path --------------

    private static Monster verzikP1() {
        return Monster.builder()
                .name("Verzik Vitur (Normal mode, Phase 1)")
                .hitpoints(400)
                .defenceLevel(150)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(200)
                .build();
    }

    /** Same defence profile as {@link #verzikP1()} but not curated — reveals the true (uncapped) max hit. */
    private static Monster uncappedControl() {
        return Monster.builder()
                .name("Zzz Reroll Bump Control (Uncapped)")
                .hitpoints(400)
                .defenceLevel(150)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(200)
                .build();
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).defence(70, 70).ranged(99, 99).magic(99, 99)
                .prayer(70, 70).hitpoints(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    /** Deliberately huge ranged-strength bonus so the true (uncapped) ranged max hit is far above Verzik's cap of 3. */
    private static EquipmentStats rangedGear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 80, 0, 0, 0, 0, 0, 0, 150, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    private static EquipmentStats magicGear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 80, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    @Test
    public void dpsCalculatorRangedLoadoutAtVerzikUsesTheZeroAwareRerollFormula() {
        DpsResult control = DpsCalculator.compute(rangedGear(), player(), CombatStyle.RANGED, uncappedControl(), 0);
        DpsResult r = DpsCalculator.compute(rangedGear(), player(), CombatStyle.RANGED, verzikP1(), 0);
        assertEquals("cap must actually bind", 3, r.maxHit());
        assertTrue("sanity: true max hit must exceed the cap", control.maxHit() > 3);

        double expectedAvg = DamageDistribution.rerolledAverageDamage(r.accuracy(), control.maxHit(), 3);
        assertEquals(expectedAvg, r.avgHit(), 1e-9);
        assertNotEquals("must not be the naive bumped-cap average", 1.75, r.avgHit(), 1e-6);

        double expectedOverkill = DamageDistribution.rerolledExpectedOverkill(control.maxHit(), 3, verzikP1().hitpoints());
        assertEquals(expectedOverkill, r.overkillPerKill(), 1e-9);

        double expectedDps = CombatMath.dps(expectedAvg, 4);
        assertEquals(expectedDps, r.dps(), 1e-9);
        double expectedTtk = expectedDps > 0 ? (verzikP1().hitpoints() + expectedOverkill) / expectedDps : 0.0;
        assertEquals(expectedTtk, r.ttkSeconds(), 1e-9);
    }

    @Test
    public void dpsCalculatorMagicLoadoutAtVerzikUsesTheZeroAwareRerollFormula() {
        int baseSpellMaxHit = 60;
        DpsResult control = DpsCalculator.compute(magicGear(), player(), CombatStyle.MAGIC, uncappedControl(), baseSpellMaxHit);
        DpsResult r = DpsCalculator.compute(magicGear(), player(), CombatStyle.MAGIC, verzikP1(), baseSpellMaxHit);
        assertEquals("cap must actually bind", 3, r.maxHit());
        assertTrue("sanity: true max hit must exceed the cap", control.maxHit() > 3);

        double expectedAvg = DamageDistribution.rerolledAverageDamage(r.accuracy(), control.maxHit(), 3);
        assertEquals(expectedAvg, r.avgHit(), 1e-9);

        double expectedOverkill = DamageDistribution.rerolledExpectedOverkill(control.maxHit(), 3, verzikP1().hitpoints());
        assertEquals(expectedOverkill, r.overkillPerKill(), 1e-9);
    }
}
