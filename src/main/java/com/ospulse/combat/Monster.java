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
        this.size = b.size;
        this.attributes = Collections.unmodifiableSet(EnumSet.copyOf(
                b.attributes.isEmpty() ? EnumSet.noneOf(MonsterAttribute.class) : b.attributes));
        this.attackSpeedTicks = b.attackSpeedTicks;
        this.demonbaneResistPercent = b.demonbaneResistPercent;
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

        public Monster build() {
            return new Monster(this);
        }
    }
}
