package com.ospulse.combat;

/**
 * Summed offensive + defensive gear bonuses for one loadout (the sum across
 * all worn slots). Field names mirror RuneLite's
 * {@code net.runelite.client.game.ItemEquipmentStats} so a caller can add
 * per-slot stats directly without renaming anything.
 * <p>
 * Immutable; build one with {@link #builder()} and accumulate per-slot
 * stats via {@link Builder#add(EquipmentStats)}.
 */
public final class EquipmentStats {
    private final int astab;
    private final int aslash;
    private final int acrush;
    private final int amagic;
    private final int arange;
    private final int dstab;
    private final int dslash;
    private final int dcrush;
    private final int dmagic;
    private final int drange;
    private final int str;
    private final int rstr;
    private final double mdmg;
    private final int prayer;
    private final int weaponSpeedTicks;
    private final boolean isTwoHanded;
    private final SalveType salveType;
    private final SlayerHeadgear slayerHeadgear;
    private final VoidSet voidSet;

    private EquipmentStats(Builder b) {
        this.astab = b.astab;
        this.aslash = b.aslash;
        this.acrush = b.acrush;
        this.amagic = b.amagic;
        this.arange = b.arange;
        this.dstab = b.dstab;
        this.dslash = b.dslash;
        this.dcrush = b.dcrush;
        this.dmagic = b.dmagic;
        this.drange = b.drange;
        this.str = b.str;
        this.rstr = b.rstr;
        this.mdmg = b.mdmg;
        this.prayer = b.prayer;
        this.weaponSpeedTicks = b.weaponSpeedTicks;
        this.isTwoHanded = b.isTwoHanded;
        this.salveType = b.salveType;
        this.slayerHeadgear = b.slayerHeadgear;
        this.voidSet = b.voidSet;
    }

    public int astab() {
        return astab;
    }

    public int aslash() {
        return aslash;
    }

    public int acrush() {
        return acrush;
    }

    public int amagic() {
        return amagic;
    }

    public int arange() {
        return arange;
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

    public int str() {
        return str;
    }

    public int rstr() {
        return rstr;
    }

    public double mdmg() {
        return mdmg;
    }

    public int prayer() {
        return prayer;
    }

    public int weaponSpeedTicks() {
        return weaponSpeedTicks;
    }

    public boolean isTwoHanded() {
        return isTwoHanded;
    }

    /** Attack-bonus for the given style (astab/aslash/acrush/arange/amagic). */
    public int attackBonus(CombatStyle style) {
        switch (style) {
            case STAB:
                return astab;
            case SLASH:
                return aslash;
            case CRUSH:
                return acrush;
            case RANGED:
                return arange;
            case MAGIC:
                return amagic;
            default:
                throw new IllegalArgumentException("Unknown style: " + style);
        }
    }

    public SalveType salveType() {
        return salveType;
    }

    public SlayerHeadgear slayerHeadgear() {
        return slayerHeadgear;
    }

    public VoidSet voidSet() {
        return voidSet;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int astab;
        private int aslash;
        private int acrush;
        private int amagic;
        private int arange;
        private int dstab;
        private int dslash;
        private int dcrush;
        private int dmagic;
        private int drange;
        private int str;
        private int rstr;
        private double mdmg;
        private int prayer;
        private int weaponSpeedTicks = 4;
        private boolean isTwoHanded;
        private SalveType salveType = SalveType.NONE;
        private SlayerHeadgear slayerHeadgear = SlayerHeadgear.NONE;
        private VoidSet voidSet = VoidSet.NONE;

        private Builder() {
        }

        /** Accumulates one slot's stats into this loadout (sums every numeric bonus). */
        public Builder add(int astab, int aslash, int acrush, int amagic, int arange,
                            int dstab, int dslash, int dcrush, int dmagic, int drange,
                            int str, int rstr, double mdmg, int prayer) {
            this.astab += astab;
            this.aslash += aslash;
            this.acrush += acrush;
            this.amagic += amagic;
            this.arange += arange;
            this.dstab += dstab;
            this.dslash += dslash;
            this.dcrush += dcrush;
            this.dmagic += dmagic;
            this.drange += drange;
            this.str += str;
            this.rstr += rstr;
            this.mdmg += mdmg;
            this.prayer += prayer;
            return this;
        }

        /** Accumulates the totals of an already-built {@link EquipmentStats} (e.g. merging two loadouts). */
        public Builder add(EquipmentStats other) {
            return add(other.astab, other.aslash, other.acrush, other.amagic, other.arange,
                    other.dstab, other.dslash, other.dcrush, other.dmagic, other.drange,
                    other.str, other.rstr, other.mdmg, other.prayer);
        }

        public Builder weaponSpeedTicks(int ticks) {
            this.weaponSpeedTicks = ticks;
            return this;
        }

        public Builder isTwoHanded(boolean value) {
            this.isTwoHanded = value;
            return this;
        }

        public Builder salveType(SalveType value) {
            this.salveType = value;
            return this;
        }

        public Builder slayerHeadgear(SlayerHeadgear value) {
            this.slayerHeadgear = value;
            return this;
        }

        public Builder voidSet(VoidSet value) {
            this.voidSet = value;
            return this;
        }

        public EquipmentStats build() {
            return new EquipmentStats(this);
        }
    }
}
