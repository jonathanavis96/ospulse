package com.ospulse.wealth;

import com.ospulse.model.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable point-in-time snapshot of a player's wealth, split into
 * "tracked" wealth (things that can change without a bank visit: inventory,
 * equipment, GE-in-flight offers, pouches) and bank (cold storage).
 *
 * Pure domain type: no RuneLite imports, unit-testable without a game client.
 */
public final class WealthSnapshot
{
	private final long inventoryValue;
	private final long equipmentValue;
	private final long geInFlightValue;
	/**
	 * Value awaiting collection in the GE collection box — sale proceeds and
	 * bought/refunded goods that have left the inventory into an offer but not
	 * yet been collected. See {@link com.ospulse.ge.GeReconciler#collectableValue}.
	 * Kept separate from {@link #geInFlightValue} (the still-locked, not-yet-filled
	 * side) so both together make tracked wealth continuous across a fill: the
	 * value moves locked → collectable → inventory without a dip.
	 */
	private final long geCollectableValue;
	private final long pouchValue;
	private final long bankValue;
	private final boolean bankKnown;
	private final long timestampMs;
	private final List<ItemStack> topHoldings;
	/**
	 * itemId -> stack for inventory + equipment + pouch contents ONLY.
	 * Does NOT include bank or GE-in-flight. Used for loot diffing.
	 */
	private final Map<Integer, ItemStack> trackedItems;
	/**
	 * Canonical itemId -> stack for EVERYTHING the player owns: inventory +
	 * equipment + bank (live or restored from the per-account cache) +
	 * pouches — the same complete item set the bank/net-worth valuation is
	 * computed from. Unlike {@link #topHoldings} this is NEVER truncated or
	 * sorted by value, so membership here is the authoritative "does the
	 * player own this item at all?" answer — a 0-GE-value untradeable (e.g. a
	 * fire cape in the bank) is present here even though it can never make a
	 * top-N-by-value cut.
	 */
	private final Map<Integer, ItemStack> allHoldings;

	public WealthSnapshot(
		long inventoryValue,
		long equipmentValue,
		long geInFlightValue,
		long pouchValue,
		long bankValue,
		boolean bankKnown,
		long timestampMs,
		List<ItemStack> topHoldings,
		Map<Integer, ItemStack> trackedItems)
	{
		this(inventoryValue, equipmentValue, geInFlightValue, pouchValue, bankValue, bankKnown,
			timestampMs, topHoldings, trackedItems, null);
	}

	public WealthSnapshot(
		long inventoryValue,
		long equipmentValue,
		long geInFlightValue,
		long pouchValue,
		long bankValue,
		boolean bankKnown,
		long timestampMs,
		List<ItemStack> topHoldings,
		Map<Integer, ItemStack> trackedItems,
		Map<Integer, ItemStack> allHoldings)
	{
		this(inventoryValue, equipmentValue, geInFlightValue, 0L, pouchValue, bankValue,
			bankKnown, timestampMs, topHoldings, trackedItems, allHoldings);
	}

	private WealthSnapshot(
		long inventoryValue,
		long equipmentValue,
		long geInFlightValue,
		long geCollectableValue,
		long pouchValue,
		long bankValue,
		boolean bankKnown,
		long timestampMs,
		List<ItemStack> topHoldings,
		Map<Integer, ItemStack> trackedItems,
		Map<Integer, ItemStack> allHoldings)
	{
		this.inventoryValue = inventoryValue;
		this.equipmentValue = equipmentValue;
		this.geInFlightValue = geInFlightValue;
		this.geCollectableValue = geCollectableValue;
		this.pouchValue = pouchValue;
		this.bankValue = bankValue;
		this.bankKnown = bankKnown;
		this.timestampMs = timestampMs;
		this.topHoldings = topHoldings == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(topHoldings));
		this.trackedItems = trackedItems == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(trackedItems));
		this.allHoldings = allHoldings == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(allHoldings));
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public long getInventoryValue()
	{
		return inventoryValue;
	}

	public long getEquipmentValue()
	{
		return equipmentValue;
	}

	public long getGeInFlightValue()
	{
		return geInFlightValue;
	}

	public long getGeCollectableValue()
	{
		return geCollectableValue;
	}

	public long getPouchValue()
	{
		return pouchValue;
	}

	public long getBankValue()
	{
		return bankValue;
	}

	public boolean isBankKnown()
	{
		return bankKnown;
	}

	public long getTimestampMs()
	{
		return timestampMs;
	}

	public List<ItemStack> getTopHoldings()
	{
		return topHoldings;
	}

	public Map<Integer, ItemStack> getTrackedItems()
	{
		return trackedItems;
	}

	/**
	 * The COMPLETE owned-item map (inventory + equipment + bank + pouches) —
	 * see the {@link #allHoldings} field javadoc. Empty when the snapshot was
	 * built without it (legacy callers/tests); membership-based ownership
	 * checks should fall back to {@link #getTopHoldings()} in that case.
	 */
	public Map<Integer, ItemStack> getAllHoldings()
	{
		return allHoldings;
	}

	/**
	 * Wealth that can move without a bank visit: inventory + equipment +
	 * GE-in-flight (still locked) + GE-collectable (awaiting collection) +
	 * pouches.
	 */
	public long tracked()
	{
		return inventoryValue + equipmentValue + geInFlightValue + geCollectableValue + pouchValue;
	}

	/**
	 * tracked() plus bank value, if the bank value is known. If the bank is
	 * unknown (e.g. never opened this session), bank is excluded so we don't
	 * silently treat 0 as the real bank balance.
	 */
	public long netWorth()
	{
		return tracked() + (bankKnown ? bankValue : 0);
	}

	public static final class Builder
	{
		private long inventoryValue;
		private long equipmentValue;
		private long geInFlightValue;
		private long geCollectableValue;
		private long pouchValue;
		private long bankValue;
		private boolean bankKnown;
		private long timestampMs;
		private List<ItemStack> topHoldings = Collections.emptyList();
		private Map<Integer, ItemStack> trackedItems = Collections.emptyMap();
		private Map<Integer, ItemStack> allHoldings = Collections.emptyMap();

		public Builder inventoryValue(long inventoryValue)
		{
			this.inventoryValue = inventoryValue;
			return this;
		}

		public Builder equipmentValue(long equipmentValue)
		{
			this.equipmentValue = equipmentValue;
			return this;
		}

		public Builder geInFlightValue(long geInFlightValue)
		{
			this.geInFlightValue = geInFlightValue;
			return this;
		}

		/** GE value awaiting collection — see {@link WealthSnapshot#getGeCollectableValue()}. */
		public Builder geCollectableValue(long geCollectableValue)
		{
			this.geCollectableValue = geCollectableValue;
			return this;
		}

		public Builder pouchValue(long pouchValue)
		{
			this.pouchValue = pouchValue;
			return this;
		}

		public Builder bankValue(long bankValue)
		{
			this.bankValue = bankValue;
			return this;
		}

		public Builder bankKnown(boolean bankKnown)
		{
			this.bankKnown = bankKnown;
			return this;
		}

		public Builder timestampMs(long timestampMs)
		{
			this.timestampMs = timestampMs;
			return this;
		}

		public Builder topHoldings(List<ItemStack> topHoldings)
		{
			this.topHoldings = topHoldings;
			return this;
		}

		public Builder trackedItems(Map<Integer, ItemStack> trackedItems)
		{
			this.trackedItems = trackedItems;
			return this;
		}

		/** The complete owned-item map — see {@link WealthSnapshot#getAllHoldings()}. */
		public Builder allHoldings(Map<Integer, ItemStack> allHoldings)
		{
			this.allHoldings = allHoldings;
			return this;
		}

		public WealthSnapshot build()
		{
			return new WealthSnapshot(
				inventoryValue,
				equipmentValue,
				geInFlightValue,
				geCollectableValue,
				pouchValue,
				bankValue,
				bankKnown,
				timestampMs,
				topHoldings,
				trackedItems,
				allHoldings);
		}
	}
}
