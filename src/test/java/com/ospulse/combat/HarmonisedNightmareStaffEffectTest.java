package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * End-to-end Harmonised nightmare staff (item id 24423) tests via {@link
 * DpsCalculator}: 4-tick cast speed while autocasting an offensive standard
 * spell, 5 ticks otherwise (no discount on Ancient Magicks/Arceuus, which the
 * staff cannot even autocast per the OSRS Wiki) — see {@link
 * MagicCastSpeedTest} for the pure-function version of these same cases.
 */
public class HarmonisedNightmareStaffEffectTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    private static PlayerCombat mage99() {
        return PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.STANDARD)
                .build();
    }

    private static Monster plainTarget() {
        return Monster.builder()
                .name("Harmonised test target")
                .magicLevel(60)
                .defenceBonuses(0, 0, 0, 30, 0)
                .hitpoints(50_000)
                .build();
    }

    private static EquipmentStats gearWith(boolean harmonisedNightmareStaff) {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .harmonisedNightmareStaff(harmonisedNightmareStaff)
                .build();
    }

    @Test
    public void fourTicks_autocastingStandardSpell() {
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        double dpsWithStaff = DpsCalculator.compute(gearWith(true), player, CombatStyle.MAGIC, target, Spell.FIRE_BOLT).dps();
        double dpsWithoutStaff = DpsCalculator.compute(gearWith(false), player, CombatStyle.MAGIC, target, Spell.FIRE_BOLT).dps();

        // Same accuracy/maxHit (the flag alone carries no gear bonus), only the
        // cast speed differs (4 vs 5 ticks): the ratio must be exactly 5/4.
        assertEquals(5.0 / 4.0, dpsWithStaff / dpsWithoutStaff, 1e-9);
    }

    @Test
    public void fiveTicks_onAncientMagicks_noDiscount() {
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        double dpsWithStaff = DpsCalculator.compute(gearWith(true), player, CombatStyle.MAGIC, target, Spell.ICE_BARRAGE).dps();
        double dpsWithoutStaff = DpsCalculator.compute(gearWith(false), player, CombatStyle.MAGIC, target, Spell.ICE_BARRAGE).dps();

        assertEquals(1.0, dpsWithStaff / dpsWithoutStaff, 1e-9);
    }

    @Test
    public void fiveTicks_withNoSpellSelected_noDiscount() {
        PlayerCombat player = mage99();
        Monster target = plainTarget();

        DpsResult withStaff = DpsCalculator.compute(gearWith(true), player, CombatStyle.MAGIC, target, (Spell) null);
        DpsResult withoutStaff = DpsCalculator.compute(gearWith(false), player, CombatStyle.MAGIC, target, (Spell) null);

        // Both zero-damage (no spell, no powered staff) - the point is the
        // staff must not crash or misbehave with a null spell.
        assertEquals(0, withStaff.maxHit());
        assertEquals(0, withoutStaff.maxHit());
        assertEquals(withoutStaff.dps(), withStaff.dps(), 1e-9);
    }
}
