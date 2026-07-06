package com.ospulse.ge;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeReconcilerTest
{
	private static final int WHIP = 4151;
	private static final int OLD_SCHOOL_BOND = 13190; // GE-tax exempt
	private static final int COINS = GeReconciler.COINS_ITEM_ID;

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

		// Everything is back to a fresh-session zero: no realised P&L and no
		// outstanding arrival expectation for the bought whips.
		assertEquals(0L, reconciler.realizedPnl());
		assertEquals(0L, reconciler.attributeArrival(WHIP, 10L));

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
		int dartId = 811;
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, dartId, "Rune dart",
			5000L, 5000L, 5000L, 1L, 1200L);

		assertEquals(10L, reconciler.attributeArrival(WHIP, 10L));
		assertEquals(5000L, reconciler.attributeArrival(dartId, 5000L));
	}

	@Test
	public void arrivalAttributionIsQuantityCappedAndConsumesTheLedger()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);

		// Partial consumption whittles the expectation down; once exhausted,
		// further arrivals of the same item are NOT attributed (genuine loot
		// beyond the bought quantity must stay loot).
		assertEquals(6L, reconciler.attributeArrival(WHIP, 6L));
		assertEquals(4L, reconciler.attributeArrival(WHIP, 10L));
		assertEquals(0L, reconciler.attributeArrival(WHIP, 10L));
	}

	@Test
	public void emptyStateDoesNotAttributeOrAffectLedger()
	{
		reconciler.onOfferUpdate(0, GeOfferState.EMPTY, 0, null,
			0L, 0L, 0L, 0L, 1000L);

		assertEquals(0L, reconciler.realizedPnl());
		assertEquals(0L, reconciler.attributeArrival(WHIP, 1L));
		assertEquals(0L, reconciler.attributeArrival(COINS, 1L));
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
		// It's still GE activity: the placement's inventory removal and the
		// net-of-tax coin proceeds (12M - 24,000 * 10) are both attributed,
		// keeping the sale out of the supplies and loot feeds.
		assertEquals(10L, reconciler.attributeRemoval(WHIP, 10L));
		assertEquals(11_760_000L, reconciler.attributeArrival(COINS, 20_000_000L));
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
	public void cancelledBuyRefundsUnfilledEscrowExactlyOnce()
	{
		// Buy partially fills 4/10, then is cancelled.
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			10L, 4L, 4_000_000L, 1_000_000L, 1000L);
		assertEquals(4L, reconciler.attributeArrival(WHIP, 10L));

		reconciler.onOfferUpdate(0, GeOfferState.CANCELLED_BUY, WHIP, "Abyssal whip",
			10L, 4L, 4_000_000L, 1_000_000L, 1100L);

		// No new items moved on the cancellation itself, but the unfilled
		// escrow (6 x 1M) returns to the collection box as coins.
		assertEquals(0L, reconciler.attributeArrival(WHIP, 10L));
		assertEquals(6_000_000L, reconciler.attributeArrival(COINS, 10_000_000L));

		// A repeated cancelled-state event must not refund again.
		reconciler.onOfferUpdate(0, GeOfferState.CANCELLED_BUY, WHIP, "Abyssal whip",
			10L, 4L, 4_000_000L, 1_000_000L, 1200L);
		assertEquals(0L, reconciler.attributeArrival(COINS, 10_000_000L));
	}

	@Test
	public void cancelledSellReturnsUnsoldItemsToCollectionBox()
	{
		// Sell 10 placed (removal expectation), fills 4 (net-of-tax coin
		// proceeds), then cancelled: the 6 unsold whips become collectable.
		reconciler.onOfferUpdate(0, GeOfferState.SELLING, WHIP, "Abyssal whip",
			10L, 0L, 0L, 1_200_000L, 1000L);
		assertEquals(10L, reconciler.attributeRemoval(WHIP, 10L));

		reconciler.onOfferUpdate(0, GeOfferState.SELLING, WHIP, "Abyssal whip",
			10L, 4L, 4_800_000L, 1_200_000L, 1100L);
		assertEquals(4_704_000L, reconciler.attributeArrival(COINS, 10_000_000L));

		reconciler.onOfferUpdate(0, GeOfferState.CANCELLED_SELL, WHIP, "Abyssal whip",
			10L, 4L, 4_800_000L, 1_200_000L, 1200L);
		assertEquals(6L, reconciler.attributeArrival(WHIP, 10L));
	}

	@Test
	public void buyFilledUnderOfferPriceRefundsTheDifferenceAsCoins()
	{
		// Offered 10 @ 1M but matched cheaper sellers for 9M total: the 1M
		// difference is collectable as coins alongside the 10 items.
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 9_000_000L, 1_000_000L, 1000L);

		assertEquals(10L, reconciler.attributeArrival(WHIP, 10L));
		assertEquals(1_000_000L, reconciler.attributeArrival(COINS, 10_000_000L));
	}

	@Test
	public void saleProceedsUseActualGpWhenFilledAboveOfferPrice()
	{
		// Offered 10 @ 1.2M but matched higher buyers for 13M total: proceeds
		// are the actual 13M minus per-item tax at the actual average price
		// (1.3M/50 = 26,000 each) -> 13M - 260,000 = 12,740,000.
		reconciler.onOfferUpdate(0, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 13_000_000L, 1_200_000L, 1000L);

		assertEquals(12_740_000L, reconciler.attributeArrival(COINS, 20_000_000L));
	}

	@Test
	public void arrivalPersistsIndefinitelyWhileOfferOccupiesItsSlot()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);

		// The completed offer sits uncollected in the collection box for ten
		// minutes: the expectation must survive until the goods are seen.
		reconciler.expireAttributions(601_000L);
		assertEquals(10L, reconciler.attributeArrival(WHIP, 10L));
	}

	@Test
	public void arrivalExpiresAfterSlotClearsPlusSettleWindow()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);
		// Slot cleared (collected — possibly straight to bank) at t=2000.
		reconciler.onOfferUpdate(0, GeOfferState.EMPTY, 0, null,
			0L, 0L, 0L, 0L, 2000L);

		// Past the settle window the leftover dies, so a later genuine whip
		// drop cannot be swallowed by a stale expectation.
		reconciler.expireAttributions(12_001L);
		assertEquals(0L, reconciler.attributeArrival(WHIP, 10L));
	}

	@Test
	public void arrivalStillConsumableWithinSlotClearSettleWindow()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(0, GeOfferState.EMPTY, 0, null,
			0L, 0L, 0L, 0L, 2000L);

		// The collect-to-inventory arrival lands a moment after the slot
		// clears; within the settle window it is still attributed.
		reconciler.expireAttributions(3_000L);
		assertEquals(10L, reconciler.attributeArrival(WHIP, 10L));
	}

	@Test
	public void sellPlacementRemovalExpiresAfterSettleWindow()
	{
		reconciler.onOfferUpdate(0, GeOfferState.SELLING, WHIP, "Abyssal whip",
			10L, 0L, 0L, 1_200_000L, 1000L);

		// The placement-time inventory removal lands within the same tick
		// batch; long after that any leftover must not suppress a genuine
		// removal (drop/trade) of the same item.
		reconciler.expireAttributions(11_001L);
		assertEquals(0L, reconciler.attributeRemoval(WHIP, 10L));
	}

	@Test
	public void primeSlotDoesNotAttributePreSessionFills()
	{
		// A live offer predating the session: 4/10 already bought. Priming
		// seeds the incremental tracking without attributing that history.
		reconciler.primeSlot(0, GeOfferState.BUYING, WHIP, 4L, 4_000_000L);
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			10L, 4L, 4_000_000L, 1_000_000L, 1000L);
		assertEquals(0L, reconciler.attributeArrival(WHIP, 10L));

		// Only genuinely new fills count from here on.
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			10L, 7L, 7_000_000L, 1_000_000L, 2000L);
		assertEquals(3L, reconciler.attributeArrival(WHIP, 10L));
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
