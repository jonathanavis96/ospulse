package com.ospulse.combat;

import java.util.Set;

/**
 * Damage-magnitude effects a hard style-gate cannot express: a monster that
 * takes reduced damage from an off-style weapon rather than none at all
 * ({@link MonsterCombatRequirement.Type#DAMAGE_PENALTY}, e.g. Corporeal
 * Beast's half-damage stab), and a monster whose max hit is flatly capped
 * regardless of gear ({@link MonsterCombatRequirement.Type#DAMAGE_CAP}, e.g.
 * The Hueycoatl's tail). Pure, no RuneLite deps — see {@link DpsCalculator}
 * for where these are applied.
 */
public final class TargetDamageRule
{
    private TargetDamageRule()
    {
    }

    /**
     * Applies when: {@code req} is non-null, {@code req.type()} is
     * {@link MonsterCombatRequirement.Type#DAMAGE_PENALTY}, {@code style} is
     * one of {@code req.penalisedStyles()} (an empty set means "all styles"),
     * AND the weapon does not earn the exemption. When it applies, returns
     * {@code req.damageMultiplier()}; otherwise returns {@code 1.0} — the
     * neutral value for a {@code null} requirement, any other {@link
     * MonsterCombatRequirement.Type}, an unpenalised style, or an exempt
     * weapon. A penalty never gates a weapon out — see {@link
     * MonsterCombatRequirement#permits} for that.
     *
     * <p><b>The exemption is style-sensitive.</b> A weapon in
     * {@code req.allowedItemIds()} escapes the penalty only when the current
     * style is in {@code req.exemptStyles()}; an empty {@code exemptStyles}
     * means the weapon is exempt on any penalised style. This distinction is
     * load-bearing at the Corporeal Beast, whose rule is "50% reduction against
     * any weapon that is not a corpbane weapon <i>on stab attack style</i>" — a
     * Zamorakian spear swung on slash is still halved, so exempting it purely
     * on item id would overstate that loadout.
     */
    public static double damageMultiplierFor(MonsterCombatRequirement req, int weaponId, CombatStyle style)
    {
        if (req == null || req.type() != MonsterCombatRequirement.Type.DAMAGE_PENALTY)
        {
            return 1.0;
        }
        Set<CombatStyle> penalisedStyles = req.penalisedStyles();
        if (!penalisedStyles.isEmpty() && !penalisedStyles.contains(style))
        {
            return 1.0;
        }
        if (req.allowedItemIds().contains(weaponId) && exemptOnThisStyle(req, style))
        {
            return 1.0;
        }
        return req.damageMultiplier();
    }

    /** An empty {@code exemptStyles} keeps the old "exempt on any penalised style" behaviour. */
    private static boolean exemptOnThisStyle(MonsterCombatRequirement req, CombatStyle style)
    {
        Set<CombatStyle> exemptStyles = req.exemptStyles();
        return exemptStyles.isEmpty() || exemptStyles.contains(style);
    }

    /**
     * Applies when: {@code req} is non-null and {@code req.type()} is
     * {@link MonsterCombatRequirement.Type#DAMAGE_CAP}. Otherwise (including
     * a {@code null} requirement or any other type) returns {@code -1} —
     * "no cap".
     *
     * <p>Resolution order, each step only reached if the previous one
     * doesn't decide it:
     * <ol>
     * <li>{@code weaponId} is in {@code req.allowedItemIds()} — the weapon is
     * wholly exempt from the cap (e.g. Dawnbringer at Verzik Vitur: "has no
     * damage cap on the boss"). Returns {@code -1}.</li>
     * <li>{@code style} has an entry in {@code req.maxHitCapByStyle()} — a
     * per-style split (e.g. Verzik phase 1: melee 10, ranged/magic 3, one
     * flat value cannot express this). Returns that value.</li>
     * <li>Otherwise, the original flat/crush-highest logic: {@code
     * req.maxHitCapWhenCrushHighest()} if that is non-negative AND the
     * loadout's crush attack bonus is strictly greater than both its stab
     * and slash attack bonuses, else {@code req.maxHitCap()}.</li>
     * </ol>
     * Every entry with an empty {@code maxHitCapByStyle} and empty {@code
     * allowedItemIds} (every one shipped before per-style caps existed) is
     * unaffected — steps 1 and 2 never match and step 3 runs exactly as
     * before.
     */
    public static int maxHitCapFor(MonsterCombatRequirement req, EquipmentStats gear, CombatStyle style, int weaponId)
    {
        if (req == null || req.type() != MonsterCombatRequirement.Type.DAMAGE_CAP)
        {
            return -1;
        }
        if (req.allowedItemIds().contains(weaponId))
        {
            return -1;
        }
        Integer perStyle = req.maxHitCapByStyle().get(style);
        if (perStyle != null)
        {
            return perStyle;
        }
        if (req.maxHitCapWhenCrushHighest() >= 0
                && gear.acrush() > gear.astab() && gear.acrush() > gear.aslash())
        {
            return req.maxHitCapWhenCrushHighest();
        }
        return req.maxHitCap();
    }

    /**
     * Which distribution {@link #maxHitCapFor} applies with — see {@link
     * MonsterCombatRequirement.CapMode}. {@code null} or a non-{@link
     * MonsterCombatRequirement.Type#DAMAGE_CAP} requirement returns {@link
     * MonsterCombatRequirement.CapMode#CLAMP} (the neutral default; harmless
     * since {@link #maxHitCapFor} already returns {@code -1} — "no cap" — for
     * those cases, so no caller ever acts on this value without a real cap).
     */
    public static MonsterCombatRequirement.CapMode capModeFor(MonsterCombatRequirement req)
    {
        if (req == null || req.type() != MonsterCombatRequirement.Type.DAMAGE_CAP)
        {
            return MonsterCombatRequirement.CapMode.CLAMP;
        }
        return req.capMode();
    }
}
