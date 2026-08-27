package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

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
 * Issue #11 §7 — the "don't forget" consumables reminder row below the
 * potion/prayer/slayer toggles. Covers {@link GearSection}'s wiring of
 * {@code ConsumablesReminderPanel} end-to-end: picking a curated target shows
 * its note, and picking a monster with no curated entry renders NOTHING (no
 * empty row, no placeholder) — the explicit requirement from the design.
 */
public class GearSectionConsumablesReminderTest
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

	private static SessionSnapshot emptyGearSnapshot()
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		GearSnapshot gear = GearSnapshot.builder()
			.equippedItemIds(ids)
			.attack(99, 99)
			.strength(99, 99)
			.defence(99, 99)
			.ranged(99, 99)
			.magic(99, 99)
			.prayer(77, 77)
			.hitpoints(99, 99)
			.equipmentStats(stats)
			.build();
		return new SessionSnapshot(0L, 0L, 0L, 0L, 0L, 0L, false,
			null, null, 0L, null, null, null, null, 0L, gear);
	}

	private static int indexOfContaining(ListModel<String> model, String name)
	{
		String needle = name.toLowerCase(Locale.ROOT);
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).toLowerCase(Locale.ROOT).contains(needle))
			{
				return i;
			}
		}
		return -1;
	}

	private static int indexOfExact(ListModel<String> model, String name)
	{
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).equalsIgnoreCase(name))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Exact-match variant of {@link #pickMonster}, needed for "Nex" — the
	 * substring "nex" also matches "Blood Reaver (Nex's chamber)", which can
	 * sort ahead of "Nex" alphabetically and get picked instead by a
	 * contains-based lookup.
	 */
	private static void pickMonsterExact(GearSection section, String name)
	{
		section.monsterSearchField.setText(name);
		int index = indexOfExact(section.monsterList.getModel(), name);
		assertTrue(name + " must appear exactly in the filtered list", index >= 0);
		section.monsterList.setSelectedIndex(index);
	}

	private static void pickMonster(GearSection section, String name)
	{
		section.monsterSearchField.setText(name);
		int index = indexOfContaining(section.monsterList.getModel(), name);
		assertTrue(name + " must appear in the filtered list", index >= 0);
		section.monsterList.setSelectedIndex(index);
	}

	@Test
	public void zulrah_showsItsCuratedReminder()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(emptyGearSnapshot());
			pickMonster(section, "Zulrah (Serpentine)");

			assertTrue("Zulrah must show its consumables reminder", section.consumablesReminderPanel.isVisible());
			List<String> notes = section.consumablesReminderPanel.noteTextsForTest();
			assertEquals(1, notes.size());
			assertTrue("must mention antivenom: " + notes.get(0),
				notes.get(0).toLowerCase(Locale.ROOT).contains("antivenom"));
		});
	}

	/**
	 * The explicit no-placeholder requirement: a monster with no curated
	 * reminder must render NOTHING, not an empty visible row.
	 */
	@Test
	public void cow_hasNoCuratedReminder_rendersNothing()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(emptyGearSnapshot());
			pickMonster(section, "Cow");

			assertFalse("a monster with no curated reminder must not show the panel",
				section.consumablesReminderPanel.isVisible());
			assertTrue("no advisory text must be rendered",
				section.consumablesReminderPanel.noteTextsForTest().isEmpty());
		});
	}

	/** Switching from a curated target back to an uncurated one must clear the reminder, not leave it stuck. */
	@Test
	public void switchingFromZulrahToCow_clearsTheReminder()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(emptyGearSnapshot());

			pickMonster(section, "Zulrah");
			assertTrue(section.consumablesReminderPanel.isVisible());

			pickMonster(section, "Cow");
			assertFalse("must clear once a target with no reminder is picked",
				section.consumablesReminderPanel.isVisible());
		});
	}

	/**
	 * Regression guard for the "6 of 9 rows show nothing in the bank" gap:
	 * before {@code consumableItemIds} existed, these six monsters' reminders
	 * were prose-only and {@code bankConsumableIds()} returned nothing for
	 * them because it only ever read {@code equipmentItemIds}. Each must now
	 * yield at least one id via the combined equipment+consumable path.
	 */
	@Test
	public void previouslyProseOnlyMonsters_nowYieldBankConsumableItemIds()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(emptyGearSnapshot());

			String[] monsters = {
				"Zulrah (Serpentine)", "Alchemical Hydra (Fire)", "Abyssal Sire (Phase 1)",
				"K'ril Tsutsaroth", "Cerberus"
			};
			for (String monster : monsters)
			{
				pickMonster(section, monster);
				assertFalse(monster + " must yield at least one bank consumable item id",
					section.bankConsumableIds().isEmpty());
			}
			// "Nex" needs an exact match — the substring "nex" also matches
			// "Blood Reaver (Nex's chamber)", which can sort ahead of "Nex"
			// alphabetically and get picked instead by a contains-based lookup.
			pickMonsterExact(section, "Nex");
			assertFalse("Nex must yield at least one bank consumable item id",
				section.bankConsumableIds().isEmpty());
		});
	}

	/** Zulrah's combined bank ids must include the serpentine helm alongside the antivenom+ potion ids. */
	@Test
	public void zulrah_bankConsumableItemIds_includeSerpentineHelmAndAntivenomIds()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(emptyGearSnapshot());
			pickMonster(section, "Zulrah");

			List<Integer> ids = section.bankConsumableIds();
			assertTrue("must include the serpentine helm (12931)", ids.contains(12931));
			assertTrue("must include an antivenom+/extended antivenom+ dose",
				ids.contains(12913) || ids.contains(29824));
		});
	}

	/**
	 * Vorkath's combined bank tag path ({@code bankConsumableItemIdsForTest})
	 * must return the curated equipment ids first, then the curated
	 * consumable ids in their JSON insertion order — {@code
	 * bankConsumableIds()} builds a {@code LinkedHashSet} seeded with
	 * {@code equipmentItemIds()} before adding {@code consumableItemIds()},
	 * so this is only correct end-to-end once the repository itself stops
	 * scrambling the consumable order via {@code HashSet}.
	 */
	@Test
	public void vorkath_bankConsumableItemIds_equipmentFirstThenCuratedConsumableOrder()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(emptyGearSnapshot());
			pickMonster(section, "Vorkath (Post-quest)");

			List<Integer> ids = section.bankConsumableIds();
			List<Integer> expectedEquipment = Arrays.asList(1540, 11710, 11283, 11284, 22002, 22003);
			List<Integer> expectedConsumables = Arrays.asList(
				21978, 21981, 21984, 21987, 22209, 22212, 22215, 22218,
				12913, 12915, 12917, 12919, 29824, 29827, 29830, 29833);

			assertEquals("equipment ids must come first, in their curated order",
				expectedEquipment, ids.subList(0, expectedEquipment.size()));
			assertEquals("consumable ids must follow, in their curated JSON order",
				expectedConsumables, ids.subList(expectedEquipment.size(), ids.size()));
		});
	}
}
