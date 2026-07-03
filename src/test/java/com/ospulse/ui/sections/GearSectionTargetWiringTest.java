package com.ospulse.ui.sections;

import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.SlayerHeadgear;
import com.ospulse.combat.WeaponStyle;
import com.ospulse.session.GearMapper;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import org.junit.Test;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the target-picker → DPS wiring (the old JComboBox
 * silently computed against the first bundled monster — "A corpse", 90 HP —
 * instead of the user's pick, e.g. Cerberus showing a ~12s time-to-kill
 * instead of ~1:26).
 *
 * <p>Drives the real Swing components headlessly (no client thread needed —
 * the section is EDT-only by design and is given a null ItemManager, which it
 * must tolerate by simply skipping icons).
 */
public class GearSectionTargetWiringTest
{
	/**
	 * Runs the test body on the Swing EDT — the section is an EDT-only
	 * component and RuneLite's {@code IconTextField} asserts the EDT in
	 * {@code setText} — unwrapping any assertion/exception it threw.
	 */
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

	/** A plausible melee loadout: whip-ish accuracy/str on a 4-tick weapon, level-99 stats. */
	private static GearSnapshot meleeGear()
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(82, 82, 82, 0, 0,
				0, 0, 0, 0, 0,
				82, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		return GearSnapshot.builder()
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

	/** The same melee loadout, but with a slayer helmet / black mask in the head slot. */
	private static GearSnapshot meleeGearWithSlayerHeadgear(SlayerHeadgear headgear)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(82, 82, 82, 0, 0,
				0, 0, 0, 0, 0,
				82, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.slayerHeadgear(headgear)
			.build();
		return GearSnapshot.builder()
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

	/** Mirrors GearSection's private TTK formatting for label comparison. */
	private static String formatTtk(double ttkSeconds)
	{
		if (ttkSeconds < 60)
		{
			return String.format(Locale.ROOT, "%.1fs", ttkSeconds);
		}
		int total = (int) Math.round(ttkSeconds);
		return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
	}

	@Test
	public void noTargetIsAnExplicitEmptyStateNotASilentDefault()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(meleeGear()));

			assertNull("no monster may be pre-selected", section.selectedMonsterForTest());
			assertEquals("-", section.dpsTextForTest());
			assertEquals("-", section.ttkTextForTest());
			assertEquals("Target: pick a monster above", section.targetTextForTest());
		});
	}

	@Test
	public void selectingCerberusComputesAgainstCerberus()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(meleeGear()));

			// Type into the search box (fires the DocumentListener synchronously)
			// and click the exact "Cerberus" row in the scrollable result list.
			section.searchFieldForTest().setText("cerberus");
			int index = indexOf(section.monsterListForTest().getModel(), "Cerberus");
			assertTrue("Cerberus must appear in the filtered list", index >= 0);
			section.monsterListForTest().setSelectedIndex(index);

			Monster selected = section.selectedMonsterForTest();
			assertEquals("Cerberus", selected.name());
			assertEquals(600, selected.hitpoints());
			assertEquals("Target: Cerberus", section.targetTextForTest());

			// The rendered numbers must equal an independent computation against
			// the SAME monster the user picked, using whichever attack style the
			// ranked picker auto-selected (best DPS) with no toggles.
			GearSnapshot gear = meleeGear();
			WeaponStyle sel = section.selectedStyleForTest();
			PlayerCombat player = GearMapper.toPlayerCombat(gear, sel.stance(), false, false, false);
			DpsResult expected = DpsCalculator.compute(
				gear.equipmentStats(), player, sel.type(),
				MonsterRepository.getInstance().byName("Cerberus").get(), 20);

			assertEquals(String.format(Locale.ROOT, "%.2f", expected.dps()), section.dpsTextForTest());
			assertEquals(formatTtk(expected.ttkSeconds()), section.ttkTextForTest());

			// Sanity for the historical bug: TTK must be on the 600-HP scale
			// (hp / dps), NOT the ~12s of the old accidental 90-HP default.
			double impliedTtk = selected.hitpoints() / expected.dps();
			assertTrue("ttk should be about hp/dps for the picked monster, got "
					+ expected.ttkSeconds() + "s vs implied " + impliedTtk + "s",
				Math.abs(expected.ttkSeconds() - impliedTtk) / impliedTtk < 0.35);
			assertTrue("ttk must reflect Cerberus' 600 HP, not a small default monster",
				expected.ttkSeconds() > 30.0);

			System.out.printf(Locale.ROOT, "Cerberus check: dps=%.2f ttk=%.1fs (label \"%s\")%n",
				expected.dps(), expected.ttkSeconds(), section.ttkTextForTest());
		});
	}

	/**
	 * Regression: the boost toggles must recompute on change. They previously
	 * only carried a restyle listener, so ticking "Best potion" left Max hit
	 * frozen (the "it's 23 with them off" report). Ticking it must raise Max hit
	 * to exactly the independently-computed potion-boosted value.
	 */
	@Test
	public void tickingBestPotionRecomputesAndRaisesMaxHit()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(meleeGear()));

			section.searchFieldForTest().setText("cerberus");
			int index = indexOf(section.monsterListForTest().getModel(), "Cerberus");
			section.monsterListForTest().setSelectedIndex(index);

			int maxHitOff = Integer.parseInt(section.maxHitTextForTest());
			section.bestPotionToggleForTest().setSelected(true);
			int maxHitOn = Integer.parseInt(section.maxHitTextForTest());

			assertTrue("best potion must raise max hit (" + maxHitOn + " vs " + maxHitOff + ")",
				maxHitOn > maxHitOff);

			GearSnapshot gear = meleeGear();
			WeaponStyle sel = section.selectedStyleForTest();
			PlayerCombat boosted = GearMapper.toPlayerCombat(gear, sel.stance(), true, false, false);
			DpsResult expected = DpsCalculator.compute(
				gear.equipmentStats(), boosted, sel.type(),
				MonsterRepository.getInstance().byName("Cerberus").get(), 20);
			assertEquals(String.valueOf(expected.maxHit()), section.maxHitTextForTest());
		});
	}

	/**
	 * Wearing a slayer helm / black mask auto-ticks "On Slayer task"; taking it
	 * off auto-unticks it; and a manual untick while it is still worn is
	 * respected (edge-triggered — a later snapshot with the helm still on must
	 * not re-tick it).
	 */
	@Test
	public void slayerHeadgearAutoTicksOnSlayerTaskButUntickSticks()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);

			// No headgear -> stays off.
			section.apply(snapshotWith(meleeGear()));
			assertTrue("off with no slayer headgear", !section.onSlayerTaskToggleForTest().isSelected());

			// Put on a black mask / slayer helm -> auto-ticks.
			section.apply(snapshotWith(meleeGearWithSlayerHeadgear(SlayerHeadgear.STANDARD)));
			assertTrue("auto-ticked while slayer headgear worn", section.onSlayerTaskToggleForTest().isSelected());

			// User unticks (off-task) while still wearing it, then another
			// snapshot arrives with the helm still on -> must stay unticked.
			section.onSlayerTaskToggleForTest().setSelected(false);
			section.apply(snapshotWith(meleeGearWithSlayerHeadgear(SlayerHeadgear.IMBUED)));
			assertTrue("manual untick respected while still worn",
				!section.onSlayerTaskToggleForTest().isSelected());

			// Take the headgear off -> auto-unticks (no-op here, already off) and
			// re-equipping re-arms the auto-tick.
			section.apply(snapshotWith(meleeGear()));
			section.apply(snapshotWith(meleeGearWithSlayerHeadgear(SlayerHeadgear.STANDARD)));
			assertTrue("re-equipping re-arms auto-tick", section.onSlayerTaskToggleForTest().isSelected());
		});
	}

	@Test
	public void targetSurvivesFilteringItOutOfTheList()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(meleeGear()));

			section.searchFieldForTest().setText("cerberus");
			int index = indexOf(section.monsterListForTest().getModel(), "Cerberus");
			section.monsterListForTest().setSelectedIndex(index);
			String dpsBefore = section.dpsTextForTest();

			// Narrow the filter so Cerberus vanishes from the visible list — the
			// chosen target (and the numbers) must stick.
			section.searchFieldForTest().setText("zulrah");
			assertEquals("Cerberus", section.selectedMonsterForTest().name());
			assertEquals("Target: Cerberus", section.targetTextForTest());
			assertEquals(dpsBefore, section.dpsTextForTest());
		});
	}
}
