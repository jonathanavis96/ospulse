package com.ospulse.wealth;

import lombok.Getter;
import com.ospulse.model.ItemStack;

import java.util.*;

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
	@Getter private final long timestampMs;
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

	public List<Entry> getItems()
	{
		return items == null ? Collections.emptyList() : items;
	}

	/** A single cached bank line: canonical item id and quantity. */
	public static final class Entry
	{
		@Getter private final int id;
		@Getter private final long quantity;

		public Entry(int id, long quantity)
		{
			this.id = id;
			this.quantity = quantity;
		}

	}
}
