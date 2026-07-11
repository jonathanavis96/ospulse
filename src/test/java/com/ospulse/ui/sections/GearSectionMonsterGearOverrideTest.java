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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Review finding 3: the worn-gear grid used to red-cross a monster's
 * curated face-protection requirement (Facemask/Earmuffs/Nose peg/Spiny
 * helmet) on an EXACT item-id match, so a Slayer helmet — which grants the
 * identical in-game protection — was wrongly flagged invalid. Covers
 * {@link GearSection#isSlotInvalidForTarget} end-to-end via the real HEAD
 * slot border ({@link GearSection#INVALID_BORDER}), driven headlessly
 * through the real Swing component (null ItemManager, tolerated by
 * skipping icons).
 */
public class GearSectionMonsterGearOverrideTest
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

	private static final int HEAD_SLOT = 0;
	private static final int FACEMASK = 4164;
	private static final int SLAYER_HELMET = 11864;
	private static final int SLAYER_HELMET_IMBUED = 11865;
	private static final int BLACK_SLAYER_HELMET = 19639; // colour recolour
	private static final int RANDOM_UNRELATED_HELM = 1155; // Iron full helm

	/** int[EQUIPMENT_SLOT_COUNT] with only the HEAD slot populated. */
	private static int[] headSlot(int headItemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[HEAD_SLOT] = headItemId;
		return ids;
	}

	private static GearSnapshot gearWithHead(int headItemId)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		return GearSnapshot.builder()
			.equippedItemIds(headSlot(headItemId))
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

	/**
	 * First filtered-list row whose display name CONTAINS {@code name}
	 * (case-insensitive) — several of the four face-protection monsters only
	 * exist as combat-instance-variant names in the bundled monster data
	 * (e.g. "Dust devil (Catacombs of Kourend)", no bare "Dust devil"), so an
	 * exact-match lookup would miss them; any instance shares the same
	 * curated {@code monsters}-list base-name override per
	 * {@code MonsterNameKey.baseName}.
	 */
	private static int indexOfContaining(ListModel<String> model, String name)
	{
		String needle = name.toLowerCase(java.util.Locale.ROOT);
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).toLowerCase(java.util.Locale.ROOT).contains(needle))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Picks the monster and forces a synchronous grid refresh — mirroring
	 * production ({@code onOptimizerResult} calls {@code updateGearGrid}) but
	 * without needing to also drive the real, ASYNCHRONOUS {@code
	 * SwingWorker}-based optimiser run the list-selection listener kicks off
	 * (which would otherwise leave {@link GearSection#slotBorderForTest} still
	 * reading its pre-pick state by the time the test's assertions run).
	 */
	private static void pickMonster(GearSection section, String name)
	{
		section.searchFieldForTest().setText(name);
		int index = indexOfContaining(section.monsterListForTest().getModel(), name);
		org.junit.Assert.assertTrue(name + " must appear in the filtered list", index >= 0);
		section.monsterListForTest().setSelectedIndex(index);
		section.updateGearGridForTest();
	}

	@Test
	public void dustDevil_flagsAnUnrelatedHelmAsInvalid_butAcceptsTheListedFacemask()
	{
		onEdt(() ->
		{
			GearSection unrelated = new GearSection(NO_STORE, null, null);
			unrelated.apply(snapshotWith(gearWithHead(RANDOM_UNRELATED_HELM)));
			pickMonster(unrelated, "Dust devil");
			assertEquals("an unrelated helm must red-cross the HEAD slot vs Dust devil",
				GearSection.INVALID_BORDER, unrelated.slotBorderForTest(HEAD_SLOT));

			GearSection listed = new GearSection(NO_STORE, null, null);
			listed.apply(snapshotWith(gearWithHead(FACEMASK)));
			pickMonster(listed, "Dust devil");
			assertNull("the exact listed Facemask must not be flagged invalid",
				listed.slotBorderForTest(HEAD_SLOT));
		});
	}

	/**
	 * The core finding 3 regression: a Slayer helmet (plain, imbued, or a
	 * colour recolour) must be accepted as a substitute for EACH of the four
	 * curated face-protection requirements, not just the one exact item
	 * originally listed.
	 */
	@Test
	public void slayerHelmetFamily_substitutesForEveryFaceProtectionRequirement()
	{
		String[] monsters = {"Dust devil", "Banshee", "Aberrant spectre", "Wall beast"};
		int[] slayerHelmetVariants = {SLAYER_HELMET, SLAYER_HELMET_IMBUED, BLACK_SLAYER_HELMET};

		onEdt(() ->
		{
			for (String monster : monsters)
			{
				for (int variant : slayerHelmetVariants)
				{
					GearSection section = new GearSection(NO_STORE, null, null);
					section.apply(snapshotWith(gearWithHead(variant)));
					pickMonster(section, monster);
					assertNull(monster + ": Slayer helmet variant " + variant + " must satisfy the face-protection "
							+ "requirement, not red-cross the HEAD slot",
						section.slotBorderForTest(HEAD_SLOT));
				}
			}
		});
	}
}
