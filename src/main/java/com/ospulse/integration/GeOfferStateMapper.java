package com.ospulse.integration;

import com.ospulse.ge.GeOfferState;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Maps RuneLite's {@link GrandExchangeOfferState} onto the pure domain
 * {@link GeOfferState}. The two enums currently have identical members, but
 * this mapping is kept explicit (rather than {@code Enum#valueOf} by name)
 * so a RuneLite-side rename or addition doesn't silently break the domain
 * engine.
 */
public final class GeOfferStateMapper
{
	private GeOfferStateMapper()
	{
	}

	public static GeOfferState map(GrandExchangeOfferState state)
	{
		if (state == null)
		{
			return GeOfferState.EMPTY;
		}
		switch (state)
		{
			case BUYING:
				return GeOfferState.BUYING;
			case BOUGHT:
				return GeOfferState.BOUGHT;
			case SELLING:
				return GeOfferState.SELLING;
			case SOLD:
				return GeOfferState.SOLD;
			case CANCELLED_BUY:
				return GeOfferState.CANCELLED_BUY;
			case CANCELLED_SELL:
				return GeOfferState.CANCELLED_SELL;
			case EMPTY:
			default:
				return GeOfferState.EMPTY;
		}
	}
}
