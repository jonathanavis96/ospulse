package com.ospulse.combat;

/**
 * Dragon Hunter ("dragonbane") weapons and their vs-dragon multipliers, per
 * each weapon's OSRS Wiki page (verified 2026-07-03): Dragon hunter lance
 * "20% increased accuracy and damage when fighting draconic creatures";
 * Dragon hunter crossbow "30% increase in ranged accuracy and 25% increase in
 * damage". Applies only when the target carries
 * {@link MonsterAttribute#DRAGON} (the bundled data already tags hydras,
 * wyverns and Great Olm; Elvarg/revenant-dragon exceptions are not in the
 * monster set).
 *
 * <p><b>Stacking (per the wiki DPS calculator, weirdgloop
 * {@code PlayerVsNPCCalc.ts}):</b> dragonbane is the weapon's own passive and
 * is applied as a SEPARATE multiplicative floor step on top of the
 * salve/slayer slot — with one exception: the ranged MAX HIT folds the DHCB's
 * +25% damage additively into an on-task IMBUED slayer helm's 23/20 step
 * ((23+5)/20, one floor — the wiki's "additive with the Slayer helm (i)");
 * DHCB accuracy always stays its own separate ×13/10 step. See
 * {@code DpsCalculator.computeRanged}.
 *
 * <p>Dragon hunter wand (magic): grants +75% magic accuracy and +40% magic
 * damage vs draconic creatures, per the OSRS Wiki (verified 2026-07-03).
 */
public enum DragonHunterWeapon {
    NONE(Fraction.ONE, Fraction.ONE, null),
    /** Dragon hunter lance (melee): +20% accuracy and +20% damage vs dragons. */
    LANCE(new Fraction(6, 5), new Fraction(6, 5), CombatStyle.STAB),
    /** Dragon hunter crossbow (ranged): +30% accuracy and +25% damage vs dragons. */
    CROSSBOW(new Fraction(13, 10), new Fraction(5, 4), CombatStyle.RANGED),
    /** Dragon hunter wand (magic): +75% accuracy and +40% damage vs dragons. */
    WAND(new Fraction(7, 4), new Fraction(7, 5), CombatStyle.MAGIC);

    private final Fraction accuracyMult;
    private final Fraction damageMult;
    private final CombatStyle styleFamily;

    DragonHunterWeapon(Fraction accuracyMult, Fraction damageMult, CombatStyle styleFamily) {
        this.accuracyMult = accuracyMult;
        this.damageMult = damageMult;
        this.styleFamily = styleFamily;
    }

    /** True when this weapon's dragonbane passive applies to an attack of the given style. */
    public boolean appliesTo(CombatStyle style) {
        if (this == NONE || style == null) {
            return false;
        }
        // The lance is a melee weapon: its passive covers all three melee styles.
        if (this == LANCE) {
            return style.isMelee();
        }
        return style == styleFamily;
    }

    /** Multiplier on the attack roll vs dragons. */
    public Fraction accuracyMult() {
        return accuracyMult;
    }

    /** Multiplier on max hit vs dragons. */
    public Fraction damageMult() {
        return damageMult;
    }
}
