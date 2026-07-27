package com.ospulse.combat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

/**
 * Guards the hand-curated {@code monster_consumables.json}, mirroring {@link
 * MonsterCombatRequirementDataTest}.
 *
 * <p>Every monster key must resolve against the bundled monster data and
 * every {@code equipmentItemIds} entry against the bundled equipment index —
 * both failure modes are silent in production (a typo'd name simply never
 * matches, so the reminder stops applying with no error anywhere), which is
 * exactly why this test exists.
 */
public class MonsterConsumablesDataTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /**
     * Every curated key must match a bundled monster, either exactly or via
     * the base-name fallback {@code forMonster} uses.
     */
    @Test
    public void everyCuratedMonsterNameResolves() {
        Set<String> exactNames = new HashSet<>();
        Set<String> baseNames = new HashSet<>();
        for (Monster monster : MonsterRepository.getInstance().all()) {
            String name = monster.name().toLowerCase(Locale.ROOT);
            exactNames.add(name);
            baseNames.add(MonsterConsumablesRepository.baseNameOf(name));
        }

        for (String key : MonsterConsumablesRepository.getInstance().curatedKeys()) {
            assertTrue(
                "curated monster name matches no bundled monster: '" + key + "'",
                exactNames.contains(key) || baseNames.contains(key));
        }
    }

    /** Every equipment item id must exist in the bundled equipment index — never guessed from the wiki. */
    @Test
    public void everyEquipmentItemIdResolves() {
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
        for (MonsterConsumablesReminder reminder : MonsterConsumablesRepository.getInstance().allReminders()) {
            for (Integer id : reminder.equipmentItemIds()) {
                assertTrue("equipment item id not in the equipment index: " + id,
                    index.entryFor(id) != null);
            }
        }
    }

    /**
     * Every {@code consumableItemIds} entry must be a positive int — this is
     * NOT checked against {@link EquipmentIndexRepository} (potions aren't
     * equipment; that index has no rows for them), only that the value is
     * plausible. Deliberately separate from {@link #everyEquipmentItemIdResolves()}
     * so the two verification channels never get conflated.
     */
    @Test
    public void everyConsumableItemIdIsAPositiveInt() {
        for (MonsterConsumablesReminder reminder : MonsterConsumablesRepository.getInstance().allReminders()) {
            for (Integer id : reminder.consumableItemIds()) {
                assertTrue("consumable item id must be a positive int: " + id, id != null && id > 0);
            }
        }
    }

    /**
     * The six monsters whose reminders were prose-only before {@code
     * consumableItemIds} was added (Zulrah, Alchemical Hydra, Abyssal Sire,
     * K'ril Tsutsaroth, Nex, Cerberus) must each now carry at least one
     * consumable id, so this gap cannot silently regress to "nothing shows in
     * the bank".
     */
    @Test
    public void previouslyProseOnlyMonsters_nowHaveConsumableItemIds() {
        MonsterConsumablesRepository repo = MonsterConsumablesRepository.getInstance();
        String[] monsters = {
            "Zulrah", "Alchemical Hydra (Fire)", "Abyssal Sire (Phase 1)",
            "K'ril Tsutsaroth", "Nex", "Cerberus"
        };
        for (String monster : monsters) {
            Optional<MonsterConsumablesReminder> reminder = repo.forMonster(monster);
            assertTrue(monster + " must have a curated reminder", reminder.isPresent());
            assertFalse(monster + " must have at least one consumable item id",
                reminder.get().consumableItemIds().isEmpty());
        }
    }

    /** Every entry must carry a note — it is the whole payload for a text-only reminder. */
    @Test
    public void everyReminderHasANote() {
        for (MonsterConsumablesReminder reminder : MonsterConsumablesRepository.getInstance().allReminders()) {
            String note = reminder.note();
            assertTrue("reminder is missing its explanatory note",
                note != null && !note.trim().isEmpty());
        }
    }

    /** A parse failure would leave an empty map and make every other assertion vacuous. */
    @Test
    public void datasetActuallyLoaded() {
        assertFalse("the curated dataset is empty — did it fail to parse?",
            MonsterConsumablesRepository.getInstance().curatedKeys().isEmpty());
    }
}
