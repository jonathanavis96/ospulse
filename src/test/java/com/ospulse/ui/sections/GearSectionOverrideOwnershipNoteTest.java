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
import static org.junit.Assert.assertTrue;

/**
 * Ownership-aware {@link GearSection#updateGearOverrideNote()} advisory
 * (coordinator follow-up on issue #11's owned-only work): {@code
 * GearOptimizer} force-includes a monster-mechanic override item PAST the
 * budget filter and force-equips it regardless of ownership ({@code
 * applyForcedIncludes}) — a deliberate, unchanged behaviour (these are
 * mechanical/safety requirements, not DPS suggestions). What this note adds
 * is disclosure: when the player doesn't actually own the required item (or
 * any of its accepted substitutes), the note says so, in every mode (not
 * just owned-only budget-0) — an unowned mandatory include bypasses the
 * budget filter in normal mode too.
 *
 * <p>Uses the real curated Rune dragon / Insulated boots entry (no
 * alternatives, so ownership is a plain worn-boots check) — same target
 * both ways, only the worn boots differ, isolating the ownership clause.
 */
public class GearSectionOverrideOwnershipNoteTest
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

	private static final int BOOTS_SLOT = 10;
	private static final int INSULATED_BOOTS = 7159;
	private static final int RANDOM_UNRELATED_BOOTS = 4119; // Climbing boots — no override relevance

	private static final int SHIELD_SLOT = 5;
	private static final int VS_SHIELD = 24266;

	/** int[EQUIPMENT_SLOT_COUNT] with only the BOOTS slot populated. */
	private static int[] bootsSlot(int bootsItemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[BOOTS_SLOT] = bootsItemId;
		return ids;
	}

	private static GearSnapshot gearWithBoots(int bootsItemId)
	{
		return gearWearing(bootsSlot(bootsItemId));
	}

	private static GearSnapshot gearWithShield(int shieldItemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[SHIELD_SLOT] = shieldItemId;
		return gearWearing(ids);
	}

	private static GearSnapshot gearWearing(int[] equippedItemIds)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		return GearSnapshot.builder()
			.equippedItemIds(equippedItemIds)
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
		section.monsterSearchField.setText(name);
		int index = indexOfContaining(section.monsterList.getModel(), name);
		assertTrue(name + " must appear in the filtered list", index >= 0);
		section.monsterList.setSelectedIndex(index);
	}

	@Test
	public void runeDragon_bootsNotOwned_noteDisclosesItIsNotOwned()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithBoots(RANDOM_UNRELATED_BOOTS)));
			pickMonster(section, "Rune dragon");

			List<String> notes = GearSectionTestOps.gearOverrideNoteTextsForTest(section);
			assertEquals("exactly one advisory line for the Rune dragon boots override", 1, notes.size());
			assertTrue("must name the required item",
				notes.get(0).contains("Insulated boots"));
			assertTrue("must disclose that the player doesn't own it: " + notes.get(0),
				notes.get(0).contains("you don't own this"));
		});
	}

	@Test
	public void runeDragon_bootsOwned_noteOmitsTheNotOwnedClause()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithBoots(INSULATED_BOOTS)));
			pickMonster(section, "Rune dragon");

			List<String> notes = GearSectionTestOps.gearOverrideNoteTextsForTest(section);
			assertEquals(1, notes.size());
			assertTrue("must still name the required item", notes.get(0).contains("Insulated boots"));
			assertTrue("must NOT disclose 'not owned' when the boots are actually worn: " + notes.get(0),
				!notes.get(0).contains("you don't own this"));
		});
	}

	/** Issue #35: the note was telling V's shield owners to equip a Mirror shield. */
	@Test
	public void basilisk_vsShieldWorn_noteNamesTheVsShield_notTheMirrorShield()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithShield(VS_SHIELD)));
			pickMonster(section, "Basilisk");

			List<String> notes = GearSectionTestOps.gearOverrideNoteTextsForTest(section);
			assertEquals(1, notes.size());
			assertTrue("must tell the player to keep the V's shield: " + notes.get(0),
				notes.get(0).contains("equip V's shield"));
			assertTrue("must not tell them to swap to a Mirror shield: " + notes.get(0),
				!notes.get(0).contains("equip Mirror shield"));
			assertTrue("the V's shield is owned: " + notes.get(0),
				!notes.get(0).contains("you don't own this"));
		});
	}
}
