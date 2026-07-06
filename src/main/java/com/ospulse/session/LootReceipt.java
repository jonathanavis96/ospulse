package com.ospulse.session;

/** A single item gained via a RuneLite LootReceived event this tick. */
public final class LootReceipt
{
	public final int itemId;
	public final long quantity;
	public final long unitValue;

	public LootReceipt(int itemId, long quantity, long unitValue)
	{
		this.itemId = itemId;
		this.quantity = quantity;
		this.unitValue = unitValue;
	}
}
