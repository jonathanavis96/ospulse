package com.ospulse.ui;

import org.junit.Test;

import java.awt.Rectangle;
import javax.swing.SwingConstants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the contract that keeps a scrolled list from clipping its
 * right-hand column (the attack-styles DPS label rendering "5." instead of
 * "5.23" — see {@link WidthTrackingPanel}'s javadoc).
 */
public class WidthTrackingPanelTest
{
	private static final Rectangle VISIBLE = new Rectangle(0, 0, 200, 110);

	/**
	 * THE regression: a false here means the scroll pane sizes the view to its
	 * own preferred width, letting content overflow the viewport where — with
	 * HORIZONTAL_SCROLLBAR_NEVER — it is clipped away unreachably.
	 */
	@Test
	public void tracksViewportWidthSoContentCanNeverBeClippedOffTheRightEdge()
	{
		assertTrue(new WidthTrackingPanel(22).getScrollableTracksViewportWidth());
	}

	/** Height must NOT track, or a taller-than-viewport list would stop scrolling. */
	@Test
	public void doesNotTrackViewportHeightSoVerticalScrollingStillWorks()
	{
		assertFalse(new WidthTrackingPanel(22).getScrollableTracksViewportHeight());
	}

	/**
	 * Once the view is Scrollable, JScrollPane's scrollbar reads its increment
	 * from here and IGNORES setUnitIncrement on the scrollbar — so the row
	 * height passed to the constructor has to come back out, or one wheel
	 * notch silently stops stepping exactly one row.
	 */
	@Test
	public void unitIncrementIsTheRowHeightItWasBuiltWith()
	{
		WidthTrackingPanel panel = new WidthTrackingPanel(22);
		assertEquals(22, panel.getScrollableUnitIncrement(VISIBLE, SwingConstants.VERTICAL, 1));
		assertEquals(22, panel.getScrollableUnitIncrement(VISIBLE, SwingConstants.VERTICAL, -1));
	}

	/** A non-positive increment would freeze scrolling entirely. */
	@Test
	public void unitIncrementIsClampedToAtLeastOnePixel()
	{
		assertEquals(1, new WidthTrackingPanel(0).getScrollableUnitIncrement(VISIBLE, SwingConstants.VERTICAL, 1));
	}

	/** A page scroll keeps one row of overlap for context, and never goes backwards. */
	@Test
	public void blockIncrementIsAViewportfulLessOneRowOfOverlap()
	{
		WidthTrackingPanel panel = new WidthTrackingPanel(22);
		assertEquals(88, panel.getScrollableBlockIncrement(VISIBLE, SwingConstants.VERTICAL, 1));

		// Viewport shorter than a row must still advance by a whole row.
		assertEquals(22, panel.getScrollableBlockIncrement(new Rectangle(0, 0, 200, 10), SwingConstants.VERTICAL, 1));
	}
}
