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

	private static void pickMonster(GearSection section, String name)
	{
		section.searchFieldForTest().setText(name);
		int index = indexOfContaining(section.monsterListForTest().getModel(), name);
		assertTrue(name + " must appear in the filtered list", index >= 0);
		section.monsterListForTest().setSelectedIndex(index);
	}

	@Test
	public void zulrah_showsItsCuratedReminder()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(emptyGearSnapshot());
			pickMonster(section, "Zulrah (Serpentine)");

			assertTrue("Zulrah must show its consumables reminder", section.consumablesReminderVisibleForTest());
			List<String> notes = section.consumablesReminderNoteTextsForTest();
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
				section.consumablesReminderVisibleForTest());
			assertTrue("no advisory text must be rendered",
				section.consumablesReminderNoteTextsForTest().isEmpty());
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
			assertTrue(section.consumablesReminderVisibleForTest());

			pickMonster(section, "Cow");
			assertFalse("must clear once a target with no reminder is picked",
				section.consumablesReminderVisibleForTest());
		});
	}
}
