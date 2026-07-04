package com.ospulse.session;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.OffensivePrayer;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.SalveType;
import com.ospulse.combat.SlayerHeadgear;
import com.ospulse.combat.Stance;
import com.ospulse.combat.VoidSet;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the pure id-&gt;{@link EquipmentStats} and
 * snapshot-&gt;{@link PlayerCombat} mapping logic, with no RuneLite runtime
 * dependency at all: item stats are supplied via a fake
 * {@link GearMapper.SlotStatsLookup} rather than a real {@code ItemManager}.
 */
public class GearMapperTest
{
	private static final int WEAPON_SLOT = 3; // net.runelite.api.EquipmentInventorySlot.WEAPON.ordinal()

	@Test
	public void buildEquipmentStats_sumsWornSlotsAndSkipsEmptyAndUnknown()
	{
		// Slot layout: [head, cape, amulet, weapon, body, shield, ...] (only head/weapon/shield populated here).
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);
		equippedItemIds[0] = 1001; // head: +5 astab, +2 prayer
		equippedItemIds[3] = 1003; // weapon: +10 aslash, +8 str, aspeed 4, not 2h
		equippedItemIds[5] = 1005; // shield: unresolvable (e.g. non-equipable/unknown) - must be skipped

		Map<Integer, GearMapper.SlotStats> table = Map.of(
			1001, new GearMapper.SlotStats(5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 2, 0, false),
			1003, new GearMapper.SlotStats(0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0.0, 0, 4, false));

		GearMapper.SlotStatsLookup lookup = table::get;

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, lookup);

		assertEquals(5, result.astab());
		assertEquals(10, result.aslash());
		assertEquals(8, result.str());
		assertEquals(2, result.prayer());
		assertEquals(4, result.weaponSpeedTicks());
		assertFalse(result.isTwoHanded());
	}

	@Test
	public void buildEquipmentStats_weaponSlotDrivesSpeedAndTwoHanded()
	{
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);
		equippedItemIds[WEAPON_SLOT] = 2001;

		GearMapper.SlotStatsLookup lookup = id -> id == 2001
			? new GearMapper.SlotStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0, 6, true)
			: null;

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, lookup);

		assertEquals(6, result.weaponSpeedTicks());
		assertTrue(result.isTwoHanded());
	}

	@Test
	public void buildEquipmentStats_emptyLoadoutUsesBuilderDefaults()
	{
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, id -> null);

		assertEquals(0, result.str());
		assertEquals(4, result.weaponSpeedTicks()); // EquipmentStats.Builder's default
		assertFalse(result.isTwoHanded());
	}

	@Test
	public void buildEquipmentStats_detectsSalveSlayerAndVoidVariantsFromWornSlots()
	{
		// Slot layout: 0=head, 2=amulet, 4=body, 7=legs, 9=gloves (see GearMapper's SLOT_* constants).
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);
		equippedItemIds[0] = 11665; // head: Void melee helm
		equippedItemIds[2] = 12017; // amulet: Salve amulet(i)
		equippedItemIds[4] = 8839;  // body: Void knight top
		equippedItemIds[7] = 8840;  // legs: Void knight robe
		equippedItemIds[9] = 8842;  // gloves: Void knight gloves

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, id -> null);

		assertEquals(SalveType.SALVE_I, result.salveType());
		assertEquals(SlayerHeadgear.NONE, result.slayerHeadgear()); // void melee helm isn't slayer headgear
		assertEquals(VoidSet.MELEE, result.voidSet());
	}

	@Test
	public void buildEquipmentStats_detectsChargedTomeInShieldSlotButNotEmpty()
	{
		int[] chargedFire = new int[14];
		java.util.Arrays.fill(chargedFire, -1);
		chargedFire[5] = 20714; // shield: charged Tome of fire
		assertEquals(com.ospulse.combat.Tome.FIRE,
			GearMapper.buildEquipmentStats(chargedFire, WEAPON_SLOT, id -> null).tome());

		int[] emptyFire = new int[14];
		java.util.Arrays.fill(emptyFire, -1);
		emptyFire[5] = 20716; // shield: empty Tome of fire -> no bonus
		assertEquals(com.ospulse.combat.Tome.NONE,
			GearMapper.buildEquipmentStats(emptyFire, WEAPON_SLOT, id -> null).tome());

		int[] chargedWater = new int[14];
		java.util.Arrays.fill(chargedWater, -1);
		chargedWater[5] = 25574; // shield: charged Tome of water
		assertEquals(com.ospulse.combat.Tome.WATER,
			GearMapper.buildEquipmentStats(chargedWater, WEAPON_SLOT, id -> null).tome());
	}

	@Test
	public void buildEquipmentStats_detectsImbuedSlayerHelmAndNoSalveOrVoid()
	{
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);
		equippedItemIds[0] = 11865; // head: Slayer helmet (i)

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, id -> null);

		assertEquals(SalveType.NONE, result.salveType());
		assertEquals(SlayerHeadgear.IMBUED, result.slayerHeadgear());
		assertEquals(VoidSet.NONE, result.voidSet());
	}

	@Test
	public void buildEquipmentStats_blowpipeIgnoresWornAmmoAndAddsConfiguredDartRstr()
	{
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);
		equippedItemIds[WEAPON_SLOT] = 12926; // Toxic blowpipe
		equippedItemIds[13] = 9999; // ammo slot: a high-rstr item that must be ignored

		Map<Integer, GearMapper.SlotStats> table = Map.of(
			12926, new GearMapper.SlotStats(0, 0, 0, 0, 30, 0, 0, 0, 0, 0, 0, 20, 0.0, 0, 3, true),
			9999, new GearMapper.SlotStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100, 0.0, 0, 0, false));

		GearMapper.SlotStatsLookup lookup = table::get;

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, lookup, 35);

		// 20 (blowpipe's own rstr) + 35 (configured dart) - NOT +100 from the worn ammo.
		assertEquals(55, result.rstr());
	}

	@Test
	public void buildEquipmentStats_nonBlowpipeWeaponStillSumsAmmoSlotNormally()
	{
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);
		equippedItemIds[WEAPON_SLOT] = 861; // some non-blowpipe weapon (magic shortbow), not indexed - resolves to null stats but not a blowpipe
		equippedItemIds[13] = 9999; // ammo slot: worn ammo must still count

		Map<Integer, GearMapper.SlotStats> table = Map.of(
			9999, new GearMapper.SlotStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100, 0.0, 0, 0, false));

		GearMapper.SlotStatsLookup lookup = table::get;

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, lookup, 35);

		assertEquals(100, result.rstr());
	}

	@Test
	public void buildEquipmentStats_threeArgOverloadDefaultsToDragonDartRstr()
	{
		int[] equippedItemIds = new int[14];
		java.util.Arrays.fill(equippedItemIds, -1);
		equippedItemIds[WEAPON_SLOT] = 12926; // Toxic blowpipe

		Map<Integer, GearMapper.SlotStats> table = Map.of(
			12926, new GearMapper.SlotStats(0, 0, 0, 0, 30, 0, 0, 0, 0, 0, 0, 20, 0.0, 0, 3, true));

		GearMapper.SlotStatsLookup lookup = table::get;

		EquipmentStats result = GearMapper.buildEquipmentStats(equippedItemIds, WEAPON_SLOT, lookup);

		// 20 (blowpipe) + 35 (default Dragon dart, no config supplied).
		assertEquals(55, result.rstr());
	}

	@Test
	public void toPlayerCombat_mapsLevelsPrayersStanceAndToggles()
	{
		GearSnapshot gear = GearSnapshot.builder()
			.attack(80, 85)
			.strength(85, 90)
			.defence(70, 70)
			.ranged(1, 1)
			.magic(1, 1)
			.prayer(52, 52)
			.hitpoints(90, 99)
			.activePrayers(EnumSet.of(OffensivePrayer.PIETY))
			.onSlayerTask(false) // onSlayerTask is now taken from the explicit toPlayerCombat() param, not the snapshot
			.build();

		PlayerCombat player = GearMapper.toPlayerCombat(gear, Stance.AGGRESSIVE, true, false, true);

		assertEquals(80, player.baseAttack());
		assertEquals(85, player.boostedAttack());
		assertEquals(85, player.baseStrength());
		assertEquals(90, player.boostedStrength());
		assertEquals(99, player.boostedHitpoints());
		assertTrue(player.activePrayers().contains(OffensivePrayer.PIETY));
		assertEquals(Stance.AGGRESSIVE, player.stance());
		assertTrue(player.assumeBestPotion());
		assertFalse(player.assumeBestPrayer());
		assertTrue(player.onSlayerTask());
	}
}
