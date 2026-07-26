package com.ospulse.ui.sections.gear;

import com.ospulse.combat.optimizer.GearOptimizer;

/**
 * Pure decision logic for the ironman "owned gear only" optimiser mode
 * (issue #11). An ironman can't buy anything, so the upgrade-oriented UI
 * (budget/risk row, "Best DPS found", "vs owned-only", "Total spend", "DPS
 * per gp spent", suggested swaps) is noise — this is a real optimiser
 * restriction (budget forced to 0), not merely a visual mask, so every
 * remaining recommendation is actually reachable.
 *
 * <p>Deliberately free of any {@link com.ospulse.ui.sections.GearSection}
 * state: callers pass in whatever budget/visibility inputs they hold rather
 * than this class reaching into private mutable fields, so it stays trivially
 * unit-testable and easy to split out later.
 */
public final class OwnedOnlyMode
{
	private OwnedOnlyMode()
	{
	}

	/**
	 * The optimiser request's actual budget: forced to 0 in owned-only mode.
	 * {@code storedBudget} (whatever the user has typed/persisted) is only
	 * ever read here, never written, so toggling the mode back off restores
	 * it unchanged.
	 */
	public static long effectiveBudget(boolean ownedOnly, long storedBudget)
	{
		return ownedOnly ? 0L : storedBudget;
	}

	/**
	 * Whether the budget/risk row (gold-pile badge, budget field, K/M toggle,
	 * risk column) should be visible. Unlike {@link #upgradeStatRowsVisible},
	 * this row has no other reason to hide, so owned-only mode is the only
	 * input.
	 */
	public static boolean upgradeUiVisible(boolean ownedOnly)
	{
		return !ownedOnly;
	}

	/**
	 * Whether the four upgrade-oriented result rows ("Best DPS found", "vs
	 * owned-only", "Total spend", "DPS per gp spent") and the suggested-swaps
	 * list should be visible. Composes owned-only mode with whether the
	 * current result is otherwise usable ({@code hasUsableResult} — false in
	 * the pre-existing "no usable weapon" state, which must keep hiding these
	 * regardless of mode). The "Optimised for" row is deliberately NOT
	 * included here — it describes the current pick, not an upgrade, and
	 * stays visible whenever {@code hasUsableResult} alone would show it; see
	 * {@code GearSection#setOptimizerStyleRowVisible}.
	 */
	public static boolean upgradeStatRowsVisible(boolean ownedOnly, boolean hasUsableResult)
	{
		return hasUsableResult && !ownedOnly;
	}

	/** {@link #upgradeStatRowsVisible(boolean, boolean)}, deriving "has a usable result" from a nullable optimiser result — {@code null} or a {@code null} style means not usable. */
	public static boolean upgradeStatRowsVisible(boolean ownedOnly, GearOptimizer.Result result)
	{
		return upgradeStatRowsVisible(ownedOnly, result != null && result.style() != null);
	}
}
