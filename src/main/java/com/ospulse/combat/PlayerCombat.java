package com.ospulse.combat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

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
    private final int baseAttack;
    private final int boostedAttack;
    private final int baseStrength;
    private final int boostedStrength;
    private final int baseDefence;
    private final int boostedDefence;
    private final int baseRanged;
    private final int boostedRanged;
    private final int baseMagic;
    private final int boostedMagic;
    private final int basePrayer;
    private final int boostedPrayer;
    private final int baseHitpoints;
    private final int boostedHitpoints;
    private final Set<OffensivePrayer> activePrayers;
    private final Stance stance;
    private final boolean assumeBestPotion;
    private final boolean assumeBestPrayer;
    private final boolean onSlayerTask;

    private PlayerCombat(Builder b) {
        this.baseAttack = b.baseAttack;
        this.boostedAttack = b.boostedAttack;
        this.baseStrength = b.baseStrength;
        this.boostedStrength = b.boostedStrength;
        this.baseDefence = b.baseDefence;
        this.boostedDefence = b.boostedDefence;
        this.baseRanged = b.baseRanged;
        this.boostedRanged = b.boostedRanged;
        this.baseMagic = b.baseMagic;
        this.boostedMagic = b.boostedMagic;
        this.basePrayer = b.basePrayer;
        this.boostedPrayer = b.boostedPrayer;
        this.baseHitpoints = b.baseHitpoints;
        this.boostedHitpoints = b.boostedHitpoints;
        this.activePrayers = Collections.unmodifiableSet(EnumSet.copyOf(b.activePrayers));
        this.stance = b.stance;
        this.assumeBestPotion = b.assumeBestPotion;
        this.assumeBestPrayer = b.assumeBestPrayer;
        this.onSlayerTask = b.onSlayerTask;
    }

    public int baseAttack() {
        return baseAttack;
    }

    public int boostedAttack() {
        return boostedAttack;
    }

    public int baseStrength() {
        return baseStrength;
    }

    public int boostedStrength() {
        return boostedStrength;
    }

    public int baseDefence() {
        return baseDefence;
    }

    public int boostedDefence() {
        return boostedDefence;
    }

    public int baseRanged() {
        return baseRanged;
    }

    public int boostedRanged() {
        return boostedRanged;
    }

    public int baseMagic() {
        return baseMagic;
    }

    public int boostedMagic() {
        return boostedMagic;
    }

    public int basePrayer() {
        return basePrayer;
    }

    public int boostedPrayer() {
        return boostedPrayer;
    }

    public int baseHitpoints() {
        return baseHitpoints;
    }

    public int boostedHitpoints() {
        return boostedHitpoints;
    }

    public Set<OffensivePrayer> activePrayers() {
        return activePrayers;
    }

    public Stance stance() {
        return stance;
    }

    public boolean assumeBestPotion() {
        return assumeBestPotion;
    }

    public boolean assumeBestPrayer() {
        return assumeBestPrayer;
    }

    public boolean onSlayerTask() {
        return onSlayerTask;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
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

        public PlayerCombat build() {
            return new PlayerCombat(this);
        }
    }
}
