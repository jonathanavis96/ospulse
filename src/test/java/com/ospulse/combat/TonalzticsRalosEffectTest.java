package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tonalztics of Ralos, both charge states.
 *
 * <p>CHARGED (item id 28922): two full, INDEPENDENTLY ACCURACY-ROLLED hits
 * per attack, each dealing 0-75% of the calculated weapon max hit (NOT
 * 0-100%, per the OSRS Wiki "Multi-hit weapons" comparison table — see
 * {@link TonalzticsDualHit}'s class javadoc for the citation and the two
 * prior mis-readings this stage corrects).
 *
 * <p>UNCHARGED (item id 28919): per the OSRS Wiki, "Uncharged, the weapon
 * hits a target once for 0-75% of the player's maximum ranged hit" — a
 * SINGLE hit over that SAME reduced 75% range, NOT an ordinary full-range
 * 0..M single-hit weapon (a prior P1 defect this stage also fixes). Both
 * charge states therefore share the identical per-hit range; only the hit
 * count differs.
 *
 * <p>The regression the "unrelatedWeapon" test below proves is that {@code
 * tonalzticsOfRalosCharged() == false} and {@code
 * tonalzticsOfRalosUncharged() == false} (the default, and the only state
 * {@link com.ospulse.session.GearVariants#isTonalzticsOfRalosCharged}/{@link
 * com.ospulse.session.GearVariants#isTonalzticsOfRalosUncharged} ever return
 * for anything other than ids 28922/28919) never touches {@link
 * DpsCalculator}'s generic path at all.
 */
public class TonalzticsRalosEffectTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /** +100 arange, +150 rstr, speed 4; charged flag set by the caller. */
    private static EquipmentStats.Builder gear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 150, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.ACCURATE)
                .build();
    }

    private static Monster monster() {
        return Monster.builder()
                .name("Test")
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear, Monster target) {
        return DpsCalculator.compute(gear, player(), CombatStyle.RANGED, target, 0);
    }

    @Test
    public void charged_reportsSeventyFivePercentMaxHit_andRoughlyOneAndAHalfTimesTheAverageDamage() {
        DpsResult uncharged = compute(gear().build(), monster());
        DpsResult charged = compute(gear().tonalzticsOfRalosCharged(true).build(), monster());

        // Accuracy is unaffected by the passive - only the damage side changes.
        assertEquals(uncharged.accuracy(), charged.accuracy(), 1e-12);
        // Charged max hit is 75% of the calculated (uncharged) max hit, floored -
        // the largest a SINGLE hitsplat can now show, not the full weapon max hit.
        assertEquals((uncharged.maxHit() * 3) / 4, charged.maxHit());
        assertTrue("charged max hit must be strictly less than the uncharged one "
                + "(75% < 100%) - this is the exact defect this stage fixes",
                charged.maxHit() < uncharged.maxHit());
        // Combined avgHit: two 75%-range hits average out to noticeably MORE
        // than one full-range hit (roughly 2*0.75 = 1.5x for a large maxHit,
        // ignoring the small +1/(maxHit+1) bump term) but strictly LESS than
        // double it - proves neither the old (2x) nor the review's wrong
        // guess (1x, from halving each hit to 50%) survived.
        assertTrue(charged.avgHit() > uncharged.avgHit());
        assertTrue(charged.avgHit() < 2.0 * uncharged.avgHit());
    }

    @Test
    public void charged_endToEnd_matchesTonalzticsDualHitFormulaDirectly() {
        EquipmentStats chargedGear = gear().tonalzticsOfRalosCharged(true).build();
        Monster target = monster();
        DpsResult result = compute(chargedGear, target);

        // Recompute independently via the same formula this stage adds,
        // using the plain (uncapped, no target-damage-rule) inputs this
        // fixture is built to exercise, to prove the wiring - not just the
        // isolated math - is correct.
        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 150, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 100, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        DpsResult expected = TonalzticsDualHit.finish(maxHit, -1, MonsterCombatRequirement.CapMode.CLAMP,
                attackRoll, defenceRoll, 4, target.hitpoints());

        assertEquals(expected.maxHit(), result.maxHit());
        assertEquals(expected.accuracy(), result.accuracy(), 1e-12);
        assertEquals(expected.avgHit(), result.avgHit(), 1e-9);
        assertEquals(expected.dps(), result.dps(), 1e-9);
        assertEquals(expected.ttkSeconds(), result.ttkSeconds(), 1e-9);
        assertEquals(expected.overkillPerKill(), result.overkillPerKill(), 1e-9);
    }

    /**
     * Hard-coded 75% pin at the full DpsCalculator-wiring level (distinct
     * from {@code TonalzticsDualHitMathTest}'s own unit-level pin on {@link
     * TonalzticsDualHit#perHitMaxHit}) — independent of any of
     * TonalzticsDualHit's own arithmetic, so a future refactor cannot
     * silently re-widen the per-hit range back to 100% (the original
     * defect) or narrow it to 50% (the review's wrong guess) without this
     * test catching it end-to-end.
     */
    @Test
    public void charged_maxHit_isExactlySeventyFivePercentOfCalculatedMaxHit_flooredExactly() {
        EquipmentStats chargedGear = gear().tonalzticsOfRalosCharged(true).build();
        DpsResult uncharged = compute(gear().build(), monster());
        DpsResult charged = compute(chargedGear, monster());

        int calculatedMaxHit = uncharged.maxHit();
        int expectedChargedMaxHit = (int) Math.floor(calculatedMaxHit * 0.75);
        assertEquals(expectedChargedMaxHit, charged.maxHit());
    }

    @Test
    public void unrelatedWeapon_regression_dpsUnchangedFromPlainGenericPath() {
        // A loadout that never sets tonalzticsOfRalosCharged must go through
        // the ordinary finish() path untouched - i.e. produce the exact same
        // result as before this stage existed. Cross-checked directly
        // against DamageDistribution.averageDamagePerAttack/expectedOverkill.
        EquipmentStats plain = gear().build();
        Monster target = monster();
        DpsResult result = compute(plain, target);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 150, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 100, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double avg = DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
        double overkill = DamageDistribution.expectedOverkill(maxHit, target.hitpoints());
        double dps = CombatMath.dps(avg, 4);

        assertEquals(maxHit, result.maxHit());
        assertEquals(hitChance, result.accuracy(), 1e-12);
        assertEquals(avg, result.avgHit(), 1e-9);
        assertEquals(dps, result.dps(), 1e-9);
        assertEquals(overkill, result.overkillPerKill(), 1e-9);
    }

    /**
     * The discriminating P1 regression test: the UNCHARGED Tonalztics must
     * NOT be an ordinary full-range 0..M single-hit weapon (the prior
     * defect, which inflated its max hit/DPS by 1/0.75 ~= 33%) — it rolls a
     * SINGLE hit over the SAME reduced 75% range the charged form uses per
     * hit. {@code fullRangeBaseline} below is a plain weapon of identical
     * stats with neither Tonalztics flag set — exactly the shape of the old
     * (wrong) uncharged model — so this test fails immediately if the
     * uncharged branch in {@code DpsCalculator#computeRanged} is ever
     * reverted/removed.
     */
    @Test
    public void uncharged_reportsSeventyFivePercentMaxHit_andIsStrictlyLessThanTheOldFullRangeModel() {
        DpsResult fullRangeBaseline = compute(gear().build(), monster()); // the old (wrong) uncharged model
        DpsResult uncharged = compute(gear().tonalzticsOfRalosUncharged(true).build(), monster());

        // Accuracy is unaffected by the reduced-range roll - only the damage side changes.
        assertEquals(fullRangeBaseline.accuracy(), uncharged.accuracy(), 1e-12);
        // Uncharged max hit is 75% of the calculated (full-range) max hit, floored.
        assertEquals((fullRangeBaseline.maxHit() * 3) / 4, uncharged.maxHit());
        assertTrue("uncharged max hit must be strictly less than the old full 0..M model "
                + "(75% < 100%) - this is the exact P1 defect this stage fixes",
                uncharged.maxHit() < fullRangeBaseline.maxHit());
        assertTrue("uncharged average damage must be strictly less than the old full-range model",
                uncharged.avgHit() < fullRangeBaseline.avgHit());
    }

    /**
     * Uncharged must remain a SINGLE hit: same reduced per-hit range as the
     * charged form, but roughly HALF its average damage (exactly half here,
     * since neither branch is capped, by linearity of expectation over the
     * identical per-hit distribution) - not equal to the charged form's
     * doubled-up average.
     */
    @Test
    public void uncharged_isSingleHit_averageIsExactlyHalfTheChargedForms() {
        DpsResult uncharged = compute(gear().tonalzticsOfRalosUncharged(true).build(), monster());
        DpsResult charged = compute(gear().tonalzticsOfRalosCharged(true).build(), monster());

        // Same reduced 75% range for both - only the hit COUNT differs.
        assertEquals(uncharged.maxHit(), charged.maxHit());
        assertTrue(uncharged.avgHit() < charged.avgHit());
        assertEquals(2.0 * uncharged.avgHit(), charged.avgHit(), 1e-6);
    }

    /**
     * End-to-end wiring check, mirroring {@code
     * charged_endToEnd_matchesTonalzticsDualHitFormulaDirectly} above:
     * recomputes the expected result independently via the plain
     * single-roll formulas over {@link TonalzticsDualHit#perHitMaxHit}, to
     * prove the {@code DpsCalculator} wiring - not just the isolated 75%
     * math - is correct.
     */
    @Test
    public void uncharged_endToEnd_matchesSingleReducedRangeRollDirectly() {
        EquipmentStats unchargedGear = gear().tonalzticsOfRalosUncharged(true).build();
        Monster target = monster();
        DpsResult result = compute(unchargedGear, target);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, 150, Fraction.ONE);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 100, Fraction.ONE);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        int perHit = TonalzticsDualHit.perHitMaxHit(maxHit);
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double avg = DamageDistribution.averageDamagePerAttack(hitChance, perHit);
        double overkill = DamageDistribution.expectedOverkill(perHit, target.hitpoints());
        double dps = CombatMath.dps(avg, 4);

        assertEquals(perHit, result.maxHit());
        assertEquals(hitChance, result.accuracy(), 1e-12);
        assertEquals(avg, result.avgHit(), 1e-9);
        assertEquals(dps, result.dps(), 1e-9);
        assertEquals(overkill, result.overkillPerKill(), 1e-9);
    }
}
