package com.ospulse.combat;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Combat spells and their base max hits — hand-transcribed raw FACTS from the
 * OSRS Wiki spell pages ("OSRS Wiki spell facts, hand-transcribed"; NOT copied
 * from any third-party dataset, so this table carries no NC licence).
 *
 * <p><b>Within-tier level scaling (deliberate simplification):</b> since the
 * 2023 elemental-spell rework, a lower element's max hit scales up with Magic
 * level until it matches the strongest spell of its tier (e.g. Wind Bolt
 * reaches Fire Bolt's 12 well before level 59). The values here are each
 * tier's cap — exact whenever the player's Magic level is at or above the
 * tier's strongest-spell requirement, which is the realistic use of a DPS
 * picker; the sub-cap low-level ramp is not modelled.
 *
 * <p>God spells assume no Charge (Charge's +10 is a separate TODO). Magic
 * Dart, salamanders and Tumeken/Trident built-ins are NOT spells — powered
 * staves are handled by {@link PoweredStaff}.
 *
 * <p><b>{@code spriteId}:</b> the legacy (deprecated but still bundled)
 * {@code net.runelite.api.SpriteID.SPELL_*} constant for this spell's
 * spellbook icon, hand-copied here as a raw int so this pure combat-engine
 * class carries no RuneLite import. Every offensive spell in this enum has an
 * exact {@code SPELL_<NAME>} match in that class (verified against the
 * pinned client jar) — none needed a rune-icon fallback.
 */
public enum Spell {
    // ---- Standard spellbook (elemental tiers at their tier-cap max hits) ----
    WIND_STRIKE("Wind Strike", SpellBook.STANDARD, 8, 15, Element.WIND),
    WATER_STRIKE("Water Strike", SpellBook.STANDARD, 8, 17, Element.WATER),
    EARTH_STRIKE("Earth Strike", SpellBook.STANDARD, 8, 19, Element.EARTH),
    FIRE_STRIKE("Fire Strike", SpellBook.STANDARD, 8, 21, Element.FIRE),
    WIND_BOLT("Wind Bolt", SpellBook.STANDARD, 12, 23, Element.WIND),
    WATER_BOLT("Water Bolt", SpellBook.STANDARD, 12, 26, Element.WATER),
    EARTH_BOLT("Earth Bolt", SpellBook.STANDARD, 12, 29, Element.EARTH),
    FIRE_BOLT("Fire Bolt", SpellBook.STANDARD, 12, 32, Element.FIRE),
    WIND_BLAST("Wind Blast", SpellBook.STANDARD, 16, 35, Element.WIND),
    WATER_BLAST("Water Blast", SpellBook.STANDARD, 16, 38, Element.WATER),
    EARTH_BLAST("Earth Blast", SpellBook.STANDARD, 16, 40, Element.EARTH),
    FIRE_BLAST("Fire Blast", SpellBook.STANDARD, 16, 44, Element.FIRE),
    WIND_WAVE("Wind Wave", SpellBook.STANDARD, 20, 46, Element.WIND),
    WATER_WAVE("Water Wave", SpellBook.STANDARD, 20, 48, Element.WATER),
    EARTH_WAVE("Earth Wave", SpellBook.STANDARD, 20, 51, Element.EARTH),
    FIRE_WAVE("Fire Wave", SpellBook.STANDARD, 20, 52, Element.FIRE),
    WIND_SURGE("Wind Surge", SpellBook.STANDARD, 24, 362, Element.WIND),
    WATER_SURGE("Water Surge", SpellBook.STANDARD, 24, 363, Element.WATER),
    EARTH_SURGE("Earth Surge", SpellBook.STANDARD, 24, 364, Element.EARTH),
    FIRE_SURGE("Fire Surge", SpellBook.STANDARD, 24, 365, Element.FIRE),
    CRUMBLE_UNDEAD("Crumble Undead", SpellBook.STANDARD, 15, 34),
    /**
     * Castable ONLY with Iban's staff equipped (regular 1409, the other
     * charged id 1410, or the upgraded Iban's staff (u) 12658) — see
     * {@link #isCastableWith(int)}.
     */
    IBAN_BLAST("Iban Blast", SpellBook.STANDARD, 25, 53, 1409, 1410, 12658),
    SARADOMIN_STRIKE("Saradomin Strike", SpellBook.STANDARD, 20, 61),
    CLAWS_OF_GUTHIX("Claws of Guthix", SpellBook.STANDARD, 20, 60),
    FLAMES_OF_ZAMORAK("Flames of Zamorak", SpellBook.STANDARD, 20, 59),

    // ---- Ancient Magicks ----
    SMOKE_RUSH("Smoke Rush", SpellBook.ANCIENT, 13, 329),
    SHADOW_RUSH("Shadow Rush", SpellBook.ANCIENT, 14, 337),
    BLOOD_RUSH("Blood Rush", SpellBook.ANCIENT, 15, 333),
    ICE_RUSH("Ice Rush", SpellBook.ANCIENT, 16, 325),
    SMOKE_BURST("Smoke Burst", SpellBook.ANCIENT, 17, 330),
    SHADOW_BURST("Shadow Burst", SpellBook.ANCIENT, 18, 338),
    BLOOD_BURST("Blood Burst", SpellBook.ANCIENT, 21, 334),
    ICE_BURST("Ice Burst", SpellBook.ANCIENT, 22, 326),
    SMOKE_BLITZ("Smoke Blitz", SpellBook.ANCIENT, 23, 331),
    SHADOW_BLITZ("Shadow Blitz", SpellBook.ANCIENT, 24, 339),
    BLOOD_BLITZ("Blood Blitz", SpellBook.ANCIENT, 25, 335),
    ICE_BLITZ("Ice Blitz", SpellBook.ANCIENT, 26, 327),
    SMOKE_BARRAGE("Smoke Barrage", SpellBook.ANCIENT, 27, 332),
    SHADOW_BARRAGE("Shadow Barrage", SpellBook.ANCIENT, 28, 340),
    BLOOD_BARRAGE("Blood Barrage", SpellBook.ANCIENT, 29, 336),
    ICE_BARRAGE("Ice Barrage", SpellBook.ANCIENT, 30, 328);

    /** All autocast spell casts take 5 game ticks (3.0s), regardless of the staff's own melee speed. */
    public static final int CAST_SPEED_TICKS = 5;

    /**
     * The classic elemental-spell element, used to match a monster's
     * elemental weakness (see {@link Monster#weaknessElement()} /
     * {@code DpsCalculator.computeMagic}). Only the standard Strike/Bolt/
     * Blast/Wave/Surge tiers carry one; Iban Blast, Magic Dart, the god
     * spells and every Ancient Magicks spell (ice/blood/smoke/shadow) have
     * none, so they never get a weakness bonus. The upstream weirdgloop
     * data calls the wind element "air" — {@code WIND} is this enum's name
     * for the same element.
     */
    public enum Element {
        WIND,
        WATER,
        EARTH,
        FIRE
    }

    public enum SpellBook {
        STANDARD("Standard"),
        ANCIENT("Ancient");

        private final String displayName;

        SpellBook(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final String displayName;
    private final SpellBook book;
    private final int baseMaxHit;
    private final int spriteId;
    /** Weapon item ids this spell may be cast with; empty = castable with any (magic) weapon. */
    private final Set<Integer> requiredWeaponItemIds;
    /** This spell's elemental-weakness-matching element, or {@code null} if it has none. */
    private final Element element;

    Spell(String displayName, SpellBook book, int baseMaxHit, int spriteId) {
        this(displayName, book, baseMaxHit, spriteId, null, new int[0]);
    }

    /** Standard elemental tiers (Strike/Bolt/Blast/Wave/Surge): carries an {@link Element}, no weapon requirement. */
    Spell(String displayName, SpellBook book, int baseMaxHit, int spriteId, Element element) {
        this(displayName, book, baseMaxHit, spriteId, element, new int[0]);
    }

    /**
     * @param requiredWeaponItemIds if non-empty, {@link #isCastableWith(int)} only
     *                               returns {@code true} for one of these weapon
     *                               item ids (e.g. Iban Blast + Iban's staff).
     */
    Spell(String displayName, SpellBook book, int baseMaxHit, int spriteId, int... requiredWeaponItemIds) {
        this(displayName, book, baseMaxHit, spriteId, null, requiredWeaponItemIds);
    }

    private Spell(String displayName, SpellBook book, int baseMaxHit, int spriteId, Element element,
                  int... requiredWeaponItemIds) {
        this.displayName = displayName;
        this.book = book;
        this.baseMaxHit = baseMaxHit;
        this.spriteId = spriteId;
        this.element = element;
        this.requiredWeaponItemIds = requiredWeaponItemIds.length == 0
                ? Collections.emptySet()
                : java.util.Arrays.stream(requiredWeaponItemIds).boxed().collect(Collectors.toSet());
    }

    public String displayName() {
        return displayName;
    }

    public SpellBook book() {
        return book;
    }

    /** The spell's base max hit BEFORE any magic-damage bonuses (see class javadoc for the tier-cap simplification). */
    public int baseMaxHit() {
        return baseMaxHit;
    }

    /** The {@code net.runelite.api.SpriteID.SPELL_*} constant for this spell's icon (see class javadoc). */
    public int spriteId() {
        return spriteId;
    }

    /**
     * This spell's element for elemental-weakness matching, or {@code null}
     * when it has none (Iban Blast, Magic Dart, god spells, all Ancient
     * Magicks — see {@link Element}).
     */
    public Element element() {
        return element;
    }

    /**
     * True when this spell may actually be cast with the given equipped
     * weapon item id. Spells with no weapon requirement (the vast majority)
     * are always castable; Iban Blast is the one exception in this enum —
     * castable ONLY with Iban's staff (1409/1410/12658).
     */
    public boolean isCastableWith(int weaponItemId) {
        return requiredWeaponItemIds.isEmpty() || requiredWeaponItemIds.contains(weaponItemId);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
