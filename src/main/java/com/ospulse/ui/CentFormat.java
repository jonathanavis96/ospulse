package com.ospulse.ui;

/**
 * Pure, side-effect-free builder for the "cent" number styling used across
 * the panel — "cent" here as in the fractional/decimal part of a number, by
 * analogy with money cents being the fraction of a currency unit after the
 * decimal point: an integer part and any trailing suffix (k/m/b, %, s, ...)
 * render bright in a context colour, while only the decimal point and its
 * fractional digits (the "cents") are dimmed to a duller colour, so the
 * integer magnitude reads at a glance (the "1.98 read as 198" misread this
 * treatment exists to prevent) without shrinking anything to a
 * harder-to-read size.
 *
 * <p>Shared by {@link GpFormat} (gp values in the wealth/session panels) and
 * {@code GearSection}'s DPS/Accuracy/Avg-hit/TTK/Overkill readouts so every
 * "cent" number in the plugin gets the exact same treatment from one place.
 * Kept dependency-free (no Swing/RuneLite imports) so it's trivially
 * unit-testable and easy to split into its own module later.
 *
 * <p>No {@code size} attribute is emitted at all: every run renders at the
 * label's normal font size, and only the {@code color} changes between the
 * bright integer/suffix spans and the dim decimal span.
 */
public final class CentFormat
{
	private CentFormat()
	{
	}

	/** Default context colour for the integer part — plain white. */
	public static final String WHITE = "#FFFFFF";
	/** Default de-emphasised colour for the fractional (decimal) part — dim grey. */
	public static final String GREY = "#8C8C8C";

	/**
	 * The green used for a positive "vs worn"/gp-gain delta — mirrors
	 * {@code ColorScheme.PROGRESS_COMPLETE_COLOR} (#37F046), which is also
	 * {@code GearSection.DELTA_UP_COLOR} and the colour
	 * {@code PanelWidgets.setSignedGpLabel} already applies to non-negative
	 * gp deltas.
	 */
	public static final String GREEN = "#37F046";

	/**
	 * The red used for a negative "vs worn"/gp-loss delta — mirrors
	 * {@code ColorScheme.PROGRESS_ERROR_COLOR} (#E61E1E), which is also
	 * {@code GearSection.DELTA_DOWN_COLOR} and the colour
	 * {@code PanelWidgets.setSignedGpLabel} already applies to negative gp
	 * deltas.
	 */
	public static final String RED = "#E61E1E";

	/**
	 * Duller/darker decimal companions for {@link #GREEN}/{@link #RED},
	 * produced by {@link #dim(String)} — see there for the exact ratio.
	 */
	public static final String GREEN_DIM = dim(GREEN);
	public static final String RED_DIM = dim(RED);

	/**
	 * Darkens a {@code "#RRGGBB"} colour to the dim companion a "cent"
	 * fragment uses for its de-emphasised part, by scaling each channel to
	 * the same ~55% ratio the existing grey decimal colour already sits at
	 * relative to white ({@code 0x8C / 0xFF ≈ 0.549}):
	 * {@code round(channel * 0.55)}. The single source of truth for that
	 * ratio — {@link #GREEN_DIM}/{@link #RED_DIM} are just {@code dim(GREEN)}/
	 * {@code dim(RED)}, and any caller with its own integer colour (e.g. a
	 * highlighted row's own accent colour) can derive a matching dim decimal
	 * the same way instead of hand-picking one.
	 */
	public static String dim(String hexColor)
	{
		int r = Integer.parseInt(hexColor.substring(1, 3), 16);
		int g = Integer.parseInt(hexColor.substring(3, 5), 16);
		int b = Integer.parseInt(hexColor.substring(5, 7), 16);
		return String.format("#%02X%02X%02X",
			Math.round(r * 0.55f), Math.round(g * 0.55f), Math.round(b * 0.55f));
	}

	/**
	 * Splits {@code formatted} (e.g. {@code "1.5m"}, {@code "100k"},
	 * {@code "1,234"}, a signed value, or a plain integer) into an integer
	 * part (see {@link #integerEnd}), an optional decimal part (a
	 * {@code .} plus its fractional digits, if present right after the
	 * integer part), and a trailing suffix (k/m/b, %, s, ...). The integer
	 * and suffix render in {@code intColor}; only the decimal part — if any —
	 * renders dimmed in {@code decimalColor}. Nothing changes size. Returns a
	 * bare fragment (no surrounding {@code <html>} tags) so callers can
	 * compose it with other fragments (e.g. a "before -&gt; after" comparison
	 * in one label).
	 */
	public static String fragment(String formatted, String intColor, String decimalColor)
	{
		int intEnd = integerEnd(formatted);
		int decimalEnd = intEnd;
		if (decimalEnd < formatted.length() && formatted.charAt(decimalEnd) == '.')
		{
			decimalEnd++;
			while (decimalEnd < formatted.length() && Character.isDigit(formatted.charAt(decimalEnd)))
			{
				decimalEnd++;
			}
		}

		if (decimalEnd == intEnd)
		{
			// No decimal part: integer and suffix are the same colour, so
			// merge them into a single <font> tag instead of two adjacent
			// identical ones.
			return "<font color='" + intColor + "'>" + formatted + "</font>";
		}

		String integerPart = formatted.substring(0, intEnd);
		String decimalPart = formatted.substring(intEnd, decimalEnd);
		String suffixPart = formatted.substring(decimalEnd);

		StringBuilder result = new StringBuilder();
		result.append("<font color='").append(intColor).append("'>").append(integerPart).append("</font>");
		result.append("<font color='").append(decimalColor).append("'>").append(decimalPart).append("</font>");
		if (!suffixPart.isEmpty())
		{
			result.append("<font color='").append(intColor).append("'>").append(suffixPart).append("</font>");
		}
		return result.toString();
	}

	/**
	 * Index where the whole-number part of {@code formatted} ends: the first
	 * character that isn't part of the integer magnitude reading (i.e. not a
	 * digit, a leading sign, or a grouping comma). Everything from there on is
	 * a decimal point and fractional digits (dimmed) and/or a trailing
	 * {@code k}/{@code m}/{@code b} (or {@code %}/{@code s}/…) suffix (bright,
	 * same colour as the integer). Equals the string length for a bare
	 * integer such as {@code "1,234"} or a round abbreviation such as
	 * {@code "100k"} (nothing to dim).
	 */
	private static int integerEnd(String formatted)
	{
		int i = 0;
		while (i < formatted.length())
		{
			char c = formatted.charAt(i);
			if ((c >= '0' && c <= '9') || c == '-' || c == ',')
			{
				i++;
			}
			else
			{
				break;
			}
		}
		return i;
	}

	/** {@link #fragment(String, String, String)} using the default white-integer / grey-decimal pairing. */
	public static String fragment(String formatted)
	{
		return fragment(formatted, WHITE, GREY);
	}

	/**
	 * {@link #fragment(String, String, String)} with the integer (and suffix)
	 * in {@code color} and the decimal in {@link #dim(String) dim(color)} —
	 * for a context colour that isn't one of the standing {@link #GREEN}/
	 * {@link #RED} pairings (e.g. a highlighted/best row rendered in the
	 * panel's brand accent colour, which must still dim its decimals rather
	 * than hard-coding white).
	 */
	public static String fragment(String formatted, String color)
	{
		return fragment(formatted, color, dim(color));
	}

	/** {@link #fragment(String, String, String)} using the green-integer / dull-green-decimal pairing (positive delta). */
	public static String greenFragment(String formatted)
	{
		return fragment(formatted, GREEN, GREEN_DIM);
	}

	/** {@link #fragment(String, String, String)} using the red-integer / dull-red-decimal pairing (negative delta). */
	public static String redFragment(String formatted)
	{
		return fragment(formatted, RED, RED_DIM);
	}

	/** {@link #fragment(String, String, String)} wrapped in standalone {@code <html>} tags. */
	public static String html(String formatted, String intColor, String decimalColor)
	{
		return "<html>" + fragment(formatted, intColor, decimalColor) + "</html>";
	}

	/** {@link #fragment(String)} wrapped in standalone {@code <html>} tags. */
	public static String html(String formatted)
	{
		return "<html>" + fragment(formatted) + "</html>";
	}
}
