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
    public void searchIsCaseInsensitiveAndCapped() {
        MonsterRepository repo = MonsterRepository.getInstance();
        List<Monster> results = repo.search("abyssal demon");
        assertFalse(results.isEmpty());
        for (Monster m : results) {
            assertTrue(m.name().toLowerCase().contains("abyssal demon"));
        }
        assertTrue("search should be capped around 25", repo.search("a").size() <= 25);
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
}
