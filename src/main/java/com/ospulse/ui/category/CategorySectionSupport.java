package com.ospulse.ui.category;

import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.swing.JPopupMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Wires a {@link CategoryController} to {@link CategoryOverlay}s and {@link
 * CategoryContextMenu}s for one {@code CollapsibleSection}, so each section
 * (session stats, loot sources, XP skills) doesn't have to hand-roll the
 * "Add to canvas" -&gt; create-and-register-an-{@code Overlay},
 * "Remove from canvas" -&gt; unregister-it plumbing itself. This is the bit
 * that corresponds to {@code XpTrackerPlugin#addOverlay}/{@code
 * removeOverlay} in the original (BSD-2-Clause) — see repo {@code NOTICE}.
 *
 * <p>Not thread-safe; construct and use on the Swing EDT only, same as the
 * {@code CollapsibleSection}s that own an instance of this.
 */
public final class CategorySectionSupport
{
	private final Plugin plugin;
	private final Client client;
	private final OverlayManager overlayManager;
	private final CategoryController controller = new CategoryController();
	private final Map<String, CategoryOverlay> activeOverlays = new LinkedHashMap<>();

	public CategorySectionSupport(Plugin plugin, Client client, OverlayManager overlayManager)
	{
		this.plugin = plugin;
		this.client = client;
		this.overlayManager = overlayManager;
		this.controller.setCanvasListener(this::onCanvasChange);
	}

	public CategoryController controller()
	{
		return controller;
	}

	/**
	 * Builds a right-click popup menu for {@code categoryId}, wired to this
	 * support's controller. {@code womMetric} is an optional Wise Old Man
	 * metric segment (e.g. a skill name); pass {@code null} to link to the
	 * player's overall gains.
	 */
	public JPopupMenu buildMenu(String categoryId, String womMetric)
	{
		return CategoryContextMenu.build(controller, categoryId, this::currentRsn, womMetric,
			System::currentTimeMillis);
	}

	private String currentRsn()
	{
		if (client == null || client.getLocalPlayer() == null)
		{
			return null;
		}
		return client.getLocalPlayer().getName();
	}

	private void onCanvasChange(CategoryController.CanvasChange change)
	{
		if (overlayManager == null)
		{
			return;
		}
		if (change.onCanvas)
		{
			addOverlay(change.categoryId);
		}
		else
		{
			removeOverlay(change.categoryId);
		}
	}

	private void addOverlay(String categoryId)
	{
		removeOverlay(categoryId);
		CategoryOverlay overlay = new CategoryOverlay(plugin, categoryId, categoryId,
			lineSuppliers.getOrDefault(categoryId, List::of));
		activeOverlays.put(categoryId, overlay);
		overlayManager.add(overlay);
	}

	private void removeOverlay(String categoryId)
	{
		CategoryOverlay overlay = activeOverlays.remove(categoryId);
		if (overlay != null)
		{
			overlayManager.remove(overlay);
		}
	}

	/**
	 * Per-category line suppliers, registered by the owning section so the
	 * overlay (created lazily on "Add to canvas") always renders the latest
	 * figures. Must be set before the user can add that category to canvas.
	 */
	private final Map<String, Supplier<List<CategoryOverlay.Line>>> lineSuppliers = new LinkedHashMap<>();

	public void setLinesSupplier(String categoryId, Supplier<List<CategoryOverlay.Line>> supplier)
	{
		lineSuppliers.put(categoryId, supplier);
	}

	/** Removes every active canvas overlay this support instance has registered. Idempotent. */
	public void removeAllOverlays()
	{
		for (String categoryId : activeOverlays.keySet().toArray(new String[0]))
		{
			removeOverlay(categoryId);
			controller.setOnCanvas(categoryId, false);
		}
	}
}
