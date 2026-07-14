package com.ospulse.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A small, self-drawn tick box, sized to sit flush on a text line.
 *
 * <p><b>Why not just style a JCheckBox.</b> The stock box is sized by the
 * look-and-feel, and the LAF is NOT the same everywhere this code runs:
 * RuneLite ships <b>FlatLaf</b>, but a unit-test JVM with no LAF installed
 * gets <b>Metal</b>. Measured (2026-07-15):
 *
 * <pre>
 *                          Metal      FlatLaf (RuneLite)
 *   default box            21x21      19x19
 *   margins/border zeroed  13x13      17x17
 *   stat-row text line     12         12
 * </pre>
 *
 * Zeroing the margin therefore looks like a big win under Metal (21->13) and
 * is nearly worthless under FlatLaf (19->17) — which is how a toggle row
 * shipped ~5px taller than the stat rows around it while a Metal-based test
 * asserted it was within 1px. A test that measures a different LAF from the
 * client is not measuring anything.
 *
 * <p>Drawing the box ourselves removes the LAF from the question entirely:
 * the size is exactly what we pass in, in every JVM, so the test and the
 * client agree. Pass the row's text height ({@code 12}) and the toggle row
 * ends up the same height as a plain stat row — which is what "equal spacing"
 * actually requires.
 *
 * <p>Set via {@code checkbox.setIcon(...)} only: {@link #paintIcon} reads the
 * button's own selected/enabled state, so one icon instance covers every
 * state and no {@code setSelectedIcon} is needed.
 */
public final class SmallCheckBoxIcon implements Icon
{
	/** Matches the 12px RuneScape-small text line the stat rows use. */
	public static final int DEFAULT_SIZE = 12;

	private final int size;

	public SmallCheckBoxIcon(int size)
	{
		this.size = size;
	}

	@Override
	public int getIconWidth()
	{
		return size;
	}

	@Override
	public int getIconHeight()
	{
		return size;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		boolean selected = c instanceof AbstractButton && ((AbstractButton) c).isSelected();
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

			int box = size - 1;
			// An unchecked box must still read as a control, so the outline is
			// always drawn — only the fill/tick carry the state.
			g2.setColor(ColorScheme.DARK_GRAY_COLOR);
			g2.fillRoundRect(x, y, box, box, 3, 3);
			g2.setColor(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR);
			g2.drawRoundRect(x, y, box, box, 3, 3);

			if (selected)
			{
				g2.setColor(c.isEnabled() ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
				g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				// Tick drawn proportionally so it stays centred at any size.
				g2.drawLine(x + Math.round(size * 0.26f), y + Math.round(size * 0.52f),
					x + Math.round(size * 0.44f), y + Math.round(size * 0.72f));
				g2.drawLine(x + Math.round(size * 0.44f), y + Math.round(size * 0.72f),
					x + Math.round(size * 0.76f), y + Math.round(size * 0.28f));
			}
		}
		finally
		{
			g2.dispose();
		}
	}
}
