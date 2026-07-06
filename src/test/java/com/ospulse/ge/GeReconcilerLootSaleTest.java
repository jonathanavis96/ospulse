package com.ospulse.ge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class GeReconcilerLootSaleTest
{
	private static final int SHIELD = 1201;
	private static final int SHARK = 385;

	@Test
	public void sellingWithNoBuyBasisSurfacesLootSaleAfterTax()
	{
		GeReconciler r = new GeReconciler();
		// Sell 1 shield @22,400, no prior buy -> a loot-sale, not a flip.
		r.onOfferUpdate(0, GeOfferState.SOLD, SHIELD, "Rune sq shield", 1, 1, 22_400L, 22_400L, 1000L);

		assertEquals("no flip P&L for a loot sale", 0L, r.realizedPnl());
		List<LootSale> sales = r.drainLootSales();
		assertEquals(1, sales.size());
		assertEquals(SHIELD, sales.get(0).itemId);
		assertEquals(1L, sales.get(0).quantity);
		assertEquals(21_952L, sales.get(0).netProceeds); // 22,400 - 448 tax
		assertTrue("drain clears the queue", r.drainLootSales().isEmpty());
	}

	@Test
	public void flipDoesNotSurfaceALootSale()
	{
		GeReconciler r = new GeReconciler();
		// Buy 100 sharks @800 then sell 100 @850 -> a pure flip, no loot-sale.
		r.onOfferUpdate(0, GeOfferState.BOUGHT, SHARK, "Shark", 100, 100, 80_000L, 800L, 1000L);
		r.onOfferUpdate(0, GeOfferState.EMPTY, SHARK, "Shark", 100, 100, 80_000L, 800L, 1100L);
		r.onOfferUpdate(1, GeOfferState.SOLD, SHARK, "Shark", 100, 100, 85_000L, 850L, 1200L);

		assertTrue("flip surfaces no loot sale", r.drainLootSales().isEmpty());
		assertTrue("flip P&L is positive", r.realizedPnl() > 0);
	}

	@Test
	public void partialFlipPartialLootSurfacesOnlyTheUnmatchedPortion()
	{
		GeReconciler r = new GeReconciler();
		// Bought 50 sharks; then sell 100 (50 flip + 50 looted) @850.
		r.onOfferUpdate(0, GeOfferState.BOUGHT, SHARK, "Shark", 50, 50, 40_000L, 800L, 1000L);
		r.onOfferUpdate(0, GeOfferState.EMPTY, SHARK, "Shark", 50, 50, 40_000L, 800L, 1100L);
		r.onOfferUpdate(1, GeOfferState.SOLD, SHARK, "Shark", 100, 100, 85_000L, 850L, 1200L);

		List<LootSale> sales = r.drainLootSales();
		assertEquals(1, sales.size());
		assertEquals(50L, sales.get(0).quantity); // only the unmatched (looted) half
		// net per item = 850 - 17 tax = 833; 50 * 833 = 41,650
		assertEquals(41_650L, sales.get(0).netProceeds);
	}
}
