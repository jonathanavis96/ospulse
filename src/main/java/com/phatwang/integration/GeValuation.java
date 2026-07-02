package com.phatwang.integration;

/**
 * Pure arithmetic for estimating the tracked-wealth value tied up in an
 * active Grand Exchange offer. This is a documented approximation, refinable
 * later — no RuneLite imports, so it's directly unit-testable.
 *
 * <p>BUY offers: the coins committed to the offer are counted at the full
 * offer price for the full requested quantity, whether they're still sitting
 * as reserved coins or have already been converted into bought-but-uncollected
 * items — both are worth {@code price * totalQuantity} of tracked wealth.
 *
 * <p>SELL offers: whatever hasn't sold yet is still worth current market
 * price ({@code unitValue * (totalQuantity - quantitySold)}), plus whatever
 * coins have already been received from partial sells ({@code spent}, which
 * for a sell offer is gp received so far, per RuneLite's
 * {@code GrandExchangeOffer#getSpent()} naming).
 */
public final class GeValuation
{
	private GeValuation()
	{
	}

	public static long buyOfferValue(long pricePerItem, long totalQuantity)
	{
		return pricePerItem * totalQuantity;
	}

	public static long sellOfferValue(long unitValue, long totalQuantity, long quantitySold, long spent)
	{
		long remaining = totalQuantity - quantitySold;
		if (remaining < 0)
		{
			remaining = 0;
		}
		return unitValue * remaining + spent;
	}
}
