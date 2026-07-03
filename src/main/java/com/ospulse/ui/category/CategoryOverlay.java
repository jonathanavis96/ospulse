package com.ospulse.ui.category;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
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
		super(plugin);
		this.categoryId = categoryId;
		this.title = title;
		this.linesSupplier = linesSupplier;
		setPosition(OverlayPosition.DETACHED);
	}

	public String getCategoryId()
	{
		return categoryId;
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

		return super.render(graphics);
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
