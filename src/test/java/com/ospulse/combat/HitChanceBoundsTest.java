package com.ospulse.combat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Hit chance is a probability. A bonus below -64 makes a roll negative: the
 * bundled monster data has 80 defence bonuses of -100 (Acidic Bloodveld,
 * Tombs of Amascut eggs), and heavy armour can push a magic attack bonus
 * under -64. Neither may give a chance outside 0..1.
 */
public class HitChanceBoundsTest {

    @Test
    public void negativeDefenceRollIsCappedAtCertainHit() {
        // Acidic Bloodveld: defence level 2, every defence bonus -100.
        int defenceRoll = CombatMath.npcDefenceRoll(2, -100);
        assertEquals(1.0, CombatMath.hitChance(10_000, defenceRoll), 0.0);
    }

    @Test
    public void negativeAttackRollIsFlooredAtZero() {
        int attackRoll = CombatMath.magicAccuracyRoll(81, -68, Fraction.ONE);
        assertEquals(0.0, CombatMath.hitChance(attackRoll, 640), 0.0);
    }
}
