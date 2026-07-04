package com.ospulse.ge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure reconciler for Grand Exchange offers.
 *
 * <p>Maintains a per-item weighted-average-cost ledger so it can compute
 * realised flip P&amp;L, and tracks which item ids had GE-driven quantity
 * movement since the last drain, so the integration layer can exclude those
 * ids from the loot feed (buying/selling isn't loot).
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class GeReconciler
{
	private static final class SlotTracker
	{
		int itemId;
		long lastQuantityTransacted;
	}

	private static final class CostBasis
	{
		long qty;
		long avgUnitCost;
	}

	/**
	 * Per-item Grand Exchange sales tax, matching the live game: 2% of the sale
	 * price per item (raised from 1% on 29 May 2025), rounded down, capped at
	 * {@link #GE_TAX_CAP_PER_ITEM} gp per item. Because it rounds down, any item
	 * sold below 50 gp incurs less than 1 gp of tax and so pays nothing — that
	 * threshold falls out of the integer division and needs no special case.
	 * Integer {@code price / 50} is exactly {@code floor(price * 0.02)} and
	 * avoids floating-point drift on large prices.
	 */
	private static final long GE_TAX_DIVISOR = 50L; // 1/50 == 2%
	private static final long GE_TAX_CAP_PER_ITEM = 5_000_000L;

	/**
	 * Items the Grand Exchange never taxes regardless of price. The full live
	 * list (OSRS Wiki "Items exempt from Grand Exchange tax" category) is large
	 * and grows over time — mostly cheap, high-volume items (runes, food, early
	 * ammo, tools) where 2% is negligible to flip P&amp;L. This curated set
	 * carries the only items where omitting the exemption would be materially
	 * wrong: the Old school bond (id 13190), a routinely-flipped ~8M item.
	 * Extend with one id per entry if other high-value exempt items appear.
	 */
	private static final Set<Integer> TAX_EXEMPT_ITEM_IDS = new HashSet<>(Arrays.asList(
		13190 // Old school bond (tradeable)
	));

	private final Map<Integer, SlotTracker> slots = new HashMap<>();
	private final Map<Integer, CostBasis> costBasis = new HashMap<>();
	private final Set<Integer> attributedItemIds = new LinkedHashSet<>();
	private long realizedPnl;

	/**
	 * The per-item GE sales tax on a sale of {@code itemId} at
	 * {@code pricePerItem}: 2% floored, capped at 5M/item, zero for tax-exempt
	 * items and for anything whose 2% rounds down to nothing (sub-50 gp).
	 */
	static long saleTaxPerItem(int itemId, long pricePerItem)
	{
		if (pricePerItem <= 0 || TAX_EXEMPT_ITEM_IDS.contains(itemId))
		{
			return 0L;
		}
		return Math.min(pricePerItem / GE_TAX_DIVISOR, GE_TAX_CAP_PER_ITEM);
	}

	/**
	 * Feeds an offer update into the reconciler.
	 *
	 * @param slot                the GE offer slot index (0-7 typically)
	 * @param state                current lifecycle state of the offer
	 * @param itemId               item id being traded
	 * @param itemName             item name (unused for math, kept for API symmetry)
	 * @param totalQuantity        total quantity of the offer (unused for math, kept for API symmetry)
	 * @param quantityTransacted   cumulative quantity transacted so far on this offer
	 * @param gpTransacted         cumulative gp transacted so far (unused; pricePerItem drives the ledger)
	 * @param pricePerItem         price per item for this offer
	 * @param tsMs                 event timestamp (unused for math, kept for API symmetry)
	 */
	public void onOfferUpdate(
		int slot,
		GeOfferState state,
		int itemId,
		String itemName,
		long totalQuantity,
		long quantityTransacted,
		long gpTransacted,
		long pricePerItem,
		long tsMs)
	{
		if (state == GeOfferState.EMPTY)
		{
			slots.remove(slot);
			return;
		}

		SlotTracker tracker = slots.get(slot);
		if (tracker == null || tracker.itemId != itemId)
		{
			// New offer occupying this slot (or a stale/first observation):
			// reset incremental tracking.
			tracker = new SlotTracker();
			tracker.itemId = itemId;
			tracker.lastQuantityTransacted = 0L;
			slots.put(slot, tracker);
		}

		long incremental = quantityTransacted - tracker.lastQuantityTransacted;
		if (incremental < 0)
		{
			// Defensive: never seen going backwards in practice, but don't
			// let it corrupt the ledger.
			incremental = 0;
		}
		tracker.lastQuantityTransacted = Math.max(tracker.lastQuantityTransacted, quantityTransacted);

		if (incremental <= 0)
		{
			return;
		}

		switch (state)
		{
			case BUYING:
			case BOUGHT:
				applyBuy(itemId, incremental, pricePerItem);
				attributedItemIds.add(itemId);
				break;
			case SELLING:
			case SOLD:
				applySell(itemId, incremental, pricePerItem);
				attributedItemIds.add(itemId);
				break;
			case CANCELLED_BUY:
			case CANCELLED_SELL:
			default:
				// Any quantity actually filled before cancellation was
				// already attributed on the BUYING/SELLING updates that
				// carried it; cancellation itself moves nothing further.
				break;
		}
	}

	private void applyBuy(int itemId, long qty, long pricePerItem)
	{
		CostBasis basis = costBasis.computeIfAbsent(itemId, k -> new CostBasis());
		long newQty = basis.qty + qty;
		if (newQty <= 0)
		{
			basis.qty = 0;
			basis.avgUnitCost = 0;
			return;
		}
		basis.avgUnitCost = (basis.qty * basis.avgUnitCost + qty * pricePerItem) / newQty;
		basis.qty = newQty;
	}

	private void applySell(int itemId, long qty, long pricePerItem)
	{
		CostBasis basis = costBasis.get(itemId);
		if (basis == null || basis.qty <= 0)
		{
			// Selling with no GE cost basis (e.g. dumping looted or previously
			// owned items on the GE, not flipping) is not a flip and must not
			// count towards flip P&L — that value is already captured in the
			// wealth-delta profit number.
			return;
		}

		// Only the quantity that was actually bought via the GE counts as a
		// flip; any excess sold beyond the tracked basis is not flip profit.
		long matched = Math.min(qty, basis.qty);

		// The seller nets the sale price minus the GE's per-item sales tax, so
		// realised flip profit is (net proceeds - avg cost). Without this the
		// stat overstates profit by the tax the player actually paid.
		long netProceedsPerItem = pricePerItem - saleTaxPerItem(itemId, pricePerItem);
		realizedPnl += (netProceedsPerItem - basis.avgUnitCost) * matched;

		basis.qty -= matched;
		if (basis.qty == 0)
		{
			basis.avgUnitCost = 0;
		}
	}

	/**
	 * Total realised flip P&amp;L across all items observed so far.
	 */
	public long realizedPnl()
	{
		return realizedPnl;
	}

	/**
	 * Clears all per-session state (open-slot tracking, cost-basis ledger,
	 * attributed ids and realised P&amp;L) so a new tracking window starts from
	 * zero. Must be called whenever the session is reset or re-bootstrapped
	 * (manual reset button, login/relogin) — otherwise realised flip P&amp;L
	 * carries over into the "new" session and keeps accumulating, unlike every
	 * other per-session stat.
	 */
	public void reset()
	{
		slots.clear();
		costBasis.clear();
		attributedItemIds.clear();
		realizedPnl = 0L;
	}

	/**
	 * Returns the set of item ids that had GE-driven quantity movement since
	 * the last drain, and clears that set.
	 */
	public Set<Integer> drainAttributedItemIds()
	{
		Set<Integer> drained = new LinkedHashSet<>(attributedItemIds);
		attributedItemIds.clear();
		return drained;
	}
}
