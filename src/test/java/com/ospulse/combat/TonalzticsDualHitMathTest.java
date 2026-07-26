package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross-checks {@link TonalzticsDualHit}'s average/overkill formulas against
 * an independent brute-force enumeration built directly from the mechanic
 * itself (two fully independent hit/miss + raw-damage draws), NOT from
 * {@link TonalzticsDualHit}'s own convolution code — the same discipline
 * {@code TwinflameSecondHitMathTest} uses for the second-hit mechanic, per
 * the standing rule that a formula checked against itself proves nothing
 * (this is how the twinflame cap defect was originally found).
 */
public class TonalzticsDualHitMathTest {
    private static final double DELTA = 1e-9;

    // ---- Uncapped: brute force over every raw (miss-or-1..maxHit) x (miss-or-1..maxHit) pair ----

    /**
     * Enumerates BOTH hits' outcomes independently and explicitly: each hit
     * is a miss with probability {@code (1 - hitChance)}, or lands with
     * probability {@code hitChance}, uniformly distributed over the
     * {@code maxHit + 1} raw draws {@code 0..maxHit} (a raw 0 bumped to a
     * landed 1) — i.e. {@code maxHit + 2} distinct weighted outcomes per
     * hit, {@code (maxHit + 2)^2} combined outcomes total. This mirrors
     * neither {@link TonalzticsDualHit#combinedAverageDamagePerAttack} (which
     * doubles the single-hit average) nor its convolution-based overkill
     * helper — it is a completely independent enumeration of the raw
     * mechanic.
     */
    private static double bruteForceCombinedAverage(double hitChance, int maxHit) {
        double sum = 0.0;
        for (int i = 0; i < 2; i++) {
            // Each hit's own expected contribution, summed (this loop is just
            // "do this twice" — the two hits are identical and independent).
            double perHit = 0.0;
            for (int raw = 0; raw <= maxHit; raw++) {
                int landed = raw == 0 ? 1 : raw;
                perHit += landed;
            }
            perHit = hitChance * (perHit / (maxHit + 1.0));
            sum += perHit;
        }
        return sum;
    }

    private static double bruteForceCombinedOverkill(double hitChance, int maxHit, int targetHitpoints) {
        double[] over = new double[targetHitpoints + 1];
        double missProb = 1.0 - hitChance;
        double perRaw = hitChance / (maxHit + 1.0);
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            // hit1: MISS, or raw1 in 0..maxHit (bumped 0->1)
            for (int raw1 = -1; raw1 <= maxHit; raw1++) {
                double p1 = raw1 == -1 ? missProb : perRaw;
                int d1 = raw1 == -1 ? 0 : (raw1 == 0 ? 1 : raw1);
                for (int raw2 = -1; raw2 <= maxHit; raw2++) {
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

    @Test
    public void combinedAverage_matchesBruteForceEnumeration_acrossVariousMaxHitsAndHitChances() {
        int[] maxHits = {1, 4, 5, 8, 12, 23, 44};
        double[] hitChances = {1.0, 0.85, 0.53, 0.2};
        for (int maxHit : maxHits) {
            for (double hitChance : hitChances) {
                double expected = bruteForceCombinedAverage(hitChance, maxHit);
                double actual = TonalzticsDualHit.combinedAverageDamagePerAttack(hitChance, maxHit);
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
    public void combinedAverage_isExactlyDoubleTheSingleHitAverage() {
        // Sanity for the linearity-of-expectation argument documented on the class.
        double hitChance = 0.73;
        int maxHit = 37;
        double single = DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
        double combined = TonalzticsDualHit.combinedAverageDamagePerAttack(hitChance, maxHit);
        assertEquals(2.0 * single, combined, DELTA);
    }

    @Test
    public void combinedOverkill_zeroMaxHitOrZeroHpIsZero() {
        assertEquals(0.0, TonalzticsDualHit.combinedExpectedOverkill(0.8, 0, 10), DELTA);
        assertEquals(0.0, TonalzticsDualHit.combinedExpectedOverkill(0.8, 10, 0), DELTA);
    }

    // ---- Capped (CLAMP) / re-rolled: collapse identities + a hand-worked example ----

    @Test
    public void cappedCombinedAverage_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        int maxHit = 20;
        double expected = TonalzticsDualHit.combinedAverageDamagePerAttack(hitChance, maxHit);
        assertEquals(expected, TonalzticsDualHit.cappedCombinedAverageDamagePerAttack(hitChance, maxHit, 20), DELTA);
        assertEquals(expected, TonalzticsDualHit.cappedCombinedAverageDamagePerAttack(hitChance, maxHit, 99), DELTA);
    }

    @Test
    public void rerolledCombinedAverage_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        int maxHit = 20;
        double expected = TonalzticsDualHit.combinedAverageDamagePerAttack(hitChance, maxHit);
        assertEquals(expected, TonalzticsDualHit.rerolledCombinedAverageDamagePerAttack(hitChance, maxHit, 20), DELTA);
    }

    @Test
    public void cappedCombinedOverkill_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        int maxHit = 12;
        double expected = TonalzticsDualHit.combinedExpectedOverkill(hitChance, maxHit, 15);
        assertEquals(expected, TonalzticsDualHit.cappedCombinedExpectedOverkill(hitChance, maxHit, 12, 15), DELTA);
    }

    @Test
    public void rerolledCombinedOverkill_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        int maxHit = 12;
        double expected = TonalzticsDualHit.combinedExpectedOverkill(hitChance, maxHit, 15);
        assertEquals(expected, TonalzticsDualHit.rerolledCombinedExpectedOverkill(hitChance, maxHit, 12, 15), DELTA);
    }

    /**
     * A hand-worked small example for the CLAMP average, cross-checked
     * against a direct enumeration of the two-hit raw space (independent of
     * both {@link TonalzticsDualHit} and {@link DamageDistribution}):
     * hitChance=1.0 (every hit lands), maxHit=10, cap=4. Each single hit's
     * clamped value is {@code min(bumpedRaw, 4)}; raws 0 and 1 both bump to
     * 1, raw 2 stays 2, raw 3 stays 3, and raws 4..10 (7 of the 11 raws) all
     * clamp to 4. Expected single-hit clamped damage =
     * {@code (1+1+2+3 + 7*4)/11 = 35/11}. Combined (doubled) = 70/11.
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
        assertEquals(expectedCombined, TonalzticsDualHit.cappedCombinedAverageDamagePerAttack(1.0, 10, 4), DELTA);
    }
}
