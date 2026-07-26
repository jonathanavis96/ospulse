package com.ospulse.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
            new SpecWeapon(19675, ids(30305), "Arclight", SpecRole.DEFENCE_DRAIN, 50,
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
