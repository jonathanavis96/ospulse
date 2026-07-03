package com.ospulse.integration;

import net.runelite.api.ItemComposition;
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

	/** True if this item id is a bank placeholder (empty reserved slot, not owned). */
	public boolean isPlaceholder(int itemId)
	{
		return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
	}

	/**
	 * True if the item can be traded (GE/player trade), canonicalized first.
	 * Calls {@link ItemManager#getItemComposition(int)}, which asserts it is
	 * running on the RuneLite client thread — callers must never invoke this
	 * from the Swing EDT or a background search thread; precompute the answers
	 * into a plain map on the client thread instead (see
	 * {@code OSPulsePlugin}'s optimizer price resolver).
	 */
	public boolean isTradeable(int itemId)
	{
		int canonicalId = itemManager.canonicalize(itemId);
		ItemComposition comp = itemManager.getItemComposition(canonicalId);
		return comp != null && comp.isTradeable();
	}

	/**
	 * Per-unit High Alchemy price for the given item, canonicalized first, or
	 * {@code -1} if no composition is available. Calls
	 * {@link ItemManager#getItemComposition(int)}, which asserts it is running
	 * on the RuneLite client thread — callers must never invoke this from the
	 * Swing EDT.
	 */
	public long haPrice(int itemId)
	{
		int canonicalId = itemManager.canonicalize(itemId);
		ItemComposition comp = itemManager.getItemComposition(canonicalId);
		return comp == null ? -1L : comp.getHaPrice();
	}
}
