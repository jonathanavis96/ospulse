package com.ospulse.ui.sections.gear;

import javax.swing.JToggleButton;
import java.util.Locale;

/**
 * Parses the optimiser's budget/expensive-threshold number fields (issue #11
 * batch extraction — pure text/number logic, no {@code GearSection} state).
 */
public final class BudgetAmount
{
	private BudgetAmount()
	{
	}

	/**
	 * Parses a budget string with an optional trailing k/m/b unit (design
	 * spec: "numeric + K/M unit toggle" — a suffix is a lighter-weight
	 * equivalent for a text field than a separate toggle button and reads
	 * naturally, matching how players already type prices in-game, e.g. GE
	 * search). Blank/unparseable input is treated as 0 (owned-only search)
	 * rather than rejected, since a budget field is not a validated form
	 * control here.
	 */
	public static long parse(String text)
	{
		if (text == null)
		{
			return 0L;
		}
		String trimmed = text.trim().toLowerCase(Locale.ROOT).replace(",", "");
		if (trimmed.isEmpty())
		{
			return 0L;
		}
		double multiplier = 1.0;
		if (trimmed.endsWith("b"))
		{
			multiplier = 1_000_000_000.0;
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		else if (trimmed.endsWith("m"))
		{
			multiplier = 1_000_000.0;
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		else if (trimmed.endsWith("k"))
		{
			multiplier = 1_000.0;
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		try
		{
			double value = Double.parseDouble(trimmed.trim());
			return value <= 0 ? 0L : Math.round(value * multiplier);
		}
		catch (NumberFormatException e)
		{
			return 0L;
		}
	}

	/**
	 * Combines a plain numeric field's text with a K/M segmented toggle's
	 * current selection into the same "10m"/"500k" shape {@link #parse} has
	 * always accepted, then parses it — so the budget/expensive-threshold
	 * number fields feed {@code GearOptimizer.Request} exactly as the old
	 * single free-text budget field did. Neither toggle selected is treated
	 * as a plain number (no unit multiplier).
	 */
	public static long parseUnitAmount(String numberText, JToggleButton kToggle, JToggleButton mToggle)
	{
		String suffix = mToggle.isSelected() ? "m" : kToggle.isSelected() ? "k" : "";
		return parse((numberText == null ? "" : numberText.trim()) + suffix);
	}
}
