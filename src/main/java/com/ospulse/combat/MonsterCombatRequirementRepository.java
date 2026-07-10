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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the bundled, hand-curated monster combat-requirement table
 * ({@code /com/ospulse/combat/monster_combat_requirements.json}) and serves
 * in-memory lookups by monster name.
 *
 * <p>These are cases where a monster can only be damaged by a specific
 * subset of weapons/ammo/combat styles (a {@link MonsterCombatRequirement.Type#WEAPON_GATE}),
 * or must be finished off with a specific item at low HP (a
 * {@link MonsterCombatRequirement.Type#FINISHER}) — flagship examples: Kurask
 * (leaf-bladed weapons, broad ammunition, or magic only) and Gargoyles
 * (rock/granite hammer finisher).
 *
 * <p>Matches by monster NAME (case-insensitive), not npc id, mirroring
 * {@link MonsterGearOverrideRepository}'s bundled-resource singleton pattern.
 */
public final class MonsterCombatRequirementRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/monster_combat_requirements.json";

    private static volatile MonsterCombatRequirementRepository instance;

    private final Map<String, MonsterCombatRequirement> byLowercaseMonsterName;

    private MonsterCombatRequirementRepository(Map<String, MonsterCombatRequirement> byLowercaseMonsterName) {
        this.byLowercaseMonsterName = Collections.unmodifiableMap(byLowercaseMonsterName);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static MonsterCombatRequirementRepository getInstance() {
        MonsterCombatRequirementRepository result = instance;
        if (result == null) {
            synchronized (MonsterCombatRequirementRepository.class) {
                result = instance;
                if (result == null) {
                    instance = result = loadFromResource(RESOURCE_PATH);
                }
            }
        }
        return result;
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static MonsterCombatRequirementRepository loadFromResource(String resourcePath) {
        Gson gson = BundledGson.get();
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            RootDto root = gson.fromJson(reader, RootDto.class);
            Map<String, MonsterCombatRequirement> byName = new HashMap<>();
            if (root != null && root.requirements != null) {
                for (ReqDto dto : root.requirements) {
                    if (dto.monsters == null || dto.type == null) {
                        continue; // malformed entry — treated as "no data"
                    }
                    MonsterCombatRequirement.Type type;
                    try {
                        type = MonsterCombatRequirement.Type.valueOf(dto.type.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        continue; // unknown type in the data — skip defensively
                    }
                    Set<CombatStyle> allowedStyles = EnumSet.noneOf(CombatStyle.class);
                    if (dto.allowedStyles != null) {
                        for (String styleName : dto.allowedStyles) {
                            if (styleName == null) {
                                continue;
                            }
                            try {
                                allowedStyles.add(CombatStyle.valueOf(styleName.trim().toUpperCase(Locale.ROOT)));
                            } catch (IllegalArgumentException ignored) {
                                // unknown style name in the data — skip defensively
                            }
                        }
                    }
                    MonsterCombatRequirement requirement;
                    if (type == MonsterCombatRequirement.Type.FINISHER) {
                        requirement = MonsterCombatRequirement.finisher(
                                dto.finisherItemIds == null ? Collections.emptySet() : new HashSet<>(dto.finisherItemIds),
                                dto.note);
                    } else {
                        requirement = MonsterCombatRequirement.weaponGate(
                                dto.allowedItemIds == null ? Collections.emptySet() : new HashSet<>(dto.allowedItemIds),
                                dto.allowedAmmoIds == null ? Collections.emptySet() : new HashSet<>(dto.allowedAmmoIds),
                                allowedStyles, dto.note);
                    }
                    for (String monsterName : dto.monsters) {
                        if (monsterName == null || monsterName.isEmpty()) {
                            continue;
                        }
                        byName.put(monsterName.toLowerCase(Locale.ROOT), requirement);
                    }
                }
            }
            return new MonsterCombatRequirementRepository(byName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load monster combat requirement data from " + resourcePath, e);
        }
    }

    private static InputStream requireResource(String resourcePath) {
        InputStream in = MonsterCombatRequirementRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        return byLowercaseMonsterName.size();
    }

    /**
     * The curated combat requirement for the given monster name
     * (case-insensitive exact match, mirroring
     * {@link MonsterGearOverrideRepository#forMonster}), or empty if this
     * monster has none.
     */
    public Optional<MonsterCombatRequirement> forMonster(String monsterName) {
        if (monsterName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byLowercaseMonsterName.get(monsterName.toLowerCase(Locale.ROOT)));
    }

    /** Internal Gson deserialisation shape mirroring {@code monster_combat_requirements.json}'s top-level object. */
    private static final class RootDto {
        List<ReqDto> requirements;
    }

    /** Internal Gson deserialisation shape mirroring one entry of the {@code requirements} array. */
    private static final class ReqDto {
        List<String> monsters;
        String type;
        List<Integer> allowedItemIds;
        List<Integer> allowedAmmoIds;
        List<String> allowedStyles;
        List<Integer> finisherItemIds;
        String note;
    }
}
