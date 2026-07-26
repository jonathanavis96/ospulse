package com.ospulse.combat;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the bundled, hand-curated {@code wilderness_monsters.json}
 * parses, serves case-insensitive name lookups correctly, and — the
 * director's explicit requirement — that every single curated name is
 * copied EXACTLY from the bundled {@code monsters.min.json.gz}: this test
 * fails on a typo (a name present here but not in the real monster
 * repository) rather than silently doing nothing.
 */
public class WildernessMonsterRepositoryTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    @Test
    public void loadsBundledResource() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        assertTrue("expected at least the reported wilderness bosses + all 12 revenants", repo.size() >= 20);
    }

    @Test
    public void everyCuratedName_resolvesAgainstTheRealBundledMonsterRepository() {
        WildernessMonsterRepository wilderness = WildernessMonsterRepository.getInstance();
        MonsterRepository monsters = MonsterRepository.getInstance();
        List<String> names = wilderness.namesForTesting();
        assertFalse("curated list must not be empty", names.isEmpty());
        for (String lowercaseName : names) {
            Optional<Monster> resolved = monsters.byName(lowercaseName);
            assertTrue("curated wilderness monster name '" + lowercaseName
                    + "' does not resolve against the bundled monster data - check for a typo", resolved.isPresent());
        }
    }

    @Test
    public void reportedWildernessBosses_areAllCurated() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        String[] bosses = {
                "Callisto", "Artio", "Venenatis", "Spindel",
                "Vet'ion (Normal)", "Vet'ion (Enraged)", "Calvar'ion (Normal)", "Calvar'ion (Enraged)",
                "Chaos Elemental", "Chaos Fanatic", "Crazy archaeologist", "Scorpia", "King Black Dragon",
        };
        for (String boss : bosses) {
            assertTrue(boss + " must be curated as Wilderness", repo.isWilderness(boss));
        }
    }

    @Test
    public void allTwelveRevenants_areCurated() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        String[] revenants = {
                "Revenant cyclops", "Revenant dark beast", "Revenant demon", "Revenant dragon",
                "Revenant goblin", "Revenant hellhound", "Revenant hobgoblin", "Revenant imp",
                "Revenant knight", "Revenant maledictus", "Revenant ork", "Revenant pyrefiend",
        };
        for (String revenant : revenants) {
            assertTrue(revenant + " must be curated as Wilderness", repo.isWilderness(revenant));
        }
    }

    @Test
    public void matchIsCaseInsensitive() {
        assertTrue(WildernessMonsterRepository.getInstance().isWilderness("cAlLiStO"));
    }

    @Test
    public void nonWildernessMonster_isNotCurated() {
        assertFalse(WildernessMonsterRepository.getInstance().isWilderness("General Graardor"));
        assertFalse(WildernessMonsterRepository.getInstance().isWilderness("Zulrah"));
        assertFalse(WildernessMonsterRepository.getInstance().isWilderness(null));
    }

    /**
     * The ordinary Wilderness combat NPCs added in response to the review
     * finding that the original bosses+revenants-only set left common,
     * selectable Wilderness monsters uncovered. Each of these is either
     * Wilderness-exclusive or explicitly location-tagged in the bundled
     * data — see {@code wilderness_monsters.json.README.md}'s "Included, and
     * why" section for the citation behind each one.
     */
    @Test
    public void ordinaryWildernessCombatNpcs_areCurated() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        String[] names = {
                "Lava dragon",
                "Elder Chaos druid",
                "Mammoth (Normal)",
                "Earth warrior",
                "Green dragon (Level 79)",
                "Black dragon (Level 247)",
                "Bandit (Bandit Camp) (Level 57)",
                "Bandit (Bandit Camp) (Level 74)",
                "Rogue (Level 15)",
                "Rogue (Level 135)",
                "Ent (Wilderness)",
                "Abyssal demon (Wilderness Slayer Cave)",
                "Dust devil (Wilderness Slayer Cave)",
                "Greater Nechryael (Wilderness Slayer Cave)",
                "Ice giant (Wilderness Slayer Cave 1)",
                "Ice giant (Wilderness Slayer Cave 2)",
                "Ice giant (Wilderness Slayer Cave 3)",
                "Lesser demon (Level 94 (Wilderness Slayer Cave))",
                "Greater demon (Level 104 (Wilderness Slayer Cave))",
        };
        for (String name : names) {
            assertTrue(name + " must be curated as Wilderness", repo.isWilderness(name));
        }
    }

    @Test
    public void wildernessAgilityCourseSkeletons_andWildernessZombies_areCurated() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        for (int i = 1; i <= 7; i++) {
            String skeleton = "Skeleton (Wilderness Agility Course) (" + i + ")";
            assertTrue(skeleton + " must be curated as Wilderness", repo.isWilderness(skeleton));
        }
        for (int i = 1; i <= 7; i++) {
            String zombie = "Zombie (Wilderness) (Level 18, " + i + ")";
            assertTrue(zombie + " must be curated as Wilderness", repo.isWilderness(zombie));
        }
        for (int i = 1; i <= 5; i++) {
            String zombie = "Zombie (Wilderness) (Level 24, " + i + ")";
            assertTrue(zombie + " must be curated as Wilderness", repo.isWilderness(zombie));
        }
    }

    /**
     * Every name in this list shares its bundled DISPLAY NAME with a
     * non-Wilderness spawn (or is majority non-Wilderness), per the
     * README's "Deliberately excluded" section — a false positive here
     * would OVER-state DPS, so these must resolve as "not Wilderness" even
     * though a same-family name (a different level/tag) IS curated above.
     * This is the direct regression test for the fail-safe direction the
     * review demanded: prefer under-selling a weapon over sending someone
     * into the Wilderness expecting a damage boost they will not get.
     */
    @Test
    public void ambiguousOrMajorityNonWildernessNames_areDeliberatelyExcluded() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        String[] excluded = {
                "Green dragon (Level 88)", // Corsair Cove/Myths' Guild ONLY - never Wilderness
                "Black dragon (Level 227)", // 7 locations, only 1 (Lava Maze) is Wilderness
                "Black dragon (Echo)",
                "Chaos druid",
                "Chaos druid warrior",
                "Reanimated chaos druid",
                "Hill Giant",
                "Ankou (Level 95)",
                "Dark Ankou",
                "Greater demon (Level 92)",
                "Black demon (Level 172)",
                "Bandit (Level 22)", // untagged - a same-named Desert Bandit Camp also exists
                "Bandit (Level 130)",
                "Ice giant (1)", // untagged non-Wilderness variant, distinct from the tagged cave ones
                "Lesser demon (Level 94)", // untagged - distinct from the "(Wilderness Slayer Cave)" tagged one
                "Dust devil (Catacombs of Kourend)",
                "Abyssal demon (Standard)",
        };
        for (String name : excluded) {
            assertFalse(name + " must NOT be curated as Wilderness (see README)", repo.isWilderness(name));
        }
    }
}
