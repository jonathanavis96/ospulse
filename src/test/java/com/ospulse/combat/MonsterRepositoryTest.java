package com.ospulse.combat;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonsterRepositoryTest {
    @Test
    public void loadsBundledResourceWithManyEntries() {
        MonsterRepository repo = MonsterRepository.getInstance();
        assertTrue("expected a substantial bundled monster count", repo.size() > 1000);
    }

    @Test
    public void searchIsCaseInsensitiveAndUncapped() {
        MonsterRepository repo = MonsterRepository.getInstance();
        List<Monster> results = repo.search("abyssal demon");
        assertFalse(results.isEmpty());
        for (Monster m : results) {
            assertTrue(m.name().toLowerCase().contains("abyssal demon"));
        }
        // The old 25-result cap is gone: the UI shows a scrollable list of ALL
        // matches, so a broad query must return far more than 25.
        assertTrue("search should return all matches (uncapped)", repo.search("a").size() > 25);
        // search() dedupes near-identical combat-instance variants (same name
        // + defence level, differing only by a trailing variant marker — see
        // dedupesRepeatedCombatVariants below), so an empty query returns
        // fewer entries than the raw bundled count, but still the vast
        // majority of it (dedup collapses ~2830 -> ~2450, not a drastic cut).
        int deduped = repo.search("").size();
        assertTrue("empty query should return most of the bundled list, deduped", deduped > repo.size() / 2);
        assertTrue("dedup should actually reduce the count below the raw total", deduped < repo.size());
    }

    @Test
    public void dedupesRepeatedCombatVariants() {
        MonsterRepository repo = MonsterRepository.getInstance();
        List<Monster> zombies = repo.search("Zombie (Wilderness)");
        long level18Count = zombies.stream().filter(m -> m.defenceLevel() == 18).count();
        assertEquals("the 7 identical (Level 18, N) Zombie (Wilderness) variants should collapse to 1",
                1, level18Count);

        // A genuinely distinct trailing parenthetical (not a numeric variant
        // marker) must NOT be collapsed away.
        List<Monster> billyGoats = repo.search("Billy Goat");
        assertTrue("non-variant distinguishing names (e.g. \"Billy Goat (Tan)\") must survive dedup",
                billyGoats.stream().anyMatch(m -> m.name().contains("Tan")));
    }

    @Test
    public void byIdFindsAKnownAbyssalDemonVariant() {
        MonsterRepository repo = MonsterRepository.getInstance();
        Optional<Monster> byName = repo.byName("Abyssal demon (Catacombs of Kourend)");
        assertTrue(byName.isPresent());
        Monster m = byName.get();
        assertEquals(135, m.defenceLevel());
        assertTrue(m.attributes().contains(MonsterAttribute.DEMON));

        Optional<Monster> byId = repo.byId(m.npcIds().get(0));
        assertTrue(byId.isPresent());
        assertEquals(m.name(), byId.get().name());
    }

    @Test
    public void byIdReturnsEmptyForUnknownId() {
        MonsterRepository repo = MonsterRepository.getInstance();
        assertFalse(repo.byId(-999999).isPresent());
    }

    @Test
    public void undeadMonsterIsFlaggedUndead() {
        MonsterRepository repo = MonsterRepository.getInstance();
        List<Monster> ghosts = repo.search("Ankou");
        assertFalse(ghosts.isEmpty());
        assertTrue(ghosts.stream().anyMatch(Monster::isUndead));
    }

    /**
     * Guards the gzip-compressed resource load path (monsters.min.json.gz,
     * decompressed via GZIPInputStream in MonsterRepository.loadFromResource):
     * a known monster with well-established stats must still resolve exactly
     * as it did from the plain JSON. Cerberus is also exercised end-to-end by
     * ScorchingBowCerberusParityTest.
     */
    @Test
    public void cerberusLoadsFromGzippedResourceWithKnownStats() {
        MonsterRepository repo = MonsterRepository.getInstance();
        Optional<Monster> byId = repo.byId(5862);
        assertTrue("Cerberus (npc id 5862) must load from the gzipped bundle", byId.isPresent());
        Monster cerberus = byId.get();
        assertEquals("Cerberus", cerberus.name());
        assertEquals(600, cerberus.hitpoints());
        assertTrue("Cerberus must be tagged DEMON", cerberus.isDemon());
    }
}
