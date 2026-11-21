package com.ospulse.wealth;

/**
 * One Grand Exchange offer slot's goods of one item awaiting collection: how
 * many units (item quantity, or gp for coins) and what they are worth. Kept per
 * slot and item so a collection can be told apart from a fill or a reprice of
 * something else in the same observation, and valued on its own.
 */
@lombok.Value
public class GeCollectablePosition
{
	long units;
	long value;

	/** Key for a slot's goods of one item. */
	public static long key(int slot, int itemId)
	{
		return ((long) slot << 32) | (itemId & 0xFFFFFFFFL);
	}

	/**
	 * Value of {@code departed} of this position's units, at this position's own
	 * per-unit value. Split so a large stack's value times its units cannot
	 * overflow.
	 */
	public long valueOf(long departed)
	{
		if (units <= 0 || departed <= 0)
		{
			return 0L;
		}
		long n = Math.min(departed, units);
		return (value / units) * n + (value % units) * n / units;
	}
}
