package com.ospulse.ui.sections.gear;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure decision logic for the P2-A fix (Codex finding on PR #19, {@code
 * GearSection.java:4789}, "Revalidate every recommended item against current
 * ownership").
 *
 * <p>{@link OwnedOnlyMandatoryOverrideGate} already guarantees a target's
 * mandatory {@code MonsterGearOverride} is satisfiable by something owned
 * RIGHT NOW (it reads a live {@code ownedIds} set, not a stale one) before
 * {@code GearSection#onOptimizerResult} installs a result. But that gate only
 * covers the one forced item a mandatory override injects — every OTHER slot
 * in the loadout is whatever {@code GearOptimizer} picked from the {@code
 * ownedPrices} snapshot {@code GearSection#withResolvedPrices} captured when
 * the search LAUNCHED. Owned-only mode's entire guarantee is that every
 * recommended item is one the player owns; if the player drops, sells, or
 * banks-then-loses an item in the gap between that snapshot and the result
 * landing (price resolution / the background {@code SwingWorker} can take a
 * while), an ordinary — non-mandatory — loadout choice can be installed,
 * previewed, and bank-highlighted for an item the player no longer owns,
 * silently breaking the guarantee. Worse, a result that WAS valid at install
 * time can go stale later still: {@code GearSection#apply(SessionSnapshot)}
 * refreshes the player's live holdings on every wealth snapshot without
 * re-validating whatever result is currently installed.
 *
 * <p>This class answers one question for both of those moments — "does
 * {@code ownedIds} (a LIVE read, taken at whatever instant the caller calls
 * this) still contain every id this loadout actually resolves to" — so
 * {@code GearSection} can reuse the exact same check for both: once right
 * before installing a freshly-landed {@code GearOptimizer.Result} (P2-A half
 * 1), and again as a targeted check whenever the player's holdings change
 * while a result is already installed (P2-A half 2), rather than either
 * skipping the second check or blanket-invalidating on every wealth update
 * (which would flicker the panel and discard still-valid results on every
 * unrelated snapshot).
 *
 * <p>Deliberately keyed on the caller-supplied RESOLVED slot-&gt;item-id map
 * (e.g. {@code GearSection#optimizerLoadoutSlotMap}'s output) rather than the
 * raw {@code GearOptimizer.SlotChoice} list: {@code
 * GearSection#resolvedChoiceItemId} is the single choke point that turns a
 * choice into the exact id the panel actually shows/applies/bank-highlights
 * (an owned fortified/imbued variant resolves to the owned variant's id, not
 * the optimiser's plain base id) — validating anything else risks agreeing
 * with a stale/wrong id the user was never actually shown.
 */
public final class OwnedOnlyResultOwnershipGate
{
	private OwnedOnlyResultOwnershipGate()
	{
	}

	/**
	 * The first (slot ordinal, resolved item id) entry in {@code
	 * resolvedLoadout} that {@code ownedIds} does not contain, or empty when
	 * owned-only mode is off or every resolved id in the loadout is still
	 * owned. Iteration order follows {@code resolvedLoadout}'s own order
	 * (callers pass a {@code LinkedHashMap} keyed by slot ordinal), so the
	 * result is deterministic for a given loadout.
	 *
	 * @param ownedOnly whether the ironman "owned gear only" mode is active
	 * @param resolvedLoadout equipment-slot-ordinal -&gt; RESOLVED item-id map
	 *                         for the loadout being validated (see the class
	 *                         javadoc on why this must already be resolved)
	 * @param ownedIds every item id the player currently owns (worn + banked
	 *                  + variant-normalised — see {@code
	 *                  GearSection#ownedPriceMap()}), read live by the caller
	 *                  at the instant of the check
	 */
	public static Optional<Map.Entry<Integer, Integer>> firstUnownedEntry(boolean ownedOnly,
		Map<Integer, Integer> resolvedLoadout, Set<Integer> ownedIds)
	{
		if (!ownedOnly)
		{
			return Optional.empty();
		}
		for (Map.Entry<Integer, Integer> entry : resolvedLoadout.entrySet())
		{
			if (!ownedIds.contains(entry.getValue()))
			{
				return Optional.of(entry);
			}
		}
		return Optional.empty();
	}
}
