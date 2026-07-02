package com.phatwang.session;

import java.util.Objects;

/**
 * Immutable record of a single loot event detected via wealth-delta diffing
 * while away from a bank.
 */
public final class LootEntry
{
	private final int itemId;
	private final String name;
	private final long quantity;
	private final long value;
	private final long timestampMs;

	public LootEntry(int itemId, String name, long quantity, long value, long timestampMs)
	{
		this.itemId = itemId;
		this.name = name;
		this.quantity = quantity;
		this.value = value;
		this.timestampMs = timestampMs;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getName()
	{
		return name;
	}

	public long getQuantity()
	{
		return quantity;
	}

	public long getValue()
	{
		return value;
	}

	public long getTimestampMs()
	{
		return timestampMs;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof LootEntry))
		{
			return false;
		}
		LootEntry lootEntry = (LootEntry) o;
		return itemId == lootEntry.itemId
			&& quantity == lootEntry.quantity
			&& value == lootEntry.value
			&& timestampMs == lootEntry.timestampMs
			&& Objects.equals(name, lootEntry.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(itemId, name, quantity, value, timestampMs);
	}

	@Override
	public String toString()
	{
		return "LootEntry{"
			+ "itemId=" + itemId
			+ ", name='" + name + '\''
			+ ", quantity=" + quantity
			+ ", value=" + value
			+ ", timestampMs=" + timestampMs
			+ '}';
	}
}
