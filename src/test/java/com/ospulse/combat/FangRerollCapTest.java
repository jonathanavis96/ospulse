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
 * re-rolled top-up. {@link CombatMath#rerolledFangAverageDamagePerAttack} and
 * {@link CombatMath#rerolledFangExpectedOverkill} model this exactly; this
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
                    CombatMath.rerolledFangAverageDamagePerAttack(1.0, max, cap), 1e-12);
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
        double result = CombatMath.rerolledFangAverageDamagePerAttack(1.0, 40, 10);
        assertEquals(160.0 / 29.0, result, 1e-12);
        assertNotEquals("must not reproduce the shrink-the-cap-itself result",
            5.0, result, 1e-9);
    }

    @Test
    public void capAtOrAboveTheShrunkMaximum_reducesToThePlainFangFormula() {
        for (int max : new int[]{20, 40, 99}) {
            int hi = max - (max * 3 / 20);
            assertEquals("cap == hi must be a no-op for max=" + max,
                CombatMath.fangAverageDamagePerAttack(1.0, max),
                CombatMath.rerolledFangAverageDamagePerAttack(1.0, max, hi), 1e-12);
            assertEquals("cap above hi must be a no-op for max=" + max,
                CombatMath.fangAverageDamagePerAttack(1.0, max),
                CombatMath.rerolledFangAverageDamagePerAttack(1.0, max, hi + 50), 1e-12);
        }
    }

    @Test
    public void capBelowTheShrunkMinimum_givesExactlyHalfTheCap() {
        // trueMax=40 -> lo=6; a cap below 6 means every raw result re-rolls.
        assertEquals(2.5, CombatMath.rerolledFangAverageDamagePerAttack(1.0, 40, 5), 1e-12);
        assertEquals(1.25, CombatMath.rerolledFangAverageDamagePerAttack(0.5, 40, 5), 1e-12);
        assertEquals(0.0, CombatMath.rerolledFangAverageDamagePerAttack(1.0, 40, 0), 1e-12);
    }

    // ---- rerolledFangExpectedOverkill: Monte Carlo simulation of the real mechanic --------

    /**
     * Simulates the ACTUAL described mechanic directly (roll uniform lo..hi;
     * if it exceeds the cap, re-roll uniformly into 0..cap; accumulate until
     * the target's HP is exhausted; record the overshoot) many times and
     * averages the result. Independent of every formula under test — this
     * doesn't call {@link CombatMath} at all — so it can catch a bug in the
     * closed-form DP that a test built from the same algebra could not.
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
                int damage = roll > cap ? random.nextInt(cap + 1) : roll;
                hp -= damage;
            }
            totalOverkill += -hp; // hp is <= 0 here; the overshoot is its magnitude
        }
        return totalOverkill / trials;
    }

    @Test
    public void rerolledFangOverkillMatchesAMonteCarloSimulation_forTheWorkedExample() {
        double analytic = CombatMath.rerolledFangExpectedOverkill(40, 10, 60);
        double simulated = simulateRerolledFangOverkill(40, 10, 60, 42L, 400_000);
        assertEquals("Monte Carlo simulation of the real mechanic must agree with the exact DP",
            analytic, simulated, 0.05);
    }

    @Test
    public void rerolledFangOverkillMatchesAMonteCarloSimulation_capBelowShrunkMinimum() {
        double analytic = CombatMath.rerolledFangExpectedOverkill(40, 5, 30);
        double simulated = simulateRerolledFangOverkill(40, 5, 30, 7L, 400_000);
        assertEquals(analytic, simulated, 0.05);
    }

    @Test
    public void overkillCapAtOrAboveTheShrunkMaximum_reducesToThePlainUncappedOverkill() {
        // Matches finishFang's own uncapped-path approximation: the generic
        // uniform model on the TRUE max hit (not the shrunk range).
        assertEquals(CombatMath.expectedOverkill(40, 60),
            CombatMath.rerolledFangExpectedOverkill(40, 34, 60), 1e-12);
        assertEquals(CombatMath.expectedOverkill(40, 60),
            CombatMath.rerolledFangExpectedOverkill(40, 100, 60), 1e-12);
    }

    @Test
    public void overkillCapBelowTheShrunkMinimum_isAPlainUniformZeroToCapDp_noBump() {
        // cap < lo -> the whole distribution is a plain uniform 0..cap roll,
        // WITHOUT the ordinary "0 is bumped to 1" convention that
        // expectedOverkill(cap, ...) would apply -- so these must differ.
        double rerolled = CombatMath.rerolledFangExpectedOverkill(40, 5, 30);
        double bumped = CombatMath.expectedOverkill(5, 30);
        assertNotEquals("a re-rolled fang hitsplat of 0 is genuine, not bumped to 1", bumped, rerolled, 1e-6);
    }

    @Test
    public void overkillIsZeroWhenTheCapIsZero() {
        assertEquals(0.0, CombatMath.rerolledFangExpectedOverkill(40, 0, 60), 1e-12);
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
     * {@link CombatMath#fangAverageDamagePerAttack}, unlike a much larger
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

        double expectedAvg = CombatMath.rerolledFangAverageDamagePerAttack(capped.accuracy(), trueMaxHit, 10);
        assertEquals(expectedAvg, capped.avgHit(), 1e-9);
        assertTrue("must not reproduce the shrink-the-cap-itself (much lower) average",
            capped.avgHit() > CombatMath.fangAverageDamagePerAttack(capped.accuracy(), 10) + 0.1);

        double expectedOverkill = CombatMath.rerolledFangExpectedOverkill(trueMaxHit, 10, verzikP1().hitpoints());
        assertEquals(expectedOverkill, capped.overkillPerKill(), 1e-9);

        double expectedDps = CombatMath.dps(expectedAvg, 4);
        assertEquals(expectedDps, capped.dps(), 1e-9);
        double expectedTtk = expectedDps > 0 ? (verzikP1().hitpoints() + expectedOverkill) / expectedDps : 0.0;
        assertEquals(expectedTtk, capped.ttkSeconds(), 1e-9);
    }
}
