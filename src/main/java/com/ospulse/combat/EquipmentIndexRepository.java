package com.ospulse.combat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves an equippable item id &lt;-&gt; its display name and
 * {@code EquipmentInventorySlot} ordinal, from the bundled
 * {@code equipment_index.min.json} (see that resource's README for
 * provenance). This is the name/slot half of the item-search index the
 * Phase 2 what-if swap picker and the Phase 3 optimiser's candidate pool
 * both need; {@link EquipmentStatsRepository} supplies the numeric bonuses
 * for the same id space (every id here is guaranteed to also resolve there —
 * see the README).
 *
 * <p>Pure and thread-safe to read from anywhere (including the EDT): static
 * bundled data, no {@code Client}/{@code ItemManager} dependency. Mirrors
 * {@link EquipmentStatsRepository}/{@link WeaponCategoryRepository}'s
 * bundled-resource singleton pattern.
 */
public final class EquipmentIndexRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/equipment_index.min.json";

    private static volatile EquipmentIndexRepository instance;

    private final List<Entry> entries;
    private final Map<Integer, Entry> byItemId;

    private EquipmentIndexRepository(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
        Map<Integer, Entry> byId = new HashMap<>();
        for (Entry e : entries) {
            byId.put(e.itemId(), e);
        }
        this.byItemId = Collections.unmodifiableMap(byId);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static EquipmentIndexRepository getInstance() {
        EquipmentIndexRepository result = instance;
        if (result == null) {
            synchronized (EquipmentIndexRepository.class) {
                result = instance;
                if (result == null) {
                    instance = result = loadFromResource(RESOURCE_PATH);
                }
            }
        }
        return result;
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static EquipmentIndexRepository loadFromResource(String resourcePath) {
        Gson gson = new Gson();
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, JsonArray>>() {
            }.getType();
            Map<String, JsonArray> raw = gson.fromJson(reader, mapType);
            List<Entry> parsed = new ArrayList<>();
            if (raw != null) {
                for (Map.Entry<String, JsonArray> e : raw.entrySet()) {
                    JsonArray row = e.getValue();
                    if (row == null || row.size() < 2) {
                        continue; // malformed row — treated as "no data"
                    }
                    int itemId;
                    try {
                        itemId = Integer.parseInt(e.getKey().trim());
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    JsonElement nameEl = row.get(0);
                    JsonElement slotEl = row.get(1);
                    if (nameEl == null || nameEl.isJsonNull() || slotEl == null || slotEl.isJsonNull()) {
                        continue;
                    }
                    boolean isTwoHanded = row.size() > 2 && !row.get(2).isJsonNull() && row.get(2).getAsBoolean();
                    parsed.add(new Entry(itemId, nameEl.getAsString(), slotEl.getAsInt(), isTwoHanded));
                }
            }
            return new EquipmentIndexRepository(parsed);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load equipment index data from " + resourcePath, e);
        }
    }

    private static InputStream requireResource(String resourcePath) {
        InputStream in = EquipmentIndexRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        return entries.size();
    }

    /** The name+slot entry for an item id, or {@code null} if unknown/unindexed. */
    public Entry entryFor(int itemId) {
        return byItemId.get(itemId);
    }

    /**
     * Every indexed item that fits the given {@code EquipmentInventorySlot}
     * ordinal, in file order. Never {@code null}; empty if the slot ordinal
     * indexes nothing (e.g. one of the internal-only ordinals).
     */
    public List<Entry> forSlot(int slotOrdinal) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.slotOrdinal() == slotOrdinal) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Case-insensitive substring search over a slot's items by display name,
     * empty query returns every item in the slot (matching
     * {@code MonsterRepository.search}'s convention).
     */
    public List<Entry> searchSlot(int slotOrdinal, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.slotOrdinal() == slotOrdinal
                    && (needle.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(needle))) {
                out.add(e);
            }
        }
        return out;
    }

    /** One equippable item's display name, equipment-slot ordinal and two-handedness. Immutable. */
    public static final class Entry {
        private final int itemId;
        private final String name;
        private final int slotOrdinal;
        private final boolean twoHanded;

        Entry(int itemId, String name, int slotOrdinal, boolean twoHanded) {
            this.itemId = itemId;
            this.name = name;
            this.slotOrdinal = slotOrdinal;
            this.twoHanded = twoHanded;
        }

        public int itemId() {
            return itemId;
        }

        public String name() {
            return name;
        }

        /** {@code net.runelite.api.EquipmentInventorySlot} ordinal this item is worn in. */
        public int slotOrdinal() {
            return slotOrdinal;
        }

        /**
         * True for a two-handed weapon (weapon-slot entries only — always
         * {@code false} for every other slot). Lets the EDT-only what-if
         * picker enforce shield exclusivity without an {@code ItemManager}
         * call (see the resource README).
         */
        public boolean isTwoHanded() {
            return twoHanded;
        }
    }
}
