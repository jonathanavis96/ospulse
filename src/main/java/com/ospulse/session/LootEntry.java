package com.ospulse.session;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Immutable record of a single loot event detected via wealth-delta diffing
 * while away from a bank.
 */
@Getter
@EqualsAndHashCode
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
