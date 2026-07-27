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
        return monster(name, false);
    }

    /**
     * {@code wildernessTarget} must be set explicitly here — production
     * Monsters get it baked in once at {@code MonsterRepository} load time
     * (from the curated {@code WildernessMonsterRepository} set, or as a
     * synthetic Wilderness-variant twin), not re-derived from the name at
     * {@code DpsCalculator} call time, so a hand-built test fixture has to
     * say so itself, the same way production construction does.
     */
    private static Monster monster(String name, boolean wildernessTarget) {
        return Monster.builder()
                .name(name)
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .wildernessTarget(wildernessTarget)
                .build();
    }

    private static final Monster ARTIO = monster("Artio", true);
    private static final Monster NON_WILDERNESS = monster("Test", false);

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

    // ---- Per-weapon multiplier pinning (verified against the OSRS Wiki 2026-07-27; see
    // RevenantWeapon's class javadoc for the citations and Thammaron's sceptre's 2022/2023
    // percentage history). Each assertion is hard-coded independently of RevenantWeapon's own
    // Fraction fields so a future single-value refactor cannot silently re-symmetrise (or
    // mis-symmetrise) these without a test noticing. -----------------------------------------

    /** Fraction has no equals()/hashCode() override (see its own javadoc) - compare exact numerator/denominator instead. */
    private static void assertFractionEquals(long expectedNumerator, long expectedDenominator, Fraction actual) {
        assertEquals(expectedNumerator, actual.numerator);
        assertEquals(expectedDenominator, actual.denominator);
    }

    @Test
    public void crawsBow_pinnedAt_fiftyPercentAccuracy_fiftyPercentDamage() {
        assertFractionEquals(3, 2, RevenantWeapon.CRAWS_BOW.accuracyMult());
        assertFractionEquals(3, 2, RevenantWeapon.CRAWS_BOW.damageMult());
    }

    @Test
    public void viggorasChainmace_pinnedAt_fiftyPercentAccuracy_fiftyPercentDamage() {
        assertFractionEquals(3, 2, RevenantWeapon.VIGGORAS_CHAINMACE.accuracyMult());
        assertFractionEquals(3, 2, RevenantWeapon.VIGGORAS_CHAINMACE.damageMult());
    }

    /**
     * Thammaron's sceptre is CURRENTLY symmetric too (50%/50%, live since the
     * 25 January 2023 Wilderness Boss Rework) — NOT the pre-2023 asymmetric
     * 100%/25% a review finding proposed based on stale data. Pinned as its
     * own independent test (rather than sharing an assertion with the other
     * two) specifically so a future correction attempt has to overwrite THIS
     * value, with its citation, instead of silently flowing through a shared
     * constant.
     */
    @Test
    public void thammaronsSceptre_pinnedAt_fiftyPercentAccuracy_fiftyPercentDamage_currentPostReworkValue() {
        assertFractionEquals(3, 2, RevenantWeapon.THAMMARONS_SCEPTRE.accuracyMult());
        assertFractionEquals(3, 2, RevenantWeapon.THAMMARONS_SCEPTRE.damageMult());
    }

    // ---- P1 finding: King Black Dragon's LAIR is explicitly not the Wilderness (OSRS Wiki's
    // "King Black Dragon Lair" page: "the lair itself is not the Wilderness" / "isn't in the
    // Wilderness" / "not considered the Wilderness"), even though its entrance sits inside
    // level 42 Wilderness - so it must not be treated as a Wilderness target. These go through
    // the REAL bundled MonsterRepository/WildernessMonsterRepository data (not a hand-built
    // fixture), since the bug lives in the curated data, not in DpsCalculator's gating logic. --

    @Test
    public void kingBlackDragon_isNotAWildernessTarget_revenantWeaponsHaveNoEffect() {
        Monster kbd = MonsterRepository.getInstance().byName("King Black Dragon")
                .orElseThrow(() -> new AssertionError("King Black Dragon must resolve against the bundled monster data"));
        assertFalse("King Black Dragon's lair is explicitly not the Wilderness (OSRS Wiki) - "
                + "it must not be flagged as a Wilderness target", kbd.isWildernessTarget());

        DpsResult base = DpsCalculator.compute(rangedGear().build(), player(), CombatStyle.RANGED, kbd, 0);
        DpsResult craws = DpsCalculator.compute(
                rangedGear().revenantWeapon(RevenantWeapon.CRAWS_BOW).build(), player(), CombatStyle.RANGED, kbd, 0);
        assertEquals("Craw's bow must not get the +50% Wilderness bonus against King Black Dragon",
                base.maxHit(), craws.maxHit());
        assertEquals(base.dps(), craws.dps(), 1e-9);
    }

    /**
     * Regression pin so the King Black Dragon fix above cannot over-correct: a genuinely
     * Wilderness-exclusive boss from the same curated data must still get the bonus.
     */
    @Test
    public void callisto_isStillAWildernessTarget_revenantWeaponsStillApply() {
        Monster callisto = MonsterRepository.getInstance().byName("Callisto")
                .orElseThrow(() -> new AssertionError("Callisto must resolve against the bundled monster data"));
        assertTrue("Callisto is a genuine Wilderness boss and must stay a Wilderness target",
                callisto.isWildernessTarget());

        DpsResult base = DpsCalculator.compute(rangedGear().build(), player(), CombatStyle.RANGED, callisto, 0);
        DpsResult craws = DpsCalculator.compute(
                rangedGear().revenantWeapon(RevenantWeapon.CRAWS_BOW).build(), player(), CombatStyle.RANGED, callisto, 0);
        assertEquals((int) Math.floor(base.maxHit() * 1.5), craws.maxHit());
        assertTrue("accuracy must still improve at a genuine Wilderness target", craws.accuracy() > base.accuracy());
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
