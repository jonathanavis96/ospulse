package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tier-B effect tests: Dragon Hunter lance/crossbow, Twisted bow scaling, the
 * demonbane accuracy/damage split, spell-based magic and powered staves.
 * Expected values hand-derived from the documented wiki formulas.
 */
public class TierBEffectsTest {
    private static final double DELTA = 1e-9;

    private static EquipmentStats.Builder plainMeleeGear() {
        // +100 astab, +80 str, speed 4.
        return EquipmentStats.builder()
                .add(100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static EquipmentStats.Builder plainRangedGear() {
        // +100 arange, +80 rstr, speed 4 (crossbow-ish).
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 80, 0.0, 0)
                .weaponSpeedTicks(4);
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

    // ---- Dragon Hunter ------------------------------------------------------------------

    @Test
    public void dragonHunterLance_vsDragon_multipliesBothRolls() {
        Monster dragon = monster(200).attributes(EnumSet.of(MonsterAttribute.DRAGON)).build();
        Monster notDragon = monster(200).build();

        EquipmentStats plain = plainMeleeGear().build();
        EquipmentStats lance = plainMeleeGear().dragonHunterWeapon(DragonHunterWeapon.LANCE).build();

        DpsResult base = DpsCalculator.compute(plain, player99(), CombatStyle.STAB, notDragon, 0);
        DpsResult vsDragon = DpsCalculator.compute(lance, player99(), CombatStyle.STAB, dragon, 0);
        DpsResult offTarget = DpsCalculator.compute(lance, player99(), CombatStyle.STAB, notDragon, 0);

        // effStr = floor(99+3... ACCURATE gives att bonus only; str style bonus 0) = 99+0+8 = 107
        // base maxHit = floor((107*(80+64)+320)/640) = floor((15408+320)/640) = floor(15728/640) = 24
        assertEquals(24, base.maxHit());
        // lance vs dragon: floor(24 * 6/5) = 28
        assertEquals(28, vsDragon.maxHit());
        // and the passive must NOT fire off-dragon.
        assertEquals(24, offTarget.maxHit());
        // accuracy strictly better vs dragon than the same gear off-dragon.
        assertTrue(vsDragon.accuracy() > offTarget.accuracy());
    }

    @Test
    public void dragonHunterLance_stacksWithSlayerSlot() {
        Monster dragonTask = monster(200).attributes(EnumSet.of(MonsterAttribute.DRAGON)).build();
        EquipmentStats lanceAndHelm = plainMeleeGear()
                .dragonHunterWeapon(DragonHunterWeapon.LANCE)
                .slayerHeadgear(SlayerHeadgear.STANDARD)
                .build();
        PlayerCombat onTask = PlayerCombat.builder()
                .attack(99, 99).strength(99, 99)
                .stance(Stance.ACCURATE)
                .onSlayerTask(true)
                .build();

        DpsResult result = DpsCalculator.compute(lanceAndHelm, onTask, CombatStyle.STAB, dragonTask, 0);

        // slot: slayer 7/6 -> floor(24 * 7/6) = 28; then lance step: floor(28 * 6/5) = 33.
        assertEquals(33, result.maxHit());
    }

    @Test
    public void dragonHunterCrossbow_asymmetricPercentages() {
        Monster dragon = monster(200).attributes(EnumSet.of(MonsterAttribute.DRAGON)).build();
        EquipmentStats dhcb = plainRangedGear().dragonHunterWeapon(DragonHunterWeapon.CROSSBOW).build();
        EquipmentStats plain = plainRangedGear().build();

        DpsResult base = DpsCalculator.compute(plain, player99(), CombatStyle.RANGED, dragon, 0);
        DpsResult withDhcb = DpsCalculator.compute(dhcb, player99(), CombatStyle.RANGED, dragon, 0);

        // ranged ACCURATE adds +3 to effective strength too (unlike melee): effStr = 99+3+8 = 110,
        // base maxHit = floor((110*144+320)/640) = 25; +25% dmg -> floor(25 * 5/4) = 31.
        assertEquals(25, base.maxHit());
        assertEquals(31, withDhcb.maxHit());
    }

    // ---- Twisted bow ----------------------------------------------------------------------

    @Test
    public void twistedBowPercents_matchPublishedCheckpoints() {
        // Magic 250 (cap): t=75 -> acc 141 clamped to 140; dmg 250+7-42 = 215.
        assertEquals(140, CombatMath.twistedBowAccuracyPercent(250));
        assertEquals(215, CombatMath.twistedBowDamagePercent(250));
        // Above the cap clamps to the 250 values.
        assertEquals(140, CombatMath.twistedBowAccuracyPercent(400));
        assertEquals(215, CombatMath.twistedBowDamagePercent(400));
        // Magic 85: t=25 -> acc 140+2-56 = 86; dmg 250+2-132 = 120.
        assertEquals(86, CombatMath.twistedBowAccuracyPercent(85));
        assertEquals(120, CombatMath.twistedBowDamagePercent(85));
        // Magic 1: heavily penalised but never negative.
        assertTrue(CombatMath.twistedBowAccuracyPercent(1) >= 0);
        assertTrue(CombatMath.twistedBowDamagePercent(1) >= 0);
    }

    @Test
    public void twistedBow_scalesWithTargetMagic_throughFullComputePath() {
        EquipmentStats tbow = plainRangedGear().twistedBow(true).build();
        Monster highMagic = monster(300).magicLevel(250).build();
        Monster lowMagic = monster(300).magicLevel(1).build();

        DpsResult vsHigh = DpsCalculator.compute(tbow, player99(), CombatStyle.RANGED, highMagic, 0);
        DpsResult vsLow = DpsCalculator.compute(tbow, player99(), CombatStyle.RANGED, lowMagic, 0);

        // ranged base 25 -> floor(25 * 215/100) = 53 vs a 250-magic target.
        assertEquals(53, vsHigh.maxHit());
        assertTrue(vsHigh.maxHit() > vsLow.maxHit());
    }

    // ---- Demonbane accuracy/damage split ---------------------------------------------------

    @Test
    public void silverlight_boostsDamageOnly() {
        Monster demon = monster(200).attributes(EnumSet.of(MonsterAttribute.DEMON)).build();
        EquipmentStats plain = plainMeleeGear().build();
        EquipmentStats silverlight = plainMeleeGear().demonbaneWeapon(DemonbaneWeapon.SILVERLIGHT).build();

        DpsResult base = DpsCalculator.compute(plain, player99(), CombatStyle.STAB, demon, 0);
        DpsResult withSword = DpsCalculator.compute(silverlight, player99(), CombatStyle.STAB, demon, 0);

        // damage: floor(24 * 8/5) = 38; accuracy untouched (1/1 on the accuracy component).
        assertEquals(38, withSword.maxHit());
        assertEquals(base.accuracy(), withSword.accuracy(), DELTA);
    }

    @Test
    public void emberlight_boostsBothComponents() {
        Monster demon = monster(200).attributes(EnumSet.of(MonsterAttribute.DEMON)).build();
        EquipmentStats plain = plainMeleeGear().build();
        EquipmentStats emberlight = plainMeleeGear().demonbaneWeapon(DemonbaneWeapon.EMBERLIGHT).build();

        DpsResult base = DpsCalculator.compute(plain, player99(), CombatStyle.STAB, demon, 0);
        DpsResult withSword = DpsCalculator.compute(emberlight, player99(), CombatStyle.STAB, demon, 0);

        // floor(24 * 17/10) = 40, and accuracy strictly improves too.
        assertEquals(40, withSword.maxHit());
        assertTrue(withSword.accuracy() > base.accuracy());
    }

    // ---- Spells and powered staves ---------------------------------------------------------

    @Test
    public void spellCast_usesBaseMaxHitAndFiveTickSpeed() {
        // Weapon speed 4 must be ignored for autocast: casts are 5 ticks.
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
        Monster target = monster(100).magicLevel(60).build();

        DpsResult fireSurge = DpsCalculator.compute(gear, player99(), CombatStyle.MAGIC, target, Spell.FIRE_SURGE);
        DpsResult legacySameMaxHitAtWeaponSpeed = DpsCalculator.compute(gear, player99(), CombatStyle.MAGIC, target, 24);

        assertEquals(24, fireSurge.maxHit());
        assertFalse(fireSurge.baseEstimate());
        // Same avg damage, but the spell path is 5t where the legacy path used the 4t weapon speed:
        // dps ratio = 4/5.
        assertEquals(legacySameMaxHitAtWeaponSpeed.dps() * 4.0 / 5.0, fireSurge.dps(), DELTA);
    }

    @Test
    public void spellFacts_spotChecks() {
        assertEquals(30, Spell.ICE_BARRAGE.baseMaxHit());
        assertEquals(26, Spell.ICE_BLITZ.baseMaxHit());
        assertEquals(24, Spell.FIRE_SURGE.baseMaxHit());
        assertEquals(25, Spell.IBAN_BLAST.baseMaxHit());
        assertEquals(15, Spell.CRUMBLE_UNDEAD.baseMaxHit());
        assertEquals(Spell.SpellBook.ANCIENT, Spell.BLOOD_BARRAGE.book());
    }

    @Test
    public void poweredStaves_deriveMaxHitFromBoostedMagicLevel() {
        // Wiki facts at 99 magic: seas 28, swamp 31, sang 32, shadow 34.
        assertEquals(28, PoweredStaff.TRIDENT_OF_THE_SEAS.maxHitAt(99));
        assertEquals(31, PoweredStaff.TRIDENT_OF_THE_SWAMP.maxHitAt(99));
        assertEquals(32, PoweredStaff.SANGUINESTI_STAFF.maxHitAt(99));
        assertEquals(34, PoweredStaff.TUMEKENS_SHADOW.maxHitAt(99));
        // Seas at 75 magic: 20 (the wiki's stated starting point).
        assertEquals(20, PoweredStaff.TRIDENT_OF_THE_SEAS.maxHitAt(75));
    }

    @Test
    public void poweredStaff_overridesSpellAndUsesWeaponSpeed() {
        EquipmentStats trident = EquipmentStats.builder()
                .add(0, 0, 0, 50, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .poweredStaff(PoweredStaff.TRIDENT_OF_THE_SEAS)
                .build();
        Monster target = monster(100).magicLevel(60).build();

        // Even with a spell passed, the worn powered staff wins.
        DpsResult result = DpsCalculator.compute(trident, player99(), CombatStyle.MAGIC, target, Spell.FIRE_SURGE);

        assertEquals(28, result.maxHit());
        assertFalse(result.baseEstimate());
    }

    @Test
    public void tumekensShadow_isFlaggedApproximate() {
        EquipmentStats shadow = EquipmentStats.builder()
                .add(0, 0, 0, 35, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(5)
                .poweredStaff(PoweredStaff.TUMEKENS_SHADOW)
                .build();
        Monster target = monster(100).magicLevel(60).build();

        DpsResult result = DpsCalculator.compute(shadow, player99(), CombatStyle.MAGIC, target, (Spell) null);

        assertEquals(34, result.maxHit());
        assertTrue(result.baseEstimate());
    }

    @Test
    public void noSpellNoPoweredStaff_yieldsZeroDamageNotAGuess() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 50, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(5)
                .build();
        Monster target = monster(100).build();

        DpsResult result = DpsCalculator.compute(gear, player99(), CombatStyle.MAGIC, target, (Spell) null);

        assertEquals(0, result.maxHit());
    }

    // ---- Overkill ---------------------------------------------------------------------------

    @Test
    public void overkill_handWorkedExamples() {
        // maxHit 1 vs 1 hp: the only successful damage is exactly 1 -> no waste.
        assertEquals(0.0, CombatMath.expectedOverkill(1, 1), DELTA);
        // maxHit 2 vs 1 hp: successful damage is 1 w.p. 2/3, 2 w.p. 1/3 -> E[waste] = 1/3.
        assertEquals(1.0 / 3.0, CombatMath.expectedOverkill(2, 1), DELTA);
        // maxHit 2 vs 2 hp: O[1] = 1/3; O[2] = (2/3)*O[1] + (1/3)*0 = 2/9.
        assertEquals(2.0 / 9.0, CombatMath.expectedOverkill(2, 2), DELTA);
        // Degenerate inputs are safe.
        assertEquals(0.0, CombatMath.expectedOverkill(0, 50), DELTA);
        assertEquals(0.0, CombatMath.expectedOverkill(10, 0), DELTA);
    }

    @Test
    public void overkill_isIndependentOfAccuracyAndExposedOnResult() {
        Monster tanky = monster(200).build();
        Monster squishy = monster(200).defenceLevel(1).defenceBonuses(0, 0, 0, 0, 0).magicLevel(1).build();
        EquipmentStats gear = plainMeleeGear().build();

        DpsResult vsTanky = DpsCalculator.compute(gear, player99(), CombatStyle.STAB, tanky, 0);
        DpsResult vsSquishy = DpsCalculator.compute(gear, player99(), CombatStyle.STAB, squishy, 0);

        // Same maxHit + same hp -> identical overkill despite very different accuracy (misses cancel).
        assertEquals(vsTanky.maxHit(), vsSquishy.maxHit());
        assertEquals(vsTanky.overkillPerKill(), vsSquishy.overkillPerKill(), DELTA);
        assertTrue(vsTanky.overkillPerKill() > 0.0);
        // Overkill can never exceed maxHit - 1.
        assertTrue(vsTanky.overkillPerKill() < gearMaxHitUpperBound());
    }

    private static double gearMaxHitUpperBound() {
        return 24.0; // the shared worked example's max hit
    }
}
