package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
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
    private static final int KINGS_BARRAGE = 33251; // the one corpbane weapon that is ranged, not melee
    private static final int ARMADYL_CROSSBOW = 11785; // an ordinary, non-exempt ranged weapon

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
        assertEquals(9, TargetDamageRule.maxHitCapFor(req, crushHighest, CombatStyle.CRUSH, WHIP));
    }

    @Test
    public void cap_returnsBaseValue_whenCrushIsNotTheLoadoutsHighestAttackBonus() {
        MonsterCombatRequirement req = MonsterCombatRequirement.damageCap(4, 9, "cap note");
        EquipmentStats stabHighest = gearWithAttackBonuses(50, 10, 10);
        assertEquals(4, TargetDamageRule.maxHitCapFor(req, stabHighest, CombatStyle.STAB, WHIP));
    }

    @Test
    public void nullRequirement_returnsNoCap() {
        assertEquals(-1, TargetDamageRule.maxHitCapFor(null, gearWithAttackBonuses(50, 10, 10), CombatStyle.STAB, WHIP));
    }

    @Test
    public void weaponGateRequirement_returnsNoCap_typesDoNotBleed() {
        MonsterCombatRequirement gate = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(), Collections.emptySet(), EnumSet.of(CombatStyle.MAGIC), "gate note");
        assertEquals(-1, TargetDamageRule.maxHitCapFor(gate, gearWithAttackBonuses(50, 10, 10), CombatStyle.STAB, WHIP));
    }

    // ---- per-style caps, cap-exempt weapons, and CapMode ----------------------------------

    @Test
    public void perStyleCap_takesPriorityOverTheFlatValue() {
        MonsterCombatRequirement req = MonsterCombatRequirement.damageCap(10, -1,
                Collections.emptySet(),
                enumMapOf(CombatStyle.RANGED, 3, CombatStyle.MAGIC, 3),
                MonsterCombatRequirement.CapMode.REROLL, "Verzik-shaped cap note");
        assertEquals("ranged has its own entry in the per-style map", 3,
                TargetDamageRule.maxHitCapFor(req, gearWithAttackBonuses(10, 10, 10), CombatStyle.RANGED, WHIP));
        assertEquals("magic has its own entry in the per-style map", 3,
                TargetDamageRule.maxHitCapFor(req, gearWithAttackBonuses(10, 10, 10), CombatStyle.MAGIC, WHIP));
        assertEquals("stab has no per-style entry, so it falls back to the flat value", 10,
                TargetDamageRule.maxHitCapFor(req, gearWithAttackBonuses(10, 10, 10), CombatStyle.STAB, WHIP));
    }

    @Test
    public void perStyleCap_takesPriorityOverCrushHighestToo() {
        MonsterCombatRequirement req = MonsterCombatRequirement.damageCap(10, 20,
                Collections.emptySet(),
                enumMapOf(CombatStyle.CRUSH, 10),
                MonsterCombatRequirement.CapMode.REROLL, "note");
        // Crush is the loadout's highest attack bonus, so the OLD logic would answer 20 —
        // but crush has an explicit per-style entry (10), which must win instead.
        assertEquals(10, TargetDamageRule.maxHitCapFor(req, gearWithAttackBonuses(1, 1, 50), CombatStyle.CRUSH, WHIP));
    }

    @Test
    public void weaponInAllowedItemIds_isWhollyExemptFromTheCap_regardlessOfStyle() {
        int dawnbringer = 22516;
        MonsterCombatRequirement req = MonsterCombatRequirement.damageCap(10, -1,
                new HashSet<>(Arrays.asList(dawnbringer)),
                enumMapOf(CombatStyle.RANGED, 3, CombatStyle.MAGIC, 3),
                MonsterCombatRequirement.CapMode.REROLL, "Verzik-shaped cap note, Dawnbringer exempt");
        assertEquals(-1, TargetDamageRule.maxHitCapFor(req, gearWithAttackBonuses(1, 1, 1), CombatStyle.MAGIC, dawnbringer));
        assertEquals("a non-exempt weapon still gets the cap", 3,
                TargetDamageRule.maxHitCapFor(req, gearWithAttackBonuses(1, 1, 1), CombatStyle.MAGIC, WHIP));
    }

    @Test
    public void capModeFor_returnsTheRequirementsMode() {
        MonsterCombatRequirement clamp = MonsterCombatRequirement.damageCap(4, 9, "cap note");
        assertEquals(MonsterCombatRequirement.CapMode.CLAMP, TargetDamageRule.capModeFor(clamp));

        MonsterCombatRequirement reroll = MonsterCombatRequirement.damageCap(10, -1,
                Collections.emptySet(), Collections.emptyMap(),
                MonsterCombatRequirement.CapMode.REROLL, "note");
        assertEquals(MonsterCombatRequirement.CapMode.REROLL, TargetDamageRule.capModeFor(reroll));
    }

    @Test
    public void capModeFor_defaultsToClampForNullOrNonCapRequirements() {
        assertEquals(MonsterCombatRequirement.CapMode.CLAMP, TargetDamageRule.capModeFor(null));
        MonsterCombatRequirement gate = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(), Collections.emptySet(), EnumSet.of(CombatStyle.MAGIC), "gate note");
        assertEquals(MonsterCombatRequirement.CapMode.CLAMP, TargetDamageRule.capModeFor(gate));
    }

    private static java.util.Map<CombatStyle, Integer> enumMapOf(CombatStyle style, int value) {
        java.util.Map<CombatStyle, Integer> map = new java.util.EnumMap<>(CombatStyle.class);
        map.put(style, value);
        return map;
    }

    private static java.util.Map<CombatStyle, Integer> enumMapOf(CombatStyle s1, int v1, CombatStyle s2, int v2) {
        java.util.Map<CombatStyle, Integer> map = new java.util.EnumMap<>(CombatStyle.class);
        map.put(s1, v1);
        map.put(s2, v2);
        return map;
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

    // ---- capped damage distribution ------------------------------------------------------
    // A cap is not a lower max hit. The roll still spans 0..uncapped and everything above
    // the cap lands ON the cap, so that probability mass must be preserved.

    /** With cap == max there is nothing to cap, so it must equal the uncapped formula exactly. */
    @Test
    public void cappedAverageReducesToTheUncappedFormulaWhenCapEqualsMax() {
        for (int max : new int[]{1, 4, 9, 40, 99}) {
            assertEquals("cap == max must be a no-op for max=" + max,
                DamageDistribution.averageDamagePerAttack(1.0, max),
                DamageDistribution.cappedAverageDamagePerAttack(1.0, max, max), 1e-12);
        }
    }

    /** A cap above the max cannot bind. */
    @Test
    public void aCapAboveTheMaxIsANoOp() {
        assertEquals(DamageDistribution.averageDamagePerAttack(1.0, 10),
            DamageDistribution.cappedAverageDamagePerAttack(1.0, 10, 50), 1e-12);
    }

    /**
     * The bug this formula exists to fix: clamping max hit to the cap and reusing the
     * uncapped formula assumes a uniform 0..cap roll and badly understates the result.
     */
    @Test
    public void cappedAverageBeatsNaivelyClampingTheMaxHit() {
        double naive = DamageDistribution.averageDamagePerAttack(1.0, 4);          // ~2.2
        double correct = DamageDistribution.cappedAverageDamagePerAttack(1.0, 40, 4);
        assertTrue("a cap of 4 against an uncapped max of 40 should average close to 4, not 2",
            correct > naive * 1.5);
        assertTrue("but it can never exceed the cap itself", correct <= 4.0);
    }

    /**
     * Closed form: {@code (C(C-1)/2 + (M-C+1)*C + 1) / (M+1)}. With C=4, M=40 that is
     * {@code (6 + 148 + 1) / 41 = 155/41}. The trailing +1 is the "a rolled 0 becomes 1"
     * correction — dropping it is an easy off-by-one, so it is asserted explicitly.
     */
    @Test
    public void cappedAverageMatchesTheClosedForm() {
        assertEquals(155.0 / 41.0, DamageDistribution.cappedAverageDamagePerAttack(1.0, 40, 4), 1e-12);
    }

    /** Hit chance scales the whole thing linearly. */
    @Test
    public void cappedAverageScalesWithHitChance() {
        assertEquals(0.5 * DamageDistribution.cappedAverageDamagePerAttack(1.0, 40, 4),
            DamageDistribution.cappedAverageDamagePerAttack(0.5, 40, 4), 1e-12);
    }

    // ---- capped overkill -----------------------------------------------------------------
    // Overkill must use the SAME distribution as the average, or TTK (derived from it) is wrong.

    /** Same boundary check as the average: cap == max must delegate to the uniform version. */
    @Test
    public void cappedOverkillReducesToTheUniformVersionWhenCapEqualsMax() {
        for (int max : new int[]{1, 4, 9, 40}) {
            assertEquals("cap == max must be a no-op for max=" + max,
                DamageDistribution.expectedOverkill(max, 60),
                DamageDistribution.cappedExpectedOverkill(max, max, 60), 1e-12);
        }
    }

    @Test
    public void aCapAboveTheMaxIsANoOpForOverkill() {
        assertEquals(DamageDistribution.expectedOverkill(10, 60),
            DamageDistribution.cappedExpectedOverkill(10, 50, 60), 1e-12);
    }

    /**
     * The bug: clamping the max hit and using the uniform version assumes a flat 1..cap
     * spread, but 37/41 of the mass sits on the cap itself, so real overkill is higher.
     */
    @Test
    public void cappedOverkillExceedsTheNaivelyClampedValue() {
        double naive = DamageDistribution.expectedOverkill(4, 60);
        double correct = DamageDistribution.cappedExpectedOverkill(40, 4, 60);
        assertTrue("mass piled on the cap makes the killing blow overshoot more often",
            correct > naive);
    }

    /** A cap of 1 puts the entire distribution on 1, so a kill can never overshoot. */
    @Test
    public void aCapOfOneCanNeverOverkill() {
        assertEquals(0.0, DamageDistribution.cappedExpectedOverkill(40, 1, 60), 1e-12);
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

    /**
     * King's barrage is corpbane but it is a crossbow, so RANGED is the only style it can ever
     * attack with. A STAB-only exemption silently halved it; the exemption has to cover RANGED,
     * and it must not become a blanket "any ranged weapon is fine at Corp".
     */
    @Test
    public void shippedCorpBeastEntryExemptsKingsBarrageOnRangedOnly() {
        MonsterCombatRequirement req = MonsterCombatRequirementRepository.getInstance()
            .forMonster("Corporeal Beast").orElseThrow(AssertionError::new);
        assertEquals("King's barrage is corpbane on its only style, ranged", 1.0,
            TargetDamageRule.damageMultiplierFor(req, KINGS_BARRAGE, CombatStyle.RANGED), 0.0);
        assertEquals("an ordinary crossbow is still halved", 0.5,
            TargetDamageRule.damageMultiplierFor(req, ARMADYL_CROSSBOW, CombatStyle.RANGED), 0.0);
        assertEquals("a spear is still halved on slash", 0.5,
            TargetDamageRule.damageMultiplierFor(req, CORPBANE_SPEAR, CombatStyle.SLASH), 0.0);
    }

    // ---- the fang's compressed roll, capped ----------------------------------------------
    // The cap meets the fang's SHRUNK roll, not the max hit that roll is derived from.
    // Collapsing maxHit to the cap first and shrinking that is a different, far smaller
    // distribution — the mistake this pair of formulas exists to keep apart.

    /** Brute-force expectation over the fang's uniform lo..hi roll with each result clamped. */
    private static double bruteForceCappedFang(int trueMaxHit, int cap) {
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        double total = 0.0;
        for (int d = lo; d <= hi; d++) {
            total += Math.min(d, cap);
        }
        return total / (hi - lo + 1);
    }

    @Test
    public void cappedFangMatchesABruteForceEnumeration() {
        for (int max : new int[]{20, 27, 40, 55, 99}) {
            for (int cap : new int[]{1, 4, 9, 15, 30, 60}) {
                if (max * 3 / 20 <= 0) {
                    continue; // degenerate range is covered by its own fallback test
                }
                assertEquals("max=" + max + " cap=" + cap,
                    bruteForceCappedFang(max, cap),
                    DamageDistribution.cappedFangAverageDamagePerAttack(1.0, max, cap), 1e-12);
            }
        }
    }

    /** A cap above the shrunk roll's top cannot bind, so it must equal the uncapped fang formula. */
    @Test
    public void cappedFangReducesToTheUncappedFangFormulaWhenTheCapCannotBind() {
        for (int max : new int[]{20, 40, 99}) {
            assertEquals("cap above the shrunk max must be a no-op for max=" + max,
                DamageDistribution.fangAverageDamagePerAttack(1.0, max),
                DamageDistribution.cappedFangAverageDamagePerAttack(1.0, max, max), 1e-12);
        }
    }

    /**
     * The reported defect. A true max of 40 shrinks to a 6..34 roll, every result of which
     * exceeds a cap of 4 — so the fang lands a flat 4 on every successful hit. Deriving the
     * roll from the already-capped 4 instead gives a 0..4 roll averaging ~2.2, understating
     * by nearly half.
     */
    @Test
    public void cappedFangDoesNotShrinkTheCapItself() {
        double correct = DamageDistribution.cappedFangAverageDamagePerAttack(1.0, 40, 4);
        double wrong = DamageDistribution.fangAverageDamagePerAttack(1.0, 4); // ~2.2, the old path
        assertEquals("every hit in a 6..34 roll caps at 4", 4.0, correct, 1e-12);
        assertTrue("the capped-fang formula must not reproduce the shrink-the-cap result",
            correct > wrong + 1.0);
    }

    /** Below the shrunk minimum every hit caps, so the average is exactly the cap. */
    @Test
    public void cappedFangIsFlatWhenTheCapIsBelowTheShrunkMinimum() {
        assertEquals(3.0, DamageDistribution.cappedFangAverageDamagePerAttack(1.0, 99, 3), 1e-12);
        assertEquals(1.5, DamageDistribution.cappedFangAverageDamagePerAttack(0.5, 99, 3), 1e-12);
    }

    /**
     * Both Hueycoatl tail records must reach the cap. `MonsterNameKey` strips a single
     * NON-NESTED trailing parenthetical, so "The Hueycoatl (Tail (broken))" never reduces to
     * the base name and would otherwise report fully uncapped damage.
     */
    @Test
    public void shippedHueycoatlCapCoversBothTailRecords() {
        MonsterCombatRequirementRepository repo = MonsterCombatRequirementRepository.getInstance();
        for (String name : new String[]{"The Hueycoatl (Tail)", "The Hueycoatl (Tail (broken))"}) {
            MonsterCombatRequirement req = repo.forMonster(name).orElseThrow(AssertionError::new);
            assertEquals(name + " must be capped at 4", 4,
                TargetDamageRule.maxHitCapFor(req, gearWithAttackBonuses(50, 10, 10), CombatStyle.STAB, WHIP));
        }
    }

    /**
     * Independent DP over an explicit damage distribution — deliberately built from the
     * distribution itself rather than from the formula under test, so it can disagree.
     */
    private static double bruteForceOverkill(double[] probByDamage, int targetHitpoints) {
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d < probByDamage.length; d++) {
                if (probByDamage[d] == 0.0) {
                    continue;
                }
                sum += probByDamage[d] * (d >= h ? (d - h) : over[h - d]);
            }
            over[h] = sum;
        }
        return over[targetHitpoints];
    }

    /** The fang's compressed roll with each result clamped, as an explicit distribution. */
    private static double[] cappedFangDistribution(int trueMaxHit, int cap) {
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        int top = Math.min(cap, hi);
        double[] p = new double[top + 1];
        double n = hi - lo + 1.0;
        for (int d = lo; d <= hi; d++) {
            p[Math.min(d, cap)] += 1.0 / n;
        }
        return p;
    }

    @Test
    public void cappedFangOverkillMatchesABruteForceDp() {
        for (int max : new int[]{20, 27, 40, 55, 99}) {
            for (int cap : new int[]{1, 4, 9, 15}) {
                for (int hp : new int[]{1, 7, 55, 200}) {
                    assertEquals("max=" + max + " cap=" + cap + " hp=" + hp,
                        bruteForceOverkill(cappedFangDistribution(max, cap), hp),
                        DamageDistribution.cappedFangExpectedOverkill(max, cap, hp), 1e-9);
                }
            }
        }
    }

    /**
     * The reported inconsistency. A true max of 40 shrinks to 6..34, so with a cap of 4
     * EVERY landed hit deals exactly 4 — overkill is whatever a fixed 4-damage hit wastes.
     * The generic capped distribution still spreads mass over 1..4 and disagrees.
     */
    @Test
    public void cappedFangOverkillIsDeterministicWhenEveryRollExceedsTheCap() {
        int hp = 50; // 50 = 12 hits of 4 plus 2, so the killing blow wastes 2
        assertEquals(2.0, DamageDistribution.cappedFangExpectedOverkill(40, 4, hp), 1e-9);
        assertTrue("the generic capped model must not agree — that is the defect",
            Math.abs(DamageDistribution.cappedExpectedOverkill(40, 4, hp) - 2.0) > 1e-6);
    }

    /** Overkill and average damage must come from the same distribution end-to-end. */
    @Test
    public void cappedFangTtkUsesTheSameDistributionAsItsAverage() {
        EquipmentStats fangGear = EquipmentStats.builder()
                .add(80, 60, 40, 30, 70,
                     20, 20, 20, 20, 20,
                     64, 60, 10.0, 0)
                .weaponSpeedTicks(4)
                .osmumtensFang(true)
                .build();
        Monster tail = Monster.builder()
                .name("The Hueycoatl (Tail)")
                .hitpoints(50)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(100)
                .build();
        DpsResult r = DpsCalculator.compute(fangGear, regressionPlayer(), CombatStyle.STAB, tail, 26219);

        assertEquals("overkill must come from the fang's own capped distribution",
            DamageDistribution.cappedFangExpectedOverkill(20, 4, 50), r.overkillPerKill(), 1e-9);
        assertEquals("and TTK must be derived from that same overkill",
            (50 + r.overkillPerKill()) / r.dps(), r.ttkSeconds(), 1e-9);
    }

    /**
     * The formula existing is not the same as {@link DpsCalculator} using it. Drives a real fang
     * loadout through the shipped Hueycoatl entry and asserts the average damage is the capped
     * COMPRESSED roll, not the shrink-the-cap result — and that both tail records agree.
     */
    @Test
    public void dpsCalculatorRoutesTheFangThroughTheCappedCompressedRoll() {
        EquipmentStats fangGear = EquipmentStats.builder()
                .add(80, 60, 40, 30, 70,
                     20, 20, 20, 20, 20,
                     64, 60, 10.0, 0)
                .weaponSpeedTicks(4)
                .osmumtensFang(true)
                .build();
        double previous = -1.0;
        for (String name : new String[]{"The Hueycoatl (Tail)", "The Hueycoatl (Tail (broken))"}) {
            Monster tail = Monster.builder()
                    .name(name)
                    .hitpoints(200)
                    .defenceLevel(100)
                    .defenceBonuses(50, 50, 50, 50, 50)
                    .magicLevel(100)
                    .build();
            DpsResult r = DpsCalculator.compute(fangGear, regressionPlayer(), CombatStyle.STAB, tail, 26219);

            assertEquals("the readout shows the cap as the max hit", 4, r.maxHit());
            assertEquals(name + ": must use the capped compressed roll",
                DamageDistribution.cappedFangAverageDamagePerAttack(r.accuracy(), 20, 4),
                r.avgHit(), 1e-12);
            assertTrue(name + ": must not reproduce the shrink-the-cap average",
                r.avgHit() > DamageDistribution.fangAverageDamagePerAttack(r.accuracy(), 4) + 1.0);

            if (previous >= 0) {
                assertEquals("both tail records must cap identically", previous, r.avgHit(), 1e-12);
            }
            previous = r.avgHit();
        }
    }

    /**
     * The dead-exemption guard. Listing a weapon in {@code allowedItemIds} only helps if one of
     * the styles it can actually attack with is in {@code exemptStyles} — otherwise the entry is
     * inert and the weapon is quietly scored at half damage, which is exactly how King's barrage
     * was wrong. Checks every shipped corpbane weapon against its real style list.
     */
    @Test
    public void everyShippedCorpbaneWeaponIsExemptOnAStyleItCanActuallyUse() {
        MonsterCombatRequirement req = MonsterCombatRequirementRepository.getInstance()
            .forMonster("Corporeal Beast").orElseThrow(AssertionError::new);
        WeaponCategoryRepository categories = WeaponCategoryRepository.getInstance();
        for (int itemId : req.allowedItemIds()) {
            List<WeaponStyle> styles = categories.stylesForItem(itemId);
            assertTrue("no weapon category for corpbane item " + itemId, !styles.isEmpty());
            boolean exemptOnSomeRealStyle = false;
            for (WeaponStyle style : styles) {
                if (TargetDamageRule.damageMultiplierFor(req, itemId, style.type()) == 1.0) {
                    exemptOnSomeRealStyle = true;
                }
            }
            assertTrue("corpbane item " + itemId + " is listed but exempt on no style it can use",
                exemptOnSomeRealStyle);
        }
    }
}
