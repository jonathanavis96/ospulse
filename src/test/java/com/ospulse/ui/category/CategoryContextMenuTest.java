package com.ospulse.ui.category;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the pure URL-building logic in {@link CategoryContextMenu},
 * ported from RuneLite XP Tracker's {@code XpPanel#buildXpTrackerUrl}
 * (BSD-2-Clause). The rest of {@link CategoryContextMenu} builds Swing
 * components and isn't unit-tested here - see the feature report for the
 * in-client visual checks needed instead.
 */
public class CategoryContextMenuTest
{
	@Test
	public void blankRsnProducesEmptyUrl()
	{
		assertEquals("", CategoryContextMenu.buildWiseOldManUrl(null, null));
		assertEquals("", CategoryContextMenu.buildWiseOldManUrl("  ", null));
	}

	@Test
	public void defaultsToOverallMetricWhenNoneGiven()
	{
		String url = CategoryContextMenu.buildWiseOldManUrl("Zezima", null);
		assertEquals("https://wiseoldman.net/players/Zezima/gained?metric=overall&period=week", url);
	}

	@Test
	public void usesGivenMetricLowercased()
	{
		String url = CategoryContextMenu.buildWiseOldManUrl("Zezima", "Woodcutting");
		assertEquals("https://wiseoldman.net/players/Zezima/gained?metric=woodcutting&period=week", url);
	}

	@Test
	public void encodesSpacesInRsn()
	{
		String url = CategoryContextMenu.buildWiseOldManUrl("Iron Man 99", null);
		assertEquals("https://wiseoldman.net/players/Iron+Man+99/gained?metric=overall&period=week", url);
	}

	@Test
	public void trimsSurroundingWhitespaceInRsn()
	{
		String url = CategoryContextMenu.buildWiseOldManUrl("  Zezima  ", null);
		assertEquals("https://wiseoldman.net/players/Zezima/gained?metric=overall&period=week", url);
	}
}
