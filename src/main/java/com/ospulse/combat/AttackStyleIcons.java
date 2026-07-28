package com.ospulse.combat;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps a {@link WeaponCategory} + {@link WeaponStyle} to the NATIVE in-game
 * Combat Options sprite id that OSRS itself draws on that weapon-type's
 * attack-style buttons — {@code net.runelite.api.gameval.SpriteID.Combaticons}
 * / {@code Combaticons2} / {@code Combaticons3} (verified 2026-07-03 against
 * {@code runelite-api-1.12.31.1.jar}: e.g. {@code AXE_CHOP}, {@code SWORD_SLASH},
 * {@code BOW_RAPID}, {@code CLAWS_LUNGE}).
 *
 * <p><b>Why not RuneLite's own {@code attackstyles} plugin:</b> that core
 * plugin (see {@code net.runelite.client.plugins.attackstyles.AttackStylesPlugin})
 * only shows/hides the game's own combat-options widgets and reads style
 * NAMES from {@code EnumID.WEAPON_STYLES} — it never draws icons itself and
 * ships no sprite-id table; the actual icons are painted by the game client's
 * own interface script directly from each weapon's struct data, which is not
 * exposed to plugins as a lookup. This class is therefore a hand-verified,
 * pure-data port of the pertinent sprite ids (mirroring how {@link WeaponStyles}
 * itself already ports weirdgloop's {@code getCombatStylesForCategory}),
 * keyed by the same {@code (WeaponCategory, style name)} this plugin's
 * {@link WeaponStyles} already produces — so the mapping is exact for every
 * category with a genuinely distinct native icon, and falls back to the
 * closest same-damage-type sprite where OSRS itself reuses one weapon type's
 * icon set for another (see the fallback notes below).
 *
 * <p>The category/style/spriteId table itself is bundled data — see {@code
 * /com/ospulse/combat/attack_style_icons.json} — loaded once through {@link
 * CombatDataLoader}, the same shared bundled-resource loading path {@link
 * WeaponStyles} uses. A handful of entries in that table are deliberate
 * same-icon reuses rather than genuinely distinct native sprites, because OSRS
 * itself has no dedicated icon for the style:
 * <ul>
 *   <li>{@code TWO_HANDED_SWORD} "Smash" reuses the mace Pound icon (no
 *       dedicated 2h-sword "Smash" sprite; nearest crush icon).</li>
 *   <li>{@code WHIP} "Deflect" reuses "Lash" (OSRS has no dedicated "Deflect"
 *       combat icon; the whip's own Lash is closest).</li>
 *   <li>{@code BULWARK} "Pummel" reuses the mace Pummel icon (no dedicated
 *       bulwark sprite; nearest crush icon).</li>
 *   <li>{@code FLAIL} "Chop" reuses the sword Chop icon (no dedicated flail
 *       sprite; nearest slash icon).</li>
 *   <li>{@code GUN} "Kick" reuses the unarmed Kick icon (no dedicated gun
 *       sprite).</li>
 *   <li>{@code THROWN} reuses the {@code BOW} icons wholesale (no dedicated
 *       thrown-weapon sprite set; nearest ranged icons).</li>
 * </ul>
 */
public final class AttackStyleIcons {

    private static final String RESOURCE_PATH = "/com/ospulse/combat/attack_style_icons.json";
    private static final Type BY_CATEGORY_JSON_TYPE = new TypeToken<Map<String, Map<String, Integer>>>() {
    }.getType();

    private AttackStyleIcons() {
    }

    private static final Map<WeaponCategory, Map<String, Integer>> BY_CATEGORY = buildByCategory();

    private static Map<WeaponCategory, Map<String, Integer>> buildByCategory() {
        Map<String, Map<String, Integer>> raw = CombatDataLoader.load(
            AttackStyleIcons.class, RESOURCE_PATH, BY_CATEGORY_JSON_TYPE);
        Map<WeaponCategory, Map<String, Integer>> parsed = new EnumMap<>(WeaponCategory.class);
        for (Map.Entry<String, Map<String, Integer>> categoryEntry : raw.entrySet()) {
            WeaponCategory category;
            try {
                category = WeaponCategory.valueOf(categoryEntry.getKey());
            } catch (IllegalArgumentException e) {
                continue; // unknown category key in the bundled data — treated as "no data"
            }
            parsed.put(category, Collections.unmodifiableMap(new HashMap<>(categoryEntry.getValue())));
        }
        return Collections.unmodifiableMap(parsed);
    }

    /**
     * The native Combat Options sprite id for {@code category}'s {@code styleName}
     * (as produced by {@link WeaponStyles#forCategory}), or a same-damage-type
     * generic fallback (see {@link #genericSpriteFor}) if this exact
     * category/name pair has no bundled entry (e.g. an unmapped category).
     * Never returns 0/invalid — always resolves to SOME reasonable sprite id.
     */
    public static int spriteIdFor(WeaponCategory category, WeaponStyle style) {
        if (category != null && style != null) {
            Map<String, Integer> byName = BY_CATEGORY.get(category);
            if (byName != null) {
                Integer id = byName.get(style.name());
                if (id != null) {
                    return id;
                }
            }
        }
        return style == null ? 249 /* UNARMED_BLOCK */ : genericSpriteFor(style);
    }

    /** A same-damage-type/stance generic icon for a style with no bundled per-category entry. */
    private static int genericSpriteFor(WeaponStyle style) {
        switch (style.type()) {
            case STAB:
                return 240; // SWORD_STAB
            case SLASH:
                return 238; // SWORD_SLASH
            case CRUSH:
                return 246; // MACE_POUND
            case RANGED:
                return 268; // BOW_ACCURATE
            case MAGIC:
                return 263; // MAGIC_ACCURATE
            default:
                return 249; // UNARMED_BLOCK
        }
    }
}
