package com.ospulse.ui.sections.gear;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * A decorative gold-coin pile with the resolved budget value (e.g. "50M")
 * painted over its top-left corner in a magnitude colour, per Jonathan's
 * mockup. The value is anchored to the top-left and shrinks to stay within the
 * pile's width (never spills past the gold), with a black drop-shadow for
 * legibility like in-game stack text.
 */
public final class CoinPileBadge extends JComponent
{
	private final BufferedImage pile;
	private final int w;
	private final int h;
	private final Font baseFont;

	private String value = "0";
	private Color valueColor = Color.WHITE;

	public CoinPileBadge(BufferedImage pile, int width, int height)
	{
		this.pile = pile;
		this.w = width;
		this.h = height;
		this.baseFont = FontManager.getRunescapeBoldFont();
		Dimension d = new Dimension(width, height);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
	}

	/** Sets the displayed value text and its (magnitude) colour, then repaints. */
	public void setValue(String value, Color color)
	{
		this.value = value == null ? "" : value;
		this.valueColor = color;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		// Pile fills the component (bottom-aligned so the value sits over its top).
		if (pile != null)
		{
			g2.drawImage(pile, 0, 0, w, h, null);
		}

		// Value at top-left, shrunk until it fits the pile's width.
		float size = 15f;
		Font font = baseFont.deriveFont(Font.BOLD, size);
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();
		while (fm.stringWidth(value) > w - 2 && size > 8f)
		{
			size -= 1f;
			font = baseFont.deriveFont(Font.BOLD, size);
			g2.setFont(font);
			fm = g2.getFontMetrics();
		}
		int x = 1;
		int y = fm.getAscent();
		// Full black outline (8 offsets) so the value stays legible over the
		// gold pile, then the coloured value on top.
		g2.setColor(Color.BLACK);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					g2.drawString(value, x + dx, y + dy);
				}
			}
		}
		g2.setColor(valueColor);
		g2.drawString(value, x, y);
		g2.dispose();
	}
}
