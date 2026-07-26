package com.ospulse.combat;

/**
 * The Twinflame staff's (item id 30634 — see {@code GearVariants}) second-hit
 * passive, extracted into its own small, pure, dependency-light class (per
 * the existing precedent of {@code DemonbaneWeapon}/{@code
 * DragonHunterWeapon}/{@code Tome}/{@code VoidSet}) rather than folded into
 * {@code CombatMath}, so this narrow mechanic can be unit-tested in
 * isolation and extracted further without touching that already-large,
 * concurrently-edited file.
 *
 * <p>Per the OSRS Wiki: "the staff fires two spells at once ... when casting
 * elemental spells (excluding Strike and Surge spells)" (gated by {@link
 * Spell#twinflameEligible()}, which lives on {@link Spell} since it is
 * genuinely spell metadata) and "the second hit will land one tick after the
 * first, dealing 40% of the damage dealt by the first, rounded down." The
 * second spell does not consume runes/charges or grant XP — irrelevant here,
 * since this calculator only models damage/DPS.
 *
 * <p><b>The second hit is NOT an independent roll or accuracy check.</b> Per
 * the OSRS Wiki's own DPS calculator (weirdgloop/osrs-dps-calc,
 * {@code PlayerVsNPCCalc.ts}), the second hitsplat is derived from the FIRST
 * hit's actual rolled damage {@code d} as {@code floor(0.4 * d)} — a missed
 * first hit yields a zero second hit, and a landed first hit always produces
 * this second hit alongside it (both hitsplats share the same landed-hit
 * probability, {@code hitChance}).
 *
 * <p><b>Why the floor cannot be pulled out of the expectation:</b> {@code
 * floor} is not a linear operator, so {@code E[floor(0.4*D)] != 0.4*E[D]} in
 * general — e.g. rolls of 1, 2, 3 and 4 all floor {@code 0.4*d} to 0, 0, 1,
 * and 1 respectively, not a smooth 0.4x scaling. The only correct way to get
 * the exact expectation is to sum {@code floor(0.4*d) * P(d)} over the same
 * discrete per-attack damage distribution {@code DamageDistribution
 * .averageDamagePerAttack} uses: a landed hit rolls uniform {@code 0..maxHit}
 * with a rolled 0 bumped to 1 (so {@code P(1) = 2/(maxHit+1)}, {@code P(d) =
 * 1/(maxHit+1)} for {@code d} in {@code 2..maxHit}). A short direct
 * enumeration over that range is exact and fast (magic max hits are small),
 * so that is what every method below does — no closed form is derived.
 */
final class TwinflameSecondHit {
    private TwinflameSecondHit() {
    }

    /**
     * Exact expected second-hit damage per attack (misses included), for an
     * uncapped target: {@code hitChance * sum_{d=1..maxHit} P(d) * floor(0.4*d)}.
     * {@code floor(0.4*d)} is computed as the exact integer division
     * {@code (2*d)/5} (Java truncates toward zero, identical to floor for
     * non-negative {@code d}) to avoid any floating-point rounding on the
     * 0.4 literal.
     */
    static double secondHitAverage(double hitChance, int maxHit) {
        if (maxHit <= 0) {
            return 0.0;
        }
        double denom = maxHit + 1.0;
        double sum = 0.0;
        for (int d = 1; d <= maxHit; d++) {
            double p = (d == 1 ? 2.0 : 1.0) / denom;
            sum += p * ((2 * d) / 5);
        }
        return hitChance * sum;
    }

    /**
     * Exact expected second-hit damage per attack when the target caps each
     * hitsplat (e.g. The Hueycoatl's tail — see {@code TargetDamageRule}).
     *
     * <p>The wiki's "40% of the damage DEALT by the first hit" is the
     * first hit's DISPLAYED (i.e. already-capped) value, so this enumerates
     * the same raw roll {@code d} over {@code 0..uncappedMaxHit} as {@link
     * #secondHitAverage}, clamps each {@code d} to the cap to get the
     * visible first-hit damage, then floors 40% of THAT. The second hit's own
     * value is never itself reduced further by the cap: it is at most {@code
     * floor(0.4*cap)}, strictly below {@code cap} for any {@code cap > 0},
     * so it can never exceed the per-hitsplat ceiling on its own.
     */
    static double cappedSecondHitAverage(double hitChance, int uncappedMaxHit, int cap) {
        if (cap >= uncappedMaxHit) {
            return secondHitAverage(hitChance, uncappedMaxHit);
        }
        if (cap <= 0 || uncappedMaxHit <= 0) {
            return 0.0;
        }
        double denom = uncappedMaxHit + 1.0;
        double sum = 0.0;
        for (int d = 1; d <= uncappedMaxHit; d++) {
            double p = (d == 1 ? 2.0 : 1.0) / denom;
            int visibleFirst = Math.min(d, cap);
            sum += p * ((2 * visibleFirst) / 5);
        }
        return hitChance * sum;
    }

    /**
     * Exact expected second-hit damage per attack when a monster RE-ROLLS
     * each hitsplat above a cap back into {@code 0..cap} (e.g. Verzik Vitur
     * phase 1), as opposed to {@link #cappedSecondHitAverage}'s clamp.
     *
     * <p>The 40%-of-first-hit rule reads off the DISPLAYED first hit, i.e.
     * the value AFTER any re-roll — so this sums {@code floor(0.4*v) * P(v)}
     * over {@link DamageDistribution#rerolledHitsplatDistribution}'s {@code 0..cap}
     * array (the same one {@link DamageDistribution#rerolledExpectedOverkill} uses),
     * NOT over the raw {@code 0..uncappedMaxHit} roll the uncapped {@link
     * #secondHitAverage} enumerates. Using the raw roll here would be the
     * exact "REROLL is just a clamp with extra steps" mistake this whole
     * feature keeps having to guard against elsewhere.
     *
     * <p>{@code cap >= uncappedMaxHit}: nothing is ever re-rolled, so this
     * delegates to {@link #secondHitAverage} against the true max hit.
     * {@code cap <= 0}: every outcome (including the bumped 1) re-rolls into
     * the single value {@code {0}}, so the second hit is always {@code 0} —
     * matching {@link DamageDistribution#rerolledAverageDamagePerAttack}'s own
     * {@code cap <= 0} guard.
     */
    static double rerolledSecondHitAverage(double hitChance, int uncappedMaxHit, int cap) {
        if (cap >= uncappedMaxHit) {
            return secondHitAverage(hitChance, uncappedMaxHit);
        }
        if (cap <= 0) {
            return 0.0;
        }
        double[] p = DamageDistribution.rerolledHitsplatDistribution(uncappedMaxHit, cap);
        double sum = 0.0;
        for (int v = 0; v <= cap; v++) {
            sum += p[v] * ((2 * v) / 5);
        }
        return hitChance * sum;
    }

    /**
     * Expected overkill (damage wasted on the killing blow) for a Twinflame
     * attack, uncapped target.
     *
     * <p><b>Modelling choice (stated explicitly, not left implicit):</b> the
     * second hit lands only one tick after the first, and this calculator
     * only ever models a single continuous attacking source with nothing
     * else able to act on the target in that one-tick gap — so, for the
     * purpose of tracking the target's remaining hitpoints between attack
     * cycles, the first and second hitsplat are treated as landing TOGETHER:
     * each attack cycle removes {@code d + floor(0.4*d)} hitpoints (for a
     * landed roll {@code d}), or 0 on a miss. This is the same discrete
     * dynamic-programme recurrence {@code DamageDistribution.expectedOverkill} uses,
     * generalised so a single successful attack can remove more than {@code
     * maxHit} hitpoints (up to {@code maxHit + floor(0.4*maxHit)}).
     *
     * <p><b>Known simplification (documented, not silent):</b> if the first
     * hitsplat alone is already lethal, the real game likely does not apply
     * the second hitsplat's damage against an already-dead target (or its
     * damage number becomes moot), whereas this combined model still counts
     * the full {@code d + floor(0.4*d)} toward that kill's overkill. This
     * only affects the literal last attack of a kill — a single hit's worth
     * of a much longer kill sequence — so its effect on the reported
     * overkill/TTK is negligible; an exact treatment of that boundary case is
     * out of scope here, matching how {@code DpsCalculator.finishFang}
     * documents its own uncapped-overkill approximation.
     */
    static double combinedExpectedOverkill(int maxHit, int targetHitpoints) {
        if (maxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d <= maxHit; d++) {
                double p = (d == 1 ? 2.0 : 1.0) / (maxHit + 1.0);
                int combined = d + ((2 * d) / 5);
                sum += p * (combined >= h ? (combined - h) : over[h - combined]);
            }
            over[h] = sum;
        }
        return over[targetHitpoints];
    }

    /**
     * Expected overkill for a Twinflame attack against a target that caps
     * each hitsplat — same combined-hitsplat model as {@link
     * #combinedExpectedOverkill}, but the first hit (and therefore the
     * second, derived from it) is clamped to {@code cap} before combining,
     * matching {@link #cappedSecondHitAverage}.
     */
    static double cappedCombinedExpectedOverkill(int uncappedMaxHit, int cap, int targetHitpoints) {
        if (cap >= uncappedMaxHit) {
            return combinedExpectedOverkill(uncappedMaxHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0 || uncappedMaxHit <= 0) {
            return 0.0;
        }
        double denom = uncappedMaxHit + 1.0;
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d <= uncappedMaxHit; d++) {
                double p = (d == 1 ? 2.0 : 1.0) / denom;
                int visibleFirst = Math.min(d, cap);
                int combined = visibleFirst + ((2 * visibleFirst) / 5);
                sum += p * (combined >= h ? (combined - h) : over[h - combined]);
            }
            over[h] = sum;
        }
        return over[targetHitpoints];
    }

    /**
     * Expected overkill for a Twinflame attack against a target that
     * RE-ROLLS each hitsplat above a cap back into {@code 0..cap} — same
     * combined-hitsplat model as {@link #combinedExpectedOverkill}/{@link
     * #cappedCombinedExpectedOverkill}, but over {@link
     * DamageDistribution#rerolledHitsplatDistribution}'s zero-aware {@code 0..cap}
     * array (the one {@link #rerolledSecondHitAverage} and {@link
     * DamageDistribution#rerolledExpectedOverkill} also consume), rather than a raw
     * enumeration of the {@code 0..uncappedMaxHit} roll.
     *
     * <p><b>The zero-mass trap:</b> under REROLL, {@code P(0)} is genuinely
     * positive (a re-rolled zero), and {@code combined(0) = 0}, so the naive
     * {@code over[h] = sum_v P(v) * (...)} recurrence used by {@link
     * #combinedExpectedOverkill}/{@link #cappedCombinedExpectedOverkill}
     * would put {@code over[h]} on its own right-hand side via the
     * {@code v = 0} term ({@code over[h - 0]}). This delegates to {@link
     * DamageDistribution#overkillFromExplicitDistribution(double[], int[], int)}
     * instead, which conditions on a state-changing (non-zero-value)
     * outcome exactly as {@link DamageDistribution#rerolledExpectedOverkill} does —
     * the two must not, and here cannot, disagree on that convention, since
     * both ultimately call the same method.
     *
     * <p>{@code cap >= uncappedMaxHit}: nothing is ever re-rolled, so this
     * delegates to {@link #combinedExpectedOverkill} against the true max
     * hit. {@code cap <= 0}: every outcome re-rolls into the single value
     * {@code {0}}, so the combined hitsplat is always {@code 0} and there is
     * never any overkill to speak of.
     */
    static double rerolledCombinedExpectedOverkill(int uncappedMaxHit, int cap, int targetHitpoints) {
        if (cap >= uncappedMaxHit) {
            return combinedExpectedOverkill(uncappedMaxHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0 || uncappedMaxHit <= 0) {
            return 0.0;
        }
        double[] p = DamageDistribution.rerolledHitsplatDistribution(uncappedMaxHit, cap);
        int[] combined = new int[cap + 1];
        for (int v = 0; v <= cap; v++) {
            combined[v] = v + ((2 * v) / 5);
        }
        return DamageDistribution.overkillFromExplicitDistribution(p, combined, targetHitpoints);
    }
}
