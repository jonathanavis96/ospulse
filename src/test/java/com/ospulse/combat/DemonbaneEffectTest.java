package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Emberlight's +70% vs-demon accuracy/damage bonus: it must (1) raise max hit
 * to exactly floor(base * 17/10) and raise DPS against a demon, (2) do nothing
 * against a non-demon, and (3) STACK with the on-task slayer helm bonus as a
 * separate sequential floor step (per the wiki DPS calc — unlike salve vs
 * slayer, which are one non-stacking slot).
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
    public void emberlight_stacksWithSlayerHelm_sequentialFloorSteps() {
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        // Per the wiki DPS calculator (weirdgloop PlayerVsNPCCalc.ts), melee
        // demonbane is the weapon's OWN passive, applied as a separate floor
        // step AFTER the salve/slayer target-bonus slot — it stacks with the
        // on-task slayer helm: floor(floor(base * 7/6) * 17/10).
        DpsResult noBonuses = compute(gear(DemonbaneWeapon.NONE, SlayerHeadgear.NONE), player(false), demon);
        DpsResult withSlayerToo = compute(gear(DemonbaneWeapon.EMBERLIGHT, SlayerHeadgear.STANDARD), player(true), demon);

        long slotStep = new Fraction(7, 6).applyFloor(noBonuses.maxHit());
        int expected = (int) new Fraction(17, 10).applyFloor(slotStep);
        assertEquals("slayer helm 7/6 then demonbane 17/10, each its own floor",
                expected, withSlayerToo.maxHit());

        DpsResult demonbaneOnly = compute(gear(DemonbaneWeapon.EMBERLIGHT, SlayerHeadgear.NONE), player(false), demon);
        assertTrue("adding the on-task helm must strictly raise DPS over demonbane alone",
                withSlayerToo.dps() > demonbaneOnly.dps());
    }

    @Test
    public void burningClaws_vsDemon_raisesMaxHitByFivePercentAndDps() {
        // Burning claws (item id 29577, cache-verified): melee (slash), +5%
        // accuracy AND damage vs demons -> Fraction(21, 20).
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        DpsResult base = compute(gear(DemonbaneWeapon.NONE, SlayerHeadgear.NONE), player(false), demon);
        DpsResult claws = compute(gear(DemonbaneWeapon.BURNING_CLAWS, SlayerHeadgear.NONE), player(false), demon);

        int expectedMaxHit = (int) new Fraction(21, 20).applyFloor(base.maxHit());
        assertEquals("max hit must be floor(base * 1.05) vs a demon", expectedMaxHit, claws.maxHit());
        assertTrue("Burning claws must raise DPS vs a demon (" + claws.dps() + " vs " + base.dps() + ")",
                claws.dps() > base.dps());
    }

    @Test
    public void burningClaws_vsNonDemon_hasNoEffect() {
        Monster notDemon = monster(EnumSet.noneOf(MonsterAttribute.class));
        DpsResult base = compute(gear(DemonbaneWeapon.NONE, SlayerHeadgear.NONE), player(false), notDemon);
        DpsResult claws = compute(gear(DemonbaneWeapon.BURNING_CLAWS, SlayerHeadgear.NONE), player(false), notDemon);

        assertEquals("no demonbane bonus vs a non-demon", base.maxHit(), claws.maxHit());
        assertEquals("no demonbane bonus vs a non-demon", base.dps(), claws.dps(), 1e-9);
    }

    @Test
    public void burningClaws_appliesOnAllMeleeStyles() {
        // The claws' style family marker is SLASH, but per the wired melee
        // demonbane rule (DemonbaneWeapon#appliesTo), the passive applies to
        // ALL melee styles (STAB/SLASH/CRUSH), matching the sword line.
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        assertTrue(DemonbaneWeapon.BURNING_CLAWS.appliesTo(CombatStyle.STAB));
        assertTrue(DemonbaneWeapon.BURNING_CLAWS.appliesTo(CombatStyle.SLASH));
        assertTrue(DemonbaneWeapon.BURNING_CLAWS.appliesTo(CombatStyle.CRUSH));
        assertTrue("sanity: gear still usable for a CRUSH-style calc",
                compute(gear(DemonbaneWeapon.BURNING_CLAWS, SlayerHeadgear.NONE), player(false), demon).maxHit() > 0);
    }

    @Test
    public void burningClaws_stacksWithSlayerHelm_sequentialFloorSteps() {
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        DpsResult noBonuses = compute(gear(DemonbaneWeapon.NONE, SlayerHeadgear.NONE), player(false), demon);
        DpsResult withSlayerToo = compute(gear(DemonbaneWeapon.BURNING_CLAWS, SlayerHeadgear.STANDARD), player(true), demon);

        long slotStep = new Fraction(7, 6).applyFloor(noBonuses.maxHit());
        int expected = (int) new Fraction(21, 20).applyFloor(slotStep);
        assertEquals("slayer helm 7/6 then demonbane 21/20, each its own floor",
                expected, withSlayerToo.maxHit());
    }
}
