package com.ospulse.combat;

/**
 * Which Void Knight set (if any) the player has fully equipped. Void applies
 * a multiplier to the *effective level* calculation (not to gear bonuses),
 * per the OSRS Wiki DPS formula pages.
 *
 * <p>Melee/ranged void: 1.1x effective level (both style variants).
 * Elite ranged void: 1.125x effective ranged level (max hit only differs
 * from regular void; this implementation applies 1.125 uniformly for the
 * elite ranged case per the "Maximum ranged hit" wiki page).
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

    /** Multiplier applied to the ranged effective attack/strength level. */
    public double rangedMultiplier() {
        if (this == RANGED) {
            return 1.1;
        }
        if (this == RANGED_ELITE) {
            return 1.125;
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
