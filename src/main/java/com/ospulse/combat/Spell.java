package com.ospulse.combat;

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
    WIND_STRIKE("Wind Strike", SpellBook.STANDARD, 8, 15),
    WATER_STRIKE("Water Strike", SpellBook.STANDARD, 8, 17),
    EARTH_STRIKE("Earth Strike", SpellBook.STANDARD, 8, 19),
    FIRE_STRIKE("Fire Strike", SpellBook.STANDARD, 8, 21),
    WIND_BOLT("Wind Bolt", SpellBook.STANDARD, 12, 23),
    WATER_BOLT("Water Bolt", SpellBook.STANDARD, 12, 26),
    EARTH_BOLT("Earth Bolt", SpellBook.STANDARD, 12, 29),
    FIRE_BOLT("Fire Bolt", SpellBook.STANDARD, 12, 32),
    WIND_BLAST("Wind Blast", SpellBook.STANDARD, 16, 35),
    WATER_BLAST("Water Blast", SpellBook.STANDARD, 16, 38),
    EARTH_BLAST("Earth Blast", SpellBook.STANDARD, 16, 40),
    FIRE_BLAST("Fire Blast", SpellBook.STANDARD, 16, 44),
    WIND_WAVE("Wind Wave", SpellBook.STANDARD, 20, 46),
    WATER_WAVE("Water Wave", SpellBook.STANDARD, 20, 48),
    EARTH_WAVE("Earth Wave", SpellBook.STANDARD, 20, 51),
    FIRE_WAVE("Fire Wave", SpellBook.STANDARD, 20, 52),
    WIND_SURGE("Wind Surge", SpellBook.STANDARD, 24, 362),
    WATER_SURGE("Water Surge", SpellBook.STANDARD, 24, 363),
    EARTH_SURGE("Earth Surge", SpellBook.STANDARD, 24, 364),
    FIRE_SURGE("Fire Surge", SpellBook.STANDARD, 24, 365),
    CRUMBLE_UNDEAD("Crumble Undead", SpellBook.STANDARD, 15, 34),
    IBAN_BLAST("Iban Blast", SpellBook.STANDARD, 25, 53),
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

    Spell(String displayName, SpellBook book, int baseMaxHit, int spriteId) {
        this.displayName = displayName;
        this.book = book;
        this.baseMaxHit = baseMaxHit;
        this.spriteId = spriteId;
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

    @Override
    public String toString() {
        return displayName;
    }
}
