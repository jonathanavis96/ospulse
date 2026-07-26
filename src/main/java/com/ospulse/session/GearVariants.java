package com.ospulse.session;

import com.ospulse.combat.DemonbaneWeapon;
import com.ospulse.combat.DragonHunterWeapon;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.KerisPartisan;
import com.ospulse.combat.PoweredStaff;
import com.ospulse.combat.RevenantWeapon;
import com.ospulse.combat.SalveType;
import com.ospulse.combat.SlayerHeadgear;
import com.ospulse.combat.Tome;
import com.ospulse.combat.VoidSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
public final class GearVariants
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
	 * <p>TODO: dyed Silverlight variants are still unwired.
	 */
	private static final int EMBERLIGHT = 29589;

	/**
	 * Burning claws — melee (slash) demonbane weapon; +5% accuracy AND damage
	 * vs demons. Id 29577, cross-checked against the bundled cache-derived
	 * {@code equipment_stats.min.json} (29577 = astab +43 / aslash +54 / str
	 * +32 / 4-tick / weapon slot), which exactly matches the OSRS Wiki's
	 * published stats for Burning claws.
	 */
	private static final int BURNING_CLAWS = 29577;

	/**
	 * Rest of the melee sword line. Ids are the OSRS Wiki infobox values for
	 * these long-stable pre-migration items (Silverlight 2402, Darklight 6746,
	 * Arclight 19675) — same live-cache cross-check caveat as the class
	 * javadoc.
	 */
	private static final int SILVERLIGHT = 2402;
	private static final int DARKLIGHT = 6746;
	private static final int ARCLIGHT = 19675;

	/**
	 * Scorching bow — the RANGED demonbane weapon; +30% accuracy AND damage vs
	 * demons, the damage side stacking ADDITIVELY with the slayer helm (i)
	 * (30+15=45% — handled by {@code DpsCalculator}'s ranged fold). Id 29591
	 * from the OSRS Wiki infobox, cross-checked against the bundled live-cache
	 * {@code equipment_stats.min.json} (29591 = arange +124 / rstr +40 /
	 * 5-tick, exactly the bow's published stats).
	 */
	private static final int SCORCHING_BOW = 29591;

	/** Maps a worn WEAPON-slot item id to the {@link DemonbaneWeapon} it is ({@link DemonbaneWeapon#NONE} if not demonbane). */
	public static DemonbaneWeapon demonbaneWeaponFor(int weaponItemId)
	{
		switch (weaponItemId)
		{
			case EMBERLIGHT:
				return DemonbaneWeapon.EMBERLIGHT;
			case ARCLIGHT:
				return DemonbaneWeapon.ARCLIGHT;
			case DARKLIGHT:
				return DemonbaneWeapon.DARKLIGHT;
			case SILVERLIGHT:
				return DemonbaneWeapon.SILVERLIGHT;
			case SCORCHING_BOW:
				return DemonbaneWeapon.SCORCHING_BOW;
			case BURNING_CLAWS:
				return DemonbaneWeapon.BURNING_CLAWS;
			default:
				return DemonbaneWeapon.NONE;
		}
	}

	// ==== Dragon Hunter (dragonbane) weapons + Twisted bow ================================

	/**
	 * Dragon hunter crossbow 21012, Dragon hunter lance 22978, and Dragon
	 * hunter wand 30070, all verified against the OSRS Wiki infobox
	 * 2026-07-03 (dragon-hunter crossbow (b)/(t) cosmetic variants are TODO).
	 * Twisted bow 20997.
	 */
	private static final int DRAGON_HUNTER_CROSSBOW = 21012;
	private static final int DRAGON_HUNTER_LANCE = 22978;
	private static final int DRAGON_HUNTER_WAND = 30070;
	private static final int TWISTED_BOW = 20997;

	/**
	 * Osmumten's fang 26219, the "(or)" cosmetic variant 27246, and the
	 * Fang of the hound re-skin 33249 — all identical mechanically, verified
	 * against the OSRS Wiki 2026-07-04.
	 */
	private static final int OSMUMTENS_FANG = 26219;
	private static final int OSMUMTENS_FANG_OR = 27246;
	private static final int FANG_OF_THE_HOUND = 33249;

	/**
	 * CHARGED elemental tome shield-slot ids (the empty variants +2 give no
	 * bonus and are deliberately excluded): Tome of fire 20714 (empty 20716),
	 * Tome of water 25574 (empty 25576), Tome of earth 30064 (empty 30066).
	 * Verified vs the OSRS Wiki + the bundled equipment_index 2026-07-04.
	 */
	private static final int TOME_OF_FIRE = 20714;
	private static final int TOME_OF_WATER = 25574;
	private static final int TOME_OF_EARTH = 30064;

	/** Maps a worn WEAPON-slot item id to its {@link DragonHunterWeapon} ({@link DragonHunterWeapon#NONE} if not dragonbane). */
	public static DragonHunterWeapon dragonHunterWeaponFor(int weaponItemId)
	{
		switch (weaponItemId)
		{
			case DRAGON_HUNTER_LANCE:
				return DragonHunterWeapon.LANCE;
			case DRAGON_HUNTER_CROSSBOW:
				return DragonHunterWeapon.CROSSBOW;
			case DRAGON_HUNTER_WAND:
				return DragonHunterWeapon.WAND;
			default:
				return DragonHunterWeapon.NONE;
		}
	}

	/** True when the worn weapon is the Twisted bow. */
	static boolean isTwistedBow(int weaponItemId)
	{
		return weaponItemId == TWISTED_BOW;
	}

	/** True when the worn weapon is Osmumten's fang (either cosmetic variant, or the Fang of the hound re-skin). */
	static boolean isOsmumtensFang(int weaponItemId)
	{
		return weaponItemId == OSMUMTENS_FANG || weaponItemId == OSMUMTENS_FANG_OR || weaponItemId == FANG_OF_THE_HOUND;
	}

	/** Maps a worn SHIELD-slot item id to its charged {@link Tome} ({@link Tome#NONE} if empty/not a tome). */
	static Tome tomeFor(int shieldItemId)
	{
		switch (shieldItemId)
		{
			case TOME_OF_FIRE:
				return Tome.FIRE;
			case TOME_OF_WATER:
				return Tome.WATER;
			case TOME_OF_EARTH:
				return Tome.EARTH;
			default:
				return Tome.NONE;
		}
	}

	// ==== Powered staves ==================================================================

	/**
	 * Powered staves whose built-in spell scales with Magic level (see
	 * {@link PoweredStaff}). Charged-variant ids from the OSRS Wiki infobox
	 * (same cross-check caveat): Trident of the seas 11905 (full)/11907,
	 * Trident of the swamp 12899, Sanguinesti staff 22323 (+ Holy 25731),
	 * Tumeken's shadow 27275. Uncharged variants can't attack and are
	 * deliberately excluded; the enhanced "(e)" tridents and Accursed/Warped
	 * sceptres are TODO pending id + formula verification.
	 */
	static PoweredStaff poweredStaffFor(int weaponItemId)
	{
		switch (weaponItemId)
		{
			case 11905:
			case 11907:
				return PoweredStaff.TRIDENT_OF_THE_SEAS;
			case 12899:
				return PoweredStaff.TRIDENT_OF_THE_SWAMP;
			case 22323:
			case 25731:
				return PoweredStaff.SANGUINESTI_STAFF;
			case 27275:
				return PoweredStaff.TUMEKENS_SHADOW;
			default:
				return PoweredStaff.NONE;
		}
	}

	// ==== Magic cast-speed override weapons ==============================================

	/**
	 * Twinflame staff. Bundled {@code weapon_categories} says {@code staff};
	 * bundled {@code equipment_stats} index 14 = 6 ticks. The OSRS Wiki's
	 * combat-styles table lists "Spell (Autocast) = 6 ticks" too — i.e. the
	 * 6-tick speed applies to spellcasting, not only melee — and the staff
	 * also fires a second hit on eligible elemental spells (see {@link
	 * com.ospulse.combat.Spell#twinflameEligible()} /
	 * {@link com.ospulse.combat.TwinflameSecondHit}).
	 *
	 * <p>NOT modelled here (explicitly out of scope): the wiki also documents
	 * that the staff auto-substitutes the elemental spell matching an NPC's
	 * weakness when autocasting (e.g. autocasting Wind Wave at a moss giant
	 * actually casts Fire Wave) — that changes WHICH spell is cast and
	 * interacts with the optimizer's spell selection, so it is a separate
	 * follow-up, not part of the cast-speed/second-hit wiring here.
	 */
	private static final int TWINFLAME_STAFF = 30634;

	/**
	 * Harmonised nightmare staff. Bundled {@code equipment_stats} index 14 = 5
	 * ticks (the base/manual-cast speed — the wiki-documented 4-tick discount
	 * is a conditional runtime effect that never appears in the bundled
	 * cache data). Per the OSRS Wiki: "reduces the cast time from 5 (3.0s) to
	 * 4 (2.4s) ticks ... The 4-tick spell speed only applies when
	 * autocasting"; "Manually casting spells with the staff equipped will
	 * result in a 5-tick attack speed"; and it "can autocast offensive
	 * standard spells, but cannot autocast any other spells (including
	 * Ancient Magicks and the Arceuus spellbook)" — see {@code
	 * com.ospulse.combat.MagicCastSpeed}.
	 */
	private static final int HARMONISED_NIGHTMARE_STAFF = 24423;

	/** True when the worn weapon is the Twinflame staff. */
	static boolean isTwinflameStaff(int weaponItemId)
	{
		return weaponItemId == TWINFLAME_STAFF;
	}

	/** True when the worn weapon is the Harmonised nightmare staff. */
	static boolean isHarmonisedNightmareStaff(int weaponItemId)
	{
		return weaponItemId == HARMONISED_NIGHTMARE_STAFF;
	}

	// ==== Tonalztics of Ralos (charged dual-hit passive) ==================================

	/**
	 * Tonalztics of Ralos, UNCHARGED — no combat passive; ids from the
	 * bundled {@code equipment_index.min.json} 2026-07-26 (both ids share the
	 * display name "Tonalztics of ralos"; the charged/uncharged distinction
	 * is not in the name, only in which id the game hands out after charging
	 * with Ralos's blessing).
	 */
	private static final int TONALZTICS_OF_RALOS_UNCHARGED = 28919;

	/**
	 * Tonalztics of Ralos, CHARGED — fires two full, independent damage
	 * rolls per attack (neither halved), per the OSRS Wiki: "the weapon will
	 * hit twice, with two independent damage rolls". Only this charged id
	 * carries the passive; the uncharged variant behaves as an ordinary
	 * single-hit ranged weapon and is deliberately excluded below.
	 */
	private static final int TONALZTICS_OF_RALOS_CHARGED = 28922;

	/** True when the worn weapon is the CHARGED Tonalztics of Ralos (its dual-hit passive applies). */
	static boolean isTonalzticsOfRalosCharged(int weaponItemId)
	{
		return weaponItemId == TONALZTICS_OF_RALOS_CHARGED;
	}

	// ==== Scythe of Vitur family (target-size-scaled multi-hit cascade) ==================

	/**
	 * Scythe of Vitur, Holy scythe of vitur, and Sanguine scythe of vitur,
	 * each with two ids sharing the same display name in the bundled {@code
	 * equipment_index.min.json} 2026-07-26 (an uncharged/dyed-cosmetic pair
	 * per variant, both fully functional): Scythe of vitur (22325, 22486),
	 * Holy scythe of vitur (25736, 25738), Sanguine scythe of vitur
	 * (25739, 25741). All six ids carry the identical size-scaled cascade
	 * passive (the Holy/Sanguine reskins only differ cosmetically/in
	 * blood-heal flavour, not in the damage mechanic modelled here).
	 */
	private static final Set<Integer> SCYTHE_OF_VITUR = setOf(
		22325, 22486, // Scythe of vitur
		25736, 25738, // Holy scythe of vitur
		25739, 25741  // Sanguine scythe of vitur
	);

	/** True when the worn weapon is any Scythe of Vitur variant (its size-scaled multi-hit cascade applies). */
	static boolean isScytheOfVitur(int weaponItemId)
	{
		return SCYTHE_OF_VITUR.contains(weaponItemId);
	}

	// ==== Colossal blade (flat target-size max-hit bonus) =================================

	/** Colossal blade — id 27021, verified against the bundled equipment_index.min.json 2026-07-26. */
	private static final int COLOSSAL_BLADE = 27021;

	/** True when the worn weapon is the Colossal blade (its flat +2*min(size,5) max-hit bonus applies). */
	static boolean isColossalBlade(int weaponItemId)
	{
		return weaponItemId == COLOSSAL_BLADE;
	}

	// ==== Keris partisan family (vs-Kalphite/Scarabite damage + triple-roll) ==============

	/**
	 * The five Keris partisan family ids, verified against the bundled
	 * {@code equipment_index.min.json} 2026-07-26: Keris partisan (25979),
	 * Keris partisan of amascut (30891, Tombs of Amascut reward), Keris
	 * partisan of breaching (25981 — the only variant with the additional
	 * vs-Kalphite accuracy bonus), Keris partisan of corruption (27287),
	 * Keris partisan of the sun (27291).
	 */
	private static final Map<Integer, KerisPartisan> KERIS_PARTISAN_IDS = new HashMap<>();
	static
	{
		KERIS_PARTISAN_IDS.put(25979, KerisPartisan.PARTISAN);
		KERIS_PARTISAN_IDS.put(30891, KerisPartisan.OF_AMASCUT);
		KERIS_PARTISAN_IDS.put(25981, KerisPartisan.OF_BREACHING);
		KERIS_PARTISAN_IDS.put(27287, KerisPartisan.OF_CORRUPTION);
		KERIS_PARTISAN_IDS.put(27291, KerisPartisan.OF_THE_SUN);
	}

	/** Maps a worn WEAPON-slot item id to its {@link KerisPartisan} variant ({@link KerisPartisan#NONE} if not a Keris). */
	public static KerisPartisan kerisPartisanFor(int weaponItemId)
	{
		return KERIS_PARTISAN_IDS.getOrDefault(weaponItemId, KerisPartisan.NONE);
	}

	// ==== Revenant weapons (Wilderness-only +50% accuracy/damage) ========================

	/**
	 * Craw's bow (22547 base / 22550 — same display name, both fully
	 * statted per the bundled {@code equipment_stats.min.json}), Viggora's
	 * chainmace (22542 / 22545), Thammaron's sceptre (22552 / 22555) and its
	 * "(a)" ether-enhanced reskin (27785 / 27788) — all verified against the
	 * bundled {@code equipment_index.min.json} 2026-07-26.
	 */
	private static final Map<Integer, RevenantWeapon> REVENANT_WEAPON_IDS = new HashMap<>();
	static
	{
		REVENANT_WEAPON_IDS.put(22547, RevenantWeapon.CRAWS_BOW);
		REVENANT_WEAPON_IDS.put(22550, RevenantWeapon.CRAWS_BOW);
		REVENANT_WEAPON_IDS.put(22542, RevenantWeapon.VIGGORAS_CHAINMACE);
		REVENANT_WEAPON_IDS.put(22545, RevenantWeapon.VIGGORAS_CHAINMACE);
		REVENANT_WEAPON_IDS.put(22552, RevenantWeapon.THAMMARONS_SCEPTRE);
		REVENANT_WEAPON_IDS.put(22555, RevenantWeapon.THAMMARONS_SCEPTRE);
		REVENANT_WEAPON_IDS.put(27785, RevenantWeapon.THAMMARONS_SCEPTRE);
		REVENANT_WEAPON_IDS.put(27788, RevenantWeapon.THAMMARONS_SCEPTRE);
	}

	/** Maps a worn WEAPON-slot item id to its {@link RevenantWeapon} ({@link RevenantWeapon#NONE} if not one). */
	public static RevenantWeapon revenantWeaponFor(int weaponItemId)
	{
		return REVENANT_WEAPON_IDS.getOrDefault(weaponItemId, RevenantWeapon.NONE);
	}

	// ==== Crystal armour set + crystal bow / Bow of Faerdhinen set effect =================

	/**
	 * ACTIVE (charged, fully-statted) Crystal helm ids only — verified
	 * against the bundled {@code equipment_stats.min.json} 2026-07-26 by
	 * checking every combat-bonus field is nonzero: basic/attuned/perfected
	 * (23886-23888), the pre-rework single "Crystal helm" (23971), the
	 * "beta" leftover (25495), and each of the seven Elf clan cosmetic
	 * recolours that got an armour reskin — Hefin/Ithell/Iorwerth/
	 * Trahaearn/Cadarn/Crwys/Amlodd (27705/27717/27729/27741/27753/27765/
	 * 27777) plus the deadman-mode cosmetic (33031). Meilyr has NO armour
	 * recolour in the bundled data (only a weapon one) — verified, not
	 * assumed. The INACTIVE (uncharged/broken, all-zero-stat) counterpart of
	 * every one of these — a SEPARATE id sharing the identical display name
	 * — is deliberately EXCLUDED: crediting a zero-stat inactive piece with
	 * the active set bonus is exactly the prior P1-class defect the design
	 * spec warns about for this mechanic.
	 */
	private static final Set<Integer> ACTIVE_CRYSTAL_HELM = setOf(
		23886, 23887, 23888, 23971, 25495,
		27705, 27717, 27729, 27741, 27753, 27765, 27777, 33031
	);

	/** ACTIVE Crystal body ids — same provenance/exclusion note as {@link #ACTIVE_CRYSTAL_HELM}. */
	private static final Set<Integer> ACTIVE_CRYSTAL_BODY = setOf(
		23889, 23890, 23891, 23975, 25496,
		27697, 27709, 27721, 27733, 27745, 27757, 27769, 33023
	);

	/** ACTIVE Crystal legs ids — same provenance/exclusion note as {@link #ACTIVE_CRYSTAL_HELM}. */
	private static final Set<Integer> ACTIVE_CRYSTAL_LEGS = setOf(
		23892, 23893, 23894, 23979, 25497,
		27701, 27713, 27725, 27737, 27749, 27761, 27773, 33027
	);

	/**
	 * ACTIVE (charged) Crystal bow AND Bow of Faerdhinen ids — verified the
	 * same way as the armour pieces: basic/attuned/perfected Crystal bow
	 * (23901-23903), the pre-rework single "Crystal bow" (23983), charged
	 * Bow of Faerdhinen (25865) and every one of its "(c)" cosmetic
	 * recolours (25867 plain, 25884 Ithell, 25886 Iorwerth, 25888 Trahaearn,
	 * 25890 Cadarn, 25892 Crwys, 25894 Meilyr, 25896 Amlodd, 33021 deadman).
	 * The UNCHARGED Bow of Faerdhinen (25862, all-zero stats) is deliberately
	 * EXCLUDED — same "don't credit the inactive piece" reasoning.
	 */
	private static final Set<Integer> ACTIVE_CRYSTAL_BOW = setOf(
		23901, 23902, 23903, 23983,
		25865, 25867, 25884, 25886, 25888, 25890, 25892, 25894, 25896, 33021
	);

	/**
	 * True when the HEAD/BODY/LEGS slots all carry an ACTIVE Crystal armour
	 * piece — the "full armour set" half of the §9f condition; see {@link
	 * #isActiveCrystalBowOrFaerdhinen} for the weapon half. Both must hold
	 * for the set's +15% damage/+30% accuracy bonus to apply (see {@code
	 * DpsCalculator#computeRanged}).
	 *
	 * <p><b>Modelled as all-or-nothing</b>, mirroring {@link VoidSet}'s own
	 * all-or-nothing precedent — the OSRS Wiki documents the 30%/15% total
	 * as a sum of PER-PIECE contributions (helm 5%/2.5%, body 15%/7.5%, legs
	 * 10%/5%), so a partial set (e.g. body+legs but no helm) technically
	 * grants a partial bonus in-game. That per-piece partial credit is NOT
	 * modelled here — only the full-set case the design spec explicitly
	 * calls out ("Full set: +15% damage/+30% accuracy") — a disclosed,
	 * deliberate simplification, not an oversight.
	 */
	public static boolean isActiveCrystalArmourSet(int headItemId, int bodyItemId, int legsItemId)
	{
		return ACTIVE_CRYSTAL_HELM.contains(headItemId)
			&& ACTIVE_CRYSTAL_BODY.contains(bodyItemId)
			&& ACTIVE_CRYSTAL_LEGS.contains(legsItemId);
	}

	/** True when the worn weapon is an ACTIVE Crystal bow or Bow of Faerdhinen variant. */
	public static boolean isActiveCrystalBowOrFaerdhinen(int weaponItemId)
	{
		return ACTIVE_CRYSTAL_BOW.contains(weaponItemId);
	}

	// ==== Blowpipe (loads darts internally, ignores worn ammo) ============================

	/**
	 * True when {@code weaponItemId} is any blowpipe variant (Toxic/Blazing/
	 * Drygore/Camphor/Ironwood/Rosewood blowpipe, including future variants).
	 * Resolved via the bundled {@link EquipmentIndexRepository} display name
	 * rather than a hard-coded id list, since every current and future
	 * blowpipe variant's name contains "blowpipe" (verified against the
	 * bundled equipment_index.min.json 2026-07-04: Toxic/Blazing/Drygore/
	 * Camphor/Ironwood/Rosewood blowpipe, each with 1-2 ids per variant).
	 */
	public static boolean isBlowpipe(int weaponItemId)
	{
		EquipmentIndexRepository.Entry entry = EquipmentIndexRepository.getInstance().entryFor(weaponItemId);
		return entry != null && entry.name().toLowerCase(Locale.ROOT).contains("blowpipe");
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
