package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

/**
 * Osmumten's fang against a target that RE-ROLLS each hitsplat above a cap
 * (e.g. Verzik Vitur phase 1), as opposed to {@code CLAMP} (The Hueycoatl's
 * tail, already covered elsewhere).
 *
 * <p>This is a genuinely different distribution from BOTH the fang's clamp
 * model AND the generic re-roll equivalence: the fang's roll is uniform over
 * {@code lo..hi} (never the full {@code 0..maxHit}), so re-rolling a result
 * above the cap back into {@code 0..cap} does not reduce to a plain
 * {@code lo..cap} roll — values below {@code lo} are only reachable via a
 * re-roll, and values in {@code lo..cap} get both their own mass AND a
 * re-rolled top-up. {@link DamageDistribution#rerolledFangAverageDamagePerAttack} and
 * {@link DamageDistribution#rerolledFangExpectedOverkill} model this exactly; this
 * class proves both the formulas and that {@link DpsCalculator} actually
 * routes a fang loadout through them.
 */
public class FangRerollCapTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    // ---- rerolledFangAverageDamagePerAttack: brute-force + worked example + boundaries ---

    /**
     * Direct enumeration over the fang's uniform {@code lo..hi} raw roll: a
     * result {@code <= cap} stands, a result {@code > cap} re-rolls uniformly
     * into {@code 0..cap} (expectation exactly {@code cap/2}, no bump — a
     * genuine zero is a real re-rolled result, not folded into 1). Built from
     * first principles, not the closed form under test.
     */
    private static double bruteForceRerolledFang(int trueMaxHit, int cap) {
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        double total = 0.0;
        for (int r = lo; r <= hi; r++) {
            total += (r <= cap) ? r : cap / 2.0;
        }
        return total / (hi - lo + 1);
    }

    @Test
    public void rerolledFangMatchesABruteForceEnumeration() {
        for (int max : new int[]{20, 27, 40, 55, 99}) {
            int shrink = max * 3 / 20;
            if (shrink <= 0) {
                continue; // degenerate range is covered by its own fallback test
            }
            for (int cap : new int[]{1, 4, 9, 10, 15, 30, 60}) {
                assertEquals("max=" + max + " cap=" + cap,
                    bruteForceRerolledFang(max, cap),
                    DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, max, cap), 1e-12);
            }
        }
    }

    /**
     * The worked example from the review: a true max of 40 shrinks to a
     * 6..34 roll. Values 6-10 (5 of 29 equally likely outcomes) stand; values
     * 11-34 (24 outcomes) re-roll to an expectation of 5. Average =
     * (6+7+8+9+10 + 24*5) / 29 = 160/29. NOT 5.0, which is what wrongly
     * re-deriving the compression from the already-capped value (10) gives.
     */
    @Test
    public void cap10OfTrueMax40MatchesTheWorkedExample() {
        double result = DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, 40, 10);
        assertEquals(160.0 / 29.0, result, 1e-12);
        assertNotEquals("must not reproduce the shrink-the-cap-itself result",
            5.0, result, 1e-9);
    }

    @Test
    public void capAtOrAboveTheShrunkMaximum_reducesToThePlainFangFormula() {
        for (int max : new int[]{20, 40, 99}) {
            int hi = max - (max * 3 / 20);
            assertEquals("cap == hi must be a no-op for max=" + max,
                DamageDistribution.fangAverageDamagePerAttack(1.0, max),
                DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, max, hi), 1e-12);
            assertEquals("cap above hi must be a no-op for max=" + max,
                DamageDistribution.fangAverageDamagePerAttack(1.0, max),
                DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, max, hi + 50), 1e-12);
        }
    }

    @Test
    public void capBelowTheShrunkMinimum_givesExactlyHalfTheCap() {
        // trueMax=40 -> lo=6; a cap below 6 means every raw result re-rolls.
        assertEquals(2.5, DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, 40, 5), 1e-12);
        assertEquals(1.25, DamageDistribution.rerolledFangAverageDamagePerAttack(0.5, 40, 5), 1e-12);
        assertEquals(0.0, DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, 40, 0), 1e-12);
    }

    // ---- The degenerate lo<=0 fallback: it IS a generic 0..hi roll, so it needs the SAME
    // zero-aware fix as DpsCalculator.finish's REROLL branch, not the ordinary bumped formula.

    /**
     * A true max hit of 6 or below shrinks to {@code lo=0}, so the fang's
     * "compressed" roll is really a plain {@code 0..hi} roll — exactly the
     * generic case, and re-rolling it at a cap needs {@link
     * DamageDistribution#rerolledAverageDamagePerAttack}, not the ordinary bumped
     * {@link DamageDistribution#averageDamagePerAttack} (an earlier version of this
     * method used that, double-applying the "rolled 0 becomes 1" bump to the
     * re-roll's own genuine zero).
     */
    private static double bruteForceDegenerateFangMean(int trueMaxHit, int cap) {
        int hi = trueMaxHit - (trueMaxHit * 3 / 20);
        double total = 0.0;
        for (int raw = 0; raw <= hi; raw++) {
            int bumped = raw <= 1 ? 1 : raw;
            total += (bumped > cap) ? (cap / 2.0) : bumped;
        }
        return total / (hi + 1);
    }

    @Test
    public void degenerateBranch_matchesABruteForceEnumeration_ofTheGenericZeroAwareModel() {
        // trueMaxHit <= 6 -> shrink == 0 -> lo <= 0 (the degenerate branch).
        for (int trueMax : new int[]{0, 1, 3, 5, 6}) {
            int hi = trueMax - (trueMax * 3 / 20);
            for (int cap : new int[]{0, 1, 2, 3}) {
                if (cap >= hi) {
                    continue; // covered by capAtOrAboveTheShrunkMaximum_reducesToThePlainFangFormula
                }
                assertEquals("trueMax=" + trueMax + " cap=" + cap,
                    bruteForceDegenerateFangMean(trueMax, cap),
                    DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, trueMax, cap), 1e-12);
            }
        }
    }

    @Test
    public void degenerateBranch_isNotTheNaiveBumpedFormula() {
        // trueMax=6 -> shrink=0, lo=0, hi=6 -- a binding cap of 3 must NOT be
        // averageDamagePerAttack(hitChance, 3) (~1.75), which double-applies the bump.
        double correct = DamageDistribution.rerolledFangAverageDamagePerAttack(1.0, 6, 3);
        double naiveBumped = DamageDistribution.averageDamagePerAttack(1.0, 3);
        assertNotEquals(naiveBumped, correct, 1e-9);
        assertEquals(3.0 / 2.0 + 1.0 / 7.0, correct, 1e-12);
    }

    // ---- rerolledFangExpectedOverkill: Monte Carlo simulation of the real mechanic --------

    /**
     * Simulates the ACTUAL described mechanic directly (roll uniform lo..hi;
     * apply the ordinary "rolled 0 becomes 1" bump — only ever reachable when
     * {@code lo <= 0}, the degenerate case, since a genuine {@code lo >= 1}
     * roll can never produce a 0 in the first place; if the (bumped) result
     * exceeds the cap, re-roll uniformly into 0..cap; accumulate until the
     * target's HP is exhausted; record the overshoot) many times and averages
     * the result. Independent of every formula under test — this doesn't call
     * {@link CombatMath} at all — so it can catch a bug in the closed-form DP
     * that a test built from the same algebra could not.
     */
    private static double simulateRerolledFangOverkill(int trueMaxHit, int cap, int targetHitpoints, long seed, int trials) {
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        Random random = new Random(seed);
        double totalOverkill = 0.0;
        for (int t = 0; t < trials; t++) {
            int hp = targetHitpoints;
            while (hp > 0) {
                int roll = lo + random.nextInt(hi - lo + 1);
                int bumped = roll == 0 ? 1 : roll;
                int damage = bumped > cap ? random.nextInt(cap + 1) : bumped;
                hp -= damage;
            }
            totalOverkill += -hp; // hp is <= 0 here; the overshoot is its magnitude
        }
        return totalOverkill / trials;
    }

    @Test
    public void rerolledFangOverkillMatchesAMonteCarloSimulation_forTheWorkedExample() {
        double analytic = DamageDistribution.rerolledFangExpectedOverkill(40, 10, 60);
        double simulated = simulateRerolledFangOverkill(40, 10, 60, 42L, 400_000);
        assertEquals("Monte Carlo simulation of the real mechanic must agree with the exact DP",
            analytic, simulated, 0.05);
    }

    @Test
    public void rerolledFangOverkillMatchesAMonteCarloSimulation_capBelowShrunkMinimum() {
        double analytic = DamageDistribution.rerolledFangExpectedOverkill(40, 5, 30);
        double simulated = simulateRerolledFangOverkill(40, 5, 30, 7L, 400_000);
        assertEquals(analytic, simulated, 0.05);
    }

    /** trueMax=6 -> shrink=0, lo=0, hi=6 -- the degenerate branch, simulated directly. */
    @Test
    public void rerolledFangOverkillMatchesAMonteCarloSimulation_degenerateBranch() {
        double analytic = DamageDistribution.rerolledFangExpectedOverkill(6, 3, 20);
        double simulated = simulateRerolledFangOverkill(6, 3, 20, 99L, 400_000);
        assertEquals(analytic, simulated, 0.05);
    }

    /**
     * The degenerate branch must NOT reuse {@link DamageDistribution#expectedOverkill}
     * on the cap (an earlier version of this method did) -- that bakes in the
     * ordinary bump, assuming the re-roll's zero is impossible.
     */
    @Test
    public void degenerateOverkillBranch_isNotTheNaiveBumpedFormula() {
        double correct = DamageDistribution.rerolledFangExpectedOverkill(6, 3, 20);
        double naiveBumped = DamageDistribution.expectedOverkill(3, 20);
        assertNotEquals(naiveBumped, correct, 1e-6);
    }

    @Test
    public void overkillCapAtOrAboveTheShrunkMaximum_reducesToThePlainUncappedOverkill() {
        // Matches finishFang's own uncapped-path approximation: the generic
        // uniform model on the TRUE max hit (not the shrunk range).
        assertEquals(DamageDistribution.expectedOverkill(40, 60),
            DamageDistribution.rerolledFangExpectedOverkill(40, 34, 60), 1e-12);
        assertEquals(DamageDistribution.expectedOverkill(40, 60),
            DamageDistribution.rerolledFangExpectedOverkill(40, 100, 60), 1e-12);
    }

    @Test
    public void overkillCapBelowTheShrunkMinimum_isAPlainUniformZeroToCapDp_noBump() {
        // cap < lo -> the whole distribution is a plain uniform 0..cap roll,
        // WITHOUT the ordinary "0 is bumped to 1" convention that
        // expectedOverkill(cap, ...) would apply -- so these must differ.
        double rerolled = DamageDistribution.rerolledFangExpectedOverkill(40, 5, 30);
        double bumped = DamageDistribution.expectedOverkill(5, 30);
        assertNotEquals("a re-rolled fang hitsplat of 0 is genuine, not bumped to 1", bumped, rerolled, 1e-6);
    }

    @Test
    public void overkillIsZeroWhenTheCapIsZero() {
        assertEquals(0.0, DamageDistribution.rerolledFangExpectedOverkill(40, 0, 60), 1e-12);
    }

    // ---- End-to-end: DpsCalculator actually routes the fang through these formulas -------

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).defence(70, 70).ranged(80, 80).magic(75, 75)
                .prayer(70, 70).hitpoints(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    /**
     * Tuned so the fang's TRUE max hit (37 with this player/gear) puts Verzik
     * P1's melee cap (10) strictly between the shrunk {@code lo} (5) and
     * {@code hi} (32) — the "otherwise" branch that actually differs from
     * {@link DamageDistribution#fangAverageDamagePerAttack}, unlike a much larger
     * true max hit where the cap falls below {@code lo} and both formulas
     * coincidentally agree (both reduce to {@code cap/2}).
     */
    private static EquipmentStats fangGear() {
        return EquipmentStats.builder()
                .add(80, 60, 40, 0, 0, 0, 0, 0, 0, 0, 150, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .osmumtensFang(true)
                .build();
    }

    private static Monster verzikP1() {
        return Monster.builder()
                .name("Verzik Vitur (Entry mode, Phase 1)")
                .hitpoints(400)
                .defenceLevel(150)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(200)
                .build();
    }

    /** Same defence profile as {@link #verzikP1()} but not a curated monster -- reveals the fang's TRUE max hit. */
    private static Monster uncappedControl() {
        return Monster.builder()
                .name("Zzz Fang Reroll Control (Uncapped)")
                .hitpoints(400)
                .defenceLevel(150)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(200)
                .build();
    }

    @Test
    public void dpsCalculatorRoutesAFangLoadoutAtVerzikThroughTheRerolledFormula() {
        DpsResult control = DpsCalculator.compute(fangGear(), player(), CombatStyle.STAB, uncappedControl(), 0);
        int trueMaxHit = control.maxHit(); // fang leaves maxHit as the TRUE unshrunk value when uncapped
        assertTrue("sanity: the true max hit must exceed Verzik's cap of 10 for this to be a real proof",
            trueMaxHit > 10);

        DpsResult capped = DpsCalculator.compute(fangGear(), player(), CombatStyle.STAB, verzikP1(), 0);
        assertEquals("the readout shows the cap as the max hit", 10, capped.maxHit());
        assertEquals("accuracy is untouched by capping -- same gear, same target defence",
            control.accuracy(), capped.accuracy(), 1e-12);

        double expectedAvg = DamageDistribution.rerolledFangAverageDamagePerAttack(capped.accuracy(), trueMaxHit, 10);
        assertEquals(expectedAvg, capped.avgHit(), 1e-9);
        assertTrue("must not reproduce the shrink-the-cap-itself (much lower) average",
            capped.avgHit() > DamageDistribution.fangAverageDamagePerAttack(capped.accuracy(), 10) + 0.1);

        double expectedOverkill = DamageDistribution.rerolledFangExpectedOverkill(trueMaxHit, 10, verzikP1().hitpoints());
        assertEquals(expectedOverkill, capped.overkillPerKill(), 1e-9);

        double expectedDps = CombatMath.dps(expectedAvg, 4);
        assertEquals(expectedDps, capped.dps(), 1e-9);
        double expectedTtk = expectedDps > 0 ? (verzikP1().hitpoints() + expectedOverkill) / expectedDps : 0.0;
        assertEquals(expectedTtk, capped.ttkSeconds(), 1e-9);
    }
}
