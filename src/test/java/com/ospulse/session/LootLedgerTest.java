package com.ospulse.session;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LootLedgerTest
{
	private static final int SHIELD = 1201;

	@Test
	public void heldLootValuedAtPickup()
	{
		LootLedger l = new LootLedger();
		l.recordLoot(SHIELD, 1, 22_400L);
		assertEquals(22_400L, l.lootValue());
	}

	@Test
	public void saleAtPickupPriceRealisesAfterTax()
	{
		LootLedger l = new LootLedger();
		l.recordLoot(SHIELD, 1, 22_400L);
		l.realiseSale(SHIELD, 1, 21_952L); // 22,400 - 2% tax
		assertEquals(21_952L, l.lootValue());
	}

	@Test
	public void saleHigherRaisesLootLowerLowersLoot()
	{
		LootLedger high = new LootLedger();
		high.recordLoot(SHIELD, 1, 22_400L);
		high.realiseSale(SHIELD, 1, 29_400L); // sold 30k, tax 600
		assertEquals(29_400L, high.lootValue());

		LootLedger low = new LootLedger();
		low.recordLoot(SHIELD, 1, 22_400L);
		low.realiseSale(SHIELD, 1, 9_800L); // sold 10k, tax 200
		assertEquals(9_800L, low.lootValue());
	}

	@Test
	public void saleQuantityCappedAtHeldSoPreOwnedExcessIgnored()
	{
		LootLedger l = new LootLedger();
		l.recordLoot(SHIELD, 1, 22_400L);
		// Sell 2 (1 looted + 1 pre-owned) for 43,904 net total: only the 1 looted realises.
		l.realiseSale(SHIELD, 2, 43_904L);
		assertEquals("only the looted unit's proportional proceeds count", 21_952L, l.lootValue());
	}

	@Test
	public void partialSaleLeavesRemainderAtPickup()
	{
		LootLedger l = new LootLedger();
		l.recordLoot(SHIELD, 50, 10_000L); // 500k held
		l.realiseSale(SHIELD, 20, 196_000L); // sold 20 @10k, tax 200/ea -> 9,800*20
		// 20 realised at 196,000 + 30 still held at 10,000 = 300,000 -> 496,000
		assertEquals(496_000L, l.lootValue());
	}

	@Test
	public void reverseLootRemovesAtAverageAndEmptiesCleanly()
	{
		LootLedger l = new LootLedger();
		l.recordLoot(SHIELD, 2, 10_000L);
		l.reverseLoot(SHIELD, 1);
		assertEquals(10_000L, l.lootValue());
		l.reverseLoot(SHIELD, 5); // over-reverse clamps
		assertEquals(0L, l.lootValue());
	}

	@Test
	public void resetClears()
	{
		LootLedger l = new LootLedger();
		l.recordLoot(SHIELD, 1, 22_400L);
		l.realiseSale(SHIELD, 1, 21_952L);
		l.reset();
		assertEquals(0L, l.lootValue());
	}
}
