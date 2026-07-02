package com.phatwang.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeValuationTest
{
	@Test
	public void buyOfferValueIsPriceTimesTotalQuantity()
	{
		assertEquals(10_000_000L, GeValuation.buyOfferValue(1_000_000L, 10L));
	}

	@Test
	public void buyOfferValueIsUnaffectedByPartialFill()
	{
		// Coins committed to the offer are counted at the full requested
		// quantity regardless of how much has filled so far.
		assertEquals(10_000_000L, GeValuation.buyOfferValue(1_000_000L, 10L));
	}

	@Test
	public void sellOfferValueCountsRemainingAtMarketPlusCoinsReceived()
	{
		// 10 total, 4 sold so far at whatever price, 4_000_000 gp received;
		// 6 remain unsold, valued at current market price 900,000 each.
		long value = GeValuation.sellOfferValue(900_000L, 10L, 4L, 4_000_000L);
		assertEquals(900_000L * 6L + 4_000_000L, value);
	}

	@Test
	public void sellOfferValueWhenFullySoldIsJustCoinsReceived()
	{
		long value = GeValuation.sellOfferValue(900_000L, 10L, 10L, 9_500_000L);
		assertEquals(9_500_000L, value);
	}

	@Test
	public void sellOfferValueNeverGoesNegativeOnRemaining()
	{
		// Defensive: quantitySold should never exceed totalQuantity, but if
		// it did, remaining must clamp to 0 rather than subtract value.
		long value = GeValuation.sellOfferValue(900_000L, 10L, 12L, 9_500_000L);
		assertEquals(9_500_000L, value);
	}
}
