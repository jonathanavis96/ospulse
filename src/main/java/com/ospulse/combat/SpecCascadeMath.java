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
 * <h2>Sourcing and confidence — read before trusting a number</h2>
 * Special attack mechanics are not published in any bundled data file, nor by
 * upstream {@code weirdgloop/osrs-dps-calc} (it hand-writes ~30 {@code if}
 * blocks instead) — see {@link SpecWeapon}'s class javadoc. They were sourced
 * from the OSRS Wiki, but this environment cannot fetch the wiki directly
 * (Cloudflare blocks the raw request from this network), so every mechanic
 * below was reconstructed via an AI-mediated fetch tool that converts the
 * rendered page to a paraphrased summary rather than verbatim wikitext. The
 * clean, unambiguous rules (a fixed hit count, a flat percentage bonus) are
 * high confidence. The dragon-claws-family "damage cascade" ranges quoted
 * with words like "about" or "roughly" in the wiki's OWN prose are
 * reproduced faithfully here, but should be read as MODERATE confidence, not
 * wiki-verified-exact the way {@link CombatMath}'s cited formulas are —
 * spot-check these against the live wiki before relying on the
 * claws/dagger/burning-claws numbers for anything beyond a rough steer. This
 * is the honest state of a heuristic feature that was never going to always
 * match an experienced player's judgement anyway.
 */
final class SpecCascadeMath {
    private SpecCascadeMath() {
    }

    /**
     * Dragon claws' "Slice and Dice" special (50% spec energy): four
     * successive attacks, each rolling for accuracy at the SAME chance as an
     * ordinary attack until one of them lands — after which every remaining
     * attack is guaranteed to land (no further accuracy rolls). Per the OSRS
     * Wiki's special-attack section (see this class's sourcing note):
     * <ul>
     * <li>If hit 1 lands: hit1 is an ordinary {@code 0..maxHit} roll (with the
     * usual "rolled 0 becomes 1" bump); hit2 = floor(hit1/2); hit3 =
     * floor(hit2/2); hit4 = floor(hit3/2) + 1.</li>
     * <li>If hit 1 misses and hit 2 lands: hit2 is drawn (roughly uniformly)
     * from {@code [3*maxHit/8, 7*maxHit/8]}, then the SAME halving chain as
     * above continues from hit2 (hit3 = floor(hit2/2), hit4 = floor(hit3/2)+1).</li>
     * <li>If hits 1-2 miss and hit 3 lands: hit3 ~ {@code [maxHit/4, 3*maxHit/4]},
     * then hit4 = floor(hit3/2) + 1.</li>
     * <li>If hits 1-3 miss and hit 4 lands: hit4 ~ {@code [maxHit/4, 5*maxHit/4]}
     * directly (can exceed the ordinary max hit) — no further chain, it is the
     * last hit.</li>
     * <li>If all four miss: a small flat consolation — about 2/3 chance of 2
     * total damage, about 1/3 chance of 0.</li>
     * </ul>
     * Each branch's expected value is computed by exact enumeration over its
     * (small, integer) range rather than a shortcut algebraic identity, so the
     * arithmetic is easy to audit line-by-line and to cross-check against an
     * independently-written brute-force enumeration in the test suite.
     */
    static double dragonClawsExpectedDamage(double hitChance, int maxHit) {
        if (maxHit <= 0) {
            return 0.0;
        }
        double p = hitChance;
        double branch1 = expectedChainFromOrdinaryRoll(maxHit);
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

    private static int scaledBound(int maxHit, int numerator, int denominator) {
        return (int) Math.round(maxHit * (double) numerator / denominator);
    }

    /**
     * Expected total of the hit1-lands branch: hit1 ranges over the ordinary
     * {@code 1..maxHit} damage-roll distribution (value 1 carries the rolled-
     * 0-becomes-1 weight {@code 2/(maxHit+1)}; values 2..maxHit each carry
     * {@code 1/(maxHit+1)}), and every subsequent hit in the chain is a
     * deterministic function of hit1 (see {@link #chainTotal}).
     */
    private static double expectedChainFromOrdinaryRoll(int maxHit) {
        double denom = maxHit + 1.0;
        double sum = 0.0;
        for (int hit1 = 1; hit1 <= maxHit; hit1++) {
            double weight = (hit1 == 1 ? 2.0 : 1.0) / denom;
            sum += weight * chainTotal(hit1, 3);
        }
        return sum;
    }

    /**
     * Expected total when the landing hit is drawn (roughly) uniformly from
     * {@code [lo, hi]} (clamped to a valid, non-empty integer range) and then
     * {@code remainingHits} more hits follow the halving chain (see
     * {@link #chainTotal}) — {@code remainingHits} is 2 for hit2 landing
     * (hit3, hit4 follow), 1 for hit3 landing (only hit4 follows), 0 for hit4
     * landing (it IS the last hit, no chain).
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
     * {@code landingValue} plus {@code remainingHits} further halved hits,
     * where every hit strictly before the LAST one is {@code floor(prev/2)}
     * and the last hit in the whole 4-hit sequence is {@code floor(prev/2)+1}
     * (the "+1" pity bump the wiki describes on the final hit only).
     * {@code remainingHits} counts how many MORE hits follow {@code
     * landingValue} in the 4-hit sequence (0, 1, or 2 — the landing hit is
     * never hit4 with remainingHits &gt; 0, since hit4 is always last).
     */
    private static int chainTotal(int landingValue, int remainingHits) {
        int total = landingValue;
        int prev = landingValue;
        for (int i = 0; i < remainingHits; i++) {
            boolean isLast = i == remainingHits - 1;
            int next = prev / 2 + (isLast ? 1 : 0);
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
     * input since {@code hitChance} is not linear in the attack roll; see
     * this class's sourcing note). +15% damage is an exact floor step,
     * matching every other flat-percentage damage bonus in this codebase
     * (e.g. {@link CombatMath#magicPreHitRoll}'s +15% imbued-helm step).
     */
    static double dragonDaggerExpectedDamage(double hitChance, int maxHit) {
        double boostedChance = Math.min(1.0, hitChance * 1.15);
        int boostedMax = (int) Math.floor(maxHit * 1.15);
        double perHit = DamageDistribution.averageDamagePerAttack(boostedChance, boostedMax);
        return 2.0 * perHit;
    }

    /**
     * Burning claws' "Burning barrage" special (35% spec energy): up to three
     * successive attacks, each with the SAME per-attack accuracy as an
     * ordinary hit, stopping at the first success (mirroring Dragon claws'
     * "first success ends the accuracy checks" structure, but over three
     * hits instead of four, and with the total damage of the landing roll
     * pre-inflated rather than chained/halved):
     * <ul>
     * <li>Roll 1 lands: total damage ~ {@code [0.75*maxHit, 1.75*maxHit]}
     * (split 25/25/50 across three hitsplats — the split doesn't change the
     * expected TOTAL, only its presentation).</li>
     * <li>Roll 1 misses, roll 2 lands: total ~ {@code [0.5*maxHit, 1.5*maxHit]}.</li>
     * <li>Rolls 1-2 miss, roll 3 lands: total ~ {@code [0.25*maxHit, 1.25*maxHit]}.</li>
     * <li>All three miss: 20% chance of 0, 40% chance of 1, 40% chance of 2.</li>
     * </ul>
     * The burn damage-over-time proc (stacking, up to 5 stacks, ~10 damage
     * over 40 ticks) is NOT modelled — see {@link SpecWeapon}'s class javadoc
     * for why (it is a delayed, stacking, probabilistic side effect entirely
     * outside this per-use expected-damage metric, the same tier of effect
     * {@link DpsCalculator}'s own javadoc already excludes elsewhere, e.g.
     * Avarice/Tumeken's 3x). This is stated plainly, not hidden: the burn
     * proc means burning claws' TRUE value is somewhat higher than the number
     * this method returns.
     */
    static double burningClawsExpectedDamage(double hitChance, int maxHit) {
        if (maxHit <= 0) {
            return 0.0;
        }
        double p = hitChance;
        double e1 = rangeMean(scaledBound(maxHit, 3, 4), scaledBound(maxHit, 7, 4));
        double e2 = rangeMean(scaledBound(maxHit, 1, 2), scaledBound(maxHit, 3, 2));
        double e3 = rangeMean(scaledBound(maxHit, 1, 4), scaledBound(maxHit, 5, 4));
        double allMiss = 0.2 * 0 + 0.4 * 1 + 0.4 * 2; // 1.2

        return p * e1
                + p * (1 - p) * e2
                + p * (1 - p) * (1 - p) * e3
                + (1 - p) * (1 - p) * (1 - p) * allMiss;
    }

    /** Mean of an inclusive integer range, clamped to a valid non-empty range (lo &gt;= 0, hi &gt;= lo). */
    private static double rangeMean(int lo, int hi) {
        int loClamped = Math.max(0, lo);
        int hiClamped = Math.max(loClamped, hi);
        return (loClamped + hiClamped) / 2.0;
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
     * is (see this class's sourcing note); damage is an exact floor step.
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
        return rangeMean(scaledBound(maxMeleeHit, 1, 2), scaledBound(maxMeleeHit, 3, 2));
    }
}
