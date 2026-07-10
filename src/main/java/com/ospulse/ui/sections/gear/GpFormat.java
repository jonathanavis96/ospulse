package com.ospulse.ui.sections.gear;

import java.awt.Color;
import java.util.Locale;

/**
 * Formats and colours gp (coin) amounts for the Gear DPS panel. Colour by
 * magnitude per Jonathan's spec: under 100K is yellow, 100K up to just under
 * 10M is white, 10M and above is green (the GearScape {@code #08F67F} green).
 */
public final class GpFormat
{
	public static final Color YELLOW = new Color(240, 220, 90);
	public static final Color WHITE = Color.WHITE;
	public static final Color GREEN = new Color(8, 246, 127);

	private GpFormat()
	{
	}

	/** Colour for a gp amount: {@code <100K} yellow, {@code 100K–<10M} white, {@code >=10M} green. */
	public static Color color(long gp)
	{
		long a = Math.abs(gp);
		if (a >= 10_000_000)
		{
			return GREEN;
		}
		if (a >= 100_000)
		{
			return WHITE;
		}
		return YELLOW;
	}

	/**
	 * Short stacked amount capped at four visible digits: {@code 10B},
	 * {@code 9999M}, {@code 10M}, {@code 1.5M}, {@code 350K}, {@code 999}. Values
	 * that would need a fifth digit as millions (>= 10B) roll over to billions,
	 * so the widest string is "9999M".
	 */
	public static String format(long gp)
	{
		long a = Math.abs(gp);
		if (a >= 10_000_000_000L)
		{
			return (gp / 1_000_000_000L) + "B";
		}
		if (a >= 10_000_000)
		{
			return (gp / 1_000_000) + "M";
		}
		if (a >= 1_000_000)
		{
			return String.format(Locale.ROOT, "%.1fM", gp / 1_000_000.0);
		}
		if (a >= 1_000)
		{
			return String.format(Locale.ROOT, "%.0fK", gp / 1000.0);
		}
		return String.valueOf(gp);
	}
}
