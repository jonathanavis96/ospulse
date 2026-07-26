package com.ospulse.ui.sections.gear;

import com.ospulse.combat.CombatStyle;

/**
 * The display label for a {@link CombatStyle} damage type (issue #11 batch
 * extraction — a pure lookup table, no {@code GearSection} state).
 */
public final class CombatStyleLabel
{
	private CombatStyleLabel()
	{
	}

	public static String of(CombatStyle type)
	{
		switch (type)
		{
			case STAB:
				return "Stab";
			case SLASH:
				return "Slash";
			case CRUSH:
				return "Crush";
			case RANGED:
				return "Ranged";
			case MAGIC:
				return "Magic";
			default:
				return type.name();
		}
	}
}
