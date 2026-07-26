package com.ospulse.combat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

/**
 * Guards the hand-curated {@code monster_combat_requirements.json}.
 *
 * <p>Every monster key must resolve against the bundled monster data and every
 * allowed item id against the bundled equipment index. Both failure modes are
 * silent in production — a typo'd name simply never matches, so the gate stops
 * applying and the optimiser goes back to recommending an unusable weapon with
 * no error anywhere. This test is the only thing standing between a data edit
 * and a wrong recommendation.
 */
public class MonsterCombatRequirementDataTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /**
     * Every curated key must match a bundled monster, either exactly or via the
     * base-name fallback {@code forMonster} uses.
     */
    @Test
    public void everyCuratedMonsterNameResolves() {
        Set<String> exactNames = new HashSet<>();
        Set<String> baseNames = new HashSet<>();
        for (Monster monster : MonsterRepository.getInstance().all()) {
            String name = monster.name().toLowerCase(Locale.ROOT);
            exactNames.add(name);
            baseNames.add(MonsterCombatRequirementRepository.baseNameOf(name));
        }

        for (String key : MonsterCombatRequirementRepository.getInstance().curatedKeys()) {
            assertTrue(
                "curated monster name matches no bundled monster: '" + key + "'",
                exactNames.contains(key) || baseNames.contains(key));
        }
    }

    /** Every allowed weapon and ammo id must exist in the bundled equipment index. */
    @Test
    public void everyAllowedItemIdResolves() {
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
        for (MonsterCombatRequirement requirement
                : MonsterCombatRequirementRepository.getInstance().allRequirements()) {
            for (Integer id : requirement.allowedItemIds()) {
                assertTrue("allowed item id not in the equipment index: " + id,
                    index.entryFor(id) != null);
            }
            for (Integer id : requirement.allowedAmmoIds()) {
                assertTrue("allowed ammo id not in the equipment index: " + id,
                    index.entryFor(id) != null);
            }
            // finisherItemIds are deliberately NOT checked here: a finisher is an
            // inventory tool or consumable (rock hammer, bag of salt, fungicide
            // spray), not worn gear, so it is correctly absent from an index of
            // equippable items. Asserting them here fails on valid data.
        }
    }

    /** Every entry must carry a note — it is what the panel shows to explain a greyed-out style. */
    @Test
    public void everyRequirementHasANote() {
        for (MonsterCombatRequirement requirement
                : MonsterCombatRequirementRepository.getInstance().allRequirements()) {
            String note = requirement.note();
            assertTrue("requirement is missing its explanatory note",
                note != null && !note.trim().isEmpty());
        }
    }

    /**
     * A weapon gate that permits nothing would silently exclude every candidate.
     * Each gate must allow at least one style or at least one specific item.
     */
    @Test
    public void everyWeaponGateAllowsSomething() {
        for (MonsterCombatRequirement requirement
                : MonsterCombatRequirementRepository.getInstance().allRequirements()) {
            if (requirement.type() != MonsterCombatRequirement.Type.WEAPON_GATE) {
                continue;
            }
            assertFalse("weapon gate permits nothing at all — every candidate would be pruned",
                requirement.allowedStyles().isEmpty()
                    && requirement.allowedItemIds().isEmpty()
                    && requirement.allowedAmmoIds().isEmpty());
        }
    }

    /** A parse failure would leave an empty map and make every other assertion vacuous. */
    @Test
    public void datasetActuallyLoaded() {
        assertFalse("the curated dataset is empty — did it fail to parse?",
            MonsterCombatRequirementRepository.getInstance().curatedKeys().isEmpty());
    }
}
