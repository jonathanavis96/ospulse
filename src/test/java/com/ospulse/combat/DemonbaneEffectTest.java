package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Emberlight's +70% vs-demon accuracy/damage bonus: it must (1) raise max hit
 * to exactly floor(base * 17/10) and raise DPS against a demon, (2) do nothing
 * against a non-demon, and (3) stack multiplicatively with the on-task slayer
 * helm bonus (both apply — unlike salve vs slayer, which don't stack).
 *
 * <p>Regression for the GearScape mismatch on Cerberus: the engine previously
 * ignored demonbane entirely, so an Emberlight loadout underestimated DPS.
 */
public class DemonbaneEffectTest {

    /** A representative max-melee STAB loadout; demonbaneWeapon set by the caller. */
    private static EquipmentStats gear(DemonbaneWeapon demonbane, SlayerHeadgear slayer) {
        return EquipmentStats.builder()
                .add(140, 0, 0, 0, 0, 0, 0, 0, 0, 0, 130, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .demonbaneWeapon(demonbane)
                .slayerHeadgear(slayer)
                .build();
    }

    private static PlayerCombat player(boolean onTask) {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).defence(99, 99).ranged(99, 99).magic(99, 99)
                .prayer(99, 99).hitpoints(99, 99)
                .stance(Stance.AGGRESSIVE)
                .onSlayerTask(onTask)
                .build();
    }

    private static Monster monster(Set<MonsterAttribute> attributes) {
        return Monster.builder()
                .name("Test")
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 100, 25, 65, 100) // Cerberus-like
                .magicLevel(220)
                .attributes(attributes)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear, PlayerCombat player, Monster target) {
        return DpsCalculator.compute(gear, player, CombatStyle.STAB, target, 20);
    }

    @Test
    public void emberlight_vsDemon_raisesMaxHitBySeventyPercentAndDps() {
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        DpsResult base = compute(gear(DemonbaneWeapon.NONE, SlayerHeadgear.NONE), player(false), demon);
        DpsResult ember = compute(gear(DemonbaneWeapon.EMBERLIGHT, SlayerHeadgear.NONE), player(false), demon);

        int expectedMaxHit = (int) new Fraction(17, 10).applyFloor(base.maxHit());
        assertEquals("max hit must be floor(base * 1.7) vs a demon", expectedMaxHit, ember.maxHit());
        assertTrue("demonbane must raise DPS vs a demon (" + ember.dps() + " vs " + base.dps() + ")",
                ember.dps() > base.dps());
    }

    @Test
    public void emberlight_vsNonDemon_hasNoEffect() {
        Monster notDemon = monster(EnumSet.noneOf(MonsterAttribute.class));
        DpsResult base = compute(gear(DemonbaneWeapon.NONE, SlayerHeadgear.NONE), player(false), notDemon);
        DpsResult ember = compute(gear(DemonbaneWeapon.EMBERLIGHT, SlayerHeadgear.NONE), player(false), notDemon);

        assertEquals("no demonbane bonus vs a non-demon", base.maxHit(), ember.maxHit());
        assertEquals("no demonbane bonus vs a non-demon", base.dps(), ember.dps(), 1e-9);
    }

    @Test
    public void emberlight_doesNotStackWithSlayerHelm_demonbaneWins() {
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        // Demonbane and the slayer helm share the single non-stacking target
        // bonus slot; the higher (demonbane 1.7 > slayer 7/6) wins, so on-task
        // with a slayer helm must give the SAME numbers as demonbane alone —
        // NOT base * 7/6 * 1.7. (Matches GearScape's Cerberus figures.)
        DpsResult demonbaneOnly = compute(gear(DemonbaneWeapon.EMBERLIGHT, SlayerHeadgear.NONE), player(false), demon);
        DpsResult withSlayerToo = compute(gear(DemonbaneWeapon.EMBERLIGHT, SlayerHeadgear.STANDARD), player(true), demon);

        assertEquals("slayer helm must not stack on top of demonbane",
                demonbaneOnly.maxHit(), withSlayerToo.maxHit());
        assertEquals("slayer helm must not stack on top of demonbane",
                demonbaneOnly.dps(), withSlayerToo.dps(), 1e-9);
    }
}
