package com.ospulse.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeValuationTest
{
	// ------------------------------------------------------------------- buy

	@Test
	public void buyOfferValueIsFullEscrowWhenNothingBought()
	{
		// 10 @ 1m offered, none bought yet -> all 10m still escrowed.
		assertEquals(10_000_000L, GeValuation.buyOfferValue(1_000_000L, 10L, 0L));
	}

	@Test
	public void buyOfferValueCountsOnlyTheUnboughtEscrow()
	{
		// 10 @ 1m, 4 bought so far -> only the 6 not-yet-bought are still
		// escrowed (6m). The 4 bought are collectable and must NOT be counted,
		// otherwise a mid-offer collect would double-count them against the
		// inventory and inflate net worth.
		assertEquals(6_000_000L, GeValuation.buyOfferValue(1_000_000L, 10L, 4L));
	}

	@Test
	public void buyOfferValueIsZeroWhenFullyBought()
	{
		// Everything bought -> the whole order is now collectable, nothing locked.
		assertEquals(0L, GeValuation.buyOfferValue(1_000_000L, 10L, 10L));
	}

	@Test
	public void buyOfferValueClampsIfSoldExceedsTotal()
	{
		// Defensive: quantitySold should never exceed totalQuantity, but if it
		// did, remaining escrow clamps to 0 rather than going negative.
		assertEquals(0L, GeValuation.buyOfferValue(1_000_000L, 10L, 12L));
	}

	// ------------------------------------------------------------------ sell

	@Test
	public void sellOfferValueCountsRemainingUnsoldAtMarket()
	{
		// 10 total, 4 sold, 6 unsold @ 900k current market -> 5.4m still locked.
		// Proceeds from the 4 sold are collectable and deliberately excluded.
		assertEquals(900_000L * 6L, GeValuation.sellOfferValue(900_000L, 10L, 4L));
	}

	@Test
	public void sellOfferValueIsZeroWhenFullySold()
	{
		// Nothing unsold remains in the exchange; proceeds are collectable.
		assertEquals(0L, GeValuation.sellOfferValue(900_000L, 10L, 10L));
	}

	@Test
	public void sellOfferValueClampsRemainingToZero()
	{
		assertEquals(0L, GeValuation.sellOfferValue(900_000L, 10L, 12L));
	}
}
