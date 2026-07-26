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

    /**
     * Average damage per attack when a monster caps each hitsplat (e.g. The
     * Hueycoatl's tail) rather than reducing the roll.
     *
     * <p><b>A cap is not the same as a lower max hit.</b> The damage roll is
     * still uniform over {@code 0..uncappedMaxHit}; every result above the cap
     * is simply reduced TO the cap, so all of that probability mass piles up on
     * the cap instead of being spread over {@code 0..cap}. Modelling it by
     * clamping {@code maxHit} and reusing {@link #averageDamagePerAttack} would
     * assume a uniform {@code 0..cap} roll and badly understate the result —
     * with a cap of 4 against an uncapped max of 40 the true average is ~3.8,
     * not 2.0.
     *
     * <pre>
     * hitChance * ( C(C-1)/2 + (M - C + 1) * C + 1 ) / (M + 1)
     * </pre>
     *
     * where {@code M} is the uncapped max hit and {@code C} the cap. The
     * trailing {@code + 1} is the same "a rolled 0 becomes 1" correction the
     * uncapped formula carries. Setting {@code C == M} reduces this exactly to
     * {@link #averageDamagePerAttack}, which is asserted in the tests.
     */
    static double cappedAverageDamagePerAttack(double hitChance, int uncappedMaxHit, int cap) {
        if (cap >= uncappedMaxHit) {
            return averageDamagePerAttack(hitChance, uncappedMaxHit);
        }
        if (cap <= 0) {
            return 0.0;
        }
        double belowCap = cap * (cap - 1) / 2.0;
        double atCap = (uncappedMaxHit - cap + 1.0) * cap;
        return hitChance * (belowCap + atCap + 1.0) / (uncappedMaxHit + 1.0);
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

    // ---- Osmumten's fang ------------------------------------------------------------------

    /**
     * Osmumten's fang hit chance for STAB attacks only (its double-accuracy-roll
     * passive: two independent accuracy rolls are made and the attack succeeds
     * if EITHER beats the defence roll), per the
     * <a href="https://oldschool.runescape.wiki/w/Osmumten%27s_fang">OSRS Wiki</a>
     * and cross-checked against the weirdgloop DPS calc's {@code getFangAccuracyRoll}
     * (our parity oracle elsewhere in this package):
     * <pre>
     * a &gt; d:  1 - (d+2)(2d+3) / (6*(a+1)^2)
     * a &lt;= d: a(4a+5) / (6*(a+1)*(d+1))
     * </pre>
     * where {@code a} = attack roll, {@code d} = defence roll. NOT the naive
     * {@code 1-(1-p)^2} (that formula is only used inside Tombs of Amascut,
     * out of scope here). Only wired for STAB (per the 17 Jan 2024 update that
     * restricted the passive to Stab styles).
     */
    static double fangHitChance(int attackRoll, int defenceRoll) {
        double a = attackRoll;
        double d = defenceRoll;
        if (attackRoll > defenceRoll) {
            return 1.0 - (d + 2.0) * (2.0 * d + 3.0) / (6.0 * (a + 1.0) * (a + 1.0));
        }
        return a * (4.0 * a + 5.0) / (6.0 * (a + 1.0) * (d + 1.0));
    }

    /**
     * Osmumten's fang average damage per attack, for its compressed damage
     * roll: instead of a uniform 0..maxHit roll, the fang always deals between
     * 15% and 85% of the true max hit (rounded down), per the
     * <a href="https://oldschool.runescape.wiki/w/Osmumten%27s_fang">OSRS Wiki</a>
     * ("if the fang's true max hit was 60, it would roll between 9 and 51") and
     * the weirdgloop DPS calc ({@code shrink = trunc(maxHit * 3/20); minHit =
     * shrink; maxHit -= shrink}). This does not change the expected damage vs a
     * normal 0..maxHit roll (only its variance) EXCEPT for the small "rolled 0
     * is bumped to 1" correction, which only matters when the shrunk min is
     * itself 0 (true max hit &lt;= 6).
     */
    static double fangAverageDamagePerAttack(double hitChance, int trueMaxHit) {
        int shrink = trueMaxHit * 3 / 20; // truncating integer division, matches Math.trunc(maxHit * 3/20)
        int shrunkMin = shrink;
        int shrunkMax = trueMaxHit - shrink;
        if (shrunkMin <= 0) {
            // Degenerate low-level case: falls back to the standard 0..maxHit
            // "rolled 0 -> 1" correction since the shrunk range still touches 0.
            return averageDamagePerAttack(hitChance, shrunkMax);
        }
        return hitChance * (shrunkMin + shrunkMax) / 2.0;
    }

    /**
     * Osmumten's fang average damage per attack against a target that caps each
     * hitsplat (e.g. The Hueycoatl's tail).
     *
     * <p><b>The cap must be applied to the fang's compressed roll, not to the
     * max hit that roll is derived from.</b> The fang shrinks the TRUE max into
     * {@code shrink..(max-shrink)} and only then does each result meet the cap.
     * Capping {@code maxHit} first and shrinking the cap is a different, much
     * smaller distribution: with a true max of 40 the real roll is 6..34, every
     * result of which caps to 4, so the fang averages a full 4 per landed hit —
     * whereas shrinking the cap gives a 0..4 roll averaging only ~2.2.
     *
     * <p>Because the fang's roll is uniform over {@code lo..hi}, clamping it at
     * {@code C} is exact:
     * <pre>
     * C &gt;= hi:  (lo + hi) / 2          — cap never binds
     * C &lt;= lo:  C                      — every hit caps
     * else:     ( (C-1+lo)(C-lo)/2 + (hi-C+1)C ) / (hi-lo+1)
     * </pre>
     *
     * <p>This is the CLAMP model, matching {@link #cappedAverageDamagePerAttack}
     * — a cap that instead re-rolls into {@code 0..C} is simply a lower max hit
     * and needs none of this.
     */
    static double cappedFangAverageDamagePerAttack(double hitChance, int trueMaxHit, int cap) {
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        if (lo <= 0) {
            // Degenerate low-level case: the shrunk range still touches 0, so the
            // standard capped distribution (with its "rolled 0 -> 1" correction)
            // is the right model — same fallback as the uncapped fang formula.
            return cappedAverageDamagePerAttack(hitChance, hi, cap);
        }
        if (cap >= hi) {
            return hitChance * (lo + hi) / 2.0;
        }
        if (cap <= lo) {
            return hitChance * cap;
        }
        double belowCap = (cap - 1.0 + lo) * (cap - lo) / 2.0;
        double atCap = (hi - cap + 1.0) * cap;
        return hitChance * (belowCap + atCap) / (hi - lo + 1.0);
    }

    /**
     * Osmumten's fang average damage per attack against a target that
     * RE-ROLLS each hitsplat above a cap (e.g. Verzik Vitur phase 1), as
     * opposed to {@link #cappedFangAverageDamagePerAttack}'s clamp.
     *
     * <p><b>This is a genuinely different distribution from clamping, and
     * also different from the generic {@code REROLL} equivalence.</b> The
     * generic case (a re-roll into {@code 0..cap} from a uniform
     * {@code 0..M} roll) is exactly a uniform {@code 0..cap} roll — see
     * {@link MonsterCombatRequirement.CapMode#REROLL} — because the WHOLE
     * roll is uniform to begin with. The fang's roll is uniform over
     * {@code lo..hi} (never touching values below {@code lo} or above
     * {@code hi}), so a monster re-rolling one of ITS hitsplats above the cap
     * back into {@code 0..cap} does not reproduce a plain {@code lo..cap}
     * roll: values in {@code lo..cap} are both directly kept AND topped up by
     * the re-rolled share from every {@code hi..(cap+1)} result, while values
     * below {@code lo} can ONLY be reached via a re-roll. Collapsing
     * {@code maxHit} to the cap and re-shrinking (mirroring the generic
     * equivalence) reproduces neither: with a true max of 40 and a cap of 10,
     * the real roll is {@code 6..34} (every result of which is &gt;= 6), so a
     * re-roll into {@code 0..10} can and does land below 6 — a shrink-the-cap
     * model (compressing 10 into {@code 1..9}) can never produce that.
     *
     * <p>Worked example (also pinned in the tests): true max 40, cap 10 -&gt;
     * {@code lo=6, hi=34}. Results 6-10 (5 of the 29 equally likely raw
     * outcomes) stand as themselves; results 11-34 (24 outcomes) re-roll
     * uniformly into {@code 0..10}, each contributing an expectation of 5.
     * Average = {@code (6+7+8+9+10 + 24*5) / 29 = 160/29 ≈ 5.5172}, NOT
     * {@code fangAverageDamagePerAttack(hitChance, 10) ≈ 5.0} (which wrongly
     * re-derives the compression from the already-capped value).
     *
     * <pre>
     * lo &lt;= 0  : averageDamagePerAttack(hitChance, min(hi, cap))     — degenerate range touches 0; the generic equivalence applies directly
     * cap &gt;= hi: fangAverageDamagePerAttack(hitChance, trueMaxHit)   — cap can never bind
     * cap &lt; lo : hitChance * cap / 2                                 — every result re-rolls into a plain uniform 0..cap
     * else     : hitChance * ( sum_(d=lo)^(cap) d + (hi-cap)*cap/2 ) / (hi-lo+1)
     *            where sum_(d=lo)^(cap) d = (lo+cap)*(cap-lo+1)/2
     * </pre>
     * None of these branches carry the ordinary "rolled 0 is bumped to 1"
     * correction: the worked example above only reduces to {@code 160/29}
     * without it, and a re-rolled fang hitsplat of 0 is a genuine result, not
     * folded into 1.
     */
    static double rerolledFangAverageDamagePerAttack(double hitChance, int trueMaxHit, int cap) {
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        if (lo <= 0) {
            return averageDamagePerAttack(hitChance, Math.min(hi, cap));
        }
        if (cap >= hi) {
            return fangAverageDamagePerAttack(hitChance, trueMaxHit);
        }
        if (cap < lo) {
            return hitChance * cap / 2.0;
        }
        double keptSum = (lo + cap) * (cap - lo + 1.0) / 2.0;
        double rerolledSum = (hi - cap) * cap / 2.0;
        return hitChance * (keptSum + rerolledSum) / (hi - lo + 1.0);
    }

    // ---- Overkill ---------------------------------------------------------------------------

    /**
     * Expected damage wasted on the killing blow (damage rolled beyond the
     * target's remaining hitpoints), in hitpoints per kill.
     *
     * <p>Model: exact dynamic programme over remaining-HP states using the same
     * per-attack damage distribution as {@link #averageDamagePerAttack} — a
     * successful hit rolls uniform 0..maxHit with a rolled 0 bumped to 1 (so 1
     * has probability 2/(maxHit+1), each of 2..maxHit has 1/(maxHit+1)); a miss
     * deals 0. Misses don't change the HP state, so they cancel out of the
     * recursion algebraically and the result is independent of hit chance:
     * <pre>
     * O[h] = sum over successful damage d of P(d) * (d &gt;= h ? d - h : O[h - d])
     * </pre>
     * O(hp * maxHit) time — trivially fast at OSRS scales.
     */
    static double expectedOverkill(int maxHit, int targetHitpoints) {
        if (maxHit <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d <= maxHit; d++) {
                double p = (d == 1 ? 2.0 : 1.0) / (maxHit + 1);
                sum += p * (d >= h ? (d - h) : over[h - d]);
            }
            over[h] = sum;
        }
        return over[targetHitpoints];
    }

    /**
     * Expected overkill when each hitsplat is capped — the same recurrence as
     * {@link #expectedOverkill}, but over the capped damage distribution rather
     * than a uniform {@code 0..cap} one.
     *
     * <p>Necessary for the same reason as {@link #cappedAverageDamagePerAttack}:
     * the roll spans {@code 0..M} and everything from {@code cap} upward lands on
     * the cap, so the cap carries {@code (M - cap + 1)/(M + 1)} of the mass rather
     * than {@code 1/(cap + 1)}. Feeding a clamped max hit into the uniform
     * version assumes a flat 1..cap spread and gets overkill — and therefore TTK
     * — wrong for every capped setup.
     *
     * <p>Distribution (the 0-to-1 correction applies to the roll, before the cap):
     * roll 0 and roll 1 both give 1; rolls {@code 2..cap-1} give themselves;
     * rolls {@code cap..M} all give {@code cap}. With {@code cap == 1} every roll
     * gives 1. Setting {@code cap >= M} delegates to {@link #expectedOverkill},
     * which the tests assert.
     */
    static double cappedExpectedOverkill(int uncappedMaxHit, int cap, int targetHitpoints) {
        if (cap >= uncappedMaxHit) {
            return expectedOverkill(uncappedMaxHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double denom = uncappedMaxHit + 1.0;
        double[] p = new double[cap + 1];
        if (cap == 1) {
            p[1] = 1.0;
        } else {
            p[1] = 2.0 / denom;
            for (int d = 2; d <= cap - 1; d++) {
                p[d] = 1.0 / denom;
            }
            p[cap] = (uncappedMaxHit - cap + 1.0) / denom;
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d <= cap; d++) {
                sum += p[d] * (d >= h ? (d - h) : over[h - d]);
            }
            over[h] = sum;
        }
        return over[targetHitpoints];
    }

    /**
     * Expected overkill for Osmumten's fang against a capped target: the same
     * recurrence again, but over the fang's COMPRESSED roll with each result
     * clamped — the distribution {@link #cappedFangAverageDamagePerAttack}
     * averages.
     *
     * <p>Without this, a capped fang setup reports its average damage from one
     * distribution and its overkill (and therefore TTK) from another. That
     * inconsistency is not academic: with a true max of 40 and a cap of 4 the
     * fang's roll is {@code 6..34}, so <b>every</b> landed hit deals exactly 4
     * — yet the generic capped distribution still assigns probability to 1, 2
     * and 3, and predicts a different number of hits to a kill.
     *
     * <p>The uncapped fang path deliberately keeps using {@link
     * #expectedOverkill} on the true max: the compressed roll has the same MEAN
     * as the uniform one, so there the generic model is a fair approximation
     * (documented on {@code DpsCalculator.finishFang}). Capping breaks that
     * equivalence, which is why only this path needs its own recurrence.
     */
    static double cappedFangExpectedOverkill(int trueMaxHit, int cap, int targetHitpoints) {
        if (cap <= 0 || targetHitpoints <= 0 || trueMaxHit <= 0) {
            return 0.0;
        }
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        if (lo <= 0) {
            // The shrunk range still touches 0, so the standard capped
            // distribution (with its "rolled 0 -> 1" correction) is the model —
            // same fallback as the capped-fang average.
            return cappedExpectedOverkill(hi, cap, targetHitpoints);
        }
        int top = Math.min(cap, hi);
        double n = hi - lo + 1.0;
        double[] p = new double[top + 1];
        if (cap <= lo) {
            p[cap] = 1.0; // every result of the roll exceeds the cap
        } else {
            for (int d = lo; d <= top - 1; d++) {
                p[d] = 1.0 / n;
            }
            p[top] = (hi - top + 1.0) / n;
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d <= top; d++) {
                if (p[d] == 0.0) {
                    continue;
                }
                sum += p[d] * (d >= h ? (d - h) : over[h - d]);
            }
            over[h] = sum;
        }
        return over[targetHitpoints];
    }

    /**
     * Expected overkill for Osmumten's fang against a target that RE-ROLLS
     * each hitsplat above a cap — the same per-attack distribution as
     * {@link #rerolledFangAverageDamagePerAttack}, so this cannot disagree
     * with it (that was precisely the earlier defect this PR was bitten by
     * once already: an average migrated onto a new distribution while
     * overkill silently stayed on the old one).
     *
     * <p>Mirrors {@link #rerolledFangAverageDamagePerAttack}'s branches
     * exactly, each delegating to whichever EXACT overkill model matches
     * that branch's distribution:
     * <pre>
     * lo &lt;= 0  : expectedOverkill(min(hi, cap), ...)   — degenerate range touches 0, generic equivalence applies
     * cap &gt;= hi: expectedOverkill(trueMaxHit, ...)      — cap can never bind; same approximation tier as the uncapped fang path
     * cap &lt; lo : uniform 0..cap, no bump                — every result re-rolls
     * else     : the exact "kept lo..cap plus re-rolled share" mixture
     * </pre>
     * The last two build the per-attack probability array explicitly (see
     * {@link #overkillFromExplicitDistribution}) rather than reusing {@link
     * #expectedOverkill}/{@link #cappedExpectedOverkill}, because BOTH of
     * those bake in the ordinary "rolled 0 is bumped to 1" convention, which
     * {@link #rerolledFangAverageDamagePerAttack} does not carry (a
     * re-rolled fang hitsplat of 0 is a genuine, undisguised result).
     */
    static double rerolledFangExpectedOverkill(int trueMaxHit, int cap, int targetHitpoints) {
        if (cap <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        if (lo <= 0) {
            return expectedOverkill(Math.min(hi, cap), targetHitpoints);
        }
        if (cap >= hi) {
            return expectedOverkill(trueMaxHit, targetHitpoints);
        }
        if (cap < lo) {
            double[] p = new double[cap + 1];
            double share = 1.0 / (cap + 1);
            for (int d = 0; d <= cap; d++) {
                p[d] = share;
            }
            return overkillFromExplicitDistribution(p, cap, targetHitpoints);
        }
        double w = 1.0 / (hi - lo + 1.0);
        double rerollShare = (hi - cap) * w / (cap + 1.0);
        double[] p = new double[cap + 1];
        for (int d = 0; d <= cap; d++) {
            p[d] = rerollShare;
        }
        for (int d = lo; d <= cap; d++) {
            p[d] += w;
        }
        return overkillFromExplicitDistribution(p, cap, targetHitpoints);
    }

    /**
     * Exact overkill DP over an arbitrary explicit {@code 0..cap} probability
     * array — unlike {@link #expectedOverkill}/{@link #cappedExpectedOverkill},
     * this does NOT assume the ordinary "rolled 0 is bumped to 1" damage-roll
     * convention, since {@code p[0]} can be genuinely positive here (a
     * re-rolled fang hitsplat of 0 is a real result, not folded into 1).
     *
     * <p>A landed hit of value 0 leaves the remaining-HP state unchanged —
     * exactly like a miss — but unlike a miss (which never enters this
     * recursion at all; see {@link #expectedOverkill}'s Javadoc) it DOES
     * carry a share of the "hit landed" probability mass, so it cannot simply
     * be dropped. It makes {@code over[h]} reference itself:
     * <pre>
     * over[h] = p[0]*over[h] + sum_(d=1)^(cap) p[d] * (d &gt;= h ? d-h : over[h-d])
     * </pre>
     * which rearranges to dividing by {@code (1 - p[0])}, the probability the
     * damage roll produced a real, HP-reducing result.
     */
    private static double overkillFromExplicitDistribution(double[] p, int cap, int targetHitpoints) {
        double retain = 1.0 - p[0];
        if (retain <= 0.0) {
            return 0.0; // every result is 0 -- can never contribute overkill
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d <= cap; d++) {
                sum += p[d] * (d >= h ? (d - h) : over[h - d]);
            }
            over[h] = sum / retain;
        }
        return over[targetHitpoints];
    }
}
