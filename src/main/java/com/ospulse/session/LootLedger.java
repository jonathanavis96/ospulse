package com.ospulse.session;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bottom-up accounting for the Session panel's "Loot" line: the value of items
 * the player gained this session, held at pickup value and swapped to the actual
 * after-tax Grand Exchange sale proceeds once sold.
 *
 * <p>Location-independent: a looted item counts as held whether it sits in the
 * inventory or the bank, so banking is a no-op here. Consuming a looted item is
 * also a no-op — it stays counted as loot and its spend surfaces via
 * {@code suppliesUsed}, so Profit = Loot - Supplies nets it out.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class LootLedger
{
	/** Held (looted, not-yet-sold) quantity of an item and the pickup value of that quantity. */
	private static final class Held
	{
		long quantity;
		long value;
	}

	private final Map<Integer, Held> held = new LinkedHashMap<>();
	private long realised;

	/** Records a classified loot gain of {@code quantity} units at {@code unitPickupValue} each. */
	public void recordLoot(int itemId, long quantity, long unitPickupValue)
	{
		if (quantity <= 0)
		{
			return;
		}
		Held h = held.computeIfAbsent(itemId, k -> new Held());
		h.quantity += quantity;
		h.value += quantity * unitPickupValue;
	}

	/**
	 * Reverses up to {@code quantity} units of previously recorded loot (a drop,
	 * equip-transient, or returned-stack reversal), removing value at the current
	 * average and dropping the entry once empty. Returns the quantity actually
	 * reversed — 0 if none of it was held as loot (e.g. dropping a never-looted,
	 * pre-owned item) — so a caller can restore exactly that much if the units
	 * legitimately return.
	 */
	public long reverseLoot(int itemId, long quantity)
	{
		if (quantity <= 0)
		{
			return 0L;
		}
		Held h = held.get(itemId);
		if (h == null || h.quantity <= 0)
		{
			return 0L;
		}
		long remove = Math.min(quantity, h.quantity);
		long removeValue = Math.round((double) h.value * remove / h.quantity);
		h.quantity -= remove;
		h.value -= removeValue;
		if (h.quantity <= 0)
		{
			held.remove(itemId);
		}
		return remove;
	}

	/**
	 * Realises a Grand Exchange loot-sale: {@code quantity} units of {@code itemId}
	 * sold for {@code netProceeds} gp total (after GE tax). Caps at the held
	 * quantity so selling more than was looted (pre-owned excess) never inflates
	 * loot; only the looted portion's proportional proceeds are realised.
	 */
	public void realiseSale(int itemId, long quantity, long netProceeds)
	{
		if (quantity <= 0)
		{
			return;
		}
		Held h = held.get(itemId);
		if (h == null || h.quantity <= 0)
		{
			return;
		}
		long realiseQty = Math.min(quantity, h.quantity);
		long removeValue = Math.round((double) h.value * realiseQty / h.quantity);
		h.quantity -= realiseQty;
		h.value -= removeValue;
		realised += Math.round((double) netProceeds * realiseQty / quantity);
		if (h.quantity <= 0)
		{
			held.remove(itemId);
		}
	}

	/** The Loot number: still-held loot at pickup value + realised after-tax sale proceeds. */
	public long lootValue()
	{
		long total = realised;
		for (Held h : held.values())
		{
			total += h.value;
		}
		return total;
	}

	/** Clears all state for a new session. */
	public void reset()
	{
		held.clear();
		realised = 0L;
	}
}
