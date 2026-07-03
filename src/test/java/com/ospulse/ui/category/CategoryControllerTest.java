package com.ospulse.ui.category;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link CategoryController}, the plain-Java half of the
 * XP-Tracker-style per-category reset/pause/canvas port. No Swing, no
 * RuneLite overlay types involved — see class javadoc for what's ported from
 * where.
 */
public class CategoryControllerTest
{
	@Test
	public void newCategoryStartsNotPausedNotOnCanvasEpochZero()
	{
		CategoryController controller = new CategoryController();

		assertFalse(controller.isPaused("Woodcutting"));
		assertFalse(controller.isOnCanvas("Woodcutting"));
		assertEquals(0, controller.resetEpoch("Woodcutting"));
	}

	@Test
	public void resetBumpsEpochForThatCategoryOnly()
	{
		CategoryController controller = new CategoryController();
		controller.stateFor("Woodcutting");
		controller.stateFor("Fishing");

		controller.reset("Woodcutting", 1000L);

		assertEquals(1, controller.resetEpoch("Woodcutting"));
		assertEquals(0, controller.resetEpoch("Fishing"));
	}

	@Test
	public void resetRemovesCanvasOverlayForThatCategory()
	{
		CategoryController controller = new CategoryController();
		controller.setOnCanvas("Woodcutting", true);
		assertTrue(controller.isOnCanvas("Woodcutting"));

		controller.reset("Woodcutting", 1000L);

		assertFalse(controller.isOnCanvas("Woodcutting"));
	}

	@Test
	public void resetOthersResetsEveryCategoryExceptTheGivenOne()
	{
		CategoryController controller = new CategoryController();
		controller.stateFor("Woodcutting");
		controller.stateFor("Fishing");
		controller.stateFor("Mining");

		controller.resetOthers("Fishing", 5000L);

		assertEquals(1, controller.resetEpoch("Woodcutting"));
		assertEquals(0, controller.resetEpoch("Fishing"));
		assertEquals(1, controller.resetEpoch("Mining"));
	}

	@Test
	public void resetAllResetsEveryKnownCategoryIncludingTheOneGiven()
	{
		CategoryController controller = new CategoryController();
		controller.stateFor("Woodcutting");
		controller.stateFor("Fishing");

		controller.resetAll(9000L);

		assertEquals(1, controller.resetEpoch("Woodcutting"));
		assertEquals(1, controller.resetEpoch("Fishing"));
	}

	@Test
	public void setPausedOnlyAffectsThatCategory()
	{
		CategoryController controller = new CategoryController();

		controller.setPaused("Woodcutting", true);

		assertTrue(controller.isPaused("Woodcutting"));
		assertFalse(controller.isPaused("Fishing"));
	}

	@Test
	public void setPausedAllAffectsEveryKnownCategory()
	{
		CategoryController controller = new CategoryController();
		controller.stateFor("Woodcutting");
		controller.stateFor("Fishing");

		controller.setPausedAll(true);

		assertTrue(controller.isPaused("Woodcutting"));
		assertTrue(controller.isPaused("Fishing"));

		controller.setPausedAll(false);

		assertFalse(controller.isPaused("Woodcutting"));
		assertFalse(controller.isPaused("Fishing"));
	}

	@Test
	public void pausedAllDoesNotAffectCategoriesRegisteredAfterTheCall()
	{
		CategoryController controller = new CategoryController();
		controller.setPausedAll(true);

		// A category first touched after "pause all" was called should not
		// retroactively become paused - matches XpTrackerPlugin's per-Skill
		// loop semantics, which only touches skills known at call time.
		assertFalse(controller.isPaused("Woodcutting"));
	}

	@Test
	public void toggleOnCanvasFlipsState()
	{
		CategoryController controller = new CategoryController();
		assertFalse(controller.isOnCanvas("Woodcutting"));

		controller.toggleOnCanvas("Woodcutting");
		assertTrue(controller.isOnCanvas("Woodcutting"));

		controller.toggleOnCanvas("Woodcutting");
		assertFalse(controller.isOnCanvas("Woodcutting"));
	}

	@Test
	public void canvasListenerFiresOnlyOnActualChange()
	{
		CategoryController controller = new CategoryController();
		List<CategoryController.CanvasChange> events = new ArrayList<>();
		controller.setCanvasListener(events::add);

		controller.setOnCanvas("Woodcutting", true);
		controller.setOnCanvas("Woodcutting", true); // no-op, already true
		controller.setOnCanvas("Woodcutting", false);

		assertEquals(2, events.size());
		assertEquals("Woodcutting", events.get(0).categoryId);
		assertTrue(events.get(0).onCanvas);
		assertFalse(events.get(1).onCanvas);
	}

	@Test
	public void resetFiresCanvasListenerOnlyWhenCategoryWasOnCanvas()
	{
		CategoryController controller = new CategoryController();
		List<CategoryController.CanvasChange> events = new ArrayList<>();

		controller.reset("Woodcutting", 1L); // not on canvas yet - no event
		controller.setCanvasListener(events::add);

		controller.setOnCanvas("Woodcutting", true);
		controller.reset("Woodcutting", 2L); // was on canvas - fires removal

		assertEquals(2, events.size());
		assertTrue(events.get(0).onCanvas);
		assertFalse(events.get(1).onCanvas);
	}
}
