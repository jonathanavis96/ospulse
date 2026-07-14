package com.ospulse.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;

/**
 * Shared, stateless builders for the small labelled rows used across every
 * panel section. Extracted from the original monolithic {@code OSPulsePanel} so
 * each {@link CollapsibleSection} can reuse the exact same row styling.
 */
public final class PanelWidgets
{
	private PanelWidgets()
	{
	}

	/**
	 * Adds a "label ........ value" row to {@code container} and returns the
	 * value label so the caller can mutate it on each update.
	 */
	public static JLabel statRow(JPanel container, String labelText)
	{
		return statRow(container, labelText, null);
	}

	/**
	 * Same as {@link #statRow(JPanel, String)}, but also attaches {@code
	 * popupMenu} to the row (via {@code setComponentPopupMenu}) when
	 * non-null, e.g. for an XP-Tracker-style right-click reset/pause/canvas
	 * menu on a per-stat basis.
	 */
	public static JLabel statRow(JPanel container, String labelText, javax.swing.JPopupMenu popupMenu)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());

		JLabel value = new JLabel("-");
		value.setForeground(Color.WHITE);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(label, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		if (popupMenu != null)
		{
			row.setComponentPopupMenu(popupMenu);
			value.setComponentPopupMenu(popupMenu);
		}
		container.add(row);
		return value;
	}

	/**
	 * A row pairing a {@link #statRow(JPanel, String)}-styled "label ........
	 * value" row with a leading {@link JCheckBox} the caller can read/set to
	 * include or exclude the row from a computed total — e.g. the session
	 * panel's "GE positions"/"Bank" include/exclude toggles. Bundled together
	 * so callers get both live components from one call.
	 */
	public static final class ToggleRow
	{
		public final JCheckBox checkbox;
		public final JLabel value;

		ToggleRow(JCheckBox checkbox, JLabel value)
		{
			this.checkbox = checkbox;
			this.value = value;
		}
	}

	/**
	 * Adds a "[x] label ........ value" row to {@code container}, checked by
	 * default, and returns the checkbox + value label so the caller can read
	 * the toggle state and mutate the value on each update.
	 */
	public static ToggleRow toggleStatRow(JPanel container, String labelText)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JCheckBox checkbox = new JCheckBox(labelText, true);
		checkbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		checkbox.setFont(FontManager.getRunescapeSmallFont());
		checkbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		checkbox.setFocusPainted(false);
		row.add(checkbox, BorderLayout.WEST);

		JLabel value = new JLabel("-");
		value.setForeground(Color.WHITE);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(value, BorderLayout.EAST);

		container.add(row);
		return new ToggleRow(checkbox, value);
	}

	/** A "left ........ right" row (no icon), pinned right. */
	public static JPanel listRow(String left, String right)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel leftLabel = new JLabel(left);
		leftLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		leftLabel.setFont(FontManager.getRunescapeSmallFont());

		JLabel rightLabel = new JLabel(right);
		rightLabel.setForeground(Color.WHITE);
		rightLabel.setFont(FontManager.getRunescapeSmallFont());
		rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(leftLabel, BorderLayout.WEST);
		row.add(rightLabel, BorderLayout.EAST);
		return row;
	}

	/**
	 * A list row with the item's sprite left of {@code leftText}, and
	 * {@code rightText} pinned right. The icon loads asynchronously via
	 * {@link ItemManager} and repaints when ready.
	 *
	 * <p>The text sits in {@code CENTER} so it takes the leftover width and
	 * ellipsizes long names instead of pushing the row (and the whole panel)
	 * wider than the fixed side-panel width.
	 */
	public static JPanel iconRow(ItemManager itemManager, int itemId, String leftText,
		String rightText, Color leftColor)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (itemManager != null && itemId > 0)
		{
			JLabel iconLabel = new JLabel();
			itemManager.getImage(itemId).addTo(iconLabel);
			row.add(iconLabel, BorderLayout.WEST);
		}

		JLabel textLabel = new JLabel(leftText);
		textLabel.setForeground(leftColor);
		textLabel.setFont(FontManager.getRunescapeSmallFont());
		row.add(textLabel, BorderLayout.CENTER);

		JLabel rightLabel = new JLabel(rightText);
		rightLabel.setForeground(Color.WHITE);
		rightLabel.setFont(FontManager.getRunescapeSmallFont());
		rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		rightLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
		row.add(rightLabel, BorderLayout.EAST);

		// Don't let the row stretch vertically under BoxLayout.
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * A row with a ready {@link Image} (e.g. a skill icon) left of
	 * {@code leftText}, and {@code rightText} pinned right. Unlike the item
	 * {@link #iconRow(ItemManager, int, String, String, Color)} overload, this
	 * takes an already-loaded image rather than an async item sprite.
	 */
	public static JPanel iconRow(Image image, String leftText, String rightText, Color leftColor)
	{
		return iconRow(image, leftText, rightText, leftColor, null);
	}

	/**
	 * Same as {@link #iconRow(Image, String, String, Color)}, but also
	 * attaches {@code popupMenu} to the row when non-null, e.g. for an
	 * XP-Tracker-style right-click reset/pause/canvas menu.
	 */
	public static JPanel iconRow(Image image, String leftText, String rightText, Color leftColor,
		javax.swing.JPopupMenu popupMenu)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (image != null)
		{
			row.add(new JLabel(new ImageIcon(image)), BorderLayout.WEST);
		}

		JLabel textLabel = new JLabel(leftText);
		textLabel.setForeground(leftColor);
		textLabel.setFont(FontManager.getRunescapeSmallFont());
		row.add(textLabel, BorderLayout.CENTER);

		JLabel rightLabel = new JLabel(rightText);
		rightLabel.setForeground(Color.WHITE);
		rightLabel.setFont(FontManager.getRunescapeSmallFont());
		rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		rightLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
		row.add(rightLabel, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		if (popupMenu != null)
		{
			row.setComponentPopupMenu(popupMenu);
			textLabel.setComponentPopupMenu(popupMenu);
			rightLabel.setComponentPopupMenu(popupMenu);
		}
		return row;
	}

	public static JLabel emptyRowLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	public static Component spacer()
	{
		return Box.createRigidArea(new Dimension(0, 6));
	}

	public static void setSignedGpLabel(JLabel label, long value)
	{
		label.setText(value >= 0
			? GpFormat.centHtml(value, CentFormat.GREEN, CentFormat.GREEN_DIM)
			: GpFormat.centHtml(value, CentFormat.RED, CentFormat.RED_DIM));
		label.setForeground(value >= 0
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_ERROR_COLOR);
	}

	public static void gpLabel(JLabel label, long value)
	{
		label.setText(GpFormat.centHtml(value));
		resetLabelColor(label);
	}

	public static void resetLabelColor(JLabel label)
	{
		label.setForeground(Color.WHITE);
	}

	public static String formatElapsed(long elapsedMs)
	{
		long totalSeconds = Math.max(0, elapsedMs) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return String.format("%d:%02d:%02d", hours, minutes, seconds);
	}
}
