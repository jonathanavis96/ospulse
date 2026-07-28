package com.ospulse.combat;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The attack styles each {@link WeaponCategory} offers — a faithful port of
 * weirdgloop {@code osrs-dps-calc}'s {@code getCombatStylesForCategory}
 * ({@code src/utils.ts}). Each entry is a real combat-options style with its
 * damage {@link CombatStyle} and {@link Stance}, so a DPS ranking over the list
 * reproduces the in-game "which style hits hardest here" choice exactly.
 *
 * <p>Two deviations from the upstream, both intentional:
 * <ul>
 *   <li>Styles with no damage type (the Bulwark's "Block") are dropped — they
 *       do no offence, so they never rank.</li>
 *   <li>The upstream appends a synthetic "Manual Cast" magic style to every
 *       weapon (a UI affordance for its manual-cast feature); we do not, since a
 *       melee weapon's list should not sprout a magic option.</li>
 * </ul>
 * Duplicate {@code (type, stance)} pairs collapse via {@link WeaponStyle}'s
 * equality (e.g. a bludgeon's three aggressive-crush styles → one row).
 *
 * <p>Magic stances (Autocast / Defensive Autocast / the salamander's magic
 * "Blaze") map to {@link Stance#STANDARD}; the magic max-hit itself still needs
 * a spell picker (a separate TODO), so callers that want honest numbers filter
 * {@link CombatStyle#MAGIC} out of the ranking for now.
 *
 * <p>The per-category style table itself is bundled data — see {@code
 * /com/ospulse/combat/weapon_styles.json}, in the in-game combat-tab order —
 * loaded once through {@link CombatDataLoader}, the same shared bundled-resource
 * loading path {@link AttackStyleIcons} uses.
 */
public final class WeaponStyles {
    private WeaponStyles() {
    }

    private static final String RESOURCE_PATH = "/com/ospulse/combat/weapon_styles.json";
    private static final Type RAW_STYLES_JSON_TYPE = new TypeToken<Map<String, List<RawStyle>>>() {
    }.getType();

    private static final Map<WeaponCategory, List<WeaponStyle>> BY_CATEGORY = buildByCategory();

    private static Map<WeaponCategory, List<WeaponStyle>> buildByCategory() {
        Map<String, List<RawStyle>> raw = CombatDataLoader.load(WeaponStyles.class, RESOURCE_PATH, RAW_STYLES_JSON_TYPE);
        Map<WeaponCategory, List<WeaponStyle>> parsed = new EnumMap<>(WeaponCategory.class);
        for (Map.Entry<String, List<RawStyle>> categoryEntry : raw.entrySet()) {
            WeaponCategory category;
            try {
                category = WeaponCategory.valueOf(categoryEntry.getKey());
            } catch (IllegalArgumentException e) {
                continue; // unknown category key in the bundled data — treated as "no data"
            }
            List<WeaponStyle> styles = new ArrayList<>();
            for (RawStyle rawStyle : categoryEntry.getValue()) {
                if (rawStyle.name == null || rawStyle.type == null || rawStyle.stance == null) {
                    continue; // malformed entry — treated as "no data"
                }
                styles.add(new WeaponStyle(rawStyle.name, CombatStyle.valueOf(rawStyle.type), Stance.valueOf(rawStyle.stance)));
            }
            parsed.put(category, Collections.unmodifiableList(styles));
        }
        return Collections.unmodifiableMap(parsed);
    }

    /**
     * The distinct, offence-bearing attack styles for a category, in the
     * in-game combat-tab order. Never {@code null}; may be empty (e.g.
     * {@link WeaponCategory#BLASTER}), in which case the caller should fall back
     * to a sensible default (unarmed / a magic placeholder).
     */
    public static List<WeaponStyle> forCategory(WeaponCategory category) {
        List<WeaponStyle> raw = rawStyles(category);
        LinkedHashSet<WeaponStyle> distinct = new LinkedHashSet<>();
        for (WeaponStyle style : raw) {
            if (style.type() != null) {
                distinct.add(style);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(distinct));
    }

    private static List<WeaponStyle> rawStyles(WeaponCategory category) {
        if (category == null) {
            category = WeaponCategory.UNARMED;
        }
        List<WeaponStyle> styles = BY_CATEGORY.get(category);
        return styles != null ? styles : BY_CATEGORY.get(WeaponCategory.UNARMED);
    }

    /** Internal Gson deserialisation shape mirroring one entry of {@code weapon_styles.json}'s per-category arrays. */
    private static final class RawStyle {
        String name;
        String type;
        String stance;
    }
}
