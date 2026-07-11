package com.ospulse.combat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private final Set<Integer> alternativeItemIds;

    MonsterGearOverride(String monsterName, Slot slot, int itemId, String itemName, String reason) {
        this(monsterName, slot, itemId, itemName, reason, Collections.emptySet());
    }

    /**
     * @param alternativeItemIds Other item ids that ALSO satisfy this
     * requirement just as well as {@code itemId} (e.g. a Slayer helmet
     * substitutes for a Dust devil's Facemask requirement — see review
     * finding 3). {@code itemId} itself never needs to be repeated here.
     */
    MonsterGearOverride(String monsterName, Slot slot, int itemId, String itemName, String reason,
                         Set<Integer> alternativeItemIds) {
        this.monsterName = monsterName;
        this.slot = slot;
        this.itemId = itemId;
        this.itemName = itemName;
        this.reason = reason;
        this.alternativeItemIds = alternativeItemIds == null || alternativeItemIds.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(alternativeItemIds));
    }

    /** The exact monster display name this entry was declared under (one name per expanded entry). */
    public String monsterName() {
        return monsterName;
    }

    public Slot slot() {
        return slot;
    }

    /** The primary/canonical item id — shown in the advisory note and what the optimiser force-includes. */
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

    /**
     * Other item ids that satisfy this requirement equally well as
     * {@link #itemId()} (e.g. every Slayer helmet variant substitutes for a
     * plain face-protection item like a Facemask) — see {@link #satisfiedBy}.
     * Empty when this requirement has no known substitutes.
     */
    public Set<Integer> alternativeItemIds() {
        return alternativeItemIds;
    }

    /** True when {@code shownId} is either the primary {@link #itemId()} or one of {@link #alternativeItemIds()}. */
    public boolean satisfiedBy(int shownId) {
        return shownId == itemId || alternativeItemIds.contains(shownId);
    }
}
