package com.ospulse.ui.category;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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

	/**
	 * Everything the XP-skill flavour of this overlay needs to render like
	 * RuneLite's own {@code XpInfoBoxOverlay}: the skill icon (drawn instead of
	 * a text title), the skill colour (progress-bar fill), two stat lines shown
	 * beside the icon, and the level/percent/next-level labels drawn under the
	 * bar. Supplied fresh each render so figures stay live.
	 */
	public static final class XpModel
	{
		public final BufferedImage icon;
		public final Color skillColor;
		public final String line1Left;
		public final String line1Right;
		public final String line2Left;
		public final String line2Right;
		public final String leftLevel;
		public final String centerPercent;
		public final String rightLevel;
		public final double progress;

		public XpModel(BufferedImage icon, Color skillColor,
			String line1Left, String line1Right, String line2Left, String line2Right,
			String leftLevel, String centerPercent, String rightLevel, double progress)
		{
			this.icon = icon;
			this.skillColor = skillColor;
			this.line1Left = line1Left;
			this.line1Right = line1Right;
			this.line2Left = line2Left;
			this.line2Right = line2Right;
			this.leftLevel = leftLevel;
			this.centerPercent = centerPercent;
			this.rightLevel = rightLevel;
			this.progress = progress;
		}
	}

	private final String categoryId;
	private final String title;
	private final Supplier<List<Line>> linesSupplier;
	/** Optional 0.0-1.0 progress source; {@code null} renders no bar (backward-compatible default). */
	private final Supplier<Double> progressSupplier;
	/**
	 * Optional RuneLite-XpInfoBox-style model source; when set and returning
	 * non-null, the overlay renders the skill-icon + stats + skill-coloured bar
	 * layout instead of the generic title/lines/bar. Null for loot/session
	 * categories, which keep the generic look.
	 */
	private final Supplier<XpModel> xpModelSupplier;

	/** Reused across renders (RuneLite does the same) — the icon+stats sub-panel for the XP layout. */
	private final PanelComponent iconXpPanel = new PanelComponent();

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
		this(plugin, categoryId, title, linesSupplier, null, null);
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
		this(plugin, categoryId, title, linesSupplier, progressSupplier, null);
	}

	/**
	 * Full constructor. When {@code xpModelSupplier} is non-null and returns a
	 * non-null {@link XpModel}, the overlay renders RuneLite's XpInfoBox look
	 * (skill icon + stats + skill-coloured bar) instead of the generic
	 * title/lines/bar; the {@code linesSupplier}/{@code progressSupplier} are
	 * then ignored in favour of the model.
	 */
	public CategoryOverlay(Plugin plugin, String categoryId, String title, Supplier<List<Line>> linesSupplier,
		Supplier<Double> progressSupplier, Supplier<XpModel> xpModelSupplier)
	{
		super(plugin);
		this.categoryId = categoryId;
		this.title = title;
		this.linesSupplier = linesSupplier;
		this.progressSupplier = progressSupplier;
		this.xpModelSupplier = xpModelSupplier;
		// Use RuneLite's snap-corner system (like the real XpInfoBoxOverlay, which
		// leaves the default TOP_LEFT position) rather than DETACHED: the renderer
		// then auto-stacks each overlay in the corner BELOW any existing info/
		// overlays there, so multiple "Add to canvas" boxes form a tidy column
		// instead of piling up at (0,0) over the top-left info. Still movable —
		// the user can drag any of them wherever they like.
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public String getCategoryId()
	{
		return categoryId;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		XpModel xp = xpModelSupplier != null ? xpModelSupplier.get() : null;
		if (xp != null)
		{
			return renderXp(graphics, xp);
		}
		return renderGeneric(graphics);
	}

	/** The generic look (session stats, loot sources): a text title, label/value lines, optional thin bar. */
	private Dimension renderGeneric(Graphics2D graphics)
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
				progressBar.centerLabel = String.format(Locale.ROOT, "%.1f%%", clamped * 100.0);
				panelComponent.getChildren().add(progressBar);
			}
		}

		return super.render(graphics);
	}

	/**
	 * The XP-skill look, a near-verbatim port of RuneLite's {@code
	 * XpInfoBoxOverlay}: the skill icon on the left with two stat lines beside
	 * it (via {@link SplitComponent}), then the skill-coloured progress bar — but
	 * with our two tweaks, i.e. the bar is thin and its current-level / % /
	 * next-level labels render BELOW it rather than inside. No text title (the
	 * icon identifies the skill), matching XpInfoBoxOverlay.
	 */
	private Dimension renderXp(Graphics2D graphics, XpModel xp)
	{
		// Small font so the overlay stays compact — XpInfoBoxOverlay does the same.
		graphics.setFont(FontManager.getRunescapeSmallFont());

		panelComponent.getChildren().clear();
		panelComponent.setGap(new Point(0, 2));

		final LineComponent line1 = LineComponent.builder().left(xp.line1Left).right(xp.line1Right).build();
		final LineComponent line2 = LineComponent.builder().left(xp.line2Left).right(xp.line2Right).build();
		final SplitComponent stats = SplitComponent.builder()
			.first(line1)
			.second(line2)
			.orientation(ComponentOrientation.VERTICAL)
			.build();

		final LayoutableRenderableEntity iconAndStats;
		if (xp.icon != null)
		{
			iconAndStats = SplitComponent.builder()
				.first(new ImageComponent(xp.icon))
				.second(stats)
				.orientation(ComponentOrientation.HORIZONTAL)
				.gap(new Point(4, 0))
				.build();
		}
		else
		{
			iconAndStats = stats;
		}

		iconXpPanel.getChildren().clear();
		iconXpPanel.setBackgroundColor(null);
		iconXpPanel.setBorder(new Rectangle(2, 1, 4, 0));
		iconXpPanel.getChildren().add(iconAndStats);
		panelComponent.getChildren().add(iconXpPanel);

		final ThinProgressBar bar = new ThinProgressBar();
		bar.value = xp.progress;
		if (xp.skillColor != null)
		{
			bar.foregroundColor = xp.skillColor;
		}
		bar.leftLabel = xp.leftLevel;
		bar.centerLabel = xp.centerPercent;
		bar.rightLabel = xp.rightLevel;
		panelComponent.getChildren().add(bar);

		return super.render(graphics);
	}

	/**
	 * A slim RuneLite-style progress bar for the overlay: a dark trough with a
	 * coloured fill, with the current-level / % / next-level labels rendered
	 * BELOW the bar (left / centred / right, at RuneLite's {@code SIDE_LABEL_OFFSET})
	 * rather than inside it. RuneLite's stock {@link
	 * net.runelite.client.ui.overlay.components.ProgressBarComponent} can't do
	 * either — it clamps its height to a minimum of 16px and always draws its
	 * labels inside the bar — so this reimplements the same {@link
	 * LayoutableRenderableEntity} contract (default colours matched to
	 * XpInfoBoxOverlay's bar: {@code (61,56,49)} trough, {@code (82,161,82)}
	 * fill) with the two requested tweaks: thinner, and text below.
	 */
	private static final class ThinProgressBar implements LayoutableRenderableEntity
	{
		private static final int BAR_HEIGHT = 5;
		/** Vertical gap between the bar and the label row below it. */
		private static final int TEXT_GAP = 2;
		/** Left/right label inset from the bar edges (matches ProgressBarComponent). */
		private static final int SIDE_LABEL_OFFSET = 4;

		/** Fill fraction, 0..1. */
		private double value;
		/** Labels drawn below the bar (left-aligned / centred / right-aligned); empty draws nothing. */
		private String leftLabel = "";
		private String centerLabel = "";
		private String rightLabel = "";
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
			final boolean hasText = notEmpty(leftLabel) || notEmpty(centerLabel) || notEmpty(rightLabel);
			if (hasText)
			{
				final int textY = y + BAR_HEIGHT + TEXT_GAP + metrics.getAscent();
				if (notEmpty(leftLabel))
				{
					drawText(graphics, leftLabel, x + SIDE_LABEL_OFFSET, textY);
				}
				if (notEmpty(centerLabel))
				{
					drawText(graphics, centerLabel, x + (width - metrics.stringWidth(centerLabel)) / 2, textY);
				}
				if (notEmpty(rightLabel))
				{
					drawText(graphics, rightLabel, x + width - metrics.stringWidth(rightLabel) - SIDE_LABEL_OFFSET, textY);
				}
				totalHeight = BAR_HEIGHT + TEXT_GAP + metrics.getHeight();
			}

			final Dimension dimension = new Dimension(width, totalHeight);
			bounds.setLocation(preferredLocation);
			bounds.setSize(dimension);
			return dimension;
		}

		private void drawText(Graphics2D graphics, String text, int x, int y)
		{
			final TextComponent component = new TextComponent();
			component.setPosition(x, y);
			component.setColor(fontColor);
			component.setText(text);
			component.render(graphics);
		}

		private static boolean notEmpty(String s)
		{
			return s != null && !s.isEmpty();
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
