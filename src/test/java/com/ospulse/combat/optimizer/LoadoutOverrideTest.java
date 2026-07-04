package com.ospulse.combat.optimizer;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure unit tests for {@link LoadoutOverride}: the immutable per-slot
 * what-if map, independent of any real item data (2H/shield exclusivity that
 * DOES depend on real item ids is covered in {@link WhatIfLoadoutTest}).
 */
public class LoadoutOverrideTest {

    @Test
    public void emptyOverride_hasNoSlots() {
        LoadoutOverride override = LoadoutOverride.empty();
        assertTrue(override.isEmpty());
        assertEquals(-1, override.itemIdFor(3));
        assertFalse(override.hasOverride(3));
        assertArrayEquals(new int[0], override.overriddenSlots());
    }

    @Test
    public void withSlot_addsAnOverride_immutably() {
        LoadoutOverride base = LoadoutOverride.empty();
        LoadoutOverride withWeapon = base.withSlot(3, 4151);

        // base is unchanged (immutability).
        assertTrue(base.isEmpty());

        assertFalse(withWeapon.isEmpty());
        assertTrue(withWeapon.hasOverride(3));
        assertEquals(4151, withWeapon.itemIdFor(3));
        assertArrayEquals(new int[] {3}, withWeapon.overriddenSlots());
    }

    @Test
    public void withSlot_multipleSlotsAccumulate() {
        LoadoutOverride override = LoadoutOverride.empty()
                .withSlot(3, 4151)
                .withSlot(0, 1234);
        assertEquals(4151, override.itemIdFor(3));
        assertEquals(1234, override.itemIdFor(0));
        assertArrayEquals(new int[] {0, 3}, override.overriddenSlots());
    }

    @Test
    public void withSlot_zeroOrNegativeItemId_clearsSlot() {
        LoadoutOverride override = LoadoutOverride.empty().withSlot(3, 4151);
        assertTrue(override.hasOverride(3));

        LoadoutOverride cleared = override.withSlot(3, 0);
        assertFalse(cleared.hasOverride(3));
        assertTrue(cleared.isEmpty());

        LoadoutOverride clearedNegative = override.withSlot(3, -1);
        assertTrue(clearedNegative.isEmpty());
    }

    @Test
    public void withoutSlot_resetsOneSlotOnly() {
        LoadoutOverride override = LoadoutOverride.empty()
                .withSlot(3, 4151)
                .withSlot(0, 1234);
        LoadoutOverride reset = override.withoutSlot(3);
        assertFalse(reset.hasOverride(3));
        assertTrue(reset.hasOverride(0));
        assertEquals(1234, reset.itemIdFor(0));
    }

    @Test
    public void withoutSlot_onUnoverriddenSlot_isNoOp() {
        LoadoutOverride override = LoadoutOverride.empty().withSlot(3, 4151);
        LoadoutOverride same = override.withoutSlot(0);
        assertEquals(override, same);
    }

    @Test
    public void withoutSlot_lastSlotReturnsEmptySingleton() {
        LoadoutOverride override = LoadoutOverride.empty().withSlot(3, 4151);
        LoadoutOverride reset = override.withoutSlot(3);
        assertTrue(reset.isEmpty());
        assertEquals(LoadoutOverride.empty(), reset);
    }

    @Test
    public void equalsAndHashCode_matchOnContent() {
        LoadoutOverride a = LoadoutOverride.empty().withSlot(3, 4151).withSlot(0, 10);
        LoadoutOverride b = LoadoutOverride.empty().withSlot(0, 10).withSlot(3, 4151);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void withWeaponSlot_twoHandedEmptiesShield() {
        LoadoutOverride base = LoadoutOverride.empty().withSlot(5, 1201); // shield override present
        LoadoutOverride afterTwoHandedWeapon = base.withWeaponSlot(3, 5, 20997, true);
        assertEquals(20997, afterTwoHandedWeapon.itemIdFor(3));
        // A 2H weapon EMPTIES the shield slot (unequips a live shield too), not merely
        // clears the override — so the slot stays overridden, to EMPTIED.
        assertTrue("2H weapon must empty the shield slot", afterTwoHandedWeapon.hasOverride(5));
        assertEquals(LoadoutOverride.EMPTIED, afterTwoHandedWeapon.itemIdFor(5));
    }

    @Test
    public void withWeaponSlot_oneHandedKeepsShield() {
        LoadoutOverride base = LoadoutOverride.empty().withSlot(5, 1201);
        LoadoutOverride afterOneHandedWeapon = base.withWeaponSlot(3, 5, 4151, false);
        assertEquals(4151, afterOneHandedWeapon.itemIdFor(3));
        assertTrue("1H weapon must not disturb the shield override", afterOneHandedWeapon.hasOverride(5));
        assertEquals(1201, afterOneHandedWeapon.itemIdFor(5));
    }

    @Test
    public void withShieldSlot_whenCurrentWeaponTwoHanded_emptiesWeapon() {
        LoadoutOverride base = LoadoutOverride.empty().withSlot(3, 20997); // 2H weapon override present
        LoadoutOverride afterShield = base.withShieldSlot(3, 5, 1201, true);
        assertEquals(1201, afterShield.itemIdFor(5));
        // Equipping a shield over a 2H weapon EMPTIES the weapon slot (unequips a live
        // 2H weapon too), so the slot stays overridden, to EMPTIED.
        assertTrue("equipping a shield must empty a 2H weapon", afterShield.hasOverride(3));
        assertEquals(LoadoutOverride.EMPTIED, afterShield.itemIdFor(3));
    }

    @Test
    public void withShieldSlot_whenCurrentWeaponOneHanded_keepsWeapon() {
        LoadoutOverride base = LoadoutOverride.empty().withSlot(3, 4151);
        LoadoutOverride afterShield = base.withShieldSlot(3, 5, 1201, false);
        assertEquals(1201, afterShield.itemIdFor(5));
        assertTrue(afterShield.hasOverride(3));
    }
}
