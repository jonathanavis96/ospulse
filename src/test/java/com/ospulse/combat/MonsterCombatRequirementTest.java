package com.ospulse.combat;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MonsterCombatRequirementTest
{
    private static final int LEAF_SWORD = 20368, BROAD_BOLTS = 11875, DRAGON_ARROW = 11212;
    private static final int WHIP = 4151, BOW = 861, STAFF = 1387;

    private MonsterCombatRequirement kurask()
    {
        return MonsterCombatRequirement.weaponGate(
            new HashSet<>(Arrays.asList(LEAF_SWORD)),
            new HashSet<>(Arrays.asList(BROAD_BOLTS)),
            EnumSet.of(CombatStyle.MAGIC),
            "Kurask can only be harmed by leaf-bladed weapons, broad ammunition, or magic.");
    }

    @Test public void magicAlwaysPermitted()
    {
        assertTrue(kurask().permits(STAFF, CombatStyle.MAGIC, 0));
    }
    @Test public void leafBladedMeleePermitted()
    {
        assertTrue(kurask().permits(LEAF_SWORD, CombatStyle.SLASH, 0));
    }
    @Test public void normalMeleeRejected()
    {
        assertFalse(kurask().permits(WHIP, CombatStyle.SLASH, 0));
    }
    @Test public void rangedWithBroadAmmoPermitted()
    {
        assertTrue(kurask().permits(BOW, CombatStyle.RANGED, BROAD_BOLTS));
    }
    @Test public void rangedWithWrongAmmoRejected()
    {
        assertFalse(kurask().permits(BOW, CombatStyle.RANGED, DRAGON_ARROW));
    }
    @Test public void perSlotGatesAgreeWithPermits()
    {
        MonsterCombatRequirement k = kurask();
        assertTrue(k.permitsWeapon(BOW, CombatStyle.RANGED));
        assertTrue(k.permitsAmmo(BROAD_BOLTS, CombatStyle.RANGED));
        assertFalse(k.permitsAmmo(DRAGON_ARROW, CombatStyle.RANGED));
        assertFalse(k.permitsWeapon(WHIP, CombatStyle.SLASH));
        assertTrue(k.permitsWeapon(LEAF_SWORD, CombatStyle.SLASH));
    }
    @Test public void aviansieStyleGate()
    {
        MonsterCombatRequirement a = MonsterCombatRequirement.weaponGate(
            Collections.emptySet(), Collections.emptySet(),
            EnumSet.of(CombatStyle.RANGED, CombatStyle.MAGIC), "Use ranged or magic.");
        assertTrue(a.permits(BOW, CombatStyle.RANGED, DRAGON_ARROW));
        assertFalse(a.permits(WHIP, CombatStyle.SLASH, 0));
    }
    @Test public void finisherNeverGatesDamage()
    {
        MonsterCombatRequirement g = MonsterCombatRequirement.finisher(
            new HashSet<>(Arrays.asList(4162)), "Finish with a rock hammer.");
        assertTrue(g.permits(WHIP, CombatStyle.CRUSH, 0));
        assertEquals(MonsterCombatRequirement.Type.FINISHER, g.type());
    }
}
