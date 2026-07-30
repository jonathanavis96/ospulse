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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers issue #11's ironman "owned gear only" optimiser mode: a real
 * restriction (budget forced to 0 in the optimiser request, never mutating
 * the user's stored/typed budget) plus an upgrade-UI mask (the buy-side
 * budget column and every upgrade-oriented result row hide, while the
 * expensive-item risk column, "Optimised for", the "Best setup for this
 * target" heading, the style selector and the visible "Find Best" button all
 * stay up — the regression guard for the {@code setOptimizerStatRowsVisible}
 * split into {@code setOptimizerStyleRowVisible} / {@code
 * setUpgradeStatRowsVisible}). The risk column stays reachable for ironmen
 * because the cap is about what they are willing to LOSE, not what they can
 * buy — see {@code GearSection#riskColumn}'s javadoc.
 */
public class GearSectionOwnedOnlyModeTest
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

	/**
	 * Stubs a logged-in account whose PER-PROFILE {@code ironmanOwnedOnly}
	 * value is {@code rawIronmanOwnedOnly} — {@link GearSection#ironmanOwnedOnlyPref()}
	 * is now a merged per-account read (issue #11 leak fix) that never
	 * consults the client-wide {@code ironmanOwnedOnly} key directly, so
	 * these tests (which only care about the resolved boolean, not the
	 * profile-vs-global distinction) stub the per-profile path — the most
	 * direct analogue of "this account's setting is X".
	 */
	private static ConfigManager mockConfigManager(String rawIronmanOwnedOnly)
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("test-profile");
		Mockito.when(configManager.getRSProfileConfiguration(
				com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
			.thenReturn(rawIronmanOwnedOnly);
		return configManager;
	}

	// --------------------------------------------------------- budget forcing

	@Test
	public void ownedOnlyOn_forcesRequestBudgetToZero_butPreservesStoredBudget()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.setBudgetTextForTest("50");
			section.setBudgetUnitMillionsForTest(true);

			assertEquals("owned-only mode must force the optimiser's request budget to 0",
				0L, section.resolvedBudgetForTest());
			assertEquals("the user's stored/typed budget must be untouched by owned-only mode",
				50_000_000L, section.storedBudgetForTest());
		});
	}

	@Test
	public void ownedOnlyOff_requestBudgetMatchesStoredBudget()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("false");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.setBudgetTextForTest("50");
			section.setBudgetUnitMillionsForTest(true);

			assertEquals(50_000_000L, section.resolvedBudgetForTest());
			assertEquals(50_000_000L, section.storedBudgetForTest());
		});
	}

	@Test
	public void togglingOwnedOnlyModeOff_restoresTheStoredBudget_withoutTouchingTheField()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.setBudgetTextForTest("50");
			section.setBudgetUnitMillionsForTest(true);
			assertEquals("mode ON: request budget forced to 0", 0L, section.resolvedBudgetForTest());

			// Toggle the mode off (e.g. the user unticks the RuneLite plugin
			// setting) — resolvedBudget() is a LIVE read (see GearSection#ironmanOwnedOnlyPref),
			// so re-stubbing the mock and re-reading is exactly what a live
			// config change looks like from GearSection's perspective.
			Mockito.when(configManager.getRSProfileConfiguration(com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
				.thenReturn("false");

			assertEquals("mode OFF: the stored 50M budget must be restored exactly, unmutated",
				50_000_000L, section.resolvedBudgetForTest());
			assertEquals(50_000_000L, section.storedBudgetForTest());
		});
	}

	// ------------------------------------------------------- widget hiding

	@Test
	public void ownedOnlyOn_hidesUpgradeUi_butKeepsRequiredWidgetsVisible()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			assertFalse("budget column must be hidden outright in owned-only mode",
				section.budgetColumnForTest().isVisible());
			assertTrue("risk column must stay reachable for ironmen (issue #11)",
				section.riskColumnForTest().isVisible());

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			assertTrue("a usable result must still show the result panel",
				section.optimizerResultVisibleForTest());

			assertFalse("'Best DPS found' row must hide", section.optimizerDpsRowVisibleForTest());
			assertFalse("'vs owned-only' row must hide", section.optimizerDeltaRowVisibleForTest());
			assertFalse("'Total spend' row must hide", section.optimizerSpendRowVisibleForTest());
			assertFalse("'DPS per gp spent' row must hide", section.optimizerDpsPerGpRowVisibleForTest());
			assertFalse("the swap list + heading must hide", section.optimizerSwapListVisibleForTest());

			// Regression guard for the setOptimizerStatRowsVisible split.
			assertTrue("'Optimised for' must stay visible — it describes the pick, not an upgrade",
				section.optimizerStyleRowVisibleForTest());
			assertTrue("the 'Best setup for this target' heading must stay visible",
				section.optimizerHeadingVisibleForTest());
			assertTrue("the style selector must stay visible",
				section.optimizerStyleSelectorVisibleForTest());
			assertTrue("the visible 'Find Best' grid button must stay visible",
				section.findBestSetupGridButtonForTest().isVisible());
		});
	}

	@Test
	public void ownedOnlyOff_showsUpgradeUi_withUsableResult()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("false");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			assertTrue("budget column must be visible when owned-only mode is off",
				section.budgetColumnForTest().isVisible());

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			assertTrue(section.optimizerResultVisibleForTest());

			assertTrue("'Best DPS found' row must be visible", section.optimizerDpsRowVisibleForTest());
			assertTrue("'vs owned-only' row must be visible", section.optimizerDeltaRowVisibleForTest());
			assertTrue("'Total spend' row must be visible", section.optimizerSpendRowVisibleForTest());
			assertTrue("'DPS per gp spent' row must be visible", section.optimizerDpsPerGpRowVisibleForTest());
			assertTrue("the swap list + heading must be visible", section.optimizerSwapListVisibleForTest());

			assertTrue(section.optimizerStyleRowVisibleForTest());
			assertTrue(section.optimizerHeadingVisibleForTest());
			assertTrue(section.optimizerStyleSelectorVisibleForTest());
			assertTrue(section.findBestSetupGridButtonForTest().isVisible());
		});
	}

	// ---------------------------------------- P2 fix: reacts after construction

	@Test
	public void configChangedAfterConstruction_offToOn_hidesUpgradeUiWithoutRebuild()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("false");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			assertTrue("sanity: budget column visible before the config change",
				section.budgetColumnForTest().isVisible());
			assertTrue(section.optimizerDpsRowVisibleForTest());

			// P2 fix: the panel used to only compute this once, at
			// construction — refreshIronmanOwnedOnlyMode is the new live
			// recompute hook a config-change listener calls. Same JLabel/
			// JPanel objects throughout (final fields, never reassigned), so
			// this can only ever setVisible on them, never rebuild.
			Mockito.when(configManager.getRSProfileConfiguration(com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
				.thenReturn("true");
			section.refreshIronmanOwnedOnlyMode();

			assertFalse("budget column must hide once the config flips on, with no rebuild needed",
				section.budgetColumnForTest().isVisible());
			assertTrue("risk column must stay reachable for ironmen (issue #11)",
				section.riskColumnForTest().isVisible());
			assertFalse(section.optimizerDpsRowVisibleForTest());
			assertFalse(section.optimizerDeltaRowVisibleForTest());
			assertFalse(section.optimizerSpendRowVisibleForTest());
			assertFalse(section.optimizerDpsPerGpRowVisibleForTest());
			assertFalse(section.optimizerSwapListVisibleForTest());

			assertTrue("'Optimised for' must stay visible", section.optimizerStyleRowVisibleForTest());
			assertTrue("the heading must stay visible", section.optimizerHeadingVisibleForTest());
			assertTrue("the style selector must stay visible", section.optimizerStyleSelectorVisibleForTest());
			assertTrue("Find Best must stay visible", section.findBestSetupGridButtonForTest().isVisible());
		});
	}

	@Test
	public void configChangedAfterConstruction_onToOff_restoresUpgradeUiAndStoredBudget()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.setBudgetTextForTest("50");
			section.setBudgetUnitMillionsForTest(true);
			assertEquals("sanity: still forced to 0 while on", 0L, section.resolvedBudgetForTest());

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.runOptimizerSyncForTest();

			assertFalse("budget column must be hidden while owned-only mode is on",
				section.budgetColumnForTest().isVisible());
			assertTrue("risk column must stay reachable for ironmen (issue #11)",
				section.riskColumnForTest().isVisible());
			assertFalse(section.optimizerDpsRowVisibleForTest());

			Mockito.when(configManager.getRSProfileConfiguration(com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
				.thenReturn("false");
			section.refreshIronmanOwnedOnlyMode();

			assertTrue("budget column must return once the config flips off",
				section.budgetColumnForTest().isVisible());
			assertTrue(section.optimizerDpsRowVisibleForTest());
			assertTrue(section.optimizerDeltaRowVisibleForTest());
			assertTrue(section.optimizerSpendRowVisibleForTest());
			assertTrue(section.optimizerDpsPerGpRowVisibleForTest());
			assertTrue(section.optimizerSwapListVisibleForTest());

			assertEquals("the stored 50M budget must be restored, not left forced at 0",
				50_000_000L, section.resolvedBudgetForTest());
			assertEquals(50_000_000L, section.storedBudgetForTest());
		});
	}

	// --------------------------------------- stale result clears on OFF->ON

	private static final int DRAGON_SCIMITAR = 4587;

	/**
	 * Coordinator-flagged Codex/CodeRabbit finding on PR #19: a result found
	 * while owned-only mode was OFF (here, a real upgrade from a Bronze
	 * sword to an owned-via-wealth Dragon scimitar) must not silently survive
	 * an OFF-&gt;ON mode flip — {@link GearSection#refreshIronmanOwnedOnlyMode()}
	 * must clear the stale result, its auto-applied what-if override/preview,
	 * AND the bank highlight armed from it, asserting on the actual cleared
	 * state (not merely that the method ran without throwing).
	 */
	@Test
	public void refreshIronmanOwnedOnlyMode_offToOnWithStaleResult_clearsResultOverrideAndBankHighlight()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("false");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);
			BankRecommendationHighlighter bankHighlighter = Mockito.mock(BankRecommendationHighlighter.class);
			section.setBankHighlighter(bankHighlighter);

			List<ItemStack> holdings = new ArrayList<>();
			holdings.add(new ItemStack(DRAGON_SCIMITAR, "Dragon scimitar", 1, 100_000L));
			WealthSnapshot wealth = WealthSnapshot.builder().topHoldings(holdings).build();

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealth));
			pickCerberus(section);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			// Sanity: a real result/preview exists before the mode flips on —
			// B8-4's auto-preview already applied it to the what-if override.
			assertTrue("sanity: a stale result must exist before the flip",
				section.lastOptimizerResultForTest() != null);
			assertFalse("sanity: the auto-preview must have applied an override",
				section.overrideForTest().isEmpty());

			Mockito.when(configManager.getRSProfileConfiguration(com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
				.thenReturn("true");
			section.refreshIronmanOwnedOnlyMode();

			assertEquals("the stale optimiser result must be cleared, not just hidden",
				null, section.lastOptimizerResultForTest());
			assertTrue("the auto-applied what-if override/preview must be cleared",
				section.overrideForTest().isEmpty());
			assertFalse("the stale result panel must be hidden, not left showing unowned gear",
				section.optimizerResultVisibleForTest());
			Mockito.verify(bankHighlighter, Mockito.atLeastOnce()).clear();
		});
	}

	/**
	 * A result computed WHILE owned-only mode is already ON is always
	 * budget-0 (hence always owned-only-safe — see {@code
	 * OwnedOnlyMode#effectiveBudget}), so an unrelated later refresh call
	 * (e.g. the RS-profile-change mirror re-triggering) must NOT clear it —
	 * only the OFF-&gt;ON transition itself is a leak risk. Regression guard
	 * for {@code GearSection}'s internal "last known owned-only pref"
	 * tracking gating on the transition rather than "currently on".
	 */
	@Test
	public void refreshIronmanOwnedOnlyMode_alreadyOn_doesNotClearAFreshOwnedOnlySafeResult()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.runOptimizerSyncForTest();

			assertTrue("sanity: a result exists, computed while already owned-only",
				section.lastOptimizerResultForTest() != null);

			// No config change at all — mirrors a later unrelated refresh call
			// (e.g. RS-profile-change mirroring) with the mode still ON.
			section.refreshIronmanOwnedOnlyMode();

			assertTrue("a result computed while owned-only was already on must survive an unrelated refresh",
				section.lastOptimizerResultForTest() != null);
		});
	}

	// ------------------------- P1-B: in-flight search invalidated on OFF->ON

	/**
	 * Codex P1 finding on PR #19 ({@code GearSection.java:4843}, "Invalidate
	 * searches started before owned-only activation"): a search launched
	 * under the previous (possibly nonzero) budget that is STILL IN FLIGHT
	 * when owned-only mode flips OFF-&gt;ON — so {@link
	 * GearSection#lastOptimizerResultForTest()} is still {@code null} at the
	 * moment of the flip, meaning the {@code lastOptimizerResult != null}
	 * guard covered by {@link #refreshIronmanOwnedOnlyMode_offToOnWithStaleResult_clearsResultOverrideAndBankHighlight}
	 * has nothing to clear — must never install its eventually-arriving
	 * result. {@code GearSection#refreshIronmanOwnedOnlyMode()} now bumps a
	 * generation token unconditionally on the OFF-&gt;ON transition; real
	 * search launches capture it up front (before price resolution/the
	 * {@code SwingWorker} hop), so a result stamped with a since-stale
	 * generation is dropped instead of installed/auto-previewed/bank-
	 * highlighted.
	 *
	 * <p>Real end-to-end async timing isn't reproducible deterministically
	 * in a headless test, so this drives the install-or-drop decision
	 * directly via {@code installOptimizerResultForTest} with a generation
	 * captured BEFORE the flip — exactly what {@code
	 * installResultIfCurrent} is handed for the real async paths.
	 */
	@Test
	public void staleSearchGeneration_capturedBeforeOffToOnFlip_isNeverInstalled()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("false");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);
			BankRecommendationHighlighter bankHighlighter = Mockito.mock(BankRecommendationHighlighter.class);
			section.setBankHighlighter(bankHighlighter);

			List<ItemStack> holdings = new ArrayList<>();
			holdings.add(new ItemStack(DRAGON_SCIMITAR, "Dragon scimitar", 1, 100_000L));
			WealthSnapshot wealth = WealthSnapshot.builder().topHoldings(holdings).build();

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealth));
			pickCerberus(section);
			section.setBudgetTextForTest("0");

			// Capture the generation an in-flight search would have been
			// stamped with, and a real usable result to stand in for it —
			// runOptimizerSyncForTest() here mirrors exactly what the
			// in-flight search would eventually compute (a real Bronze
			// sword -> Dragon scimitar upgrade), all while the generation
			// token hasn't moved yet.
			int staleGeneration = section.optimizerGenerationForTest();
			section.runOptimizerSyncForTest();
			GearOptimizer.Result staleResult = section.lastOptimizerResultForTest();
			assertTrue("sanity: a real usable result exists to stand in for the in-flight search",
				staleResult != null && staleResult.style() != null);

			// Simulate the search NOT having landed yet at flip time —
			// mirrors the real race, where price resolution/the SwingWorker
			// is still running when the flip happens — without touching the
			// generation token itself.
			section.clickResetAllForTest();
			assertNull("sanity: nothing installed yet — mirrors the in-flight race",
				section.lastOptimizerResultForTest());
			Mockito.clearInvocations(bankHighlighter);

			// The mode flips ON while that search is (simulated) still in
			// flight — lastOptimizerResult is null here, so the pre-existing
			// guard alone would have had nothing to clear (the exact bug).
			Mockito.when(configManager.getRSProfileConfiguration(com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
				.thenReturn("true");
			section.refreshIronmanOwnedOnlyMode();

			// The stale search's result finally "lands", stamped with the
			// generation captured before the flip.
			section.installOptimizerResultForTest(staleResult, staleGeneration);

			assertNull("a result from before the OFF->ON flip must never be installed",
				section.lastOptimizerResultForTest());
			assertTrue("no what-if override may be auto-applied from a stale result",
				section.overrideForTest().isEmpty());
			Mockito.verify(bankHighlighter, Mockito.never()).showInBank(Mockito.anyMap(), Mockito.anyList());
		});
	}
}
