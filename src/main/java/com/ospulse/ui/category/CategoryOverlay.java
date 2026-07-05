package com.ospulse.ui.category;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A generic, positionable game-canvas overlay for one panel category — the
 * "Add to canvas" counterpart to a {@code CollapsibleSection} row, adapted
 * from RuneLite's own XP Tracker plugin's {@code XpInfoBoxOverlay} (which
 * pops one skill's XP progress out onto the canvas; BSD-2-Clause, see repo
 * {@code NOTICE}).
 *
 * <p>Unlike {@code XpInfoBoxOverlay}, this isn't specialised to skill XP: it
 * renders a title plus an arbitrary list of label/value lines supplied by a
 * {@link Supplier}, so the same overlay class serves session stats, loot
 * sources and XP skills alike — each {@code CollapsibleSection} decides what
 * lines to show and re-supplies them live (the overlay re-reads the supplier
 * on every render, i.e. every frame, same as {@code XpInfoBoxOverlay} reads
 * live plugin state).
 *
 * <p>Movable via the standard RuneLite overlay drag handling: constructing
 * with the owning {@link Plugin} (rather than the no-arg constructor) opts
 * into the default draggable/{@link OverlayPosition#DETACHED} behaviour once
 * the user drags it, matching every other movable RuneLite overlay including
 * XP Tracker's.
 */
public class CategoryOverlay extends OverlayPanel
{
	/** One label/value row on the overlay, e.g. ("Profit", "1.2m"). */
	public static final class Line
	{
		public final String label;
		public final String value;

		public Line(String label, String value)
		{
			this.label = label;
			this.value = value;
		}
	}

	private final String categoryId;
	private final String title;
	private final Supplier<List<Line>> linesSupplier;
	/** Optional 0.0-1.0 progress source; {@code null} renders no bar (backward-compatible default). */
	private final Supplier<Double> progressSupplier;

	/**
	 * @param plugin        the owning plugin, so RuneLite's overlay drag/reset
	 *                      machinery can find it (matches every {@code
	 *                      OverlayPanel(Plugin)} subclass in RuneLite core)
	 * @param categoryId    stable id for this category, used for equality by
	 *                      {@code CategoryController#isOnCanvas}/{@code
	 *                      removeOverlay}-style lookups done by the owning
	 *                      section
	 * @param title         header line shown at the top of the overlay
	 * @param linesSupplier called on every render; returning an up-to-date
	 *                      list of label/value lines to draw
	 */
	public CategoryOverlay(Plugin plugin, String categoryId, String title, Supplier<List<Line>> linesSupplier)
	{
		this(plugin, categoryId, title, linesSupplier, null);
	}

	/**
	 * Same as {@link #CategoryOverlay(Plugin, String, String, Supplier)}, but
	 * with an optional progress bar rendered below the label/value lines —
	 * matching RuneLite's own XP Tracker {@code XpInfoBoxOverlay}, which
	 * shows a progress-to-next-level bar under its lines.
	 *
	 * @param progressSupplier called on every render, returning progress in
	 *                         [0, 1] to fill the bar; pass {@code null} (or
	 *                         use the shorter constructor) to render without
	 *                         a bar at all, e.g. for loot/session categories
	 *                         that have no single "progress" concept.
	 */
	public CategoryOverlay(Plugin plugin, String categoryId, String title, Supplier<List<Line>> linesSupplier,
		Supplier<Double> progressSupplier)
	{
		super(plugin);
		this.categoryId = categoryId;
		this.title = title;
		this.linesSupplier = linesSupplier;
		this.progressSupplier = progressSupplier;
		setPosition(OverlayPosition.DETACHED);
	}

	public String getCategoryId()
	{
		return categoryId;
	}

	/**
	 * Sets the default on-canvas location for this overlay before it's first
	 * rendered, so multiple "Add to canvas" overlays cascade instead of
	 * landing exactly on top of each other (RuneLite otherwise anchors every
	 * DETACHED overlay with no preferred location at the same default spot).
	 * Has no effect once the user has dragged the overlay, since RuneLite
	 * then persists their own location instead.
	 */
	public void setDefaultLocation(Point point)
	{
		setPreferredLocation(point);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder().text(title).build());

		List<Line> lines = linesSupplier.get();
		if (lines != null)
		{
			for (Line line : lines)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(line.label)
					.right(line.value)
					.build());
			}
		}

		if (progressSupplier != null)
		{
			Double progress = progressSupplier.get();
			if (progress != null)
			{
				double clamped = Math.max(0.0, Math.min(1.0, progress));
				ThinProgressBar progressBar = new ThinProgressBar();
				progressBar.value = clamped;
				progressBar.label = String.format(Locale.ROOT, "%.1f%%", clamped * 100.0);
				panelComponent.getChildren().add(progressBar);
			}
		}

		return super.render(graphics);
	}

	/**
	 * A slim RuneLite-style progress bar for the overlay: a dark trough with a
	 * coloured fill, with the percentage rendered as centred text BELOW the bar
	 * rather than inside it. RuneLite's stock {@link
	 * net.runelite.client.ui.overlay.components.ProgressBarComponent} can't do
	 * either — it clamps its height to a minimum of 16px and always draws its
	 * labels inside the bar — so this reimplements the same {@link
	 * LayoutableRenderableEntity} contract (colours matched to XpInfoBoxOverlay's
	 * bar: {@code (61,56,49)} trough, {@code (82,161,82)} fill) with the two
	 * requested tweaks: thinner, and text below.
	 */
	private static final class ThinProgressBar implements LayoutableRenderableEntity
	{
		private static final int BAR_HEIGHT = 5;
		/** Vertical gap between the bar and the percentage text below it. */
		private static final int TEXT_GAP = 2;

		/** Fill fraction, 0..1. */
		private double value;
		/** Percentage text drawn centred below the bar; empty draws no text. */
		private String label = "";
		private Color foregroundColor = new Color(82, 161, 82);
		private Color backgroundColor = new Color(61, 56, 49);
		private Color fontColor = Color.WHITE;
		private Point preferredLocation = new Point();
		private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, 0);
		private final Rectangle bounds = new Rectangle();

		@Override
		public Dimension render(Graphics2D graphics)
		{
			final FontMetrics metrics = graphics.getFontMetrics();
			final int x = preferredLocation.x;
			final int y = preferredLocation.y;
			final int width = preferredSize.width;
			final double pc = Math.max(0.0, Math.min(1.0, value));
			final int fill = (int) (width * pc);

			// Thin bar: trough first, coloured fill from the left over it.
			graphics.setColor(backgroundColor);
			graphics.fillRect(x, y, width, BAR_HEIGHT);
			graphics.setColor(foregroundColor);
			graphics.fillRect(x, y, fill, BAR_HEIGHT);

			int totalHeight = BAR_HEIGHT;
			if (label != null && !label.isEmpty())
			{
				final int textX = x + (width - metrics.stringWidth(label)) / 2;
				final int textY = y + BAR_HEIGHT + TEXT_GAP + metrics.getAscent();
				final TextComponent text = new TextComponent();
				text.setPosition(textX, textY);
				text.setColor(fontColor);
				text.setText(label);
				text.render(graphics);
				totalHeight = BAR_HEIGHT + TEXT_GAP + metrics.getHeight();
			}

			final Dimension dimension = new Dimension(width, totalHeight);
			bounds.setLocation(preferredLocation);
			bounds.setSize(dimension);
			return dimension;
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}

		@Override
		public void setPreferredLocation(Point location)
		{
			this.preferredLocation = location;
		}

		@Override
		public void setPreferredSize(Dimension dimension)
		{
			this.preferredSize = dimension;
		}
	}

	@Override
	public String getName()
	{
		return super.getName() + ":" + categoryId;
	}

	/** Convenience for building a {@link Line} list without importing the inner type everywhere. */
	public static List<Line> lines()
	{
		return new ArrayList<>();
	}
}
