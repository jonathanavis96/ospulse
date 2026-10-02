package com.ospulse.combat;

import com.google.gson.Gson;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Shared bundled-JSON-resource loading boilerplate for the small pure-data
 * combat tables ({@link AttackStyleIcons}, {@link WeaponStyles}) that don't
 * warrant their own singleton repository class — mirrors the loading half of
 * {@link WeaponCategoryRepository}/{@code MonsterConsumablesRepository}
 * (open resource, parse through {@link BundledGson}, fail loudly on a
 * missing/unreadable resource) without duplicating it per caller.
 */
final class CombatDataLoader {
    private CombatDataLoader() {
    }

    /** Parses {@code resourcePath} (relative to {@code anchor}'s classloader) as {@code type}, or throws. */
    static <T> T load(Class<?> anchor, String resourcePath, Type type) {
        T parsed = parse(anchor, resourcePath, type);
        if (parsed == null) {
            throw new IllegalStateException("Bundled resource parsed to nothing: " + resourcePath);
        }
        return parsed;
    }

    /**
     * Parses {@code resourcePath} as {@code type}; {@code null} when the JSON is
     * empty. Throws when the resource is missing or unreadable.
     */
    static <T> T parse(Class<?> anchor, String resourcePath, Type type) {
        Gson gson = BundledGson.get();
        try (Reader reader = new InputStreamReader(requireResource(anchor, resourcePath), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load bundled data from " + resourcePath, e);
        }
    }

    /** Case-insensitive, whitespace-trimmed enum lookup; {@code null} for a null or unknown name. */
    static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static InputStream requireResource(Class<?> anchor, String resourcePath) {
        InputStream in = anchor.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }

    /**
     * Thread-safe lazy singleton holder (double-checked locking): the value is
     * built on first {@link #get()}; a failed build is retried on the next call.
     */
    static final class Lazy<T> {
        private final Supplier<T> factory;
        private volatile T value;

        Lazy(Supplier<T> factory) {
            this.factory = factory;
        }

        T get() {
            T result = value;
            if (result == null) {
                synchronized (this) {
                    result = value;
                    if (result == null) {
                        value = result = factory.get();
                    }
                }
            }
            return result;
        }
    }
}
