package com.phatwang.wealth;

import com.phatwang.model.ItemStack;
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
}
