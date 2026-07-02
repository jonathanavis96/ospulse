package com.ospulse.integration;

import com.ospulse.ge.GeOfferState;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verifies every RuneLite {@link GrandExchangeOfferState} maps to the
 * equivalent pure domain {@link GeOfferState}, and that nothing in the
 * RuneLite enum is left unhandled.
 */
public class GeOfferStateMapperTest
{
	@Test
	public void mapsEveryStateExplicitly()
	{
		assertEquals(GeOfferState.EMPTY, GeOfferStateMapper.map(GrandExchangeOfferState.EMPTY));
		assertEquals(GeOfferState.BUYING, GeOfferStateMapper.map(GrandExchangeOfferState.BUYING));
		assertEquals(GeOfferState.BOUGHT, GeOfferStateMapper.map(GrandExchangeOfferState.BOUGHT));
		assertEquals(GeOfferState.SELLING, GeOfferStateMapper.map(GrandExchangeOfferState.SELLING));
		assertEquals(GeOfferState.SOLD, GeOfferStateMapper.map(GrandExchangeOfferState.SOLD));
		assertEquals(GeOfferState.CANCELLED_BUY, GeOfferStateMapper.map(GrandExchangeOfferState.CANCELLED_BUY));
		assertEquals(GeOfferState.CANCELLED_SELL, GeOfferStateMapper.map(GrandExchangeOfferState.CANCELLED_SELL));
	}

	@Test
	public void nullMapsToEmpty()
	{
		assertEquals(GeOfferState.EMPTY, GeOfferStateMapper.map(null));
	}

	@Test
	public void everyRuneLiteStateProducesANonNullMapping()
	{
		for (GrandExchangeOfferState state : GrandExchangeOfferState.values())
		{
			assertNotNull(GeOfferStateMapper.map(state));
		}
	}
}
