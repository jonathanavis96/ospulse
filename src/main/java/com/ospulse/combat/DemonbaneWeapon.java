package com.ospulse.combat;

/**
 * Demonbane weapons and their vs-demon multipliers, per each weapon's OSRS
 * Wiki page: the melee Silverlight → Emberlight sword line and the ranged
 * Scorching bow. The bonus applies ONLY when the target carries the
 * {@link MonsterAttribute#DEMON} attribute and only for the weapon's own
 * combat style family (see {@link #appliesTo(CombatStyle)}).
 *
 * <p><b>Stacking (per the wiki DPS calculator, weirdgloop
 * {@code PlayerVsNPCCalc.ts} — our GearScape-parity oracle):</b> demonbane is
 * the weapon's OWN passive and does NOT live in the single salve/slayer
 * target-bonus slot. It is applied as a separate multiplicative floor step
 * AFTER that slot on both the attack roll and max hit — so it stacks with the
 * on-task slayer helm and with salve. One documented exception: for the
 * RANGED max hit only, the Scorching bow's +30% damage folds ADDITIVELY into
 * an on-task IMBUED slayer helm/black mask's 23/20 step (a single floor of
 * (23+6)/20 = 29/20 — the wiki's "total damage boost of 45%"); see
 * {@link DpsCalculator}.
 *
 * <p>The accuracy multiplier scales the attack roll; the damage multiplier
 * scales max hit. Silverlight/Darklight boost damage only (+60%); Arclight
 * and Emberlight boost both (+70%); the Scorching bow boosts both (+30%).
 * Cerberus and almost all demons take the full multiplier — the sole
 * documented exception is Duke Sucellus (30% demonbane resistance), a
 * per-monster resistance not modelled here (out of the current monster set).
 *
 * <p><b>Scope:</b> the sword line and the Scorching bow are wired in
 * {@code GearVariants}; Burning claws (+5%) and the magic demonbane path
 * (Purging staff, which doubles demonbane-SPELL bonuses rather than granting
 * a flat passive) are deferred.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Demonbane_weapons">Demonbane weapons</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Emberlight">Emberlight</a> (+70% accuracy and damage)
 * @see <a href="https://oldschool.runescape.wiki/w/Scorching_bow">Scorching bow</a> (+30% accuracy and damage, damage additive with slayer helm (i))
 */
public enum DemonbaneWeapon {
    NONE(Fraction.ONE, Fraction.ONE, null),
    /** Silverlight (melee): +60% damage only. */
    SILVERLIGHT(Fraction.ONE, new Fraction(8, 5), CombatStyle.STAB),
    /** Darklight (melee): +60% damage only. */
    DARKLIGHT(Fraction.ONE, new Fraction(8, 5), CombatStyle.STAB),
    /** Arclight (melee): +70% accuracy AND damage. */
    ARCLIGHT(new Fraction(17, 10), new Fraction(17, 10), CombatStyle.STAB),
    /** Emberlight (melee): +70% accuracy AND damage. */
    EMBERLIGHT(new Fraction(17, 10), new Fraction(17, 10), CombatStyle.STAB),
    /** Scorching bow (ranged): +30% accuracy AND damage. */
    SCORCHING_BOW(new Fraction(13, 10), new Fraction(13, 10), CombatStyle.RANGED);

    private final Fraction accuracyMult;
    private final Fraction damageMult;
    /** Style family marker: any melee style for the sword line, RANGED for the bow. */
    private final CombatStyle styleFamily;

    DemonbaneWeapon(Fraction accuracyMult, Fraction damageMult, CombatStyle styleFamily) {
        this.accuracyMult = accuracyMult;
        this.damageMult = damageMult;
        this.styleFamily = styleFamily;
    }

    /** True when this weapon's demonbane passive applies to an attack of the given style. */
    public boolean appliesTo(CombatStyle style) {
        if (this == NONE || style == null) {
            return false;
        }
        // The sword line is melee: its passive covers all three melee styles.
        if (styleFamily != null && styleFamily.isMelee()) {
            return style.isMelee();
        }
        return style == styleFamily;
    }

    /** Multiplier on the attack roll vs demons (1/1 for the damage-only Silver/Darklight). */
    public Fraction accuracyMult() {
        return accuracyMult;
    }

    /** Multiplier on max hit vs demons. */
    public Fraction damageMult() {
        return damageMult;
    }
}
