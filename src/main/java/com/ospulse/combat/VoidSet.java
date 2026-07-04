package com.ospulse.combat;

/**
 * Which Void Knight set (if any) the player has fully equipped. Void applies
 * a multiplier to the *effective level* calculation (not to gear bonuses),
 * per the OSRS Wiki DPS formula pages.
 *
 * <p>Melee void: 1.1x effective attack and strength level.
 * Ranged void: 1.1x effective ranged attack (accuracy) AND strength (max
 * hit). Elite ranged void: the elite top/bottom upgrade raises the max-hit
 * (strength) multiplier to 1.125x but leaves the ACCURACY multiplier at the
 * regular 1.1x — only the damage side differs, per weirdgloop/osrs-dps-calc
 * ({@code isWearingEliteRangedVoid()} is checked in {@code getPlayerMaxRangedHit}
 * only, while accuracy uses the plain {@code isWearingRangedVoid()} at 11/10).
 * Magic void ("Void Magic", the mage helm + robe combo, NOT elite): 1.45x
 * effective magic level accuracy, per the wiki's "Multiply by 1.45 if using
 * Void Magic" step. Elite void additionally adds a flat +5% magic damage
 * bonus (modelled as {@code EquipmentStats#mdmg} contribution by the caller,
 * or via {@link #eliteMagicDamageBonusPercent()}).
 */
public enum VoidSet {
    NONE,
    MELEE,
    RANGED,
    RANGED_ELITE,
    MAGIC,
    MAGIC_ELITE;

    /** Multiplier applied to the melee effective attack/strength level. */
    public double meleeMultiplier() {
        return this == MELEE ? 1.1 : 1.0;
    }

    /**
     * Multiplier applied to the ranged effective ATTACK (accuracy) level.
     * Both regular and elite ranged void give +10% here — the elite upgrade
     * only improves the max-hit (strength) side, not accuracy.
     */
    public double rangedAccuracyMultiplier() {
        return (this == RANGED || this == RANGED_ELITE) ? 1.1 : 1.0;
    }

    /**
     * Multiplier applied to the ranged effective STRENGTH (max hit) level:
     * +10% for regular ranged void, +12.5% for elite ranged void.
     */
    public double rangedStrengthMultiplier() {
        if (this == RANGED_ELITE) {
            return 1.125;
        }
        if (this == RANGED) {
            return 1.1;
        }
        return 1.0;
    }

    /** Multiplier applied to the magic effective (accuracy) level. */
    public double magicMultiplier() {
        return (this == MAGIC || this == MAGIC_ELITE) ? 1.45 : 1.0;
    }

    /** Flat magic-damage percentage bonus contributed by Elite Void's mage helm (Tier-A: additive into magic max hit). */
    public double eliteMagicDamageBonusPercent() {
        return this == MAGIC_ELITE ? 5.0 : 0.0;
    }
}
