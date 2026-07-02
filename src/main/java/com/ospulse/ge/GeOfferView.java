package com.ospulse.ge;

import java.util.Objects;

/**
 * Immutable, read-only view of a single active Grand Exchange offer slot, for
 * display in the panel's "Grand Exchange" breakdown. Pure domain type: no
 * RuneLite imports, unit-testable without a game client.
 *
 * <p>Mirrors what the in-game GE interface shows per slot: the item, whether
 * it is a buy or sell, how far the offer has progressed (quantity transacted
 * out of the total ordered), and the gp moved so far out of the gp the offer
 * would move if fully filled.
 */
public final class GeOfferView
{
	private final boolean buying;
	private final String itemName;
	private final long totalQuantity;
	private final long quantityTransacted;
	private final long pricePerItem;
	/** gp moved so far: spent (buy) or received (sell). */
	private final long gpProgress;
	/** gp the offer would move if fully filled: pricePerItem * totalQuantity. */
	private final long gpPotential;

	public GeOfferView(
		boolean buying,
		String itemName,
		long totalQuantity,
		long quantityTransacted,
		long pricePerItem,
		long gpProgress,
		long gpPotential)
	{
		this.buying = buying;
		this.itemName = itemName;
		this.totalQuantity = totalQuantity;
		this.quantityTransacted = quantityTransacted;
		this.pricePerItem = pricePerItem;
		this.gpProgress = gpProgress;
		this.gpPotential = gpPotential;
	}

	public boolean isBuying()
	{
		return buying;
	}

	public String getItemName()
	{
		return itemName;
	}

	public long getTotalQuantity()
	{
		return totalQuantity;
	}

	public long getQuantityTransacted()
	{
		return quantityTransacted;
	}

	public long getPricePerItem()
	{
		return pricePerItem;
	}

	public long getGpProgress()
	{
		return gpProgress;
	}

	public long getGpPotential()
	{
		return gpPotential;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof GeOfferView))
		{
			return false;
		}
		GeOfferView that = (GeOfferView) o;
		return buying == that.buying
			&& totalQuantity == that.totalQuantity
			&& quantityTransacted == that.quantityTransacted
			&& pricePerItem == that.pricePerItem
			&& gpProgress == that.gpProgress
			&& gpPotential == that.gpPotential
			&& Objects.equals(itemName, that.itemName);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(buying, itemName, totalQuantity, quantityTransacted,
			pricePerItem, gpProgress, gpPotential);
	}

	@Override
	public String toString()
	{
		return "GeOfferView{"
			+ (buying ? "BUY " : "SELL ") + itemName
			+ " " + quantityTransacted + "/" + totalQuantity
			+ " @ " + pricePerItem
			+ " gp " + gpProgress + "/" + gpPotential
			+ '}';
	}
}
