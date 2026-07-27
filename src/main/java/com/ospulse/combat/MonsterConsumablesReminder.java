package com.ospulse.combat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One curated "don't forget" consumables/gear reminder for a monster — e.g.
 * "Zulrah poisons you — bring antivenom." Text is the payload; the optional
 * {@link #equipmentItemIds()} only ever names items that are genuinely
 * equipment and therefore verifiable against {@link EquipmentIndexRepository}
 * (a ring, a shield). Inventory consumables (potions) are named in
 * {@link #note()} prose, and — when the id has been verified against the
 * runelite-api jar's {@code ItemID} constants, not the equipment index — also
 * carried as an id in {@link #consumableItemIds()}, a separately-validated
 * field because {@code equipment_index.min.json} indexes equipment alone. See
 * {@link MonsterConsumablesRepository} for how this is loaded/looked up and
 * that class's bundled resource README for provenance.
 */
public final class MonsterConsumablesReminder {
    private final String note;
    private final Set<Integer> equipmentItemIds;
    private final Set<Integer> consumableItemIds;

    MonsterConsumablesReminder(String note, Set<Integer> equipmentItemIds, Set<Integer> consumableItemIds) {
        this.note = note == null ? "" : note;
        this.equipmentItemIds = equipmentItemIds == null || equipmentItemIds.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(equipmentItemIds));
        this.consumableItemIds = consumableItemIds == null || consumableItemIds.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(consumableItemIds));
    }

    /** The advisory text shown verbatim in the panel — the whole payload for a consumables-only reminder. */
    public String note() {
        return note;
    }

    /**
     * Optional equipment item ids relevant to this reminder (e.g. a
     * dragonfire shield, an anti-dragon shield) — never a potion/consumable
     * id, since those cannot be verified against the equipment index. Empty
     * for a reminder with no equipment component.
     */
    public Set<Integer> equipmentItemIds() {
        return equipmentItemIds;
    }

    /**
     * Optional inventory consumable item ids relevant to this reminder (a
     * potion dose, e.g. antivenom+ or antipoison) — verified against the
     * runelite-api jar's {@code net.runelite.api.gameval.ItemID} constants,
     * NOT the equipment index (potions aren't equipment). Empty for a
     * reminder with no verified consumable ids.
     */
    public Set<Integer> consumableItemIds() {
        return consumableItemIds;
    }
}
