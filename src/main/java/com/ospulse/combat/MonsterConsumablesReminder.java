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
 * {@link #note()} prose only, never as an id, because
 * {@code equipment_index.min.json} indexes equipment alone and guessing a
 * consumable's item id from the wiki is exactly what this dataset's sibling
 * files forbid. See {@link MonsterConsumablesRepository} for how this is
 * loaded/looked up and that class's bundled resource README for provenance.
 */
public final class MonsterConsumablesReminder {
    private final String note;
    private final Set<Integer> equipmentItemIds;

    MonsterConsumablesReminder(String note, Set<Integer> equipmentItemIds) {
        this.note = note == null ? "" : note;
        this.equipmentItemIds = equipmentItemIds == null || equipmentItemIds.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(equipmentItemIds));
    }

    /** The advisory text shown verbatim in the panel — the whole payload for a consumables-only reminder. */
    public String note() {
        return note;
    }

    /**
     * Optional equipment item ids relevant to this reminder (e.g. a
     * dragonfire shield, an anti-dragon shield) — never a potion/consumable
     * id, since those cannot be verified against the equipment index. Empty
     * for a reminder that is entirely prose (e.g. Zulrah's antivenom note).
     */
    public Set<Integer> equipmentItemIds() {
        return equipmentItemIds;
    }
}
