package com.ospulse.session;

import com.ospulse.model.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loot aggregated by its <em>source</em> (the NPC / boss / activity that
 * produced it), mirroring how the Loot Tracker plugin groups drops. One
 * {@code SourceLoot} = one collapsible group in the panel: a source name, how
 * many times loot was received from it this session ({@link #getCount()}), the
 * summed value, and the per-item rows (already sorted by value descending).
 *
 * <p>Pure domain type: no RuneLite imports, unit-testable without a game client.
 */
public final class SourceLoot
{
	private final String source;
	private final long totalValue;
	private final long count;
	private final List<ItemStack> items;

	public SourceLoot(String source, long totalValue, long count, List<ItemStack> items)
	{
		this.source = source;
		this.totalValue = totalValue;
		this.count = count;
		this.items = items == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(items));
	}

	public String getSource()
	{
		return source;
	}

	public long getTotalValue()
	{
		return totalValue;
	}

	/** Number of loot drops received from this source this session (e.g. kill count). */
	public long getCount()
	{
		return count;
	}

	public List<ItemStack> getItems()
	{
		return items;
	}
}
