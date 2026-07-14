package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
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
import javax.swing.border.Border;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Items #6e/#6g: the Best-setup optimiser's 5-way damage-type selector.
 * <ul>
 *   <li>it DEFAULTS to the equipped weapon's current combat style (a bow
 *       detects Ranged, never an implicit melee search — the #6g bug);</li>
 *   <li>a user pick overrides detection until the weapon changes;</li>
 *   <li>the search is genuinely constrained (result weapon/style match);</li>
 *   <li>"Preview these swaps" locks the readout to the optimised style;</li>
 *   <li>not-owned suggestions render a gp price label (owned ones don't);</li>
 *   <li>the worn-grid tooltip says preview/not-owned over preview content
 *       (#6f second site) and live otherwise.</li>
 * </ul>
 */
public class GearSectionOptimizerStyleTest
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
	private static final int ABYSSAL_WHIP = 4151;
	private static final int ABYSSAL_BLUDGEON = 13263;
	private static final int MAGIC_SHORTBOW = 861;
	private static final int RADAS_BLESSING_4 = 22947; // ammo slot, no ranged strength but +2 prayer
	private static final int MASORI_MASK = 27226;       // plain base form the optimiser's ownership map aliases to
	private static final int MASORI_MASK_F = 27235;     // the actual owned fortified variant

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

	private static WealthSnapshot wealthWith(int... itemIds)
	{
		List<ItemStack> holdings = new ArrayList<>();
		for (int id : itemIds)
		{
			holdings.add(new ItemStack(id, "item " + id, 1, 100_000L));
		}
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

	// ------------------------------------------------ default-style detection

	@Test
	public void selectorDefaultsToEquippedWeaponsCurrentStyle()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);

			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), null));
			assertEquals("a bow must detect Ranged", CombatStyle.RANGED, section.optimizerStyleForTest());

			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			assertEquals("a whip must detect Slash", CombatStyle.SLASH, section.optimizerStyleForTest());
		});
	}

	@Test
	public void userPick_overridesDetection_untilTheWeaponChanges()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			assertEquals(CombatStyle.SLASH, section.optimizerStyleForTest());

			section.clickOptimizerStyleForTest(CombatStyle.CRUSH);
			assertEquals("a user pick must override the detected style", CombatStyle.CRUSH, section.optimizerStyleForTest());
			assertTrue(section.optimizerStyleUserPickedForTest());

			// A snapshot with the SAME weapon must not clobber the pick...
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			assertEquals(CombatStyle.CRUSH, section.optimizerStyleForTest());

			// ...but a weapon CHANGE re-detects (same rule as the readout's style lock).
			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), null));
			assertFalse(section.optimizerStyleUserPickedForTest());
			assertEquals(CombatStyle.RANGED, section.optimizerStyleForTest());
		});
	}

	// ------------------------------------------------ #6g: ranged never falls back to melee

	/**
	 * THE #6g regression: a bow is equipped and a strictly-better melee weapon
	 * (whip) sits owned in the bank. The old unconstrained search picked the
	 * whip — "Preview best setup" then swapped the player's ranged gear back
	 * to melee. Now the search is anchored to the DETECTED Ranged style: the
	 * bow stays, the whip is never suggested, and the preview locks the
	 * readout to a Ranged style.
	 */
	@Test
	public void rangedWeaponEquipped_optimiserAndPreviewStayRanged()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), wealthWith(ABYSSAL_WHIP)));
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals("the equipped bow must remain the weapon", MAGIC_SHORTBOW, weaponIdInResult(result));
			assertEquals("the result must be Ranged-driven", CombatStyle.RANGED, result.style().type());
			assertEquals("the result panel must say what it optimised for", "Ranged",
				section.optimizerResultStyleTextForTest());

			section.clickApplyOptimizerResultForTest();
			// The optimiser kept the bow (asserted above), so the corrected
			// preview (only genuinely-changed slots are overridden) must NOT
			// override the weapon slot at all — it stays the live bow, never the
			// owned melee whip. A regression that swapped in the whip would
			// create a weapon-slot override and fail this.
			assertFalse("preview must not swap the bow for the owned melee whip",
				section.overrideForTest().hasOverride(WhatIfLoadout.WEAPON_SLOT));
			assertEquals("preview must lock the readout to the optimised (Ranged) style",
				CombatStyle.RANGED, section.selectedStyleForTest().type());
		});
	}

	@Test
	public void userPickedCrush_constrainsTheSearchToTheOwnedCrushWeapon()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), wealthWith(ABYSSAL_BLUDGEON)));
			section.clickOptimizerStyleForTest(CombatStyle.CRUSH);
			pickCerberus(section);

			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals("Crush pick must anchor to the owned bludgeon, not the higher-DPS whip",
				ABYSSAL_BLUDGEON, weaponIdInResult(result));
			assertEquals(CombatStyle.CRUSH, result.style().type());
		});
	}

	// ------------------------------------------------ owned vs not-owned rendering

	/** A synchronous fake resolver — calls {@code onResolved} inline with a fixed price map (everything tradeable), no threading involved. */
	private static GearSection.OptimizerPriceResolver fakeResolver(java.util.Map<Integer, Long> prices)
	{
		return (ids, onResolved) -> onResolved.accept(new GearSection.PriceLookup(prices, java.util.Set.of()));
	}

	@Test
	public void notOwnedSuggestion_rendersAGoldPriceLabel_ownedOnesDoNot()
	{
		onEdt(() ->
		{
			// A purchase: bronze sword worn, scimitar affordable via the resolver.
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			GearSection bought = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			bought.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(bought);
			bought.setBudgetTextForTest("100k");
			bought.runOptimizerSyncForTest();
			assertEquals("the affordable scimitar must be bought",
				DRAGON_SCIMITAR, weaponIdInResult(bought.lastOptimizerResultForTest()));
			assertTrue("a not-owned suggestion must render its gp price label",
				bought.notOwnedPriceLabelCountForTest() >= 1);

			// Owned-only: the same upgrade already sits in the bank — no price label.
			GearSection owned = new GearSection(NO_STORE, null, null);
			owned.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealthWith(DRAGON_SCIMITAR)));
			pickCerberus(owned);
			owned.setBudgetTextForTest("0");
			owned.runOptimizerSyncForTest();
			assertEquals(DRAGON_SCIMITAR, weaponIdInResult(owned.lastOptimizerResultForTest()));
			assertEquals("an owned suggestion must not render a price label",
				0, owned.notOwnedPriceLabelCountForTest());
		});
	}

	// ------------------------------------------------ #6f: preview tooltip names the real slot/state

	@Test
	public void gridTooltip_saysLiveNormally_andPreviewNotOwnedWhilePreviewingAPurchase()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);

			String liveTooltip = section.slotTooltipForTest(WhatIfLoadout.WEAPON_SLOT);
			assertTrue("pre-preview the weapon cell must read as live: " + liveTooltip,
				liveTooltip.contains("Weapon slot (live)"));

			section.setBudgetTextForTest("100k");
			section.runOptimizerSyncForTest();
			section.clickApplyOptimizerResultForTest();

			String previewTooltip = section.slotTooltipForTest(WhatIfLoadout.WEAPON_SLOT);
			assertTrue("previewing a purchase must name the item: " + previewTooltip,
				previewTooltip.contains("Dragon scimitar"));
			assertTrue("previewing must name the REAL slot: " + previewTooltip,
				previewTooltip.contains("Weapon slot"));
			assertTrue("a not-owned previewed item must say so: " + previewTooltip,
				previewTooltip.contains("preview, not owned"));

			// Clearing the preview restores the live wording.
			section.clickResetAllForTest();
			assertTrue(section.slotTooltipForTest(WhatIfLoadout.WEAPON_SLOT).contains("Weapon slot (live)"));
		});
	}

	// ------------------------------------------------ #8: ammo slot names the item, not a placeholder

	/**
	 * Item #8: the worn-gear grid's AMMO cell must name the actually-equipped
	 * ammo item in its live tooltip, unlike the other slots' generic "<Slot>
	 * slot (live)" wording (see {@link #gridTooltip_saysLiveNormally_andPreviewNotOwnedWhilePreviewingAPurchase}
	 * above) — ammo icons are often too similar to tell apart at a glance
	 * (many arrows/bolts/blessing charges share a near-identical sprite).
	 */
	@Test
	public void ammoSlotLiveTooltip_namesTheEquippedItem_notAGenericPlaceholder()
	{
		onEdt(() ->
		{
			int[] ids = loadout(MAGIC_SHORTBOW);
			ids[WhatIfLoadout.AMMO_SLOT] = RADAS_BLESSING_4;

			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(ids), null));

			String ammoTooltip = section.slotTooltipForTest(WhatIfLoadout.AMMO_SLOT);
			assertTrue("ammo slot tooltip must name the equipped item: " + ammoTooltip,
				ammoTooltip.contains("Rada's blessing 4"));
			assertFalse("must not be the generic slot-name placeholder",
				ammoTooltip.startsWith("Ammo slot (live)"));
		});
	}

	// ------------------------------------------------ #9: recommendation shows the OWNED variant's name

	/**
	 * Item #9: when the optimiser's ownership map (see {@code
	 * OwnedVariantResolver}/{@code GearSection#addVariantPlainForm}) marks a
	 * plain base item owned because the player actually owns a fortified/
	 * imbued VARIANT of it, the resulting recommendation must be displayed
	 * under the variant's own name ("Masori mask (f)"), not the plain base
	 * name the optimiser matched candidates against — the player doesn't own
	 * a plain Masori mask, they own the fortified one.
	 *
	 * <p>The owned variant (Masori mask (f)) is excluded from suggestions so
	 * the optimiser is forced to fall back to its plain-base-id ownership
	 * mapping for the head slot instead of just picking the (objectively
	 * better-stat) variant directly on its own merits — deterministically
	 * reproducing the "resolved to the plain form" case this fix targets.
	 */
	@Test
	public void ownedRecommendation_showsTheOwnedVariantName_notThePlainBaseName()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), wealthWith(MASORI_MASK_F)));
			section.excludeItemFromSuggestionsForTest(MASORI_MASK_F);
			pickCerberus(section);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			int headChoiceId = -1;
			boolean headOwned = false;
			for (GearOptimizer.SlotChoice choice : section.lastOptimizerResultForTest().loadout())
			{
				if (choice.slotOrdinal() == 0) // HEAD
				{
					headChoiceId = choice.itemId();
					headOwned = choice.owned();
				}
			}
			assertEquals("the optimiser must match against the plain base id via the ownership map",
				MASORI_MASK, headChoiceId);
			assertTrue("the plain base id must be free/owned (that's the whole point of the mapping)", headOwned);

			String swapTooltip = null;
			for (int i = 0; i < section.optimizerSwapRowCountForTest() && swapTooltip == null; i++)
			{
				String tooltip;
				try
				{
					tooltip = section.suggestedIconTooltipForTest(i);
				}
				catch (IllegalArgumentException notARow)
				{
					continue; // a Box.createRigidArea spacer between rows, not a row
				}
				if (tooltip != null && tooltip.startsWith("Masori"))
				{
					swapTooltip = tooltip;
				}
			}
			assertTrue("the recommendation must name the OWNED variant, not the plain base item: " + swapTooltip,
				swapTooltip != null && swapTooltip.contains("Masori mask (f)"));
		});
	}

	// ------------------------------------------------ #1: Find Best auto-picks the GLOBAL-best style

	/**
	 * Item #1: "Find best" selects whichever damage type is actually the global
	 * best for the target, not merely the equipped weapon's detected style. A
	 * bow is equipped (detects Ranged) but a stronger melee whip sits owned in
	 * the bank; on Cerberus the whip out-DPSes the weak bow, so Find Best must
	 * land on Slash. That auto-pick is NOT a manual lock ({@code
	 * optimizerStyleUserPicked} stays false), so the next target re-evaluates.
	 */
	@Test
	public void findBest_autoPicksTheGlobalBestStyle_notJustTheEquippedWeaponsStyle()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), wealthWith(ABYSSAL_WHIP)));
			pickCerberus(section);
			section.setBudgetTextForTest("0");

			section.runOptimizerAndRankStylesSyncForTest();

			assertEquals("Find Best must pick the global-best style (owned whip Slash), not the detected Ranged",
				CombatStyle.SLASH, section.optimizerStyleForTest());
			assertEquals("the shown result must be the Slash whip setup",
				ABYSSAL_WHIP, weaponIdInResult(section.lastOptimizerResultForTest()));
			assertFalse("an auto-picked best is not a manual lock — it re-evaluates on the next target",
				section.optimizerStyleUserPickedForTest());
		});
	}

	// ------------------------------------------------ CRITICAL regression: all-unusable styles must not NPE

	/**
	 * Every style's {@link GearOptimizer.Result} unusable ({@code style() ==
	 * null}, i.e. {@code bestDps()} is {@code Double.NEGATIVE_INFINITY} for
	 * all five) used to NPE: the best-style search seeded {@code bestScore}
	 * at {@code NEGATIVE_INFINITY} and compared with a STRICT {@code score >
	 * bestScore}, so {@code best} never got assigned; with no equipped style
	 * detected ({@code selected == null}) either, {@code display} stayed
	 * {@code null} and {@code onOptimizerResult(results.get(null))}
	 * dereferenced a null {@code Result}. Must now degrade gracefully to a
	 * "no usable weapon" result instead of crashing.
	 */
	@Test
	public void allStylesUnusable_doesNotThrow_andShowsNoUsableWeaponState()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(-1)), null));
			pickCerberus(section);

			// Every style's result is "unusable" (style() == null): a request
			// built with a null target short-circuits GearOptimizer's evaluate()
			// immediately, guaranteeing no weapon/style is ever scored, without
			// needing a real monster combat-requirement gate to reproduce it.
			java.util.Map<CombatStyle, GearOptimizer.Result> allUnusable = new java.util.LinkedHashMap<>();
			for (CombatStyle style : CombatStyle.values())
			{
				com.ospulse.combat.PlayerCombat.Builder player = com.ospulse.combat.PlayerCombat.builder()
					.attack(1, 1).strength(1, 1).defence(1, 1).ranged(1, 1).magic(1, 1)
					.prayer(1, 1).hitpoints(1, 1)
					.assumeBestPotion(false).assumeBestPrayer(false).onSlayerTask(false);
				GearOptimizer.Request request = GearOptimizer.Request.builder(loadout(-1), null, player)
					.style(style)
					.build();
				GearOptimizer.Result result = GearOptimizer.optimize(request);
				assertEquals("fixture sanity: a null-target request must never resolve a usable style",
					null, result.style());
				allUnusable.put(style, result);
			}

			// selected == null mirrors "no equipped style detected" — the exact
			// condition needed to leave `display` unset before the fix.
			section.applyRankedStyleResultsForTest(allUnusable, null);

			assertTrue("the shown result must report no usable weapon",
				section.lastOptimizerResultForTest().style() == null);
		});
	}

	/**
	 * Item #1 companion: a deliberate MANUAL style pick must SURVIVE a target
	 * change (only the auto-pick is re-evaluated). Guards that the two-flag
	 * model didn't collapse manual picks into the auto-pick reset.
	 */
	@Test
	public void manualStylePick_survivesATargetChange()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			section.clickOptimizerStyleForTest(CombatStyle.CRUSH);
			assertTrue(section.optimizerStyleUserPickedForTest());

			pickCerberus(section);

			assertTrue("a manual style pick must survive picking a target",
				section.optimizerStyleUserPickedForTest());
			assertEquals(CombatStyle.CRUSH, section.optimizerStyleForTest());
		});
	}

	// ------------------------------------------------ #3: owned vs must-buy preview border

	/**
	 * Item #3: an optimiser recommendation the player already OWNS (sitting in
	 * the bank) previews with the duller {@link GearSection#OWNED_OVERRIDE_BORDER},
	 * while a genuinely must-buy one keeps the bright {@link GearSection#OVERRIDE_BORDER}.
	 */
	@Test
	public void ownedRecommendation_previewsWithDullerBorderThanAMustBuyOne()
	{
		onEdt(() ->
		{
			// Owned: bronze sword worn, dragon scimitar already sitting in the bank.
			GearSection owned = new GearSection(NO_STORE, null, null);
			owned.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealthWith(DRAGON_SCIMITAR)));
			pickCerberus(owned);
			owned.setBudgetTextForTest("0");
			owned.runOptimizerSyncForTest();
			owned.clickApplyOptimizerResultForTest();
			assertEquals(DRAGON_SCIMITAR, owned.overrideForTest().itemIdFor(WhatIfLoadout.WEAPON_SLOT));
			assertEquals("an owned recommendation must use the duller grey border",
				GearSection.OWNED_OVERRIDE_BORDER, owned.slotBorderForTest(WhatIfLoadout.WEAPON_SLOT));

			// Must-buy: the same upgrade is only affordable via the resolver, not owned.
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			GearSection buy = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			buy.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(buy);
			buy.setBudgetTextForTest("100k");
			buy.runOptimizerSyncForTest();
			buy.clickApplyOptimizerResultForTest();
			assertEquals(DRAGON_SCIMITAR, buy.overrideForTest().itemIdFor(WhatIfLoadout.WEAPON_SLOT));
			assertEquals("a must-buy recommendation must keep the bright orange border",
				GearSection.OVERRIDE_BORDER, buy.slotBorderForTest(WhatIfLoadout.WEAPON_SLOT));
		});
	}
}
