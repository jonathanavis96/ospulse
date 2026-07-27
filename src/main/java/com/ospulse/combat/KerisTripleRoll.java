package com.ospulse.combat;

/**
 * The Keris partisan family's 1/51 "puncturing" triple-damage roll (see
 * {@link KerisPartisan} for the family's OTHER, plain-multiplier passive):
 * per the OSRS Wiki, a landed hit has a 1/51 chance to deal triple the
 * rolled damage. Extracted into its own small, pure, dependency-light class
 * (the {@code TonalzticsDualHit}/{@code ScytheCascade} precedent) since this
 * is a genuinely different DISTRIBUTION from a plain roll — the roll is
 * tripled AFTER whatever value it would otherwise have displayed, so it
 * cannot be expressed as a multiplier on max hit — and therefore bypasses
 * the generic {@code finish()} the same way {@code finishFang} does.
 *
 * <p><b>The average is always exactly {@code baseAverage * 53/51}</b> —
 * {@code E[displayed'] = (50/51)*E[displayed] + (1/51)*E[3*displayed] =
 * E[displayed] * (50 + 3)/51}, a pure linear scaling that holds regardless
 * of what "displayed" distribution feeds it (uncapped, CLAMP, or REROLL —
 * see {@link #averageDamagePerAttack}). This mirrors why {@code
 * TonalzticsDualHit}'s combined average is "just" a doubling: the SHAPE of
 * the underlying distribution is irrelevant to a linear rescaling of its
 * mean, only to its variance/overkill.
 *
 * <p><b>Overkill needs the full mixture distribution</b> (unlike the
 * average, {@code floor}/clamp/re-roll boundaries interact with which raw
 * value gets tripled, so this builds the explicit {@code 0..3*range}
 * mixture — 50/51 of the ordinary displayed-damage mass kept as-is, 1/51
 * of it moved to triple its own value — then hands it to {@link
 * DamageDistribution#overkillFromExplicitDistribution(double[], int[], int)}.
 *
 * <p><b>Disclosed modelling choice for a capped target</b> (no currently
 * curated Kalphite/Scarabite monster has a damage cap, so this path is
 * untested against real data): the cap is modelled as applying to the
 * PRE-triple roll — i.e. the weapon's own passive triples whatever value
 * the roll would otherwise display, INCLUDING past the cap — rather than
 * the monster's cap re-applying a second time after tripling. If a future
 * curated monster is both Kalphite-tagged and damage-capped, this
 * assumption should be revisited against the real in-game behaviour.
 */
final class KerisTripleRoll {
    private KerisTripleRoll() {
    }

    /** The 1/51 chance of a triple, expressed as a {@link Fraction}-shaped constant pair used throughout this class. */
    private static final double TRIPLE_CHANCE = 1.0 / 51.0;
    private static final double PLAIN_CHANCE = 50.0 / 51.0;

    /** Exact average-damage scaling factor: {@code 50/51 + 3/51 = 53/51}. */
    static double averageDamagePerAttack(double baseAverage) {
        return baseAverage * (PLAIN_CHANCE + 3.0 * TRIPLE_CHANCE);
    }

    /** Uncapped expected overkill, including the 1/51 triple-damage mixture. */
    static double expectedOverkill(double hitChance, int maxHit, int targetHitpoints) {
        if (maxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] displayed = uncappedPerHitDistribution(hitChance, maxHit);
        return overkillFromMixture(displayed, targetHitpoints);
    }

    /** Expected overkill against a target that CLAMPS each hitsplat, including the triple-damage mixture. */
    static double cappedExpectedOverkill(double hitChance, int uncappedMaxHit, int cap, int targetHitpoints) {
        if (targetHitpoints <= 0) {
            return 0.0;
        }
        double[] displayed = cappedPerHitDistribution(hitChance, uncappedMaxHit, cap);
        return overkillFromMixture(displayed, targetHitpoints);
    }

    /** Expected overkill against a target that RE-ROLLS each hitsplat above a cap, including the triple-damage mixture. */
    static double rerolledExpectedOverkill(double hitChance, int uncappedMaxHit, int cap, int targetHitpoints) {
        if (targetHitpoints <= 0) {
            return 0.0;
        }
        double[] displayed = rerolledPerHitDistribution(hitChance, uncappedMaxHit, cap);
        return overkillFromMixture(displayed, targetHitpoints);
    }

    /** One hit's outcome distribution (index 0 = miss, summing to 1): miss, or the ordinary bumped-uniform {@code 0..maxHit} roll. */
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

    private static double[] cappedPerHitDistribution(double hitChance, int maxHit, int cap) {
        if (maxHit <= 0 || cap >= maxHit) {
            return uncappedPerHitDistribution(hitChance, maxHit);
        }
        if (cap <= 0) {
            double[] p = new double[1];
            p[0] = 1.0;
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

    private static double[] rerolledPerHitDistribution(double hitChance, int maxHit, int cap) {
        if (maxHit <= 0 || cap >= maxHit) {
            return uncappedPerHitDistribution(hitChance, maxHit);
        }
        if (cap <= 0) {
            double[] p = new double[1];
            p[0] = 1.0;
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

    /**
     * Mixes a "displayed damage" distribution with its own 1/51-triggered
     * tripled self: {@code q[v] += 50/51 * displayed[v]} (kept as-is) and
     * {@code q[3*v] += 1/51 * displayed[v]} (tripled), for every {@code v}
     * (including {@code v = 0}, whose triple is still 0 — a miss is
     * unaffected by the puncture roll either way).
     */
    private static double[] tripleMixture(double[] displayed) {
        int maxDisplayed = displayed.length - 1;
        double[] q = new double[3 * maxDisplayed + 1];
        for (int v = 0; v <= maxDisplayed; v++) {
            if (displayed[v] == 0.0) {
                continue;
            }
            q[v] += PLAIN_CHANCE * displayed[v];
            q[3 * v] += TRIPLE_CHANCE * displayed[v];
        }
        return q;
    }

    private static double overkillFromMixture(double[] displayed, int targetHitpoints) {
        double[] mixture = tripleMixture(displayed);
        int[] identity = new int[mixture.length];
        for (int v = 0; v < mixture.length; v++) {
            identity[v] = v;
        }
        return DamageDistribution.overkillFromExplicitDistribution(mixture, identity, targetHitpoints);
    }

    /**
     * Assembles the full {@link DpsResult} for a Keris-partisan-vs-Kalphite
     * attack, mirroring {@link TonalzticsDualHit#finish}/{@link
     * ScytheCascade#finish}'s shape. {@code uncappedMaxHit}/{@code
     * attackRoll} must already include the family's own +33% (and, for "of
     * breaching", +33% accuracy) multiplicative step — this class only
     * models the triple-damage mixture on top of whatever roll it is given.
     */
    static DpsResult finish(int uncappedMaxHit, int cap, MonsterCombatRequirement.CapMode mode,
                             int attackRoll, int defenceRoll, int weaponSpeedTicks, int targetHitpoints) {
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        boolean capped = cap >= 0 && cap < uncappedMaxHit;
        int visibleMaxHit = capped ? Math.min(uncappedMaxHit, cap) : uncappedMaxHit;
        double baseAverage;
        double overkill;
        if (!capped) {
            baseAverage = DamageDistribution.averageDamagePerAttack(hitChance, uncappedMaxHit);
            overkill = expectedOverkill(hitChance, uncappedMaxHit, targetHitpoints);
        } else if (mode == MonsterCombatRequirement.CapMode.REROLL) {
            baseAverage = DamageDistribution.rerolledAverageDamagePerAttack(hitChance, uncappedMaxHit, cap);
            overkill = rerolledExpectedOverkill(hitChance, uncappedMaxHit, cap, targetHitpoints);
        } else {
            baseAverage = DamageDistribution.cappedAverageDamagePerAttack(hitChance, uncappedMaxHit, cap);
            overkill = cappedExpectedOverkill(hitChance, uncappedMaxHit, cap, targetHitpoints);
        }
        double avgDamage = averageDamagePerAttack(baseAverage);
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        // Deliberately returning visibleMaxHit (NOT 3x it) even though the 1/51
        // puncture roll can triple the landed damage: DpsResult#maxHit()'s
        // contract is the standard/displayed max hit, matching the wiki and
        // other calculators, not the true theoretical maximum with a rare proc
        // included - see that javadoc for the full rationale. The proc is
        // still fully reflected in dps/avgDamage/ttkSeconds/overkill above.
        return new DpsResult(visibleMaxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, false);
    }
}
