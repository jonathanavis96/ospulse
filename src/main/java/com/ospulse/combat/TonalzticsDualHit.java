package com.ospulse.combat;

/**
 * The CHARGED Tonalztics of Ralos's (item id 28922 — see {@code
 * com.ospulse.session.GearVariants}) dual-hit passive, extracted into its
 * own small, pure, dependency-light class (the same precedent as {@code
 * TwinflameSecondHit}/{@code DemonbaneWeapon}/{@code VoidSet}) rather than
 * folded into {@link DpsCalculator}, so this narrow mechanic can be
 * unit-tested in isolation without touching that already-large,
 * concurrently-edited file.
 *
 * <p>Per the OSRS Wiki: charged, "the weapon will hit twice, with two
 * independent damage rolls" — unlike {@code TwinflameSecondHit} (whose
 * second hitsplat is a fixed 40% of the FIRST hit's own rolled value) or the
 * decaying {@code ScytheCascade} (whose later hits use a SMALLER max hit),
 * here BOTH hits are drawn from the exact same {@code 0..maxHit} distribution,
 * fully independently of one another and of each other's accuracy. This is
 * deliberately the simplest of the six §9 multi-hit mechanics and establishes
 * the pattern the more complex ones (scythe) build on.
 *
 * <p><b>Average damage is a trivial doubling</b> — {@code E[hit1 + hit2] =
 * E[hit1] + E[hit2]} by linearity of expectation, true regardless of any
 * correlation between the two rolls, so the existing single-hit average
 * formulas ({@link DamageDistribution#averageDamagePerAttack}/{@code
 * cappedAverageDamagePerAttack}/{@code rerolledAverageDamagePerAttack}) are
 * reused unchanged and simply doubled — no new average formula is needed.
 *
 * <p><b>Overkill is NOT a trivial doubling</b> — the target's remaining HP
 * depletes by the SUM of both hits within one attack cycle (they land the
 * same tick per the wiki's "attack range" wording, with nothing else able to
 * act on the target in between, the same one-continuous-source assumption
 * {@code TwinflameSecondHit} documents), so the overkill DP needs the joint
 * distribution of {@code hit1 + hit2}, not twice the single-hit overkill.
 * Both hits share one convolution helper ({@link #convolveAndOverkill}) fed a
 * single-hit distribution ({@link #uncappedPerHitDistribution} or its
 * capped/re-rolled equivalents) built from the SAME single-hit distribution
 * the average functions consume
 * (a miss contributes a real, explicit {@code 0}, a landed hit contributes
 * the ordinary bumped-uniform-or-capped-or-rerolled pmf), convolved with
 * itself, then handed to {@link
 * DamageDistribution#overkillFromExplicitDistribution(double[], int[], int)}
 * — which already conditions on a non-zero result via its {@code 1 - p[0]}
 * renormalisation, so a "both hits missed" cycle (a real, positive-probability
 * outcome here, unlike the single-hit models where a miss is simply excluded
 * from the distribution entirely) correctly contributes nothing, exactly like
 * an ordinary miss.
 */
final class TonalzticsDualHit {
    private TonalzticsDualHit() {
    }

    /** Uncapped combined average damage per attack cycle (both hits). */
    static double combinedAverageDamagePerAttack(double hitChance, int maxHit) {
        return 2.0 * DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
    }

    /** Combined average damage per attack cycle against a target that CLAMPS each hitsplat. */
    static double cappedCombinedAverageDamagePerAttack(double hitChance, int uncappedMaxHit, int cap) {
        return 2.0 * DamageDistribution.cappedAverageDamagePerAttack(hitChance, uncappedMaxHit, cap);
    }

    /** Combined average damage per attack cycle against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledCombinedAverageDamagePerAttack(double hitChance, int uncappedMaxHit, int cap) {
        return 2.0 * DamageDistribution.rerolledAverageDamagePerAttack(hitChance, uncappedMaxHit, cap);
    }

    /** Uncapped combined expected overkill for one attack cycle (both hits together). */
    static double combinedExpectedOverkill(double hitChance, int maxHit, int targetHitpoints) {
        if (maxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] perHit = uncappedPerHitDistribution(hitChance, maxHit);
        return convolveAndOverkill(perHit, targetHitpoints);
    }

    /** Combined expected overkill for one attack cycle against a target that CLAMPS each hitsplat. */
    static double cappedCombinedExpectedOverkill(double hitChance, int uncappedMaxHit, int cap, int targetHitpoints) {
        if (cap >= uncappedMaxHit) {
            return combinedExpectedOverkill(hitChance, uncappedMaxHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0 || uncappedMaxHit <= 0) {
            return 0.0;
        }
        double[] capped = DamageDistribution.cappedHitsplatDistribution(uncappedMaxHit, cap);
        double[] perHit = new double[cap + 1];
        perHit[0] = 1.0 - hitChance;
        for (int d = 1; d <= cap; d++) {
            perHit[d] = hitChance * capped[d];
        }
        return convolveAndOverkill(perHit, targetHitpoints);
    }

    /** Combined expected overkill for one attack cycle against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledCombinedExpectedOverkill(double hitChance, int uncappedMaxHit, int cap, int targetHitpoints) {
        if (cap >= uncappedMaxHit) {
            return combinedExpectedOverkill(hitChance, uncappedMaxHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0 || uncappedMaxHit <= 0) {
            return 0.0;
        }
        double[] rerolled = DamageDistribution.rerolledHitsplatDistribution(uncappedMaxHit, cap);
        double[] perHit = new double[cap + 1];
        perHit[0] = (1.0 - hitChance) + hitChance * rerolled[0];
        for (int d = 1; d <= cap; d++) {
            perHit[d] = hitChance * rerolled[d];
        }
        return convolveAndOverkill(perHit, targetHitpoints);
    }

    /**
     * One hit's full outcome distribution INCLUDING the miss probability at
     * index 0 (unlike {@link DamageDistribution}'s "landed only" arrays,
     * which sum to 1 over a genuine hit) — a miss (probability
     * {@code 1 - hitChance}) contributes 0, and a landed hit
     * (probability {@code hitChance}) contributes the ordinary
     * "rolled 0 becomes 1" bumped-uniform pmf over {@code 1..maxHit}.
     */
    private static double[] uncappedPerHitDistribution(double hitChance, int maxHit) {
        double[] p = new double[maxHit + 1];
        p[0] = 1.0 - hitChance;
        double denom = maxHit + 1.0;
        p[1] = hitChance * 2.0 / denom;
        for (int d = 2; d <= maxHit; d++) {
            p[d] = hitChance / denom;
        }
        return p;
    }

    /**
     * Convolves a single-hit outcome distribution (index 0 = miss/zero,
     * summing to 1 overall) with itself to get the two-hit combined
     * per-cycle distribution, then runs {@link
     * DamageDistribution#overkillFromExplicitDistribution(double[], int[], int)}
     * over it (identity {@code amount[v] = v}, since both hits' raw values
     * are exactly the HP removed).
     */
    private static double convolveAndOverkill(double[] perHit, int targetHitpoints) {
        int n = perHit.length - 1;
        double[] combined = new double[2 * n + 1];
        for (int a = 0; a <= n; a++) {
            if (perHit[a] == 0.0) {
                continue;
            }
            for (int b = 0; b <= n; b++) {
                if (perHit[b] == 0.0) {
                    continue;
                }
                combined[a + b] += perHit[a] * perHit[b];
            }
        }
        int[] identity = new int[combined.length];
        for (int v = 0; v < combined.length; v++) {
            identity[v] = v;
        }
        return DamageDistribution.overkillFromExplicitDistribution(combined, identity, targetHitpoints);
    }

    /**
     * Assembles the full {@link DpsResult} for a charged-Tonalztics attack,
     * mirroring {@link DpsCalculator}'s {@code finish}/{@code finishFang}/
     * {@code finishTwinflame} shape but taking the target-damage inputs as
     * plain primitives (uncapped max hit, per-hitsplat cap or {@code -1} for
     * none, and the {@link MonsterCombatRequirement.CapMode}) rather than
     * that class's private {@code TargetDamage} type, so this class needs no
     * visibility change to anything in {@link DpsCalculator} and — per the
     * standing split directive — {@link DpsCalculator} itself gains no new
     * private method for this mechanic, only a guarded call straight into
     * here from {@code computeRanged}.
     *
     * <p>{@code maxHit} in the returned result is the PER-HIT visible max
     * (what a single hitsplat could show), not the two-hit total — matching
     * how {@code finishFang} reports the true, uncompressed max hit rather
     * than a derived aggregate.
     */
    static DpsResult finish(int uncappedMaxHit, int cap, MonsterCombatRequirement.CapMode mode,
                             int attackRoll, int defenceRoll, int weaponSpeedTicks, int targetHitpoints) {
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        boolean capped = cap >= 0 && cap < uncappedMaxHit;
        int visibleMaxHit = capped ? Math.min(uncappedMaxHit, cap) : uncappedMaxHit;
        double avgDamage;
        double overkill;
        if (!capped) {
            avgDamage = combinedAverageDamagePerAttack(hitChance, uncappedMaxHit);
            overkill = combinedExpectedOverkill(hitChance, uncappedMaxHit, targetHitpoints);
        } else if (mode == MonsterCombatRequirement.CapMode.REROLL) {
            avgDamage = rerolledCombinedAverageDamagePerAttack(hitChance, uncappedMaxHit, cap);
            overkill = rerolledCombinedExpectedOverkill(hitChance, uncappedMaxHit, cap, targetHitpoints);
        } else {
            avgDamage = cappedCombinedAverageDamagePerAttack(hitChance, uncappedMaxHit, cap);
            overkill = cappedCombinedExpectedOverkill(hitChance, uncappedMaxHit, cap, targetHitpoints);
        }
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(visibleMaxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, false);
    }
}
