package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.wealth.WealthSnapshot;

import javax.swing.JLabel;

/**
 * Net-worth breakdown: net worth, tracked wealth and its components, plus bank
 * (shown as "unknown" until the bank has been opened). Collapsed summary shows
 * net worth.
 */
public final class WealthSection extends CollapsibleSection
{
	public static final String KEY = "wealth";

	private final JLabel netWorthValue;
	private final JLabel trackedValue;
	private final JLabel inventoryValue;
	private final JLabel equipmentValue;
	private final JLabel geInFlightValue;
	private final JLabel pouchValue;
	private final JLabel bankValue;

	private String summary = "-";

	public WealthSection(CollapseStore store)
	{
		super(KEY, "Net worth breakdown", store);
		netWorthValue = PanelWidgets.statRow(body(), "Net worth");
		trackedValue = PanelWidgets.statRow(body(), "Tracked");
		inventoryValue = PanelWidgets.statRow(body(), "Inventory");
		equipmentValue = PanelWidgets.statRow(body(), "Equipment");
		geInFlightValue = PanelWidgets.statRow(body(), "GE in-flight");
		pouchValue = PanelWidgets.statRow(body(), "Pouch");
		bankValue = PanelWidgets.statRow(body(), "Bank");
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		WealthSnapshot wealth = snapshot.getWealth();
		if (wealth == null)
		{
			netWorthValue.setText("—");
			trackedValue.setText("—");
			inventoryValue.setText("—");
			equipmentValue.setText("—");
			geInFlightValue.setText("—");
			pouchValue.setText("—");
			bankValue.setText("—");
			PanelWidgets.resetLabelColor(netWorthValue);
			summary = "—";
			refreshSummary();
			return;
		}

		PanelWidgets.setSignedGpLabel(netWorthValue, wealth.netWorth());
		PanelWidgets.gpLabel(trackedValue, wealth.tracked());
		PanelWidgets.gpLabel(inventoryValue, wealth.getInventoryValue());
		PanelWidgets.gpLabel(equipmentValue, wealth.getEquipmentValue());
		PanelWidgets.gpLabel(geInFlightValue, wealth.getGeInFlightValue());
		PanelWidgets.gpLabel(pouchValue, wealth.getPouchValue());

		if (wealth.isBankKnown())
		{
			PanelWidgets.gpLabel(bankValue, wealth.getBankValue());
		}
		else
		{
			bankValue.setText("— (unknown)");
			PanelWidgets.resetLabelColor(bankValue);
		}

		summary = GpFormat.format(wealth.netWorth());
		refreshSummary();
	}

	@Override
	protected String summaryText()
	{
		return summary;
	}
}
