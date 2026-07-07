package com.ospulse.combat;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the bundled, hand-curated monster-mechanic gear override table
 * ({@code /com/ospulse/combat/monster_gear_overrides.json}, see the
 * accompanying README for provenance/shape) and serves in-memory lookups by
 * monster name.
 *
 * <p>These are cases where a specific item matters for a MECHANICS reason
 * (special-attack mitigation, safespotting, immunity, etc.) rather than raw
 * DPS — flagship example: Insulated boots vs Rune dragons (halves the
 * lightning special-attack damage) — so the DPS optimiser would never
 * surface them on its own. This repository just exposes the curated data;
 * {@code GearSection} renders the advisory note and (where wired up) pins the
 * item into the optimiser search.
 *
 * <p>Matches by monster NAME (case-insensitive), not npc id: a monster has
 * many combat-instance-variant ids sharing the same name, and the curated
 * data is authored against display names (see the resource README). Mirrors
 * {@link MonsterRepository}/{@link EquipmentStatsRepository}'s
 * bundled-resource singleton pattern.
 */
public final class MonsterGearOverrideRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/monster_gear_overrides.json";

    private static volatile MonsterGearOverrideRepository instance;

    private final Map<String, List<MonsterGearOverride>> byLowercaseMonsterName;

    private MonsterGearOverrideRepository(Map<String, List<MonsterGearOverride>> byLowercaseMonsterName) {
        this.byLowercaseMonsterName = Collections.unmodifiableMap(byLowercaseMonsterName);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static MonsterGearOverrideRepository getInstance() {
        MonsterGearOverrideRepository result = instance;
        if (result == null) {
            synchronized (MonsterGearOverrideRepository.class) {
                result = instance;
                if (result == null) {
                    instance = result = loadFromResource(RESOURCE_PATH);
                }
            }
        }
        return result;
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static MonsterGearOverrideRepository loadFromResource(String resourcePath) {
        Gson gson = BundledGson.get();
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            RootDto root = gson.fromJson(reader, RootDto.class);
            Map<String, List<MonsterGearOverride>> byName = new HashMap<>();
            if (root != null && root.overrides != null) {
                for (OverrideDto dto : root.overrides) {
                    if (dto.monsters == null || dto.slot == null || dto.itemName == null) {
                        continue; // malformed entry — treated as "no data"
                    }
                    MonsterGearOverride.Slot slot;
                    try {
                        slot = MonsterGearOverride.Slot.valueOf(dto.slot.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        continue; // unknown slot name in the data — skip defensively
                    }
                    for (String monsterName : dto.monsters) {
                        if (monsterName == null || monsterName.isEmpty()) {
                            continue;
                        }
                        MonsterGearOverride override = new MonsterGearOverride(
                                monsterName, slot, dto.itemId, dto.itemName, dto.reason);
                        byName.computeIfAbsent(monsterName.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                                .add(override);
                    }
                }
            }
            Map<String, List<MonsterGearOverride>> immutableByName = new HashMap<>();
            for (Map.Entry<String, List<MonsterGearOverride>> e : byName.entrySet()) {
                immutableByName.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
            }
            return new MonsterGearOverrideRepository(immutableByName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load monster gear override data from " + resourcePath, e);
        }
    }

    private static InputStream requireResource(String resourcePath) {
        InputStream in = MonsterGearOverrideRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        int total = 0;
        for (List<MonsterGearOverride> overrides : byLowercaseMonsterName.values()) {
            total += overrides.size();
        }
        return total;
    }

    /**
     * The curated gear override(s) for the given monster name (case-insensitive
     * exact match, mirroring {@link MonsterRepository#byName}), or an empty
     * list if this monster has none.
     */
    public List<MonsterGearOverride> forMonster(String monsterName) {
        if (monsterName == null) {
            return Collections.emptyList();
        }
        List<MonsterGearOverride> found = byLowercaseMonsterName.get(monsterName.toLowerCase(Locale.ROOT));
        return found == null ? Collections.emptyList() : found;
    }

    /** Internal Gson deserialisation shape mirroring {@code monster_gear_overrides.json}'s top-level object. */
    private static final class RootDto {
        List<OverrideDto> overrides;
    }

    /** Internal Gson deserialisation shape mirroring one entry of the {@code overrides} array. */
    private static final class OverrideDto {
        List<String> monsters;
        String slot;
        int itemId;
        String itemName;
        String reason;
    }
}
