package com.ospulse.combat;

import org.junit.Test;

import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Coverage for {@link PlayerCombat#assumedPrayer()}: the assumeBestPrayer
 * simulation branch in {@link DpsCalculator} must apply an explicitly picked
 * prayer instead of always hardcoding Piety/Rigour/Augury, and the magic
 * branch must move BOTH magicAccuracyMult() and magicDamagePercent() off
 * Augury — it is the only magic prayer granting a damage percent, so routing
 * only the accuracy multiplier would silently keep Augury's +4% magic damage
 * while simulating a weaker prayer.
 */
public class DpsCalculatorPrayerTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
    private static final double DELTA = 1e-9;

    @Test
    public void assumedPrayer_null_keepsTheHardcodedTopTierPrayer() {
        // Baseline guard: null must change nothing versus the pre-existing behaviour.
        double withNull = magicDps(player -> player.assumeBestPrayer(true));
        double explicitAugury = magicDps(player -> player.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.AUGURY));

        assertEquals(explicitAugury, withNull, DELTA);
    }

    @Test
    public void assumedPrayer_magic_dropsAuguryDamagePercentWhenAWeakerPrayerIsPicked() {
        DpsResult augury = magicResult(p -> p.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.AUGURY));
        DpsResult mysticMight = magicResult(p -> p.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.MYSTIC_MIGHT));

        // Fixture: gear mdmg = 10%, baseSpellMaxHit = 30.
        // Augury: +4% magic damage -> primary = floor(30 * 1.14) = 34.
        // Mystic Might: +2% magic damage -> primary = floor(30 * 1.12) = 33.
        // If only magicAccuracyMult() were routed and magicDamagePercent() stayed
        // hardcoded to Augury's 4%, Mystic Might's maxHit would also come out 34 -
        // this exact-value assertion is the one that catches that specific bug;
        // a plain "mysticMight < augury" on dps() alone is NOT enough, because
        // Mystic Might's weaker accuracy multiplier (1.15x vs 1.25x) already makes
        // dps() lower even when the damage percent is wrongly left at Augury's.
        assertEquals(34, augury.maxHit());
        assertEquals(33, mysticMight.maxHit());
        assertTrue("Mystic Might must be strictly worse than Augury", mysticMight.dps() < augury.dps());
        assertNotEquals(augury.dps(), mysticMight.dps(), DELTA);
    }

    @Test
    public void assumedPrayer_melee_appliesThePickedPrayersMultipliers() {
        double piety = meleeDps(p -> p.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.PIETY));
        double chivalry = meleeDps(p -> p.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.CHIVALRY));
        double burst = meleeDps(p -> p.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.BURST_OF_STRENGTH));

        assertTrue(chivalry < piety);
        assertTrue(burst < chivalry);
    }

    @Test
    public void assumedPrayer_ranged_appliesThePickedPrayersMultipliers() {
        double rigour = rangedDps(p -> p.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.RIGOUR));
        double eagleEye = rangedDps(p -> p.assumeBestPrayer(true).assumedPrayer(OffensivePrayer.EAGLE_EYE));

        assertTrue(eagleEye < rigour);
    }

    @Test
    public void assumedPrayer_isIgnoredWhenAssumeBestPrayerIsOff() {
        // Toggle off means "use what I actually have on" - a simulation pick must
        // not leak into that path.
        double noPrayers = magicDps(p -> p.assumeBestPrayer(false));
        double withPick = magicDps(p -> p.assumeBestPrayer(false).assumedPrayer(OffensivePrayer.AUGURY));

        assertEquals(noPrayers, withPick, DELTA);
    }

    // ---- style-specific fixtures: each must genuinely exercise its style, or the
    // comparisons above collapse to equal values and prove nothing. ----

    private static double magicDps(UnaryOperator<PlayerCombat.Builder> playerConfig) {
        return magicResult(playerConfig).dps();
    }

    private static DpsResult magicResult(UnaryOperator<PlayerCombat.Builder> playerConfig) {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0,
                        0, 0, 0, 0, 0,
                        0, 0, 10.0, 0) // mdmg = 10%
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat.Builder builder = PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.ACCURATE);
        PlayerCombat player = playerConfig.apply(builder).build();

        Monster target = Monster.builder()
                .name("Magic prayer-pick target")
                .magicLevel(60)
                .defenceBonuses(0, 0, 0, 30, 0)
                .hitpoints(100)
                .build();

        return DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, 30);
    }

    private static double meleeDps(UnaryOperator<PlayerCombat.Builder> playerConfig) {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 100, 0, 0, 0,
                        0, 0, 0, 0, 0,
                        108, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();

        PlayerCombat.Builder builder = PlayerCombat.builder()
                .attack(99, 99)
                .strength(99, 99)
                .stance(Stance.AGGRESSIVE);
        PlayerCombat player = playerConfig.apply(builder).build();

        Monster target = Monster.builder()
                .name("Melee prayer-pick target")
                .defenceLevel(70)
                .defenceBonuses(0, 60, 0, 0, 0)
                .hitpoints(100)
                .build();

        return DpsCalculator.compute(gear, player, CombatStyle.SLASH, target, 0).dps();
    }

    private static double rangedDps(UnaryOperator<PlayerCombat.Builder> playerConfig) {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 0, 110,
                        0, 0, 0, 0, 0,
                        0, 90, 0.0, 0)
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat.Builder builder = PlayerCombat.builder()
                .ranged(99, 99)
                .stance(Stance.ACCURATE);
        PlayerCombat player = playerConfig.apply(builder).build();

        Monster target = Monster.builder()
                .name("Ranged prayer-pick target")
                .defenceLevel(50)
                .defenceBonuses(0, 0, 0, 0, 40)
                .hitpoints(100)
                .build();

        return DpsCalculator.compute(gear, player, CombatStyle.RANGED, target, 0).dps();
    }
}
