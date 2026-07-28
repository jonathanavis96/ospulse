package com.ospulse.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

/**
 * Stage 1c: Verzik Vitur phase 1's per-style, {@code REROLL}-mode damage cap
 * ("Any hitsplats done to the boss with melee above 10 damage will be
 * re-rolled to 0-10 damage. Any hitsplats done to the boss with ranged or
 * magic above 3 damage (excluding Dawnbringer) will be re-rolled to 0-3
 * damage." — <a href="https://oldschool.runescape.wiki/w/Maximum_damage_cap">
 * OSRS Wiki: Maximum damage cap</a>).
 *
 * <p>Split in two halves: shipped-data assertions against the real {@link
 * MonsterCombatRequirementRepository#getInstance()} singleton (not a
 * hand-built fixture — this is what actually ships), and end-to-end {@link
 * DpsCalculator} wiring proofs that {@code REROLL} produces EXACTLY what an
 * uncapped computation at {@code maxHit = cap} would, while {@code CLAMP}
 * (The Hueycoatl's tail) does not — the check that stops the two modes being
 * silently swapped.
 */
public class VerzikDamageCapTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    private static final int DAWNBRINGER = 22516;
    private static final int ORDINARY_WEAPON = 4151; // abyssal whip — not cap-exempt

    private static final String[] VERZIK_P1_NAMES = {
        "Verzik Vitur (Entry mode, Phase 1)",
        "Verzik Vitur (Normal mode, Phase 1)",
        "Verzik Vitur (Hard mode, Phase 1)",
    };

    // ---- Shipped-data assertions (real repository, not a fixture) ------------------------

    @Test
    public void allThreeModeNamesResolveToTheSameShapeOfRequirement() {
        MonsterCombatRequirementRepository repo = MonsterCombatRequirementRepository.getInstance();
        for (String name : VERZIK_P1_NAMES) {
            Optional<MonsterCombatRequirement> req = repo.forMonster(name);
            assertTrue(name + " must resolve to a curated requirement", req.isPresent());
            assertEquals(MonsterCombatRequirement.Type.DAMAGE_CAP, req.get().type());
            assertEquals("mode must be REROLL, per the wiki's own wording (\"re-rolled to 0-N damage\")",
                MonsterCombatRequirement.CapMode.REROLL, req.get().capMode());
        }
    }

    @Test
    public void shippedVerzikP1_capsMeleeAt10() {
        MonsterCombatRequirementRepository repo = MonsterCombatRequirementRepository.getInstance();
        EquipmentStats neutralGear = neutralGear();
        for (String name : VERZIK_P1_NAMES) {
            MonsterCombatRequirement req = repo.forMonster(name).orElseThrow(AssertionError::new);
            assertEquals(name + ": stab", 10,
                TargetDamageRule.maxHitCapFor(req, neutralGear, CombatStyle.STAB, ORDINARY_WEAPON));
            assertEquals(name + ": slash", 10,
                TargetDamageRule.maxHitCapFor(req, neutralGear, CombatStyle.SLASH, ORDINARY_WEAPON));
            assertEquals(name + ": crush", 10,
                TargetDamageRule.maxHitCapFor(req, neutralGear, CombatStyle.CRUSH, ORDINARY_WEAPON));
        }
    }

    @Test
    public void shippedVerzikP1_capsRangedAndMagicAt3() {
        MonsterCombatRequirementRepository repo = MonsterCombatRequirementRepository.getInstance();
        EquipmentStats neutralGear = neutralGear();
        for (String name : VERZIK_P1_NAMES) {
            MonsterCombatRequirement req = repo.forMonster(name).orElseThrow(AssertionError::new);
            assertEquals(name + ": ranged", 3,
                TargetDamageRule.maxHitCapFor(req, neutralGear, CombatStyle.RANGED, ORDINARY_WEAPON));
            assertEquals(name + ": magic", 3,
                TargetDamageRule.maxHitCapFor(req, neutralGear, CombatStyle.MAGIC, ORDINARY_WEAPON));
        }
    }

    @Test
    public void shippedVerzikP1_dawnbringerIsWhollyUncapped() {
        MonsterCombatRequirementRepository repo = MonsterCombatRequirementRepository.getInstance();
        EquipmentStats neutralGear = neutralGear();
        for (String name : VERZIK_P1_NAMES) {
            MonsterCombatRequirement req = repo.forMonster(name).orElseThrow(AssertionError::new);
            for (CombatStyle style : CombatStyle.values()) {
                assertEquals(name + " / " + style + ": Dawnbringer must have no cap at all", -1,
                    TargetDamageRule.maxHitCapFor(req, neutralGear, style, DAWNBRINGER));
            }
        }
    }

    @Test
    public void shippedVerzikP1_phase2And3AreNotCapped() {
        MonsterCombatRequirementRepository repo = MonsterCombatRequirementRepository.getInstance();
        String[] uncappedPhases = {
            "Verzik Vitur (Entry mode, Phase 2)", "Verzik Vitur (Entry mode, Phase 3)",
            "Verzik Vitur (Normal mode, Phase 2)", "Verzik Vitur (Normal mode, Phase 3)",
            "Verzik Vitur (Hard mode, Phase 2)", "Verzik Vitur (Hard mode, Phase 3)",
        };
        for (String name : uncappedPhases) {
            assertFalse(name + " must NOT resolve to a curated requirement — the cap is phase-1-only",
                repo.forMonster(name).isPresent());
        }
    }

    private static EquipmentStats neutralGear() {
        // Equal attack bonuses across styles -> crush is never "the highest", so this
        // exercises the per-style map rather than the flat/crush-highest fallback.
        return EquipmentStats.builder()
                .add(50, 50, 50, 30, 50, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .build();
    }

    // ---- End-to-end DpsCalculator wiring: REROLL == reduced max hit -----------------------

    private static Monster verzikP1() {
        return Monster.builder()
                .name("Verzik Vitur (Entry mode, Phase 1)")
                .hitpoints(400)
                .defenceLevel(150)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(200)
                .build();
    }

    /** Same defence profile as {@link #verzikP1()} but not a curated monster — reveals the true (uncapped) max hit. */
    private static Monster uncappedControl() {
        return Monster.builder()
                .name("Zzz Verzik Reroll Control (Uncapped)")
                .hitpoints(400)
                .defenceLevel(150)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(200)
                .build();
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).defence(70, 70).ranged(99, 99).magic(99, 99)
                .prayer(70, 70).hitpoints(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    /** Deliberately huge strength bonus so the natural (uncapped) melee max hit is far above 10. */
    private static EquipmentStats meleeGear(int strBonus) {
        return EquipmentStats.builder()
                .add(80, 80, 80, 0, 0, 0, 0, 0, 0, 0, strBonus, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    /** Deliberately huge ranged-strength bonus so the natural (uncapped) ranged max hit is far above 3. */
    private static EquipmentStats rangedGear(int rangedStrBonus) {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 80, 0, 0, 0, 0, 0, 0, rangedStrBonus, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    private static EquipmentStats magicGear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 80, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    /**
     * For several distinct (natural max hit, cap) pairs — varied by gear strength,
     * so the "M" in the reroll math genuinely differs each time — a REROLL cap of
     * C must produce EXACTLY what an uncapped computation at maxHit = C would.
     * The visible {@code r.maxHit() == cap} assertion confirms the cap actually
     * bound each time (otherwise the equivalence below would be vacuous).
     */
    @Test
    public void rerollCap_meleeEqualsAnUncappedComputationAtTheCapValue_forSeveralMaxHitPairs() {
        for (int strBonus : new int[]{80, 200, 500}) {
            DpsResult control = DpsCalculator.compute(meleeGear(strBonus), player(), CombatStyle.STAB, uncappedControl(), 0);
            DpsResult r = DpsCalculator.compute(meleeGear(strBonus), player(), CombatStyle.STAB, verzikP1(), 0);
            assertEquals("strBonus=" + strBonus + ": cap must actually bind", 10, r.maxHit());
            assertRerollMatchesReferenceFormula(r, control.maxHit(), 10, verzikP1().hitpoints(), 4);
        }
    }

    @Test
    public void rerollCap_rangedEqualsAnUncappedComputationAtTheCapValue_forSeveralMaxHitPairs() {
        for (int rangedStrBonus : new int[]{50, 150, 400}) {
            DpsResult control = DpsCalculator.compute(rangedGear(rangedStrBonus), player(), CombatStyle.RANGED, uncappedControl(), 0);
            DpsResult r = DpsCalculator.compute(rangedGear(rangedStrBonus), player(), CombatStyle.RANGED, verzikP1(), 0);
            assertEquals("rangedStrBonus=" + rangedStrBonus + ": cap must actually bind", 3, r.maxHit());
            assertRerollMatchesReferenceFormula(r, control.maxHit(), 3, verzikP1().hitpoints(), 4);
        }
    }

    @Test
    public void rerollCap_magicEqualsAnUncappedComputationAtTheCapValue_forSeveralMaxHitPairs() {
        // Vary the base spell max hit directly instead of gear — magic's max hit
        // doesn't depend on strength bonuses.
        for (int baseSpellMaxHit : new int[]{20, 60, 150}) {
            DpsResult control = DpsCalculator.compute(magicGear(), player(), CombatStyle.MAGIC, uncappedControl(), baseSpellMaxHit);
            DpsResult r = DpsCalculator.compute(magicGear(), player(), CombatStyle.MAGIC, verzikP1(), baseSpellMaxHit);
            assertEquals("baseSpellMaxHit=" + baseSpellMaxHit + ": cap must actually bind", 3, r.maxHit());
            assertRerollMatchesReferenceFormula(r, control.maxHit(), 3, verzikP1().hitpoints(), 4);
        }
    }

    /**
     * The equivalence itself: REROLL's SHAPE reduces to a plain 0..cap roll,
     * but its mean still depends on the true uncapped max hit {@code M} — the
     * ordinary "rolled 0 becomes 1" bump belongs to the ORIGINAL {@code 0..M}
     * roll, not to the re-roll's own genuine zero. So avgHit, overkill, dps
     * and ttk must all match {@link DamageDistribution#rerolledAverageDamage}/
     * {@code rerolledExpectedOverkill} fed the REAL {@code M} (obtained from an
     * uncapped control run against an identical, uncurated target) and the
     * cap — NOT the plain {@link DamageDistribution#averageDamage}/{@code
     * expectedOverkill} fed just the cap, which would double-apply the bump
     * and overstate the mean (e.g. 1.75 instead of Verzik's true ~1.524 at a
     * cap of 3). Uses the SAME accuracy the capped computation produced
     * (capping never touches accuracy — only the damage magnitude).
     */
    private static void assertRerollMatchesReferenceFormula(DpsResult r, int uncappedMaxHit, int cap,
                                                             int targetHitpoints, int weaponSpeedTicks) {
        double expectedAvg = DamageDistribution.rerolledAverageDamage(r.accuracy(), uncappedMaxHit, cap);
        assertEquals(expectedAvg, r.avgHit(), 1e-9);

        double expectedOverkill = DamageDistribution.rerolledExpectedOverkill(uncappedMaxHit, cap, targetHitpoints);
        assertEquals(expectedOverkill, r.overkillPerKill(), 1e-9);

        double expectedDps = CombatMath.dps(expectedAvg, weaponSpeedTicks);
        assertEquals(expectedDps, r.dps(), 1e-9);

        double expectedTtk = expectedDps > 0 ? (targetHitpoints + expectedOverkill) / expectedDps : 0.0;
        assertEquals(expectedTtk, r.ttkSeconds(), 1e-9);
    }

    /** Dawnbringer bypasses the cap entirely end-to-end, not just at the TargetDamageRule layer. */
    @Test
    public void dawnbringer_endToEnd_isNeverCapped() {
        DpsResult capped = DpsCalculator.compute(meleeGear(500), player(), CombatStyle.STAB, verzikP1(), 0, ORDINARY_WEAPON);
        DpsResult uncapped = DpsCalculator.compute(meleeGear(500), player(), CombatStyle.STAB, verzikP1(), 0, DAWNBRINGER);
        assertEquals("an ordinary weapon is still capped at 10", 10, capped.maxHit());
        assertTrue("Dawnbringer's natural max hit must exceed the cap for this to be a real proof",
            uncapped.maxHit() > 10);
        assertTrue("Dawnbringer must hit harder than a capped weapon here", uncapped.avgHit() > capped.avgHit());
    }

    // ---- CLAMP != REROLL: the check that stops the two modes being silently swapped ------

    /**
     * The Hueycoatl's tail ships CLAMP (unchanged by this feature); Verzik phase 1
     * ships REROLL. For each, compare the actual avgHit against what the REROLL
     * formula (a plain uncapped computation at maxHit = cap) would have given for
     * the SAME accuracy: Verzik must match it exactly (it IS that formula), while
     * Hueycoatl must exceed it (CLAMP piles probability mass on the cap, so its
     * average is materially higher) — never the other way around, and never equal.
     */
    @Test
    public void clampAndRerollGiveMateriallyDifferentAnswers_clampHigher() {
        Monster hueycoatl = Monster.builder()
                .name("The Hueycoatl (Tail)")
                .hitpoints(300)
                .defenceLevel(125)
                .defenceBonuses(100, 100, 0, 200, 350)
                .magicLevel(50)
                .build();
        // acrush is not the highest bonus here -> Hueycoatl's flat cap (4) applies.
        EquipmentStats gear = EquipmentStats.builder()
                .add(80, 60, 40, 0, 0, 0, 0, 0, 0, 0, 500, 0, 0.0, 0)
                .weaponSpeedTicks(4)
                .build();

        Monster hueycoatlUncappedControl = Monster.builder()
                .name("Zzz Hueycoatl Reroll Comparison Control (Uncapped)")
                .hitpoints(300)
                .defenceLevel(125)
                .defenceBonuses(100, 100, 0, 200, 350)
                .magicLevel(50)
                .build();
        DpsResult hueycoatlControl = DpsCalculator.compute(gear, player(), CombatStyle.STAB, hueycoatlUncappedControl, 0);
        DpsResult clampResult = DpsCalculator.compute(gear, player(), CombatStyle.STAB, hueycoatl, 0);
        assertEquals("sanity: Hueycoatl's flat cap must actually bind here", 4, clampResult.maxHit());
        double rerollEquivalentAvg = DamageDistribution.rerolledAverageDamage(
            clampResult.accuracy(), hueycoatlControl.maxHit(), 4);
        assertTrue("CLAMP piles mass on the cap, so its average must be strictly higher "
                + "than what REROLL would have given for the same true max, cap and accuracy",
            clampResult.avgHit() > rerollEquivalentAvg);

        DpsResult verzikControl = DpsCalculator.compute(meleeGear(500), player(), CombatStyle.STAB, uncappedControl(), 0);
        DpsResult rerollResult = DpsCalculator.compute(meleeGear(500), player(), CombatStyle.STAB, verzikP1(), 0);
        assertEquals(10, rerollResult.maxHit());
        double sameFormulaAvg = DamageDistribution.rerolledAverageDamage(
            rerollResult.accuracy(), verzikControl.maxHit(), 10);
        assertEquals("REROLL must match the formula exactly — no distribution gap",
            sameFormulaAvg, rerollResult.avgHit(), 1e-9);
    }
}
