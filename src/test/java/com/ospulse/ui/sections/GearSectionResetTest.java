package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.optimizer.LoadoutOverride;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.model.ItemStack;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.wealth.WealthSnapshot;

import org.junit.Test;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Feature 11 — the gear panel's slice of the panel-wide full Reset. A reset
 * returns the gear panel to fresh (no target, no what-if override, no optimiser
 * preview) but KEEPS the user's persisted preferences (excluded items / potion
 * variants), which are config-backed choices rather than session state.
 */
public class GearSectionResetTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
	private static final int ABYSSAL_WHIP = 4151;
	private static final int DRAGON_SCIMITAR = 4587;
	private static final int WEAPON_SLOT = 3;

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

	private static int[] loadout(int weaponId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[WEAPON_SLOT] = weaponId;
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

	private static WealthSnapshot wealthWith(int... itemIds)
	{
		List<ItemStack> holdings = new ArrayList<>();
		for (int id : itemIds)
		{
			holdings.add(new ItemStack(id, "item " + id, 1, 100_000L));
		}
		return WealthSnapshot.builder().topHoldings(holdings).build();
	}

	private static SessionSnapshot snapshotWith(GearSnapshot gear, WealthSnapshot wealth)
	{
		return new SessionSnapshot(0L, 0L, 0L, 0L, 0L, 0L, false,
			null, null, 0L, wealth, null, null, null, 0L, gear, 0L, null, 0L);
	}

	private static int indexOf(ListModel<String> model, String name)
	{
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	@Test
	public void fullResetClearsGearSelectionsButKeepsPersistedPrefs()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), wealthWith(ABYSSAL_WHIP, DRAGON_SCIMITAR)));

			// Pick a target monster.
			section.searchFieldForTest().setText("cerberus");
			int monsterIdx = indexOf(section.monsterListForTest().getModel(), "Cerberus");
			assertTrue("Cerberus must be in the filtered list", monsterIdx >= 0);
			section.monsterListForTest().setSelectedIndex(monsterIdx);
			assertNotNull(section.selectedMonsterForTest());

			// Apply a what-if weapon override.
			section.clickSlotForTest(WEAPON_SLOT);
			section.itemSearchFieldForTest().setText("dragon scimitar");
			section.pickItemForTest(0);
			assertFalse("a what-if override is active", section.overrideForTest().isEmpty());

			// A persisted preference: exclude an item from optimiser suggestions.
			section.excludeItemFromSuggestionsForTest(DRAGON_SCIMITAR);
			assertTrue(section.excludedItemIdsForTest().contains(DRAGON_SCIMITAR));

			// Produce an optimiser result on screen (owned-only search, no resolver).
			section.runOptimizerSyncForTest();

			// --- full panel reset ---
			section.resetState();

			assertNull("reset clears the target monster", section.selectedMonsterForTest());
			assertTrue("reset clears the what-if override", section.overrideForTest().isEmpty());
			assertNull("reset clears the optimiser result", section.lastOptimizerResultForTest());
			assertFalse("reset re-detects style, dropping the manual optimiser-style lock",
				section.optimizerStyleUserPickedForTest());
			assertTrue("reset KEEPS persisted excluded-item preferences",
				section.excludedItemIdsForTest().contains(DRAGON_SCIMITAR));
		});
	}
}
