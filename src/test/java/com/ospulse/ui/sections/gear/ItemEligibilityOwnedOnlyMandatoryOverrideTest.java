package com.ospulse.ui.sections.gear;

import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterGearOverride;
import com.ospulse.combat.MonsterGearOverrideRepository;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Codex P1 finding on PR #19, {@code OwnedOnlyMandatoryOverrideGate.java:74}
 * ("Force the owned alternative instead of the unowned primary"): the
 * previous round's fix let {@link OwnedOnlyMandatoryOverrideGate#blockingOverride}
 * accept an owned {@link MonsterGearOverride#alternativeItemIds()} substitute
 * (e.g. an owned Slayer helmet satisfying a Facemask-type requirement) as
 * satisfying the requirement and let the result through — but left {@link
 * ItemEligibility#mandatoryOverrideItemIds} force-including the UNOWNED
 * primary id regardless, which {@code GearOptimizer.applyForcedIncludes}
 * then force-equips with no ownership/budget check at all. An ironman in
 * owned-only mode who owns the alternative but not the primary therefore got
 * an unowned item recommended — exactly the guarantee owned-only mode exists
 * to prevent.
 *
 * <p>Uses the real bundled "Wall beast" / Spiny helmet override (primary id
 * 4551, alternatives including Slayer helmet variant 11864) — the one
 * curated entry whose primary AND at least one alternative are both present
 * in the bundled equipment index (so {@code GearOptimizer.applyForcedIncludes}
 * would actually resolve a slot for either id — Dust devil/Facemask's
 * primary id 4164 is not itself indexed, so it can't demonstrate the
 * force-include half of this bug the way Wall beast/Spiny helmet can).
 */
public class ItemEligibilityOwnedOnlyMandatoryOverrideTest
{
	static
	{
		com.ospulse.combat.BundledGson.set(new com.google.gson.Gson());
	}

	private static final Monster WALL_BEAST = Monster.builder().name("Wall beast").build();
	private static final int SPINY_HELMET = 4551; // primary itemId
	private static final int SLAYER_HELMET = 11864; // one of alternativeItemIds()

	private static MonsterGearOverride wallBeastOverride()
	{
		java.util.List<MonsterGearOverride> overrides =
			MonsterGearOverrideRepository.getInstance().forMonster("Wall beast");
		assertEquals("test fixture assumption: exactly one curated override for Wall beast", 1, overrides.size());
		MonsterGearOverride override = overrides.get(0);
		assertEquals(SPINY_HELMET, override.itemId());
		assertTrue(override.alternativeItemIds().contains(SLAYER_HELMET));
		return override;
	}

	// ---------------------------- the actual defect: forced id vs gate agreement

	@Test
	public void ownedOnly_primaryUnowned_alternativeOwned_forcesTheOwnedAlternative_notTheUnownedPrimary()
	{
		wallBeastOverride(); // sanity-check the fixture data before asserting behaviour on it
		Set<Integer> ownedIds = Collections.singleton(SLAYER_HELMET);

		Set<Integer> forced = ItemEligibility.mandatoryOverrideItemIds(
			WALL_BEAST, Collections.emptySet(), true, ownedIds);

		assertTrue("must force-include the OWNED alternative (Slayer helmet): " + forced,
			forced.contains(SLAYER_HELMET));
		assertFalse("must NOT force-include the unowned primary (Spiny helmet) once an owned "
			+ "alternative satisfies the requirement: " + forced,
			forced.contains(SPINY_HELMET));
	}

	@Test
	public void ownedOnly_primaryUnowned_alternativeOwned_gateDoesNotBlock_consistentWithTheForcedId()
	{
		// The gate's own notion of "satisfied" (owned Slayer helmet substitutes
		// for the Spiny helmet requirement) must agree with which id
		// mandatoryOverrideItemIds() force-includes -- that agreement (not just
		// "the gate doesn't block") is the actual fix: before it, the gate let
		// this scenario through while the force-include path still injected the
		// unowned primary.
		Set<Integer> ownedIds = Collections.singleton(SLAYER_HELMET);

		Optional<MonsterGearOverride> blocking =
			OwnedOnlyMandatoryOverrideGate.blockingOverride(true, WALL_BEAST, ownedIds);
		assertFalse("owned alternative must satisfy the requirement (no block)", blocking.isPresent());

		Set<Integer> forced = ItemEligibility.mandatoryOverrideItemIds(
			WALL_BEAST, Collections.emptySet(), true, ownedIds);
		assertTrue("the id force-included must be one the player actually owns: " + forced,
			forced.stream().allMatch(ownedIds::contains));
	}

	@Test
	public void ownedOnly_primaryOwned_forcesThePrimary_notAnAlternative()
	{
		Set<Integer> ownedIds = Collections.singleton(SPINY_HELMET);

		Set<Integer> forced = ItemEligibility.mandatoryOverrideItemIds(
			WALL_BEAST, Collections.emptySet(), true, ownedIds);

		assertEquals(Collections.singleton(SPINY_HELMET), forced);
	}

	@Test
	public void ownedOnly_neitherPrimaryNorAlternativeOwned_stillFallsBackToThePrimary()
	{
		// mandatoryOverrideItemIds() alone still returns the (unowned) primary
		// here -- that is fine and expected, because this exact case is what
		// OwnedOnlyMandatoryOverrideGate#blockingOverride refuses the target for
		// before any result (and therefore this forced id) ever reaches the
		// player. See GearSectionOwnedOnlyMandatoryOverrideBlockTest for that
		// end-to-end block.
		Set<Integer> forced = ItemEligibility.mandatoryOverrideItemIds(
			WALL_BEAST, Collections.emptySet(), true, Collections.emptySet());

		assertEquals(Collections.singleton(SPINY_HELMET), forced);
	}

	// ---------------------------- non-owned-only mode must not regress

	@Test
	public void notOwnedOnly_alternativeOwnedPrimaryNot_stillForcesThePrimary_unchangedBehaviour()
	{
		// Outside owned-only mode an unaffordable/unowned force-include is a
		// legitimate purchase suggestion (the player may simply buy the
		// primary) -- this must be completely unaffected by the P1 fix.
		Set<Integer> ownedIds = Collections.singleton(SLAYER_HELMET);

		Set<Integer> forced = ItemEligibility.mandatoryOverrideItemIds(
			WALL_BEAST, Collections.emptySet(), false, ownedIds);

		assertEquals(Collections.singleton(SPINY_HELMET), forced);
	}

	@Test
	public void ownedOnly_resolvedIdStillHonoursUserExclusion()
	{
		// A user's explicit slot exclusion still wins over the resolved
		// (owned-alternative) id, same as it already did for the primary.
		Set<Integer> ownedIds = Collections.singleton(SLAYER_HELMET);
		Set<Integer> exclusions = new LinkedHashSet<>(Collections.singleton(SLAYER_HELMET));

		Set<Integer> forced = ItemEligibility.mandatoryOverrideItemIds(WALL_BEAST, exclusions, true, ownedIds);

		assertTrue("the excluded resolved id must not be forced back in: " + forced,
			forced.isEmpty());
	}
}
