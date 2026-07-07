package com.ospulse.session;

import com.ospulse.combat.CombatIcons;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.Stance;

/**
 * Pure mapping helpers between the plugin's own snapshot/session types and
 * the {@code com.ospulse.combat} engine's input types. Deliberately has no
 * RuneLite dependency so it is unit-testable without a running game client —
 * {@code com.ospulse.integration.SessionTracker} is the only caller of
 * {@link #buildEquipmentStats}, resolving each slot's {@link SlotStats} via
 * {@code ItemManager.getItemStats} on the client thread (that call asserts
 * the client thread internally, so it must never happen on the EDT); {@code
 * com.ospulse.ui.sections.GearSection} only calls {@link #toPlayerCombat}.
 */
public final class GearMapper
{
	// Slot ordinals mirror net.runelite.api.EquipmentInventorySlot (HEAD=0 .. AMMO=13).
	// Kept as literals since this class has zero RuneLite dependency (see class javadoc).
	private static final int SLOT_HEAD = 0;
	private static final int SLOT_AMULET = 2;
	private static final int SLOT_BODY = 4;
	private static final int SLOT_SHIELD = 5;
	private static final int SLOT_LEGS = 7;
	private static final int SLOT_GLOVES = 9;
	private static final int AMMO_SLOT = 13;

	/**
	 * Ranged strength assumed for a blowpipe's internally-loaded dart when the
	 * caller doesn't supply one explicitly (the what-if/optimiser paths use
	 * the 3-arg {@link #buildEquipmentStats(int[], int, SlotStatsLookup)}
	 * overload) — Dragon dart, the best-in-slot choice, matching Gearscape's
	 * default assumption.
	 */
	private static final int DEFAULT_BLOWPIPE_DART_RANGED_STRENGTH = 35;

	private GearMapper()
	{
	}

	/**
	 * One equipment slot's raw numeric bonuses, mirroring
	 * {@code net.runelite.client.game.ItemEquipmentStats}'s fields exactly (see
	 * the combat design spec's confirmed API), but with zero RuneLite
	 * dependency so tests can construct one directly.
	 */
	public static final class SlotStats
	{
		final int astab;
		final int aslash;
		final int acrush;
		final int amagic;
		final int arange;
		final int dstab;
		final int dslash;
		final int dcrush;
		final int dmagic;
		final int drange;
		final int str;
		final int rstr;
		final double mdmg;
		final int prayer;
		final int aspeed;
		final boolean isTwoHanded;

		public SlotStats(int astab, int aslash, int acrush, int amagic, int arange,
				  int dstab, int dslash, int dcrush, int dmagic, int drange,
				  int str, int rstr, double mdmg, int prayer, int aspeed, boolean isTwoHanded)
		{
			this.astab = astab;
			this.aslash = aslash;
			this.acrush = acrush;
			this.amagic = amagic;
			this.arange = arange;
			this.dstab = dstab;
			this.dslash = dslash;
			this.dcrush = dcrush;
			this.dmagic = dmagic;
			this.drange = drange;
			this.str = str;
			this.rstr = rstr;
			this.mdmg = mdmg;
			this.prayer = prayer;
			this.aspeed = aspeed;
			this.isTwoHanded = isTwoHanded;
		}
	}

	/** Resolves an item id to its {@link SlotStats}, or {@code null} if empty/non-equipable/unknown. */
	@FunctionalInterface
	public interface SlotStatsLookup
	{
		SlotStats statsFor(int itemId);
	}

	/**
	 * As {@link #buildEquipmentStats(int[], int, SlotStatsLookup, int)}, using
	 * {@link #DEFAULT_BLOWPIPE_DART_RANGED_STRENGTH} (Dragon dart) as the
	 * assumed blowpipe dart. Used by the what-if/optimiser paths, which have
	 * no config to read a user-selected dart from.
	 */
	public static EquipmentStats buildEquipmentStats(int[] equippedItemIds, int weaponSlotIndex, SlotStatsLookup lookup)
	{
		return buildEquipmentStats(equippedItemIds, weaponSlotIndex, lookup, DEFAULT_BLOWPIPE_DART_RANGED_STRENGTH);
	}

	/**
	 * Sums every worn slot's {@link SlotStats} into one loadout-wide
	 * {@link EquipmentStats}, skipping empty slots ({@code itemId <= 0}) and
	 * anything the lookup can't resolve (non-equipable/unknown items). The
	 * weapon slot's {@code aspeed}/{@code isTwoHanded} drive the loadout's
	 * attack speed and two-handed flag (every other slot's aspeed is normally
	 * 0 and would otherwise be nonsensical to sum).
	 *
	 * <p>A blowpipe loads darts internally and ignores whatever is worn in the
	 * ammo slot: when the weapon is a blowpipe ({@link GearVariants#isBlowpipe}),
	 * the ammo slot is skipped entirely in the summation (worn ammo contributes
	 * nothing), and {@code blowpipeDartRangedStrength} is added on top instead
	 * (the blowpipe's own bonuses, including its own rstr, still come from its
	 * weapon-slot {@link SlotStats} as normal).
	 *
	 * <p>For every other weapon, worn CONSUMABLE ammo only counts when the
	 * weapon actually fires that ammo class (see
	 * {@link com.ospulse.combat.AmmoCompatibility#wornAmmoContributes}) — per
	 * the wiki's DPS rules, dragon javelins worn behind a shortbow add no
	 * ranged strength (they're ballista ammo the bow never fires), while a
	 * passive blessing always counts. This keeps the live readout, the
	 * what-if picker and the optimiser from crediting impossible
	 * weapon/ammo pairings.
	 */
	public static EquipmentStats buildEquipmentStats(int[] equippedItemIds, int weaponSlotIndex, SlotStatsLookup lookup,
									int blowpipeDartRangedStrength)
	{
		int weaponItemId = slotItemId(equippedItemIds, weaponSlotIndex);
		boolean weaponIsBlowpipe = GearVariants.isBlowpipe(weaponItemId);

		EquipmentStats.Builder builder = EquipmentStats.builder();
		for (int slot = 0; slot < equippedItemIds.length; slot++)
		{
			if (slot == AMMO_SLOT && weaponIsBlowpipe)
			{
				// Blowpipes ignore worn ammo entirely - the loaded dart is internal.
				continue;
			}
			int itemId = equippedItemIds[slot];
			if (itemId <= 0)
			{
				continue;
			}
			if (slot == AMMO_SLOT && !com.ospulse.combat.AmmoCompatibility.wornAmmoContributes(weaponItemId, itemId))
			{
				// Consumable ammo the weapon can't fire (e.g. javelins on a bow) contributes nothing.
				continue;
			}
			SlotStats s = lookup.statsFor(itemId);
			if (s == null)
			{
				continue;
			}
			builder.add(s.astab, s.aslash, s.acrush, s.amagic, s.arange,
				s.dstab, s.dslash, s.dcrush, s.dmagic, s.drange,
				s.str, s.rstr, s.mdmg, s.prayer);
			if (slot == weaponSlotIndex)
			{
				if (s.aspeed > 0)
				{
					builder.weaponSpeedTicks(s.aspeed);
				}
				builder.isTwoHanded(s.isTwoHanded);
			}
		}

		if (weaponIsBlowpipe)
		{
			builder.add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, blowpipeDartRangedStrength, 0.0, 0);
		}

		builder.salveType(GearVariants.salveTypeFor(slotItemId(equippedItemIds, SLOT_AMULET)));
		builder.slayerHeadgear(GearVariants.slayerHeadgearFor(slotItemId(equippedItemIds, SLOT_HEAD)));
		builder.demonbaneWeapon(GearVariants.demonbaneWeaponFor(weaponItemId));
		builder.dragonHunterWeapon(GearVariants.dragonHunterWeaponFor(weaponItemId));
		builder.twistedBow(GearVariants.isTwistedBow(weaponItemId));
		builder.osmumtensFang(GearVariants.isOsmumtensFang(weaponItemId));
		builder.poweredStaff(GearVariants.poweredStaffFor(weaponItemId));
		builder.tome(GearVariants.tomeFor(slotItemId(equippedItemIds, SLOT_SHIELD)));
		builder.voidSet(GearVariants.voidSetFor(
			slotItemId(equippedItemIds, SLOT_HEAD),
			slotItemId(equippedItemIds, SLOT_BODY),
			slotItemId(equippedItemIds, SLOT_LEGS),
			slotItemId(equippedItemIds, SLOT_GLOVES)));

		return builder.build();
	}

	/** {@code equippedItemIds[slot]}, or {@code -1} (empty) if {@code slot} is out of bounds. */
	private static int slotItemId(int[] equippedItemIds, int slot)
	{
		return slot >= 0 && slot < equippedItemIds.length ? equippedItemIds[slot] : -1;
	}

	/**
	 * Converts a {@link GearSnapshot} (levels + active prayers) into the
	 * engine's {@link PlayerCombat}, for the given {@link Stance} and
	 * simulation toggles ("now vs potted" / "best offensive prayer").
	 *
	 * <p>{@code onSlayerTask} is passed in explicitly rather than read from
	 * {@code gear.onSlayerTask()}: Phase 1 has no live client read for
	 * on-task status (see {@link GearSnapshot}'s javadoc), so it's always
	 * false there — the real value comes from {@code
	 * com.ospulse.ui.sections.GearSection}'s manual "On Slayer task" checkbox
	 * instead.
	 */
	public static PlayerCombat toPlayerCombat(GearSnapshot gear, Stance stance, boolean assumeBestPotion, boolean assumeBestPrayer,
									boolean onSlayerTask)
	{
		return toPlayerCombat(gear, stance, assumeBestPotion, assumeBestPrayer, onSlayerTask, null);
	}

	/**
	 * As {@link #toPlayerCombat(GearSnapshot, Stance, boolean, boolean, boolean)},
	 * additionally passing through {@code magicPotionVariant} — the potion-toggle
	 * right-click swap in {@code GearSection} ({@code null} keeps the default
	 * Imbued heart behaviour; see {@link PlayerCombat#magicPotionVariant()}).
	 */
	public static PlayerCombat toPlayerCombat(GearSnapshot gear, Stance stance, boolean assumeBestPotion, boolean assumeBestPrayer,
									boolean onSlayerTask, CombatIcons.BoostPotion magicPotionVariant)
	{
		return PlayerCombat.builder()
			.attack(gear.baseAttack(), gear.boostedAttack())
			.strength(gear.baseStrength(), gear.boostedStrength())
			.defence(gear.baseDefence(), gear.boostedDefence())
			.ranged(gear.baseRanged(), gear.boostedRanged())
			.magic(gear.baseMagic(), gear.boostedMagic())
			.prayer(gear.basePrayer(), gear.boostedPrayer())
			.hitpoints(gear.baseHitpoints(), gear.boostedHitpoints())
			.activePrayers(gear.activePrayers())
			.stance(stance)
			.assumeBestPotion(assumeBestPotion)
			.assumeBestPrayer(assumeBestPrayer)
			.onSlayerTask(onSlayerTask)
			.magicPotionVariant(magicPotionVariant)
			.build();
	}
}
