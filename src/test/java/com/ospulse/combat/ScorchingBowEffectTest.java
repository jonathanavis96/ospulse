package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Scorching bow — the RANGED demonbane weapon: +30% accuracy AND damage vs
 * demons (wiki, Scorching bow page). Stacking rules per the wiki DPS
 * calculator (weirdgloop {@code PlayerVsNPCCalc.ts}, our GearScape-parity
 * oracle):
 * <ul>
 *   <li><b>Max hit:</b> when the on-task IMBUED slayer helm/black mask wins
 *       the target-bonus slot, the bow's +30% folds ADDITIVELY into the
 *       helm's 23/20 step — one single floor of (23+6)/20 = 29/20 — not two
 *       sequential floors. (Wiki: "applied additively ... total damage boost
 *       of 45%".)</li>
 *   <li><b>Accuracy:</b> always its own separate multiplicative floor step
 *       (x13/10) applied after the salve/slayer slot.</li>
 *   <li>With Salve (vs an undead demon) there is no fold: salve floors
 *       first, then the bow's x13/10 floors on top.</li>
 * </ul>
 *
 * <p>The worked numbers use a base max hit of 40 (99 Ranged, ACCURATE, +168
 * rstr: effStr 110, floor((110*232+320)/640) = 40), chosen because 40 is a
 * value where additive-fold and sequential floors genuinely differ:
 * floor(40*29/20) = 58 but floor(floor(40*23/20)*13/10) = floor(46*1.3) = 59.
 */
public class ScorchingBowEffectTest {

    /** +100 arange, +168 rstr, speed 5; demonbane/slayer/salve set by the caller. */
    private static EquipmentStats.Builder gear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 168, 0.0, 0)
                .weaponSpeedTicks(5);
    }

    private static PlayerCombat player(boolean onTask) {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.ACCURATE)
                .onSlayerTask(onTask)
                .build();
    }

    private static Monster monster(Set<MonsterAttribute> attributes) {
        return Monster.builder()
                .name("Test")
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .attributes(attributes)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear, PlayerCombat player, Monster target) {
        return DpsCalculator.compute(gear, player, CombatStyle.RANGED, target, 0);
    }

    @Test
    public void scorchingBow_vsDemon_thirtyPercentBothRolls() {
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        DpsResult base = compute(gear().build(), player(false), demon);
        DpsResult bow = compute(gear().demonbaneWeapon(DemonbaneWeapon.SCORCHING_BOW).build(), player(false), demon);

        assertEquals(40, base.maxHit());
        // floor(40 * 13/10) = 52
        assertEquals(52, bow.maxHit());
        assertTrue("accuracy must improve vs a demon", bow.accuracy() > base.accuracy());
        // accuracy roll: effAtt 110, roll = 110*(100+64) = 18040; floor(18040*13/10) = 23452.
        double expectedAccuracy = CombatMath.hitChance(23452, CombatMath.npcDefenceRoll(100, 50));
        assertEquals(expectedAccuracy, bow.accuracy(), 1e-12);
    }

    @Test
    public void scorchingBow_vsNonDemon_hasNoEffect() {
        Monster notDemon = monster(EnumSet.noneOf(MonsterAttribute.class));
        DpsResult base = compute(gear().build(), player(false), notDemon);
        DpsResult bow = compute(gear().demonbaneWeapon(DemonbaneWeapon.SCORCHING_BOW).build(), player(false), notDemon);

        assertEquals(base.maxHit(), bow.maxHit());
        assertEquals(base.dps(), bow.dps(), 1e-9);
    }

    @Test
    public void scorchingBow_maxHitFoldsAdditivelyIntoImbuedSlayerHelm() {
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        DpsResult result = compute(
                gear().demonbaneWeapon(DemonbaneWeapon.SCORCHING_BOW).slayerHeadgear(SlayerHeadgear.IMBUED).build(),
                player(true), demon);

        // ADDITIVE fold: floor(40 * (23+6)/20) = 58 — NOT the sequential
        // floor(floor(40*23/20) * 13/10) = 59.
        assertEquals(58, result.maxHit());
        // Accuracy stays sequential: floor(18040*23/20) = 20746, floor(20746*13/10) = 26969.
        double expectedAccuracy = CombatMath.hitChance(26969, CombatMath.npcDefenceRoll(100, 50));
        assertEquals(expectedAccuracy, result.accuracy(), 1e-12);
    }

    @Test
    public void scorchingBow_nonImbuedHelm_noRangedSlotBonus_soNoFold() {
        Monster demon = monster(EnumSet.of(MonsterAttribute.DEMON));
        DpsResult standardHelm = compute(
                gear().demonbaneWeapon(DemonbaneWeapon.SCORCHING_BOW).slayerHeadgear(SlayerHeadgear.STANDARD).build(),
                player(true), demon);
        DpsResult noHelm = compute(
                gear().demonbaneWeapon(DemonbaneWeapon.SCORCHING_BOW).build(),
                player(true), demon);

        // A non-imbued mask grants no ranged bonus at all — identical to no helm.
        assertEquals(noHelm.maxHit(), standardHelm.maxHit());
        assertEquals(noHelm.accuracy(), standardHelm.accuracy(), 1e-12);
    }

    @Test
    public void scorchingBow_withSalve_noFold_sequentialSteps() {
        // Undead demon corner: salve(i) wins the slot over the imbued helm;
        // the bow then applies as its own separate step on both rolls.
        Monster undeadDemon = monster(EnumSet.of(MonsterAttribute.DEMON, MonsterAttribute.UNDEAD));
        DpsResult result = compute(
                gear().demonbaneWeapon(DemonbaneWeapon.SCORCHING_BOW)
                        .salveType(SalveType.SALVE_I)
                        .slayerHeadgear(SlayerHeadgear.IMBUED)
                        .build(),
                player(true), undeadDemon);

        // floor(40 * 7/6) = 46, then floor(46 * 13/10) = 59.
        assertEquals(59, result.maxHit());
    }

    @Test
    public void scorchingBow_isRangedOnly_neverFiresForMelee() {
        // Sanity on the style gate itself.
        assertTrue(DemonbaneWeapon.SCORCHING_BOW.appliesTo(CombatStyle.RANGED));
        assertTrue(!DemonbaneWeapon.SCORCHING_BOW.appliesTo(CombatStyle.STAB));
        assertTrue(!DemonbaneWeapon.SCORCHING_BOW.appliesTo(CombatStyle.MAGIC));
        assertTrue(!DemonbaneWeapon.EMBERLIGHT.appliesTo(CombatStyle.RANGED));
        assertTrue(DemonbaneWeapon.EMBERLIGHT.appliesTo(CombatStyle.SLASH));
    }
}
