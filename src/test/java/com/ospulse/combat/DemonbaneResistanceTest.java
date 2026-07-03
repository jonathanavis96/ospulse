package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Per-monster demonbane resistance: {@link Monster#demonbaneResistPercent()}
 * scales down the demonbane weapon's BONUS portion (its multiplier's
 * excess-over-1) by the resist percentage, applied in
 * {@link DpsCalculator}'s demonbane apply step. Duke Sucellus is the
 * documented OSRS example — 30% resistance turns Emberlight's +70% into +49%
 * ("an emberlight would only have 49% increased damage and accuracy against
 * him rather than 70%" — OSRS Wiki).
 *
 * <p>These tests use a SYNTHETIC monster (resist=30) rather than relying on
 * Duke Sucellus's bundled data for the core mechanism, so the mechanism is
 * verified independently of the data file; a separate test below confirms
 * Duke Sucellus's bundled entries do carry resist=30.
 */
public class DemonbaneResistanceTest {

    private static EquipmentStats gear(DemonbaneWeapon demonbane) {
        return EquipmentStats.builder()
                .add(140, 0, 0, 0, 0, 0, 0, 0, 0, 0, 130, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .demonbaneWeapon(demonbane)
                .build();
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).defence(99, 99).ranged(99, 99).magic(99, 99)
                .prayer(99, 99).hitpoints(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    private static Monster demon(int resistPercent) {
        return monsterWith(EnumSet.of(MonsterAttribute.DEMON), resistPercent);
    }

    private static Monster monsterWith(Set<MonsterAttribute> attributes, int resistPercent) {
        return Monster.builder()
                .name("Test")
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 100, 25, 65, 100)
                .magicLevel(220)
                .attributes(attributes)
                .demonbaneResistPercent(resistPercent)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear, Monster target) {
        return DpsCalculator.compute(gear, player(), CombatStyle.STAB, target, 20);
    }

    @Test
    public void defaultResistIsZero_forABuilderWithNoResistSpecified() {
        Monster m = Monster.builder().name("x").attributes(EnumSet.noneOf(MonsterAttribute.class)).build();
        assertEquals(0, m.demonbaneResistPercent());
    }

    @Test
    public void unresistedDemon_getsFullSeventyPercentBonus() {
        DpsResult base = compute(gear(DemonbaneWeapon.NONE), demon(0));
        DpsResult ember = compute(gear(DemonbaneWeapon.EMBERLIGHT), demon(0));

        int expectedMaxHit = (int) new Fraction(17, 10).applyFloor(base.maxHit());
        assertEquals("no resistance: full +70% bonus", expectedMaxHit, ember.maxHit());
    }

    @Test
    public void resistThirtyPercent_reducesEmberlightBonusToFortyNinePercent() {
        // 30% resist scales the +70% (excess 0.7) bonus down to 0.7*0.7=0.49
        // -> 149/100 instead of 17/10, per the documented Duke Sucellus case.
        DpsResult base = compute(gear(DemonbaneWeapon.NONE), demon(30));
        DpsResult ember = compute(gear(DemonbaneWeapon.EMBERLIGHT), demon(30));

        int expectedMaxHit = (int) new Fraction(149, 100).applyFloor(base.maxHit());
        assertEquals("30% resistance: +70% bonus reduced to +49%", expectedMaxHit, ember.maxHit());

        // Sanity: strictly less than the unresisted bonus, strictly more than base.
        DpsResult emberUnresisted = compute(gear(DemonbaneWeapon.EMBERLIGHT), demon(0));
        assertTrue("resisted bonus must be less than the full bonus",
                ember.maxHit() <= emberUnresisted.maxHit());
        assertTrue("resisted bonus must still exceed the unbuffed base",
                ember.maxHit() > base.maxHit());
    }

    @Test
    public void resistOneHundredPercent_negatesTheBonusEntirely() {
        DpsResult base = compute(gear(DemonbaneWeapon.NONE), demon(100));
        DpsResult ember = compute(gear(DemonbaneWeapon.EMBERLIGHT), demon(100));

        assertEquals("100% resistance: bonus fully negated", base.maxHit(), ember.maxHit());
    }

    @Test
    public void resistDoesNotApply_toDamageOnlyWeaponsAccuracySide() {
        // Silverlight has NO accuracy bonus (Fraction.ONE): resistance must not
        // touch an already-1x fraction (resistedDemonbaneFraction short-circuits
        // on isOne()).
        DpsResult base = compute(gear(DemonbaneWeapon.NONE), demon(30));
        DpsResult silver = compute(gear(DemonbaneWeapon.SILVERLIGHT), demon(30));
        assertEquals("Silverlight has no accuracy bonus to resist", base.accuracy(), silver.accuracy(), 1e-9);
    }

    @Test
    public void resistDoesNotApply_whenTargetIsNotADemon() {
        Monster notDemon = monsterWith(EnumSet.noneOf(MonsterAttribute.class), 30);
        DpsResult base = compute(gear(DemonbaneWeapon.NONE), notDemon);
        DpsResult ember = compute(gear(DemonbaneWeapon.EMBERLIGHT), notDemon);
        assertEquals("non-demon: demonbane never applies regardless of resist field", base.maxHit(), ember.maxHit());
    }

    @Test
    public void bundledDukeSucellusEntries_carryThirtyPercentResist() {
        MonsterRepository repo = MonsterRepository.getInstance();
        // Duke Sucellus's fightable "Awake" npc ids in the bundled data.
        int[] dukeIds = {12191, 12195};
        boolean foundAny = false;
        for (int id : dukeIds) {
            Optional<Monster> m = repo.byId(id);
            if (m.isPresent()) {
                foundAny = true;
                assertTrue("Duke Sucellus (" + id + ") must be tagged DEMON", m.get().isDemon());
                assertEquals("Duke Sucellus (" + id + ") demonbane resist", 30, m.get().demonbaneResistPercent());
            }
        }
        assertTrue("expected at least one Duke Sucellus npc id in the bundled data", foundAny);
    }
}
