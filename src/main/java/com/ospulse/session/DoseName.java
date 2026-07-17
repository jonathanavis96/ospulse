package com.ospulse.session;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed "<base> (<dose>)" shape of a dose-suffixed item name (potions,
 * brews, restores — anything named "...(1)" through "...(4)"), used only
 * to pair a dose-down within the same update (see {@link SessionEngine#update}).
 * Not related to {@link SupplyClassifier}'s broader consumable matching.
 */
final class DoseName
{
	private static final Pattern PATTERN =
		Pattern.compile("^(?<base>.+?)\\s*\\((?<dose>[1-4])\\)$", Pattern.CASE_INSENSITIVE);

	final String base;
	final int dose;

	private DoseName(String base, int dose)
	{
		this.base = base;
		this.dose = dose;
	}

	/** @return the parsed base/dose, or {@code null} if not dose-suffixed. */
	static DoseName parse(String name)
	{
		if (name == null)
		{
			return null;
		}
		Matcher m = PATTERN.matcher(name.trim());
		if (!m.matches())
		{
			return null;
		}
		return new DoseName(m.group("base").trim().toLowerCase(Locale.ROOT),
			Integer.parseInt(m.group("dose")));
	}
}
