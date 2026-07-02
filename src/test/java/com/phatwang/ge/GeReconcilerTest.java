package com.phatwang.ge;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeReconcilerTest
{
	private static final int WHIP = 4151;

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

		// (1,200,000 - 1,000,000) * 10 = 2,000,000 profit.
		assertEquals(2_000_000L, reconciler.realizedPnl());
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

		assertEquals(2_000_000L, reconciler.realizedPnl());
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

		// Sell all 20 at 1,300,000 -> pnl = (1,300,000 - 1,100,000) * 20 = 4,000,000
		reconciler.onOfferUpdate(2, GeOfferState.SOLD, WHIP, "Abyssal whip",
			20L, 20L, 26_000_000L, 1_300_000L, 2000L);

		assertEquals(4_000_000L, reconciler.realizedPnl());
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
		// actually flipped count -> (1,200,000 - 1,000,000) * 5 = 1,000,000.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			5L, 5L, 5_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 2000L);

		assertEquals(1_000_000L, reconciler.realizedPnl());
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
}
