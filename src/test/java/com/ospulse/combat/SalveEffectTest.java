package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * Salve amulet (e) vs Undead: melee x1.2, gated on {@code target.isUndead()}.
 * https://oldschool.runescape.wiki/w/Maximum_melee_hit
 * "If using salve amulet (e) or (ei) [vs undead], gear bonus is 1.2."
 *
 * <p>Reuses the same base setup as {@link MeleeDpsWorkedExampleTest}
 * (base max hit = 35 before any target-specific gear bonus).
 */
public class SalveEffectTest {
    private static PlayerCombat basePlayer() {
        return PlayerCombat.builder()
                .attack(99, 99)
                .strength(99, 99)
                .stance(Stance.AGGRESSIVE)
                .activePrayers(EnumSet.of(OffensivePrayer.PIETY))
                .build();
    }

    private static EquipmentStats.Builder baseGear() {
        return EquipmentStats.builder()
                .add(0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 108, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static Monster.Builder targetBuilder() {
        return Monster.builder().name("Salve target").defenceLevel(70).defenceBonuses(0, 60, 0, 0, 0).hitpoints(100);
    }

    @Test
    public void salveE_vsUndead_boostsMaxHitByOnePointTwo() {
        EquipmentStats gear = baseGear().salveType(SalveType.SALVE_E).build();
        Monster undead = targetBuilder().attributes(java.util.EnumSet.of(MonsterAttribute.UNDEAD)).build();

        DpsResult result = DpsCalculator.compute(gear, basePlayer(), CombatStyle.SLASH, undead, 0);

        // floor(35 * 1.2) = floor(42.0) = 42.
        assertEquals(42, result.maxHit());
    }

    @Test
    public void salveE_vsNonUndead_noBonus() {
        EquipmentStats gear = baseGear().salveType(SalveType.SALVE_E).build();
        Monster nonUndead = targetBuilder().build(); // no attributes

        DpsResult result = DpsCalculator.compute(gear, basePlayer(), CombatStyle.SLASH, nonUndead, 0);

        assertEquals(35, result.maxHit());
    }

    @Test
    public void plainSalve_vsUndead_sevenSixthsNotOnePointTwo() {
        EquipmentStats gear = baseGear().salveType(SalveType.SALVE).build();
        Monster undead = targetBuilder().attributes(java.util.EnumSet.of(MonsterAttribute.UNDEAD)).build();

        DpsResult result = DpsCalculator.compute(gear, basePlayer(), CombatStyle.SLASH, undead, 0);

        // floor(35 * 7/6) = 40, not 42 - the plain (non-enchanted) amulet only gets 7/6.
        assertEquals(40, result.maxHit());
    }

    @Test
    public void salveAndSlayerHelm_doNotStack_salveWins() {
        EquipmentStats gear = baseGear().salveType(SalveType.SALVE_E).slayerHeadgear(SlayerHeadgear.STANDARD).build();
        Monster undead = targetBuilder().attributes(java.util.EnumSet.of(MonsterAttribute.UNDEAD)).build();
        PlayerCombat onTaskPlayer = PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).stance(Stance.AGGRESSIVE)
                .activePrayers(EnumSet.of(OffensivePrayer.PIETY))
                .onSlayerTask(true)
                .build();

        DpsResult result = DpsCalculator.compute(gear, onTaskPlayer, CombatStyle.SLASH, undead, 0);

        // Salve (e)'s 1.2x wins over the slayer helm's 7/6 (~1.167x) per the wiki's non-stacking rule.
        assertEquals(42, result.maxHit());
    }
}
