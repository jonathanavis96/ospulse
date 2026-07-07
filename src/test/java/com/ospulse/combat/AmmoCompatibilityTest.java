package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link AmmoCompatibility}: the ammo-slot name classifier, the
 * weapon-side consumed-class resolution (incl. the data quirks: ballistae
 * are categorised "crossbow" but fire javelins; the crystal-bow family and
 * blowpipes use no worn ammo), and the contributes rule that keeps e.g.
 * javelin ranged strength from ever counting behind a bow. All ids verified
 * against the bundled equipment_index.min.json.
 */
public class AmmoCompatibilityTest {

    // Ammo-slot fixtures
    private static final int DRAGON_ARROW = 11212;
    private static final int RUNE_BRUTAL = 4803;
    private static final int RUNITE_BOLTS = 9144;
    private static final int DRAGON_JAVELIN = 19484;
    private static final int HARRALANDER_TAR = 10145;
    private static final int RADAS_BLESSING_4 = 22947;

    // Weapon-slot fixtures
    private static final int MAGIC_SHORTBOW = 861;
    private static final int RUNE_CROSSBOW = 9185;
    private static final int HEAVY_BALLISTA = 19481;
    private static final int LIGHT_BALLISTA = 19478;
    private static final int TOXIC_BLOWPIPE = 12926;
    private static final int CRYSTAL_BOW = 23983;
    private static final int BOW_OF_FAERDHINEN = 25865;
    private static final int CRAWS_BOW = 22550;
    private static final int WEBWEAVER_BOW = 27652;
    private static final int ECLIPSE_ATLATL = 29000;
    private static final int BLACK_SALAMANDER = 10148;
    private static final int RED_CHINCHOMPA = 10034;
    private static final int DRAGON_DART = 11230; // weapon-slot thrown, not ammo
    private static final int ABYSSAL_WHIP = 4151;
    private static final int SCORCHING_BOW = 29591;

    @Test
    public void classify_coversEveryAmmoClassByName() {
        assertEquals(AmmoCompatibility.AmmoClass.ARROW, AmmoCompatibility.classify(DRAGON_ARROW));
        assertEquals("comp-bow 'brutal' arrows classify as arrows",
                AmmoCompatibility.AmmoClass.ARROW, AmmoCompatibility.classify(RUNE_BRUTAL));
        assertEquals(AmmoCompatibility.AmmoClass.BOLT, AmmoCompatibility.classify(RUNITE_BOLTS));
        assertEquals(AmmoCompatibility.AmmoClass.JAVELIN, AmmoCompatibility.classify(DRAGON_JAVELIN));
        assertEquals(AmmoCompatibility.AmmoClass.TAR, AmmoCompatibility.classify(HARRALANDER_TAR));
        assertEquals(AmmoCompatibility.AmmoClass.BLESSING, AmmoCompatibility.classify(RADAS_BLESSING_4));
    }

    @Test
    public void classify_nonAmmoSlotAndUnknownIdsResolveToNull() {
        assertNull("a weapon-slot item is not ammo", AmmoCompatibility.classify(MAGIC_SHORTBOW));
        assertNull("weapon-slot darts are not worn ammo", AmmoCompatibility.classify(DRAGON_DART));
        assertNull("an unindexed id has no class", AmmoCompatibility.classify(999_999));
        assertNull(AmmoCompatibility.classify(-1));
    }

    @Test
    public void classify_everyIndexedAmmoSlotItemHasAClass() {
        // The bundled data partitions cleanly by name (see the enum javadoc);
        // this guards a future data regen from silently introducing ammo the
        // classifier can't place (which would fall back to always-contributes).
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
        for (EquipmentIndexRepository.Entry e : index.forSlot(13)) {
            assertTrue("unclassified ammo-slot item: " + e.name() + " (" + e.itemId() + ")",
                    AmmoCompatibility.classify(e.itemId()) != null);
        }
    }

    @Test
    public void consumedClass_bowsFireArrows_crossbowsFireBolts_salamandersBurnTar() {
        assertEquals(AmmoCompatibility.AmmoClass.ARROW, AmmoCompatibility.consumedClass(MAGIC_SHORTBOW));
        assertEquals(AmmoCompatibility.AmmoClass.ARROW, AmmoCompatibility.consumedClass(SCORCHING_BOW));
        assertEquals(AmmoCompatibility.AmmoClass.BOLT, AmmoCompatibility.consumedClass(RUNE_CROSSBOW));
        assertEquals(AmmoCompatibility.AmmoClass.TAR, AmmoCompatibility.consumedClass(BLACK_SALAMANDER));
    }

    @Test
    public void consumedClass_ballistaeFireJavelins_despiteCrossbowCategoryData() {
        assertEquals(AmmoCompatibility.AmmoClass.JAVELIN, AmmoCompatibility.consumedClass(HEAVY_BALLISTA));
        assertEquals(AmmoCompatibility.AmmoClass.JAVELIN, AmmoCompatibility.consumedClass(LIGHT_BALLISTA));
    }

    @Test
    public void consumedClass_karilsAndHuntersCrossbowsGetTheirOwnAmmoClasses() {
        final int KARILS_CROSSBOW = 4734;
        final int HUNTERS_CROSSBOW = 10156;
        final int HUNTERS_SUNLIGHT_CROSSBOW = 28869;
        final int BOLT_RACK = 4740;
        final int KEBBIT_BOLTS = 10158;
        final int SUNLIGHT_ANTLER_BOLTS = 28872;

        assertEquals(AmmoCompatibility.AmmoClass.RACK, AmmoCompatibility.consumedClass(KARILS_CROSSBOW));
        assertEquals(AmmoCompatibility.AmmoClass.RACK, AmmoCompatibility.classify(BOLT_RACK));
        assertEquals(AmmoCompatibility.AmmoClass.HUNTER_BOLT, AmmoCompatibility.consumedClass(HUNTERS_CROSSBOW));
        assertEquals(AmmoCompatibility.AmmoClass.HUNTER_BOLT,
                AmmoCompatibility.consumedClass(HUNTERS_SUNLIGHT_CROSSBOW));
        assertEquals(AmmoCompatibility.AmmoClass.HUNTER_BOLT, AmmoCompatibility.classify(KEBBIT_BOLTS));
        assertEquals(AmmoCompatibility.AmmoClass.HUNTER_BOLT, AmmoCompatibility.classify(SUNLIGHT_ANTLER_BOLTS));

        assertFalse("Karil's crossbow must never credit regular bolts",
                AmmoCompatibility.wornAmmoContributes(KARILS_CROSSBOW, RUNITE_BOLTS));
        assertTrue(AmmoCompatibility.wornAmmoContributes(KARILS_CROSSBOW, BOLT_RACK));
        assertFalse("a regular crossbow must never credit bolt racks",
                AmmoCompatibility.wornAmmoContributes(RUNE_CROSSBOW, BOLT_RACK));
        assertFalse("the Hunters' sunlight crossbow must never credit dragon-tier bolts",
                AmmoCompatibility.wornAmmoContributes(HUNTERS_SUNLIGHT_CROSSBOW, RUNITE_BOLTS));
        assertTrue(AmmoCompatibility.wornAmmoContributes(HUNTERS_SUNLIGHT_CROSSBOW, SUNLIGHT_ANTLER_BOLTS));
    }

    @Test
    public void consumedClass_atlatlFiresUnindexedDarts() {
        assertEquals(AmmoCompatibility.AmmoClass.DART, AmmoCompatibility.consumedClass(ECLIPSE_ATLATL));
    }

    @Test
    public void consumedClass_selfSupplyingAndNonAmmoWeaponsUseNoWornAmmo() {
        assertNull("blowpipe loads darts internally", AmmoCompatibility.consumedClass(TOXIC_BLOWPIPE));
        assertNull("crystal bow supplies its own ammo", AmmoCompatibility.consumedClass(CRYSTAL_BOW));
        assertNull(AmmoCompatibility.consumedClass(BOW_OF_FAERDHINEN));
        assertNull(AmmoCompatibility.consumedClass(CRAWS_BOW));
        assertNull(AmmoCompatibility.consumedClass(WEBWEAVER_BOW));
        assertNull("thrown weapons ARE their own ammo", AmmoCompatibility.consumedClass(DRAGON_DART));
        assertNull(AmmoCompatibility.consumedClass(RED_CHINCHOMPA));
        assertNull("melee weapons use no ammo", AmmoCompatibility.consumedClass(ABYSSAL_WHIP));
        assertNull(AmmoCompatibility.consumedClass(-1));
        assertFalse(AmmoCompatibility.usesWornAmmo(TOXIC_BLOWPIPE));
        assertTrue(AmmoCompatibility.usesWornAmmo(MAGIC_SHORTBOW));
    }

    @Test
    public void wornAmmoContributes_matchingClassOnly_forConsumables() {
        assertTrue("arrows count behind a bow",
                AmmoCompatibility.wornAmmoContributes(MAGIC_SHORTBOW, DRAGON_ARROW));
        assertFalse("javelins never count behind a bow",
                AmmoCompatibility.wornAmmoContributes(MAGIC_SHORTBOW, DRAGON_JAVELIN));
        assertFalse("bolts never count behind a bow",
                AmmoCompatibility.wornAmmoContributes(MAGIC_SHORTBOW, RUNITE_BOLTS));
        assertFalse("arrows never count behind a crossbow",
                AmmoCompatibility.wornAmmoContributes(RUNE_CROSSBOW, DRAGON_ARROW));
        assertTrue("bolts count behind a crossbow",
                AmmoCompatibility.wornAmmoContributes(RUNE_CROSSBOW, RUNITE_BOLTS));
        assertTrue("javelins count behind a ballista",
                AmmoCompatibility.wornAmmoContributes(HEAVY_BALLISTA, DRAGON_JAVELIN));
        assertFalse("consumable ammo never counts behind a weapon that fires nothing",
                AmmoCompatibility.wornAmmoContributes(CRYSTAL_BOW, DRAGON_ARROW));
        assertFalse(AmmoCompatibility.wornAmmoContributes(ABYSSAL_WHIP, DRAGON_JAVELIN));
    }

    @Test
    public void wornAmmoContributes_blessingsAndUnknownsAlwaysCount() {
        assertTrue("a blessing is passive — counts behind anything",
                AmmoCompatibility.wornAmmoContributes(MAGIC_SHORTBOW, RADAS_BLESSING_4));
        assertTrue(AmmoCompatibility.wornAmmoContributes(CRYSTAL_BOW, RADAS_BLESSING_4));
        assertTrue(AmmoCompatibility.wornAmmoContributes(ABYSSAL_WHIP, RADAS_BLESSING_4));
        assertTrue("an unindexed ammo id keeps the pre-compatibility behaviour",
                AmmoCompatibility.wornAmmoContributes(MAGIC_SHORTBOW, 999_999));
    }
}
