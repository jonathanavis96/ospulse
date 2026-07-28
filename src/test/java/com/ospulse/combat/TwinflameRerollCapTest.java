package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

/**
 * {@link DpsCalculator#finishTwinflame} used to branch only on {@link
 * TargetDamage#isCapped()} and route every capped target through the
 * {@code cappedXxx} ({@code CLAMP}) helpers, regardless of the target's real
 * {@link MonsterCombatRequirement.CapMode}. A {@code REROLL}-capped target
 * (e.g. Verzik Vitur phase 1's ranged/magic cap of 3) reaching a Twinflame
 * cast was therefore silently treated as a clamp, piling excess probability
 * onto the cap instead of re-rolling it into {@code 0..cap} — overstating
 * average damage/DPS and corrupting overkill/TTK.
 *
 * <p>This file is the oracle for the fix: an EXACT brute-force enumeration of
 * the underlying mechanic (not the closed-form formulas under test), built
 * independently in this test, cross-checked against {@link
 * TwinflameSecondHit#rerolledSecondHitAverage}/{@code
 * rerolledCombinedExpectedOverkill} and {@link
 * DamageDistribution#rerolledAverageDamage} for several {@code (M, C)}
 * pairs, plus one end-to-end {@link DpsCalculator} proof that the wiring
 * (not just the maths) is fixed.
 */
public class TwinflameRerollCapTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    private static final double DELTA = 1e-9;

    /**
     * {@code (uncappedMaxHit, cap)} pairs required by the brief, covering: an
     * ordinary interior cap (40,3 / 25,3), a bigger cap (40,10 / 99,10), a
     * cap close to the max (4,3), the {@code cap == uncappedMaxHit} boundary
     * (3,3 — nothing ever re-rolls), and the {@code cap == 0} boundary (5,0 —
     * everything re-rolls into the single value 0).
     */
    private static final int[][] PAIRS = {
        {40, 3}, {40, 10}, {25, 3}, {99, 10}, {4, 3}, {3, 3}, {5, 0},
    };

    private static final int TARGET_HP = 15;

    // ---- Exact brute-force oracle, built from the mechanic, not the formulas -------------

    /**
     * Enumerates the raw roll {@code r} in {@code 0..M}, applies the ordinary
     * "rolled 0 becomes 1" bump, and — where the bumped result exceeds
     * {@code cap} — enumerates the re-roll {@code v} in {@code 0..cap},
     * accumulating exact integer weights over the common denominator
     * {@code (M+1)*(cap+1)}. Returns the numerators for displayed values
     * {@code 0..cap}; the caller divides by {@link #denom}.
     */
    private static long[] exactNumerators(int uncappedMaxHit, int cap) {
        long weightPerRawRoll = cap + 1L;
        long[] numerator = new long[cap + 1];
        for (int r = 0; r <= uncappedMaxHit; r++) {
            int bumped = (r == 0) ? 1 : r;
            if (bumped <= cap) {
                numerator[bumped] += weightPerRawRoll;
            } else {
                for (int v = 0; v <= cap; v++) {
                    numerator[v] += 1L;
                }
            }
        }
        return numerator;
    }

    private static long denom(int uncappedMaxHit, int cap) {
        return (long) (uncappedMaxHit + 1) * (cap + 1);
    }

    /** Sanity check on the oracle itself: the exact distribution must sum to exactly 1. */
    @Test
    public void oracleDistributionSumsToExactlyOne() {
        for (int[] pair : PAIRS) {
            int m = pair[0];
            int c = pair[1];
            long[] numerator = exactNumerators(m, c);
            long total = 0;
            for (long n : numerator) {
                total += n;
            }
            assertEquals("M=" + m + ", C=" + c, denom(m, c), total);
        }
    }

    private static double exactFirstHitAverage(int uncappedMaxHit, int cap) {
        long[] numerator = exactNumerators(uncappedMaxHit, cap);
        long denom = denom(uncappedMaxHit, cap);
        long sum = 0;
        for (int v = 0; v <= cap; v++) {
            sum += (long) v * numerator[v];
        }
        return (double) sum / denom;
    }

    private static double exactSecondHitAverage(int uncappedMaxHit, int cap) {
        long[] numerator = exactNumerators(uncappedMaxHit, cap);
        long denom = denom(uncappedMaxHit, cap);
        long sum = 0;
        for (int v = 0; v <= cap; v++) {
            sum += (long) ((2 * v) / 5) * numerator[v];
        }
        return (double) sum / denom;
    }

    /**
     * The combined-hitsplat expected overkill, computed from the exact
     * distribution above via a plain, independently-written renormalised DP
     * (verification #2 in the brief): {@code over[h] = (sum_{v>=1} p[v] *
     * (combined(v) >= h ? combined(v)-h : over[h-combined(v)])) / (1-p[0])} —
     * conditioning on a state-changing (v >= 1) outcome, exactly the
     * correction the production code needs to avoid putting {@code over[h]}
     * on its own right-hand side via the {@code v=0} term.
     */
    private static double exactCombinedExpectedOverkill(int uncappedMaxHit, int cap, int targetHitpoints) {
        long[] numerator = exactNumerators(uncappedMaxHit, cap);
        long denom = denom(uncappedMaxHit, cap);
        double[] p = new double[cap + 1];
        for (int v = 0; v <= cap; v++) {
            p[v] = (double) numerator[v] / denom;
        }
        double retain = 1.0 - p[0];
        if (retain <= 0.0) {
            return 0.0;
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int v = 1; v <= cap; v++) {
                int combined = v + ((2 * v) / 5);
                sum += p[v] * (combined >= h ? (combined - h) : over[h - combined]);
            }
            over[h] = sum / retain;
        }
        return over[targetHitpoints];
    }

    /**
     * The naive, NOT-zero-aware recurrence the brief warns about: summing
     * {@code v} from {@code 0} (instead of {@code 1}) with no renormalising
     * division. Since {@code combined(0) == 0}, the {@code v=0} term reads
     * {@code over[h - 0] == over[h]} before it has been assigned for this
     * {@code h} — silently reading the array's zero-initialised default
     * rather than a real value. Algebraically this computes exactly {@code
     * correct * retain} (the correct answer's numerator without dividing by
     * {@code 1-p[0]}), which is a DIFFERENT, WRONG number whenever {@code
     * p[0] > 0} — used below only to prove the tests actually discriminate.
     */
    private static double naiveUnrenormalisedCombinedExpectedOverkill(int uncappedMaxHit, int cap, int targetHitpoints) {
        long[] numerator = exactNumerators(uncappedMaxHit, cap);
        long denom = denom(uncappedMaxHit, cap);
        double[] p = new double[cap + 1];
        for (int v = 0; v <= cap; v++) {
            p[v] = (double) numerator[v] / denom;
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int v = 0; v <= cap; v++) {
                int combined = v + ((2 * v) / 5);
                sum += p[v] * (combined >= h ? (combined - h) : over[h - combined]);
            }
            over[h] = sum;
        }
        return over[targetHitpoints];
    }

    // ---- Term 1: first-hit average (hitChance excluded, i.e. hitChance = 1.0) ------------

    @Test
    public void firstHitAverageMatchesBruteForceOracle() {
        for (int[] pair : PAIRS) {
            int m = pair[0];
            int c = pair[1];
            double expected = exactFirstHitAverage(m, c);
            double actual = DamageDistribution.rerolledAverageDamage(1.0, m, c);
            assertEquals("M=" + m + ", C=" + c, expected, actual, DELTA);
        }
    }

    // ---- Term 2: second-hit average --------------------------------------------------------

    @Test
    public void secondHitAverageMatchesBruteForceOracle() {
        for (int[] pair : PAIRS) {
            int m = pair[0];
            int c = pair[1];
            double expected = exactSecondHitAverage(m, c);
            double actual = TwinflameSecondHit.rerolledSecondHitAverage(1.0, m, c);
            assertEquals("M=" + m + ", C=" + c, expected, actual, DELTA);
        }
    }

    // ---- Term 3: combined-hitsplat expected overkill ---------------------------------------

    @Test
    public void combinedExpectedOverkillMatchesBruteForceOracle() {
        for (int[] pair : PAIRS) {
            int m = pair[0];
            int c = pair[1];
            double expected = exactCombinedExpectedOverkill(m, c, TARGET_HP);
            double actual = TwinflameSecondHit.rerolledCombinedExpectedOverkill(m, c, TARGET_HP);
            assertEquals("M=" + m + ", C=" + c, expected, actual, DELTA);
        }
    }

    /**
     * Proves the zero-mass trap is real, not hypothetical: the naive
     * unrenormalised recurrence disagrees with the correct value for every
     * interior {@code (M, C)} pair where a genuine re-rolled zero has
     * positive probability ({@code cap < uncappedMaxHit} and {@code cap >
     * 0}). The gap is not a single constant factor applied once — the
     * missing {@code 1/(1-p[0])} renormalisation is dropped at EVERY level of
     * the recursion (each {@code over[h-combined]} sub-term the naive version
     * reads back is itself already under-renormalised), so the two DPs
     * diverge by more than a single multiplicative correction; this only
     * asserts the direction (naive silently understates), which is enough to
     * show the trap is a real, silent divergence rather than a cosmetic one.
     */
    @Test
    public void naiveRecurrenceDisagreesWithTheCorrectAnswerWhenZeroMassIsPositive() {
        for (int[] pair : PAIRS) {
            int m = pair[0];
            int c = pair[1];
            if (c >= m || c <= 0) {
                continue; // boundary cases: P(0) == 0 or the whole thing collapses to {0}
            }
            double correct = exactCombinedExpectedOverkill(m, c, TARGET_HP);
            double naive = naiveUnrenormalisedCombinedExpectedOverkill(m, c, TARGET_HP);

            assertTrue("M=" + m + ", C=" + c + ": naive must strictly understate the correct overkill",
                    naive < correct);
        }
    }

    // ---- End-to-end wiring proof through DpsCalculator -------------------------------------

    /**
     * The actual defect, reproduced end-to-end: a Twinflame cast at a
     * {@code REROLL}-capped target must NOT match the {@code CLAMP} result —
     * {@code CLAMP} piles probability mass onto the cap, so its average
     * damage per attack must be strictly higher than the equivalent
     * {@code REROLL} setup for the SAME natural max hit, cap and accuracy.
     * Before the fix, both modes were routed through the same
     * {@code cappedXxx} helpers and this assertion would fail.
     */
    @Test
    public void rerollCappedTwinflameCastProducesLowerAverageDamageThanClamp() {
        PlayerCombat player = PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.STANDARD)
                .build();
        Monster target = Monster.builder()
                .name("Twinflame reroll-cap wiring test target")
                .magicLevel(60)
                .defenceBonuses(0, 0, 0, 30, 0)
                .hitpoints(50_000) // large HP so overkill/TTK stay well-behaved
                .build();
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(6)
                .twinflameStaff(true)
                .build();

        int cap = 3; // well below Fire Wave's natural max hit at 99 magic
        MonsterCombatRequirement rerollReq = MonsterCombatRequirement.damageCap(cap, cap,
                Collections.emptySet(), Collections.emptyMap(), MonsterCombatRequirement.CapMode.REROLL,
                "test: reroll");
        MonsterCombatRequirement clampReq = MonsterCombatRequirement.damageCap(cap, cap,
                Collections.emptySet(), Collections.emptyMap(), MonsterCombatRequirement.CapMode.CLAMP,
                "test: clamp");

        DpsResult rerollResult = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, Spell.FIRE_WAVE,
                -1, rerollReq);
        DpsResult clampResult = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, Spell.FIRE_WAVE,
                -1, clampReq);

        assertEquals("sanity: the cap must actually bind for this proof to be meaningful",
                cap, rerollResult.maxHit());
        assertEquals("sanity: same cap value for the clamp comparison", cap, clampResult.maxHit());
        assertTrue("REROLL must produce strictly LOWER average damage than CLAMP for the same cap "
                        + "(before the fix both silently used the CLAMP formulas and this would be equal)",
                rerollResult.avgHit() < clampResult.avgHit());
    }
}
