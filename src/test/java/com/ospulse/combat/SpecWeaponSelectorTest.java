package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

/**
 * Tests {@link SpecWeaponSelector#select} against the design spec §8
 * selection rule, using REAL {@link SpecWeapon#CATALOG} entries (so a
 * catalog edit that breaks the rule's assumptions — e.g. removing every
 * DEFENCE_DRAIN entry — shows up here) with a synthetic {@link
 * SpecWeaponSelector.DpsProbe} so the ranking logic is tested independently
 * of the real DPS pipeline.
 */
public class SpecWeaponSelectorTest {
    static {
        BundledGson.set(new com.google.gson.Gson());
    }

    private static final int DRAGON_CLAWS = 13652;
    private static final int DRAGON_DAGGER = 1231;
    private static final int TOXIC_BLOWPIPE = 12926;
    private static final int DRAGON_WARHAMMER = 13576;
    private static final int BANDOS_GODSWORD = 11804;
    private static final int SARADOMIN_GODSWORD = 11806;
    private static final int ZARYTE_CROSSBOW = 26374;

    private static Monster monsterWithDefence(int defenceLevel) {
        return Monster.builder().name("Test monster").hitpoints(100).defenceLevel(defenceLevel)
                .defenceBonuses(0, 0, 0, 0, 0).magicLevel(1).build();
    }

    /** A fixed max hit + accuracy for every candidate, keyed by item id; unknown ids return null (not computable). */
    private static SpecWeaponSelector.DpsProbe fixedProbe(java.util.Map<Integer, DpsResult> byItemId) {
        return weapon -> byItemId.get(weapon.itemId());
    }

    private static DpsResult result(int maxHit, double accuracy) {
        return new DpsResult(maxHit, accuracy, 0, 0, 0, 0, false);
    }

    @Test
    public void emptyWhenNothingOwned() {
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, Collections.emptySet(), fixedProbe(Collections.emptyMap()));
        assertFalse(rec.isPresent());
    }

    @Test
    public void emptyWhenTargetIsNull() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                null, null, owned, fixedProbe(Collections.emptyMap()));
        assertFalse(rec.isPresent());
    }

    @Test
    public void utilityRoleIsNeverRecommendedEvenWhenItScoresHighest() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(ZARYTE_CROSSBOW, DRAGON_CLAWS));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(ZARYTE_CROSSBOW, result(1000, 1.0)); // absurdly high, would win on any damage metric
        byId.put(DRAGON_CLAWS, result(20, 0.5));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(DRAGON_CLAWS, rec.get().itemId());
    }

    /** Faithful reading of the literal rule (see class javadoc): HEAL is catalogued but never selected. */
    @Test
    public void healRoleIsNeverSelectedEvenWhenItIsTheOnlyOwnedSpec() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(SARADOMIN_GODSWORD));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(SARADOMIN_GODSWORD, result(40, 0.8));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, fixedProbe(byId));
        assertFalse(rec.isPresent());
    }

    @Test
    public void picksBestDamageScoreWhenDefenceIsLow() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS, DRAGON_DAGGER));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        // Dragon claws (50% cost): tiny hit so its damage/cost score is low.
        byId.put(DRAGON_CLAWS, result(1, 0.01));
        // Dragon dagger (25% cost): big hit so its damage/cost score wins.
        byId.put(DRAGON_DAGGER, result(80, 0.9));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(DRAGON_DAGGER, rec.get().itemId());
    }

    @Test
    public void defenceDrainOwnedAndTargetHighDefenceWinsOverAHigherScoringDamageSpec() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS, DRAGON_WARHAMMER));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_CLAWS, result(90, 0.95)); // would clearly win on damage/cost alone
        byId.put(DRAGON_WARHAMMER, result(10, 0.3));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(SpecWeaponSelector.HIGH_DEFENCE_THRESHOLD), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals("rule 2 must take priority over rule 3 at/above the threshold",
                DRAGON_WARHAMMER, rec.get().itemId());
    }

    @Test
    public void fallsBackToDamageWhenNoDefenceDrainOwnedEvenAtHighDefence() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_CLAWS, result(40, 0.6));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(SpecWeaponSelector.HIGH_DEFENCE_THRESHOLD + 50), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(DRAGON_CLAWS, rec.get().itemId());
    }

    @Test
    public void belowThresholdIgnoresDefenceDrainEvenIfOwned() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS, DRAGON_WARHAMMER));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_CLAWS, result(40, 0.6));
        byId.put(DRAGON_WARHAMMER, result(90, 0.99));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(SpecWeaponSelector.HIGH_DEFENCE_THRESHOLD - 1), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals("below threshold, rule 3 (best DAMAGE) applies even with a DEFENCE_DRAIN owned",
                DRAGON_CLAWS, rec.get().itemId());
    }

    @Test
    public void illegalWeaponUnderTheCombatRequirementIsExcluded() {
        // A Zulrah-style gate: only RANGED (via allowedStyles) or a specific
        // ranged item id may damage this target — dragon claws (SLASH) and
        // dragon dagger (SLASH per its own spec style) are both excluded,
        // leaving toxic blowpipe (RANGED) as the only eligible DAMAGE spec.
        MonsterCombatRequirement rangedOnly = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(), Collections.emptySet(), EnumSet.of(CombatStyle.RANGED), "Ranged/Magic only");
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS, TOXIC_BLOWPIPE));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_CLAWS, result(90, 0.95));
        byId.put(TOXIC_BLOWPIPE, result(20, 0.5));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), rangedOnly, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(TOXIC_BLOWPIPE, rec.get().itemId());
    }

    @Test
    public void emptyWhenEveryOwnedCandidateIsIllegal() {
        MonsterCombatRequirement rangedOnly = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(), Collections.emptySet(), EnumSet.of(CombatStyle.RANGED), "Ranged/Magic only");
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS, BANDOS_GODSWORD));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), rangedOnly, owned, fixedProbe(Collections.emptyMap()));
        assertFalse(rec.isPresent());
    }

    @Test
    public void ownedAliasIdCountsAsOwnership() {
        // 28039 is one of Dragon claws' cosmetic-recolour alias ids.
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(28039));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_CLAWS, result(40, 0.6));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(DRAGON_CLAWS, rec.get().itemId());
    }
}
