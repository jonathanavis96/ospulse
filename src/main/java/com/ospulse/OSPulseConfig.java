package com.ospulse;

import com.ospulse.combat.BlowpipeDart;
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
		name = "Price trends (optional)",
		description = "Optional, off-by-default price trend badges on Top Holdings, sourced from "
			+ "the OSRS Wiki prices API.",
		position = 1,
		closedByDefault = true
	)
	String priceTrendSection = "priceTrend";

	@ConfigSection(
		name = "Panel sections",
		description = "Choose which sections appear in the OSPulse side panel.",
		position = 2,
		closedByDefault = true
	)
	String panelSectionsSection = "panelSections";

	@ConfigSection(
		name = "Gear & optimiser",
		description = "Options affecting the DPS calculator's gear readout and the gear optimiser.",
		position = 3,
		closedByDefault = true
	)
	String gearSection = "gear";

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

	// ------------------------------------------------------------ Panel sections

	@ConfigItem(
		keyName = "showSessionSection",
		name = "Show Session section",
		description = "Show the Session section in the OSPulse panel.",
		position = 0,
		section = panelSectionsSection
	)
	default boolean showSessionSection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLootSection",
		name = "Show Loot section",
		description = "Show the Loot section in the OSPulse panel.",
		position = 1,
		section = panelSectionsSection
	)
	default boolean showLootSection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showXpSection",
		name = "Show XP section",
		description = "Show the XP section in the OSPulse panel.",
		position = 2,
		section = panelSectionsSection
	)
	default boolean showXpSection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGearSection",
		name = "Show Gear section",
		description = "Show the Gear section in the OSPulse panel.",
		position = 3,
		section = panelSectionsSection
	)
	default boolean showGearSection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGeSection",
		name = "Show Grand Exchange section",
		description = "Show the Grand Exchange section in the OSPulse panel.",
		position = 4,
		section = panelSectionsSection
	)
	default boolean showGeSection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWealthSection",
		name = "Show Wealth section",
		description = "Show the Wealth section in the OSPulse panel.",
		position = 5,
		section = panelSectionsSection
	)
	default boolean showWealthSection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHoldingsSection",
		name = "Show Top Holdings section",
		description = "Show the Top Holdings section in the OSPulse panel.",
		position = 6,
		section = panelSectionsSection
	)
	default boolean showHoldingsSection()
	{
		return true;
	}

	// ------------------------------------------------------------------ Gear

	@ConfigItem(
		keyName = "blowpipeDart",
		name = "Blowpipe dart",
		description = "Which dart the calculator assumes is loaded in a blowpipe (its ranged "
			+ "strength is added; a blowpipe ignores worn ammo).",
		position = 0,
		section = gearSection
	)
	default BlowpipeDart blowpipeDart()
	{
		return BlowpipeDart.DRAGON;
	}
}
