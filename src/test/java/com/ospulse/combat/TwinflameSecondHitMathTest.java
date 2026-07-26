package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pure-math tests for {@link TwinflameSecondHit}, independent of any
 * DpsCalculator scenario. The core claim under test: the exact expected
 * second-hit damage is {@code hitChance * E[floor(0.4*D)]}, which is NOT the
 * same as {@code hitChance * floor(0.4 * E[D])} nor {@code 0.4 *
 * hitChance*E[D]} — the floor must be applied per-outcome, before averaging.
 */
public class TwinflameSecondHitMathTest {
    private static final double DELTA = 1e-9;

    /**
     * Reconstructs the expected second-hit average from first principles,
     * independently of {@link TwinflameSecondHit}'s implementation: every raw
     * roll 0..maxHit is equally likely ({@code 1/(maxHit+1)} each), a raw roll
     * of 0 is bumped to a landed damage of 1 (the standard "rolled 0 becomes
     * 1 on a hit" rule), and the second hit is {@code floor(0.4 * landed)}.
     * This does not reuse the production formula's grouped probabilities
     * (P(1)=2/(maxHit+1), P(d)=1/(maxHit+1) for d=2..maxHit) — it enumerates
     * every one of the {@code maxHit+1} raw outcomes individually.
     */
    private static double bruteForceSecondHitAverage(double hitChance, int maxHit) {
        int outcomes = maxHit + 1;
        double sum = 0.0;
        for (int raw = 0; raw <= maxHit; raw++) {
            int landed = raw == 0 ? 1 : raw;
            sum += Math.floor(landed * 0.4);
        }
        return hitChance * (sum / outcomes);
    }

    @Test
    public void secondHitAverage_matchesBruteForceEnumeration_acrossVariousMaxHits() {
        int[] maxHits = {1, 4, 5, 8, 12, 16, 20, 24, 44};
        double[] hitChances = {1.0, 0.85, 0.53, 0.1};
        for (int maxHit : maxHits) {
            for (double hitChance : hitChances) {
                double expected = bruteForceSecondHitAverage(hitChance, maxHit);
                double actual = TwinflameSecondHit.secondHitAverage(hitChance, maxHit);
                assertEquals("maxHit=" + maxHit + " hitChance=" + hitChance, expected, actual, DELTA);
            }
        }
    }

    @Test
    public void secondHitAverage_isNotTheNaiveFortyPercentOfExpectedFirstHit() {
        // Fire Bolt tier: maxHit=12. Naive (rejected) shortcut: 0.4 * averageDamagePerAttack.
        // Exact: hitChance * sum(floor(0.4*d) * P(d)) - these must differ for a hit chance of 1.
        int maxHit = 12;
        double hitChance = 1.0;
        double naiveShortcut = 0.4 * CombatMath.averageDamagePerAttack(hitChance, maxHit);
        double exact = TwinflameSecondHit.secondHitAverage(hitChance, maxHit);
        assertTrue("the floored exact expectation must differ from the naive 0.4x-of-average shortcut",
                Math.abs(naiveShortcut - exact) > 1e-6);
    }

    @Test
    public void secondHitAverage_zeroMaxHitIsZero() {
        assertEquals(0.0, TwinflameSecondHit.secondHitAverage(0.9, 0), DELTA);
    }

    @Test
    public void cappedSecondHitAverage_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.7;
        int maxHit = 20;
        assertEquals(TwinflameSecondHit.secondHitAverage(hitChance, maxHit),
                TwinflameSecondHit.cappedSecondHitAverage(hitChance, maxHit, maxHit), DELTA);
        assertEquals(TwinflameSecondHit.secondHitAverage(hitChance, maxHit),
                TwinflameSecondHit.cappedSecondHitAverage(hitChance, maxHit, maxHit + 5), DELTA);
    }

    @Test
    public void cappedSecondHitAverage_handComputedExample() {
        // uncappedMaxHit=10, cap=4, hitChance=1.0. Raw rolls 0..10, each weight 1/11.
        // Landed damage: raw 0 -> 1; else raw. Visible first hit = min(landed, 4).
        // second hit = floor(0.4 * visibleFirst).
        // raw=0 -> landed=1 -> visible=1 -> second=0
        // raw=1 -> landed=1 -> visible=1 -> second=0
        // raw=2 -> landed=2 -> visible=2 -> second=0
        // raw=3 -> landed=3 -> visible=3 -> second=1 (floor(1.2)=1)
        // raw=4..10 (7 outcomes) -> landed>=4 -> visible=4 -> second=1 (floor(1.6)=1)
        // sum = 0+0+0+1+1*7 = 8; average = 8/11
        double expected = 8.0 / 11.0;
        double actual = TwinflameSecondHit.cappedSecondHitAverage(1.0, 10, 4);
        assertEquals(expected, actual, DELTA);
    }

    @Test
    public void cappedSecondHitAverage_capAtOrBelowZeroIsZero() {
        assertEquals(0.0, TwinflameSecondHit.cappedSecondHitAverage(0.8, 10, 0), DELTA);
    }

    // ---- Overkill (combined-hitsplat model) --------------------------------------------

    /**
     * Independent brute-force reference for {@link
     * TwinflameSecondHit#combinedExpectedOverkill}: a plain recursive
     * expectation over remaining hitpoints, built directly from the raw
     * 0..maxHit roll enumeration (same style as {@link
     * #bruteForceSecondHitAverage}) rather than reusing the production
     * recurrence's grouped probabilities.
     */
    private static double bruteForceCombinedOverkill(int maxHit, int targetHitpoints) {
        double[] over = new double[targetHitpoints + 1];
        int outcomes = maxHit + 1;
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int raw = 0; raw <= maxHit; raw++) {
                int landed = raw == 0 ? 1 : raw;
                int combined = landed + (int) Math.floor(landed * 0.4);
                sum += (combined >= h) ? (combined - h) : over[h - combined];
            }
            over[h] = sum / outcomes;
        }
        return over[targetHitpoints];
    }

    @Test
    public void combinedExpectedOverkill_matchesBruteForceEnumeration_smallScale() {
        int[] maxHits = {3, 5, 8, 12};
        int[] hitpoints = {1, 2, 3, 7, 15};
        for (int maxHit : maxHits) {
            for (int hp : hitpoints) {
                double expected = bruteForceCombinedOverkill(maxHit, hp);
                double actual = TwinflameSecondHit.combinedExpectedOverkill(maxHit, hp);
                assertEquals("maxHit=" + maxHit + " hp=" + hp, expected, actual, DELTA);
            }
        }
    }

    @Test
    public void combinedExpectedOverkill_zeroMaxHitOrZeroHpIsZero() {
        assertEquals(0.0, TwinflameSecondHit.combinedExpectedOverkill(0, 10), DELTA);
        assertEquals(0.0, TwinflameSecondHit.combinedExpectedOverkill(10, 0), DELTA);
    }

    @Test
    public void cappedCombinedExpectedOverkill_capAtOrAboveMaxHitDelegatesToUncapped() {
        int maxHit = 20;
        int hp = 50;
        assertEquals(TwinflameSecondHit.combinedExpectedOverkill(maxHit, hp),
                TwinflameSecondHit.cappedCombinedExpectedOverkill(maxHit, maxHit, hp), DELTA);
    }

    @Test
    public void cappedCombinedExpectedOverkill_isNoGreaterThanUncapped() {
        // Capping each hitsplat can only shrink or leave unchanged the damage dealt,
        // so overkill (damage wasted past the kill) can only shrink or stay the same too.
        int maxHit = 30;
        int hp = 40;
        double uncapped = TwinflameSecondHit.combinedExpectedOverkill(maxHit, hp);
        double capped = TwinflameSecondHit.cappedCombinedExpectedOverkill(maxHit, 10, hp);
        assertTrue(capped <= uncapped);
    }
}
