package com.ospulse.combat;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Elemental weakness (frost dragons and ~1200 other monsters have one, per
 * the bundled monster data's {@code weakness.element}/{@code severity} —
 * see monsters.min.json.README.md). Casting the matching element must do
 * strictly more damage/DPS than the other three elements of the same tier;
 * a monster with no weakness must rank all four elements equal.
 *
 * <p>Formula under test (DpsCalculator.computeMagic): when the cast spell's
 * {@link Spell.Element} matches the monster's {@code weaknessElement}, the
 * bonus is a SEPARATE additive term {@code floor(baseSpellMaxHit *
 * weaknessSeverity%)}, computed from the spell's base max hit and floored on
 * its own, then added to the max hit as the final damage modifier — matching
 * weirdgloop/osrs-dps-calc ({@code maxHit += trunc(baseMax * severity/100)})
 * and the OSRS Wiki "Maximum magic hit" (two independent floors). It is NOT
 * folded into the caster's magic-damage percent; that single fold diverges
 * by 1 in ~12% of gear/level combos, which
 * {@link #weaknessIsASeparateFlooredTermNotFoldedIntoTheDamagePercent()} pins.
 */
public class ElementalWeaknessTest {
    private static PlayerCombat mage99() {
        return PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.STANDARD)
                .build();
    }

    private static EquipmentStats plainMagicGear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(5)
                .build();
    }

    @Test
    public void frostDragonHasFireWeaknessInBundledData() {
        Optional<Monster> frostDragon = MonsterRepository.getInstance().byName("Frost dragon");
        assertTrue("Frost dragon must be present in the bundled data", frostDragon.isPresent());
        Monster m = frostDragon.get();
        assertEquals("FIRE", m.weaknessElement());
        assertTrue("Frost dragon's weakness severity must be positive", m.weaknessSeverity() > 0);
    }

    @Test
    public void frostDragonTakesStrictlyMoreDamageFromFireThanOtherElementsSameTier() {
        Monster frostDragon = MonsterRepository.getInstance().byName("Frost dragon").get();
        EquipmentStats gear = plainMagicGear();
        PlayerCombat player = mage99();

        // Same tier (Blast) across all four elements.
        double fireDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, frostDragon, Spell.FIRE_BLAST).dps();
        double waterDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, frostDragon, Spell.WATER_BLAST).dps();
        double windDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, frostDragon, Spell.WIND_BLAST).dps();
        double earthDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, frostDragon, Spell.EARTH_BLAST).dps();

        assertTrue("Fire Blast must beat Water Blast on a frost dragon", fireDps > waterDps);
        assertTrue("Fire Blast must beat Wind Blast on a frost dragon", fireDps > windDps);
        assertTrue("Fire Blast must beat Earth Blast on a frost dragon", fireDps > earthDps);

        int fireMaxHit = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, frostDragon, Spell.FIRE_BLAST).maxHit();
        int waterMaxHit = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, frostDragon, Spell.WATER_BLAST).maxHit();
        assertTrue("Fire Blast's max hit must be strictly higher than Water Blast's on a frost dragon (fire base "
                        + Spell.FIRE_BLAST.baseMaxHit() + " vs water base " + Spell.WATER_BLAST.baseMaxHit() + ")",
                fireMaxHit > waterMaxHit);
    }

    @Test
    public void monsterWithNoWeaknessRanksAllFourElementsEqual() {
        Monster noWeakness = Monster.builder()
                .name("No-weakness target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .build();
        EquipmentStats gear = plainMagicGear();
        PlayerCombat player = mage99();

        double fireDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, noWeakness, Spell.FIRE_BLAST).dps();
        double waterDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, noWeakness, Spell.WATER_BLAST).dps();
        double windDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, noWeakness, Spell.WIND_BLAST).dps();
        double earthDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, noWeakness, Spell.EARTH_BLAST).dps();

        // Wind/Water/Earth/Fire Blast share the same tier-cap max hit per the
        // Spell class javadoc's within-tier scaling simplification, so with no
        // weakness bonus in play they must be exactly equal.
        assertEquals(fireDps, waterDps, 1e-9);
        assertEquals(fireDps, windDps, 1e-9);
        assertEquals(fireDps, earthDps, 1e-9);
    }

    @Test
    public void weaknessIsASeparateFlooredTermNotFoldedIntoTheDamagePercent() {
        // Fire Blast (base max 16, FIRE) vs a FIRE-weak monster with severity 56,
        // wearing 13% magic-damage gear. The two approaches diverge here:
        //   separate (correct): floor(16 * 1.13)=18, + floor(16 * 0.56)=8  -> 26
        //   folded  (rejected): floor(16 * (1 + 0.13 + 0.56)) = floor(27.04) -> 27
        // Asserting 26 pins the two-independent-floor semantics and would fail if
        // the weakness bonus were ever folded back into the magic-damage percent.
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
                .build();
        PlayerCombat player = mage99();

        assertEquals(16, Spell.FIRE_BLAST.baseMaxHit());
        int maxHit = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, fireWeak, Spell.FIRE_BLAST).maxHit();
        assertEquals(26, maxHit);
    }

    @Test
    public void nonElementalSpellsNeverGetAWeaknessBonus() {
        Monster frostDragon = MonsterRepository.getInstance().byName("Frost dragon").get();
        EquipmentStats gear = plainMagicGear();
        PlayerCombat player = mage99();

        // Ice Barrage has no element (all Ancient Magicks are elementless) -
        // its max hit must be exactly its base max hit (no weakness bonus),
        // even though "ice" sounds thematically opposed to a frost dragon.
        DpsResult iceBarrage = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, frostDragon, Spell.ICE_BARRAGE);
        assertEquals(Spell.ICE_BARRAGE.baseMaxHit(), iceBarrage.maxHit());
    }
}
