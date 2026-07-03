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

    /** The style-appropriate boosting potion to show as the style's icon. */
    public enum BoostPotion {
        /** Super combat potion (also covers plain super attack/strength) — {@link PotionBoosts#bestMeleeBoostedLevel}. */
        SUPER_COMBAT,
        /** Ranging potion / Bastion potion — {@link PotionBoosts#bestRangedBoostedLevel}. */
        RANGING,
        /** Imbued heart — {@link PotionBoosts#bestMagicBoostedLevel}. */
        IMBUED_HEART,
    }

    /** The boosting potion the calculator's {@code assumeBestPotion} branch applies for {@code style}. */
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
}
