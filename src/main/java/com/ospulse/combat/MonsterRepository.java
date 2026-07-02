package com.ospulse.combat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads the bundled monster combat-stat snapshot
 * ({@code /com/ospulse/combat/monsters.min.json}, see the accompanying
 * README in that resource directory for provenance/licensing/regeneration)
 * and serves in-memory lookups. RuneLite bundles Gson (transitively, via
 * {@code net.runelite:client}; see {@code build.gradle}), so no extra
 * dependency is needed at runtime.
 */
public final class MonsterRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/monsters.min.json";
    private static final int SEARCH_CAP = 25;

    private static volatile MonsterRepository instance;

    private final List<Monster> monsters;

    private MonsterRepository(List<Monster> monsters) {
        this.monsters = Collections.unmodifiableList(monsters);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static MonsterRepository getInstance() {
        MonsterRepository result = instance;
        if (result == null) {
            synchronized (MonsterRepository.class) {
                result = instance;
                if (result == null) {
                    instance = result = loadFromResource(RESOURCE_PATH);
                }
            }
        }
        return result;
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static MonsterRepository loadFromResource(String resourcePath) {
        Gson gson = new Gson();
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<MonsterDto>>() {
            }.getType();
            List<MonsterDto> dtos = gson.fromJson(reader, listType);
            List<Monster> parsed = new ArrayList<>(dtos.size());
            for (MonsterDto dto : dtos) {
                parsed.add(dto.toMonster());
            }
            return new MonsterRepository(parsed);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load monster data from " + resourcePath, e);
        }
    }

    private static java.io.InputStream requireResource(String resourcePath) {
        java.io.InputStream in = MonsterRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        return monsters.size();
    }

    /** Case-insensitive name-contains search, capped at ~25 results. */
    public List<Monster> search(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return monsters.stream()
                .filter(m -> m.name().toLowerCase(Locale.ROOT).contains(needle))
                .limit(SEARCH_CAP)
                .collect(Collectors.toList());
    }

    public Optional<Monster> byId(int npcId) {
        return monsters.stream()
                .filter(m -> m.npcIds().contains(npcId))
                .findFirst();
    }

    public Optional<Monster> byName(String name) {
        return monsters.stream()
                .filter(m -> m.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** Internal Gson deserialisation shape, mirroring {@code monsters.min.json}'s fields exactly. */
    private static final class MonsterDto {
        int id;
        String name;
        int hitpoints;
        int defenceLevel;
        int dstab;
        int dslash;
        int dcrush;
        int dmagic;
        int drange;
        int magicLevel;
        int size;
        List<String> attributes;
        Integer attackSpeedTicks;

        Monster toMonster() {
            java.util.EnumSet<MonsterAttribute> attrs = java.util.EnumSet.noneOf(MonsterAttribute.class);
            if (attributes != null) {
                for (String raw : attributes) {
                    try {
                        attrs.add(MonsterAttribute.valueOf(raw));
                    } catch (IllegalArgumentException ignored) {
                        // Unmapped/unknown attribute in the data source - no Tier-A effect keys off it.
                    }
                }
            }
            return Monster.builder()
                    .name(name)
                    .npcIds(Collections.singletonList(id))
                    .hitpoints(hitpoints)
                    .defenceLevel(defenceLevel)
                    .defenceBonuses(dstab, dslash, dcrush, dmagic, drange)
                    .magicLevel(magicLevel)
                    .size(size)
                    .attributes(attrs)
                    .attackSpeedTicks(attackSpeedTicks)
                    .build();
        }
    }
}
