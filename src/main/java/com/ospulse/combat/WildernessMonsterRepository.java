package com.ospulse.combat;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the bundled, hand-curated Wilderness-EXCLUSIVE monster name list
 * ({@code /com/ospulse/combat/wilderness_monsters.json}, see the
 * accompanying README for provenance/generation) and serves in-memory
 * lookups by monster name.
 *
 * <p>This is one of the two curated inputs the revenant-weapon Wilderness
 * bonus (§9e) needs — the bundled monster snapshot has no location field at
 * all (confirmed when the expensive-item cap's "wilderness" framing was
 * investigated — that is advisory tooltip text, not a data property), so
 * "is this monster in the Wilderness" cannot be derived and must be
 * generated from the OSRS Wiki's own location data, mirroring {@link
 * MonsterGearOverrideRepository}/{@link MonsterCombatRequirementRepository}'s
 * own bundled-resource pattern.
 *
 * <p><b>This set holds only Wilderness-EXCLUSIVE monsters</b> — every
 * location the OSRS Wiki documents for the specific bundled entry is a
 * Wilderness location, so selecting it always applies the revenant-weapon
 * bonus with no ambiguity. A monster that ALSO exists outside the
 * Wilderness (e.g. Black dragon — found at both the Lava Maze Dungeon and
 * several non-Wilderness dungeons) does NOT belong here: crediting its
 * ordinary, possibly-non-Wilderness entry with the bonus would be a false
 * positive. Those monsters instead get an explicitly separate,
 * player-selected "(Wilderness)" twin — see {@link
 * WildernessVariantMonsterRepository} — so the player states which instance
 * they mean rather than the engine guessing. Together, the two curated sets
 * make coverage comprehensive: every Wilderness-fightable monster the
 * generation pass found is selectable as a Wilderness target one way or the
 * other; see that README for the documented residual gap.
 *
 * <p>Matches by monster NAME (case-insensitive exact match), not npc id —
 * same convention as {@link MonsterRepository#byName}.
 */
public final class WildernessMonsterRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/wilderness_monsters.json";

    private static volatile WildernessMonsterRepository instance;

    private final Set<String> lowercaseNames;

    private WildernessMonsterRepository(Set<String> lowercaseNames) {
        this.lowercaseNames = Collections.unmodifiableSet(lowercaseNames);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static WildernessMonsterRepository getInstance() {
        WildernessMonsterRepository result = instance;
        if (result == null) {
            synchronized (WildernessMonsterRepository.class) {
                result = instance;
                if (result == null) {
                    instance = result = loadFromResource(RESOURCE_PATH);
                }
            }
        }
        return result;
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static WildernessMonsterRepository loadFromResource(String resourcePath) {
        Gson gson = BundledGson.get();
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            RootDto root = gson.fromJson(reader, RootDto.class);
            Set<String> names = new HashSet<>();
            if (root != null && root.monsters != null) {
                for (String name : root.monsters) {
                    if (name == null || name.isEmpty()) {
                        continue; // malformed entry - treated as "not present"
                    }
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
            return new WildernessMonsterRepository(names);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load wilderness monster data from " + resourcePath, e);
        }
    }

    private static InputStream requireResource(String resourcePath) {
        InputStream in = WildernessMonsterRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        return lowercaseNames.size();
    }

    /**
     * True when {@code monsterName} (case-insensitive exact match) is a
     * Wilderness-EXCLUSIVE monster — see the class javadoc for how a
     * both-locations monster is handled instead ({@link
     * WildernessVariantMonsterRepository}).
     */
    public boolean isWilderness(String monsterName) {
        return monsterName != null && lowercaseNames.contains(monsterName.toLowerCase(Locale.ROOT));
    }

    /** Every curated name, for tests that want to verify each one against the real {@link MonsterRepository}. */
    List<String> namesForTesting() {
        return new java.util.ArrayList<>(lowercaseNames);
    }

    /** Internal Gson deserialisation shape mirroring {@code wilderness_monsters.json}'s top-level object. */
    private static final class RootDto {
        List<String> monsters;
    }
}
