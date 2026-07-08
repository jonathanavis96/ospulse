package com.ospulse.integration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the fish barrel's "Check" interface text (interface 193, component 2)
 * into a per-species item id -&gt; quantity breakdown.
 *
 * <p>Adapted from the community "Fish Barrel" plugin by molo-pl
 * (BSD-2-Clause, https://github.com/molo-pl/runelite-plugins, branch
 * {@code fish-barrel}) — see {@code NOTICE}. That plugin's parser only sums a
 * total fish count; this version additionally resolves each entry's species
 * name against {@link FishBarrelTracker}'s catch-message fish map so
 * {@link FishBarrelTracker} can restore an exact per-item breakdown (needed
 * to price the barrel's contents), not just a headline total.
 *
 * <p>Pure/stateless: safe to unit test without a game client.
 */
final class FishBarrelWidgetParser
{
	private static final String EMPTY_MESSAGE = "The barrel is empty.";
	private static final String MESSAGE_ENTRY_REGEX = "([0-9]+) x ([a-zA-Z ]+),? ?";
	private static final Pattern MESSAGE_ENTRY_PATTERN = Pattern.compile(MESSAGE_ENTRY_REGEX);
	private static final Pattern FULL_MESSAGE_PATTERN =
		Pattern.compile("^The barrel contains: (" + MESSAGE_ENTRY_REGEX + ")+$");

	private FishBarrelWidgetParser()
	{
	}

	/**
	 * @param message        the widget's raw text (may contain {@code <br>} line breaks)
	 * @param fishTypesByName catch-message fish name -&gt; item id map (singular form,
	 *                        e.g. "shark", "anglerfish") used to resolve each entry's
	 *                        plural display name (e.g. "Raw sharks") back to an item id
	 * @return item id -&gt; quantity, or {@code null} if the message couldn't be parsed
	 *         (unrecognised format, or an entry names a fish not in {@code fishTypesByName})
	 */
	static Map<Integer, Integer> parse(String message, Map<String, Integer> fishTypesByName)
	{
		if (message == null || message.isBlank())
		{
			return null;
		}
		String normalized = message.replace("<br>", " ").trim();

		if (EMPTY_MESSAGE.equals(normalized))
		{
			return Map.of();
		}

		if (!FULL_MESSAGE_PATTERN.matcher(normalized).matches())
		{
			return null;
		}

		Map<Integer, Integer> result = new LinkedHashMap<>();
		Matcher matcher = MESSAGE_ENTRY_PATTERN.matcher(normalized);
		while (matcher.find())
		{
			int qty;
			try
			{
				qty = Integer.parseInt(matcher.group(1));
			}
			catch (NumberFormatException e)
			{
				return null;
			}

			Integer itemId = resolveFishId(matcher.group(2), fishTypesByName);
			if (itemId == null)
			{
				// Unrecognised species text — don't silently drop it into an
				// "unknown fish" bucket; fail the whole parse so the caller keeps
				// its previous (still-attributed) state instead of a partial one.
				return null;
			}
			result.merge(itemId, qty, Integer::sum);
		}

		return result;
	}

	/**
	 * Resolves a widget entry's display name (e.g. "Raw sharks", "Raw shrimps")
	 * to an item id by matching it against the singular catch-message names in
	 * {@code fishTypesByName}, tolerating the "Raw " prefix and a trailing "s".
	 */
	private static Integer resolveFishId(String displayName, Map<String, Integer> fishTypesByName)
	{
		String cleaned = displayName.trim().toLowerCase(Locale.ROOT);
		if (cleaned.startsWith("raw "))
		{
			cleaned = cleaned.substring(4);
		}

		Integer exact = lookupCaseInsensitive(fishTypesByName, cleaned);
		if (exact != null)
		{
			return exact;
		}

		// Widget entries are pluralised (e.g. "sharks", "anglerfish" is already
		// invariant); try stripping a trailing 's' against the singular map.
		if (cleaned.endsWith("s"))
		{
			Integer singular = lookupCaseInsensitive(fishTypesByName, cleaned.substring(0, cleaned.length() - 1));
			if (singular != null)
			{
				return singular;
			}
		}

		return null;
	}

	private static Integer lookupCaseInsensitive(Map<String, Integer> map, String key)
	{
		Integer direct = map.get(key);
		if (direct != null)
		{
			return direct;
		}
		for (Map.Entry<String, Integer> entry : map.entrySet())
		{
			if (entry.getKey().toLowerCase(Locale.ROOT).equals(key))
			{
				return entry.getValue();
			}
		}
		return null;
	}
}
