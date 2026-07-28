package com.ospulse.combat;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

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
        Gson gson = BundledGson.get();
        try (Reader reader = new InputStreamReader(requireResource(anchor, resourcePath), StandardCharsets.UTF_8)) {
            T parsed = gson.fromJson(reader, type);
            if (parsed == null) {
                throw new IllegalStateException("Bundled resource parsed to nothing: " + resourcePath);
            }
            return parsed;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load bundled data from " + resourcePath, e);
        }
    }

    private static InputStream requireResource(Class<?> anchor, String resourcePath) {
        InputStream in = anchor.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Bundled resource not found on classpath: " + resourcePath);
        }
        return in;
    }
}
