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

	// P1 (issue #11 Stage 3 follow-up): self-cast magic weapon (no spellbook/rune
	// plumbing needed, mirrors GearOptimizerOwnedDeadmanCapeTest's fixture) plus the
	// Deadman-mode Imbued saradomin cape — mode-locked (GearSection.isModeLockedItem),
	// so restrictedItemIds() always excludes it, yet its ownership legitimately
	// credits the real, non-mode-locked "Imbued saradomin cape" via OwnedVariantResolver.
	private static final int TRIDENT_OF_THE_SEAS = 11905;
	private static final int IMBUED_SARADOMIN_CAPE_DEADMAN = 29617;
	/**
	 * The ordinary counterpart 29617's ownership credits — the magic-75-gated
	 * id, not the ungated same-named duplicate 24248 (see
	 * {@code OwnedVariantResolver.resolveByStatMatch}).
	 */
	private static final int IMBUED_SARADOMIN_CAPE_PLAIN = 21791;
	private static final int CAPE_SLOT = 1;

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
		section.monsterSearchField.setText("cerberus");
		int index = indexOf(section.monsterList.getModel(), "Cerberus");
		assertTrue("Cerberus must appear in the filtered list", index >= 0);
		section.monsterList.setSelectedIndex(index);
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
			assertEquals("a bow must detect Ranged", CombatStyle.RANGED, section.optimizerConstraint());

			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			assertEquals("a whip must detect Slash", CombatStyle.SLASH, section.optimizerConstraint());
		});
	}

	@Test
	public void userPick_overridesDetection_untilTheWeaponChanges()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			assertEquals(CombatStyle.SLASH, section.optimizerConstraint());

			section.clickOptimizerStyleForTest(CombatStyle.CRUSH);
			assertEquals("a user pick must override the detected style", CombatStyle.CRUSH, section.optimizerConstraint());
			assertTrue(section.styleUserPicked);

			// A snapshot with the SAME weapon must not clobber the pick...
			section.apply(snapshotWith(gearFor(loadout(ABYSSAL_WHIP)), null));
			assertEquals(CombatStyle.CRUSH, section.optimizerConstraint());

			// ...but a weapon CHANGE re-detects (same rule as the readout's style lock).
			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), null));
			assertFalse(section.styleUserPicked);
			assertEquals(CombatStyle.RANGED, section.optimizerConstraint());
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

			GearOptimizer.Result result = section.lastOptimizerResult;
			assertEquals("the equipped bow must remain the weapon", MAGIC_SHORTBOW, weaponIdInResult(result));
			assertEquals("the result must be Ranged-driven", CombatStyle.RANGED, result.style().type());
			assertEquals("the result panel must say what it optimised for", "Ranged",
				section.resultStyle.getText());

			section.applyResultToOverride();
			// The optimiser kept the bow (asserted above), so the corrected
			// preview (only genuinely-changed slots are overridden) must NOT
			// override the weapon slot at all — it stays the live bow, never the
			// owned melee whip. A regression that swapped in the whip would
			// create a weapon-slot override and fail this.
			assertFalse("preview must not swap the bow for the owned melee whip",
				section.override.hasOverride(WhatIfLoadout.WEAPON_SLOT));
			assertEquals("preview must lock the readout to the optimised (Ranged) style",
				CombatStyle.RANGED, section.selectedStyle.type());
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

			GearOptimizer.Result result = section.lastOptimizerResult;
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
				DRAGON_SCIMITAR, weaponIdInResult(bought.lastOptimizerResult));
			assertTrue("a not-owned suggestion must render its gp price label",
				bought.countComponentsNamed(bought.swapList, "notOwnedPrice") >= 1);

			// Owned-only: the same upgrade already sits in the bank — no price label.
			GearSection owned = new GearSection(NO_STORE, null, null);
			owned.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealthWith(DRAGON_SCIMITAR)));
			pickCerberus(owned);
			owned.setBudgetTextForTest("0");
			owned.runOptimizerSyncForTest();
			assertEquals(DRAGON_SCIMITAR, weaponIdInResult(owned.lastOptimizerResult));
			assertEquals("an owned suggestion must not render a price label",
				0, owned.countComponentsNamed(owned.swapList, "notOwnedPrice"));
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

			String liveTooltip = section.slotLabels[WhatIfLoadout.WEAPON_SLOT].getToolTipText();
			assertTrue("pre-preview the weapon cell must read as live: " + liveTooltip,
				liveTooltip.contains("Weapon slot (live)"));

			section.setBudgetTextForTest("100k");
			section.runOptimizerSyncForTest();
			section.applyResultToOverride();

			String previewTooltip = section.slotLabels[WhatIfLoadout.WEAPON_SLOT].getToolTipText();
			assertTrue("previewing a purchase must name the item: " + previewTooltip,
				previewTooltip.contains("Dragon scimitar"));
			assertTrue("previewing must name the REAL slot: " + previewTooltip,
				previewTooltip.contains("Weapon slot"));
			assertTrue("a not-owned previewed item must say so: " + previewTooltip,
				previewTooltip.contains("preview, not owned"));

			// Clearing the preview restores the live wording.
			section.resetAllOverrides();
			assertTrue(section.slotLabels[WhatIfLoadout.WEAPON_SLOT].getToolTipText().contains("Weapon slot (live)"));
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

			String ammoTooltip = section.slotLabels[WhatIfLoadout.AMMO_SLOT].getToolTipText();
			assertTrue("ammo slot tooltip must name the equipped item: " + ammoTooltip,
				ammoTooltip.contains("Rada's blessing 4"));
			assertFalse("must not be the generic slot-name placeholder",
				ammoTooltip.startsWith("Ammo slot (live)"));
		});
	}

	// ------------------------------------------------ #9: recommendation shows the OWNED variant's name

	/** First swap-row tooltip that starts with {@code prefix}, skipping the non-row spacer components, or {@code null}. */
	private static String findSwapTooltipStartingWith(GearSection section, String prefix)
	{
		for (int i = 0; i < section.swapList.getComponentCount(); i++)
		{
			String tooltip;
			try
			{
				tooltip = section.suggestedIconForTest(i).getToolTipText();
			}
			catch (IllegalArgumentException notARow)
			{
				continue; // a Box.createRigidArea spacer between rows, not a row
			}
			if (tooltip != null && tooltip.startsWith(prefix))
			{
				return tooltip;
			}
		}
		return null;
	}

	/**
	 * Item #9 / Codex review finding #1 (PR #5): when the optimiser's
	 * ownership map (see {@code OwnedVariantResolver}/{@code
	 * GearSection#addVariantPlainForm}) marks a plain base item owned
	 * because the player actually owns a fortified/imbued VARIANT of it, the
	 * resulting recommendation must be displayed under the variant's own
	 * name ("Masori mask (f)") — AND, once "Apply to readout" is clicked,
	 * the what-if preview must equip that SAME item, not the plain base the
	 * row's name doesn't match. Finding #1 flagged the row and the applied
	 * preview disagreeing; both now go through the same {@code
	 * GearSection#resolvedItemId} choke point, so this asserts they
	 * agree by construction.
	 */
	@Test
	public void ownedRecommendation_showsTheOwnedVariantName_andAppliesTheSameItem()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), wealthWith(MASORI_MASK_F)));
			pickCerberus(section);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			int headChoiceId = -1;
			for (GearOptimizer.SlotChoice choice : section.lastOptimizerResult.loadout())
			{
				if (choice.slotOrdinal() == 0) // HEAD
				{
					headChoiceId = choice.itemId();
				}
			}
			assertEquals("the unconstrained search picks the owned, stat-superior variant directly",
				MASORI_MASK_F, headChoiceId);

			String swapTooltip = findSwapTooltipStartingWith(section, "Masori");
			assertTrue("the recommendation must name the OWNED variant, not the plain base item: " + swapTooltip,
				swapTooltip != null && swapTooltip.contains("Masori mask (f)"));

			section.applyResultToOverride();
			assertEquals("the applied what-if preview must equip the SAME item the row named",
				MASORI_MASK_F, section.override.itemIdFor(0)); // HEAD
		});
	}

	/**
	 * Codex review finding #2 (PR #5) established that an excluded owned
	 * variant must never reappear in the suggestions. That still holds and is
	 * still asserted here.
	 *
	 * <p><b>What changed is the fallback</b> (2026-07-26): the original
	 * version asserted the plain base id came back <i>owned and free</i> —
	 * "that's the whole point of the mapping". It is not, and that assertion
	 * encoded a defect. The credit's entire justification is that the player
	 * effectively HAS the plain item because they hold the variant. Excluding
	 * the variant withdraws that backing, and a player who fortified their
	 * Masori mask has no plain Masori mask in the bank — so recommending one
	 * at zero spend proposes an item that does not exist anywhere, and the
	 * bank highlighter then points at an id that cannot be found. The
	 * exclusion would have manufactured a free item out of nothing.
	 *
	 * <p>The honest outcome at budget 0 is what this now asserts: no free
	 * plain fallback for that slot. With a real budget the plain form is
	 * simply a purchase, priced like any other — which is exactly what it
	 * would be.
	 *
	 * <p>Applied uniformly across {@code SUFFIXES} rather than only to the
	 * mode-locked "(deadman)" class that surfaced it: a rule that held for one
	 * variant class and not another is precisely the kind of split that
	 * produced several of these findings.
	 */
	@Test
	public void excludedOwnedVariant_doesNotReappearInSuggestions()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(MAGIC_SHORTBOW)), wealthWith(MASORI_MASK_F)));
			section.excludeFromSuggestions(MASORI_MASK_F);
			pickCerberus(section);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			assertFalse("excluding the only backing variant must withdraw the synthetic credit — the plain "
					+ "mask is in no bank and must not be marked owned at price 0",
				section.ownedPriceMap().containsKey(MASORI_MASK));
			assertTrue("the excluded variant itself is still genuinely owned; exclusion means \"never "
					+ "suggest\", not \"pretend it is gone\"",
				section.ownedPriceMap().containsKey(MASORI_MASK_F));

			for (GearOptimizer.SlotChoice choice : section.lastOptimizerResult.loadout())
			{
				if (choice.slotOrdinal() == 0) // HEAD
				{
					assertFalse("the excluded variant must never be recommended", choice.itemId() == MASORI_MASK_F);
					assertFalse("and its plain form must not be handed over free in its place",
						choice.itemId() == MASORI_MASK && choice.owned());
				}
			}

			String swapTooltip = findSwapTooltipStartingWith(section, "Masori");
			assertEquals("no Masori row at all at budget 0 — neither the excluded variant nor a plain mask "
					+ "the player does not have: " + swapTooltip,
				null, swapTooltip);

			section.applyResultToOverride();
			assertFalse("the preview must not equip the excluded variant",
				section.override.itemIdFor(0) == MASORI_MASK_F);
			assertFalse("nor the plain form it no longer credits",
				section.override.itemIdFor(0) == MASORI_MASK);
		});
	}

	// ------------------------------------------------ P1 (issue #11 Stage 3 follow-up): mode-locked reverse substitution

	/**
	 * Second P1 on the deadman-suffix fix, a consequence of it rather than a
	 * pre-existing bug: once owning a "(deadman)" item cross-maps to its
	 * plain counterpart ({@code OwnedVariantResolver.SUFFIXES}), the
	 * optimiser can legitimately SELECT that plain counterpart — but {@code
	 * GearSection#resolvedItemId} previously passed {@code
	 * preferOwnedVariant} only the user-managed {@code excludedItemIds}, not
	 * {@code restrictedItemIds()} (the mode-locked/Gauntlet-only set {@code
	 * buildRequest} always folds into the optimiser's OWN exclude
	 * set — see {@code GearSectionGearPoolTest
	 * #deadmanNamedItem_isNeverSuggestedByTheOptimizer}). So the reverse
	 * display lookup could remap the plain choice straight back to the
	 * owned-but-mode-locked deadman id, resurrecting under the swap row /
	 * applied preview / bank highlight exactly the item the optimiser
	 * correctly refused to ever suggest directly.
	 *
	 * <p><b>That reading was wrong, and this test now asserts the opposite
	 * (see {@code GearSection#resolvedItemId}).</b> Blocking the
	 * substitution left the player holding ONLY the deadman cape while every
	 * display surface named the plain id — an item that is not in their bank,
	 * because {@code addVariantPlainForm} invented it at price 0 purely to
	 * make the credit work. The swap row then said "equip Imbued saradomin
	 * cape" for a cape they do not have, and the bank highlight armed on an
	 * id that cannot be in the bank while missing the one that is.
	 *
	 * <p>The mode-lock's actual guarantee is about the SEARCH, and it is
	 * untouched: the optimiser still never picks, scores or recommends a
	 * mode-locked id ({@code GearSectionGearPoolTest
	 * #deadmanNamedItem_isNeverSuggestedByTheOptimizer}), which this test
	 * re-asserts below. Once the credit has already been granted, naming the
	 * physical item the player owns is the only display that is not a lie —
	 * and the two ids are stat- and requirement-identical by construction, so
	 * the recommendation itself is unchanged either way. See {@link
	 * #ownedPlainAndDeadmanCape_prefersTheGenuinelyHeldPlainId} for the
	 * kernel of the original P1 that IS still enforced.
	 */
	@Test
	public void ownedDeadmanCapeCredit_resolvesEveryDisplaySurfaceToTheHeldVariant()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(TRIDENT_OF_THE_SEAS)), wealthWith(IMBUED_SARADOMIN_CAPE_DEADMAN)));
			pickCerberus(section);
			section.clickOptimizerStyleForTest(CombatStyle.MAGIC);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			int capeChoiceId = -1;
			boolean capeOwned = false;
			for (GearOptimizer.SlotChoice choice : section.lastOptimizerResult.loadout())
			{
				if (choice.slotOrdinal() == CAPE_SLOT)
				{
					capeChoiceId = choice.itemId();
					capeOwned = choice.owned();
				}
			}
			// The optimiser itself must pick the credited PLAIN "Imbued saradomin cape"
			// id — restrictedItemIds() always excludes the deadman id itself, so the
			// optimiser can never choose it directly (this much already worked before
			// this fix, and is not what's under test here).
			assertTrue("the cape-slot recommendation must be owned via the deadman-suffix credit", capeOwned);
			assertFalse("the optimiser itself must never choose the mode-locked deadman id directly",
				capeChoiceId == IMBUED_SARADOMIN_CAPE_DEADMAN);

			String swapTooltip = findSwapTooltipStartingWith(section, "Imbued saradomin cape");
			assertTrue("a swap row must exist for the cape slot: " + swapTooltip, swapTooltip != null);
			assertTrue("the swap row must name the cape the player actually holds, not the credited plain id "
					+ "they have no copy of: " + swapTooltip,
				swapTooltip.contains("(deadman)"));

			section.applyResultToOverride();
			int appliedCapeId = section.override.itemIdFor(CAPE_SLOT);
			assertEquals("the applied preview must equip the held deadman id, matching the row "
					+ "(Codex finding #1's consistency guarantee — every surface resolves identically)",
				IMBUED_SARADOMIN_CAPE_DEADMAN, appliedCapeId);
			assertEquals("the bank highlight must point at the same held id, or it highlights nothing at all",
				IMBUED_SARADOMIN_CAPE_DEADMAN,
				(int) section.loadoutSlotMap(section.lastOptimizerResult).get(CAPE_SLOT));
		});
	}

	/**
	 * The WORN case, and the reason a reported self-swap does not actually
	 * occur — recorded because the reasoning is not obvious from either side.
	 *
	 * <p>The concern: with the mode-locked cape equipped rather than banked,
	 * the optimiser would be forced onto its credited plain counterpart
	 * (21791) since 29617 is excluded from every search, the display would
	 * resolve that back to 29617, and comparing the RAW choice against the
	 * live id would render a 29617 → 29617 self-swap, report a slot change
	 * and auto-apply a redundant override.
	 *
	 * <p>It does not happen, because the exclude set filters <b>candidates</b>
	 * and a worn item is not a candidate — it is the <b>seed</b>.
	 * {@code GearOptimizer.Request.Builder} adds every live id to {@code
	 * owned} unconditionally ("the player's own worn gear is always owned"),
	 * and the search starts from {@code request.liveItemIds.clone()}. A worn
	 * mode-locked item therefore stays in its slot and comes back as the raw
	 * choice, so the live-vs-choice comparison already matches. This test
	 * pins that: the raw cape choice IS the worn deadman id.
	 *
	 * <p>The margin is one tie-break wide, which is why the comparisons still
	 * resolve first (see {@code GearSection#hasAnySlotChange}). The greedy
	 * seed picks each slot's best owned CANDIDATE, and 21791 is in the owned
	 * set via the credit while 29617 is not a candidate — the two are
	 * stat-identical, so the tie keeps the worn item today. Anything that
	 * made the credited counterpart score strictly higher would hand the slot
	 * to 21791 and make the self-swap real.
	 */
	@Test
	public void wornDeadmanCape_isNotReportedAsASwapAgainstItself()
	{
		onEdt(() ->
		{
			int[] worn = loadout(TRIDENT_OF_THE_SEAS);
			worn[CAPE_SLOT] = IMBUED_SARADOMIN_CAPE_DEADMAN;
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(worn), wealthWith(IMBUED_SARADOMIN_CAPE_DEADMAN)));
			pickCerberus(section);
			section.clickOptimizerStyleForTest(CombatStyle.MAGIC);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			int capeChoiceId = -1;
			for (GearOptimizer.SlotChoice choice : section.lastOptimizerResult.loadout())
			{
				if (choice.slotOrdinal() == CAPE_SLOT)
				{
					capeChoiceId = choice.itemId();
				}
			}
			assertEquals("a worn mode-locked item is the search SEED, not a candidate, so it comes "
					+ "back as the raw choice unchanged",
				IMBUED_SARADOMIN_CAPE_DEADMAN, capeChoiceId);

			assertEquals("no swap row may be rendered for a cape the player is already wearing",
				null, findSwapTooltipStartingWith(section, "Imbued saradomin cape"));
			assertFalse("the cape slot must not count as a change, or the panel reports an upgrade "
					+ "that is the item already on the player's back",
				section.hasAnySlotChange(section.lastOptimizerResult));

			section.applyResultToOverride();
			assertEquals("no redundant override may be applied for an unchanged slot",
				-1, section.override.itemIdFor(CAPE_SLOT));
		});
	}

	/**
	 * The kernel of the original P1, still enforced: a substitution only ever
	 * happens for a choice the player does NOT physically hold. Here they own
	 * the plain "Imbued saradomin cape" outright AND the mode-locked deadman
	 * copy; the optimiser picks the plain id it can legitimately recommend,
	 * and the display must leave it alone rather than swapping in the
	 * look-alike the optimiser is forbidden to suggest. Without the
	 * {@code heldItemIds} guard in {@code GearSection#resolvedItemId},
	 * {@code preferOwnedVariant} would happily return the deadman id here,
	 * since it is an owned, non-excluded, stat-identical variant.
	 */
	@Test
	public void ownedPlainAndDeadmanCape_prefersTheGenuinelyHeldPlainId()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(TRIDENT_OF_THE_SEAS)),
				wealthWith(IMBUED_SARADOMIN_CAPE_DEADMAN, IMBUED_SARADOMIN_CAPE_PLAIN)));
			pickCerberus(section);
			section.clickOptimizerStyleForTest(CombatStyle.MAGIC);
			section.setBudgetTextForTest("0");
			section.runOptimizerSyncForTest();

			int capeChoiceId = -1;
			for (GearOptimizer.SlotChoice choice : section.lastOptimizerResult.loadout())
			{
				if (choice.slotOrdinal() == CAPE_SLOT)
				{
					capeChoiceId = choice.itemId();
				}
			}
			assertEquals("the optimiser must pick the plain cape the player genuinely holds",
				IMBUED_SARADOMIN_CAPE_PLAIN, capeChoiceId);

			String swapTooltip = findSwapTooltipStartingWith(section, "Imbued saradomin cape");
			assertTrue("a swap row must exist for the cape slot: " + swapTooltip, swapTooltip != null);
			assertFalse("a genuinely-held plain id must never be swapped out for the mode-locked "
					+ "look-alike: " + swapTooltip,
				swapTooltip.contains("(deadman)"));

			section.applyResultToOverride();
			assertEquals("the applied preview must equip the genuinely-held plain id",
				IMBUED_SARADOMIN_CAPE_PLAIN, section.override.itemIdFor(CAPE_SLOT));
		});
	}

	// ------------------------------------------------ #1: Find Best auto-picks the GLOBAL-best style

	/**
	 * Item #1: "Find best" selects whichever damage type is actually the global
	 * best for the target, not merely the equipped weapon's detected style. A
	 * bow is equipped (detects Ranged) but a stronger melee whip sits owned in
	 * the bank; on Cerberus the whip out-DPSes the weak bow, so Find Best must
	 * land on Slash. That auto-pick is NOT a manual lock ({@code
	 * styleUserPicked} stays false), so the next target re-evaluates.
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
				CombatStyle.SLASH, section.optimizerConstraint());
			assertEquals("the shown result must be the Slash whip setup",
				ABYSSAL_WHIP, weaponIdInResult(section.lastOptimizerResult));
			assertFalse("an auto-picked best is not a manual lock — it re-evaluates on the next target",
				section.styleUserPicked);
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
			section.applyRankedStyleResults(allUnusable, null);

			assertTrue("the shown result must report no usable weapon",
				section.lastOptimizerResult.style() == null);
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
			assertTrue(section.styleUserPicked);

			pickCerberus(section);

			assertTrue("a manual style pick must survive picking a target",
				section.styleUserPicked);
			assertEquals(CombatStyle.CRUSH, section.optimizerConstraint());
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
			owned.applyResultToOverride();
			assertEquals(DRAGON_SCIMITAR, owned.override.itemIdFor(WhatIfLoadout.WEAPON_SLOT));
			assertEquals("an owned recommendation must use the duller grey border",
				GearSection.OWNED_OVERRIDE_BORDER, owned.slotLabels[WhatIfLoadout.WEAPON_SLOT].getBorder());

			// Must-buy: the same upgrade is only affordable via the resolver, not owned.
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_SCIMITAR, 50_000L);
			GearSection buy = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			buy.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(buy);
			buy.setBudgetTextForTest("100k");
			buy.runOptimizerSyncForTest();
			buy.applyResultToOverride();
			assertEquals(DRAGON_SCIMITAR, buy.override.itemIdFor(WhatIfLoadout.WEAPON_SLOT));
			assertEquals("a must-buy recommendation must keep the bright orange border",
				GearSection.OVERRIDE_BORDER, buy.slotLabels[WhatIfLoadout.WEAPON_SLOT].getBorder());
		});
	}
}
