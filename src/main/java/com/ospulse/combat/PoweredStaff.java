package com.ospulse.combat;

/**
 * Powered staves whose built-in spell's max hit scales with the player's
 * (boosted) Magic level. Formulas are raw FACTS hand-transcribed from each
 * weapon's OSRS Wiki page (verified 2026-07-03): all are
 * {@code floor(magic / 3) + offset} —
 * Trident of the seas −5 ("⌊Magic/3⌋−5", 4t), Trident of the swamp −2
 * ("increases the base maximum hit by 3" over the seas trident; 23 at 75),
 * Sanguinesti staff −1 ("up to 32 at Magic 99"), Tumeken's shadow +1
 * ("up to 34 at Magic 99 without any magic damage bonus").
 *
 * <p><b>Tumeken's shadow is approximate:</b> its passive also multiplies the
 * worn equipment's magic attack bonus and magic damage by 3 (4 inside ToA),
 * with its own caps — that multiplier is NOT yet modelled, so shadow results
 * set {@link DpsResult#baseEstimate()}. Warped sceptre, Accursed sceptre,
 * salamanders and the Crystal staff line are TODO (formulas not yet
 * transcribed/verified).
 */
public enum PoweredStaff {
    NONE(0, false),
    TRIDENT_OF_THE_SEAS(-5, false),
    TRIDENT_OF_THE_SWAMP(-2, false),
    SANGUINESTI_STAFF(-1, false),
    TUMEKENS_SHADOW(1, true);

    private final int maxHitOffset;
    private final boolean approximate;

    PoweredStaff(int maxHitOffset, boolean approximate) {
        this.maxHitOffset = maxHitOffset;
        this.approximate = approximate;
    }

    /** True for every real powered staff (anything but {@link #NONE}). */
    public boolean applies() {
        return this != NONE;
    }

    /** Built-in spell max hit at the given (boosted) Magic level: {@code floor(magic/3) + offset}, never negative. */
    public int maxHitAt(int boostedMagicLevel) {
        return Math.max(0, Math.floorDiv(boostedMagicLevel, 3) + maxHitOffset);
    }

    /** True when a known passive on this staff is not fully modelled yet (Tumeken's 3x gear-bonus multiplier). */
    public boolean approximate() {
        return approximate;
    }
}
