package com.ospulse.combat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The prayer step of the effective-level formula is floor(level * percent / 100)
 * on exact integers. In doubles 100 * 1.15 is 114.99999999999999, which a plain
 * floor turns into 114 instead of 115.
 */
public class PrayerMultiplierFloorTest {

    @Test
    public void fifteenPercentPrayerAtBoostedLevel100GivesExactly115() {
        // floor(100 * 1.15) = 115, + 8 = 123
        assertEquals(123, CombatMath.effectiveMeleeOrRangedLevel(100, 1.15, 0, 8, 1.0));
        // floor(100 * 1.15) = 115, + 9 = 124
        assertEquals(124, CombatMath.effectiveMagicLevel(100, 1.15, 0, 1.0));
    }

    @Test
    public void everyModelledPrayerMatchesExactIntegerFloor() {
        for (OffensivePrayer p : OffensivePrayer.values()) {
            double[] mults = {p.meleeAttackMult(), p.meleeStrengthMult(), p.rangedAttackMult(),
                p.rangedStrengthMult(), p.magicAccuracyMult()};
            for (double m : mults) {
                long percent = Math.round(m * 100);
                for (int level = 1; level <= 140; level++) {
                    int expected = (int) (level * percent / 100) + 8;
                    assertEquals(p + " x" + m + " at " + level, expected,
                        CombatMath.effectiveMeleeOrRangedLevel(level, m, 0, 8, 1.0));
                }
            }
        }
    }
}
