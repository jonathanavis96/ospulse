package com.ospulse.combat;

/**
 * Monster attributes/weaknesses relevant to Tier-A gear effects (Salve vs
 * Undead, on-task Slayer bonuses gate on the player's task rather than an
 * attribute, dragon-hunter gear is Tier-B). Extend this set as later tiers
 * are implemented (DRAGON, DEMON, KALPHITE, LEAFY, VAMPYRE, ... for Tier
 * B/C weapon-specific bonuses); unknown attributes simply mean the
 * corresponding Tier-A/B effect never triggers.
 */
public enum MonsterAttribute {
    UNDEAD,
    DRAGON,
    DEMON,
    KALPHITE,
    VAMPYRE,
    LEAFY,
    SHADE
}
