package com.ospulse.ui.sections.gear;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentRequirementsRepository;
import com.ospulse.combat.EquipmentStatsRepository;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fortified/imbued cosmetic-or-charged variant suffixes (e.g. Masori
 * armour's " (f)" fortify, a ring's " (i)" imbue) and the two-way mapping
 * between a variant's name and its plain base form that {@link
 * com.ospulse.ui.sections.GearSection}'s ownership/recommendation logic
 * needs:
 * <ul>
 *   <li><b>ownership</b> — owning the VARIANT also counts as owning the
 *   plain base form, so the optimiser doesn't suggest "upgrading" to an item
 *   the player already effectively has (see {@link #plainFormId}, used by
 *   {@code GearSection#addVariantPlainForm});</li>
 *   <li><b>display</b> — when a recommendation resolves to the plain base
 *   form because that's the id the ownership check above marked owned, the
 *   UI should still show the actual OWNED variant's name/icon, not the
 *   plain one — {@link #preferOwnedVariant} is the reverse lookup for
 *   that.</li>
 * </ul>
 *
 * <p><b>Name collisions carry two DIFFERENT relationships, not one</b> (P1
 * fix, issue #11 Stage 3 follow-up): several display names are shared by
 * more than one backing id ({@link EquipmentIndexRepository#idsForName}),
 * and this class must not treat every such group the same way.
 * <ul>
 *   <li><i>Reward-source/charge duplicates</i> (an imbued ring's three
 *   quest-source copies, Black mask's 0-10 charge levels, the two Imbued god
 *   cape ids per god): every member shares IDENTICAL combat stats AND
 *   IDENTICAL equip requirements — picking any one of them is always safe,
 *   so the historical "first in file order" behaviour is kept for these.</li>
 *   <li><i>Genuinely different items sharing a name</i> — Deadman-mode
 *   crystal helm/body/legs each have an ACTIVE id (real combat stats) and an
 *   INACTIVE id (all-zero stats, cosmetically deactivated) that share the
 *   SAME plain "Crystal body"/"Crystal helm"/"Crystal legs" name. Picking
 *   the first file-order id here is wrong: owning the zero-stat inactive
 *   deadman piece must never credit the fully-statted active plain id (the
 *   optimiser would then compute and recommend DPS the player cannot
 *   actually achieve), and the reverse display lookup must never show the
 *   zero-stat inactive icon for a result actually calculated off the active
 *   id's real stats.</li>
 *   <li><i>Combat-stat-identical but requirement-different</i> — a subtler
 *   variant of the above: Deadman-mode Imbued god capes and the Dark bow's
 *   several reward duplicates are combat-stat-IDENTICAL across every field
 *   {@link EquipmentStatsRepository} models, yet differ on recorded equip
 *   requirement (e.g. two "Imbued saradomin cape" ids, one requiring magic
 *   75 and one with no recorded requirement — {@code
 *   equipment_requirements.min.json}). Stats alone made these look like safe
 *   "any pick is fine" duplicates; picking file order here would silently
 *   drop the level gate and recommend/credit gear the player cannot actually
 *   equip. Requirements are therefore checked as a second, independent
 *   dimension alongside stats — see {@link #isHomogeneous}.</li>
 * </ul>
 * Both {@link #plainFormId} and {@link #preferOwnedVariant} now detect which
 * case applies via {@link #resolveByStatMatch} / {@link #isHomogeneous}: a
 * name-sharing group is resolved by position ONLY when every member is
 * BOTH stat-identical and requirement-identical (safe by construction);
 * otherwise the specific member whose stats AND requirements match the item
 * on the OTHER side of the mapping is required, and {@code null}/the
 * original id (no credit, no substitution) is returned when no exact match
 * exists — over-crediting ownership the player cannot actually reach, or
 * bypassing a level gate, is the real harm, and a missing credit is visibly
 * absent while a wrong one looks modelled.
 */
public final class OwnedVariantResolver
{
	/**
	 * Space-prefixed variant suffixes, as they appear at the end of an
	 * {@link EquipmentIndexRepository.Entry#name()}. " (deadman)" covers
	 * Deadman Mode reward duplicates (e.g. "Imbued saradomin cape
	 * (deadman)") — most are stat-identical to a real, non-mode-locked
	 * counterpart item, but Deadman crystal armour's active/inactive split
	 * is NOT (see the class javadoc) — {@code
	 * GearSection.restrictedItemIds()} always excludes the "(deadman)" id
	 * itself from optimiser candidates (by design, regardless of
	 * ownership), so without this suffix, owning one never credits the
	 * player with owning its real, recommendable counterpart, and the
	 * optimiser falls back to a worse candidate.
	 */
	public static final String[] SUFFIXES = { " (f)", " (i)", " (deadman)" };

	/**
	 * Curated alias for a suffix-stripped plain name that has NO indexed entry
	 * at all under its own name, but still has a stat-matching counterpart
	 * under a genuinely DIFFERENT display name (every other suffix case —
	 * see {@link #SUFFIXES} — already lands on the right name once stripped,
	 * so this table is the exception, not the rule).
	 *
	 * <p><b>"Toxic staff (deadman)"</b> (ids 33035/33036, {@code
	 * net.runelite.api.gameval.ItemID.TOXIC_SOTD_DEADMAN}/{@code
	 * _CHARGED_DEADMAN}) strips to "Toxic staff", which isn't indexed under
	 * any id in the bundled {@code equipment_index.min.json} — the real item
	 * is named "Toxic staff of the dead" (12902 uncharged/12904 charged,
	 * {@code ItemID.TOXIC_SOTD}/{@code _CHARGED}), whose two ids are NOT
	 * stat-identical (uncharged amagic +17 vs. charged amagic +25 in {@code
	 * equipment_stats.min.json}) but exactly stat-match 33035/33036
	 * respectively — the same heterogeneous-name-collision shape as Deadman
	 * crystal armour, just with the collision landing on a different name
	 * than a plain suffix-strip finds. A survey of every {@link #SUFFIXES}
	 * suffix across the entire bundled index (see {@code
	 * OwnedVariantResolverTest}) found this is the ONLY suffix-stripped name
	 * with zero indexed entries, so a single curated alias is used here
	 * rather than a general renaming rule.
	 */
	private static final Map<String, String> UNINDEXED_PLAIN_NAME_ALIASES =
		Collections.singletonMap("toxic staff", "Toxic staff of the dead");

	private OwnedVariantResolver()
	{
	}

	/**
	 * The plain (suffix-stripped) form's item id for {@code variantItemId}, or
	 * {@code null} if the item isn't indexed, its name doesn't end in a known
	 * variant suffix, neither the plain form nor its {@link
	 * #UNINDEXED_PLAIN_NAME_ALIASES} counterpart is indexed, or the resolved
	 * name maps to more than one id that differs in stats and/or equip
	 * requirements and none of them matches {@code variantItemId}'s own stats
	 * AND requirements (see class javadoc) — crediting nothing is safer than
	 * guessing which one the player can actually reach.
	 */
	public static Integer plainFormId(EquipmentIndexRepository index, int variantItemId)
	{
		EquipmentIndexRepository.Entry entry = index.entryFor(variantItemId);
		if (entry == null)
		{
			return null;
		}
		String name = entry.name();
		for (String suffix : SUFFIXES)
		{
			if (name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length()))
			{
				String plainName = name.substring(0, name.length() - suffix.length());
				List<Integer> candidates = index.idsForName(plainName);
				if (candidates.isEmpty())
				{
					String alias = UNINDEXED_PLAIN_NAME_ALIASES.get(plainName.toLowerCase(Locale.ROOT));
					if (alias != null)
					{
						candidates = index.idsForName(alias);
					}
				}
				return resolveByStatMatch(index, candidates, variantItemId);
			}
		}
		return null;
	}

	/**
	 * The item id that should actually be DISPLAYED (and, by the caller's own
	 * choice, applied) for a recommendation that resolved to {@code itemId}:
	 * if {@code itemId} is a plain base form and {@code ownedIds} genuinely
	 * contains one of its variants' ids, returns an owned, non-excluded
	 * variant id instead — so a recommendation that's really "you already own
	 * Masori mask (f)" shows that name/icon, not the plain "Masori mask" the
	 * optimiser matched candidates against. Falls back to {@code itemId}
	 * unchanged when no such owned variant applies (including when
	 * {@code itemId} is already a variant, owns nothing extra, or the only
	 * owned variant is in {@code excludedItemIds}).
	 *
	 * <p><b>Excluded variants are never returned</b> (a caller-supplied
	 * {@code excludedItemIds}): if the player right-clicked "Exclude from
	 * suggestions" on their own owned variant, the optimiser correctly falls
	 * back to the plain form as its recommendation, and remapping the display
	 * back to the excluded item would silently defeat that exclusion — the
	 * excluded item would reappear under a different id's row. Showing the
	 * plain name in that case is the honest answer: it really is what the
	 * optimiser is proposing.
	 *
	 * <p><b>Name collisions:</b> {@link EquipmentIndexRepository#idForName}
	 * keeps only one id per display name, but several variant names
	 * genuinely have more than one backing id (e.g. an imbued ring or a
	 * slayer helmet has one id per reward source/quest). This method checks
	 * {@link EquipmentIndexRepository#idsForName} — every id sharing the
	 * variant's name — not just the first, so owning a different reward-
	 * source copy of the same-named variant still resolves correctly.
	 *
	 * <p><b>Stat- or requirement-different name collisions</b> (Deadman
	 * crystal armour's active/inactive split, or Deadman god capes'/Dark
	 * bow's requirement-only differences — see class javadoc): when the
	 * variant NAME's ids are not all both stat- and requirement-identical,
	 * only an owned id whose stats AND requirements match {@code itemId}'s
	 * own is substituted — never a wrong-state or wrong-level-gate duplicate
	 * (e.g. an owned zero-stat inactive deadman piece must never be
	 * displayed for a result actually calculated off the fully-statted
	 * active plain id).
	 */
	public static int preferOwnedVariant(EquipmentIndexRepository index, int itemId, Map<Integer, Long> ownedIds,
		Set<Integer> excludedItemIds)
	{
		if (ownedIds == null || ownedIds.isEmpty())
		{
			return itemId;
		}
		EquipmentIndexRepository.Entry entry = index.entryFor(itemId);
		if (entry == null)
		{
			return itemId;
		}
		EquipmentStatsRepository.Stats itemStats = EquipmentStatsRepository.getInstance().statsFor(itemId);
		for (String baseName : variantBaseNamesFor(entry.name()))
		{
			for (String suffix : SUFFIXES)
			{
				List<Integer> variantIds = index.idsForName(baseName + suffix);
				if (variantIds.isEmpty())
				{
					continue;
				}
				boolean homogeneous = isHomogeneous(variantIds);
				for (Integer variantId : variantIds)
				{
					if (!ownedIds.containsKey(variantId)
						|| (excludedItemIds != null && excludedItemIds.contains(variantId)))
					{
						continue;
					}
					if (homogeneous
						|| (sameStats(itemStats, EquipmentStatsRepository.getInstance().statsFor(variantId))
							&& sameRequirements(itemId, variantId)))
					{
						return variantId;
					}
				}
			}
		}
		return itemId;
	}

	/**
	 * Every name a variant of {@code indexedName} could be filed under: the
	 * item's own name first, then any {@link #UNINDEXED_PLAIN_NAME_ALIASES}
	 * key that maps ONTO it.
	 *
	 * <p><b>The alias table has to work in both directions</b>, or the
	 * credit it enables has no matching display. {@link #plainFormId} reads
	 * it forwards: "Toxic staff (deadman)" strips to the unindexed "Toxic
	 * staff", which the table redirects to the real "Toxic staff of the
	 * dead", crediting 12902/12904. {@link #preferOwnedVariant} then has to
	 * come back the other way — and appending a suffix to the item's OWN
	 * name looks for "Toxic staff of the dead (deadman)", which does not
	 * exist. Without the reverse hop the credited plain id is displayed and
	 * highlighted even though the player holds only 33035/33036, which is
	 * the same class of defect as crediting an id and then naming a
	 * different one (see {@code GearSection#resolvedItemId}): the
	 * ownership map says "you have this" and every surface points at
	 * something that is not in the bank.
	 *
	 * <p>Only the NAME lookup is widened. The stat/requirement match still
	 * decides which id is substituted, which is what keeps the charged and
	 * uncharged staffs apart — 33035 ↔ 12902 (amagic +17) and 33036 ↔ 12904
	 * (amagic +25) — since that name group is heterogeneous and therefore
	 * requires an exact match rather than file order.
	 */
	private static List<String> variantBaseNamesFor(String indexedName)
	{
		List<String> names = new java.util.ArrayList<>(2);
		names.add(indexedName);
		for (Map.Entry<String, String> alias : UNINDEXED_PLAIN_NAME_ALIASES.entrySet())
		{
			if (alias.getValue().equalsIgnoreCase(indexedName))
			{
				// The key is already lowercase and idsForName is
				// case-insensitive, so no display-case reconstruction is needed.
				names.add(alias.getKey());
			}
		}
		return names;
	}

	/**
	 * Resolves a name-sharing group of ids to a single id "compatible with"
	 * {@code referenceItemId}: if every id in {@code candidates} has
	 * IDENTICAL combat stats AND IDENTICAL equip requirements (the common
	 * case — reward-source/charge duplicates, or a (f)/(i) suffix's single
	 * plain form), any one of them is safe and the first (file order,
	 * matching historical behaviour) is returned. Otherwise the group holds
	 * items that genuinely differ — either in combat stats (Deadman crystal
	 * armour's active/inactive split) or in equip requirements alone
	 * (Deadman god capes, Dark bow's reward duplicates) — and only an id
	 * whose stats AND requirements exactly match {@code referenceItemId}'s
	 * own is trustworthy; {@code null} if none does — see class javadoc for
	 * why that's the safe answer, not a guess.
	 */
	private static Integer resolveByStatMatch(EquipmentIndexRepository index, List<Integer> candidates,
		int referenceItemId)
	{
		if (candidates.isEmpty())
		{
			return null;
		}
		if (isHomogeneous(candidates))
		{
			return candidates.get(0);
		}
		EquipmentStatsRepository.Stats referenceStats = EquipmentStatsRepository.getInstance().statsFor(referenceItemId);
		for (Integer candidateId : candidates)
		{
			if (sameStats(referenceStats, EquipmentStatsRepository.getInstance().statsFor(candidateId))
				&& sameRequirements(referenceItemId, candidateId))
			{
				return candidateId;
			}
		}
		return null;
	}

	/**
	 * True when every id in {@code ids} (non-empty) shares identical combat
	 * stats AND identical equip skill/level requirements. Both must agree:
	 * {@link EquipmentStatsRepository}'s 14 combat-bonus fields don't capture
	 * everything a same-named duplicate can differ on — a stat-identical pair
	 * can still gate on a different skill level (e.g. two "Imbued saradomin
	 * cape" ids are combat-stat-identical, but one requires magic 75 and the
	 * other has no recorded requirement at all). Treating that pair as
	 * "homogeneous" and picking file order would silently drop the level
	 * gate, so requirements are checked as a second, independent dimension —
	 * a single-element list is always homogeneous (nothing to disagree with).
	 */
	private static boolean isHomogeneous(List<Integer> ids)
	{
		int first = ids.get(0);
		EquipmentStatsRepository.Stats firstStats = EquipmentStatsRepository.getInstance().statsFor(first);
		for (int i = 1; i < ids.size(); i++)
		{
			int other = ids.get(i);
			if (!sameStats(firstStats, EquipmentStatsRepository.getInstance().statsFor(other))
				|| !sameRequirements(first, other))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Every field {@link EquipmentStatsRepository.Stats} models equal (both
	 * {@code null} counts as equal; one {@code null} does not) — the 14
	 * bonus fields AND {@link EquipmentStatsRepository.Stats#aspeed()}.
	 *
	 * <p><b>Attack speed is part of equivalence, not a bonus footnote.</b>
	 * {@code BundledSlotStatsLookup} feeds {@code aspeed} straight into the
	 * optimiser's {@code SlotStats}, so it is a direct DPS input: two ids
	 * with byte-identical bonuses and identical requirements but different
	 * weapon speeds are NOT interchangeable, and treating them as
	 * "homogeneous" (or as an exact match in {@link #resolveByStatMatch})
	 * would let ownership of a slower variant credit a faster counterpart
	 * and produce a DPS figure the player cannot actually achieve — the
	 * exact class of over-credit this whole resolver exists to prevent.
	 *
	 * <p>On the currently bundled data this changes no outcome: across all
	 * 84 name groups reachable by a {@link #SUFFIXES} strip, no group's
	 * homogeneity verdict flips when speed is included, and no
	 * variant/plain pair matches on the 14 bonus fields and requirements
	 * while differing in speed (checked including the {@link
	 * #UNINDEXED_PLAIN_NAME_ALIASES} pair — Toxic staff 33035/33036 vs
	 * 12902/12904 — and every god cape/Dark bow deadman pair). It is a
	 * guard against a future data refresh introducing such a pair
	 * silently, which the previous comparison could not have caught.
	 *
	 * <p>The stat rows carry a 16th entry the repository deliberately does
	 * not model (attack range: -1 for melee, 3-13 for ranged/magic). It is
	 * not a DPS input in this engine and is not compared here.
	 */
	private static boolean sameStats(EquipmentStatsRepository.Stats a, EquipmentStatsRepository.Stats b)
	{
		if (a == null || b == null)
		{
			return a == b;
		}
		return a.astab() == b.astab() && a.aslash() == b.aslash() && a.acrush() == b.acrush()
			&& a.amagic() == b.amagic() && a.arange() == b.arange()
			&& a.dstab() == b.dstab() && a.dslash() == b.dslash() && a.dcrush() == b.dcrush()
			&& a.dmagic() == b.dmagic() && a.drange() == b.drange()
			&& a.str() == b.str() && a.rstr() == b.rstr()
			&& a.mdmg() == b.mdmg() && a.prayer() == b.prayer()
			&& a.aspeed() == b.aspeed();
	}

	/**
	 * Test seam for {@link #sameStats} on two real bundled item ids — the
	 * comparison is data-driven and its attack-speed term changes no outcome
	 * on the currently shipped variant groups, so the only way to assert it
	 * at all is against a pair that genuinely exercises it.
	 */
	static boolean sameStatsForTest(int itemIdA, int itemIdB)
	{
		EquipmentStatsRepository repo = EquipmentStatsRepository.getInstance();
		return sameStats(repo.statsFor(itemIdA), repo.statsFor(itemIdB));
	}

	/**
	 * True when {@code itemIdA} and {@code itemIdB} carry the EXACT same equip
	 * skill/level requirements (an absent requirement map counts as "no
	 * requirements", equal to another absent one, but NOT equal to any
	 * non-empty requirement map — {@code equipment_requirements.min.json}
	 * only records an item when it carries at least one requirement, so
	 * absent means "no requirement recorded in the cache", never "definitely
	 * ungated"; this comparison is written to be correct either way, since it
	 * only ever asks "do these two match", not "is this one gated"). Used
	 * alongside {@link #sameStats} so a duplicate that differs ONLY in its
	 * level gate (not in combat stats) is never treated as an interchangeable
	 * "any pick is safe" duplicate — see {@link #isHomogeneous}.
	 */
	private static boolean sameRequirements(int itemIdA, int itemIdB)
	{
		Map<String, Integer> a = EquipmentRequirementsRepository.getInstance().requirementsFor(itemIdA);
		Map<String, Integer> b = EquipmentRequirementsRepository.getInstance().requirementsFor(itemIdB);
		Map<String, Integer> normalizedA = a == null ? Collections.emptyMap() : a;
		Map<String, Integer> normalizedB = b == null ? Collections.emptyMap() : b;
		return normalizedA.equals(normalizedB);
	}
}
