package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.model.ItemStack;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.wealth.WealthSnapshot;

import org.junit.Test;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end wiring test for the "best spec weapon" cell (design spec §8):
 * drives the real {@code GearSection} + real curated catalog + real
 * {@code MonsterCombatRequirementRepository} data (Zulrah's genuine
 * ranged/magic gate, General Graardor's genuine high Defence) headlessly, no
 * client thread required (same pattern as {@code GearSectionTargetWiringTest}).
 */
public class GearSectionSpecWeaponCellTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

	private static final int DRAGON_CLAWS = 13652;
	private static final int DRAGON_WARHAMMER = 13576;

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

	/** A 99-stats loadout with {@code weaponItemId} worn (making it owned via GearSection.ownedPriceMap's equipped-item path). */
	private static GearSnapshot gearWielding(int weaponItemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		ids[3] = weaponItemId; // WEAPON_SLOT
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		return GearSnapshot.builder()
			.attack(99, 99)
			.strength(99, 99)
			.defence(99, 99)
			.ranged(99, 99)
			.magic(99, 99)
			.prayer(99, 99)
			.hitpoints(99, 99)
			.equipmentStats(stats)
			.equippedItemIds(ids)
			.build();
	}

	private static WealthSnapshot wealthOwning(int... itemIds)
	{
		Map<Integer, ItemStack> holdings = new HashMap<>();
		for (int id : itemIds)
		{
			holdings.put(id, new ItemStack(id, "test item", 1, 0));
		}
		return WealthSnapshot.builder().allHoldings(holdings).build();
	}

	private static SessionSnapshot snapshotWith(GearSnapshot gear, WealthSnapshot wealth)
	{
		return new SessionSnapshot(0L, 0L, 0L, 0L, 0L, 0L, false,
			null, null, 0L, wealth, null, null, null, 0L, gear, 0L, null, 0L);
	}

	private static int indexOf(ListModel<String> model, String needleLowercase)
	{
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).toLowerCase(java.util.Locale.ROOT).contains(needleLowercase))
			{
				return i;
			}
		}
		return -1;
	}

	@Test
	public void noTargetSelectedShowsNoRecommendation()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWielding(DRAGON_CLAWS), null));
			// -1, not the raw Integer.MIN_VALUE constructor default: apply()
			// already ran one refresh(null, ...) with no target selected.
			assertEquals(-1, section.specWeaponCellItemIdForTest());
		});
	}

	@Test
	public void ownedDamageSpecIsRecommendedAgainstALowDefenceTarget()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWielding(DRAGON_CLAWS), null));

			// Cerberus: bundled defenceLevel 100, well under HIGH_DEFENCE_THRESHOLD,
			// no combat-requirement gate — a plain DAMAGE-role pick.
			section.searchFieldForTest().setText("cerberus");
			int index = indexOf(section.monsterListForTest().getModel(), "cerberus");
			assertTrue("Cerberus must appear in the filtered list", index >= 0);
			section.monsterListForTest().setSelectedIndex(index);

			assertEquals(DRAGON_CLAWS, section.specWeaponCellItemIdForTest());
		});
	}

	@Test
	public void illegalWeaponAgainstARealGatedTargetIsExcluded()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Only a melee DAMAGE spec is owned; Zulrah's genuine, already-shipped
			// combat requirement permits only Ranged/Magic (plus a polearm
			// exception dragon claws does not fall under).
			section.apply(snapshotWith(gearWielding(DRAGON_CLAWS), null));

			section.searchFieldForTest().setText("zulrah");
			int index = indexOf(section.monsterListForTest().getModel(), "zulrah");
			assertTrue("a Zulrah phase must appear in the filtered list", index >= 0);
			section.monsterListForTest().setSelectedIndex(index);

			assertEquals("dragon claws is melee-only and must be excluded by Zulrah's real gate",
				-1, section.specWeaponCellItemIdForTest());
		});
	}

	@Test
	public void defenceDrainOwnedInTheBankOutranksADamageSpecAtHighDefence()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Dragon claws worn (DAMAGE), Dragon warhammer owned in the bank
			// (DEFENCE_DRAIN, not equipped — proving bank ownership, not just
			// worn-item ownership, feeds the selector).
			section.apply(snapshotWith(gearWielding(DRAGON_CLAWS), wealthOwning(DRAGON_WARHAMMER)));

			// General Graardor: bundled defenceLevel 250 (>= HIGH_DEFENCE_THRESHOLD),
			// no combat-requirement gate (explicitly on the REJECTED list — see the
			// stage-1a melee-gate dataset README).
			section.searchFieldForTest().setText("graardor");
			int index = indexOf(section.monsterListForTest().getModel(), "graardor");
			assertTrue("General Graardor must appear in the filtered list", index >= 0);
			section.monsterListForTest().setSelectedIndex(index);

			assertEquals("rule 2 (defence-drain priority) must win at high Defence",
				DRAGON_WARHAMMER, section.specWeaponCellItemIdForTest());
		});
	}
}
