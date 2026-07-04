package com.ospulse.combat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the bundled {@code monster_gear_overrides.json} (curated
 * monster-mechanic gear requirements) parses and serves name-based lookups
 * correctly.
 */
public class MonsterGearOverrideRepositoryTest {

    @Test
    public void loadsBundledResource() {
        MonsterGearOverrideRepository repo = MonsterGearOverrideRepository.getInstance();
        assertTrue("expected at least the flagship Rune dragon entry", repo.size() >= 1);
    }

    @Test
    public void runeDragon_hasInsulatedBootsOverride() {
        List<MonsterGearOverride> overrides = MonsterGearOverrideRepository.getInstance().forMonster("Rune dragon");
        assertEquals(1, overrides.size());
        MonsterGearOverride override = overrides.get(0);
        assertEquals("Insulated boots", override.itemName());
        assertEquals(7159, override.itemId());
        assertEquals(MonsterGearOverride.Slot.BOOTS, override.slot());
        assertFalse(override.reason().isEmpty());
    }

    @Test
    public void matchIsCaseInsensitive() {
        List<MonsterGearOverride> overrides = MonsterGearOverrideRepository.getInstance().forMonster("rUNE dRAGON");
        assertEquals(1, overrides.size());
        assertEquals("Insulated boots", overrides.get(0).itemName());
    }

    @Test
    public void constructionVariant_alsoMatches() {
        List<MonsterGearOverride> overrides =
                MonsterGearOverrideRepository.getInstance().forMonster("Rune dragon (Construction)");
        assertEquals(1, overrides.size());
        assertEquals("Insulated boots", overrides.get(0).itemName());
    }

    @Test
    public void unknownMonster_returnsEmpty() {
        List<MonsterGearOverride> overrides = MonsterGearOverrideRepository.getInstance().forMonster("Giant rat");
        assertTrue(overrides.isEmpty());
    }

    @Test
    public void nullMonsterName_returnsEmpty() {
        assertTrue(MonsterGearOverrideRepository.getInstance().forMonster(null).isEmpty());
    }
}
