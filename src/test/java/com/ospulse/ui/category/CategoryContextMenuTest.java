package com.ospulse.ui.category;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

	/** "Pause all" / "Reset all" / "Reset others" must reach every row that has a menu, not only rows touched one by one. */
	@Test
	public void bulkActionsReachRowsNeverTouchedIndividually()
	{
		CategoryController controller = new CategoryController();
		CategoryContextMenu.build(controller, "a", null, null, () -> 0L);
		CategoryContextMenu.build(controller, "b", null, null, () -> 0L);

		controller.setPausedAll(true);
		assertTrue(controller.isPaused("a"));
		assertTrue(controller.isPaused("b"));

		controller.resetOthers("a", 1L);
		assertEquals(0, controller.resetEpoch("a"));
		assertEquals(1, controller.resetEpoch("b"));

		controller.resetAll(2L);
		assertEquals(1, controller.resetEpoch("a"));
		assertEquals(2, controller.resetEpoch("b"));
	}

	/** Session-row menus are built once, so a full panel reset must not unregister their rows. */
	@Test
	public void bulkActionsStillReachRowsAfterClearAll()
	{
		CategoryController controller = new CategoryController();
		CategoryContextMenu.build(controller, "a", null, null, () -> 0L);
		controller.setPaused("a", true);
		controller.reset("a", 1L);

		controller.clearAll();
		assertEquals(false, controller.isPaused("a"));
		assertEquals(0, controller.resetEpoch("a"));

		controller.setPausedAll(true);
		assertTrue(controller.isPaused("a"));
	}
}
