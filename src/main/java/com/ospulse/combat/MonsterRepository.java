package com.ospulse.combat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.zip.GZIPInputStream;

/**
 * Loads the bundled monster combat-stat snapshot
 * ({@code /com/ospulse/combat/monsters.min.json.gz}, see the accompanying
 * README in that resource directory for provenance/licensing/regeneration)
 * and serves in-memory lookups. The resource is gzip-compressed to shrink
 * the shipped jar (~560KB -> ~50KB) and transparently decompressed with
 * {@link GZIPInputStream} at load time. RuneLite bundles Gson (transitively,
 * via {@code net.runelite:client}; see {@code build.gradle}), so no extra
 * dependency is needed at runtime.
 */
public final class MonsterRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/monsters.min.json.gz";

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

    /**
     * Loads a repository from an arbitrary gzip-compressed classpath resource
     * (mainly for tests). The resource is expected to be a gzip stream
     * wrapping the same JSON array shape as {@code monsters.min.json}.
     */
    static MonsterRepository loadFromResource(String resourcePath) {
        Gson gson = new Gson();
        try (InputStream raw = requireResource(resourcePath);
             InputStream gunzipped = new GZIPInputStream(raw);
             Reader reader = new InputStreamReader(gunzipped, StandardCharsets.UTF_8)) {
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

    private static InputStream requireResource(String resourcePath) {
        InputStream in = MonsterRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        return monsters.size();
    }

    /**
     * Case-insensitive name-contains search over the full bundled list —
     * deliberately uncapped so the UI's scrollable result list can show every
     * match (a contains-scan over ~2.8k names is trivially fast; the JList
     * consuming this is virtualised, so large result sets are cheap too).
     */
    public List<Monster> search(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return monsters.stream()
                .filter(m -> m.name().toLowerCase(Locale.ROOT).contains(needle))
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

    /**
     * Internal Gson deserialisation shape, mirroring {@code monsters.min.json}'s
     * fields. {@code demonbaneResistPercent} is optional in the JSON (Gson
     * leaves the {@code int} field at its default 0 when absent) — the
     * mechanism is data-driven and activates automatically once a monster
     * entry carries a non-zero value.
     */
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
        int demonbaneResistPercent;
        WeaknessDto weakness;

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
                    .demonbaneResistPercent(demonbaneResistPercent)
                    .weakness(weakness == null ? null : weakness.element, weakness == null ? 0 : weakness.severity)
                    .build();
        }
    }

    /**
     * Deserialisation shape for the trimmed {@code weakness} object
     * ({@code {"element":"WIND"|"WATER"|"EARTH"|"FIRE", "severity":<int>}}),
     * omitted from the JSON entirely for monsters with no elemental weakness
     * (Gson leaves the {@code MonsterDto.weakness} field {@code null}).
     */
    private static final class WeaknessDto {
        String element;
        int severity;
    }
}
