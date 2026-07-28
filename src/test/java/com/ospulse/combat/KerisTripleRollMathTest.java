package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross-checks {@link KerisTripleRoll}'s average/overkill formulas against
 * an independent brute-force enumeration of the raw {@code 0..maxHit} roll
 * with its own restated 1/51 triple chance (NOT {@link KerisTripleRoll}'s
 * private constants), per the standing rule that a formula checked against
 * itself proves nothing.
 */
public class KerisTripleRollMathTest {
    private static final double DELTA = 1e-9;

    private static double bruteForceAverage(double hitChance, int maxHit) {
        double sum = 0.0;
        for (int raw = 0; raw <= maxHit; raw++) {
            int landed = raw == 0 ? 1 : raw;
            // 50/51 chance kept as-is, 1/51 chance tripled.
            double mixed = (50.0 / 51.0) * landed + (1.0 / 51.0) * (3 * landed);
            sum += mixed;
        }
        return hitChance * (sum / (maxHit + 1.0));
    }

    private static double bruteForceOverkill(double hitChance, int maxHit, int targetHitpoints) {
        // Full raw enumeration: outcome space is {MISS} union {raw 0..maxHit} x {kept, tripled}.
        double missProb = 1.0 - hitChance;
        double perRaw = hitChance / (maxHit + 1.0);
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            // Miss: 0 damage, doesn't change state - excluded from the sum entirely
            // (mirrors DamageDistribution.expectedOverkill's own miss handling).
            for (int raw = 0; raw <= maxHit; raw++) {
                int landed = raw == 0 ? 1 : raw;
                // Kept branch.
                double pKept = perRaw * (50.0 / 51.0);
                sum += pKept * (landed >= h ? (landed - h) : over[h - landed]);
                // Tripled branch.
                int tripled = 3 * landed;
                double pTripled = perRaw * (1.0 / 51.0);
                sum += pTripled * (tripled >= h ? (tripled - h) : over[h - tripled]);
            }
            // Renormalise over the "hit landed" probability mass only, matching
            // DamageDistribution.expectedOverkill's own convention (misses excluded).
            over[h] = sum / hitChance;
        }
        return over[targetHitpoints];
    }

    @Test
    public void averageDamagePerAttack_matchesBruteForceEnumeration() {
        int[] maxHits = {1, 5, 12, 23, 47};
        double[] hitChances = {1.0, 0.85, 0.5, 0.2};
        for (int maxHit : maxHits) {
            for (double hitChance : hitChances) {
                double baseAverage = DamageDistribution.averageDamage(hitChance, maxHit);
                double expected = bruteForceAverage(hitChance, maxHit);
                double actual = KerisTripleRoll.averageDamage(baseAverage);
                assertEquals("maxHit=" + maxHit + " hitChance=" + hitChance, expected, actual, DELTA);
            }
        }
    }

    @Test
    public void averageDamagePerAttack_isExactlyFiftyThreeFiftyOnesOfBase() {
        double hitChance = 0.66;
        int maxHit = 40;
        double baseAverage = DamageDistribution.averageDamage(hitChance, maxHit);
        double actual = KerisTripleRoll.averageDamage(baseAverage);
        assertEquals(baseAverage * 53.0 / 51.0, actual, DELTA);
    }

    @Test
    public void expectedOverkill_matchesBruteForceEnumeration() {
        int[] maxHits = {3, 8, 15};
        double[] hitChances = {1.0, 0.8, 0.4};
        int[] hitpoints = {1, 4, 10, 25};
        for (int maxHit : maxHits) {
            for (double hitChance : hitChances) {
                for (int hp : hitpoints) {
                    double expected = bruteForceOverkill(hitChance, maxHit, hp);
                    double actual = KerisTripleRoll.expectedOverkill(hitChance, maxHit, hp);
                    assertEquals("maxHit=" + maxHit + " hitChance=" + hitChance + " hp=" + hp,
                            expected, actual, DELTA);
                }
            }
        }
    }

    @Test
    public void expectedOverkill_isNeverLessThanTheUncappedGenericOverkill() {
        // Tripling adds extra high-damage mass, so overkill should generally
        // exceed the plain (non-tripling) model's overkill for the same maxHit.
        double hitChance = 1.0;
        int maxHit = 20;
        int hp = 15;
        double plain = DamageDistribution.expectedOverkill(maxHit, hp);
        double withTriple = KerisTripleRoll.expectedOverkill(hitChance, maxHit, hp);
        org.junit.Assert.assertTrue(withTriple >= plain);
    }

    @Test
    public void cappedOverkill_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.7;
        int maxHit = 30;
        double expected = KerisTripleRoll.expectedOverkill(hitChance, maxHit, 20);
        assertEquals(expected, KerisTripleRoll.cappedExpectedOverkill(hitChance, maxHit, 99, 20), DELTA);
    }

    @Test
    public void rerolledOverkill_capAtOrAboveMaxHitDelegatesToUncapped() {
        double hitChance = 0.7;
        int maxHit = 30;
        double expected = KerisTripleRoll.expectedOverkill(hitChance, maxHit, 20);
        assertEquals(expected, KerisTripleRoll.rerolledExpectedOverkill(hitChance, maxHit, 99, 20), DELTA);
    }
}
