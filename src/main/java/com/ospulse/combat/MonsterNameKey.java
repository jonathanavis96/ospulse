package com.ospulse.combat;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises a monster display name into a lookup key for the curated
 * requirement/override tables.
 *
 * <p>The bundled monster dataset names most monsters with a trailing
 * non-numeric variant marker — {@code "Kurask (Normal)"}, {@code "Kurask (Big)"},
 * {@code "Gargoyle (Basement)"}, {@code "Gargoyle (Upstairs)"},
 * {@code "Turoth (Baby)"} — and never the bare {@code "Kurask"}. The curated
 * tables are keyed on the base name, so callers try the exact (lowercased) name
 * first and fall back to {@link #baseName(String)} when that misses. This keeps
 * verbatim names that carry no marker (e.g. {@code "King kurask"},
 * {@code "Marble gargoyle"}) matching directly while every {@code "(…)"} variant
 * resolves to its base requirement.
 *
 * <p>Unlike {@code MonsterRepository.dedupeKeyName} (which strips only numeric
 * {@code "(N)"}/{@code "(label, N)"} combat-instance markers), this strips ANY
 * single trailing parenthetical — safe here precisely because it is only used as
 * a fallback against a small curated key set, so a stripped name can only match
 * a monster we deliberately authored.
 */
final class MonsterNameKey
{
    private MonsterNameKey() {}

    /** A single trailing " (...)" group (no nested parens), e.g. "Kurask (Normal)" -> "Kurask". */
    private static final Pattern TRAILING_PARENTHETICAL = Pattern.compile("\\s*\\([^()]*\\)\\s*$");

    /** Lowercased name with a single trailing "(…)" variant marker removed. */
    static String baseName(String monsterName)
    {
        String stripped = TRAILING_PARENTHETICAL.matcher(monsterName).replaceAll("").trim();
        return stripped.toLowerCase(Locale.ROOT);
    }
}
