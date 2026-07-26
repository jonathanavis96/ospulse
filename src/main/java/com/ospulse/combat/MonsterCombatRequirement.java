package com.ospulse.combat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A monster's combat requirement: a weapon/ammo/style damage gate, a finisher
 * item, or a damage-magnitude effect (penalty/cap) a hard style-gate cannot
 * express. Pure, no RuneLite deps.
 */
public final class MonsterCombatRequirement
{
    public enum Type { WEAPON_GATE, FINISHER, DAMAGE_PENALTY, DAMAGE_CAP }

    private final Type type;
    private final Set<Integer> allowedItemIds;
    private final Set<Integer> allowedAmmoIds;
    private final Set<CombatStyle> allowedStyles;
    private final Set<Integer> finisherItemIds;
    private final String note;
    private final double damageMultiplier;
    private final Set<CombatStyle> penalisedStyles;
    private final int maxHitCap;
    private final int maxHitCapWhenCrushHighest;

    private MonsterCombatRequirement(Type type, Set<Integer> allowedItemIds, Set<Integer> allowedAmmoIds,
                                     Set<CombatStyle> allowedStyles, Set<Integer> finisherItemIds, String note,
                                     double damageMultiplier, Set<CombatStyle> penalisedStyles,
                                     int maxHitCap, int maxHitCapWhenCrushHighest)
    {
        this.type = type;
        this.allowedItemIds = allowedItemIds == null ? Collections.emptySet() : new HashSet<>(allowedItemIds);
        this.allowedAmmoIds = allowedAmmoIds == null ? Collections.emptySet() : new HashSet<>(allowedAmmoIds);
        this.allowedStyles = (allowedStyles == null || allowedStyles.isEmpty())
            ? EnumSet.noneOf(CombatStyle.class) : EnumSet.copyOf(allowedStyles);
        this.finisherItemIds = finisherItemIds == null ? Collections.emptySet() : new HashSet<>(finisherItemIds);
        this.note = note == null ? "" : note;
        this.damageMultiplier = damageMultiplier;
        this.penalisedStyles = (penalisedStyles == null || penalisedStyles.isEmpty())
            ? EnumSet.noneOf(CombatStyle.class) : EnumSet.copyOf(penalisedStyles);
        this.maxHitCap = maxHitCap;
        this.maxHitCapWhenCrushHighest = maxHitCapWhenCrushHighest;
    }

    public static MonsterCombatRequirement weaponGate(Set<Integer> allowedItemIds, Set<Integer> allowedAmmoIds,
                                                      Set<CombatStyle> allowedStyles, String note)
    {
        return new MonsterCombatRequirement(Type.WEAPON_GATE, allowedItemIds, allowedAmmoIds,
            allowedStyles, Collections.emptySet(), note, 1.0, Collections.emptySet(), -1, -1);
    }

    public static MonsterCombatRequirement finisher(Set<Integer> finisherItemIds, String note)
    {
        return new MonsterCombatRequirement(Type.FINISHER, Collections.emptySet(), Collections.emptySet(),
            EnumSet.noneOf(CombatStyle.class), finisherItemIds, note, 1.0, Collections.emptySet(), -1, -1);
    }

    /**
     * A monster that deals-with (rather than blocks) an off-style weapon: any
     * weapon NOT in {@code allowedItemIds} has its max hit multiplied by
     * {@code damageMultiplier} for a style in {@code penalisedStyles} (empty
     * means every style). This never gates — see {@link TargetDamageRule}.
     */
    public static MonsterCombatRequirement damagePenalty(Set<Integer> allowedItemIds, double damageMultiplier,
                                                          Set<CombatStyle> penalisedStyles, String note)
    {
        return new MonsterCombatRequirement(Type.DAMAGE_PENALTY, allowedItemIds, Collections.emptySet(),
            EnumSet.noneOf(CombatStyle.class), Collections.emptySet(), note, damageMultiplier, penalisedStyles, -1, -1);
    }

    /**
     * A flat max-hit ceiling regardless of gear, with an alternative (usually
     * higher) ceiling when the loadout's crush attack bonus is its highest —
     * see {@link TargetDamageRule#maxHitCapFor}. This never gates.
     */
    public static MonsterCombatRequirement damageCap(int maxHitCap, int maxHitCapWhenCrushHighest, String note)
    {
        return new MonsterCombatRequirement(Type.DAMAGE_CAP, Collections.emptySet(), Collections.emptySet(),
            EnumSet.noneOf(CombatStyle.class), Collections.emptySet(), note, 1.0, Collections.emptySet(),
            maxHitCap, maxHitCapWhenCrushHighest);
    }

    public Type type() { return type; }
    public String note() { return note; }
    public Set<Integer> finisherItemIds() { return Collections.unmodifiableSet(finisherItemIds); }
    public Set<Integer> allowedItemIds() { return Collections.unmodifiableSet(allowedItemIds); }
    public Set<Integer> allowedAmmoIds() { return Collections.unmodifiableSet(allowedAmmoIds); }
    public Set<CombatStyle> allowedStyles() { return Collections.unmodifiableSet(allowedStyles); }

    /** Multiplier applied to max hit when the weapon is NOT in {@link #allowedItemIds()}. Default {@code 1.0}. */
    public double damageMultiplier() { return damageMultiplier; }

    /** Styles the {@link #damageMultiplier()} applies to; empty means "all styles". Default empty. */
    public Set<CombatStyle> penalisedStyles() { return Collections.unmodifiableSet(penalisedStyles); }

    /** Flat max-hit ceiling; {@code -1} means "no cap". Default {@code -1}. */
    public int maxHitCap() { return maxHitCap; }

    /** Alternative cap used when crush is the loadout's highest attack bonus; {@code -1} means "no such rule". Default {@code -1}. */
    public int maxHitCapWhenCrushHighest() { return maxHitCapWhenCrushHighest; }

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
