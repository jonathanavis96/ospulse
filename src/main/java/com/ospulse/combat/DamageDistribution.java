package com.ospulse.combat;

/**
 * Per-attack damage distribution arithmetic for the OSRS Wiki DPS calculator:
 * average damage per attack (uncapped, capped, and re-rolled cap variants),
 * the Osmumten's fang compressed-roll equivalents, and the expected-overkill
 * dynamic-programme family built on those same distributions. Split out of
 * {@link CombatMath}, which keeps only the effective-level/roll/hit-chance
 * arithmetic the distributions here consume as inputs.
 *
 * <p>Average damage and overkill are deliberately kept together in one class:
 * every capped/re-rolled variant pairs an {@code averageXxx} method with an
 * {@code xxxExpectedOverkill} method built on the SAME underlying
 * distribution (see e.g. {@link #rerolledHitsplatDistribution}), so the two
 * families cannot silently drift onto different models the way an earlier
 * fang defect did — see the individual method Javadoc for that history.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Damage_per_second/Melee">DPS/Melee</a>
 * @see <a href="https://oldschool.runescape.wiki/w/Osmumten%27s_fang">Osmumten's fang</a>
 */
final class DamageDistribution {
    private DamageDistribution() {
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

    /**
     * Average damage per attack when a monster RE-ROLLS each hitsplat above a
     * cap back into {@code 0..cap} (e.g. Verzik Vitur phase 1), as opposed to
     * {@link #cappedAverageDamagePerAttack}'s clamp.
     *
     * <p><b>This is NOT simply {@link #averageDamagePerAttack} fed the cap as
     * maxHit.</b> That formula's "+1/(maxHit+1)" term bakes in the ordinary
     * damage-roll convention — a rolled 0 on a successful hit is bumped to 1 —
     * which is true of a genuine {@code 0..cap} weapon roll but NOT of a
     * re-rolled hitsplat: the monster re-rolls a value that has ALREADY had
     * that bump applied (the bump belongs to the original {@code 0..M} roll,
     * which happens first; the re-roll into {@code 0..cap} is a separate,
     * later step that produces its own genuine zero). Feeding the cap into
     * {@link #averageDamagePerAttack} therefore reports a mean of
     * {@code cap/2 + 1/(cap+1)} — at Verzik's ranged/magic cap of 3 that is
     * 1.75, a 14.8% overstatement of the true ~1.524.
     *
     * <p>Derivation: a roll of 0 or 1 both count as a landed 1 (weight
     * {@code 2/(M+1)}), a roll of {@code 2..C} counts as itself (weight
     * {@code 1/(M+1)} each), and each of the {@code M-C} rolls above {@code C}
     * re-rolls uniformly into {@code 0..C} (weight {@code 1/(M+1)} split
     * {@code (M-C)/((M+1)(C+1))} onto every value in {@code 0..C}, INCLUDING a
     * genuine, un-bumped 0). Summing {@code d * P(d)} over the resulting
     * distribution collapses — verified in exact rational arithmetic against a
     * direct enumeration for several {@code (M, C)} pairs — to the clean
     * identity:
     * <pre>
     * hitChance * ( cap / 2 + 1 / (uncappedMaxHit + 1) )
     * </pre>
     * Note the correction term is {@code 1/(M+1)} (the ORIGINAL roll's range),
     * NOT {@code 1/(cap+1)} — a plain {@code C/2} is closer than the naive
     * {@link #averageDamagePerAttack} call but still drops the bump the kept
     * values (0 and 1, when they land within {@code 0..cap} directly) carry.
     *
     * <p>The closed form {@code cap/2 + 1/(uncappedMaxHit+1)} is only valid for
     * {@code 1 <= cap <= uncappedMaxHit} — nothing is re-rolled once
     * {@code cap >= uncappedMaxHit}, at which point the uncapped maximum
     * applies instead.
     */
    static double rerolledAverageDamagePerAttack(double hitChance, int uncappedMaxHit, int cap) {
        if (cap >= uncappedMaxHit) {
            return averageDamagePerAttack(hitChance, uncappedMaxHit);
        }
        if (cap <= 0) {
            // The identity below assumes cap >= 1 (so the bumped value 1 can
            // legitimately survive as "kept" rather than itself needing a
            // re-roll). At cap == 0 every outcome, including the bumped 1,
            // exceeds the cap and re-rolls into the single value {0}, so the
            // true mean is exactly 0 — matching cappedAverageDamagePerAttack's
            // own cap <= 0 guard.
            return 0.0;
        }
        return hitChance * (cap / 2.0 + 1.0 / (uncappedMaxHit + 1.0));
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
     * lo &lt;= 0  : rerolledAverageDamagePerAttack(hitChance, hi, min(hi, cap)) — degenerate range touches 0; this IS a genuine uniform 0..hi roll, so the GENERIC zero-aware equivalence applies directly
     * cap &gt;= hi: fangAverageDamagePerAttack(hitChance, trueMaxHit)          — cap can never bind
     * cap &lt; lo : hitChance * cap / 2                                        — every result re-rolls into a plain uniform 0..cap
     * else     : hitChance * ( sum_(d=lo)^(cap) d + (hi-cap)*cap/2 ) / (hi-lo+1)
     *            where sum_(d=lo)^(cap) d = (lo+cap)*(cap-lo+1)/2
     * </pre>
     * The middle two branches carry NO correction term at all — a re-rolled
     * fang hitsplat of 0 is a genuine result, never folded into 1, so there is
     * no bump to apply. The {@code lo <= 0} branch is the one exception: it
     * delegates to {@link #rerolledAverageDamagePerAttack}, whose
     * {@code 1/(hi+1)} term is NOT the ordinary bump — it is that same
     * function's own zero-aware correction for the ORIGINAL {@code 0..hi}
     * roll (which this degenerate case genuinely is), not a re-application of
     * it to the re-roll. Calling the plain {@link #averageDamagePerAttack}
     * here instead (as an earlier version of this method did) double-applies
     * the bump to the re-roll's own genuine zero and overstates the mean —
     * exactly the mistake {@link DpsCalculator#finish}'s generic {@code
     * REROLL} branch was fixed for in the same review.
     */
    static double rerolledFangAverageDamagePerAttack(double hitChance, int trueMaxHit, int cap) {
        int shrink = trueMaxHit * 3 / 20;
        int lo = shrink;
        int hi = trueMaxHit - shrink;
        if (lo <= 0) {
            // The shrunk range touches 0, so this IS a genuine uniform 0..hi roll
            // re-rolled at cap — the generic zero-aware equivalence applies
            // directly. NOT averageDamagePerAttack(hitChance, min(hi, cap)): that
            // bakes in the ordinary "rolled 0 becomes 1" bump a second time, on
            // the re-roll's own genuine zero (the same mistake fixed in
            // DpsCalculator.finish's REROLL branch).
            return rerolledAverageDamagePerAttack(hitChance, hi, Math.min(hi, cap));
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
        double[] p = cappedHitsplatDistribution(uncappedMaxHit, cap);
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
     * Expected overkill when a monster RE-ROLLS each hitsplat above a cap back
     * into {@code 0..cap} — the SAME per-attack distribution {@link
     * #rerolledAverageDamagePerAttack} uses, so this cannot disagree with it
     * (that was precisely the earlier fang defect this PR was bitten by once
     * already: an average migrated onto a new distribution while overkill
     * silently stayed on the old one — the same over-general "REROLL is just
     * {@link #expectedOverkill} fed the cap" mistake for the generic path,
     * caught in the SAME review as the fang one).
     *
     * <p>Builds the explicit distribution described on {@link
     * #rerolledAverageDamagePerAttack} — a kept 0/1 both bump to 1 (weight
     * {@code 2/(M+1)}), a kept {@code 2..cap} counts as itself (weight
     * {@code 1/(M+1)} each), and every value in {@code 0..cap} — INCLUDING a
     * genuine, un-bumped 0 — additionally gains a re-rolled share {@code
     * (M-cap)/((M+1)(cap+1))} — then runs it through {@link
     * #overkillFromExplicitDistribution}, which is already zero-aware (built
     * for exactly this: a re-rolled hitsplat of 0 is a real, HP-neutral
     * result, not folded into 1 the way {@link #expectedOverkill}/{@link
     * #cappedExpectedOverkill} assume). Setting {@code cap >= uncappedMaxHit}
     * delegates to {@link #expectedOverkill} (the cap can never bind).
     */
    static double rerolledExpectedOverkill(int uncappedMaxHit, int cap, int targetHitpoints) {
        if (cap >= uncappedMaxHit) {
            return expectedOverkill(uncappedMaxHit, targetHitpoints);
        }
        if (cap <= 0 || targetHitpoints <= 0) {
            return 0.0;
        }
        double[] p = rerolledHitsplatDistribution(uncappedMaxHit, cap);
        return overkillFromExplicitDistribution(p, cap, targetHitpoints);
    }

    /**
     * The displayed first-hitsplat distribution for a monster that RE-ROLLS
     * each hit above a cap back into {@code 0..cap} — the single source of
     * truth for that distribution's shape, extracted here so {@link
     * #rerolledExpectedOverkill} and every {@code TwinflameSecondHit}
     * REROLL-mode method consume the SAME array and can never independently
     * drift apart (see this class's and {@code TwinflameSecondHit}'s Javadoc
     * for why that consistency matters — average and overkill/second-hit
     * disagreeing with each other is exactly the defect class this codebase
     * has already been bitten by once on this feature).
     *
     * <p>Returns a {@code double[cap + 1]} indexed by displayed damage
     * {@code v}: {@code p[0]} is the re-roll-only share (a genuine, un-bumped
     * zero), {@code p[1]} additionally carries the ordinary "rolled 0 becomes
     * 1" bump, and {@code p[2..cap]} are the plain kept share plus their own
     * re-roll share — see {@link #rerolledAverageDamagePerAttack}'s Javadoc
     * for the full derivation. Callers must ensure {@code 1 <= cap <
     * uncappedMaxHit} themselves (mirroring every existing caller's own
     * boundary guards) — this method does not re-check either bound.
     */
    static double[] rerolledHitsplatDistribution(int uncappedMaxHit, int cap) {
        double denom = uncappedMaxHit + 1.0;
        double rerollShare = (uncappedMaxHit - cap) / (denom * (cap + 1.0));
        double[] p = new double[cap + 1];
        p[0] = rerollShare;
        p[1] = 2.0 / denom + rerollShare;
        for (int d = 2; d <= cap; d++) {
            p[d] = 1.0 / denom + rerollShare;
        }
        return p;
    }

    /**
     * The displayed first-hitsplat distribution for a monster that CLAMPS
     * each hit above a cap onto the cap itself — the single source of truth
     * for that distribution's shape, extracted here (from what was
     * previously an inline array build in {@link #cappedExpectedOverkill})
     * so every caller (that method, and any multi-hit mechanic that needs to
     * convolve several capped hits together, e.g. a decaying-cascade or
     * dual-hit weapon against a capped target) consumes the SAME array and
     * cannot silently drift apart, mirroring why {@link
     * #rerolledHitsplatDistribution} exists as its own method rather than
     * being inlined at each call site.
     *
     * <p>Returns a {@code double[cap + 1]} indexed by displayed damage
     * {@code v}: {@code p[1]} carries the ordinary "rolled 0 becomes 1"
     * bump ({@code 2/(uncappedMaxHit+1)}), {@code p[2..cap-1]} are the plain
     * kept share, and {@code p[cap]} additionally piles on every raw result
     * from {@code cap} to {@code uncappedMaxHit} (the excess probability
     * mass a clamp — unlike a re-roll — leaves stacked on the cap itself).
     * Callers must ensure {@code 1 <= cap < uncappedMaxHit} themselves
     * (mirroring {@link #rerolledHitsplatDistribution}'s own contract) —
     * this method does not re-check either bound.
     */
    static double[] cappedHitsplatDistribution(int uncappedMaxHit, int cap) {
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
        return p;
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
     * lo &lt;= 0  : rerolledExpectedOverkill(hi, min(hi, cap), ...) — degenerate range touches 0; this IS a genuine uniform 0..hi roll, so the GENERIC zero-aware equivalence applies directly (NOT expectedOverkill, which bakes in the bump a second time)
     * cap &gt;= hi: expectedOverkill(trueMaxHit, ...)               — cap can never bind; same approximation tier as the uncapped fang path
     * cap &lt; lo : uniform 0..cap, no bump                         — every result re-rolls
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
            // Same reasoning as the average's degenerate branch: this is a
            // genuine uniform 0..hi roll re-rolled at cap, so the generic
            // zero-aware equivalence applies directly — NOT expectedOverkill,
            // which bakes in the ordinary bump a second time.
            return rerolledExpectedOverkill(hi, Math.min(hi, cap), targetHitpoints);
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
        int[] identity = new int[cap + 1];
        for (int d = 0; d <= cap; d++) {
            identity[d] = d;
        }
        return overkillFromExplicitDistribution(p, identity, targetHitpoints);
    }

    /**
     * As {@link #overkillFromExplicitDistribution(double[], int, int)}, but
     * for a caller whose landed value {@code v} removes some OTHER amount of
     * hitpoints than {@code v} itself — e.g. {@code TwinflameSecondHit}'s
     * combined-hitsplat model, where a displayed first hit of {@code v}
     * actually removes {@code v + floor(0.4*v)}. {@code amount[v]} gives that
     * removed quantity for probability index {@code v}; {@code amount[0]} is
     * never read (index 0 is always excluded from the recurrence, whatever
     * it would be, since a landed 0 changes nothing).
     *
     * <p>Package-private (not {@code private}) specifically so {@code
     * TwinflameSecondHit} can reuse the SAME zero-mass renormalisation this
     * class already uses for its own REROLL overkill, rather than that class
     * growing a second, independently-written copy of the same {@code
     * (1 - p[0])} correction that could silently drift out of sync with this
     * one.
     *
     * <p><b>Every {@code amount[d]} for {@code d >= 1} must itself be
     * {@code >= 1}.</b> If some other index also mapped to a zero removal,
     * that index's mass would need folding into the {@code (1 - p[0])}
     * denominator too, or {@code over[h]} recurses on itself again — the
     * exact self-reference bug this whole renormalisation exists to avoid.
     */
    static double overkillFromExplicitDistribution(double[] p, int[] amount, int targetHitpoints) {
        double retain = 1.0 - p[0];
        if (retain <= 0.0) {
            return 0.0; // every result is 0 -- can never contribute overkill
        }
        double[] over = new double[targetHitpoints + 1];
        for (int h = 1; h <= targetHitpoints; h++) {
            double sum = 0.0;
            for (int d = 1; d < p.length; d++) {
                int removed = amount[d];
                sum += p[d] * (removed >= h ? (removed - h) : over[h - removed]);
            }
            over[h] = sum / retain;
        }
        return over[targetHitpoints];
    }
}
