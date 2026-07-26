package com.ospulse.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Picks the single best curated {@link SpecWeapon} to recommend for the
 * current target, per the design spec §8 "Selection rule" (director decision
 * — implemented exactly as specified below, not redesigned):
 * <pre>
 * 1. Filter to owned ({@link SpecWeapon#isOwned}) and legal
 *    ({@link MonsterCombatRequirement#permitsWeapon}) — so a monster with a
 *    melee gate (e.g. Zulrah) never gets a melee spec suggested, falling out
 *    of the existing combat-requirement work for free.
 * 2. If the target's Defence is high enough to matter
 *    ({@link #HIGH_DEFENCE_THRESHOLD}) and a {@link SpecRole#DEFENCE_DRAIN}
 *    spec is owned+legal, recommend the best-scoring one of those.
 * 3. Otherwise recommend the best-scoring owned+legal {@link SpecRole#DAMAGE}
 *    spec, scored by expected damage per use divided by spec-bar cost
 *    ("damage per spec-bar %").
 * 4. {@link SpecRole#UTILITY} specs are never recommended.
 * 5. Final fallback (round-2 director decision, added after the original
 *    rule shipped): if neither step 2 nor step 3 produced a recommendation
 *    — the player owns no applicable {@code DAMAGE} or {@code
 *    DEFENCE_DRAIN} spec for this target — but does own a {@link
 *    SpecRole#HEAL} spec, recommend it. Sustain is genuinely incommensurable
 *    with damage and unknowable at planning time, so it deliberately never
 *    OUTRANKS steps 2-3; it only fills the gap where they would otherwise
 *    recommend nothing at all, which is strictly worse when the player is
 *    holding a Saradomin godsword. Its readout names the effect ("heals 50%
 *    of damage dealt"), never a DPS number, so it carries no false claim.
 * </pre>
 *
 * <p><b>Interpreting "no applicable DEFENCE_DRAIN" for step 5.</b> A
 * DEFENCE_DRAIN spec that IS owned+legal but whose target doesn't clear
 * {@link #HIGH_DEFENCE_THRESHOLD} (so step 2 never fires) is treated the
 * same as "none owned" for step 5's purposes — the rule falls through to
 * whatever step 3/5 produces rather than recommending a drain the target is
 * too weak to warrant. This one under-specified cell was not covered by
 * either of the director's two required HEAL-fallback scenarios (own ONLY
 * HEAL; own HEAL AND DAMAGE), so it is resolved by the simplest reading
 * that adds no branch beyond what was asked, stated here rather than left
 * implicit.
 */
public final class SpecWeaponSelector {
    private SpecWeaponSelector() {
    }

    /**
     * "High enough [Defence] to matter" (design spec §8) — derived from the
     * bundled monster defence-level distribution in {@code
     * monsters.min.json.gz}, not invented. That file holds 2830 rows across
     * every monster/phase; deduplicating to one entry per distinct base name
     * (keeping each monster's highest-defence phase) gives 1497 distinct
     * monsters with defence levels ranging 0-480 (mean 69.3, median 52).
     * This constant is the 90th percentile of THAT distribution: 150. In
     * other words, a defence-drain spec is only recommended over a pure
     * damage spec against a target in the toughest 10% of monsters by
     * Defence — exactly the point of the role split (a drain that never
     * meaningfully reduces the target's effective defence isn't worth
     * spending the spec bar on). Computed 2026-07-26 via a one-off script
     * over the bundled data; re-derive the same way if the bundled monster
     * set is ever refreshed and this should be revisited.
     */
    static final int HIGH_DEFENCE_THRESHOLD = 150;

    /**
     * Supplies the (hitChance, maxHit) pair for wielding one curated {@link
     * SpecWeapon} at its own {@link SpecWeapon#style()} against the current
     * target, with every other worn slot held fixed and only the weapon slot
     * swapped to the candidate — "if I switched to this weapon and attacked
     * normally, what would that attack roll look like" — the same
     * swap-one-slot machinery {@code GearOptimizer} already uses for
     * ordinary candidate scoring. {@code null} when not computable (e.g. no
     * target selected, or the weapon's style can't be resolved).
     */
    public interface DpsProbe {
        DpsResult probe(SpecWeapon weapon);
    }

    /** See the class javadoc for the exact rule; {@link Optional#empty()} when nothing owned+legal qualifies. */
    public static Optional<SpecWeaponRecommendation> select(Monster target, MonsterCombatRequirement requirement,
                                                              Set<Integer> ownedItemIds, DpsProbe probe) {
        if (target == null || ownedItemIds == null || probe == null) {
            return Optional.empty();
        }
        List<SpecWeapon> eligible = new ArrayList<>();
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            if (weapon.role() == SpecRole.UTILITY) {
                continue; // rule 4 — never recommended, so not even worth probing.
            }
            if (!weapon.isOwned(ownedItemIds)) {
                continue;
            }
            boolean weaponUsesWornAmmo = AmmoCompatibility.consumedClass(weapon.itemId()) != null;
            if (requirement != null && !requirement.permitsWeapon(weapon.itemId(), weapon.style(), weaponUsesWornAmmo)) {
                continue;
            }
            eligible.add(weapon);
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        if (target.defenceLevel() >= HIGH_DEFENCE_THRESHOLD) {
            SpecWeaponRecommendation drain = bestByRole(eligible, SpecRole.DEFENCE_DRAIN, probe);
            if (drain != null) {
                return Optional.of(drain);
            }
        }
        SpecWeaponRecommendation damage = bestByRole(eligible, SpecRole.DAMAGE, probe);
        if (damage != null) {
            return Optional.of(damage);
        }
        // Step 5 fallback — see class javadoc. Never reached when step 2 or 3
        // above already returned.
        return Optional.ofNullable(bestByRole(eligible, SpecRole.HEAL, probe));
    }

    private static SpecWeaponRecommendation bestByRole(List<SpecWeapon> eligible, SpecRole role, DpsProbe probe) {
        SpecWeapon bestWeapon = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SpecWeapon weapon : eligible) {
            if (weapon.role() != role) {
                continue;
            }
            DpsResult result = probe.probe(weapon);
            if (result == null) {
                continue;
            }
            double damagePerUse = weapon.expectedDamagePerUse(result.accuracy(), result.maxHit());
            double score = damagePerUse / weapon.specCostPercent();
            if (score > bestScore) {
                bestScore = score;
                bestWeapon = weapon;
            }
        }
        return bestWeapon == null ? null : new SpecWeaponRecommendation(bestWeapon, bestScore);
    }
}
