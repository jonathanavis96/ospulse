package com.ospulse.ui.sections.gear;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStatsRepository;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pure unit tests for {@link OwnedVariantResolver} against the REAL bundled
 * {@link EquipmentIndexRepository} singleton (no client/Swing needed — this
 * is exactly the kind of dependency-light logic the class exists to isolate
 * from {@code GearSection}). Covers Codex PR #5 review findings #2 and #3,
 * which are about {@link OwnedVariantResolver#preferOwnedVariant} itself
 * rather than how {@code GearSection} wires it in (see
 * {@code GearSectionOptimizerStyleTest} for the end-to-end wiring tests).
 */
public class OwnedVariantResolverTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

	private static final EquipmentIndexRepository INDEX = EquipmentIndexRepository.getInstance();

	// Masori mask (plain) 27226 <-> Masori mask (f) 27235 — single-id-per-name pair.
	private static final int MASORI_MASK = 27226;
	private static final int MASORI_MASK_F = 27235;

	// Warrior ring (plain) 6735 <-> "Warrior ring (i)" — a NAME shared by three
	// distinct reward-source ids: [26769, 11772, 25262] (idForName alone only
	// ever returns the first, 26769 — see Codex finding #3).
	private static final int WARRIOR_RING = 6735;
	private static final int WARRIOR_RING_I_FIRST = 26769;
	private static final int WARRIOR_RING_I_OTHER = 11772;

	private static Map<Integer, Long> owned(int... ids)
	{
		Map<Integer, Long> map = new HashMap<>();
		for (int id : ids)
		{
			map.put(id, 0L);
		}
		return map;
	}

	@Test
	public void preferOwnedVariant_resolvesPlainToOwnedVariant()
	{
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, MASORI_MASK, owned(MASORI_MASK_F), null);
		assertEquals(MASORI_MASK_F, resolved);
	}

	@Test
	public void preferOwnedVariant_noOwnedVariant_returnsPlainUnchanged()
	{
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, MASORI_MASK, owned(), null);
		assertEquals(MASORI_MASK, resolved);

		int resolvedNullMap = OwnedVariantResolver.preferOwnedVariant(INDEX, MASORI_MASK, null, null);
		assertEquals(MASORI_MASK, resolvedNullMap);
	}

	@Test
	public void preferOwnedVariant_alreadyAVariant_returnsUnchanged()
	{
		// itemId is already "Masori mask (f)" — appending another suffix
		// ("Masori mask (f) (f)"/" (i)") resolves nothing, so it's returned as-is.
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, MASORI_MASK_F, owned(MASORI_MASK_F), null);
		assertEquals(MASORI_MASK_F, resolved);
	}

	/**
	 * Codex review finding #2 (PR #5): an EXCLUDED owned variant must never
	 * be the resolved id — remapping the display back to an item the player
	 * explicitly excluded from suggestions would silently defeat that
	 * exclusion (the excluded item reappearing under the plain id's row).
	 */
	@Test
	public void preferOwnedVariant_excludedVariant_isNotReturned()
	{
		Set<Integer> excluded = new LinkedHashSet<>();
		excluded.add(MASORI_MASK_F);

		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, MASORI_MASK, owned(MASORI_MASK_F), excluded);
		assertEquals("the excluded variant must not be resurrected — the plain id stays as-is",
			MASORI_MASK, resolved);
	}

	/** An empty (not null) exclusion set behaves exactly like no exclusions. */
	@Test
	public void preferOwnedVariant_emptyExclusionSet_behavesLikeNoExclusions()
	{
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, MASORI_MASK, owned(MASORI_MASK_F),
			Collections.emptySet());
		assertEquals(MASORI_MASK_F, resolved);
	}

	/**
	 * Codex review finding #3 (PR #5): {@link EquipmentIndexRepository#idForName}
	 * keeps only ONE id per display name, but "Warrior ring (i)" genuinely
	 * has three backing ids (one per reward source) — 26769, 11772, 25262.
	 * Owning a copy that ISN'T the first-file-order id (26769) must still
	 * resolve correctly via {@link EquipmentIndexRepository#idsForName}.
	 */
	@Test
	public void preferOwnedVariant_ownsNonFirstDuplicateId_stillResolves()
	{
		// Sanity-check the fixture: idForName alone would have missed this.
		assertEquals(WARRIOR_RING_I_FIRST, (int) INDEX.idForName("Warrior ring (i)"));

		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, WARRIOR_RING, owned(WARRIOR_RING_I_OTHER), null);
		assertEquals("owning the SECOND reward-source copy must still resolve, not just the first",
			WARRIOR_RING_I_OTHER, resolved);
	}

	@Test
	public void plainFormId_resolvesVariantToPlain()
	{
		assertEquals(Integer.valueOf(MASORI_MASK), OwnedVariantResolver.plainFormId(INDEX, MASORI_MASK_F));
	}

	@Test
	public void plainFormId_nonVariantName_returnsNull()
	{
		assertEquals(null, OwnedVariantResolver.plainFormId(INDEX, MASORI_MASK));
	}

	// Imbued saradomin cape (deadman) 29617 <-> Imbued saradomin cape 24248/21791 —
	// a Deadman Mode reward duplicate, stat-identical to the real, non-mode-locked
	// item (see equipment_stats.min.json: both amagic+15/dmagic+15/mdmg+20).
	// GearSection.restrictedItemIds() always excludes 29617 itself from optimiser
	// candidates (mode-locked, regardless of ownership) — issue #11 Stage 3.
	private static final int IMBUED_SARADOMIN_CAPE_DEADMAN = 29617;

	@Test
	public void plainFormId_deadmanSuffix_resolvesToRealNonModeLockedCounterpart()
	{
		Integer expected = INDEX.idForName("Imbued saradomin cape");
		assertEquals(expected, OwnedVariantResolver.plainFormId(INDEX, IMBUED_SARADOMIN_CAPE_DEADMAN));
	}

	@Test
	public void preferOwnedVariant_ownedDeadmanCape_resolvesFromRealCounterpart()
	{
		int realImbuedSaradominCape = INDEX.idForName("Imbued saradomin cape");
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, realImbuedSaradominCape,
			owned(IMBUED_SARADOMIN_CAPE_DEADMAN), null);
		assertEquals("owning the deadman-mode duplicate must resolve display back to it, exactly like "
				+ "the (f)/(i) suffixes already do",
			IMBUED_SARADOMIN_CAPE_DEADMAN, resolved);
	}

	// ==================================================================================
	// P1 follow-up (code review on issue #11 Stage 3): Deadman crystal armour has an
	// ACTIVE id (real combat stats) and an INACTIVE id (all-zero stats, cosmetically
	// deactivated) that share the SAME plain "Crystal body"/"Crystal helm"/"Crystal
	// legs" name — a genuinely different-item name collision, unlike every other
	// (f)/(i)/(deadman) case in the bundled data, where same-named ids are always
	// stat-identical duplicates (charge levels, reward sources). The original
	// deadmanSuffix_everyBundledMatch_hasAStatIdenticalPlainCounterpart check above
	// only asserted "at least one stat-identical id exists in the group" — true even
	// when idForName's first-file-order pick resolves to the WRONG (different-stats)
	// member of that same group, which is exactly what happened here. Ids verified
	// against the decompiled net.runelite.api.gameval.ItemID constants in the
	// runelite-api jar on this project's classpath (javap -constants):
	// CRYSTAL_HELMET(_INACTIVE) 23971/23973, CRYSTAL_CHESTPLATE(_INACTIVE) 23975/23977,
	// CRYSTAL_PLATELEGS(_INACTIVE) 23979/23981, and their _DEADMAN/_INACTIVE_DEADMAN
	// twins 33031/33033, 33023/33025, 33027/33029.
	// ==================================================================================

	private static final int CRYSTAL_HELM_ACTIVE = 23971;
	private static final int CRYSTAL_HELM_INACTIVE = 23973;
	private static final int CRYSTAL_HELM_DEADMAN_ACTIVE = 33031;
	private static final int CRYSTAL_HELM_DEADMAN_INACTIVE = 33033;

	private static final int CRYSTAL_BODY_ACTIVE = 23975;
	private static final int CRYSTAL_BODY_INACTIVE = 23977;
	private static final int CRYSTAL_BODY_DEADMAN_ACTIVE = 33023;
	private static final int CRYSTAL_BODY_DEADMAN_INACTIVE = 33025;

	private static final int CRYSTAL_LEGS_ACTIVE = 23979;
	private static final int CRYSTAL_LEGS_INACTIVE = 23981;
	private static final int CRYSTAL_LEGS_DEADMAN_ACTIVE = 33027;
	private static final int CRYSTAL_LEGS_DEADMAN_INACTIVE = 33029;

	@Test
	public void plainFormId_deadmanCrystalBody_inactiveNeverCreditsActivePlainId()
	{
		assertEquals("owning the zero-stat inactive deadman body must credit the INACTIVE plain id, "
				+ "never the fully-statted active one",
			Integer.valueOf(CRYSTAL_BODY_INACTIVE),
			OwnedVariantResolver.plainFormId(INDEX, CRYSTAL_BODY_DEADMAN_INACTIVE));
	}

	@Test
	public void plainFormId_deadmanCrystalBody_activeCreditsActivePlainId()
	{
		assertEquals("owning the real-stat active deadman body must credit the ACTIVE plain id",
			Integer.valueOf(CRYSTAL_BODY_ACTIVE),
			OwnedVariantResolver.plainFormId(INDEX, CRYSTAL_BODY_DEADMAN_ACTIVE));
	}

	@Test
	public void plainFormId_deadmanCrystalHelm_inactiveNeverCreditsActivePlainId()
	{
		assertEquals(Integer.valueOf(CRYSTAL_HELM_INACTIVE),
			OwnedVariantResolver.plainFormId(INDEX, CRYSTAL_HELM_DEADMAN_INACTIVE));
	}

	@Test
	public void plainFormId_deadmanCrystalHelm_activeCreditsActivePlainId()
	{
		assertEquals(Integer.valueOf(CRYSTAL_HELM_ACTIVE),
			OwnedVariantResolver.plainFormId(INDEX, CRYSTAL_HELM_DEADMAN_ACTIVE));
	}

	@Test
	public void plainFormId_deadmanCrystalLegs_inactiveNeverCreditsActivePlainId()
	{
		assertEquals(Integer.valueOf(CRYSTAL_LEGS_INACTIVE),
			OwnedVariantResolver.plainFormId(INDEX, CRYSTAL_LEGS_DEADMAN_INACTIVE));
	}

	@Test
	public void plainFormId_deadmanCrystalLegs_activeCreditsActivePlainId()
	{
		assertEquals(Integer.valueOf(CRYSTAL_LEGS_ACTIVE),
			OwnedVariantResolver.plainFormId(INDEX, CRYSTAL_LEGS_DEADMAN_ACTIVE));
	}

	/**
	 * The reverse-display half of the same defect: a recommendation actually
	 * calculated off the ACTIVE plain id must never be displayed as the
	 * owned zero-stat INACTIVE deadman piece, even though they share a name.
	 */
	@Test
	public void preferOwnedVariant_ownedInactiveDeadmanCrystalBody_neverDisplayedForActiveResult()
	{
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, CRYSTAL_BODY_ACTIVE,
			owned(CRYSTAL_BODY_DEADMAN_INACTIVE), null);
		assertEquals("a result calculated off the ACTIVE plain id must stay the active id — the owned "
				+ "INACTIVE deadman duplicate has different (zero) stats and must not be substituted",
			CRYSTAL_BODY_ACTIVE, resolved);
	}

	/** Companion: the ACTIVE deadman duplicate DOES correctly substitute for an active-id result. */
	@Test
	public void preferOwnedVariant_ownedActiveDeadmanCrystalBody_displayedForActiveResult()
	{
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, CRYSTAL_BODY_ACTIVE,
			owned(CRYSTAL_BODY_DEADMAN_ACTIVE), null);
		assertEquals(CRYSTAL_BODY_DEADMAN_ACTIVE, resolved);
	}

	/** And the INACTIVE deadman duplicate correctly substitutes for an inactive-id result. */
	@Test
	public void preferOwnedVariant_ownedInactiveDeadmanCrystalBody_displayedForInactiveResult()
	{
		int resolved = OwnedVariantResolver.preferOwnedVariant(INDEX, CRYSTAL_BODY_INACTIVE,
			owned(CRYSTAL_BODY_DEADMAN_INACTIVE), null);
		assertEquals(CRYSTAL_BODY_DEADMAN_INACTIVE, resolved);
	}

	/**
	 * Strengthened regression (replaces the existence-only check that missed this
	 * P1): for EVERY suffix in {@link OwnedVariantResolver#SUFFIXES} and every
	 * bundled item whose name ends in it, asserts what {@link
	 * OwnedVariantResolver#plainFormId} actually RETURNS, not merely that some
	 * stat-identical candidate exists somewhere in the name group.
	 *
	 * <p>The real invariant is conditional, not a blanket equality: most
	 * suffixes mean "this variant SUPERSEDES (better stats than) its plain
	 * form" (Masori mask (f) has strictly better defensive bonuses than plain
	 * Masori mask — see {@code plainFormId_resolvesVariantToPlain}'s fixture),
	 * so requiring the resolved id's stats to equal the variant's own would be
	 * wrong for every (f)/(i) case in the bundled data. The property that MUST
	 * hold universally is: when a plain name resolves to more than one id AND
	 * those ids are NOT all stat-identical to each other (a genuine same-name
	 * collision between different items — currently only Deadman crystal
	 * armour), {@code plainFormId} must resolve to the specific id matching the
	 * variant's own stats, or {@code null} if none matches — never an
	 * arbitrary/first-position pick. When the plain name's ids ARE all
	 * stat-identical (the common case), any pick is safe and unconstrained.
	 */
	@Test
	public void plainFormId_everySuffix_resolvesCorrectlyForEveryHeterogeneousNameCollision()
	{
		EquipmentStatsRepository stats = EquipmentStatsRepository.getInstance();
		Map<String, java.util.List<Integer>> idsByName = new HashMap<>();
		for (Integer id : INDEX.allItemIds())
		{
			EquipmentIndexRepository.Entry entry = INDEX.entryFor(id);
			if (entry != null)
			{
				idsByName.computeIfAbsent(entry.name(), n -> new java.util.ArrayList<>()).add(id);
			}
		}

		int heterogeneousGroupsChecked = 0;
		int variantsChecked = 0;
		for (String suffix : OwnedVariantResolver.SUFFIXES)
		{
			for (Map.Entry<String, java.util.List<Integer>> e : idsByName.entrySet())
			{
				String name = e.getKey();
				if (!name.endsWith(suffix))
				{
					continue;
				}
				String plainName = name.substring(0, name.length() - suffix.length());
				java.util.List<Integer> plainIds = idsByName.get(plainName);
				if (plainIds == null)
				{
					continue; // no indexed plain form at all — plainFormId must return null, checked separately below
				}
				boolean heterogeneous = !allSameStats(stats, plainIds);

				for (Integer variantId : e.getValue())
				{
					variantsChecked++;
					Integer resolved = OwnedVariantResolver.plainFormId(INDEX, variantId);
					if (!heterogeneous)
					{
						// Safe by construction: any pick is stat-identical to every other, so the only
						// requirement is that SOME real plain id was returned (never null when one exists).
						assertTrue("\"" + name + "\" (" + variantId + "): a stat-homogeneous plain-name group "
								+ "must still resolve to a real member, not null",
							plainIds.contains(resolved));
						continue;
					}
					heterogeneousGroupsChecked++;
					// Heterogeneous: the resolved id (if any) MUST match the variant's own stats exactly.
					if (resolved != null)
					{
						assertTrue("\"" + name + "\" (" + variantId + ") resolved to " + resolved
								+ ", whose stats do not match the variant's own — a heterogeneous name "
								+ "collision must only resolve to a stat-matching member, or null",
							sameStats(stats.statsFor(variantId), stats.statsFor(resolved)));
					}
				}
			}
		}
		assertTrue("fixture sanity: at least one variant must exist for a SUFFIXES entry", variantsChecked > 0);
		assertTrue("fixture sanity: this check must actually exercise a heterogeneous name collision "
				+ "(Deadman crystal armour) — otherwise it never tests the property it exists for",
			heterogeneousGroupsChecked > 0);
	}

	// ==================================================================================
	// P2 follow-up (missed credit, not over-credit): "Toxic staff (deadman)" strips to
	// "Toxic staff", which has NO indexed entry at all — unlike every other SUFFIXES
	// case, the real item lives under a genuinely DIFFERENT name, "Toxic staff of the
	// dead". A full survey of every SUFFIXES-suffixed name in the bundled
	// equipment_index.min.json (every " (f)"/" (i)"/" (deadman)" entry, checked whether
	// its stripped name resolves to ANY indexed id) found this is the ONLY such case —
	// so OwnedVariantResolver.UNINDEXED_PLAIN_NAME_ALIASES is a single curated entry,
	// not a general rule. Ids verified against net.runelite.api.gameval.ItemID
	// (javap -constants on the runelite-api jar on this project's classpath):
	// TOXIC_SOTD_DEADMAN=33035, TOXIC_SOTD_CHARGED_DEADMAN=33036, TOXIC_SOTD=12902,
	// TOXIC_SOTD_CHARGED=12904. Like Deadman crystal armour, the two real ids are NOT
	// stat-identical (equipment_stats.min.json: 12902 amagic +17, 12904 amagic +25 —
	// 33035/33036 match those exactly), and are in the OPPOSITE file-order pair-up
	// (33036/12904 both happen to be first in file order, 33035/12902 both second) —
	// so a naive "first candidate wins" resolution would accidentally get the charged
	// pair right while silently over-crediting the UNCHARGED deadman staff (33035) with
	// the CHARGED real item (12904). The two tests below discriminate exactly that.
	// ==================================================================================

	private static final int TOXIC_STAFF_DEADMAN_UNCHARGED = 33035;
	private static final int TOXIC_STAFF_DEADMAN_CHARGED = 33036;
	private static final int TOXIC_STAFF_OF_THE_DEAD_UNCHARGED = 12902;
	private static final int TOXIC_STAFF_OF_THE_DEAD_CHARGED = 12904;

	@Test
	public void plainFormId_deadmanToxicStaffUncharged_creditsUnchargedRealCounterpart()
	{
		assertEquals("owning the uncharged deadman toxic staff must credit the uncharged real "
				+ "\"Toxic staff of the dead\" (12902), even though \"Toxic staff\" itself has no indexed entry",
			Integer.valueOf(TOXIC_STAFF_OF_THE_DEAD_UNCHARGED),
			OwnedVariantResolver.plainFormId(INDEX, TOXIC_STAFF_DEADMAN_UNCHARGED));
	}

	@Test
	public void plainFormId_deadmanToxicStaffCharged_creditsChargedRealCounterpart()
	{
		assertEquals("owning the charged deadman toxic staff must credit the charged real "
				+ "\"Toxic staff of the dead\" (12904)",
			Integer.valueOf(TOXIC_STAFF_OF_THE_DEAD_CHARGED),
			OwnedVariantResolver.plainFormId(INDEX, TOXIC_STAFF_DEADMAN_CHARGED));
	}

	/**
	 * Guard against over-credit: the uncharged deadman staff (33035) must never
	 * resolve to the CHARGED real item (12904) — which is what a naive
	 * "first-in-file-order" pick would return, since 12904 happens to be first
	 * for the "Toxic staff of the dead" name group. Existence of a same-name
	 * candidate is not enough; the SPECIFIC stat-matching one must be chosen.
	 * (The two tests above already assert the specific expected id, which
	 * inherently rules out 12904 for 33035 — this test states that guard
	 * explicitly as a not-equals, so a future refactor that starts picking
	 * "any same-name candidate" cannot silently pass by returning the wrong id.)
	 */
	@Test
	public void plainFormId_deadmanToxicStaffUncharged_neverCreditsChargedCounterpart()
	{
		Integer resolved = OwnedVariantResolver.plainFormId(INDEX, TOXIC_STAFF_DEADMAN_UNCHARGED);
		assertTrue("must resolve to the uncharged id (12902), not the charged one (12904) that "
				+ "would be picked first in file order",
			resolved != null && resolved != TOXIC_STAFF_OF_THE_DEAD_CHARGED);
	}

	private static boolean allSameStats(EquipmentStatsRepository stats, java.util.List<Integer> ids)
	{
		EquipmentStatsRepository.Stats first = stats.statsFor(ids.get(0));
		for (Integer id : ids)
		{
			if (!sameStats(first, stats.statsFor(id)))
			{
				return false;
			}
		}
		return true;
	}

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
