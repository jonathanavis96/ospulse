package com.ospulse.combat;

/**
 * One curated monster-mechanic gear requirement: a specific item that matters
 * for a MECHANICS reason (special-attack mitigation, safespotting, immunity,
 * etc.) rather than raw DPS — the kind of thing the DPS optimiser would never
 * surface on its own. See {@link MonsterGearOverrideRepository} for how this
 * is loaded and looked up, and that class's bundled resource README for the
 * data shape/provenance.
 */
public final class MonsterGearOverride {
    /**
     * The {@code net.runelite.api.EquipmentInventorySlot} this override
     * applies to, named (not the raw ordinal) for readability in the bundled
     * JSON. Ordinals mirror {@link EquipmentIndexRepository}'s README.
     */
    public enum Slot {
        HEAD(0),
        CAPE(1),
        AMULET(2),
        WEAPON(3),
        BODY(4),
        SHIELD(5),
        LEGS(7),
        GLOVES(9),
        BOOTS(10),
        RING(12),
        AMMO(13);

        private final int slotOrdinal;

        Slot(int slotOrdinal) {
            this.slotOrdinal = slotOrdinal;
        }

        /** The {@code EquipmentInventorySlot} ordinal this named slot maps to. */
        public int slotOrdinal() {
            return slotOrdinal;
        }
    }

    private final String monsterName;
    private final Slot slot;
    private final int itemId;
    private final String itemName;
    private final String reason;

    MonsterGearOverride(String monsterName, Slot slot, int itemId, String itemName, String reason) {
        this.monsterName = monsterName;
        this.slot = slot;
        this.itemId = itemId;
        this.itemName = itemName;
        this.reason = reason;
    }

    /** The exact monster display name this entry was declared under (one name per expanded entry). */
    public String monsterName() {
        return monsterName;
    }

    public Slot slot() {
        return slot;
    }

    public int itemId() {
        return itemId;
    }

    public String itemName() {
        return itemName;
    }

    /** One short sentence explaining why this item matters (shown verbatim in the advisory note). */
    public String reason() {
        return reason;
    }
}
