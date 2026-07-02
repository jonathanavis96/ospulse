package com.ospulse;

/**
 * Lookback window used when computing a per-item price trend percentage from
 * the OSRS Wiki prices API's 24h timeseries endpoint. Selectable via
 * {@link OSPulseConfig#priceTrendWindow()}.
 */
public enum PriceTrendWindow
{
	DAY("24h", 1),
	WEEK("7d", 7),
	MONTH("30d", 30);

	private final String displayName;
	private final int days;

	PriceTrendWindow(String displayName, int days)
	{
		this.displayName = displayName;
		this.days = days;
	}

	/** Number of days back from now the trend is measured over. */
	public int days()
	{
		return days;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
