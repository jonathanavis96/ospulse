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

    /** Private snapshot of the builder that made this object; never mutated after construction. */
    private final Builder v;

    private EquipmentStats(Builder b) {
        this.v = b.copy();
    }

    public int astab() {
        return v.astab;
    }

    public int aslash() {
        return v.aslash;
    }

    public int acrush() {
        return v.acrush;
    }

    public int amagic() {
        return v.amagic;
    }

    public int arange() {
        return v.arange;
    }

    public int dstab() {
        return v.dstab;
    }

    public int dslash() {
        return v.dslash;
    }

    public int dcrush() {
        return v.dcrush;
    }

    public int dmagic() {
        return v.dmagic;
    }

    public int drange() {
        return v.drange;
    }

    public int str() {
        return v.str;
    }

    public int rstr() {
        return v.rstr;
    }

    public double mdmg() {
        return v.mdmg;
    }

    public int prayer() {
        return v.prayer;
    }

    public int weaponSpeedTicks() {
        return v.weaponSpeedTicks;
    }

    public boolean isTwoHanded() {
        return v.isTwoHanded;
    }

    /** Attack-bonus for the given style (astab/aslash/acrush/arange/amagic). */
    public int attackBonus(CombatStyle style) {
        switch (style) {
            case STAB:
                return v.astab;
            case SLASH:
                return v.aslash;
            case CRUSH:
                return v.acrush;
            case RANGED:
                return v.arange;
            case MAGIC:
                return v.amagic;
            default:
                throw new IllegalArgumentException("Unknown style: " + style);
        }
    }

    public SalveType salveType() {
        return v.salveType;
    }

    public SlayerHeadgear slayerHeadgear() {
        return v.slayerHeadgear;
    }

    public VoidSet voidSet() {
        return v.voidSet;
    }

    public DemonbaneWeapon demonbaneWeapon() {
        return v.demonbaneWeapon;
    }

    public DragonHunterWeapon dragonHunterWeapon() {
        return v.dragonHunterWeapon;
    }

    /** True when the worn weapon is the Twisted bow (its vs-target magic scaling applies to ranged attacks). */
    public boolean twistedBow() {
        return v.twistedBow;
    }

    /**
     * True when the worn weapon is Osmumten's fang (or its "(or)" cosmetic
     * variant / the Fang of the hound re-skin) — its double-accuracy-roll and
     * compressed-damage-roll passives apply to STAB attacks; see
     * {@link DpsCalculator#computeMelee}.
     */
    public boolean osmumtensFang() {
        return v.osmumtensFang;
    }

    /**
     * True when the worn weapon is the Twinflame staff (item id 30634) — its
     * 6-tick cast speed and elemental Bolt/Blast/Wave second-hit passive
     * apply to magic casts; see {@link MagicCastSpeed} / {@link
     * TwinflameSecondHit} / {@link Spell#twinflameEligible()}.
     */
    public boolean twinflameStaff() {
        return v.twinflameStaff;
    }

    /**
     * True when the worn weapon is the Harmonised nightmare staff (item id
     * 24423) — its 4-tick autocast speed (offensive standard spells only, 5
     * ticks otherwise) applies to magic casts; see {@link MagicCastSpeed}.
     */
    public boolean harmonisedNightmareStaff() {
        return v.harmonisedNightmareStaff;
    }

    public PoweredStaff poweredStaff() {
        return v.poweredStaff;
    }

    /** The charged shield-slot elemental tome (fire/water/earth), or {@link Tome#NONE}. */
    public Tome tome() {
        return v.tome;
    }

    /**
     * True when the worn weapon is the CHARGED Tonalztics of Ralos (item id
     * 28922) — fires two full, independent damage rolls per attack rather
     * than the ordinary single roll; see {@link TonalzticsDualHit}. The
     * uncharged variant (28919) leaves this {@code false}.
     */
    public boolean tonalzticsOfRalosCharged() {
        return v.tonalzticsOfRalosCharged;
    }

    /**
     * True when the worn weapon is the UNCHARGED Tonalztics of Ralos (item id
     * 28919) — fires a SINGLE hit over the same reduced 75% range as the
     * charged form's per-hit roll (not an ordinary full-range 0..M single
     * hit); see {@link TonalzticsDualHit#perHitMaxHit} and {@link
     * com.ospulse.combat.DpsCalculator}'s ranged compute method. The charged
     * variant (28922) leaves this {@code false}.
     */
    public boolean tonalzticsOfRalosUncharged() {
        return v.tonalzticsOfRalosUncharged;
    }

    /**
     * True when the worn weapon is any Scythe of Vitur variant (Holy/Sanguine
     * reskins included) — its target-size-scaled multi-hit cascade applies to
     * melee attacks; see {@link ScytheCascade}.
     */
    public boolean scytheOfVitur() {
        return v.scytheOfVitur;
    }

    /**
     * True when the worn weapon is the Colossal blade (item id 27021) — a
     * flat {@code +2 * min(targetSize, 5)} bonus (up to +10) applies to its
     * melee max hit, stacking with the on-task slayer helm/black mask; see
     * {@link DpsCalculator#computeMelee}.
     */
    public boolean colossalBlade() {
        return v.colossalBlade;
    }

    /**
     * The worn weapon's {@link KerisPartisan} variant ({@link
     * KerisPartisan#NONE} if not a Keris) — its vs-Kalphite/Scarabite
     * damage/accuracy bonus and triple-damage roll apply when the target
     * carries {@link MonsterAttribute#KALPHITE}; see {@link
     * DpsCalculator#computeMelee} / {@link KerisTripleRoll}.
     */
    public KerisPartisan kerisPartisan() {
        return v.kerisPartisan;
    }

    /**
     * The worn weapon's {@link RevenantWeapon} ({@link RevenantWeapon#NONE}
     * if not one) — Craw's bow / Viggora's chainmace / Thammaron's sceptre's
     * +50% accuracy/damage vs any Wilderness NPC; see {@link
     * DpsCalculator}'s per-style compute methods and {@link
     * WildernessMonsterRepository}.
     */
    public RevenantWeapon revenantWeapon() {
        return v.revenantWeapon;
    }

    /**
     * True when the loadout wears the full ACTIVE Crystal armour set (head +
     * body + legs) AND wields an ACTIVE Crystal bow / Bow of Faerdhinen
     * variant — the conditional weapon+armour combo's +15% damage/+30%
     * accuracy applies to ranged attacks; see {@link
     * com.ospulse.session.GearVariants#isActiveCrystalArmourSet}/{@link
     * com.ospulse.session.GearVariants#isActiveCrystalBowOrFaerdhinen} for
     * how this is resolved (both computed once by {@code GearMapper}, since
     * it is the only place with access to every relevant slot).
     */
    public boolean crystalSetBonusActive() {
        return v.crystalSetBonusActive;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements Cloneable {
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
        private boolean tonalzticsOfRalosUncharged;
        private boolean scytheOfVitur;
        private boolean colossalBlade;
        private KerisPartisan kerisPartisan = KerisPartisan.NONE;
        private RevenantWeapon revenantWeapon = RevenantWeapon.NONE;
        private boolean crystalSetBonusActive;

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
            return add(other.v.astab, other.v.aslash, other.v.acrush, other.v.amagic, other.v.arange,
                    other.v.dstab, other.v.dslash, other.v.dcrush, other.v.dmagic, other.v.drange,
                    other.v.str, other.v.rstr, other.v.mdmg, other.v.prayer);
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

        public Builder tonalzticsOfRalosUncharged(boolean value) {
            this.tonalzticsOfRalosUncharged = value;
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

        public Builder crystalSetBonusActive(boolean value) {
            this.crystalSetBonusActive = value;
            return this;
        }

        private Builder copy() {
            try {
                return (Builder) clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        public EquipmentStats build() {
            return new EquipmentStats(this);
        }
    }
}
