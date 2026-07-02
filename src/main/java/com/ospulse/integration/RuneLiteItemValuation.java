package com.ospulse.integration;

import net.runelite.client.game.ItemManager;

/**
 * Thin wrapper around RuneLite's {@link ItemManager} that canonicalizes item
 * ids before pricing/naming them, so noted and placeholder variants of an
 * item price identically to the base item.
 */
public class RuneLiteItemValuation
{
	private final ItemManager itemManager;

	public RuneLiteItemValuation(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * GE unit price for the given item, canonicalized first. Returns 0 for
	 * untradeable items — that's correct, they contribute 0 to wealth.
	 */
	public long unitValue(int itemId)
	{
		int canonicalId = itemManager.canonicalize(itemId);
		return itemManager.getItemPrice(canonicalId);
	}

	public String name(int itemId)
	{
		int canonicalId = itemManager.canonicalize(itemId);
		return itemManager.getItemComposition(canonicalId).getName();
	}

	/**
	 * The canonicalized id for {@code itemId} (noted/placeholder variants
	 * resolve to their base item id), used to key tracked items so noted and
	 * unnoted stacks of the same item merge together.
	 */
	public int canonical(int itemId)
	{
		return itemManager.canonicalize(itemId);
	}
}
