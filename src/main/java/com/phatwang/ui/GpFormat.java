package com.phatwang.ui;

/**
 * Pure, side-effect-free formatter for gp (game-point / coin) quantities used
 * throughout {@link PhatWangPanel}. Kept separate from the panel so it can be
 * unit-tested without any Swing or RuneLite UI dependencies.
 */
final class GpFormat
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
	static String format(long value)
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
}
