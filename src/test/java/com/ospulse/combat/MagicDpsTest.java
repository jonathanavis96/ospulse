package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Magic end-to-end sanity check (not a wiki-narrated worked example - the
 * task's mandatory 3 worked examples are melee/ranged; this exercises the
 * Tier-A magic path: Augury's accuracy multiplier + magic-damage%, gear
 * mdmg%, and the "baseSpellMaxHit scaled by magic-damage%" simplification).
 * Expected numbers independently derived by hand-applying the documented
 * formulas (DPS/Magic, Maximum magic hit) with a Python script.
 */
public class MagicDpsTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
    private static final double DELTA = 1e-9;

    @Test
    public void endToEnd_augury_gearMdmg_noTaskBonus() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, // amagic
                        0, 0, 0, 0, 0,
                        0, 0, 10.0, 0) // mdmg = 10%
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.ACCURATE)
                .activePrayers(EnumSet.of(OffensivePrayer.AUGURY))
                .build();

        Monster target = Monster.builder()
                .name("Magic target")
                .magicLevel(60)
                .defenceBonuses(0, 0, 0, 30, 0) // dmagic
                .hitpoints(100)
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, 30);

        // primary = floor(30 * (1 + (10 + 4)/100)) = floor(34.2) = 34; no on-task slayer bonus.
        assertEquals(34, result.maxHit());
        assertEquals(0.8534844857955829, result.accuracy(), DELTA);
        assertEquals(4.844540509849213, result.dps(), DELTA);
    }

    @Test
    public void slayerHelmImbued_onTask_addsFifteenPercentPreHitRoll() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .slayerHeadgear(SlayerHeadgear.IMBUED)
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .magic(99, 99)
                .onSlayerTask(true)
                .build();

        Monster target = Monster.builder()
                .name("Task target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, 20);

        // primary = floor(20 * 1.0) = 20 (no mdmg/prayer). Pre-hit-roll: floor(20 * 23/20) = floor(23.0) = 23.
        assertEquals(23, result.maxHit());
    }

    @Test
    public void slayerBonus_doesNotStackWithSalveVsUndead() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .slayerHeadgear(SlayerHeadgear.IMBUED)
                .salveType(SalveType.SALVE_EI)
                .weaponSpeedTicks(5)
                .build();

        PlayerCombat player = PlayerCombat.builder()
                .magic(99, 99)
                .onSlayerTask(true)
                .build();

        Monster undeadTarget = Monster.builder()
                .name("Undead task target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .attributes(EnumSet.of(MonsterAttribute.UNDEAD))
                .build();

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, undeadTarget, 20);

        // Salve (ei) additive +20% wins the "primary damage" stage; the slayer +15%
        // pre-hit-roll multiplicative stage must NOT also apply (mutual exclusion).
        // primary = floor(20 * 1.20) = 24; pre-hit-roll unchanged since slayer bonus is suppressed.
        assertEquals(24, result.maxHit());
    }

    // ---- QA fix 1: the potion-toggle right-click swap must actually feed the
    // calculator a different Magic-level boost per variant (GearSection wires
    // PlayerCombat.magicPotionVariant() through to here) — proven end-to-end via
    // a powered staff, whose built-in max hit scales directly with boosted Magic. ----

    @Test
    public void magicPotionVariant_changesBoostedMagicLevel_viaPoweredStaffMaxHit() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .poweredStaff(PoweredStaff.SANGUINESTI_STAFF) // maxHitAt = floor(magic/3) - 1
                .weaponSpeedTicks(4)
                .build();

        Monster target = Monster.builder()
                .name("Potion variant target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .build();

        int baseMagic = 80;

        int imbuedHeartMaxHit = maxHitWithVariant(gear, target, baseMagic, CombatIcons.BoostPotion.IMBUED_HEART);
        int saturatedHeartMaxHit = maxHitWithVariant(gear, target, baseMagic, CombatIcons.BoostPotion.SATURATED_HEART);
        int ancientBrewMaxHit = maxHitWithVariant(gear, target, baseMagic, CombatIcons.BoostPotion.ANCIENT_BREW);

        // Saturated heart (4 + 10%) boosts Magic higher than Imbued heart (1 + 10%),
        // which in turn boosts higher than Ancient brew (2 + 5%) at this level —
        // matching the OSRS Wiki formulas (PotionBoosts.*BoostedLevel).
        assertEquals(80 + 8 + 4, PotionBoosts.saturatedHeartBoostedLevel(baseMagic));
        assertEquals(80 + 8 + 1, PotionBoosts.imbuedHeartBoostedLevel(baseMagic));
        assertEquals(80 + 4 + 2, PotionBoosts.ancientBrewBoostedLevel(baseMagic));
        assertTrue("saturated heart must out-boost imbued heart", saturatedHeartMaxHit >= imbuedHeartMaxHit);
        assertTrue("imbued heart must out-boost ancient brew here", imbuedHeartMaxHit >= ancientBrewMaxHit);
        assertNotEquals("the three variants must not collapse to one identical result",
                saturatedHeartMaxHit, ancientBrewMaxHit);
    }

    @Test
    public void magicPotionVariant_null_defaultsToImbuedHeart() {
        EquipmentStats gear = EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .poweredStaff(PoweredStaff.SANGUINESTI_STAFF)
                .weaponSpeedTicks(4)
                .build();
        Monster target = Monster.builder()
                .name("Default variant target")
                .magicLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .hitpoints(100)
                .build();

        int explicitImbued = maxHitWithVariant(gear, target, 80, CombatIcons.BoostPotion.IMBUED_HEART);
        int nullVariant = maxHitWithVariant(gear, target, 80, null);

        assertEquals(explicitImbued, nullVariant);
    }

    private static int maxHitWithVariant(EquipmentStats gear, Monster target, int baseMagic,
                                          CombatIcons.BoostPotion variant) {
        PlayerCombat player = PlayerCombat.builder()
                .magic(baseMagic, baseMagic)
                .assumeBestPotion(true)
                .magicPotionVariant(variant)
                .build();
        // The Spell-overload is required here (not the legacy int-maxHit
        // overload) so the worn powered staff's max-hit-from-boosted-Magic path
        // in computeMagic actually engages — see DpsCalculator.compute(..., Spell).
        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.MAGIC, target, (Spell) null);
        return result.maxHit();
    }
}
