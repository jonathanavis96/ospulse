package com.ospulse.ui.category;

/**
 * Per-category resettable/pausable state, adapted from RuneLite's own XP
 * Tracker plugin ({@code net.runelite.client.plugins.xptracker.XpStateSingle}
 * — BSD-2-Clause, see repo {@code NOTICE}).
 *
 * <p>The key architectural lesson borrowed from XP Tracker: don't bolt
 * per-category reset onto one monolithic accumulator ({@link
 * com.ospulse.session.SessionEngine} is exactly that kind of accumulator).
 * Instead, each independently resettable/pausable thing on the panel (a
 * session stat, a loot source, an XP skill) gets its own small state object
 * that can be reset or paused without disturbing its siblings.
 *
 * <p>Unlike {@code XpStateSingle}, this class carries no domain data itself
 * (no XP counters) — the actual figures always come fresh from the latest
 * {@link com.ospulse.session.SessionSnapshot}. What this tracks is purely the
 * user-facing controls XP Tracker's {@code XpInfoBox} exposes per skill:
 * whether the category is paused, and a reset "epoch" that callers can use to
 * decide whether to keep displaying data gained before the last reset.
 *
 * <p>Not thread-safe; intended for exclusive use on the Swing EDT, matching
 * every other {@code CollapsibleSection}.
 */
public final class CategoryState
{
	private final String id;
	private boolean paused;
	/**
	 * Incremented on every {@link #reset()}. Sections that want "reset"
	 * semantics for values derived from session-lifetime data (rather than
	 * genuinely per-category accumulators) can snapshot this alongside the
	 * baseline value at reset time and subtract it back out when rendering.
	 */
	private int resetEpoch;
	private long resetAtMs;

	public CategoryState(String id)
	{
		this.id = id;
	}

	public String getId()
	{
		return id;
	}

	public boolean isPaused()
	{
		return paused;
	}

	public void setPaused(boolean paused)
	{
		this.paused = paused;
	}

	public void togglePaused()
	{
		this.paused = !this.paused;
	}

	/** Marks this category as reset at {@code tsMs}, bumping its reset epoch. */
	public void reset(long tsMs)
	{
		this.resetEpoch++;
		this.resetAtMs = tsMs;
	}

	public int getResetEpoch()
	{
		return resetEpoch;
	}

	public long getResetAtMs()
	{
		return resetAtMs;
	}
}
