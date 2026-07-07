package com.ospulse.combat;

import com.ospulse.OSPulseConfig;
import net.runelite.client.config.ConfigManager;

/**
 * The dart type assumed loaded inside a blowpipe (Toxic/Blazing/Drygore/
 * Camphor/Ironwood/Rosewood blowpipe — every 2H ranged "blowpipe" weapon
 * variant). A blowpipe loads darts <i>internally</i>: the loaded dart is not
 * held in the equipment ammo slot, and the blowpipe ignores whatever item is
 * worn there. Each dart only contributes ranged strength (its ranged-attack
 * bonus is 0) — see {@link #rangedStrength()}. Values verified against the
 * bundled equipment_stats data and the OSRS Wiki (dart ranged-strength table)
 * 2026-07-04. Picked via the gear panel's blowpipe right-click "Set darts"
 * submenu, persisted under the {@value #CONFIG_KEY} config key.
 */
public enum BlowpipeDart
{
	BRONZE("Bronze", 1),
	IRON("Iron", 2),
	STEEL("Steel", 3),
	BLACK("Black", 6),
	MITHRIL("Mithril", 9),
	ADAMANT("Adamant", 17),
	RUNE("Rune", 26),
	AMETHYST("Amethyst", 28),
	DRAGON("Dragon", 35);

	/** Config key the picked dart persists under (raw {@link ConfigManager} storage — no {@code @ConfigItem}). */
	public static final String CONFIG_KEY = "blowpipeDart";

	private final String displayName;
	private final int rangedStrength;

	BlowpipeDart(String displayName, int rangedStrength)
	{
		this.displayName = displayName;
		this.rangedStrength = rangedStrength;
	}

	/** The dart's ranged-strength bonus (its ranged-attack bonus is always 0). */
	public int rangedStrength()
	{
		return rangedStrength;
	}

	/**
	 * The dart the user picked via the gear panel's "Set darts" submenu,
	 * read from raw {@link ConfigManager} storage (the {@value #CONFIG_KEY}
	 * key — there is no {@code @ConfigItem}, dart selection lives in the panel
	 * UI, not the settings screen). Falls back to {@link #DRAGON} when there is
	 * no config manager (headless tests), no stored value, or a stale/unknown
	 * enum name (e.g. an older plugin version).
	 */
	public static BlowpipeDart fromConfig(ConfigManager configManager)
	{
		if (configManager == null)
		{
			return DRAGON;
		}
		String raw = configManager.getConfiguration(OSPulseConfig.GROUP, CONFIG_KEY);
		if (raw == null || raw.isEmpty())
		{
			return DRAGON;
		}
		try
		{
			return BlowpipeDart.valueOf(raw);
		}
		catch (IllegalArgumentException e)
		{
			return DRAGON;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
