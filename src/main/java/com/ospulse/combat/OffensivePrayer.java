package com.ospulse.combat;

/**
 * Offensive prayers and their exact effective-level multipliers, taken
 * verbatim from the OSRS Wiki DPS calculator pages (cross-checked against
 * the percentages published on the <a href="https://oldschool.runescape.wiki/w/Prayer">Prayer</a>
 * page, which agree: e.g. Piety "+20% Attack, +23% Strength").
 * <p>
 * Each prayer carries up to five multipliers (melee attack, melee strength,
 * ranged attack, ranged strength, magic attack/accuracy) defaulting to
 * {@code 1.0} (no effect) plus an additive magic-damage percentage (only
 * Mystic Lore/Might/Vigour/Augury contribute one, per the "Primary Magic
 * Damage" section of <a href="https://oldschool.runescape.wiki/w/Maximum_magic_hit">Maximum magic hit</a>).
 * <p>
 * In-game only one offensive prayer can be active per combat style at a
 * time; {@link DpsCalculator} takes the strongest applicable multiplier
 * from {@link PlayerCombat#activePrayers()} for whichever category the
 * chosen {@link CombatStyle} needs.
 */
public enum OffensivePrayer {
    BURST_OF_STRENGTH(1.0, 1.05, 1.0, 1.0, 1.0, 0),
    SUPERHUMAN_STRENGTH(1.0, 1.10, 1.0, 1.0, 1.0, 0),
    ULTIMATE_STRENGTH(1.0, 1.15, 1.0, 1.0, 1.0, 0),
    CLARITY_OF_THOUGHT(1.05, 1.0, 1.0, 1.0, 1.0, 0),
    IMPROVED_REFLEXES(1.10, 1.0, 1.0, 1.0, 1.0, 0),
    INCREDIBLE_REFLEXES(1.15, 1.0, 1.0, 1.0, 1.0, 0),
    CHIVALRY(1.15, 1.18, 1.0, 1.0, 1.0, 0),
    PIETY(1.20, 1.23, 1.0, 1.0, 1.0, 0),
    SHARP_EYE(1.0, 1.0, 1.05, 1.05, 1.0, 0),
    HAWK_EYE(1.0, 1.0, 1.10, 1.10, 1.0, 0),
    EAGLE_EYE(1.0, 1.0, 1.15, 1.15, 1.0, 0),
    DEADEYE(1.0, 1.0, 1.18, 1.18, 1.0, 0),
    RIGOUR(1.0, 1.0, 1.20, 1.23, 1.0, 0),
    MYSTIC_WILL(1.0, 1.0, 1.0, 1.0, 1.05, 0),
    MYSTIC_LORE(1.0, 1.0, 1.0, 1.0, 1.10, 1),
    MYSTIC_MIGHT(1.0, 1.0, 1.0, 1.0, 1.15, 2),
    MYSTIC_VIGOUR(1.0, 1.0, 1.0, 1.0, 1.18, 3),
    AUGURY(1.0, 1.0, 1.0, 1.0, 1.25, 4);

    private final double meleeAttackMult;
    private final double meleeStrengthMult;
    private final double rangedAttackMult;
    private final double rangedStrengthMult;
    private final double magicAccuracyMult;
    private final double magicDamagePercent;

    OffensivePrayer(double meleeAttackMult, double meleeStrengthMult, double rangedAttackMult,
                    double rangedStrengthMult, double magicAccuracyMult, double magicDamagePercent) {
        this.meleeAttackMult = meleeAttackMult;
        this.meleeStrengthMult = meleeStrengthMult;
        this.rangedAttackMult = rangedAttackMult;
        this.rangedStrengthMult = rangedStrengthMult;
        this.magicAccuracyMult = magicAccuracyMult;
        this.magicDamagePercent = magicDamagePercent;
    }

    double meleeAttackMult() {
        return meleeAttackMult;
    }

    double meleeStrengthMult() {
        return meleeStrengthMult;
    }

    double rangedAttackMult() {
        return rangedAttackMult;
    }

    double rangedStrengthMult() {
        return rangedStrengthMult;
    }

    double magicAccuracyMult() {
        return magicAccuracyMult;
    }

    double magicDamagePercent() {
        return magicDamagePercent;
    }
}
