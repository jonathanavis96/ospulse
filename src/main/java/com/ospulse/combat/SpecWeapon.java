package com.ospulse.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One hand-curated entry in the "best spec weapon" catalog (design spec §8).
 * Special attack EFFECTS are not published in any bundled data file, and
 * upstream {@code weirdgloop/osrs-dps-calc} does not publish a reusable table
 * either — it hand-writes ~30 {@code if (usingSpecialAttack && wearing('X'))}
 * blocks in {@code PlayerVsNPCCalc.ts}. Only the special-attack ENERGY COST
 * is that kind of "small, stable, safely reusable" fact; the effect itself is
 * curated by hand here, exactly as {@link DemonbaneWeapon}/{@link
 * DragonHunterWeapon}/{@link Tome} already are in this codebase.
 *
 * <p><b>Every {@link #itemId()} and every id in {@link #ownedAliasIds()} is
 * verified against the bundled {@code equipment_index.min.json}</b> — see
 * {@code SpecWeaponCatalogDataTest}. {@link #displayName()} is that id's
 * EXACT indexed name, not a paraphrase. A weapon that could not be verified
 * this way is omitted from {@link #CATALOG} entirely, never included with a
 * guessed id (a missing entry is visibly absent; a wrong one looks modelled
 * — see the design spec's item-id rule).
 *
 * <p>Alias ids are cosmetic recolours / poison-charge variants of the SAME
 * physical weapon (verified to share the exact indexed display name, or its
 * {@code (cr)}/{@code (or)}/{@code (deadman)} cosmetic-recolour form) — owning
 * any one of them counts as owning the weapon for recommendation purposes,
 * mirroring how {@code OwnedVariantResolver} treats a cosmetic recolour as
 * the same underlying item.
 *
 * <p><b>A CHARGE-STATE variant is NOT an alias</b>, even when the game calls
 * it "the same weapon" narratively. Worked example: 30305 "Arclight
 * (inactive)" is what a charged Arclight (19675) turns into once its charges
 * run out — the wiki notes it then "functions identically to Darklight",
 * keeping only the Weaken special. It has different stats (19675: aslash 38,
 * str 8, speed 4; 30305: aslash 16, str 13, speed 5, per {@code
 * equipment_stats.min.json}) and loses the demonbane bonus ({@code
 * GearVariants#demonbaneWeaponFor} recognises only 19675). Aliasing it would
 * let an inactive-only owner be recommended, probed, and rendered a weapon
 * with better stats than the one they actually have. 30305 is therefore
 * deliberately absent from the Arclight entry's {@link #ownedAliasIds()}.
 */
public final class SpecWeapon {
    /**
     * (hitChance, maxHit) for the weapon's own designated {@link #style()},
     * computed through the ordinary {@link DpsCalculator}/{@link CombatMath}
     * pipeline against the real target — see {@code SpecWeaponSelector.DpsProbe}
     * for how the caller obtains this pair with every other worn slot held
     * fixed and only the weapon slot swapped to this candidate.
     */
    @FunctionalInterface
    interface DamageModel {
        double expectedDamagePerUse(double hitChance, int maxHit);
    }

    private final int itemId;
    private final Set<Integer> ownedAliasIds;
    private final String displayName;
    private final SpecRole role;
    private final int specCostPercent;
    private final CombatStyle style;
    private final Stance stance;
    private final String effectSummary;
    private final DamageModel damageModel;

    private SpecWeapon(int itemId, int[] aliasIds, String displayName, SpecRole role, int specCostPercent,
                        CombatStyle style, Stance stance, String effectSummary, DamageModel damageModel) {
        this.itemId = itemId;
        Set<Integer> aliases = new HashSet<>();
        for (int alias : aliasIds) {
            aliases.add(alias);
        }
        this.ownedAliasIds = Collections.unmodifiableSet(aliases);
        this.displayName = displayName;
        this.role = role;
        this.specCostPercent = specCostPercent;
        this.style = style;
        this.stance = stance;
        this.effectSummary = effectSummary;
        this.damageModel = damageModel;
    }

    /** The canonical, index-verified item id (see class javadoc) used for the DPS probe and the rendered icon. */
    public int itemId() {
        return itemId;
    }

    /** Other item ids (cosmetic recolours / poison-charge variants) that count as owning this same weapon. */
    public Set<Integer> ownedAliasIds() {
        return ownedAliasIds;
    }

    /** The exact name {@code itemId} resolves to in {@code equipment_index.min.json} — never a paraphrase. */
    public String displayName() {
        return displayName;
    }

    public SpecRole role() {
        return role;
    }

    /** Special attack energy cost as a whole percent of the spec bar (e.g. 50 for half the bar). */
    public int specCostPercent() {
        return specCostPercent;
    }

    /** The combat style this weapon's special is evaluated at (see {@code SpecWeaponSelector.DpsProbe}). */
    public CombatStyle style() {
        return style;
    }

    /** The stance (attack-options row) that style is evaluated at — see {@link Stance}'s per-style bonus table. */
    public Stance stance() {
        return stance;
    }

    /**
     * Short, name-the-effect text for the UI readout (e.g. "+50% damage,
     * −30% Defence") — deliberately never a bare DPS number, so the cell does
     * not imply false precision comparing incommensurable effects (design
     * spec §8).
     */
    public String effectSummary() {
        return effectSummary;
    }

    /**
     * The real {@link WeaponStyle} this catalog entry should be evaluated
     * at: the one offered by {@code weaponRepo} for {@link #itemId()} whose
     * type/stance exactly match {@link #style()}/{@link #stance()}, or the
     * first style of the right {@link #style()} type if that exact stance
     * isn't offered, or {@code null} if this weapon offers no style of that
     * type at all (should not happen for a correctly-curated entry — see
     * {@code SpecWeaponCatalogDataTest#everyWeaponOffersItsDeclaredCombatStyle}
     * — but fails closed, no DPS probe result, rather than guessing a style).
     */
    public WeaponStyle resolvedStyle(WeaponCategoryRepository weaponRepo) {
        WeaponStyle firstOfType = null;
        for (WeaponStyle candidate : weaponRepo.stylesForItem(itemId)) {
            if (candidate.type() != style) {
                continue;
            }
            if (candidate.stance() == stance) {
                return candidate;
            }
            if (firstOfType == null) {
                firstOfType = candidate;
            }
        }
        return firstOfType;
    }

    /** True if {@code ownedItemIds} contains this weapon's canonical id or any of {@link #ownedAliasIds()}. */
    public boolean isOwned(Set<Integer> ownedItemIds) {
        if (ownedItemIds.contains(itemId)) {
            return true;
        }
        for (int alias : ownedAliasIds) {
            if (ownedItemIds.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if {@code excludedItemIds} (the panel's "Exclude from suggestions"
     * set — see {@code GearSection#excludedItemIds}, shipped in stage 4 at
     * the reporter's explicit request) contains this weapon's canonical id
     * or any of {@link #ownedAliasIds()} — mirrors {@link #isOwned}'s
     * alias-symmetry so excluding a cosmetic recolour excludes the whole
     * weapon, the same as it would for an ordinary optimiser candidate.
     */
    public boolean isExcluded(Set<Integer> excludedItemIds) {
        if (excludedItemIds.contains(itemId)) {
            return true;
        }
        for (int alias : ownedAliasIds) {
            if (excludedItemIds.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if at least one of {@link #itemId()} or {@link #ownedAliasIds()}
     * is BOTH owned ({@code ownedItemIds}) AND NOT restricted ({@code
     * restrictedItemIds}) — round-3 fix. Checking the restriction against
     * the canonical id alone (as {@code SpecWeaponSelector} previously did)
     * let a player who owns ONLY a restricted alias (e.g. the deadman-locked
     * Voidwaker 29607) still be recommended the ordinary Voidwaker 27690
     * they never actually owned, since {@link #isOwned} succeeds
     * family-wide while the restriction only ever looked at 27690. A player
     * who owns the canonical id itself is unaffected: that id, being
     * unrestricted, satisfies this check on its own, so a restricted alias
     * still never suppresses a genuinely owned, unrestricted family member.
     */
    public boolean hasOwnedUnrestrictedId(Set<Integer> ownedItemIds, Set<Integer> restrictedItemIds) {
        if (ownedItemIds.contains(itemId) && !restrictedItemIds.contains(itemId)) {
            return true;
        }
        for (int alias : ownedAliasIds) {
            if (ownedItemIds.contains(alias) && !restrictedItemIds.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if a player at {@code baseLevels} can equip this weapon, per
     * {@link EquipmentRequirementsRepository#canEquip} — BUT resolved across
     * the whole family ({@link #itemId()} plus every {@link #ownedAliasIds()}
     * entry) rather than the canonical id alone (round-2 fix). Data-shape bug
     * found in review: the canonical Dragon dagger id (1231) has NO row in
     * {@code equipment_requirements.min.json}, while alias ids 1215/5698
     * carry the real 60 Attack requirement — checking 1231 alone fails OPEN
     * (no row = no requirement) and lets a sub-60-Attack owner be
     * recommended it. Every alias here is the SAME physical weapon (cosmetic
     * recolour / poison-charge variant — see class javadoc), so they share
     * one true in-game requirement; a missing row on one member is a data
     * gap, never evidence of a lower requirement. The binding requirement is
     * the union of every requirement found across the family, keeping the
     * STRICTEST (max) level per skill so a hole in one row can never relax
     * what another row in the same family proves is actually required.
     *
     * <p>Deliberately NOT a change to {@link
     * EquipmentRequirementsRepository#canEquip}'s fail-open default itself —
     * that default is correct for the optimiser at large (an item with
     * genuinely no requirement, e.g. level 1 gear, must stay unblocked) and
     * changing it globally would affect every candidate, not just this
     * catalog's data gap.
     */
    public boolean canEquip(Map<String, Integer> baseLevels) {
        EquipmentRequirementsRepository repo = EquipmentRequirementsRepository.getInstance();
        Map<String, Integer> merged = new HashMap<>();
        mergeRequirements(merged, repo.requirementsFor(itemId));
        for (int alias : ownedAliasIds) {
            mergeRequirements(merged, repo.requirementsFor(alias));
        }
        if (merged.isEmpty()) {
            return true;
        }
        if (baseLevels == null || baseLevels.isEmpty()) {
            return true; // no level info supplied → don't over-filter, mirrors the repository's own rule.
        }
        for (Map.Entry<String, Integer> req : merged.entrySet()) {
            Integer have = baseLevels.get(req.getKey());
            if (have != null && have < req.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void mergeRequirements(Map<String, Integer> merged, Map<String, Integer> reqs) {
        if (reqs == null) {
            return;
        }
        for (Map.Entry<String, Integer> e : reqs.entrySet()) {
            merged.merge(e.getKey(), e.getValue(), Math::max);
        }
    }

    /** Expected damage dealt by ONE use of this special, given the generic pipeline's (hitChance, maxHit) for {@link #style()}. */
    double expectedDamagePerUse(double hitChance, int maxHit) {
        return damageModel.expectedDamagePerUse(hitChance, maxHit);
    }

    private static int[] ids(int... aliasIds) {
        return aliasIds;
    }

    /**
     * The hand-curated spec-weapon catalog (design spec §8's table). Every id
     * is verified against {@code equipment_index.min.json} — see
     * {@code SpecWeaponCatalogDataTest}.
     */
    public static final List<SpecWeapon> CATALOG = Collections.unmodifiableList(Arrays.asList(
            // ---- DAMAGE ---------------------------------------------------------------
            new SpecWeapon(13652, ids(28039, 26708), "Dragon claws", SpecRole.DAMAGE, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "~2x damage on landing (4-hit cascade)",
                    SpecCascadeMath::dragonClawsExpectedDamage),
            new SpecWeapon(1231, ids(5680, 5698, 1215, 28021, 28023, 28025, 28019), "Dragon dagger", SpecRole.DAMAGE, 25,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "+15% accuracy & damage, two hits",
                    SpecCascadeMath::dragonDaggerExpectedDamage),
            new SpecWeapon(29577, ids(), "Burning claws", SpecRole.DAMAGE, 35,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "3-hit cascade + stacking burn",
                    SpecCascadeMath::burningClawsExpectedDamage),
            new SpecWeapon(12926, ids(12924), "Toxic blowpipe", SpecRole.DAMAGE, 50,
                    CombatStyle.RANGED, Stance.RAPID,
                    "+100% accuracy, +50% damage, heals 50% of damage dealt",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 2.0, 1.5)),
            new SpecWeapon(27690, ids(29607), "Voidwaker", SpecRole.DAMAGE, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "Guaranteed extra Magic hit (50-150% of max melee hit)",
                    (hitChance, maxHit) -> SpecCascadeMath.voidwakerExpectedDamage(maxHit)),
            new SpecWeapon(861, ids(), "Magic shortbow", SpecRole.DAMAGE, 55,
                    CombatStyle.RANGED, Stance.RAPID,
                    "Two shots (accuracy penalty not numerically modelled — see class javadoc)",
                    (hitChance, maxHit) -> 2.0 * DamageDistribution.averageDamagePerAttack(hitChance, maxHit)),
            new SpecWeapon(12788, ids(), "Magic shortbow (i)", SpecRole.DAMAGE, 50,
                    CombatStyle.RANGED, Stance.RAPID,
                    "Two shots, imbued (accuracy penalty not numerically modelled — see class javadoc)",
                    (hitChance, maxHit) -> 2.0 * DamageDistribution.averageDamagePerAttack(hitChance, maxHit)),

            // ---- DEFENCE_DRAIN ----------------------------------------------------------
            new SpecWeapon(13576, ids(28035), "Dragon warhammer", SpecRole.DEFENCE_DRAIN, 50,
                    CombatStyle.CRUSH, Stance.AGGRESSIVE,
                    "+50% damage, -30% target Defence",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 1.0, 1.5)),
            new SpecWeapon(11804, ids(20370), "Bandos godsword", SpecRole.DEFENCE_DRAIN, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "Double accuracy, +21% damage, drains a combat stat by the damage dealt",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 2.0, 1.21)),
            new SpecWeapon(21003, ids(27100), "Elder maul", SpecRole.DEFENCE_DRAIN, 50,
                    CombatStyle.CRUSH, Stance.AGGRESSIVE,
                    "+25% accuracy, -35% target Defence",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 1.25, 1.0)),
            // NOT aliased to 30305 "Arclight (inactive)" — that is a charge-state
            // variant, not a cosmetic recolour of the SAME physical weapon (see
            // class javadoc). Per the wiki, an exhausted Arclight (19675) turns
            // into 30305, which "functions identically to Darklight": it keeps
            // the Weaken special but loses Arclight's own stats and demonbane
            // bonus (equipment_stats.min.json: 19675 aslash 38/str 8/speed 4 vs
            // 30305 aslash 16/str 13/speed 5), and {@code
            // GearVariants#demonbaneWeaponFor} recognises only 19675. Aliasing
            // it here would recommend/probe/render a better weapon than the one
            // an inactive-only owner actually has.
            new SpecWeapon(19675, ids(), "Arclight", SpecRole.DEFENCE_DRAIN, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "-5% target Attack/Strength/Defence (x2 vs demons)",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 1.0, 1.0)),
            new SpecWeapon(29589, ids(), "Emberlight", SpecRole.DEFENCE_DRAIN, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "-5% target Attack/Strength/Defence (x3 vs demons)",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 1.0, 1.0)),

            // ---- HEAL --------------------------------------------------------------------
            new SpecWeapon(11806, ids(20372), "Saradomin godsword", SpecRole.HEAL, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "Double accuracy, +10% damage, heals 50% HP / 25% Prayer of damage dealt",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 2.0, 1.10)),

            // ---- UTILITY (never the top PvM recommendation — SpecWeaponSelector rule 4) --
            new SpecWeapon(26374, ids(), "Zaryte crossbow", SpecRole.UTILITY, 75,
                    CombatStyle.RANGED, Stance.RAPID,
                    "Double accuracy, guaranteed enchanted-bolt effect",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 2.0, 1.0)),
            new SpecWeapon(12006, ids(26484), "Abyssal tentacle", SpecRole.UTILITY, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "Binds target 5s, ~50% chance to poison",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 1.0, 1.0)),
            new SpecWeapon(4151, ids(26482), "Abyssal whip", SpecRole.UTILITY, 50,
                    CombatStyle.SLASH, Stance.AGGRESSIVE,
                    "+25% accuracy; PvP-only run-energy drain — never useful in PvM",
                    (hitChance, maxHit) -> SpecCascadeMath.boostedSingleHit(hitChance, maxHit, 1.25, 1.0))
    ));

    static SpecWeapon forTest(int itemId, int[] aliasIds, String displayName, SpecRole role, int specCostPercent,
                               CombatStyle style, Stance stance, String effectSummary, DamageModel damageModel) {
        return new SpecWeapon(itemId, aliasIds, displayName, role, specCostPercent, style, stance, effectSummary, damageModel);
    }
}
