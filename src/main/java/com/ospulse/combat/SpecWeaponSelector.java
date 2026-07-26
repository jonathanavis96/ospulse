package com.ospulse.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Picks the single best curated {@link SpecWeapon} to recommend for the
 * current target, per the design spec §8 "Selection rule" (director decision
 * — implemented exactly as specified below, not redesigned):
 * <pre>
 * 1. Filter to owned ({@link SpecWeapon#isOwned}), NOT excluded ({@link
 *    SpecWeapon#isExcluded} — the panel's user-facing "Exclude from
 *    suggestions" action, same as any ordinary optimiser candidate), NOT
 *    permanently restricted ({@link SpecWeapon#itemId()} present in the
 *    caller's mode-lock restriction set — per-ID only, deliberately not
 *    alias-symmetric like {@code isExcluded}), equippable at the player's
 *    current levels ({@link SpecWeapon#canEquip} — owning an item never
 *    implies meeting its equip requirements), and legal ({@link
 *    MonsterCombatRequirement#permitsWeapon}/{@link
 *    MonsterCombatRequirement#permitsAmmo} — a ranged spec that fires WORN
 *    ammo must also have the player's actual worn ammo satisfy an
 *    ammo-gated target; {@code permitsWeapon} alone deliberately accepts a
 *    worn-ammo-capable weapon type without checking which ammo is actually
 *    loaded) — so a monster with a melee gate (e.g. Zulrah) never gets a
 *    melee spec suggested, and an ammo-gated target (e.g. Kurask) never
 *    gets a ranged spec suggested unless the player's actual arrows/bolts
 *    qualify, falling out of the existing combat-requirement work for free.
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

    /**
     * Convenience overload for a caller with no exclusions, no equip-level
     * data, and no worn ammo to validate (e.g. a test exercising only the
     * role/threshold/ownership/legality logic) — equivalent to calling the
     * full overload with an empty exclusion set, an empty level map (which
     * {@link EquipmentRequirementsRepository#canEquip} treats as "don't
     * over-filter"), and no worn ammo id.
     */
    public static Optional<SpecWeaponRecommendation> select(Monster target, MonsterCombatRequirement requirement,
                                                              Set<Integer> ownedItemIds, DpsProbe probe) {
        return select(target, requirement, ownedItemIds, java.util.Collections.emptySet(),
                java.util.Collections.emptyMap(), -1, probe);
    }

    /**
     * Back-compat overload with no standing (mode-locked) restriction set —
     * equivalent to calling the full overload with an empty {@code
     * restrictedItemIds}. See the full overload for the exact rule.
     *
     * @param excludedItemIds   the panel's "Exclude from suggestions" set —
     *                          a USER action, alias-symmetric via {@link
     *                          SpecWeapon#isExcluded} (excluding any cosmetic
     *                          recolour excludes the whole weapon family).
     */
    public static Optional<SpecWeaponRecommendation> select(Monster target, MonsterCombatRequirement requirement,
                                                              Set<Integer> ownedItemIds, Set<Integer> excludedItemIds,
                                                              Map<String, Integer> playerBaseLevels, int wornAmmoItemId,
                                                              DpsProbe probe) {
        return select(target, requirement, ownedItemIds, excludedItemIds, java.util.Collections.emptySet(),
                playerBaseLevels, wornAmmoItemId, probe);
    }

    /**
     * See the class javadoc for the exact rule; {@link Optional#empty()} when
     * nothing owned+legal qualifies.
     *
     * @param excludedItemIds    the panel's "Exclude from suggestions" set —
     *                           a USER action, alias-symmetric via {@link
     *                           SpecWeapon#isExcluded}: excluding any one
     *                           cosmetic-recolour/alias id excludes the whole
     *                           weapon family, same as an ordinary optimiser
     *                           candidate.
     * @param restrictedItemIds  standing, permanent per-ID restrictions (e.g.
     *                           {@code ItemEligibility.restrictedItemIds()} —
     *                           Deadman/BH/LMS/beta-locked ids). Deliberately
     *                           NOT alias-symmetric and NOT merged with
     *                           {@code excludedItemIds}: a restricted id is
     *                           checked only against {@link
     *                           SpecWeapon#itemId()} directly (round-2 fix —
     *                           merging it into the alias-symmetric exclusion
     *                           set previously suppressed an entire family,
     *                           e.g. Voidwaker (deadman) 29607 being
     *                           restricted wrongly excluded the ordinary,
     *                           perfectly legal Voidwaker 27690, since 29607
     *                           is only an {@code ownedAliasIds} entry of
     *                           that catalog row, not its own row).
     * @param playerBaseLevels   the player's base skill levels (lowercase
     *                           skill-name keys, e.g. {@code "attack"}), fed
     *                           straight to {@link SpecWeapon#canEquip} — the
     *                           SAME map {@code GearOptimizer.Request}'s
     *                           {@code playerBaseLevels} carries.
     * @param wornAmmoItemId     the player's currently effective worn ammo
     *                           item id (or {@code <= 0} for none) — used for
     *                           ANY candidate that fires worn ammo (see
     *                           {@link AmmoCompatibility#consumedClass}),
     *                           regardless of target, to enforce that the
     *                           worn ammo's class actually matches what the
     *                           weapon fires; ALSO fed to {@link
     *                           MonsterCombatRequirement#permitsAmmo} when
     *                           the target additionally gates ammo (e.g.
     *                           Kurask's broad-ammo-only gate — {@code
     *                           permitsWeapon} alone can't tell "Zaryte
     *                           crossbow, but the ammo gate needs broad
     *                           bolts and I'm not wearing any").
     */
    public static Optional<SpecWeaponRecommendation> select(Monster target, MonsterCombatRequirement requirement,
                                                              Set<Integer> ownedItemIds, Set<Integer> excludedItemIds,
                                                              Set<Integer> restrictedItemIds,
                                                              Map<String, Integer> playerBaseLevels, int wornAmmoItemId,
                                                              DpsProbe probe) {
        if (target == null || ownedItemIds == null || probe == null) {
            return Optional.empty();
        }
        Set<Integer> exclusions = excludedItemIds == null ? java.util.Collections.emptySet() : excludedItemIds;
        Set<Integer> restrictions = restrictedItemIds == null ? java.util.Collections.emptySet() : restrictedItemIds;
        List<SpecWeapon> eligible = new ArrayList<>();
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            if (weapon.role() == SpecRole.UTILITY) {
                continue; // rule 4 — never recommended, so not even worth probing.
            }
            if (!weapon.isOwned(ownedItemIds)) {
                continue;
            }
            if (weapon.isExcluded(exclusions)) {
                continue; // "Exclude from suggestions" — same rule an ordinary candidate obeys.
            }
            if (restrictions.contains(weapon.itemId())) {
                // Per-ID only, deliberately NOT alias-symmetric — see the
                // @param javadoc above. A restricted ALIAS (e.g. the deadman
                // recolour) must not suppress this row when the row's own
                // canonical id is not itself restricted.
                continue;
            }
            if (!weapon.canEquip(playerBaseLevels)) {
                continue; // owning it is not the same as meeting its equip requirements.
            }
            AmmoCompatibility.AmmoClass consumedClass = AmmoCompatibility.consumedClass(weapon.itemId());
            boolean weaponUsesWornAmmo = consumedClass != null;
            if (weapon.style() == CombatStyle.RANGED && weaponUsesWornAmmo
                    && AmmoCompatibility.classify(wornAmmoItemId) != consumedClass) {
                // Weapon/ammo compatibility is a property of the WEAPON and
                // must be enforced regardless of whether the monster gates
                // ammo at all — most targets are unrestricted, so this can't
                // live inside the `requirement != null` branch below. E.g. a
                // Magic shortbow (fires ARROW) with broad bolts (BOLT) worn
                // must be rejected even at a target with no combat
                // requirement, and even at one whose allowedAmmoIds happen to
                // include broad bolts for a DIFFERENT weapon (Kurask permits
                // both broad arrows and broad bolts).
                continue;
            }
            if (requirement != null) {
                if (!requirement.permitsWeapon(weapon.itemId(), weapon.style(), weaponUsesWornAmmo)) {
                    continue;
                }
                if (weapon.style() == CombatStyle.RANGED && weaponUsesWornAmmo
                        && !requirement.permitsAmmo(wornAmmoItemId, weapon.style())) {
                    // permitsWeapon() alone accepts any worn-ammo-capable
                    // ranged weapon on an ammo-gated target (e.g. Kurask) —
                    // it can't know WHICH ammo is actually loaded. A weapon
                    // that fires its own ammo (blowpipe) never reaches this
                    // branch: weaponUsesWornAmmo is false for it.
                    continue;
                }
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
