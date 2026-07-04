package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Charged elemental tome (Tome of fire/water/earth) magic-damage boost —
 * DpsCalculator.computeMagic. Each grants +10% damage vs NPCs to its element's
 * standard-spellbook spell, applied as weirdgloop's final {@code MAX_HIT_TOME}
 * step {@code floor(maxHit * 11/10)} — AFTER the elemental-weakness bonus.
 *
 * <p>Before this the tome was unmodelled (its equipment magic-damage stat is 0
 * because the bonus is conditional), so the optimiser under-valued e.g. the
 * Tome of fire against a fire-weak dragon and preferred a plain magic book.
 */
public class TomeTest {
    private static PlayerCombat mage99() {
        return PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.STANDARD)
                .build();
    }

    private static EquipmentStats gearWithTome(Tome tome) {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(5)
                .tome(tome)
                .build();
    }

    private static Monster noWeaknessTarget() {
        return Monster.builder()
                .name("No-weakness target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .build();
    }

    @Test
    public void fireTomeBoostsFireSpellByTenPercentFloored() {
        Monster target = noWeaknessTarget();
        PlayerCombat player = mage99();

        // Fire Blast base max 16, no magic-damage gear, no weakness:
        //   no tome  -> 16
        //   fire tome -> floor(16 * 11/10) = floor(17.6) = 17
        int withoutTome = DpsCalculator.compute(gearWithTome(Tome.NONE), player, CombatStyle.MAGIC, target, Spell.FIRE_BLAST).maxHit();
        int withTome = DpsCalculator.compute(gearWithTome(Tome.FIRE), player, CombatStyle.MAGIC, target, Spell.FIRE_BLAST).maxHit();
        assertEquals(16, withoutTome);
        assertEquals(17, withTome);
    }

    @Test
    public void fireTomeDoesNotBoostANonFireSpell() {
        Monster target = noWeaknessTarget();
        PlayerCombat player = mage99();
        // Water Blast (base 16, WATER) with a FIRE tome: no bonus.
        int water = DpsCalculator.compute(gearWithTome(Tome.FIRE), player, CombatStyle.MAGIC, target, Spell.WATER_BLAST).maxHit();
        assertEquals(16, water);
    }

    @Test
    public void waterTomeBoostsWaterSpell() {
        Monster target = noWeaknessTarget();
        PlayerCombat player = mage99();
        int water = DpsCalculator.compute(gearWithTome(Tome.WATER), player, CombatStyle.MAGIC, target, Spell.WATER_BLAST).maxHit();
        assertEquals(17, water);
    }

    @Test
    public void tomeAppliesAfterTheElementalWeaknessBonus() {
        // Fire Blast (base 16) vs a FIRE-weak monster (severity 56), 13% magic-damage gear:
        //   primary floor(16 * 1.13) = 18, + weakness floor(16 * 0.56) = 8  -> 26 (ElementalWeaknessTest)
        //   THEN the tome:  floor(26 * 11/10) = floor(28.6) = 28
        // If the tome were (wrongly) applied BEFORE the weakness it would be
        // floor(18 * 1.1)=19, +8 = 27 — so 28 pins weirdgloop's tome-last ordering.
        Monster fireWeak = Monster.builder()
                .name("Fire-weak target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .weakness("FIRE", 56)
                .build();
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 13.0, 0) // 13% magic damage
                .weaponSpeedTicks(5)
                .tome(Tome.FIRE)
                .build();

        int maxHit = DpsCalculator.compute(gear, mage99(), CombatStyle.MAGIC, fireWeak, Spell.FIRE_BLAST).maxHit();
        assertEquals(28, maxHit);
    }
}
