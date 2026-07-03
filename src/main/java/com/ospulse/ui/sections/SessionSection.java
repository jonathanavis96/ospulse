package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.ui.category.CategoryOverlay;
import com.ospulse.ui.category.CategorySectionSupport;

import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.swing.JLabel;
import java.util.List;

/**
 * Session summary: elapsed, profit, supplies used, profit/hr, net-worth delta
 * and GE flip P&L. Profit is the headline figure and excludes consumption
 * spend entirely (see {@link com.ospulse.session.SessionEngine#update});
 * supplies used is shown alongside it as a separate spent/negative-styled
 * readout so consumables burned through this session are visible at a
 * glance. Collapsed summary shows elapsed + profit.
 *
 * <p>Each stat row carries an XP-Tracker-style right-click menu (see {@code
 * com.ospulse.ui.category.CategoryContextMenu}, ported from RuneLite's XP
 * Tracker plugin). Since these stats are read straight off the single {@link
 * com.ospulse.session.SessionEngine} accumulator rather than each having
 * their own independent counter, "Reset" here rebases the displayed figure
 * to zero from that point (subtracting a captured baseline) rather than
 * clearing any engine-level state; "Pause" freezes the row's displayed value
 * rather than stopping the engine from tracking it.
 */
public final class SessionSection extends CollapsibleSection
{
	public static final String KEY = "session";

	private static final String CAT_ELAPSED = "session:elapsed";
	private static final String CAT_PROFIT = "session:profit";
	private static final String CAT_SUPPLIES = "session:supplies";
	private static final String CAT_PROFIT_PER_HOUR = "session:profitPerHour";
	private static final String CAT_NET_WORTH_DELTA = "session:netWorthDelta";
	private static final String CAT_GE_PNL = "session:gePnl";

	private final JLabel elapsedValue;
	private final JLabel profitValue;
	private final JLabel suppliesUsedValue;
	private final JLabel profitPerHourValue;
	private final JLabel netWorthDeltaValue;
	private final JLabel geRealizedPnlValue;
	private final JLabel unrealizedPnlValue;

	private final CategorySectionSupport categorySupport;

	/** Baselines captured at "Reset" time (raw engine value when reset was clicked), keyed by category id. */
	private long profitBaseline;
	private long suppliesBaseline;
	private long profitPerHourBaseline;
	private long netWorthDeltaBaseline;
	private long gePnlBaseline;

	private long elapsedMs;
	private long displayedProfit;

	public SessionSection(CollapseStore store, Plugin plugin, Client client, OverlayManager overlayManager)
	{
		super(KEY, "Session", store);
		categorySupport = new CategorySectionSupport(plugin, client, overlayManager);

		elapsedValue = PanelWidgets.statRow(body(), "Elapsed",
			categorySupport.buildMenu(CAT_ELAPSED, null));
		profitValue = PanelWidgets.statRow(body(), "Profit",
			categorySupport.buildMenu(CAT_PROFIT, null));
		suppliesUsedValue = PanelWidgets.statRow(body(), "Supplies used",
			categorySupport.buildMenu(CAT_SUPPLIES, null));
		profitPerHourValue = PanelWidgets.statRow(body(), "Profit/hr",
			categorySupport.buildMenu(CAT_PROFIT_PER_HOUR, null));
		netWorthDeltaValue = PanelWidgets.statRow(body(), "Net worth Δ",
			categorySupport.buildMenu(CAT_NET_WORTH_DELTA, null));
		geRealizedPnlValue = PanelWidgets.statRow(body(), "GE flip P&L",
			categorySupport.buildMenu(CAT_GE_PNL, null));
		// Display-only duplicate of the Unrealized P/L figure that already
		// lives at the top of the Holdings section (see HoldingsSection).
		// Shown here too, directly below GE flip P&L, purely as a readout —
		// it carries no category menu/reset/pause (there's nothing to
		// reset independently; it always mirrors the live snapshot value)
		// and is never folded into profit, net worth, or any other
		// computation, so it cannot double-count.
		unrealizedPnlValue = PanelWidgets.statRow(body(), "Unrealized P/L");

		categorySupport.setLinesSupplier(CAT_PROFIT, () -> List.of(
			new CategoryOverlay.Line("Profit", GpFormat.format(displayedProfit))));
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		elapsedMs = snapshot.getElapsedMs();
		long rawProfit = snapshot.getProfit();
		long rawSuppliesUsed = snapshot.getSuppliesUsed();
		long rawProfitPerHour = snapshot.getProfitPerHour();
		long rawNetWorthDelta = snapshot.getNetWorthDelta();
		long rawGePnl = snapshot.getGeRealizedPnl();
		long rawUnrealizedPnl = snapshot.getUnrealizedPnl();

		if (!categorySupport.controller().isPaused(CAT_ELAPSED))
		{
			elapsedValue.setText(PanelWidgets.formatElapsed(elapsedMs));
		}
		if (!categorySupport.controller().isPaused(CAT_PROFIT))
		{
			displayedProfit = rawProfit - profitBaseline;
			PanelWidgets.setSignedGpLabel(profitValue, displayedProfit);
		}
		if (!categorySupport.controller().isPaused(CAT_SUPPLIES))
		{
			// Supplies used is always a spend, never a gain — render it as a
			// negative-style figure (like a cost) regardless of sign so it
			// reads consistently as "gp burned", not as profit/loss.
			PanelWidgets.setSignedGpLabel(suppliesUsedValue, -(rawSuppliesUsed - suppliesBaseline));
		}
		if (!categorySupport.controller().isPaused(CAT_PROFIT_PER_HOUR))
		{
			PanelWidgets.setSignedGpLabel(profitPerHourValue, rawProfitPerHour - profitPerHourBaseline);
		}
		if (!categorySupport.controller().isPaused(CAT_NET_WORTH_DELTA))
		{
			PanelWidgets.setSignedGpLabel(netWorthDeltaValue, rawNetWorthDelta - netWorthDeltaBaseline);
		}
		if (!categorySupport.controller().isPaused(CAT_GE_PNL))
		{
			PanelWidgets.setSignedGpLabel(geRealizedPnlValue, rawGePnl - gePnlBaseline);
		}
		// Read-only mirror of the snapshot's unrealized P/L: always shows the
		// live figure directly, no baseline/pause — see the field javadoc.
		PanelWidgets.setSignedGpLabel(unrealizedPnlValue, rawUnrealizedPnl);

		rebaseIfJustReset(CAT_PROFIT, rawProfit, v -> profitBaseline = v);
		rebaseIfJustReset(CAT_SUPPLIES, rawSuppliesUsed, v -> suppliesBaseline = v);
		rebaseIfJustReset(CAT_PROFIT_PER_HOUR, rawProfitPerHour, v -> profitPerHourBaseline = v);
		rebaseIfJustReset(CAT_NET_WORTH_DELTA, rawNetWorthDelta, v -> netWorthDeltaBaseline = v);
		rebaseIfJustReset(CAT_GE_PNL, rawGePnl, v -> gePnlBaseline = v);

		refreshSummary();
	}

	/** Reset-epoch tracking per category, so a baseline is captured exactly once per "Reset" click. */
	private final java.util.Map<String, Integer> lastSeenEpoch = new java.util.HashMap<>();

	private void rebaseIfJustReset(String categoryId, long rawValue, java.util.function.LongConsumer setBaseline)
	{
		int epoch = categorySupport.controller().resetEpoch(categoryId);
		Integer lastEpoch = lastSeenEpoch.get(categoryId);
		if (lastEpoch == null)
		{
			lastSeenEpoch.put(categoryId, epoch);
			return;
		}
		if (epoch != lastEpoch)
		{
			setBaseline.accept(rawValue);
			lastSeenEpoch.put(categoryId, epoch);
		}
	}

	@Override
	public void removeAllCategoryOverlays()
	{
		categorySupport.removeAllOverlays();
	}

	/**
	 * Full panel reset (feature 11): drop the per-category "Reset" baselines
	 * back to zero and forget every paused/reset category, so the next snapshot
	 * shows the freshly re-anchored engine figures rather than a phantom.
	 *
	 * <p>This is the fix for the "phantom profit on reset" bug: each displayed
	 * stat is {@code raw - baseline}, where a per-row "Reset" click captured
	 * {@code baseline = raw} at that instant. When the panel-wide Reset
	 * re-anchors the engine so {@code raw} returns to ~0, a stale non-zero
	 * baseline would render {@code 0 - baseline} — a large phantom of the
	 * opposite sign. Zeroing the baselines (and clearing {@link #lastSeenEpoch}
	 * so the next {@code apply} re-syncs the epoch without re-capturing a
	 * baseline) makes the fresh figure {@code raw - 0 = raw}.
	 */
	@Override
	public void resetState()
	{
		profitBaseline = 0;
		suppliesBaseline = 0;
		profitPerHourBaseline = 0;
		netWorthDeltaBaseline = 0;
		gePnlBaseline = 0;
		elapsedMs = 0;
		displayedProfit = 0;
		lastSeenEpoch.clear();
		categorySupport.clearAll();
		refreshSummary();
	}

	// ------------------------------------------------- test seams (package)

	long displayedProfitForTest()
	{
		return displayedProfit;
	}

	boolean isPausedForTest(String categoryId)
	{
		return categorySupport.controller().isPaused(categoryId);
	}

	/** Simulates the per-row "Reset" right-click action (bumps the category's reset epoch). */
	void resetCategoryForTest(String categoryId)
	{
		categorySupport.controller().reset(categoryId, System.currentTimeMillis());
	}

	void pauseCategoryForTest(String categoryId, boolean paused)
	{
		categorySupport.controller().setPaused(categoryId, paused);
	}

	@Override
	protected String summaryText()
	{
		return PanelWidgets.formatElapsed(elapsedMs) + "  " + GpFormat.format(displayedProfit);
	}
}
