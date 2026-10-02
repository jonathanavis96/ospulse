package com.ospulse.wealth;

import com.ospulse.model.ItemStack;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WealthSnapshotTest
{
	@Test
	public void trackedSumsInventoryEquipmentGeAndPouch()
	{
		WealthSnapshot snap = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.equipmentValue(2_000_000L)
			.geInFlightValue(500_000L)
			.pouchValue(250_000L)
			.bankValue(100_000_000L)
			.bankKnown(true)
			.timestampMs(123L)
			.build();

		assertEquals(3_750_000L, snap.tracked());
	}

	@Test
	public void trackedIncludesGeCollectableValue()
	{
		// Value awaiting collection in the GE box (sale proceeds / bought goods)
		// counts as tracked wealth, so net worth stays continuous across a fill
		// instead of dipping until the goods are collected.
		WealthSnapshot snap = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.equipmentValue(2_000_000L)
			.geInFlightValue(500_000L)
			.geCollectableValue(750_000L)
			.pouchValue(250_000L)
			.bankValue(100_000_000L)
			.bankKnown(true)
			.build();

		assertEquals(4_500_000L, snap.tracked());
		assertEquals(750_000L, snap.getGeCollectableValue());
		assertEquals(104_500_000L, snap.netWorth());
	}

	@Test
	public void netWorthIncludesBankWhenKnown()
	{
		WealthSnapshot snap = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.bankValue(10_000_000L)
			.bankKnown(true)
			.build();

		assertEquals(1_000_000L, snap.tracked());
		assertEquals(11_000_000L, snap.netWorth());
	}

	@Test
	public void netWorthIgnoresBankWhenNotKnown()
	{
		WealthSnapshot snap = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.bankValue(10_000_000L)
			.bankKnown(false)
			.build();

		// Bank value is present in the field but must not leak into
		// netWorth() unless we actually observed the bank this session.
		assertEquals(1_000_000L, snap.tracked());
		assertEquals(1_000_000L, snap.netWorth());
	}

	@Test
	public void defensiveCopyOfTopHoldingsAndTrackedItems()
	{
		List<ItemStack> holdings = new java.util.ArrayList<>();
		holdings.add(new ItemStack(995, "Coins", 100L, 1L));

		Map<Integer, ItemStack> tracked = new HashMap<>();
		tracked.put(995, new ItemStack(995, "Coins", 100L, 1L));

		WealthSnapshot snap = WealthSnapshot.builder()
			.topHoldings(holdings)
			.trackedItems(tracked)
			.build();

		holdings.add(new ItemStack(4151, "Abyssal whip", 1L, 2_000_000L));
		tracked.put(4151, new ItemStack(4151, "Abyssal whip", 1L, 2_000_000L));

		assertEquals(1, snap.getTopHoldings().size());
		assertEquals(1, snap.getTrackedItems().size());
	}

	@Test
	public void nullCollectionsBecomeEmpty()
	{
		WealthSnapshot snap = new WealthSnapshot(
			0L, 0L, 0L, 0L, 0L, false, 0L, null, null);

		assertTrue(snap.getTopHoldings().isEmpty());
		assertTrue(snap.getTrackedItems().isEmpty());
		assertEquals(Collections.emptyList(), snap.getTopHoldings());
	}

	@Test
	public void emptySnapshotIsAllZero()
	{
		WealthSnapshot snap = WealthSnapshot.builder().build();
		assertEquals(0L, snap.tracked());
		assertEquals(0L, snap.netWorth());
		assertTrue(snap.getAllHoldings().isEmpty());
	}

	@Test
	public void knownEmptyBankStillCountsAsZeroNotMissing()
	{
		WealthSnapshot snap = WealthSnapshot.builder()
			.inventoryValue(5L).bankValue(0L).bankKnown(true).build();
		assertEquals(5L, snap.netWorth());
	}

	@Test
	public void netWorthSumsPastIntRangeWithoutOverflow()
	{
		long big = 2_000_000_000L;
		WealthSnapshot snap = WealthSnapshot.builder()
			.inventoryValue(big).equipmentValue(big).geInFlightValue(big)
			.geCollectableValue(big).pouchValue(big).bankValue(big).bankKnown(true).build();
		assertEquals(12_000_000_000L, snap.netWorth());
	}

	@Test
	public void legacyConstructorLeavesAllHoldingsEmptyAndCollectableZero()
	{
		WealthSnapshot snap = new WealthSnapshot(1L, 2L, 3L, 4L, 5L, true, 0L, null, null, null);
		assertTrue(snap.getAllHoldings().isEmpty());
		assertEquals(0L, snap.getGeCollectableValue());
		assertEquals(10L, snap.tracked());
	}

	@Test
	public void allHoldingsIsDefensivelyCopiedAndReadOnly()
	{
		Map<Integer, ItemStack> all = new HashMap<>();
		all.put(995, new ItemStack(995, "Coins", 1L, 1L));
		WealthSnapshot snap = WealthSnapshot.builder().allHoldings(all).build();
		all.put(4151, new ItemStack(4151, "Abyssal whip", 1L, 2L));
		assertEquals(1, snap.getAllHoldings().size());
		try
		{
			snap.getAllHoldings().clear();
			org.junit.Assert.fail("allHoldings must be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// ok
		}
	}
}
