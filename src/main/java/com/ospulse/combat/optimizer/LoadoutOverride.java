package com.ospulse.combat.optimizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A hypothetical per-slot item-id override on top of a live loadout — Phase 2's
 * "what-if" state (see the design spec section 2). Purely a
 * {@code slotOrdinal -> itemId} map; it is never mutated into the real
 * {@code GearSnapshot}, only merged with the live gear at compute time by
 * {@link WhatIfLoadout#apply}.
 *
 * <p>Immutable — every mutator returns a new instance, which keeps the
 * {@code GearSection} UI state simple to reason about (undo/redo would be
 * trivial if ever wanted) and makes this trivially unit-testable without any
 * Swing/RuneLite dependency.
 */
public final class LoadoutOverride {
    /**
     * Sentinel override value meaning "this slot is explicitly EMPTIED
     * (unequipped)" — distinct from having no override at all. Needed because
     * the override map can only <em>replace</em> a slot, so 2H/shield
     * exclusivity could not otherwise unequip a LIVE weapon/shield that has no
     * override entry (see {@link #withWeaponSlot}/{@link #withShieldSlot}). Real
     * item ids are always {@code > 0}, so {@code 0} is unambiguous.
     */
    public static final int EMPTIED = 0;

    private static final LoadoutOverride EMPTY = new LoadoutOverride(Collections.emptyMap());

    private final Map<Integer, Integer> bySlot;

    private LoadoutOverride(Map<Integer, Integer> bySlot) {
        this.bySlot = bySlot;
    }

    /** No overrides — the what-if loadout equals the live loadout. */
    public static LoadoutOverride empty() {
        return EMPTY;
    }

    /** True when no slot is overridden. */
    public boolean isEmpty() {
        return bySlot.isEmpty();
    }

    /**
     * The overridden item id for a slot, or {@code -1} if that slot is not
     * overridden. A return of {@code 0} ({@link #EMPTIED}) means the slot is
     * overridden to <em>empty</em> (explicitly unequipped) — callers already
     * treat {@code id <= 0} as an empty slot.
     */
    public int itemIdFor(int slotOrdinal) {
        Integer id = bySlot.get(slotOrdinal);
        return id == null ? -1 : id;
    }

    public boolean hasOverride(int slotOrdinal) {
        return bySlot.containsKey(slotOrdinal);
    }

    /**
     * Returns a new override with {@code slotOrdinal} set to {@code itemId}
     * (equip a hypothetical item in that slot). {@code itemId <= 0} clears the
     * slot back to "no override" — equivalent to {@link #withoutSlot}.
     */
    public LoadoutOverride withSlot(int slotOrdinal, int itemId) {
        if (itemId <= 0) {
            return withoutSlot(slotOrdinal);
        }
        Map<Integer, Integer> next = new LinkedHashMap<>(bySlot);
        next.put(slotOrdinal, itemId);
        return new LoadoutOverride(next);
    }

    /**
     * Returns a new override with {@code slotOrdinal} explicitly EMPTIED
     * (unequipped). Unlike {@link #withoutSlot} — which clears the override so
     * the LIVE item shows again — this forces the slot empty in the effective
     * loadout, which is what 2H/shield exclusivity needs to unequip a live
     * weapon/shield the override map could not otherwise reach.
     */
    public LoadoutOverride withEmptiedSlot(int slotOrdinal) {
        Map<Integer, Integer> next = new LinkedHashMap<>(bySlot);
        next.put(slotOrdinal, EMPTIED);
        return new LoadoutOverride(next);
    }

    /** Returns a new override with {@code slotOrdinal}'s override cleared ("reset slot" — live gear shows again). */
    public LoadoutOverride withoutSlot(int slotOrdinal) {
        if (!bySlot.containsKey(slotOrdinal)) {
            return this;
        }
        Map<Integer, Integer> next = new LinkedHashMap<>(bySlot);
        next.remove(slotOrdinal);
        return next.isEmpty() ? EMPTY : new LoadoutOverride(next);
    }

    /**
     * Two-handed weapon / shield exclusivity: equipping a 2H weapon in the
     * WEAPON slot must clear any SHIELD override (and vice versa — equipping a
     * shield while a 2H weapon is overridden must clear the weapon override),
     * matching in-game behaviour. {@code weaponIsTwoHanded} is resolved by the
     * caller (see {@link WhatIfLoadout#isTwoHanded}) since this class has no
     * item-data dependency.
     *
     * <p>A 2H weapon EMPTIES the shield slot ({@link #withEmptiedSlot}) rather
     * than merely clearing its override — otherwise a LIVE shield (with no
     * override entry) would survive into the effective loadout alongside the 2H
     * weapon, an impossible in-game combination whose summed bonuses inflate DPS.
     */
    public LoadoutOverride withWeaponSlot(int weaponSlotOrdinal, int shieldSlotOrdinal, int itemId, boolean itemIsTwoHanded) {
        LoadoutOverride next = withSlot(weaponSlotOrdinal, itemId);
        return itemIsTwoHanded ? next.withEmptiedSlot(shieldSlotOrdinal) : next;
    }

    /**
     * Shield-slot exclusivity counterpart: equipping ANY shield while the
     * current effective weapon (live-or-overridden) is two-handed must clear
     * the weapon override back to live gear, since a 2H weapon cannot coexist
     * with a shield. {@code currentWeaponIsTwoHanded} is resolved by the
     * caller from the CURRENT effective weapon (live gear, unless already
     * overridden).
     *
     * <p>Equipping a shield over a two-handed weapon EMPTIES the weapon slot
     * ({@link #withEmptiedSlot}) rather than clearing its override — otherwise a
     * LIVE 2H weapon (no override entry) would survive alongside the shield, the
     * exact bug where the optimiser recommended e.g. a buckler on top of a live
     * blowpipe and summed both sets of bonuses.
     */
    public LoadoutOverride withShieldSlot(int weaponSlotOrdinal, int shieldSlotOrdinal, int itemId, boolean currentWeaponIsTwoHanded) {
        LoadoutOverride next = withSlot(shieldSlotOrdinal, itemId);
        return currentWeaponIsTwoHanded ? next.withEmptiedSlot(weaponSlotOrdinal) : next;
    }

    /** All overridden slot ordinals (unspecified order — this is a value type, not a UI model). */
    public int[] overriddenSlots() {
        int[] out = new int[bySlot.size()];
        int i = 0;
        for (Integer slot : bySlot.keySet()) {
            out[i++] = slot;
        }
        Arrays.sort(out);
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LoadoutOverride)) {
            return false;
        }
        return bySlot.equals(((LoadoutOverride) o).bySlot);
    }

    @Override
    public int hashCode() {
        return bySlot.hashCode();
    }

    @Override
    public String toString() {
        return "LoadoutOverride" + bySlot;
    }
}
