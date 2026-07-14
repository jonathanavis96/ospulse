package com.ospulse.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

/**
 * A {@link JPanel} for use as a {@link JScrollPane} view that never lays out
 * WIDER than the viewport, so content can only ever scroll vertically and can
 * never be clipped off the right-hand edge.
 *
 * <p><b>The bug this exists to prevent.</b> A plain {@code JPanel} does not
 * implement {@link Scrollable}, so {@code JScrollPane} sizes the view to the
 * view's own <i>preferred</i> width. If that preferred width exceeds the
 * viewport — either because the content is intrinsically wide, or because a
 * vertical scrollbar appeared and stole ~15px of viewport width — the overflow
 * hangs off the right. With {@code HORIZONTAL_SCROLLBAR_NEVER} there is no
 * scrollbar to reach it, so it is silently <i>clipped</i>, not scrolled. Any
 * child pinned to {@code BorderLayout.EAST} is exactly what disappears. This
 * shipped as the attack-styles list rendering "5." instead of "5.23" (the DPS
 * label vanishing under the scroll pane's right edge, 2026-07-14).
 *
 * <p>Returning {@code true} from {@link #getScrollableTracksViewportWidth()}
 * forces the view's width to the viewport's, so the layout manager must fit
 * its children into the visible width instead of overflowing it.
 *
 * <p><b>Gotcha — this takes over the scrollbar's unit increment.</b> Once the
 * view is {@code Scrollable}, {@code JScrollPane}'s scrollbar consults
 * {@link #getScrollableUnitIncrement} and IGNORES any
 * {@code getVerticalScrollBar().setUnitIncrement(n)} the caller set. That is
 * why the increment is a constructor parameter: pass the row height so
 * one wheel/arrow click still steps exactly one row.
 */
public class WidthTrackingPanel extends JPanel implements Scrollable
{
	private final int unitIncrement;

	/**
	 * @param unitIncrement pixels to scroll per unit (arrow click / wheel
	 *                      notch) — pass the list's row height so one click
	 *                      steps one row.
	 */
	public WidthTrackingPanel(int unitIncrement)
	{
		this.unitIncrement = Math.max(1, unitIncrement);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return unitIncrement;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		// A page = a viewport-full, less one row of overlap for context.
		return Math.max(unitIncrement, visibleRect.height - unitIncrement);
	}

	/** Always true — the whole point of this class: fit the width, never clip it. */
	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	/** False, so taller-than-viewport content still scrolls vertically as normal. */
	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}
}
