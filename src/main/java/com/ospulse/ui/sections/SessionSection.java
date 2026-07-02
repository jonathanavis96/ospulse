package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;

import javax.swing.JLabel;

/**
 * Session summary: elapsed, profit, profit/hr, net-worth delta and GE flip P&L.
 * Collapsed summary shows elapsed + profit.
 */
public final class SessionSection extends CollapsibleSection
{
	public static final String KEY = "session";

	private final JLabel elapsedValue;
	private final JLabel profitValue;
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
		profitPerHourValue = PanelWidgets.statRow(body(), "Profit/hr");
		netWorthDeltaValue = PanelWidgets.statRow(body(), "Net worth Δ");
		geRealizedPnlValue = PanelWidgets.statRow(body(), "GE flip P&L");
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		elapsedMs = snapshot.getElapsedMs();
		profit = snapshot.getProfit();

		elapsedValue.setText(PanelWidgets.formatElapsed(elapsedMs));
		PanelWidgets.setSignedGpLabel(profitValue, profit);
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
