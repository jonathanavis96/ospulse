package com.ospulse.ge;

import java.util.HashMap;
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

	private final Map<Integer, SlotTracker> slots = new HashMap<>();
	private final Map<Integer, CostBasis> costBasis = new HashMap<>();
	private final Set<Integer> attributedItemIds = new LinkedHashSet<>();
	private long realizedPnl;

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
		realizedPnl += (pricePerItem - basis.avgUnitCost) * matched;

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
