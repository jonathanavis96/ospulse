package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.optimizer.GearOptimizer;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the Phase 3 optimiser wiring in {@link GearSection} (design spec
 * section 3): the owned pool is built from worn gear + {@code WealthSnapshot}
 * top holdings, a budget unlocks affordable upgrades, and "apply to readout"
 * hands the result to the Phase 2 what-if overrides unchanged. Uses
 * {@code runOptimizerSyncForTest} (see {@link GearSection}) to avoid awaiting
 * the real {@code SwingWorker} background thread deterministically.
 */
public class GearSectionOptimizerTest
{
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

	private static final int BRONZE_SWORD = 1277;
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

	private static void pickCerberus(GearSection section)
	{
		section.searchFieldForTest().setText("cerberus");
		int index = indexOf(section.monsterListForTest().getModel(), "Cerberus");
		assertTrue("Cerberus must appear in the filtered list", index >= 0);
		section.monsterListForTest().setSelectedIndex(index);
	}

	@Test
	public void ownedOnlyBudget_findsNoBetterWeaponThanLive()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			assertTrue(section.optimizerResultVisibleForTest());
			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals(0L, result.totalSpend());
		});
	}

	@Test
	public void bankHoldingsBecomeOwnedPool_evenWithoutSpend()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);

			// Player is wielding the bronze sword but has a dragon scimitar sitting
			// in the bank — WealthSnapshot.topHoldings is the ONLY source that
			// surfaces it (see GearSection.ownedPriceMap), so the optimizer should
			// pick it up as a free (already-owned) upgrade with budget 0.
			List<ItemStack> holdings = new ArrayList<>();
			holdings.add(new ItemStack(DRAGON_SCIMITAR, "Dragon scimitar", 1, 100_000L));
			WealthSnapshot wealth = WealthSnapshot.builder().topHoldings(holdings).build();

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealth));
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals(0L, result.totalSpend()); // owned, not purchased
			boolean foundScimitar = false;
			for (GearOptimizer.SlotChoice choice : result.loadout())
			{
				if (choice.slotOrdinal() == WhatIfLoadout.WEAPON_SLOT && choice.itemId() == DRAGON_SCIMITAR)
				{
					foundScimitar = true;
					assertTrue("the bank scimitar must be reported as owned, not purchased", choice.owned());
				}
			}
			assertTrue("optimizer must pick up the bank-held scimitar as a free upgrade", foundScimitar);
		});
	}

	@Test
	public void applyToReadout_createsOverridesMatchingTheResult()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			List<ItemStack> holdings = new ArrayList<>();
			holdings.add(new ItemStack(DRAGON_SCIMITAR, "Dragon scimitar", 1, 100_000L));
			WealthSnapshot wealth = WealthSnapshot.builder().topHoldings(holdings).build();

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealth));
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();
			section.clickApplyOptimizerResultForTest();

			assertEquals(DRAGON_SCIMITAR, section.overrideForTest().itemIdFor(WhatIfLoadout.WEAPON_SLOT));
			assertTrue(section.whatIfRowVisibleForTest());
		});
	}

	/**
	 * Regression for the reported "clicked reset, nothing happened" bug: after
	 * applying an optimiser result (the what-if preview + the visible result
	 * panel/"highlight"), a single reset must clear BOTH — the overrides (so
	 * the readout goes back to real worn gear) AND the optimiser result panel
	 * (so no stale "Apply to readout" affordance lingers looking unchanged).
	 */
	@Test
	public void resetAfterApplyingOptimizerResult_clearsOverridesAndTheResultPanel()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			List<ItemStack> holdings = new ArrayList<>();
			holdings.add(new ItemStack(DRAGON_SCIMITAR, "Dragon scimitar", 1, 100_000L));
			WealthSnapshot wealth = WealthSnapshot.builder().topHoldings(holdings).build();

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealth));
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();
			section.clickApplyOptimizerResultForTest();
			assertFalse(section.overrideForTest().isEmpty());
			assertTrue(section.optimizerResultVisibleForTest());

			section.clickResetAllForTest();

			assertTrue("overrides must be cleared", section.overrideForTest().isEmpty());
			assertEquals(BRONZE_SWORD, section.renderedSlotIdForTest(WhatIfLoadout.WEAPON_SLOT));
			assertEquals(null, section.lastOptimizerResultForTest());
			assertFalse("the stale optimiser result panel must be hidden by reset",
				section.optimizerResultVisibleForTest());
		});
	}

	@Test
	public void noTargetSelected_doesNotRunTheSearch()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			// No target picked yet — clicking the REAL button must not crash and
			// must not produce a result (the guard in runOptimizer()).
			section.findBestSetupButtonForTest().doClick();
			assertEquals(null, section.lastOptimizerResultForTest());
			assertTrue(section.optimizerStatusTextForTest().toLowerCase(java.util.Locale.ROOT).contains("target"));
		});
	}

	@Test
	public void parseBudget_handlesKAndMSuffixesAndPlainNumbers()
	{
		assertEquals(0L, GearSection.parseBudget(null));
		assertEquals(0L, GearSection.parseBudget(""));
		assertEquals(0L, GearSection.parseBudget("not a number"));
		assertEquals(0L, GearSection.parseBudget("0"));
		assertEquals(500L, GearSection.parseBudget("500"));
		assertEquals(500_000L, GearSection.parseBudget("500k"));
		assertEquals(10_000_000L, GearSection.parseBudget("10m"));
		assertEquals(10_000_000L, GearSection.parseBudget("10M"));
		assertEquals(1_500_000L, GearSection.parseBudget("1.5m"));
		assertEquals(2_000_000L, GearSection.parseBudget("2,000,000"));
	}
}
