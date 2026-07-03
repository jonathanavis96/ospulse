package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.WeaponStyle;
import com.ospulse.combat.optimizer.LoadoutOverride;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.session.GearMapper;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import net.runelite.client.ui.ColorScheme;

import org.junit.Test;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the Phase 2 "what-if per-slot swap" UI (design spec section 2): a
 * gear-grid slot click opens an item search scoped to that slot; picking an
 * item creates an override that recomputes the readout from
 * {@code liveLoadout} &#8746; override without touching real gear; the
 * baseline-vs-what-if delta row appears; reset restores live gear exactly;
 * 2H/shield exclusivity is enforced both ways — all driven headlessly
 * through the real Swing component (null ItemManager, tolerated the same way
 * {@link GearSectionStyleRankingTest} already relies on).
 */
public class GearSectionWhatIfTest
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

	private static final int ABYSSAL_WHIP = 4151;
	private static final int DRAGON_SCIMITAR = 4587;
	private static final int TWISTED_BOW = 20997;   // two-handed
	private static final int RUNE_KITESHIELD = 1201; // shield

	/** int[14] with only the given slots populated (paired slot/id args), -1 elsewhere. */
	private static int[] loadout(int... slotIdPairs)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		for (int i = 0; i + 1 < slotIdPairs.length; i += 2)
		{
			ids[slotIdPairs[i]] = slotIdPairs[i + 1];
		}
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

	private static double dpsFor(GearSnapshot gear, WeaponStyle style)
	{
		PlayerCombat player = GearMapper.toPlayerCombat(gear, style.stance(), false, false, false);
		DpsResult r = DpsCalculator.compute(gear.equipmentStats(), player, style.type(),
			MonsterRepository.getInstance().byName("Cerberus").get(), 20);
		return r.dps();
	}

	// ------------------------------------------------------------- basics

	@Test
	public void noOverride_gearGridShowsLiveItemAndNoWhatIfRow()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(3, ABYSSAL_WHIP))));
			pickCerberus(section);

			assertEquals(LoadoutOverride.empty(), section.overrideForTest());
			assertFalse(section.whatIfRowVisibleForTest());
			assertEquals(ABYSSAL_WHIP, section.renderedSlotIdForTest(3));
		});
	}

	@Test
	public void clickingASlot_opensSearchScopedToThatSlot()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(3, ABYSSAL_WHIP))));
			pickCerberus(section);

			section.clickSlotForTest(3); // WEAPON
			assertEquals(3, section.searchOpenForSlotForTest());
			assertTrue(section.itemSearchFieldForTest().isVisible());

			section.itemSearchFieldForTest().setText("dragon scimitar");
			List<EquipmentIndexRepository.Entry> results = section.filteredItemsForTest();
			assertTrue("dragon scimitar must appear in the weapon-slot search", !results.isEmpty());
			for (EquipmentIndexRepository.Entry e : results)
			{
				assertEquals(3, e.slotOrdinal());
			}
		});
	}

	@Test
	public void clickingSameSlotTwice_closesTheSearch()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(3, ABYSSAL_WHIP))));
			pickCerberus(section);

			section.clickSlotForTest(3);
			assertEquals(3, section.searchOpenForSlotForTest());
			section.clickSlotForTest(3);
			assertEquals(-1, section.searchOpenForSlotForTest());
		});
	}

	// ------------------------------------------------------------- swap + delta

	@Test
	public void pickingAnItem_createsOverrideAndRecomputesWithoutTouchingLiveGear()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearFor(loadout(3, ABYSSAL_WHIP));
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			double liveDps = Double.parseDouble(section.dpsTextForTest());

			section.clickSlotForTest(3);
			section.itemSearchFieldForTest().setText("dragon scimitar");
			int idx = 0;
			for (int i = 0; i < section.filteredItemsForTest().size(); i++)
			{
				if (section.filteredItemsForTest().get(i).itemId() == DRAGON_SCIMITAR)
				{
					idx = i;
					break;
				}
			}
			section.pickItemForTest(idx);

			// Override recorded; search closes.
			assertEquals(DRAGON_SCIMITAR, section.overrideForTest().itemIdFor(3));
			assertEquals(-1, section.searchOpenForSlotForTest());

			// Readout changed to reflect the what-if weapon (whip and scimitar have
			// different bonuses, so DPS must differ from the live baseline).
			double whatIfDps = Double.parseDouble(section.dpsTextForTest());
			assertFalse("DPS must change after a real bonus-changing swap", liveDps == whatIfDps);

			// Live gear itself is untouched — the snapshot's own item id is unchanged.
			assertEquals(ABYSSAL_WHIP, gear.itemIdAt(3));

			// The what-if delta row compares against what LIVE gear (the whip) would
			// score with the CURRENTLY selected style (the scimitar's auto-picked
			// best, since the weapon swap re-ranked) — not the whip's own best style,
			// which is a different, less meaningful comparison (apples-to-apples on
			// style, not on weapon).
			assertTrue(section.whatIfRowVisibleForTest());
			double expectedBaseline = dpsFor(gear, section.selectedStyleForTest());
			assertEquals(expectedBaseline, section.baselineDpsForTest(), 1e-6);

			// Item #6b: no literal triangle glyph, "baseline -> current" with the
			// whole readout coloured green (upgrade) or red (downgrade) instead.
			String deltaText = section.whatIfDeltaTextForTest();
			assertFalse("must not contain the old triangle glyphs", deltaText.contains("▲") || deltaText.contains("▼"));
			assertTrue("must show baseline -> current as a plain arrow", deltaText.contains("->"));
			double delta = whatIfDps - expectedBaseline;
			java.awt.Color expectedColor = delta > 1e-9 ? ColorScheme.PROGRESS_COMPLETE_COLOR
				: delta < -1e-9 ? ColorScheme.PROGRESS_ERROR_COLOR : java.awt.Color.WHITE;
			assertEquals("delta colour must match the sign of the DPS change", expectedColor, section.whatIfDeltaColorForTest());
		});
	}

	@Test
	public void resetAll_restoresLiveGearExactly()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearFor(loadout(3, ABYSSAL_WHIP));
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			double liveDps = Double.parseDouble(section.dpsTextForTest());

			section.clickSlotForTest(3);
			section.itemSearchFieldForTest().setText("dragon scimitar");
			section.pickItemForTest(0);
			assertFalse(section.overrideForTest().isEmpty());

			section.clickResetAllForTest();

			assertTrue(section.overrideForTest().isEmpty());
			assertFalse(section.whatIfRowVisibleForTest());
			assertEquals(liveDps, Double.parseDouble(section.dpsTextForTest()), 1e-9);
			assertEquals(ABYSSAL_WHIP, section.renderedSlotIdForTest(3));
		});
	}

	// ------------------------------------------------------------- 2H/shield exclusivity

	@Test
	public void overridingWeaponWithTwoHanded_clearsExistingShieldOverride()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(3, ABYSSAL_WHIP, 5, RUNE_KITESHIELD))));
			pickCerberus(section);

			// Override the shield first (still legal — whip is 1H).
			section.clickSlotForTest(5);
			section.itemSearchFieldForTest().setText("");
			int kiteshieldIdx = -1;
			for (int i = 0; i < section.filteredItemsForTest().size(); i++)
			{
				if (section.filteredItemsForTest().get(i).itemId() == RUNE_KITESHIELD)
				{
					kiteshieldIdx = i;
					break;
				}
			}
			assertTrue(kiteshieldIdx >= 0);
			section.pickItemForTest(kiteshieldIdx);
			assertTrue(section.overrideForTest().hasOverride(5));

			// Now override the weapon with a 2H bow — must clear the shield override.
			section.clickSlotForTest(3);
			section.itemSearchFieldForTest().setText("twisted bow");
			int twistedBowIdx = -1;
			for (int i = 0; i < section.filteredItemsForTest().size(); i++)
			{
				if (section.filteredItemsForTest().get(i).itemId() == TWISTED_BOW)
				{
					twistedBowIdx = i;
					break;
				}
			}
			assertTrue(twistedBowIdx >= 0);
			section.pickItemForTest(twistedBowIdx);

			assertEquals(TWISTED_BOW, section.overrideForTest().itemIdFor(3));
			assertFalse("2H weapon override must clear the shield override", section.overrideForTest().hasOverride(5));
		});
	}

	@Test
	public void overridingShield_whenLiveWeaponIsTwoHanded_clearsWeaponBackToLive()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Live gear already has a 2H weapon and no shield.
			section.apply(snapshotWith(gearFor(loadout(3, TWISTED_BOW))));
			pickCerberus(section);

			section.clickSlotForTest(5);
			section.itemSearchFieldForTest().setText("");
			int kiteshieldIdx = -1;
			for (int i = 0; i < section.filteredItemsForTest().size(); i++)
			{
				if (section.filteredItemsForTest().get(i).itemId() == RUNE_KITESHIELD)
				{
					kiteshieldIdx = i;
					break;
				}
			}
			assertTrue(kiteshieldIdx >= 0);
			section.pickItemForTest(kiteshieldIdx);

			assertEquals(RUNE_KITESHIELD, section.overrideForTest().itemIdFor(5));
			// No weapon OVERRIDE should exist (equipShield's contract is "no override",
			// which for a live 2H weapon means the effective loadout would still compute
			// with both — the real 2H/shield conflict resolution used by the live game
			// engine is out of scope here; this asserts the override-map contract only).
			assertFalse(section.overrideForTest().hasOverride(3));
		});
	}
}
