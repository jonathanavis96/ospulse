package com.ospulse.combat;

import java.util.EnumMap;
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
 * icon set for another (see per-entry comments below).
 */
public final class AttackStyleIcons {

    private AttackStyleIcons() {
    }

    private static final Map<WeaponCategory, Map<String, Integer>> BY_CATEGORY = new EnumMap<>(WeaponCategory.class);

    private static void put(WeaponCategory category, String styleName, int spriteId) {
        BY_CATEGORY.computeIfAbsent(category, c -> new java.util.HashMap<>()).put(styleName, spriteId);
    }

    static {
        // ---- exact per-category native icons -------------------------------------------------
        put(WeaponCategory.AXE, "Chop", SpriteConst.AXE_CHOP);
        put(WeaponCategory.AXE, "Hack", SpriteConst.AXE_HACK);
        put(WeaponCategory.AXE, "Smash", SpriteConst.AXE_SMASH);
        put(WeaponCategory.AXE, "Block", SpriteConst.AXE_BLOCK);

        put(WeaponCategory.TWO_HANDED_SWORD, "Chop", SpriteConst.SWORD_CHOP);
        put(WeaponCategory.TWO_HANDED_SWORD, "Slash", SpriteConst.SWORD_SLASH);
        put(WeaponCategory.TWO_HANDED_SWORD, "Smash", SpriteConst.MACE_POUND); // no dedicated 2h-sword "Smash" sprite; nearest crush icon
        put(WeaponCategory.TWO_HANDED_SWORD, "Block", SpriteConst.SWORD_BLOCK);

        put(WeaponCategory.SLASH_SWORD, "Chop", SpriteConst.SWORD_CHOP);
        put(WeaponCategory.SLASH_SWORD, "Slash", SpriteConst.SWORD_SLASH);
        put(WeaponCategory.SLASH_SWORD, "Lunge", SpriteConst.SWORD_STAB);
        put(WeaponCategory.SLASH_SWORD, "Block", SpriteConst.SWORD_BLOCK);

        put(WeaponCategory.STAB_SWORD, "Stab", SpriteConst.SWORD_STAB);
        put(WeaponCategory.STAB_SWORD, "Lunge", SpriteConst.SWORD_STAB);
        put(WeaponCategory.STAB_SWORD, "Slash", SpriteConst.SWORD_SLASH);
        put(WeaponCategory.STAB_SWORD, "Block", SpriteConst.SWORD_BLOCK);

        put(WeaponCategory.DAGGER, "Stab", SpriteConst.SWORD_STAB);
        put(WeaponCategory.DAGGER, "Lunge", SpriteConst.SWORD_STAB);
        put(WeaponCategory.DAGGER, "Slash", SpriteConst.SWORD_SLASH);
        put(WeaponCategory.DAGGER, "Block", SpriteConst.SWORD_BLOCK);

        put(WeaponCategory.WHIP, "Flick", SpriteConst.WHIP_FLICK);
        put(WeaponCategory.WHIP, "Lash", SpriteConst.WHIP_LASH);
        put(WeaponCategory.WHIP, "Deflect", SpriteConst.WHIP_LASH); // OSRS has no dedicated "Deflect" combat icon; whip's own Lash is closest

        put(WeaponCategory.CLAW, "Chop", SpriteConst.CLAWS_CHOP);
        put(WeaponCategory.CLAW, "Slash", SpriteConst.CLAWS_SLASH);
        put(WeaponCategory.CLAW, "Lunge", SpriteConst.CLAWS_LUNGE);
        put(WeaponCategory.CLAW, "Block", SpriteConst.CLAWS_BLOCK);

        put(WeaponCategory.PICKAXE, "Spike", SpriteConst.PICKAXE_SPIKE);
        put(WeaponCategory.PICKAXE, "Impale", SpriteConst.PICKAXE_IMPALE);
        put(WeaponCategory.PICKAXE, "Smash", SpriteConst.PICKAXE_SMASH);
        put(WeaponCategory.PICKAXE, "Block", SpriteConst.PICKAXE_BLOCK);

        put(WeaponCategory.SCYTHE, "Reap", SpriteConst.SCYTHE_REAP);
        put(WeaponCategory.SCYTHE, "Chop", SpriteConst.SCYTHE_CHOP);
        put(WeaponCategory.SCYTHE, "Jab", SpriteConst.SCYTHE_JAB);
        put(WeaponCategory.SCYTHE, "Block", SpriteConst.SCYTHE_BLOCK);

        put(WeaponCategory.SPEAR, "Lunge", SpriteConst.SPEAR_LUNGE);
        put(WeaponCategory.SPEAR, "Swipe", SpriteConst.SPEAR_SWIPE);
        put(WeaponCategory.SPEAR, "Pound", SpriteConst.SPEAR_POUND);
        put(WeaponCategory.SPEAR, "Block", SpriteConst.SPEAR_BLOCK);

        put(WeaponCategory.BANNER, "Lunge", SpriteConst.SPEAR_LUNGE);
        put(WeaponCategory.BANNER, "Swipe", SpriteConst.SPEAR_SWIPE);
        put(WeaponCategory.BANNER, "Pound", SpriteConst.SPEAR_POUND);
        put(WeaponCategory.BANNER, "Block", SpriteConst.SPEAR_BLOCK);

        put(WeaponCategory.PARTISAN, "Stab", SpriteConst.SPEAR_LUNGE);
        put(WeaponCategory.PARTISAN, "Lunge", SpriteConst.SPEAR_LUNGE);
        put(WeaponCategory.PARTISAN, "Pound", SpriteConst.SPEAR_POUND);
        put(WeaponCategory.PARTISAN, "Block", SpriteConst.SPEAR_BLOCK);

        put(WeaponCategory.MULTI_MELEE, "Poke", SpriteConst.SPEAR_LUNGE);
        put(WeaponCategory.MULTI_MELEE, "Slash", SpriteConst.SWORD_SLASH);
        put(WeaponCategory.MULTI_MELEE, "Pound", SpriteConst.MACE_POUND);
        put(WeaponCategory.MULTI_MELEE, "Block", SpriteConst.SWORD_BLOCK);

        put(WeaponCategory.POLEARM, "Jab", SpriteConst.HALBERD_JAB);
        put(WeaponCategory.POLEARM, "Swipe", SpriteConst.HALBERD_SWIPE);
        put(WeaponCategory.POLEARM, "Fend", SpriteConst.HALBERD_BLOCK);

        put(WeaponCategory.BLADED_STAFF, "Jab", SpriteConst.HALBERD_JAB);
        put(WeaponCategory.BLADED_STAFF, "Swipe", SpriteConst.HALBERD_SWIPE);
        put(WeaponCategory.BLADED_STAFF, "Fend", SpriteConst.STAFF_BLOCK);
        put(WeaponCategory.BLADED_STAFF, "Spell", SpriteConst.MAGIC_ACCURATE);

        put(WeaponCategory.BLUNT, "Pound", SpriteConst.MACE_POUND);
        put(WeaponCategory.BLUNT, "Pummel", SpriteConst.MACE_PUMMEL);
        put(WeaponCategory.BLUNT, "Block", SpriteConst.MACE_BLOCK);

        put(WeaponCategory.SPIKED, "Pound", SpriteConst.MACE_POUND);
        put(WeaponCategory.SPIKED, "Pummel", SpriteConst.MACE_PUMMEL);
        put(WeaponCategory.SPIKED, "Spike", SpriteConst.MACE_SPIKE);
        put(WeaponCategory.SPIKED, "Block", SpriteConst.MACE_BLOCK);

        put(WeaponCategory.BLUDGEON, "Pound", SpriteConst.HAMMER_POUND);

        put(WeaponCategory.BULWARK, "Pummel", SpriteConst.MACE_PUMMEL); // no dedicated bulwark sprite; nearest crush icon

        put(WeaponCategory.FLAIL, "Chop", SpriteConst.SWORD_CHOP); // no dedicated flail sprite; nearest slash icon
        put(WeaponCategory.FLAIL, "Slash", SpriteConst.SWORD_SLASH);
        put(WeaponCategory.FLAIL, "Block", SpriteConst.SWORD_BLOCK);

        put(WeaponCategory.GUN, "Kick", SpriteConst.UNARMED_KICK); // no dedicated gun sprite

        put(WeaponCategory.POLESTAFF, "Bash", SpriteConst.STAFF_BASH);
        put(WeaponCategory.POLESTAFF, "Pound", SpriteConst.STAFF_POUND);
        put(WeaponCategory.POLESTAFF, "Block", SpriteConst.STAFF_BLOCK);

        put(WeaponCategory.STAFF, "Bash", SpriteConst.STAFF_BASH);
        put(WeaponCategory.STAFF, "Pound", SpriteConst.STAFF_POUND);
        put(WeaponCategory.STAFF, "Focus", SpriteConst.STAFF_BLOCK);
        put(WeaponCategory.STAFF, "Spell", SpriteConst.MAGIC_ACCURATE);

        put(WeaponCategory.POWERED_STAFF, "Accurate", SpriteConst.MAGIC_ACCURATE);
        put(WeaponCategory.POWERED_STAFF, "Longrange", SpriteConst.MAGIC_LONGRANGE);
        put(WeaponCategory.POWERED_WAND, "Accurate", SpriteConst.MAGIC_ACCURATE);
        put(WeaponCategory.POWERED_WAND, "Longrange", SpriteConst.MAGIC_LONGRANGE);

        put(WeaponCategory.BOW, "Accurate", SpriteConst.BOW_ACCURATE);
        put(WeaponCategory.BOW, "Rapid", SpriteConst.BOW_RAPID);
        put(WeaponCategory.BOW, "Longrange", SpriteConst.BOW_LONGRANGE);

        put(WeaponCategory.CROSSBOW, "Accurate", SpriteConst.CROSSBOW_ACCURATE);
        put(WeaponCategory.CROSSBOW, "Rapid", SpriteConst.CROSSBOW_RAPID);
        put(WeaponCategory.CROSSBOW, "Longrange", SpriteConst.CROSSBOW_LONGRANGE);

        put(WeaponCategory.THROWN, "Accurate", SpriteConst.BOW_ACCURATE); // no dedicated thrown-weapon sprite; nearest ranged icon
        put(WeaponCategory.THROWN, "Rapid", SpriteConst.BOW_RAPID);
        put(WeaponCategory.THROWN, "Longrange", SpriteConst.BOW_LONGRANGE);

        put(WeaponCategory.CHINCHOMPA, "Short fuse", SpriteConst.CHINCHOMPA_SHORT_FUSE);
        put(WeaponCategory.CHINCHOMPA, "Medium fuse", SpriteConst.CHINCHOMPA_MEDIUM_FUSE);
        put(WeaponCategory.CHINCHOMPA, "Long fuse", SpriteConst.CHINCHOMPA_LONG_FUSE);

        put(WeaponCategory.SALAMANDER, "Scorch", SpriteConst.SALAMANDER_SCORCH);
        put(WeaponCategory.SALAMANDER, "Flare", SpriteConst.SALAMANDER_FLARE);
        put(WeaponCategory.SALAMANDER, "Blaze", SpriteConst.SALAMANDER_BLAZE);

        put(WeaponCategory.UNARMED, "Punch", SpriteConst.UNARMED_PUNCH);
        put(WeaponCategory.UNARMED, "Kick", SpriteConst.UNARMED_KICK);
        put(WeaponCategory.UNARMED, "Block", SpriteConst.UNARMED_BLOCK);
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
        return style == null ? SpriteConst.UNARMED_BLOCK : genericSpriteFor(style);
    }

    /** A same-damage-type/stance generic icon for a style with no bundled per-category entry. */
    private static int genericSpriteFor(WeaponStyle style) {
        switch (style.type()) {
            case STAB:
                return SpriteConst.SWORD_STAB;
            case SLASH:
                return SpriteConst.SWORD_SLASH;
            case CRUSH:
                return SpriteConst.MACE_POUND;
            case RANGED:
                return SpriteConst.BOW_ACCURATE;
            case MAGIC:
                return SpriteConst.MAGIC_ACCURATE;
            default:
                return SpriteConst.UNARMED_BLOCK;
        }
    }

    /**
     * The raw {@code net.runelite.api.gameval.SpriteID.Combaticons}/{@code 2}/{@code 3}
     * int constants this class maps into, hand-transcribed (not RuneLite-typed)
     * so {@link AttackStyleIcons} itself stays testable without a RuneLite
     * classpath — {@code GearSection} passes these ids straight into
     * {@code SpriteManager.getSpriteAsync}, which takes a plain int.
     */
    static final class SpriteConst {
        private SpriteConst() {
        }

        static final int AXE_BLOCK = 233;
        static final int AXE_CHOP = 234;
        static final int AXE_HACK = 235;
        static final int AXE_SMASH = 236;
        static final int SWORD_BLOCK = 237;
        static final int SWORD_SLASH = 238;
        static final int SWORD_CHOP = 239;
        static final int SWORD_STAB = 240;
        static final int SPEAR_LUNGE = 241;
        static final int SPEAR_POUND = 242;
        static final int MACE_BLOCK = 243;
        static final int MACE_PUMMEL = 244;
        static final int MACE_SPIKE = 245;
        static final int MACE_POUND = 246;
        static final int UNARMED_PUNCH = 247;
        static final int UNARMED_KICK = 248;
        static final int UNARMED_BLOCK = 249;
        static final int SPEAR_BLOCK = 250;
        static final int SPEAR_SWIPE = 251;
        static final int STAFF_BLOCK = 252;
        static final int HAMMER_BLOCK = 253;
        static final int HAMMER_POUND = 255;
        static final int HAMMER_PUMMEL = 256;
        static final int CROSSBOW_ACCURATE = 258;
        static final int CROSSBOW_RAPID = 259;
        static final int CROSSBOW_LONGRANGE = 260;
        static final int SCYTHE_BLOCK = 261;
        static final int SCYTHE_CHOP = 262;
        static final int MAGIC_ACCURATE = 263;
        static final int MAGIC_RAPID = 264;
        static final int MAGIC_LONGRANGE = 265;
        static final int STAFF_BASH = 266;
        static final int STAFF_POUND = 267;
        static final int BOW_ACCURATE = 268;
        static final int BOW_RAPID = 269;
        static final int BOW_LONGRANGE = 270;
        static final int SCYTHE_JAB = 271;
        static final int SCYTHE_REAP = 272;
        static final int PICKAXE_BLOCK = 273;
        static final int PICKAXE_SPIKE = 274;
        static final int PICKAXE_SMASH = 275;
        static final int PICKAXE_IMPALE = 276;
        static final int CLAWS_LUNGE = 277;
        static final int CLAWS_SLASH = 278;
        static final int CLAWS_CHOP = 279;
        static final int CLAWS_BLOCK = 280;
        static final int CHINCHOMPA_LONG_FUSE = 281;
        static final int CHINCHOMPA_MEDIUM_FUSE = 282;
        static final int HALBERD_BLOCK = 283;
        static final int HALBERD_JAB = 284;
        static final int HALBERD_SWIPE = 285;
        static final int WHIP_FLICK = 286;
        static final int WHIP_LASH = 287;
        static final int CHINCHOMPA_SHORT_FUSE = 288;
        static final int SALAMANDER_SCORCH = 289;
        static final int SALAMANDER_FLARE = 290;
        static final int SALAMANDER_BLAZE = 291;
    }
}
