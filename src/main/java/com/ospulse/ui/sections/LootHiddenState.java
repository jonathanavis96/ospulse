package com.ospulse.ui.sections;

import com.ospulse.model.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-Java (no Swing) tracker for the loot feed's "Hide item" / "Hide
 * loot" right-click actions, kept separate from {@link LootSection} so the
 * filtering logic is unit-testable without a Swing/RuneLite environment.
 *
 * <p>Two independent sets are maintained deliberately:
 * <ul>
 *   <li>Item hides ({@link #hideItem}) and manual source hides ({@link
 *   #hideSource}) are both "hidden until explicitly un-hidden" — distinct
 *   from {@code LootSection}'s existing {@code hiddenSources} set, which is
 *   "hidden until a new pickup" (the Reset/Reset-others/Reset-all
 *   semantics).
 * </ul>
 *
 * <p>Not thread-safe; use on the Swing EDT only, same as the section that
 * owns an instance of this.
 */
public final class LootHiddenState
{
	private final Map<Integer, String> hiddenItemIds = new LinkedHashMap<>();
	private final Map<String, String> manuallyHiddenSources = new LinkedHashMap<>();

	/** Hides {@code item}'s id from the feed; retains its last-seen name for the "View hidden items" list. */
	public void hideItem(ItemStack item)
	{
		hiddenItemIds.put(item.getId(), item.getName());
	}

	public boolean isItemHidden(int itemId)
	{
		return hiddenItemIds.containsKey(itemId);
	}

	public void unhideItem(int itemId)
	{
		hiddenItemIds.remove(itemId);
	}

	/** Hides an entire source (by category id) until explicitly unhidden via "View hidden items". */
	public void hideSource(String catId, String displayName)
	{
		manuallyHiddenSources.put(catId, displayName);
	}

	public boolean isSourceHidden(String catId)
	{
		return manuallyHiddenSources.containsKey(catId);
	}

	public void unhideSource(String catId)
	{
		manuallyHiddenSources.remove(catId);
	}

	/** Snapshot of hidden item id -> last-seen name, in insertion order. */
	public Map<Integer, String> hiddenItems()
	{
		return new LinkedHashMap<>(hiddenItemIds);
	}

	/** Snapshot of hidden source catId -> display name, in insertion order. */
	public Map<String, String> hiddenSources()
	{
		return new LinkedHashMap<>(manuallyHiddenSources);
	}

	public boolean isEmpty()
	{
		return hiddenItemIds.isEmpty() && manuallyHiddenSources.isEmpty();
	}

	/** Clears both sets, e.g. as part of a section/panel-wide "full reset". */
	public void clear()
	{
		hiddenItemIds.clear();
		manuallyHiddenSources.clear();
	}

	/** Whether {@code item} should be filtered out of the loot feed grid. */
	public boolean isItemFiltered(ItemStack item)
	{
		return isItemHidden(item.getId());
	}
}
