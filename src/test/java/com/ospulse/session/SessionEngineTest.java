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

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 0L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 20M supplies: tracked rises by 20M while bank is open.
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

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 0L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 20M worth of runes.
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
	public void profitPerHourIsZeroWhenElapsedIsZero()
	{
		WealthSnapshot initial = snap(1_000_000L, Collections.emptyMap(), 0L, false, 500L);
		engine.startSession(initial, 500L);

		SessionSnapshot result = engine.snapshot(initial, 0L, Collections.emptyMap(), 0L, 500L);
		assertEquals(0L, result.getProfitPerHour());
	}

	@Test
	public void lootListIsBoundedToTwoHundredEntries()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		WealthSnapshot previous = initial;
		for (int i = 0; i < 250; i++)
		{
			tracked = new LinkedHashMap<>(tracked);
			tracked.put(i, new ItemStack(i, "item" + i, 1L, 100L));
			WealthSnapshot current = snap(100L * (i + 1), tracked, 0L, false, i + 1);
			engine.update(current, Collections.emptySet(), i + 1);
			previous = current;
		}

		SessionSnapshot result = engine.snapshot(previous, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(200, result.getLoot().size());
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
