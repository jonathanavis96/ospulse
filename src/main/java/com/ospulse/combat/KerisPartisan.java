package com.ospulse.combat;

/**
 * The Keris partisan family's (item ids — see {@code
 * com.ospulse.session.GearVariants}) vs-Kalphite/Scarabite passives: a
 * per-variant damage bonus (Mod Ash, OSRS Wiki talk page: "33%" for the
 * base line), and the "of breaching" variant's ADDITIONAL +33% accuracy
 * bonus ("effect persists outside the raid" — i.e. not gated to Tombs of
 * Amascut). <b>Exception:</b> {@code OF_AMASCUT} (Keris partisan of
 * amascut, item id 30891) carries only +15% damage, not +33% — the Jewel
 * of Amascut upgrade path "decreas[es] damage bonus against Kalphites and
 * Scabarites but enhanc[es] base stats" (OSRS Wiki), and the reference
 * implementation this codebase's data mirrors, weirdgloop/osrs-dps-calc's
 * {@code PlayerVsNPCCalc.ts}, special-cases exactly this variant to
 * {@code [115, 100]} while every other variant gets {@code [133, 100]}.
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
    NONE(Fraction.ONE, false),
    /** Keris partisan (base): +33% damage vs Kalphites/Scarabites. */
    PARTISAN(new Fraction(133, 100), false),
    /** Keris partisan of amascut (Tombs of Amascut reward): only +15% damage — the Jewel of Amascut upgrade trades damage bonus for base stats. */
    OF_AMASCUT(new Fraction(115, 100), false),
    /** Keris partisan of breaching: +33% damage, and additionally grants +33% accuracy vs Kalphites/Scarabites. */
    OF_BREACHING(new Fraction(133, 100), true),
    /** Keris partisan of corruption: +33% damage vs Kalphites/Scarabites. */
    OF_CORRUPTION(new Fraction(133, 100), false),
    /** Keris partisan of the sun: +33% damage vs Kalphites/Scarabites. */
    OF_THE_SUN(new Fraction(133, 100), false);

    private final Fraction damageMultiplier;
    private final boolean hasAccuracyBonus;

    KerisPartisan(Fraction damageMultiplier, boolean hasAccuracyBonus) {
        this.damageMultiplier = damageMultiplier;
        this.hasAccuracyBonus = hasAccuracyBonus;
    }

    /**
     * This variant's own vs-Kalphite/Scarabite damage multiplier ({@code
     * NONE}'s is {@link Fraction#ONE} but is never actually applied — see
     * {@link DpsCalculator}'s {@code KALPHITE}/{@code NONE} guard).
     */
    Fraction damageMultiplier() {
        return damageMultiplier;
    }

    /** True only for "of breaching" — the sole variant with a vs-Kalphite accuracy bonus. */
    boolean hasAccuracyBonus() {
        return hasAccuracyBonus;
    }
}
