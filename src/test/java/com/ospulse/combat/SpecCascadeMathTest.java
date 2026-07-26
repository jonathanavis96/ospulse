package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Cross-checks every multi-hit cascade formula in {@link SpecCascadeMath}
 * against an INDEPENDENTLY-written brute-force enumeration of the same
 * described mechanic (never by re-calling the production helper methods) —
 * the design spec §8 requirement to catch a transcription bug (off-by-one in
 * a chain, a wrong branch weight, a swapped range) that a same-formula
 * "cross-check" could never catch. See {@link SpecCascadeMath}'s class
 * javadoc for the separate, orthogonal caveat this does NOT resolve: whether
 * the MECHANIC itself (sourced via an AI-mediated wiki fetch, direct access
 * being Cloudflare-blocked from this environment) matches the real game.
 */
public class SpecCascadeMathTest {
    private static final double EPSILON = 1e-9;

    // ---- Dragon claws -----------------------------------------------------------------

    @Test
    public void dragonClawsMatchesIndependentBruteForceEnumeration() {
        for (double p : new double[]{0.0, 0.1, 0.35, 0.5, 0.75, 0.99, 1.0}) {
            for (int maxHit : new int[]{1, 6, 7, 30, 31, 60}) {
                double expected = bruteForceDragonClaws(p, maxHit);
                double actual = SpecCascadeMath.dragonClawsExpectedDamage(p, maxHit);
                assertEquals("p=" + p + " maxHit=" + maxHit, expected, actual, EPSILON);
            }
        }
    }

    @Test
    public void dragonClawsZeroMaxHitIsZero() {
        assertEquals(0.0, SpecCascadeMath.dragonClawsExpectedDamage(0.5, 0), EPSILON);
    }

    @Test
    public void dragonClawsAtCertainAccuracyEqualsBranch1Only() {
        // p == 1: every attack sequence lands on hit 1, so only branch 1 (the
        // ordinary-roll chain) ever contributes.
        double expected = bruteForceDragonClaws(1.0, 40);
        double branch1Only = bruteForceBranch1(40);
        assertEquals(expected, branch1Only, EPSILON);
    }

    @Test
    public void dragonClawsAtZeroAccuracyEqualsAllMissConsolationOnly() {
        // p == 0: every accuracy roll fails, so only the flat consolation applies.
        double actual = SpecCascadeMath.dragonClawsExpectedDamage(0.0, 40);
        assertEquals(4.0 / 3.0, actual, EPSILON);
    }

    /** Independent reference implementation — deliberately not sharing any code with {@link SpecCascadeMath}. */
    private static double bruteForceDragonClaws(double p, int maxHit) {
        double e1 = bruteForceBranch1(maxHit);
        double e2 = bruteForceRangeChain(scaled(maxHit, 3, 8), scaled(maxHit, 7, 8), 2);
        double e3 = bruteForceRangeChain(scaled(maxHit, 1, 4), scaled(maxHit, 3, 4), 1);
        double e4 = bruteForceRangeChain(scaled(maxHit, 1, 4), scaled(maxHit, 5, 4), 0);
        double allMiss = 4.0 / 3.0;
        return p * e1
                + p * (1 - p) * e2
                + p * (1 - p) * (1 - p) * e3
                + p * (1 - p) * (1 - p) * (1 - p) * e4
                + (1 - p) * (1 - p) * (1 - p) * (1 - p) * allMiss;
    }

    private static double bruteForceBranch1(int maxHit) {
        if (maxHit <= 0) {
            return 0.0;
        }
        double denom = maxHit + 1.0;
        double sum = 0.0;
        for (int hit1 = 1; hit1 <= maxHit; hit1++) {
            double weight = (hit1 == 1 ? 2.0 : 1.0) / denom;
            int hit2 = hit1 / 2;
            int hit3 = hit2 / 2;
            int hit4 = hit3 / 2 + 1;
            sum += weight * (hit1 + hit2 + hit3 + hit4);
        }
        return sum;
    }

    private static double bruteForceRangeChain(int lo, int hi, int remainingHits) {
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        int n = hiClamped - loClamped + 1;
        double sum = 0.0;
        for (int landing = loClamped; landing <= hiClamped; landing++) {
            int total = landing;
            int prev = landing;
            for (int i = 0; i < remainingHits; i++) {
                int next = i == remainingHits - 1 ? prev / 2 + 1 : prev / 2;
                total += next;
                prev = next;
            }
            sum += total;
        }
        return sum / n;
    }

    // ---- Burning claws -----------------------------------------------------------------

    @Test
    public void burningClawsMatchesIndependentBruteForceEnumeration() {
        for (double p : new double[]{0.0, 0.2, 0.5, 0.8, 1.0}) {
            for (int maxHit : new int[]{1, 8, 30, 60}) {
                double expected = bruteForceBurningClaws(p, maxHit);
                double actual = SpecCascadeMath.burningClawsExpectedDamage(p, maxHit);
                assertEquals("p=" + p + " maxHit=" + maxHit, expected, actual, EPSILON);
            }
        }
    }

    @Test
    public void burningClawsZeroMaxHitIsZero() {
        assertEquals(0.0, SpecCascadeMath.burningClawsExpectedDamage(0.4, 0), EPSILON);
    }

    private static double bruteForceBurningClaws(double p, int maxHit) {
        double e1 = rangeMean(scaled(maxHit, 3, 4), scaled(maxHit, 7, 4));
        double e2 = rangeMean(scaled(maxHit, 1, 2), scaled(maxHit, 3, 2));
        double e3 = rangeMean(scaled(maxHit, 1, 4), scaled(maxHit, 5, 4));
        double allMiss = 1.2;
        return p * e1
                + (1 - p) * p * e2
                + (1 - p) * (1 - p) * p * e3
                + (1 - p) * (1 - p) * (1 - p) * allMiss;
    }

    private static double rangeMean(int lo, int hi) {
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        return (loClamped + hiClamped) / 2.0;
    }

    private static int scaled(int maxHit, int numerator, int denominator) {
        return (int) Math.round(maxHit * (double) numerator / denominator);
    }

    // ---- Simple modifier-only hits (accuracy/damage % bumps) ----------------------------

    @Test
    public void dragonDaggerIsTwiceOneBoostedHit() {
        double p = 0.6;
        int maxHit = 40;
        double boostedChance = Math.min(1.0, p * 1.15);
        int boostedMax = (int) Math.floor(maxHit * 1.15);
        double expected = 2.0 * DamageDistribution.averageDamagePerAttack(boostedChance, boostedMax);
        assertEquals(expected, SpecCascadeMath.dragonDaggerExpectedDamage(p, maxHit), EPSILON);
    }

    @Test
    public void boostedSingleHitMatchesDamageDistributionDirectly() {
        double p = 0.55;
        int maxHit = 30;
        double expected = DamageDistribution.averageDamagePerAttack(Math.min(1.0, p * 2.0), (int) Math.floor(maxHit * 1.5));
        assertEquals(expected, SpecCascadeMath.boostedSingleHit(p, maxHit, 2.0, 1.5), EPSILON);
    }

    @Test
    public void boostedSingleHitAccuracyNeverExceedsOne() {
        // A x2 accuracy multiplier at a high base hit chance must clamp, not exceed 1.0's dps ceiling.
        double uncapped = DamageDistribution.averageDamagePerAttack(1.0, 40);
        double actual = SpecCascadeMath.boostedSingleHit(0.9, 40, 2.0, 1.0);
        assertEquals(uncapped, actual, EPSILON);
    }

    @Test
    public void voidwakerIsGuaranteedHalfToOneAndAHalfOfMeleeMaxHit() {
        int maxMeleeHit = 40;
        double expected = (20 + 60) / 2.0; // 0.5*40=20, 1.5*40=60
        assertEquals(expected, SpecCascadeMath.voidwakerExpectedDamage(maxMeleeHit), EPSILON);
    }

    @Test
    public void voidwakerScalesLinearlyWithMeleeMaxHit() {
        double small = SpecCascadeMath.voidwakerExpectedDamage(20);
        double large = SpecCascadeMath.voidwakerExpectedDamage(40);
        assertTrue(large > small);
    }
}
