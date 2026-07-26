package com.ospulse.ui.sections.gear;

import net.runelite.client.ui.FontManager;

import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

/**
 * Builds the one word-wrapping advisory "label" every DPS-blind curated note
 * in the Gear section shares — the mechanic-override note, the
 * combat-requirement note, and the consumables reminder note all render
 * through this single method so they read (and wrap) identically. Extracted
 * out of {@code GearSection} verbatim (no behaviour change) so a brand-new
 * advisory panel can reuse the exact same rendering instead of a parallel
 * implementation.
 */
public final class AdvisoryNoteRenderer
{
	private AdvisoryNoteRenderer()
	{
	}

	/**
	 * A read-only, non-opaque, word-wrapping "label" that — unlike a plain
	 * {@code JLabel} (no wrap) or an HTML-{@code JLabel} approach
	 * (hard-coded {@code width:200px}, clipped in a narrower panel) — wraps to
	 * whatever width the enclosing {@code BoxLayout} panel actually gives it.
	 * Standard Swing trick: {@link JTextArea#getPreferredSize()} normally
	 * measures the UNWRAPPED width, so it is overridden here to re-measure
	 * against the parent's current width once one is known, and
	 * {@code getMaximumSize()} caps height at that (dynamic) preferred height
	 * while leaving width free to stretch to the container.
	 */
	public static JTextArea wrappingNote(String text, Color foreground)
	{
		JTextArea area = new JTextArea(text)
		{
			@Override
			public Dimension getPreferredSize()
			{
				Container parent = getParent();
				int width = parent != null ? parent.getWidth() : 0;
				if (width > 0)
				{
					setSize(width, Short.MAX_VALUE);
				}
				return super.getPreferredSize();
			}

			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setForeground(foreground);
		area.setBorder(null);
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		return area;
	}
}
