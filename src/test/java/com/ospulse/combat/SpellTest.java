package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Weapon-requirement metadata on {@link Spell}: Iban Blast is castable ONLY
 * with Iban's staff (regular id 1409, the other charged id 1410, or the
 * upgraded Iban's staff (u) 12658) — see the OSRS Wiki's Iban Blast page.
 * Every other spell in the enum has no weapon requirement and is castable
 * with any weapon (the ranking method still separately filters by whether the
 * weapon is even a magic weapon at all; this is just the extra per-spell gate).
 */
public class SpellTest {
    private static final int IBANS_STAFF = 1409;
    private static final int IBANS_STAFF_OTHER_CHARGED_ID = 1410;
    private static final int IBANS_STAFF_U = 12658;
    private static final int DRAGON_HUNTER_WAND = 24422;
    private static final int STAFF_OF_FIRE = 1387;

    @Test
    public void ibanBlastIsCastableOnlyWithIbansStaffVariants() {
        assertTrue(Spell.IBAN_BLAST.isCastableWith(IBANS_STAFF));
        assertTrue(Spell.IBAN_BLAST.isCastableWith(IBANS_STAFF_OTHER_CHARGED_ID));
        assertTrue(Spell.IBAN_BLAST.isCastableWith(IBANS_STAFF_U));
    }

    @Test
    public void ibanBlastIsNotCastableWithOtherStaves() {
        assertFalse(Spell.IBAN_BLAST.isCastableWith(DRAGON_HUNTER_WAND));
        assertFalse(Spell.IBAN_BLAST.isCastableWith(STAFF_OF_FIRE));
        assertFalse(Spell.IBAN_BLAST.isCastableWith(-1));
    }

    @Test
    public void spellsWithNoWeaponRequirementAreCastableWithAnyWeapon() {
        assertTrue(Spell.FIRE_SURGE.isCastableWith(STAFF_OF_FIRE));
        assertTrue(Spell.FIRE_SURGE.isCastableWith(DRAGON_HUNTER_WAND));
        assertTrue(Spell.FIRE_SURGE.isCastableWith(-1));
        assertTrue(Spell.ICE_BARRAGE.isCastableWith(STAFF_OF_FIRE));
    }

    /**
     * {@link Spell#twinflameEligible()} — Bolt/Blast/Wave across all four
     * elements only, and NOT the name-substring trap the reference JS
     * implementation falls into (Iban Blast).
     */
    @Test
    public void twinflameEligible_boltBlastWaveAcrossAllFourElements() {
        assertTrue(Spell.WIND_BOLT.twinflameEligible());
        assertTrue(Spell.WATER_BOLT.twinflameEligible());
        assertTrue(Spell.EARTH_BOLT.twinflameEligible());
        assertTrue(Spell.FIRE_BOLT.twinflameEligible());
        assertTrue(Spell.WIND_BLAST.twinflameEligible());
        assertTrue(Spell.WATER_BLAST.twinflameEligible());
        assertTrue(Spell.EARTH_BLAST.twinflameEligible());
        assertTrue(Spell.FIRE_BLAST.twinflameEligible());
        assertTrue(Spell.WIND_WAVE.twinflameEligible());
        assertTrue(Spell.WATER_WAVE.twinflameEligible());
        assertTrue(Spell.EARTH_WAVE.twinflameEligible());
        assertTrue(Spell.FIRE_WAVE.twinflameEligible());
    }

    @Test
    public void twinflameEligible_falseForStrikeAndSurge() {
        assertFalse(Spell.WIND_STRIKE.twinflameEligible());
        assertFalse(Spell.FIRE_STRIKE.twinflameEligible());
        assertFalse(Spell.WIND_SURGE.twinflameEligible());
        assertFalse(Spell.FIRE_SURGE.twinflameEligible());
    }

    @Test
    public void twinflameEligible_falseForIbanBlast_nameSubstringTrap() {
        // Iban Blast's display name contains "Blast" - the reference JS
        // implementation's name.includes('Blast') gate would wrongly match it.
        // It has no Element/Tier, so twinflameEligible() must be false.
        assertFalse(Spell.IBAN_BLAST.twinflameEligible());
        assertEquals(null, Spell.IBAN_BLAST.element());
        assertEquals(null, Spell.IBAN_BLAST.tier());
    }

    @Test
    public void twinflameEligible_falseForNonElementalSpells() {
        assertFalse(Spell.CRUMBLE_UNDEAD.twinflameEligible());
        assertFalse(Spell.SARADOMIN_STRIKE.twinflameEligible());
        assertFalse(Spell.CLAWS_OF_GUTHIX.twinflameEligible());
        assertFalse(Spell.FLAMES_OF_ZAMORAK.twinflameEligible());
        assertFalse(Spell.ICE_BARRAGE.twinflameEligible());
        assertFalse(Spell.SMOKE_RUSH.twinflameEligible());
    }
}
