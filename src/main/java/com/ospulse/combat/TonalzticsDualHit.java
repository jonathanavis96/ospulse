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
 * <p><b>Each hit rolls 0-75% of the calculated weapon max hit, NOT 0-100%
 * and NOT 0-50%.</b> Verified directly from the OSRS Wiki's "Multi-hit
 * weapons" comparison table (2026-07-27,
 * {@code api.php?action=parse&page=Multi-hit_weapons&prop=wikitext}):
 * <pre>
 * Tonalztics of ralos —
 *   "When charged with Sunfire splinters, the weapon will hit twice.
 *    Each hit deals 0-75% of total weapon damage.
 *    Each hit rolls for accuracy independently."
 * </pre>
 * corroborated by the weapon's own page, which states the SAME 75% figure
 * for the uncharged single hit ("Uncharged, the weapon hits a target once
 * for 0-75% of the player's maximum ranged hit") — the charged, two-hit
 * case simply applies that identical per-hit range twice, independently.
 * This was mis-modelled in BOTH directions before this fix was verified: an
 * earlier version of this class used the full, un-reduced 0-100% range per
 * hit (roughly doubling the weapon's true DPS), and a review round
 * proposed halving it to 0-50% (reading "two independent damage rolls" as
 * implying an even split, which the wiki does not say for THIS weapon —
 * contrast {@link ScytheCascade}/the sword-line weapons on the same
 * comparison table, which explicitly DO split 50/50). Neither guess is the
 * source's own number.
 *
 * <p><b>Rounding: the reduced per-hit max floors</b> ({@code
 * floor(weaponMaxHit * 3/4)}, computed as exact integer division — 75% is
 * an exact fraction, so there is no fractional-percent rounding ambiguity
 * the way there can be for e.g. Twinflame's 40%). The source text does not
 * spell out the rounding direction explicitly, so this follows the same
 * convention every other percentage-of-max-hit sub-value in this codebase
 * already floors under (the Scythe cascade's {@code M/2}/{@code M/4}, the
 * elemental-weakness bonus, the charged-tome +10%, every {@link Fraction}
 * application) — OSRS server-side damage calculations floor, never round,
 * at each such step.
 *
 * <p>Per the OSRS Wiki: "Each hit rolls for accuracy independently" — both
 * hits share the SAME hit chance (the normal accuracy formula is unaffected
 * by this passive) but each makes its own independent Bernoulli trial, and
 * each draws its OWN independent uniform roll over {@code 0..perHitMaxHit}
 * — unlike {@code TwinflameSecondHit} (whose second hitsplat is a fixed 40%
 * of the FIRST hit's own rolled value) or the decaying {@link
 * ScytheCascade} (whose later hits use a progressively smaller max hit),
 * here BOTH hits are drawn from the exact same, identically-reduced
 * distribution, fully independently of one another.
 *
 * <p><b>Average damage is a trivial doubling of the REDUCED per-hit
 * average</b> — {@code E[hit1 + hit2] = E[hit1] + E[hit2]} by linearity of
 * expectation, true regardless of any correlation between the two rolls,
 * so the existing single-hit average formulas ({@link
 * DamageDistribution#averageDamage}/{@code
 * cappedAverageDamage}/{@code rerolledAverageDamage}) are
 * reused unchanged — fed {@link #perHitMaxHit}, not the raw calculated
 * weapon max hit — and simply doubled.
 *
 * <p><b>Overkill is NOT a trivial doubling</b> — the target's remaining HP
 * depletes by the SUM of both hits within one attack cycle (they land the
 * same tick per the wiki's "attack range" wording, with nothing else able to
 * act on the target in between, the same one-continuous-source assumption
 * {@code TwinflameSecondHit} documents), so the overkill DP needs the joint
 * distribution of {@code hit1 + hit2} over the REDUCED per-hit range, not
 * twice the single-hit overkill and not a distribution built from the full
 * weapon max hit. Both hits share one convolution helper ({@link
 * #convolveAndOverkill}) fed a single-hit distribution ({@link
 * #uncappedPerHitDistribution} or its capped/re-rolled equivalents) built
 * from the SAME single-hit distribution the average functions consume (a
 * miss contributes a real, explicit {@code 0}, a landed hit contributes the
 * ordinary bumped-uniform-or-capped-or-rerolled pmf over {@code
 * 0..perHitMaxHit}), convolved with itself, then handed to {@link
 * DamageDistribution#overkillFromExplicitDistribution(double[], int[], int)}
 * — which already conditions on a non-zero result via its {@code 1 - p[0]}
 * renormalisation, so a "both hits missed" cycle (a real, positive-probability
 * outcome here, unlike the single-hit models where a miss is simply excluded
 * from the distribution entirely) correctly contributes nothing, exactly like
 * an ordinary miss.
 *
 * <p><b>A per-target damage cap applies to the REDUCED per-hit range, not
 * the raw calculated weapon max hit</b> — the same precedent {@code
 * DamageDistribution#cappedFangAverageDamage}/{@code
 * rerolledFangAverageDamage} set for Osmumten's fang's own
 * compressed roll: the weapon's own passive establishes what its TRUE
 * per-hit roll range is first, and only then does a monster's cap (defined
 * in absolute hitpoint terms) clamp or re-roll that already-reduced range.
 * {@link #finish} computes {@link #perHitMaxHit} from the calculated weapon
 * max hit BEFORE deciding whether the cap even binds, so a cap that sits
 * between 75% and 100% of the calculated max hit (which would bind against
 * the old, un-reduced model) correctly never binds here.
 */
final class TonalzticsDualHit {
    private TonalzticsDualHit() {
    }

    /**
     * Each hit's true max hit: 75% of the calculated weapon max hit,
     * floored — see the class javadoc for the citation and rounding
     * rationale. Exact integer arithmetic ({@code weaponMaxHit * 3 / 4}),
     * matching how {@link Fraction} applies every other exact-percentage
     * bonus in this codebase.
     */
    static int perHitMaxHit(int weaponMaxHit) {
        if (weaponMaxHit <= 0) {
            return 0;
        }
        return (int) Math.floorDiv((long) weaponMaxHit * 3, 4);
    }

    /**
     * Uncapped combined average damage per attack cycle (both hits, each over
     * the reduced 75% range) — reduces {@code weaponMaxHit} via {@link
     * #perHitMaxHit} itself. Callers that have ALREADY reduced their max hit
     * (see {@link #finishFromPerHit}'s javadoc for why that split exists) must
     * use {@link #combinedAverageDamageFromPerHit} instead, or the
     * 75% reduction is silently applied twice.
     */
    static double combinedAverageDamage(double hitChance, int weaponMaxHit) {
        return combinedAverageDamageFromPerHit(hitChance, perHitMaxHit(weaponMaxHit));
    }

    /** As {@link #combinedAverageDamage}, but {@code perHit} is already the reduced 75% value. */
    private static double combinedAverageDamageFromPerHit(double hitChance, int perHit) {
        return 2.0 * DamageDistribution.averageDamage(hitChance, perHit);
    }

    /** Combined average damage per attack cycle against a target that CLAMPS each hitsplat. */
    static double cappedCombinedAverageDamage(double hitChance, int uncappedWeaponMaxHit, int cap) {
        return cappedCombinedAverageDamageFromPerHit(hitChance, perHitMaxHit(uncappedWeaponMaxHit), cap);
    }

    /** As {@link #cappedCombinedAverageDamage}, but {@code perHit} is already the reduced 75% value. */
    private static double cappedCombinedAverageDamageFromPerHit(double hitChance, int perHit, int cap) {
        return 2.0 * DamageDistribution.cappedAverageDamage(hitChance, perHit, cap);
    }

    /** Combined average damage per attack cycle against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledCombinedAverageDamage(double hitChance, int uncappedWeaponMaxHit, int cap) {
        return rerolledCombinedAverageDamageFromPerHit(hitChance, perHitMaxHit(uncappedWeaponMaxHit), cap);
    }

    /** As {@link #rerolledCombinedAverageDamage}, but {@code perHit} is already the reduced 75% value. */
    private static double rerolledCombinedAverageDamageFromPerHit(double hitChance, int perHit, int cap) {
        return 2.0 * DamageDistribution.rerolledAverageDamage(hitChance, perHit, cap);
    }

    /** Uncapped combined expected overkill for one attack cycle (both hits together, each over the reduced 75% range). */
    static double combinedExpectedOverkill(double hitChance, int weaponMaxHit, int targetHitpoints) {
        return combinedExpectedOverkillFromPerHit(hitChance, perHitMaxHit(weaponMaxHit), targetHitpoints);
    }

    /** As {@link #combinedExpectedOverkill}, but {@code perHit} is already the reduced 75% value. */
    private static double combinedExpectedOverkillFromPerHit(double hitChance, int perHit, int targetHitpoints) {
        if (perHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] perHitDist = uncappedPerHitDistribution(hitChance, perHit);
        return convolveAndOverkill(perHitDist, targetHitpoints);
    }

    /** Combined expected overkill for one attack cycle against a target that CLAMPS each hitsplat. */
    static double cappedCombinedExpectedOverkill(double hitChance, int uncappedWeaponMaxHit, int cap, int targetHitpoints) {
        return cappedCombinedExpectedOverkillFromPerHit(hitChance, perHitMaxHit(uncappedWeaponMaxHit), cap, targetHitpoints);
    }

    /** As {@link #cappedCombinedExpectedOverkill}, but {@code perHit} is already the reduced 75% value. */
    private static double cappedCombinedExpectedOverkillFromPerHit(double hitChance, int perHit, int cap, int targetHitpoints) {
        if (cap >= perHit) {
            return combinedExpectedOverkillFromPerHit(hitChance, perHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0 || perHit <= 0) {
            return 0.0;
        }
        double[] capped = DamageDistribution.cappedHitsplatDistribution(perHit, cap);
        double[] perHitDist = new double[cap + 1];
        perHitDist[0] = 1.0 - hitChance;
        for (int d = 1; d <= cap; d++) {
            perHitDist[d] = hitChance * capped[d];
        }
        return convolveAndOverkill(perHitDist, targetHitpoints);
    }

    /** Combined expected overkill for one attack cycle against a target that RE-ROLLS each hitsplat above a cap. */
    static double rerolledCombinedExpectedOverkill(double hitChance, int uncappedWeaponMaxHit, int cap, int targetHitpoints) {
        return rerolledCombinedExpectedOverkillFromPerHit(hitChance, perHitMaxHit(uncappedWeaponMaxHit), cap, targetHitpoints);
    }

    /** As {@link #rerolledCombinedExpectedOverkill}, but {@code perHit} is already the reduced 75% value. */
    private static double rerolledCombinedExpectedOverkillFromPerHit(double hitChance, int perHit, int cap, int targetHitpoints) {
        if (cap >= perHit) {
            return combinedExpectedOverkillFromPerHit(hitChance, perHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0 || perHit <= 0) {
            return 0.0;
        }
        double[] rerolled = DamageDistribution.rerolledHitsplatDistribution(perHit, cap);
        double[] perHitDist = new double[cap + 1];
        perHitDist[0] = (1.0 - hitChance) + hitChance * rerolled[0];
        for (int d = 1; d <= cap; d++) {
            perHitDist[d] = hitChance * rerolled[d];
        }
        return convolveAndOverkill(perHitDist, targetHitpoints);
    }

    /**
     * One hit's full outcome distribution INCLUDING the miss probability at
     * index 0 (unlike {@link DamageDistribution}'s "landed only" arrays,
     * which sum to 1 over a genuine hit) — a miss (probability
     * {@code 1 - hitChance}) contributes 0, and a landed hit
     * (probability {@code hitChance}) contributes the ordinary
     * "rolled 0 becomes 1" bumped-uniform pmf over {@code 1..maxHit} (here,
     * {@code maxHit} is already the REDUCED {@link #perHitMaxHit}, not the
     * raw calculated weapon max hit).
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
     * <p>{@code maxHit} in the returned result is the REDUCED per-hit
     * visible max (75% of the calculated weapon max hit, floored, and
     * capped if applicable) — the largest a single hitsplat can actually
     * show — not the two-hit total and not the un-reduced calculated max
     * hit.
     *
     * <p><b>This overload assumes {@code uncappedWeaponMaxHit} carries NO
     * target-specific damage penalty yet</b> (e.g. Corporeal Beast's 0.5
     * multiplier) — it derives {@link #perHitMaxHit} straight from the value
     * given. A caller that must apply BOTH the weapon's own 75% reduction AND
     * a target damage penalty needs {@link #finishFromPerHit} instead — see
     * that method's javadoc for why the two floor steps cannot be combined
     * in either order interchangeably.
     */
    static DpsResult finish(int uncappedWeaponMaxHit, int cap, MonsterCombatRequirement.CapMode mode,
                             int attackRoll, int defenceRoll, int weaponSpeedTicks, int targetHitpoints) {
        return finishFromPerHit(perHitMaxHit(uncappedWeaponMaxHit), cap, mode, attackRoll, defenceRoll,
                weaponSpeedTicks, targetHitpoints);
    }

    /**
     * As {@link #finish}, but for a caller that has ALREADY reduced the max
     * hit to its true per-hit range — {@code perHitMaxHit} here is taken
     * as-is, with no further {@link #perHitMaxHit} call applied to it.
     *
     * <p><b>Why this split exists.</b> {@link DpsCalculator}'s ranged path
     * must combine two independent floor-based reductions for a Tonalztics
     * attack against a target with a {@link
     * MonsterCombatRequirement.Type#DAMAGE_PENALTY} (e.g. Corporeal Beast's
     * 0.5 multiplier without a corpbane weapon): the weapon's OWN 75%
     * per-hit range shaping, and the target's damage-multiplier floor.
     * {@code floor(floor(x * a) * b)} and {@code floor(floor(x * b) * a)} do
     * NOT commute in general, so the ORDER is load-bearing, not a style
     * choice — and per the reference implementation
     * (weirdgloop/osrs-dps-calc's {@code PlayerVsNPCCalc.ts}), the weapon's
     * own hit-distribution transform is built FIRST, with the Corp penalty
     * applied as a further transform over that already-formed distribution
     * afterwards. Concretely, for a calculated max of 35 against Corp:
     * <ul>
     * <li>weapon-first-then-target (correct): {@code floor(floor(35 * 3/4) *
     * 0.5) = floor(26 * 0.5) = 13}</li>
     * <li>target-first-then-weapon (the defect this method exists to
     * prevent from reappearing): {@code floor(floor(35 * 0.5) * 3/4) =
     * floor(17 * 3/4) = 12} — one damage point short on every hit.</li>
     * </ul>
     * {@link DpsCalculator#computeRanged} therefore computes {@link
     * #perHitMaxHit} from the RAW, pre-penalty max hit first, runs the
     * target's damage-penalty/cap rules on THAT already-reduced per-hit
     * value second, and calls this method — never {@link #finish} — with the
     * result. {@link #finish} itself still exists, unchanged, for every
     * caller (including every existing test) that has no target penalty to
     * combine with the weapon's own reduction; it now simply delegates here
     * after computing {@link #perHitMaxHit} itself, so its behaviour is
     * byte-for-byte identical to before this split.
     */
    static DpsResult finishFromPerHit(int perHitMaxHit, int cap, MonsterCombatRequirement.CapMode mode,
                                       int attackRoll, int defenceRoll, int weaponSpeedTicks, int targetHitpoints) {
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        boolean capped = cap >= 0 && cap < perHitMaxHit;
        int visibleMaxHit = capped ? Math.min(perHitMaxHit, cap) : perHitMaxHit;
        double avgDamage;
        double overkill;
        if (!capped) {
            avgDamage = combinedAverageDamageFromPerHit(hitChance, perHitMaxHit);
            overkill = combinedExpectedOverkillFromPerHit(hitChance, perHitMaxHit, targetHitpoints);
        } else if (mode == MonsterCombatRequirement.CapMode.REROLL) {
            avgDamage = rerolledCombinedAverageDamageFromPerHit(hitChance, perHitMaxHit, cap);
            overkill = rerolledCombinedExpectedOverkillFromPerHit(hitChance, perHitMaxHit, cap, targetHitpoints);
        } else {
            avgDamage = cappedCombinedAverageDamageFromPerHit(hitChance, perHitMaxHit, cap);
            overkill = cappedCombinedExpectedOverkillFromPerHit(hitChance, perHitMaxHit, cap, targetHitpoints);
        }
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(visibleMaxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, false);
    }
}
