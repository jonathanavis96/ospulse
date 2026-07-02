package com.ospulse.xp;

/**
 * Extension of the standard OSRS experience curve past level 99, up to the
 * "virtual level" cap of 126 that RuneLite's own XP tracker displays once a
 * skill is maxed. Kept as its own small table (rather than raising {@link
 * LevelTable#MAX_LEVEL}) so real-level consumers are unaffected and this
 * plugin's session XP view can keep reporting a next-level target and
 * progress percentage after 99.
 *
 * <p>Same canonical formula as {@link LevelTable}: {@code points(l) = floor(l
 * + 300 * 2^(l/7))} and {@code xpForLevel(L) = floor(sum(points(l) for l in
 * 1..L-1) / 4)}, with {@code xpForLevel(1) = 0}. Anchors: level 99 =
 * 13,034,431 xp, level 100 = 14,391,160 xp, level 120 = 104,273,167 xp,
 * level 126 = 188,884,740 xp.
 *
 * <p>Pure and RuneLite-free so it stays unit-testable.
 */
public final class VirtualLevelTable
{
	public static final int MAX_LEVEL = 126;

	/** Index = level (1..126); [0] unused. Total xp required to reach that level. */
	private static final long[] XP_FOR_LEVEL = buildTable();

	private VirtualLevelTable()
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
	 * return 0; levels at or above {@link #MAX_LEVEL} return the xp for 126.
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
	 * The highest (virtual) level whose xp requirement is at most {@code xp},
	 * clamped to the range 1..{@link #MAX_LEVEL}.
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
