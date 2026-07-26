package com.ospulse.combat;

/**
 * Entry point: computes max hit / accuracy / DPS for one gear+player+style
 * setup against one {@link Monster}, per the OSRS Wiki DPS calculator
 * formulas (see per-method Javadoc in {@link CombatMath}/{@link DamageDistribution} for citations).
 * <p>
 * Tier-A effects modelled (each isolated below, with an "applies when"
 * predicate, per the design doc): offensive prayers, best-potion boosts,
 * Slayer helm(i)/black mask(i) on-task bonus, Salve amulet variants vs
 * Undead, Void/Elite Void. Tier-B effects: demonbane weapons (melee sword
 * line + Burning claws + ranged Scorching bow; accuracy/damage split, applied
 * as the weapon's own step stacking with the salve/slayer slot, with the
 * ranged max hit folding additively into an on-task imbued helm — see
 * computeRanged; a per-monster demonbane RESISTANCE, e.g. Duke Sucellus's
 * 30%, scales the bonus's excess-over-1 down — see
 * resistedDemonbaneFraction), Dragon
 * Hunter lance/crossbow/wand vs dragons and the Twisted bow's vs-target-magic
 * scaling (separate multiplicative steps — they stack with the target
 * slot, with the same ranged-max-hit additive fold for the DHCB),
 * powered-staff built-in spells and real {@link Spell} max hits for
 * autocast, and elemental weakness (a monster's {@code weaknessElement}/
 * {@code weaknessSeverity} adds straight into the magic-damage percent when
 * the cast {@link Spell}'s {@link Spell.Element} matches - see
 * computeMagic), and Osmumten's fang's two STAB-only passives (double
 * accuracy roll + compressed 15%-85%-of-max-hit damage roll — its own
 * hitChance/avgDamage formulas replace the generic ones entirely rather than
 * being a multiplicative step on maxHit/attackRoll — see computeMelee /
 * finishFang / {@link DamageDistribution#fangHitChance} /
 * {@link DamageDistribution#fangAverageDamagePerAttack}). Six further
 * multi-hit/conditional-bonus mechanics (design spec §9, each behind its own
 * "applies when" predicate): the Scythe of Vitur family's target-size-scaled
 * 1-3x cascade (own bespoke hitChance/average pair, see {@link
 * ScytheCascade}/finishScythe-equivalent bypass in computeMelee), Colossal
 * blade's flat {@code +2*min(size,5)} max hit, the Keris partisan family's
 * vs-Kalphite/Scarabite +33% damage(+accuracy for "of breaching")/1-in-51
 * triple-damage roll (see {@link KerisPartisan}/{@link KerisTripleRoll}),
 * the charged Tonalztics of Ralos's two full independent damage rolls (see
 * {@link TonalzticsDualHit}), the revenant weapons' (Craw's bow/Viggora's
 * chainmace/Thammaron's sceptre) +50% accuracy/damage vs a Wilderness target
 * — gated on {@link Monster#isWildernessTarget()}, true for both genuinely
 * Wilderness-exclusive monsters ({@link WildernessMonsterRepository}) and a
 * player-selected synthetic "(Wilderness)" twin of a both-locations monster
 * ({@link WildernessVariantMonsterRepository}; see {@link
 * Monster#lookupName()} for how every OTHER lookup resolves such a twin back
 * to its real underlying monster) — and the
 * Crystal armour set + Crystal bow/Bow of Faerdhinen's +15% damage/+30%
 * accuracy (see {@code EquipmentStats#crystalSetBonusActive}). Remaining
 * unmodelled effects (special attacks, Avarice, Tumeken's 3x gear
 * multiplier, ...) are simply not applied — extend this class (and {@link
 * CombatMath}/{@link DamageDistribution}) to add them, gated behind their
 * own "applies when" predicate.
 *
 * <p>Per-target damage-magnitude effects (a curated {@link
 * MonsterCombatRequirement}, resolved once per compute call from {@code
 * target.lookupName()} — {@link Monster#lookupName()}, NOT {@link
 * Monster#name()}, so a synthetic Wilderness-variant target resolves the
 * SAME requirement its real underlying monster would — via {@link
 * MonsterCombatRequirementRepository} by the
 * overloads below that don't take one explicitly, or supplied directly by a
 * caller — such as {@code GearOptimizer} — that already resolved its own,
 * which then takes priority instead of a second, independent lookup here; see
 * {@link #applyTargetDamageRules} for how "not supplied" and "supplied null"
 * are told apart): a {@link
 * MonsterCombatRequirement.Type#DAMAGE_PENALTY} multiplies max hit for an
 * off-style weapon (e.g. Corporeal Beast's half-damage stab), and a {@link
 * MonsterCombatRequirement.Type#DAMAGE_CAP} ceilings max hit, optionally per
 * {@link CombatStyle} (e.g. Verzik Vitur phase 1: melee 10, ranged/magic 3)
 * and optionally exempting a specific weapon entirely (e.g. Dawnbringer) —
 * both applied as the final step immediately before {@code finish}/{@code
 * finishFang}; see {@link TargetDamageRule}. A cap's {@link
 * MonsterCombatRequirement.CapMode} decides HOW it applies: {@code CLAMP}
 * piles the roll's excess probability mass onto the cap itself (The
 * Hueycoatl's tail), {@code REROLL} re-rolls a too-high hit uniformly into
 * {@code 0..cap} (Verzik). Even for the GENERIC 0..maxHit roll this is NOT
 * simply {@link DamageDistribution#averageDamagePerAttack}/{@link
 * DamageDistribution#expectedOverkill} fed the cap as maxHit — that shape equivalence
 * (a re-roll into {@code 0..cap} of a uniform {@code 0..M} roll is exactly a
 * uniform {@code 0..cap} roll) is real, but those two methods ALSO bake in
 * the ordinary "rolled 0 becomes 1" bump, which belongs to the ORIGINAL roll
 * and does not apply a second time to the re-roll's own genuine zero —
 * {@link DamageDistribution#rerolledAverageDamagePerAttack}/{@code
 * rerolledExpectedOverkill} get this right; see {@link #finish}. Osmumten's
 * fang needs its own re-rolled formulas for a different reason (its
 * compressed roll isn't 0..M to begin with) — see {@link #finishFang} and
 * {@link DamageDistribution#rerolledFangAverageDamagePerAttack}.
 */
public final class DpsCalculator {
    private DpsCalculator() {
    }

    /**
     * Legacy/simple entry point: magic uses {@code baseSpellMaxHit} verbatim
     * with the weapon's own attack speed (no powered-staff or spell logic).
     * Prefer {@link #compute(EquipmentStats, PlayerCombat, CombatStyle, Monster, Spell)}
     * for magic with a real spell, which also auto-detects powered staves.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, int baseSpellMaxHit) {
        return compute(gear, player, style, target, baseSpellMaxHit, UNKNOWN_WEAPON_ID);
    }

    /**
     * As above, plus the worn weapon's item id — needed to resolve a
     * per-target {@link MonsterCombatRequirement.Type#DAMAGE_PENALTY} (see
     * {@link TargetDamageRule#damageMultiplierFor}). Callers that do not know
     * the weapon id may use the no-arg overload; an unknown id never matches
     * a curated exemption list, so any penalty for the target still applies
     * (the conservative default).
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, int baseSpellMaxHit, int weaponId) {
        return compute(gear, player, style, target, baseSpellMaxHit, weaponId, resolveRequirement(target));
    }

    /**
     * As {@link #compute(EquipmentStats, PlayerCombat, CombatStyle, Monster, int)},
     * but the caller supplies the target's already-resolved {@link
     * MonsterCombatRequirement} instead of it being looked up here — see
     * {@link #applyTargetDamageRules} for why a caller-supplied {@code null}
     * is never re-resolved.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, int baseSpellMaxHit, MonsterCombatRequirement requirement) {
        return compute(gear, player, style, target, baseSpellMaxHit, UNKNOWN_WEAPON_ID, requirement);
    }

    /**
     * As {@link #compute(EquipmentStats, PlayerCombat, CombatStyle, Monster, int, int)},
     * but the caller supplies the target's already-resolved {@link
     * MonsterCombatRequirement} instead of it being looked up here — see
     * {@link #applyTargetDamageRules} for why a caller-supplied {@code null}
     * is never re-resolved.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, int baseSpellMaxHit, int weaponId,
                                     MonsterCombatRequirement requirement) {
        if (style == CombatStyle.MAGIC) {
            return computeMagic(gear, player, target, baseSpellMaxHit, gear.weaponSpeedTicks(), false, null, weaponId,
                    requirement, false);
        }
        return computeNonMagic(gear, player, style, target, weaponId, requirement);
    }

    /**
     * Spell-aware entry point. For {@link CombatStyle#MAGIC}: a worn powered
     * staff (detected on {@code gear}) takes precedence — its built-in spell's
     * max hit is derived from the (boosted) Magic level at the weapon's own
     * speed; otherwise the given {@link Spell}'s base max hit is cast at a
     * weapon-aware speed (see {@link MagicCastSpeed}: the Twinflame staff and
     * Harmonised nightmare staff override the default {@link
     * Spell#CAST_SPEED_TICKS}). A {@code null} spell with no powered staff
     * yields a zero-damage result rather than a guess.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, Spell spell) {
        return compute(gear, player, style, target, spell, UNKNOWN_WEAPON_ID);
    }

    /**
     * As above, plus the worn weapon's item id — see {@link #compute(EquipmentStats,
     * PlayerCombat, CombatStyle, Monster, int, int)} for why it is needed.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, Spell spell, int weaponId) {
        return compute(gear, player, style, target, spell, weaponId, resolveRequirement(target));
    }

    /**
     * As {@link #compute(EquipmentStats, PlayerCombat, CombatStyle, Monster, Spell)},
     * but the caller supplies the target's already-resolved {@link
     * MonsterCombatRequirement} instead of it being looked up here — see
     * {@link #applyTargetDamageRules} for why a caller-supplied {@code null}
     * is never re-resolved.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, Spell spell, MonsterCombatRequirement requirement) {
        return compute(gear, player, style, target, spell, UNKNOWN_WEAPON_ID, requirement);
    }

    /**
     * As {@link #compute(EquipmentStats, PlayerCombat, CombatStyle, Monster, Spell, int)},
     * but the caller supplies the target's already-resolved {@link
     * MonsterCombatRequirement} instead of it being looked up here — see
     * {@link #applyTargetDamageRules} for why a caller-supplied {@code null}
     * is never re-resolved.
     */
    public static DpsResult compute(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                     Monster target, Spell spell, int weaponId, MonsterCombatRequirement requirement) {
        if (style != CombatStyle.MAGIC) {
            return computeNonMagic(gear, player, style, target, weaponId, requirement);
        }
        if (gear.poweredStaff().applies()) {
            // Base max hit derives from the boosted level inside computeMagic.
            // Powered staves cast their own built-in "spell" (Magic Dart etc.),
            // which is not a real Spell and carries no element - no weakness bonus.
            // Twinflame/Harmonised are not powered staves, so this branch never
            // needs the weapon-aware cast speed or the second-hit passive.
            return computeMagic(gear, player, target, POWERED_STAFF_SENTINEL, gear.weaponSpeedTicks(),
                    gear.poweredStaff().approximate(), null, weaponId, requirement, false);
        }
        int baseMaxHit = spell == null ? 0 : spell.baseMaxHit();
        Spell.Element element = spell == null ? null : spell.element();
        int castSpeedTicks = MagicCastSpeed.ticksFor(gear.twinflameStaff(), gear.harmonisedNightmareStaff(), spell);
        boolean twinflameSecondHit = gear.twinflameStaff() && spell != null && spell.twinflameEligible();
        return computeMagic(gear, player, target, baseMaxHit, castSpeedTicks, false, element, weaponId,
                requirement, twinflameSecondHit);
    }

    /** Sentinel meaning "the worn weapon's item id is not known to this caller". */
    private static final int UNKNOWN_WEAPON_ID = -1;

    /**
     * The repository-default resolution used by every overload that does not
     * take an explicit {@link MonsterCombatRequirement} — exactly what {@link
     * #applyTargetDamageRules} looked up internally before this class started
     * threading the requirement through as a parameter. Kept as its own
     * method so every "resolve it yourself" overload performs the identical
     * lookup, byte-identical to the old behaviour.
     */
    private static MonsterCombatRequirement resolveRequirement(Monster target) {
        return MonsterCombatRequirementRepository.getInstance().forMonster(target.lookupName()).orElse(null);
    }

    private static DpsResult computeNonMagic(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                             Monster target, int weaponId, MonsterCombatRequirement requirement) {
        if (style == CombatStyle.RANGED) {
            return computeRanged(gear, player, target, weaponId, requirement);
        }
        return computeMelee(gear, player, style, target, weaponId, requirement);
    }

    // ---- Melee (STAB/SLASH/CRUSH) -----------------------------------------------------

    private static DpsResult computeMelee(EquipmentStats gear, PlayerCombat player, CombatStyle style,
                                          Monster target, int weaponId, MonsterCombatRequirement requirement) {
        int boostedStr = player.assumeBestPotion()
                ? PotionBoosts.bestMeleeBoostedLevel(player.baseStrength())
                : player.boostedStrength();
        int boostedAtt = player.assumeBestPotion()
                ? PotionBoosts.bestMeleeBoostedLevel(player.baseAttack())
                : player.boostedAttack();

        double prayerStrMult = player.assumeBestPrayer() ? OffensivePrayer.PIETY.meleeStrengthMult()
                : maxOf(player, OffensivePrayer::meleeStrengthMult);
        double prayerAttMult = player.assumeBestPrayer() ? OffensivePrayer.PIETY.meleeAttackMult()
                : maxOf(player, OffensivePrayer::meleeAttackMult);

        int styleBonusStr = player.stance() == Stance.AGGRESSIVE ? 3 : player.stance() == Stance.CONTROLLED ? 1 : 0;
        int styleBonusAtt = player.stance() == Stance.ACCURATE ? 3 : player.stance() == Stance.CONTROLLED ? 1 : 0;

        double voidMult = gear.voidSet().meleeMultiplier();

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(boostedStr, prayerStrMult, styleBonusStr, 8, voidMult);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(boostedAtt, prayerAttMult, styleBonusAtt, 8, voidMult);

        TargetBonus targetGearBonus = salveOrSlayerTargetBonus(style, gear, target, player);

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.str(), targetGearBonus.damage);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.attackBonus(style), targetGearBonus.accuracy);

        // Melee demonbane (Emberlight/Arclight +70% acc+dmg; Silver/Darklight
        // +60% dmg-only): the weapon's OWN vs-demon passive — a SEPARATE
        // multiplicative floor step AFTER the salve/slayer slot, so it STACKS with
        // the on-task slayer helm: floor(floor(base*7/6)*17/10).
        // ⚠️ DO NOT change this to highest-wins. IN-GAME VERIFIED 2026-07-07:
        // Emberlight + slayer helm + Piety + super combat on Cerberus = 62 max hit
        // (matches weirdgloop, which stacks). GearScape shows 52 and is WRONG/
        // unreliable for demon numbers — do not chase GearScape parity here. Some
        // demons (Duke Sucellus) partially resist — see resistedDemonbaneFraction.
        DemonbaneWeapon demonbane = gear.demonbaneWeapon();
        if (target.isDemon() && demonbane.appliesTo(style)) {
            int resist = target.demonbaneResistPercent();
            maxHit = (int) resistedDemonbaneFraction(demonbane.damageMult(), resist).applyFloor(maxHit);
            attackRoll = (int) resistedDemonbaneFraction(demonbane.accuracyMult(), resist).applyFloor(attackRoll);
        }

        // Dragon Hunter lance: the weapon's own vs-dragon passive — a SEPARATE
        // multiplicative floor step, stacking with the salve/slayer slot.
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        if (target.isDragon() && dh.appliesTo(style)) {
            maxHit = (int) dh.damageMult().applyFloor(maxHit);
            attackRoll = (int) dh.accuracyMult().applyFloor(attackRoll);
        }

        // Colossal blade: a flat +2*min(targetSize, 5) max-hit bonus (up to
        // +10), stacking with everything above INCLUDING the slayer/salve
        // slot and demonbane/dragonbane — this is the weapon's own passive,
        // a separate additive term (not a multiplicative floor step), added
        // last so a per-target damage cap (below) applies to the TOTAL,
        // matching how the game's own displayed max hit already includes it.
        if (gear.colossalBlade()) {
            maxHit += 2 * Math.min(target.size(), 5);
        }

        // Keris partisan family: +33% damage vs Kalphites/Scarabites, plus
        // (for "of breaching" only) +33% accuracy — the weapon's own
        // separate multiplicative step(s), stacking with everything above.
        // The family's OTHER passive (a 1/51 chance to triple the landed
        // damage) is a genuinely different distribution and is applied below
        // via KerisTripleRoll, bypassing the generic finish() path.
        KerisPartisan keris = gear.kerisPartisan();
        boolean kerisApplies = keris != KerisPartisan.NONE && target.attributes().contains(MonsterAttribute.KALPHITE);
        if (kerisApplies) {
            maxHit = (int) KerisPartisan.DAMAGE_MULT.applyFloor(maxHit);
            if (keris.hasAccuracyBonus()) {
                attackRoll = (int) KerisPartisan.DAMAGE_MULT.applyFloor(attackRoll);
            }
        }

        // Viggora's chainmace (revenant weapon): +50% accuracy AND damage vs
        // any Wilderness NPC, while charged with ether (assumed — see
        // RevenantWeapon) — the weapon's own separate multiplicative step.
        RevenantWeapon revenant = gear.revenantWeapon();
        if (revenant.appliesTo(style) && target.isWildernessTarget()) {
            maxHit = (int) revenant.damageMult().applyFloor(maxHit);
            attackRoll = (int) revenant.accuracyMult().applyFloor(attackRoll);
        }

        int defenceRoll = CombatMath.npcDefenceRoll(target.defenceLevel(), target.defenceBonus(style));

        // Osmumten's fang (and re-skins/cosmetics): two passives, STAB style
        // only (the double-accuracy-roll passive was restricted to Stab by the
        // 17 Jan 2024 update — see DamageDistribution.fangHitChance/fangAverageDamagePerAttack
        // for the exact formulas + citation). Neither passive changes maxHit or
        // attackRoll themselves — they change how hitChance/avgDamage are
        // derived from them, so this bypasses the generic finish() path.
        boolean fangApplies = gear.osmumtensFang() && style == CombatStyle.STAB;
        // Scythe of Vitur family: hits 1-3x by target size, each hit's max
        // decaying to 50% of the previous (rounded down) — a genuinely
        // different distribution from a single roll, so this too bypasses
        // the generic finish() path entirely; see ScytheCascade.
        boolean scytheApplies = gear.scytheOfVitur();
        TargetDamage damage = applyTargetDamageRules(maxHit, requirement, gear, style, weaponId);
        maxHit = damage.visibleMaxHit();
        if (fangApplies) {
            return finishFang(damage, attackRoll, defenceRoll, gear.weaponSpeedTicks(), target.hitpoints());
        }
        if (scytheApplies) {
            return ScytheCascade.finish(damage.uncapped, damage.cap, damage.mode, target.size(), attackRoll,
                    defenceRoll, gear.weaponSpeedTicks(), target.hitpoints());
        }
        if (kerisApplies) {
            return KerisTripleRoll.finish(damage.uncapped, damage.cap, damage.mode, attackRoll, defenceRoll,
                    gear.weaponSpeedTicks(), target.hitpoints());
        }

        return finish(damage, attackRoll, defenceRoll, gear.weaponSpeedTicks(), target.hitpoints(), false);
    }

    // ---- Ranged -----------------------------------------------------------------------

    private static DpsResult computeRanged(EquipmentStats gear, PlayerCombat player, Monster target, int weaponId,
                                           MonsterCombatRequirement requirement) {
        int boostedRanged = player.assumeBestPotion()
                ? PotionBoosts.bestRangedBoostedLevel(player.baseRanged())
                : player.boostedRanged();

        double prayerAttMult = player.assumeBestPrayer() ? OffensivePrayer.RIGOUR.rangedAttackMult()
                : maxOf(player, OffensivePrayer::rangedAttackMult);
        double prayerStrMult = player.assumeBestPrayer() ? OffensivePrayer.RIGOUR.rangedStrengthMult()
                : maxOf(player, OffensivePrayer::rangedStrengthMult);

        // Ranged has only one meaningful style bonus (Accurate, +3), applied identically
        // to both the effective-attack and effective-strength calculations.
        int styleBonus = player.stance() == Stance.ACCURATE ? 3 : 0;

        // Elite ranged void boosts max hit (+12.5%) more than accuracy (+10%),
        // so the strength and attack sides take DIFFERENT void multipliers.
        int effStr = CombatMath.effectiveMeleeOrRangedLevel(boostedRanged, prayerStrMult, styleBonus, 8,
                gear.voidSet().rangedStrengthMultiplier());
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(boostedRanged, prayerAttMult, styleBonus, 8,
                gear.voidSet().rangedAccuracyMultiplier());

        TargetBonus targetGearBonus = salveOrSlayerTargetBonus(CombatStyle.RANGED, gear, target, player);

        DemonbaneWeapon demonbane = gear.demonbaneWeapon();
        boolean demonbaneApplies = target.isDemon() && demonbane.appliesTo(CombatStyle.RANGED);
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        boolean dragonbaneApplies = target.isDragon() && dh.appliesTo(CombatStyle.RANGED);

        // MAX HIT ONLY (per the wiki DPS calc's "these are additive with slayer
        // only"): when the on-task IMBUED slayer helm/black mask wins the target
        // slot, the ranged demonbane (Scorching bow +30% -> +6/20) and dragonbane
        // (DHCB +25% -> +5/20) damage bonuses fold ADDITIVELY into its 23/20 step
        // — ONE floor of (23+5+6)/20 — instead of applying as separate steps.
        // E.g. Cerberus on-task with a Scorching bow: floor(40 * 29/20) = 58
        // (the wiki's "total damage boost of 45%"), not floor(46 * 13/10) = 59.
        Fraction damageSlot = targetGearBonus.damage;
        boolean foldIntoSlayer = targetGearBonus.imbuedSlayer && (demonbaneApplies || dragonbaneApplies);
        if (foldIntoSlayer) {
            long numerator = 23
                    + (dragonbaneApplies ? additiveTwentieths(dh.damageMult()) : 0)
                    + (demonbaneApplies ? additiveTwentieths(demonbane.damageMult()) : 0);
            damageSlot = new Fraction(numerator, 20);
        }

        int maxHit = CombatMath.meleeOrRangedMaxHit(effStr, gear.rstr(), damageSlot);
        int attackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, gear.arange(), targetGearBonus.accuracy);

        // Dragon Hunter crossbow — the accuracy side is ALWAYS its own separate
        // multiplicative step; the damage side only when not folded above.
        if (dragonbaneApplies) {
            if (!foldIntoSlayer) {
                maxHit = (int) dh.damageMult().applyFloor(maxHit);
            }
            attackRoll = (int) dh.accuracyMult().applyFloor(attackRoll);
        }

        // Scorching bow (ranged demonbane) — same shape as dragonbane above.
        // Per-monster demonbane resistance (Duke Sucellus) is applied to the
        // separate-step accuracy roll and to the damage step when it is NOT
        // folded into the slayer-helm slot above; a resisted value folded
        // additively into that slot is not documented by the wiki calc, so we
        // do not attempt to fold a resisted bonus (out of scope — no known
        // demon combines on-task-imbued-helm ranged demonbane with resistance).
        if (demonbaneApplies) {
            int resist = target.demonbaneResistPercent();
            if (!foldIntoSlayer) {
                maxHit = (int) resistedDemonbaneFraction(demonbane.damageMult(), resist).applyFloor(maxHit);
            }
            attackRoll = (int) resistedDemonbaneFraction(demonbane.accuracyMult(), resist).applyFloor(attackRoll);
        }

        // Twisted bow: the weapon's own scaling with the TARGET's magic level —
        // also its own multiplicative step (stacks with everything above).
        if (gear.twistedBow()) {
            int accPct = CombatMath.twistedBowAccuracyPercent(target.magicLevel());
            int dmgPct = CombatMath.twistedBowDamagePercent(target.magicLevel());
            attackRoll = (int) new Fraction(accPct, 100).applyFloor(attackRoll);
            maxHit = (int) new Fraction(dmgPct, 100).applyFloor(maxHit);
        }

        // Craw's bow (revenant weapon): +50% accuracy AND damage vs any
        // Wilderness NPC, while charged with ether (assumed — see
        // RevenantWeapon) — the weapon's own separate multiplicative step,
        // stacking with everything above (including a folded slayer helm).
        RevenantWeapon revenant = gear.revenantWeapon();
        if (revenant.appliesTo(CombatStyle.RANGED) && target.isWildernessTarget()) {
            maxHit = (int) revenant.damageMult().applyFloor(maxHit);
            attackRoll = (int) revenant.accuracyMult().applyFloor(attackRoll);
        }

        // Crystal armour set (full ACTIVE helm+body+legs) + an ACTIVE Crystal
        // bow/Bow of Faerdhinen: a conditional weapon+armour combo, not baked
        // into per-piece stats — +15% damage/+30% accuracy, its own separate
        // multiplicative step (see EquipmentStats#crystalSetBonusActive /
        // GearVariants for the active/inactive-piece distinction).
        if (gear.crystalSetBonusActive()) {
            maxHit = (int) new Fraction(23, 20).applyFloor(maxHit); // +15%
            attackRoll = (int) new Fraction(13, 10).applyFloor(attackRoll); // +30%
        }

        int defenceRoll = CombatMath.npcDefenceRoll(target.defenceLevel(), target.drange());

        // Rapid attack style reduces the weapon's attack speed by 1 tick.
        int weaponSpeedTicks = gear.weaponSpeedTicks() - (player.stance() == Stance.RAPID ? 1 : 0);
        weaponSpeedTicks = Math.max(1, weaponSpeedTicks);

        TargetDamage damage = applyTargetDamageRules(maxHit, requirement, gear, CombatStyle.RANGED, weaponId);
        maxHit = damage.visibleMaxHit();
        // Charged Tonalztics of Ralos: two full, independent damage rolls per
        // attack (neither halved) — bypasses the generic finish() entirely,
        // same shape as finishFang/finishTwinflame; see TonalzticsDualHit.
        if (gear.tonalzticsOfRalosCharged()) {
            return TonalzticsDualHit.finish(damage.uncapped, damage.cap, damage.mode, attackRoll, defenceRoll,
                    weaponSpeedTicks, target.hitpoints());
        }
        return finish(damage, attackRoll, defenceRoll, weaponSpeedTicks, target.hitpoints(), false);
    }

    // ---- Magic --------------------------------------------------------------------------

    /** Marker for "derive the base max hit from the worn powered staff at the boosted Magic level". */
    private static final int POWERED_STAFF_SENTINEL = -1;

    /**
     * The Magic-level boost {@link PlayerCombat#assumeBestPotion()} applies for
     * the given {@link PlayerCombat#magicPotionVariant()} — see
     * {@link CombatIcons.BoostPotion} / {@link PotionBoosts}. Only the three
     * magic-boosting variants are meaningful here; a non-magic variant (or
     * {@code null}) falls back to the Imbued heart formula.
     */
    private static int magicPotionBoostedLevel(CombatIcons.BoostPotion variant, int baseLevel) {
        if (variant == CombatIcons.BoostPotion.SATURATED_HEART) {
            return PotionBoosts.saturatedHeartBoostedLevel(baseLevel);
        }
        if (variant == CombatIcons.BoostPotion.ANCIENT_BREW) {
            return PotionBoosts.ancientBrewBoostedLevel(baseLevel);
        }
        return PotionBoosts.imbuedHeartBoostedLevel(baseLevel);
    }

    private static DpsResult computeMagic(EquipmentStats gear, PlayerCombat player, Monster target,
                                          int baseSpellMaxHit, int castSpeedTicks, boolean approximate,
                                          Spell.Element spellElement, int weaponId,
                                          MonsterCombatRequirement requirement, boolean twinflameSecondHit) {
        int boostedMagic = player.assumeBestPotion()
                ? magicPotionBoostedLevel(player.magicPotionVariant(), player.baseMagic())
                : player.boostedMagic();

        if (baseSpellMaxHit == POWERED_STAFF_SENTINEL) {
            // Powered staff: built-in spell max hit scales with the boosted level.
            baseSpellMaxHit = gear.poweredStaff().maxHitAt(boostedMagic);
        }

        double prayerAccMult = player.assumeBestPrayer() ? OffensivePrayer.AUGURY.magicAccuracyMult()
                : maxOf(player, OffensivePrayer::magicAccuracyMult);
        double prayerDamagePercent = player.assumeBestPrayer() ? OffensivePrayer.AUGURY.magicDamagePercent()
                : maxOf(player, OffensivePrayer::magicDamagePercent);

        int styleBonus = player.stance() == Stance.ACCURATE ? 3 : player.stance() == Stance.LONGRANGE ? 1 : 0;
        double voidMult = gear.voidSet().magicMultiplier();

        int effMagic = CombatMath.effectiveMagicLevel(boostedMagic, prayerAccMult, styleBonus, voidMult);

        boolean salveVsUndead = target.isUndead() && gear.salveType() != SalveType.NONE;
        boolean slayerOnTaskImbued = player.onSlayerTask() && gear.slayerHeadgear().isImbued();
        boolean magicAccuracyBonusApplies = (salveVsUndead && gear.salveType().isImbued()) || slayerOnTaskImbued;
        Fraction accuracyTargetBonus = magicAccuracyBonusApplies ? new Fraction(23, 20) : Fraction.ONE;

        int accuracyRoll = CombatMath.magicAccuracyRoll(effMagic, gear.amagic(), accuracyTargetBonus);
        int defenceRoll = CombatMath.npcDefenceRoll(target.magicLevel(), target.dmagic());

        // Elemental weakness (e.g. frost dragons vs Fire spells): a per-monster
        // percent, added straight into the magic-damage percent when the cast
        // spell's element matches the monster's weakness element - matching
        // weirdgloop/osrs-dps-calc semantics. Spells with no element (Iban
        // Blast, Magic Dart/powered staves, god spells, all Ancient Magicks -
        // spellElement is null for those) never get this bonus, and a monster
        // with no weakness (weaknessElement() null) never grants it.
        // It is NOT folded into the magic-damage percent below; it is a separate
        // additive term applied last (see the elemental-weakness step at the end).
        boolean weaknessApplies = spellElement != null
                && target.weaknessElement() != null
                && target.weaknessElement().equals(spellElement.name());

        double totalDamagePercent = gear.mdmg()
                + gear.voidSet().eliteMagicDamageBonusPercent()
                + (target.isUndead() ? gear.salveType().magicDamagePercent() : 0.0)
                + prayerDamagePercent;
        int primaryDamage = CombatMath.magicPrimaryDamage(baseSpellMaxHit, totalDamagePercent);

        // Slayer on-task +15% never stacks with an active Salve bonus (per wiki).
        boolean slayerPreHitRollApplies = slayerOnTaskImbued && !salveVsUndead;
        int maxHit = CombatMath.magicPreHitRoll(primaryDamage, slayerPreHitRollApplies);

        // Dragon hunter wand — the weapon's own vs-dragon passive, a separate
        // multiplicative floor step stacking with the salve/slayer slot (see computeRanged).
        DragonHunterWeapon dh = gear.dragonHunterWeapon();
        if (target.isDragon() && dh.appliesTo(CombatStyle.MAGIC)) {
            maxHit = (int) dh.damageMult().applyFloor(maxHit);
            accuracyRoll = (int) dh.accuracyMult().applyFloor(accuracyRoll);
        }

        // Elemental weakness — the FINAL damage modifier: a separate additive term,
        // floor(baseSpellMaxHit * severity%), computed from the base spell max hit
        // and floored independently, then added on. This is weirdgloop/osrs-dps-calc's
        // exact stage (maxHit += trunc(baseMax * severity/100)) and the OSRS Wiki
        // "Maximum magic hit" (two floors, not one — folding it into the damage
        // percent above over-/under-shoots by 1 in ~12% of gear/level combos).
        if (weaknessApplies) {
            maxHit += CombatMath.elementalWeaknessBonus(baseSpellMaxHit, target.weaknessSeverity());
        }

        // Charged elemental tome (fire/water/earth): +10% damage vs NPCs to the
        // matching-element standard-spellbook spell. weirdgloop's MAX_HIT_TOME
        // step (floor(maxHit * 11/10)) is the FINAL magic-damage modifier —
        // applied AFTER the elemental-weakness bonus above, as its own floored
        // multiplicative step. spellElement is null for powered staves / Ancient
        // / non-elemental casts, so those never receive it.
        if (spellElement != null && gear.tome().boosts(spellElement)) {
            maxHit = (int) new Fraction(11, 10).applyFloor(maxHit);
        }

        // Thammaron's sceptre (revenant weapon): +50% accuracy AND damage vs
        // any Wilderness NPC, while charged with ether (assumed — see
        // RevenantWeapon) — the weapon's own separate multiplicative step.
        RevenantWeapon revenant = gear.revenantWeapon();
        if (revenant.appliesTo(CombatStyle.MAGIC) && target.isWildernessTarget()) {
            maxHit = (int) revenant.damageMult().applyFloor(maxHit);
            accuracyRoll = (int) revenant.accuracyMult().applyFloor(accuracyRoll);
        }

        TargetDamage damage = applyTargetDamageRules(maxHit, requirement, gear, CombatStyle.MAGIC, weaponId);
        maxHit = damage.visibleMaxHit();
        if (twinflameSecondHit) {
            return finishTwinflame(damage, accuracyRoll, defenceRoll, castSpeedTicks, target.hitpoints());
        }
        return finish(damage, accuracyRoll, defenceRoll, castSpeedTicks, target.hitpoints(), approximate);
    }

    /**
     * The final max-hit step, shared by all three styles: applies the given,
     * already-resolved {@link MonsterCombatRequirement}'s damage penalty then
     * its damage cap, in that order — see {@link TargetDamageRule}. A
     * {@code null} req (the overwhelming majority of monsters) is unaffected:
     * both {@link TargetDamageRule} methods return their neutral values for a
     * {@code null} requirement.
     *
     * <p><b>{@code req} is never re-resolved here</b> — resolution happens
     * exactly once, before this method is ever called, so a plain {@code
     * null} unambiguously means "this target has no requirement" and nothing
     * else. There is no third "not yet resolved" state to confuse it with:
     * the public {@code compute(...)} overloads that do NOT accept a {@link
     * MonsterCombatRequirement} parameter (the original API) look it up
     * themselves via {@link #resolveRequirement} — exactly the {@code
     * MonsterCombatRequirementRepository.getInstance().forMonster(target.lookupName())}
     * call this method used to make internally — and pass that (possibly
     * {@code null}) result down through {@code computeNonMagic}/{@code
     * computeMelee}/{@code computeRanged}/{@code computeMagic} to here
     * unchanged; the newer overloads that DO accept the parameter pass the
     * caller's value straight through, likewise unchanged. Overload dispatch
     * (arity/type, decided at the call site, not at runtime) is therefore the
     * whole "sentinel": which family of overload was invoked is what tells
     * "resolve it yourself" apart from "here is the resolved answer, even if
     * that answer is null" — no in-band marker value is needed, and none of
     * {@code MonsterCombatRequirement}'s real states is stolen for the
     * purpose.
     *
     * <p>Deliberately does NOT collapse a {@code REROLL} cap into a lower
     * {@code uncapped} value here, tempting as that shortcut looks for the
     * generic melee/ranged/magic path (see {@link #finish}, where it IS
     * exact). {@link #finishFang} needs the true, uncapped max hit AND the
     * cap AND the mode kept separate — Osmumten's fang compresses its roll
     * from the TRUE max, not from a value that has already had the cap
     * folded into it, exactly the defect the fang's {@code CLAMP} path was
     * fixed for in review; collapsing here would silently reintroduce the
     * same defect for {@code REROLL} instead. {@link TargetDamage} therefore
     * carries the mode, and each of {@link #finish}/{@link #finishFang}
     * decides for itself what to do with it.
     */
    private static TargetDamage applyTargetDamageRules(int maxHit, MonsterCombatRequirement req, EquipmentStats gear,
                                                       CombatStyle style, int weaponId) {
        int afterPenalty = (int) Math.floor(maxHit * TargetDamageRule.damageMultiplierFor(req, weaponId, style));
        int cap = TargetDamageRule.maxHitCapFor(req, gear, style, weaponId);
        MonsterCombatRequirement.CapMode mode = TargetDamageRule.capModeFor(req);
        return new TargetDamage(afterPenalty, cap, mode);
    }

    /**
     * A max hit split into the roll's real (uncapped) range, the per-hitsplat
     * cap, and the {@link MonsterCombatRequirement.CapMode} it applies with —
     * three separate things are needed because none of them is
     * interchangeable with another, in BOTH modes. Under {@code CLAMP} the
     * roll stays {@code 0..uncapped} and everything above {@code cap} lands
     * ON the cap — collapsing {@code uncapped} and {@code cap} into one
     * number loses the probability mass that piles up there (see {@link
     * DamageDistribution#cappedAverageDamagePerAttack}). Under {@code REROLL} the
     * distribution's SHAPE reduces to a plain {@code 0..cap} roll, but its
     * mean still depends on {@code uncapped}: the ordinary "rolled 0 becomes
     * 1" bump belongs to the ORIGINAL {@code 0..uncapped} roll (see {@link
     * DamageDistribution#rerolledAverageDamagePerAttack}'s {@code 1/(uncapped+1)}
     * term) — collapsing to just {@code cap} and feeding it through the
     * ordinary {@link DamageDistribution#averageDamagePerAttack} would apply that
     * bump a second time, to the re-roll's own genuine zero, and overstate
     * the mean. {@link #finishFang} needs the same three kept apart for a
     * different reason on top: it must compress its own {@code lo..hi} roll
     * from the TRUE max, not from a value the cap has already been folded
     * into. Both numbers and the mode therefore travel together all the way
     * to whichever {@code finish*} method consumes them.
     */
    private static final class TargetDamage {
        /** Max hit after any damage penalty, before any cap — the roll's real range. */
        final int uncapped;
        /** Per-hitsplat ceiling, or {@code -1} for none. */
        final int cap;
        /** How the cap applies; meaningless when {@link #cap} is {@code -1}. */
        final MonsterCombatRequirement.CapMode mode;

        TargetDamage(int uncapped, int cap, MonsterCombatRequirement.CapMode mode) {
            this.uncapped = uncapped;
            this.cap = cap;
            this.mode = mode;
        }

        /** What the player actually sees as their max hit. */
        int visibleMaxHit() {
            return cap >= 0 ? Math.min(uncapped, cap) : uncapped;
        }

        boolean isCapped() {
            return cap >= 0 && cap < uncapped;
        }
    }

    // ---- Shared helpers -----------------------------------------------------------------

    /**
     * The single non-stacking salve/slayer target-bonus slot, split into its
     * accuracy and damage components. {@code imbuedSlayer} records that the
     * WINNER is the on-task imbued slayer helm/black mask — the only case the
     * ranged max hit folds demonbane/dragonbane additively into (see
     * computeRanged).
     */
    private static final class TargetBonus {
        static final TargetBonus NONE = new TargetBonus(Fraction.ONE, Fraction.ONE, false);

        final Fraction accuracy;
        final Fraction damage;
        final boolean imbuedSlayer;

        TargetBonus(Fraction accuracy, Fraction damage, boolean imbuedSlayer) {
            this.accuracy = accuracy;
            this.damage = damage;
            this.imbuedSlayer = imbuedSlayer;
        }
    }

    /**
     * Target-specific gear-bonus slot shared by max hit and attack roll for
     * melee/ranged. ONE non-stacking slot holding Salve (vs undead) and the
     * on-task Slayer helm/black mask — the strongest effect wins (matching the
     * wiki DPS calc's if/else chain, where Salve takes precedence over the
     * helm; Salve's fractions also dominate numerically, so highest-wins and
     * the documented "salve wins over slayer" coincide).
     *
     * <p>Demonbane, Dragon Hunter weapons and the Twisted bow do NOT live in
     * this slot — each is the weapon's own passive, applied as a separate
     * multiplicative floor step in computeMelee/computeRanged (stacking with
     * this slot), with one exception: the ranged MAX HIT folds demonbane/
     * dragonbane damage additively into an on-task imbued helm's 23/20 (see
     * computeRanged; wiki calc: "these are additive with slayer only").
     */
    private static TargetBonus salveOrSlayerTargetBonus(CombatStyle style, EquipmentStats gear, Monster target, PlayerCombat player) {
        TargetBonus best = TargetBonus.NONE;

        if (target.isUndead()) {
            Fraction salve = style.isMelee() ? gear.salveType().meleeBonus() : gear.salveType().rangedBonus();
            best = stronger(best, new TargetBonus(salve, salve, false));
        }
        if (player.onSlayerTask() && gear.slayerHeadgear().wornAtAll()) {
            boolean imbued = gear.slayerHeadgear().isImbued();
            Fraction slayer = style.isMelee()
                    ? new Fraction(7, 6)
                    : (imbued ? new Fraction(23, 20) : Fraction.ONE); // ranged: imbued only
            best = stronger(best, new TargetBonus(slayer, slayer, imbued && !slayer.isOne()));
        }
        return best;
    }

    /**
     * A bonus fraction's additive contribution to the imbued slayer helm's
     * numerator, in twentieths: e.g. the Scorching bow's 13/10 (+30%) is +6,
     * the DHCB's 5/4 (+25%) is +5 — exactly the wiki calc's
     * {@code numerator += 5/6} constants on top of the helm's 23/20. Exact for
     * every wired bonus (both divide evenly); revisit if a bonus whose percent
     * is not a multiple of 5 ever needs folding.
     */
    private static long additiveTwentieths(Fraction bonus) {
        return (bonus.numerator - bonus.denominator) * 20 / bonus.denominator;
    }

    /**
     * Scales a demonbane bonus fraction down for a monster's demonbane
     * resistance: the bonus's excess-over-1 (its "BONUS portion", e.g.
     * Emberlight's 17/10 is 1 + 7/10) is reduced by {@code resistPercent}%,
     * so the final multiplier is {@code 1 + excess * (1 - resistPercent/100)}.
     * Duke Sucellus (30% resistance): Emberlight's documented +70% becomes
     * +49% (0.7 * 0.7 = 0.49), i.e. 149/100 instead of 17/10 — per the OSRS
     * Wiki ("an emberlight would only have 49% increased damage and accuracy
     * against him rather than 70%"). {@code resistPercent} of 0 (the default
     * for almost every demon) returns the bonus unchanged.
     */
    private static Fraction resistedDemonbaneFraction(Fraction bonus, int resistPercent) {
        if (resistPercent <= 0 || bonus.isOne()) {
            return bonus;
        }
        // excess = bonus - 1, as a fraction with the same denominator as bonus.
        long excessNumerator = bonus.numerator - bonus.denominator;
        // scaledExcess = excess * (100 - resistPercent) / 100
        long numerator = bonus.denominator * 100L + excessNumerator * (100L - resistPercent);
        long denominator = bonus.denominator * 100L;
        return new Fraction(numerator, denominator);
    }

    /** The stronger of two slot candidates: higher damage multiplier wins, accuracy breaks ties. */
    private static TargetBonus stronger(TargetBonus a, TargetBonus b) {
        double da = a.damage.asDouble();
        double db = b.damage.asDouble();
        if (db > da) {
            return b;
        }
        if (db == da && b.accuracy.asDouble() > a.accuracy.asDouble()) {
            return b;
        }
        return a;
    }

    private static double maxOf(PlayerCombat player, java.util.function.ToDoubleFunction<OffensivePrayer> extractor) {
        double best = 1.0;
        for (OffensivePrayer prayer : player.activePrayers()) {
            best = Math.max(best, extractor.applyAsDouble(prayer));
        }
        return best;
    }

    private static DpsResult finish(int maxHit, int attackRoll, int defenceRoll, int weaponSpeedTicks,
                                    int targetHitpoints, boolean baseEstimate) {
        return finish(new TargetDamage(maxHit, -1, MonsterCombatRequirement.CapMode.CLAMP), attackRoll, defenceRoll,
            weaponSpeedTicks, targetHitpoints, baseEstimate);
    }

    /**
     * Applies a {@link TargetDamage}'s cap (if any) to the GENERIC
     * uniform-{@code 0..maxHit} roll used by every style except Osmumten's
     * fang (see {@link #finishFang} for that one). Uncapped: the ordinary
     * formulas. {@code CLAMP}: the roll stays {@code 0..uncapped} with excess
     * mass piled on the cap ({@code cappedAverageDamagePerAttack}/
     * {@code cappedExpectedOverkill}). {@code REROLL}: a re-roll into
     * {@code 0..cap} of a uniform {@code 0..M} roll is EXACTLY a uniform
     * {@code 0..cap} roll — BUT that equivalence is about the shape of the
     * distribution only, not the "rolled 0 becomes 1" bump: the bump belongs
     * to the ORIGINAL {@code 0..M} roll (it happens first), and the re-roll
     * into {@code 0..cap} is a separate, later step with its own genuine
     * zero. Feeding the cap into {@link DamageDistribution#averageDamagePerAttack}/
     * {@link DamageDistribution#expectedOverkill} (as if it were a plain {@code
     * 0..cap} weapon roll) therefore overstates the mean by the bump's
     * {@code 1/(cap+1)} term where the correct term is {@code 1/(M+1)} — at
     * Verzik's ranged/magic cap of 3 that reports 1.75 instead of the true
     * ~1.524, a 14.8% overstatement (the same over-general-equivalence
     * mistake the fang's re-rolled formulas were built to avoid, caught in
     * the same review). {@link DamageDistribution#rerolledAverageDamagePerAttack}/
     * {@code rerolledExpectedOverkill} are the exact, zero-aware versions.
     * Overkill always uses the SAME distribution as avgDamage in every
     * branch, since TTK is derived from both together.
     */
    private static DpsResult finish(TargetDamage damage, int attackRoll, int defenceRoll, int weaponSpeedTicks,
                                    int targetHitpoints, boolean baseEstimate) {
        int maxHit = damage.visibleMaxHit();
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double avgDamage;
        double overkill;
        if (!damage.isCapped()) {
            avgDamage = DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
            overkill = DamageDistribution.expectedOverkill(maxHit, targetHitpoints);
        } else if (damage.mode == MonsterCombatRequirement.CapMode.REROLL) {
            avgDamage = DamageDistribution.rerolledAverageDamagePerAttack(hitChance, damage.uncapped, damage.cap);
            overkill = DamageDistribution.rerolledExpectedOverkill(damage.uncapped, damage.cap, targetHitpoints);
        } else {
            avgDamage = DamageDistribution.cappedAverageDamagePerAttack(hitChance, damage.uncapped, damage.cap);
            overkill = DamageDistribution.cappedExpectedOverkill(damage.uncapped, damage.cap, targetHitpoints);
        }
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        // TTK must account for overkill: the killing blow rolls past 0 HP, wasting
        // `overkill` HP of damage, so effective damage per kill is HP + overkill.
        // By Wald's identity E[TTK] = (HP + E[overkill]) / DPS exactly — the naive
        // HP/DPS understates it (e.g. Cerberus 57.3s vs GearScape's overkill-aware 59s).
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(maxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, baseEstimate);
    }

    /**
     * Osmumten's fang variant of {@link #finish}: uses the fang's own
     * double-accuracy-roll hit chance and compressed-damage-roll average
     * damage (see {@link DamageDistribution#fangHitChance} /
     * {@link DamageDistribution#fangAverageDamagePerAttack} for the formulas +
     * citation). {@code maxHit} in the returned {@link DpsResult} is left as
     * the TRUE (unshrunk) max hit, matching how the wiki/GearScape display it
     * — only the roll range feeding avgDamage is compressed.
     *
     * <p>Uncapped: overkill/TTK reuse the generic uniform-0..maxHit overkill
     * model on the true max hit (not the shrunk range) as an approximation —
     * modelling overkill exactly for the fang's UNCAPPED compressed
     * distribution is out of scope here (Tier B, not requested); this only
     * matters for the very last hit of a kill and the effect is small.
     *
     * <p><b>A capped target caps the fang's compressed roll, not the max hit it
     * is compressed from.</b> The cap therefore cannot be folded into {@code
     * maxHit} before this method is reached — it has to travel alongside the
     * true max (and the {@link MonsterCombatRequirement.CapMode}), which is
     * why this takes the whole {@link TargetDamage} rather than an
     * already-collapsed int. {@code CLAMP}: {@link
     * DamageDistribution#cappedFangAverageDamagePerAttack} / {@code
     * cappedFangExpectedOverkill} — both against the fang's own
     * compressed-and-clamped distribution, so the average and the overkill
     * cannot disagree (an earlier version of this ran overkill on the
     * GENERIC capped distribution instead, which silently reported a
     * different TTK than the average implied — fixed in review).
     * {@code REROLL}: unlike the generic path, this is NOT algebraically a
     * lower max hit — the fang would re-derive a different, much narrower
     * compression from a lowered max — so it needs its own EXACT formula,
     * {@link DamageDistribution#rerolledFangAverageDamagePerAttack} / {@code
     * rerolledFangExpectedOverkill}, built from the same {@code lo..hi}
     * distribution for both, for exactly the same reason.
     */
    private static DpsResult finishFang(TargetDamage damage, int attackRoll, int defenceRoll, int weaponSpeedTicks,
                                        int targetHitpoints) {
        double hitChance = DamageDistribution.fangHitChance(attackRoll, defenceRoll);
        double avgDamage;
        double overkill;
        if (!damage.isCapped()) {
            avgDamage = DamageDistribution.fangAverageDamagePerAttack(hitChance, damage.uncapped);
            overkill = DamageDistribution.expectedOverkill(damage.uncapped, targetHitpoints);
        } else if (damage.mode == MonsterCombatRequirement.CapMode.REROLL) {
            avgDamage = DamageDistribution.rerolledFangAverageDamagePerAttack(hitChance, damage.uncapped, damage.cap);
            overkill = DamageDistribution.rerolledFangExpectedOverkill(damage.uncapped, damage.cap, targetHitpoints);
        } else {
            avgDamage = DamageDistribution.cappedFangAverageDamagePerAttack(hitChance, damage.uncapped, damage.cap);
            overkill = DamageDistribution.cappedFangExpectedOverkill(damage.uncapped, damage.cap, targetHitpoints);
        }
        double dps = CombatMath.dps(avgDamage, weaponSpeedTicks);
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(damage.visibleMaxHit(), hitChance, dps, avgDamage, ttkSeconds, overkill, false);
    }

    /**
     * Twinflame staff variant of {@link #finish}: adds the exact expected
     * second-hit damage (see {@link TwinflameSecondHit} for the derivation
     * and why {@code E[floor(0.4*D)] != 0.4*E[D]}) to the average damage used
     * for DPS, and uses {@link TwinflameSecondHit}'s combined-hitsplat model
     * for overkill/TTK — see that class's javadoc for the explicit modelling
     * choice and its one documented, negligible simplification (the killing
     * blow's second hitsplat is still counted in full even when the first
     * alone would already be lethal).
     *
     * <p>Both the average AND the overkill are migrated together here
     * deliberately: reflecting the second hit in only one of the two would
     * leave the reported DPS and TTK disagreeing with each other for every
     * Twinflame-eligible cast.
     *
     * <p>Mirrors {@link #finish}'s three-way switch on {@link
     * TargetDamage#mode} rather than the two-way {@code isCapped()} check
     * this used to branch on: a {@code REROLL}-capped target (e.g. Verzik
     * Vitur phase 1's ranged/magic cap of 3) was previously silently routed
     * through the {@code cappedXxx} (i.e. {@code CLAMP}) helpers for all
     * three terms, piling excess probability onto the cap instead of
     * re-rolling it into {@code 0..cap} — the exact same over-general
     * "REROLL is just a clamp" mistake {@link #finish}/{@link #finishFang}
     * were already fixed for. {@link TwinflameSecondHit#rerolledSecondHitAverage}/
     * {@code rerolledCombinedExpectedOverkill} are the exact, zero-aware
     * REROLL replacements, built from the same distribution {@link
     * DamageDistribution#rerolledAverageDamagePerAttack}/{@code
     * rerolledExpectedOverkill} use so all three terms cannot disagree.
     */
    private static DpsResult finishTwinflame(TargetDamage damage, int attackRoll, int defenceRoll, int castSpeedTicks,
                                              int targetHitpoints) {
        int maxHit = damage.visibleMaxHit();
        double hitChance = CombatMath.hitChance(attackRoll, defenceRoll);
        double firstHitAvg;
        double secondHitAvg;
        double overkill;
        if (!damage.isCapped()) {
            firstHitAvg = DamageDistribution.averageDamagePerAttack(hitChance, maxHit);
            secondHitAvg = TwinflameSecondHit.secondHitAverage(hitChance, maxHit);
            overkill = TwinflameSecondHit.combinedExpectedOverkill(maxHit, targetHitpoints);
        } else if (damage.mode == MonsterCombatRequirement.CapMode.REROLL) {
            firstHitAvg = DamageDistribution.rerolledAverageDamagePerAttack(hitChance, damage.uncapped, damage.cap);
            secondHitAvg = TwinflameSecondHit.rerolledSecondHitAverage(hitChance, damage.uncapped, damage.cap);
            overkill = TwinflameSecondHit.rerolledCombinedExpectedOverkill(damage.uncapped, damage.cap, targetHitpoints);
        } else {
            firstHitAvg = DamageDistribution.cappedAverageDamagePerAttack(hitChance, damage.uncapped, damage.cap);
            secondHitAvg = TwinflameSecondHit.cappedSecondHitAverage(hitChance, damage.uncapped, damage.cap);
            overkill = TwinflameSecondHit.cappedCombinedExpectedOverkill(damage.uncapped, damage.cap, targetHitpoints);
        }
        double avgDamage = firstHitAvg + secondHitAvg;
        double dps = CombatMath.dps(avgDamage, castSpeedTicks);
        double ttkSeconds = dps > 0 ? (targetHitpoints + overkill) / dps : 0.0;
        return new DpsResult(maxHit, hitChance, dps, avgDamage, ttkSeconds, overkill, false);
    }
}
