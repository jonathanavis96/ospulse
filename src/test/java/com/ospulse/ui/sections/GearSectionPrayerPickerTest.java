package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.OffensivePrayer;
import com.ospulse.combat.PoweredStaff;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import net.runelite.client.config.ConfigManager;

import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * Covers the prayer toggle's right-click swap menu: mirrors the potion
 * toggle's swap menu byte-for-byte, offering each style's existing prayer
 * ladder ({@link com.ospulse.combat.CombatIcons#prayerVariantsFor}) so an
 * account without Piety/Rigour/Augury can simulate the tier it actually has.
 * The pick persists per style ({@code prayerVariant.<style>}) and applies
 * only while {@link GearSection#bestPrayerToggleForTest} is selected — with
 * the toggle off the player's real active prayers win.
 *
 * <p>The picker was implemented by generalizing the potion swap-menu
 * machinery ({@code buildVariantPopup}/{@code loadVariants}/{@code
 * saveVariant}) rather than duplicating it (token budget), so several of the
 * pieces this test exercises ({@code populatePrayerVariantPopup}, {@code
 * effectivePrayerFor}, {@code prayerVariantByStyle}) are private with no
 * dedicated test seam — reached here via reflection instead, the same way
 * {@link GearSection#bestPrayerToggleForTest} already exposes the toggle
 * whose real {@code getComponentPopupMenu()} the popup tests reuse.
 */
public class GearSectionPrayerPickerTest
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

	// Same map-backed ConfigManager mock as GearSectionRiskCapTest, so a
	// value written through the mock is actually read back by a later
	// call/second GearSection instance — a plain no-op mock would make every
	// persistence-round-trip test a false green.
	private final Map<String, String> groupConfig = new HashMap<>();
	private final ConfigManager configManager = newConfigManager();

	private ConfigManager newConfigManager()
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("test-profile");
		Mockito.when(configManager.getConfiguration(Mockito.eq(com.ospulse.OSPulseConfig.GROUP), Mockito.anyString()))
			.thenAnswer(invocation -> groupConfig.get((String) invocation.getArgument(1)));
		Mockito.doAnswer(invocation ->
			{
				groupConfig.put((String) invocation.getArgument(1), (String) invocation.getArgument(2));
				return null;
			})
			.when(configManager).setConfiguration(
				Mockito.eq(com.ospulse.OSPulseConfig.GROUP), Mockito.anyString(), Mockito.anyString());
		return configManager;
	}

	private GearSection newSectionWithConfig()
	{
		return new GearSection(NO_STORE, null, null, null, configManager);
	}

	/** Writes a raw config value directly, bypassing any GearSection round-trip — used to simulate a stale/unknown persisted enum name. */
	private void writeRawConfig(String key, String value)
	{
		groupConfig.put(key, value);
	}

	/** int[14] with only the WEAPON slot (ordinal 3) populated. */
	private static int[] weaponSlot(int weaponId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[3] = weaponId;
		return ids;
	}

	/** Trident of the seas (11907) -> a powered staff, which resolves straight to CombatStyle.MAGIC with no spell picker involved. */
	private static GearSnapshot magicGear()
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 60, 0,
				0, 0, 0, 0, 0,
				0, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.poweredStaff(PoweredStaff.TRIDENT_OF_THE_SEAS)
			.build();
		return GearSnapshot.builder()
			.equippedItemIds(weaponSlot(11907))
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

	/** Equips a magic weapon and selects a minimal synthetic target, so {@code selectedStyle.type()} resolves to MAGIC. */
	private static void selectMagicTarget(GearSection section)
	{
		section.apply(snapshotWith(magicGear()));
		section.selectTargetForTest(Monster.builder().name("Cerberus").hitpoints(1).build());
	}

	// ---- reflection seams: buildVariantPopup/loadVariants/saveVariant generalization
	// (see class javadoc) left populatePrayerVariantPopup/effectivePrayerFor/
	// prayerVariantByStyle private with no dedicated main-source test seam ----

	private static Object invokePrivate(Object target, String name, Class<?>[] types, Object... args)
	{
		try
		{
			Method m = GearSection.class.getDeclaredMethod(name, types);
			m.setAccessible(true);
			return m.invoke(target, args);
		}
		catch (ReflectiveOperationException e)
		{
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, OffensivePrayer> prayerVariantByStyle(GearSection section)
	{
		try
		{
			Field f = GearSection.class.getDeclaredField("prayerVariantByStyle");
			f.setAccessible(true);
			return (Map<String, OffensivePrayer>) f.get(section);
		}
		catch (ReflectiveOperationException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static void populatePrayerVariantPopup(GearSection section, JPopupMenu menu)
	{
		invokePrivate(section, "populatePrayerVariantPopup", new Class<?>[] {JPopupMenu.class}, menu);
	}

	/**
	 * Forces {@code selectedStyle} to null via reflection. A plain freshly-constructed
	 * section is NOT actually in this state — {@code rankAndRender}'s unarmed fallback
	 * (see {@code GearSectionStyleRankingTest#unarmedFallsBackToThreeCrushStyles})
	 * always resolves to a real CRUSH style, even with no gear/target — so the
	 * "no style selected" defensive branch in {@code populatePrayerVariantPopup}
	 * (mirrored from the potion popup's identical branch) has to be reached this way.
	 */
	private static void clearSelectedStyle(GearSection section)
	{
		try
		{
			Field f = GearSection.class.getDeclaredField("selectedStyle");
			f.setAccessible(true);
			f.set(section, null);
		}
		catch (ReflectiveOperationException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static OffensivePrayer effectivePrayerFor(GearSection section, CombatStyle style)
	{
		return (OffensivePrayer) invokePrivate(section, "effectivePrayerFor", new Class<?>[] {CombatStyle.class}, style);
	}

	@Test
	public void prayerPopup_offersTheStylesLadderAndPersistsThePick()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			selectMagicTarget(section);

			JPopupMenu menu = section.bestPrayerToggleForTest().getComponentPopupMenu();
			populatePrayerVariantPopup(section, menu);

			// Finding 3: the picker's own list (not the level-inferred ladder) —
			// Augury, Mystic Vigour, Mystic Might, Mystic Lore, Mystic Will.
			assertEquals("Augury", ((JMenuItem) menu.getComponent(0)).getText());
			assertEquals("Mystic Vigour", ((JMenuItem) menu.getComponent(1)).getText());

			((JMenuItem) menu.getComponent(1)).doClick();

			assertEquals(OffensivePrayer.MYSTIC_VIGOUR, prayerVariantByStyle(section).get("magic"));
			GearSection reloaded = newSectionWithConfig();
			assertEquals(OffensivePrayer.MYSTIC_VIGOUR, prayerVariantByStyle(reloaded).get("magic"));
		});
	}

	@Test
	public void prayerPopup_withNoStyleSelected_showsOneDisabledHint()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			clearSelectedStyle(section);

			JPopupMenu menu = section.bestPrayerToggleForTest().getComponentPopupMenu();
			populatePrayerVariantPopup(section, menu);

			assertEquals(1, menu.getComponentCount());
			assertFalse(((JMenuItem) menu.getComponent(0)).isEnabled());
		});
	}

	@Test
	public void effectivePrayer_usesThePickOnlyWhileTheBestPrayerToggleIsOn()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			selectMagicTarget(section);
			prayerVariantByStyle(section).put("magic", OffensivePrayer.MYSTIC_MIGHT);

			section.bestPrayerToggleForTest().setSelected(true);
			assertEquals(OffensivePrayer.MYSTIC_MIGHT, effectivePrayerFor(section, CombatStyle.MAGIC));

			section.bestPrayerToggleForTest().setSelected(false);
			assertNotEquals("toggle off means real prayers win, not the simulation pick",
				OffensivePrayer.MYSTIC_MIGHT, effectivePrayerFor(section, CombatStyle.MAGIC));
		});
	}

	/**
	 * Finding 1 (P1): the prayer pick changed the toggle's icon/tooltip and
	 * persisted config, but {@code GearMapper.toPlayerCombat} never threaded
	 * {@code assumedPrayer} through to the DPS calculator, so it silently fell
	 * back to the hardcoded top-tier prayer (Augury here) regardless of the
	 * pick — the LIVE DPS READOUT ({@link GearSection#dpsTextForTest}) never
	 * moved. Picks the weakest listed prayer (a real strength cut from
	 * Augury) so any live-wired path is guaranteed to change the number.
	 */
	@Test
	public void bestPrayerToggle_appliesThePickToTheLiveDpsReadout()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			selectMagicTarget(section);
			section.bestPrayerToggleForTest().setSelected(true);

			String withDefaultPrayer = section.dpsTextForTest();
			assertFalse("fixture sanity: a real DPS number must be showing before the pick",
				withDefaultPrayer == null || withDefaultPrayer.equals("-"));

			JPopupMenu menu = section.bestPrayerToggleForTest().getComponentPopupMenu();
			populatePrayerVariantPopup(section, menu);
			((JMenuItem) menu.getComponent(menu.getComponentCount() - 1)).doClick(); // weakest listed prayer

			assertNotEquals("picking a weaker prayer must move the live DPS readout, not just the icon/config",
				withDefaultPrayer, section.dpsTextForTest());
		});
	}

	@Test
	public void unknownPersistedPrayerName_fallsBackToTheStyleDefault()
	{
		onEdt(() ->
		{
			writeRawConfig("prayerVariant.magic", "NOT_A_PRAYER");

			GearSection section = newSectionWithConfig();

			assertNull(prayerVariantByStyle(section).get("magic"));
		});
	}
}
