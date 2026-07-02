package com.ospulse.session;

import com.ospulse.ge.GeOfferView;
import com.ospulse.model.ItemStack;
import com.ospulse.wealth.WealthSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core banking-aware wealth-delta session tracker.
 *
 * <p>"Tracked wealth" (see {@link WealthSnapshot#tracked()}) is inventory +
 * equipment + GE-in-flight + pouches. While away from a bank, any change in
 * tracked wealth is real profit/loss. Bank visits are transfers of cold
 * storage into/out of tracked wealth and must not register as profit, so a
 * bank deposit/withdrawal shifts the profit {@code baseline} by exactly the
 * amount that moved, neutralising it.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class SessionEngine
{
	/**
	 * Loot list is kept most-recent-first and bounded to this many entries.
	 */
	private static final int MAX_LOOT_ENTRIES = 200;

	/** Tracked wealth that currently defines zero profit. */
	private long baseline;
	private long startMs;
	private boolean bankOpen;
	private long trackedAtBankOpen;
	private WealthSnapshot previous;
	private final LinkedList<LootEntry> loot = new LinkedList<>();
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
		this.loot.clear();
	}

	/**
	 * Reports a bank-open/close transition. On open, records the tracked
	 * wealth at that instant. On close, shifts the baseline by however much
	 * tracked wealth moved while the bank was open, so deposits/withdrawals
	 * never register as profit or loss. No loot diffing happens while the
	 * bank is open (see {@link #update}).
	 */
	public void setBankOpen(boolean open, WealthSnapshot current, long tsMs)
	{
		if (!this.bankOpen && open)
		{
			// FALSE -> TRUE: bank just opened.
			this.trackedAtBankOpen = current.tracked();
		}
		else if (this.bankOpen && !open)
		{
			// TRUE -> FALSE: bank just closed. Neutralise whatever moved
			// in/out of tracked wealth while the bank was open.
			this.baseline += current.tracked() - trackedAtBankOpen;
		}

		this.previous = current;
		this.bankOpen = open;
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
		loot.addFirst(entry);
		while (loot.size() > MAX_LOOT_ENTRIES)
		{
			loot.removeLast();
		}
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
		return snapshot(current, geRealizedPnl, Collections.emptyList(), xpGained, xpTotal, tsMs);
	}

	/**
	 * Produces a read-only snapshot of the session's current state given the
	 * latest wealth reading and externally-tracked GE/XP data.
	 */
	public SessionSnapshot snapshot(
		WealthSnapshot current,
		long geRealizedPnl,
		List<GeOfferView> geOffers,
		Map<String, Long> xpGained,
		long xpTotal,
		long tsMs)
	{
		foldNewlyKnownBankIntoStart(current);

		long profit = current.tracked() - baseline;
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
			new ArrayList<>(loot),
			xpGained,
			xpTotal,
			current,
			geOffers);
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
		return Collections.unmodifiableList(new ArrayList<>(loot));
	}

	public boolean isStartBankKnown()
	{
		return startBankKnown;
	}
}
