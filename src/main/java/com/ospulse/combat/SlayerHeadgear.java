package com.ospulse.combat;

/**
 * Black mask / Slayer helmet family worn by the player. Only the imbued
 * variants ("(i)") extend the on-task bonus to ranged and magic; the plain
 * variants only affect melee.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Maximum_melee_hit">Maximum melee hit</a> (melee: plain OR imbued, x7/6)
 * @see <a href="https://oldschool.runescape.wiki/w/Maximum_ranged_hit">Maximum ranged hit</a> (ranged: imbued only, x1.15)
 * @see <a href="https://oldschool.runescape.wiki/w/Maximum_magic_hit">Maximum magic hit</a> (magic: imbued only, +15% pre-hit-roll)
 */
public enum SlayerHeadgear {
    NONE,
    STANDARD,
    IMBUED;

    public boolean wornAtAll() {
        return this != NONE;
    }

    public boolean isImbued() {
        return this == IMBUED;
    }
}
