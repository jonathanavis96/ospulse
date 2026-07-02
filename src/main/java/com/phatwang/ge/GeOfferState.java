package com.phatwang.ge;

/**
 * Lifecycle state of a Grand Exchange offer slot, as reported to
 * {@link GeReconciler#onOfferUpdate}.
 */
public enum GeOfferState
{
	EMPTY,
	BUYING,
	SELLING,
	BOUGHT,
	SOLD,
	CANCELLED_BUY,
	CANCELLED_SELL
}
