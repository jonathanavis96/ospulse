package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Verifies the bundled clean-room {@code equipment_stats.min.json} (cache-derived,
 * see the accompanying README for provenance) parses into per-item bonuses with
 * the engine's expected units — most importantly that magic damage is scaled from
 * the file's tenths-of-a-percent into whole percent (Kodai wand: file 150 -> 15.0).
 */
public class EquipmentStatsRepositoryTest {

    private static final double DELTA = 1e-9;

    @Test
    public void loadsBundledResourceWithManyEntries() {
        EquipmentStatsRepository repo = EquipmentStatsRepository.getInstance();
        assertEquals("expected the full cache-derived item set", 3501, repo.size());
    }

    @Test
    public void abyssalWhip_slashStrengthSpeed() {
        EquipmentStatsRepository.Stats s = EquipmentStatsRepository.getInstance().statsFor(4151);
        assertNotNull(s);
        assertEquals(82, s.aslash());
        assertEquals(82, s.str());
        assertEquals(4, s.aspeed());
        assertEquals(0.0, s.mdmg(), DELTA);
    }

    @Test
    public void armadylGodsword_fullBonusRow() {
        EquipmentStatsRepository.Stats s = EquipmentStatsRepository.getInstance().statsFor(11802);
        assertNotNull(s);
        assertEquals(132, s.aslash());
        assertEquals(80, s.acrush());
        assertEquals(132, s.str());
        assertEquals(8, s.prayer());
        assertEquals(6, s.aspeed());
    }

    @Test
    public void kodaiWand_magicDamageScaledToWholePercent() {
        // File stores magic damage in tenths of a percent (150); the DPS engine
        // expects whole percent (CombatMath applies 1 + mdmg/100), so 150 -> 15.0.
        EquipmentStatsRepository.Stats s = EquipmentStatsRepository.getInstance().statsFor(21006);
        assertNotNull(s);
        assertEquals(28, s.amagic());
        assertEquals(20, s.dmagic());
        assertEquals(15.0, s.mdmg(), DELTA);
    }

    @Test
    public void twistedBow_rangedStrengthAndSpeed() {
        EquipmentStatsRepository.Stats s = EquipmentStatsRepository.getInstance().statsFor(20997);
        assertNotNull(s);
        assertEquals(70, s.arange());
        assertEquals(20, s.rstr());
        assertEquals(6, s.aspeed());
    }

    @Test
    public void unknownAndEmptyIdsReturnNull() {
        EquipmentStatsRepository repo = EquipmentStatsRepository.getInstance();
        assertNull("an item id absent from the data must be null (caller falls back)", repo.statsFor(99_999_999));
        assertNull("empty slot", repo.statsFor(0));
        assertNull("empty slot", repo.statsFor(-1));
    }
}
