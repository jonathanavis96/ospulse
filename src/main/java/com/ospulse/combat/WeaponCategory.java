package com.ospulse.combat;

import java.util.Locale;

/**
 * The in-game weapon "type" (combat-options category) a weapon belongs to — the
 * one thing that determines which attack styles it offers (see
 * {@link WeaponStyles}). Names mirror weirdgloop {@code osrs-dps-calc}'s
 * {@code EquipmentCategory} enum; {@link #fromDataName(String)} parses the
 * lower-cased strings stored in the bundled {@code weapon_categories.min.json}.
 *
 * <p>RuneLite's {@code ItemEquipmentStats} exposes attack bonuses and speed but
 * not the category, so it is resolved purely from the weapon item id via
 * {@link WeaponCategoryRepository} — no {@code Client}/{@code ItemManager} read
 * and no client thread required, which is also why the same lookup serves
 * hypothetical (not-yet-worn) weapons for the future optimiser.
 */
public enum WeaponCategory {
    TWO_HANDED_SWORD,
    AXE,
    BANNER,
    BLADED_STAFF,
    BLASTER,
    BLUDGEON,
    BLUNT,
    BOW,
    BULWARK,
    CHINCHOMPA,
    CLAW,
    CROSSBOW,
    DAGGER,
    FLAIL,
    GUN,
    MULTI_MELEE,
    PARTISAN,
    PICKAXE,
    POLEARM,
    POLESTAFF,
    POWERED_STAFF,
    POWERED_WAND,
    SALAMANDER,
    SCYTHE,
    SLASH_SWORD,
    SPEAR,
    SPIKED,
    STAB_SWORD,
    STAFF,
    THROWN,
    UNARMED,
    WHIP;

    /**
     * Parses a lower-cased category string from the bundled data (e.g.
     * {@code "slash sword"}, {@code "2h sword"}, {@code "chinchompas"}) into an
     * enum constant, or {@code null} for an unknown/blank value (caller falls
     * back to {@link #UNARMED}).
     */
    public static WeaponCategory fromDataName(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "2h sword":
                return TWO_HANDED_SWORD;
            case "axe":
                return AXE;
            case "banner":
                return BANNER;
            case "bladed staff":
                return BLADED_STAFF;
            case "blaster":
                return BLASTER;
            case "bludgeon":
                return BLUDGEON;
            case "blunt":
                return BLUNT;
            case "bow":
                return BOW;
            case "bulwark":
                return BULWARK;
            case "chinchompas":
                return CHINCHOMPA;
            case "claw":
                return CLAW;
            case "crossbow":
                return CROSSBOW;
            case "dagger":
                return DAGGER;
            case "flail":
                return FLAIL;
            case "gun":
                return GUN;
            case "multi-melee":
                return MULTI_MELEE;
            case "partisan":
                return PARTISAN;
            case "pickaxe":
                return PICKAXE;
            case "polearm":
                return POLEARM;
            case "polestaff":
                return POLESTAFF;
            case "powered staff":
                return POWERED_STAFF;
            case "powered wand":
                return POWERED_WAND;
            case "salamander":
                return SALAMANDER;
            case "scythe":
                return SCYTHE;
            case "slash sword":
                return SLASH_SWORD;
            case "spear":
                return SPEAR;
            case "spiked":
                return SPIKED;
            case "stab sword":
                return STAB_SWORD;
            case "staff":
                return STAFF;
            case "thrown":
                return THROWN;
            case "unarmed":
            case "":
                return UNARMED;
            case "whip":
                return WHIP;
            default:
                return null;
        }
    }
}
