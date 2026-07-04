package com.ospulse.combat;

/**
 * A charged elemental tome worn in the shield slot (Tome of fire/water/earth).
 * Each grants a flat +10% magic damage vs NPCs to its element's standard-
 * spellbook combat spells — weirdgloop's {@code MAX_HIT_TOME} step
 * ({@code trackFactor [11,10]}), applied as the FINAL magic-damage modifier,
 * after the elemental-weakness bonus (see {@link DpsCalculator}). Only the
 * CHARGED item ids map to a tome here; the empty variants resolve to
 * {@link #NONE} (no bonus), matching the in-game "must be charged" rule.
 *
 * <p>The PvP-only larger multipliers (fire +50%, water +20%) are out of scope:
 * this calculator is PvM. There is no tome of air/wind, so {@link Spell.Element#WIND}
 * never maps to a tome.
 */
public enum Tome
{
	NONE(null),
	FIRE(Spell.Element.FIRE),
	WATER(Spell.Element.WATER),
	EARTH(Spell.Element.EARTH);

	private final Spell.Element element;

	Tome(Spell.Element element)
	{
		this.element = element;
	}

	/** The spell {@link Spell.Element} this tome boosts, or {@code null} for {@link #NONE}. */
	public Spell.Element element()
	{
		return element;
	}

	/** True when this tome boosts a spell cast with the given element (never for {@link #NONE}/{@code null}). */
	public boolean boosts(Spell.Element spellElement)
	{
		return element != null && element == spellElement;
	}
}
