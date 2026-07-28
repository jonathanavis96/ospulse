package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.optimizer.GearOptimizer;
import com.ospulse.combat.optimizer.LoadoutOverride;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.integration.BankRecommendationHighlighter;
import com.ospulse.model.ItemStack;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.wealth.WealthSnapshot;

import net.runelite.client.config.ConfigManager;

import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * P2-A fix (Codex finding on PR #19, {@code GearSection.java:4789},
 * "Revalidate every recommended item against current ownership"): owned-only
 * mode's core guarantee is that every recommended item is one the player
 * owns. The pre-existing {@code OwnedOnlyMandatoryOverrideGate} check only
 * covered a target's mandatory-override forced item — it never re-checked
 * the ORDINARY (non-mandatory) slot choices the optimiser resolved from
 * whatever {@code ownedPrices} snapshot existed when the search launched.
 * Two distinct gaps, covered one test class each:
 *
 * <ul>
 * <li>Half 1 ({@link #atInstall_resolvedLoadoutIdNoLongerOwned_blocksInsteadOfInstalling()}):
 * a search result finally lands (via {@code installOptimizerResultForTest},
 * mirroring the real async {@code SwingWorker} path) referencing an item the
 * player owned when the search launched but no longer owns by the time the
 * result arrives.</li>
 * <li>Half 2 ({@link #afterInstall_laterApplyWithReducedHoldings_invalidatesTheInstalledResult()}):
 * a result that WAS fully owned at install time goes stale afterwards when a
 * later {@code apply(SessionSnapshot)} (a routine wealth-snapshot refresh)
 * reports the item as no longer held.</li>
 * </ul>
 */
public class GearSectionOwnedOnlyResultRevalidationTest
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

	private static WealthSnapshot dragonScimitarHeld()
	{
		List<ItemStack> holdings = new ArrayList<>();
		holdings.add(new ItemStack(DRAGON_SCIMITAR, "Dragon scimitar", 1, 100_000L));
		return WealthSnapshot.builder().topHoldings(holdings).build();
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

	/** See {@code GearSectionOwnedOnlyModeTest#mockConfigManager}. */
	private static ConfigManager mockConfigManager(String rawIronmanOwnedOnly)
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("test-profile");
		Mockito.when(configManager.getRSProfileConfiguration(
				com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
			.thenReturn(rawIronmanOwnedOnly);
		return configManager;
	}

	// ------------------------------------------------------------- half 1

	/**
	 * Simulates the real race directly: the search that produced {@code
	 * result} launched while Dragon scimitar was owned, but by the time the
	 * result "lands" (here: is handed to {@code installOptimizerResultForTest},
	 * exactly what {@code installResultIfCurrent} does for the real
	 * async {@code SwingWorker} paths — real end-to-end async timing isn't
	 * reproducible deterministically in a headless test), the player has sold
	 * it. Owned-only mode's guarantee means this must never be installed,
	 * previewed, or bank-highlighted.
	 */
	@Test
	public void atInstall_resolvedLoadoutIdNoLongerOwned_blocksInsteadOfInstalling()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);
			BankRecommendationHighlighter bankHighlighter = Mockito.mock(BankRecommendationHighlighter.class);
			section.setBankHighlighter(bankHighlighter);

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), dragonScimitarHeld()));
			pickCerberus(section);
			int generation = section.optimizerGenerationForTest();
			section.runOptimizerSyncForTest();

			GearOptimizer.Result staleResult = section.lastOptimizerResultForTest();
			assertTrue("sanity: a real usable result exists, recommending the owned Dragon scimitar",
				staleResult != null && staleResult.style() != null);
			assertTrue("sanity: the resolved loadout actually references the Dragon scimitar",
				section.optimizerLoadoutSlotMapForTest(staleResult).containsValue(DRAGON_SCIMITAR));

			// Simulate the search NOT having landed yet (mirrors the real
			// async race, without touching the generation token), then the
			// player selling the Dragon scimitar before it does.
			section.clickResetAllForTest();
			assertNull("sanity: nothing installed yet", section.lastOptimizerResultForTest());
			Mockito.clearInvocations(bankHighlighter);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));

			// The stale search's result finally lands.
			section.installOptimizerResultForTest(staleResult, generation);

			assertNull("a resolved id no longer owned must never be installed",
				section.lastOptimizerResultForTest());
			assertTrue("no what-if override may be auto-applied from an unowned result",
				section.overrideForTest().isEmpty());
			Mockito.verify(bankHighlighter, Mockito.never()).showInBank(Mockito.anyMap(), Mockito.anyList());
		});
	}

	// ------------------------------------------------------------- half 2

	/**
	 * A result that WAS fully owned when installed (Dragon scimitar owned,
	 * auto-previewed, bank-highlighted) must not survive the player then
	 * selling it — {@code apply(SessionSnapshot)} refreshing {@link
	 * GearSection#ownedPriceMapForTest()}'s underlying wealth on a later,
	 * routine snapshot must catch that and revert, not silently leave the
	 * stale recommendation installed indefinitely.
	 */
	@Test
	public void afterInstall_laterApplyWithReducedHoldings_invalidatesTheInstalledResult()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);
			BankRecommendationHighlighter bankHighlighter = Mockito.mock(BankRecommendationHighlighter.class);
			section.setBankHighlighter(bankHighlighter);

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), dragonScimitarHeld()));
			pickCerberus(section);
			section.runOptimizerSyncForTest();

			assertTrue("sanity: a real result was installed while the Dragon scimitar was owned",
				section.lastOptimizerResultForTest() != null);
			assertFalse("sanity: the auto-preview applied an override",
				section.overrideForTest().isEmpty());
			Mockito.verify(bankHighlighter, Mockito.atLeastOnce()).showInBank(Mockito.anyMap(), Mockito.anyList());
			Mockito.clearInvocations(bankHighlighter);

			// The player sells the Dragon scimitar — a later, routine wealth
			// snapshot (same gear, no more holdings) must invalidate the
			// now-stale installed result.
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));

			assertNull("the now-stale installed result must be cleared, not left installed",
				section.lastOptimizerResultForTest());
			assertTrue("the auto-applied what-if override/preview must be reverted",
				section.overrideForTest().isEmpty());
			assertFalse("the stale result panel must be hidden, not left showing unowned gear",
				section.optimizerResultVisibleForTest());
			Mockito.verify(bankHighlighter, Mockito.atLeastOnce()).clear();
		});
	}

	/**
	 * Regression guard alongside the invalidation test above: an unrelated
	 * later {@code apply()} (same holdings, e.g. a routine gear-tick refresh)
	 * must NOT clear a result that is still fully owned — only an actual
	 * ownership loss is a leak.
	 */
	@Test
	public void afterInstall_laterApplyWithSameHoldings_leavesTheInstalledResultAlone()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), dragonScimitarHeld()));
			pickCerberus(section);
			section.runOptimizerSyncForTest();

			assertTrue("sanity: a real result was installed",
				section.lastOptimizerResultForTest() != null);

			// Same gear, same holdings — a routine unrelated refresh.
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), dragonScimitarHeld()));

			assertTrue("a result whose items are all still owned must survive an unrelated apply()",
				section.lastOptimizerResultForTest() != null);
		});
	}
}
