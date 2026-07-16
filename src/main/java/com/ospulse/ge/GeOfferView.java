package com.ospulse.ge;

import java.util.Objects;
import java.util.OptionalLong;

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
	private final int itemId;
	private final String itemName;
	private final long totalQuantity;
	private final long quantityTransacted;
	private final long pricePerItem;
	/** gp moved so far: spent (buy) or received (sell). */
	private final long gpProgress;
	/** gp the offer would move if fully filled: pricePerItem * totalQuantity. */
	private final long gpPotential;
	/**
	 * Realised flip P&amp;L accumulated so far for this offer's slot (see
	 * {@link GeReconciler#slotRealizedPnl(int)}), or {@link
	 * OptionalLong#empty()} if this offer isn't a flip — a still-open buy
	 * offer, or a sell that only dumped items with no GE cost basis. Absent
	 * vs. a present {@code 0} is deliberate: a dump shows no P&amp;L at all,
	 * while a flip that broke exactly even (net of GE sales tax) still shows
	 * a real, present figure.
	 */
	private final OptionalLong realizedPnl;

	public GeOfferView(
		boolean buying,
		int itemId,
		String itemName,
		long totalQuantity,
		long quantityTransacted,
		long pricePerItem,
		long gpProgress,
		long gpPotential,
		OptionalLong realizedPnl)
	{
		this.buying = buying;
		this.itemId = itemId;
		this.itemName = itemName;
		this.totalQuantity = totalQuantity;
		this.quantityTransacted = quantityTransacted;
		this.pricePerItem = pricePerItem;
		this.gpProgress = gpProgress;
		this.gpPotential = gpPotential;
		this.realizedPnl = realizedPnl == null ? OptionalLong.empty() : realizedPnl;
	}

	public boolean isBuying()
	{
		return buying;
	}

	public int getItemId()
	{
		return itemId;
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

	public OptionalLong getRealizedPnl()
	{
		return realizedPnl;
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
			&& itemId == that.itemId
			&& totalQuantity == that.totalQuantity
			&& quantityTransacted == that.quantityTransacted
			&& pricePerItem == that.pricePerItem
			&& gpProgress == that.gpProgress
			&& gpPotential == that.gpPotential
			&& Objects.equals(itemName, that.itemName)
			&& Objects.equals(realizedPnl, that.realizedPnl);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(buying, itemId, itemName, totalQuantity, quantityTransacted,
			pricePerItem, gpProgress, gpPotential, realizedPnl);
	}

	@Override
	public String toString()
	{
		return "GeOfferView{"
			+ (buying ? "BUY " : "SELL ") + itemName
			+ " " + quantityTransacted + "/" + totalQuantity
			+ " @ " + pricePerItem
			+ " gp " + gpProgress + "/" + gpPotential
			+ (realizedPnl.isPresent() ? " pnl " + realizedPnl.getAsLong() : "")
			+ '}';
	}
}
