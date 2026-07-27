package com.ospulse.combat;

/**
 * The result of {@link SpecWeaponSelector#select}: one curated {@link
 * SpecWeapon} plus the score it was chosen with (kept for tests/debugging
 * only — the UI cell renders {@link #displayName()}/{@link #effectSummary()},
 * deliberately never {@link #score()}, since a bare number would imply false
 * precision comparing incommensurable effects; see the design spec §8).
 */
public final class SpecWeaponRecommendation {
    private final SpecWeapon weapon;
    private final double score;

    SpecWeaponRecommendation(SpecWeapon weapon, double score) {
        this.weapon = weapon;
        this.score = score;
    }

    public int itemId() {
        return weapon.itemId();
    }

    public String displayName() {
        return weapon.displayName();
    }

    public SpecRole role() {
        return weapon.role();
    }

    public int specCostPercent() {
        return weapon.specCostPercent();
    }

    public String effectSummary() {
        return weapon.effectSummary();
    }

    /** The expected-damage-per-spec-bar-% score this recommendation was chosen with. Test/debug only — not rendered. */
    public double score() {
        return score;
    }

    /** The full tooltip/readout text: name, cost, and the named effect — never a bare DPS number. */
    public String readoutText() {
        return displayName() + " (" + specCostPercent() + "% spec) — " + effectSummary();
    }
}
