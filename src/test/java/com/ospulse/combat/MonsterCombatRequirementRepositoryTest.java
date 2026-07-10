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
        // A leaf-bladed weapon (leaf-bladed spear 4158) always damages it.
        assertTrue("leaf-bladed weapon must damage Kurask",
            r.get().permits(4158, CombatStyle.SLASH, 0));
        // Broad ammunition (broad bolts 11875) damages it when ranging.
        assertTrue("broad ammunition must damage Kurask via ranged",
            r.get().permits(861, CombatStyle.RANGED, 11875));
        // An ordinary weapon (abyssal whip 4151) does not — magic here is
        // Magic-Dart-only, which we deliberately do NOT model as blanket MAGIC,
        // so a plain staff/whip is correctly rejected.
        assertFalse("whip must not damage Kurask",
            r.get().permits(4151, CombatStyle.SLASH, 0));
    }

    @Test public void unknownMonsterEmpty()
    {
        assertFalse(MonsterCombatRequirementRepository.getInstance().forMonster("Cow").isPresent());
    }

    @Test public void nullSafe()
    {
        assertFalse(MonsterCombatRequirementRepository.getInstance().forMonster(null).isPresent());
    }

    /** The shipped bundled data parses and covers the expected monster spread. */
    @Test public void bundledDataParsesAndCoversExpectedMonsters()
    {
        MonsterCombatRequirementRepository repo = MonsterCombatRequirementRepository.getInstance();
        assertTrue("expected all bundled monster-name variants to load", repo.size() >= 19);

        // A WEAPON_GATE variant name resolves to the same gate as its base.
        Optional<MonsterCombatRequirement> turoth = repo.forMonster("Turoth");
        assertTrue(turoth.isPresent());
        assertEquals(MonsterCombatRequirement.Type.WEAPON_GATE, turoth.get().type());
        assertFalse("whip must not damage Turoth",
            turoth.get().permits(4151, CombatStyle.SLASH, 0));

        // A FINISHER variant name resolves and never gates damage.
        Optional<MonsterCombatRequirement> marble = repo.forMonster("Marble gargoyle");
        assertTrue(marble.isPresent());
        assertEquals(MonsterCombatRequirement.Type.FINISHER, marble.get().type());
        assertTrue("finisher monsters take damage from any weapon",
            marble.get().permits(4151, CombatStyle.SLASH, 0));

        // Case-insensitive lookup resolves regardless of caller casing.
        assertTrue("lookup must be case-insensitive", repo.forMonster("dUsK").isPresent());
        assertEquals(MonsterCombatRequirement.Type.WEAPON_GATE, repo.forMonster("Dusk").get().type());
    }
}
