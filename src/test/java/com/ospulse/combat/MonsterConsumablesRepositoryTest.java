package com.ospulse.combat;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonsterConsumablesRepositoryTest
{
	static { BundledGson.set(new com.google.gson.Gson()); }

	@Test public void loadsZulrahWithSerpentineHelmAndAntivenomIds()
	{
		Optional<MonsterConsumablesReminder> r = MonsterConsumablesRepository.getInstance().forMonster("Zulrah");
		assertTrue(r.isPresent());
		assertTrue("Zulrah's note must mention antivenom",
			r.get().note().toLowerCase(java.util.Locale.ROOT).contains("antivenom"));
		assertTrue("Zulrah's serpentine helm alternative is verified equipment (12931)",
			r.get().equipmentItemIds().contains(12931));
		assertTrue("Zulrah's antivenom+ consumable ids must be present",
			r.get().consumableItemIds().containsAll(
				java.util.Arrays.asList(12913, 12915, 12917, 12919, 29824, 29827, 29830, 29833)));
	}

	@Test public void loadsVorkathWithVerifiedEquipmentIds()
	{
		Optional<MonsterConsumablesReminder> r = MonsterConsumablesRepository.getInstance().forMonster("Vorkath");
		assertTrue(r.isPresent());
		// Anti-dragon shield, verified via equipment_index.min.json.
		assertTrue(r.get().equipmentItemIds().contains(1540));
	}

	@Test public void unknownMonsterEmpty()
	{
		assertFalse(MonsterConsumablesRepository.getInstance().forMonster("Cow").isPresent());
	}

	@Test public void nullSafe()
	{
		assertFalse(MonsterConsumablesRepository.getInstance().forMonster(null).isPresent());
	}

	/**
	 * The monster picker hands us dataset display names carrying a trailing
	 * non-numeric "(…)" variant marker — the lookup must resolve those to the
	 * base reminder, exactly like {@link MonsterCombatRequirementRepository}.
	 */
	@Test public void resolvesDatasetVariantNamesWithTrailingParenthetical()
	{
		MonsterConsumablesRepository repo = MonsterConsumablesRepository.getInstance();

		assertTrue("Zulrah (Serpentine) must resolve to the Zulrah reminder",
			repo.forMonster("Zulrah (Serpentine)").isPresent());
		assertTrue("Zulrah (Magma) must resolve", repo.forMonster("Zulrah (Magma)").isPresent());
		assertTrue("Zulrah (Tanzanite) must resolve", repo.forMonster("Zulrah (Tanzanite)").isPresent());

		assertTrue("Vorkath (Dragon Slayer II) must resolve to the Vorkath reminder",
			repo.forMonster("Vorkath (Dragon Slayer II)").isPresent());
		assertTrue("Vorkath (Post-quest) must resolve", repo.forMonster("Vorkath (Post-quest)").isPresent());

		// A suffixed name whose base is not curated must still miss.
		assertFalse(repo.forMonster("Cow (Wilderness)").isPresent());
	}

	/** The nested-parenthetical Steel dragon task-only variant needs its own exact key — base-name stripping cannot reach it. */
	@Test public void steelDragonNestedParentheticalVariantResolvesViaExactKey()
	{
		MonsterConsumablesRepository repo = MonsterConsumablesRepository.getInstance();
		assertTrue("the plain-suffixed Steel dragon variants resolve via base name",
			repo.forMonster("Steel dragon (Level 246)").isPresent());
		assertTrue("the nested-parenthetical variant must resolve via its own exact curated key",
			repo.forMonster("Steel dragon (Level 246 (Task only))").isPresent());
	}

	/** Case-insensitive lookup, matching every sibling repository's convention. */
	@Test public void lookupIsCaseInsensitive()
	{
		assertTrue(MonsterConsumablesRepository.getInstance().forMonster("zULRAH").isPresent());
	}

	/**
	 * A metal dragon's reminder must warn that Protect from Magic alone does
	 * nothing, and must include the anti-dragon shield — per the OSRS Wiki's
	 * Dragonfire damage-reduction table, anti-dragon shield + an ordinary
	 * antifire potion is a fully-protective (0 max hit) combo against
	 * metallic dragons, not just the upgraded dragonfire shield/ward or
	 * super antifire potion.
	 */
	@Test public void metalDragonNoteWarnsProtectFromMagicDoesNothing()
	{
		Optional<MonsterConsumablesReminder> r = MonsterConsumablesRepository.getInstance().forMonster("Rune dragon");
		assertTrue(r.isPresent());
		assertEquals("Metal dragons' dragonfire pierces Protect from Magic, so the prayer alone does nothing. A super antifire potion gives full protection by itself, and an ordinary antifire potion paired with an anti-dragon shield, dragonfire shield, or dragonfire ward does too.",
			r.get().note());
		assertTrue("anti-dragon shield (1540) is a fully-protective combo with an ordinary antifire potion, not just the upgraded shield/potion",
			r.get().equipmentItemIds().contains(1540));
	}

	/**
	 * Baby dragons (red/blue/green/black) are visually similar to their adult
	 * and brutal counterparts, whose dragonfire reminder is keyed by base
	 * name — but per the OSRS Wiki, baby dragons "do not breathe fire" and
	 * must NOT inherit that reminder. Uses the exact bundled dataset names
	 * from monsters.min.json.gz.
	 */
	@Test public void babyDragons_doNotResolveTheDragonfireReminder()
	{
		MonsterConsumablesRepository repo = MonsterConsumablesRepository.getInstance();
		assertFalse("Baby red dragon does not breathe dragonfire", repo.forMonster("Baby red dragon (1)").isPresent());
		assertFalse("Baby blue dragon does not breathe dragonfire", repo.forMonster("Baby blue dragon (1)").isPresent());
		assertFalse("Baby green dragon does not breathe dragonfire", repo.forMonster("Baby green dragon (1)").isPresent());
		assertFalse("Baby black dragon does not breathe dragonfire", repo.forMonster("Baby black dragon (Normal)").isPresent());
	}

	/** A parse failure would leave an empty map and make every other assertion vacuous. */
	@Test public void datasetActuallyLoaded()
	{
		assertFalse("the curated dataset is empty — did it fail to parse?",
			MonsterConsumablesRepository.getInstance().curatedKeys().isEmpty());
	}

	/**
	 * Round-2 poison/venom entries recovered after the first pass's research
	 * file was found to have been truncated before its poison/venom table.
	 * All four Alchemical Hydra phase names are keyed explicitly (not relying
	 * on a shared base) and every one must resolve to the same reminder.
	 */
	@Test public void alchemicalHydra_everyPhaseResolvesToTheVenomReminder()
	{
		MonsterConsumablesRepository repo = MonsterConsumablesRepository.getInstance();
		String[] phases = {
			"Alchemical Hydra (Electric)", "Alchemical Hydra (Extinguished)",
			"Alchemical Hydra (Fire)", "Alchemical Hydra (Serpentine)"
		};
		for (String phase : phases)
		{
			Optional<MonsterConsumablesReminder> r = repo.forMonster(phase);
			assertTrue(phase + " must resolve", r.isPresent());
			assertTrue(phase + "'s note must mention antivenom+",
				r.get().note().toLowerCase(java.util.Locale.ROOT).contains("antivenom+"));
			assertTrue(phase + "'s reminder has no equipment component", r.get().equipmentItemIds().isEmpty());
			assertFalse(phase + "'s reminder must carry antivenom+ consumable ids",
				r.get().consumableItemIds().isEmpty());
		}
	}

	/**
	 * Abyssal Sire's four phase names are the nested-parenthetical trap:
	 * "(Phase 1)"/"(Phase 2)" strip to a shared base via
	 * {@code MonsterNameKey.baseName}, but "(Phase 3 (stage 1))"/"(stage 2))"
	 * do NOT (the regex can't strip nested parens), so every phase is keyed
	 * explicitly rather than relying on any fallback. {@code
	 * "Tentacle (Abyssal Sire)"} is a different bundled monster and must
	 * never match.
	 */
	@Test public void abyssalSire_everyPhaseResolvesButTheTentacleDoesNot()
	{
		MonsterConsumablesRepository repo = MonsterConsumablesRepository.getInstance();
		String[] phases = {
			"Abyssal Sire (Phase 1)", "Abyssal Sire (Phase 2)",
			"Abyssal Sire (Phase 3 (stage 1))", "Abyssal Sire (Phase 3 (stage 2))"
		};
		for (String phase : phases)
		{
			Optional<MonsterConsumablesReminder> r = repo.forMonster(phase);
			assertTrue(phase + " must resolve", r.isPresent());
			assertFalse(phase + " must carry antipoison consumable ids", r.get().consumableItemIds().isEmpty());
		}
		assertFalse("Tentacle (Abyssal Sire) is a different monster and must not match",
			repo.forMonster("Tentacle (Abyssal Sire)").isPresent());
	}

	@Test public void krilTsutsaroth_warnsPoisonPiercesProtectFromMelee()
	{
		Optional<MonsterConsumablesReminder> r =
			MonsterConsumablesRepository.getInstance().forMonster("K'ril Tsutsaroth");
		assertTrue(r.isPresent());
		assertTrue(r.get().note().contains("Protect from Melee"));
		assertTrue(r.get().equipmentItemIds().isEmpty());
		assertFalse("must carry antidote++/sanfew salve consumable ids", r.get().consumableItemIds().isEmpty());
	}

	/** Nex's Smoke-phase poison reminder must not leak onto the visually-similar Blood Reaver in her chamber. */
	@Test public void nex_hasAReminder_butBloodReaverInHerChamberDoesNot()
	{
		MonsterConsumablesRepository repo = MonsterConsumablesRepository.getInstance();
		Optional<MonsterConsumablesReminder> nex = repo.forMonster("Nex");
		assertTrue(nex.isPresent());
		assertTrue(nex.get().note().toLowerCase(java.util.Locale.ROOT).contains("smoke"));
		assertFalse("Nex must carry antipoison/antidote++ consumable ids", nex.get().consumableItemIds().isEmpty());

		assertFalse("Blood Reaver (Nex's chamber) is a different monster and must not match Nex's reminder",
			repo.forMonster("Blood Reaver (Nex's chamber)").isPresent());
	}

	/** Cerberus's note must put the hazard on the tunnel-spider approach, not the boss herself. */
	@Test public void cerberus_notePlacesTheHazardOnTheApproachNotTheBoss()
	{
		Optional<MonsterConsumablesReminder> r = MonsterConsumablesRepository.getInstance().forMonster("Cerberus");
		assertTrue(r.isPresent());
		assertTrue(r.get().note().contains("Cerberus herself does not poison you"));
		assertTrue(r.get().equipmentItemIds().isEmpty());
		assertFalse("Cerberus must carry antipoison consumable ids", r.get().consumableItemIds().isEmpty());
	}
}
