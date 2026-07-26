package com.ospulse.session;

/**
 * An unexplained inventory appearance that {@link SessionEngine}'s per-update
 * diff booked as Loot — the residue that survived GE attribution, transfer
 * pairing, reversal netting, ground/Death returns, storage draws and the
 * purchase guard. Reported per update via {@link SessionEngine#lastUpdateDiffLoot()}
 * so the tracker can attribute it to a source for the loot feed.
 *
 * <p>Deliberately a separate, immutable value type rather than the engine's
 * internal {@code Swing}: {@code Swing}'s quantity is mutated destructively as
 * each classification stage claims part of it, and its cost-basis fields are
 * accounting internals no consumer should see. What escapes here is only what
 * the engine has already finished deciding is loot.
 *
 * <p>Pure domain type: no RuneLite imports, unit-testable without a game client.
 */
public final class DiffLoot
{
	public final int itemId;
	public final String name;
	public final long quantity;
	public final long unitValue;

	public DiffLoot(int itemId, String name, long quantity, long unitValue)
	{
		this.itemId = itemId;
		this.name = name;
		this.quantity = quantity;
		this.unitValue = unitValue;
	}
}
