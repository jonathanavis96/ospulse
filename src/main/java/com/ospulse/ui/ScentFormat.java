package com.ospulse.ui;

/**
 * Pure, side-effect-free builder for the "scent" number styling used across
 * the panel: an unbolded integer part in a context colour, with the decimal
 * point, fractional digits and any suffix rendered smaller and in a duller
 * colour so they visually recede while the integer magnitude reads at a
 * glance (the "1.98 read as 198" misread this treatment exists to prevent).
 *
 * <p>Shared by {@link GpFormat} (gp values in the wealth/session panels) and
 * {@code GearSection}'s DPS/Accuracy/Avg-hit/TTK/Overkill readouts so every
 * "scent" number in the plugin gets the exact same treatment from one place.
 * Kept dependency-free (no Swing/RuneLite imports) so it's trivially
 * unit-testable and easy to split into its own module later.
 *
 * <p>The smaller decimal size is done with the HTML legacy {@code size='2'}
 * attribute rather than a proportional scale: Swing's HTML renderer maps
 * {@code size} levels 1-7 to a fixed absolute point-size table, not to a
 * fraction of the surrounding label's actual font size. Verified against the
 * real, {@code GraphicsEnvironment}-registered "RuneScape Small" font (16pt)
 * used throughout the panel: an unwrapped/{@code size='3'} run stays at the
 * label's true configured size, while a {@code size='2'} run resolves to a
 * fixed 10pt of the SAME font family (no fallback to a mismatched
 * proportional font — RuneLite's {@code FontManager} registers these fonts
 * with the JRE, so family-by-name lookup succeeds) — a real, if not
 * precisely "half", size reduction.
 */
public final class ScentFormat
{
	private ScentFormat()
	{
	}

	/** Default context colour for the integer part — plain white. */
	public static final String WHITE = "#FFFFFF";
	/** Default de-emphasised colour for the fractional/suffix part — dim grey. */
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
	 * each channel scaled by the same ~55% ratio the existing grey decimal
	 * colour already sits at relative to white ({@code 0x8C / 0xFF ≈
	 * 0.549}), so every "scent" colour pairing dims its decimals by the same
	 * amount regardless of hue: {@code round(channel * 0.55)}.
	 */
	public static final String GREEN_DIM = "#1E8427";
	public static final String RED_DIM = "#7F1111";

	/**
	 * Splits {@code formatted} (e.g. {@code "1.5m"}, {@code "1,234"}, a
	 * signed value, or a plain integer with no fractional part) on its first
	 * {@code '.'} and renders the integer part unbolded in {@code intColor}
	 * and the fractional digits plus any trailing suffix (k/m/b, %, s, ...)
	 * at half-size in {@code decimalColor}. Returns a bare fragment (no
	 * surrounding {@code <html>} tags) so callers can compose it with other
	 * fragments (e.g. a "before -&gt; after" comparison in one label).
	 */
	public static String fragment(String formatted, String intColor, String decimalColor)
	{
		int dot = formatted.indexOf('.');
		if (dot < 0)
		{
			return "<font color='" + intColor + "'>" + formatted + "</font>";
		}
		return "<font color='" + intColor + "'>" + formatted.substring(0, dot) + "</font>"
			+ "<font size='2' color='" + decimalColor + "'>" + formatted.substring(dot) + "</font>";
	}

	/** {@link #fragment(String, String, String)} using the default white-integer / grey-decimal pairing. */
	public static String fragment(String formatted)
	{
		return fragment(formatted, WHITE, GREY);
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
