package com.ospulse.ui.sections.gear;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import net.runelite.client.ui.FontManager;

/**
 * A flat, filled, rounded-rectangle button for the Gear DPS panel — e.g. the
 * green "Find Best" / red "Revert" controls flanking the helmet slot. Paints
 * its own solid background (with hover/pressed/disabled shading) so it reads as
 * a pill regardless of the platform look-and-feel, which would otherwise draw a
 * grey system button. First extracted piece of the {@code ui.sections.gear}
 * package (the GearSection redesign is being modularised section by section).
 */
public final class RoundedButton extends JButton
{
	private static final int ARC = 12;

	private final Color base;
	private final Color hover;
	private final Color pressed;

	public RoundedButton(String text, Color base, Color fg)
	{
		super(text);
		this.base = base;
		this.hover = shade(base, 0.12f);
		this.pressed = shade(base, -0.12f);
		setForeground(fg);
		setFont(FontManager.getRunescapeSmallFont());
		setFocusPainted(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setOpaque(false);
		setRolloverEnabled(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setMargin(new Insets(3, 8, 3, 8));
	}

	/** Convenience factory: styled button wired to {@code onClick} with a tooltip. */
	public static RoundedButton action(String text, Color base, Color fg, String tooltip, ActionListener onClick)
	{
		RoundedButton button = new RoundedButton(text, base, fg);
		button.setToolTipText(tooltip);
		button.addActionListener(onClick);
		return button;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		ButtonModel model = getModel();
		Color bg;
		if (!isEnabled())
		{
			bg = shade(base, -0.28f);
		}
		else if (model.isPressed())
		{
			bg = pressed;
		}
		else if (model.isRollover())
		{
			bg = hover;
		}
		else
		{
			bg = base;
		}
		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
		g2.dispose();
		// Draws the label/icon on top; content-area-filled is off so nothing
		// paints over our rounded background.
		super.paintComponent(g);
	}

	/** Lighten ({@code f>0}) or darken ({@code f<0}) a colour by a fraction of full scale. */
	private static Color shade(Color c, float f)
	{
		return new Color(
			clamp(Math.round(c.getRed() + 255 * f)),
			clamp(Math.round(c.getGreen() + 255 * f)),
			clamp(Math.round(c.getBlue() + 255 * f)));
	}

	private static int clamp(int v)
	{
		return Math.max(0, Math.min(255, v));
	}
}
