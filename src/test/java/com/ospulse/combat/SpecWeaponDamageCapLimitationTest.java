package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * <b>Honest, verified limitation</b> — spec-weapon scoring is not target-damage-cap
 * aware (raised on PR #25 review round 4, accepted as real, deferred to its own
 * change rather than bolted onto that PR).
 *
 * <p>{@code SpecWeaponSelector.bestByRole} scores a candidate with
 * {@code weapon.expectedDamagePerUse(result.accuracy(), result.maxHit())}, and
 * {@link DpsResult#maxHit()} is the target's <i>visible</i> max hit —
 * {@code DpsCalculator.finish} sets it from {@code TargetDamage.visibleMaxHit()},
 * i.e. {@code min(uncapped, cap)}. At a damage-capped target (The Hueycoatl's
 * tail under {@link MonsterCombatRequirement.CapMode#CLAMP}, Verzik Vitur phase 1
 * under {@code REROLL}) the special's own boosts are therefore applied on top of
 * a number the cap has ALREADY been folded into, as if that cap were the
 * weapon's uncapped base roll. Two things go wrong at once: the resulting
 * hitsplat can exceed the target's ceiling, and multi-hit specials lose the real
 * uncapped roll distribution before each hitsplat is capped or re-rolled.
 *
 * <p>Because this distorts the SCORE, it can reorder which owned damage spec the
 * gear picker recommends at exactly those targets. It is a ranking defect, not a
 * displayed-number defect — nothing here is shown to the player as a max hit.
 *
 * <p><b>Why this is pinned rather than fixed here.</b> A correct fix has to
 * carry the uncapped max, the cap, and the {@link MonsterCombatRequirement.CapMode}
 * all the way from {@link DpsCalculator} through {@link DpsResult} and
 * {@code SpecWeaponSelector} into every {@code SpecWeapon.DamageModel}, then
 * apply the cap per hitsplat inside each of the six model families — including
 * the {@link SpecCascadeMath} claw/dagger branch enumerations, whose ranges that
 * class's own javadoc already flags as reverse-engineered and only "about
 * right". A partial fix would be worse than none: making some model families
 * cap-aware and not others would skew the cross-weapon comparison that is the
 * selector's entire purpose.
 *
 * <p>This test pins the CURRENT behaviour so the gap cannot be mistaken for
 * correctness, and states the target value the fix must produce.
 */
public class SpecWeaponDamageCapLimitationTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /** Toxic blowpipe — "+100% accuracy, +50% damage", i.e. boostedSingleHit(acc x2, dmg x1.5). */
    private static final int TOXIC_BLOWPIPE = 12926;

    private static SpecWeapon blowpipe() {
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            if (weapon.itemId() == TOXIC_BLOWPIPE) {
                return weapon;
            }
        }
        throw new AssertionError("Toxic blowpipe missing from SpecWeapon.CATALOG");
    }

    /**
     * The blowpipe's damage model multiplies the supplied max hit by 1.5. Fed a
     * max hit of 4 that a target cap already produced, it scores a 0..6 roll —
     * two points above the ceiling the 4 came from.
     */
    @Test
    public void knownGap_specBoostIsAppliedOnTopOfAnAlreadyCappedMaxHit() {
        int cappedMaxHit = 4;      // what DpsResult.maxHit() reports at a cap-4 target
        int boostedMax = 6;        // floor(4 * 1.5) — the roll the model actually scores
        double certainHit = 1.0;

        double scored = blowpipe().expectedDamagePerUse(certainHit, cappedMaxHit);

        // Pins the defect precisely: the score equals a plain uncapped 0..6 roll,
        // so the cap that produced the 4 has vanished from the calculation.
        assertEquals(DamageDistribution.averageDamagePerAttack(certainHit, boostedMax), scored, 1e-9);
        assertTrue("boosted roll must exceed the cap for this to be a real gap", boostedMax > cappedMaxHit);
    }

    /**
     * The value the deferred fix must produce for this same case under {@code
     * CLAMP}: the roll stays 0..6 and everything above 4 lands ON 4. Asserting
     * the two are different is what makes this a gap rather than a restatement
     * of the formula — if a future change makes scoring cap-aware, this test
     * fails and must be rewritten as the new contract.
     */
    @Test
    public void knownGap_clampCorrectValueDiffersFromWhatIsCurrentlyScored() {
        int cappedMaxHit = 4;
        int boostedMax = 6;
        int cap = 4;
        double certainHit = 1.0;

        double scored = blowpipe().expectedDamagePerUse(certainHit, cappedMaxHit);
        double clampCorrect = DamageDistribution.cappedAverageDamagePerAttack(certainHit, boostedMax, cap);

        assertEquals(19.0 / 7.0, clampCorrect, 1e-9);   // 2.714...
        assertEquals(22.0 / 7.0, scored, 1e-9);         // 3.142...
        assertTrue("current scoring must overstate, otherwise there is nothing to fix",
                scored > clampCorrect);
    }
}
