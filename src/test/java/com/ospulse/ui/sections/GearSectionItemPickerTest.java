package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.optimizer.LoadoutOverride;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers QA fix 6 (item picker -&gt; icon grid): clicking a gear-grid slot opens
 * a {@link EquipmentIndexRepository#searchSlot}-backed 4-columns-wide icon
 * grid (not a text {@code JList}), the text search box still filters it, a
 * real click on a grid cell applies the override via the same
 * {@code applyOverride} path the old list used, and a dedicated close button
 * dismisses the picker (previously the only way to close it was re-clicking
 * the same slot).
 */
public class GearSectionItemPickerTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
	private static void onEdt(Runnable body)
	{
		try
		{
			SwingUtilities.invokeAndWait(body);
		}
		catch (InvocationTargetException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			throw new RuntimeException(cause);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	private static final CollapsibleSection.CollapseStore NO_STORE = new CollapsibleSection.CollapseStore()
	{
		@Override
		public boolean isCollapsed(String key)
		{
			return false;
		}

		@Override
		public void setCollapsed(String key, boolean collapsed)
		{
		}
	};

	private static final int ABYSSAL_WHIP = 4151;
	private static final int DRAGON_SCIMITAR = 4587;

	private static int[] loadout(int weaponId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[3] = weaponId;
		return ids;
	}

	private static GearSnapshot gearFor(int[] itemIds)
	{
		EquipmentStats stats = WhatIfLoadout.buildEquipmentStats(itemIds, LoadoutOverride.empty());
		return GearSnapshot.builder()
			.equippedItemIds(itemIds)
			.attack(99, 99)
			.strength(99, 99)
			.defence(99, 99)
			.ranged(99, 99)
			.magic(99, 99)
			.prayer(77, 77)
			.hitpoints(99, 99)
			.equipmentStats(stats)
			.build();
	}

	private static SessionSnapshot snapshotWith(GearSnapshot gear)
	{
		return new SessionSnapshot(0L, 0L, 0L, 0L, 0L, 0L, false,
			null, null, 0L, null, null, null, null, 0L, gear);
	}

	@Test
	public void openingASlot_showsTheIconGridNotAList()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP))));

			assertFalse(section.itemGridVisibleForTest());
			assertFalse(section.itemSearchRowVisibleForTest());

			section.clickSlotForTest(3); // WEAPON

			assertTrue(section.itemSearchRowVisibleForTest());
			assertTrue(section.itemGridVisibleForTest());
			assertTrue("the weapon slot has candidates", section.itemGridCellCountForTest() > 0);
			assertEquals(section.filteredItemsForTest().size(), section.itemGridCellCountForTest());
		});
	}

	@Test
	public void typingInTheSearchBox_refiltersTheGrid()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP))));
			section.clickSlotForTest(3);

			int unfilteredCount = section.itemGridCellCountForTest();

			section.itemSearchFieldForTest().setText("dragon scimitar");

			List<EquipmentIndexRepository.Entry> results = section.filteredItemsForTest();
			assertTrue("dragon scimitar must appear in the weapon-slot search", !results.isEmpty());
			assertEquals(results.size(), section.itemGridCellCountForTest());
			assertTrue("a specific search must narrow the grid vs the unfiltered slot list",
				section.itemGridCellCountForTest() <= unfilteredCount);
			for (EquipmentIndexRepository.Entry e : results)
			{
				assertEquals(3, e.slotOrdinal());
			}
		});
	}

	@Test
	public void clickingAGridCell_appliesTheOverrideAndClosesThePicker()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearFor(loadout(ABYSSAL_WHIP));
			section.apply(snapshotWith(gear));
			section.clickSlotForTest(3);
			section.itemSearchFieldForTest().setText("dragon scimitar");

			int idx = -1;
			for (int i = 0; i < section.filteredItemsForTest().size(); i++)
			{
				if (section.filteredItemsForTest().get(i).itemId() == DRAGON_SCIMITAR)
				{
					idx = i;
					break;
				}
			}
			assertTrue(idx >= 0);

			// A REAL mouse click on the grid cell itself (not the filteredItems
			// test seam) — exercises ItemGridCell's own MouseListener.
			section.clickItemGridCellForTest(idx);

			assertEquals(DRAGON_SCIMITAR, section.overrideForTest().itemIdFor(3));
			assertFalse("picker must close after a pick", section.itemGridVisibleForTest());
			assertFalse(section.itemSearchRowVisibleForTest());
			assertEquals(-1, section.searchOpenForSlotForTest());
		});
	}

	@Test
	public void closeButton_dismissesThePickerWithoutPickingAnything()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP))));
			section.clickSlotForTest(3);
			assertTrue(section.itemGridVisibleForTest());

			section.clickCloseItemSearchForTest();

			assertFalse(section.itemGridVisibleForTest());
			assertFalse(section.itemSearchRowVisibleForTest());
			assertEquals(-1, section.searchOpenForSlotForTest());
			// No override was applied — closing is not the same as picking.
			assertTrue(section.overrideForTest().isEmpty());
		});
	}
}
