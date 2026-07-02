package com.ospulse.combat;

/**
 * Which variant of the Salve amulet (if any) the player is wearing. Only
 * applies its bonus when the target has the {@link MonsterAttribute#UNDEAD}
 * attribute.
 *
 * <p>Source: <a href="https://oldschool.runescape.wiki/w/Maximum_melee_hit">Maximum melee hit</a>
 * (plain/e melee bonus), <a href="https://oldschool.runescape.wiki/w/Maximum_ranged_hit">Maximum
 * ranged hit</a> (i/ei ranged bonus) and
 * <a href="https://oldschool.runescape.wiki/w/Damage_per_second/Magic">DPS/Magic</a> +
 * <a href="https://oldschool.runescape.wiki/w/Maximum_magic_hit">Maximum magic hit</a>
 * (i/ei magic accuracy + damage bonus).
 *
 * <p>Salve and Slayer helm(i)/black mask(i) bonuses never stack; callers
 * (see {@link DpsCalculator}) apply Salve first and skip the Slayer bonus
 * when Salve already applied, matching the wiki's documented mutual
 * exclusion.
 */
public enum SalveType {
    /** Not worn. */
    NONE,
    /** Plain Salve amulet: melee only, x7/6. */
    SALVE,
    /** Salve amulet (e): melee only, x1.2. */
    SALVE_E,
    /** Salve amulet (i): melee x7/6, ranged x7/6 (accuracy+damage), magic accuracy x1.15 / damage +15%. */
    SALVE_I,
    /** Salve amulet (ei): melee x1.2, ranged x1.2, magic accuracy x1.15 / damage +20%. */
    SALVE_EI;

    public boolean isImbued() {
        return this == SALVE_I || this == SALVE_EI;
    }

    /** Target-specific gear-bonus fraction applied to melee max hit and attack roll (1/1 = no effect). */
    public Fraction meleeBonus() {
        switch (this) {
            case SALVE:
            case SALVE_I:
                return new Fraction(7, 6);
            case SALVE_E:
            case SALVE_EI:
                return new Fraction(6, 5); // 1.2
            default:
                return Fraction.ONE;
        }
    }

    /** Target-specific gear-bonus fraction applied to ranged max hit and attack roll (1/1 = no effect). Only (i)/(ei) affect ranged. */
    public Fraction rangedBonus() {
        switch (this) {
            case SALVE_I:
                return new Fraction(7, 6);
            case SALVE_EI:
                return new Fraction(6, 5); // 1.2
            default:
                return Fraction.ONE;
        }
    }

    /** Magic accuracy-roll multiplier (only (i)/(ei) affect magic, both at the same 1.15x per the wiki). */
    public Fraction magicAccuracyBonus() {
        return isImbued() ? new Fraction(23, 20) : Fraction.ONE; // 1.15
    }

    /** Additive magic-damage percentage folded into the magic max hit calc. */
    public double magicDamagePercent() {
        if (this == SALVE_I) {
            return 15.0;
        }
        if (this == SALVE_EI) {
            return 20.0;
        }
        return 0.0;
    }
}
