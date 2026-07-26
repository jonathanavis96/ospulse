package com.ospulse.combat;

/**
 * The core, style-agnostic arithmetic from the OSRS Wiki DPS calculator
 * pages. Every method here floors at exactly the steps the wiki documents —
 * no more, no fewer — since the rounding order is load-bearing for
 * correctness. Integer arithmetic ({@link Fraction}) is used wherever the
 * wiki specifies an exact fraction (7/6, 6/5, 23/20, ...) to avoid
 * floating-point representation error changing a floor() result.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Damage_per_second/Melee">DPS/Melee</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Maximum_ranged_hit">Maximum ranged hit</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Damage_per_second/Magic">DPS/Magic</a>
 */
final class CombatMath {
    private CombatMath() {
    }

    /**
     * Effective melee or ranged level (attack or strength side), per:
     * <pre>
     * floor((floor(boostedLevel * prayerMult) + styleBonus + flatAdd) * voidMult)
     * </pre>
     * {@code boostedLevel} is the level AFTER any potion boost (i.e. "(level + boost)"
     * in wiki terms — {@link PlayerCombat#boostedAttack()} etc. already are this, or
     * see {@link PotionBoosts} to derive one from a base level).
     * flatAdd is 8 for melee/ranged. Void multiplies AFTER the style bonus is
     * added, floor only at the very start and the very end.
     */
    static int effectiveMeleeOrRangedLevel(int boostedLevel, double prayerMult, int styleBonus, int flatAdd, double voidMult) {
        int afterPrayer = (int) Math.floor(boostedLevel * prayerMult);
        return (int) Math.floor((afterPrayer + styleBonus + flatAdd) * voidMult);
    }

    /**
     * Effective magic (accuracy) level, per DPS/Magic step two:
     * <pre>
     * floor(floor(boostedLevel * prayerMult) * voidMult + styleBonus + 9)
     * </pre>
     * Void multiplies BEFORE the style bonus/+9 is added here (unlike melee/ranged) —
     * the wiki lists "round down", "multiply void", "+3/+1", "+9", "round down" in that order.
     */
    static int effectiveMagicLevel(int boostedLevel, double prayerMult, int styleBonus, double voidMult) {
        int afterPrayer = (int) Math.floor(boostedLevel * prayerMult);
        double afterVoid = afterPrayer * voidMult;
        return (int) Math.floor(afterVoid + styleBonus + 9);
    }

    /**
     * Melee/ranged max hit, per DPS/Melee step two:
     * <pre>
     * base = floor(0.5 + effStr * (gearStrBonus + 64) / 640)     [== floor((effStr*(gearStrBonus+64) + 320) / 640)]
     * maxHit = floor(base * targetGearBonus)
     * </pre>
     * targetGearBonus is an exact {@link Fraction} (e.g. 7/6 for on-task slayer helm); pass {@link Fraction#ONE} for none.
     */
    static int meleeOrRangedMaxHit(int effStr, int gearStrBonus, Fraction targetGearBonus) {
        long base = Math.floorDiv((long) effStr * (gearStrBonus + 64) + 320, 640);
        return (int) targetGearBonus.applyFloor(base);
    }

    /**
     * Melee/ranged attack roll, per DPS/Melee step four — a SINGLE floor at the end
     * (unlike max hit's two separate floor steps):
     * <pre>
     * floor(effAtt * (gearAttBonus + 64) * targetGearBonus)
     * </pre>
     */
    static int meleeOrRangedAttackRoll(int effAtt, int gearAttBonus, Fraction targetGearBonus) {
        long base = (long) effAtt * (gearAttBonus + 64);
        return (int) targetGearBonus.applyFloor(base);
    }

    /**
     * Magic accuracy roll, per DPS/Magic step four — single floor at the end:
     * <pre>
     * floor(effMagic * (gearAmagicBonus + 64) * targetGearBonus)
     * </pre>
     * targetGearBonus is 1.15 (23/20) if wearing an on-task imbued slayer helm/black mask,
     * or attacking undead with an imbued salve amulet; {@link Fraction#ONE} otherwise.
     */
    static int magicAccuracyRoll(int effMagic, int gearAmagicBonus, Fraction targetGearBonus) {
        long base = (long) effMagic * (gearAmagicBonus + 64);
        return (int) targetGearBonus.applyFloor(base);
    }

    /**
     * NPC defence roll (players are out of scope for this calculator — it always
     * targets a {@link Monster}), per DPS/Melee step six:
     * <pre>
     * (targetDefenceLevel + 9) * (targetStyleDefenceBonus + 64)
     * </pre>
     * NPCs get no effective-level bonus (no +8, no stance) — the raw defence level is used directly.
     */
    static int npcDefenceRoll(int targetDefenceLevel, int targetStyleDefenceBonus) {
        return (targetDefenceLevel + 9) * (targetStyleDefenceBonus + 64);
    }

    /**
     * Hit chance, per DPS/Melee step seven (identical formula reused for ranged/magic):
     * <pre>
     * atkRoll &gt; defRoll:  1 - (defRoll + 2) / (2 * (atkRoll + 1))
     * else:               atkRoll / (2 * (defRoll + 1))
     * </pre>
     */
    static double hitChance(int attackRoll, int defenceRoll) {
        if (attackRoll > defenceRoll) {
            return 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0));
        }
        return attackRoll / (2.0 * (defenceRoll + 1.0));
    }

    /** DPS = average damage per attack / (weaponSpeedTicks * 0.6 seconds/tick). */
    static double dps(double averageDamagePerAttack, int weaponSpeedTicks) {
        return averageDamagePerAttack / (weaponSpeedTicks * 0.6);
    }

    /**
     * Simplified Tier-A magic max hit ("Primary Magic Damage" additive stage), per
     * <a href="https://oldschool.runescape.wiki/w/Maximum_magic_hit">Maximum magic hit</a>:
     * <pre>
     * floor(baseSpellMaxHit * (1 + totalDamagePercent / 100))
     * </pre>
     * totalDamagePercent sums gear mdmg%, Salve(i)/(ei)-vs-undead%, and the active
     * Mystic Lore/Might/Vigour/Augury prayer%. Uses integer basis-point arithmetic
     * (percent * 100) to avoid floating-point floor artifacts, matching the exact
     * fraction handling used for melee/ranged elsewhere in this class. Tier B/C
     * multiplicative stages (Shadow bonus, Avarice, Tomes, elemental weakness, ...)
     * are NOT modelled — see {@link DpsCalculator} for the extension point.
     */
    static int magicPrimaryDamage(int baseSpellMaxHit, double totalDamagePercent) {
        long basisPoints = Math.round(totalDamagePercent * 100.0);
        long numerator = 10_000L + basisPoints;
        return (int) Math.floorDiv((long) baseSpellMaxHit * numerator, 10_000L);
    }

    /**
     * Magic "Pre Hit Roll" multiplicative stage — Tier-A only models the on-task
     * imbued slayer helm/black mask +15% bonus, applied as its own floor step
     * after {@link #magicPrimaryDamage}, per Maximum magic hit's "Slayer" bullet.
     */
    static int magicPreHitRoll(int primaryDamage, boolean slayerOnTaskBonusApplies) {
        if (!slayerOnTaskBonusApplies) {
            return primaryDamage;
        }
        return (int) new Fraction(23, 20).applyFloor(primaryDamage); // +15%
    }

    /**
     * Elemental-weakness bonus damage, per the OSRS Wiki
     * <a href="https://oldschool.runescape.wiki/w/Maximum_magic_hit">Maximum
     * magic hit</a> and weirdgloop/osrs-dps-calc: {@code floor(baseSpellMaxHit *
     * severity%)}, computed from the SPELL'S BASE max hit (not the gear-boosted
     * value) and floored on its own. The caller ADDS this to the max hit as the
     * final damage modifier — a separate additive term with its own floor, which
     * is why it must NOT be folded into {@link #magicPrimaryDamage}'s damage
     * percent (that single fold diverges by 1 in ~12% of gear/level combos).
     *
     * @param weaknessSeverityPercent the monster's weakness severity in whole
     *                                percent (e.g. 50 for a 50% weakness)
     */
    static int elementalWeaknessBonus(int baseSpellMaxHit, double weaknessSeverityPercent) {
        long basisPoints = Math.round(weaknessSeverityPercent * 100.0);
        return (int) Math.floorDiv((long) baseSpellMaxHit * basisPoints, 10_000L);
    }

    // ---- Twisted bow ----------------------------------------------------------------------

    /**
     * Twisted bow accuracy modifier (percent), per the published formula on the
     * <a href="https://oldschool.runescape.wiki/w/Twisted_bow">Twisted bow</a> page:
     * <pre>
     * Accuracy% = 140 + (3*Magic - 10)/100 - ((3*Magic/10 - 100)^2)/100,  clamped to [0, 140]
     * </pre>
     * where {@code Magic} is the target's magic level (or magic attack bonus if
     * higher — the bundled monster data has no magic attack bonus field, so the
     * level alone is used), capped at 250 outside the Chambers of Xeric (the
     * CoX 350 cap is not modelled — Tier C). Integer-step truncation matches the
     * weirdgloop reference implementation ({@code tbowScaling}), our parity
     * oracle: the linear term uses the RAW {@code 3*Magic}; only the squared
     * term pre-truncates {@code 3*Magic/10}.
     */
    static int twistedBowAccuracyPercent(int targetMagic) {
        int m = Math.min(Math.max(targetMagic, 0), 250);
        int t = (3 * m) / 10;
        int pct = 140 + (3 * m - 10) / 100 - ((t - 100) * (t - 100)) / 100;
        return Math.max(0, Math.min(140, pct));
    }

    /**
     * Twisted bow damage modifier (percent), same source/shape as
     * {@link #twistedBowAccuracyPercent}:
     * <pre>
     * Damage% = 250 + (3*Magic - 14)/100 - ((3*Magic/10 - 140)^2)/100,  clamped to [0, 250]
     * </pre>
     * As with the accuracy term, the linear part uses the RAW {@code 3*Magic}
     * (not {@code 10*(3*Magic/10)}); only the squared term pre-truncates. This
     * matches weirdgloop's {@code tbowScaling} exactly and fixes a 1%
     * under-count at target magic levels where {@code 3*Magic} is not a
     * multiple of 10 (e.g. magic 38: 85%, not 84%).
     */
    static int twistedBowDamagePercent(int targetMagic) {
        int m = Math.min(Math.max(targetMagic, 0), 250);
        int t = (3 * m) / 10;
        int pct = 250 + (3 * m - 14) / 100 - ((t - 140) * (t - 140)) / 100;
        return Math.max(0, Math.min(250, pct));
    }
}
