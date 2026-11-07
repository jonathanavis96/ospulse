package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.MonsterGearOverride;
import com.ospulse.combat.MonsterGearOverrideRepository;
import com.ospulse.combat.optimizer.GearOptimizer;
import com.ospulse.integration.BankRecommendationHighlighter;
import com.ospulse.model.ItemStack;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.wealth.WealthSnapshot;

import net.runelite.client.config.ConfigManager;

import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
			GearSectionTestOps.runOptimizerSyncForTest(section);

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
			GearSectionTestOps.runOptimizerSyncForTest(section);

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
			GearSectionTestOps.runOptimizerSyncForTest(section);

			assertFalse("outside owned-only mode, the force-include behaviour is unchanged — never blocked",
				section.ownedOnlyBlockedLabel.isVisible());
		});
	}

	private static final int EARMUFFS = 4166;
	private static final int HELM_OF_NEITIZNOT = 10828;
	private static final int SLAYER_HELMET_IMBUED = 11865;
	private static final int HEAD_SLOT = 0;

	private static GearSnapshot gearWithHead(int headItemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[HEAD_SLOT] = headItemId;
		return GearSnapshot.builder()
			.equippedItemIds(ids)
			.attack(99, 99)
			.strength(99, 99)
			.defence(99, 99)
			.ranged(99, 99)
			.magic(99, 99)
			.prayer(77, 77)
			.hitpoints(99, 99)
			.equipmentStats(EquipmentStats.builder().weaponSpeedTicks(4).build())
			.build();
	}

	private static SessionSnapshot snapshotWith(GearSnapshot gear, WealthSnapshot wealth)
	{
		return new SessionSnapshot(0L, 0L, 0L, 0L, 0L, 0L, false,
			null, null, 0L, wealth, null, null, null, 0L, gear, 0L, null, 0L);
	}

	private static WealthSnapshot imbuedHelmetAndNeitiznotBanked()
	{
		List<ItemStack> holdings = new ArrayList<>();
		holdings.add(new ItemStack(SLAYER_HELMET_IMBUED, "Slayer helmet (i)", 1, 0L));
		holdings.add(new ItemStack(HELM_OF_NEITIZNOT, "Helm of neitiznot", 1, 50_000L));
		return WealthSnapshot.builder().topHoldings(holdings).build();
	}

	/**
	 * Earmuffs have no stats row, so no equipment index row, so the ownership
	 * map never lists them. The requirement must still count as met by a
	 * player who has them, and the result must outlive the next snapshot: the
	 * check {@code apply()} repeats on every snapshot uses the same ownership
	 * as the check that installed the result.
	 */
	@Test
	public void ownedOnly_requiredItemHasNoIndexRow_isNotBlockedWhenWorn()
	{
		GearSnapshot gear = gearWithHead(EARMUFFS);

		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null, null, mockConfigManager("true"));

			section.apply(snapshotWith(gear));
			pickMonster(section, "Banshee");
			GearSectionTestOps.runOptimizerSyncForTest(section);

			assertFalse("owning the Earmuffs must not refuse the target",
				section.ownedOnlyBlockedLabel.isVisible());
			assertTrue("must not claim the worn Earmuffs are not owned",
				GearSectionTestOps.gearOverrideNoteTextsForTest(section).stream()
					.noneMatch(note -> note.contains("you don't own this")));

			GearOptimizer.Result installed = section.lastOptimizerResult;
			assertNotNull("a loadout keeping the Earmuffs must be installed", installed);

			section.apply(snapshotWith(gear));

			assertSame("the next identical snapshot must not wipe the result", installed,
				section.lastOptimizerResult);
		});
	}

	/**
	 * Worn Earmuffs cannot be force-equipped, so an owned Slayer helmet (i)
	 * has to be, or the search is free to pick a helm that fails the
	 * requirement. Checked through the real panel, because the panel is what
	 * works out which ids count as owned.
	 */
	private static void assertBansheeHeadSlotKeepsTheRequirement(String ownedOnly)
	{
		GearSnapshot gear = gearWithHead(EARMUFFS);
		WealthSnapshot wealth = imbuedHelmetAndNeitiznotBanked();
		MonsterGearOverride requirement = MonsterGearOverrideRepository.getInstance().forMonster("Banshee").get(0);

		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null, null, mockConfigManager(ownedOnly));

			section.apply(snapshotWith(gear, wealth));
			pickMonster(section, "Banshee");
			GearSectionTestOps.runOptimizerSyncForTest(section);

			assertNotNull("a result must be installed", section.lastOptimizerResult);
			int head = section.loadoutSlotMap(section.lastOptimizerResult).get(HEAD_SLOT);
			assertTrue("head " + head + " must satisfy the requirement", requirement.satisfiedBy(head));
			assertEquals(SLAYER_HELMET_IMBUED, head);

			GearOptimizer.Result installed = section.lastOptimizerResult;
			section.apply(snapshotWith(gear, wealth));
			assertSame("the next snapshot must not wipe the result", installed, section.lastOptimizerResult);
		});
	}

	@Test
	public void ownedOnly_earmuffsWornHelmetBanked_recommendsTheHelmet()
	{
		assertBansheeHeadSlotKeepsTheRequirement("true");
	}

	@Test
	public void notOwnedOnly_earmuffsWornHelmetBanked_recommendsTheHelmet()
	{
		assertBansheeHeadSlotKeepsTheRequirement("false");
	}
}
