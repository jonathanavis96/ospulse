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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves an equipment item id → its offensive/defensive bonuses from the
 * bundled, clean-room {@code equipment_stats.min.json} (read straight from the
 * OSRS cache — see the accompanying README for provenance/regeneration). This is
 * the licence-free replacement for RuneLite's wiki-derived
 * {@code ItemManager.getItemStats()} numeric data: the DPS engine sources every
 * numeric bonus from here so a future non-RuneLite (web) build can share the exact
 * same data without the CC-BY-NC-SA wiki dataset.
 *
 * <p>Pure and thread-safe to read from anywhere (including the EDT): static game
 * data with no {@code Client}/{@code ItemManager} dependency, which also lets a
 * not-yet-owned item (future optimiser) be looked up the same way. Mirrors
 * {@link WeaponCategoryRepository}/{@code MonsterRepository}'s bundled-resource
 * pattern.
 *
 * <p><b>Two-handedness is deliberately NOT here</b> — no cache param reliably
 * encodes it (best calibration was random), so the plugin still reads {@code
 * isTwoHanded} from RuneLite at runtime; the row's trailing slot hint is dropped.
 */
public final class EquipmentStatsRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/equipment_stats.min.json";

    // Column order of each row in the min.json, mirroring the extractor's FIELDS:
    // 0 astab 1 aslash 2 acrush 3 amagic 4 arange 5 dstab 6 dslash 7 dcrush
    // 8 dmagic 9 drange 10 str 11 rstr 12 mdmg 13 prayer 14 speed 15 slotId(hint).
    private static final int I_MDMG = 12;
    private static final int I_SPEED = 14;
    private static final int MIN_ROW_LEN = 15; // 15 stats; a trailing slot hint (index 15) is optional/ignored.

    // The file stores magic damage in tenths of a percent (Kodai wand = 150),
    // but the DPS engine expects whole percent (CombatMath applies 1 + mdmg/100),
    // so scale it down on load. Ranged strength / all other bonuses are 1:1.
    private static final double MDMG_TENTHS_TO_PERCENT = 10.0;

    private static volatile EquipmentStatsRepository instance;

    private final Map<Integer, Stats> byItemId;

    private EquipmentStatsRepository(Map<Integer, Stats> byItemId) {
        this.byItemId = Collections.unmodifiableMap(byItemId);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static EquipmentStatsRepository getInstance() {
        EquipmentStatsRepository result = instance;
        if (result == null) {
            synchronized (EquipmentStatsRepository.class) {
                result = instance;
                if (result == null) {
                    instance = result = loadFromResource(RESOURCE_PATH);
                }
            }
        }
        return result;
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static EquipmentStatsRepository loadFromResource(String resourcePath) {
        Gson gson = new Gson();
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, int[]>>() {
            }.getType();
            Map<String, int[]> raw = gson.fromJson(reader, mapType);
            HashMap<Integer, Stats> parsed = new HashMap<>();
            if (raw != null) {
                for (Map.Entry<String, int[]> e : raw.entrySet()) {
                    int[] row = e.getValue();
                    if (row == null || row.length < MIN_ROW_LEN) {
                        continue; // malformed row — treated as "no data" (caller falls back)
                    }
                    int itemId;
                    try {
                        itemId = Integer.parseInt(e.getKey().trim());
                    } catch (NumberFormatException ignored) {
                        continue; // non-numeric key in the data — skip defensively
                    }
                    parsed.put(itemId, Stats.fromRow(row));
                }
            }
            return new EquipmentStatsRepository(parsed);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load equipment stats data from " + resourcePath, e);
        }
    }

    private static InputStream requireResource(String resourcePath) {
        InputStream in = EquipmentStatsRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        return byItemId.size();
    }

    /**
     * The cache-derived bonuses for an item id, or {@code null} if the id is
     * empty ({@code <= 0}) or absent from the data. Callers fall back to their
     * previous source (e.g. RuneLite {@code getItemStats}) on {@code null}.
     */
    public Stats statsFor(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        return byItemId.get(itemId);
    }

    /**
     * One item's offensive/defensive bonuses in the DPS engine's units (magic
     * damage as whole percent). Immutable. Mirrors {@code ItemEquipmentStats}'s
     * numeric fields, minus {@code isTwoHanded} (not cache-derivable — see class
     * javadoc).
     */
    public static final class Stats {
        private final int astab;
        private final int aslash;
        private final int acrush;
        private final int amagic;
        private final int arange;
        private final int dstab;
        private final int dslash;
        private final int dcrush;
        private final int dmagic;
        private final int drange;
        private final int str;
        private final int rstr;
        private final double mdmg;
        private final int prayer;
        private final int aspeed;

        private Stats(int astab, int aslash, int acrush, int amagic, int arange,
                      int dstab, int dslash, int dcrush, int dmagic, int drange,
                      int str, int rstr, double mdmg, int prayer, int aspeed) {
            this.astab = astab;
            this.aslash = aslash;
            this.acrush = acrush;
            this.amagic = amagic;
            this.arange = arange;
            this.dstab = dstab;
            this.dslash = dslash;
            this.dcrush = dcrush;
            this.dmagic = dmagic;
            this.drange = drange;
            this.str = str;
            this.rstr = rstr;
            this.mdmg = mdmg;
            this.prayer = prayer;
            this.aspeed = aspeed;
        }

        static Stats fromRow(int[] r) {
            return new Stats(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9],
                    r[10], r[11], r[I_MDMG] / MDMG_TENTHS_TO_PERCENT, r[13], r[I_SPEED]);
        }

        public int astab() {
            return astab;
        }

        public int aslash() {
            return aslash;
        }

        public int acrush() {
            return acrush;
        }

        public int amagic() {
            return amagic;
        }

        public int arange() {
            return arange;
        }

        public int dstab() {
            return dstab;
        }

        public int dslash() {
            return dslash;
        }

        public int dcrush() {
            return dcrush;
        }

        public int dmagic() {
            return dmagic;
        }

        public int drange() {
            return drange;
        }

        public int str() {
            return str;
        }

        public int rstr() {
            return rstr;
        }

        /** Magic damage as a whole percent (e.g. 15.0 for +15%), already scaled from the file's tenths. */
        public double mdmg() {
            return mdmg;
        }

        public int prayer() {
            return prayer;
        }

        /** Weapon attack speed in ticks; {@code 0} means "derive from weapon category" (powered staves etc.). */
        public int aspeed() {
            return aspeed;
        }
    }
}
