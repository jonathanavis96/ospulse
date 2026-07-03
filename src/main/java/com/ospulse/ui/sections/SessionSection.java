package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;

import javax.swing.JLabel;

/**
 * Session summary: elapsed, profit, supplies used, profit/hr, net-worth delta
 * and GE flip P&L. Profit is the headline figure; supplies used is shown
 * alongside it as a separate spent/negative-styled readout so consumables
 * burned through this session are visible at a glance instead of silently
 * eating into profit. Collapsed summary shows elapsed + profit.
 */
public final class SessionSection extends CollapsibleSection
{
	public static final String KEY = "session";

	private final JLabel elapsedValue;
	private final JLabel profitValue;
	private final JLabel suppliesUsedValue;
	private final JLabel profitPerHourValue;
	private final JLabel netWorthDeltaValue;
	private final JLabel geRealizedPnlValue;

	private long elapsedMs;
	private long profit;

	public SessionSection(CollapseStore store)
	{
		super(KEY, "Session", store);
		elapsedValue = PanelWidgets.statRow(body(), "Elapsed");
		profitValue = PanelWidgets.statRow(body(), "Profit");
		suppliesUsedValue = PanelWidgets.statRow(body(), "Supplies used");
		profitPerHourValue = PanelWidgets.statRow(body(), "Profit/hr");
		netWorthDeltaValue = PanelWidgets.statRow(body(), "Net worth Δ");
		geRealizedPnlValue = PanelWidgets.statRow(body(), "GE flip P&L");
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		elapsedMs = snapshot.getElapsedMs();
		profit = snapshot.getProfit();
		long suppliesUsed = snapshot.getSuppliesUsed();

		elapsedValue.setText(PanelWidgets.formatElapsed(elapsedMs));
		PanelWidgets.setSignedGpLabel(profitValue, profit);
		// Supplies used is always a spend, never a gain — render it as a
		// negative-style figure (like a cost) regardless of sign so it reads
		// consistently as "gp burned", not as profit/loss.
		PanelWidgets.setSignedGpLabel(suppliesUsedValue, -suppliesUsed);
		PanelWidgets.setSignedGpLabel(profitPerHourValue, snapshot.getProfitPerHour());
		PanelWidgets.setSignedGpLabel(netWorthDeltaValue, snapshot.getNetWorthDelta());
		PanelWidgets.setSignedGpLabel(geRealizedPnlValue, snapshot.getGeRealizedPnl());
		refreshSummary();
	}

	@Override
	protected String summaryText()
	{
		return PanelWidgets.formatElapsed(elapsedMs) + "  " + GpFormat.format(profit);
	}
}
