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

    /**
     * Average damage per attack, per DPS/Melee step eight:
     * <pre>
     * hitChance * (maxHit / 2 + 1 / (maxHit + 1))
     * </pre>
     * The wiki explicitly documents the small "+1/(maxHit+1)" correction term
     * ("if you roll a 0 on a successful attack it will be changed to a 1").
     */
    static double averageDamagePerAttack(double hitChance, int maxHit) {
        return hitChance * (maxHit / 2.0 + 1.0 / (maxHit + 1.0));
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
}
