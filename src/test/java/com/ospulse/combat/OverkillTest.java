package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DamageDistribution#expectedOverkill} basics: hand-worked examples and
 * the accuracy-independence property it relies on (misses cancel out of the
 * recursion, so overkill depends only on max hit and target hitpoints).
 * Split out of {@code TierBEffectsTest}, which this predates and is otherwise
 * unrelated to (Dragon Hunter/Twisted bow/demonbane/spell effects) — it tests
 * {@link DamageDistribution}, not a Tier-B effect.
 */
public class OverkillTest {
    private static final double DELTA = 1e-9;

    private static EquipmentStats.Builder plainMeleeGear() {
        // +100 astab, +80 str, speed 4.
        return EquipmentStats.builder()
                .add(100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static PlayerCombat player99() {
        return PlayerCombat.builder()
                .attack(99, 99)
                .strength(99, 99)
                .ranged(99, 99)
                .magic(99, 99)
                .stance(Stance.ACCURATE)
                .build();
    }

    private static Monster.Builder monster(int hp) {
        return Monster.builder()
                .name("Target")
                .hitpoints(hp)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1);
    }

    @Test
    public void overkill_handWorkedExamples() {
        // maxHit 1 vs 1 hp: the only successful damage is exactly 1 -> no waste.
        assertEquals(0.0, DamageDistribution.expectedOverkill(1, 1), DELTA);
        // maxHit 2 vs 1 hp: successful damage is 1 w.p. 2/3, 2 w.p. 1/3 -> E[waste] = 1/3.
        assertEquals(1.0 / 3.0, DamageDistribution.expectedOverkill(2, 1), DELTA);
        // maxHit 2 vs 2 hp: O[1] = 1/3; O[2] = (2/3)*O[1] + (1/3)*0 = 2/9.
        assertEquals(2.0 / 9.0, DamageDistribution.expectedOverkill(2, 2), DELTA);
        // Degenerate inputs are safe.
        assertEquals(0.0, DamageDistribution.expectedOverkill(0, 50), DELTA);
        assertEquals(0.0, DamageDistribution.expectedOverkill(10, 0), DELTA);
    }

    @Test
    public void overkill_isIndependentOfAccuracyAndExposedOnResult() {
        Monster tanky = monster(200).build();
        Monster squishy = monster(200).defenceLevel(1).defenceBonuses(0, 0, 0, 0, 0).magicLevel(1).build();
        EquipmentStats gear = plainMeleeGear().build();

        DpsResult vsTanky = DpsCalculator.compute(gear, player99(), CombatStyle.STAB, tanky, 0);
        DpsResult vsSquishy = DpsCalculator.compute(gear, player99(), CombatStyle.STAB, squishy, 0);

        // Same maxHit + same hp -> identical overkill despite very different accuracy (misses cancel).
        assertEquals(vsTanky.maxHit(), vsSquishy.maxHit());
        assertEquals(vsTanky.overkillPerKill(), vsSquishy.overkillPerKill(), DELTA);
        assertTrue(vsTanky.overkillPerKill() > 0.0);
        // Overkill can never exceed maxHit - 1.
        assertTrue(vsTanky.overkillPerKill() < gearMaxHitUpperBound());
    }

    private static double gearMaxHitUpperBound() {
        return 24.0; // the shared worked example's max hit
    }
}
