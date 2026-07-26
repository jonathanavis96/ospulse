package com.ospulse.combat;

/**
 * Resolves the actual magic autocast speed, in game ticks, for the two
 * weapons known to override the default {@link Spell#CAST_SPEED_TICKS} (5
 * ticks) autocast speed. Deliberately its own small, dependency-light class
 * (rather than loose constants bolted onto {@link Spell} or another inline
 * block in {@link DpsCalculator}) so this narrow, self-contained mechanic
 * stays easy to test and extract on its own.
 *
 * <ul>
 *   <li><b>Twinflame staff</b> (item id 30634 — see {@code GearVariants}):
 *       its combat-styles table lists "Spell (Autocast) = 6 ticks" — i.e. the
 *       staff's own 6-tick speed applies to spellcasting, not only melee.
 *       This is unconditional: every spell cast with it (that the calculator
 *       models) runs at 6 ticks.</li>
 *   <li><b>Harmonised nightmare staff</b> (item id 24423 — bundled
 *       equipment_stats index 14 = 5 ticks, the base/manual-cast speed): the
 *       OSRS Wiki documents "reduces the cast time from 5 (3.0s) to 4 (2.4s)
 *       ticks ... The 4-tick spell speed only applies when autocasting" and
 *       "Manually casting spells with the staff equipped will result in a
 *       5-tick attack speed." It also "can autocast offensive standard
 *       spells, but cannot autocast any other spells (including Ancient
 *       Magicks and the Arceuus spellbook)" — so the 4-tick discount only
 *       ever applies to a {@link Spell.SpellBook#STANDARD} spell; a {@code
 *       null} spell (no spell selected / not autocasting a modelled spell) or
 *       an {@link Spell.SpellBook#ANCIENT} spell gets the plain 5-tick speed
 *       instead, same as with no staff at all.</li>
 * </ul>
 *
 * <p>Every entry point into {@link DpsCalculator} that reaches this class
 * already treats the cast as an autocast (there is no manual-cast modelling
 * anywhere in this calculator), so the Harmonised nightmare staff's
 * "autocasting only" condition collapses to just checking the spell's book.
 */
final class MagicCastSpeed {
    private MagicCastSpeed() {
    }

    /** Twinflame staff: 6 ticks for every spell it fires (its own combat-styles "Spell (Autocast)" entry). */
    static final int TWINFLAME_STAFF_TICKS = 6;

    /** Harmonised nightmare staff: 4 ticks, but ONLY while autocasting an offensive standard spell. */
    static final int HARMONISED_NIGHTMARE_STAFF_AUTOCAST_STANDARD_TICKS = 4;

    /**
     * @param twinflameStaff            true when the worn weapon is the Twinflame staff.
     * @param harmonisedNightmareStaff  true when the worn weapon is the Harmonised nightmare staff.
     * @param spell                     the spell being autocast, or {@code null} (e.g. no spell selected).
     * @return the cast speed in ticks: 6 for the Twinflame staff (regardless of spell), 4 for the
     *         Harmonised nightmare staff autocasting a {@link Spell.SpellBook#STANDARD} spell, or the
     *         default {@link Spell#CAST_SPEED_TICKS} (5) otherwise.
     */
    static int ticksFor(boolean twinflameStaff, boolean harmonisedNightmareStaff, Spell spell) {
        if (twinflameStaff) {
            return TWINFLAME_STAFF_TICKS;
        }
        if (harmonisedNightmareStaff && spell != null && spell.book() == Spell.SpellBook.STANDARD) {
            return HARMONISED_NIGHTMARE_STAFF_AUTOCAST_STANDARD_TICKS;
        }
        return Spell.CAST_SPEED_TICKS;
    }
}
