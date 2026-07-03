package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit coverage for {@link CombatIcons}: the prayer-ladder-by-level and
 * style→potion mapping that drives the Gear DPS section's prayer/potion
 * icon indicators.
 */
public class CombatIconsTest {

    // ---- Melee ladder: Piety(70) -> Chivalry(60) -> Ultimate Strength(31)
    // -> Superhuman Strength(20) -> Burst of Strength(4) ---------------------

    @Test
    public void meleeLadderDegradesByPrayerLevel() {
        assertEquals(OffensivePrayer.PIETY,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 70, false));
        assertEquals(OffensivePrayer.CHIVALRY,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 69, false));
        assertEquals(OffensivePrayer.CHIVALRY,
            CombatIcons.bestOffensivePrayer(CombatStyle.STAB, 60, false));
        assertEquals(OffensivePrayer.ULTIMATE_STRENGTH,
            CombatIcons.bestOffensivePrayer(CombatStyle.CRUSH, 59, false));
        assertEquals(OffensivePrayer.ULTIMATE_STRENGTH,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 31, false));
        assertEquals(OffensivePrayer.SUPERHUMAN_STRENGTH,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 30, false));
        assertEquals(OffensivePrayer.SUPERHUMAN_STRENGTH,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 20, false));
        assertEquals(OffensivePrayer.BURST_OF_STRENGTH,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 19, false));
        assertEquals(OffensivePrayer.BURST_OF_STRENGTH,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 4, false));
        assertNull(CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 3, false));
        assertNull(CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 1, false));
    }

    // ---- Ranged ladder: Rigour(74) -> Eagle Eye(70) -> Hawk Eye(44) -> Sharp Eye(8) ----

    @Test
    public void rangedLadderDegradesByPrayerLevel() {
        assertEquals(OffensivePrayer.RIGOUR,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 74, false));
        assertEquals(OffensivePrayer.EAGLE_EYE,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 73, false));
        assertEquals(OffensivePrayer.EAGLE_EYE,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 70, false));
        assertEquals(OffensivePrayer.HAWK_EYE,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 69, false));
        assertEquals(OffensivePrayer.HAWK_EYE,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 44, false));
        assertEquals(OffensivePrayer.SHARP_EYE,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 43, false));
        assertEquals(OffensivePrayer.SHARP_EYE,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 8, false));
        assertNull(CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 7, false));
    }

    // ---- Magic ladder: Augury(77) -> Mystic Might(74) -> Mystic Lore(60) -> Mystic Will(45) ----

    @Test
    public void magicLadderDegradesByPrayerLevel() {
        assertEquals(OffensivePrayer.AUGURY,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 77, false));
        assertEquals(OffensivePrayer.MYSTIC_MIGHT,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 76, false));
        assertEquals(OffensivePrayer.MYSTIC_MIGHT,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 74, false));
        assertEquals(OffensivePrayer.MYSTIC_LORE,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 73, false));
        assertEquals(OffensivePrayer.MYSTIC_LORE,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 60, false));
        assertEquals(OffensivePrayer.MYSTIC_WILL,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 59, false));
        assertEquals(OffensivePrayer.MYSTIC_WILL,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 45, false));
        assertNull(CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 44, false));
    }

    // ---- assumeBestPrayer=true must match DpsCalculator's hardcoded top-tier prayer ----

    @Test
    public void assumeBestPrayerIgnoresLevelAndMatchesCalculatorHardcode() {
        assertEquals(OffensivePrayer.PIETY,
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 1, true));
        assertEquals(OffensivePrayer.RIGOUR,
            CombatIcons.bestOffensivePrayer(CombatStyle.RANGED, 1, true));
        assertEquals(OffensivePrayer.AUGURY,
            CombatIcons.bestOffensivePrayer(CombatStyle.MAGIC, 1, true));
    }

    @Test
    public void allMeleeSubStylesShareTheSameLadder() {
        assertEquals(CombatIcons.bestOffensivePrayer(CombatStyle.STAB, 70, false),
            CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 70, false));
        assertEquals(CombatIcons.bestOffensivePrayer(CombatStyle.SLASH, 70, false),
            CombatIcons.bestOffensivePrayer(CombatStyle.CRUSH, 70, false));
    }

    // ---- style -> potion mapping ----

    @Test
    public void meleeStylesMapToSuperCombatPotion() {
        assertEquals(CombatIcons.BoostPotion.SUPER_COMBAT, CombatIcons.bestPotion(CombatStyle.STAB));
        assertEquals(CombatIcons.BoostPotion.SUPER_COMBAT, CombatIcons.bestPotion(CombatStyle.SLASH));
        assertEquals(CombatIcons.BoostPotion.SUPER_COMBAT, CombatIcons.bestPotion(CombatStyle.CRUSH));
    }

    @Test
    public void rangedMapsToRangingPotion() {
        assertEquals(CombatIcons.BoostPotion.RANGING, CombatIcons.bestPotion(CombatStyle.RANGED));
    }

    @Test
    public void magicMapsToImbuedHeart() {
        assertEquals(CombatIcons.BoostPotion.IMBUED_HEART, CombatIcons.bestPotion(CombatStyle.MAGIC));
    }

    @Test
    public void nullStyleYieldsNoIcon() {
        assertNull(CombatIcons.bestOffensivePrayer(null, 99, false));
        assertNull(CombatIcons.bestPotion(null));
    }
}
