package com.ospulse.combat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins The Hueycoatl (Tail)'s shipped {@link DpsCalculator} numbers to
 * literals captured by running this EXACT setup against the code as it stood
 * immediately BEFORE Stage 1c (per-style caps, {@code CapMode}, cap-exempt
 * weapons) was implemented — see the shipped {@code DAMAGE_CAP} entry for
 * "The Hueycoatl (Tail)" in {@code monster_combat_requirements.json}, which
 * this change deliberately leaves untouched (no {@code maxHitCapByStyle}, no
 * {@code capMode} — so it defaults to {@link
 * MonsterCombatRequirement.CapMode#CLAMP}, exactly as before).
 *
 * <p>If any literal below ever needs to change, the Stage 1c changes broke
 * Hueycoatl — stop and investigate rather than updating the literal.
 */
public class HueycoatlDamageCapRegressionTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    private static Monster hueycoatlTail() {
        return Monster.builder()
                .name("The Hueycoatl (Tail)")
                .hitpoints(300)
                .defenceLevel(125)
                .defenceBonuses(100, 100, 0, 200, 350)
                .magicLevel(50)
                .attributes(java.util.EnumSet.of(MonsterAttribute.DRAGON))
                .build();
    }

    private static PlayerCombat player() {
        return PlayerCombat.builder()
                .attack(90, 90).strength(90, 90).defence(70, 70).ranged(80, 80).magic(75, 75)
                .prayer(70, 70).hitpoints(99, 99)
                .stance(Stance.AGGRESSIVE)
                .build();
    }

    /** acrush is NOT the loadout's highest attack bonus -> the flat cap (4) applies. */
    private static EquipmentStats flatCapGear() {
        return EquipmentStats.builder()
                .add(80, 60, 40, 30, 70,
                     20, 20, 20, 20, 20,
                     64, 60, 10.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    /** acrush IS the loadout's highest attack bonus -> the crush-highest cap (9) applies. */
    private static EquipmentStats crushHighestGear() {
        return EquipmentStats.builder()
                .add(10, 10, 90, 30, 70,
                     20, 20, 20, 20, 20,
                     64, 60, 10.0, 0)
                .weaponSpeedTicks(4)
                .build();
    }

    @Test
    public void stab_flatCap_isByteIdenticalToBeforeStage1c() {
        DpsResult r = DpsCalculator.compute(flatCapGear(), player(), CombatStyle.STAB, hueycoatlTail(), 20);
        assertEquals(4, r.maxHit());
        assertEquals(0.32106292942621834, r.accuracy(), 0.0);
        assertEquals(0.47777221640806306, r.dps(), 0.0);
        assertEquals(1.1466533193793513, r.avgHit(), 0.0);
        assertEquals(630.8724596871102, r.ttkSeconds(), 0.0);
        assertEquals(1.4133333355170707, r.overkillPerKill(), 0.0);
    }

    @Test
    public void crush_crushHighestCap_isByteIdenticalToBeforeStage1c() {
        DpsResult r = DpsCalculator.compute(crushHighestGear(), player(), CombatStyle.CRUSH, hueycoatlTail(), 20);
        assertEquals(9, r.maxHit());
        assertEquals(0.7158285297820182, r.accuracy(), 0.0);
        assertEquals(2.0594273178252505, r.dps(), 0.0);
        assertEquals(4.942625562780601, r.avgHit(), 0.0);
        assertEquals(147.3995309620129, r.ttkSeconds(), 0.0);
        assertEquals(3.558620697798159, r.overkillPerKill(), 0.0);
    }

    @Test
    public void ranged_flatCap_isByteIdenticalToBeforeStage1c() {
        DpsResult r = DpsCalculator.compute(flatCapGear(), player(), CombatStyle.RANGED, hueycoatlTail(), 20);
        assertEquals(4, r.maxHit());
        assertEquals(0.10627827748436289, r.accuracy(), 0.0);
        assertEquals(0.15498915466469587, r.dps(), 0.0);
        assertEquals(0.3719739711952701, r.avgHit(), 0.0);
        assertEquals(1944.6317134184517, r.ttkSeconds(), 0.0);
        assertEquals(1.3968253968849282, r.overkillPerKill(), 0.0);
    }

    @Test
    public void magic_flatCap_isByteIdenticalToBeforeStage1c() {
        DpsResult r = DpsCalculator.compute(flatCapGear(), player(), CombatStyle.MAGIC, hueycoatlTail(), 20);
        assertEquals(4, r.maxHit());
        assertEquals(0.2534506002439494, r.accuracy(), 0.0);
        assertEquals(0.3810941996421703, r.dps(), 0.0);
        assertEquals(0.9146260791412086, r.avgHit(), 0.0);
        assertEquals(790.9374822332303, r.ttkSeconds(), 0.0);
        assertEquals(1.421686758666166, r.overkillPerKill(), 0.0);
    }
}
