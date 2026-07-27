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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the bundled, hand-curated "don't forget" consumables/gear reminder
 * table ({@code /com/ospulse/combat/monster_consumables.json}) and serves
 * in-memory lookups by monster name.
 *
 * <p>A reminder is advisory only — e.g. "Zulrah poisons you — bring
 * antivenom" — and is never fed into {@link
 * com.ospulse.combat.optimizer.GearOptimizer}: it is a pure keyed map lookup
 * at render time, entirely outside the optimiser search, so it cannot slow it
 * down. {@code GearSection} (via {@code
 * com.ospulse.ui.sections.gear.ConsumablesReminderPanel}) renders the note;
 * this repository just exposes the curated data.
 *
 * <p>Matches by monster NAME, exact-then-base-name — mirroring {@link
 * MonsterCombatRequirementRepository#forMonster}, NOT {@link
 * MonsterGearOverrideRepository}'s exact-only matching — so {@code "Zulrah"}
 * resolves {@code "Zulrah (Serpentine)"}/{@code "(Magma)"}/{@code
 * "(Tanzanite)"} for free while a phase-scoped entry can still key on an
 * exact dataset name. Mirrors {@link MonsterCombatRequirementRepository}'s
 * bundled-resource singleton pattern.
 */
public final class MonsterConsumablesRepository {
    private static final String RESOURCE_PATH = "/com/ospulse/combat/monster_consumables.json";

    private static volatile MonsterConsumablesRepository instance;

    private final Map<String, MonsterConsumablesReminder> byLowercaseMonsterName;

    private MonsterConsumablesRepository(Map<String, MonsterConsumablesReminder> byLowercaseMonsterName) {
        this.byLowercaseMonsterName = Collections.unmodifiableMap(byLowercaseMonsterName);
    }

    /** Shared, lazily-initialised singleton loaded from the bundled resource. */
    public static MonsterConsumablesRepository getInstance() {
        MonsterConsumablesRepository result = instance;
        if (result == null) {
            synchronized (MonsterConsumablesRepository.class) {
                result = instance;
                if (result == null) {
                    instance = result = loadFromResource(RESOURCE_PATH);
                }
            }
        }
        return result;
    }

    /** Loads a repository from an arbitrary classpath resource (mainly for tests). */
    static MonsterConsumablesRepository loadFromResource(String resourcePath) {
        Gson gson = BundledGson.get();
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            RootDto root = gson.fromJson(reader, RootDto.class);
            Map<String, MonsterConsumablesReminder> byName = new HashMap<>();
            if (root != null && root.reminders != null) {
                for (ReminderDto dto : root.reminders) {
                    if (dto.monsters == null || dto.note == null) {
                        continue; // malformed entry — treated as "no data"
                    }
                    Set<Integer> equipmentItemIds = dto.equipmentItemIds == null
                        ? Collections.emptySet()
                        : new LinkedHashSet<>(dto.equipmentItemIds);
                    Set<Integer> consumableItemIds = dto.consumableItemIds == null
                        ? Collections.emptySet()
                        : new LinkedHashSet<>(dto.consumableItemIds);
                    MonsterConsumablesReminder reminder =
                        new MonsterConsumablesReminder(dto.note, equipmentItemIds, consumableItemIds);
                    for (String monsterName : dto.monsters) {
                        if (monsterName == null || monsterName.isEmpty()) {
                            continue;
                        }
                        byName.put(monsterName.toLowerCase(Locale.ROOT), reminder);
                    }
                }
            }
            return new MonsterConsumablesRepository(byName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load monster consumables reminder data from " + resourcePath, e);
        }
    }

    private static InputStream requireResource(String resourcePath) {
        InputStream in = MonsterConsumablesRepository.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    public int size() {
        return byLowercaseMonsterName.size();
    }

    /**
     * The curated consumables reminder for the given monster name, or empty
     * if this monster has none. Tries the full lowercased name first, then
     * falls back to {@link MonsterNameKey#baseName} — the picker hands us
     * dataset names like {@code "Zulrah (Serpentine)"}.
     */
    public Optional<MonsterConsumablesReminder> forMonster(String monsterName) {
        if (monsterName == null) {
            return Optional.empty();
        }
        MonsterConsumablesReminder exact = byLowercaseMonsterName.get(monsterName.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(byLowercaseMonsterName.get(MonsterNameKey.baseName(monsterName)));
    }

    /**
     * Every curated key, lowercased, exactly as stored in the lookup map.
     * Exposed so the data-integrity test can assert each one still resolves
     * against the bundled monster data.
     */
    Set<String> curatedKeys() {
        return Collections.unmodifiableSet(byLowercaseMonsterName.keySet());
    }

    /** Every curated entry, for dataset-wide integrity checks (e.g. every equipment id resolves). */
    Collection<MonsterConsumablesReminder> allReminders() {
        return Collections.unmodifiableCollection(byLowercaseMonsterName.values());
    }

    /** Exposes the name normalisation {@link #forMonster} falls back to. */
    static String baseNameOf(String monsterName) {
        return MonsterNameKey.baseName(monsterName);
    }

    /** Internal Gson deserialisation shape mirroring {@code monster_consumables.json}'s top-level object. */
    private static final class RootDto {
        List<ReminderDto> reminders;
    }

    /** Internal Gson deserialisation shape mirroring one entry of the {@code reminders} array. */
    private static final class ReminderDto {
        List<String> monsters;
        String note;
        List<Integer> equipmentItemIds;
        List<Integer> consumableItemIds;
    }
}
