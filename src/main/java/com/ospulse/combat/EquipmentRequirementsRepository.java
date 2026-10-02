package com.ospulse.combat;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Resolves an equipment item id → its skill EQUIP requirements (e.g. Dragon
 * scimitar → {attack: 60}) from the bundled, clean-room
 * {@code equipment_requirements.min.json} (read straight from the OSRS cache's
 * item config params 434-437 — see the accompanying README for provenance /
 * regeneration). This is the licence-free source the gear optimiser uses to
 * avoid recommending gear a player cannot equip at their current levels.
 *
 * <p>Pure and thread-safe to read from anywhere (including the EDT): static game
 * data with no {@code Client}/{@code ItemManager} dependency, so a not-yet-owned
 * item can be checked the same way. Mirrors {@link EquipmentStatsRepository}'s
 * bundled-resource singleton pattern.
 *
 * <p>Skill keys are lowercase names ({@code attack}, {@code defence},
 * {@code strength}, {@code hitpoints}, {@code ranged}, {@code prayer},
 * {@code magic}, {@code agility}, {@code slayer}, …) — the same lowercase form
 * the extractor writes.
 */
public final class EquipmentRequirementsRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/equipment_requirements.min.json";

    private static final CombatDataLoader.Lazy<EquipmentRequirementsRepository> INSTANCE =
            new CombatDataLoader.Lazy<>(() -> loadFromResource(RESOURCE_PATH));

    /** item id → (skill name → required level). Inner maps are unmodifiable. */
    private final Map<Integer, Map<String, Integer>> byItemId;

    private EquipmentRequirementsRepository(Map<Integer, Map<String, Integer>> byItemId) {
        this.byItemId = Collections.unmodifiableMap(byItemId);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static EquipmentRequirementsRepository getInstance() {
        return INSTANCE.get();
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static EquipmentRequirementsRepository loadFromResource(String resourcePath) {
        Type mapType = new TypeToken<Map<String, Map<String, Number>>>() {
        }.getType();
        Map<String, Map<String, Number>> raw = CombatDataLoader.parse(EquipmentRequirementsRepository.class, resourcePath, mapType);
        HashMap<Integer, Map<String, Integer>> parsed = new HashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Map<String, Number>> e : raw.entrySet()) {
                Map<String, Number> reqs = e.getValue();
                if (reqs == null || reqs.isEmpty()) {
                    continue;
                }
                int itemId;
                try {
                    itemId = Integer.parseInt(e.getKey().trim());
                } catch (NumberFormatException ignored) {
                    continue; // non-numeric key — skip defensively
                }
                Map<String, Integer> levels = new LinkedHashMap<>();
                for (Map.Entry<String, Number> r : reqs.entrySet()) {
                    if (r.getKey() != null && r.getValue() != null) {
                        levels.put(r.getKey().toLowerCase(Locale.ROOT), r.getValue().intValue());
                    }
                }
                if (!levels.isEmpty()) {
                    parsed.put(itemId, Collections.unmodifiableMap(levels));
                }
            }
        }
        return new EquipmentRequirementsRepository(parsed);
    }

    public int size() {
        return byItemId.size();
    }

    /**
     * The skill equip requirements for an item id as an unmodifiable
     * (skill name → level) map, or {@code null} if the id is empty
     * ({@code <= 0}) or has no requirements in the data (i.e. equippable at
     * level 1 / no skill gate).
     */
    public Map<String, Integer> requirementsFor(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        return byItemId.get(itemId);
    }

    /**
     * True if a player with the given base skill levels ({@code skill name →
     * level}, lowercase keys) can equip {@code itemId}. Fail-open: an item with
     * no requirement data is always allowed, and a requirement whose skill is
     * ABSENT from {@code baseLevels} is assumed met (so an incomplete level map
     * never hides otherwise-wearable gear — callers should populate at least the
     * combat skills). Only a KNOWN level below the requirement blocks the item.
     */
    public boolean canEquip(int itemId, Map<String, Integer> baseLevels) {
        Map<String, Integer> reqs = requirementsFor(itemId);
        if (reqs == null || reqs.isEmpty()) {
            return true;
        }
        if (baseLevels == null || baseLevels.isEmpty()) {
            return true; // no level info supplied → don't over-filter
        }
        for (Map.Entry<String, Integer> req : reqs.entrySet()) {
            Integer have = baseLevels.get(req.getKey());
            if (have != null && have < req.getValue()) {
                return false;
            }
        }
        return true;
    }
}
