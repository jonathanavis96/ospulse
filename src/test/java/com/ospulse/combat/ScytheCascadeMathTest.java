package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross-checks {@link ScytheCascade}'s average/overkill formulas against an
 * independent brute-force enumeration built directly from the mechanic
 * itself (every cascade hit rolled independently — its own miss chance at
 * the SAME hit chance, its own {@code 0..maxHit_i} raw draw), NOT from
 * {@link ScytheCascade}'s own convolution/per-hit-distribution code — the
 * same discipline {@code TonalzticsDualHitMathTest}/{@code
 * TwinflameSecondHitMathTest} use, per the standing rule that a formula
 * checked against itself proves nothing.
 */
public class ScytheCascadeMathTest {
    private static final double DELTA = 1e-9;

    /**
     * Recursively enumerates every combination of {@code maxHits.length}
     * independent hits (each a miss with probability {@code 1 - hitChance},
     * else a bumped-uniform {@code 0..maxHits[i]} draw), invoking {@code
     * visitor} with each combination's total probability and total (summed)
     * damage. This is a complete, brute-force enumeration of the cascade's
     * raw outcome space — {@code product(maxHits[i] + 2)} combinations.
     */
    private interface OutcomeVisitor {
        void visit(double probability, int totalDamage);
    }

    private static void enumerate(double hitChance, int[] maxHits, int index, double probSoFar, int damageSoFar,
                                   OutcomeVisitor visitor) {
        if (index == maxHits.length) {
            visitor.visit(probSoFar, damageSoFar);
            return;
        }
        int maxHit = maxHits[index];
        // Miss branch.
        enumerate(hitChance, maxHits, index + 1, probSoFar * (1.0 - hitChance), damageSoFar, visitor);
        // Landed branch: raw 0..maxHit, each equally likely, raw 0 bumped to 1.
        for (int raw = 0; raw <= maxHit; raw++) {
            int landed = raw == 0 ? 1 : raw;
            double p = probSoFar * hitChance / (maxHit + 1.0);
            enumerate(hitChance, maxHits, index + 1, p, damageSoFar + landed, visitor);
        }
    }

    private static double bruteForceAverage(double hitChance, int[] maxHits) {
        double[] sum = {0.0};
        enumerate(hitChance, maxHits, 0, 1.0, 0, (p, d) -> sum[0] += p * d);
        return sum[0];
    }

    private static double bruteForceOverkill(double hitChance, int[] maxHits, int targetHitpoints) {
        // Build the full combined-damage distribution by enumeration, then
        // run the standard "expected overkill" recurrence (conditioning out
        // the zero-damage mass) - independent of ScytheCascade's own
        // convolution/DamageDistribution.overkillFromExplicitDistribution code.
        int maxTotal = 0;
        for (int m : maxHits) {
            maxTotal += m;
        }
        double[] dist = new double[maxTotal + 1];
        enumerate(hitChance, maxHits, 0, 1.0, 0, (p, d) -> dist[d] += p);

        double zeroMass = dist[0];
        double retain = 1.0 - zeroMass;
        if (retain <= 0.0) {
            return 0.0;
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d < dist.length; d++) {
                if (dist[d] == 0.0) {
                    continue;
                }
                sum += dist[d] * (d >= h ? (d - h) : over[h - d]);
            }
            over[h] = sum / retain;
        }
        return over[targetHitpoints];
    }

    // ---- cascadeMaxHits / hitsForSize -----------------------------------------------------

    @Test
    public void hitsForSize_matchesTheWikiTable() {
        assertEquals(1, ScytheCascade.hitsForSize(1));
        assertEquals(2, ScytheCascade.hitsForSize(2));
        assertEquals(3, ScytheCascade.hitsForSize(3));
        assertEquals(3, ScytheCascade.hitsForSize(7)); // 3x3 "or larger"
    }

    @Test
    public void cascadeMaxHits_halvesAndFloorsEachStep() {
        assertEquals(1, ScytheCascade.cascadeMaxHits(47, 1).length);
        assertEquals(47, ScytheCascade.cascadeMaxHits(47, 1)[0]);
        assertEquals(23, ScytheCascade.cascadeMaxHits(47, 2)[1]); // floor(47/2)
        int[] three = ScytheCascade.cascadeMaxHits(47, 3);
        assertEquals(47, three[0]);
        assertEquals(23, three[1]); // floor(47/2)
        assertEquals(11, three[2]); // floor(23/2)
        int[] evenThree = ScytheCascade.cascadeMaxHits(48, 3);
        assertEquals(48, evenThree[0]);
        assertEquals(24, evenThree[1]);
        assertEquals(12, evenThree[2]);
    }

    // ---- Uncapped average/overkill: brute force -------------------------------------------

    @Test
    public void averageDamagePerAttack_matchesBruteForceEnumeration_oneHit() {
        double[] hitChances = {1.0, 0.85, 0.4};
        for (double hitChance : hitChances) {
            int[] maxHits = ScytheCascade.cascadeMaxHits(30, 1);
            double expected = bruteForceAverage(hitChance, maxHits);
            double actual = ScytheCascade.averageDamage(hitChance, 30, 1);
            assertEquals("hitChance=" + hitChance, expected, actual, DELTA);
        }
    }

    @Test
    public void averageDamagePerAttack_matchesBruteForceEnumeration_twoHits() {
        double[] hitChances = {1.0, 0.85, 0.4};
        for (double hitChance : hitChances) {
            double expected = bruteForceAverage(hitChance, ScytheCascade.cascadeMaxHits(24, 2));
            double actual = ScytheCascade.averageDamage(hitChance, 24, 2);
            assertEquals("hitChance=" + hitChance, expected, actual, DELTA);
        }
    }

    @Test
    public void averageDamagePerAttack_matchesBruteForceEnumeration_threeHits() {
        double[] hitChances = {1.0, 0.85, 0.6, 0.25};
        int[] baseMaxHits = {12, 23, 47};
        for (int base : baseMaxHits) {
            for (double hitChance : hitChances) {
                double expected = bruteForceAverage(hitChance, ScytheCascade.cascadeMaxHits(base, 3));
                double actual = ScytheCascade.averageDamage(hitChance, base, 3);
                assertEquals("base=" + base + " hitChance=" + hitChance, expected, actual, DELTA);
            }
        }
    }

    @Test
    public void expectedOverkill_matchesBruteForceEnumeration_threeHits() {
        double[] hitChances = {1.0, 0.85, 0.5};
        int[] hitpoints = {1, 3, 7, 15, 40};
        int base = 12; // small enough to keep the brute-force enumeration fast
        for (double hitChance : hitChances) {
            for (int hp : hitpoints) {
                double expected = bruteForceOverkill(hitChance, ScytheCascade.cascadeMaxHits(base, 3), hp);
                double actual = ScytheCascade.expectedOverkill(hitChance, base, 3, hp);
                assertEquals("hitChance=" + hitChance + " hp=" + hp, expected, actual, DELTA);
            }
        }
    }

    @Test
    public void expectedOverkill_matchesBruteForceEnumeration_twoHits() {
        double[] hitChances = {1.0, 0.7};
        int[] hitpoints = {1, 5, 20};
        int base = 16;
        for (double hitChance : hitChances) {
            for (int hp : hitpoints) {
                double expected = bruteForceOverkill(hitChance, ScytheCascade.cascadeMaxHits(base, 2), hp);
                double actual = ScytheCascade.expectedOverkill(hitChance, base, 2, hp);
                assertEquals("hitChance=" + hitChance + " hp=" + hp, expected, actual, DELTA);
            }
        }
    }

    @Test
    public void oneHit_matchesThePlainSingleHitFormula() {
        // A 1x1 target degenerates to exactly the ordinary single-hit case.
        double hitChance = 0.77;
        int maxHit = 33;
        assertEquals(DamageDistribution.averageDamage(hitChance, maxHit),
                ScytheCascade.averageDamage(hitChance, maxHit, 1), DELTA);
        assertEquals(DamageDistribution.expectedOverkill(maxHit, 50),
                ScytheCascade.expectedOverkill(hitChance, maxHit, 1, 50), DELTA);
    }

    // ---- Capped (CLAMP) / re-rolled: collapse identities + a hand-worked example ----------

    @Test
    public void cappedAverage_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        double expected = ScytheCascade.averageDamage(hitChance, 47, 3);
        assertEquals(expected, ScytheCascade.cappedAverageDamage(hitChance, 47, 3, 99), DELTA);
    }

    @Test
    public void rerolledAverage_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        double expected = ScytheCascade.averageDamage(hitChance, 47, 3);
        assertEquals(expected, ScytheCascade.rerolledAverageDamage(hitChance, 47, 3, 99), DELTA);
    }

    @Test
    public void cappedOverkill_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        double expected = ScytheCascade.expectedOverkill(hitChance, 24, 3, 20);
        assertEquals(expected, ScytheCascade.cappedExpectedOverkill(hitChance, 24, 3, 99, 20), DELTA);
    }

    @Test
    public void rerolledOverkill_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.6;
        double expected = ScytheCascade.expectedOverkill(hitChance, 24, 3, 20);
        assertEquals(expected, ScytheCascade.rerolledExpectedOverkill(hitChance, 24, 3, 99, 20), DELTA);
    }

    /**
     * Verzik Vitur phase 1 is exactly the scenario this matters for in real
     * gameplay: size 5 (3 hits), melee cap 10, CLAMP mode is NOT used there
     * (Verzik is REROLL) but this pins the CLAMP average against a direct,
     * independent per-hit clamp enumeration for a base max hit of 47
     * (cascade 47/23/11), cap 10: each cascade hit's clamped mean is
     * computed by hand-enumerating its own raw range and clamping.
     */
    @Test
    public void cappedAverage_handWorkedAgainstPerHitClampEnumeration() {
        double hitChance = 1.0;
        int[] maxHits = {47, 23, 11};
        int cap = 10;
        double expectedSum = 0.0;
        for (int m : maxHits) {
            double sum = 0.0;
            for (int raw = 0; raw <= m; raw++) {
                int landed = raw == 0 ? 1 : raw;
                sum += Math.min(landed, cap);
            }
            expectedSum += sum / (m + 1.0);
        }
        double actual = ScytheCascade.cappedAverageDamage(hitChance, 47, 3, cap);
        assertEquals(expectedSum, actual, DELTA);
    }
}
