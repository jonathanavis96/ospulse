package com.ospulse.ui;

/**
 * Pure, side-effect-free formatter for gp (game-point / coin) quantities used
 * throughout {@link OSPulsePanel}. Kept separate from the panel so it can be
 * unit-tested without any Swing or RuneLite UI dependencies.
 */
public final class GpFormat
{
	private static final String[] SUFFIXES = {"", "k", "m", "b"};
	private static final long COMMA_THRESHOLD = 100_000L;

	private GpFormat()
	{
	}

	/**
	 * Formats a gp amount for display, e.g. {@code 1234 -> "1,234"},
	 * {@code 1_500_000 -> "1.5m"}, {@code 2_000_000_000 -> "2b"}. Negative
	 * values keep their sign (e.g. {@code -1_500_000 -> "-1.5m"}).
	 */
	public static String format(long value)
	{
		if (value == 0)
		{
			return "0";
		}

		String sign = value < 0 ? "-" : "";
		long abs = Math.abs(value);

		if (abs < COMMA_THRESHOLD)
		{
			return sign + String.format("%,d", abs);
		}

		int magnitude = 0;
		double scaled = abs;
		while (scaled >= 1000 && magnitude < SUFFIXES.length - 1)
		{
			scaled /= 1000.0;
			magnitude++;
		}

		double rounded = Math.round(scaled * 10) / 10.0;
		if (rounded >= 1000 && magnitude < SUFFIXES.length - 1)
		{
			rounded = Math.round((rounded / 1000.0) * 10) / 10.0;
			magnitude++;
		}

		String numberPart = rounded == Math.floor(rounded)
			? String.valueOf((long) rounded)
			: String.valueOf(rounded);

		return sign + numberPart + SUFFIXES[magnitude];
	}

	// Colour of the de-emphasised fractional/suffix part of a "scent" number
	// (see GearSection.dpsFragment's DPS_DECIMAL_COLOR — kept in sync).
	private static final String DECIMAL_COLOR = "#8C8C8C";

	/**
	 * Renders a gp amount using the "scent" number styling: bold whole-number
	 * part, with the decimal point, fractional digits and k/m/b suffix (if
	 * any) dimmed grey. Mirrors {@code GearSection.dpsFragment} so
	 * wealth-panel rows can reuse the same misread-resistant treatment for gp
	 * values. Built on top of {@link #format(long)} — the sign stays with the
	 * bold integer part.
	 */
	public static String scentFragment(long gp)
	{
		String s = format(gp);
		int dot = s.indexOf('.');
		if (dot < 0)
		{
			return "<b>" + s + "</b>";
		}
		return "<b>" + s.substring(0, dot) + "</b>"
			+ "<font color='" + DECIMAL_COLOR + "'>" + s.substring(dot) + "</font>";
	}

	/** {@link #scentFragment} as a standalone HTML label string. */
	public static String scentHtml(long gp)
	{
		return "<html>" + scentFragment(gp) + "</html>";
	}
}
