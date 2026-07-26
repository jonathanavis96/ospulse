package com.ospulse.combat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The mathematical fact {@code REROLL} relies on: re-rolling a hit that lands
 * above a cap {@code C} uniformly back into {@code 0..C} produces a
 * distribution that is <b>exactly uniform over {@code 0..C}</b> — independent
 * of the uncapped max hit {@code M} the original roll came from.
 *
 * <p>Proof sketch (also stated in {@code MonsterCombatRequirement.CapMode}):
 * a raw roll is uniform over {@code 0..M}, each outcome with probability
 * {@code 1/(M+1)}. An outcome {@code r <= C} is kept as-is; an outcome
 * {@code r > C} (there are {@code M-C} of them) is re-rolled uniformly over
 * {@code 0..C}, so each of the {@code C+1} rerolled values gets an equal
 * share {@code 1/(C+1)} of that re-rolled mass. So for every {@code d} in
 * {@code 0..C}:
 * <pre>
 * P(d) = 1/(M+1)                                  [kept outcome d itself]
 *      + (M-C)/(M+1) * 1/(C+1)                    [share of the re-rolled mass]
 * </pre>
 * which does not depend on {@code d} at all — so the distribution is
 * uniform over {@code 0..C}.
 *
 * <p><b>This flatness is about the SHAPE only, and does NOT license
 * implementing {@code CapMode#REROLL} as {@code maxHit = min(maxHit, cap)} fed
 * through the ordinary formulas.</b> Those carry OSRS's "a rolled 0 becomes 1"
 * correction, which belongs to the damage roll and therefore applies BEFORE the
 * monster re-rolls — surviving only on values that were never re-rolled, while a
 * re-rolled 0 stays a genuine 0. The real mean is
 * {@code C/2 + 1/(M+1)}, not {@code C/2 + 1/(C+1)}; see
 * {@link DamageDistribution#rerolledAverageDamagePerAttack} /
 * {@link DamageDistribution#rerolledExpectedOverkill}, which are what
 * {@code DpsCalculator} actually calls. The shortcut shipped once on the
 * strength of the result below and was caught in review.
 *
 * <p>This test does NOT hand-encode that closed form and check it is
 * self-consistent (that would prove nothing about the mechanic, only that
 * arithmetic is arithmetic). It instead builds the rerolled distribution from
 * first principles — enumerate every one of the {@code M+1} equally-likely
 * raw outcomes, redistribute each one that exceeds the cap uniformly across
 * {@code 0..C} — and checks the result is flat.
 */
public class CombatMathRerollEquivalenceTest {

    /**
     * Builds P(d) for d in {@code 0..cap} from first principles: each of the
     * {@code uncappedMax + 1} raw outcomes is equally likely; an outcome
     * {@code <= cap} contributes its full weight to itself, an outcome
     * {@code > cap} contributes an equal 1/(cap+1) share of its weight to
     * EVERY value in {@code 0..cap} (a fresh uniform re-roll).
     */
    private static double[] rerolledDistribution(int uncappedMax, int cap) {
        double[] p = new double[cap + 1];
        double rawWeight = 1.0 / (uncappedMax + 1);
        for (int r = 0; r <= uncappedMax; r++) {
            if (r <= cap) {
                p[r] += rawWeight;
            } else {
                double share = rawWeight / (cap + 1);
                for (int d = 0; d <= cap; d++) {
                    p[d] += share;
                }
            }
        }
        return p;
    }

    /** The distribution built above must be a real probability distribution: it sums to 1. */
    @Test
    public void rerolledDistributionSumsToOne() {
        for (int[] pair : new int[][]{{40, 4}, {40, 10}, {100, 3}, {20, 15}, {7, 7}, {10, 10}}) {
            double[] p = rerolledDistribution(pair[0], pair[1]);
            double sum = 0.0;
            for (double v : p) {
                sum += v;
            }
            assertEquals("distribution for M=" + pair[0] + " cap=" + pair[1] + " must sum to 1", 1.0, sum, 1e-9);
        }
    }

    /**
     * The core claim: every value in {@code 0..cap} is equally likely after a
     * re-roll, for every (M, cap) pair with cap &lt; M (a binding cap) — not
     * just the boundary values.
     */
    @Test
    public void rerolledDistributionIsExactlyUniform_forSeveralMaxCapPairs() {
        int[][] pairs = {{40, 4}, {40, 10}, {100, 3}, {20, 15}, {11, 1}, {200, 50}};
        for (int[] pair : pairs) {
            int uncappedMax = pair[0];
            int cap = pair[1];
            double[] p = rerolledDistribution(uncappedMax, cap);
            double expected = p[0];
            for (int d = 1; d <= cap; d++) {
                assertEquals("P(d) must be constant across d for M=" + uncappedMax + " cap=" + cap
                        + " (d=" + d + " diverged from d=0)", expected, p[d], 1e-12);
            }
            // And it must match the plain "1/(cap+1)" a genuinely uniform 0..cap roll would give.
            assertEquals("uniform value must equal a plain 1/(cap+1) roll", 1.0 / (cap + 1), expected, 1e-12);
        }
    }

    /** With cap == M there is nothing to re-roll: the distribution is trivially the original uniform 0..M roll. */
    @Test
    public void noBindingCap_isStillUniform() {
        double[] p = rerolledDistribution(10, 10);
        for (double v : p) {
            assertEquals(1.0 / 11.0, v, 1e-12);
        }
    }

    /**
     * Consequence: since the rerolled distribution is uniform 0..cap, its
     * expectation is the ordinary uniform mean {@code cap/2} — precisely the
     * value {@link DamageDistribution#averageDamagePerAttack} uses (up to that
     * method's own separate "rolled 0 becomes 1" correction, which applies
     * identically whether the roll came from a genuine 0..cap weapon or a
     * re-roll into 0..cap — the point this test proves is that the RAW
     * distributions are identical, so any downstream formula built on top of
     * a raw uniform 0..cap roll gets the same answer either way).
     */
    @Test
    public void rerolledMeanMatchesThePlainUniformMean() {
        for (int[] pair : new int[][]{{40, 4}, {40, 10}, {100, 3}, {20, 15}}) {
            int uncappedMax = pair[0];
            int cap = pair[1];
            double[] p = rerolledDistribution(uncappedMax, cap);
            double mean = 0.0;
            for (int d = 0; d <= cap; d++) {
                mean += d * p[d];
            }
            assertEquals("rerolled mean must equal the plain uniform 0..cap mean", cap / 2.0, mean, 1e-9);
        }
    }
}
