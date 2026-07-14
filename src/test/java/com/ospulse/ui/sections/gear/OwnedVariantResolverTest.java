package com.ospulse.ui.sections.gear;

import com.ospulse.combat.EquipmentIndexRepository;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
}
