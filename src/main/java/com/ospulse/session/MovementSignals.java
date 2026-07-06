package com.ospulse.session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Immutable per-tick bundle of deliberate item-movement signals fed to {@link SessionEngine#update}. */
public final class MovementSignals
{
	public static final MovementSignals NONE = builder().build();

	private final Set<Integer> droppedItemIds;
	private final Set<Integer> destroyedItemIds;
	private final boolean diedThisTick;

	private MovementSignals(Builder b)
	{
		this.droppedItemIds = Collections.unmodifiableSet(new HashSet<>(b.droppedItemIds));
		this.destroyedItemIds = Collections.unmodifiableSet(new HashSet<>(b.destroyedItemIds));
		this.diedThisTick = b.diedThisTick;
	}

	public Set<Integer> droppedItemIds() { return droppedItemIds; }
	public Set<Integer> destroyedItemIds() { return destroyedItemIds; }
	public boolean diedThisTick() { return diedThisTick; }

	public static Builder builder() { return new Builder(); }

	public static final class Builder
	{
		private final Set<Integer> droppedItemIds = new HashSet<>();
		private final Set<Integer> destroyedItemIds = new HashSet<>();
		private boolean diedThisTick;

		public Builder dropped(int itemId) { droppedItemIds.add(itemId); return this; }
		public Builder destroyed(int itemId) { destroyedItemIds.add(itemId); return this; }
		public Builder died(boolean died) { this.diedThisTick = died; return this; }
		public MovementSignals build() { return new MovementSignals(this); }
	}
}
