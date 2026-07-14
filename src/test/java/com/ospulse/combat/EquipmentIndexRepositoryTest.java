package com.ospulse.combat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the bundled {@code equipment_index.min.json} (name+slot for every
 * item id that also has an {@link EquipmentStatsRepository} row) parses and
 * serves lookups/search correctly — this is the name/slot half of the item
 * index the Phase 2/3 candidate picker relies on.
 */
public class EquipmentIndexRepositoryTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    @Test
    public void loadsBundledResourceWithManyEntries() {
        EquipmentIndexRepository repo = EquipmentIndexRepository.getInstance();
        assertEquals(3179, repo.size());
    }

    @Test
    public void everyEntryAlsoHasEquipmentStats() {
        // Guarantees the search index never surfaces an item the DPS engine
        // cannot compute (see the resource README).
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
        EquipmentStatsRepository stats = EquipmentStatsRepository.getInstance();
        for (EquipmentIndexRepository.Entry e : index.forSlot(3)) {
            assertNotNull("weapon " + e.name() + " (" + e.itemId() + ") must have stats",
                    stats.statsFor(e.itemId()));
        }
    }

    @Test
    public void abyssalWhip_nameAndWeaponSlot_oneHanded() {
        EquipmentIndexRepository.Entry e = EquipmentIndexRepository.getInstance().entryFor(4151);
        assertNotNull(e);
        assertEquals("Abyssal whip", e.name());
        assertEquals(3, e.slotOrdinal()); // WEAPON
        assertTrue("whip is one-handed", !e.isTwoHanded());
    }

    @Test
    public void twistedBow_nameAndWeaponSlot_twoHanded() {
        EquipmentIndexRepository.Entry e = EquipmentIndexRepository.getInstance().entryFor(20997);
        assertNotNull(e);
        assertEquals("Twisted bow", e.name());
        assertEquals(3, e.slotOrdinal());
        assertTrue("twisted bow is two-handed", e.isTwoHanded());
    }

    @Test
    public void nonWeaponSlotsAreNeverTwoHanded() {
        EquipmentIndexRepository repo = EquipmentIndexRepository.getInstance();
        for (int slot : new int[] {0, 1, 2, 4, 5, 7, 9, 10, 12, 13}) {
            for (EquipmentIndexRepository.Entry e : repo.forSlot(slot)) {
                assertTrue(e.name() + " in non-weapon slot must not be two-handed", !e.isTwoHanded());
            }
        }
    }

    @Test
    public void searchSlot_filtersByNameCaseInsensitive() {
        EquipmentIndexRepository repo = EquipmentIndexRepository.getInstance();
        // "abyssal whip" as a substring also matches variants (Frozen/Volcanic/(or)) —
        // confirm the base whip is among them and every hit genuinely contains the query.
        List<EquipmentIndexRepository.Entry> results = repo.searchSlot(3, "abyssal whip");
        assertTrue(results.size() >= 1);
        boolean foundBase = false;
        for (EquipmentIndexRepository.Entry e : results) {
            assertTrue(e.name().toLowerCase(java.util.Locale.ROOT).contains("abyssal whip"));
            foundBase |= e.itemId() == 4151;
        }
        assertTrue("expected the base Abyssal whip (4151) among the matches", foundBase);
    }

    @Test
    public void searchSlot_emptyQueryReturnsAllInSlot() {
        EquipmentIndexRepository repo = EquipmentIndexRepository.getInstance();
        List<EquipmentIndexRepository.Entry> weapons = repo.forSlot(3);
        List<EquipmentIndexRepository.Entry> searched = repo.searchSlot(3, "");
        assertEquals(weapons.size(), searched.size());
        assertTrue(weapons.size() > 100);
    }

    @Test
    public void searchSlot_wrongSlotExcluded() {
        EquipmentIndexRepository repo = EquipmentIndexRepository.getInstance();
        // Abyssal whip is a weapon (slot 3) — searching the shield slot (5) must not find it.
        List<EquipmentIndexRepository.Entry> results = repo.searchSlot(5, "abyssal whip");
        assertTrue(results.isEmpty());
    }

    @Test
    public void unknownIdReturnsNull() {
        assertNull(EquipmentIndexRepository.getInstance().entryFor(99_999_999));
    }

    /**
     * Codex review finding #3 (PR #5, {@code OwnedVariantResolver}): several
     * indexed names genuinely have more than one backing id (one per reward
     * source) — {@code idForName} keeps only the first, but {@code
     * idsForName} must return every one so a caller checking "do I own ANY
     * copy of this name" doesn't miss a match.
     */
    @Test
    public void idsForName_returnsEveryIdSharingTheName() {
        EquipmentIndexRepository repo = EquipmentIndexRepository.getInstance();
        List<Integer> ids = repo.idsForName("Warrior ring (i)");
        assertEquals(java.util.Arrays.asList(26769, 11772, 25262), ids);
        // idForName's single-id pick must be the FIRST of idsForName's list —
        // the two must never silently disagree.
        assertEquals(ids.get(0), repo.idForName("Warrior ring (i)"));
    }

    @Test
    public void idsForName_isCaseInsensitive() {
        EquipmentIndexRepository repo = EquipmentIndexRepository.getInstance();
        assertEquals(repo.idsForName("Abyssal whip"), repo.idsForName("ABYSSAL WHIP"));
        assertTrue(repo.idsForName("abyssal whip").contains(4151));
    }

    @Test
    public void idsForName_unknownNameReturnsEmptyNotNull() {
        List<Integer> ids = EquipmentIndexRepository.getInstance().idsForName("Not a real item, honestly");
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }

    @Test
    public void idsForName_nullReturnsEmpty() {
        List<Integer> ids = EquipmentIndexRepository.getInstance().idsForName(null);
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }
}
