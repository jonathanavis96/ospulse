package com.ospulse.ui.sections.gear;

import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterGearOverride;
import com.ospulse.combat.MonsterGearOverrideRepository;

import java.util.Optional;
import java.util.Set;

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
 * primary form of nor any {@link MonsterGearOverride#alternativeItemIds()}
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
 * <p>{@link #ownedOnlySatisfyingItemId} is the single source of truth for
 * "which owned id (if any) satisfies this override" — both {@link
 * #blockingOverride} (should owned-only mode refuse this target outright)
 * and {@code ItemEligibility#mandatoryOverrideItemIds} (which id owned-only
 * mode's force-include should actually inject) are derived from it, so the
 * two questions cannot disagree the way they did before this fix: the gate
 * accepted an owned alternative (e.g. a Slayer helmet for a Dust devil's
 * Facemask requirement) as satisfying the requirement and let the result
 * through, while the force-include path kept injecting the unowned primary
 * — equipping an item owned-only mode had just promised was never going to
 * happen.
 */
public final class OwnedOnlyMandatoryOverrideGate
{
	private OwnedOnlyMandatoryOverrideGate()
	{
	}

	/**
	 * The first mandatory {@link MonsterGearOverride} for {@code target} that
	 * owned-only mode cannot satisfy — the player owns neither {@link
	 * MonsterGearOverride#itemId()} nor any of {@link
	 * MonsterGearOverride#alternativeItemIds()} — or empty when owned-only
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
		for (MonsterGearOverride override : MonsterGearOverrideRepository.getInstance().forMonster(target.name()))
		{
			if (!ownedIds.contains(ownedOnlySatisfyingItemId(override, ownedIds)))
			{
				return Optional.of(override);
			}
		}
		return Optional.empty();
	}

	/**
	 * The item id that satisfies {@code override} under owned-only mode given
	 * {@code ownedIds}: the owned alternative when the primary {@link
	 * MonsterGearOverride#itemId()} isn't owned but one of {@link
	 * MonsterGearOverride#alternativeItemIds()} is, otherwise the primary
	 * itself (whether owned or not — an unowned return here is exactly what
	 * makes {@link #blockingOverride} refuse the target, and is also what
	 * {@code ItemEligibility#mandatoryOverrideItemIds} force-includes outside
	 * owned-only mode, where an unaffordable force-include is a legitimate
	 * purchase suggestion rather than a broken guarantee).
	 */
	static int ownedOnlySatisfyingItemId(MonsterGearOverride override, Set<Integer> ownedIds)
	{
		if (!ownedIds.contains(override.itemId()))
		{
			for (Integer alternativeId : override.alternativeItemIds())
			{
				if (ownedIds.contains(alternativeId))
				{
					return alternativeId;
				}
			}
		}
		return override.itemId();
	}
}
