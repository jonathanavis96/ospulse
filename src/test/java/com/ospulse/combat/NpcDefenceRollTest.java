package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * NPC defence roll = (Target Defence level + 9) * (Target Style Defence Bonus + 64).
 * NPCs get no +8/stance effective-level treatment (that's players-only).
 * https://oldschool.runescape.wiki/w/Damage_per_second/Melee#Step_six:_Calculate_the_Defence_roll
 */
public class NpcDefenceRollTest {
    @Test
    public void computesDocumentedFormula() {
        assertEquals(9796, CombatMath.npcDefenceRoll(70, 60));
        assertEquals(6136, CombatMath.npcDefenceRoll(50, 40));
    }

    @Test
    public void zeroDefenceLevelAndBonusStillAddsTheConstants() {
        assertEquals(9 * 64, CombatMath.npcDefenceRoll(0, 0));
    }
}
