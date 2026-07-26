package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link MagicCastSpeed} — the two weapons known to override the default
 * 5-tick {@link Spell#CAST_SPEED_TICKS} autocast speed.
 */
public class MagicCastSpeedTest {
    @Test
    public void twinflameStaff_alwaysSixTicks_regardlessOfSpell() {
        assertEquals(6, MagicCastSpeed.ticksFor(true, false, Spell.FIRE_STRIKE));
        assertEquals(6, MagicCastSpeed.ticksFor(true, false, Spell.FIRE_WAVE));
        assertEquals(6, MagicCastSpeed.ticksFor(true, false, Spell.ICE_BARRAGE));
        assertEquals(6, MagicCastSpeed.ticksFor(true, false, null));
    }

    @Test
    public void twinflameStaff_takesPrecedenceOverHarmonisedFlag() {
        // Not a real dual-wield scenario (both are two-handed staves), but the
        // function's precedence order should still be well-defined.
        assertEquals(6, MagicCastSpeed.ticksFor(true, true, Spell.FIRE_BOLT));
    }

    @Test
    public void harmonisedNightmareStaff_fourTicks_autocastingStandardSpell() {
        assertEquals(4, MagicCastSpeed.ticksFor(false, true, Spell.FIRE_BOLT));
        assertEquals(4, MagicCastSpeed.ticksFor(false, true, Spell.FIRE_STRIKE));
        assertEquals(4, MagicCastSpeed.ticksFor(false, true, Spell.IBAN_BLAST));
    }

    @Test
    public void harmonisedNightmareStaff_fiveTicks_onAncientMagicks() {
        assertEquals(5, MagicCastSpeed.ticksFor(false, true, Spell.ICE_BARRAGE));
        assertEquals(5, MagicCastSpeed.ticksFor(false, true, Spell.SMOKE_RUSH));
    }

    @Test
    public void harmonisedNightmareStaff_fiveTicks_withNoSpellSelected() {
        assertEquals(5, MagicCastSpeed.ticksFor(false, true, null));
    }

    @Test
    public void noSpecialWeapon_defaultsToFiveTicks() {
        assertEquals(5, MagicCastSpeed.ticksFor(false, false, Spell.FIRE_BOLT));
        assertEquals(5, MagicCastSpeed.ticksFor(false, false, Spell.ICE_BARRAGE));
        assertEquals(5, MagicCastSpeed.ticksFor(false, false, null));
    }
}
