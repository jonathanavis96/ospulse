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

    /** Round-2 director decision (step 5 fallback): HEAL is recommended when it is the only owned+legal spec at all. */
    @Test
    public void healRoleIsSelectedAsAFallbackWhenItIsTheOnlyOwnedSpec() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(SARADOMIN_GODSWORD));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(SARADOMIN_GODSWORD, result(40, 0.8));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(SARADOMIN_GODSWORD, rec.get().itemId());
    }

    /** HEAL is a last resort only — an owned DAMAGE spec always outranks it, never the other way round. */
    @Test
    public void healRoleNeverOutranksAnOwnedDamageSpec() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(SARADOMIN_GODSWORD, DRAGON_CLAWS));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(SARADOMIN_GODSWORD, result(90, 0.99)); // absurdly high, would win on any damage metric
        byId.put(DRAGON_CLAWS, result(10, 0.2));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(DRAGON_CLAWS, rec.get().itemId());
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

    // ---- PR #25 finding 1: ownership does not imply equippability -----------------------

    /**
     * Voidwaker requires 75 Attack (verified against {@code
     * equipment_requirements.min.json}). Owning one at a lower Attack level
     * must not surface it — {@link EquipmentRequirementsRepository#canEquip}
     * is the exact seam {@code GearOptimizer} already uses for this, not a
     * second requirements table.
     */
    @Test
    public void equipLevelRequirementIsEnforcedEvenWhenOwned() {
        int voidwaker = 27690;
        assertEquals("test assumes Voidwaker's real requirement — if this fails the bundled data changed",
                Integer.valueOf(75), EquipmentRequirementsRepository.getInstance().requirementsFor(voidwaker).get("attack"));

        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(voidwaker));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(voidwaker, result(40, 0.6));
        java.util.Map<String, Integer> tooLowAttack = Collections.singletonMap("attack", 60);

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, Collections.emptySet(), tooLowAttack, -1, fixedProbe(byId));
        assertFalse("60 Attack cannot equip a 75-Attack Voidwaker", rec.isPresent());
    }

    /** Same setup as above, but with a level that DOES meet the requirement — proves the gate isn't just always rejecting. */
    @Test
    public void equipLevelRequirementAllowsTheWeaponOnceMet() {
        int voidwaker = 27690;
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(voidwaker));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(voidwaker, result(40, 0.6));
        java.util.Map<String, Integer> sufficientAttack = Collections.singletonMap("attack", 75);

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, Collections.emptySet(), sufficientAttack, -1, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(voidwaker, rec.get().itemId());
    }

    // ---- Round 2 finding (c): equip level must be resolved across the weapon family ------

    /**
     * The catalog's canonical Dragon dagger id (1231) has NO row in
     * {@code equipment_requirements.min.json} — a data-shape gap found in
     * review — while alias ids 1215/5698 carry the real 60 Attack
     * requirement. Checking the canonical id alone (as {@link
     * EquipmentRequirementsRepository#canEquip} does when given just {@link
     * #DRAGON_DAGGER}) fails OPEN and lets a sub-60-Attack player be
     * recommended it. The fix resolves the requirement across the whole
     * family (canonical + every alias) at the spec-catalog level.
     */
    @Test
    public void equipLevelRequirementIsResolvedAcrossTheWeaponFamilyEvenWhenTheCanonicalIdHasNoRow() {
        assertEquals("test assumes the canonical id's data gap — if this fails the bundled data changed",
                null, EquipmentRequirementsRepository.getInstance().requirementsFor(DRAGON_DAGGER));
        assertEquals("test assumes an alias carries the real requirement — if this fails the bundled data changed",
                Integer.valueOf(60), EquipmentRequirementsRepository.getInstance().requirementsFor(1215).get("attack"));

        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_DAGGER));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_DAGGER, result(40, 0.6));
        java.util.Map<String, Integer> tooLowAttack = Collections.singletonMap("attack", 30);

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, Collections.emptySet(), tooLowAttack, -1, fixedProbe(byId));
        assertFalse("30 Attack cannot equip a 60-Attack Dragon dagger, even though the canonical id's own row is missing",
                rec.isPresent());
    }

    /** Same setup as above, but with a level that DOES meet the family's real requirement. */
    @Test
    public void equipLevelRequirementResolvedAcrossTheFamilyAllowsTheWeaponOnceMet() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_DAGGER));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_DAGGER, result(40, 0.6));
        java.util.Map<String, Integer> sufficientAttack = Collections.singletonMap("attack", 60);

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, Collections.emptySet(), sufficientAttack, -1, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(DRAGON_DAGGER, rec.get().itemId());
    }

    // ---- PR #25 finding 2: ranged specs need worn-ammo validation ------------------------

    /**
     * A Kurask/Turoth-shaped gate (real shape, from {@code
     * monster_combat_requirements.json}: leaf-bladed weapons, broad ammo
     * (both broad arrows AND broad bolts), or Magic Dart): no
     * broadly-allowed style, a small leaf-bladed-weapon exception list, and
     * a broad-ammo allowlist. {@code permitsWeapon} alone accepts ANY
     * worn-ammo-firing ranged weapon here (it can't see which ammo is
     * actually loaded) — {@code permitsAmmo} is what actually enforces
     * "broad ammo only".
     */
    private static MonsterCombatRequirement kuraskShapedAmmoGate() {
        int broadArrows = 4160;
        int broadBolts = 11875;
        int leafBladedSword = 4158;
        return MonsterCombatRequirement.weaponGate(
                new HashSet<>(java.util.Arrays.asList(leafBladedSword)),
                new HashSet<>(java.util.Arrays.asList(broadArrows, broadBolts)),
                Collections.emptySet(),
                "Only leaf-bladed weapons, broad ammo, or Magic Dart can harm it.");
    }

    /** Magic shortbow (861) — a DAMAGE-role RANGED spec that fires worn ammo (ARROW class), unlike the UTILITY-role Zaryte crossbow. */
    private static final int MAGIC_SHORTBOW = 861;

    @Test
    public void rangedSpecOwningWrongWornAmmoIsNotRecommended() {
        int regularArrow = 893; // "Rune arrow" — not in the gate's allowedAmmoIds
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(MAGIC_SHORTBOW));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(MAGIC_SHORTBOW, result(90, 0.99));

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), kuraskShapedAmmoGate(), owned, Collections.emptySet(),
                Collections.emptyMap(), regularArrow, fixedProbe(byId));
        assertFalse("permitsWeapon() alone would accept this — permitsAmmo() must reject the non-broad arrow",
                rec.isPresent());
    }

    @Test
    public void rangedSpecWithCorrectBroadAmmoIsRecommended() {
        int broadArrows = 4160; // matches kuraskShapedAmmoGate()'s allowedAmmoIds
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(MAGIC_SHORTBOW));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(MAGIC_SHORTBOW, result(90, 0.99));

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), kuraskShapedAmmoGate(), owned, Collections.emptySet(),
                Collections.emptyMap(), broadArrows, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(MAGIC_SHORTBOW, rec.get().itemId());
    }

    /** A self-supplying ranged weapon (no worn ammo) is unaffected by the ammo gate either way — it was already excluded by permitsWeapon. */
    @Test
    public void selfSupplyingRangedSpecIgnoresWornAmmoValidation() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(TOXIC_BLOWPIPE));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(TOXIC_BLOWPIPE, result(90, 0.99));

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), kuraskShapedAmmoGate(), owned, Collections.emptySet(),
                Collections.emptyMap(), -1, fixedProbe(byId));
        assertFalse("a dart-firing blowpipe cannot hit a broad-ammo-gated target regardless of worn ammo",
                rec.isPresent());
    }

    // ---- Round 2 finding (b): ammo must match the WEAPON, not just the target ------------

    /**
     * Kurask's real gate permits BOTH broad arrows and broad bolts (see
     * {@link #kuraskShapedAmmoGate}) — that is a target-side allowlist, not a
     * promise that any weapon can fire any allowed ammo. A Magic shortbow
     * only ever fires the ARROW class ({@link AmmoCompatibility#consumedClass}),
     * so broad BOLTS worn behind it must still be rejected even though
     * {@code permitsWeapon}/{@code permitsAmmo} both pass — the bow physically
     * cannot load a bolt.
     */
    @Test
    public void rangedSpecWithWrongAmmoClassForTheWeaponIsRejectedEvenWhenTheTargetAllowsIt() {
        int broadBolts = 11875; // in kuraskShapedAmmoGate()'s allowedAmmoIds, but wrong CLASS for a bow
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(MAGIC_SHORTBOW));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(MAGIC_SHORTBOW, result(90, 0.99));

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), kuraskShapedAmmoGate(), owned, Collections.emptySet(),
                Collections.emptyMap(), broadBolts, fixedProbe(byId));
        assertFalse("a Magic shortbow cannot fire broad bolts even though Kurask's gate allows the ammo class",
                rec.isPresent());
    }

    /**
     * The bigger hole from the finding: MOST targets have no combat
     * requirement at all (unrestricted), so {@code requirement == null}
     * skipped the ammo check entirely. Weapon/ammo compatibility is a
     * property of the weapon and must be enforced regardless of whether the
     * monster gates ammo.
     */
    @Test
    public void rangedSpecWithWrongAmmoClassForTheWeaponIsRejectedEvenAtAnUnrestrictedTarget() {
        int broadBolts = 11875; // BOLT class; Magic shortbow only fires ARROW
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(MAGIC_SHORTBOW));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(MAGIC_SHORTBOW, result(90, 0.99));

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, Collections.emptySet(),
                Collections.emptyMap(), broadBolts, fixedProbe(byId));
        assertFalse("no monster requirement at all must not exempt weapon/ammo compatibility",
                rec.isPresent());
    }

    /** Sanity check: the SAME unrestricted target still recommends the shortbow when the worn ammo actually matches. */
    @Test
    public void rangedSpecWithCorrectAmmoClassIsRecommendedAtAnUnrestrictedTarget() {
        int runeArrow = 893; // ARROW class — matches Magic shortbow's consumedClass
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(MAGIC_SHORTBOW));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(MAGIC_SHORTBOW, result(90, 0.99));

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, Collections.emptySet(),
                Collections.emptyMap(), runeArrow, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals(MAGIC_SHORTBOW, rec.get().itemId());
    }

    // ---- PR #25 finding 4: "Exclude from suggestions" must be honoured -------------------

    /**
     * Excluding the higher-scoring owned spec must remove it from
     * consideration entirely (not just demote it) and let the next-best
     * owned+legal spec take over — the exact two-part assertion the
     * "Exclude from suggestions" feature (shipped stage 4, reporter's
     * explicit request) already guarantees for ordinary optimiser
     * candidates via {@code GearOptimizer.Request.exclude}.
     */
    @Test
    public void excludedSpecIsRemovedAndADifferentSpecIsChosenInstead() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS, DRAGON_DAGGER));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        // Dragon claws would clearly win on damage/cost if not excluded.
        byId.put(DRAGON_CLAWS, result(90, 0.95));
        byId.put(DRAGON_DAGGER, result(20, 0.5));

        Optional<SpecWeaponRecommendation> unexcluded = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, Collections.emptySet(), Collections.emptyMap(), -1, fixedProbe(byId));
        assertEquals("sanity check: claws must win before exclusion", DRAGON_CLAWS, unexcluded.get().itemId());

        Set<Integer> excluded = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS));
        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, excluded, Collections.emptyMap(), -1, fixedProbe(byId));
        assertTrue(rec.isPresent());
        assertEquals("excluding claws must let dagger take over, not just leave nothing recommended",
                DRAGON_DAGGER, rec.get().itemId());
    }

    /** Excluding a cosmetic recolour (alias id) excludes the whole weapon, mirroring {@link #ownedAliasIdCountsAsOwnership}. */
    @Test
    public void excludingAnAliasIdExcludesTheWholeWeapon() {
        Set<Integer> owned = new HashSet<>(java.util.Arrays.asList(DRAGON_CLAWS));
        java.util.Map<Integer, DpsResult> byId = new java.util.HashMap<>();
        byId.put(DRAGON_CLAWS, result(40, 0.6));
        Set<Integer> excludedByAlias = new HashSet<>(java.util.Arrays.asList(28039)); // Dragon claws (or)

        Optional<SpecWeaponRecommendation> rec = SpecWeaponSelector.select(
                monsterWithDefence(50), null, owned, excludedByAlias, Collections.emptyMap(), -1, fixedProbe(byId));
        assertFalse(rec.isPresent());
    }
}
