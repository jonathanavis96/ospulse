package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.WeaponStyle;
import com.ospulse.session.GearMapper;
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
 * Covers the ranked attack-style picker: the equipped weapon's real styles are
 * listed, computed and sorted by DPS (best first, auto-selected), and clicking
 * a row re-points the readout — all driven headlessly through the real Swing
 * component (null ItemManager, which it tolerates by skipping icons).
 */
public class GearSectionStyleRankingTest
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

	/** int[14] with only the WEAPON slot (ordinal 3) populated. */
	private static int[] weaponSlot(int weaponId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[3] = weaponId;
		return ids;
	}

	private static GearSnapshot gearWithWeapon(int weaponId)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(82, 82, 82, 0, 0,
				0, 0, 0, 0, 0,
				82, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		return GearSnapshot.builder()
			.equippedItemIds(weaponSlot(weaponId))
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

	@Test
	public void equippedWeaponStylesAreListedRankedAndBestSelected()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324); // Ghrazi rapier -> stab sword (4 styles)
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			List<WeaponStyle> ranked = section.rankedStylesForTest();
			assertEquals("stab sword exposes four distinct styles", 4, ranked.size());

			// Rows must be sorted by DPS descending, matching an independent compute.
			double prev = Double.MAX_VALUE;
			for (WeaponStyle style : ranked)
			{
				double dps = dpsFor(gear, style);
				assertTrue("styles must be ranked DPS-descending (" + dps + " after " + prev + ")",
					dps <= prev + 1e-9);
				prev = dps;
			}

			// The best (row 0) is auto-selected and drives the readout.
			assertEquals(ranked.get(0), section.selectedStyleForTest());
			assertEquals(String.format(Locale.ROOT, "%.2f", dpsFor(gear, ranked.get(0))),
				section.dpsTextForTest());
		});
	}

	@Test
	public void clickingAStyleRowRepointsTheReadout()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			int last = section.styleRowCountForTest() - 1;
			WeaponStyle worst = section.rankedStylesForTest().get(last);
			section.clickStyleRowForTest(last);

			assertEquals(worst, section.selectedStyleForTest());
			assertEquals(String.format(Locale.ROOT, "%.2f", dpsFor(gear, worst)),
				section.dpsTextForTest());
		});
	}

	@Test
	public void unarmedFallsBackToThreeCrushStyles()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(-1))); // no weapon
			pickCerberus(section);

			List<WeaponStyle> ranked = section.rankedStylesForTest();
			assertEquals(3, ranked.size());
			for (WeaponStyle s : ranked)
			{
				assertEquals(CombatStyle.CRUSH, s.type());
			}
		});
	}

	@Test
	public void whipExposesItsThreeSlashStyles()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(4151))); // Abyssal whip -> whip (3 slash styles)
			pickCerberus(section);

			List<WeaponStyle> ranked = section.rankedStylesForTest();
			assertEquals(3, ranked.size());
			for (WeaponStyle s : ranked)
			{
				assertEquals(CombatStyle.SLASH, s.type());
			}
		});
	}
}
