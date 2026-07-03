package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Dragon hunter wand (magic) dragonbane passive: +75% magic accuracy and
 * +40% magic damage vs draconic targets, verified against the OSRS Wiki
 * 2026-07-03. Mirrors {@link TierBEffectsTest}'s Dragon Hunter lance/crossbow
 * coverage but for the MAGIC path.
 */
public class DragonHunterWandEffectTest {

    private static EquipmentStats.Builder plainMagicGear() {
        // +100 amagic, +0 mdmg, speed 5 (wand-ish).
        return EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(5);
    }

    private static PlayerCombat player99() {
        return PlayerCombat.builder()
                .attack(99, 99)
                .strength(99, 99)
                .ranged(99, 99)
                .magic(99, 99)
                .stance(Stance.ACCURATE)
                .build();
    }

    private static Monster.Builder monster(int hp) {
        return Monster.builder()
                .name("Target")
                .hitpoints(hp)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1);
    }

    @Test
    public void dragonHunterWand_vsDragon_multipliesBothRolls() {
        Monster dragon = monster(200).attributes(EnumSet.of(MonsterAttribute.DRAGON)).build();
        Monster notDragon = monster(200).build();

        EquipmentStats wand = plainMagicGear().dragonHunterWeapon(DragonHunterWeapon.WAND).build();

        DpsResult vsDragon = DpsCalculator.compute(wand, player99(), CombatStyle.MAGIC, dragon, 24);
        DpsResult offTarget = DpsCalculator.compute(wand, player99(), CombatStyle.MAGIC, notDragon, 24);

        // The passive must NOT fire off-dragon.
        assertEquals(24, offTarget.maxHit());

        // vs dragon: damage floor(24 * 7/5) = 33; accuracy strictly higher too.
        int expectedMaxHit = (int) Math.floor(offTarget.maxHit() * 1.4);
        assertEquals(expectedMaxHit, vsDragon.maxHit());
        assertTrue(vsDragon.maxHit() > offTarget.maxHit());
        assertTrue(vsDragon.accuracy() > offTarget.accuracy());
    }

    @Test
    public void dragonHunterWand_vsNonDragon_noBonus() {
        Monster dragon = monster(200).attributes(EnumSet.of(MonsterAttribute.DRAGON)).build();
        Monster notDragon = monster(200).build();

        EquipmentStats wand = plainMagicGear().dragonHunterWeapon(DragonHunterWeapon.WAND).build();

        DpsResult vsDragon = DpsCalculator.compute(wand, player99(), CombatStyle.MAGIC, dragon, 24);
        DpsResult vsNonDragon = DpsCalculator.compute(wand, player99(), CombatStyle.MAGIC, notDragon, 24);

        assertTrue(vsDragon.maxHit() > vsNonDragon.maxHit());
    }
}
