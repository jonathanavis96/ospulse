package com.ospulse.combat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Locale;
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
