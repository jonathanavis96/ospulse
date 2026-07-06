package com.ospulse.ge;

/**
 * A Grand Exchange sale of items the player did NOT buy on the GE (looted or
 * previously owned): the after-tax proceeds to realise into the session Loot
 * figure. A sale matched against a GE buy basis is a flip and never produces one.
 */
public final class LootSale
{
	public final int itemId;
	public final long quantity;
	public final long netProceeds;

	public LootSale(int itemId, long quantity, long netProceeds)
	{
		this.itemId = itemId;
		this.quantity = quantity;
		this.netProceeds = netProceeds;
	}
}
