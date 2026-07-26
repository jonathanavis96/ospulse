package com.ospulse.combat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

/**
 * Covers the targets a player physically cannot melee, or cannot hit with a
 * whole combat style.
 *
 * <p>Reported by a user on issue #11: the optimiser recommended an Abyssal
 * tentacle for Zulrah, which cannot connect at all — Zulrah sits across water
 * and only a halberd has the reach.
 *
 * <p>These assert through the real dataset (not a hand-built fixture) and
 * through the variant names the monster picker actually hands over, e.g.
 * {@code "Zulrah (Magma)"}, so a rename or a typo'd key fails here rather than
 * silently disabling the gate in production. The gate *mechanism* is already
 * covered by {@code GearOptimizerCombatGateTest}; this covers the *data*.
 */
public class UnreachableTargetGateTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    private static final int ABYSSAL_TENTACLE = 12006;  // 1-tile slash melee — the reported bad pick
    private static final int ABYSSAL_WHIP = 4151;       // 1-tile slash melee
    private static final int DRAGON_SCIMITAR = 4587;    // 1-tile slash melee
    private static final int NOXIOUS_HALBERD = 29796;   // extended reach — Zulrah's only melee option
    private static final int DARK_BOW = 11235;          // ranged
    private static final int TRIDENT_OF_THE_SEAS = 11905; // magic

    private static MonsterCombatRequirement gateFor(String monsterName) {
        Optional<MonsterCombatRequirement> found =
            MonsterCombatRequirementRepository.getInstance().forMonster(monsterName);
        assertTrue("no combat requirement found for '" + monsterName + "'", found.isPresent());
        return found.get();
    }

    /** Every bundled Zulrah phase must resolve to the gate via the base-name fallback. */
    @Test
    public void everyZulrahPhaseIsGated() {
        for (String phase : new String[]{"Zulrah (Magma)", "Zulrah (Serpentine)", "Zulrah (Tanzanite)"}) {
            MonsterCombatRequirement gate = gateFor(phase);
            assertFalse(phase + " still permits a tentacle",
                gate.permitsWeapon(ABYSSAL_TENTACLE, CombatStyle.SLASH, true));
        }
    }

    /** The exact weapon the reporter saw recommended, at the phase he was fighting. */
    @Test
    public void zulrahRejectsOneTileMelee() {
        MonsterCombatRequirement gate = gateFor("Zulrah (Magma)");
        assertFalse("tentacle cannot reach Zulrah",
            gate.permitsWeapon(ABYSSAL_TENTACLE, CombatStyle.SLASH, true));
        assertFalse("whip cannot reach Zulrah",
            gate.permitsWeapon(ABYSSAL_WHIP, CombatStyle.SLASH, true));
        assertFalse("scimitar cannot reach Zulrah",
            gate.permitsWeapon(DRAGON_SCIMITAR, CombatStyle.SLASH, true));
    }

    /**
     * The halberd exception is the point of the entry — gating melee entirely
     * would be just as wrong as gating nothing.
     */
    @Test
    public void zulrahStillAllowsAHalberd() {
        MonsterCombatRequirement gate = gateFor("Zulrah (Serpentine)");
        assertTrue("a halberd does reach Zulrah and must stay recommendable",
            gate.permitsWeapon(NOXIOUS_HALBERD, CombatStyle.SLASH, true));
        assertTrue("ranged is always fine at Zulrah",
            gate.permitsWeapon(DARK_BOW, CombatStyle.RANGED, true));
    }

    /**
     * Krakens and the Leviathan take no melee at all — unlike Zulrah, a halberd
     * reaches a cave kraken but deals no damage, so it must not be suggested.
     */
    @Test
    public void krakensAndLeviathanRejectAllMeleeIncludingHalberds() {
        for (String name : new String[]{"Kraken (Kraken)", "Cave kraken (Cave kraken)",
                                        "The Leviathan (Post-quest)"}) {
            MonsterCombatRequirement gate = gateFor(name);
            assertFalse(name + " must not permit a whip",
                gate.permitsWeapon(ABYSSAL_WHIP, CombatStyle.SLASH, true));
            assertFalse(name + " must not permit a halberd either",
                gate.permitsWeapon(NOXIOUS_HALBERD, CombatStyle.SLASH, true));
            assertTrue(name + " must still permit ranged",
                gate.permitsWeapon(DARK_BOW, CombatStyle.RANGED, true));
        }
    }

    /**
     * Tekton is immune to ranged, so that is gated. Its bundled defence bonuses
     * read a flat drange of 0, so nothing in the DPS data steers away from
     * ranged on its own.
     *
     * <p>Magic is deliberately NOT gated: Tekton takes 80% reduced magic damage,
     * which is graduated resistance, not immunity. Encoding it as a hard block
     * would grey out a style that genuinely works, and the dataset's own rule is
     * that only true immunities become gates. The 80% reduction is modelled
     * separately as a damage penalty.
     */
    @Test
    public void tektonRejectsRangedOnly() {
        MonsterCombatRequirement gate = gateFor("Tekton (Normal)");
        assertFalse("Tekton is immune to ranged",
            gate.permitsWeapon(DARK_BOW, CombatStyle.RANGED, true));
        assertTrue("Tekton is a melee target",
            gate.permitsWeapon(ABYSSAL_WHIP, CombatStyle.SLASH, true));
        assertTrue("magic is reduced at Tekton, not blocked — it must stay selectable",
            gate.permitsWeapon(TRIDENT_OF_THE_SEAS, CombatStyle.MAGIC, true));
    }

    /**
     * Dawn flies; Dusk does not. They are separate bundled monsters and only
     * Dusk had an entry before — Dawn was silently ungated.
     */
    @Test
    public void dawnIsGatedAsAFlyingTarget() {
        MonsterCombatRequirement gate = gateFor("Dawn");
        assertFalse("Dawn flies — a 1-tile melee weapon cannot target it",
            gate.permitsWeapon(ABYSSAL_WHIP, CombatStyle.SLASH, true));
        assertTrue("a halberd reaches Dawn",
            gate.permitsWeapon(NOXIOUS_HALBERD, CombatStyle.SLASH, true));
    }

    /**
     * Warden gates are keyed on the exact phase name, so the active phase is
     * restricted while every other phase stays untouched. This is the assertion
     * that would fail if the lookup ever stopped preferring exact names over the
     * base-name fallback.
     */
    @Test
    public void onlyTheActiveWardenPhaseIsGated() {
        MonsterCombatRequirement active = gateFor("Tumeken's Warden (Active)");
        assertFalse("melee does not damage the active warden",
            active.permitsWeapon(ABYSSAL_WHIP, CombatStyle.SLASH, true));
        assertTrue("ranged is the answer for Tumeken's warden",
            active.permitsWeapon(DARK_BOW, CombatStyle.RANGED, true));

        assertFalse("a non-active warden phase must not be gated",
            MonsterCombatRequirementRepository.getInstance()
                .forMonster("Tumeken's Warden (Damaged)").isPresent());
    }

    /** A monster with no entry must be entirely unaffected — the gating must not leak. */
    @Test
    public void anUngatedMonsterIsUnaffected() {
        assertFalse("Vorkath is fully meleeable and must have no gate",
            MonsterCombatRequirementRepository.getInstance()
                .forMonster("Vorkath (Post-quest)").isPresent());
    }
}
