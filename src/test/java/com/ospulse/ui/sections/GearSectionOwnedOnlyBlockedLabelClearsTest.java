package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import net.runelite.client.config.ConfigManager;

import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * P2-B fix (Codex finding on PR #19, {@code GearSection.java:5123}, "Hide
 * the blocked message before rendering a later result"): {@code
 * optimizerOwnedOnlyBlockedLabel} (the P1-A "cannot recommend" line) was made
 * visible by {@code renderOwnedOnlyBlockedState} but never hidden by any
 * successful or no-usable-weapon path — so once a search was blocked for a
 * target with an unsatisfiable mandatory-gear requirement (e.g. Rune dragon /
 * Insulated boots), searching a DIFFERENT target with no such requirement
 * (e.g. Cerberus) would show a valid new result with the stale "Cannot
 * recommend a loadout vs Rune dragon: ..." message still visible underneath
 * it.
 *
 * <p>Uses the same real curated Rune dragon / Insulated boots entry as {@link
 * GearSectionOwnedOnlyMandatoryOverrideBlockTest} to trigger the blocked
 * state, then switches to Cerberus (no mandatory override — the standard
 * "normal owned-only result" target used across this test suite) to drive a
 * non-blocked result and assert the earlier message is gone.
 */
public class GearSectionOwnedOnlyBlockedLabelClearsTest
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
	private static final int RANDOM_UNRELATED_BOOTS = 4119; // Climbing boots — no override relevance

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
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		return GearSnapshot.builder()
			.equippedItemIds(bootsSlot(bootsItemId))
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
		section.searchFieldForTest().setText(name);
		int index = indexOfContaining(section.monsterListForTest().getModel(), name);
		assertTrue(name + " must appear in the filtered list", index >= 0);
		section.monsterListForTest().setSelectedIndex(index);
	}

	/** See {@code GearSectionOwnedOnlyModeTest#mockConfigManager}. */
	private static ConfigManager mockConfigManager(String rawIronmanOwnedOnly)
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("test-profile");
		Mockito.when(configManager.getRSProfileConfiguration(
				com.ospulse.OSPulseConfig.GROUP, "ironmanOwnedOnly"))
			.thenReturn(rawIronmanOwnedOnly);
		return configManager;
	}

	@Test
	public void blockedMessage_clearsWhenALaterTargetProducesANonBlockedResult()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.apply(snapshotWith(gearWithBoots(RANDOM_UNRELATED_BOOTS)));
			pickMonster(section, "Rune dragon");
			section.runOptimizerSyncForTest();

			assertTrue("sanity: the blocked message must be showing before the target switch",
				section.optimizerOwnedOnlyBlockedVisibleForTest());

			pickMonster(section, "Cerberus");
			section.runOptimizerSyncForTest();

			assertFalse("the stale blocked message from the Rune dragon search must be hidden "
					+ "once a non-blocked result for a different target lands",
				section.optimizerOwnedOnlyBlockedVisibleForTest());
			assertTrue("a normal usable result for the new target must be shown",
				section.optimizerResultVisibleForTest());
		});
	}
}
