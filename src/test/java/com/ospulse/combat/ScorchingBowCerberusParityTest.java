package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * GearScape-parity regression for the exact reported Cerberus loadout that
 * exposed the missing Scorching-bow ranged demonbane passive: Scorching bow +
 * dragon arrows, Necklace of anguish, Ava's assembler, Slayer helmet (i),
 * Masori body (f), Masori chaps (f), Pegasian boots, Barrows gloves,
 * Archers ring (i); 99s, best potions + Rigour, Rapid, on-task.
 *
 * <p>GearScape (matches in-game): max hit <b>58</b>, accuracy <b>86.67%</b>,
 * DPS <b>10.479</b>. Derivation: boosted ranged 112 (ranging potion), Rigour;
 * effStr floor(112*1.23)+8 = 145, effAtt floor(112*1.20)+8 = 142; total
 * arange +252, rstr +113 (cache data); base max floor((145*177+320)/640)=40;
 * on-task imbued helm + demonbane fold: floor(40*29/20) = <b>58</b>; base
 * roll 142*316 = 44872, floor(*23/20)=51602, floor(*13/10)=67082 vs Cerberus
 * defence roll (100+9)*(100+64)=17876 -> hit 1-17878/134166 = <b>0.86675</b>;
 * avg 25.150 damage / 2.4s (5t Rapid->4t) = <b>10.479</b> DPS.
 *
 * <p>The pre-fix plugin gave 46 / 82.68% / 7.93 (slayer helm only — the bow's
 * +30% never applied).
 */
public class ScorchingBowCerberusParityTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    private static final int SCORCHING_BOW = 29591;
    private static final int DRAGON_ARROW = 11212;
    private static final int NECKLACE_OF_ANGUISH = 19547;
    private static final int AVAS_ASSEMBLER = 22109;
    private static final int SLAYER_HELMET_I = 11865;
    private static final int MASORI_BODY_F = 27238;
    private static final int MASORI_CHAPS_F = 27241;
    private static final int PEGASIAN_BOOTS = 13237;
    private static final int BARROWS_GLOVES = 7462;
    private static final int ARCHERS_RING_I = 11771;

    private static final int CERBERUS_NPC_ID = 5862;

    @Test
    public void cerberusRangedLoadout_matchesGearScape() {
        EquipmentStatsRepository repo = EquipmentStatsRepository.getInstance();
        int[] loadout = {SCORCHING_BOW, DRAGON_ARROW, NECKLACE_OF_ANGUISH, AVAS_ASSEMBLER,
                SLAYER_HELMET_I, MASORI_BODY_F, MASORI_CHAPS_F, PEGASIAN_BOOTS,
                BARROWS_GLOVES, ARCHERS_RING_I};

        EquipmentStats.Builder builder = EquipmentStats.builder();
        for (int itemId : loadout) {
            EquipmentStatsRepository.Stats s = repo.statsFor(itemId);
            assertTrue("stats missing for item " + itemId, s != null);
            builder.add(s.astab(), s.aslash(), s.acrush(), s.amagic(), s.arange(),
                    s.dstab(), s.dslash(), s.dcrush(), s.dmagic(), s.drange(),
                    s.str(), s.rstr(), s.mdmg(), s.prayer());
        }
        EquipmentStats gear = builder
                .weaponSpeedTicks(repo.statsFor(SCORCHING_BOW).aspeed())
                .slayerHeadgear(SlayerHeadgear.IMBUED)
                .demonbaneWeapon(DemonbaneWeapon.SCORCHING_BOW)
                .build();

        // Guard the aggregate inputs so a data regression fails loudly here,
        // not as a mysteriously shifted max hit.
        assertEquals("total ranged attack bonus", 252, gear.arange());
        assertEquals("total ranged strength bonus", 113, gear.rstr());
        assertEquals("Scorching bow speed", 5, gear.weaponSpeedTicks());

        PlayerCombat player = PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).defence(99, 99)
                .ranged(99, 99).magic(99, 99).prayer(99, 99).hitpoints(99, 99)
                .stance(Stance.RAPID)
                .assumeBestPotion(true)
                .assumeBestPrayer(true)
                .onSlayerTask(true)
                .build();

        Monster cerberus = MonsterRepository.getInstance().byId(CERBERUS_NPC_ID)
                .orElseThrow(() -> new AssertionError("Cerberus (5862) missing from monster data"));
        assertTrue("Cerberus must be tagged DEMON", cerberus.isDemon());

        DpsResult result = DpsCalculator.compute(gear, player, CombatStyle.RANGED, cerberus, 0);

        assertEquals("max hit (GearScape 58 = base 40 * (23+6)/20)", 58, result.maxHit());
        assertEquals("accuracy (GearScape 86.67%)", 0.866748, result.accuracy(), 5e-5);
        assertEquals("DPS (GearScape 10.479)", 10.479, result.dps(), 2e-3);

        // TTK must fold in overkill: the killing blow wastes ~19 HP of rolled
        // damage, so effective damage is HP + overkill = 600 + ~19 = ~619, and
        // TTK = 619 / 10.479 ~ 59.1s. GearScape shows 59s. The pre-fix plugin
        // used naive HP/DPS = 600/10.479 = 57.3s (overkill omitted).
        assertEquals("overkill wasted per kill (~19 HP)", 18.99, result.overkillPerKill(), 0.1);
        assertEquals("TTK folds in overkill (GearScape ~59s)", 59.07, result.ttkSeconds(), 0.2);
    }
}
