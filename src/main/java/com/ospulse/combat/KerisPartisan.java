package com.ospulse.combat;

/**
 * The Keris partisan family's (item ids — see {@code
 * com.ospulse.session.GearVariants}) vs-Kalphite/Scarabite passives: a flat
 * +33% damage bonus every variant carries (Mod Ash, OSRS Wiki talk page:
 * "33%"), and the "of breaching" variant's ADDITIONAL +33% accuracy bonus
 * ("effect persists outside the raid" — i.e. not gated to Tombs of Amascut).
 * Mirrors the {@code DemonbaneWeapon}/{@code DragonHunterWeapon} enum
 * shape: a plain multiplicative floor step {@link DpsCalculator} applies to
 * max hit (and, for breaching, the attack roll) before the target-damage
 * cap — the same "weapon's own passive, separate step" precedent those two
 * classes already establish. The family's OTHER passive (a 1/51 chance to
 * triple the landed damage) is a genuinely different DISTRIBUTION, not a
 * multiplier on the roll's mean alone, so it lives in {@link
 * KerisTripleRoll} instead and bypasses the generic {@code finish()}.
 *
 * <p>Gate: applies only when the target carries {@link
 * MonsterAttribute#KALPHITE} — see {@link Monster#attributes()}. The
 * bundled monster data confirms every Kalphite/Scarab-family monster this
 * matters for (Kalphite Queen's both forms, Kalphite Guardian, Kalphite
 * Worker/Soldier, every "Scarab"-named monster) carries this attribute,
 * with a handful of narrow exceptions (a few Tombs of Amascut scarab
 * entries and one Construction Kalphite soldier variant lack the tag in
 * the bundled data) — see {@code KerisPartisanEffectTest} for the verified
 * list; that data gap is a bundled-monster-data completeness issue, not
 * something this stage's item-mechanic wiring can fix.
 */
public enum KerisPartisan {
    NONE(false),
    /** Keris partisan (base). */
    PARTISAN(false),
    /** Keris partisan of amascut (Tombs of Amascut reward). */
    OF_AMASCUT(false),
    /** Keris partisan of breaching — additionally grants +33% accuracy vs Kalphites/Scarabites. */
    OF_BREACHING(true),
    /** Keris partisan of corruption. */
    OF_CORRUPTION(false),
    /** Keris partisan of the sun. */
    OF_THE_SUN(false);

    /** +33% damage, every variant, per Mod Ash's confirmed figure. */
    static final Fraction DAMAGE_MULT = new Fraction(133, 100);

    private final boolean hasAccuracyBonus;

    KerisPartisan(boolean hasAccuracyBonus) {
        this.hasAccuracyBonus = hasAccuracyBonus;
    }

    /** True only for "of breaching" — the sole variant with a vs-Kalphite accuracy bonus. */
    boolean hasAccuracyBonus() {
        return hasAccuracyBonus;
    }
}
