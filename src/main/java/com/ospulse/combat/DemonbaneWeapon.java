package com.ospulse.combat;

/**
 * Demonbane melee weapons (the Silverlight → Emberlight sword line) and their
 * vs-demon multipliers, per the OSRS Wiki. The bonus applies ONLY when the
 * target carries the {@link MonsterAttribute#DEMON} attribute, and is a
 * SEPARATE multiplicative step that STACKS with the slayer-helm / salve
 * "target gear bonus" (in-game you can run e.g. Arclight + an on-task slayer
 * helm on a demon and both apply — unlike salve vs slayer, which don't stack
 * with each other).
 *
 * <p>The accuracy multiplier scales the attack roll; the damage multiplier
 * scales max hit. Silverlight/Darklight boost damage only (+60%); Arclight and
 * Emberlight boost both accuracy and damage (+70%). Cerberus and almost all
 * demons take the full multiplier — the sole documented exception is Duke
 * Sucellus (30% demonbane resistance → 49%), a per-monster resistance not
 * modelled here (out of the current monster set).
 *
 * <p><b>Scope:</b> only Emberlight is wired for detection today (its live id
 * {@code 29589} is verified); the ranged demonbane line (Scorching bow) and
 * Burning claws are deferred until their live ids and exact vs-demon
 * percentages are cross-checked. The enum constants for the rest of the sword
 * line are defined ready for wiring in {@code GearVariants}.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Demonbane_weapons">Demonbane weapons</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Emberlight">Emberlight</a> (+70% accuracy and damage)
 */
public enum DemonbaneWeapon {
    NONE(Fraction.ONE, Fraction.ONE),
    SILVERLIGHT(Fraction.ONE, new Fraction(8, 5)),          // +60% damage only
    DARKLIGHT(Fraction.ONE, new Fraction(8, 5)),            // +60% damage only
    ARCLIGHT(new Fraction(17, 10), new Fraction(17, 10)),   // +70% accuracy AND damage
    EMBERLIGHT(new Fraction(17, 10), new Fraction(17, 10)); // +70% accuracy AND damage

    private final Fraction accuracyMult;
    private final Fraction damageMult;

    DemonbaneWeapon(Fraction accuracyMult, Fraction damageMult) {
        this.accuracyMult = accuracyMult;
        this.damageMult = damageMult;
    }

    /** True for every demonbane weapon (i.e. anything but {@link #NONE}). */
    public boolean applies() {
        return this != NONE;
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
