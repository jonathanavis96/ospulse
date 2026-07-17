package com.ospulse.ui.sections.gear;

import com.ospulse.ui.CentFormat;
import java.util.Locale;

/**
 * Formats DPS numbers for the Gear DPS panel. The sibling of {@link GpFormat}:
 * where that one handles gp amounts, this one handles the "x.xx" damage-per-second
 * values, and both defer to {@link CentFormat} so every "cent" number in the panel
 * shares one treatment.
 */
public final class DpsFormat
{
	private DpsFormat()
	{
	}

	/** A DPS value at the panel's fixed two-decimal precision. */
	public static String format(double dps)
	{
		return String.format(Locale.ROOT, "%.2f", dps);
	}

	/**
	 * Renders a DPS value as an HTML fragment that resists the "1.98 read as
	 * 198" misread — a real report, since a woodcutting felling axe legitimately
	 * does ~2 DPS. Price-tag style: the whole-number part is unbolded in the
	 * given colour, and the decimal point plus the fractional digits are
	 * dimmed and half-size, so the magnitude reads at a glance and the
	 * decimals visibly recede. Delegates to {@link CentFormat} so every
	 * "cent" number in the panel (gp values, DPS, accuracy, avg hit, TTK,
	 * overkill — "cent" meaning the fractional/decimal part, by analogy
	 * with money cents) shares the exact same treatment.
	 */
	public static String fragment(double dps)
	{
		return CentFormat.fragment(format(dps));
	}

	/**
	 * {@link #fragment(double)}, but with an explicit integer/decimal
	 * colour pairing — used by the "vs worn"/optimizer delta rows so a
	 * green (upgrade) or red (downgrade) comparison dims its decimals in the
	 * matching hue instead of the default grey.
	 */
	public static String fragment(double dps, String intColor, String decimalColor)
	{
		return CentFormat.fragment(format(dps), intColor, decimalColor);
	}

	/**
	 * {@link #fragment(double)}, but with the integer in {@code intColor}
	 * and the decimal dimmed to match it (see {@link CentFormat#fragment(String, String)}) —
	 * used by a highlighted/best row whose label foreground isn't the
	 * default white, so the number's own colour has to agree with the row
	 * instead of hard-coding white (an explicit HTML {@code <font color>}
	 * always wins over the label's {@code setForeground}).
	 */
	public static String fragment(double dps, String intColor)
	{
		return CentFormat.fragment(format(dps), intColor);
	}

	/**
	 * {@link #fragment(double)} coloured to match a "vs worn"/optimizer
	 * delta's sign — green (with a dull-green decimal) for an upgrade, red
	 * (dull-red decimal) for a downgrade, or the plain white/grey default
	 * when the delta is effectively zero. {@code delta} is shared across
	 * both the "before" and "after" values in a single comparison row so
	 * they always render in the same colour.
	 */
	public static String deltaFragment(double dps, double delta)
	{
		if (delta > 1e-9)
		{
			return fragment(dps, CentFormat.GREEN, CentFormat.GREEN_DIM);
		}
		if (delta < -1e-9)
		{
			return fragment(dps, CentFormat.RED, CentFormat.RED_DIM);
		}
		return fragment(dps);
	}

	/** {@link #fragment(double)} as a standalone HTML label string. */
	public static String html(double dps)
	{
		return "<html>" + fragment(dps) + "</html>";
	}

	/** {@link #fragment(double, String)} as a standalone HTML label string. */
	public static String html(double dps, String intColor)
	{
		return "<html>" + fragment(dps, intColor) + "</html>";
	}
}
