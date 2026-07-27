package com.ospulse.combat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A monster's combat-relevant stats: the data RuneLite does not expose
 * (defensive bonuses, magic level, attributes), sourced from a bundled/
 * refreshed data snapshot via {@link MonsterRepository}.
 */
public final class Monster {
    private final String name;
    private final List<Integer> npcIds;
    private final int hitpoints;
    private final int defenceLevel;
    private final int dstab;
    private final int dslash;
    private final int dcrush;
    private final int dmagic;
    private final int drange;
    private final int magicLevel;
    private final int size;
    private final Set<MonsterAttribute> attributes;
    private final Integer attackSpeedTicks;
    private final int demonbaneResistPercent;
    private final String weaknessElement;
    private final int weaknessSeverity;
    private final boolean wildernessTarget;
    private final String lookupName;

    private Monster(Builder b) {
        this.name = b.name;
        this.npcIds = Collections.unmodifiableList(b.npcIds);
        this.hitpoints = b.hitpoints;
        this.defenceLevel = b.defenceLevel;
        this.dstab = b.dstab;
        this.dslash = b.dslash;
        this.dcrush = b.dcrush;
        this.dmagic = b.dmagic;
        this.drange = b.drange;
        this.magicLevel = b.magicLevel;
        // Upstream uses 0 (and, defensively, any non-positive value) to mean
        // "size unknown" - never a genuine monster footprint, since an NPC
        // cannot be 0x0. Normalise to the smallest real size, 1x1, here at
        // the model boundary so every consumer (Colossal blade's
        // +2*min(size,5) bonus, ScytheCascade's hit count, etc.) sees a
        // realistic size without each call site having to special-case it.
        this.size = b.size > 0 ? b.size : 1;
        this.attributes = Collections.unmodifiableSet(EnumSet.copyOf(
                b.attributes.isEmpty() ? EnumSet.noneOf(MonsterAttribute.class) : b.attributes));
        this.attackSpeedTicks = b.attackSpeedTicks;
        this.demonbaneResistPercent = b.demonbaneResistPercent;
        this.weaknessElement = b.weaknessElement;
        this.weaknessSeverity = b.weaknessSeverity;
        this.wildernessTarget = b.wildernessTarget;
        this.lookupName = b.lookupName != null ? b.lookupName : b.name;
    }

    public String name() {
        return name;
    }

    public List<Integer> npcIds() {
        return npcIds;
    }

    public int hitpoints() {
        return hitpoints;
    }

    public int defenceLevel() {
        return defenceLevel;
    }

    public int dstab() {
        return dstab;
    }

    public int dslash() {
        return dslash;
    }

    public int dcrush() {
        return dcrush;
    }

    public int dmagic() {
        return dmagic;
    }

    public int drange() {
        return drange;
    }

    /** Defensive bonus relevant to the given attacking style (dstab/dslash/dcrush/drange, or dmagic for MAGIC). */
    public int defenceBonus(CombatStyle style) {
        switch (style) {
            case STAB:
                return dstab;
            case SLASH:
                return dslash;
            case CRUSH:
                return dcrush;
            case RANGED:
                return drange;
            case MAGIC:
                return dmagic;
            default:
                throw new IllegalArgumentException("Unknown style: " + style);
        }
    }

    public int magicLevel() {
        return magicLevel;
    }

    public int size() {
        return size;
    }

    public Set<MonsterAttribute> attributes() {
        return attributes;
    }

    public boolean isUndead() {
        return attributes.contains(MonsterAttribute.UNDEAD);
    }

    public boolean isDemon() {
        return attributes.contains(MonsterAttribute.DEMON);
    }

    /** True for draconic creatures (dragons, hydras, wyverns, Great Olm) — gates Dragon Hunter and similar dragonbane effects. */
    public boolean isDragon() {
        return attributes.contains(MonsterAttribute.DRAGON);
    }

    /** Attack speed in ticks, if known (not always present in the source data). */
    public Integer attackSpeedTicks() {
        return attackSpeedTicks;
    }

    /**
     * Percent by which this monster resists the demonbane weapon BONUS
     * (0-100; default 0 = no resistance). E.g. Duke Sucellus is 30: a
     * demonbane weapon's excess-over-1 multiplier is scaled down by
     * {@code (1 - resistPercent / 100)} — see {@code DpsCalculator}'s
     * demonbane apply step for the exact formula. Populated from the bundled
     * monster data when present; otherwise 0 (mechanism is data-driven, so it
     * activates automatically once a monster's data carries a non-zero value).
     */
    public int demonbaneResistPercent() {
        return demonbaneResistPercent;
    }

    /**
     * This monster's elemental weakness element ({@code "WIND"}/{@code "WATER"}/
     * {@code "EARTH"}/{@code "FIRE"}, matching {@link Spell.Element} names), or
     * {@code null} when this monster has no elemental weakness. Populated from
     * the bundled monster data's {@code weakness.element} (upstream's
     * {@code "air"} is mapped to {@code "WIND"} at minify time — see the
     * monsters.min.json.README.md field-mapping table).
     */
    public String weaknessElement() {
        return weaknessElement;
    }

    /**
     * The elemental-weakness damage bonus, as a percent added to the caster's
     * magic-damage percent when the cast spell's {@link Spell.Element} matches
     * {@link #weaknessElement()} (0 when {@link #weaknessElement()} is
     * {@code null}) — see {@code DpsCalculator.computeMagic}.
     */
    public int weaknessSeverity() {
        return weaknessSeverity;
    }

    /**
     * True when this target should receive the revenant-weapon Wilderness
     * bonus (see {@code RevenantWeapon}/{@code DpsCalculator}) — either
     * because it is a genuinely Wilderness-exclusive monster ({@link
     * WildernessMonsterRepository}'s curated set), or because it is a
     * SYNTHETIC "(Wilderness)" twin of a both-locations monster (see {@link
     * #lookupName()} and {@link WildernessVariantMonsterRepository}) that
     * the player explicitly selected in preference to the ordinary,
     * non-Wilderness entry of the same species. An ordinary both-locations
     * monster's own (non-synthetic) entry always returns {@code false} here
     * — selecting it means the player is NOT claiming to be fighting it in
     * the Wilderness.
     */
    public boolean isWildernessTarget() {
        return wildernessTarget;
    }

    /**
     * The name every OTHER lookup (per-target damage caps/penalties via
     * {@code MonsterCombatRequirementRepository}, required-gear reminders
     * via {@code MonsterGearOverrideRepository}, consumables reminders via
     * {@code MonsterConsumablesRepository}) should resolve against — equal
     * to {@link #name()} for an ordinary monster, but for a SYNTHETIC
     * "(Wilderness)" variant this returns the REAL underlying monster's
     * name instead (e.g. "Black dragon (Level 227)" for the "Black dragon
     * (Wilderness)" synthetic entry).
     *
     * <p>This is the reverse-map a synthetic target needs so it behaves
     * identically to its base monster in every respect except the
     * revenant-weapon bonus: a Wilderness Black dragon must still gate on
     * the same weapon requirements, get the same required-gear warnings,
     * and get the same dragonfire consumables reminder as the ordinary
     * Black dragon (Level 227) — only {@link #isWildernessTarget()} and the
     * DISPLAY name ({@link #name()}) differ. Callers resolving identity-based
     * data must use this method, never {@link #name()}, for that reason.
     */
    public String lookupName() {
        return lookupName;
    }

    /**
     * Builds a new {@link Monster} that copies every field of {@code
     * source} — used ONLY to synthesize a Wilderness-selectable "twin" of a
     * both-locations monster (see {@link WildernessVariantMonsterRepository}):
     * the caller then overrides {@link Builder#name}, {@link
     * Builder#wildernessTarget}, and {@link Builder#lookupName} on the
     * returned builder before calling {@link Builder#build()}.
     */
    public static Builder builderFrom(Monster source) {
        Builder b = new Builder();
        b.name = source.name;
        b.npcIds = source.npcIds;
        b.hitpoints = source.hitpoints;
        b.defenceLevel = source.defenceLevel;
        b.dstab = source.dstab;
        b.dslash = source.dslash;
        b.dcrush = source.dcrush;
        b.dmagic = source.dmagic;
        b.drange = source.drange;
        b.magicLevel = source.magicLevel;
        b.size = source.size;
        b.attributes = EnumSet.copyOf(source.attributes.isEmpty()
                ? EnumSet.noneOf(MonsterAttribute.class) : source.attributes);
        b.attackSpeedTicks = source.attackSpeedTicks;
        b.demonbaneResistPercent = source.demonbaneResistPercent;
        b.weaknessElement = source.weaknessElement;
        b.weaknessSeverity = source.weaknessSeverity;
        b.wildernessTarget = source.wildernessTarget;
        b.lookupName = source.lookupName;
        return b;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private List<Integer> npcIds = Collections.emptyList();
        private int hitpoints;
        private int defenceLevel;
        private int dstab;
        private int dslash;
        private int dcrush;
        private int dmagic;
        private int drange;
        private int magicLevel;
        private int size = 1;
        private Set<MonsterAttribute> attributes = EnumSet.noneOf(MonsterAttribute.class);
        private Integer attackSpeedTicks;
        private int demonbaneResistPercent;
        private String weaknessElement;
        private int weaknessSeverity;
        private boolean wildernessTarget;
        private String lookupName;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder npcIds(List<Integer> ids) {
            this.npcIds = ids;
            return this;
        }

        public Builder hitpoints(int hp) {
            this.hitpoints = hp;
            return this;
        }

        public Builder defenceLevel(int level) {
            this.defenceLevel = level;
            return this;
        }

        public Builder defenceBonuses(int dstab, int dslash, int dcrush, int dmagic, int drange) {
            this.dstab = dstab;
            this.dslash = dslash;
            this.dcrush = dcrush;
            this.dmagic = dmagic;
            this.drange = drange;
            return this;
        }

        public Builder magicLevel(int level) {
            this.magicLevel = level;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder attributes(Set<MonsterAttribute> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder attackSpeedTicks(Integer ticks) {
            this.attackSpeedTicks = ticks;
            return this;
        }

        /** Percent demonbane resistance (0-100; default 0). See {@link Monster#demonbaneResistPercent()}. */
        public Builder demonbaneResistPercent(int percent) {
            this.demonbaneResistPercent = percent;
            return this;
        }

        /** Elemental weakness (nullable element + severity). See {@link Monster#weaknessElement()}/{@link Monster#weaknessSeverity()}. */
        public Builder weakness(String element, int severity) {
            this.weaknessElement = element;
            this.weaknessSeverity = severity;
            return this;
        }

        /** See {@link Monster#isWildernessTarget()}. Defaults to {@code false}. */
        public Builder wildernessTarget(boolean value) {
            this.wildernessTarget = value;
            return this;
        }

        /** See {@link Monster#lookupName()}. Defaults to this builder's {@link #name} if never called. */
        public Builder lookupName(String value) {
            this.lookupName = value;
            return this;
        }

        public Monster build() {
            return new Monster(this);
        }
    }
}
