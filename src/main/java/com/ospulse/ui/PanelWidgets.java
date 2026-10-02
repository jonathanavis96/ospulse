package com.ospulse.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

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

	/** A panel on the section background, left-aligned for BoxLayout stacks. */
	public static JPanel panel(LayoutManager layout)
	{
		JPanel p = new JPanel(layout);
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	/** {@link #panel} stacking its children vertically. */
	public static JPanel vbox()
	{
		JPanel p = panel(null);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		return p;
	}

	/** {@link #panel} with the 1px top/bottom padding every list row uses. */
	public static JPanel row(LayoutManager layout)
	{
		JPanel p = panel(layout);
		p.setBorder(new EmptyBorder(1, 0, 1, 0));
		return p;
	}

	/** A small-font label in {@code color}. */
	public static JLabel label(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setForeground(color);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	/** A white, right-aligned small-font value label. */
	public static JLabel valueLabel(String text)
	{
		JLabel l = label(text, Color.WHITE);
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		return l;
	}

	/** Caps the component's maximum height at its preferred height so BoxLayout cannot stretch it. */
	public static <T extends JComponent> T capHeight(T c)
	{
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return c;
	}

	/** An empty popup menu with the standard 5px padding. */
	public static JPopupMenu popupMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));
		return menu;
	}

	/** Adds a menu item running {@code action} and returns it. */
	public static JMenuItem menuItem(JPopupMenu menu, String text, Runnable action)
	{
		JMenuItem item = new JMenuItem(text);
		item.addActionListener(e -> action.run());
		menu.add(item);
		return item;
	}

	/** Runs {@code action} each time {@code menu} is about to show. */
	public static void onPopupShow(JPopupMenu menu, Runnable action)
	{
		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
			{
				action.run();
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e)
			{
			}
		});
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
		JPanel row = row(new BorderLayout());
		JLabel value = valueLabel("-");
		row.add(label(labelText, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
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

	/** Gap in px between the end of a toggle row's name and its tick box. */
	private static final int TOGGLE_NAME_BOX_GAP = 4;

	/**
	 * A bare, tightly-packed tick box (no text of its own — the name is a
	 * separate {@link JLabel} so it can left-align with the plain
	 * {@link #statRow} names).
	 *
	 * <p>The box is drawn by {@link SmallCheckBoxIcon} rather than the
	 * look-and-feel. Zeroing the margin alone is NOT enough: it only shrinks
	 * the LAF box to 17x17 under RuneLite's FlatLaf (vs a 12px text line), so
	 * the row still stood ~5px taller than the stat rows around it. Owning the
	 * icon pins it to the text height in every LAF — see that class's javadoc
	 * for the measurements.
	 */
	private static JCheckBox toggleCheckBox()
	{
		JCheckBox checkbox = new JCheckBox("", true);
		checkbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		checkbox.setIcon(new SmallCheckBoxIcon(SmallCheckBoxIcon.DEFAULT_SIZE));
		checkbox.setMargin(new Insets(0, 0, 0, 0));
		checkbox.setBorder(new EmptyBorder(0, 0, 0, 0));
		checkbox.setBorderPainted(false);
		checkbox.setFocusPainted(false);
		return checkbox;
	}

	/**
	 * Adds a "label ... [x]" row with NO value column — the display-only
	 * toggles (e.g. "Show breakdown") that sit alongside
	 * {@link #toggleStatRow}s. Shares the same name column so every tick box
	 * in the section lines up on one x.
	 */
	public static JCheckBox toggleOnlyRow(JPanel container, String labelText, int nameColumnWidth)
	{
		JPanel row = row(new BorderLayout(4, 0));
		JCheckBox checkbox = toggleCheckBox();
		row.add(nameColumn(labelText, nameColumnWidth, checkbox, null), BorderLayout.WEST);
		container.add(row);
		return checkbox;
	}

	/**
	 * The shared "name ..... [x]" column: the name flush left, the tick box
	 * right-aligned at a fixed width so every row's box shares one x.
	 *
	 * @param tooltip tooltip for the box, or null for none.
	 */
	private static JPanel nameColumn(String labelText, int nameColumnWidth, JCheckBox checkbox, String tooltip)
	{
		JPanel namePanel = new JPanel(new BorderLayout(TOGGLE_NAME_BOX_GAP, 0));
		namePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel label = label(labelText, ColorScheme.LIGHT_GRAY_COLOR);
		namePanel.add(label, BorderLayout.CENTER);

		if (tooltip != null)
		{
			checkbox.setToolTipText(tooltip);
		}
		namePanel.add(checkbox, BorderLayout.EAST);

		// The box carries no text of its own any more, so without this the
		// clickable area would have shrunk from "box + name" to just the box.
		MouseAdapter toggleOnClick = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				checkbox.doClick();
			}
		};
		label.addMouseListener(toggleOnClick);
		namePanel.addMouseListener(toggleOnClick);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		namePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Pin the column so every row's box shares one x. Height stays the
		// panel's natural height so the row never grows taller than a statRow.
		Dimension column = new Dimension(nameColumnWidth, namePanel.getPreferredSize().height);
		namePanel.setPreferredSize(column);
		namePanel.setMinimumSize(column);
		namePanel.setMaximumSize(column);
		return namePanel;
	}

	/**
	 * The width of the shared name column for a set of {@link #toggleStatRow}
	 * rows: the widest name, plus the gap and the tick box itself.
	 *
	 * <p>Pass the SAME value to every {@code toggleStatRow} in one section so
	 * the tick boxes all land on one x (right-aligned at the column's edge)
	 * while the names stay flush-left with the section's plain stat rows.
	 * Measured from the real font + a real checkbox rather than hard-coded, so
	 * it cannot drift when a label is renamed or the LAF's box size changes.
	 */
	public static int toggleNameColumnWidth(String... labelTexts)
	{
		JLabel probe = new JLabel();
		probe.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = probe.getFontMetrics(probe.getFont());
		int widest = 0;
		for (String text : labelTexts)
		{
			widest = Math.max(widest, metrics.stringWidth(text));
		}
		return widest + TOGGLE_NAME_BOX_GAP + toggleCheckBox().getPreferredSize().width;
	}

	/**
	 * Adds a "label ... [x] ........ value" row to {@code container}, checked
	 * by default, and returns the checkbox + value label so the caller can
	 * read the toggle state and mutate the value on each update.
	 *
	 * <p>The name leads and the tick box FOLLOWS it, right-aligned inside a
	 * fixed-width name column ({@code nameColumnWidth}, from
	 * {@link #toggleNameColumnWidth}). That keeps this row's name flush-left
	 * with the plain {@link #statRow} names above it — a leading checkbox used
	 * to indent the name by the box's width, so toggle and non-toggle names
	 * never lined up — while still aligning the boxes with each other.
	 *
	 * @param nameColumnWidth shared column width; pass the same value for
	 *                        every toggle row in a section.
	 */
	public static ToggleRow toggleStatRow(JPanel container, String labelText, int nameColumnWidth)
	{
		JPanel row = row(new BorderLayout(4, 0));
		JCheckBox checkbox = toggleCheckBox();
		row.add(nameColumn(labelText, nameColumnWidth, checkbox,
			"Include " + labelText + " in the total"), BorderLayout.WEST);
		JLabel value = valueLabel("-");
		row.add(value, BorderLayout.EAST);

		container.add(row);
		return new ToggleRow(checkbox, value);
	}

	/** A "left ........ right" row (no icon), pinned right. */
	public static JPanel listRow(String left, String right)
	{
		JPanel row = row(new BorderLayout());
		row.add(label(left, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		row.add(valueLabel(right), BorderLayout.EAST);
		return row;
	}

	/**
	 * A "left ........ ±right" row (no icon), like {@link #listRow(String,
	 * String)} but the right-hand side is a signed gp figure rendered via
	 * {@link #setSignedGpLabel(JLabel, long)} — green for a gain (or an
	 * exact-zero result), red for a loss. Exists so per-row dynamic content
	 * (e.g. one row per active GE offer, rebuilt from scratch on every
	 * update) can get the same signed-gp treatment as the static stat rows
	 * without hand-rolling its own colour logic.
	 */
	public static JPanel signedListRow(String left, long value)
	{
		JPanel row = row(new BorderLayout());
		JLabel rightLabel = valueLabel(null);
		setSignedGpLabel(rightLabel, value);
		row.add(label(left, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
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
		JPanel row = row(new BorderLayout(4, 0));
		if (itemManager != null && itemId > 0)
		{
			JLabel iconLabel = new JLabel();
			itemManager.getImage(itemId).addTo(iconLabel);
			row.add(iconLabel, BorderLayout.WEST);
		}
		row.add(label(leftText, leftColor), BorderLayout.CENTER);
		JLabel rightLabel = valueLabel(rightText);
		rightLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
		row.add(rightLabel, BorderLayout.EAST);

		// Don't let the row stretch vertically under BoxLayout.
		return capHeight(row);
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
		JPanel row = row(new BorderLayout(4, 0));
		if (image != null)
		{
			row.add(new JLabel(new ImageIcon(image)), BorderLayout.WEST);
		}
		JLabel textLabel = label(leftText, leftColor);
		row.add(textLabel, BorderLayout.CENTER);
		JLabel rightLabel = valueLabel(rightText);
		rightLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
		row.add(rightLabel, BorderLayout.EAST);
		capHeight(row);
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
		JLabel label = label(text, ColorScheme.LIGHT_GRAY_COLOR);
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
