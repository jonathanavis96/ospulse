package com.ospulse.combat.optimizer;

import net.runelite.api.gameval.ItemID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Charge-variant families: groups every charge level of one chargeable item
 * (e.g. Amulet of glory (1) … (6), the uncharged amulet, and the infinite
 * Amulet of eternal glory) so the optimiser can treat the whole family as a
 * SINGLE item for ownership and recommendation — the equivalent of RuneLite's
 * Equipment Inventory Setups "fuzzy matching" toggle, enabled automatically.
 *
 * <h2>Why this can't come from the bundled index</h2>
 * The plugin's {@code equipment_index} deliberately flattens every glory charge
 * to the same display name ("Amulet of glory") with the same slot, and
 * {@code equipment_stats} gives every charge byte-identical combat bonuses — so
 * neither the family membership NOR the charge ORDER is recoverable from bundled
 * data alone (a low charge is indistinguishable from a full one there).
 *
 * <h2>Clean-room source of the ordering</h2>
 * Membership and order are taken from RuneLite's public
 * {@link net.runelite.api.gameval.ItemID} constants — an API the plugin already
 * depends on. Those constant NAMES are a mechanical transcription of the game's
 * own item names, so they carry the charge count the display names dropped:
 * {@code AMULET_OF_GLORY} (uncharged) &lt; {@code AMULET_OF_GLORY_1} &lt; … &lt;
 * {@code AMULET_OF_GLORY_6} &lt; {@code AMULET_OF_GLORY_INF} (infinite). Each
 * family below lists its members highest-charge FIRST, so the first owned member
 * walking the array is always the most-charged one.
 *
 * <p>The "higher suffix = more charges" convention is corroborated by two
 * independent signals so it is not an assumption: (1) the original glory ids
 * ascend with charge (1704 uncharged → 1712 the (4)); (2) the trimmed family's
 * ids <em>descend</em> with charge (10362 uncharged → 10354 the (4)), which
 * proves the item-id order is NOT the rank and the name suffix is — the two
 * families would disagree if we ranked by id.
 *
 * <h2>Scope: DPS-safe families only</h2>
 * Only families whose members share IDENTICAL combat bonuses are included, so
 * collapsing a family can never change a DPS result — the pick is purely about
 * which owned charge to surface. Both glory families satisfy this (verified
 * against bundled {@code equipment_stats}). Weapon charge families
 * (trident/sanguinesti/shadow/blowpipe) and stat-changing "variants" (serpentine
 * helm charged vs uncharged, ring of suffering imbue/recoil) are deliberately
 * EXCLUDED: their charged and uncharged forms have different combat stats, so
 * "treat as one item" would misstate achievable DPS. This class is a registry —
 * adding a family is one line once its members are known to be stat-identical.
 */
public final class ChargeFamilies
{
	/**
	 * Amulet of glory — every charge shares byte-identical combat bonuses; the
	 * charge count only governs teleports, so the highest-charge owned member is
	 * always the one to equip. Highest-charge first (infinite → 6 → … → uncharged).
	 */
	private static final int[] GLORY = {
		ItemID.AMULET_OF_GLORY_INF, // Amulet of eternal glory (infinite charges)
		ItemID.AMULET_OF_GLORY_6,
		ItemID.AMULET_OF_GLORY_5,
		ItemID.AMULET_OF_GLORY_4,
		ItemID.AMULET_OF_GLORY_3,
		ItemID.AMULET_OF_GLORY_2,
		ItemID.AMULET_OF_GLORY_1,
		ItemID.AMULET_OF_GLORY, // uncharged
	};

	/** Amulet of glory (t) — trimmed cosmetic twin of {@link #GLORY}; same stat-identity, its own family (distinct ids, no infinite variant). */
	private static final int[] GLORY_TRIMMED = {
		ItemID.TRAIL_AMULET_OF_GLORY_6,
		ItemID.TRAIL_AMULET_OF_GLORY_5,
		ItemID.TRAIL_AMULET_OF_GLORY_4,
		ItemID.TRAIL_AMULET_OF_GLORY_3,
		ItemID.TRAIL_AMULET_OF_GLORY_2,
		ItemID.TRAIL_AMULET_OF_GLORY_1,
		ItemID.TRAIL_AMULET_OF_GLORY, // uncharged (t)
	};

	private static final int[][] FAMILIES = { GLORY, GLORY_TRIMMED };

	/** member item id -> its family array (highest-charge first). */
	private static final Map<Integer, int[]> BY_MEMBER;

	static
	{
		Map<Integer, int[]> byMember = new HashMap<>();
		for (int[] family : FAMILIES)
		{
			for (int memberId : family)
			{
				byMember.put(memberId, family);
			}
		}
		BY_MEMBER = Collections.unmodifiableMap(byMember);
	}

	private ChargeFamilies()
	{
	}

	/** Test-only view of the raw family tables (each highest-charge first). Package-private. */
	static int[][] familiesForTest()
	{
		return FAMILIES;
	}

	/** Whether {@code itemId} is a charge-variant of a known family. */
	public static boolean isMember(int itemId)
	{
		return BY_MEMBER.containsKey(itemId);
	}

	/**
	 * The charge family {@code itemId} belongs to, highest-charge first, or
	 * {@code null} if {@code itemId} isn't a known charge-variant. The returned
	 * array is shared/immutable — treat it as read-only.
	 */
	public static int[] familyOf(int itemId)
	{
		return BY_MEMBER.get(itemId);
	}

	/**
	 * The highest-charge member of {@code itemId}'s family that the player OWNS
	 * and hasn't excluded, or {@code null} when {@code itemId} isn't a family
	 * member or no owned, non-excluded member exists. Because members are stored
	 * highest-charge first, this is the first array entry present in
	 * {@code owned} and absent from {@code excluded}.
	 *
	 * @param itemId   any member of the family to resolve within
	 * @param owned    item ids the player owns (never {@code null})
	 * @param excluded item ids the player excluded from suggestions, or {@code null}
	 */
	public static Integer bestOwnedMember(int itemId, Set<Integer> owned, Set<Integer> excluded)
	{
		int[] family = BY_MEMBER.get(itemId);
		if (family == null || owned == null)
		{
			return null;
		}
		for (int memberId : family)
		{
			if (owned.contains(memberId) && (excluded == null || !excluded.contains(memberId)))
			{
				return memberId;
			}
		}
		return null;
	}
}
