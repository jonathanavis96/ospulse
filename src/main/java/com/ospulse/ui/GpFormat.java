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

	/**
	 * Renders a gp amount using the "cent" number styling: unbolded
	 * whole-number part in the default white, with the decimal point,
	 * fractional digits and k/m/b suffix (if any) dimmed grey and half-size.
	 * Delegates to {@link CentFormat} so wealth/session-panel rows share the
	 * exact same misread-resistant treatment as {@code GearSection}'s
	 * DPS/Accuracy/Avg-hit/TTK/Overkill readouts. Built on top of
	 * {@link #format(long)} — the sign stays with the integer part.
	 */
	public static String centFragment(long gp)
	{
		return CentFormat.fragment(format(gp));
	}

	/**
	 * {@link #centFragment(long)}, but with an explicit integer/decimal
	 * colour pairing — e.g. {@link CentFormat#GREEN}/{@link
	 * CentFormat#GREEN_DIM} for a positive gp delta, or {@link
	 * CentFormat#RED}/{@link CentFormat#RED_DIM} for a negative one.
	 */
	public static String centFragment(long gp, String intColor, String decimalColor)
	{
		return CentFormat.fragment(format(gp), intColor, decimalColor);
	}

	/** {@link #centFragment(long)} as a standalone HTML label string. */
	public static String centHtml(long gp)
	{
		return "<html>" + centFragment(gp) + "</html>";
	}

	/** {@link #centFragment(long, String, String)} as a standalone HTML label string. */
	public static String centHtml(long gp, String intColor, String decimalColor)
	{
		return "<html>" + centFragment(gp, intColor, decimalColor) + "</html>";
	}
}
