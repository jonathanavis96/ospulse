package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross-checks {@link TonalzticsDualHit}'s average/overkill formulas against
 * an independent brute-force enumeration built directly from the mechanic
 * itself (two fully independent hit/miss + raw-damage draws over the
 * REDUCED 75%-of-weapon-max-hit range — see {@link TonalzticsDualHit}'s
 * class javadoc for the citation), NOT from {@link TonalzticsDualHit}'s own
 * convolution code — the same discipline {@code TwinflameSecondHitMathTest}
 * uses for the second-hit mechanic, per the standing rule that a formula
 * checked against itself proves nothing (this is how the twinflame cap
 * defect was originally found, and also how this class's own 75%-vs-100%
 * defect was caught in review).
 */
public class TonalzticsDualHitMathTest {
    private static final double DELTA = 1e-9;

    /**
     * Independently restates the 75% reduction (NOT by calling {@link
     * TonalzticsDualHit#perHitMaxHit}) so the brute force below cannot
     * silently share a bug with the production rounding.
     */
    private static int bruteForcePerHitMax(int weaponMaxHit) {
        return (weaponMaxHit * 3) / 4; // exact integer division, floors for non-negative operands
    }

    // ---- Uncapped: brute force over every raw (miss-or-1..perHitMax) x (miss-or-1..perHitMax) pair ----

    /**
     * Enumerates BOTH hits' outcomes independently and explicitly over the
     * REDUCED {@code 0..perHitMax} range: each hit is a miss with
     * probability {@code (1 - hitChance)}, or lands with probability
     * {@code hitChance}, uniformly distributed over the {@code perHitMax + 1}
     * raw draws (a raw 0 bumped to a landed 1).
     */
    private static double bruteForceCombinedAverage(double hitChance, int weaponMaxHit) {
        int perHitMax = bruteForcePerHitMax(weaponMaxHit);
        double sum = 0.0;
        for (int i = 0; i < 2; i++) {
            // Each hit's own expected contribution, summed (this loop is just
            // "do this twice" — the two hits are identical and independent).
            double perHit = 0.0;
            for (int raw = 0; raw <= perHitMax; raw++) {
                int landed = raw == 0 ? 1 : raw;
                perHit += landed;
            }
            perHit = hitChance * (perHit / (perHitMax + 1.0));
            sum += perHit;
        }
        return sum;
    }

    private static double bruteForceCombinedOverkill(double hitChance, int weaponMaxHit, int targetHitpoints) {
        int perHitMax = bruteForcePerHitMax(weaponMaxHit);
        double[] over = new double[targetHitpoints + 1];
        double missProb = 1.0 - hitChance;
        double perRaw = hitChance / (perHitMax + 1.0);
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            // hit1: MISS, or raw1 in 0..perHitMax (bumped 0->1)
            for (int raw1 = -1; raw1 <= perHitMax; raw1++) {
                double p1 = raw1 == -1 ? missProb : perRaw;
                int d1 = raw1 == -1 ? 0 : (raw1 == 0 ? 1 : raw1);
                for (int raw2 = -1; raw2 <= perHitMax; raw2++) {
                    double p2 = raw2 == -1 ? missProb : perRaw;
                    int d2 = raw2 == -1 ? 0 : (raw2 == 0 ? 1 : raw2);
                    int combined = d1 + d2;
                    double p = p1 * p2;
                    if (combined == 0) {
                        continue; // a fully-missed cycle changes nothing, like an ordinary miss
                    }
                    sum += p * (combined >= h ? (combined - h) : over[h - combined]);
                }
            }
            // Renormalise: the loop above summed p1*p2 over every NON-both-miss
            // outcome; the excluded both-miss mass must not appear in the
            // denominator (mirrors DamageDistribution.overkillFromExplicitDistribution's
            // "retain = 1 - p[0]" conditioning).
            double bothMiss = missProb * missProb;
            over[h] = sum / (1.0 - bothMiss);
        }
        return over[targetHitpoints];
    }

    // ---- The 75% figure itself, pinned so neither 100% nor 50% can be reintroduced ----

    /**
     * The exact source: OSRS Wiki "Multi-hit weapons" comparison table
     * (2026-07-27) — "Tonalztics of ralos ... Each hit deals 0-75% of total
     * weapon damage." Hard-coded here independently of {@link
     * TonalzticsDualHit#perHitMaxHit}'s own arithmetic, so a future change
     * to either 100% (an earlier defect in this codebase) or 50% (a review
     * finding that was itself wrong, based on misreading "two independent
     * damage rolls" as implying an even split) fails this test immediately.
     */
    @Test
    public void perHitMaxHit_isSeventyFivePercentOfWeaponMaxHit_flooredExactly() {
        assertEquals(75, TonalzticsDualHit.perHitMaxHit(100));
        assertEquals(30, TonalzticsDualHit.perHitMaxHit(40)); // 40*0.75 = 30 exactly
        assertEquals(29, TonalzticsDualHit.perHitMaxHit(39)); // 39*0.75 = 29.25 -> floors to 29, NOT 50% (19/20) or 100% (39)
        assertEquals(0, TonalzticsDualHit.perHitMaxHit(0));
        assertEquals(0, TonalzticsDualHit.perHitMaxHit(1)); // 1*0.75 = 0.75 -> floors to 0
        assertEquals(1, TonalzticsDualHit.perHitMaxHit(2)); // 2*0.75 = 1.5 -> floors to 1
    }

    @Test
    public void combinedAverage_matchesBruteForceEnumeration_acrossVariousMaxHitsAndHitChances() {
        int[] maxHits = {1, 4, 5, 8, 12, 23, 44};
        double[] hitChances = {1.0, 0.85, 0.53, 0.2};
        for (int maxHit : maxHits) {
            for (double hitChance : hitChances) {
                double expected = bruteForceCombinedAverage(hitChance, maxHit);
                double actual = TonalzticsDualHit.combinedAverageDamage(hitChance, maxHit);
                assertEquals("maxHit=" + maxHit + " hitChance=" + hitChance, expected, actual, DELTA);
            }
        }
    }

    @Test
    public void combinedOverkill_matchesBruteForceEnumeration_acrossVariousMaxHitsAndHitChances() {
        int[] maxHits = {3, 5, 8, 12};
        int[] hitpoints = {1, 2, 3, 7, 15, 40};
        double[] hitChances = {1.0, 0.8, 0.4};
        for (int maxHit : maxHits) {
            for (double hitChance : hitChances) {
                for (int hp : hitpoints) {
                    double expected = bruteForceCombinedOverkill(hitChance, maxHit, hp);
                    double actual = TonalzticsDualHit.combinedExpectedOverkill(hitChance, maxHit, hp);
                    assertEquals("maxHit=" + maxHit + " hitChance=" + hitChance + " hp=" + hp,
                            expected, actual, DELTA);
                }
            }
        }
    }

    @Test
    public void combinedAverage_isExactlyDoubleTheSingleHitAverageOverTheReducedRange() {
        // Sanity for the linearity-of-expectation argument documented on the class.
        double hitChance = 0.73;
        int weaponMaxHit = 37;
        int perHitMax = (weaponMaxHit * 3) / 4;
        double single = DamageDistribution.averageDamage(hitChance, perHitMax);
        double combined = TonalzticsDualHit.combinedAverageDamage(hitChance, weaponMaxHit);
        assertEquals(2.0 * single, combined, DELTA);
    }

    @Test
    public void combinedAverage_isNotTheOldFullRangeModel_andNotAFiftyPercentSplit() {
        // Guards the exact regression this stage exists to fix: neither the
        // pre-fix (100% per hit) nor the review's wrong guess (50% per hit)
        // may silently come back.
        double hitChance = 1.0;
        int weaponMaxHit = 40;
        double correct = TonalzticsDualHit.combinedAverageDamage(hitChance, weaponMaxHit);
        double oldFullRangeModel = 2.0 * DamageDistribution.averageDamage(hitChance, weaponMaxHit);
        double fiftyPercentModel = 2.0 * DamageDistribution.averageDamage(hitChance, weaponMaxHit / 2);
        assertEquals(2.0 * DamageDistribution.averageDamage(hitChance, 30), correct, DELTA); // 40*0.75=30
        org.junit.Assert.assertTrue(Math.abs(correct - oldFullRangeModel) > 1.0);
        org.junit.Assert.assertTrue(Math.abs(correct - fiftyPercentModel) > 1.0);
    }

    @Test
    public void combinedOverkill_zeroMaxHitOrZeroHpIsZero() {
        assertEquals(0.0, TonalzticsDualHit.combinedExpectedOverkill(0.8, 0, 10), DELTA);
        assertEquals(0.0, TonalzticsDualHit.combinedExpectedOverkill(0.8, 10, 0), DELTA);
    }

    // ---- Capped (CLAMP) / re-rolled: collapse identities + a hand-worked example ----

    @Test
    public void cappedCombinedAverage_capAtOrAboveThePerHitRangeDelegatesToUncapped() {
        double hitChance = 0.6;
        int weaponMaxHit = 20; // perHitMax = 15
        double expected = TonalzticsDualHit.combinedAverageDamage(hitChance, weaponMaxHit);
        assertEquals(expected, TonalzticsDualHit.cappedCombinedAverageDamage(hitChance, weaponMaxHit, 15), DELTA);
        assertEquals(expected, TonalzticsDualHit.cappedCombinedAverageDamage(hitChance, weaponMaxHit, 99), DELTA);
    }

    /**
     * The exact correctness fix this stage adds on top of the 75% figure
     * itself: a cap that sits BETWEEN the reduced per-hit max and the raw
     * weapon max hit must NOT bind, because the roll never reaches that high
     * in the first place. A model that (incorrectly) compared the cap
     * against the un-reduced weapon max hit would wrongly treat this as
     * capped.
     */
    @Test
    public void cappedCombinedAverage_capBetweenPerHitMaxAndRawWeaponMaxHit_doesNotBind() {
        double hitChance = 0.7;
        int weaponMaxHit = 20; // perHitMax = 15
        int capAboveReducedRangeButBelowRawMax = 18; // 15 < 18 < 20
        double uncapped = TonalzticsDualHit.combinedAverageDamage(hitChance, weaponMaxHit);
        double withCap = TonalzticsDualHit.cappedCombinedAverageDamage(hitChance, weaponMaxHit, capAboveReducedRangeButBelowRawMax);
        assertEquals("a cap above the true (reduced) per-hit range must never bind", uncapped, withCap, DELTA);
    }

    @Test
    public void rerolledCombinedAverage_capAtOrAboveThePerHitRangeDelegatesToUncapped() {
        double hitChance = 0.6;
        int weaponMaxHit = 20; // perHitMax = 15
        double expected = TonalzticsDualHit.combinedAverageDamage(hitChance, weaponMaxHit);
        assertEquals(expected, TonalzticsDualHit.rerolledCombinedAverageDamage(hitChance, weaponMaxHit, 15), DELTA);
    }

    @Test
    public void cappedCombinedOverkill_capAtOrAboveThePerHitRangeDelegatesToUncapped() {
        double hitChance = 0.6;
        int weaponMaxHit = 16; // perHitMax = 12
        double expected = TonalzticsDualHit.combinedExpectedOverkill(hitChance, weaponMaxHit, 15);
        assertEquals(expected, TonalzticsDualHit.cappedCombinedExpectedOverkill(hitChance, weaponMaxHit, 12, 15), DELTA);
    }

    @Test
    public void rerolledCombinedOverkill_capAtOrAboveThePerHitRangeDelegatesToUncapped() {
        double hitChance = 0.6;
        int weaponMaxHit = 16; // perHitMax = 12
        double expected = TonalzticsDualHit.combinedExpectedOverkill(hitChance, weaponMaxHit, 15);
        assertEquals(expected, TonalzticsDualHit.rerolledCombinedExpectedOverkill(hitChance, weaponMaxHit, 12, 15), DELTA);
    }

    /**
     * A hand-worked small example for the CLAMP average, cross-checked
     * against a direct enumeration of the two-hit raw space (independent of
     * both {@link TonalzticsDualHit} and {@link DamageDistribution}):
     * hitChance=1.0 (every hit lands), weaponMaxHit=14 so perHitMax =
     * floor(14*0.75) = 10, cap=4. Each single hit's clamped value is
     * {@code min(bumpedRaw, 4)} over the reduced {@code 0..10} range; raws
     * 0 and 1 both bump to 1, raw 2 stays 2, raw 3 stays 3, and raws 4..10
     * (7 of the 11 raws) all clamp to 4. Expected single-hit clamped damage
     * = {@code (1+1+2+3 + 7*4)/11 = 35/11}. Combined (doubled) = 70/11.
     */
    @Test
    public void cappedCombinedAverage_handWorkedExample() {
        double sum = 0.0;
        for (int raw = 0; raw <= 10; raw++) {
            int landed = raw == 0 ? 1 : raw;
            sum += Math.min(landed, 4);
        }
        double expectedSingle = sum / 11.0;
        double expectedCombined = 2.0 * expectedSingle;
        assertEquals(70.0 / 11.0, expectedCombined, DELTA);
        assertEquals(10, TonalzticsDualHit.perHitMaxHit(14)); // confirms the fixture's own premise
        assertEquals(expectedCombined, TonalzticsDualHit.cappedCombinedAverageDamage(1.0, 14, 4), DELTA);
    }
}
