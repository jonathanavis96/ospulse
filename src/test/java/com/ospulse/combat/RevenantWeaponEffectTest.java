package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Craw's bow / Viggora's chainmace / Thammaron's sceptre — +50% accuracy
 * AND damage vs any NPC in the Wilderness, while charged with revenant
 * ether (assumed charged — see {@link RevenantWeapon}). Gated on {@link
 * WildernessMonsterRepository}, the one genuinely new curated input this
 * stage adds. Previously unmodelled entirely, making these look mediocre
 * instead of BiS at Wilderness bosses — directly relevant to the reporter's
 * Artio case.
 */
public class RevenantWeaponEffectTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.ACCURATE)
                .build();
    }

    private static Monster monster(String name) {
        return Monster.builder()
                .name(name)
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .build();
    }

    private static final Monster ARTIO = monster("Artio");
    private static final Monster NON_WILDERNESS = monster("Test");

    // ---- Craw's bow (RANGED) --------------------------------------------------------------

    private static EquipmentStats.Builder rangedGear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 150, 0.0, 0)
                .weaponSpeedTicks(5);
    }

    @Test
    public void crawsBow_wildernessTarget_fiftyPercentAccuracyAndDamage() {
        DpsResult base = DpsCalculator.compute(rangedGear().build(), player(), CombatStyle.RANGED, ARTIO, 0);
        DpsResult craws = DpsCalculator.compute(
                rangedGear().revenantWeapon(RevenantWeapon.CRAWS_BOW).build(), player(), CombatStyle.RANGED, ARTIO, 0);

        // floor(base.maxHit * 3/2) - hard-coded 50%, independent of RevenantWeapon's own Fraction field.
        assertEquals((int) Math.floor(base.maxHit() * 1.5), craws.maxHit());
        assertTrue("accuracy must improve at a wilderness target", craws.accuracy() > base.accuracy());
    }

    @Test
    public void crawsBow_nonWildernessTarget_hasNoEffect() {
        DpsResult base = DpsCalculator.compute(rangedGear().build(), player(), CombatStyle.RANGED, NON_WILDERNESS, 0);
        DpsResult craws = DpsCalculator.compute(
                rangedGear().revenantWeapon(RevenantWeapon.CRAWS_BOW).build(), player(), CombatStyle.RANGED, NON_WILDERNESS, 0);
        assertEquals(base.maxHit(), craws.maxHit());
        assertEquals(base.dps(), craws.dps(), 1e-9);
    }

    // ---- Viggora's chainmace (melee, any style) -------------------------------------------

    private static EquipmentStats.Builder meleeGear() {
        return EquipmentStats.builder()
                .add(80, 80, 80, 0, 0, 0, 0, 0, 0, 0, 100, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    @Test
    public void viggorasChainmace_wildernessTarget_fiftyPercentAccuracyAndDamage_crush() {
        DpsResult base = DpsCalculator.compute(meleeGear().build(), player(), CombatStyle.CRUSH, ARTIO, 0);
        DpsResult viggoras = DpsCalculator.compute(
                meleeGear().revenantWeapon(RevenantWeapon.VIGGORAS_CHAINMACE).build(), player(), CombatStyle.CRUSH, ARTIO, 0);
        assertEquals((int) Math.floor(base.maxHit() * 1.5), viggoras.maxHit());
        assertTrue(viggoras.accuracy() > base.accuracy());
    }

    @Test
    public void viggorasChainmace_appliesToAnyMeleeStyle_notJustCrush() {
        DpsResult base = DpsCalculator.compute(meleeGear().build(), player(), CombatStyle.STAB, ARTIO, 0);
        DpsResult viggoras = DpsCalculator.compute(
                meleeGear().revenantWeapon(RevenantWeapon.VIGGORAS_CHAINMACE).build(), player(), CombatStyle.STAB, ARTIO, 0);
        assertEquals((int) Math.floor(base.maxHit() * 1.5), viggoras.maxHit());
    }

    @Test
    public void viggorasChainmace_nonWildernessTarget_hasNoEffect() {
        DpsResult base = DpsCalculator.compute(meleeGear().build(), player(), CombatStyle.CRUSH, NON_WILDERNESS, 0);
        DpsResult viggoras = DpsCalculator.compute(
                meleeGear().revenantWeapon(RevenantWeapon.VIGGORAS_CHAINMACE).build(), player(), CombatStyle.CRUSH, NON_WILDERNESS, 0);
        assertEquals(base.maxHit(), viggoras.maxHit());
        assertEquals(base.dps(), viggoras.dps(), 1e-9);
    }

    // ---- Thammaron's sceptre (MAGIC) ------------------------------------------------------

    private static EquipmentStats.Builder magicGear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 80, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    @Test
    public void thammaronsSceptre_wildernessTarget_fiftyPercentAccuracyAndDamage() {
        int baseSpellMaxHit = 30;
        DpsResult base = DpsCalculator.compute(magicGear().build(), player(), CombatStyle.MAGIC, ARTIO, baseSpellMaxHit);
        DpsResult thammarons = DpsCalculator.compute(
                magicGear().revenantWeapon(RevenantWeapon.THAMMARONS_SCEPTRE).build(), player(), CombatStyle.MAGIC,
                ARTIO, baseSpellMaxHit);
        assertEquals((int) Math.floor(base.maxHit() * 1.5), thammarons.maxHit());
        assertTrue(thammarons.accuracy() > base.accuracy());
    }

    @Test
    public void thammaronsSceptre_nonWildernessTarget_hasNoEffect() {
        int baseSpellMaxHit = 30;
        DpsResult base = DpsCalculator.compute(magicGear().build(), player(), CombatStyle.MAGIC, NON_WILDERNESS, baseSpellMaxHit);
        DpsResult thammarons = DpsCalculator.compute(
                magicGear().revenantWeapon(RevenantWeapon.THAMMARONS_SCEPTRE).build(), player(), CombatStyle.MAGIC,
                NON_WILDERNESS, baseSpellMaxHit);
        assertEquals(base.maxHit(), thammarons.maxHit());
        assertEquals(base.dps(), thammarons.dps(), 1e-9);
    }

    // ---- Style-mismatch gating (unit-level, on the enum itself) ---------------------------

    @Test
    public void appliesTo_isStyleGated() {
        assertTrue(RevenantWeapon.CRAWS_BOW.appliesTo(CombatStyle.RANGED));
        assertFalse(RevenantWeapon.CRAWS_BOW.appliesTo(CombatStyle.MAGIC));
        assertFalse(RevenantWeapon.CRAWS_BOW.appliesTo(CombatStyle.STAB));

        assertTrue(RevenantWeapon.THAMMARONS_SCEPTRE.appliesTo(CombatStyle.MAGIC));
        assertFalse(RevenantWeapon.THAMMARONS_SCEPTRE.appliesTo(CombatStyle.RANGED));

        assertTrue(RevenantWeapon.VIGGORAS_CHAINMACE.appliesTo(CombatStyle.STAB));
        assertTrue(RevenantWeapon.VIGGORAS_CHAINMACE.appliesTo(CombatStyle.SLASH));
        assertTrue(RevenantWeapon.VIGGORAS_CHAINMACE.appliesTo(CombatStyle.CRUSH));
        assertFalse(RevenantWeapon.VIGGORAS_CHAINMACE.appliesTo(CombatStyle.RANGED));

        assertFalse(RevenantWeapon.NONE.appliesTo(CombatStyle.RANGED));
    }

    // ---- Regression: no RevenantWeapon, DPS never depends on target name -----------------

    @Test
    public void unaffectedLoadout_regression_targetIsNeverWildernessSensitiveWithoutARevenantWeapon() {
        EquipmentStats plainRanged = rangedGear().build();
        DpsResult wild = DpsCalculator.compute(plainRanged, player(), CombatStyle.RANGED, ARTIO, 0);
        DpsResult nonWild = DpsCalculator.compute(plainRanged, player(), CombatStyle.RANGED, NON_WILDERNESS, 0);
        assertEquals(wild.maxHit(), nonWild.maxHit());
        assertEquals(wild.dps(), nonWild.dps(), 1e-9);
    }
}
