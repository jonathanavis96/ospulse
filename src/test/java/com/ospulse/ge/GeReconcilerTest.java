package com.ospulse.ge;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeReconcilerTest
{
	private static final int WHIP = 4151;
	private static final int OLD_SCHOOL_BOND = 13190; // GE-tax exempt

	private GeReconciler reconciler;

	@Before
	public void setUp()
	{
		reconciler = new GeReconciler();
	}

	@Test
	public void buyThenSellRealisesPnl()
	{
		// Buy 10 whips at 1,000,000 each.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);
		assertEquals(0L, reconciler.realizedPnl());

		// Offer slot cleared/reused for a sell of the same 10 whips at
		// 1,200,000 each.
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 2000L);

		// Sale nets the price minus 2% GE tax: 1,200,000 - (1,200,000/50=24,000)
		// = 1,176,000 per whip. (1,176,000 - 1,000,000) * 10 = 1,760,000 profit.
		assertEquals(1_760_000L, reconciler.realizedPnl());
	}

	@Test
	public void resetClearsRealisedPnlCostBasisAndAttribution()
	{
		// Realise some flip P&L and leave an open cost basis + attributed ids.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 2000L);
		assertEquals(1_760_000L, reconciler.realizedPnl());

		reconciler.reset();

		// Everything is back to a fresh-session zero.
		assertEquals(0L, reconciler.realizedPnl());
		assertTrue(reconciler.drainAttributedItemIds().isEmpty());

		// Cost basis was cleared too: selling the same item now has no basis, so
		// it realises nothing (rather than resurrecting the pre-reset basis).
		reconciler.onOfferUpdate(0, GeOfferState.SOLD, WHIP, "Abyssal whip",
			5L, 5L, 6_000_000L, 1_200_000L, 3000L);
		assertEquals(0L, reconciler.realizedPnl());
	}

	@Test
	public void repeatedUpdatesForSameOfferDoNotDoubleCount()
	{
		// Partial fill progression on the same slot: 0 -> 3 -> 3 (repeat) -> 7 -> 10.
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			10L, 3L, 3_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			10L, 3L, 3_000_000L, 1_000_000L, 1100L);
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			10L, 7L, 7_000_000L, 1_000_000L, 1200L);
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1300L);

		// Now sell all 10 in one shot, repeated update included.
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 2000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 2100L);

		// 10 whips sold at 1.2M, net of 2% tax (24,000/ea) -> 1,760,000 profit.
		assertEquals(1_760_000L, reconciler.realizedPnl());
	}

	@Test
	public void weightedAverageCostAcrossTwoBuys()
	{
		// Two separate buy offers in different slots: 10 @ 1,000,000, then
		// 10 more @ 1,200,000 -> avg cost 1,100,000.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 1500L);

		// Sell all 20 at 1,300,000, net of 2% tax (1,300,000/50 = 26,000/ea) ->
		// (1,274,000 - 1,100,000) * 20 = 3,480,000
		reconciler.onOfferUpdate(2, GeOfferState.SOLD, WHIP, "Abyssal whip",
			20L, 20L, 26_000_000L, 1_300_000L, 2000L);

		assertEquals(3_480_000L, reconciler.realizedPnl());
	}

	@Test
	public void slotReuseWithDifferentItemResetsTracking()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(0, GeOfferState.EMPTY, WHIP, "Abyssal whip",
			0L, 0L, 0L, 0L, 1100L);

		// Same slot, new offer for a different item; incremental should
		// start fresh from 0, not be offset by the whip's prior quantity.
		int coins = 995;
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, coins, "Coins",
			5000L, 5000L, 5000L, 1L, 1200L);

		Set<Integer> attributed = reconciler.drainAttributedItemIds();
		assertTrue(attributed.contains(WHIP));
		assertTrue(attributed.contains(coins));
	}

	@Test
	public void drainAttributedItemIdsClearsAfterDrain()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);

		Set<Integer> first = reconciler.drainAttributedItemIds();
		assertEquals(1, first.size());
		assertTrue(first.contains(WHIP));

		Set<Integer> second = reconciler.drainAttributedItemIds();
		assertTrue(second.isEmpty());
	}

	@Test
	public void emptyStateDoesNotAttributeOrAffectLedger()
	{
		reconciler.onOfferUpdate(0, GeOfferState.EMPTY, 0, null,
			0L, 0L, 0L, 0L, 1000L);

		assertEquals(0L, reconciler.realizedPnl());
		assertTrue(reconciler.drainAttributedItemIds().isEmpty());
	}

	@Test
	public void sellingWithoutGeCostBasisDoesNotCountAsFlipProfit()
	{
		// Dump 10 looted whips on the GE at 1.2M with no prior GE buy: this is
		// not a flip, so realised flip P&L stays 0 (the sale value is captured
		// in wealth-delta profit elsewhere, not here).
		reconciler.onOfferUpdate(0, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 1000L);

		assertEquals(0L, reconciler.realizedPnl());
		// It's still GE-attributed so it's excluded from the loot feed.
		assertTrue(reconciler.drainAttributedItemIds().contains(WHIP));
	}

	@Test
	public void sellingMoreThanBoughtOnlyCountsFlippedQuantity()
	{
		// Bought 5 @ 1,000,000, then sold 10 @ 1,200,000: only the 5 that were
		// actually flipped count -> (1,176,000 net-of-tax - 1,000,000) * 5 = 880,000.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			5L, 5L, 5_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 2000L);

		assertEquals(880_000L, reconciler.realizedPnl());
	}

	@Test
	public void cancelledOfferDoesNotDoubleAttributePastPartialFill()
	{
		// Buy partially fills 4/10, then is cancelled.
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			10L, 4L, 4_000_000L, 1_000_000L, 1000L);
		reconciler.drainAttributedItemIds();

		reconciler.onOfferUpdate(0, GeOfferState.CANCELLED_BUY, WHIP, "Abyssal whip",
			10L, 4L, 4_000_000L, 1_000_000L, 1100L);

		// No new quantity moved on the cancellation event itself.
		assertTrue(reconciler.drainAttributedItemIds().isEmpty());
	}

	@Test
	public void geTaxIsCappedAtFiveMillionPerItem()
	{
		// Buy 1 @ 200M, sell 1 @ 300M. Uncapped 2% would be 6M; the tax is
		// capped at 5M/item, so net proceeds = 295M and pnl = 295M - 200M = 95M.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			1L, 1L, 200_000_000L, 200_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			1L, 1L, 300_000_000L, 300_000_000L, 2000L);

		assertEquals(95_000_000L, reconciler.realizedPnl());
	}

	@Test
	public void taxExemptItemPaysNoGeTax()
	{
		// The Old school bond is exempt from GE tax: a flat buy-and-sell at the
		// same price realises exactly zero, not a small tax-driven loss.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, OLD_SCHOOL_BOND, "Old school bond",
			1L, 1L, 8_000_000L, 8_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, OLD_SCHOOL_BOND, "Old school bond",
			1L, 1L, 8_000_000L, 8_000_000L, 2000L);

		assertEquals(0L, reconciler.realizedPnl());
	}

	@Test
	public void subFiftyGpSaleIncursNoTax()
	{
		// 2% of a sub-50 gp sale rounds down to 0, so no tax is charged:
		// buy 10 @ 40, sell 10 @ 49 -> (49 - 40) * 10 = 90 (no tax deducted).
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 400L, 40L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 490L, 49L, 2000L);

		assertEquals(90L, reconciler.realizedPnl());
	}
}
