package com.ospulse.combat;

/**
 * Presentation-facing mapping from (combat style, Prayer level, boost
 * toggles) to the single best-available offensive prayer / boosting potion
 * to show as an icon — pure logic over the existing {@link OffensivePrayer}
 * and {@link PotionBoosts} model (see {@code com.ospulse.ui.sections.GearSection}
 * for the Swing rendering, and {@link DpsCalculator} for the actual boost
 * math this must stay consistent with).
 * <p>
 * <b>Consistency with the calculator:</b> when the "best prayer" toggle is
 * ON, {@link DpsCalculator} always assumes the level-70+ prayer (Piety /
 * Rigour / Augury) regardless of the player's real Prayer level — a
 * "what's my ceiling" simulation. {@link #bestOffensivePrayer} mirrors that:
 * pass {@code assumeBestPrayer = true} to get the calculator's hardcoded
 * top-tier prayer unconditionally, or {@code false} to get the best prayer
 * the player's actual {@code prayerLevel} can use (degrading down the
 * ladder) — matching how {@link DpsCalculator} otherwise reads whichever
 * prayer the player actually has toggled on in {@link PlayerCombat#activePrayers()}.
 * The ladder ordering here is by Prayer-level requirement, so "auto-selected
 * by combat style and the player's stats" always resolves to the same
 * prayer that a level-gated {@link PlayerCombat#activePrayers()} entry would
 * be for a legitimate account.
 */
public final class CombatIcons {

    private CombatIcons() {
    }

    /**
     * One rung of a prayer ladder: the prayer plus the Prayer level required
     * to use it in-game (per the <a href="https://oldschool.runescape.wiki/w/Prayer">
     * OSRS Wiki Prayer page</a>).
     */
    private static final class Rung {
        final OffensivePrayer prayer;
        final int levelRequired;

        Rung(OffensivePrayer prayer, int levelRequired) {
            this.prayer = prayer;
            this.levelRequired = levelRequired;
        }
    }

    // Highest-tier rung first — ladders are walked top-down so the first
    // rung the player's level satisfies wins.
    private static final Rung[] MELEE_LADDER = {
        new Rung(OffensivePrayer.PIETY, 70),
        new Rung(OffensivePrayer.CHIVALRY, 60),
        new Rung(OffensivePrayer.ULTIMATE_STRENGTH, 31),
        new Rung(OffensivePrayer.SUPERHUMAN_STRENGTH, 20),
        new Rung(OffensivePrayer.BURST_OF_STRENGTH, 4),
    };

    private static final Rung[] RANGED_LADDER = {
        new Rung(OffensivePrayer.RIGOUR, 74),
        new Rung(OffensivePrayer.EAGLE_EYE, 70),
        new Rung(OffensivePrayer.HAWK_EYE, 44),
        new Rung(OffensivePrayer.SHARP_EYE, 8),
    };

    private static final Rung[] MAGIC_LADDER = {
        new Rung(OffensivePrayer.AUGURY, 77),
        new Rung(OffensivePrayer.MYSTIC_MIGHT, 74),
        new Rung(OffensivePrayer.MYSTIC_LORE, 60),
        new Rung(OffensivePrayer.MYSTIC_WILL, 45),
    };

    /**
     * The single offensive prayer to show as the style's icon.
     *
     * @param style            the active combat style (melee sub-styles all
     *                         share the same ladder — see {@link CombatStyle#isMelee()})
     * @param prayerLevel      the player's (base) Prayer level
     * @param assumeBestPrayer mirrors the "best prayer" simulation toggle:
     *                         when {@code true}, returns the calculator's
     *                         hardcoded top-tier prayer (Piety/Rigour/Augury)
     *                         regardless of level, matching {@link DpsCalculator}'s
     *                         {@code assumeBestPrayer} branch; when
     *                         {@code false}, degrades down the ladder to the
     *                         best prayer {@code prayerLevel} actually unlocks
     * @return the best-available prayer for {@code style}, or {@code null} if
     *         the player's level is below even the lowest ladder rung
     */
    public static OffensivePrayer bestOffensivePrayer(CombatStyle style, int prayerLevel, boolean assumeBestPrayer) {
        Rung[] ladder = ladderFor(style);
        if (ladder == null) {
            return null;
        }
        if (assumeBestPrayer) {
            return ladder[0].prayer;
        }
        for (Rung rung : ladder) {
            if (prayerLevel >= rung.levelRequired) {
                return rung.prayer;
            }
        }
        return null;
    }

    private static Rung[] ladderFor(CombatStyle style) {
        if (style == null) {
            return null;
        }
        if (style.isMelee()) {
            return MELEE_LADDER;
        }
        if (style == CombatStyle.RANGED) {
            return RANGED_LADDER;
        }
        if (style == CombatStyle.MAGIC) {
            return MAGIC_LADDER;
        }
        return null;
    }

    /**
     * The style-appropriate boosting potion to show as the style's icon.
     *
     * <p>Melee's three variants (Super combat / Super strength / Super attack)
     * all resolve to the exact same {@link PotionBoosts#bestMeleeBoostedLevel}
     * boost, and Ranged's two variants (Ranging / Bastion) both resolve to
     * {@link PotionBoosts#bestRangedBoostedLevel} — real OSRS potions differ
     * there only in which stat(s) they boost (Super attack: Attack only;
     * Super strength: Strength only; Super combat/Bastion: both at once), and
     * since the DPS calculator already applies "best" to whichever levels the
     * active style actually reads, the choice between them is cosmetic
     * (icon/inventory-item only), unlike Magic's three variants which genuinely
     * differ in Magic-level boost.
     */
    public enum BoostPotion {
        /** Super combat potion — the default melee pick (both Attack and Strength boosted at once). */
        SUPER_COMBAT,
        /** Super strength potion — melee variant, Strength-boost flavour only (same {@link PotionBoosts#bestMeleeBoostedLevel} boost). */
        SUPER_STRENGTH,
        /** Super attack potion — melee variant, Attack-boost flavour only (same {@link PotionBoosts#bestMeleeBoostedLevel} boost). */
        SUPER_ATTACK,
        /** Ranging potion — the default ranged pick. */
        RANGING,
        /** Bastion potion — ranged variant (same {@link PotionBoosts#bestRangedBoostedLevel} boost as Ranging). */
        BASTION,
        /** Divine ranging potion — ranged variant (same {@link PotionBoosts#bestRangedBoostedLevel} boost; in-game its real edge is auto-reapplying on expiry, not modelled here). */
        DIVINE_RANGING,
        /** Imbued heart (Invigorate) — {@link PotionBoosts#imbuedHeartBoostedLevel}. The default magic pick. */
        IMBUED_HEART,
        /** Saturated heart (upgraded Imbued heart) — {@link PotionBoosts#saturatedHeartBoostedLevel}, the highest magic-level boost. */
        SATURATED_HEART,
        /**
         * Ancient brew — {@link PotionBoosts#ancientBrewBoostedLevel}, a smaller
         * Magic boost traded for draining Attack/Strength/Defence (the drain
         * itself is not modelled by the DPS calculator).
         */
        ANCIENT_BREW,
    }

    /** The magic-style potion variants offered by the potion toggle's right-click swap menu, best-to-worst Magic boost. */
    public static final BoostPotion[] MAGIC_POTION_VARIANTS = {
        BoostPotion.SATURATED_HEART, BoostPotion.IMBUED_HEART, BoostPotion.ANCIENT_BREW,
    };

    /** The melee-style potion variants offered by the potion toggle's right-click swap menu (default first). */
    public static final BoostPotion[] MELEE_POTION_VARIANTS = {
        BoostPotion.SUPER_COMBAT, BoostPotion.SUPER_STRENGTH, BoostPotion.SUPER_ATTACK,
    };

    /** The ranged-style potion variants offered by the potion toggle's right-click swap menu (default first). */
    public static final BoostPotion[] RANGED_POTION_VARIANTS = {
        BoostPotion.RANGING, BoostPotion.BASTION, BoostPotion.DIVINE_RANGING,
    };

    /** The right-click swap menu's variant list for {@code style} (melee/ranged/magic), or an empty array if {@code style} has no swappable variants. */
    public static BoostPotion[] variantsFor(CombatStyle style) {
        if (style == null) {
            return new BoostPotion[0];
        }
        if (style.isMelee()) {
            return MELEE_POTION_VARIANTS;
        }
        if (style == CombatStyle.RANGED) {
            return RANGED_POTION_VARIANTS;
        }
        if (style == CombatStyle.MAGIC) {
            return MAGIC_POTION_VARIANTS;
        }
        return new BoostPotion[0];
    }

    /** The boosting potion the calculator's {@code assumeBestPotion} branch applies for {@code style} — the style's DEFAULT variant. */
    public static BoostPotion bestPotion(CombatStyle style) {
        if (style == null) {
            return null;
        }
        if (style.isMelee()) {
            return BoostPotion.SUPER_COMBAT;
        }
        if (style == CombatStyle.RANGED) {
            return BoostPotion.RANGING;
        }
        if (style == CombatStyle.MAGIC) {
            return BoostPotion.IMBUED_HEART;
        }
        return null;
    }

    /** True for a melee-flavour variant (Super combat/strength/attack) — used to normalize the DPS boost math, which is identical across all three. */
    public static boolean isMeleeVariant(BoostPotion potion) {
        return potion == BoostPotion.SUPER_COMBAT || potion == BoostPotion.SUPER_STRENGTH || potion == BoostPotion.SUPER_ATTACK;
    }

    /** True for a ranged-flavour variant (Ranging/Bastion/Divine ranging) — used to normalize the DPS boost math, which is identical across all three. */
    public static boolean isRangedVariant(BoostPotion potion) {
        return potion == BoostPotion.RANGING || potion == BoostPotion.BASTION || potion == BoostPotion.DIVINE_RANGING;
    }
}
