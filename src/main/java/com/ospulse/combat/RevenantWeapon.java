package com.ospulse.combat;

/**
 * Craw's bow / Viggora's chainmace / Thammaron's sceptre — the three
 * revenant-cave weapons that, while charged with revenant ether, grant a
 * flat accuracy AND damage boost vs any NPC recognised as being in the
 * Wilderness by {@link WildernessMonsterRepository} — a curated SUBSET of
 * real Wilderness monsters, not literally "any" (see that class's javadoc
 * and its README for what is/isn't covered and why). Mirrors the {@code
 * DemonbaneWeapon}/{@code DragonHunterWeapon} enum shape: a plain
 * multiplicative floor step {@link DpsCalculator} applies to both the
 * attack roll and max hit.
 *
 * <p><b>All three are currently +50% accuracy / +50% damage — verified
 * individually against the OSRS Wiki 2026-07-27, NOT assumed symmetric just
 * because the design spec's summary table said so:</b>
 * <ul>
 * <li>Craw's bow: "an additional 50% ranged accuracy and damage boost is
 * applied when attacking any NPC in the Wilderness" (OSRS Wiki, Craw's
 * bow).</li>
 * <li>Viggora's chainmace: "an additional 50% melee accuracy and damage
 * boost is applied when attacking any NPC in the Wilderness" (OSRS Wiki,
 * Viggora's chainmace).</li>
 * <li>Thammaron's sceptre: "an additional 50% magic accuracy and damage
 * boost is applied when attacking any NPC in the Wilderness" (OSRS Wiki,
 * Thammaron's sceptre).</li>
 * </ul>
 * <b>Thammaron's sceptre specifically was flagged in review as asymmetric
 * (2/1 accuracy, 5/4 damage) — that is the PRE-2023 value, not the current
 * one.</b> The sceptre's own wiki changelog records: 14 September 2022,
 * accuracy 100%→50% and damage 25%→50%, reverted the next day as
 * unintentional; then reapplied for real on 25 January 2023 ("Wilderness
 * Boss Rework") with the identical wording. No changelog entry since has
 * touched these percentages, so the live value today is the symmetric
 * 50%/50% already implemented here — the review finding was based on stale
 * data. Each enum constant still takes independent accuracy/damage {@link
 * Fraction}s (not one shared value) specifically so a future weapon whose
 * real numbers DO differ can be added without a structural change, and so
 * {@code RevenantWeaponEffectTest} can pin each weapon's own pair
 * independently.
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
 * @see <a href="https://oldschool.runescape.wiki/w/Viggora%27s_chainmace">Viggora's chainmace</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Thammaron%27s_sceptre">Thammaron's sceptre</a> (see the "Changes" section for the 2022/2023 percentage history)
 */
public enum RevenantWeapon {
    NONE(Fraction.ONE, Fraction.ONE, null),
    /** Craw's bow (ranged): +50% accuracy and +50% damage vs any curated Wilderness NPC. */
    CRAWS_BOW(new Fraction(3, 2), new Fraction(3, 2), CombatStyle.RANGED),
    /** Viggora's chainmace (melee, any style): +50% accuracy and +50% damage vs any curated Wilderness NPC. */
    VIGGORAS_CHAINMACE(new Fraction(3, 2), new Fraction(3, 2), CombatStyle.STAB),
    /**
     * Thammaron's sceptre (magic): +50% accuracy and +50% damage vs any
     * curated Wilderness NPC — CURRENT value since the 25 January 2023
     * "Wilderness Boss Rework" (previously an asymmetric 100%/25% before
     * that update; see the class javadoc).
     */
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
