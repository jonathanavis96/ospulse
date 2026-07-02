package com.ospulse.session;

import com.ospulse.model.ItemStack;
import com.ospulse.wealth.WealthSnapshot;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionEngineTest
{
	private static final int DRAGON_BONES = 536;
	private static final int RUNE_ITEM = 561;

	private SessionEngine engine;

	@Before
	public void setUp()
	{
		engine = new SessionEngine();
	}

	private static WealthSnapshot snap(long inventoryValue, Map<Integer, ItemStack> trackedItems,
		long bankValue, boolean bankKnown, long ts)
	{
		return WealthSnapshot.builder()
			.inventoryValue(inventoryValue)
			.equipmentValue(0L)
			.geInFlightValue(0L)
			.pouchValue(0L)
			.bankValue(bankValue)
			.bankKnown(bankKnown)
			.timestampMs(ts)
			.trackedItems(trackedItems)
			.build();
	}

	@Test
	public void awayLootGainRecordsProfitAndLootEntry()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot current = snap(11_000_000L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(1_000_000L, result.getProfit());
		assertEquals(1, result.getLoot().size());
		LootEntry loot = result.getLoot().get(0);
		assertEquals(DRAGON_BONES, loot.getItemId());
		assertEquals(1_000_000L, loot.getValue());
		assertEquals(100L, loot.getQuantity());
	}

	@Test
	public void bankDepositIsNeutralised()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 0L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Deposited 50M: tracked drops by 50M while bank is open.
		WealthSnapshot afterDeposit = snap(50_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.setBankOpen(false, afterDeposit, 200L);

		SessionSnapshot result = engine.snapshot(afterDeposit, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(0L, result.getProfit());
	}

	@Test
	public void bankWithdrawIsNeutralised()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 20_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 20M supplies: tracked rises by 20M, bank drops by 20M.
		WealthSnapshot afterWithdraw = snap(120_000_000L, Collections.emptyMap(), 0L, true, 200L);
		engine.setBankOpen(false, afterWithdraw, 200L);

		SessionSnapshot result = engine.snapshot(afterWithdraw, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(0L, result.getProfit());
	}

	@Test
	public void withdrawThenConsumeShowsRealLoss()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 20_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 20M worth of runes: tracked rises by 20M, bank drops by 20M.
		WealthSnapshot afterWithdraw = snap(120_000_000L, Collections.emptyMap(), 0L, true, 200L);
		engine.setBankOpen(false, afterWithdraw, 200L);

		// Away: used the runes, tracked wealth drops by 20M with no
		// corresponding item gain (negative deltas aren't loot).
		WealthSnapshot afterConsume = snap(100_000_000L, Collections.emptyMap(), 0L, true, 300L);
		engine.update(afterConsume, Collections.emptySet(), 300L);

		SessionSnapshot result = engine.snapshot(afterConsume, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(-20_000_000L, result.getProfit());
		assertTrue(result.getLoot().isEmpty());
	}

	@Test
	public void depositWithBankStillOpenIsZeroSumImmediately()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Deposited 30M while the bank is STILL OPEN: inventory down 30M,
		// bank up 30M. Zero-sum transfer — nothing may move, not even
		// transiently before the bank closes.
		WealthSnapshot midDeposit = snap(70_000_000L, Collections.emptyMap(), 80_000_000L, true, 200L);
		engine.update(midDeposit, Collections.emptySet(), 200L);

		SessionSnapshot result = engine.snapshot(midDeposit, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals("deposit must not read as a loss while the bank is open",
			0L, result.getProfit());
		assertEquals(0L, result.getProfitPerHour());
		assertEquals(0L, result.getNetWorthDelta());
	}

	@Test
	public void withdrawalWithBankStillOpenIsZeroSumImmediately()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 30M while the bank is STILL OPEN: inventory up 30M, bank
		// down 30M. Must not read as a gain.
		WealthSnapshot midWithdraw = snap(130_000_000L, Collections.emptyMap(), 20_000_000L, true, 200L);
		engine.update(midWithdraw, Collections.emptySet(), 200L);

		SessionSnapshot result = engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals("withdrawal must not read as a gain while the bank is open",
			0L, result.getProfit());
		assertEquals(0L, result.getProfitPerHour());
		assertEquals(0L, result.getNetWorthDelta());
	}

	@Test
	public void realGainWhileBankOpenStillCountsLiveAndAfterClose()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// A genuine 5M gain lands while the bank happens to be open (e.g. a
		// collected GE sale): tracked up 5M, bank untouched. This is real
		// profit and must be counted live...
		WealthSnapshot midGain = snap(105_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.update(midGain, Collections.emptySet(), 200L);

		SessionSnapshot live = engine.snapshot(midGain, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(5_000_000L, live.getProfit());
		assertEquals(5_000_000L, live.getNetWorthDelta());

		// ...and must survive the close reconciliation (not be swallowed by
		// the bank-visit neutralisation, and not be double-counted).
		engine.setBankOpen(false, midGain, 300L);
		SessionSnapshot closed = engine.snapshot(midGain, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(5_000_000L, closed.getProfit());
		assertEquals(5_000_000L, closed.getNetWorthDelta());
	}

	@Test
	public void depositPlusRealGainWhileBankOpenIsContinuousAcrossClose()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Same visit: deposited 30M (inventory -30M, bank +30M) AND gained a
		// genuine 5M. Only the 5M is profit, live and repeatedly.
		WealthSnapshot mid = snap(75_000_000L, Collections.emptyMap(), 80_000_000L, true, 200L);
		engine.update(mid, Collections.emptySet(), 200L);

		SessionSnapshot first = engine.snapshot(mid, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(5_000_000L, first.getProfit());
		SessionSnapshot second = engine.snapshot(mid, 0L, Collections.emptyMap(), 0L, 250L);
		assertEquals("repeated snapshots while the bank is open must not drift",
			5_000_000L, second.getProfit());

		// Closing must not change the figure (no jump, no double-count)...
		engine.setBankOpen(false, mid, 300L);
		SessionSnapshot closed = engine.snapshot(mid, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(5_000_000L, closed.getProfit());
		assertEquals(5_000_000L, closed.getNetWorthDelta());

		// ...and a quiet away-tick after the close keeps it stable.
		WealthSnapshot after = snap(75_000_000L, Collections.emptyMap(), 80_000_000L, true, 400L);
		engine.update(after, Collections.emptySet(), 400L);
		SessionSnapshot later = engine.snapshot(after, 0L, Collections.emptyMap(), 0L, 400L);
		assertEquals(5_000_000L, later.getProfit());
	}

	@Test
	public void secondBankVisitReanchorsTransferOffset()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		// Visit 1: deposit 30M, close. Profit stays 0.
		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);
		WealthSnapshot afterDeposit = snap(70_000_000L, Collections.emptyMap(), 80_000_000L, true, 200L);
		engine.setBankOpen(false, afterDeposit, 200L);
		assertEquals(0L, engine.snapshot(afterDeposit, 0L, Collections.emptyMap(), 0L, 200L).getProfit());

		// Visit 2: withdraw 10M mid-visit. The offset must be measured from
		// THIS visit's opening bank value, not the first visit's.
		engine.setBankOpen(true, snap(70_000_000L, Collections.emptyMap(), 80_000_000L, true, 300L), 300L);
		WealthSnapshot midWithdraw = snap(80_000_000L, Collections.emptyMap(), 70_000_000L, true, 400L);
		engine.update(midWithdraw, Collections.emptySet(), 400L);
		assertEquals(0L, engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 400L).getProfit());

		engine.setBankOpen(false, midWithdraw, 500L);
		assertEquals(0L, engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 500L).getProfit());
	}

	@Test
	public void restartWhileBankOpenKeepsPostResetTransfersZeroSum()
	{
		// Session running, bank open, mid-visit (mirrors the "reset while the
		// bank interface is open" edge: SessionTracker.resetSession() restarts
		// the engine and then must re-apply bank-open mode via setBankOpen,
		// exactly like this test does, rather than leaving the fresh engine
		// thinking the bank is closed).
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);
		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);

		// Reset fired while the bank is still open: restart the engine...
		WealthSnapshot atReset = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.startSession(atReset, 200L);
		// ...the engine alone would now report bank-closed:
		assertTrue("startSession must not leave bank-open mode active on its own",
			!engine.isBankOpen());
		// ...so the reset path must re-apply bank-open mode, anchored to the
		// same snapshot the engine was just restarted with.
		engine.setBankOpen(true, atReset, 200L);
		assertTrue(engine.isBankOpen());

		// A withdrawal made AFTER the reset but before the next open/close
		// event: tracked rises by 10M, bank drops by 10M. Must stay zero-sum
		// immediately (live, bank still open)...
		WealthSnapshot midWithdraw = snap(110_000_000L, Collections.emptyMap(), 40_000_000L, true, 300L);
		engine.update(midWithdraw, Collections.emptySet(), 300L);
		SessionSnapshot live = engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals("post-reset transfer must not read as profit while the bank is open",
			0L, live.getProfit());
		assertEquals(0L, live.getProfitPerHour());
		assertEquals(0L, live.getNetWorthDelta());

		// ...and stay zero-sum once the bank actually closes.
		engine.setBankOpen(false, midWithdraw, 400L);
		SessionSnapshot closed = engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 400L);
		assertEquals("post-reset transfer must still be neutral after the real close",
			0L, closed.getProfit());
	}

	@Test
	public void geBuyIsExcludedFromLootFeed()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 1000L, 200L));
		WealthSnapshot current = snap(10_200_000L, tracked, 0L, false, 1000L);

		Set<Integer> geAttributed = new HashSet<>();
		geAttributed.add(RUNE_ITEM);

		engine.update(current, geAttributed, 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);
		assertTrue("GE-attributed item gain must not appear as loot", result.getLoot().isEmpty());
		// Profit still reflects the wealth change even though it's not "loot".
		assertEquals(200_000L, result.getProfit());
	}

	@Test
	public void profitPerHourMath()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot current = snap(3_600_000L, Collections.emptyMap(), 0L, false, 1_800_000L);
		engine.update(current, Collections.emptySet(), 1_800_000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1_800_000L);

		assertEquals(3_600_000L, result.getProfit());
		assertEquals(1_800_000L, result.getElapsedMs());
		assertEquals(7_200_000L, result.getProfitPerHour());
	}

	@Test
	public void netWorthDeltaIncludesBankWhenKnown()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot current = snap(10_000_000L, Collections.emptyMap(), 60_000_000L, true, 1000L);
		engine.update(current, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		// Tracked wealth unchanged (bank grew via some external means in
		// this synthetic scenario), netWorthDelta must reflect the bank
		// change since it's part of netWorth().
		assertEquals(0L, result.getProfit());
		assertEquals(10_000_000L, result.getNetWorthDelta());
		assertTrue(result.isBankKnown());
	}

	@Test
	public void firstBankOpenDoesNotInflateNetWorthDelta()
	{
		// Session starts before the bank is opened: bank value is unknown.
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Bank opened for the first time; it holds 1.6B. Net worth only "jumps"
		// because the bank was previously invisible — it must NOT be reported as
		// a session gain.
		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 1_600_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		SessionSnapshot result = engine.snapshot(atOpen, 0L, Collections.emptyMap(), 0L, 100L);
		assertEquals("first bank sighting must not register as a net-worth gain",
			0L, result.getNetWorthDelta());
		assertTrue(result.isBankKnown());

		// A genuine gain AFTER the bank is known is still reflected.
		WealthSnapshot later = snap(150_000_000L, Collections.emptyMap(), 1_600_000_000L, true, 200L);
		engine.update(later, Collections.emptySet(), 200L);
		SessionSnapshot after = engine.snapshot(later, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(50_000_000L, after.getNetWorthDelta());
	}

	@Test
	public void netWorthAboveIntMaxIsNotTruncated()
	{
		// 3B exceeds Integer.MAX_VALUE (~2.147B): the whole pipeline must stay
		// in long arithmetic and never wrap negative.
		long huge = 3_000_000_000L;
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot current = snap(huge, Collections.emptyMap(), 0L, false, 1000L);
		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(huge, result.getProfit());
		assertEquals(huge, result.getNetWorthDelta());
	}

	@Test
	public void profitPerHourIsZeroWhenElapsedIsZero()
	{
		WealthSnapshot initial = snap(1_000_000L, Collections.emptyMap(), 0L, false, 500L);
		engine.startSession(initial, 500L);

		SessionSnapshot result = engine.snapshot(initial, 0L, Collections.emptyMap(), 0L, 500L);
		assertEquals(0L, result.getProfitPerHour());
	}

	@Test
	public void lootIsAggregatedPerItemAcrossPickups()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Same item (bird nest) looted many times, one per update: must collapse
		// into a single summarised row with summed quantity and value, not one
		// row per pickup.
		long qtySoFar = 0;
		WealthSnapshot current = initial;
		for (int i = 1; i <= 300; i++)
		{
			qtySoFar = i;
			Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
			tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Bird nest", qtySoFar, 5_000L));
			current = snap(qtySoFar * 5_000L, tracked, 0L, false, i);
			engine.update(current, Collections.emptySet(), i);
		}

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(1, result.getLoot().size());
		LootEntry nest = result.getLoot().get(0);
		assertEquals(300L, nest.getQuantity());
		assertEquals(300L * 5_000L, nest.getValue());
	}

	@Test
	public void lootSummaryIsOrderedByValueDescending()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(1, new ItemStack(1, "Cheap", 1L, 10_000L));       // 10k
		tracked.put(2, new ItemStack(2, "Pricey", 1L, 5_000_000L));   // 5m
		tracked.put(3, new ItemStack(3, "Mid", 1L, 500_000L));        // 500k
		WealthSnapshot current = snap(5_510_000L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(3, result.getLoot().size());
		assertEquals("Pricey", result.getLoot().get(0).getName());
		assertEquals("Mid", result.getLoot().get(1).getName());
		assertEquals("Cheap", result.getLoot().get(2).getName());
	}

	@Test
	public void multipleNewItemsInSameUpdateAllRecordedAsLoot()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(1, new ItemStack(1, "Item A", 1L, 500_000L));
		tracked.put(2, new ItemStack(2, "Item B", 2L, 250_000L));
		WealthSnapshot current = snap(1_000_000L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(2, result.getLoot().size());
	}

	@Test
	public void zeroValueItemsAreNotLoot()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(1, new ItemStack(1, "Junk clue scroll", 1L, 0L));
		WealthSnapshot current = snap(0L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertTrue(result.getLoot().isEmpty());
	}
}
