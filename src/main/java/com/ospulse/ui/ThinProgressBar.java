package com.ospulse.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * A slim, flat horizontal progress bar in the RuneLite side-panel style: a dark
 * track with a coloured fill from the left, its width proportional to a 0..1
 * progress value. No text, no border chrome — meant to sit under a labelled row
 * (a GE offer, an XP-to-level row).
 *
 * <p>Shared by the Grand Exchange and XP sections so both render an identical
 * bar. Set the fill colour with {@link #setForeground(Color)} and the progress
 * with {@link #setProgress(double)}.
 */
public final class ThinProgressBar extends JComponent
{
	private static final int DEFAULT_HEIGHT = 5;

	private final int barHeight;
	private double progress;

	public ThinProgressBar()
	{
		this(DEFAULT_HEIGHT);
	}

	public ThinProgressBar(int barHeight)
	{
		this.barHeight = barHeight;
		setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		Dimension size = new Dimension(0, barHeight);
		setPreferredSize(size);
		setMinimumSize(new Dimension(0, barHeight));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, barHeight));
	}

	/** Progress in the range [0, 1]; values outside are clamped. */
	public void setProgress(double progress)
	{
		double clamped = Math.max(0.0, Math.min(1.0, progress));
		if (clamped != this.progress)
		{
			this.progress = clamped;
			repaint();
		}
	}

	public double getProgress()
	{
		return progress;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		int w = getWidth();
		int h = barHeight;

		g.setColor(getBackground());
		g.fillRect(0, 0, w, h);

		int fill = (int) Math.round(w * progress);
		if (fill > 0)
		{
			g.setColor(getForeground());
			g.fillRect(0, 0, fill, h);
		}
	}
}
