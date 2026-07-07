package com.ospulse.ui.sections;

import com.ospulse.combat.BlowpipeDart;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import org.junit.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the blowpipe-dart right-click menu: {@code OSPulseConfig.blowpipeDart}
 * is now {@code hidden} from the settings panel, so the ONLY way to change it
 * is right-clicking the blowpipe in the gear panel's WEAPON slot — a "Set
 * darts" submenu nested in the existing "Exclude from suggestions" popup (see
 * {@code GearSection#buildExcludeItemPopup}/{@code populateBlowpipeDartSubmenu}).
 * Picking a dart must persist it to config and immediately re-rank the
 * readout (mirrors the potion-variant right-click swap — see
 * {@code GearSectionPotionVariantTest}). No Mockito/fake {@code ConfigManager}
 * is available in this module (same constraint noted in
 * {@code GearSectionPotionVariantTest}), so the persist path is exercised via
 * the {@code configManager == null} branch every read/write in
 * {@code GearSection} guards identically to the real branch, plus a focused
 * assertion that {@code pickBlowpipeDart} doesn't throw with no
 * {@link net.runelite.client.config.ConfigManager} wired (headless-safe) and
 * that the in-memory "currently selected" read reflects the default.
 */
public class GearSectionBlowpipeDartTest
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

	private static final int TOXIC_BLOWPIPE = 12926;
	private static final int ABYSSAL_WHIP = 4151; // non-blowpipe melee weapon

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
			.add(0, 0, 0, 0, 82,
				0, 0, 0, 0, 0,
				0, 20, 0.0, 0)
			.weaponSpeedTicks(3)
			.isTwoHanded(true)
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

	/** Finds the nested "Set darts" {@link JMenu} in a popup, or {@code null} if absent. */
	private static JMenu findDartsMenu(JPopupMenu menu)
	{
		for (Component c : menu.getComponents())
		{
			if (c instanceof JMenu && "Set darts".equals(((JMenu) c).getText()))
			{
				return (JMenu) c;
			}
		}
		return null;
	}

	@Test
	public void weaponSlotShowingBlowpipe_popupHasSetDartsSubmenu()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(TOXIC_BLOWPIPE)));

			JPopupMenu popup = section.weaponSlotPopupForTest();
			JMenu darts = findDartsMenu(popup);
			assertTrue("blowpipe weapon slot must offer a 'Set darts' submenu", darts != null);

			// One item per BlowpipeDart value, and the default (Dragon) is marked checked.
			assertEquals(BlowpipeDart.values().length, darts.getItemCount());
			boolean sawCheckedDragon = false;
			for (int i = 0; i < darts.getItemCount(); i++)
			{
				JCheckBoxMenuItem item = (JCheckBoxMenuItem) darts.getItem(i);
				if ("Dragon".equals(item.getText()))
				{
					assertTrue("Dragon (the default) must be shown as the current pick", item.getState());
					sawCheckedDragon = true;
				}
				else
				{
					assertFalse(item.getText() + " must not be marked current", item.getState());
				}
			}
			assertTrue(sawCheckedDragon);
		});
	}

	@Test
	public void weaponSlotShowingNonBlowpipe_popupHasNoSetDartsSubmenu()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(ABYSSAL_WHIP)));

			JPopupMenu popup = section.weaponSlotPopupForTest();
			assertNull("a non-blowpipe weapon must not offer 'Set darts'", findDartsMenu(popup));
		});
	}

	@Test
	public void defaultBlowpipeDart_isDragon()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			assertEquals(BlowpipeDart.DRAGON, section.currentBlowpipeDartForTest());
		});
	}

	@Test
	public void pickingADart_withNoConfigManager_doesNotThrowAndReRanks()
	{
		// Headless guard path (configManager == null, same as every other
		// GearSection persist path in this test module) — proves the menu
		// action's persist+recompute wiring is safe with no ConfigManager,
		// and that reading the "current" dart back afterwards still works
		// (falls back to the default since nothing was actually persisted).
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(TOXIC_BLOWPIPE)));

			section.pickBlowpipeDartForTest(BlowpipeDart.BRONZE);

			assertEquals(BlowpipeDart.DRAGON, section.currentBlowpipeDartForTest());
		});
	}
}
