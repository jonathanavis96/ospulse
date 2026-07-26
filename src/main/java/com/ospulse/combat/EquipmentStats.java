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
    private final DemonbaneWeapon demonbaneWeapon;
    private final DragonHunterWeapon dragonHunterWeapon;
    private final boolean twistedBow;
    private final boolean osmumtensFang;
    private final boolean twinflameStaff;
    private final boolean harmonisedNightmareStaff;
    private final PoweredStaff poweredStaff;
    private final Tome tome;
    private final boolean tonalzticsOfRalosCharged;
    private final boolean scytheOfVitur;
    private final boolean colossalBlade;
    private final KerisPartisan kerisPartisan;
    private final RevenantWeapon revenantWeapon;

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
        this.demonbaneWeapon = b.demonbaneWeapon;
        this.dragonHunterWeapon = b.dragonHunterWeapon;
        this.twistedBow = b.twistedBow;
        this.osmumtensFang = b.osmumtensFang;
        this.twinflameStaff = b.twinflameStaff;
        this.harmonisedNightmareStaff = b.harmonisedNightmareStaff;
        this.poweredStaff = b.poweredStaff;
        this.tome = b.tome;
        this.tonalzticsOfRalosCharged = b.tonalzticsOfRalosCharged;
        this.scytheOfVitur = b.scytheOfVitur;
        this.colossalBlade = b.colossalBlade;
        this.kerisPartisan = b.kerisPartisan;
        this.revenantWeapon = b.revenantWeapon;
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

    public DemonbaneWeapon demonbaneWeapon() {
        return demonbaneWeapon;
    }

    public DragonHunterWeapon dragonHunterWeapon() {
        return dragonHunterWeapon;
    }

    /** True when the worn weapon is the Twisted bow (its vs-target magic scaling applies to ranged attacks). */
    public boolean twistedBow() {
        return twistedBow;
    }

    /**
     * True when the worn weapon is Osmumten's fang (or its "(or)" cosmetic
     * variant / the Fang of the hound re-skin) — its double-accuracy-roll and
     * compressed-damage-roll passives apply to STAB attacks; see
     * {@link DpsCalculator#computeMelee}.
     */
    public boolean osmumtensFang() {
        return osmumtensFang;
    }

    /**
     * True when the worn weapon is the Twinflame staff (item id 30634) — its
     * 6-tick cast speed and elemental Bolt/Blast/Wave second-hit passive
     * apply to magic casts; see {@link MagicCastSpeed} / {@link
     * TwinflameSecondHit} / {@link Spell#twinflameEligible()}.
     */
    public boolean twinflameStaff() {
        return twinflameStaff;
    }

    /**
     * True when the worn weapon is the Harmonised nightmare staff (item id
     * 24423) — its 4-tick autocast speed (offensive standard spells only, 5
     * ticks otherwise) applies to magic casts; see {@link MagicCastSpeed}.
     */
    public boolean harmonisedNightmareStaff() {
        return harmonisedNightmareStaff;
    }

    public PoweredStaff poweredStaff() {
        return poweredStaff;
    }

    /** The charged shield-slot elemental tome (fire/water/earth), or {@link Tome#NONE}. */
    public Tome tome() {
        return tome;
    }

    /**
     * True when the worn weapon is the CHARGED Tonalztics of Ralos (item id
     * 28922) — fires two full, independent damage rolls per attack rather
     * than the ordinary single roll; see {@link TonalzticsDualHit}. The
     * uncharged variant (28919) leaves this {@code false}.
     */
    public boolean tonalzticsOfRalosCharged() {
        return tonalzticsOfRalosCharged;
    }

    /**
     * True when the worn weapon is any Scythe of Vitur variant (Holy/Sanguine
     * reskins included) — its target-size-scaled multi-hit cascade applies to
     * melee attacks; see {@link ScytheCascade}.
     */
    public boolean scytheOfVitur() {
        return scytheOfVitur;
    }

    /**
     * True when the worn weapon is the Colossal blade (item id 27021) — a
     * flat {@code +2 * min(targetSize, 5)} bonus (up to +10) applies to its
     * melee max hit, stacking with the on-task slayer helm/black mask; see
     * {@link DpsCalculator#computeMelee}.
     */
    public boolean colossalBlade() {
        return colossalBlade;
    }

    /**
     * The worn weapon's {@link KerisPartisan} variant ({@link
     * KerisPartisan#NONE} if not a Keris) — its vs-Kalphite/Scarabite
     * damage/accuracy bonus and triple-damage roll apply when the target
     * carries {@link MonsterAttribute#KALPHITE}; see {@link
     * DpsCalculator#computeMelee} / {@link KerisTripleRoll}.
     */
    public KerisPartisan kerisPartisan() {
        return kerisPartisan;
    }

    /**
     * The worn weapon's {@link RevenantWeapon} ({@link RevenantWeapon#NONE}
     * if not one) — Craw's bow / Viggora's chainmace / Thammaron's sceptre's
     * +50% accuracy/damage vs any Wilderness NPC; see {@link
     * DpsCalculator}'s per-style compute methods and {@link
     * WildernessMonsterRepository}.
     */
    public RevenantWeapon revenantWeapon() {
        return revenantWeapon;
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
        private DemonbaneWeapon demonbaneWeapon = DemonbaneWeapon.NONE;
        private DragonHunterWeapon dragonHunterWeapon = DragonHunterWeapon.NONE;
        private boolean twistedBow;
        private boolean osmumtensFang;
        private boolean twinflameStaff;
        private boolean harmonisedNightmareStaff;
        private PoweredStaff poweredStaff = PoweredStaff.NONE;
        private Tome tome = Tome.NONE;
        private boolean tonalzticsOfRalosCharged;
        private boolean scytheOfVitur;
        private boolean colossalBlade;
        private KerisPartisan kerisPartisan = KerisPartisan.NONE;
        private RevenantWeapon revenantWeapon = RevenantWeapon.NONE;

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

        public Builder demonbaneWeapon(DemonbaneWeapon value) {
            this.demonbaneWeapon = value;
            return this;
        }

        public Builder dragonHunterWeapon(DragonHunterWeapon value) {
            this.dragonHunterWeapon = value;
            return this;
        }

        public Builder twistedBow(boolean value) {
            this.twistedBow = value;
            return this;
        }

        public Builder osmumtensFang(boolean value) {
            this.osmumtensFang = value;
            return this;
        }

        public Builder twinflameStaff(boolean value) {
            this.twinflameStaff = value;
            return this;
        }

        public Builder harmonisedNightmareStaff(boolean value) {
            this.harmonisedNightmareStaff = value;
            return this;
        }

        public Builder tome(Tome value) {
            this.tome = value;
            return this;
        }

        public Builder poweredStaff(PoweredStaff value) {
            this.poweredStaff = value;
            return this;
        }

        public Builder tonalzticsOfRalosCharged(boolean value) {
            this.tonalzticsOfRalosCharged = value;
            return this;
        }

        public Builder scytheOfVitur(boolean value) {
            this.scytheOfVitur = value;
            return this;
        }

        public Builder colossalBlade(boolean value) {
            this.colossalBlade = value;
            return this;
        }

        public Builder kerisPartisan(KerisPartisan value) {
            this.kerisPartisan = value;
            return this;
        }

        public Builder revenantWeapon(RevenantWeapon value) {
            this.revenantWeapon = value;
            return this;
        }

        public EquipmentStats build() {
            return new EquipmentStats(this);
        }
    }
}
