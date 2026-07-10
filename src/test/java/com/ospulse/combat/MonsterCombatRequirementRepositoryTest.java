package com.ospulse.combat;

import org.junit.Test;
import java.util.Optional;
import static org.junit.Assert.*;

public class MonsterCombatRequirementRepositoryTest
{
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    @Test public void loadsKuraskAsWeaponGate()
    {
        Optional<MonsterCombatRequirement> r =
            MonsterCombatRequirementRepository.getInstance().forMonster("Kurask");
        assertTrue(r.isPresent());
        assertEquals(MonsterCombatRequirement.Type.WEAPON_GATE, r.get().type());
        assertFalse("whip must not damage Kurask",
            r.get().permits(4151, CombatStyle.SLASH, 0));
        assertTrue("magic must damage Kurask",
            r.get().permits(1387, CombatStyle.MAGIC, 0));
    }

    @Test public void unknownMonsterEmpty()
    {
        assertFalse(MonsterCombatRequirementRepository.getInstance().forMonster("Cow").isPresent());
    }

    @Test public void nullSafe()
    {
        assertFalse(MonsterCombatRequirementRepository.getInstance().forMonster(null).isPresent());
    }
}
