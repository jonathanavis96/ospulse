package com.ospulse.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

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
 */
public final class WeaponStyles {
    private WeaponStyles() {
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
        switch (category) {
            case TWO_HANDED_SWORD:
                return list(
                    s("Chop", CombatStyle.SLASH, Stance.ACCURATE),
                    s("Slash", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Smash", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.SLASH, Stance.DEFENSIVE));
            case AXE:
                return list(
                    s("Chop", CombatStyle.SLASH, Stance.ACCURATE),
                    s("Hack", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Smash", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.SLASH, Stance.DEFENSIVE));
            case BANNER:
                return list(
                    s("Lunge", CombatStyle.STAB, Stance.ACCURATE),
                    s("Swipe", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Pound", CombatStyle.CRUSH, Stance.CONTROLLED),
                    s("Block", CombatStyle.STAB, Stance.DEFENSIVE));
            case BLADED_STAFF:
                return list(
                    s("Jab", CombatStyle.STAB, Stance.ACCURATE),
                    s("Swipe", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Fend", CombatStyle.CRUSH, Stance.DEFENSIVE),
                    s("Spell", CombatStyle.MAGIC, Stance.STANDARD));
            case BLASTER:
                return Collections.emptyList();
            case BLUDGEON:
                return list(s("Pound", CombatStyle.CRUSH, Stance.AGGRESSIVE));
            case BLUNT:
                return list(
                    s("Pound", CombatStyle.CRUSH, Stance.ACCURATE),
                    s("Pummel", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.CRUSH, Stance.DEFENSIVE));
            case BOW:
            case CROSSBOW:
            case THROWN:
                return list(
                    s("Accurate", CombatStyle.RANGED, Stance.ACCURATE),
                    s("Rapid", CombatStyle.RANGED, Stance.RAPID),
                    s("Longrange", CombatStyle.RANGED, Stance.LONGRANGE));
            case BULWARK:
                return list(s("Pummel", CombatStyle.CRUSH, Stance.ACCURATE));
            case CHINCHOMPA:
                return list(
                    s("Short fuse", CombatStyle.RANGED, Stance.ACCURATE),
                    s("Medium fuse", CombatStyle.RANGED, Stance.RAPID),
                    s("Long fuse", CombatStyle.RANGED, Stance.LONGRANGE));
            case CLAW:
                return list(
                    s("Chop", CombatStyle.SLASH, Stance.ACCURATE),
                    s("Slash", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Lunge", CombatStyle.STAB, Stance.CONTROLLED),
                    s("Block", CombatStyle.SLASH, Stance.DEFENSIVE));
            case FLAIL:
                return list(
                    s("Chop", CombatStyle.SLASH, Stance.ACCURATE),
                    s("Slash", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.SLASH, Stance.DEFENSIVE));
            case GUN:
                return list(s("Kick", CombatStyle.CRUSH, Stance.AGGRESSIVE));
            case MULTI_MELEE:
                return list(
                    s("Poke", CombatStyle.STAB, Stance.ACCURATE),
                    s("Slash", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Pound", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.SLASH, Stance.DEFENSIVE));
            case PARTISAN:
                return list(
                    s("Stab", CombatStyle.STAB, Stance.ACCURATE),
                    s("Lunge", CombatStyle.STAB, Stance.AGGRESSIVE),
                    s("Pound", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.STAB, Stance.DEFENSIVE));
            case PICKAXE:
                return list(
                    s("Spike", CombatStyle.STAB, Stance.ACCURATE),
                    s("Impale", CombatStyle.STAB, Stance.AGGRESSIVE),
                    s("Smash", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.STAB, Stance.DEFENSIVE));
            case POLEARM:
                return list(
                    s("Jab", CombatStyle.STAB, Stance.CONTROLLED),
                    s("Swipe", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Fend", CombatStyle.STAB, Stance.DEFENSIVE));
            case POLESTAFF:
                return list(
                    s("Bash", CombatStyle.CRUSH, Stance.ACCURATE),
                    s("Pound", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.CRUSH, Stance.DEFENSIVE));
            case POWERED_STAFF:
            case POWERED_WAND:
                return list(
                    s("Accurate", CombatStyle.MAGIC, Stance.ACCURATE),
                    s("Longrange", CombatStyle.MAGIC, Stance.LONGRANGE));
            case SALAMANDER:
                return list(
                    s("Scorch", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Flare", CombatStyle.RANGED, Stance.RAPID),
                    s("Blaze", CombatStyle.MAGIC, Stance.STANDARD));
            case SCYTHE:
                return list(
                    s("Reap", CombatStyle.SLASH, Stance.ACCURATE),
                    s("Chop", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Jab", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.SLASH, Stance.DEFENSIVE));
            case SLASH_SWORD:
                return list(
                    s("Chop", CombatStyle.SLASH, Stance.ACCURATE),
                    s("Slash", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Lunge", CombatStyle.STAB, Stance.CONTROLLED),
                    s("Block", CombatStyle.SLASH, Stance.DEFENSIVE));
            case SPEAR:
                return list(
                    s("Lunge", CombatStyle.STAB, Stance.CONTROLLED),
                    s("Swipe", CombatStyle.SLASH, Stance.CONTROLLED),
                    s("Pound", CombatStyle.CRUSH, Stance.CONTROLLED),
                    s("Block", CombatStyle.STAB, Stance.DEFENSIVE));
            case SPIKED:
                return list(
                    s("Pound", CombatStyle.CRUSH, Stance.ACCURATE),
                    s("Pummel", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Spike", CombatStyle.STAB, Stance.CONTROLLED),
                    s("Block", CombatStyle.CRUSH, Stance.DEFENSIVE));
            case DAGGER:
            case STAB_SWORD:
                return list(
                    s("Stab", CombatStyle.STAB, Stance.ACCURATE),
                    s("Lunge", CombatStyle.STAB, Stance.AGGRESSIVE),
                    s("Slash", CombatStyle.SLASH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.STAB, Stance.DEFENSIVE));
            case STAFF:
                return list(
                    s("Bash", CombatStyle.CRUSH, Stance.ACCURATE),
                    s("Pound", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Focus", CombatStyle.CRUSH, Stance.DEFENSIVE),
                    s("Spell", CombatStyle.MAGIC, Stance.STANDARD));
            case WHIP:
                return list(
                    s("Flick", CombatStyle.SLASH, Stance.ACCURATE),
                    s("Lash", CombatStyle.SLASH, Stance.CONTROLLED),
                    s("Deflect", CombatStyle.SLASH, Stance.DEFENSIVE));
            case UNARMED:
            default:
                return list(
                    s("Punch", CombatStyle.CRUSH, Stance.ACCURATE),
                    s("Kick", CombatStyle.CRUSH, Stance.AGGRESSIVE),
                    s("Block", CombatStyle.CRUSH, Stance.DEFENSIVE));
        }
    }

    private static WeaponStyle s(String name, CombatStyle type, Stance stance) {
        return new WeaponStyle(name, type, stance);
    }

    private static List<WeaponStyle> list(WeaponStyle... styles) {
        List<WeaponStyle> out = new ArrayList<>(styles.length);
        Collections.addAll(out, styles);
        return out;
    }
}
