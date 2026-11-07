package com.ospulse.combat;

/**
 * The Obligator's stances and charged passive, per the OSRS Wiki: Thrust
 * attacks every 5 ticks and Smash every 3. While charged, Thrust gains 20% or
 * 40% damage against 2x2 and 3x3 or bigger targets and makes two damage rolls,
 * plus one more for each of the target's stab and slash defences that beats its
 * crush defence, keeping the best. Smash and an uncharged Obligator get none of
 * this. Same shape as {@link KerisTripleRoll}: best-of-N is a different
 * distribution from a plain roll, so it bypasses the generic {@code finish()}.
 *
 * <p>Formula details follow weirdgloop's calculator, which also places the
 * damage bonus last among the max-hit steps. A landed hit keeps the best of the
 * rolls; accuracy is rolled once.
 */
final class ObligatorThrust {
    private ObligatorThrust() {
    }

    private static final int CHARGED_ID = 34715;
    /** Smash's attack speed; {@link Stance#SMASH} belongs to the Obligator alone. */
    static final int SMASH_SPEED_TICKS = 3;

    /** True for the charged Obligator on either Thrust stance. */
    static boolean appliesTo(int weaponId, Stance stance) {
        return weaponId == CHARGED_ID && stance != Stance.SMASH;
    }

    /** {@code maxHit} after the target-size damage bonus. */
    static int boostedMaxHit(int maxHit, int targetSize) {
        if (targetSize < 2) {
            return maxHit;
        }
        return maxHit * (targetSize == 2 ? 120 : 140) / 100;
    }

    /** How many damage rolls one attack makes against {@code target}. */
    static int damageRolls(Monster target) {
        int crush = target.defenceBonus(CombatStyle.CRUSH);
        return 2 + (target.defenceBonus(CombatStyle.STAB) > crush ? 1 : 0)
                + (target.defenceBonus(CombatStyle.SLASH) > crush ? 1 : 0);
    }

    /**
     * One landed roll's distribution raised to best-of-{@code rolls}: the
     * chance of a best roll of at most {@code d} is the chance of one roll
     * being at most {@code d}, to the power {@code rolls}.
     */
    static double[] bestOf(double[] oneRoll, int rolls) {
        double[] best = new double[oneRoll.length];
        double atMost = 0.0;
        double previous = 0.0;
        for (int d = 0; d < oneRoll.length; d++) {
            atMost += oneRoll[d];
            double power = Math.pow(atMost, rolls);
            best[d] = power - previous;
            previous = power;
        }
        return best;
    }

    /** Assembles the {@link DpsResult}. {@code uncappedMaxHit} must already include {@link #boostedMaxHit}. */
    static DpsResult finish(int uncappedMaxHit, int cap, MonsterCombatRequirement.CapMode mode, int rolls,
                            int attackRoll, int defenceRoll, int weaponSpeedTicks, int targetHitpoints) {
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        boolean capped = cap >= 0 && cap < uncappedMaxHit;
        double[] oneRoll;
        if (!capped) {
            oneRoll = DamageDistribution.perHitDistribution(1.0, uncappedMaxHit);
        } else if (mode == MonsterCombatRequirement.CapMode.REROLL) {
            oneRoll = DamageDistribution.rerolledPerHitDistribution(1.0, uncappedMaxHit, cap);
        } else {
            oneRoll = DamageDistribution.cappedPerHitDistribution(1.0, uncappedMaxHit, cap);
        }
        double[] outcome = bestOf(oneRoll, rolls);
        double avgDamage = 0.0;
        for (int d = 0; d < outcome.length; d++) {
            outcome[d] *= hitChance;
            avgDamage += d * outcome[d];
        }
        outcome[0] += 1.0 - hitChance;
        double overkill = DamageDistribution.overkillFromExplicitDistribution(outcome, targetHitpoints);
        return DpsCalculator.result(capped ? cap : uncappedMaxHit, hitChance, avgDamage, overkill, weaponSpeedTicks,
                targetHitpoints, false);
    }
}
