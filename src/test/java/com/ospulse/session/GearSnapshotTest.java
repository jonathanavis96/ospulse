package com.ospulse.session;

import com.ospulse.combat.OffensivePrayer;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GearSnapshotTest
{
	@Test
	public void empty_hasAllSlotsUnfilledAndNoPrayers()
	{
		GearSnapshot empty = GearSnapshot.empty();

		int[] expected = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		java.util.Arrays.fill(expected, -1);
		assertArrayEquals(expected, empty.equippedItemIds());
		assertTrue(empty.activePrayers().isEmpty());
		assertEquals(0, empty.boostedAttack());
		assertFalse(empty.onSlayerTask());
	}

	@Test
	public void builder_roundTripsLevelsAndPrayers()
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		java.util.Arrays.fill(ids, -1);
		ids[3] = 4587; // arbitrary weapon slot id

		GearSnapshot snapshot = GearSnapshot.builder()
			.equippedItemIds(ids)
			.attack(75, 80)
			.strength(80, 85)
			.activePrayers(EnumSet.of(OffensivePrayer.PIETY, OffensivePrayer.RIGOUR))
			.onSlayerTask(true)
			.build();

		assertEquals(4587, snapshot.itemIdAt(3));
		assertEquals(-1, snapshot.itemIdAt(0));
		assertEquals(75, snapshot.baseAttack());
		assertEquals(80, snapshot.boostedAttack());
		assertEquals(2, snapshot.activePrayers().size());
		assertTrue(snapshot.onSlayerTask());
	}

	@Test
	public void sessionSnapshot_backwardCompatibleConstructorDefaultsToEmptyGear()
	{
		SessionSnapshot snapshot = new SessionSnapshot(
			0L, 0L, 0L, 0L, 0L, 0L, false,
			java.util.Collections.emptyList(), java.util.Collections.emptyMap(), 0L, null);

		assertEquals(GearSnapshot.empty().activePrayers(), snapshot.getGear().activePrayers());
	}
}
