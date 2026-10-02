package com.ospulse.combat;


import java.util.*;

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

    private static final CombatDataLoader.Lazy<MonsterGearOverrideRepository> INSTANCE =
            new CombatDataLoader.Lazy<>(() -> loadFromResource(RESOURCE_PATH));

    private final Map<String, List<MonsterGearOverride>> byLowercaseMonsterName;

    private MonsterGearOverrideRepository(Map<String, List<MonsterGearOverride>> byLowercaseMonsterName) {
        this.byLowercaseMonsterName = Collections.unmodifiableMap(byLowercaseMonsterName);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static MonsterGearOverrideRepository getInstance() {
        return INSTANCE.get();
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static MonsterGearOverrideRepository loadFromResource(String resourcePath) {
        RootDto root = CombatDataLoader.parse(MonsterGearOverrideRepository.class, resourcePath, RootDto.class);
        Map<String, List<MonsterGearOverride>> byName = new HashMap<>();
        if (root != null && root.overrides != null) {
            for (OverrideDto dto : root.overrides) {
                if (dto.monsters == null || dto.slot == null || dto.itemName == null) {
                    continue; // malformed entry — treated as "no data"
                }
                MonsterGearOverride.Slot slot = CombatDataLoader.parseEnum(MonsterGearOverride.Slot.class, dto.slot);
                if (slot == null) {
                    continue; // unknown slot name in the data — skip defensively
                }
                java.util.Set<Integer> alternativeItemIds = dto.alternativeItemIds == null
                        ? java.util.Collections.emptySet()
                        : new java.util.LinkedHashSet<>(dto.alternativeItemIds);
                for (String monsterName : dto.monsters) {
                    if (monsterName == null || monsterName.isEmpty()) {
                        continue;
                    }
                    MonsterGearOverride override = new MonsterGearOverride(
                            monsterName, slot, dto.itemId, dto.itemName, dto.reason, alternativeItemIds);
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
        if (found == null) {
            // The picker hands us dataset names like "Rune dragon (Elvarg)" — fall back to the base name.
            found = byLowercaseMonsterName.get(MonsterNameKey.baseName(monsterName));
        }
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
        /**
         * Optional: other item ids that satisfy this requirement equally well
         * as {@code itemId} (review finding 3 — e.g. every Slayer helmet
         * variant substitutes for a plain face-protection item). Absent/empty
         * for every requirement with no known substitute.
         */
        List<Integer> alternativeItemIds;
    }
}
