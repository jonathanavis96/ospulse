package com.ospulse.ui.category;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Registry of {@link CategoryState}s plus the reset/pause/canvas actions XP
 * Tracker's right-click menu exposes, ported from
 * {@code net.runelite.client.plugins.xptracker.XpTrackerPlugin}'s
 * {@code resetSkillState}/{@code resetOtherSkillState}/{@code pauseSkill}/
 * {@code pauseAllSkills}/{@code addOverlay}/{@code removeOverlay} family
 * (BSD-2-Clause, see repo {@code NOTICE}) — generalised from "one registry
 * entry per {@code Skill}" to "one entry per arbitrary category id" so the
 * same controller backs session-stat rows, loot-source rows and XP-skill rows
 * alike.
 *
 * <p>This is the plain-Java half of the port: no Swing, no RuneLite overlay
 * types, so it is unit-testable on its own. The Swing side ({@code
 * CategoryContextMenu}) and the canvas side ({@code CategoryOverlay}) call
 * into this for state changes and register/unregister canvas membership via
 * {@link #setOnCanvas}.
 */
public final class CategoryController
{
	private final Map<String, CategoryState> states = new LinkedHashMap<>();
	private final Map<String, Boolean> onCanvas = new LinkedHashMap<>();

	/** Fired whenever canvas membership changes for a category, so the owning section can (de)register an overlay. */
	private Consumer<CanvasChange> canvasListener = change -> {};

	/** One canvas-membership change: which category, and whether it should now be shown on the canvas. */
	public static final class CanvasChange
	{
		public final String categoryId;
		public final boolean onCanvas;

		CanvasChange(String categoryId, boolean onCanvas)
		{
			this.categoryId = categoryId;
			this.onCanvas = onCanvas;
		}
	}

	public void setCanvasListener(Consumer<CanvasChange> listener)
	{
		this.canvasListener = listener == null ? change -> {} : listener;
	}

	/** Returns the state for {@code categoryId}, creating it (not paused, epoch 0) on first use. */
	public CategoryState stateFor(String categoryId)
	{
		Objects.requireNonNull(categoryId, "categoryId");
		return states.computeIfAbsent(categoryId, CategoryState::new);
	}

	public boolean isPaused(String categoryId)
	{
		CategoryState state = states.get(categoryId);
		return state != null && state.isPaused();
	}

	/** The reset epoch for {@code categoryId} (0 if never reset / unknown). */
	public int resetEpoch(String categoryId)
	{
		CategoryState state = states.get(categoryId);
		return state == null ? 0 : state.getResetEpoch();
	}

	/**
	 * Resets one category. Mirrors {@code XpTrackerPlugin#resetSkillState}:
	 * bumps the category's reset epoch and drops any canvas overlay for it
	 * (a reset category showing stale canvas figures would be confusing).
	 */
	public void reset(String categoryId, long tsMs)
	{
		stateFor(categoryId).reset(tsMs);
		setOnCanvas(categoryId, false);
	}

	/**
	 * Resets every known category except {@code exceptCategoryId}. Mirrors
	 * {@code XpTrackerPlugin#resetOtherSkillState}.
	 */
	public void resetOthers(String exceptCategoryId, long tsMs)
	{
		for (String id : states.keySet().toArray(new String[0]))
		{
			if (!id.equals(exceptCategoryId))
			{
				reset(id, tsMs);
			}
		}
	}

	/** Resets every known category, including {@code exceptCategoryId}-style callers with none to exempt. */
	public void resetAll(long tsMs)
	{
		for (String id : states.keySet().toArray(new String[0]))
		{
			reset(id, tsMs);
		}
	}

	/**
	 * Drops every category's pause/reset/canvas state back to just-constructed
	 * (feature 11: panel-wide full reset). Unlike {@link #resetAll} — which only
	 * bumps each category's reset epoch and leaves the {@code states} entries in
	 * place — this forgets the categories entirely, so a paused/reset row starts
	 * fresh (not paused, epoch 0) on the next snapshot. Does NOT fire the canvas
	 * listener: callers that own overlays must drop them first (the section's
	 * {@code CategorySectionSupport.clearAll} does exactly that).
	 */
	public void clearAll()
	{
		states.clear();
		onCanvas.clear();
	}

	/** Sets pause on one category. Mirrors {@code XpTrackerPlugin#pauseSkill}. */
	public void setPaused(String categoryId, boolean paused)
	{
		stateFor(categoryId).setPaused(paused);
	}

	/** Sets pause on every known category. Mirrors {@code XpTrackerPlugin#pauseAllSkills}. */
	public void setPausedAll(boolean paused)
	{
		for (CategoryState state : states.values())
		{
			state.setPaused(paused);
		}
	}

	/** Whether {@code categoryId} currently has a canvas overlay. Mirrors {@code XpTrackerPlugin#hasOverlay}. */
	public boolean isOnCanvas(String categoryId)
	{
		return onCanvas.getOrDefault(categoryId, Boolean.FALSE);
	}

	/**
	 * Adds/removes {@code categoryId} from the canvas and notifies the
	 * registered listener so the caller can add/remove the actual {@code
	 * Overlay} via {@code OverlayManager}. Mirrors {@code
	 * XpTrackerPlugin#addOverlay}/{@code removeOverlay}.
	 */
	public void setOnCanvas(String categoryId, boolean show)
	{
		boolean was = isOnCanvas(categoryId);
		onCanvas.put(categoryId, show);
		if (was != show)
		{
			canvasListener.accept(new CanvasChange(categoryId, show));
		}
	}

	public void toggleOnCanvas(String categoryId)
	{
		setOnCanvas(categoryId, !isOnCanvas(categoryId));
	}
}
