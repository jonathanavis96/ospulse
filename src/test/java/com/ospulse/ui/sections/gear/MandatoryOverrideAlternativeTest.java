package com.ospulse.ui.sections.gear;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterGearOverride;
import com.ospulse.combat.MonsterGearOverrideRepository;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.optimizer.GearOptimizer;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.session.GearSnapshot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Issue #35: a V's shield works exactly like a Mirror shield against
 * basilisks, basilisk knights and cockatrice, and is a strict upgrade to it,
 * yet the optimiser told V's shield owners to drop down to a Mirror shield.
 * Listing the V's shield as an alternative was not enough: the force-include
 * ignored alternatives outside owned-only mode, and inside it an owned primary
 * always beat an owned alternative.
 */
public class MandatoryOverrideAlternativeTest
{
	static
	{
		com.ospulse.combat.BundledGson.set(new com.google.gson.Gson());
	}

	private static final Monster BASILISK = Monster.builder().name("Basilisk").build();
	private static final Monster BANSHEE = Monster.builder().name("Banshee").build();
	private static final int MIRROR_SHIELD = 4156;
	private static final int VS_SHIELD = 24266;
	private static final int SLAYER_HELMET = 11864;
	private static final int SLAYER_HELMET_IMBUED = 11865;
	private static final int EARMUFFS = 4166;
	private static final int HELM_OF_NEITIZNOT = 10828;
	private static final int RUNE_SCIMITAR = 1333;
	private static final int HEAD_SLOT = 0;
	private static final int[] NOT_WEARING_ANYTHING = new int[0];
	private static final Set<Integer> NO_EXCLUSIONS = Collections.emptySet();

	private static Set<Integer> setOf(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}

	private static int[] wearing(int slot, int itemId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[slot] = itemId;
		return ids;
	}

	@Test
	public void everyCuratedBasiliskTargetAcceptsTheVsShield()
	{
		for (String monster : Arrays.asList("Basilisk", "Monstrous basilisk", "Basilisk Knight", "Cockatrice"))
		{
			for (MonsterGearOverride override : MonsterGearOverrideRepository.getInstance().forMonster(monster))
			{
				assertTrue(monster + " must accept a V's shield", override.satisfiedBy(VS_SHIELD));
			}
		}
	}

	@Test
	public void vsShieldWorn_isKeptInEveryMode()
	{
		int[] worn = wearing(WhatIfLoadout.SHIELD_SLOT, VS_SHIELD);
		for (boolean ownedOnly : new boolean[]{false, true})
		{
			assertEquals(Collections.singleton(VS_SHIELD), ItemEligibility.mandatoryOverrideItemIds(
				BASILISK, NO_EXCLUSIONS, ownedOnly, setOf(VS_SHIELD), worn));
		}
	}

	@Test
	public void vsShieldWorn_isKeptEvenWithAMirrorShieldInTheBank()
	{
		int[] worn = wearing(WhatIfLoadout.SHIELD_SLOT, VS_SHIELD);
		for (boolean ownedOnly : new boolean[]{false, true})
		{
			assertEquals(Collections.singleton(VS_SHIELD), ItemEligibility.mandatoryOverrideItemIds(
				BASILISK, NO_EXCLUSIONS, ownedOnly, setOf(VS_SHIELD, MIRROR_SHIELD), worn));
		}
	}

	@Test
	public void bothBanked_prefersTheVsShieldInEveryMode()
	{
		for (boolean ownedOnly : new boolean[]{false, true})
		{
			assertEquals(Collections.singleton(VS_SHIELD), ItemEligibility.mandatoryOverrideItemIds(
				BASILISK, NO_EXCLUSIONS, ownedOnly, setOf(VS_SHIELD, MIRROR_SHIELD), NOT_WEARING_ANYTHING));
		}
	}

	@Test
	public void mirrorShieldWorn_isKeptRatherThanSendingThePlayerToTheBank()
	{
		int[] worn = wearing(WhatIfLoadout.SHIELD_SLOT, MIRROR_SHIELD);
		assertEquals(Collections.singleton(MIRROR_SHIELD), ItemEligibility.mandatoryOverrideItemIds(
			BASILISK, NO_EXCLUSIONS, false, setOf(VS_SHIELD, MIRROR_SHIELD), worn));
	}

	@Test
	public void excludingTheVsShield_fallsBackToTheOwnedMirrorShield()
	{
		int[] worn = wearing(WhatIfLoadout.SHIELD_SLOT, VS_SHIELD);
		assertEquals(Collections.singleton(MIRROR_SHIELD), ItemEligibility.mandatoryOverrideItemIds(
			BASILISK, setOf(VS_SHIELD), false, setOf(VS_SHIELD, MIRROR_SHIELD), worn));
	}

	@Test
	public void ownedOnly_theOnlyOwnedSubstituteExcluded_neverForcesTheUnownedPrimary()
	{
		Set<Integer> forced = ItemEligibility.mandatoryOverrideItemIds(
			BASILISK, setOf(VS_SHIELD), true, setOf(VS_SHIELD), NOT_WEARING_ANYTHING);

		assertTrue("the gate lets this through, so forcing the unowned primary would break the mode: " + forced,
			forced.isEmpty());
	}

	@Test
	public void notOwnedOnly_theOnlyOwnedSubstituteExcluded_mayStillSuggestBuyingThePrimary()
	{
		assertEquals(Collections.singleton(MIRROR_SHIELD), ItemEligibility.mandatoryOverrideItemIds(
			BASILISK, setOf(VS_SHIELD), false, setOf(VS_SHIELD), NOT_WEARING_ANYTHING));
	}

	@Test
	public void gateAcceptsAVsShieldOnItsOwn()
	{
		assertFalse(OwnedOnlyMandatoryOverrideGate.blockingOverride(true, BASILISK, setOf(VS_SHIELD)).isPresent());
		assertTrue(OwnedOnlyMandatoryOverrideGate.blockingOverride(true, BASILISK, setOf(RUNE_SCIMITAR)).isPresent());
	}

	@Test
	public void slayerHelmetWorn_isKeptVsABansheeOutsideOwnedOnlyMode()
	{
		int[] worn = wearing(HEAD_SLOT, SLAYER_HELMET);
		assertEquals(Collections.singleton(SLAYER_HELMET), ItemEligibility.mandatoryOverrideItemIds(
			BANSHEE, NO_EXCLUSIONS, false, setOf(SLAYER_HELMET), worn));
	}

	@Test
	public void bankedImbuedHelmet_beatsThePlainFormItCreditsAsOwned()
	{
		// Owning the (i) also marks the plain helmet owned. Forcing the plain
		// id would price a ranged or magic setup without the imbue's bonus.
		assertEquals(Collections.singleton(SLAYER_HELMET_IMBUED), ItemEligibility.mandatoryOverrideItemIds(
			BANSHEE, NO_EXCLUSIONS, false, setOf(SLAYER_HELMET_IMBUED, SLAYER_HELMET), NOT_WEARING_ANYTHING));
	}

	@Test
	public void sourhog_acceptsASlayerHelmet()
	{
		for (MonsterGearOverride override : MonsterGearOverrideRepository.getInstance().forMonster("Sourhog"))
		{
			assertTrue(override.satisfiedBy(SLAYER_HELMET));
			assertTrue(override.satisfiedBy(SLAYER_HELMET_IMBUED));
		}
	}

	/** The reported bug, end to end: the optimiser itself must leave a worn V's shield alone. */
	@Test
	public void optimiser_leavesAWornVsShieldInPlace_withAMirrorShieldForSale()
	{
		int[] live = wearing(WhatIfLoadout.SHIELD_SLOT, VS_SHIELD);
		live[WhatIfLoadout.WEAPON_SLOT] = RUNE_SCIMITAR;
		Set<Integer> owned = setOf(VS_SHIELD, RUNE_SCIMITAR);

		GearOptimizer.Result result = GearOptimizer.optimize(GearOptimizer.Request
			.builder(live, MonsterRepository.getInstance().byName("Basilisk").get(), PlayerCombat.builder()
				.attack(99, 99).strength(99, 99).defence(99, 99).ranged(99, 99)
				.magic(99, 99).prayer(99, 99).hitpoints(99, 99))
			.budget(10_000_000L)
			.owned(owned)
			.priceSource(id -> id == MIRROR_SHIELD ? 1L : 100_000_000L)
			.style(CombatStyle.SLASH)
			.include(ItemEligibility.mandatoryOverrideItemIds(BASILISK, NO_EXCLUSIONS, false, owned, live))
			.build());

		for (GearOptimizer.SlotChoice choice : result.loadout())
		{
			if (choice.slotOrdinal() == WhatIfLoadout.SHIELD_SLOT)
			{
				assertEquals(VS_SHIELD, choice.itemId());
			}
		}
	}

	@Test
	public void wornItemWithNoIndexRow_doesNotShadowAnOwnedAlternative_ownedOnly()
	{
		assertEquals(Collections.singleton(SLAYER_HELMET_IMBUED), ItemEligibility.mandatoryOverrideItemIds(
			BANSHEE, NO_EXCLUSIONS, true, setOf(SLAYER_HELMET_IMBUED, SLAYER_HELMET), wearing(HEAD_SLOT, EARMUFFS)));
	}

	@Test
	public void wornItemWithNoIndexRow_doesNotShadowAnOwnedAlternative_notOwnedOnly()
	{
		assertEquals(Collections.singleton(SLAYER_HELMET_IMBUED), ItemEligibility.mandatoryOverrideItemIds(
			BANSHEE, NO_EXCLUSIONS, false, setOf(SLAYER_HELMET_IMBUED, SLAYER_HELMET), wearing(HEAD_SLOT, EARMUFFS)));
	}

	@Test
	public void theNoteStillNamesAWornItemWithNoIndexRow()
	{
		MonsterGearOverride requirement = MonsterGearOverrideRepository.getInstance().forMonster("Banshee").get(0);

		assertEquals(EARMUFFS, OwnedOnlyMandatoryOverrideGate.satisfyingItemId(requirement,
			wearing(HEAD_SLOT, EARMUFFS), setOf(EARMUFFS, SLAYER_HELMET_IMBUED), NO_EXCLUSIONS));
	}

	/**
	 * The search would rather wear a Helm of Neitiznot than a Slayer helmet
	 * off task, so the requirement only holds if the helmet is really forced.
	 */
	private static void assertOptimiserPutsTheSlayerHelmetOn(boolean ownedOnly)
	{
		MonsterGearOverride requirement = MonsterGearOverrideRepository.getInstance().forMonster("Banshee").get(0);
		int[] live = wearing(HEAD_SLOT, EARMUFFS);
		live[WhatIfLoadout.WEAPON_SLOT] = RUNE_SCIMITAR;
		Set<Integer> owned = setOf(SLAYER_HELMET_IMBUED, SLAYER_HELMET, HELM_OF_NEITIZNOT, RUNE_SCIMITAR);

		GearOptimizer.Result result = GearOptimizer.optimize(GearOptimizer.Request
			.builder(live, MonsterRepository.getInstance().byName("Banshee").get(), PlayerCombat.builder()
				.attack(99, 99).strength(99, 99).defence(99, 99).ranged(99, 99)
				.magic(99, 99).prayer(99, 99).hitpoints(99, 99))
			.budget(ownedOnly ? 0L : 10_000_000L)
			.owned(owned)
			.priceSource(id -> 100_000_000L)
			.style(CombatStyle.SLASH)
			.include(ItemEligibility.mandatoryOverrideItemIds(BANSHEE, NO_EXCLUSIONS, ownedOnly, owned, live))
			.build());

		Integer head = null;
		for (GearOptimizer.SlotChoice choice : result.loadout())
		{
			if (choice.slotOrdinal() == HEAD_SLOT)
			{
				head = choice.itemId();
			}
		}
		assertTrue("head " + head + " must satisfy the requirement", head != null && requirement.satisfiedBy(head));
		assertEquals(SLAYER_HELMET_IMBUED, head.intValue());
	}

	@Test
	public void optimiser_earmuffsWorn_putsTheOwnedSlayerHelmetOn_ownedOnly()
	{
		assertOptimiserPutsTheSlayerHelmetOn(true);
	}

	@Test
	public void optimiser_earmuffsWorn_putsTheOwnedSlayerHelmetOn_notOwnedOnly()
	{
		assertOptimiserPutsTheSlayerHelmetOn(false);
	}
}
