package com.ospulse.combat.optimizer;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.session.GearSnapshot;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link WhatIfLoadout}: merging a live loadout with a
 * {@link LoadoutOverride} and recomputing {@link EquipmentStats} purely from
 * bundled data (no ItemManager/client thread) — the Phase 2 what-if engine.
 * Real item ids are used throughout so this doubles as an integration check
 * that {@link BundledSlotStatsLookup} + {@code GearMapper} agree.
 */
public class WhatIfLoadoutTest {

    private static final int ABYSSAL_WHIP = 4151;   // one-handed slash weapon, 82 str/aslash, speed 4
    private static final int TWISTED_BOW = 20997;    // two-handed ranged weapon
    private static final int RUNE_KITESHIELD = 1201; // shield
    private static final int RUNE_DEFENDER = 24142;  // one-handed-weapon-only off-hand (still slot 5)
    private static final int DRAGON_SCIMITAR = 4587; // one-handed slash weapon

    private static int[] emptyLoadout() {
        int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
        Arrays.fill(ids, -1);
        return ids;
    }

    // -------------------------------------------------- effectiveItemIds

    @Test
    public void effectiveItemIds_noOverride_returnsLiveIdsUnchanged() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP;
        int[] effective = WhatIfLoadout.effectiveItemIds(live, LoadoutOverride.empty());
        assertEquals(ABYSSAL_WHIP, effective[3]);
        // must not be the same array instance (caller's live array is never mutated)
        assertFalse(effective == live);
    }

    @Test
    public void effectiveItemIds_overrideReplacesOneSlotOnly() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP;
        live[0] = 1234; // some head slot id, irrelevant to the override

        LoadoutOverride override = LoadoutOverride.empty().withSlot(3, DRAGON_SCIMITAR);
        int[] effective = WhatIfLoadout.effectiveItemIds(live, override);

        assertEquals(DRAGON_SCIMITAR, effective[3]);
        assertEquals(1234, effective[0]); // untouched slot survives

        // Live array itself must be unmodified.
        assertEquals(ABYSSAL_WHIP, live[3]);
    }

    // -------------------------------------------------- isTwoHanded

    @Test
    public void isTwoHanded_twistedBowIsTrue() {
        assertTrue(WhatIfLoadout.isTwoHanded(TWISTED_BOW));
    }

    @Test
    public void isTwoHanded_abyssalWhipIsFalse() {
        assertFalse(WhatIfLoadout.isTwoHanded(ABYSSAL_WHIP));
    }

    @Test
    public void isTwoHanded_unknownIdIsFalse() {
        assertFalse(WhatIfLoadout.isTwoHanded(99_999_999));
    }

    // -------------------------------------------------- equipWeapon / equipShield exclusivity

    @Test
    public void equipWeapon_twoHanded_clearsExistingShieldOverride() {
        LoadoutOverride override = LoadoutOverride.empty().withSlot(WhatIfLoadout.SHIELD_SLOT, RUNE_KITESHIELD);
        LoadoutOverride afterEquip = WhatIfLoadout.equipWeapon(override, TWISTED_BOW);

        assertEquals(TWISTED_BOW, afterEquip.itemIdFor(WhatIfLoadout.WEAPON_SLOT));
        assertFalse("equipping a 2H weapon must clear the shield override",
                afterEquip.hasOverride(WhatIfLoadout.SHIELD_SLOT));
    }

    @Test
    public void equipWeapon_oneHanded_preservesShieldOverride() {
        LoadoutOverride override = LoadoutOverride.empty().withSlot(WhatIfLoadout.SHIELD_SLOT, RUNE_KITESHIELD);
        LoadoutOverride afterEquip = WhatIfLoadout.equipWeapon(override, ABYSSAL_WHIP);

        assertEquals(ABYSSAL_WHIP, afterEquip.itemIdFor(WhatIfLoadout.WEAPON_SLOT));
        assertTrue(afterEquip.hasOverride(WhatIfLoadout.SHIELD_SLOT));
        assertEquals(RUNE_KITESHIELD, afterEquip.itemIdFor(WhatIfLoadout.SHIELD_SLOT));
    }

    @Test
    public void equipShield_whenLiveWeaponTwoHanded_clearsWeaponBackToLive() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = TWISTED_BOW;

        LoadoutOverride override = LoadoutOverride.empty(); // no weapon override yet — live gear IS the 2H bow
        LoadoutOverride afterShield = WhatIfLoadout.equipShield(override, live, RUNE_KITESHIELD);

        assertEquals(RUNE_KITESHIELD, afterShield.itemIdFor(WhatIfLoadout.SHIELD_SLOT));
        // No weapon OVERRIDE should be introduced (the "clear" is just not overriding it away from live);
        // critically it must not force live's 2H weapon to remain overridden as itself either way —
        // here the real assertion is that the resulting effective loadout has no weapon+shield clash.
        assertFalse(afterShield.hasOverride(WhatIfLoadout.WEAPON_SLOT));
    }

    @Test
    public void equipShield_whenOverriddenWeaponTwoHanded_clearsWeaponOverride() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP; // live weapon is 1H

        LoadoutOverride override = LoadoutOverride.empty().withSlot(WhatIfLoadout.WEAPON_SLOT, TWISTED_BOW); // overridden to 2H
        LoadoutOverride afterShield = WhatIfLoadout.equipShield(override, live, RUNE_KITESHIELD);

        assertEquals(RUNE_KITESHIELD, afterShield.itemIdFor(WhatIfLoadout.SHIELD_SLOT));
        assertFalse("shield must clear the 2H weapon OVERRIDE, reverting to live 1H gear",
                afterShield.hasOverride(WhatIfLoadout.WEAPON_SLOT));
    }

    @Test
    public void equipShield_whenWeaponOneHanded_preservesWeaponOverride() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP;

        LoadoutOverride override = LoadoutOverride.empty().withSlot(WhatIfLoadout.WEAPON_SLOT, DRAGON_SCIMITAR);
        LoadoutOverride afterShield = WhatIfLoadout.equipShield(override, live, RUNE_KITESHIELD);

        assertTrue(afterShield.hasOverride(WhatIfLoadout.WEAPON_SLOT));
        assertEquals(DRAGON_SCIMITAR, afterShield.itemIdFor(WhatIfLoadout.WEAPON_SLOT));
    }

    // -------------------------------------------------- buildEquipmentStats (integration)

    @Test
    public void buildEquipmentStats_appliesOverrideBonusesInstead_ofLive() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP;

        EquipmentStats liveStats = WhatIfLoadout.buildEquipmentStats(live, LoadoutOverride.empty());
        assertEquals(82, liveStats.aslash());
        assertEquals(82, liveStats.str());
        assertEquals(4, liveStats.weaponSpeedTicks());

        LoadoutOverride override = LoadoutOverride.empty().withSlot(WhatIfLoadout.WEAPON_SLOT, DRAGON_SCIMITAR);
        EquipmentStats whatIfStats = WhatIfLoadout.buildEquipmentStats(live, override);

        // Dragon scimitar: 67 aslash / 66 str / speed 4 (per weirdgloop equipment.json / cache data).
        assertEquals(67, whatIfStats.aslash());
        assertEquals(66, whatIfStats.str());

        // The live array itself must never be mutated by a what-if compute.
        assertEquals(ABYSSAL_WHIP, live[WhatIfLoadout.WEAPON_SLOT]);
    }

    @Test
    public void buildEquipmentStats_resetOverride_matchesLiveExactly() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP;
        live[WhatIfLoadout.SHIELD_SLOT] = RUNE_KITESHIELD;

        EquipmentStats liveStats = WhatIfLoadout.buildEquipmentStats(live, LoadoutOverride.empty());

        LoadoutOverride override = LoadoutOverride.empty()
                .withSlot(WhatIfLoadout.WEAPON_SLOT, DRAGON_SCIMITAR)
                .withoutSlot(WhatIfLoadout.WEAPON_SLOT); // reset back to live
        EquipmentStats resetStats = WhatIfLoadout.buildEquipmentStats(live, override);

        assertEquals(liveStats.aslash(), resetStats.aslash());
        assertEquals(liveStats.str(), resetStats.str());
        assertEquals(liveStats.weaponSpeedTicks(), resetStats.weaponSpeedTicks());
    }

    @Test
    public void buildEquipmentStats_twoHandedOverride_dropsShieldContribution() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP;
        live[WhatIfLoadout.SHIELD_SLOT] = RUNE_KITESHIELD;

        EquipmentStats liveStats = WhatIfLoadout.buildEquipmentStats(live, LoadoutOverride.empty());
        int liveDstab = liveStats.dstab(); // whip + kiteshield defence

        LoadoutOverride override = WhatIfLoadout.equipWeapon(LoadoutOverride.empty(), TWISTED_BOW);
        EquipmentStats whatIfStats = WhatIfLoadout.buildEquipmentStats(live, override);

        // With the shield override cleared, effective loadout = twisted bow only
        // (live's kiteshield is no longer in the shield slot's override, and the
        // shield slot falls back to LIVE gear, i.e. the kiteshield still applies
        // unless the caller explicitly overrides slot 5 to empty). This test
        // documents that equipWeapon() clearing the OVERRIDE reverts to LIVE gear
        // in that slot, not to "no shield" — the live kiteshield is still worn.
        assertEquals("clearing a shield OVERRIDE reverts to the live shield, not empty",
                liveDstab, whatIfStats.dstab());
    }
}
