package com.ospulse.combat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A monster's combat requirement: a weapon/ammo/style damage gate, a finisher
 * item, or a damage-magnitude effect (penalty/cap) a hard style-gate cannot
 * express. Pure, no RuneLite deps.
 */
public final class MonsterCombatRequirement
{
    public enum Type { WEAPON_GATE, FINISHER, DAMAGE_PENALTY, DAMAGE_CAP }

    /**
     * How a {@link Type#DAMAGE_CAP} ceiling is actually applied to the damage
     * roll — these are mechanically different distributions, not two names
     * for the same thing:
     * <ul>
     * <li>{@link #CLAMP} — the roll stays uniform {@code 0..uncappedMaxHit}
     * and every result above the cap lands ON the cap, piling probability
     * mass there (e.g. The Hueycoatl's tail). See
     * {@link CombatMath#cappedAverageDamagePerAttack}/
     * {@link CombatMath#cappedExpectedOverkill}.</li>
     * <li>{@link #REROLL} — a hit above the cap is re-rolled uniformly into
     * {@code 0..cap} (e.g. Verzik Vitur phase 1). Use
     * {@link CombatMath#rerolledAverageDamagePerAttack}/
     * {@link CombatMath#rerolledExpectedOverkill}.
     *
     * <p><b>Do NOT implement this as {@code maxHit = min(maxHit, cap)} fed
     * through the ordinary uncapped path.</b> That is tempting because the
     * re-rolled distribution is nearly uniform over {@code 0..cap}, but the
     * ordinary formulas carry OSRS's "a rolled 0 becomes 1" correction, and a
     * re-rolled 0 is a GENUINE result that must keep its probability mass. The
     * bump belongs to the damage roll, so it applies before the monster
     * re-rolls and survives only on values that were never re-rolled, giving
     * {@code E = cap/2 + 1/(uncappedMax + 1)} for {@code cap >= 1} — not the
     * {@code cap/2 + 1/(cap + 1)} the ordinary path would produce. At Verzik's
     * ranged/magic cap of 3 that shortcut overstates by about 15% and hands
     * the overkill DP a zero-free distribution, so TTK is wrong too. This
     * exact mistake shipped once and was caught in review.</li>
     * </ul>
     * Default {@link #CLAMP} — every entry shipped before this enum existed
     * used clamp semantics, so this default keeps them byte-identical.
     */
    public enum CapMode { CLAMP, REROLL }

    private final Type type;
    private final Set<Integer> allowedItemIds;
    private final Set<Integer> allowedAmmoIds;
    private final Set<CombatStyle> allowedStyles;
    private final Set<Integer> finisherItemIds;
    private final String note;
    private final double damageMultiplier;
    private final Set<CombatStyle> penalisedStyles;
    private final Set<CombatStyle> exemptStyles;
    private final int maxHitCap;
    private final int maxHitCapWhenCrushHighest;
    private final Map<CombatStyle, Integer> maxHitCapByStyle;
    private final CapMode capMode;

    private MonsterCombatRequirement(Type type, Set<Integer> allowedItemIds, Set<Integer> allowedAmmoIds,
                                     Set<CombatStyle> allowedStyles, Set<Integer> finisherItemIds, String note,
                                     double damageMultiplier, Set<CombatStyle> penalisedStyles,
                                     Set<CombatStyle> exemptStyles,
                                     int maxHitCap, int maxHitCapWhenCrushHighest,
                                     Map<CombatStyle, Integer> maxHitCapByStyle, CapMode capMode)
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
        this.exemptStyles = (exemptStyles == null || exemptStyles.isEmpty())
            ? EnumSet.noneOf(CombatStyle.class) : EnumSet.copyOf(exemptStyles);
        this.maxHitCap = maxHitCap;
        this.maxHitCapWhenCrushHighest = maxHitCapWhenCrushHighest;
        this.maxHitCapByStyle = (maxHitCapByStyle == null || maxHitCapByStyle.isEmpty())
            ? Collections.emptyMap() : new EnumMap<>(maxHitCapByStyle);
        this.capMode = capMode == null ? CapMode.CLAMP : capMode;
    }

    public static MonsterCombatRequirement weaponGate(Set<Integer> allowedItemIds, Set<Integer> allowedAmmoIds,
                                                      Set<CombatStyle> allowedStyles, String note)
    {
        return new MonsterCombatRequirement(Type.WEAPON_GATE, allowedItemIds, allowedAmmoIds,
            allowedStyles, Collections.emptySet(), note, 1.0, Collections.emptySet(), Collections.emptySet(), -1, -1,
            Collections.emptyMap(), CapMode.CLAMP);
    }

    public static MonsterCombatRequirement finisher(Set<Integer> finisherItemIds, String note)
    {
        return new MonsterCombatRequirement(Type.FINISHER, Collections.emptySet(), Collections.emptySet(),
            EnumSet.noneOf(CombatStyle.class), finisherItemIds, note, 1.0, Collections.emptySet(), Collections.emptySet(), -1, -1,
            Collections.emptyMap(), CapMode.CLAMP);
    }

    /**
     * A monster that deals-with (rather than blocks) an off-style weapon: any
     * weapon NOT in {@code allowedItemIds} has its max hit multiplied by
     * {@code damageMultiplier} for a style in {@code penalisedStyles} (empty
     * means every style). This never gates — see {@link TargetDamageRule}.
     */
    public static MonsterCombatRequirement damagePenalty(Set<Integer> allowedItemIds, double damageMultiplier,
                                                          Set<CombatStyle> penalisedStyles,
                                                          Set<CombatStyle> exemptStyles, String note)
    {
        return new MonsterCombatRequirement(Type.DAMAGE_PENALTY, allowedItemIds, Collections.emptySet(),
            EnumSet.noneOf(CombatStyle.class), Collections.emptySet(), note, damageMultiplier, penalisedStyles,
            exemptStyles, -1, -1, Collections.emptyMap(), CapMode.CLAMP);
    }

    /**
     * A flat max-hit ceiling regardless of gear, with an alternative (usually
     * higher) ceiling when the loadout's crush attack bonus is its highest —
     * see {@link TargetDamageRule#maxHitCapFor}. Uses clamp semantics ({@link
     * CapMode#CLAMP}) and no per-style overrides or cap-exempt weapons — the
     * original shape, kept unchanged so every entry written before per-style
     * caps existed is byte-identical. This never gates.
     */
    public static MonsterCombatRequirement damageCap(int maxHitCap, int maxHitCapWhenCrushHighest, String note)
    {
        return damageCap(maxHitCap, maxHitCapWhenCrushHighest, Collections.emptySet(),
            Collections.emptyMap(), CapMode.CLAMP, note);
    }

    /**
     * Full {@link Type#DAMAGE_CAP} form: a per-style cap map (e.g. Verzik
     * Vitur phase 1's melee-10/ranged-3/magic-3 split — one flat value cannot
     * express that), a set of weapons wholly exempt from the cap (e.g.
     * Dawnbringer at Verzik), and a {@link CapMode}. Resolution order — see
     * {@link TargetDamageRule#maxHitCapFor}: an exempt weapon first, then a
     * per-style hit, then the flat/crush-highest fallback above. This never
     * gates.
     */
    public static MonsterCombatRequirement damageCap(int maxHitCap, int maxHitCapWhenCrushHighest,
                                                      Set<Integer> capExemptItemIds,
                                                      Map<CombatStyle, Integer> maxHitCapByStyle,
                                                      CapMode capMode, String note)
    {
        return new MonsterCombatRequirement(Type.DAMAGE_CAP, capExemptItemIds, Collections.emptySet(),
            EnumSet.noneOf(CombatStyle.class), Collections.emptySet(), note, 1.0, Collections.emptySet(),
            Collections.emptySet(), maxHitCap, maxHitCapWhenCrushHighest, maxHitCapByStyle, capMode);
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
    /** Styles on which {@code allowedItemIds} actually grants the exemption; empty = any penalised style. */
    public Set<CombatStyle> exemptStyles() { return Collections.unmodifiableSet(exemptStyles); }

    /** Flat max-hit ceiling; {@code -1} means "no cap". Default {@code -1}. */
    public int maxHitCap() { return maxHitCap; }

    /** Alternative cap used when crush is the loadout's highest attack bonus; {@code -1} means "no such rule". Default {@code -1}. */
    public int maxHitCapWhenCrushHighest() { return maxHitCapWhenCrushHighest; }

    /**
     * Per-{@link CombatStyle} cap override (e.g. Verzik Vitur phase 1: melee
     * 10, ranged/magic 3) — takes priority over {@link #maxHitCap()}/
     * {@link #maxHitCapWhenCrushHighest()} for a style present in this map.
     * Empty means no per-style split; every entry falls back to the flat/
     * crush-highest value. Default empty.
     */
    public Map<CombatStyle, Integer> maxHitCapByStyle() { return Collections.unmodifiableMap(maxHitCapByStyle); }

    /**
     * How the cap is applied to the damage roll — see {@link CapMode}.
     * Default {@link CapMode#CLAMP}, matching every entry shipped before this
     * enum existed.
     */
    public CapMode capMode() { return capMode; }

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
