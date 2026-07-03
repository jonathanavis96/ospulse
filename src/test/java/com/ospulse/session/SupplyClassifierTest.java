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
	public void chargedJewelleryWithHigherDoseSuffixIsNotConsumable()
	{
		// Charged jewellery uses (6)/(8) charge suffixes, which look
		// superficially similar to a dose potion's "(n)" suffix but must not
		// be swept up by the generic 1-4 dose fallback pattern.
		assertFalse(SupplyClassifier.isConsumable("Amulet of glory(6)"));
	}
}
