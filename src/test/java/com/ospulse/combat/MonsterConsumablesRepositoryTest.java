package com.ospulse.combat;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonsterConsumablesRepositoryTest
{
	static { BundledGson.set(new com.google.gson.Gson()); }

	@Test public void loadsZulrahAsAPureProseReminder()
	{
		Optional<MonsterConsumablesReminder> r = MonsterConsumablesRepository.getInstance().forMonster("Zulrah");
		assertTrue(r.isPresent());
		assertTrue("Zulrah's note must mention antivenom",
			r.get().note().toLowerCase(java.util.Locale.ROOT).contains("antivenom"));
		assertTrue("Zulrah's reminder is text-only — no verifiable equipment id exists for it",
			r.get().equipmentItemIds().isEmpty());
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

	/** A metal dragon's reminder must warn that Protect from Magic alone is not enough. */
	@Test public void metalDragonNoteWarnsProtectFromMagicIsNotEnough()
	{
		Optional<MonsterConsumablesReminder> r = MonsterConsumablesRepository.getInstance().forMonster("Rune dragon");
		assertTrue(r.isPresent());
		assertEquals("Metal dragons' dragonfire pierces Protect from Magic — a dragonfire shield/ward or a super antifire potion is mandatory here, not just a prayer.",
			r.get().note());
	}

	/** A parse failure would leave an empty map and make every other assertion vacuous. */
	@Test public void datasetActuallyLoaded()
	{
		assertFalse("the curated dataset is empty — did it fail to parse?",
			MonsterConsumablesRepository.getInstance().curatedKeys().isEmpty());
	}
}
