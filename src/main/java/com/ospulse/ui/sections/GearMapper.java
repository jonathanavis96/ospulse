package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.Stance;
import com.ospulse.session.GearSnapshot;

/**
 * Pure mapping helpers between the plugin's own snapshot/session types and
 * the {@code com.ospulse.combat} engine's input types. Deliberately has no
 * RuneLite dependency so it is unit-testable without a running game client —
 * {@link GearSection} is the only caller, and it supplies a real
 * {@link SlotStatsLookup} backed by {@code ItemManager.getItemStats}.
 */
final class GearMapper
{
	// Slot ordinals mirror net.runelite.api.EquipmentInventorySlot (HEAD=0 .. AMMO=13).
	// Kept as literals since this class has zero RuneLite dependency (see class javadoc).
	private static final int SLOT_HEAD = 0;
	private static final int SLOT_AMULET = 2;
	private static final int SLOT_BODY = 4;
	private static final int SLOT_LEGS = 7;
	private static final int SLOT_GLOVES = 9;

	private GearMapper()
	{
	}

	/**
	 * One equipment slot's raw numeric bonuses, mirroring
	 * {@code net.runelite.client.game.ItemEquipmentStats}'s fields exactly (see
	 * the combat design spec's confirmed API), but with zero RuneLite
	 * dependency so tests can construct one directly.
	 */
	static final class SlotStats
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

		SlotStats(int astab, int aslash, int acrush, int amagic, int arange,
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
	interface SlotStatsLookup
	{
		SlotStats statsFor(int itemId);
	}

	/**
	 * Sums every worn slot's {@link SlotStats} into one loadout-wide
	 * {@link EquipmentStats}, skipping empty slots ({@code itemId <= 0}) and
	 * anything the lookup can't resolve (non-equipable/unknown items). The
	 * weapon slot's {@code aspeed}/{@code isTwoHanded} drive the loadout's
	 * attack speed and two-handed flag (every other slot's aspeed is normally
	 * 0 and would otherwise be nonsensical to sum).
	 */
	static EquipmentStats buildEquipmentStats(int[] equippedItemIds, int weaponSlotIndex, SlotStatsLookup lookup)
	{
		EquipmentStats.Builder builder = EquipmentStats.builder();
		for (int slot = 0; slot < equippedItemIds.length; slot++)
		{
			int itemId = equippedItemIds[slot];
			if (itemId <= 0)
			{
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

		builder.salveType(GearVariants.salveTypeFor(slotItemId(equippedItemIds, SLOT_AMULET)));
		builder.slayerHeadgear(GearVariants.slayerHeadgearFor(slotItemId(equippedItemIds, SLOT_HEAD)));
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
	 * false there — the real value comes from {@link GearSection}'s manual
	 * "On Slayer task" checkbox instead.
	 */
	static PlayerCombat toPlayerCombat(GearSnapshot gear, Stance stance, boolean assumeBestPotion, boolean assumeBestPrayer,
									boolean onSlayerTask)
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
			.build();
	}
}
