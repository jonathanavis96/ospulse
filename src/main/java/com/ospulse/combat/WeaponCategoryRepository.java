package com.ospulse.combat;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Resolves a weapon item id → its {@link WeaponCategory} (and thence its
 * {@link WeaponStyle}s) from the bundled {@code weapon_categories.min.json}
 * (a {@code {"<itemId>": "<category>"}} map — see the accompanying README for
 * provenance/regeneration). Loaded once, in-memory.
 *
 * <p>Pure and thread-safe to read from anywhere (including the EDT): the map is
 * static game data with no {@code Client}/{@code ItemManager} dependency, which
 * is what lets both the live worn weapon and a hypothetical not-yet-owned weapon
 * (future optimiser) be looked up the same way. Mirrors
 * {@link MonsterRepository}'s bundled-resource pattern.
 */
public final class WeaponCategoryRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/weapon_categories.min.json";

    private static final CombatDataLoader.Lazy<WeaponCategoryRepository> INSTANCE =
            new CombatDataLoader.Lazy<>(() -> loadFromResource(RESOURCE_PATH));

    private final Map<Integer, WeaponCategory> byItemId;

    private WeaponCategoryRepository(Map<Integer, WeaponCategory> byItemId) {
        this.byItemId = Collections.unmodifiableMap(byItemId);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static WeaponCategoryRepository getInstance() {
        return INSTANCE.get();
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static WeaponCategoryRepository loadFromResource(String resourcePath) {
        Type mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> raw = CombatDataLoader.parse(WeaponCategoryRepository.class, resourcePath, mapType);
        java.util.HashMap<Integer, WeaponCategory> parsed = new java.util.HashMap<>();
        if (raw != null) {
            for (Map.Entry<String, String> e : raw.entrySet()) {
                WeaponCategory category = WeaponCategory.fromDataName(e.getValue());
                if (category == null) {
                    continue; // unknown/blank category — treated as "no data" (caller falls back)
                }
                try {
                    parsed.put(Integer.parseInt(e.getKey().trim()), category);
                } catch (NumberFormatException ignored) {
                    // non-numeric key in the data — skip defensively
                }
            }
        }
        return new WeaponCategoryRepository(parsed);
    }

    public int size() {
        return byItemId.size();
    }

    /**
     * The weapon category for an equipped item id, or {@code null} if the id is
     * unknown/empty (no weapon, or a weapon not in the bundled data). Callers
     * typically fall back to {@link WeaponCategory#UNARMED}.
     */
    public WeaponCategory categoryFor(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        return byItemId.get(itemId);
    }

    /**
     * The ranked-ready attack styles for an equipped weapon id: the styles of
     * its resolved category, or the {@link WeaponCategory#UNARMED} styles when
     * the id is empty/unknown (bare fists). Never {@code null}, never empty.
     */
    public List<WeaponStyle> stylesForItem(int itemId) {
        WeaponCategory category = categoryFor(itemId);
        List<WeaponStyle> styles = WeaponStyles.forCategory(category);
        if (styles.isEmpty()) {
            styles = WeaponStyles.forCategory(WeaponCategory.UNARMED);
        }
        return styles;
    }
}
