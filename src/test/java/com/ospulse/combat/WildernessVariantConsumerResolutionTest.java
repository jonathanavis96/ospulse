package com.ospulse.combat;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The reverse-map guarantee a synthetic Wilderness-variant target needs:
 * every consumer that resolves data by monster identity (per-target damage
 * caps/penalties, required-gear reminders, consumables reminders) must
 * resolve a synthetic twin (e.g. "Black dragon (Wilderness)") back to its
 * REAL underlying monster ("Black dragon (Level 227)") via {@link
 * Monster#lookupName()}, not its decorated DISPLAY name ({@link
 * Monster#name()}). This is exactly the class of defect this repo has
 * already been bitten by once (an alias/proxy entry working for the thing
 * it was built for, then breaking every other consumer keyed by monster
 * identity) — each test below exercises the REAL production entry point
 * for one consumer, not a re-implementation of its lookup logic.
 */
public class WildernessVariantConsumerResolutionTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    // ---- DpsCalculator / MonsterCombatRequirementRepository --------------------------------

    /**
     * Corporeal Beast is not itself a Wilderness monster - this test builds
     * its OWN synthetic twin (not a real curated Wilderness variant) purely
     * to exercise the reverse-map mechanism in isolation: a target whose
     * DISPLAY name the curated {@code monster_combat_requirements.json} has
     * never heard of must still gate on the SAME damage penalty as the real
     * Corporeal Beast, because {@code DpsCalculator.resolveRequirement}
     * looks up {@link Monster#lookupName()}, not {@link Monster#name()}.
     */
    @Test
    public void dpsCalculator_resolvesCombatRequirementThroughASyntheticTwinsLookupName() {
        Monster realCorp = MonsterRepository.getInstance().byName("Corporeal Beast").orElseThrow(AssertionError::new);
        Monster syntheticTwin = Monster.builderFrom(realCorp)
                .name("Corporeal Beast (Test Synthetic Twin)")
                .lookupName("Corporeal Beast")
                .wildernessTarget(true)
                .build();

        EquipmentStats ordinaryStabWeapon = EquipmentStats.builder()
                .add(80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
        PlayerCombat player = PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();

        DpsResult viaRealCorp = DpsCalculator.compute(ordinaryStabWeapon, player, CombatStyle.STAB, realCorp, 0);
        DpsResult viaSyntheticTwin = DpsCalculator.compute(ordinaryStabWeapon, player, CombatStyle.STAB, syntheticTwin, 0);

        // Corp halves non-corpbane melee damage - both must show the SAME
        // halved max hit, proving the synthetic twin resolved the identical
        // DAMAGE_PENALTY requirement via its lookupName.
        assertTrue("fixture must actually be non-corpbane so the penalty binds", viaRealCorp.maxHit() > 0);
        assertEquals(viaRealCorp.maxHit(), viaSyntheticTwin.maxHit());
        assertEquals(viaRealCorp.dps(), viaSyntheticTwin.dps(), 1e-9);
    }

    /** The real, shipped Wilderness variant: Black dragon (Level 227) has no curated combat requirement, so both must be null/unaffected identically. */
    @Test
    public void blackDragonWildernessVariant_hasNoCombatRequirement_sameAsItsBase() {
        Monster twin = MonsterRepository.getInstance().byName("Black dragon (Wilderness)").orElseThrow(AssertionError::new);
        Monster base = MonsterRepository.getInstance().byName("Black dragon (Level 227)").orElseThrow(AssertionError::new);
        Optional<MonsterCombatRequirement> viaTwin = MonsterCombatRequirementRepository.getInstance().forMonster(twin.lookupName());
        Optional<MonsterCombatRequirement> viaBase = MonsterCombatRequirementRepository.getInstance().forMonster(base.name());
        assertEquals(viaBase.isPresent(), viaTwin.isPresent());
    }

    // ---- MonsterGearOverrideRepository (ItemEligibility / OwnedOnlyMandatoryOverrideGate) ---

    /**
     * Rune dragon requires Insulated boots (the flagship curated gear
     * override). A synthetic "Rune dragon (Wilderness)"-style twin (built
     * here purely for the test - Rune dragon has no real Wilderness spawn)
     * must still surface the SAME requirement through both production entry
     * points that consume it in the UI.
     */
    @Test
    public void itemEligibility_and_ownedOnlyGate_resolveGearOverrideThroughASyntheticTwinsLookupName() {
        Monster realRuneDragon = MonsterRepository.getInstance().byName("Rune dragon").orElseThrow(AssertionError::new);
        Monster syntheticTwin = Monster.builderFrom(realRuneDragon)
                .name("Rune dragon (Test Synthetic Twin)")
                .lookupName("Rune dragon")
                .wildernessTarget(true)
                .build();

        java.util.Set<Integer> noExclusions = java.util.Collections.emptySet();
        java.util.Set<Integer> noOwned = java.util.Collections.emptySet();

        java.util.Set<Integer> viaReal = com.ospulse.ui.sections.gear.ItemEligibility.mandatoryOverrideItemIds(
                realRuneDragon, noExclusions, false, noOwned);
        java.util.Set<Integer> viaTwin = com.ospulse.ui.sections.gear.ItemEligibility.mandatoryOverrideItemIds(
                syntheticTwin, noExclusions, false, noOwned);

        assertTrue("fixture must actually carry the Insulated boots requirement", viaReal.contains(7159));
        assertEquals(viaReal, viaTwin);

        Optional<MonsterGearOverride> viaGateReal = com.ospulse.ui.sections.gear.OwnedOnlyMandatoryOverrideGate
                .blockingOverride(true, realRuneDragon, noOwned);
        Optional<MonsterGearOverride> viaGateTwin = com.ospulse.ui.sections.gear.OwnedOnlyMandatoryOverrideGate
                .blockingOverride(true, syntheticTwin, noOwned);
        assertEquals(viaGateReal.isPresent(), viaGateTwin.isPresent());
        assertEquals(viaGateReal.get().itemId(), viaGateTwin.get().itemId());
    }

    /**
     * Same production entry points as above, but with the doubly-decorated
     * name the base-name fallback cannot rescue - the real discrimination
     * proof for {@code ItemEligibility.mandatoryOverrideItemIds} and {@code
     * OwnedOnlyMandatoryOverrideGate.blockingOverride} routing through
     * {@link Monster#lookupName()}.
     */
    @Test
    public void itemEligibility_and_ownedOnlyGate_doublyDecoratedTwin_stillResolveTheOverride() {
        Monster realRuneDragon = MonsterRepository.getInstance().byName("Rune dragon").orElseThrow(AssertionError::new);
        Monster twin = doublyDecoratedTwin(realRuneDragon);
        java.util.Set<Integer> noExclusions = java.util.Collections.emptySet();
        java.util.Set<Integer> noOwned = java.util.Collections.emptySet();

        java.util.Set<Integer> viaTwin = com.ospulse.ui.sections.gear.ItemEligibility.mandatoryOverrideItemIds(
                twin, noExclusions, false, noOwned);
        assertTrue(viaTwin.contains(7159));

        Optional<MonsterGearOverride> viaGateTwin = com.ospulse.ui.sections.gear.OwnedOnlyMandatoryOverrideGate
                .blockingOverride(true, twin, noOwned);
        assertTrue(viaGateTwin.isPresent());
        assertEquals(7159, (int) viaGateTwin.get().itemId());
    }

    /** The real, shipped Wilderness variant: Black dragon has no curated gear override, so both must agree (empty) identically. */
    @Test
    public void blackDragonWildernessVariant_hasNoGearOverride_sameAsItsBase() {
        Monster twin = MonsterRepository.getInstance().byName("Black dragon (Wilderness)").orElseThrow(AssertionError::new);
        Monster base = MonsterRepository.getInstance().byName("Black dragon (Level 227)").orElseThrow(AssertionError::new);
        List<MonsterGearOverride> viaTwin = MonsterGearOverrideRepository.getInstance().forMonster(twin.lookupName());
        List<MonsterGearOverride> viaBase = MonsterGearOverrideRepository.getInstance().forMonster(base.name());
        assertEquals(viaBase.size(), viaTwin.size());
    }

    // ---- MonsterConsumablesRepository (ConsumablesReminderPanel) ---------------------------

    /**
     * The real, shipped case: "Black dragon" (family) carries the dragonfire
     * consumables reminder. The Wilderness variant of Black dragon (Level
     * 227) must show the IDENTICAL note, proving {@code
     * ConsumablesReminderPanel.refresh} (as wired in {@code GearSection})
     * resolves through {@link Monster#lookupName()} rather than the
     * decorated "(Wilderness)" display name the curated consumables data
     * was never authored against.
     */
    @Test
    public void blackDragonWildernessVariant_getsTheSameDragonfireReminderAsItsBase() {
        Monster twin = MonsterRepository.getInstance().byName("Black dragon (Wilderness)").orElseThrow(AssertionError::new);
        Monster base = MonsterRepository.getInstance().byName("Black dragon (Level 227)").orElseThrow(AssertionError::new);

        Optional<MonsterConsumablesReminder> viaBase = MonsterConsumablesRepository.getInstance().forMonster(base.name());
        assertTrue("fixture must actually carry a curated reminder", viaBase.isPresent());

        com.ospulse.ui.sections.gear.ConsumablesReminderPanel panel = new com.ospulse.ui.sections.gear.ConsumablesReminderPanel();
        panel.refresh(twin.lookupName());
        List<String> renderedViaTwin = panel.noteTextsForTest();

        panel.refresh(base.name());
        List<String> renderedViaBase = panel.noteTextsForTest();

        assertFalse("twin must actually render the reminder (proves lookupName routing worked)", renderedViaTwin.isEmpty());
        assertEquals(renderedViaBase, renderedViaTwin);
    }

    // ---- Genuine discrimination proof --------------------------------------------------
    //
    // Every curated identity repository above (MonsterCombatRequirementRepository,
    // MonsterGearOverrideRepository, MonsterConsumablesRepository) ALSO has its own
    // "strip one trailing (…) group" base-name fallback (MonsterNameKey.baseName /
    // an equivalent). Because every real, SHIPPED variant's displayName is exactly
    // "<baseMonster> (Wilderness)" (a single trailing parenthetical), that fallback
    // happens to recover the correct base name coincidentally even if a consumer
    // used the WRONG name() field - meaning the tests above, while genuine end-to-end
    // regression proofs, do not by themselves prove the lookupName() plumbing is what
    // makes them pass. This section constructs a name with TWO trailing parenthetical
    // groups (only the fallback's own single strip is applied, so it does NOT recover
    // the real base name) to force a real discrimination between name() and
    // lookupName() routing.

    private static Monster doublyDecoratedTwin(Monster base) {
        return Monster.builderFrom(base)
                .name(base.name() + " (Wilderness) (Extra Decoration)")
                .lookupName(base.name())
                .wildernessTarget(true)
                .build();
    }

    /**
     * Exercises the ACTUAL production entry point ({@code
     * DpsCalculator.compute} -&gt; {@code resolveRequirement}), not the
     * repository directly, with the doubly-decorated name that the plain
     * base-name fallback cannot rescue - if {@code resolveRequirement} ever
     * regressed to using {@link Monster#name()} instead of {@link
     * Monster#lookupName()}, this is the test that would catch it (proven
     * below by reverting that one line and re-running).
     */
    @Test
    public void dpsCalculator_doublyDecoratedTwin_stillGetsThePenalty_provingItRoutesThroughLookupName() {
        Monster realCorp = MonsterRepository.getInstance().byName("Corporeal Beast").orElseThrow(AssertionError::new);
        Monster twin = doublyDecoratedTwin(realCorp);

        EquipmentStats ordinaryStabWeapon = EquipmentStats.builder()
                .add(80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
        PlayerCombat player = PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();

        DpsResult viaRealCorp = DpsCalculator.compute(ordinaryStabWeapon, player, CombatStyle.STAB, realCorp, 0);
        DpsResult viaDoublyDecoratedTwin = DpsCalculator.compute(ordinaryStabWeapon, player, CombatStyle.STAB, twin, 0);

        assertEquals(viaRealCorp.maxHit(), viaDoublyDecoratedTwin.maxHit());
    }

    @Test
    public void combatRequirement_doublyDecoratedName_wouldMissViaNameButHitsViaLookupName() {
        Monster realCorp = MonsterRepository.getInstance().byName("Corporeal Beast").orElseThrow(AssertionError::new);
        Monster twin = doublyDecoratedTwin(realCorp);

        Optional<MonsterCombatRequirement> viaWrongNameField =
                MonsterCombatRequirementRepository.getInstance().forMonster(twin.name());
        Optional<MonsterCombatRequirement> viaLookupName =
                MonsterCombatRequirementRepository.getInstance().forMonster(twin.lookupName());

        assertFalse("a double-decorated name() must NOT resolve (proves the fallback alone is not enough)",
                viaWrongNameField.isPresent());
        assertTrue("lookupName() must still resolve the real requirement", viaLookupName.isPresent());
    }

    @Test
    public void gearOverride_doublyDecoratedName_wouldMissViaNameButHitsViaLookupName() {
        Monster realRuneDragon = MonsterRepository.getInstance().byName("Rune dragon").orElseThrow(AssertionError::new);
        Monster twin = doublyDecoratedTwin(realRuneDragon);

        List<MonsterGearOverride> viaWrongNameField = MonsterGearOverrideRepository.getInstance().forMonster(twin.name());
        List<MonsterGearOverride> viaLookupName = MonsterGearOverrideRepository.getInstance().forMonster(twin.lookupName());

        assertTrue("a double-decorated name() must NOT resolve (proves the fallback alone is not enough)",
                viaWrongNameField.isEmpty());
        assertFalse("lookupName() must still resolve the real override", viaLookupName.isEmpty());
    }

    @Test
    public void consumables_doublyDecoratedName_wouldMissViaNameButHitsViaLookupName() {
        Monster realBlackDragon = MonsterRepository.getInstance().byName("Black dragon (Level 227)").orElseThrow(AssertionError::new);
        Monster twin = doublyDecoratedTwin(realBlackDragon);

        Optional<MonsterConsumablesReminder> viaWrongNameField =
                MonsterConsumablesRepository.getInstance().forMonster(twin.name());
        Optional<MonsterConsumablesReminder> viaLookupName =
                MonsterConsumablesRepository.getInstance().forMonster(twin.lookupName());

        assertFalse("a double-decorated name() must NOT resolve (proves the fallback alone is not enough)",
                viaWrongNameField.isPresent());
        assertTrue("lookupName() must still resolve the real reminder", viaLookupName.isPresent());
    }
}
