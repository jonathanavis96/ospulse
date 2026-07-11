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
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

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

    // ------------------------------------------------ review finding 3: Slayer helmet substitutes

    private static final int SLAYER_HELMET = 11864;
    private static final int SLAYER_HELMET_I = 11865;
    private static final int BLACK_SLAYER_HELMET = 19639; // a colour recolour — must also be accepted
    private static final int RANDOM_UNRELATED_HELM = 1155; // Iron full helm — must NOT be accepted

    /**
     * Review finding 3: face-protection requirements against Dust devils,
     * Banshees, Aberrant spectres, and Wall beasts used an EXACT item-id
     * match, so a Slayer helmet (which grants the same protection in-game as
     * the plain listed item) was wrongly rejected. Each of these four
     * requirements must now accept the primary item AND the Slayer helmet
     * family (plain, imbued, and colour/boss recolours) via {@link
     * MonsterGearOverride#satisfiedBy}, while still rejecting an unrelated
     * head item.
     */
    @Test
    public void faceProtectionRequirements_acceptSlayerHelmetFamilyAsSubstitutes() {
        String[] monsters = {"Dust devil", "Banshee", "Aberrant spectre", "Wall beast"};
        for (String monster : monsters) {
            List<MonsterGearOverride> overrides = MonsterGearOverrideRepository.getInstance().forMonster(monster);
            assertEquals(monster + " must have exactly one HEAD requirement", 1, overrides.size());
            MonsterGearOverride override = overrides.get(0);
            assertEquals(MonsterGearOverride.Slot.HEAD, override.slot());

            assertTrue(monster + ": the primary listed item must still satisfy its own requirement",
                    override.satisfiedBy(override.itemId()));
            assertTrue(monster + ": a plain Slayer helmet must substitute",
                    override.satisfiedBy(SLAYER_HELMET));
            assertTrue(monster + ": an imbued Slayer helmet must substitute",
                    override.satisfiedBy(SLAYER_HELMET_I));
            assertTrue(monster + ": a Slayer helmet colour recolour must substitute",
                    override.satisfiedBy(BLACK_SLAYER_HELMET));
            assertFalse(monster + ": an unrelated helm must NOT satisfy the requirement",
                    override.satisfiedBy(RANDOM_UNRELATED_HELM));
        }
    }
}
