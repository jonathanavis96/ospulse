package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.integration.BankRecommendationHighlighter;
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
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * P1-A fix (Codex finding on PR #19, {@code GearSection.java:4602},
 * "Restrict mandatory includes in owned-only mode"): in owned-only mode, a
 * target with a mandatory {@code MonsterGearOverride} the player owns
 * neither the primary item nor any accepted substitute for must never
 * produce a normal recommendation — the panel's own force-include (see
 * {@code ItemEligibility#mandatoryOverrideItemIds}) bypasses the zero budget
 * and would otherwise force-equip an item the player cannot actually wear,
 * defeating the mode's entire "every recommendation is something you own"
 * guarantee.
 *
 * <p>The earlier fix for this finding only disclosed the gap via {@code
 * GearSection#updateGearOverrideNote()}'s "you don't own this" advisory line
 * ({@link GearSectionOverrideOwnershipNoteTest}) — Codex correctly escalated
 * that as insufficient, since the advisory sits elsewhere on the panel and
 * does not stop the loadout/auto-preview/bank-highlight from still
 * recommending the unowned item. This test covers the explicit
 * "cannot recommend" branch taken instead (mirroring the pre-existing
 * no-usable-weapon path), using the same real curated Rune dragon /
 * Insulated boots entry as {@link GearSectionOverrideOwnershipNoteTest}.
 */
public class GearSectionOwnedOnlyMandatoryOverrideBlockTest
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
		section.monsterSearchField.setText(name);
		int index = indexOfContaining(section.monsterList.getModel(), name);
		assertTrue(name + " must appear in the filtered list", index >= 0);
		section.monsterList.setSelectedIndex(index);
	}

	/** See {@link GearSectionOwnedOnlyModeTest#mockConfigManager} — the per-profile read owns behaviour now. */
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
	public void ownedOnly_bootsNotOwned_blocksTheRecommendationInsteadOfForceEquippingUnownedItem()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);
			BankRecommendationHighlighter bankHighlighter = Mockito.mock(BankRecommendationHighlighter.class);
			section.setBankHighlighter(bankHighlighter);

			section.apply(snapshotWith(gearWithBoots(RANDOM_UNRELATED_BOOTS)));
			pickMonster(section, "Rune dragon");
			section.runOptimizerSyncForTest();

			assertTrue("the blocked message must be shown", section.ownedOnlyBlockedLabel.isVisible());
			String message = section.ownedOnlyBlockedLabel.getText();
			assertTrue("must name the required item: " + message, message.contains("Insulated boots"));
			assertTrue("must include the mechanic reason: " + message,
				message.contains("Halves the lightning special-attack damage"));

			assertNull("no result may be installed while blocked", section.lastOptimizerResult);
			assertTrue("no what-if override may be auto-applied while blocked",
				section.override.isEmpty());
			Mockito.verify(bankHighlighter, Mockito.atLeastOnce()).clear();
		});
	}

	@Test
	public void ownedOnly_bootsOwned_producesANormalResult_notBlocked()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("true");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.apply(snapshotWith(gearWithBoots(INSULATED_BOOTS)));
			pickMonster(section, "Rune dragon");
			section.runOptimizerSyncForTest();

			assertFalse("the blocked message must not show once the requirement is satisfied",
				section.ownedOnlyBlockedLabel.isVisible());
			assertTrue("a normal usable result must still be shown",
				section.resultPanel.isVisible());
		});
	}

	@Test
	public void notOwnedOnlyMode_bootsNotOwned_stillForceIncludesAndDoesNotBlock()
	{
		onEdt(() ->
		{
			ConfigManager configManager = mockConfigManager("false");
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);

			section.apply(snapshotWith(gearWithBoots(RANDOM_UNRELATED_BOOTS)));
			pickMonster(section, "Rune dragon");
			section.runOptimizerSyncForTest();

			assertFalse("outside owned-only mode, the force-include behaviour is unchanged — never blocked",
				section.ownedOnlyBlockedLabel.isVisible());
		});
	}
}
