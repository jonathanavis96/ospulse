package com.ospulse.wealth;

import com.ospulse.model.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Serializable snapshot of a player's last-observed bank contents, persisted
 * per-account so the bank value, net worth and top holdings survive a relog or
 * client restart without the player having to reopen the bank.
 *
 * <p>Only item id + quantity are stored; values are recomputed from live GE
 * prices on load, so a cached bank stays correctly priced even weeks later.
 * Pure data type: Gson-friendly, no RuneLite imports.
 */
public final class BankCache
{
	private final long timestampMs;
	private final List<Entry> items;

	public BankCache(long timestampMs, Collection<ItemStack> stacks)
	{
		this.timestampMs = timestampMs;
		this.items = new ArrayList<>();
		if (stacks != null)
		{
			for (ItemStack s : stacks)
			{
				if (s != null && s.getId() > 0 && s.getQuantity() > 0)
				{
					this.items.add(new Entry(s.getId(), s.getQuantity()));
				}
			}
		}
	}

	public long getTimestampMs()
	{
		return timestampMs;
	}

	public List<Entry> getItems()
	{
		return items == null ? Collections.emptyList() : items;
	}

	/** A single cached bank line: canonical item id and quantity. */
	public static final class Entry
	{
		private final int id;
		private final long quantity;

		public Entry(int id, long quantity)
		{
			this.id = id;
			this.quantity = quantity;
		}

		public int getId()
		{
			return id;
		}

		public long getQuantity()
		{
			return quantity;
		}
	}
}
