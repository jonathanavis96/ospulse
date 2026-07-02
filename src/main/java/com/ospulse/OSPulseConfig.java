package com.ospulse;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(OSPulseConfig.GROUP)
public interface OSPulseConfig extends Config
{
	String GROUP = "ospulse";

	@ConfigSection(
		name = "Session",
		description = "Session profit tracking options.",
		position = 0
	)
	String sessionSection = "session";

	@ConfigSection(
		name = "Sync (optional)",
		description = "Optional, off-by-default HTTP sync to a companion dashboard you host.",
		position = 1,
		closedByDefault = true
	)
	String syncSection = "sync";

	@ConfigSection(
		name = "Price trends (optional)",
		description = "Optional, off-by-default price trend badges on Top Holdings, sourced from "
			+ "the OSRS Wiki prices API.",
		position = 2,
		closedByDefault = true
	)
	String priceTrendSection = "priceTrend";

	// ---------------------------------------------------------------- Session

	@ConfigItem(
		keyName = "minLootValue",
		name = "Min loot value",
		description = "Only show loot-feed entries worth at least this many gp (per stack). "
			+ "Set to 0 to show everything.",
		position = 0,
		section = sessionSection
	)
	@Range(min = 0)
	default int minLootValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "includePouches",
		name = "Include rune pouch / looting bag",
		description = "Count the value of items stored in the rune pouch and looting bag "
			+ "as part of your tracked session wealth.",
		position = 1,
		section = sessionSection
	)
	default boolean includePouches()
	{
		return true;
	}

	// ------------------------------------------------------------------- Sync

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Enable sync",
		description = "OFF by default. When enabled, your session and wealth summary is sent "
			+ "to the URL below using the token below. Nothing is sent unless you turn this on "
			+ "and provide a URL. Normally set for you automatically by the pairing code below.",
		position = 0,
		section = syncSection
	)
	default boolean syncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pairingServerUrl",
		name = "Dashboard URL",
		description = "The base URL of your dashboard, e.g. http://100.67.160.92:8701. "
			+ "Leave blank if you're using the manual Sync URL / Sync token fields below instead.",
		position = 1,
		section = syncSection
	)
	default String pairingServerUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "pairingCode",
		name = "Pairing code",
		description = "Enter the 6-digit code shown on your dashboard's \"Connect RuneLite\" "
			+ "screen. It's exchanged for a sync token automatically, then cleared.",
		position = 2,
		section = syncSection
	)
	default String pairingCode()
	{
		return "";
	}

	@ConfigItem(
		keyName = "syncUrl",
		name = "Sync URL (advanced)",
		description = "Full HTTPS URL of your companion dashboard's ingest endpoint. Filled in "
			+ "automatically once you redeem a pairing code above; only edit directly if you're "
			+ "not using pairing.",
		position = 3,
		section = syncSection
	)
	default String syncUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "syncToken",
		name = "Sync token (advanced)",
		description = "Bearer token sent with each sync request to authenticate you to your "
			+ "own dashboard. Filled in automatically once you redeem a pairing code above; only "
			+ "edit directly if you're not using pairing.",
		secret = true,
		position = 4,
		section = syncSection
	)
	default String syncToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "syncIntervalSeconds",
		name = "Sync interval (seconds)",
		description = "Minimum seconds between sync uploads while enabled.",
		position = 5,
		section = syncSection
	)
	@Range(min = 10, max = 3600)
	default int syncIntervalSeconds()
	{
		return 60;
	}

	// ---------------------------------------------------------- Price trends

	@ConfigItem(
		keyName = "priceTrendEnabled",
		name = "Enable price trends",
		description = "OFF by default. When enabled, OSPulse fetches item price history from "
			+ "prices.runescape.wiki to show whether your holdings are trending up or down.",
		position = 0,
		section = priceTrendSection
	)
	default boolean priceTrendEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "priceTrendWindow",
		name = "Trend window",
		description = "How far back to measure the price trend shown on Top Holdings.",
		position = 1,
		section = priceTrendSection
	)
	default PriceTrendWindow priceTrendWindow()
	{
		return PriceTrendWindow.WEEK;
	}

	@ConfigItem(
		keyName = "holdingsPageSize",
		name = "Holdings page size",
		description = "How many top holdings to show at a time before needing \"Show more\".",
		position = 2,
		section = priceTrendSection
	)
	@Range(min = 1)
	default int holdingsPageSize()
	{
		return 5;
	}
}
