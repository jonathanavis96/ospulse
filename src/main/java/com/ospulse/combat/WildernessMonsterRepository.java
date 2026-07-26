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
 * Loads the bundled, hand-curated Wilderness monster name list ({@code
 * /com/ospulse/combat/wilderness_monsters.json}, see the accompanying README
 * for provenance/shape) and serves in-memory "is this target in the
 * Wilderness" lookups by monster name.
 *
 * <p>This is the ONE genuinely new *input* the revenant-weapon wilderness
 * bonus (§9e) needs: the bundled monster snapshot has no location field at
 * all (confirmed when the expensive-item cap's "wilderness" framing was
 * investigated — that is advisory tooltip text, not a data property), so
 * "is this monster in the Wilderness" cannot be derived and must be
 * curated, mirroring {@link MonsterGearOverrideRepository}/{@link
 * MonsterCombatRequirementRepository}'s own bundled-resource pattern.
 *
 * <p><b>This is a curated SUBSET of Wilderness monsters, not "every
 * Wilderness NPC".</b> It covers the reported bosses, every Revenant, and
 * ordinary Wilderness combat NPCs whose bundled display name either is
 * unique to the Wilderness or is explicitly location-tagged in the bundled
 * data (e.g. "(Wilderness Slayer Cave)"). Many OSRS monster NAMES are
 * shared between a Wilderness spawn and one or more non-Wilderness spawns
 * with no location field to tell them apart in this engine's {@link
 * Monster} — including such a name here would be a false positive (it
 * would overstate DPS for a target the player may not actually be fighting
 * in the Wilderness), so every ambiguous or majority-non-Wilderness name is
 * deliberately EXCLUDED rather than guessed into the set. See the
 * accompanying README's "Deliberately excluded" section for the specific
 * calls and evidence. {@link #isWilderness} therefore has NO fallback
 * beyond this curated set by design — a false negative here only
 * undersells a weapon's DPS, which is the safer failure direction.
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
     * True when {@code monsterName} (case-insensitive exact match) is in the
     * curated Wilderness set — a deliberate SUBSET of real Wilderness NPCs,
     * not "any Wilderness NPC"; see the class javadoc and this repository's
     * README for which names were included/excluded and why.
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
