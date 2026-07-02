package com.ospulse.integration;

/**
 * Pure arithmetic for the tracked-wealth value still <em>locked in</em> the
 * Grand Exchange for an active offer. No RuneLite imports, so it's directly
 * unit-testable.
 *
 * <p>The guiding rule is conservative: only count value that has definitively
 * left your inventory/bank and cannot already be sitting there again. We
 * therefore never count the "collectable" side of an offer — bought items or
 * sale proceeds waiting in the collection box — because RuneLite gives us no
 * way to tell whether the player has already collected it into their
 * inventory. (A partial collect does not change the offer's
 * {@code quantitySold}/{@code spent}, so it is invisible to us.) Counting the
 * collectable side would double-count anything already collected and invent
 * net worth that does not exist — the one error a wealth tracker must never
 * make. Counting only the still-locked side can transiently <em>under</em>-count
 * uncollected winnings, but that self-corrects the instant they are collected
 * (they reappear in the inventory) and never shows a phantom gain.
 *
 * <p><b>BUY offer:</b> coins are escrowed up-front for the whole order; as items
 * are bought, the coins for that portion turn into items (plus any price-
 * improvement refund) that sit in the collection box — or get collected into
 * the inventory, indistinguishable to us. Only the escrow for the
 * not-yet-bought quantity is still definitively locked:
 * {@code price * (totalQuantity - quantitySold)}. A fully-bought offer is
 * therefore worth 0 here (everything is collectable).
 *
 * <p><b>SELL offer:</b> the unsold items are still locked in the exchange,
 * valued at current market price: {@code unitValue * (totalQuantity -
 * quantitySold)}. Proceeds from the sold portion are collectable and so are
 * <em>not</em> counted here.
 *
 * <p><b>Cancelled offers</b> hold nothing locked — the unbought coins / unsold
 * items are returned to the collection box awaiting collection — so the caller
 * values them at 0 (see {@code SessionTracker}); they too self-correct on
 * collect.
 */
public final class GeValuation
{
	private GeValuation()
	{
	}

	/**
	 * Coins still escrowed for the not-yet-bought portion of a buy offer.
	 * {@code quantitySold} is the amount bought so far; anything already bought
	 * is collectable and deliberately excluded.
	 */
	public static long buyOfferValue(long pricePerItem, long totalQuantity, long quantitySold)
	{
		long remaining = totalQuantity - quantitySold;
		if (remaining < 0)
		{
			remaining = 0;
		}
		return pricePerItem * remaining;
	}

	/**
	 * Current market value of the unsold portion still locked in a sell offer.
	 * Proceeds already received ({@code spent}) are collectable and deliberately
	 * excluded.
	 */
	public static long sellOfferValue(long unitValue, long totalQuantity, long quantitySold)
	{
		long remaining = totalQuantity - quantitySold;
		if (remaining < 0)
		{
			remaining = 0;
		}
		return unitValue * remaining;
	}
}
