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
			// in the bank. This snapshot carries only topHoldings (no allHoldings) —
			// the legacy-fallback path in GearSection.ownedPriceMap — and the
			// optimizer must still pick the scimitar up as a free (already-owned)
			// upgrade with budget 0.
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

	/**
	 * Ownership must be MEMBERSHIP-based, decoupled from GE value: an owned
	 * 0-value untradeable (a fire cape in the bank) never makes the
	 * top-50-BY-VALUE {@code topHoldings} cut, but it IS in the snapshot's
	 * complete {@code allHoldings} map (the same full item set the bank
	 * valuation uses) — and that is what {@code ownedPriceMap()} must read.
	 */
	@Test
	public void zeroValueBankItem_inAllHoldings_countsAsOwned_evenWhenAbsentFromTopHoldings()
	{
		onEdt(() ->
		{
			final int fireCape = 6570;
			GearSection section = new GearSection(NO_STORE, null, null);

			// topHoldings: 50 valuable stacks, fire cape crowded out entirely.
			List<ItemStack> top = new ArrayList<>();
			for (int i = 0; i < 50; i++)
			{
				top.add(new ItemStack(2434 + i, "Valuable stack " + i, 1, 10_000_000L - i));
			}
			// allHoldings: the COMPLETE owned set — includes the 0-value cape.
			java.util.Map<Integer, ItemStack> all = new java.util.LinkedHashMap<>();
			for (ItemStack s : top)
			{
				all.put(s.getId(), s);
			}
			all.put(fireCape, new ItemStack(fireCape, "Fire cape", 1, 0L));
			WealthSnapshot wealth = WealthSnapshot.builder().topHoldings(top).allHoldings(all).build();

			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealth));

			java.util.Map<Integer, Long> owned = section.ownedPriceMapForTest();
			assertTrue("a 0-GE-value bank untradeable must count as owned via allHoldings",
				owned.containsKey(fireCape));
		});
	}

	/**
	 * Bug A (expensive-item risk cap prices worn gear at 0): an item that is
	 * WORN and also sits in the bank must be priced at its real GE value in
	 * {@code ownedPriceMap()} — the map that feeds
	 * {@link GearOptimizer.Request#ownedItemPrices()}, i.e. the expensive-item
	 * RISK cap. The old code {@code put(id, 0L)} for the equipped copy and then
	 * {@code merge(id, realValue, Math::min)} for the bank copy, so {@code min(0,
	 * real)} kept 0 — a 5m worn whip counted as 0 gp of risk, so the cap never
	 * saw any owned gear as "expensive" and silently never bound. The value here
	 * must be the item's real GE value, NOT 0.
	 */
	@Test
	public void equippedItemAlsoInBank_pricedAtRealValueForRiskCap_notZero()
	{
		onEdt(() ->
		{
			final int expensiveWhip = ABYSSAL_WHIP;
			GearSection section = new GearSection(NO_STORE, null, null);

			java.util.Map<Integer, ItemStack> all = new java.util.LinkedHashMap<>();
			all.put(expensiveWhip, new ItemStack(expensiveWhip, "Abyssal whip", 1, 5_000_000L));
			WealthSnapshot wealth = WealthSnapshot.builder().allHoldings(all).build();

			section.apply(snapshotWith(gearFor(loadout(expensiveWhip)), wealth));

			java.util.Map<Integer, Long> owned = section.ownedPriceMapForTest();
			assertEquals("a worn item that is also banked must price at its real GE value for the risk cap, not 0",
				Long.valueOf(5_000_000L), owned.get(expensiveWhip));
		});
	}

	/**
	 * Bug B (end-to-end): the reported "Find best still recommends 20-30M gear
	 * even with the expensive-item cap set" is the SAME owned-price-0 defect as
	 * bug A, exercised through the real GearSection -> GearOptimizer wiring the
	 * cap was never end-to-end tested against. A worn 5m whip, with the cap set
	 * to "0 expensive items allowed, threshold 50k", must be DE-RISKED to a
	 * cheaper owned weapon (a 30k scimitar). Before the ownedPriceMap fix the
	 * worn whip priced at 0, counted as 0 expensive items, and was kept — this
	 * test would have failed. Mirrors
	 * {@code GearOptimizerTest#expensiveCap_forcesCheaperWeaponEvenAtLowerDps}
	 * but proves the wiring feeds the cap each owned item's REAL value.
	 */
	@Test
	public void expensiveCap_deRisksWornExpensiveWeaponThroughGearSectionWiring()
	{
		onEdt(() ->
		{
			final int whip = ABYSSAL_WHIP;         // worn, 5m -> expensive
			final int scimitar = DRAGON_SCIMITAR;  // owned in bank, 30k -> not expensive
			GearSection section = new GearSection(NO_STORE, null, null);

			java.util.Map<Integer, ItemStack> all = new java.util.LinkedHashMap<>();
			all.put(whip, new ItemStack(whip, "Abyssal whip", 1, 5_000_000L));
			all.put(scimitar, new ItemStack(scimitar, "Dragon scimitar", 1, 30_000L));
			WealthSnapshot wealth = WealthSnapshot.builder().allHoldings(all).build();

			section.apply(snapshotWith(gearFor(loadout(whip)), wealth));
			pickCerberus(section);

			section.setBudgetTextForTest("0");                // owned-only: the scimitar is the free de-risk target
			section.setExpensiveCountTextForTest("0");        // zero expensive items allowed
			section.setExpensiveThresholdTextForTest("50k");  // whip is expensive, scimitar is not
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals("a worn 5m whip must be de-risked to the cheaper owned scimitar (the cap now sees its real value)",
				scimitar, weaponIdInResult(result));
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

	/**
	 * Documents the intentional owned-only fallback when {@link GearSection}
	 * has no {@code OptimizerPriceResolver} wired in (e.g. this test's 3-arg
	 * constructor, or a headless/no-client-thread context in production).
	 * With no resolver, {@code runOptimizer}/{@code runOptimizerSyncForTest}
	 * build the {@code priceSource} purely from {@code ownedPriceMap()} —
	 * worn gear + {@code WealthSnapshot} top holdings — and fall back to
	 * {@code 0L} (unaffordable-unless-owned) for anything else. Since a
	 * budget upgrade is by definition an item the player does NOT already
	 * own, this means the budget genuinely cannot buy anything in this mode —
	 * that is correct, not a bug: pricing arbitrary non-owned items requires
	 * an {@code OptimizerPriceResolver} (see
	 * {@code withInjectedPriceResolver_budgetBuysAffordableNonOwnedWeapon}
	 * below for the resolver-driven path where the budget does matter).
	 */
	@Test
	public void withoutPriceResolver_budgetCannotBuyNonOwnedItems_ownedOnlySearch()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Bronze sword is a well-known, drastically-worse weapon than the
			// abyssal whip/dragon scimitar the optimizer engine tests use to
			// prove a real, priced upgrade gets picked — see
			// GearOptimizerTest#budgetAllowsAffordableUpgrade_picksTheBestAffordableWeapon.
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);

			// A huge budget — but with no resolver wired in, nothing non-owned
			// can ever be priced affordable, by design.
			section.setBudgetTextForTest("50m");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals("no resolver: no non-owned item is ever priced affordable, so nothing is ever bought",
				0L, result.totalSpend());
			assertEquals("no resolver: the live bronze sword is kept even with a 50m budget",
				BRONZE_SWORD, weaponIdInResult(result));
		});
	}

	private static final int ABYSSAL_WHIP = 4151;

	/** A synchronous fake resolver — calls {@code onResolved} inline with a fixed price map (everything tradeable), no threading involved. */
	private static GearSection.OptimizerPriceResolver fakeResolver(java.util.Map<Integer, Long> prices)
	{
		return (ids, onResolved) -> onResolved.accept(new GearSection.PriceLookup(prices, java.util.Set.of()));
	}

	/**
	 * The real fix under test: with an {@code OptimizerPriceResolver} injected
	 * (mirroring the production client-thread-backed resolver wired in
	 * {@code OSPulsePlugin}), a large-enough budget now lets the optimiser
	 * propose buying a non-owned item it was never able to see as affordable
	 * before. Mirrors
	 * {@code GearOptimizerTest#budgetAllowsAffordableUpgrade_picksTheBestAffordableWeapon}'s
	 * cheap-scimitar/expensive-whip pattern, but exercised through the real
	 * {@link GearSection} wiring instead of calling {@code GearOptimizer}
	 * directly.
	 */
	@Test
	public void withInjectedPriceResolver_budgetBuysAffordableNonOwnedWeapon()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			prices.put(ABYSSAL_WHIP, 5_000_000L);

			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);

			section.setBudgetTextForTest("100k");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals("the resolver-priced, affordable scimitar upgrade must be bought",
				DRAGON_SCIMITAR, weaponIdInResult(result));
			assertTrue("spend must be > 0 (an actual purchase happened)", result.totalSpend() > 0L);
			assertTrue("spend must not exceed the budget", result.totalSpend() <= 100_000L);

			// Item #6b: "vs owned-only" is a real upgrade here, so it must read
			// "ownedOnlyDps -> dps" with no triangle glyph, coloured green.
			assertTrue("deltaDps must be positive for a real upgrade", result.deltaDps() > 0);
			String deltaText = section.optimizerResultDeltaTextForTest();
			assertFalse("must not contain the old triangle glyphs", deltaText.contains("▲") || deltaText.contains("▼"));
			assertTrue("must show ownedOnlyDps -> dps as a plain arrow", deltaText.contains("->"));
			assertEquals("positive delta must be coloured green (PROGRESS_COMPLETE_COLOR)",
				net.runelite.client.ui.ColorScheme.PROGRESS_COMPLETE_COLOR, section.optimizerResultDeltaColorForTest());
			assertTrue("an upgrade's decimal digits must use the dull-green shade, got: " + deltaText,
				deltaText.contains("size='2' color='" + com.ospulse.ui.ScentFormat.GREEN_DIM + "'"));
		});
	}

	/** Companion to the test above: budget 0 must keep the owned weapon even with a resolver wired in (nothing is "free" to buy). */
	@Test
	public void withInjectedPriceResolver_zeroBudgetKeepsOwnedWeapon()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			prices.put(ABYSSAL_WHIP, 5_000_000L);

			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals("budget 0 must not buy anything even with a resolver wired in",
				0L, result.totalSpend());
			assertEquals(BRONZE_SWORD, weaponIdInResult(result));
		});
	}

	/** 29580 = Tormented synapse, the Scorching bow's craft-ingredient (see {@code UNTRADEABLE_CRAFT_INGREDIENT}). */
	private static final int TORMENTED_SYNAPSE = 29580;

	/**
	 * PR-review finding (bug D follow-up): {@code withResolvedPrices} — the
	 * REAL production scaffolding behind {@code runOptimizer}/{@code
	 * runOptimizerAndRankStyles}, reached here via the real {@code
	 * findBestSetupButton} click rather than a *SyncForTest seam — must send
	 * every {@code UNTRADEABLE_CRAFT_INGREDIENT} value (e.g. the Scorching
	 * bow's Tormented synapse) to the price resolver alongside the equipment
	 * index, exactly like the {@code *SyncForTest} methods already did.
	 * Without it, {@code resolveOptimizerPriceSource}'s craft-ingredient
	 * exception reads an unpriced ingredient as 0 and the Scorching bow falls
	 * through to unaffordable even when the synapse is dirt cheap. Captures
	 * the {@code ids} a fake resolver receives (called synchronously inline,
	 * same as {@link #fakeResolver}) — this only needs the candidate SET the
	 * resolver was asked to price, not the async {@code SwingWorker} result,
	 * so no result-awaiting seam is needed.
	 */
	@Test
	public void withResolvedPrices_includesUntradeableCraftIngredientIdsInCandidateSet()
	{
		onEdt(() ->
		{
			java.util.Set<Integer> capturedIds = new java.util.HashSet<>();
			GearSection.OptimizerPriceResolver capturingResolver = (ids, onResolved) ->
			{
				capturedIds.addAll(ids);
				onResolved.accept(new GearSection.PriceLookup(java.util.Map.of(), java.util.Set.of()));
			};

			GearSection section = new GearSection(NO_STORE, null, null, null, null, capturingResolver);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("100k");

			section.findBestSetupButtonForTest().doClick();

			assertTrue("the Scorching bow's craft-ingredient id must be in the resolver's candidate "
					+ "set so its GE price is available for the craft-ingredient budget exception",
				capturedIds.contains(TORMENTED_SYNAPSE));
		});
	}

	/** A fake resolver that also wires a {@code needsProtection} set — see {@link #fakeResolver} for the plain-prices-only version. */
	private static GearSection.OptimizerPriceResolver fakeResolverWithNeedsProtection(
		java.util.Map<Integer, Long> prices, java.util.Set<Integer> needsProtection)
	{
		return (ids, onResolved) -> onResolved.accept(
			new GearSection.PriceLookup(prices, java.util.Set.of(), java.util.Map.of(), needsProtection));
	}

	/**
	 * Items #5/#6: a recommended (suggested) item flagged in {@code
	 * PriceLookup.needsProtection()} — a rare untradeable only priced via the
	 * Trouver-parchment fallback (real-world source of this data), that must
	 * be carried with a parchment or lost outright on death — must render its
	 * swap-row icon with the {@code NEEDS_PROTECTION_TINT} background and the
	 * exact {@code NEEDS_PROTECTION_TOOLTIP} hover text. Reuses the dragon
	 * scimitar upgrade fixture from {@link #withInjectedPriceResolver_budgetBuysAffordableNonOwnedWeapon}
	 * purely as a real, actually-recommended item id to flag — the semantics
	 * of what's really untradeable/parchment-priced are RiskValuation's job
	 * (covered by RiskValuationTest), not this rendering test's.
	 */
	@Test
	public void suggestedItem_flaggedNeedsProtection_rendersHighlightAndExactTooltip()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);

			GearSection section = new GearSection(NO_STORE, null, null, null, null,
				fakeResolverWithNeedsProtection(prices, java.util.Set.of(DRAGON_SCIMITAR)));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("100k");
			section.runOptimizerSyncForTest();

			// Each changed slot renders as [row, spacer] — see renderOptimizerSwapList;
			// only the weapon slot changes here, so exactly one row (+ its spacer).
			assertEquals("expected exactly one swap row (the weapon upgrade) plus its spacer",
				2, section.optimizerSwapRowCountForTest());
			assertEquals(GearSection.NEEDS_PROTECTION_TINT, section.suggestedIconBackgroundForTest(0));
			assertEquals(GearSection.NEEDS_PROTECTION_TOOLTIP, section.suggestedIconTooltipForTest(0));
		});
	}

	/** Companion to the test above: a recommended item NOT in {@code needsProtection} must keep the plain (untinted) icon and its ordinary name/price tooltip. */
	@Test
	public void suggestedItem_notFlaggedNeedsProtection_keepsPlainIconAndTooltip()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);

			GearSection section = new GearSection(NO_STORE, null, null, null, null,
				fakeResolverWithNeedsProtection(prices, java.util.Set.of()));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("100k");
			section.runOptimizerSyncForTest();

			assertEquals(2, section.optimizerSwapRowCountForTest());
			assertFalse("must not carry the needsProtection tint when not flagged",
				GearSection.NEEDS_PROTECTION_TINT.equals(section.suggestedIconBackgroundForTest(0)));
			assertFalse("must not carry the exact needsProtection tooltip when not flagged",
				GearSection.NEEDS_PROTECTION_TOOLTIP.equals(section.suggestedIconTooltipForTest(0)));
		});
	}

	private static int weaponIdInResult(GearOptimizer.Result result)
	{
		for (GearOptimizer.SlotChoice choice : result.loadout())
		{
			if (choice.slotOrdinal() == WhatIfLoadout.WEAPON_SLOT)
			{
				return choice.itemId();
			}
		}
		return -1;
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

	// -------------------------------------------- item #1: budget K/M toggle + expensive-items fields

	/**
	 * The redesigned budget entry (numeric field + K/M segmented toggle,
	 * replacing the old single "10m"/"500k" free-text field) must resolve to
	 * the same values {@code parseBudget} always produced for those strings.
	 */
	@Test
	public void budgetKMToggle_resolvesSameAsOldSingleFieldSyntax()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);

			section.setBudgetTextForTest("0");
			assertEquals(0L, section.resolvedBudgetForTest());

			section.setBudgetTextForTest("500k");
			assertEquals(500_000L, section.resolvedBudgetForTest());

			section.setBudgetTextForTest("10m");
			assertEquals(10_000_000L, section.resolvedBudgetForTest());
		});
	}

	/** Flipping the K/M toggle directly (not via the old-syntax test seam) changes the resolved budget. */
	@Test
	public void budgetUnitToggle_switchesBetweenKAndM()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.setBudgetTextForTest("5"); // plain digits, no suffix -> toggle decides the unit

			section.setBudgetUnitMillionsForTest(false);
			assertEquals(5_000L, section.resolvedBudgetForTest());

			section.setBudgetUnitMillionsForTest(true);
			assertEquals(5_000_000L, section.resolvedBudgetForTest());
		});
	}

	/**
	 * The "expensive items" count and "expensive threshold" fields (item #1
	 * parts b/c) are captured from the UI even though the optimiser doesn't
	 * enforce them yet — this covers the GearSection-side parsing only;
	 * {@code GearOptimizerTest} covers the {@code GearOptimizer.Request}
	 * round-trip.
	 */
	@Test
	public void expensiveItemFields_parseFromTheUi()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);

			assertEquals("default count is 11", 11, section.resolvedExpensiveCountForTest());
			// Foot-gun fix: the threshold now defaults to a sensible 100k (not 0), so
			// simply lowering the count below the slot total actually caps expensive
			// gear instead of silently doing nothing. The cap is still OFF by default
			// because the count defaults to 11 (== SEARCHABLE_SLOTS.length); a user
			// can still set the threshold to 0 to disable it outright.
			assertEquals("default threshold is now 100k", 100_000L, section.resolvedExpensiveThresholdForTest());

			section.setExpensiveCountTextForTest("3");
			assertEquals(3, section.resolvedExpensiveCountForTest());

			section.setExpensiveThresholdTextForTest("20m");
			assertEquals(20_000_000L, section.resolvedExpensiveThresholdForTest());

			section.setExpensiveCountTextForTest("not a number");
			assertEquals("unparseable count falls back to 0, not a crash", 0, section.resolvedExpensiveCountForTest());
		});
	}

	/**
	 * Foot-gun fix, persistence half: unlike the budget amount/unit (which DO
	 * survive a client restart via {@code ConfigManager} — see
	 * {@code GearSection#loadOptimizerPrefs}), the expensive-item count and
	 * threshold must NEVER be restored from a previously-saved config value.
	 * Seeds a mocked {@link net.runelite.client.config.ConfigManager} with
	 * stale saved values under the (now legacy, write-only-historically) keys
	 * {@code optimizerExpensiveCount}/{@code optimizerExpensiveThresholdAmount}
	 * and asserts a freshly-constructed section ignores them entirely,
	 * settling on the code defaults (count 11, threshold 100K = 100,000) every
	 * time — exactly as if the plugin had just been loaded fresh.
	 */
	@Test
	public void expensiveItemFields_ignoreStalePersistedConfig_alwaysResetToCodeDefaults()
	{
		onEdt(() ->
		{
			net.runelite.client.config.ConfigManager configManager =
				org.mockito.Mockito.mock(net.runelite.client.config.ConfigManager.class);
			org.mockito.Mockito.when(configManager.getConfiguration(
					com.ospulse.OSPulseConfig.GROUP, "optimizerExpensiveCount"))
				.thenReturn("3");
			org.mockito.Mockito.when(configManager.getConfiguration(
					com.ospulse.OSPulseConfig.GROUP, "optimizerExpensiveThresholdAmount"))
				.thenReturn("300");

			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			assertEquals("a stale persisted count must be ignored — always resets to the code default",
				11, section.resolvedExpensiveCountForTest());
			assertEquals("a stale persisted threshold must be ignored — always resets to the code default (100K)",
				100_000L, section.resolvedExpensiveThresholdForTest());
		});
	}

	/**
	 * End-to-end: running the optimiser with the expensive-items fields set
	 * must not crash and must not change the search's actual behaviour (not
	 * enforced yet — see {@code GearOptimizer.Request} javadoc), confirming
	 * the plumbing is inert until a later pass consumes it.
	 */
	@Test
	public void expensiveItemFields_doNotAffectSearchResultYet()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.setExpensiveCountTextForTest("2");
			section.setExpensiveThresholdTextForTest("10m");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals(BRONZE_SWORD, weaponIdInResult(result));
		});
	}

	// -------------------------------------------- item #6a: exclude-from-suggestions

	/**
	 * Excluding the resolver-priced affordable upgrade (the scimitar) must
	 * remove it from candidacy entirely — the search falls back to the next
	 * best affordable option (here, nothing else is affordable/owned, so it
	 * keeps the live bronze sword), mirroring
	 * {@code GearOptimizerTest#excludedItem_neverAppearsEvenIfOwned}'s
	 * coverage of the underlying {@code GearOptimizer.Request.exclude} but
	 * exercised through the real GearSection UI wiring.
	 */
	@Test
	public void excludingASuggestedItem_removesItFromFutureSearches()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			prices.put(ABYSSAL_WHIP, 5_000_000L);

			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("100k");

			section.runOptimizerSyncForTest();
			assertEquals("sanity: the scimitar is suggested before any exclude",
				DRAGON_SCIMITAR, weaponIdInResult(section.lastOptimizerResultForTest()));

			section.excludeItemFromSuggestionsForTest(DRAGON_SCIMITAR);
			assertTrue("excluded set must contain the item id", section.excludedItemIdsForTest().contains(DRAGON_SCIMITAR));

			section.runOptimizerSyncForTest();
			GearOptimizer.Result afterExclude = section.lastOptimizerResultForTest();
			assertEquals("the excluded scimitar must never be suggested again",
				BRONZE_SWORD, weaponIdInResult(afterExclude));
			assertEquals("nothing else affordable/owned to buy, so spend must be 0", 0L, afterExclude.totalSpend());
		});
	}

	/** Excluding the same item id twice must not duplicate it or throw (Set semantics). */
	@Test
	public void excludingTheSameItemTwice_isIdempotent()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.excludeItemFromSuggestionsForTest(DRAGON_SCIMITAR);
			section.excludeItemFromSuggestionsForTest(DRAGON_SCIMITAR);
			assertEquals(1, section.excludedItemIdsForTest().size());
		});
	}

	// -------------------------------------------- item #6c: suggested swaps as icons

	/**
	 * The suggested-swaps list must render one row per actually-changed slot
	 * (was previously one {@code JLabel} of text per slot; now an icon row
	 * built by {@code buildSwapRow} plus a spacer — see
	 * {@code renderOptimizerSwapList}). This locks in "a row exists and the
	 * count matches the number of changed slots" without depending on Swing
	 * layout internals inside the row itself.
	 */
	@Test
	public void swapList_rendersOneRowPerChangedSlotAsIconRows()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			prices.put(ABYSSAL_WHIP, 5_000_000L);

			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("100k");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			long changedSlots = result.loadout().stream()
				.filter(choice -> choice.itemId() != BRONZE_SWORD || choice.slotOrdinal() != WhatIfLoadout.WEAPON_SLOT)
				.count();
			assertTrue("at least the weapon slot must have changed (bronze sword -> scimitar)", changedSlots > 0);
			// Each changed slot renders as [row, spacer] — see renderOptimizerSwapList.
			assertTrue("swap list must have rendered rows for the change(s)", section.optimizerSwapRowCountForTest() >= 2);
		});
	}

	/** With no slot changes (already-best loadout), the swap list falls back to the single "no changes" label, not a phantom row. */
	@Test
	public void swapList_noChanges_showsSingleFallbackRow()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("0"); // owned-only, nothing else owned -> whip must stay
			section.runOptimizerSyncForTest();

			assertEquals(ABYSSAL_WHIP, weaponIdInResult(section.lastOptimizerResultForTest()));
			assertEquals(1, section.optimizerSwapRowCountForTest());
		});
	}
}
