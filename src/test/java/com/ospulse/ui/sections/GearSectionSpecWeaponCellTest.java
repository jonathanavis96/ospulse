package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.SpecWeapon;
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
	private static final int DRAGON_DAGGER = 1231;
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

	/** An unarmed loadout (nothing in any slot) with the given base Attack, everything else 99 — for the equip-level-gate tests. */
	private static GearSnapshot unarmedGearWithAttackLevel(int attackLevel)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
			.build();
		return GearSnapshot.builder()
			.attack(attackLevel, attackLevel)
			.strength(99, 99)
			.defence(99, 99)
			.ranged(99, 99)
			.magic(99, 99)
			.prayer(99, 99)
			.hitpoints(99, 99)
			.equipmentStats(stats)
			.equippedItemIds(new int[GearSnapshot.EQUIPMENT_SLOT_COUNT])
			.build();
	}

	/** A 99-stats loadout with {@code weaponItemId} worn and {@code ammoItemId} worn in the AMMO slot (13). */
	private static GearSnapshot gearWieldingWithAmmo(int weaponItemId, int ammoItemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		ids[3] = weaponItemId; // WEAPON_SLOT
		ids[13] = ammoItemId; // AMMO_SLOT
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

	/** A 99-stats loadout with nothing wielded but {@code shieldItemId} worn in the SHIELD slot (5). */
	private static GearSnapshot gearWithShield(int shieldItemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		ids[5] = shieldItemId; // SHIELD_SLOT
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
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

	private static SpecWeapon specWeaponByItemId(int itemId)
	{
		return SpecWeapon.CATALOG.stream().filter(w -> w.itemId() == itemId).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no catalog entry for " + itemId));
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

	// ---- PR #25 finding 1: ownership does not imply equippability ------------------------

	@Test
	public void ownedSpecBelowItsEquipLevelIsNotRecommended()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Dragon claws requires 60 Attack (verified against
			// equipment_requirements.min.json); owned in the bank at 50 Attack.
			section.apply(snapshotWith(unarmedGearWithAttackLevel(50), wealthOwning(DRAGON_CLAWS)));

			section.searchFieldForTest().setText("cerberus");
			section.monsterListForTest().setSelectedIndex(indexOf(section.monsterListForTest().getModel(), "cerberus"));

			assertEquals("50 Attack cannot equip a 60-Attack Dragon claws",
				-1, section.specWeaponCellItemIdForTest());
		});
	}

	@Test
	public void ownedSpecAtOrAboveItsEquipLevelIsRecommended()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(unarmedGearWithAttackLevel(60), wealthOwning(DRAGON_CLAWS)));

			section.searchFieldForTest().setText("cerberus");
			section.monsterListForTest().setSelectedIndex(indexOf(section.monsterListForTest().getModel(), "cerberus"));

			assertEquals(DRAGON_CLAWS, section.specWeaponCellItemIdForTest());
		});
	}

	// ---- PR #25 finding 2: ranged specs need worn-ammo validation ------------------------

	@Test
	public void rangedSpecAtAnAmmoGatedTargetNeedsTheRightWornAmmo()
	{
		onEdt(() ->
		{
			int magicShortbow = 861; // DAMAGE-role RANGED spec that fires worn ammo (unlike the UTILITY-role Zaryte crossbow)
			int regularArrow = 893; // "Rune arrow" — a real, non-broad arrow
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWieldingWithAmmo(magicShortbow, regularArrow), null));

			// Kurask: the repo's own real ammo-gated target (leaf-bladed weapons,
			// broad ammo, or Magic Dart only) — see monster_combat_requirements.json.
			section.searchFieldForTest().setText("kurask");
			int index = indexOf(section.monsterListForTest().getModel(), "kurask");
			assertTrue("Kurask must appear in the filtered list", index >= 0);
			section.monsterListForTest().setSelectedIndex(index);

			assertEquals("permitsWeapon() alone would accept a worn-ammo bow here; "
					+ "the actual (non-broad) loaded arrows must still be rejected by permitsAmmo()",
				-1, section.specWeaponCellItemIdForTest());
		});
	}

	@Test
	public void rangedSpecAtAnAmmoGatedTargetIsRecommendedWithTheRightWornAmmo()
	{
		onEdt(() ->
		{
			int magicShortbow = 861;
			int broadArrow = 4160; // "Broad arrows" — matches Kurask's allowedAmmoIds
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWieldingWithAmmo(magicShortbow, broadArrow), null));

			section.searchFieldForTest().setText("kurask");
			section.monsterListForTest().setSelectedIndex(indexOf(section.monsterListForTest().getModel(), "kurask"));

			assertEquals(magicShortbow, section.specWeaponCellItemIdForTest());
		});
	}

	// ---- PR #25 finding 3: clear the shield when scoring a two-handed spec ---------------

	@Test
	public void twoHandedSpecClearsAnEquippedShieldWhenScored()
	{
		onEdt(() ->
		{
			int avernicDefender = 24186; // real shield-slot item, str=8 (verified against equipment_stats.min.json)
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithShield(avernicDefender), wealthOwning(DRAGON_CLAWS, DRAGON_DAGGER)));

			SpecWeapon claws = specWeaponByItemId(DRAGON_CLAWS); // two-handed (isTwoHanded=true in equipment_index.min.json); own str bonus 56
			SpecWeapon dagger = specWeaponByItemId(DRAGON_DAGGER); // one-handed; own str bonus 40

			EquipmentStats clawsStats = section.swappedEquipmentStatsForTest(claws);
			EquipmentStats daggerStats = section.swappedEquipmentStatsForTest(dagger);

			assertEquals("a two-handed candidate must NOT inherit the equipped shield's strength bonus",
				56, clawsStats.str()); // Dragon claws' own str bonus only, no +8 from the shield
			assertEquals("a one-handed candidate legitimately keeps the shield it can actually wear alongside",
				48, daggerStats.str()); // Dragon dagger's own 40 + the shield's 8
		});
	}

	// ---- PR #25 finding 4: honour "Exclude from suggestions" ------------------------------

	@Test
	public void excludingASpecWeaponRemovesItAndADifferentSpecIsChosen()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Dragon claws (worn) would win over Dragon dagger on damage/cost —
			// see SpecWeaponSelectorTest's equivalent unit-level scenario.
			section.apply(snapshotWith(gearWielding(DRAGON_CLAWS), wealthOwning(DRAGON_DAGGER)));

			section.searchFieldForTest().setText("cerberus");
			section.monsterListForTest().setSelectedIndex(indexOf(section.monsterListForTest().getModel(), "cerberus"));
			assertEquals("sanity check: claws must win before exclusion",
				DRAGON_CLAWS, section.specWeaponCellItemIdForTest());

			section.excludeItemFromSuggestionsForTest(DRAGON_CLAWS);

			assertEquals("excluding claws must let dagger take over, not just show nothing",
				DRAGON_DAGGER, section.specWeaponCellItemIdForTest());
		});
	}

	@Test
	public void excludingTheOnlyOwnedSpecShowsNoRecommendation()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWielding(DRAGON_CLAWS), null));

			section.searchFieldForTest().setText("cerberus");
			section.monsterListForTest().setSelectedIndex(indexOf(section.monsterListForTest().getModel(), "cerberus"));
			assertEquals(DRAGON_CLAWS, section.specWeaponCellItemIdForTest());

			section.excludeItemFromSuggestionsForTest(DRAGON_CLAWS);

			assertEquals("Exclude from suggestions must apply to the spec cell exactly as it does to the optimiser",
				-1, section.specWeaponCellItemIdForTest());
		});
	}
}
