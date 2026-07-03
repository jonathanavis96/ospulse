package com.ospulse.combat;

/**
 * "Best available potion" boost formulas for {@link PlayerCombat#assumeBestPotion()}.
 * All boosts are {@code base + floor(baseLevel * pct)} — the flat integer is
 * added AFTER flooring the percentage term, per the OSRS Wiki's
 * "Temporary skill boost" pages (Static/current level * pct, floored, then
 * a flat addend).
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Temporary_skill_boost/Strength">Temporary skill boost/Strength</a> (Super strength: 5 + floor(level*0.15))
 * @see <a href="https://oldschool.runescape.wiki/w/Temporary_skill_boost/Attack">Temporary skill boost/Attack</a> (Super attack: 5 + floor(level*0.15))
 * @see <a href="https://oldschool.runescape.wiki/w/Temporary_skill_boost/Ranged">Temporary skill boost/Ranged</a> (Ranging potion: 4 + floor(level*0.1))
 * @see <a href="https://oldschool.runescape.wiki/w/Imbued_heart">Imbued heart</a> (Magic: floor(level*0.1) + 1)
 */
final class PotionBoosts {
    private PotionBoosts() {
    }

    /** Super attack / super strength / super combat potion: best sustained melee accuracy+damage boost. */
    static int bestMeleeBoostedLevel(int baseLevel) {
        return baseLevel + 5 + (int) Math.floor(baseLevel * 0.15);
    }

    /** Ranging potion (also Bastion potion): best sustained ranged boost. */
    static int bestRangedBoostedLevel(int baseLevel) {
        return baseLevel + 4 + (int) Math.floor(baseLevel * 0.1);
    }

    /** Imbued heart: best readily-repeatable magic accuracy boost (saturated heart's +3 is a Tier-B refinement). */
    static int bestMagicBoostedLevel(int baseLevel) {
        return imbuedHeartBoostedLevel(baseLevel);
    }

    /** Imbued heart (Invigorate): {@code 1 + floor(level * 0.1)}. */
    static int imbuedHeartBoostedLevel(int baseLevel) {
        return baseLevel + (int) Math.floor(baseLevel * 0.1) + 1;
    }

    /** Saturated heart (upgraded Imbued heart): {@code 4 + floor(level * 0.1)} — the highest magic-level boost. */
    static int saturatedHeartBoostedLevel(int baseLevel) {
        return baseLevel + (int) Math.floor(baseLevel * 0.1) + 4;
    }

    /**
     * Ancient brew: {@code 2 + floor(level * 0.05)} — a smaller Magic boost than
     * either heart, traded for draining Attack/Strength/Defence (not modelled
     * here; this method only covers the Magic-level side of the potion).
     */
    static int ancientBrewBoostedLevel(int baseLevel) {
        return baseLevel + (int) Math.floor(baseLevel * 0.05) + 2;
    }
}
