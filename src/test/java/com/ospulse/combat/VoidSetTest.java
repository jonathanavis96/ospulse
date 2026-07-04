package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Locks the ranged void multiplier split: elite ranged void gives +12.5% to
 * max hit (strength) but only +10% to accuracy — the elite top/bottom upgrade
 * improves the damage side only. Regression for the bug where a single 1.125x
 * multiplier was applied to both the strength AND the accuracy effective level.
 */
public class VoidSetTest {

    private static final double DELTA = 1e-9;

    @Test
    public void regularRangedVoid_boostsAccuracyAndStrengthEqually() {
        assertEquals(1.1, VoidSet.RANGED.rangedAccuracyMultiplier(), DELTA);
        assertEquals(1.1, VoidSet.RANGED.rangedStrengthMultiplier(), DELTA);
    }

    @Test
    public void eliteRangedVoid_boostsStrengthMoreThanAccuracy() {
        // Accuracy stays at regular void's +10%, only max hit gets +12.5%.
        assertEquals(1.1, VoidSet.RANGED_ELITE.rangedAccuracyMultiplier(), DELTA);
        assertEquals(1.125, VoidSet.RANGED_ELITE.rangedStrengthMultiplier(), DELTA);
    }

    @Test
    public void nonRangedSets_giveNoRangedBonus() {
        for (VoidSet set : new VoidSet[]{VoidSet.NONE, VoidSet.MELEE, VoidSet.MAGIC, VoidSet.MAGIC_ELITE}) {
            assertEquals(1.0, set.rangedAccuracyMultiplier(), DELTA);
            assertEquals(1.0, set.rangedStrengthMultiplier(), DELTA);
        }
    }
}
