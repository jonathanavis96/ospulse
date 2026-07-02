package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * Worked example applying the exact, verbatim OSRS Wiki ranged DPS formulas
 * ({@link CombatMath}, sourced from Damage per second/Ranged + Maximum
 * ranged hit) to a concrete setup. As with the melee test, these numbers
 * were derived by hand-applying the wiki's documented formula steps
 * (independently verified with a Python script) since the wiki pages
 * themselves don't publish a narrated numeric walkthrough.
 *
 * <p>Setup: 99 Ranged, Rigour active, Accurate stance (+3 to BOTH effective
 * ranged attack and effective ranged strength, per the wiki's ranged-specific
 * rule), no potions/void, +110 ranged attack bonus / +90 ranged strength
 * bonus gear, vs a target with 50 Defence and +40 ranged defence bonus,
 * 5-tick weapon.
 */
public class RangedDpsWorkedExampleTest {
    private static final double DELTA = 1e-9;

    @Test
    public void effectiveRangedStrength_rigour_accurate() {
        // floor(99 * 1.23) = 121; (121 + 3 + 8) * 1.0 = 132.
        int effRstr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.23, 3, 8, 1.0);
        assertEquals(132, effRstr);
    }

    @Test
    public void effectiveRangedAttack_rigour_accurate() {
        // floor(99 * 1.20) = 118; (118 + 3 + 8) * 1.0 = 129.
        int effRatt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.20, 3, 8, 1.0);
        assertEquals(129, effRatt);
    }

    @Test
    public void maxHit_worked() {
        // floor((132 * (90+64) + 320)/640) = floor((132*154+320)/640) = floor(20648/640) = floor(32.2625) = 32.
        int maxHit = CombatMath.meleeOrRangedMaxHit(132, 90, Fraction.ONE);
        assertEquals(32, maxHit);
    }

    @Test
    public void attackRoll_worked() {
        // 129 * (110+64) = 129*174 = 22446.
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(129, 110, Fraction.ONE);
        assertEquals(22446, attackRoll);
    }

    @Test
    public void hitChance_worked_accurateBranch() {
        // defenceRoll = (50+9)*(40+64) = 59*104 = 6136. attackRoll(22446) > defenceRoll: 1 - (6136+2)/(2*22447).
        int defenceRoll = CombatMath.npcDefenceRoll(50, 40);
        assertEquals(6136, defenceRoll);
        double hitChance = CombatMath.hitChance(22446, defenceRoll);
        assertEquals(0.8632779436004812, hitChance, DELTA);
    }

    @Test
    public void endToEndDps_worked() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 0, 110, // astab,aslash,acrush,amagic,arange
                        0, 0, 0, 0, 0,
                        0, 90, 0.0, 0) // str,rstr,mdmg,prayer
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .ranged(99, 99)
                .stance(Stance.ACCURATE)
                .activePrayers(EnumSet.of(OffensivePrayer.RIGOUR))
                .build();

        Monster target = Monster.builder()
                .name("Test dummy")
                .defenceLevel(50)
                .defenceBonuses(0, 0, 0, 0, 40) // drange
                .hitpoints(100)
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.RANGED, target, 0);

        assertEquals(32, result.maxHit());
        assertEquals(0.8632779436004812, result.accuracy(), DELTA);
        assertEquals(4.612869011764188, result.dps(), DELTA);
    }

    @Test
    public void hitChance_worked_inaccurateBranch() {
        // attackRoll <= defenceRoll branch: hitChance = atkRoll / (2*(defRoll+1)).
        double hitChance = CombatMath.hitChance(4000, 9000);
        assertEquals(0.22219753360737696, hitChance, DELTA);
    }
}
