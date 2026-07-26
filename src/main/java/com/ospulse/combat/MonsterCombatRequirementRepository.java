package com.ospulse.combat;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
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
                    } else if (type == MonsterCombatRequirement.Type.DAMAGE_PENALTY) {
                        requirement = MonsterCombatRequirement.damagePenalty(
                                dto.allowedItemIds == null ? Collections.emptySet() : new HashSet<>(dto.allowedItemIds),
                                dto.damageMultiplier == null ? 1.0 : dto.damageMultiplier,
                                parseStyles(dto.penalisedStyles), parseStyles(dto.exemptStyles), dto.note);
                    } else if (type == MonsterCombatRequirement.Type.DAMAGE_CAP) {
                        requirement = MonsterCombatRequirement.damageCap(
                                dto.maxHitCap == null ? -1 : dto.maxHitCap,
                                dto.maxHitCapWhenCrushHighest == null ? -1 : dto.maxHitCapWhenCrushHighest,
                                dto.allowedItemIds == null ? Collections.emptySet() : new HashSet<>(dto.allowedItemIds),
                                parseCapByStyle(dto.maxHitCapByStyle),
                                parseCapMode(dto.capMode),
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

    /** Parses a style-name list from the curated data, skipping anything unrecognised. */
    private static Set<CombatStyle> parseStyles(List<String> styleNames) {
        Set<CombatStyle> styles = EnumSet.noneOf(CombatStyle.class);
        if (styleNames == null) {
            return styles;
        }
        for (String styleName : styleNames) {
            if (styleName == null) {
                continue;
            }
            try {
                styles.add(CombatStyle.valueOf(styleName.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // unknown style name in the data — skip defensively
            }
        }
        return styles;
    }

    /**
     * Parses the optional per-style cap map (JSON keys are style names, e.g.
     * {@code "RANGED"}), skipping anything unrecognised or null-valued. Gson
     * leaves the field {@code null} when it is absent from the source JSON —
     * every existing {@code DAMAGE_CAP} entry lacks this key, so this must
     * (and does) return an empty map for them, leaving {@link
     * TargetDamageRule#maxHitCapFor} to fall back to the flat/crush-highest
     * value exactly as before.
     */
    private static Map<CombatStyle, Integer> parseCapByStyle(Map<String, Integer> raw) {
        Map<CombatStyle, Integer> result = new EnumMap<>(CombatStyle.class);
        if (raw == null) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                result.put(CombatStyle.valueOf(entry.getKey().trim().toUpperCase(Locale.ROOT)), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // unknown style name in the data — skip defensively
            }
        }
        return result;
    }

    /**
     * Parses the optional cap-mode field, defaulting to {@link
     * MonsterCombatRequirement.CapMode#CLAMP} when absent (Gson leaves it
     * {@code null}) or unrecognised — every entry written before {@code
     * CapMode} existed has no {@code capMode} key and must keep clamp
     * semantics.
     */
    private static MonsterCombatRequirement.CapMode parseCapMode(String raw) {
        if (raw == null) {
            return MonsterCombatRequirement.CapMode.CLAMP;
        }
        try {
            return MonsterCombatRequirement.CapMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MonsterCombatRequirement.CapMode.CLAMP;
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
        MonsterCombatRequirement exact = byLowercaseMonsterName.get(monsterName.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return Optional.of(exact);
        }
        // The picker hands us dataset names like "Kurask (Normal)" — fall back to the base name.
        return Optional.ofNullable(byLowercaseMonsterName.get(MonsterNameKey.baseName(monsterName)));
    }

    /**
     * Every curated key, lowercased, exactly as stored in the lookup map. Exposed so
     * the data-integrity test can assert each one still resolves against the bundled
     * monster data — a typo here silently disables a gate rather than failing loudly.
     */
    Set<String> curatedKeys() {
        return Collections.unmodifiableSet(byLowercaseMonsterName.keySet());
    }

    /** Every curated entry, for dataset-wide integrity checks. */
    Collection<MonsterCombatRequirement> allRequirements() {
        return Collections.unmodifiableCollection(byLowercaseMonsterName.values());
    }

    /** Exposes the name normalisation {@link #forMonster} falls back to. */
    static String baseNameOf(String monsterName) {
        return MonsterNameKey.baseName(monsterName);
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
        Double damageMultiplier;
        List<String> penalisedStyles;
        List<String> exemptStyles;
        Integer maxHitCap;
        Integer maxHitCapWhenCrushHighest;
        Map<String, Integer> maxHitCapByStyle;
        String capMode;
    }
}
