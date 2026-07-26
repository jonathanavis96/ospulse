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
 * </pre>
 *
 * <p><b>{@link SpecRole#HEAL} is catalogued but this literal rule never
 * selects it.</b> Steps 2-3 only ever pick from {@code DEFENCE_DRAIN} or
 * {@code DAMAGE}; there is no third branch for {@code HEAL}. That is a
 * faithful reading of the decided rule as written (it is not this class's
 * place to invent a tie-breaker the spec didn't ask for), not an oversight —
 * flagged here, and in the stage-7 report, rather than silently patched.
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
        return Optional.ofNullable(bestByRole(eligible, SpecRole.DAMAGE, probe));
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
