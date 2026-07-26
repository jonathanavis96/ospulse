package com.ospulse.session;

/** A single item gained via a RuneLite LootReceived event this tick. */
public final class LootReceipt
{
	public final int itemId;
	public final long quantity;
	public final long unitValue;
	/**
	 * True when this receipt is already known to route to a non-readable storage
	 * container that never touches the inventory diff — the fish barrel's inferred
	 * catches (see {@code FishBarrelTracker}), which fire no real LootReceived
	 * event. Such a receipt is eligible for the stored-loot ledger regardless of
	 * {@code SessionEngine.SACK_ROUTED_ITEM_IDS}; a plain (false) receipt for a
	 * non-sack id is not, so an over-reported quantity can never seed a phantom
	 * balance. The same-tick inventory-landing check still applies as a safety net.
	 */
	public final boolean storageRouted;
	/**
	 * What RuneLite said produced this loot (NPC/boss/activity name), or null
	 * when the receipt has no source — an inferred receipt (the fish barrel) or
	 * an event RuneLite raised with an empty source. Carried so a caller
	 * reconciling the inventory diff against this tick's receipts can tell a
	 * matched appearance apart from one nothing reported, and name it accordingly.
	 */
	public final String source;

	public LootReceipt(int itemId, long quantity, long unitValue)
	{
		this(itemId, quantity, unitValue, false, null);
	}

	public LootReceipt(int itemId, long quantity, long unitValue, boolean storageRouted)
	{
		this(itemId, quantity, unitValue, storageRouted, null);
	}

	public LootReceipt(int itemId, long quantity, long unitValue, boolean storageRouted, String source)
	{
		this.itemId = itemId;
		this.quantity = quantity;
		this.unitValue = unitValue;
		this.storageRouted = storageRouted;
		this.source = source;
	}
}
