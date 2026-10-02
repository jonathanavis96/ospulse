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
 * TonalzticsDualHit} (two IDENTICAL independent rolls, each over a REDUCED
 * range) this is a genuinely DECAYING cascade over the full range, and is
 * now confirmed from a source that states it plainly, not merely inferred:
 * the OSRS Wiki's "Multi-hit weapons" comparison table (2026-07-27,
 * {@code api.php?action=parse&page=Multi-hit_weapons&prop=wikitext}) —
 * <pre>
 * Scythe of vitur —
 *   "Hits 2x2 monsters twice and 3x3 or larger monsters 3 times...
 *    The 2nd hit has 1/2 of the base maximum hit, while the 3rd hit
 *    has 1/4 of the base maximum hit.
 *    Each hit rolls for accuracy independently."
 * </pre>
 * matches exactly what is implemented here: {@code M, M/2, M/4}, each hit
 * with its OWN independent accuracy roll at the SAME hit chance, not one
 * shared gate for the whole cascade. This does NOT change the average
 * (linearity of expectation makes the shared-vs-independent-accuracy
 * distinction irrelevant to the mean — see {@link #averageDamage})
 * but DOES matter for overkill, which needs the joint distribution of all
 * landed hits together (see {@link #expectedOverkill}).
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
 * DamageDistribution#averageDamage} already applies uniformly to
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
    static double averageDamage(double hitChance, int firstMaxHit, int hits) {
        double sum = 0.0;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            sum += DamageDistribution.averageDamage(hitChance, maxHit);
        }
        return sum;
    }

    /** Total average damage per attack against a target that CLAMPS each hitsplat — same cap applies to every cascade hit. */
    static double cappedAverageDamage(double hitChance, int firstMaxHit, int hits, int cap) {
        double sum = 0.0;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            sum += DamageDistribution.cappedAverageDamage(hitChance, maxHit, cap);
        }
        return sum;
    }

    /** Total average damage per attack against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledAverageDamage(double hitChance, int firstMaxHit, int hits, int cap) {
        double sum = 0.0;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            sum += DamageDistribution.rerolledAverageDamage(hitChance, maxHit, cap);
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
            double[] perHit = DamageDistribution.perHitDistribution(hitChance, maxHit);
            combined = combined == null ? perHit : DamageDistribution.convolve(combined, perHit);
        }
        return DamageDistribution.overkillFromExplicitDistribution(combined, targetHitpoints);
    }

    /** Expected overkill for one full cascade against a target that CLAMPS each hitsplat (same cap for every cascade hit). */
    static double cappedExpectedOverkill(double hitChance, int firstMaxHit, int hits, int cap, int targetHitpoints) {
        if (firstMaxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] combined = null;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            double[] perHit = DamageDistribution.cappedPerHitDistribution(hitChance, maxHit, cap);
            combined = combined == null ? perHit : DamageDistribution.convolve(combined, perHit);
        }
        return DamageDistribution.overkillFromExplicitDistribution(combined, targetHitpoints);
    }

    /** Expected overkill for one full cascade against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledExpectedOverkill(double hitChance, int firstMaxHit, int hits, int cap, int targetHitpoints) {
        if (firstMaxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] combined = null;
        for (int maxHit : cascadeMaxHits(firstMaxHit, hits)) {
            double[] perHit = DamageDistribution.rerolledPerHitDistribution(hitChance, maxHit, cap);
            combined = combined == null ? perHit : DamageDistribution.convolve(combined, perHit);
        }
        return DamageDistribution.overkillFromExplicitDistribution(combined, targetHitpoints);
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
            avgDamage = averageDamage(hitChance, uncappedMaxHit, hits);
            overkill = expectedOverkill(hitChance, uncappedMaxHit, hits, targetHitpoints);
        } else if (mode == MonsterCombatRequirement.CapMode.REROLL) {
            avgDamage = rerolledAverageDamage(hitChance, uncappedMaxHit, hits, cap);
            overkill = rerolledExpectedOverkill(hitChance, uncappedMaxHit, hits, cap, targetHitpoints);
        } else {
            avgDamage = cappedAverageDamage(hitChance, uncappedMaxHit, hits, cap);
            overkill = cappedExpectedOverkill(hitChance, uncappedMaxHit, hits, cap, targetHitpoints);
        }
        return DpsCalculator.result(visibleMaxHit, hitChance, avgDamage, overkill, weaponSpeedTicks, targetHitpoints, false);
    }
}
