package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the hand-curated {@link SpecWeapon#CATALOG} (design spec §8): every
 * canonical id AND every owned-alias id must resolve in the bundled
 * equipment index, and the canonical id's indexed name must match {@link
 * SpecWeapon#displayName()} exactly — not a paraphrase. Mirrors {@code
 * MonsterCombatRequirementDataTest}'s role for that dataset: a typo'd/renamed
 * id would otherwise fail silently (the weapon simply never gets recommended,
 * or an unowned item id gets mistaken for owned), and this is the only thing
 * standing between a catalog edit and a wrong recommendation.
 */
public class SpecWeaponCatalogDataTest {
    static {
        BundledGson.set(new com.google.gson.Gson());
    }

    @Test
    public void everyCanonicalIdResolvesWithTheExactDisplayName() {
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            EquipmentIndexRepository.Entry entry = index.entryFor(weapon.itemId());
            assertNotNull("canonical id not in equipment index: " + weapon.itemId() + " (" + weapon.displayName() + ")",
                    entry);
            assertEquals("indexed name mismatch for id " + weapon.itemId(),
                    weapon.displayName(), entry.name());
        }
    }

    @Test
    public void everyOwnedAliasIdResolvesInTheEquipmentIndex() {
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            for (int alias : weapon.ownedAliasIds()) {
                assertNotNull("alias id not in equipment index: " + alias + " (" + weapon.displayName() + ")",
                        index.entryFor(alias));
            }
        }
    }

    /** Every curated spec cost is a real percent of the spec bar (1-100 inclusive). */
    @Test
    public void everySpecCostIsAPlausiblePercent() {
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            assertTrue(weapon.displayName() + " spec cost out of range: " + weapon.specCostPercent(),
                    weapon.specCostPercent() > 0 && weapon.specCostPercent() <= 100);
        }
    }

    /** Every catalog entry resolves to at least one real attack style of its declared type — see GearSection.matchSpecWeaponStyle. */
    @Test
    public void everyWeaponOffersItsDeclaredCombatStyle() {
        WeaponCategoryRepository weaponRepo = WeaponCategoryRepository.getInstance();
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            boolean found = false;
            for (WeaponStyle style : weaponRepo.stylesForItem(weapon.itemId())) {
                if (style.type() == weapon.style()) {
                    found = true;
                    break;
                }
            }
            assertTrue(weapon.displayName() + " has no " + weapon.style() + " style in weapon_categories.min.json",
                    found);
        }
    }

    /** The catalog covers every role at least once (design spec §8's role table) — catches an accidental drop. */
    @Test
    public void everyRoleIsRepresented() {
        for (SpecRole role : SpecRole.values()) {
            boolean found = false;
            for (SpecWeapon weapon : SpecWeapon.CATALOG) {
                if (weapon.role() == role) {
                    found = true;
                    break;
                }
            }
            assertTrue("no curated spec weapon tagged " + role, found);
        }
    }

    /**
     * Guards the recurring bug class this class's javadoc warns about: a
     * charge-state variant (e.g. an empty/uncharged weapon) mis-filed as an
     * {@link SpecWeapon#ownedAliasIds()} cosmetic recolour, which would let an
     * owner of only that variant be recommended, probed, and rendered a
     * weapon they cannot actually use. Every alias id's combat stats
     * ({@code equipment_stats.min.json}, excluding the trailing slot-hint
     * field — see {@code EquipmentStatsRepository.Stats}, which already drops
     * it) must exactly match the canonical id's.
     *
     * <p><b>This is a floor, not a proof.</b> Identical combat stats are
     * NECESSARY but NOT SUFFICIENT evidence of a genuine alias: the confirmed
     * regression 12924 "Toxic blowpipe (empty)" has IDENTICAL combat stats to
     * the charged 12926 (both are 0 damage/accuracy bonus rows) yet cannot
     * perform the special attack at all until recharged with scales and
     * darts. A stat mismatch proves a bad alias; a stat match does NOT prove
     * a good one — a new alias id still needs its wiki infobox version
     * checked (the {@code |version1=}/{@code |id1=} fields) for a
     * charge/state label ("Empty", "Inactive", "Uncharged"/"Charged", ...)
     * before being added here, exactly as this catalog's javadoc already
     * requires for 30305 "Arclight (inactive)".
     */
    @Test
    public void everyAliasHasIdenticalCombatStatsToItsCanonicalId() {
        EquipmentStatsRepository stats = EquipmentStatsRepository.getInstance();
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            EquipmentStatsRepository.Stats canonical = stats.statsFor(weapon.itemId());
            assertNotNull("no combat stats for canonical id " + weapon.itemId() + " (" + weapon.displayName() + ")",
                    canonical);
            for (int alias : weapon.ownedAliasIds()) {
                EquipmentStatsRepository.Stats aliasStats = stats.statsFor(alias);
                assertNotNull("no combat stats for alias id " + alias + " (" + weapon.displayName() + ")",
                        aliasStats);
                assertEquals("alias " + alias + " has different combat stats than canonical " + weapon.itemId()
                                + " (" + weapon.displayName() + ") — likely a charge/state variant mis-filed as an alias",
                        describe(canonical), describe(aliasStats));
            }
        }
    }

    /**
     * Pins the two confirmed regressions by name so they can never silently
     * creep back into {@link SpecWeapon#ownedAliasIds()} anywhere in the
     * catalog: 12924 "Toxic blowpipe (empty)" (cannot special until charged
     * with scales/darts) and 30305 "Arclight (inactive)" (loses its stats and
     * demonbane bonus once exhausted — see the class javadoc's worked
     * example).
     */
    @Test
    public void confirmedChargeStateRegressionsAreNeverAliased() {
        for (SpecWeapon weapon : SpecWeapon.CATALOG) {
            assertFalse("12924 'Toxic blowpipe (empty)' must never be an alias (" + weapon.displayName() + ")",
                    weapon.ownedAliasIds().contains(12924));
            assertFalse("30305 'Arclight (inactive)' must never be an alias (" + weapon.displayName() + ")",
                    weapon.ownedAliasIds().contains(30305));
        }
    }

    private static String describe(EquipmentStatsRepository.Stats s) {
        return "astab=" + s.astab() + " aslash=" + s.aslash() + " acrush=" + s.acrush()
                + " amagic=" + s.amagic() + " arange=" + s.arange()
                + " dstab=" + s.dstab() + " dslash=" + s.dslash() + " dcrush=" + s.dcrush()
                + " dmagic=" + s.dmagic() + " drange=" + s.drange()
                + " str=" + s.str() + " rstr=" + s.rstr() + " mdmg=" + s.mdmg()
                + " prayer=" + s.prayer() + " aspeed=" + s.aspeed();
    }
}
