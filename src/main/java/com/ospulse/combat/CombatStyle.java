package com.ospulse.combat;

/**
 * The five attack angles a player can use against a target.
 * <p>
 * STAB / SLASH / CRUSH select which pair of (attack-bonus, defence-bonus)
 * columns are read from {@link EquipmentStats} and {@link Monster} for
 * melee; RANGED and MAGIC each have their own single pair.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Damage_per_second/Melee">OSRS Wiki: DPS/Melee</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Damage_per_second/Ranged">OSRS Wiki: DPS/Ranged</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Damage_per_second/Magic">OSRS Wiki: DPS/Magic</a>
 */
public enum CombatStyle {
    STAB,
    SLASH,
    CRUSH,
    RANGED,
    MAGIC;

    public boolean isMelee() {
        return this == STAB || this == SLASH || this == CRUSH;
    }
}
