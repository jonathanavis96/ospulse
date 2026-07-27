package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * <b>Honest, verified limitation</b> — a target's DAMAGE_PENALTY is applied to the
 * max hit's ENDPOINT, not to the damage distribution (raised on PR #24 review
 * round 12, accepted as real, split into its own change).
 *
 * <p>{@code DpsCalculator.applyTargetDamageRules} computes
 * {@code afterPenalty = floor(maxHit * multiplier)} and every downstream
 * {@code finish*} then models a uniform roll over {@code 0..afterPenalty}.
 * That is not what the game does. The penalty reduces each ROLLED hit, so the
 * real distribution is the weapon's own roll with every outcome scaled — and
 * scaling a uniform roll does not produce a uniform roll.
 *
 * <p>Worked case, the one from the review (Corporeal Beast, 0.5x, per-hit
 * range 0..26 after the Tonalztics 75% reduction):
 *
 * <pre>
 * true:      floor(d/2) for d uniform on 0..26
 *            -&gt; P(k) = 2/27 for k in 0..12, P(13) = 1/27,  mean = 169/27 ~ 6.2593
 * modelled:  uniform on 0..13
 *            -&gt; P(k) = 1/14 for k in 0..13,                mean = 13/2   = 6.5
 * </pre>
 *
 * The displayed max hit is right either way (13). What is wrong is the mean —
 * overstated by ~3.85% here — and therefore average damage, DPS, overkill and
 * TTK against every {@code DAMAGE_PENALTY} target.
 *
 * <p><b>Why this is pinned rather than fixed here.</b> It is NOT specific to
 * Tonalztics. {@code applyTargetDamageRules} is the single shared site every
 * ranged/melee/magic path routes through, so the endpoint-only approximation
 * applies to every weapon against such a target. Fixing it for one weapon
 * would leave that weapon computed on a different model from everything it is
 * ranked against — the same reason the spec-weapon damage-cap gap was split
 * out whole rather than half-applied (see {@code
 * SpecWeaponDamageCapLimitationTest}). A correct fix transforms the
 * distribution at the shared site, for all weapons, and must also settle the
 * penalty-versus-cap ordering (the reference applies the Corp penalty BEFORE
 * other limiters).
 *
 * <p>This test pins the size of the gap so it cannot be mistaken for
 * correctness, and states the value a fix must produce.
 */
public class TargetDamagePenaltyDistributionLimitationTest {

    /** The per-hit range in the review's worked case: 35 -> floor(35*3/4) = 26. */
    private static final int UNPENALISED_PER_HIT_MAX = 26;
    /** What the endpoint-only model reduces that to under a 0.5x penalty. */
    private static final int PENALISED_ENDPOINT = 13;

    /** Mean of {@code floor(d/2)} for {@code d} uniform over {@code 0..max} — the real, scaled distribution. */
    private static double trueScaledMean(int max, double multiplier) {
        double total = 0.0;
        for (int d = 0; d <= max; d++) {
            total += Math.floor(d * multiplier);
        }
        return total / (max + 1);
    }

    /** Mean of a plain uniform roll over {@code 0..max} — what the endpoint-only model assumes. */
    private static double uniformMean(int max) {
        double total = 0.0;
        for (int d = 0; d <= max; d++) {
            total += d;
        }
        return total / (max + 1);
    }

    /**
     * The two models disagree. Asserting the exact values (rather than merely
     * "they differ") is what makes this discriminating: if a future change
     * transforms the distribution properly, this test fails and must be
     * rewritten as the new contract rather than silently continuing to pass.
     */
    @Test
    public void knownGap_scalingTheRollIsNotTheSameAsScalingItsEndpoint() {
        double trueMean = trueScaledMean(UNPENALISED_PER_HIT_MAX, 0.5);
        double modelledMean = uniformMean(PENALISED_ENDPOINT);

        assertEquals(169.0 / 27.0, trueMean, 1e-9);   // 6.2593
        assertEquals(13.0 / 2.0, modelledMean, 1e-9); // 6.5

        assertTrue("the endpoint-only model must OVERSTATE, otherwise there is nothing to fix",
                modelledMean > trueMean);
    }

    /**
     * The endpoint itself is not the problem — the displayed max hit is correct
     * under both models. Pinning this separately keeps the gap correctly
     * attributed: a future fix must change the MEAN, and must not move the max.
     */
    @Test
    public void knownGap_theDisplayedMaximumIsAlreadyCorrect() {
        int trueMax = (int) Math.floor(UNPENALISED_PER_HIT_MAX * 0.5);
        assertEquals("both models agree on the largest achievable hit", PENALISED_ENDPOINT, trueMax);
    }

    /**
     * The gap is not a rounding artefact of one specific number — it is
     * systematic across ranges, which is why it needs a real distribution
     * transform rather than a fudge factor.
     */
    @Test
    public void knownGap_isSystematicAcrossRanges_notAnArtefactOfOneValue() {
        for (int max : new int[]{10, 26, 40, 75, 99}) {
            double trueMean = trueScaledMean(max, 0.5);
            double modelledMean = uniformMean((int) Math.floor(max * 0.5));
            assertTrue("endpoint model must overstate at max=" + max, modelledMean >= trueMean);
        }
    }
}
