package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
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
}
