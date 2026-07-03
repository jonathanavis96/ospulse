package com.ospulse.combat.optimizer;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.session.GearMapper;

/**
 * Merges a live loadout's worn item ids with a {@link LoadoutOverride} and
 * recomputes {@link EquipmentStats} for the resulting hypothetical loadout —
 * the Phase 2 "what-if per-slot swap" engine (design spec section 2). Pure
 * and EDT-safe: every lookup is bundled data (see
 * {@link BundledSlotStatsLookup}), so this never touches the real gear and
 * never requires the client thread.
 */
public final class WhatIfLoadout {
    /** {@code EquipmentInventorySlot.WEAPON} ordinal. */
    public static final int WEAPON_SLOT = 3;
    /** {@code EquipmentInventorySlot.SHIELD} ordinal. */
    public static final int SHIELD_SLOT = 5;

    private WhatIfLoadout() {
    }

    /**
     * The effective per-slot item ids: {@code liveItemIds} with every
     * overridden slot replaced by the override's item id. Never mutates
     * {@code liveItemIds}.
     */
    public static int[] effectiveItemIds(int[] liveItemIds, LoadoutOverride override) {
        int[] effective = liveItemIds.clone();
        for (int slot : override.overriddenSlots()) {
            if (slot >= 0 && slot < effective.length) {
                effective[slot] = override.itemIdFor(slot);
            }
        }
        return effective;
    }

    /**
     * Recomputes {@link EquipmentStats} for {@code liveItemIds} with
     * {@code override} applied, using only bundled data (no
     * {@code ItemManager}/client-thread requirement) — safe to call from the
     * Swing EDT.
     */
    public static EquipmentStats buildEquipmentStats(int[] liveItemIds, LoadoutOverride override) {
        int[] effective = effectiveItemIds(liveItemIds, override);
        return GearMapper.buildEquipmentStats(effective, WEAPON_SLOT, BundledSlotStatsLookup.INSTANCE);
    }

    /**
     * True if {@code itemId} is a two-handed weapon per the bundled equipment
     * index; {@code false} for an unknown/non-weapon id (bare fists, an
     * unindexed item). Used to enforce weapon/shield exclusivity when the
     * user overrides the weapon or shield slot in the UI.
     */
    public static boolean isTwoHanded(int itemId) {
        EquipmentIndexRepository.Entry entry = EquipmentIndexRepository.getInstance().entryFor(itemId);
        return entry != null && entry.isTwoHanded();
    }

    /**
     * Applies a weapon-slot swap to {@code override}, enforcing 2H/shield
     * exclusivity: equipping a two-handed weapon clears any shield override.
     */
    public static LoadoutOverride equipWeapon(LoadoutOverride override, int weaponItemId) {
        return override.withWeaponSlot(WEAPON_SLOT, SHIELD_SLOT, weaponItemId, isTwoHanded(weaponItemId));
    }

    /**
     * Applies a shield-slot swap to {@code override}, enforcing 2H/shield
     * exclusivity: equipping a shield while the CURRENT effective weapon
     * (override if set, else the live weapon id) is two-handed clears the
     * weapon override back to live gear (matching in-game unequip behaviour).
     */
    public static LoadoutOverride equipShield(LoadoutOverride override, int[] liveItemIds, int shieldItemId) {
        int currentWeaponId = override.hasOverride(WEAPON_SLOT)
                ? override.itemIdFor(WEAPON_SLOT)
                : (WEAPON_SLOT < liveItemIds.length ? liveItemIds[WEAPON_SLOT] : -1);
        return override.withShieldSlot(WEAPON_SLOT, SHIELD_SLOT, shieldItemId, isTwoHanded(currentWeaponId));
    }
}
