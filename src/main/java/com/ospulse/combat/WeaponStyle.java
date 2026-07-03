package com.ospulse.combat;

import java.util.Objects;

/**
 * One selectable attack style from a weapon's combat-options tab: its in-game
 * display name (e.g. "Lunge", "Rapid"), the damage {@link CombatStyle} it uses
 * (which attack-bonus and monster-defence columns apply) and the {@link Stance}
 * it sets (which drives the +3/+1/0 style bonus and, for Rapid, the -1 tick).
 *
 * <p>These are the real per-weapon-type styles, so ranking them by DPS answers
 * "which attack style should I use against this monster" exactly as the in-game
 * combat tab presents the choice. Built by {@link WeaponStyles#forCategory}.
 */
public final class WeaponStyle {
    private final String name;
    private final CombatStyle type;
    private final Stance stance;

    public WeaponStyle(String name, CombatStyle type, Stance stance) {
        this.name = name;
        this.type = type;
        this.stance = stance;
    }

    public String name() {
        return name;
    }

    public CombatStyle type() {
        return type;
    }

    public Stance stance() {
        return stance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WeaponStyle)) {
            return false;
        }
        WeaponStyle that = (WeaponStyle) o;
        // Deliberately identity-by-(type,stance): two styles that read the same
        // gear/defence columns and set the same stance compute identical DPS, so
        // the ranked picker treats them as one (dedups e.g. a bludgeon's three
        // aggressive-crush styles). Name is display-only and excluded.
        return type == that.type && stance == that.stance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, stance);
    }

    @Override
    public String toString() {
        return "WeaponStyle{" + name + ", " + type + ", " + stance + '}';
    }
}
