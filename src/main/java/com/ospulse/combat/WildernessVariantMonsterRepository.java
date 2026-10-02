package com.ospulse.combat;


import java.util.*;

/**
 * Loads the bundled, hand-curated "both-locations" monster list ({@code
 * /com/ospulse/combat/wilderness_variant_monsters.json}, see the
 * accompanying README for provenance/generation) — monsters that CAN be
 * fought in the Wilderness but are NOT Wilderness-exclusive (see {@link
 * WildernessMonsterRepository} for that set). Each entry pairs the real
 * bundled monster name ({@code baseMonster}, e.g. "Black dragon (Level
 * 227)") with the synthetic, selectable display name ({@code displayName},
 * e.g. "Black dragon (Wilderness)") {@link MonsterRepository} synthesizes a
 * twin {@link Monster} for at load time.
 *
 * <p>This is the director-mandated fix for a real gap: excluding a
 * both-locations monster entirely (the previous stage's approach)
 * silently withheld the revenant-weapon bonus from a real, selectable
 * Wilderness fight (e.g. Black dragon at the Lava Maze Dungeon). Making
 * the Wilderness instance a SEPARATE, explicitly-selected target instead
 * of guessing removes the ambiguity entirely: the player says which one
 * they mean, so nothing is silently assumed or overstated.
 */
public final class WildernessVariantMonsterRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/wilderness_variant_monsters.json";

    private static final CombatDataLoader.Lazy<WildernessVariantMonsterRepository> INSTANCE =
            new CombatDataLoader.Lazy<>(() -> loadFromResource(RESOURCE_PATH));

    private final List<Variant> variants;

    /** One base-monster -> synthetic-Wilderness-twin pairing. */
    public static final class Variant {
        private final String baseMonster;
        private final String displayName;

        Variant(String baseMonster, String displayName) {
            this.baseMonster = baseMonster;
            this.displayName = displayName;
        }

        /** The real bundled monster name this variant is a Wilderness twin of. */
        public String baseMonster() {
            return baseMonster;
        }

        /** The synthetic, selectable display name for the Wilderness twin. */
        public String displayName() {
            return displayName;
        }
    }

    private WildernessVariantMonsterRepository(List<Variant> variants) {
        this.variants = Collections.unmodifiableList(variants);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static WildernessVariantMonsterRepository getInstance() {
        return INSTANCE.get();
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static WildernessVariantMonsterRepository loadFromResource(String resourcePath) {
        RootDto root = CombatDataLoader.parse(WildernessVariantMonsterRepository.class, resourcePath, RootDto.class);
        List<Variant> parsed = new ArrayList<>();
        if (root != null && root.variants != null) {
            for (VariantDto dto : root.variants) {
                if (dto.baseMonster == null || dto.baseMonster.isEmpty()
                        || dto.displayName == null || dto.displayName.isEmpty()) {
                    continue; // malformed entry - treated as "no data"
                }
                parsed.add(new Variant(dto.baseMonster, dto.displayName));
            }
        }
        return new WildernessVariantMonsterRepository(parsed);
    }

    public int size() {
        return variants.size();
    }

    /** Every curated base-monster -> synthetic-display-name pairing. */
    public List<Variant> all() {
        return variants;
    }

    /** Internal Gson deserialisation shape mirroring {@code wilderness_variant_monsters.json}'s top-level object. */
    private static final class RootDto {
        List<VariantDto> variants;
    }

    /** Internal Gson deserialisation shape for one entry of the {@code variants} array. */
    private static final class VariantDto {
        String baseMonster;
        String displayName;
    }
}
