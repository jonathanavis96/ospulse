package com.ospulse.combat;


import java.util.*;

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

    private static final CombatDataLoader.Lazy<MonsterCombatRequirementRepository> INSTANCE =
            new CombatDataLoader.Lazy<>(() -> loadFromResource(RESOURCE_PATH));

    private final Map<String, MonsterCombatRequirement> byLowercaseMonsterName;

    private MonsterCombatRequirementRepository(Map<String, MonsterCombatRequirement> byLowercaseMonsterName) {
        this.byLowercaseMonsterName = Collections.unmodifiableMap(byLowercaseMonsterName);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static MonsterCombatRequirementRepository getInstance() {
        return INSTANCE.get();
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static MonsterCombatRequirementRepository loadFromResource(String resourcePath) {
        RootDto root = CombatDataLoader.parse(MonsterCombatRequirementRepository.class, resourcePath, RootDto.class);
        Map<String, MonsterCombatRequirement> byName = new HashMap<>();
        if (root != null && root.requirements != null) {
            for (ReqDto dto : root.requirements) {
                if (dto.monsters == null || dto.type == null) {
                    continue; // malformed entry — treated as "no data"
                }
                MonsterCombatRequirement.Type type = CombatDataLoader.parseEnum(MonsterCombatRequirement.Type.class, dto.type);
                if (type == null) {
                    continue; // unknown type in the data — skip defensively
                }
                MonsterCombatRequirement requirement;
                if (type == MonsterCombatRequirement.Type.FINISHER) {
                    requirement = MonsterCombatRequirement.finisher(ids(dto.finisherItemIds), dto.note);
                } else if (type == MonsterCombatRequirement.Type.DAMAGE_PENALTY) {
                    requirement = MonsterCombatRequirement.damagePenalty(ids(dto.allowedItemIds),
                            dto.damageMultiplier == null ? 1.0 : dto.damageMultiplier,
                            parseStyles(dto.penalisedStyles), parseStyles(dto.exemptStyles), dto.note);
                } else if (type == MonsterCombatRequirement.Type.DAMAGE_CAP) {
                    requirement = MonsterCombatRequirement.damageCap(
                            dto.maxHitCap == null ? -1 : dto.maxHitCap,
                            dto.maxHitCapWhenCrushHighest == null ? -1 : dto.maxHitCapWhenCrushHighest,
                            ids(dto.allowedItemIds), parseCapByStyle(dto.maxHitCapByStyle), parseCapMode(dto.capMode),
                            dto.note);
                } else {
                    requirement = MonsterCombatRequirement.weaponGate(ids(dto.allowedItemIds), ids(dto.allowedAmmoIds),
                            parseStyles(dto.allowedStyles), dto.note);
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
    }

    /** A mutable copy of an optional id list (empty when absent). */
    private static Set<Integer> ids(List<Integer> raw) {
        return raw == null ? Collections.emptySet() : new HashSet<>(raw);
    }

    /** Parses a style-name list from the curated data, skipping anything unrecognised. */
    private static Set<CombatStyle> parseStyles(List<String> styleNames) {
        Set<CombatStyle> styles = EnumSet.noneOf(CombatStyle.class);
        if (styleNames != null) {
            for (String styleName : styleNames) {
                CombatStyle style = CombatDataLoader.parseEnum(CombatStyle.class, styleName);
                if (style != null) {
                    styles.add(style);
                }
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
            CombatStyle style = CombatDataLoader.parseEnum(CombatStyle.class, entry.getKey());
            if (style != null && entry.getValue() != null) {
                result.put(style, entry.getValue());
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
        MonsterCombatRequirement.CapMode mode = CombatDataLoader.parseEnum(MonsterCombatRequirement.CapMode.class, raw);
        return mode == null ? MonsterCombatRequirement.CapMode.CLAMP : mode;
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
