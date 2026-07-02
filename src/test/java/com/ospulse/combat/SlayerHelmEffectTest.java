package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * Slayer helm(i)/black mask on-task bonus: melee x7/6, gated on
 * {@code player.onSlayerTask() && gear.slayerHeadgear().wornAtAll()}.
 * https://oldschool.runescape.wiki/w/Maximum_melee_hit
 * "If slaying monsters on task while wearing a black mask or slayer helm, gear bonus is 7/6."
 *
 * <p>Reuses the same base setup as {@link MeleeDpsWorkedExampleTest}
 * (base max hit = 35 before any target-specific gear bonus).
 */
public class SlayerHelmEffectTest {
    private static PlayerCombat.Builder basePlayer() {
        return PlayerCombat.builder()
                .attack(99, 99)
                .strength(99, 99)
                .stance(Stance.AGGRESSIVE)
                .activePrayers(EnumSet.of(OffensivePrayer.PIETY));
    }

    private static EquipmentStats.Builder baseGear() {
        return EquipmentStats.builder()
                .add(0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 108, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static Monster.Builder baseTarget() {
        return Monster.builder().name("Slayer target").defenceLevel(70)
                .defenceBonuses(0, 60, 0, 0, 0).hitpoints(100);
    }

    @Test
    public void onTaskWithSlayerHelm_boostsMaxHitBySevenSixths() {
        EquipmentStats gear = baseGear().slayerHeadgear(SlayerHeadgear.STANDARD).build();
        PlayerCombat player = basePlayer().onSlayerTask(true).build();
        Monster target = baseTarget().build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.SLASH, target, 0);

        // floor(35 * 7/6) = floor(40.8333) = 40.
        assertEquals(40, result.maxHit());
    }

    @Test
    public void offTask_noBonusEvenWithHelmWorn() {
        EquipmentStats gear = baseGear().slayerHeadgear(SlayerHeadgear.STANDARD).build();
        PlayerCombat player = basePlayer().onSlayerTask(false).build();
        Monster target = baseTarget().build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.SLASH, target, 0);

        assertEquals(35, result.maxHit());
    }

    @Test
    public void onTask_noHelmWorn_noBonus() {
        EquipmentStats gear = baseGear().slayerHeadgear(SlayerHeadgear.NONE).build();
        PlayerCombat player = basePlayer().onSlayerTask(true).build();
        Monster target = baseTarget().build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.SLASH, target, 0);

        assertEquals(35, result.maxHit());
    }
}
