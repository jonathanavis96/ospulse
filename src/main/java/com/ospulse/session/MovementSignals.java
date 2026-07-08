package com.ospulse.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable per-tick bundle of deliberate item-movement signals fed to {@link SessionEngine#update}. */
public final class MovementSignals
{
	public static final MovementSignals NONE = builder().build();

	private final Set<Integer> droppedItemIds;
	private final Set<Integer> destroyedItemIds;
	private final boolean diedThisTick;
	private final List<LootReceipt> lootReceipts;
	private final Map<Integer, Long> storageEmptied;

	private MovementSignals(Builder b)
	{
		this.droppedItemIds = Collections.unmodifiableSet(new HashSet<>(b.droppedItemIds));
		this.destroyedItemIds = Collections.unmodifiableSet(new HashSet<>(b.destroyedItemIds));
		this.diedThisTick = b.diedThisTick;
		this.lootReceipts = Collections.unmodifiableList(new ArrayList<>(b.lootReceipts));
		this.storageEmptied = Collections.unmodifiableMap(new LinkedHashMap<>(b.storageEmptied));
	}

	public Set<Integer> droppedItemIds() { return droppedItemIds; }
	public Set<Integer> destroyedItemIds() { return destroyedItemIds; }
	public boolean diedThisTick() { return diedThisTick; }
	public List<LootReceipt> lootReceipts() { return lootReceipts; }

	/**
	 * Exact contents (item id → quantity) emptied from a tracked storage
	 * container straight into the bank this tick (a fish barrel emptied at a
	 * bank/deposit box — fish never touch the inventory). Lets the engine draw
	 * the precise ledger entries that materialise in the bank instead of
	 * attributing an id-blind bank-value rise (see
	 * {@link SessionEngine} stored-loot materialisation).
	 */
	public Map<Integer, Long> storageEmptied() { return storageEmptied; }

	public static Builder builder() { return new Builder(); }

	public static final class Builder
	{
		private final Set<Integer> droppedItemIds = new HashSet<>();
		private final Set<Integer> destroyedItemIds = new HashSet<>();
		private boolean diedThisTick;
		private final List<LootReceipt> lootReceipts = new ArrayList<>();
		private final Map<Integer, Long> storageEmptied = new LinkedHashMap<>();

		public Builder dropped(int itemId) { droppedItemIds.add(itemId); return this; }
		public Builder destroyed(int itemId) { destroyedItemIds.add(itemId); return this; }
		public Builder died(boolean died) { this.diedThisTick = died; return this; }
		public Builder lootReceived(LootReceipt r) { lootReceipts.add(r); return this; }

		/** Records {@code qty} of {@code itemId} emptied from a storage container into the bank this tick. */
		public Builder storageEmptied(int itemId, long qty)
		{
			if (qty > 0)
			{
				storageEmptied.merge(itemId, qty, Long::sum);
			}
			return this;
		}

		public MovementSignals build() { return new MovementSignals(this); }
	}
}
