package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * Worked example applying the exact, verbatim OSRS Wiki melee DPS formulas
 * (see {@link CombatMath} citations) to a concrete setup. The wiki's DPS
 * calculator formula pages (Damage per second/Melee, Maximum melee hit) do
 * not themselves publish a narrated numeric walkthrough, so this test's
 * expected numbers were derived by hand-applying those exact documented
 * formula steps (cross-checked against the Prayer page's published Piety
 * percentages: "+20% Attack, +23% Strength") and independently verified with
 * a Python script before being hard-coded here — every intermediate is
 * asserted, not just the final number, so any regression in a specific
 * floor step is caught precisely.
 *
 * <p>Setup: 99 Attack, 99 Strength, Piety active, Aggressive stance
 * (so the strength calc gets +3, the attack calc gets +0), no potions/void,
 * +100 slash attack bonus / +108 strength bonus gear, vs a target with 70
 * Defence and +60 slash defence bonus, 4-tick weapon.
 */
public class MeleeDpsWorkedExampleTest {
    private static final double DELTA = 1e-9;

    @Test
    public void effectiveStrengthLevel_piety_aggressive() {
        // floor(99 * 1.23) = floor(121.77) = 121; +3 (aggressive) +8 = 132; no void.
        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.23, 3, 8, 1.0);
        assertEquals(132, effStr);
    }

    @Test
    public void effectiveAttackLevel_piety_aggressive() {
        // floor(99 * 1.20) = floor(118.8) = 118; +0 (aggressive gives no attack bonus) +8 = 126.
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.20, 0, 8, 1.0);
        assertEquals(126, effAtt);
    }

    @Test
    public void maxHit_worked() {
        // floor((132 * (108 + 64) + 320) / 640) = floor((132*172 + 320)/640) = floor(23024/640) = floor(35.975) = 35.
        int maxHit = CombatMath.meleeOrRangedMaxHit(132, 108, Fraction.ONE);
        assertEquals(35, maxHit);
    }

    @Test
    public void attackRoll_worked() {
        // 126 * (100 + 64) = 126 * 164 = 20664 (already integer, single floor is a no-op here).
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(126, 100, Fraction.ONE);
        assertEquals(20664, attackRoll);
    }

    @Test
    public void hitChance_worked_accurateBranch() {
        // attackRoll (20664) > defenceRoll (9796): 1 - (9796+2)/(2*(20664+1)) = 1 - 9798/41330.
        double hitChance = CombatMath.hitChance(20664, 9796);
        assertEquals(0.7629324945560125, hitChance, DELTA);
    }

    @Test
    public void endToEndDps_worked() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 100, 0, 0, 0, // astab,aslash,acrush,amagic,arange
                        0, 0, 0, 0, 0, // dstab,dslash,dcrush,dmagic,drange
                        108, 0, 0.0, 0) // str,rstr,mdmg,prayer
                .weaponSpeedTicks(4)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .attack(99, 99)
                .strength(99, 99)
                .stance(Stance.AGGRESSIVE)
                .activePrayers(EnumSet.of(OffensivePrayer.PIETY))
                .build();

        Monster target = Monster.builder()
                .name("Test dummy")
                .defenceLevel(70)
                .defenceBonuses(0, 60, 0, 0, 0) // dstab,dslash,dcrush,dmagic,drange
                .hitpoints(100)
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.SLASH, target, 0);

        assertEquals(35, result.maxHit());
        assertEquals(0.7629324945560125, result.accuracy(), DELTA);
        assertEquals(5.571879676676435, result.dps(), DELTA);
        assertEquals(false, result.baseEstimate());
    }
}
