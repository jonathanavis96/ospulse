package com.ospulse.session;

/**
 * Immutable per-holding unrealized P/L row: one currently-held tracked item,
 * its session cost basis (what the held quantity was "paid" for — live price
 * when first seen this session, averaged across later acquisitions), its
 * current live value, and the paper gain/loss between the two. Pure domain
 * type: no RuneLite imports, unit-testable without a game client.
 */
public final class HoldingPnl
{
	private final int itemId;
	private final String name;
	private final long quantity;
	/** Total cost basis of the held quantity, in gp. */
	private final long costBasis;
	/** Current live value of the held quantity, in gp. */
	private final long currentValue;

	public HoldingPnl(int itemId, String name, long quantity, long costBasis, long currentValue)
	{
		this.itemId = itemId;
		this.name = name;
		this.quantity = quantity;
		this.costBasis = costBasis;
		this.currentValue = currentValue;
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

	public long getCostBasis()
	{
		return costBasis;
	}

	public long getCurrentValue()
	{
		return currentValue;
	}

	/** Paper gain (positive) or loss (negative) on this holding, in gp. */
	public long unrealized()
	{
		return currentValue - costBasis;
	}

	@Override
	public String toString()
	{
		return "HoldingPnl{"
			+ "itemId=" + itemId
			+ ", name='" + name + '\''
			+ ", quantity=" + quantity
			+ ", costBasis=" + costBasis
			+ ", currentValue=" + currentValue
			+ '}';
	}
}
