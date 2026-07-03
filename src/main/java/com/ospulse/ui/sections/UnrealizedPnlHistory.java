package com.ospulse.ui.sections;

import java.util.OptionalLong;

/**
 * Pure helper for the "since last login" Unrealized P/L delta shown next to
 * {@link HoldingsSection}'s Unrealized P/L stat row (feature 7).
 *
 * <p>{@link HoldingsSection} persists the current Unrealized P/L to the
 * RuneLite config on shutdown / periodically, and on the next login reads
 * that stored value back as {@code previous}. This class just computes the
 * delta and formats it; it knows nothing about {@code ConfigManager} so it
 * can be unit-tested without any RuneLite dependency.
 */
final class UnrealizedPnlHistory
{
	private UnrealizedPnlHistory()
	{
	}

	/**
	 * The change in Unrealized P/L since the stored previous-login value, or
	 * empty when there is no prior value (first-ever login, or the stored
	 * config value was blank/unparsable).
	 */
	static OptionalLong delta(long current, OptionalLong previous)
	{
		if (previous.isEmpty())
		{
			return OptionalLong.empty();
		}
		return OptionalLong.of(current - previous.getAsLong());
	}

	/**
	 * Parses the raw config string written by a previous session back into a
	 * long, treating {@code null}/blank/unparsable input as "no prior value"
	 * (first-ever login) rather than throwing.
	 */
	static OptionalLong parseStored(String raw)
	{
		if (raw == null || raw.isBlank())
		{
			return OptionalLong.empty();
		}
		try
		{
			return OptionalLong.of(Long.parseLong(raw.trim()));
		}
		catch (NumberFormatException e)
		{
			return OptionalLong.empty();
		}
	}

	/** {@code "since last login: +X"} / {@code "-Y"} / {@code "—"} when unknown. */
	static String label(OptionalLong delta)
	{
		if (delta.isEmpty())
		{
			return "since last login: —"; // em dash
		}
		long d = delta.getAsLong();
		String sign = d > 0 ? "+" : "";
		return "since last login: " + sign + com.ospulse.ui.GpFormat.format(d);
	}
}
