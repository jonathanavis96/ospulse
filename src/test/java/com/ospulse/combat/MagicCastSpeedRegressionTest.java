package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Regression pin: introducing weapon-aware magic cast speed (Twinflame
 * staff / Harmonised nightmare staff — see {@link MagicCastSpeed}) must NOT
 * move the result for any spell cast with neither weapon equipped, since
 * {@link Spell#CAST_SPEED_TICKS} is a single global constant shared by every
 * other spell cast in the game.
 *
 * <p>These literals were captured by running the Spell-aware {@link
 * DpsCalculator#compute(EquipmentStats, PlayerCombat, CombatStyle, Monster,
 * Spell)} overload against the unmodified {@code master} baseline (git commit
 * c436df6, before this Stage 2 change), using 99/99 Magic, STANDARD stance,
 * no gear bonuses, a level-60/dmagic-30 target with 100 hitpoints. If any of
 * these ever change, the cast-speed change has leaked into ordinary spells
 * that must not be affected.
 */
public class MagicCastSpeedRegressionTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
    private static final double DELTA = 1e-9;

    private static EquipmentStats plainGear() {
        return EquipmentStats.builder()
                .add(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0)
                .weaponSpeedTicks(5)
                .build();
    }

    private static PlayerCombat mage99() {
        return PlayerCombat.builder()
                .magic(99, 99)
                .stance(Stance.STANDARD)
                .build();
    }

    private static Monster probeTarget() {
        return Monster.builder()
                .name("Probe target")
                .magicLevel(60)
                .defenceBonuses(0, 0, 0, 30, 0)
                .hitpoints(100)
                .build();
    }

    private static DpsResult probe(Spell spell) {
        return DpsCalculator.compute(plainGear(), mage99(), CombatStyle.MAGIC, probeTarget(), spell);
    }

    @Test
    public void fireWave_byteIdenticalToMasterBaseline() {
        DpsResult r = probe(Spell.FIRE_WAVE);
        assertEquals(20, r.maxHit());
        assertEquals(0.5307391870389122, r.accuracy(), DELTA);
        assertEquals(1.777555055003341, r.dps(), DELTA);
        assertEquals(5.332665165010023, r.avgHit(), DELTA);
        assertEquals(6.3032093895721415, r.overkillPerKill(), DELTA);
        assertEquals(59.8030475007551, r.ttkSeconds(), DELTA);
    }

    @Test
    public void fireSurge_byteIdenticalToMasterBaseline() {
        DpsResult r = probe(Spell.FIRE_SURGE);
        assertEquals(24, r.maxHit());
        assertEquals(0.5307391870389122, r.accuracy(), DELTA);
        assertEquals(2.1300332706495007, r.dps(), DELTA);
        assertEquals(6.390099811948502, r.avgHit(), DELTA);
        assertEquals(7.640518497208767, r.overkillPerKill(), DELTA);
        assertEquals(50.534665340878206, r.ttkSeconds(), DELTA);
    }

    @Test
    public void fireBolt_byteIdenticalToMasterBaseline() {
        DpsResult r = probe(Spell.FIRE_BOLT);
        assertEquals(12, r.maxHit());
        assertEquals(1.0750870711813862, r.dps(), DELTA);
        assertEquals(3.2252612135441585, r.avgHit(), DELTA);
        assertEquals(3.620253248432369, r.overkillPerKill(), DELTA);
        assertEquals(96.38312656347607, r.ttkSeconds(), DELTA);
    }

    @Test
    public void fireBlast_byteIdenticalToMasterBaseline() {
        DpsResult r = probe(Spell.FIRE_BLAST);
        assertEquals(16, r.maxHit());
        assertEquals(1.4257111494966856, r.dps(), DELTA);
        assertEquals(4.277133448490057, r.avgHit(), DELTA);
        assertEquals(4.96350802371379, r.overkillPerKill(), DELTA);
        assertEquals(73.62186096445183, r.ttkSeconds(), DELTA);
    }

    @Test
    public void fireStrike_byteIdenticalToMasterBaseline() {
        DpsResult r = probe(Spell.FIRE_STRIKE);
        assertEquals(8, r.maxHit());
        assertEquals(0.7273092563125833, r.dps(), DELTA);
        assertEquals(2.18192776893775, r.avgHit(), DELTA);
        assertEquals(2.270270270244899, r.overkillPerKill(), DELTA);
        assertEquals(140.61455891369977, r.ttkSeconds(), DELTA);
    }

    @Test
    public void ibanBlast_byteIdenticalToMasterBaseline() {
        DpsResult r = probe(Spell.IBAN_BLAST);
        assertEquals(25, r.maxHit());
        assertEquals(2.218217627880582, r.dps(), DELTA);
        assertEquals(6.654652883641745, r.avgHit(), DELTA);
        assertEquals(7.974948816496629, r.overkillPerKill(), DELTA);
        assertEquals(48.67644520509125, r.ttkSeconds(), DELTA);
    }

    @Test
    public void iceBarrage_byteIdenticalToMasterBaseline() {
        DpsResult r = probe(Spell.ICE_BARRAGE);
        assertEquals(30, r.maxHit());
        assertEquals(2.6594028081734744, r.dps(), DELTA);
        assertEquals(7.978208424520423, r.avgHit(), DELTA);
        assertEquals(9.64235404933711, r.overkillPerKill(), DELTA);
        assertEquals(41.2281861598249, r.ttkSeconds(), DELTA);
    }

    @Test
    public void legacyBaseSpellMaxHitOverload_untouched() {
        DpsResult r = DpsCalculator.compute(plainGear(), mage99(), CombatStyle.MAGIC, probeTarget(), 30);
        assertEquals(30, r.maxHit());
        assertEquals(2.6594028081734744, r.dps(), DELTA);
    }
}
