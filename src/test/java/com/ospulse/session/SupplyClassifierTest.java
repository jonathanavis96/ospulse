package com.ospulse.session;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SupplyClassifierTest
{
	@Test
	public void doseSuffixedPotionIsConsumable()
	{
		assertTrue(SupplyClassifier.isConsumable("Super antifire potion(4)"));
	}

	@Test
	public void cannonballsAreConsumableAmmo()
	{
		// Cannonballs are burned through like any other ammunition, so they
		// belong in "supplies used", not folded into profit/loot.
		assertTrue(SupplyClassifier.isConsumable("Cannonball"));
		assertTrue(SupplyClassifier.isConsumable("Granite cannonball"));
	}

	@Test
	public void chargedJewelleryWithHigherDoseSuffixIsNotConsumable()
	{
		// Charged jewellery uses (6)/(8) charge suffixes, which look
		// superficially similar to a dose potion's "(n)" suffix but must not
		// be swept up by the generic 1-4 dose fallback pattern.
		assertFalse(SupplyClassifier.isConsumable("Amulet of glory(6)"));
	}

	@Test
	public void fishingBaitAndFeathersAreConsumable()
	{
		// Bait is burned through per catch while fishing (e.g. a feather per
		// barbarian-rod catch), so it belongs in "supplies used" — previously
		// uncounted since it is neither food, ammo, potion nor rune.
		assertTrue(SupplyClassifier.isConsumable("Feather"));
		assertTrue(SupplyClassifier.isConsumable("Feathers"));
		assertTrue(SupplyClassifier.isConsumable("Stripy feather"));
		assertTrue(SupplyClassifier.isConsumable("Fishing bait"));
		assertTrue(SupplyClassifier.isConsumable("Sandworms"));
		assertTrue(SupplyClassifier.isConsumable("Fish offcuts"));
		assertTrue(SupplyClassifier.isConsumable("Raw karambwanji"));
	}

	@Test
	public void fishingToolsAreNotMistakenForBait()
	{
		// The bait pattern must not sweep up fishing TOOLS (rods/nets/harpoons),
		// which are equipment, not a consumed tracked-wealth stack.
		assertFalse(SupplyClassifier.isConsumable("Fishing rod"));
		assertFalse(SupplyClassifier.isConsumable("Barbarian rod"));
		assertFalse(SupplyClassifier.isConsumable("Small fishing net"));
		assertFalse(SupplyClassifier.isConsumable("Dragon harpoon"));
	}
}
