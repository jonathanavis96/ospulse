package com.ospulse.combat;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the bundled, hand-curated {@code wilderness_monsters.json}
 * (Wilderness-EXCLUSIVE monsters — see {@link WildernessVariantMonsterRepositoryTest}
 * for the separate both-locations set) parses, serves case-insensitive name
 * lookups correctly, and — the director's explicit requirement — that every
 * single curated name is copied EXACTLY from the bundled {@code
 * monsters.min.json.gz}: this test fails on a typo (a name present here but
 * not in the real monster repository) rather than silently doing nothing.
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
                "Chaos Elemental", "Chaos Fanatic", "Crazy archaeologist", "Scorpia",
                // King Black Dragon is deliberately NOT here - see
                // kingBlackDragonLair_isNotCurated below (P1 finding).
        };
        for (String boss : bosses) {
            assertTrue(boss + " must be curated as Wilderness", repo.isWilderness(boss));
        }
    }

    /**
     * King Black Dragon's entrance sits inside level 42 Wilderness, but the OSRS Wiki's own
     * "King Black Dragon Lair" page is explicit that the fight itself is not: "however the lair
     * itself is not the Wilderness" / "The lair itself isn't in the Wilderness, but players are
     * in the Wilderness until they pull the lever" / "As the lair itself is not considered the
     * Wilderness, players can use any means of teleportation to leave." The generation pass
     * mis-classified it by wiki category membership (its entrance page's category) rather than
     * its actual combat location - a P1 finding, since it caused the revenant weapons' +50%
     * accuracy/damage bonus to apply against KBD when it must not.
     */
    @Test
    public void kingBlackDragonLair_isNotCurated() {
        assertFalse("King Black Dragon's lair is explicitly not the Wilderness (OSRS Wiki)",
                WildernessMonsterRepository.getInstance().isWilderness("King Black Dragon"));
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
     * The ordinary Wilderness-EXCLUSIVE combat NPCs added in response to the
     * review finding that the original bosses+revenants-only set left
     * common, selectable Wilderness monsters uncovered. Each of these is
     * either the ONLY location the OSRS Wiki documents for that specific
     * bundled entry, or explicitly location-tagged in the bundled data — see
     * {@code wilderness_monsters.json.README.md}'s "Generation" section for
     * how this was derived and verified.
     *
     * <p><b>"Green dragon (Level 79)" is deliberately NOT here</b> — an
     * earlier hand-read of the wiki's own Locations table mis-attributed
     * which combat level paired with which spawn; the wiki's structured
     * {@code {{LocLine}}} data (parsed directly, not eyeballed) shows Level
     * 79 has BOTH Wilderness field spawns AND the Corsair Cove/Myths' Guild
     * spawn, while Level 88's ONLY location is the Wilderness Slayer Cave —
     * the reverse of what was first assumed. Level 79 is now a
     * both-locations entry (see {@link WildernessVariantMonsterRepositoryTest});
     * Level 88 is exclusive, asserted below.
     *
     * <p><b>"Bandit (Level 22)"/"(Level 130)" are deliberately here now</b>
     * (a prior stage excluded them on the assumption of a same-named Desert
     * Bandit Camp population) — the Bandit wiki page's own structured
     * location data shows both levels ONLY at the Wilderness Bandit Camp,
     * with no non-Wilderness location at all; the earlier exclusion was a
     * guess, not a verified fact, and is corrected here.
     */
    @Test
    public void ordinaryWildernessCombatNpcs_areCurated() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        String[] names = {
                "Lava dragon",
                "Elder Chaos druid",
                "Mammoth (Normal)",
                "Earth warrior",
                "Earth Warrior Champion",
                "Green dragon (Level 88)",
                "Black dragon (Level 247)",
                "Bandit (Bandit Camp) (Level 57)",
                "Bandit (Bandit Camp) (Level 74)",
                "Bandit (Level 22)",
                "Bandit (Level 130)",
                "Bandit champion",
                "Guard Bandit",
                "Rogue (Level 15)",
                "Rogue (Level 135)",
                "Dark warrior (Level 8)",
                "Dark warrior (Level 145)",
                "Ankou (Level 98)",
                "Black demon (Level 188)",
                "Hellhound (Level 136)",
                "Black Heather",
                "Donny the lad",
                "Speedy Keith",
                "Scorpia's guardian",
                "Scorpia's offspring (monster)",
                "Zombie pirate (Level 22)",
                "Zombie pirate (Level 28)",
                "Zombie pirate (Level 34)",
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
     * These bundled names genuinely have NO Wilderness location at all —
     * confirmed against the OSRS Wiki's own structured location data, not
     * assumed — so {@link WildernessMonsterRepository#isWilderness} must
     * stay {@code false} for them AND they must NOT appear in {@link
     * WildernessVariantMonsterRepository} either (see {@code
     * WildernessVariantMonsterRepositoryTest#noEntryHasNoWildernessLocationAtAll}
     * for that half of the guarantee).
     *
     * <p>Some names below (Chaos druid, Hill Giant, Greater demon (Level 92),
     * Black demon (Level 172), Lesser demon (Level 94) untagged) DO now have
     * a Wilderness location — but as a separately-selectable synthetic
     * variant (see {@link WildernessVariantMonsterRepository}), not as this
     * plain entry itself, which is why {@code isWilderness} on the PLAIN
     * name still correctly returns {@code false} here: selecting the plain
     * entry means the player is NOT claiming to fight it in the Wilderness.
     */
    @Test
    public void namesWithNoWildernessLocationAtAll_orOnlyViaASeparateVariant_areNotDirectlyCurated() {
        WildernessMonsterRepository repo = WildernessMonsterRepository.getInstance();
        String[] excluded = {
                "King Black Dragon", // lair is explicitly not the Wilderness (see kingBlackDragonLair_isNotCurated)
                "Black dragon (Echo)",
                "Chaos druid warrior", // zero Wilderness location at all (Yanille / Slepe roof only)
                "Reanimated chaos druid",
                "Dark Ankou",
                "Ankou (Level 95)", // no Wilderness location for this specific level
                "Ice giant (1)", // untagged non-Wilderness variant, distinct from the tagged cave ones
                "Dust devil (Catacombs of Kourend)",
                "Abyssal demon (Standard)",
                // Both-locations species: the PLAIN entry is correctly
                // excluded here; the Wilderness option is the separate
                // synthetic variant (WildernessVariantMonsterRepositoryTest).
                "Chaos druid",
                "Hill Giant",
                "Greater demon (Level 92)",
                "Black demon (Level 172)",
                "Lesser demon (Level 94)",
                "Black dragon (Level 227)",
                "Green dragon (Level 79)",
                "Ankou (Level 86)",
                "Hellhound (Level 122)",
                "Lesser demon (Level 82)",
        };
        for (String name : excluded) {
            assertFalse(name + " must NOT be curated as Wilderness-exclusive (see README)", repo.isWilderness(name));
        }
    }
}
