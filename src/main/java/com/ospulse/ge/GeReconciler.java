package com.ospulse.ge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure reconciler for Grand Exchange offers.
 *
 * <p>Maintains a per-item weighted-average-cost ledger so it can compute
 * realised flip P&amp;L, and a quantity-accurate expectation ledger (see
 * {@link GeAttributions}) of tracked-item movements the GE accounts for, so
 * the session engine can exclude them from the loot/supplies feeds
 * (buying/selling isn't loot).
 *
 * <p>The expectation ledger exists because a GE trade's goods do NOT enter
 * the inventory when the offer fills: they sit in the collection box —
 * potentially indefinitely — until the player collects them. Each fill
 * therefore records an expected ARRIVAL (bought items; sale proceeds net of
 * tax; buy-refund coins when a buy filled under the offer price;
 * cancellation refunds), and each sell-offer placement records an expected
 * REMOVAL (the offered stack leaves the inventory at placement). Arrivals
 * persist while their offer still occupies its slot; once the slot clears
 * (the player collected — to inventory or straight to bank) any remainder is
 * only kept for a short settle window, so a stale expectation can never
 * swallow genuine loot of the same item id much beyond the collection
 * moment.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class GeReconciler implements GeAttributions
{
	/** Canonical item id of coins (sale proceeds / escrow refunds). */
	public static final int COINS_ITEM_ID = 995;

	/**
	 * How long (ms) an expectation stays consumable once its settling
	 * inventory movement is due "now": for an arrival, measured from the
	 * moment its offer's slot clears (the collection instant — the matching
	 * inventory rise lands within a tick or two unless the player collected
	 * to bank, in which case it never lands and the leftover must die); for a
	 * removal, measured from the sell-offer placement that removed the stack
	 * in the same tick batch. Mirrors the engine's bank-side settle window:
	 * generous against event-ordering lag, small enough to bound how long
	 * genuine same-id loot could be misattributed.
	 */
	private static final long ATTRIBUTION_SETTLE_WINDOW_MS = 10_000L;

	private static final class SlotTracker
	{
		int itemId;
		long lastQuantityTransacted;
		long lastGpTransacted;
		GeOfferState lastState;
	}

	/**
	 * Expected inventory arrival from GE activity: items bought, coin
	 * proceeds of a sale, or a cancellation/underprice refund — awaiting
	 * collection. {@code deadlineMs} is 0 while the offer still occupies its
	 * slot (the collection box holds goods indefinitely); once the slot
	 * clears it is stamped with a settle deadline (see
	 * {@link #ATTRIBUTION_SETTLE_WINDOW_MS}).
	 */
	private static final class ExpectedArrival
	{
		final int slot;
		final int itemId;
		long quantity;
		long deadlineMs;

		ExpectedArrival(int slot, int itemId, long quantity)
		{
			this.slot = slot;
			this.itemId = itemId;
			this.quantity = quantity;
		}
	}

	/**
	 * Expected inventory removal from GE activity: a sell offer's stack
	 * leaving the inventory at placement (observed in the same tick batch as
	 * the placement event, so a short settle window suffices).
	 */
	private static final class ExpectedRemoval
	{
		final int itemId;
		long quantity;
		final long tsMs;

		ExpectedRemoval(int itemId, long quantity, long tsMs)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.tsMs = tsMs;
		}
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
	/** FIFO expectation ledgers — see {@link ExpectedArrival}/{@link ExpectedRemoval}. */
	private final List<ExpectedArrival> expectedArrivals = new ArrayList<>();
	private final List<ExpectedRemoval> expectedRemovals = new ArrayList<>();
	private long realizedPnl;
	/** Loot-sales (non-flip sells) observed since the last drain. */
	private final List<LootSale> lootSales = new ArrayList<>();

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
			// The slot cleared: whatever this offer still had awaiting
			// collection has just been claimed (to inventory, whose arrival
			// lands within the settle window, or straight to bank, in which
			// case no tracked arrival will ever come and the leftover must
			// expire rather than linger to swallow genuine loot).
			stampSlotCleared(slot, tsMs);
			slots.remove(slot);
			return;
		}

		SlotTracker tracker = slots.get(slot);
		boolean newOffer = tracker == null
			|| tracker.itemId != itemId
			// quantityTransacted never regresses within one offer, so a
			// regression means the slot was cleared and reused for a new
			// offer of the same item without an observed EMPTY in between.
			|| quantityTransacted < tracker.lastQuantityTransacted;
		if (newOffer)
		{
			if (tracker != null)
			{
				// Slot reused without an observed EMPTY: the previous
				// offer's collectables were claimed at some earlier instant.
				stampSlotCleared(slot, tsMs);
			}
			tracker = new SlotTracker();
			tracker.itemId = itemId;
			tracker.lastQuantityTransacted = 0L;
			tracker.lastGpTransacted = 0L;
			slots.put(slot, tracker);
			if (state == GeOfferState.SELLING || state == GeOfferState.SOLD)
			{
				// Sell placement: the whole offered stack left the inventory
				// the instant the offer went up (observed in the same tick
				// batch as this event) — that removal is a GE transfer, not
				// supplies consumed.
				addRemoval(itemId, totalQuantity, tsMs);
			}
		}

		long incremental = quantityTransacted - tracker.lastQuantityTransacted;
		if (incremental < 0)
		{
			// Defensive: never seen going backwards in practice, but don't
			// let it corrupt the ledger.
			incremental = 0;
		}
		long incrementalGp = gpTransacted - tracker.lastGpTransacted;
		if (incrementalGp < 0)
		{
			incrementalGp = 0;
		}
		tracker.lastQuantityTransacted = Math.max(tracker.lastQuantityTransacted, quantityTransacted);
		tracker.lastGpTransacted = Math.max(tracker.lastGpTransacted, gpTransacted);
		GeOfferState previousState = tracker.lastState;
		tracker.lastState = state;

		if (incremental > 0)
		{
			switch (state)
			{
				case BUYING:
				case BOUGHT:
					applyBuy(itemId, incremental, pricePerItem);
					addArrival(slot, itemId, incremental);
					// A buy that filled under the offer price refunds the
					// difference in coins (collectable alongside the items).
					// Only trusted when the event carries real gp movement —
					// a zero gpTransacted must not fabricate a full-escrow
					// coin expectation.
					long refund = incremental * pricePerItem - incrementalGp;
					if (incrementalGp > 0 && refund > 0)
					{
						addArrival(slot, COINS_ITEM_ID, refund);
					}
					break;
				case SELLING:
				case SOLD:
					long matched = applySell(itemId, incremental, pricePerItem);
					// The collectable proceeds are the gp actually transacted (a
					// sell can fill ABOVE the offer price) minus the per-item
					// tax.
					long gross = incrementalGp > 0 ? incrementalGp : incremental * pricePerItem;
					long netProceeds = gross - saleTaxPerItem(itemId, gross / incremental) * incremental;
					if (netProceeds > 0)
					{
						addArrival(slot, COINS_ITEM_ID, netProceeds);
					}
					// Any quantity sold beyond the GE buy basis is a loot-sale:
					// surface its proportional after-tax proceeds to realise into Loot.
					long lootQty = incremental - matched;
					if (lootQty > 0)
					{
						long lootProceeds = Math.round((double) netProceeds * lootQty / incremental);
						lootSales.add(new LootSale(itemId, lootQty, lootProceeds));
					}
					break;
				default:
					// Any quantity actually filled before cancellation was
					// already handled on the BUYING/SELLING updates that
					// carried it; cancellation itself fills nothing further.
					break;
			}
		}

		// Cancellation returns the unfilled remainder to the collection box:
		// the escrowed coins of a buy, the unsold items of a sell. Attributed
		// exactly once, on the transition into the cancelled state.
		if (state == GeOfferState.CANCELLED_BUY && previousState != GeOfferState.CANCELLED_BUY)
		{
			long refund = Math.max(0L, totalQuantity - quantityTransacted) * pricePerItem;
			if (refund > 0)
			{
				addArrival(slot, COINS_ITEM_ID, refund);
			}
		}
		else if (state == GeOfferState.CANCELLED_SELL && previousState != GeOfferState.CANCELLED_SELL)
		{
			long remaining = Math.max(0L, totalQuantity - quantityTransacted);
			if (remaining > 0)
			{
				addArrival(slot, itemId, remaining);
			}
		}
	}

	/**
	 * Seeds a slot's incremental tracking from an offer that predates the
	 * session (login burst / session reset while offers are live) WITHOUT
	 * recording any attribution or flip P&amp;L for its already-transacted
	 * portion: pre-session fills are not session activity, and attributing
	 * them would both resurrect pre-session realised P&amp;L and plant
	 * phantom arrival expectations that could swallow genuine loot.
	 */
	public void primeSlot(int slot, GeOfferState state, int itemId, long quantityTransacted, long gpTransacted)
	{
		if (state == null || state == GeOfferState.EMPTY)
		{
			slots.remove(slot);
			return;
		}
		SlotTracker tracker = new SlotTracker();
		tracker.itemId = itemId;
		tracker.lastQuantityTransacted = Math.max(0L, quantityTransacted);
		tracker.lastGpTransacted = Math.max(0L, gpTransacted);
		tracker.lastState = state;
		slots.put(slot, tracker);
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

	private long applySell(int itemId, long qty, long pricePerItem)
	{
		CostBasis basis = costBasis.get(itemId);
		if (basis == null || basis.qty <= 0)
		{
			// Selling with no GE cost basis (dumping looted or previously
			// owned items, not flipping) is not a flip: nothing matched.
			return 0L;
		}

		// Only the quantity actually bought via the GE counts as a flip.
		long matched = Math.min(qty, basis.qty);

		// The seller nets the sale price minus the GE's per-item sales tax.
		long netProceedsPerItem = pricePerItem - saleTaxPerItem(itemId, pricePerItem);
		realizedPnl += (netProceedsPerItem - basis.avgUnitCost) * matched;

		basis.qty -= matched;
		if (basis.qty == 0)
		{
			basis.avgUnitCost = 0;
		}
		return matched;
	}

	/**
	 * Total realised flip P&amp;L across all items observed so far.
	 */
	public long realizedPnl()
	{
		return realizedPnl;
	}

	/**
	 * Returns and clears the loot-sales observed since the last call — see
	 * {@link LootSale}.
	 */
	@Override
	public List<LootSale> drainLootSales()
	{
		if (lootSales.isEmpty())
		{
			return java.util.Collections.emptyList();
		}
		List<LootSale> out = new ArrayList<>(lootSales);
		lootSales.clear();
		return out;
	}

	/**
	 * Total value still awaiting collection in the Grand Exchange collection box:
	 * the sum of every live expected arrival (bought items, sale-proceed coins,
	 * under-price/cancellation refunds) whose offer slot is still occupied
	 * ({@code deadlineMs == 0}), valued via {@code unitValue} (coins are valued at
	 * 1 gp each regardless of what the lookup returns).
	 *
	 * <p>This is the wealth that has definitively left the inventory/bank into a
	 * sell offer (or the escrow of a buy) and is now sitting as collectable goods
	 * or proceeds — value the {@link com.ospulse.integration.GeValuation}
	 * "still-locked" number deliberately omits, which is why net worth otherwise
	 * dips the instant a sell fills and only recovers when the coins are collected.
	 * Folding it back into tracked wealth closes that gap.
	 *
	 * <p>Counting only slot-occupied ({@code deadlineMs == 0}) arrivals is what
	 * keeps this double-count-free: the moment a slot clears (the player
	 * collected — see {@link #stampSlotCleared}) its arrivals are stamped with a
	 * settle deadline and drop out of this total, handing the value straight back
	 * to inventory/bank accounting. Because each arrival is also decremented by
	 * the actual observed inventory arrival ({@link #attributeArrival}), a partial
	 * collect is reflected the instant it lands. The only residual overlap is the
	 * single tick on which a collected stack is already in the inventory container
	 * but its arrival has not yet been consumed by the engine's per-tick diff — a
	 * benign transient that self-corrects on the next tick (the engine re-anchors
	 * its baseline every tick), never a persistent phantom gain.
	 *
	 * @param unitValue itemId -&gt; gp value per unit
	 */
	public long collectableValue(java.util.function.IntToLongFunction unitValue)
	{
		long total = 0L;
		for (ExpectedArrival e : expectedArrivals)
		{
			if (e.deadlineMs != 0 || e.quantity <= 0)
			{
				continue;
			}
			long unit = e.itemId == COINS_ITEM_ID ? 1L : unitValue.applyAsLong(e.itemId);
			total += e.quantity * unit;
		}
		return total;
	}

	/**
	 * Clears all per-session state (open-slot tracking, cost-basis ledger,
	 * expectation ledgers and realised P&amp;L) so a new tracking window
	 * starts from zero. Must be called whenever the session is reset or
	 * re-bootstrapped (manual reset button, login/relogin) — otherwise
	 * realised flip P&amp;L carries over into the "new" session and keeps
	 * accumulating, unlike every other per-session stat. Live offers should
	 * be re-seeded via {@link #primeSlot} right after, so their pre-session
	 * fills are never re-counted as fresh movement.
	 */
	public void reset()
	{
		slots.clear();
		costBasis.clear();
		expectedArrivals.clear();
		expectedRemovals.clear();
		realizedPnl = 0L;
		lootSales.clear();
	}

	/**
	 * Drops expectations whose settle window has lapsed: arrivals whose slot
	 * cleared more than {@link #ATTRIBUTION_SETTLE_WINDOW_MS} ago (collected
	 * to bank, or an estimate's residue), and removals older than the same
	 * window (the placement-time inventory drop either arrived long ago or
	 * never will). Call once per tick, before the engine consumes.
	 */
	public void expireAttributions(long tsMs)
	{
		Iterator<ExpectedArrival> arrivals = expectedArrivals.iterator();
		while (arrivals.hasNext())
		{
			ExpectedArrival e = arrivals.next();
			if (e.deadlineMs != 0 && tsMs > e.deadlineMs)
			{
				arrivals.remove();
			}
		}
		Iterator<ExpectedRemoval> removals = expectedRemovals.iterator();
		while (removals.hasNext())
		{
			if (tsMs - removals.next().tsMs > ATTRIBUTION_SETTLE_WINDOW_MS)
			{
				removals.remove();
			}
		}
	}

	@Override
	public long attributeArrival(int itemId, long quantity)
	{
		long consumed = 0L;
		Iterator<ExpectedArrival> it = expectedArrivals.iterator();
		while (it.hasNext() && consumed < quantity)
		{
			ExpectedArrival e = it.next();
			if (e.itemId != itemId)
			{
				continue;
			}
			long take = Math.min(e.quantity, quantity - consumed);
			e.quantity -= take;
			consumed += take;
			if (e.quantity == 0)
			{
				it.remove();
			}
		}
		return consumed;
	}

	@Override
	public long attributeRemoval(int itemId, long quantity)
	{
		long consumed = 0L;
		Iterator<ExpectedRemoval> it = expectedRemovals.iterator();
		while (it.hasNext() && consumed < quantity)
		{
			ExpectedRemoval e = it.next();
			if (e.itemId != itemId)
			{
				continue;
			}
			long take = Math.min(e.quantity, quantity - consumed);
			e.quantity -= take;
			consumed += take;
			if (e.quantity == 0)
			{
				it.remove();
			}
		}
		return consumed;
	}

	/**
	 * Adds (or merges into this slot's live entry for the item) an expected
	 * GE arrival — see {@link ExpectedArrival}.
	 */
	private void addArrival(int slot, int itemId, long quantity)
	{
		if (quantity <= 0)
		{
			return;
		}
		for (ExpectedArrival e : expectedArrivals)
		{
			if (e.slot == slot && e.itemId == itemId && e.deadlineMs == 0)
			{
				e.quantity += quantity;
				return;
			}
		}
		expectedArrivals.add(new ExpectedArrival(slot, itemId, quantity));
	}

	private void addRemoval(int itemId, long quantity, long tsMs)
	{
		if (quantity <= 0)
		{
			return;
		}
		expectedRemovals.add(new ExpectedRemoval(itemId, quantity, tsMs));
	}

	/**
	 * Marks every still-live arrival expectation of {@code slot} as
	 * collected NOW: the goods were just claimed somewhere, so the matching
	 * inventory arrival must land within the settle window or never will.
	 */
	private void stampSlotCleared(int slot, long tsMs)
	{
		for (ExpectedArrival e : expectedArrivals)
		{
			if (e.slot == slot && e.deadlineMs == 0)
			{
				e.deadlineMs = tsMs + ATTRIBUTION_SETTLE_WINDOW_MS;
			}
		}
	}
}
