package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AssembledItemComponentsTest
{
	@Test
	public void avernicDefenderPricesAtItsHilt()
	{
		// Avernic defender (22322) is untradeable but assembled from the
		// tradeable Avernic defender hilt (22477).
		assertEquals(Integer.valueOf(22477), AssembledItemComponents.priceSourceComponent(22322));
	}

	@Test
	public void ordinaryItemHasNoComponentSource()
	{
		// A normal tradeable item is not a curated assembled case.
		assertNull(AssembledItemComponents.priceSourceComponent(4151)); // Abyssal whip
	}
}
