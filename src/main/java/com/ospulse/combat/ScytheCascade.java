package com.ospulse.combat;

/**
 * The Scythe of Vitur family's (and its Holy/Sanguine reskins — see {@code
 * com.ospulse.session.GearVariants}) target-size-scaled multi-hit passive,
 * extracted into its own small, pure, dependency-light class (the same
 * precedent as {@code TonalzticsDualHit}/{@code TwinflameSecondHit}) rather
 * than folded into {@link DpsCalculator}.
 *
 * <p>Per the OSRS Wiki: a 1x1 target is hit once, a 2x2 target twice, a 3x3
 * (or larger) target three times, and "each hit will deal 50% less damage
 * (rounded down) than the preceding hit" — so a base max hit {@code M}
 * cascades to {@code M, floor(M/2), floor(floor(M/2)/2)}. Unlike {@code
 * TonalzticsDualHit} (two IDENTICAL independent rolls) this is a genuinely
 * DECAYING cascade, and — cross-checked directly via search against the
 * wiki's own wording ("these attacks' accuracy and strength rolls are rolled
 * independently ... each hit gets its own separate chance to land") — each
 * cascade hit ALSO makes its own independent accuracy roll at the SAME
 * hit chance, not one shared gate for the whole cascade. This does NOT
 * change the average (linearity of expectation makes the shared-vs-
 * independent-accuracy distinction irrelevant to the mean — see {@link
 * #averageDamagePerAttack}) but DOES matter for overkill, which needs the
 * joint distribution of all landed hits together (see {@link
 * #expectedOverkill}).
 *
 * <p><b>Follows the Osmumten's Fang precedent</b> (bespoke hitChance/average
 * pair bypassing the generic {@code finish()}) per the design spec, rather
 * than trying to express the cascade as a single multiplier on one roll —
 * the multiplier approach cannot be exact because {@code floor} is not
 * linear (the same reason {@code TwinflameSecondHit} rejects a naive
 * {@code 0.4x} shortcut).
 *
 * <p><b>Known, disclosed simplification:</b> a cascade hit whose decayed max
 * hit floors all the way to 0 still follows the ordinary OSRS "a rolled 0 on
 * a landed hit becomes 1" convention (the same convention {@link
 * DamageDistribution#averageDamagePerAttack} already applies uniformly to
 * every single-hit max in this codebase) rather than dealing a genuine zero.
 * This only matters at a base max hit of 3 or below (so the second/third
 * cascade hit floors to 0) — far under any level a scythe is ever realistically
 * used at — and is the same approximation tier the codebase already accepts
 * elsewhere (e.g. {@code DpsCalculator#finishFang}'s own documented uncapped-
 * overkill approximation).
 */
final class ScytheCascade {
    private ScytheCascade() {
    }

    /** Hits per attack for a target of the given {@link Monster#size()}: 1 (1x1), 2 (2x2), or 3 (3x3+). */
    static int hitsForSize(int size) {
        if (size >= 3) {
            return 3;
        }
        if (size == 2) {
            return 2;
        }
        return 1;
    }

    /**
     * The cascade's per-hit max hits: {@code [M, floor(M/2), floor(floor(M/2)/2)]},
     * truncated to {@code hits} entries. Integer division truncates toward
     * zero, identical to floor for non-negative {@code M}.
     */
    static int[] cascadeMaxHits(int firstMaxHit, int hits) {
        int[] result = new int[hits];
        int current = Math.max(firstMaxHit, 0);
        for (int i = 0; i < hits; i++) {
            result[i] = current;
            current = current / 2;
        }
        return result;
    }

    /** Uncapped total average damage per attack: the sum of each cascade hit's own average, at the SAME hit chance. */
    static double averageDamagePerAttack(double hitChance, int firstMaxHit, int hits) {
        double sum = 0.0;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            sum += DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
        }
        return sum;
    }

    /** Total average damage per attack against a target that CLAMPS each hitsplat — same cap applies to every cascade hit. */
    static double cappedAverageDamagePerAttack(double hitChance, int firstMaxHit, int hits, int cap) {
        double sum = 0.0;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            sum += DamageDistribution.cappedAverageDamagePerAttack(hitChance, maxHit, cap);
        }
        return sum;
    }

    /** Total average damage per attack against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledAverageDamagePerAttack(double hitChance, int firstMaxHit, int hits, int cap) {
        double sum = 0.0;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            sum += DamageDistribution.rerolledAverageDamagePerAttack(hitChance, maxHit, cap);
        }
        return sum;
    }

    /** Uncapped expected overkill for one full cascade (all hits together, each independently missing or landing). */
    static double expectedOverkill(double hitChance, int firstMaxHit, int hits, int targetHitpoints) {
        if (firstMaxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] combined = null;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            double[] perHit = uncappedPerHitDistribution(hitChance, maxHit);
            combined = combined == null ? perHit : convolve(combined, perHit);
        }
        return overkill(combined, targetHitpoints);
    }

    /** Expected overkill for one full cascade against a target that CLAMPS each hitsplat (same cap for every cascade hit). */
    static double cappedExpectedOverkill(double hitChance, int firstMaxHit, int hits, int cap, int targetHitpoints) {
        if (firstMaxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] combined = null;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            double[] perHit = cappedPerHitDistribution(hitChance, maxHit, cap);
            combined = combined == null ? perHit : convolve(combined, perHit);
        }
        return overkill(combined, targetHitpoints);
    }

    /** Expected overkill for one full cascade against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledExpectedOverkill(double hitChance, int firstMaxHit, int hits, int cap, int targetHitpoints) {
        if (firstMaxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] combined = null;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            double[] perHit = rerolledPerHitDistribution(hitChance, maxHit, cap);
            combined = combined == null ? perHit : convolve(combined, perHit);
        }
        return overkill(combined, targetHitpoints);
    }

    /**
     * One cascade hit's full outcome distribution (index 0 = miss, summing
     * to 1 overall): miss with probability {@code 1 - hitChance}, else the
     * ordinary bumped-uniform {@code 0..maxHit} roll. {@code maxHit <= 0} is
     * folded to the degenerate "always deals 1 on a landed hit" case — see
     * the class javadoc's documented simplification.
     */
    private static double[] uncappedPerHitDistribution(double hitChance, int maxHit) {
        if (maxHit <= 0) {
            double[] p = new double[2];
            p[0] = 1.0 - hitChance;
            p[1] = hitChance;
            return p;
        }
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
     * As {@link #uncappedPerHitDistribution}, but for a target that CLAMPS
     * each hitsplat onto {@code cap}. {@code maxHit <= 0} is checked FIRST
     * and always delegates to the uncapped degenerate case regardless of
     * {@code cap} — the same documented low-impact simplification as
     * {@link #uncappedPerHitDistribution}, for the doubly-degenerate corner
     * where a cascade hit's own max has already floored to 0.
     */
    private static double[] cappedPerHitDistribution(double hitChance, int maxHit, int cap) {
        if (maxHit <= 0 || cap >= maxHit) {
            return uncappedPerHitDistribution(hitChance, maxHit);
        }
        if (cap <= 0) {
            double[] p = new double[1];
            p[0] = 1.0; // every landed hit clamps to 0 as well as every miss - all mass at 0
            return p;
        }
        double[] capped = DamageDistribution.cappedHitsplatDistribution(maxHit, cap);
        double[] p = new double[cap + 1];
        p[0] = 1.0 - hitChance;
        for (int d = 1; d <= cap; d++) {
            p[d] = hitChance * capped[d];
        }
        return p;
    }

    /**
     * As {@link #uncappedPerHitDistribution}, but for a target that
     * RE-ROLLS each hitsplat above {@code cap} into {@code 0..cap}. Same
     * {@code maxHit <= 0} precedence as {@link #cappedPerHitDistribution}.
     */
    private static double[] rerolledPerHitDistribution(double hitChance, int maxHit, int cap) {
        if (maxHit <= 0 || cap >= maxHit) {
            return uncappedPerHitDistribution(hitChance, maxHit);
        }
        if (cap <= 0) {
            double[] p = new double[1];
            p[0] = 1.0; // every landed hit re-rolls into the single value {0} as well as every miss
            return p;
        }
        double[] rerolled = DamageDistribution.rerolledHitsplatDistribution(maxHit, cap);
        double[] p = new double[cap + 1];
        p[0] = (1.0 - hitChance) + hitChance * rerolled[0];
        for (int d = 1; d <= cap; d++) {
            p[d] = hitChance * rerolled[d];
        }
        return p;
    }

    /** Convolves two independent per-hit outcome distributions into their combined sum's distribution. */
    private static double[] convolve(double[] a, double[] b) {
        double[] out = new double[a.length + b.length - 1];
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0.0) {
                continue;
            }
            for (int j = 0; j < b.length; j++) {
                if (b[j] == 0.0) {
                    continue;
                }
                out[i + j] += a[i] * b[j];
            }
        }
        return out;
    }

    /**
     * Runs {@link DamageDistribution#overkillFromExplicitDistribution(double[], int[], int)}
     * (identity {@code amount[v] = v}) over the full cascade's combined
     * per-cycle distribution — its {@code 1 - p[0]} renormalisation already
     * correctly treats "every hit in the cascade missed" as a no-op, exactly
     * like an ordinary miss.
     */
    private static double overkill(double[] combined, int targetHitpoints) {
        int[] identity = new int[combined.length];
        for (int v = 0; v < combined.length; v++) {
            identity[v] = v;
        }
        return DamageDistribution.overkillFromExplicitDistribution(combined, identity, targetHitpoints);
    }

    /**
     * Assembles the full {@link DpsResult} for a Scythe of Vitur attack,
     * mirroring {@link TonalzticsDualHit#finish}'s shape (plain primitives
     * for the target-damage inputs, no new private method on {@link
     * DpsCalculator} — see the standing split directive). {@code maxHit} in
     * the returned result is the FIRST (largest) cascade hit's visible max,
     * matching how the game itself displays a scythe's max hit.
     */
    static DpsResult finish(int uncappedMaxHit, int cap, MonsterCombatRequirement.CapMode mode, int targetSize,
                             int attackRoll, int defenceRoll, int weaponSpeedTicks, int targetHitpoints) {
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        boolean capped = cap >= 0 && cap < uncappedMaxHit;
        int hits = hitsForSize(targetSize);
        int visibleMaxHit = capped ? Math.min(uncappedMaxHit, cap) : uncappedMaxHit;
        double avgDamage;
        double overkill;
        if (!capped) {
            avgDamage = averageDamagePerAttack(hitChance, uncappedMaxHit, hits);
            overkill = expectedOverkill(hitChance, uncappedMaxHit, hits, targetHitpoints);
        } else if (mode == MonsterCombatRequirement.CapMode.REROLL) {
            avgDamage = rerolledAverageDamagePerAttack(hitChance, uncappedMaxHit, hits, cap);
            overkill = rerolledExpectedOverkill(hitChance, uncappedMaxHit, hits, cap, targetHitpoints);
        } else {
            avgDamage = cappedAverageDamagePerAttack(hitChance, uncappedMaxHit, hits, cap);
            overkill = cappedExpectedOverkill(hitChance, uncappedMaxHit, hits, cap, targetHitpoints);
        }
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(visibleMaxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, false);
    }
}
