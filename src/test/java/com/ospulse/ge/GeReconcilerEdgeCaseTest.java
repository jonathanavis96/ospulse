package com.ospulse.ge;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Malformed / out-of-order / boundary inputs to {@link GeReconciler}. */
public class GeReconcilerEdgeCaseTest
{
	private static final int WHIP = 4151;
	private static final int COINS = GeReconciler.COINS_ITEM_ID;
	/** Mirrors the private GeReconciler.ATTRIBUTION_SETTLE_WINDOW_MS. */
	private static final long SETTLE_WINDOW_MS = 10_000L;

	private GeReconciler reconciler;

	@Before
	public void setUp()
	{
		reconciler = new GeReconciler();
	}

	@Test
	public void saleTaxIsZeroForZeroOrNegativePrice()
	{
		assertEquals(0L, GeReconciler.saleTaxPerItem(WHIP, 0L));
		assertEquals(0L, GeReconciler.saleTaxPerItem(WHIP, -1_000L));
	}

	@Test
	public void saleTaxBoundaryAtFiftyGp()
	{
		assertEquals(0L, GeReconciler.saleTaxPerItem(WHIP, 49L));
		assertEquals(1L, GeReconciler.saleTaxPerItem(WHIP, 50L));
	}

	@Test
	public void attributeArrivalWithNothingExpectedConsumesNothing()
	{
		assertEquals(0L, reconciler.attributeArrival(WHIP, 5L));
		assertEquals(0L, reconciler.attributeArrival(WHIP, 0L));
		assertEquals(0L, reconciler.attributeArrival(WHIP, -3L));
	}

	@Test
	public void attributeArrivalIgnoresOtherItems()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			4L, 4L, 4_000L, 1_000L, 1000L);
		assertEquals(0L, reconciler.attributeArrival(WHIP + 1, 4L));
		assertEquals(4L, reconciler.attributeArrival(WHIP, 100L));
	}

	@Test
	public void gpTransactedRegressionDoesNotCorruptLaterIncrement()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			10L, 10L, 10_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SELLING, WHIP, "Abyssal whip",
			10L, 5L, 6_000_000L, 1_200_000L, 2000L);
		long afterFirst = reconciler.realizedPnl();
		// Same quantity, lower cumulative gp (malformed): no new fill, no change.
		reconciler.onOfferUpdate(1, GeOfferState.SELLING, WHIP, "Abyssal whip",
			10L, 5L, 1L, 1_200_000L, 2100L);
		assertEquals(afterFirst, reconciler.realizedPnl());
		// Genuine next fill still prices off the high-water mark, not the dip.
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			10L, 10L, 12_000_000L, 1_200_000L, 2200L);
		assertEquals(1_760_000L, reconciler.realizedPnl());
	}

	@Test
	public void sellWithZeroGpTransactedFallsBackToListedPrice()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			1L, 1L, 1_000_000L, 1_000_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			1L, 1L, 0L, 1_200_000L, 2000L);
		// 1,200,000 - 24,000 tax = 1,176,000 collectable coins.
		assertEquals(1_176_000L, reconciler.collectableValue(id -> 0L));
	}

	@Test
	public void cancelledBuyThatFullyFilledRefundsNothing()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip",
			5L, 5L, 5_000L, 1_000L, 900L);
		reconciler.onOfferUpdate(0, GeOfferState.CANCELLED_BUY, WHIP, "Abyssal whip",
			5L, 5L, 5_000L, 1_000L, 1000L);
		// Only the item arrival from the 5 filled; no escrow coin refund.
		assertEquals(5L, reconciler.attributeArrival(WHIP, 5L));
		assertEquals(0L, reconciler.attributeArrival(COINS, 1_000_000L));
	}

	@Test
	public void cancelledSellThatFullySoldReturnsNoItems()
	{
		reconciler.onOfferUpdate(0, GeOfferState.CANCELLED_SELL, WHIP, "Abyssal whip",
			3L, 3L, 3_000L, 1_000L, 1000L);
		assertEquals(0L, reconciler.attributeArrival(WHIP, 3L));
	}

	@Test
	public void cancelledSellWithOverReportedFillNeverGoesNegative()
	{
		// quantityTransacted > totalQuantity is malformed; remainder is clamped to 0.
		reconciler.onOfferUpdate(0, GeOfferState.CANCELLED_SELL, WHIP, "Abyssal whip",
			3L, 9L, 0L, 1_000L, 1000L);
		assertEquals(0L, reconciler.attributeArrival(WHIP, 3L));
	}

	@Test
	public void importCostBasisNullClearsLedger()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			2L, 2L, 2_000L, 1_000L, 1000L);
		assertEquals(1, reconciler.exportCostBasis().size());
		reconciler.importCostBasis(null);
		assertTrue(reconciler.exportCostBasis().isEmpty());
	}

	@Test
	public void importCostBasisSkipsNullZeroAndNegativeEntries()
	{
		Map<Integer, GeReconciler.CostBasisSnapshot> in = new HashMap<>();
		in.put(1, null);
		in.put(2, new GeReconciler.CostBasisSnapshot(0L, 100L));
		in.put(3, new GeReconciler.CostBasisSnapshot(-4L, 100L));
		in.put(WHIP, new GeReconciler.CostBasisSnapshot(7L, 100L));
		reconciler.importCostBasis(in);
		Map<Integer, GeReconciler.CostBasisSnapshot> out = reconciler.exportCostBasis();
		assertEquals(Collections.singleton(WHIP), out.keySet());
	}

	@Test
	public void fullySoldItemDisappearsFromExportedCostBasis()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			2L, 2L, 2_000L, 1_000L, 1000L);
		reconciler.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip",
			2L, 2L, 3_000L, 1_500L, 2000L);
		assertTrue(reconciler.exportCostBasis().isEmpty());
	}

	@Test
	public void collectableValueOfEmptyReconcilerIsZero()
	{
		assertEquals(0L, reconciler.collectableValue(id -> 1_000_000L));
	}

	@Test
	public void unpricedItemsCountZeroButCoinsCountAtParity()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			2L, 2L, 1_900L, 1_000L, 1000L);
		// 2 whips at an unpriced 0 value + 100 coins refund (2*1000 - 1900).
		assertEquals(100L, reconciler.collectableValue(id -> 0L));
	}

	@Test
	public void expireAttributionsKeepsArrivalExactlyAtDeadline()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			1L, 1L, 1_000L, 1_000L, 1000L);
		reconciler.onOfferUpdate(0, GeOfferState.EMPTY, 0, "", 0L, 0L, 0L, 0L, 5000L);
		long deadline = 5000L + SETTLE_WINDOW_MS;
		reconciler.expireAttributions(deadline);
		assertEquals(1L, reconciler.attributeArrival(WHIP, 1L));
	}

	@Test
	public void expireAttributionsDropsArrivalPastDeadline()
	{
		reconciler.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip",
			1L, 1L, 1_000L, 1_000L, 1000L);
		reconciler.onOfferUpdate(0, GeOfferState.EMPTY, 0, "", 0L, 0L, 0L, 0L, 5000L);
		reconciler.expireAttributions(5000L + SETTLE_WINDOW_MS + 1);
		assertEquals(0L, reconciler.attributeArrival(WHIP, 1L));
	}

	@Test
	public void emptyEventForUntrackedSlotIsHarmless()
	{
		reconciler.onOfferUpdate(7, GeOfferState.EMPTY, 0, "", 0L, 0L, 0L, 0L, 1000L);
		assertEquals(0L, reconciler.realizedPnl());
		assertTrue(reconciler.drainLootSales().isEmpty());
	}
}
