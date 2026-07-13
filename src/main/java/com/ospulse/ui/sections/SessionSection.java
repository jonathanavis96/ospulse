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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.List;

/**
 * Session P&amp;L breakdown: Loot, Supplies used, Profit, Profit/hr, GE flip,
 * GE positions and Bank, ending in "Net worth change" — the LOCKED session
 * model. "Net worth change" is a pure SUM of four components: {@code Profit
 * (= Loot - Supplies) + GE flip (realised) + GE positions (unrealised
 * mark-to-market on open/collectable GE offers) + Bank (bankValue -
 * bankLineAnchor)}. Profit and GE flip are always included; GE positions and
 * Bank each carry their own include/exclude checkbox (default ON) so a
 * player who only cares about realised activity can zero them out of the
 * total without losing the raw readout. Held/worn/inventory gear price drift
 * deliberately shows up nowhere in this panel — it is zeroed until the item
 * transacts (sold via GE, banked, or consumed/looted) — so there is
 * intentionally no standalone "Unrealized"/"P/L" row anywhere here.
 *
 * <p>A "Show breakdown" checkbox splits the individual component rows from
 * the final "Net worth change" total: unchecked, only elapsed and the total
 * are shown, mirroring a simple win/loss readout; checked (default), every
 * component row is visible above the total.
 *
 * <p>"Loot" is the gp value of items actually picked up — realised gains
 * with GE flip removed (flips have their own line) and excluding consumption
 * spend entirely (see {@link com.ospulse.session.SessionEngine#update});
 * Supplies used is shown below it as a separate spent/negative-styled
 * readout; "Profit" = Loot − Supplies used, and Profit/hr is the hourly
 * extrapolation of that net figure. Internal ids keep the older
 * profit/net-profit naming so persisted reset/pause state survives. Collapsed
 * summary shows elapsed + Loot.
 *
 * <p>Each stat row carries an XP-Tracker-style right-click menu (see {@code
 * com.ospulse.ui.category.CategoryContextMenu}, ported from RuneLite's XP
 * Tracker plugin). Since these stats are read straight off the single {@link
 * com.ospulse.session.SessionEngine} accumulator rather than each having
 * their own independent counter, "Reset" here rebases the displayed figure
 * to zero from that point (subtracting a captured baseline) rather than
 * clearing any engine-level state; "Pause" freezes the row's displayed value
 * rather than stopping the engine from tracking it. The include/exclude
 * toggles are a separate, display-only concern (whether a component counts
 * towards "Net worth change") and are independent of reset/pause.
 */
public final class SessionSection extends CollapsibleSection
{
	public static final String KEY = "session";

	private static final String CAT_ELAPSED = "session:elapsed";
	private static final String CAT_PROFIT = "session:profit";
	private static final String CAT_SUPPLIES = "session:supplies";
	private static final String CAT_NET_PROFIT = "session:netProfit";
	private static final String CAT_PROFIT_PER_HOUR = "session:profitPerHour";
	private static final String CAT_NET_WORTH_DELTA = "session:netWorthDelta";
	private static final String CAT_GE_PNL = "session:gePnl";
	private static final String CAT_GE_POSITIONS = "session:gePositions";
	private static final String CAT_BANK = "session:bank";

	private final JLabel elapsedValue;
	private final JLabel profitValue;
	private final JLabel suppliesUsedValue;
	private final JLabel netProfitValue;
	private final JLabel profitPerHourValue;
	private final JLabel netWorthDeltaValue;
	private final JLabel geRealizedPnlValue;
	private final JCheckBox gePositionsToggle;
	private final JLabel gePositionsValue;
	private final JCheckBox bankToggle;
	private final JLabel bankValue;
	private final JCheckBox splitToggle;
	private final JPanel breakdownPanel;

	private final CategorySectionSupport categorySupport;

	/** Baselines captured at "Reset" time (raw engine value when reset was clicked), keyed by category id. */
	private long profitBaseline;
	private long suppliesBaseline;
	private long netProfitBaseline;
	private long profitPerHourBaseline;
	private long netWorthDeltaBaseline;
	private long gePnlBaseline;

	private long elapsedMs;
	private long displayedProfit;
	/** The raw (untoggled) "Net worth change" sum from the last snapshot, so the toggles can recompute on click without waiting for the next apply(). */
	private long lastRawNetWorthDelta;
	private long lastRawGePositions;
	private long lastRawBankDelta;

	public SessionSection(CollapseStore store, Plugin plugin, Client client, OverlayManager overlayManager)
	{
		super(KEY, "Session", store);
		categorySupport = new CategorySectionSupport(plugin, client, overlayManager);

		elapsedValue = PanelWidgets.statRow(body(), "Elapsed",
			categorySupport.buildMenu(CAT_ELAPSED, null));

		// Rows 1-6 (+ Profit/hr) of the LOCKED layout live in their own panel so
		// the "Show breakdown" toggle can hide them as one block while leaving
		// Elapsed and the Net worth change total (row 8) always visible.
		breakdownPanel = new JPanel();
		breakdownPanel.setLayout(new BoxLayout(breakdownPanel, BoxLayout.Y_AXIS));
		breakdownPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		breakdownPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(breakdownPanel);

		// 1. Loot — gross realised gains (loot + trade P&L), before the cost of
		// supplies burned to earn them. (Internal ids stay CAT_PROFIT/profitValue
		// so persisted reset/pause state survives.)
		profitValue = PanelWidgets.statRow(breakdownPanel, "Loot",
			categorySupport.buildMenu(CAT_PROFIT, null));
		// 2. Supplies used.
		suppliesUsedValue = PanelWidgets.statRow(breakdownPanel, "Supplies used",
			categorySupport.buildMenu(CAT_SUPPLIES, null));
		// 3. Profit = Loot - Supplies used. (Internal id stays CAT_NET_PROFIT/netProfitValue.)
		netProfitValue = PanelWidgets.statRow(breakdownPanel, "Profit",
			categorySupport.buildMenu(CAT_NET_PROFIT, null));
		profitPerHourValue = PanelWidgets.statRow(breakdownPanel, "Profit/hr",
			categorySupport.buildMenu(CAT_PROFIT_PER_HOUR, null));
		// 4. GE flip (realised) — always included in the total, no toggle.
		geRealizedPnlValue = PanelWidgets.statRow(breakdownPanel, "GE flip",
			categorySupport.buildMenu(CAT_GE_PNL, null));
		// 5. GE positions (unrealised mark-to-market on open/collectable GE
		// offers) — include/exclude toggle, default ON.
		PanelWidgets.ToggleRow geRow = PanelWidgets.toggleStatRow(breakdownPanel, "GE positions");
		gePositionsToggle = geRow.checkbox;
		gePositionsValue = geRow.value;
		gePositionsToggle.addActionListener(e -> refreshNetWorthChange());
		// 6. Bank (bankValue - bankLineAnchor) — include/exclude toggle, default ON.
		PanelWidgets.ToggleRow bankRow = PanelWidgets.toggleStatRow(breakdownPanel, "Bank");
		bankToggle = bankRow.checkbox;
		bankValue = bankRow.value;
		bankToggle.addActionListener(e -> refreshNetWorthChange());

		// 7. Split toggle: show/hide the component breakdown above, independent
		// of the final total below.
		splitToggle = new JCheckBox("Show breakdown", true);
		splitToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		splitToggle.setFont(FontManager.getRunescapeSmallFont());
		splitToggle.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		splitToggle.setFocusPainted(false);
		splitToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		splitToggle.addActionListener(e ->
		{
			breakdownPanel.setVisible(splitToggle.isSelected());
			body().revalidate();
			body().repaint();
		});
		body().add(splitToggle);

		// 8. Net worth change — LAST, the sum of the always-included components
		// (Profit, GE flip) plus whichever of GE positions/Bank are toggled on.
		netWorthDeltaValue = PanelWidgets.statRow(body(), "Net worth change",
			categorySupport.buildMenu(CAT_NET_WORTH_DELTA, null));

		categorySupport.setLinesSupplier(CAT_PROFIT, () -> List.of(
			new CategoryOverlay.Line("Loot", GpFormat.format(displayedProfit))));
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		elapsedMs = snapshot.getElapsedMs();
		long rawProfit = snapshot.getLootValue();
		long rawSuppliesUsed = snapshot.getSuppliesUsed();
		long rawNetProfit = snapshot.getNetProfit();
		long rawProfitPerHour = snapshot.getProfitPerHour();
		long rawGePnl = snapshot.getGeRealizedPnl();
		long rawGePositions = snapshot.getGePositions();
		long rawBankDelta = snapshot.getBankDelta();
		lastRawNetWorthDelta = snapshot.getNetWorthDelta();
		lastRawGePositions = rawGePositions;
		lastRawBankDelta = rawBankDelta;

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
		if (!categorySupport.controller().isPaused(CAT_NET_PROFIT))
		{
			PanelWidgets.setSignedGpLabel(netProfitValue, rawNetProfit - netProfitBaseline);
		}
		if (!categorySupport.controller().isPaused(CAT_PROFIT_PER_HOUR))
		{
			PanelWidgets.setSignedGpLabel(profitPerHourValue, rawProfitPerHour - profitPerHourBaseline);
		}
		if (!categorySupport.controller().isPaused(CAT_GE_PNL))
		{
			PanelWidgets.setSignedGpLabel(geRealizedPnlValue, rawGePnl - gePnlBaseline);
		}
		if (!categorySupport.controller().isPaused(CAT_GE_POSITIONS))
		{
			PanelWidgets.setSignedGpLabel(gePositionsValue, rawGePositions);
		}
		if (!categorySupport.controller().isPaused(CAT_BANK))
		{
			PanelWidgets.setSignedGpLabel(bankValue, rawBankDelta);
		}

		rebaseIfJustReset(CAT_PROFIT, rawProfit, v -> profitBaseline = v);
		rebaseIfJustReset(CAT_SUPPLIES, rawSuppliesUsed, v -> suppliesBaseline = v);
		rebaseIfJustReset(CAT_NET_PROFIT, rawNetProfit, v -> netProfitBaseline = v);
		rebaseIfJustReset(CAT_PROFIT_PER_HOUR, rawProfitPerHour, v -> profitPerHourBaseline = v);
		rebaseIfJustReset(CAT_NET_WORTH_DELTA, lastRawNetWorthDelta, v -> netWorthDeltaBaseline = v);
		rebaseIfJustReset(CAT_GE_PNL, rawGePnl, v -> gePnlBaseline = v);

		refreshNetWorthChange();
		refreshSummary();
	}

	/**
	 * Recomputes and renders the "Net worth change" total: the full raw sum
	 * from the last snapshot, minus whichever of GE positions/Bank is
	 * currently toggled OFF. Called on every {@link #apply} and immediately on
	 * a toggle click, so unchecking a box updates the total without waiting
	 * for the next snapshot.
	 */
	private void refreshNetWorthChange()
	{
		if (categorySupport.controller().isPaused(CAT_NET_WORTH_DELTA))
		{
			return;
		}
		long total = lastRawNetWorthDelta;
		if (!gePositionsToggle.isSelected())
		{
			total -= lastRawGePositions;
		}
		if (!bankToggle.isSelected())
		{
			total -= lastRawBankDelta;
		}
		PanelWidgets.setSignedGpLabel(netWorthDeltaValue, total - netWorthDeltaBaseline);
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
		netProfitBaseline = 0;
		profitPerHourBaseline = 0;
		netWorthDeltaBaseline = 0;
		gePnlBaseline = 0;
		elapsedMs = 0;
		displayedProfit = 0;
		lastRawNetWorthDelta = 0;
		lastRawGePositions = 0;
		lastRawBankDelta = 0;
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

	/** The currently-displayed "Net worth change" total (post-toggle, post-reset-baseline). */
	long netWorthChangeForTest()
	{
		long total = lastRawNetWorthDelta;
		if (!gePositionsToggle.isSelected())
		{
			total -= lastRawGePositions;
		}
		if (!bankToggle.isSelected())
		{
			total -= lastRawBankDelta;
		}
		return total - netWorthDeltaBaseline;
	}

	void setGePositionsToggleForTest(boolean included)
	{
		gePositionsToggle.setSelected(included);
		refreshNetWorthChange();
	}

	void setBankToggleForTest(boolean included)
	{
		bankToggle.setSelected(included);
		refreshNetWorthChange();
	}

	void setSplitToggleForTest(boolean expanded)
	{
		splitToggle.setSelected(expanded);
		breakdownPanel.setVisible(expanded);
	}

	boolean isBreakdownVisibleForTest()
	{
		return breakdownPanel.isVisible();
	}

	@Override
	protected String summaryText()
	{
		return PanelWidgets.formatElapsed(elapsedMs) + "  " + GpFormat.format(displayedProfit);
	}
}
