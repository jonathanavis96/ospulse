package com.ospulse.combat;

import java.util.*;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * One curated "don't forget" consumables/gear reminder for a monster — e.g.
 * "Zulrah poisons you — bring antivenom." Text is the payload; the optional
 * {@code equipmentItemIds()} only ever names items that are genuinely
 * equipment and therefore verifiable against {@link EquipmentIndexRepository}
 * (a ring, a shield). Inventory consumables (potions) are named in
 * {@code note()} prose, and — when the id has been verified against the
 * runelite-api jar's {@code ItemID} constants, not the equipment index — also
 * carried as an id in {@code consumableItemIds()}, a separately-validated
 * field because {@code equipment_index.min.json} indexes equipment alone. See
 * {@link MonsterConsumablesRepository} for how this is loaded/looked up and
 * that class's bundled resource README for provenance.
 */
@Accessors(fluent = true)
public final class MonsterConsumablesReminder {
    /** The advisory text shown verbatim in the panel — the whole payload for a consumables-only reminder. */
    @Getter private final String note;
    /**
     * Optional equipment item ids relevant to this reminder (e.g. a
     * dragonfire shield, an anti-dragon shield) — never a potion/consumable
     * id, since those cannot be verified against the equipment index. Empty
     * for a reminder with no equipment component.
     */
    @Getter private final Set<Integer> equipmentItemIds;
    /**
     * Optional inventory consumable item ids relevant to this reminder (a
     * potion dose, e.g. antivenom+ or antipoison) — verified against the
     * runelite-api jar's {@code net.runelite.api.gameval.ItemID} constants,
     * NOT the equipment index (potions aren't equipment). Empty for a
     * reminder with no verified consumable ids.
     */
    @Getter private final Set<Integer> consumableItemIds;

    MonsterConsumablesReminder(String note, Set<Integer> equipmentItemIds, Set<Integer> consumableItemIds) {
        this.note = note == null ? "" : note;
        this.equipmentItemIds = equipmentItemIds == null || equipmentItemIds.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(equipmentItemIds));
        this.consumableItemIds = consumableItemIds == null || consumableItemIds.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(consumableItemIds));
    }
}
