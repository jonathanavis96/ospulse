package com.ospulse.combat;

import org.junit.Test;

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
}
