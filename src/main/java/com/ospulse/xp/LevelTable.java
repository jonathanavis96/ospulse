package com.ospulse.xp;

/**
 * The standard OSRS experience table, mapping levels 1..99 to the total
 * experience required to reach them and back.
 *
 * <p>Uses the canonical formula: {@code points(l) = floor(l + 300 * 2^(l/7))}
 * and {@code xpForLevel(L) = floor(sum(points(l) for l in 1..L-1) / 4)}, with
 * {@code xpForLevel(1) = 0}. Anchors: level 2 = 83 xp, level 10 = 1,154 xp,
 * level 50 = 101,333 xp, level 92 = 6,517,253 xp ("half of 99"), level 99 =
 * 13,034,431 xp.
 *
 * <p>Pure and RuneLite-free so it stays unit-testable.
 */
public final class LevelTable
{
	public static final int MAX_LEVEL = 99;

	/** Index = level (1..99); [0] unused. Total xp required to reach that level. */
	private static final long[] XP_FOR_LEVEL = buildTable();

	private LevelTable()
	{
	}

	private static long[] buildTable()
	{
		long[] table = new long[MAX_LEVEL + 1];
		long points = 0L;
		table[1] = 0L;
		for (int level = 2; level <= MAX_LEVEL; level++)
		{
			int l = level - 1;
			points += (long) Math.floor(l + 300.0 * Math.pow(2.0, l / 7.0));
			table[level] = points / 4;
		}
		return table;
	}

	/**
	 * Total experience required to reach {@code level}. Levels at or below 1
	 * return 0; levels at or above {@link #MAX_LEVEL} return the xp for 99.
	 */
	public static long xpForLevel(int level)
	{
		if (level <= 1)
		{
			return 0L;
		}
		if (level >= MAX_LEVEL)
		{
			return XP_FOR_LEVEL[MAX_LEVEL];
		}
		return XP_FOR_LEVEL[level];
	}

	/**
	 * The highest level whose xp requirement is at most {@code xp}, clamped
	 * to the range 1..{@link #MAX_LEVEL}.
	 */
	public static int levelForXp(long xp)
	{
		int level = 1;
		for (int l = 2; l <= MAX_LEVEL; l++)
		{
			if (XP_FOR_LEVEL[l] > xp)
			{
				break;
			}
			level = l;
		}
		return level;
	}
}
