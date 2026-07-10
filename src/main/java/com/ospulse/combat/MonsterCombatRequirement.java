package com.ospulse.combat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** A monster's combat requirement: a weapon/ammo/style damage gate, or a finisher item. Pure, no RuneLite deps. */
public final class MonsterCombatRequirement
{
    public enum Type { WEAPON_GATE, FINISHER }

    private final Type type;
    private final Set<Integer> allowedItemIds;
    private final Set<Integer> allowedAmmoIds;
    private final Set<CombatStyle> allowedStyles;
    private final Set<Integer> finisherItemIds;
    private final String note;

    private MonsterCombatRequirement(Type type, Set<Integer> allowedItemIds, Set<Integer> allowedAmmoIds,
                                     Set<CombatStyle> allowedStyles, Set<Integer> finisherItemIds, String note)
    {
        this.type = type;
        this.allowedItemIds = allowedItemIds == null ? Collections.emptySet() : new HashSet<>(allowedItemIds);
        this.allowedAmmoIds = allowedAmmoIds == null ? Collections.emptySet() : new HashSet<>(allowedAmmoIds);
        this.allowedStyles = (allowedStyles == null || allowedStyles.isEmpty())
            ? EnumSet.noneOf(CombatStyle.class) : EnumSet.copyOf(allowedStyles);
        this.finisherItemIds = finisherItemIds == null ? Collections.emptySet() : new HashSet<>(finisherItemIds);
        this.note = note == null ? "" : note;
    }

    public static MonsterCombatRequirement weaponGate(Set<Integer> allowedItemIds, Set<Integer> allowedAmmoIds,
                                                      Set<CombatStyle> allowedStyles, String note)
    {
        return new MonsterCombatRequirement(Type.WEAPON_GATE, allowedItemIds, allowedAmmoIds,
            allowedStyles, Collections.emptySet(), note);
    }

    public static MonsterCombatRequirement finisher(Set<Integer> finisherItemIds, String note)
    {
        return new MonsterCombatRequirement(Type.FINISHER, Collections.emptySet(), Collections.emptySet(),
            EnumSet.noneOf(CombatStyle.class), finisherItemIds, note);
    }

    public Type type() { return type; }
    public String note() { return note; }
    public Set<Integer> finisherItemIds() { return Collections.unmodifiableSet(finisherItemIds); }

    /** Full-attack truth: can this weapon+style+ammo deal damage to the monster? */
    public boolean permits(int weaponId, CombatStyle style, int ammoId)
    {
        if (type != Type.WEAPON_GATE) { return true; }
        if (allowedStyles.contains(style)) { return true; }
        if (allowedItemIds.contains(weaponId)) { return true; }
        if (style == CombatStyle.RANGED && !allowedAmmoIds.isEmpty() && allowedAmmoIds.contains(ammoId)) { return true; }
        return false;
    }

    /**
     * Optimiser weapon-slot gate (chosen style fixed; ammo slot enforced
     * separately). Back-compat overload — assumes the weapon fires worn ammo.
     */
    public boolean permitsWeapon(int weaponId, CombatStyle style)
    {
        return permitsWeapon(weaponId, style, true);
    }

    /**
     * Optimiser weapon-slot gate. {@code weaponUsesWornAmmo} must be {@code false}
     * for a self-supplying ranged weapon (blowpipe, chinchompa, crystal bow,
     * atlatl): such a weapon fires its own ammunition, so it can never satisfy a
     * broad-ammo gate via the worn ammo slot and is only permitted when listed
     * explicitly in {@code allowedItemIds}. Without this a blowpipe slipped
     * through Kurask's gate paired with (unfired) broad bolts.
     */
    public boolean permitsWeapon(int weaponId, CombatStyle style, boolean weaponUsesWornAmmo)
    {
        if (type != Type.WEAPON_GATE) { return true; }
        if (allowedStyles.contains(style)) { return true; }
        if (allowedItemIds.contains(weaponId)) { return true; }
        return style == CombatStyle.RANGED && !allowedAmmoIds.isEmpty() && weaponUsesWornAmmo;
    }

    /** Optimiser ammo-slot gate (only restricts ranged). */
    public boolean permitsAmmo(int ammoId, CombatStyle style)
    {
        if (type != Type.WEAPON_GATE) { return true; }
        if (style != CombatStyle.RANGED) { return true; }
        if (allowedAmmoIds.isEmpty()) { return true; }
        return allowedAmmoIds.contains(ammoId);
    }
}
