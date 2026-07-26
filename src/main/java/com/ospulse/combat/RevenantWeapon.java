package com.ospulse.combat;

/**
 * Craw's bow / Viggora's chainmace / Thammaron's sceptre — the three
 * revenant-cave weapons that, while charged with revenant ether, grant
 * "+50% accuracy and +50% damage vs any NPC in the Wilderness" (OSRS Wiki,
 * Craw's bow). Mirrors the {@code DemonbaneWeapon}/{@code
 * DragonHunterWeapon} enum shape: a plain multiplicative floor step {@link
 * DpsCalculator} applies to both the attack roll and max hit, gated on the
 * TARGET being in the Wilderness — see {@link WildernessMonsterRepository}
 * for that input, which (per the design spec) is the one genuinely new
 * input among the six §9 mechanics, since the bundled monster data has no
 * location field at all.
 *
 * <p><b>Ether is assumed charged, not modelled.</b> This calculator has no
 * concept of ether charge count (nothing else in the codebase tracks
 * per-item charge state either), so every loadout wearing one of these
 * weapons is treated as fully charged — the state a player is realistically
 * in when equipping it to check DPS. An uncharged/uncharged-and-reverted
 * weapon would not carry this bonus in-game; that state is out of scope
 * here, per the design spec's explicit instruction to assume charged rather
 * than model ether consumption.
 *
 * <p>Each weapon's own combat style: Craw's bow is RANGED-only; Thammaron's
 * sceptre (and its "(a)" ether-enhanced reskin) is MAGIC-only; Viggora's
 * chainmace applies to any melee style (its bonus is not documented as
 * style-restricted the way, say, Osmumten's fang's passive is), mirroring
 * how {@code DemonbaneWeapon}'s melee sword line covers all three melee
 * styles via the same STAB-as-"melee family" sentinel.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Craw%27s_bow">Craw's bow</a>
 */
public enum RevenantWeapon {
    NONE(Fraction.ONE, Fraction.ONE, null),
    /** Craw's bow (ranged): +50% accuracy and damage vs any Wilderness NPC. */
    CRAWS_BOW(new Fraction(3, 2), new Fraction(3, 2), CombatStyle.RANGED),
    /** Viggora's chainmace (melee, any style): +50% accuracy and damage vs any Wilderness NPC. */
    VIGGORAS_CHAINMACE(new Fraction(3, 2), new Fraction(3, 2), CombatStyle.STAB),
    /** Thammaron's sceptre (magic): +50% accuracy and damage vs any Wilderness NPC. */
    THAMMARONS_SCEPTRE(new Fraction(3, 2), new Fraction(3, 2), CombatStyle.MAGIC);

    private final Fraction accuracyMult;
    private final Fraction damageMult;
    /** Style family marker: STAB is the melee-family sentinel (see class javadoc), RANGED/MAGIC are exact. */
    private final CombatStyle styleFamily;

    RevenantWeapon(Fraction accuracyMult, Fraction damageMult, CombatStyle styleFamily) {
        this.accuracyMult = accuracyMult;
        this.damageMult = damageMult;
        this.styleFamily = styleFamily;
    }

    /** True when this weapon's Wilderness passive applies to an attack of the given style. */
    public boolean appliesTo(CombatStyle style) {
        if (this == NONE || style == null) {
            return false;
        }
        if (styleFamily.isMelee()) {
            return style.isMelee();
        }
        return style == styleFamily;
    }

    /** Multiplier on the attack roll vs a Wilderness target. */
    public Fraction accuracyMult() {
        return accuracyMult;
    }

    /** Multiplier on max hit vs a Wilderness target. */
    public Fraction damageMult() {
        return damageMult;
    }
}
