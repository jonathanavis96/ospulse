package com.ospulse.ui.sections.gear;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStatsRepository;

import java.util.List;
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
 *   cape ids per god): every member shares IDENTICAL combat stats — picking
 *   any one of them is always safe, so the historical "first in file order"
 *   behaviour is kept for these.</li>
 *   <li><i>Genuinely different items sharing a name</i> — Deadman-mode
 *   crystal helm/body/legs each have an ACTIVE id (real combat stats) and an
 *   INACTIVE id (all-zero stats, cosmetically deactivated) that share the
 *   SAME plain "Crystal body"/"Crystal helm"/"Crystal legs" name. Picking
 *   the first file-order id here is wrong: owning the zero-stat inactive
 *   deadman piece must never credit the fully-statted active plain id (the
 *   optimiser would then compute and recommend DPS the player cannot
 *   actually achieve), and the reverse display lookup must never show the
 *   zero-stat inactive icon for a result actually calculated off the active
 *   id's real stats.
 * </ul>
 * Both {@link #plainFormId} and {@link #preferOwnedVariant} now detect which
 * case applies via {@link #resolveByStatMatch} / {@link #isHomogeneous}: a
 * name-sharing group is resolved by position ONLY when every member is
 * stat-identical (safe by construction); otherwise the specific member whose
 * stats match the item on the OTHER side of the mapping is required, and
 * {@code null}/the original id (no credit, no substitution) is returned when
 * no exact match exists — over-crediting ownership the player cannot
 * actually reach is the real harm, and a missing credit is visibly absent
 * while a wrong one looks modelled.
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

	private OwnedVariantResolver()
	{
	}

	/**
	 * The plain (suffix-stripped) form's item id for {@code variantItemId}, or
	 * {@code null} if the item isn't indexed, its name doesn't end in a known
	 * variant suffix, the plain form itself isn't indexed, or the plain name
	 * resolves to more than one stat-DIFFERENT id and none of them matches
	 * {@code variantItemId}'s own stats (see class javadoc) — crediting
	 * nothing is safer than guessing which one the player can actually reach.
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
				return resolveByStatMatch(index, index.idsForName(plainName), variantItemId);
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
	 * <p><b>Stat-different name collisions</b> (Deadman crystal armour's
	 * active/inactive split — see class javadoc): when the variant NAME's
	 * ids are not all stat-identical, only an owned id whose stats match
	 * {@code itemId}'s own stats is substituted — never a wrong-state
	 * duplicate (e.g. an owned zero-stat inactive deadman piece must never
	 * be displayed for a result actually calculated off the fully-statted
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
		String name = entry.name();
		EquipmentStatsRepository.Stats itemStats = EquipmentStatsRepository.getInstance().statsFor(itemId);
		for (String suffix : SUFFIXES)
		{
			List<Integer> variantIds = index.idsForName(name + suffix);
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
				if (homogeneous || sameStats(itemStats, EquipmentStatsRepository.getInstance().statsFor(variantId)))
				{
					return variantId;
				}
			}
		}
		return itemId;
	}

	/**
	 * Resolves a name-sharing group of ids to a single id "compatible with"
	 * {@code referenceItemId}: if every id in {@code candidates} has
	 * IDENTICAL combat stats (the common case — reward-source/charge
	 * duplicates, or a (f)/(i) suffix's single plain form), any one of them
	 * is safe and the first (file order, matching historical behaviour) is
	 * returned. Otherwise the group holds genuinely DIFFERENT items sharing
	 * a name (Deadman crystal armour's active/inactive split) and only an
	 * id whose stats exactly match {@code referenceItemId}'s own is
	 * trustworthy; {@code null} if none does — see class javadoc for why
	 * that's the safe answer, not a guess.
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
			if (sameStats(referenceStats, EquipmentStatsRepository.getInstance().statsFor(candidateId)))
			{
				return candidateId;
			}
		}
		return null;
	}

	/** True when every id in {@code ids} (non-empty) shares identical combat stats. */
	private static boolean isHomogeneous(List<Integer> ids)
	{
		EquipmentStatsRepository.Stats first = EquipmentStatsRepository.getInstance().statsFor(ids.get(0));
		for (int i = 1; i < ids.size(); i++)
		{
			if (!sameStats(first, EquipmentStatsRepository.getInstance().statsFor(ids.get(i))))
			{
				return false;
			}
		}
		return true;
	}

	/** Every combat-relevant field equal (both {@code null} counts as equal; one {@code null} does not). */
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
			&& a.mdmg() == b.mdmg() && a.prayer() == b.prayer();
	}
}
