package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end Twinflame staff (item id 30634) tests via {@link DpsCalculator}:
 * 6-tick cast speed (unconditional) plus the elemental Bolt/Blast/Wave
 * second-hit passive (see {@link Spell#twinflameEligible()} / {@link
 * TwinflameSecondHit}). {@link TwinflameSecondHitMathTest} covers the pure
 * math in isolation; this file proves it is wired correctly into the actual
 * DPS pipeline.
 */
public class TwinflameStaffEffectTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
    private static final double DELTA = 1e-9;

    private static PlayerCombat mage99() {
        return PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.STANDARD)
                .build();
    }

    private static Monster plainTarget() {
        return Monster.builder()
                .name("Twinflame test target")
                .magicLevel(60)
                .defenceBonuses(0, 0, 0, 30, 0)
                .hitpoints(50_000) // large HP so overkill/TTK numbers stay well-behaved
                .build();
    }

    private static EquipmentStats gearWith(boolean twinflameStaff) {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(6)
                .twinflameStaff(twinflameStaff)
                .build();
    }

    // ---- The reporter's actual symptom -------------------------------------------------

    @Test
    public void fireWaveOutranksFireSurgeWithStaffEquipped() {
        EquipmentStats gear = gearWith(true);
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        double waveDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, Spell.FIRE_WAVE).dps();
        double surgeDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, Spell.FIRE_SURGE).dps();

        // Both cast at the same 6-tick speed with the staff equipped, so this
        // reduces to average damage: Wave gets a ~40%-of-first-hit second hit
        // (20 * 1.4 = 28) while Surge (higher base 24 but no second hit) does not.
        assertTrue("Fire Wave (with second hit) must outrank Fire Surge (no second hit) with the staff equipped",
                waveDps > surgeDps);
    }

    @Test
    public void fireSurgeOutranksFireWaveWithoutStaffEquipped() {
        EquipmentStats gear = gearWith(false);
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        double waveDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, Spell.FIRE_WAVE).dps();
        double surgeDps = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, Spell.FIRE_SURGE).dps();

        // Without the staff, both cast at 5 ticks and neither gets a second hit,
        // so Fire Surge's higher base max hit (24 vs 20) simply wins.
        assertTrue("Fire Surge must outrank Fire Wave without the staff equipped",
                surgeDps > waveDps);
    }

    // ---- Tier gating: Strike/Surge excluded, Bolt/Blast/Wave included -------------------

    @Test
    public void strikeAndSurgeGetNoSecondHit() {
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        for (Spell strikeOrSurge : new Spell[]{
                Spell.FIRE_STRIKE, Spell.WATER_STRIKE, Spell.EARTH_STRIKE, Spell.WIND_STRIKE,
                Spell.FIRE_SURGE, Spell.WATER_SURGE, Spell.EARTH_SURGE, Spell.WIND_SURGE}) {
            assertFalseSecondHit(strikeOrSurge, player, target);
        }
    }

    @Test
    public void boltBlastWaveGetASecondHitAcrossAllFourElements() {
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        for (Spell eligible : new Spell[]{
                Spell.FIRE_BOLT, Spell.WATER_BOLT, Spell.EARTH_BOLT, Spell.WIND_BOLT,
                Spell.FIRE_BLAST, Spell.WATER_BLAST, Spell.EARTH_BLAST, Spell.WIND_BLAST,
                Spell.FIRE_WAVE, Spell.WATER_WAVE, Spell.EARTH_WAVE, Spell.WIND_WAVE}) {
            assertTrueSecondHit(eligible, player, target);
        }
    }

    @Test
    public void ibanBlastGetsNoSecondHit_nameSubstringTrap() {
        // Iban Blast's display name contains "Blast", which the reference JS
        // implementation's name.includes('Blast') gate would wrongly match -
        // but it has no Element/Tier (element() == null), so it must not.
        assertFalseSecondHit(Spell.IBAN_BLAST, mage99(), plainTarget());
    }

    private static void assertFalseSecondHit(Spell spell, PlayerCombat player, Monster target) {
        DpsResult withStaff = DpsCalculator.compute(gearWith(true), player, CombatStyle.MAGIC, target, spell);
        DpsResult withoutStaff = DpsCalculator.compute(gearWith(false), player, CombatStyle.MAGIC, target, spell);
        // Same accuracy/maxHit either way (the staff flag alone carries no gear
        // bonuses in this test); only the cast speed differs (6 vs 5 ticks).
        // avgHit (damage per attack, ignoring speed) must be identical - no second hit added.
        assertEquals(spell.name() + " must get no second hit (avg damage unaffected)",
                withoutStaff.avgHit(), withStaff.avgHit(), DELTA);
    }

    private static void assertTrueSecondHit(Spell spell, PlayerCombat player, Monster target) {
        DpsResult withStaff = DpsCalculator.compute(gearWith(true), player, CombatStyle.MAGIC, target, spell);
        DpsResult withoutStaff = DpsCalculator.compute(gearWith(false), player, CombatStyle.MAGIC, target, spell);

        double expectedSecondHit = TwinflameSecondHit.secondHitAverage(withoutStaff.accuracy(), withoutStaff.maxHit());
        assertTrue(spell.name() + " must get a strictly positive second hit", expectedSecondHit > 0.0);
        assertEquals(spell.name() + " avgHit with the staff must equal the baseline plus the exact second-hit term",
                withoutStaff.avgHit() + expectedSecondHit, withStaff.avgHit(), DELTA);
    }

    // ---- Cast speed -----------------------------------------------------------------------

    @Test
    public void castSpeedIsSixTicksRegardlessOfSecondHitEligibility() {
        // Fire Strike is NOT twinflame-eligible (Strike tier), yet the staff's
        // cast speed override is unconditional: DPS with the staff must be
        // exactly 5/6 of DPS without it (same avgHit, different tick speed).
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        double dpsWithStaff = DpsCalculator.compute(gearWith(true), player, CombatStyle.MAGIC, target, Spell.FIRE_STRIKE).dps();
        double dpsWithoutStaff = DpsCalculator.compute(gearWith(false), player, CombatStyle.MAGIC, target, Spell.FIRE_STRIKE).dps();

        assertEquals(5.0 / 6.0, dpsWithStaff / dpsWithoutStaff, 1e-9);
    }

    // ---- Overkill/TTK must also reflect the second hit, not just the average ------------

    @Test
    public void overkillAndTtkDifferWhenSecondHitApplies() {
        PlayerCombat player = mage99();
        Monster smallTarget = Monster.builder()
                .name("Small twinflame overkill target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(15)
                .build();

        DpsResult withStaff = DpsCalculator.compute(gearWith(true), player, CombatStyle.MAGIC, smallTarget, Spell.FIRE_BLAST);
        DpsResult withoutStaff = DpsCalculator.compute(gearWith(false), player, CombatStyle.MAGIC, smallTarget, Spell.FIRE_BLAST);

        assertTrue("overkill with the second hit in play must differ from the single-hitsplat baseline",
                Math.abs(withStaff.overkillPerKill() - withoutStaff.overkillPerKill()) > 1e-9);
    }
}
