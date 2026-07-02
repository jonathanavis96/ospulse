package com.ospulse.combat;

/**
 * The attack-style stance selected in the combat options tab. This drives the
 * small flat "style bonus" additions documented on the OSRS Wiki DPS pages
 * (added before the final +8/+9 effective-level offset, see
 * {@link CombatMath}).
 * <p>
 * Melee: Accurate -&gt; +3 attack; Aggressive -&gt; +3 strength; Controlled -&gt;
 * +1 to both attack and strength (and defence, not modelled here); Defensive
 * -&gt; +0 to both (only affects the player's own effective defence, out of
 * scope for a vs-NPC DPS calculator).
 * <p>
 * Ranged: only the Accurate style carries a numeric bonus (+3, applied to
 * both effective ranged attack AND effective ranged strength identically);
 * Rapid/Longrange/other styles are all +0 (Rapid instead reduces the
 * weapon's attack speed by 1 tick - the caller is expected to pass an
 * already-adjusted {@code weaponSpeedTicks} for that case).
 * <p>
 * Magic: the +3/+1 style bonus documented on the wiki only applies when
 * using a powered staff's Accurate/Longrange option; ordinary spell-casting
 * has no style choice (bonus is always 0).
 */
public enum Stance {
    ACCURATE,
    AGGRESSIVE,
    DEFENSIVE,
    CONTROLLED,
    RAPID,
    LONGRANGE,
    STANDARD
}
