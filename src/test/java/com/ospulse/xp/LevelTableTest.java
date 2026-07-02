package com.ospulse.xp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LevelTableTest
{
	@Test
	public void xpForLevelMatchesKnownAnchors()
	{
		assertEquals(0L, LevelTable.xpForLevel(1));
		assertEquals(83L, LevelTable.xpForLevel(2));
		assertEquals(1_154L, LevelTable.xpForLevel(10));
		assertEquals(101_333L, LevelTable.xpForLevel(50));
		assertEquals(6_517_253L, LevelTable.xpForLevel(92));
		assertEquals(13_034_431L, LevelTable.xpForLevel(99));
	}

	@Test
	public void xpForLevelClampsOutOfRangeLevels()
	{
		assertEquals(0L, LevelTable.xpForLevel(0));
		assertEquals(0L, LevelTable.xpForLevel(-5));
		assertEquals(13_034_431L, LevelTable.xpForLevel(100));
		assertEquals(13_034_431L, LevelTable.xpForLevel(126));
	}

	@Test
	public void levelForXpReturnsHighestReachedLevel()
	{
		assertEquals(1, LevelTable.levelForXp(0L));
		assertEquals(1, LevelTable.levelForXp(82L));
		assertEquals(2, LevelTable.levelForXp(83L));
		assertEquals(9, LevelTable.levelForXp(1_153L));
		assertEquals(10, LevelTable.levelForXp(1_154L));
		assertEquals(50, LevelTable.levelForXp(101_333L));
		assertEquals(91, LevelTable.levelForXp(6_517_252L));
		assertEquals(92, LevelTable.levelForXp(6_517_253L));
		assertEquals(98, LevelTable.levelForXp(13_034_430L));
		assertEquals(99, LevelTable.levelForXp(13_034_431L));
	}

	@Test
	public void levelForXpClampsToOneAndNinetyNine()
	{
		assertEquals(1, LevelTable.levelForXp(-1L));
		assertEquals(99, LevelTable.levelForXp(200_000_000L));
	}

	@Test
	public void tableRoundTripsForEveryLevel()
	{
		for (int level = 1; level <= 99; level++)
		{
			long xp = LevelTable.xpForLevel(level);
			assertEquals("level " + level + " at its threshold",
				level, LevelTable.levelForXp(xp));
			if (level > 1)
			{
				assertEquals("one xp below level " + level,
					level - 1, LevelTable.levelForXp(xp - 1));
			}
		}
	}
}
