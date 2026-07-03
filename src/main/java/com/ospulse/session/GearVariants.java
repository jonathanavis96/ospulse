package com.ospulse.session;

import com.ospulse.combat.DemonbaneWeapon;
import com.ospulse.combat.SalveType;
import com.ospulse.combat.SlayerHeadgear;
import com.ospulse.combat.VoidSet;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure item-id -&gt; Tier-A gear-variant detection (Salve amulet variant,
 * Slayer headgear variant, full Void Knight set), with zero RuneLite
 * dependency so it is unit-testable without a running game client.
 * {@link GearMapper} is the only caller, feeding it slot item ids straight
 * from a {@code com.ospulse.session.GearSnapshot}.
 *
 * <p><b>Item-id source:</b> every id below was resolved against the
 * <i>live</i> OSRS item database — RuneLite's {@code
 * net.runelite.api.gameval.ItemID} (client 1.12.31.1 jar, extracted with
 * {@code javap}) cross-checked against the "Minimal OSRS Item DB" dataset at
 * {@code chisel.weirdgloop.org/moid/item_id.html} (a live dump of the
 * current game item cache: id/name/configName) — <b>not</b> taken from the
 * OSRS Wiki's infobox {@code |id=} values, several of which have drifted
 * from what the live client actually uses (e.g. the Wiki's "Salve amulet"
 * page still lists the pre-migration id 4081, which the current live game
 * has reassigned to an unrelated "Crystal shard necklace"-family object —
 * verified false by cross-checking the live item cache, where id 4081's
 * display name is still genuinely "Salve amulet"). A few of the ids used
 * here therefore have <i>misleading</i> {@code gameval.ItemID} constant
 * names (internal/legacy dev labels that were never renamed even though the
 * item's real in-game name/effect is something else entirely) — each such
 * case is called out in a comment below; the numeric id is what was
 * verified, not the constant name.
 */
final class GearVariants
{
	private GearVariants()
	{
	}

	// ==== Salve amulet ===================================================================

	/**
	 * Plain Salve amulet (Haunted Mine quest reward). {@code gameval.ItemID}
	 * names id 4081 {@code CRYSTALSHARD_NECKLACE} — a stale internal/dev label;
	 * the live item cache confirms id 4081 is genuinely "Salve amulet".
	 */
	private static final int SALVE_AMULET = 4081;

	/**
	 * Salve amulet (e). {@code gameval.ItemID} names id 10588 {@code
	 * LOTR_CRYSTALSHARD_NECKLACE_UPGRADE} (same stale-internal-name caveat as
	 * {@link #SALVE_AMULET}); the live item cache confirms it is "Salve amulet (e)".
	 */
	private static final int SALVE_AMULET_E = 10588;

	/**
	 * Salve amulet(i) — three id families exist for the identical item/effect,
	 * one per reward source: base-game imbue (gameval {@code NZONE_SALVE_AMULET}),
	 * Soul Wars zeal shop ({@code SW_SALVE_AMULET}), PvP Arena shop ({@code
	 * PVPA_SALVE_AMULET}).
	 */
	private static final Set<Integer> SALVE_AMULET_I = setOf(
		12017, // NZONE_SALVE_AMULET
		25250, // SW_SALVE_AMULET
		26763  // PVPA_SALVE_AMULET
	);

	/** Salve amulet(ei) — same three reward-source id families as {@link #SALVE_AMULET_I}. */
	private static final Set<Integer> SALVE_AMULET_EI = setOf(
		12018, // NZONE_SALVE_AMULET_E
		25278, // SW_SALVE_AMULET_E
		26782  // PVPA_SALVE_AMULET_E
	);

	/** Maps a worn AMULET-slot item id to the {@link SalveType} it grants ({@link SalveType#NONE} if not a Salve amulet). */
	static SalveType salveTypeFor(int amuletItemId)
	{
		if (amuletItemId == SALVE_AMULET)
		{
			return SalveType.SALVE;
		}
		if (amuletItemId == SALVE_AMULET_E)
		{
			return SalveType.SALVE_E;
		}
		if (SALVE_AMULET_I.contains(amuletItemId))
		{
			return SalveType.SALVE_I;
		}
		if (SALVE_AMULET_EI.contains(amuletItemId))
		{
			return SalveType.SALVE_EI;
		}
		return SalveType.NONE;
	}

	// ==== Slayer headgear (Black mask / Slayer helmet family) ============================

	/**
	 * Plain (non-imbued) Black mask, every charge level (0-10 charges; charge
	 * count doesn't change the combat bonus). {@code gameval.ItemID} names
	 * these {@code HARMLESS_BLACK_MASK*} — again a stale internal label; the
	 * live item cache confirms these ARE the real, bonus-granting Black mask
	 * (the actual cosmetic-only "Harmless black mask" is a different,
	 * unrelated item and is intentionally not modelled here since it grants
	 * no combat bonus).
	 */
	private static final Set<Integer> BLACK_MASK = setOf(
		8901, 8903, 8905, 8907, 8909, 8911, 8913, 8915, 8917, 8919, 8921);

	/**
	 * Imbued Black mask (i), every charge level, across all three reward-source
	 * id families: base-game imbue ({@code NZONE_BLACK_MASK*}), Soul Wars
	 * ({@code SW_BLACK_MASK*}), PvP Arena ({@code PVPA_BLACK_MASK*}).
	 */
	private static final Set<Integer> BLACK_MASK_I = setOf(
		11774, 11775, 11776, 11777, 11778, 11779, 11780, 11781, 11782, 11783, 11784, // NZONE_BLACK_MASK*
		25266, 25267, 25268, 25269, 25270, 25271, 25272, 25273, 25274, 25275, 25276, // SW_BLACK_MASK*
		26771, 26772, 26773, 26774, 26775, 26776, 26777, 26778, 26779, 26780, 26781  // PVPA_BLACK_MASK*
	);

	/**
	 * Plain (non-imbued) Slayer helmet variants: the base helm plus the common
	 * colour recolours (Black/Green/Red from Slayer master rewards, Purple/
	 * Turquoise from later reward updates) and the boss-kit recolours (Hydra,
	 * Twisted, Jad/"Tztok", Verzik/"Vampyric", Zuk/"Tzkal", Araxyte, Hooded).
	 * Deliberately omits the current Leagues-only cosmetic recolour (not
	 * wearable outside that league).
	 */
	private static final Set<Integer> SLAYER_HELM = setOf(
		11864, // SLAYER_HELM
		19639, // SLAYER_HELM_BLACK
		19643, // SLAYER_HELM_GREEN
		19647, // SLAYER_HELM_RED
		21264, // SLAYER_HELM_PURPLE
		21888, // SLAYER_HELM_TURQUOISE
		23073, // SLAYER_HELM_HYDRA
		24370, // SLAYER_HELM_TWISTED
		25898, // SLAYER_HELM_JAD ("Tztok slayer helmet")
		25904, // SLAYER_HELM_VERZIK ("Vampyric slayer helmet")
		25910, // SLAYER_HELM_ZUK ("Tzkal slayer helmet")
		29816, // SLAYER_HELM_ARAXYTE
		33066  // SLAYER_HELM_HOODED
	);

	/**
	 * Imbued counterparts of every id in {@link #SLAYER_HELM}, including the
	 * Soul Wars and PvP Arena reward-shop id families (those two shops only
	 * ever sell already-imbued helms, so there's no non-imbued equivalent for
	 * them).
	 */
	private static final Set<Integer> SLAYER_HELM_I = setOf(
		// base-game imbue
		11865, 19641, 19645, 19649, 21266, 21890, 23075, 24444, 25900, 25906, 25912, 29818, 33068,
		// Soul Wars zeal shop (SW_SLAYER_HELM_I*)
		25177, 25179, 25181, 25183, 25185, 25187, 25189, 25191, 25902, 25908, 25914, 29820, 33070,
		// PvP Arena shop (PVPA_SLAYER_HELM_I*)
		26674, 26675, 26676, 26677, 26678, 26679, 26680, 26681, 26682, 26683, 26684, 29822, 33072
	);

	/** Maps a worn HEAD-slot item id to the {@link SlayerHeadgear} it grants ({@link SlayerHeadgear#NONE} if neither). */
	static SlayerHeadgear slayerHeadgearFor(int headItemId)
	{
		if (BLACK_MASK.contains(headItemId) || SLAYER_HELM.contains(headItemId))
		{
			return SlayerHeadgear.STANDARD;
		}
		if (BLACK_MASK_I.contains(headItemId) || SLAYER_HELM_I.contains(headItemId))
		{
			return SlayerHeadgear.IMBUED;
		}
		return SlayerHeadgear.NONE;
	}

	// ==== Void Knight set =================================================================

	// Base ids are the Pest Control reward-shop items (gameval PEST_VOID_KNIGHT_*/
	// GAME_PEST_*_HELM); the second id in each pair is the Trailblazer League I
	// "(l)" cosmetic recolour, which carries over and remains wearable outside
	// the league (gameval *_TROUVER). Current league-exclusive "(or)" recolours
	// are NOT included since they can't be worn outside their league world.
	private static final Set<Integer> VOID_GLOVES = setOf(8842, 24182);
	private static final Set<Integer> VOID_TOP = setOf(8839, 24177);
	private static final Set<Integer> VOID_TOP_ELITE = setOf(13072, 24178);
	private static final Set<Integer> VOID_ROBE = setOf(8840, 24179);
	private static final Set<Integer> VOID_ROBE_ELITE = setOf(13073, 24180);
	private static final Set<Integer> VOID_HELM_MELEE = setOf(11665, 24185);
	private static final Set<Integer> VOID_HELM_RANGED = setOf(11664, 24184);
	private static final Set<Integer> VOID_HELM_MAGIC = setOf(11663, 24183);

	/**
	 * Detects the full Void Knight set from the HEAD/BODY/LEGS/GLOVES slot item
	 * ids. Per the OSRS Wiki's Void Knight equipment page: the base set bonus
	 * requires gloves + (top OR elite top) + (robe OR elite robe) + one of the
	 * three style helms, all worn together — mixed tiers (e.g. elite top with
	 * a normal robe) still grant the base bonus. The additional Elite bonus
	 * (ranged/magic only — melee has no elite-specific bonus in OSRS, matching
	 * {@link VoidSet} having no {@code MELEE_ELITE} constant) requires BOTH the
	 * elite top AND elite robe worn together.
	 *
	 * @return {@link VoidSet#NONE} if any required piece is missing or no style helm is worn.
	 */
	static VoidSet voidSetFor(int headItemId, int bodyItemId, int legsItemId, int glovesItemId)
	{
		boolean glovesWorn = VOID_GLOVES.contains(glovesItemId);
		boolean topWorn = VOID_TOP.contains(bodyItemId) || VOID_TOP_ELITE.contains(bodyItemId);
		boolean robeWorn = VOID_ROBE.contains(legsItemId) || VOID_ROBE_ELITE.contains(legsItemId);
		if (!glovesWorn || !topWorn || !robeWorn)
		{
			return VoidSet.NONE;
		}

		boolean elite = VOID_TOP_ELITE.contains(bodyItemId) && VOID_ROBE_ELITE.contains(legsItemId);

		if (VOID_HELM_MELEE.contains(headItemId))
		{
			return VoidSet.MELEE;
		}
		if (VOID_HELM_RANGED.contains(headItemId))
		{
			return elite ? VoidSet.RANGED_ELITE : VoidSet.RANGED;
		}
		if (VOID_HELM_MAGIC.contains(headItemId))
		{
			return elite ? VoidSet.MAGIC_ELITE : VoidSet.MAGIC;
		}
		return VoidSet.NONE;
	}

	// ==== Demonbane weapons (vs-demon accuracy/damage) ===================================

	/**
	 * Emberlight — the Arclight upgrade; +70% accuracy AND damage vs demons.
	 * Live id 29589 verified against the OSRS Wiki infobox (client-current).
	 *
	 * <p>TODO: extend to the rest of the demonbane line once each live id (and,
	 * for the ranged weapons, its exact vs-demon %) is cross-checked the same
	 * way the salve/slayer ids above were — Arclight, Darklight, Silverlight
	 * (melee sword line; {@link DemonbaneWeapon} constants already defined),
	 * Burning claws (melee), and Scorching bow (ranged — needs the ranged
	 * application path in {@code DpsCalculator} too).
	 */
	private static final int EMBERLIGHT = 29589;

	/** Maps a worn WEAPON-slot item id to the {@link DemonbaneWeapon} it is ({@link DemonbaneWeapon#NONE} if not demonbane). */
	static DemonbaneWeapon demonbaneWeaponFor(int weaponItemId)
	{
		if (weaponItemId == EMBERLIGHT)
		{
			return DemonbaneWeapon.EMBERLIGHT;
		}
		return DemonbaneWeapon.NONE;
	}

	private static Set<Integer> setOf(int... ids)
	{
		Set<Integer> set = new HashSet<>(ids.length * 2);
		for (int id : ids)
		{
			set.add(id);
		}
		return set;
	}
}
