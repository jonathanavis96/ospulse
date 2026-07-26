package com.ospulse.combat;

/**
 * The value-axis a curated special attack ({@link SpecWeapon}) primarily
 * competes on (design spec §8, director decision — not redesigned here). A
 * damage-only ranking would surface Dragon claws/Toxic blowpipe forever and
 * never suggest a Dragon warhammer at a high-defence boss, which reads as
 * plainly wrong: a raw damage number and "−30% Defence" are incommensurable,
 * so {@link SpecWeaponSelector} only ever compares specs WITHIN one role,
 * never across roles — see that class's selection rule.
 */
public enum SpecRole {
    /**
     * Wins by raw expected damage per use, normalised by spec-bar cost —
     * Dragon claws, Dragon dagger, Burning claws, Toxic blowpipe, Voidwaker,
     * Magic shortbow (plain and imbued).
     */
    DAMAGE,
    /**
     * Wins by lowering the TARGET's own stats for the rest of the fight,
     * which only pays off against a target tough enough that the drain
     * matters — Dragon warhammer, Bandos godsword, Elder maul, Arclight,
     * Emberlight. See {@link SpecWeaponSelector#HIGH_DEFENCE_THRESHOLD}.
     */
    DEFENCE_DRAIN,
    /** Restores the PLAYER's own resources rather than damaging the target — Saradomin godsword. */
    HEAL,
    /**
     * A non-damage utility effect (guaranteed proc, bind, PvP-only drain) —
     * Zaryte crossbow, Abyssal tentacle, Abyssal whip. Never surfaced as the
     * top PvM recommendation; see {@link SpecWeaponSelector}'s selection
     * rule 4.
     */
    UTILITY
}
