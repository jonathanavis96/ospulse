package com.ospulse.ui.sections;

/**
 * Pure, side-effect-free formatters used by {@link HoldingsSection}'s
 * collapsed-header summary. Kept separate (and Swing-free) so the compact
 * value formatting can be unit-tested directly.
 *
 * <p>Distinct from {@link com.ospulse.ui.GpFormat}: that formatter is used
 * throughout the panel for row-level gp values ({@code 1.5m}, {@code 2b}).
 * This one produces the shorter, uppercase-suffix form used specifically in
 * the "Top holdings" summary badge ({@code 2.1B}, {@code 950M}) so the
 * label + trend badge together reliably fit the fixed panel width.
 */
final class HoldingsFormat
{
	private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};

	private HoldingsFormat()
	{
	}

	/**
	 * Compact gp formatting with uppercase suffixes and no thousands
	 * separators, e.g. {@code 950_000_000 -> "950M"},
	 * {@code 2_100_000_000 -> "2.1B"}, {@code 999 -> "999"}. Negative values
	 * keep their sign.
	 */
	static String compact(long value)
	{
		if (value == 0)
		{
			return "0";
		}

		String sign = value < 0 ? "-" : "";
		long abs = Math.abs(value);

		if (abs < 1000)
		{
			return sign + abs;
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
}
