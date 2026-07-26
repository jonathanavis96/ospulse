package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * {@link TargetDamageRule}'s two "applies when" predicates, in isolation
 * from any curated data, plus one regression proving the {@link
 * DpsCalculator} hook is a no-op for a monster with no {@link
 * MonsterCombatRequirement} at all.
 */
public class TargetDamageRuleTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    private static final int CORPBANE_SPEAR = 4158; // Leaf-bladed spear — a Corp Beast exempt weapon
    private static final int WHIP = 4151;           // an ordinary, non-exempt stab-capable weapon

    /** Exemption applies on any penalised style — the pre-{@code exemptStyles} behaviour. */
    private static MonsterCombatRequirement corpBeastPenalty(Set<CombatStyle> penalisedStyles) {
        return corpBeastPenalty(penalisedStyles, EnumSet.noneOf(CombatStyle.class));
    }

    private static MonsterCombatRequirement corpBeastPenalty(Set<CombatStyle> penalisedStyles,
                                                             Set<CombatStyle> exemptStyles) {
        return MonsterCombatRequirement.damagePenalty(
                new HashSet<>(Arrays.asList(CORPBANE_SPEAR)), 0.5, penalisedStyles, exemptStyles,
                "Corp halves melee and ranged; a corpbane weapon on stab deals full damage.");
    }

    // ---- damageMultiplierFor ------------------------------------------------------------

    @Test
    public void penaltyApplies_toANonAllowedWeapon_inAPenalisedStyle() {
        MonsterCombatRequirement req = corpBeastPenalty(EnumSet.of(CombatStyle.STAB));
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.STAB), 0.0);
    }

    @Test
    public void penaltyDoesNotApply_toAnAllowedCorpbaneWeapon() {
        MonsterCombatRequirement req = corpBeastPenalty(EnumSet.of(CombatStyle.STAB));
        assertEquals(1.0, TargetDamageRule.damageMultiplierFor(req, CORPBANE_SPEAR, CombatStyle.STAB), 0.0);
    }

    @Test
    public void penaltyDoesNotApply_toANonPenalisedStyle() {
        MonsterCombatRequirement req = corpBeastPenalty(EnumSet.of(CombatStyle.STAB));
        assertEquals(1.0, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.SLASH), 0.0);
    }

    @Test
    public void emptyPenalisedStyles_meansEveryStyleIsPenalised() {
        MonsterCombatRequirement req = corpBeastPenalty(Collections.emptySet());
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.SLASH), 0.0);
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.MAGIC), 0.0);
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.RANGED), 0.0);
    }

    @Test
    public void nullRequirement_returnsNeutralMultiplier() {
        assertEquals(1.0, TargetDamageRule.damageMultiplierFor(null, WHIP, CombatStyle.STAB), 0.0);
    }

    @Test
    public void weaponGateRequirement_returnsNeutralMultiplier_typesDoNotBleed() {
        MonsterCombatRequirement gate = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(), Collections.emptySet(), EnumSet.of(CombatStyle.MAGIC), "gate note");
        assertEquals(1.0, TargetDamageRule.damageMultiplierFor(gate, WHIP, CombatStyle.STAB), 0.0);
    }

    // ---- maxHitCapFor --------------------------------------------------------------------

    private static EquipmentStats gearWithAttackBonuses(int astab, int aslash, int acrush) {
        return EquipmentStats.builder()
                .add(astab, aslash, acrush, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .build();
    }

    @Test
    public void cap_returnsCrushHighestValue_whenCrushIsTheLoadoutsHighestAttackBonus() {
        MonsterCombatRequirement req = MonsterCombatRequirement.damageCap(4, 9, "cap note");
        EquipmentStats crushHighest = gearWithAttackBonuses(10, 10, 50);
        assertEquals(9, TargetDamageRule.maxHitCapFor(req, crushHighest));
    }

    @Test
    public void cap_returnsBaseValue_whenCrushIsNotTheLoadoutsHighestAttackBonus() {
        MonsterCombatRequirement req = MonsterCombatRequirement.damageCap(4, 9, "cap note");
        EquipmentStats stabHighest = gearWithAttackBonuses(50, 10, 10);
        assertEquals(4, TargetDamageRule.maxHitCapFor(req, stabHighest));
    }

    @Test
    public void nullRequirement_returnsNoCap() {
        assertEquals(-1, TargetDamageRule.maxHitCapFor(null, gearWithAttackBonuses(50, 10, 10)));
    }

    @Test
    public void weaponGateRequirement_returnsNoCap_typesDoNotBleed() {
        MonsterCombatRequirement gate = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(), Collections.emptySet(), EnumSet.of(CombatStyle.MAGIC), "gate note");
        assertEquals(-1, TargetDamageRule.maxHitCapFor(gate, gearWithAttackBonuses(50, 10, 10)));
    }

    // ---- A penalty/cap must never gate ---------------------------------------------------

    @Test
    public void damagePenalty_neverGatesAnyWeaponOrStyle() {
        MonsterCombatRequirement req = corpBeastPenalty(EnumSet.of(CombatStyle.STAB));
        assertTrue(req.permits(WHIP, CombatStyle.STAB, 0));
        assertTrue(req.permitsWeapon(WHIP, CombatStyle.STAB));
        assertTrue(req.permitsWeapon(WHIP, CombatStyle.STAB, true));
        assertTrue(req.permitsAmmo(0, CombatStyle.RANGED));
    }

    @Test
    public void damageCap_neverGatesAnyWeaponOrStyle() {
        MonsterCombatRequirement req = MonsterCombatRequirement.damageCap(4, 9, "cap note");
        assertTrue(req.permits(WHIP, CombatStyle.MAGIC, 0));
        assertTrue(req.permitsWeapon(WHIP, CombatStyle.MAGIC));
        assertTrue(req.permitsWeapon(WHIP, CombatStyle.MAGIC, true));
        assertTrue(req.permitsAmmo(0, CombatStyle.RANGED));
    }

    // ---- Regression: an unaffected monster's DPS is byte-identical -----------------------

    /**
     * "Unaffected Test Monster" resolves to no curated {@link
     * MonsterCombatRequirement} at all (it is not in {@code
     * monster_combat_requirements.json} under any name or base name) — the
     * overwhelming majority case. The expected values below were captured by
     * running this exact setup BEFORE the {@link DpsCalculator} hook existed;
     * if any of them ever changes, the hook is firing for a monster it must
     * not touch.
     */
    private static EquipmentStats regressionGear() {
        return EquipmentStats.builder()
                .add(80, 60, 40, 30, 70,
                     20, 20, 20, 20, 20,
                     64, 60, 10.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    private static PlayerCombat regressionPlayer() {
        return PlayerCombat.builder()
                .attack(90, 90).strength(90, 90).defence(70, 70).ranged(80, 80).magic(75, 75)
                .prayer(70, 70).hitpoints(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    private static Monster regressionMonster() {
        return Monster.builder()
                .name("Unaffected Test Monster")
                .hitpoints(200)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(100)
                .build();
    }

    @Test
    public void unaffectedMonster_meleeDpsIsUnchanged() {
        DpsResult r = DpsCalculator.compute(regressionGear(), regressionPlayer(), CombatStyle.STAB, regressionMonster(), 20);
        assertEquals(20, r.maxHit());
        assertEquals(0.5596967335081131, r.accuracy(), 1e-9);
        assertEquals(2.343174816869283, r.dps(), 1e-9);
    }

    @Test
    public void unaffectedMonster_rangedDpsIsUnchanged() {
        DpsResult r = DpsCalculator.compute(regressionGear(), regressionPlayer(), CombatStyle.RANGED, regressionMonster(), 20);
        assertEquals(17, r.maxHit());
        assertEquals(0.47445079262895307, r.accuracy(), 1e-9);
        assertEquals(1.6913292144643233, r.dps(), 1e-9);
    }

    @Test
    public void unaffectedMonster_magicDpsIsUnchanged() {
        DpsResult r = DpsCalculator.compute(regressionGear(), regressionPlayer(), CombatStyle.MAGIC, regressionMonster(), 20);
        assertEquals(22, r.maxHit());
        assertEquals(0.31769534079021483, r.accuracy(), 1e-9);
        assertEquals(1.461858995665119, r.dps(), 1e-9);
    }

    // ---- style-sensitive exemption -------------------------------------------------------
    // Corp's rule is "50% reduction against any weapon that is not a corpbane weapon ON STAB
    // attack style". Exempting purely on item id overstates a corpbane weapon swung on slash,
    // and penalising only STAB lets slash/crush/ranged through at full damage.

    /** The real dataset shape: every melee style plus ranged is penalised, magic is not. */
    private static MonsterCombatRequirement realCorpBeastRule() {
        return corpBeastPenalty(
            EnumSet.of(CombatStyle.STAB, CombatStyle.SLASH, CombatStyle.CRUSH, CombatStyle.RANGED),
            EnumSet.of(CombatStyle.STAB));
    }

    @Test
    public void corpbaneSpearIsExemptOnStab() {
        assertEquals(1.0,
            TargetDamageRule.damageMultiplierFor(realCorpBeastRule(), CORPBANE_SPEAR, CombatStyle.STAB), 0.0);
    }

    @Test
    public void theSameCorpbaneSpearIsStillHalvedOnSlash() {
        assertEquals("a spear only earns the exemption on the stab style", 0.5,
            TargetDamageRule.damageMultiplierFor(realCorpBeastRule(), CORPBANE_SPEAR, CombatStyle.SLASH), 0.0);
    }

    @Test
    public void nonStabMeleeAndRangedAreHalved() {
        MonsterCombatRequirement req = realCorpBeastRule();
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.SLASH), 0.0);
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.CRUSH), 0.0);
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.RANGED), 0.0);
    }

    @Test
    public void magicIsNeverReduced() {
        assertEquals("magic deals full damage at Corp — it is only inaccurate", 1.0,
            TargetDamageRule.damageMultiplierFor(realCorpBeastRule(), WHIP, CombatStyle.MAGIC), 0.0);
    }

    /** An empty exemptStyles keeps the exemption style-agnostic, so older entries are unaffected. */
    @Test
    public void emptyExemptStylesExemptsOnAnyPenalisedStyle() {
        MonsterCombatRequirement req = corpBeastPenalty(
            EnumSet.of(CombatStyle.STAB, CombatStyle.SLASH), EnumSet.noneOf(CombatStyle.class));
        assertEquals(1.0, TargetDamageRule.damageMultiplierFor(req, CORPBANE_SPEAR, CombatStyle.STAB), 0.0);
        assertEquals(1.0, TargetDamageRule.damageMultiplierFor(req, CORPBANE_SPEAR, CombatStyle.SLASH), 0.0);
    }

    /** The shipped dataset must match the wiki rule, not just the model. */
    @Test
    public void shippedCorpBeastEntryPenalisesEverythingButMagic() {
        MonsterCombatRequirement req = MonsterCombatRequirementRepository.getInstance()
            .forMonster("Corporeal Beast").orElseThrow(AssertionError::new);
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.SLASH), 0.0);
        assertEquals(0.5, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.RANGED), 0.0);
        assertEquals(1.0, TargetDamageRule.damageMultiplierFor(req, WHIP, CombatStyle.MAGIC), 0.0);
    }
}
