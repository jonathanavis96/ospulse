package com.ospulse.ge;

import java.util.Collections;
import java.util.List;

/**
 * Quantity-accurate attribution of tracked-item movements to Grand Exchange
 * activity, consumed by the session engine while classifying per-item
 * quantity swings.
 *
 * <p>A GE trade's two halves land in different observations: an offer fills
 * (the reconciler learns items/coins are now collectable) and only later —
 * possibly many seconds or minutes later, since a completed offer sits in the
 * collection box until the player collects it — do the goods actually enter
 * the inventory. The engine therefore cannot rely on a same-tick "this id was
 * GE-traded" signal; instead it asks this ledger, at the moment it observes a
 * quantity swing, how much of that swing the GE accounts for. Attribution is
 * quantity-capped: only the outstanding expected amount for an id is ever
 * consumed, so genuine loot of an item that happens to share an id with a
 * recent GE trade still counts as loot once the expected quantity is
 * exhausted.
 */
public interface GeAttributions
{
	/**
	 * Attributes up to {@code quantity} units of an observed INCREASE of
	 * {@code itemId} in tracked wealth to outstanding GE arrivals (collected
	 * buys, sale-proceed coins, cancellation refunds), consuming what it
	 * attributes from the ledger.
	 *
	 * @return the quantity attributed (0..quantity); the caller classifies
	 *         only the remainder as loot.
	 */
	long attributeArrival(int itemId, long quantity);

	/**
	 * Attributes up to {@code quantity} units of an observed DECREASE of
	 * {@code itemId} in tracked wealth to outstanding GE removals (a sell
	 * offer's stack leaving the inventory at placement), consuming what it
	 * attributes from the ledger.
	 *
	 * @return the quantity attributed (0..quantity); the caller classifies
	 *         only the remainder as supplies/vanish.
	 */
	long attributeRemoval(int itemId, long quantity);

	/**
	 * Drains Grand Exchange loot-sales observed since the last call — sells of
	 * items with no matching GE buy basis (looted / previously owned), whose
	 * after-tax proceeds the session engine realises into the Loot figure.
	 * Default: none (non-reconciler attribution sources have no loot-sales).
	 */
	default List<LootSale> drainLootSales()
	{
		return Collections.emptyList();
	}
}
