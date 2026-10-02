package com.ospulse.combat;

import java.util.*;

/**
 * The player-side inputs to a DPS calculation: base and boosted skill
 * levels, active offensive prayers, stance, and the small set of Tier-A
 * gear-effect flags that can't be derived purely from summed
 * {@link EquipmentStats} numbers (Salve/Slayer helm/Void need to know which
 * *item variant* is worn, not just a bonus total — those flags live on
 * {@link EquipmentStats} instead; this class only carries levels/prayers/
 * stance/task-state).
 */
public final class PlayerCombat {
    private final Set<OffensivePrayer> activePrayers;

    /** Private snapshot of the builder that made this object; never mutated after construction. */
    private final Builder v;

    private PlayerCombat(Builder b) {
        this.v = b.copy();
        this.activePrayers = Collections.unmodifiableSet(EnumSet.copyOf(b.activePrayers));
    }

    public int baseAttack() {
        return v.baseAttack;
    }

    public int boostedAttack() {
        return v.boostedAttack;
    }

    public int baseStrength() {
        return v.baseStrength;
    }

    public int boostedStrength() {
        return v.boostedStrength;
    }

    public int baseDefence() {
        return v.baseDefence;
    }

    public int boostedDefence() {
        return v.boostedDefence;
    }

    public int baseRanged() {
        return v.baseRanged;
    }

    public int boostedRanged() {
        return v.boostedRanged;
    }

    public int baseMagic() {
        return v.baseMagic;
    }

    public int boostedMagic() {
        return v.boostedMagic;
    }

    public int basePrayer() {
        return v.basePrayer;
    }

    public int boostedPrayer() {
        return v.boostedPrayer;
    }

    public int baseHitpoints() {
        return v.baseHitpoints;
    }

    public int boostedHitpoints() {
        return v.boostedHitpoints;
    }

    public Set<OffensivePrayer> activePrayers() {
        return activePrayers;
    }

    public Stance stance() {
        return v.stance;
    }

    public boolean assumeBestPotion() {
        return v.assumeBestPotion;
    }

    public boolean assumeBestPrayer() {
        return v.assumeBestPrayer;
    }

    public boolean onSlayerTask() {
        return v.onSlayerTask;
    }

    /**
     * Which magic-boosting potion {@link #assumeBestPotion()} should model when
     * the active style is Magic — a UI-driven swap (right-click the potion
     * toggle in {@code GearSection}) over the otherwise-fixed "best" pick.
     * Defaults to {@link CombatIcons.BoostPotion#IMBUED_HEART} (unchanged
     * behaviour for every caller that does not set this explicitly). Melee/
     * ranged best-potion picks are not swappable and ignore this field.
     */
    public CombatIcons.BoostPotion magicPotionVariant() {
        return v.magicPotionVariant;
    }

    /**
     * The offensive prayer the {@code assumeBestPrayer} simulation should apply,
     * or {@code null} to apply the calculator's top-tier default for the style
     * (Piety/Rigour/Augury). Set from the prayer toggle's right-click swap menu
     * so an account without the top-tier prayer can simulate the tier it has.
     * Ignored entirely when {@link #assumeBestPrayer()} is false, where the
     * player's real active prayers win.
     */
    public OffensivePrayer assumedPrayer() {
        return v.assumedPrayer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements Cloneable {
        private int baseAttack;
        private int boostedAttack;
        private int baseStrength;
        private int boostedStrength;
        private int baseDefence;
        private int boostedDefence;
        private int baseRanged;
        private int boostedRanged;
        private int baseMagic;
        private int boostedMagic;
        private int basePrayer;
        private int boostedPrayer;
        private int baseHitpoints;
        private int boostedHitpoints;
        private Set<OffensivePrayer> activePrayers = EnumSet.noneOf(OffensivePrayer.class);
        private Stance stance = Stance.STANDARD;
        private boolean assumeBestPotion;
        private boolean assumeBestPrayer;
        private boolean onSlayerTask;
        private CombatIcons.BoostPotion magicPotionVariant = CombatIcons.BoostPotion.IMBUED_HEART;
        private OffensivePrayer assumedPrayer;

        private Builder() {
        }

        public Builder attack(int base, int boosted) {
            this.baseAttack = base;
            this.boostedAttack = boosted;
            return this;
        }

        public Builder strength(int base, int boosted) {
            this.baseStrength = base;
            this.boostedStrength = boosted;
            return this;
        }

        public Builder defence(int base, int boosted) {
            this.baseDefence = base;
            this.boostedDefence = boosted;
            return this;
        }

        public Builder ranged(int base, int boosted) {
            this.baseRanged = base;
            this.boostedRanged = boosted;
            return this;
        }

        public Builder magic(int base, int boosted) {
            this.baseMagic = base;
            this.boostedMagic = boosted;
            return this;
        }

        public Builder prayer(int base, int boosted) {
            this.basePrayer = base;
            this.boostedPrayer = boosted;
            return this;
        }

        public Builder hitpoints(int base, int boosted) {
            this.baseHitpoints = base;
            this.boostedHitpoints = boosted;
            return this;
        }

        public Builder activePrayers(Set<OffensivePrayer> prayers) {
            this.activePrayers = prayers.isEmpty() ? EnumSet.noneOf(OffensivePrayer.class) : EnumSet.copyOf(prayers);
            return this;
        }

        public Builder stance(Stance stance) {
            this.stance = stance;
            return this;
        }

        public Builder assumeBestPotion(boolean value) {
            this.assumeBestPotion = value;
            return this;
        }

        public Builder assumeBestPrayer(boolean value) {
            this.assumeBestPrayer = value;
            return this;
        }

        public Builder onSlayerTask(boolean value) {
            this.onSlayerTask = value;
            return this;
        }

        /** @see PlayerCombat#magicPotionVariant() */
        public Builder magicPotionVariant(CombatIcons.BoostPotion value) {
            this.magicPotionVariant = value == null ? CombatIcons.BoostPotion.IMBUED_HEART : value;
            return this;
        }

        /** @see PlayerCombat#assumedPrayer() */
        public Builder assumedPrayer(OffensivePrayer assumedPrayer) {
            this.assumedPrayer = assumedPrayer;
            return this;
        }

        private Builder copy() {
            try {
                return (Builder) clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        public PlayerCombat build() {
            return new PlayerCombat(this);
        }
    }
}
