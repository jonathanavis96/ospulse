package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * Magic end-to-end sanity check (not a wiki-narrated worked example - the
 * task's mandatory 3 worked examples are melee/ranged; this exercises the
 * Tier-A magic path: Augury's accuracy multiplier + magic-damage%, gear
 * mdmg%, and the "baseSpellMaxHit scaled by magic-damage%" simplification).
 * Expected numbers independently derived by hand-applying the documented
 * formulas (DPS/Magic, Maximum magic hit) with a Python script.
 */
public class MagicDpsTest {
    private static final double DELTA = 1e-9;

    @Test
    public void endToEnd_augury_gearMdmg_noTaskBonus() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, // amagic
                        0, 0, 0, 0, 0,
                        0, 0, 10.0, 0) // mdmg = 10%
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.ACCURATE)
                .activePrayers(EnumSet.of(OffensivePrayer.AUGURY))
                .build();

        Monster target = Monster.builder()
                .name("Magic target")
                .magicLevel(60)
                .defenceBonuses(0, 0, 0, 30, 0) // dmagic
                .hitpoints(100)
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, 30);

        // primary = floor(30 * (1 + (10 + 4)/100)) = floor(34.2) = 34; no on-task slayer bonus.
        assertEquals(34, result.maxHit());
        assertEquals(0.8534844857955829, result.accuracy(), DELTA);
        assertEquals(4.844540509849213, result.dps(), DELTA);
    }

    @Test
    public void slayerHelmImbued_onTask_addsFifteenPercentPreHitRoll() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .slayerHeadgear(SlayerHeadgear.IMBUED)
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .magic(99, 99)
                .onSlayerTask(true)
                .build();

        Monster target = Monster.builder()
                .name("Task target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, 20);

        // primary = floor(20 * 1.0) = 20 (no mdmg/prayer). Pre-hit-roll: floor(20 * 23/20) = floor(23.0) = 23.
        assertEquals(23, result.maxHit());
    }

    @Test
    public void slayerBonus_doesNotStackWithSalveVsUndead() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .slayerHeadgear(SlayerHeadgear.IMBUED)
                .salveType(SalveType.SALVE_EI)
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .magic(99, 99)
                .onSlayerTask(true)
                .build();

        Monster undeadTarget = Monster.builder()
                .name("Undead task target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .attributes(EnumSet.of(MonsterAttribute.UNDEAD))
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, undeadTarget, 20);

        // Salve (ei) additive +20% wins the "primary damage" stage; the slayer +15%
        // pre-hit-roll multiplicative stage must NOT also apply (mutual exclusion).
        // primary = floor(20 * 1.20) = 24; pre-hit-roll unchanged since slayer bonus is suppressed.
        assertEquals(24, result.maxHit());
    }
}
