package com.ospulse.ui.sections.gear;

import com.ospulse.combat.*;

import java.util.*;

/**
 * Pure decision logic for the P1-A fix (Codex finding on PR #19, {@code
 * GearSection.java:4602}, "Restrict mandatory includes in owned-only mode").
 *
 * <p>{@code GearOptimizer} force-includes a curated {@link
 * MonsterGearOverride}'s item id past the budget filter and force-equips it
 * into the recommended loadout regardless of ownership or price — a real,
 * deliberate behaviour (these are mechanical/safety requirements like Rune
 * dragons' Insulated boots, not DPS suggestions: silently dropping one would
 * produce a result that LOOKS owned-only-safe and isn't). In every mode
 * except owned-only, that is fine: an unaffordable force-include is a real
 * purchase suggestion. In owned-only mode specifically it is not — the
 * mode's entire guarantee is that every recommendation is something the
 * player already owns, and a forced item the player owns neither the
 * primary form of nor any {@code MonsterGearOverride.alternativeItemIds()}
 * substitute for breaks that guarantee outright.
 *
 * <p>The earlier attempt at this finding only disclosed the gap (an
 * advisory line added by {@code GearSection#updateGearOverrideNote()}
 * appending "you don't own this"). Codex escalated that to P1 as
 * insufficient: a warning elsewhere in the panel does not stop the loadout,
 * the auto-applied preview, or the bank highlight from still recommending
 * the unowned item. This class instead answers "should owned-only mode
 * refuse to recommend anything at all for this target" — {@code
 * GearSection#onOptimizerResult} uses it to short-circuit into an explicit
 * blocked state (mirroring the existing "no usable weapon" cannot-recommend
 * path) rather than filtering the override out and returning a loadout that
 * silently skips a mechanical requirement.
 *
 * <p>Deliberately free of any {@code GearSection} state, matching {@link
 * OwnedOnlyMode} / {@link IronmanOwnedOnlyResolver}'s shape: callers pass in
 * whatever target/ownership inputs they hold rather than this class reaching
 * into private mutable fields.
 *
 * <p>{@link #satisfyingItemId} is the single source of truth for "which id
 * should the player use for this override", shared by the advisory note
 * ({@code GearSection#updateGearOverrideNote()}) and, through {@link
 * #forceIncludeItemId}, the force-include ({@code
 * ItemEligibility#mandatoryOverrideItemIds}). The gate's own question is
 * the broader {@link MonsterGearOverride#satisfiedByAnyOf}. They cannot
 * disagree the way they did before this fix: the gate accepted an owned
 * alternative (e.g. a Slayer helmet for a Dust devil's Facemask requirement)
 * as satisfying the requirement and let the result through, while the
 * force-include path kept injecting the unowned primary — equipping an item
 * owned-only mode had just promised was never going to happen.
 */
public final class OwnedOnlyMandatoryOverrideGate
{
	private OwnedOnlyMandatoryOverrideGate()
	{
	}

	/**
	 * The first mandatory {@link MonsterGearOverride} for {@code target} that
	 * owned-only mode cannot satisfy — the player owns neither {@code
	 * MonsterGearOverride.itemId()} nor any of {@code
	 * MonsterGearOverride.alternativeItemIds()} — or empty when owned-only
	 * mode is off, no target is selected, or every mandatory override (if
	 * any) is satisfied by something owned. Same substitution rule {@code
	 * GearSection#updateGearOverrideNote()} already uses for its "you don't
	 * own this" disclosure.
	 *
	 * @param ownedOnly whether the ironman "owned gear only" mode is active
	 * @param target the currently selected monster, or {@code null} if none
	 * @param ownedIds every item id the player currently owns (worn + banked
	 *                  + variant-normalised — see {@code GearSection#ownedPriceMap()})
	 */
	public static Optional<MonsterGearOverride> blockingOverride(boolean ownedOnly, Monster target, Set<Integer> ownedIds)
	{
		if (!ownedOnly || target == null)
		{
			return Optional.empty();
		}
		for (MonsterGearOverride override : MonsterGearOverrideRepository.getInstance().forMonster(target.lookupName()))
		{
			if (!override.satisfiedByAnyOf(ownedIds))
			{
				return Optional.of(override);
			}
		}
		return Optional.empty();
	}

	/**
	 * The item id the player should use for {@code override}, in every mode:
	 * a worn item that satisfies it (so nothing is swapped needlessly), else
	 * the first {@link MonsterGearOverride#alternativeItemIds()} entry the
	 * player owns, else the primary {@link MonsterGearOverride#itemId()}. An
	 * alternative beats an owned primary because alternatives are listed
	 * because they do the same job at least as well: a player holding both a
	 * V's shield and a Mirror shield must not be told to take the worse one
	 * (issue #35). Ids in {@code exclusions} are never returned as the worn
	 * or alternative choice, so the player's own exclusion moves on to the
	 * next option; the primary is the unconditional fallback, and callers
	 * must apply their own exclusion and ownership rules to it.
	 *
	 * @param wornIds the item ids currently worn ({@code <= 0} for an empty slot)
	 * @param ownedIds every item id the player currently owns
	 * @param exclusions item ids the player has ruled out
	 */
	public static int satisfyingItemId(MonsterGearOverride override, int[] wornIds, Set<Integer> ownedIds,
		Set<Integer> exclusions)
	{
		for (int wornId : wornIds)
		{
			if (override.satisfiedBy(wornId) && !exclusions.contains(wornId))
			{
				return wornId;
			}
		}
		for (Integer alternativeId : override.alternativeItemIds())
		{
			if (ownedIds.contains(alternativeId) && !exclusions.contains(alternativeId))
			{
				return alternativeId;
			}
		}
		return override.itemId();
	}

	/**
	 * The item id to force-include for {@code override}: {@link
	 * #satisfyingItemId}, passing over any worn item with no equipment index
	 * row. The optimiser cannot equip such an item (Earmuffs, a Nose peg), so
	 * forcing it does nothing and leaves the search free to pick a helm that
	 * fails the requirement, while the player has a Slayer helmet in the bank.
	 *
	 * @param wornIds the item ids currently worn ({@code <= 0} for an empty slot)
	 * @param ownedIds every item id the player currently owns
	 * @param exclusions item ids the player has ruled out
	 */
	public static int forceIncludeItemId(MonsterGearOverride override, int[] wornIds, Set<Integer> ownedIds,
		Set<Integer> exclusions)
	{
		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		int[] equippableWornIds = Arrays.stream(wornIds).filter(id -> index.entryFor(id) != null).toArray();
		return satisfyingItemId(override, equippableWornIds, ownedIds, exclusions);
	}
}
