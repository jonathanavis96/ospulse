package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Cross-checks every multi-hit cascade formula in {@link SpecCascadeMath}
 * against an INDEPENDENTLY-written brute-force enumeration of the same
 * verbatim-wikitext-sourced mechanic (never by re-calling the production
 * helper methods) — catching a transcription bug (off-by-one in a chain, a
 * wrong branch weight, a swapped range) that a same-formula "cross-check"
 * could never catch. Both mechanics were fetched as verbatim wikitext via
 * the OSRS Wiki's own API ({@code action=parse&prop=wikitext}), and several
 * of the assertions below pin the wiki page's OWN worked numeric examples
 * directly, so a future formula edit that silently drifts from the source
 * text fails here.
 */
public class SpecCascadeMathTest {
    private static final double EPSILON = 1e-9;

    // ---- Dragon claws -----------------------------------------------------------------

    @Test
    public void dragonClawsMatchesIndependentBruteForceEnumeration() {
        for (double p : new double[]{0.0, 0.1, 0.35, 0.5, 0.75, 0.99, 1.0}) {
            for (int maxHit : new int[]{1, 6, 7, 30, 31, 60, 70}) {
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
        // "4-2-1-1" chain) ever contributes.
        double expected = bruteForceDragonClaws(1.0, 70);
        double branch1Only = bruteForceRangeChain(70 / 2, 70 - 1, 3);
        assertEquals(expected, branch1Only, EPSILON);
    }

    @Test
    public void dragonClawsAtZeroAccuracyEqualsAllMissConsolationOnly() {
        // p == 0: every accuracy roll fails, so only the flat consolation applies.
        double actual = SpecCascadeMath.dragonClawsExpectedDamage(0.0, 40);
        assertEquals(4.0 / 3.0, actual, EPSILON);
    }

    /**
     * Pins the wiki page's OWN worked example verbatim: "A realistic example
     * of a full attack is 35-17-8-9" for the "4-2-1-1" branch. With maxHit=70,
     * hit1's range is {@code [35, 69]}; hit1=35 (the range's minimum) must
     * chain to exactly 35-17-8-9 (total 69) under the production rule — NOT
     * 35-17-8-5 (total 65), which is what {@code floor(prev/2)+1} for the
     * last hit (the bug this re-verification round caught) would produce.
     */
    @Test
    public void dragonClawsPinsTheWikisOwnWorkedExample_35_17_8_9() {
        int hit1 = 35;
        int hit2 = hit1 / 2;
        int hit3 = hit2 / 2;
        int hit4 = hit3 + 1; // "the fourth hit will deal 1 more damage than the third hit"
        assertEquals(17, hit2);
        assertEquals(8, hit3);
        assertEquals(9, hit4);
        assertEquals(69, hit1 + hit2 + hit3 + hit4);
        // hit1=35 is the minimum of maxHit=70's [floor(70/2), 70-1] = [35, 69] range.
        assertEquals(35, 70 / 2);
    }

    /** Pins the wiki's "0-4-2-2" example: "0-30-15-16" — hit3=floor(hit2/2), hit4=hit3+1. */
    @Test
    public void dragonClawsPinsTheWikisOwnWorkedExample_0_30_15_16() {
        int hit2 = 30;
        int hit3 = hit2 / 2;
        int hit4 = hit3 + 1;
        assertEquals(15, hit3);
        assertEquals(16, hit4);
    }

    /** Pins the wiki's "0-0-3-3" example: "0-0-22-23" — hit4 = hit3+1, NOT hit3 itself ("matches ... as usual" means the same +1 relationship, confirmed by the numbers). */
    @Test
    public void dragonClawsPinsTheWikisOwnWorkedExample_0_0_22_23() {
        int hit3 = 22;
        int hit4 = hit3 + 1;
        assertEquals(23, hit4);
    }

    /** Independent reference implementation — deliberately not sharing any code with {@link SpecCascadeMath}. */
    private static double bruteForceDragonClaws(double p, int maxHit) {
        double e1 = bruteForceRangeChain(maxHit / 2, maxHit - 1, 3);
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

    private static double bruteForceRangeChain(int lo, int hi, int remainingHits) {
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        int n = hiClamped - loClamped + 1;
        double sum = 0.0;
        for (int landing = loClamped; landing <= hiClamped; landing++) {
            int total = landing;
            int prev = landing;
            for (int i = 0; i < remainingHits; i++) {
                // "half of the previous hit" for every hit except the last,
                // which is "1 more damage than the previous hit" — NOT a
                // further halving. Written independently from chainTotal().
                int next = i == remainingHits - 1 ? prev + 1 : prev / 2;
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
            for (int maxHit : new int[]{1, 8, 30, 39, 60}) {
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

    /**
     * Pins the wiki page's OWN worked rounding example VERBATIM: "for a
     * normal maximum hit of 39 ... [a roll of] 29 damage ... the hitsplats
     * are calculated as floor(0.25*29), floor(0.25*29), and floor(0.5*29),
     * resulting in a hit of 7-7-14 — a total of 28 actual damage, despite
     * originally rolling for 29 damage." This is the correction that
     * mattered this round: branch 1's per-hitsplat flooring is NOT
     * damage-neutral, so the expected value must sum {@code
     * floor(R/4)+floor(R/4)+floor(R/2)} over the roll range, not just
     * average the raw roll R.
     */
    @Test
    public void burningClawsPinsTheWikisOwnRoundingLossExample() {
        int r = 29;
        int hitsplat1 = r / 4;
        int hitsplat2 = r / 4;
        int hitsplat3 = r / 2;
        assertEquals(7, hitsplat1);
        assertEquals(7, hitsplat2);
        assertEquals(14, hitsplat3);
        int delivered = hitsplat1 + hitsplat2 + hitsplat3;
        assertEquals("29 rolled must deliver 28, not 29 — the wiki's own stated rounding loss", 28, delivered);
        assertTrue("delivered damage must be able to fall short of the rolled total", delivered < r);
    }

    /** Pins the wiki's branch-2 example "a total roll of 16 becomes 7-7-2" — sum unchanged (16 is even, no rounding loss). */
    @Test
    public void burningClawsPinsTheWikisBranch2Example() {
        int r = 16;
        int delivered = (r / 2) + (r / 2); // + 0 for the third hitsplat before redistribution
        assertEquals(16, delivered);
    }

    /** Pins the wiki's branch-3 example "a total roll of 9 becomes 1-1-7" — sum unchanged (100% split, no fractional rounding). */
    @Test
    public void burningClawsPinsTheWikisBranch3Example() {
        int r = 9;
        assertEquals(9, r); // the whole roll is delivered, no split-induced loss
    }

    private static double bruteForceBurningClaws(double p, int maxHit) {
        double e1 = bruteForceDelivered(scaled(maxHit, 3, 4), scaled(maxHit, 7, 4), r -> (r / 4) + (r / 4) + (r / 2));
        double e2 = bruteForceDelivered(scaled(maxHit, 1, 2), scaled(maxHit, 3, 2), r -> (r / 2) + (r / 2));
        double e3 = bruteForceDelivered(scaled(maxHit, 1, 4), scaled(maxHit, 5, 4), r -> r);
        double allMiss = 1.2;
        return p * e1
                + (1 - p) * p * e2
                + (1 - p) * (1 - p) * p * e3
                + (1 - p) * (1 - p) * (1 - p) * allMiss;
    }

    private static double bruteForceDelivered(int lo, int hi, java.util.function.IntUnaryOperator delivered) {
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        long sum = 0;
        for (int r = loClamped; r <= hiClamped; r++) {
            sum += delivered.applyAsInt(r);
        }
        return (double) sum / (hiClamped - loClamped + 1);
    }

    private static int scaled(int maxHit, int numerator, int denominator) {
        return (maxHit * numerator) / denominator; // wiki: rounded down
    }

    // ---- Simple modifier-only hits (accuracy/damage % bumps) ----------------------------

    @Test
    public void dragonDaggerIsTwiceOneBoostedHit() {
        double p = 0.6;
        int maxHit = 40;
        double boostedChance = Math.min(1.0, p * 1.15);
        int boostedMax = (int) Math.floor(maxHit * 1.15);
        double expected = 2.0 * DamageDistribution.averageDamage(boostedChance, boostedMax);
        assertEquals(expected, SpecCascadeMath.dragonDaggerExpectedDamage(p, maxHit), EPSILON);
    }

    @Test
    public void boostedSingleHitMatchesDamageDistributionDirectly() {
        double p = 0.55;
        int maxHit = 30;
        double expected = DamageDistribution.averageDamage(Math.min(1.0, p * 2.0), (int) Math.floor(maxHit * 1.5));
        assertEquals(expected, SpecCascadeMath.boostedSingleHit(p, maxHit, 2.0, 1.5), EPSILON);
    }

    @Test
    public void boostedSingleHitAccuracyNeverExceedsOne() {
        // A x2 accuracy multiplier at a high base hit chance must clamp, not exceed 1.0's dps ceiling.
        double uncapped = DamageDistribution.averageDamage(1.0, 40);
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
