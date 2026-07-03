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
 * <p><b>Stacking:</b> unlike salve/slayer/demonbane (one highest-wins slot),
 * dragonbane is the weapon's own passive and is applied as a SEPARATE
 * multiplicative floor step on top of that slot (per the approved design).
 * The wiki notes the crossbow's bonus stacks "additively with the Slayer helm
 * (i)" for ranged — a small divergence from this multiplicative model when
 * both apply (1.30×1.15 vs 1+0.30+0.15 on accuracy); GearScape-parity
 * TODO before trusting on-task-vs-dragon numbers to the last percent.
 *
 * <p>Dragon hunter wand: exists but its exact vs-dragon percentages were not
 * verifiable from the sources checked — flagged TODO rather than guessed
 * (per the item-id/percentage verification rule); until wired, a wand user
 * vs dragons simply gets no dragonbane bonus (a lower bound).
 */
public enum DragonHunterWeapon {
    NONE(Fraction.ONE, Fraction.ONE, null),
    /** Dragon hunter lance (melee): +20% accuracy and +20% damage vs dragons. */
    LANCE(new Fraction(6, 5), new Fraction(6, 5), CombatStyle.STAB),
    /** Dragon hunter crossbow (ranged): +30% accuracy and +25% damage vs dragons. */
    CROSSBOW(new Fraction(13, 10), new Fraction(5, 4), CombatStyle.RANGED);

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
