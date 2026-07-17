package com.ospulse.combat.optimizer;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.EquipmentStatsRepository;
import com.ospulse.combat.WeaponCategoryRepository;
import com.ospulse.combat.WeaponStyle;

/**
 * Pure item-id scoring heuristics shared by {@link GearOptimizer}'s pruning
 * and tie-breaking passes: cheap proxies computed straight from
 * {@link EquipmentStatsRepository}/{@link WeaponCategoryRepository} bundled
 * data, with no dependency on a search {@code Request} or any in-progress
 * loadout. Method bodies are unchanged from their previous home in
 * {@code GearOptimizer}.
 */
public final class CandidateScore {
    private CandidateScore() {
    }

    /**
     * A DPS-tie tie-break score: the sum of an item's every offensive, defensive
     * and prayer bonus. Used to fill DPS-neutral slots (B9-2) with the best
     * wearable owned piece on a DPS tie; an empty/unknown slot scores lowest so
     * any real item beats "wear nothing".
     */
    public static int tieBreakScore(int itemId) {
        if (itemId <= 0) {
            return Integer.MIN_VALUE;
        }
        EquipmentStatsRepository.Stats s = EquipmentStatsRepository.getInstance().statsFor(itemId);
        if (s == null) {
            return Integer.MIN_VALUE + 1;
        }
        return s.astab() + s.aslash() + s.acrush() + s.amagic() + s.arange()
                + s.dstab() + s.dslash() + s.dcrush() + s.dmagic() + s.drange()
                + s.str() + s.rstr() + (int) s.mdmg() + s.prayer();
    }

    /** The prayer bonus of one item (0 if unknown), for the ammo-slot-ignored-by-weapon ranking exception. */
    public static int prayerBonusOf(int itemId) {
        EquipmentStatsRepository.Stats s = EquipmentStatsRepository.getInstance().statsFor(itemId);
        return s == null ? 0 : s.prayer();
    }

    /** True when the weapon id's real combat options include a style of {@code type} ({@code null} type = no constraint). */
    public static boolean weaponSupportsStyle(int weaponId, CombatStyle type) {
        if (type == null) {
            return true;
        }
        for (WeaponStyle style : WeaponCategoryRepository.getInstance().stylesForItem(weaponId)) {
            if (style.type() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cheap pruning proxy for one item. Unconstrained ({@code style ==
     * null}): the historical sum of every offensive bonus. Style-constrained:
     * only the bonuses that actually feed that style's DPS — otherwise e.g. a
     * MAGIC-constrained search would prune Ancestral (small raw totals) in
     * favour of melee armour whose big slash/str numbers are irrelevant to the
     * constraint. Weights: magic damage is a percentage (1% is worth far more
     * than 1 accuracy point) and ranged strength scales the max hit directly
     * (keeps ammo/blessing candidates ranked above accuracy-only picks).
     */
    public static int proxyOffensiveScore(int itemId, CombatStyle style) {
        EquipmentStatsRepository.Stats s = EquipmentStatsRepository.getInstance().statsFor(itemId);
        if (s == null) {
            return Integer.MIN_VALUE;
        }
        if (style == null) {
            return s.astab() + s.aslash() + s.acrush() + s.arange() + s.amagic() + s.str() + s.rstr() + (int) s.mdmg();
        }
        switch (style) {
            case STAB:
                return s.astab() + s.str();
            case SLASH:
                return s.aslash() + s.str();
            case CRUSH:
                return s.acrush() + s.str();
            case RANGED:
                return s.arange() + 2 * s.rstr();
            case MAGIC:
                return s.amagic() + (int) (10 * s.mdmg());
            default:
                return s.astab() + s.aslash() + s.acrush() + s.arange() + s.amagic() + s.str() + s.rstr() + (int) s.mdmg();
        }
    }
}
