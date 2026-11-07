package com.ospulse.combat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit coverage for the weapon-category → attack-style layer: the bundled
 * item-id → category map ({@link WeaponCategoryRepository}) and the ported
 * per-category style tables ({@link WeaponStyles}).
 */
public class WeaponStylesTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
	private static final WeaponCategoryRepository REPO = WeaponCategoryRepository.getInstance();

	/**
	 * A category string the plugin does not know silently falls back to
	 * unarmed styles, so a data refresh that brings a new weapon type has to
	 * fail here rather than ship wrong attack styles.
	 */
	@Test
	public void everyBundledCategoryIsKnownAndHasStyles() throws java.io.IOException
	{
		try (java.io.Reader reader = new java.io.InputStreamReader(
			WeaponStylesTest.class.getResourceAsStream("/com/ospulse/combat/weapon_categories.min.json"),
			java.nio.charset.StandardCharsets.UTF_8))
		{
			java.util.Map<String, String> bundled = new com.google.gson.Gson().fromJson(reader,
				new com.google.gson.reflect.TypeToken<java.util.Map<String, String>>()
				{
				}.getType());
			for (java.util.Map.Entry<String, String> entry : bundled.entrySet())
			{
				WeaponCategory category = WeaponCategory.fromDataName(entry.getValue());
				assertTrue("item " + entry.getKey() + " has unknown category '" + entry.getValue() + "'",
					category != null);
				assertTrue("no attack styles for " + category,
					category == WeaponCategory.BLASTER || !WeaponStyles.forCategory(category).isEmpty());
			}
		}
	}

	@Test
	public void obligatorOffersBothThrustStancesAndSmash()
	{
		assertEquals(WeaponCategory.CHAIN_MACE, REPO.categoryFor(34715));
		List<WeaponStyle> styles = WeaponStyles.forCategory(WeaponCategory.CHAIN_MACE);
		assertEquals(3, styles.size());
		assertEquals(Stance.SMASH, styles.get(2).stance());
	}

	@Test
	public void zoryasTomeUsesPoweredStaffStyles()
	{
		assertEquals(WeaponCategory.POWERED_STAFF, REPO.categoryFor(34725));
	}

	@Test
	public void bundledMapLoadsAndResolvesKnownWeapons()
	{
		assertTrue("bundled weapon map should be sizeable", REPO.size() > 1000);
		assertEquals(WeaponCategory.STAB_SWORD, REPO.categoryFor(22324));  // Ghrazi rapier
		assertEquals(WeaponCategory.SLASH_SWORD, REPO.categoryFor(4587));  // Dragon scimitar
		assertEquals(WeaponCategory.BLUNT, REPO.categoryFor(13576));       // Dragon warhammer
		assertEquals(WeaponCategory.WHIP, REPO.categoryFor(12006));        // Abyssal tentacle
		assertEquals(WeaponCategory.BOW, REPO.categoryFor(20997));         // Twisted bow
		assertEquals(WeaponCategory.TWO_HANDED_SWORD, REPO.categoryFor(11802)); // Armadyl godsword
	}

	@Test
	public void unknownOrEmptyWeaponFallsBackToUnarmed()
	{
		assertNull(REPO.categoryFor(999_999));
		assertNull(REPO.categoryFor(0));
		assertNull(REPO.categoryFor(-1));

		List<WeaponStyle> unarmed = REPO.stylesForItem(-1);
		assertEquals(3, unarmed.size());
		for (WeaponStyle s : unarmed)
		{
			assertEquals("unarmed styles are all crush", CombatStyle.CRUSH, s.type());
		}
	}

	@Test
	public void stabSwordExposesItsFourRealStyles()
	{
		List<WeaponStyle> styles = REPO.stylesForItem(22324); // Ghrazi rapier
		assertEquals(4, styles.size());
		// Stab/Accurate, Lunge/Stab/Aggressive, Slash/Slash/Aggressive, Block/Stab/Defensive.
		assertEquals(new WeaponStyle("Stab", CombatStyle.STAB, Stance.ACCURATE), styles.get(0));
		assertEquals(new WeaponStyle("Lunge", CombatStyle.STAB, Stance.AGGRESSIVE), styles.get(1));
		assertEquals(new WeaponStyle("Slash", CombatStyle.SLASH, Stance.AGGRESSIVE), styles.get(2));
		assertEquals(new WeaponStyle("Block", CombatStyle.STAB, Stance.DEFENSIVE), styles.get(3));
	}

	@Test
	public void nullTypeAndDuplicateStylesAreCollapsed()
	{
		// Bulwark: "Pummel" (crush) + "Block" (no type) -> only the offence-bearing one.
		List<WeaponStyle> bulwark = WeaponStyles.forCategory(WeaponCategory.BULWARK);
		assertEquals(1, bulwark.size());
		assertEquals(CombatStyle.CRUSH, bulwark.get(0).type());

		// Bludgeon: three identical aggressive-crush styles collapse to one.
		List<WeaponStyle> bludgeon = WeaponStyles.forCategory(WeaponCategory.BLUDGEON);
		assertEquals(1, bludgeon.size());
		assertEquals(Stance.AGGRESSIVE, bludgeon.get(0).stance());

		// Powered staff: Accurate/Accurate/Longrange -> two distinct (magic) styles.
		List<WeaponStyle> powered = WeaponStyles.forCategory(WeaponCategory.POWERED_STAFF);
		assertEquals(2, powered.size());
		for (WeaponStyle s : powered)
		{
			assertEquals(CombatStyle.MAGIC, s.type());
		}
	}

	@Test
	public void bowStylesAreRangedAccurateRapidLongrange()
	{
		List<WeaponStyle> bow = REPO.stylesForItem(20997); // Twisted bow
		assertEquals(3, bow.size());
		assertEquals(Stance.ACCURATE, bow.get(0).stance());
		assertEquals(Stance.RAPID, bow.get(1).stance());
		assertEquals(Stance.LONGRANGE, bow.get(2).stance());
		for (WeaponStyle s : bow)
		{
			assertEquals(CombatStyle.RANGED, s.type());
		}
	}

	@Test
	public void categoryNameParsingHandlesTheDataStrings()
	{
		assertEquals(WeaponCategory.TWO_HANDED_SWORD, WeaponCategory.fromDataName("2h sword"));
		assertEquals(WeaponCategory.SLASH_SWORD, WeaponCategory.fromDataName("Slash Sword"));
		assertEquals(WeaponCategory.CHINCHOMPA, WeaponCategory.fromDataName("chinchompas"));
		assertEquals(WeaponCategory.UNARMED, WeaponCategory.fromDataName(""));
		assertNull(WeaponCategory.fromDataName("not a real category"));
		assertNull(WeaponCategory.fromDataName(null));
	}

	@Test
	public void everyRealCategoryYieldsAtLeastOneOffensiveStyle()
	{
		for (WeaponCategory category : WeaponCategory.values())
		{
			if (category == WeaponCategory.BLASTER)
			{
				continue; // upstream models no styles for the (unreleased) blaster
			}
			List<WeaponStyle> styles = WeaponStyles.forCategory(category);
			assertFalse(category + " should have styles", styles.isEmpty());
		}
	}
}
