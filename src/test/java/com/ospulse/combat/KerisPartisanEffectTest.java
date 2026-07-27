package com.ospulse.combat;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Keris partisan family — +33% damage vs Kalphites/Scarabites for most
 * variants, but only +15% for "of amascut" (the Jewel of Amascut upgrade
 * trades damage bonus for base stats — see {@link KerisPartisan}'s
 * javadoc), an ADDITIONAL +33% accuracy for "of breaching" only, and a 1/51
 * chance to triple the landed damage (every variant); see {@link
 * KerisPartisan}/{@link KerisTripleRoll}. Gated on {@link
 * MonsterAttribute#KALPHITE} per the design spec's explicit instruction to
 * use the existing attribute rather than a curated name list.
 */
public class KerisPartisanEffectTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    /** +80 astab/aslash/acrush, +100 str, speed 4; kerisPartisan set by the caller. */
    private static EquipmentStats.Builder gear() {
        return EquipmentStats.builder()
                .add(80, 80, 80, 0, 0, 0, 0, 0, 0, 0, 100, 0, 0.0, 0)
                .weaponSpeedTicks(4);
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).ranged(99, 99).magic(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    private static Monster monster(Set<MonsterAttribute> attributes) {
        return Monster.builder()
                .name("Test")
                .hitpoints(600)
                .defenceLevel(100)
                .defenceBonuses(50, 50, 50, 50, 50)
                .magicLevel(1)
                .attributes(attributes)
                .build();
    }

    private static DpsResult compute(EquipmentStats gear, Monster target) {
        return DpsCalculator.compute(gear, player(), CombatStyle.SLASH, target, 0);
    }

    // ---- Bundled-data attribute verification (director instruction: verify, don't assume) ----

    /**
     * Confirms the spec's claim directly against the REAL bundled monster
     * data: every Kalphite Queen form, the Kalphite Guardian, and a broad
     * sample of "Scarab"-named monsters carry {@link
     * MonsterAttribute#KALPHITE} — so keying the Keris bonus off the
     * attribute (rather than a curated name list, per the design spec) is
     * correct for these.
     */
    @Test
    public void bundledData_kalphiteQueenAndCommonScarabs_carryTheKalphiteAttribute() {
        MonsterRepository repo = MonsterRepository.getInstance();
        String[] mustCarryKalphite = {
                "Kalphite Queen (Airborne)",
                "Kalphite Queen (Crawling)",
                "Kalphite Guardian",
                "Kalphite Worker",
                "Agile Scarab",
                "Giant Scarab",
                "Scarab Swarm (Normal)",
                "Soldier Scarab",
        };
        for (String name : mustCarryKalphite) {
            Optional<Monster> m = repo.byName(name);
            assertTrue(name + " must exist in the bundled data", m.isPresent());
            assertTrue(name + " must carry KALPHITE", m.get().attributes().contains(MonsterAttribute.KALPHITE));
        }
    }

    /**
     * <b>Honest, verified gap</b> (per the design spec's explicit
     * instruction: "verify Scarabites carry it; if they do not, say so
     * rather than assuming"): a handful of Tombs of Amascut scarab entries
     * and one Construction-minigame Kalphite soldier variant do NOT carry
     * {@link MonsterAttribute#KALPHITE} in the currently bundled monster
     * data, even though they are Kalphite/Scarab creatures in-game. This is
     * a bundled-monster-data completeness gap, not something this stage's
     * item-mechanic wiring (which correctly uses the attribute, per spec)
     * can fix — flagged here so it is not silently lost. If a future data
     * refresh adds the attribute to these, this test will start failing and
     * should be updated to the "must carry" list above instead of deleted.
     */
    @Test
    public void bundledData_knownGap_someToaScarabsAndOneConstructionVariantLackTheAttribute() {
        MonsterRepository repo = MonsterRepository.getInstance();
        String[] missingKalphiteTag = {
                "Scarab (Tombs of Amascut)",
                "Scarab Swarm (Tombs of Amascut)",
                "Scarab Swarm (Beneath Cursed Sands)",
                "Kalphite soldier (Construction)",
        };
        for (String name : missingKalphiteTag) {
            Optional<Monster> m = repo.byName(name);
            assertTrue(name + " must exist in the bundled data", m.isPresent());
            assertFalse(name + " is a known gap in the bundled data (does NOT carry KALPHITE)",
                    m.get().attributes().contains(MonsterAttribute.KALPHITE));
        }
    }

    // ---- DpsCalculator wiring ------------------------------------------------------------

    @Test
    public void kalphiteTarget_getsThirtyThreePercentDamage_plainVariant() {
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        DpsResult base = compute(gear().build(), kalphite);
        DpsResult keris = compute(gear().kerisPartisan(KerisPartisan.PARTISAN).build(), kalphite);

        // Hard-coded 33% (per Mod Ash's confirmed figure, independent of the
        // production Fraction constant) so this test cannot be satisfied by
        // a wrong percentage that still happens to match KerisPartisan's own
        // DAMAGE_MULT field.
        int expectedMaxHit = (int) Math.floor(base.maxHit() * 133.0 / 100.0);
        assertEquals(expectedMaxHit, keris.maxHit());
        // Plain variants get NO accuracy bump.
        assertEquals(base.accuracy(), keris.accuracy(), 1e-12);
    }

    /**
     * The over-credit bug fix: {@code OF_AMASCUT} (Keris partisan of
     * amascut, item id 30891) gets only +15% damage, not the +33% every
     * other variant carries — the Jewel of Amascut upgrade "decreas[es]
     * damage bonus against Kalphites and Scabarites" (OSRS Wiki) and
     * weirdgloop/osrs-dps-calc's {@code PlayerVsNPCCalc.ts} special-cases
     * exactly this variant to {@code [115, 100]}. Expected values are
     * hard-coded literals derived independently of {@link KerisPartisan}'s
     * own {@code damageMultiplier()} field, and the base max hit is
     * confirmed (via an explicit assertion) to floor DIFFERENTLY at 115%
     * vs 133% — so this test cannot pass if amascut is reverted to 133/100.
     */
    @Test
    public void ofAmascut_getsFifteenPercentDamage_whileOtherVariantsGetThirtyThreePercent() {
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        DpsResult base = compute(gear().build(), kalphite);

        int expectedAmascutMaxHit = (int) Math.floor(base.maxHit() * 115.0 / 100.0);
        int expectedOtherMaxHit = (int) Math.floor(base.maxHit() * 133.0 / 100.0);
        assertTrue("test fixture must produce different floors for 115% vs 133% "
                        + "or this test would not be discriminating",
                expectedAmascutMaxHit != expectedOtherMaxHit);

        DpsResult amascut = compute(gear().kerisPartisan(KerisPartisan.OF_AMASCUT).build(), kalphite);
        assertEquals(expectedAmascutMaxHit, amascut.maxHit());

        for (KerisPartisan variant : new KerisPartisan[]{
                KerisPartisan.PARTISAN, KerisPartisan.OF_BREACHING,
                KerisPartisan.OF_CORRUPTION, KerisPartisan.OF_THE_SUN}) {
            DpsResult result = compute(gear().kerisPartisan(variant).build(), kalphite);
            assertEquals("variant=" + variant, expectedOtherMaxHit, result.maxHit());
        }
    }

    /**
     * Pins "of breaching"'s accuracy bonus at the wiki's +33% figure via a
     * hard-coded literal (not {@link KerisPartisan#OF_BREACHING}'s own
     * {@code damageMultiplier()} field), recomputed independently through
     * {@link CombatMath} and {@link KerisTripleRoll#finish}. Guards against
     * breaching's accuracy step ever being accidentally collapsed onto
     * amascut's +15% damage figure.
     */
    @Test
    public void ofBreaching_accuracyBonusIsThirtyThreePercent_notAmascutsFifteen() {
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        EquipmentStats kerisGear = gear().kerisPartisan(KerisPartisan.OF_BREACHING).build();
        DpsResult result = compute(kerisGear, kalphite);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 0, 8, 1.0);
        int baseMaxHit = CombatMath.meleeOrRangedMaxHit(effStr, 100, Fraction.ONE);
        int baseAttackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 80, Fraction.ONE);

        int expectedMaxHit = (int) Math.floor(baseMaxHit * 133.0 / 100.0);
        int expectedAttackRoll = (int) Math.floor(baseAttackRoll * 133.0 / 100.0);
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        DpsResult expected = KerisTripleRoll.finish(expectedMaxHit, -1, MonsterCombatRequirement.CapMode.CLAMP,
                expectedAttackRoll, defenceRoll, 4, kalphite.hitpoints());

        assertEquals(expected.accuracy(), result.accuracy(), 1e-12);
    }

    @Test
    public void nonKalphiteTarget_hasNoEffect_anyVariant() {
        Monster nonKalphite = monster(EnumSet.noneOf(MonsterAttribute.class));
        DpsResult base = compute(gear().build(), nonKalphite);
        for (KerisPartisan variant : KerisPartisan.values()) {
            if (variant == KerisPartisan.NONE) {
                continue;
            }
            DpsResult result = compute(gear().kerisPartisan(variant).build(), nonKalphite);
            assertEquals("variant=" + variant, base.maxHit(), result.maxHit());
            assertEquals("variant=" + variant, base.dps(), result.dps(), 1e-9);
        }
    }

    /**
     * Review finding (rejected, confirmed wrong): the wiki's own wording for
     * both the base {@code Keris} and the {@code Keris partisan} reads "It
     * deals 33% bonus damage against all kalphites and scabarites, AND has a
     * 1/51 chance of puncturing a hole in THEIR exoskeleton, dealing triple
     * damage" — "their" refers back to kalphites/scabarites, and the
     * puncture message text ("a gap in the creature's chitin") describes a
     * Kalphite-specific anatomical feature. Both effects are gated on the
     * SAME target attribute; a suggestion to decouple the 1/51 puncture from
     * the {@code KALPHITE} gate (so it would apply vs any target) is not
     * supported by the primary source. Pinned explicitly here — distinct
     * from {@link #nonKalphiteTarget_hasNoEffect_anyVariant}'s aggregate
     * dps/maxHit check — so a future re-application of that exact
     * suggestion is caught immediately: avgHit against a non-Kalphite
     * target must match the PLAIN generic formula exactly, with no residual
     * 53/51 triple-roll bump.
     */
    @Test
    public void nonKalphiteTarget_getsNeitherTheDamageBonusNorTheTripleRollPassive() {
        Monster nonKalphite = monster(EnumSet.noneOf(MonsterAttribute.class));
        DpsResult keris = compute(gear().kerisPartisan(KerisPartisan.PARTISAN).build(), nonKalphite);

        double plainAvg = DamageDistribution.averageDamagePerAttack(keris.accuracy(), keris.maxHit());
        assertEquals("no residual 1/51 triple-roll bump vs a non-Kalphite target",
                plainAvg, keris.avgHit(), 1e-9);
        // If the puncture roll were wrongly left universal, avgHit would be
        // plainAvg * 53/51 instead - assert that mismatch explicitly too.
        assertTrue("must NOT equal the triple-roll-inflated average",
                Math.abs(keris.avgHit() - plainAvg * 53.0 / 51.0) > 1e-6);
    }

    @Test
    public void ofBreaching_getsAdditionalThirtyThreePercentAccuracy() {
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        DpsResult plain = compute(gear().kerisPartisan(KerisPartisan.PARTISAN).build(), kalphite);
        DpsResult breaching = compute(gear().kerisPartisan(KerisPartisan.OF_BREACHING).build(), kalphite);

        // Same +33% damage/maxHit as every variant...
        assertEquals(plain.maxHit(), breaching.maxHit());
        // ...but breaching also boosts accuracy, so it must land more often.
        assertTrue(breaching.accuracy() > plain.accuracy());
    }

    @Test
    public void ofAmascutCorruptionAndTheSun_haveNoAccuracyBonus() {
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        DpsResult plain = compute(gear().kerisPartisan(KerisPartisan.PARTISAN).build(), kalphite);
        for (KerisPartisan variant : new KerisPartisan[]{
                KerisPartisan.OF_AMASCUT, KerisPartisan.OF_CORRUPTION, KerisPartisan.OF_THE_SUN}) {
            DpsResult result = compute(gear().kerisPartisan(variant).build(), kalphite);
            assertEquals("variant=" + variant, plain.accuracy(), result.accuracy(), 1e-12);
        }
    }

    @Test
    public void tripleRollPassive_increasesAverageDamageBeyondThePlainThirtyThreePercent() {
        // The 1/51 triple roll must show up as an EXTRA bump on top of the
        // flat +33%, not be silently dropped.
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        DpsResult keris = compute(gear().kerisPartisan(KerisPartisan.PARTISAN).build(), kalphite);

        double hitChance = keris.accuracy();
        double justThirtyThreePercent = DamageDistribution.averageDamagePerAttack(hitChance, keris.maxHit());
        assertTrue("triple-roll bump must exceed the plain +33% average",
                keris.avgHit() > justThirtyThreePercent);
        assertEquals(justThirtyThreePercent * 53.0 / 51.0, keris.avgHit(), 1e-9);
    }

    @Test
    public void unaffectedLoadout_regression_kalphiteAttributeNeverChangesDpsWithoutKeris() {
        EquipmentStats plain = gear().build();
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        Monster nonKalphite = monster(EnumSet.noneOf(MonsterAttribute.class));
        DpsResult onKalphite = compute(plain, kalphite);
        DpsResult onNonKalphite = compute(plain, nonKalphite);
        assertEquals(onKalphite.maxHit(), onNonKalphite.maxHit());
        assertEquals(onKalphite.dps(), onNonKalphite.dps(), 1e-9);
    }

    @Test
    public void endToEnd_matchesKerisTripleRollFormulaDirectly() {
        Monster kalphite = monster(EnumSet.of(MonsterAttribute.KALPHITE));
        EquipmentStats kerisGear = gear().kerisPartisan(KerisPartisan.OF_BREACHING).build();
        DpsResult result = compute(kerisGear, kalphite);

        int effStr = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 3, 8, 1.0);
        int effAtt = CombatMath.effectiveMeleeOrRangedLevel(99, 1.0, 0, 8, 1.0); // AGGRESSIVE: no attack-side style bonus
        int baseMaxHit = CombatMath.meleeOrRangedMaxHit(effStr, 100, Fraction.ONE);
        int baseAttackRoll = CombatMath.meleeOrRangedAttackRoll(effAtt, 80, Fraction.ONE);
        int maxHit = (int) KerisPartisan.OF_BREACHING.damageMultiplier().applyFloor(baseMaxHit);
        int attackRoll = (int) KerisPartisan.OF_BREACHING.damageMultiplier().applyFloor(baseAttackRoll); // breaching: accuracy bonus too
        int defenceRoll = CombatMath.npcDefenceRoll(100, 50);
        DpsResult expected = KerisTripleRoll.finish(maxHit, -1, MonsterCombatRequirement.CapMode.CLAMP,
                attackRoll, defenceRoll, 4, kalphite.hitpoints());

        assertEquals(expected.maxHit(), result.maxHit());
        assertEquals(expected.accuracy(), result.accuracy(), 1e-12);
        assertEquals(expected.avgHit(), result.avgHit(), 1e-9);
        assertEquals(expected.dps(), result.dps(), 1e-9);
        assertEquals(expected.ttkSeconds(), result.ttkSeconds(), 1e-9);
    }
}
