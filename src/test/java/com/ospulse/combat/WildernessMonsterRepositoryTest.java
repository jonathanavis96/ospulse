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
}
