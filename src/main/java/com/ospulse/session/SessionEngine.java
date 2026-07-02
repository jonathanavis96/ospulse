package com.ospulse.session;

import com.ospulse.ge.GeOfferView;
import com.ospulse.model.ItemStack;
import com.ospulse.wealth.WealthSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core banking-aware wealth-delta session tracker.
 *
 * <p>"Tracked wealth" (see {@link WealthSnapshot#tracked()}) is inventory +
 * equipment + GE-in-flight + pouches. While away from a bank, any change in
 * tracked wealth is real profit/loss. Bank visits are transfers of cold
 * storage into/out of tracked wealth and must not register as profit — not
 * even transiently. While the bank is open its value is live, and the bank's
 * change since open is exactly the net amount transferred (nothing else can
 * move the bank), so profit adds that change back on top of tracked wealth,
 * making every deposit/withdrawal zero-sum in real time. On close the same
 * net transfer is folded into the profit {@code baseline} permanently, so the
 * figure is continuous across the close. Genuine gains while the bank happens
 * to be open (e.g. a GE fill) don't move the bank and therefore still count.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class SessionEngine
{
	/** Tracked wealth that currently defines zero profit. */
	private long baseline;
	private long startMs;
	private boolean bankOpen;
	private long trackedAtBankOpen;
	/**
	 * Live bank value captured when the current bank visit began (or the
	 * first moment during the visit the bank value became known). The bank's
	 * movement away from this anchor is the net amount deposited so far.
	 */
	private long bankValueAtOpen;
	/** Whether {@link #bankValueAtOpen} has been captured for this visit. */
	private boolean bankValueAtOpenKnown;
	private WealthSnapshot previous;
	/**
	 * Loot aggregated per item across the whole session (like the Loot Tracker
	 * plugin): itemId -&gt; running total quantity + value. Unbounded — one entry
	 * per distinct item, not one per pickup — so a stream of the same drop
	 * (e.g. thousands of bird nests) collapses into a single summarised row.
	 */
	private final Map<Integer, LootEntry> lootTotals = new LinkedHashMap<>();
	private long startNetWorth;
	private boolean startBankKnown;

	/**
	 * Begins a new session. Resets baseline, start time, loot, and banking
	 * state.
	 */
	public void startSession(WealthSnapshot initial, long tsMs)
	{
		this.baseline = initial.tracked();
		this.startMs = tsMs;
		this.previous = initial;
		this.startNetWorth = initial.netWorth();
		this.startBankKnown = initial.isBankKnown();
		this.bankOpen = false;
		this.trackedAtBankOpen = 0L;
		this.bankValueAtOpen = 0L;
		this.bankValueAtOpenKnown = false;
		this.lootTotals.clear();
	}

	/**
	 * Reports a bank-open/close transition. On open, records the tracked
	 * wealth and live bank value at that instant. On close, folds the net
	 * amount transferred during the visit (measured by the bank's own change,
	 * see {@link #bankVisitBaselineShift}) into the baseline, so
	 * deposits/withdrawals never register as profit or loss — matching the
	 * live offset {@link #snapshot} applied while the bank was open, so the
	 * profit figure is continuous across the close. No loot diffing happens
	 * while the bank is open (see {@link #update}).
	 */
	public void setBankOpen(boolean open, WealthSnapshot current, long tsMs)
	{
		if (!this.bankOpen && open)
		{
			// FALSE -> TRUE: bank just opened. Anchor the visit.
			this.trackedAtBankOpen = current.tracked();
			this.bankValueAtOpenKnown = current.isBankKnown();
			this.bankValueAtOpen = current.isBankKnown() ? current.getBankValue() : 0L;
		}
		else if (this.bankOpen && !open)
		{
			// TRUE -> FALSE: bank just closed. Make the visit's net transfer
			// permanently neutral.
			this.baseline += bankVisitBaselineShift(current);
		}

		this.previous = current;
		this.bankOpen = open;
	}

	/**
	 * Baseline shift that neutralises the current bank visit at close time.
	 * The bank's change since open is exactly the net amount deposited
	 * (nothing but transfers can move the bank while it is open), so shifting
	 * the baseline by minus that change keeps a pure transfer at zero profit
	 * while leaving genuine tracked-wealth gains made during the visit
	 * counted. Falls back to neutralising the whole tracked-wealth change
	 * (the pre-live behaviour) only if the bank value somehow never became
	 * visible during the visit.
	 */
	private long bankVisitBaselineShift(WealthSnapshot current)
	{
		if (bankValueAtOpenKnown && current.isBankKnown())
		{
			return -(current.getBankValue() - bankValueAtOpen);
		}
		return current.tracked() - trackedAtBankOpen;
	}

	/**
	 * If the bank value only became known partway through the current visit,
	 * anchor {@link #bankValueAtOpen} at its first known reading (treating
	 * the blind interval as transfer-free). Idempotent.
	 */
	private void captureLateKnownBank(WealthSnapshot current)
	{
		if (bankOpen && !bankValueAtOpenKnown && current.isBankKnown())
		{
			this.bankValueAtOpen = current.getBankValue();
			this.bankValueAtOpenKnown = true;
		}
	}

	/**
	 * While the bank is open, the net amount deposited so far this visit
	 * (withdrawals negative); added to tracked wealth it makes inventory/bank
	 * transfers zero-sum in real time. Zero while the bank is closed — after
	 * a close the same amount lives in the baseline instead (see
	 * {@link #setBankOpen}).
	 */
	private long liveBankTransferOffset(WealthSnapshot current)
	{
		if (!bankOpen || !bankValueAtOpenKnown || !current.isBankKnown())
		{
			return 0L;
		}
		return current.getBankValue() - bankValueAtOpen;
	}

	/**
	 * Advances the session with a new wealth snapshot. While the bank is
	 * open, this is a no-op besides recording {@code current} as the new
	 * baseline for future diffing (banking = transfers, not loot). While
	 * away, any positive quantity delta in tracked items that isn't
	 * attributed to a GE transaction and has a positive unit value is
	 * recorded as a loot event.
	 *
	 * @param geAttributedItemIds item ids whose quantity changed this
	 *                            interval due to a GE buy/sell, supplied by
	 *                            the integration layer (see
	 *                            {@code GeReconciler#drainAttributedItemIds()});
	 *                            these are excluded from the loot feed.
	 */
	public void update(WealthSnapshot current, Set<Integer> geAttributedItemIds, long tsMs)
	{
		if (bankOpen)
		{
			captureLateKnownBank(current);
			this.previous = current;
			return;
		}

		Map<Integer, ItemStack> previousItems = previous == null
			? Collections.emptyMap()
			: previous.getTrackedItems();

		for (Map.Entry<Integer, ItemStack> entry : current.getTrackedItems().entrySet())
		{
			int itemId = entry.getKey();
			ItemStack currentStack = entry.getValue();

			ItemStack previousStack = previousItems.get(itemId);
			long previousQty = previousStack == null ? 0L : previousStack.getQuantity();
			long delta = currentStack.getQuantity() - previousQty;

			if (delta <= 0)
			{
				continue;
			}
			if (geAttributedItemIds != null && geAttributedItemIds.contains(itemId))
			{
				continue;
			}
			if (currentStack.getUnitValue() <= 0)
			{
				continue;
			}

			LootEntry entryLoot = new LootEntry(
				itemId,
				currentStack.getName(),
				delta,
				delta * currentStack.getUnitValue(),
				tsMs);
			addLoot(entryLoot);
		}

		this.previous = current;
	}

	private void addLoot(LootEntry entry)
	{
		LootEntry existing = lootTotals.get(entry.getItemId());
		if (existing == null)
		{
			lootTotals.put(entry.getItemId(), entry);
		}
		else
		{
			lootTotals.put(entry.getItemId(), new LootEntry(
				entry.getItemId(),
				entry.getName(),
				existing.getQuantity() + entry.getQuantity(),
				existing.getValue() + entry.getValue(),
				entry.getTimestampMs()));
		}
	}

	/**
	 * Aggregated loot, one row per item, ordered by total value descending
	 * (most valuable first) — the summarised view the panel renders.
	 */
	private List<LootEntry> lootSummary()
	{
		List<LootEntry> summary = new ArrayList<>(lootTotals.values());
		summary.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		return summary;
	}

	/**
	 * Produces a read-only snapshot of the session's current state given the
	 * latest wealth reading and externally-tracked GE/XP data. No active-offer
	 * breakdown (delegates with an empty offer list).
	 */
	public SessionSnapshot snapshot(
		WealthSnapshot current,
		long geRealizedPnl,
		Map<String, Long> xpGained,
		long xpTotal,
		long tsMs)
	{
		return snapshot(current, geRealizedPnl, Collections.emptyList(),
			Collections.emptyList(), xpGained, xpTotal, tsMs);
	}

	/**
	 * Produces a read-only snapshot of the session's current state given the
	 * latest wealth reading and externally-tracked GE/XP/loot-source data.
	 */
	public SessionSnapshot snapshot(
		WealthSnapshot current,
		long geRealizedPnl,
		List<GeOfferView> geOffers,
		List<SourceLoot> lootSources,
		Map<String, Long> xpGained,
		long xpTotal,
		long tsMs)
	{
		foldNewlyKnownBankIntoStart(current);
		captureLateKnownBank(current);

		// While the bank is open, add back the net amount deposited so far so
		// in-progress transfers are zero-sum immediately rather than only
		// after the bank closes.
		long profit = current.tracked() + liveBankTransferOffset(current) - baseline;
		long elapsedMs = tsMs - startMs;
		long profitPerHour = elapsedMs > 0 ? profit * 3600000L / elapsedMs : 0L;
		long netWorthDelta = current.netWorth() - startNetWorth;

		return new SessionSnapshot(
			startMs,
			elapsedMs,
			profit,
			profitPerHour,
			geRealizedPnl,
			netWorthDelta,
			current.isBankKnown(),
			lootSummary(),
			xpGained,
			xpTotal,
			current,
			geOffers,
			lootSources);
	}

	/**
	 * The bank value is unknown until the bank is first opened this session, so
	 * the session's starting net worth was captured tracked-only. The instant
	 * the bank becomes known we retroactively fold its value into the starting
	 * net worth, so the "Net worth Δ" reflects the newly-visible bank as having
	 * been there all along rather than jumping by the whole bank balance (which
	 * looked like a huge phantom gain the first time the bank was opened).
	 */
	private void foldNewlyKnownBankIntoStart(WealthSnapshot current)
	{
		if (!startBankKnown && current.isBankKnown())
		{
			startNetWorth += current.getBankValue();
			startBankKnown = true;
		}
	}

	// Accessors below are useful for tests/inspection; state remains
	// otherwise fully encapsulated.

	public long getBaseline()
	{
		return baseline;
	}

	public boolean isBankOpen()
	{
		return bankOpen;
	}

	public List<LootEntry> getLoot()
	{
		return Collections.unmodifiableList(lootSummary());
	}

	public boolean isStartBankKnown()
	{
		return startBankKnown;
	}
}
