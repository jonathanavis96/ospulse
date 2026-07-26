package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Colossal blade (item id 27021) — a flat {@code +2 * min(targetSize, 5)}
 * max-hit bonus (up to +10 against a 5x5-or-larger target), per the OSRS
 * Wiki ("MaxHit+[2×min(MonsterSize,5)]"), stacking with the on-task Slayer
 * helm/black mask (per the wiki: "does stack with the bonus granted by
 * Black mask"). A flat additive term, applied LAST (after every
 * multiplicative step), so it is added straight to whatever max hit the
 * loadout would otherwise produce.
 */
public class ColossalBladeEffectTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /** +100 astab/aslash/acrush, +150 str, speed 4; colossalBlade flag set by the caller. */
    private static EquipmentStats.Builder gear() {
        return EquipmentStats.builder()
                .add(100, 100, 100, 0, 0, 0, 0, 0, 0, 0, 150, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    private static Monster monster(int size) {
        return Monster.builder()
                .name("Test")
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .size(size)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear, Monster target) {
        return DpsCalculator.compute(gear, player(), CombatStyle.SLASH, target, 0);
    }

    @Test
    public void size1Target_addsPlusTwo() {
        DpsResult base = compute(gear().build(), monster(1));
        DpsResult blade = compute(gear().colossalBlade(true).build(), monster(1));
        assertEquals(base.maxHit() + 2, blade.maxHit());
    }

    @Test
    public void size3Target_addsPlusSix() {
        DpsResult base = compute(gear().build(), monster(3));
        DpsResult blade = compute(gear().colossalBlade(true).build(), monster(3));
        assertEquals(base.maxHit() + 6, blade.maxHit());
    }

    @Test
    public void size5OrLargerTarget_capsAtPlusTen() {
        DpsResult base5 = compute(gear().build(), monster(5));
        DpsResult blade5 = compute(gear().colossalBlade(true).build(), monster(5));
        assertEquals(base5.maxHit() + 10, blade5.maxHit());

        DpsResult base10 = compute(gear().build(), monster(10));
        DpsResult blade10 = compute(gear().colossalBlade(true).build(), monster(10));
        assertEquals(base10.maxHit() + 10, blade10.maxHit());
    }

    @Test
    public void stacksAdditivelyWithOnTaskSlayerHelm() {
        // Standard (non-imbued) black mask on task: melee slot bonus is 7/6.
        EquipmentStats withHelm = gear().colossalBlade(true).slayerHeadgear(SlayerHeadgear.STANDARD).build();
        PlayerCombat onTask = PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.AGGRESSIVE)
                .onSlayerTask(true)
                .build();
        Monster target = monster(5);
        DpsResult result = DpsCalculator.compute(withHelm, onTask, CombatStyle.SLASH, target, 0);

        // Recompute the multiplicative helm step directly, THEN add the flat +10.
        EquipmentStats noBlade = gear().slayerHeadgear(SlayerHeadgear.STANDARD).build();
        DpsResult helmOnly = DpsCalculator.compute(noBlade, onTask, CombatStyle.SLASH, target, 0);
        assertEquals(helmOnly.maxHit() + 10, result.maxHit());
    }

    @Test
    public void unaffectedLoadout_regression_targetSizeNeverChangesMaxHitWithoutColossalBlade() {
        // A weapon that is NOT the Colossal blade must be byte-identical to
        // before this stage across every target size - the flag gate is the
        // whole story.
        EquipmentStats plain = gear().build();
        DpsResult size1 = compute(plain, monster(1));
        DpsResult size3 = compute(plain, monster(3));
        DpsResult size10 = compute(plain, monster(10));
        assertEquals(size1.maxHit(), size3.maxHit());
        assertEquals(size1.maxHit(), size10.maxHit());
        assertEquals(size1.dps(), size3.dps(), 1e-9);
        assertEquals(size1.dps(), size10.dps(), 1e-9);
    }
}
