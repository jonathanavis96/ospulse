package com.ospulse.combat;

/**
 * Bespoke expected-damage-per-use formulas for the curated special attacks in
 * {@link SpecWeapon#CATALOG} whose mechanic is not a single ordinary
 * {@code 0..maxHit} roll — multi-hit cascades (Dragon claws, Dragon dagger,
 * Burning claws) and modifier-only single hits (accuracy/damage % bumps).
 * Each method takes the {@code (hitChance, maxHit)} pair the ordinary
 * {@link DpsCalculator}/{@link CombatMath} pipeline already produces for the
 * candidate weapon's own style against the target (see
 * {@code SpecWeaponSelector.DpsProbe}), and applies the special attack's own
 * mechanic on top — the same "generic accuracy/max-hit in, bespoke formula
 * out" shape {@link DamageDistribution}'s Osmumten's fang methods use, and
 * for the same reason: these mechanics are not expressible as a multiplier on
 * the generic {@link DamageDistribution#averageDamagePerAttack}.
 *
 * <h2>Sourcing — verbatim wikitext, not a paraphrase</h2>
 * Special attack mechanics are not published in any bundled data file, nor by
 * upstream {@code weirdgloop/osrs-dps-calc} (it hand-writes ~30 {@code if}
 * blocks instead) — see {@link SpecWeapon}'s class javadoc. Every mechanic
 * below was fetched as VERBATIM wikitext from the OSRS Wiki's own API
 * ({@code action=parse&prop=wikitext} on {@code oldschool.runescape.wiki}),
 * not a rendered-page paraphrase, and every numeric constant here traces to
 * a specific quoted sentence.
 *
 * <p><b>Genuine residual ambiguity, quoted from the source itself</b> (this
 * is the wiki's own caveat, not this codebase's): the Dragon claws page
 * states "Due to a lack of sources, there may be minor errors in this
 * description" and gives its branch ranges as "roughly"/"about" (e.g. "about
 * 3/8 and 7/8"), sourced from third-party reverse-engineering (a 2015 forum
 * post) rather than an official Jagex formula — a Jagex developer's only
 * on-record comment is a 2016 tweet calling the community's model "about
 * right", not confirming it exactly. This codebase reproduces the wiki's
 * numbers precisely as written; if Jagex's actual implementation differs
 * from the community's reverse-engineered model, this will inherit that
 * difference. Burning claws' page carries no such disclaimer and gives a
 * fully worked rounding example (reproduced in this class's javadoc below),
 * so it does not carry the same caveat.
 */
final class SpecCascadeMath {
    private SpecCascadeMath() {
    }

    /**
     * Dragon claws' "Slice and Dice" special (50% spec energy): four
     * successive attacks rolling against the target's SLASH defence. Per the
     * OSRS Wiki's special-attack section (verbatim, see this class's
     * sourcing note): "Each of the four attacks has the same accuracy as an
     * ordinary attack, until one of the attacks manages to successfully hit.
     * After an attack hits, all remaining attacks will also successfully hit
     * the target." All fractions below are rounded down (the page states
     * this explicitly). Branch names are the wiki's own shorthand:
     * <ul>
     * <li><b>"4-2-1-1"</b> (hit 1 lands): "its max hit will be 1 point less
     * than the max hit of an ordinary attack. The minimum hit will be half
     * of the ordinary max hit." — i.e. hit1 is drawn uniformly from {@code
     * [floor(maxHit/2), maxHit-1]}, NOT the full ordinary {@code 0..maxHit}
     * roll (confirmed against the page's own worked example, see below).
     * "The second hit will deal half of the first hit. The third hit will
     * deal half of the second. The fourth hit will deal 1 more damage than
     * the third hit." — hit2=floor(hit1/2), hit3=floor(hit2/2),
     * hit4=hit3+1 (NOT floor(hit3/2)+1 — the wiki's own example, "a
     * realistic example of a full attack is 35-17-8-9", only reproduces with
     * hit4 = hit3+1: 8+1=9, whereas floor(8/2)+1=5 does not).</li>
     * <li><b>"0-4-2-2"</b> (hit 1 misses, hit 2 lands): "the second hit will
     * deal between about 3/8 and 7/8 of the ordinary maximum hit. The
     * remaining hits will follow the same process as above" — hit2 ~
     * {@code [3*maxHit/8, 7*maxHit/8]}, then hit3=floor(hit2/2), hit4=hit3+1
     * (confirmed against the page's own example "0-30-15-16": 15=floor(30/2),
     * 16=15+1).</li>
     * <li><b>"0-0-3-3"</b> (hits 1-2 miss, hit 3 lands): "the third attack
     * will deal between about 1/4 and 3/4 of the ordinary max hit. The
     * fourth hit still matches the third hit as usual" — hit3 ~
     * {@code [maxHit/4, 3*maxHit/4]}, hit4=hit3+1 ("as usual" = the SAME
     * +1 relationship every branch's final hit carries, confirmed against
     * the page's own example "0-0-22-23": 23=22+1, not 22 itself).</li>
     * <li><b>"0-0-0-5"</b> (hits 1-3 miss, hit 4 lands): "the last hit (if
     * successful) will deal between 1/4 and 5/4 the ordinary max hit" —
     * hit4 ~ {@code [maxHit/4, 5*maxHit/4]} directly (can exceed the
     * ordinary max hit); no chain, it IS the last hit.</li>
     * <li><b>"0-0-0-0"</b> (all four miss): "there is a ~2/3 chance of
     * dealing 2 damage (1-1-0-0, 0-0-1-1, 1-0-1-0, or 0-1-0-1) and a ~1/3
     * chance of hitting 0-0-0-0" — E = 2/3*2 + 1/3*0 = 4/3.</li>
     * </ul>
     * Each branch's expected value is computed by exact enumeration over its
     * (small, integer) range rather than a shortcut algebraic identity, so
     * the arithmetic is easy to audit line-by-line and to cross-check
     * against an independently-written brute-force enumeration in the test
     * suite.
     */
    static double dragonClawsExpectedDamage(double hitChance, int maxHit) {
        if (maxHit <= 0) {
            return 0.0;
        }
        double p = hitChance;
        double branch1 = expectedChainFromRange(scaledBound(maxHit, 1, 2), maxHit - 1, 3);
        double branch2 = expectedChainFromRange(scaledBound(maxHit, 3, 8), scaledBound(maxHit, 7, 8), 2);
        double branch3 = expectedChainFromRange(scaledBound(maxHit, 1, 4), scaledBound(maxHit, 3, 4), 1);
        double branch4 = expectedChainFromRange(scaledBound(maxHit, 1, 4), scaledBound(maxHit, 5, 4), 0);
        double allMissConsolation = (2.0 / 3.0) * 2.0 + (1.0 / 3.0) * 0.0; // ~4/3

        return p * branch1
                + p * (1 - p) * branch2
                + p * (1 - p) * (1 - p) * branch3
                + p * (1 - p) * (1 - p) * (1 - p) * branch4
                + (1 - p) * (1 - p) * (1 - p) * (1 - p) * allMissConsolation;
    }

    /** {@code floor(maxHit * numerator / denominator)} — every fraction on both the claws and burning-claws pages is explicitly rounded down. */
    private static int scaledBound(int maxHit, int numerator, int denominator) {
        return (maxHit * numerator) / denominator;
    }

    /**
     * Expected total when the landing hit is drawn (roughly) uniformly from
     * {@code [lo, hi]} (clamped to a valid, non-empty integer range) and then
     * {@code remainingHits} more hits follow the chain (see {@link
     * #chainTotal}) — {@code remainingHits} is 3 for hit1 landing, 2 for hit2
     * landing, 1 for hit3 landing, 0 for hit4 landing (it IS the last hit,
     * no chain).
     */
    private static double expectedChainFromRange(int lo, int hi, int remainingHits) {
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        int n = hiClamped - loClamped + 1;
        double sum = 0.0;
        for (int v = loClamped; v <= hiClamped; v++) {
            sum += chainTotal(v, remainingHits);
        }
        return sum / n;
    }

    /**
     * {@code landingValue} plus {@code remainingHits} further hits: every
     * hit strictly before the LAST one in the whole 4-hit sequence is {@code
     * floor(prev/2)} ("will deal half of the [previous hit]"); the LAST hit
     * in the sequence is {@code prev+1} ("will deal 1 more damage than the
     * [previous] hit") — NOT a further halving. {@code remainingHits} counts
     * how many MORE hits follow {@code landingValue} (0, 1, 2, or 3 — see
     * {@link #dragonClawsExpectedDamage}'s per-branch worked-example
     * verification of this exact rule).
     */
    private static int chainTotal(int landingValue, int remainingHits) {
        int total = landingValue;
        int prev = landingValue;
        for (int i = 0; i < remainingHits; i++) {
            boolean isLast = i == remainingHits - 1;
            int next = isLast ? prev + 1 : prev / 2;
            total += next;
            prev = next;
        }
        return total;
    }

    /**
     * Dragon dagger's "Puncture" special (25% spec energy): two independent
     * hits, EACH with its own accuracy roll and damage roll, both boosted by
     * a flat +15% (accuracy applied to the hit-chance input directly — an
     * approximation of "boost the attack roll by 15%, then recompute hit
     * chance", which cannot be reproduced exactly from a hit-chance-only
     * input since {@code hitChance} is not linear in the attack roll — this
     * approximation, unlike the cascade formulas above, is NOT resolved by
     * fetching more wiki text; it is a structural limit of this feature's
     * {@code (hitChance, maxHit)}-only input). +15% damage is an exact floor
     * step, matching every other flat-percentage damage bonus in this
     * codebase (e.g. {@link CombatMath#magicPreHitRoll}'s +15% imbued-helm
     * step).
     */
    static double dragonDaggerExpectedDamage(double hitChance, int maxHit) {
        double boostedChance = Math.min(1.0, hitChance * 1.15);
        int boostedMax = (int) Math.floor(maxHit * 1.15);
        double perHit = DamageDistribution.averageDamagePerAttack(boostedChance, boostedMax);
        return 2.0 * perHit;
    }

    /**
     * Burning claws' "Burning barrage" special (35% spec energy): up to
     * three successive attacks rolling against the target's slash or stab
     * defence (whichever the current combat style uses), stopping at the
     * first accuracy success — mirroring Dragon claws' "first success ends
     * the accuracy checks" structure, but over three hits. Per the OSRS
     * Wiki's special-attack section (verbatim, see this class's sourcing
     * note):
     * <ol>
     * <li>Roll 1 lands: "roll total damage between 75% and 175% of the
     * maximum hit. Divide the damage into three hitsplats as 25%-25%-50%."</li>
     * <li>Roll 1 misses, roll 2 lands: "roll total damage between 50% and
     * 150% of the maximum hit. Divide the damage into three hitsplats as
     * 50%-50%-0%, then subtract 1 from the first two hitsplats and add that
     * damage to the third hitsplat."</li>
     * <li>Rolls 1-2 miss, roll 3 lands: "roll total damage between 25% and
     * 125% of the maximum hit. Divide the damage into three hitsplats as
     * 0%-0%-100%, then subtract 2 from the third hitsplat and add 1 to each
     * of the other two hitsplats."</li>
     * <li>All three miss: "20% chance of dealing 0 damage, a 40% chance of
     * dealing 1 damage, and a 40% chance of dealing 2 damage."</li>
     * </ol>
     * <b>The per-hitsplat split is NOT damage-neutral for branch 1 — this is
     * the correction that mattered.</b> "All of the minimum and maximum hit
     * calculations are rounded down, and the individual hitsplats are
     * rounded down as well," and the page gives a worked example proving the
     * total actually DELIVERED can be less than the total ROLLED: "for a
     * normal maximum hit of 39 ... [a roll of] 29 damage ... the hitsplats
     * are calculated as floor(0.25*29), floor(0.25*29), and floor(0.5*29),
     * resulting in a hit of 7-7-14 — a total of 28 actual damage, despite
     * originally rolling for 29 damage." Flooring three separate fractional
     * pieces of R loses more precision than flooring R once, so branch 1's
     * expected delivered damage is computed by summing {@code
     * floor(R/4)+floor(R/4)+floor(R/2)} over every integer R in its rolled
     * range, not by taking the range's raw mean.
     * <p>Branches 2 and 3's "subtract from A, add to B" adjustments net to
     * zero (they move damage between hitsplats, never destroy or create
     * it) — branch 3 has no rounding loss at all (100% of R, R already an
     * integer); branch 2's {@code floor(R/2)+floor(R/2)+0} loses at most 1
     * (when R is odd), confirmed against the page's own examples ("a total
     * roll of 16 becomes 7-7-2", sum 16, no loss since 16 is even; "a total
     * roll of 9 becomes 1-1-7" for branch 3, sum 9, no loss).
     * <p>The burn damage-over-time proc is NOT modelled in this expected-
     * damage-per-use metric — stated precisely, per the same page: each
     * landed hitsplat has an independent chance to inflict a burn (15% on a
     * branch-1 hit, 30% on branch 2, 45% on branch 3) dealing "a total of 10
     * damage over the course of 40 ticks (24 seconds)", stacking up to five
     * concurrent burns ("one spec can inflict 0, 10, 19, 20, or 29 burn
     * damage" depending on how many of the three hitsplats proc and how
     * their windows overlap). This is a delayed, stacking, probabilistic
     * side effect entirely outside a single "expected damage per use of the
     * special" number — the same tier of effect {@link DpsCalculator}'s own
     * javadoc already excludes elsewhere (Avarice, Tumeken's 3x). Excluding
     * it means burning claws' TRUE value is somewhat higher than the number
     * this method returns; this is stated plainly, not hidden.
     */
    static double burningClawsExpectedDamage(double hitChance, int maxHit) {
        if (maxHit <= 0) {
            return 0.0;
        }
        double p = hitChance;
        double e1 = expectedDeliveredOverRange(
                scaledBound(maxHit, 3, 4), scaledBound(maxHit, 7, 4), BurningClawsSplit.QUARTER_QUARTER_HALF);
        double e2 = expectedDeliveredOverRange(
                scaledBound(maxHit, 1, 2), scaledBound(maxHit, 3, 2), BurningClawsSplit.HALF_HALF_ZERO);
        double e3 = expectedDeliveredOverRange(
                scaledBound(maxHit, 1, 4), scaledBound(maxHit, 5, 4), BurningClawsSplit.ZERO_ZERO_WHOLE);
        double allMiss = 0.2 * 0 + 0.4 * 1 + 0.4 * 2; // 1.2

        return p * e1
                + p * (1 - p) * e2
                + p * (1 - p) * (1 - p) * e3
                + (1 - p) * (1 - p) * (1 - p) * allMiss;
    }

    /** Which of Burning barrage's three hitsplat splits applies — see {@link #burningClawsExpectedDamage}. */
    private enum BurningClawsSplit {
        /** Branch 1 (25%-25%-50%): {@code floor(R/4)+floor(R/4)+floor(R/2)} — the ONLY split that loses damage to per-hitsplat flooring. */
        QUARTER_QUARTER_HALF {
            @Override
            int delivered(int r) {
                return (r / 4) + (r / 4) + (r / 2);
            }
        },
        /** Branch 2 (50%-50%-0%, then -1/-1/+2, net zero): {@code floor(R/2)+floor(R/2)+0}. */
        HALF_HALF_ZERO {
            @Override
            int delivered(int r) {
                return (r / 2) + (r / 2);
            }
        },
        /** Branch 3 (0%-0%-100%, then +1/+1/-2, net zero): the whole integer R, no rounding loss. */
        ZERO_ZERO_WHOLE {
            @Override
            int delivered(int r) {
                return r;
            }
        };

        abstract int delivered(int r);
    }

    /** Expected DELIVERED damage (post per-hitsplat flooring — see {@link #burningClawsExpectedDamage}) for a total roll R uniform over {@code [lo, hi]}. */
    private static double expectedDeliveredOverRange(int lo, int hi, BurningClawsSplit split) {
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        long sum = 0;
        for (int r = loClamped; r <= hiClamped; r++) {
            sum += split.delivered(r);
        }
        return (double) sum / (hiClamped - loClamped + 1);
    }

    /**
     * A single hit with a flat accuracy multiplier and flat damage multiplier
     * applied on top of the ordinary {@code (hitChance, maxHit)} pair — the
     * shape shared by Toxic blowpipe (+100% accuracy, +50% damage), Dragon
     * warhammer (+50% damage only), Bandos godsword/Saradomin godsword/Elder
     * maul/Zaryte crossbow (accuracy and/or damage multipliers, no cascade),
     * and Arclight/Emberlight/Abyssal tentacle (no multiplier at all — pass
     * 1.0/1.0 for "just an ordinary hit, the special is the side effect").
     * Accuracy is approximated the same way {@link #dragonDaggerExpectedDamage}
     * is; damage is an exact floor step.
     */
    static double boostedSingleHit(double hitChance, int maxHit, double accuracyMultiplier, double damageMultiplier) {
        double boostedChance = Math.min(1.0, hitChance * accuracyMultiplier);
        int boostedMax = (int) Math.floor(maxHit * damageMultiplier);
        return DamageDistribution.averageDamagePerAttack(boostedChance, boostedMax);
    }

    /**
     * Voidwaker's "Disrupt" special (50% spec energy): a GUARANTEED (no
     * accuracy roll at all) Magic hit of {@code [50%, 150%]} of the wielder's
     * maximum MELEE hit — {@code maxMeleeHit} is the melee max hit computed
     * through the ordinary pipeline for Voidwaker's own (slash) style; the
     * accuracy component of that computation is deliberately unused here
     * since the spec's magic damage is guaranteed regardless of whether an
     * ordinary melee attack with this weapon would have landed.
     */
    static double voidwakerExpectedDamage(int maxMeleeHit) {
        int lo = scaledBound(maxMeleeHit, 1, 2);
        int hi = scaledBound(maxMeleeHit, 3, 2);
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        return (loClamped + hiClamped) / 2.0;
    }
}
