package com.ospulse.combat;

/**
 * The dart type assumed loaded inside a blowpipe (Toxic/Blazing/Drygore/
 * Camphor/Ironwood/Rosewood blowpipe — every 2H ranged "blowpipe" weapon
 * variant). A blowpipe loads darts <i>internally</i>: the loaded dart is not
 * held in the equipment ammo slot, and the blowpipe ignores whatever item is
 * worn there. Each dart only contributes ranged strength (its ranged-attack
 * bonus is 0) — see {@link #rangedStrength()}. Values verified against the
 * bundled equipment_stats data and the OSRS Wiki (dart ranged-strength table)
 * 2026-07-04. Selectable via {@code OSPulseConfig#blowpipeDart()}.
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

	@Override
	public String toString()
	{
		return displayName;
	}
}
