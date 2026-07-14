package com.ospulse.combat.optimizer;

import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.session.GearSnapshot;

import net.runelite.api.EquipmentInventorySlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers the expensive-item cap's de-risk ALLOCATION through
 * {@link GearOptimizer#optimize}: when the allowance forces some over-ceiling
 * items out, the demotions must be chosen by DPS loss — the allowed premium
 * slots go to the items that buy the most DPS — never by which over-cap slot
 * the local search happens to walk first in {@link GearOptimizer#SEARCHABLE_SLOTS}
 * order. The canonical shape: a premium amulet with real offensive stats
 * (Torture) worn alongside a premium ring with NONE (Ring of suffering (i) —
 * purely defensive), one premium slot allowed. Demoting the DPS-dead ring is
 * free; demoting the amulet costs real DPS for zero extra safety (either way
 * exactly one premium item is risked), so the ring must be the sacrifice.
 */
public class GearOptimizerCapDeriskTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    private static final int AMULET_SLOT = EquipmentInventorySlot.AMULET.getSlotIdx(); // 2
    private static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx(); // 3
    private static final int RING_SLOT = EquipmentInventorySlot.RING.getSlotIdx();     // 12

    // Weapon slot (3):
    private static final int GHRAZI_RAPIER = 22324; // best-in-slot stab, cheap risk here
    private static final int BRONZE_SWORD = 1277;   // junk fallback for the weapon-demotion scenario

    // Amulet slot (2) — the torture strictly out-DPSes the blood fury:
    private static final int AMULET_OF_TORTURE = 19553;
    private static final int AMULET_OF_BLOOD_FURY = 24780;

    // Ring slot (12) — both suffering variants carry ZERO offensive stats, so
    // they are DPS-identical to each other AND to an empty ring slot:
    private static final int RING_OF_SUFFERING_I = 19710;
    private static final int RING_OF_SUFFERING = 19550;

    private static final long THRESHOLD = 5_000_000L;

    private static int[] emptyLoadout() {
        int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
        Arrays.fill(ids, -1);
        return ids;
    }

    private static Monster cerberus() {
        return MonsterRepository.getInstance().byName("Cerberus").get();
    }

    private static PlayerCombat.Builder maxedPlayerTemplate() {
        return PlayerCombat.builder()
                .attack(99, 99).strength(99, 99).defence(99, 99)
                .ranged(99, 99).magic(99, 99).prayer(99, 99).hitpoints(99, 99)
                .assumeBestPotion(false).assumeBestPrayer(false).onSlayerTask(false);
    }

    /**
     * Risk values for the cap (see {@link GearOptimizer.Request.Builder#riskValueSource}):
     * torture and suffering (i) sit over the 5m ceiling, blood fury and the
     * plain suffering under it, the rapier well under. Unknown ids are 0 —
     * they can never become candidates anyway (see {@link #capped}'s
     * everything-unaffordable price source).
     */
    private static GearOptimizer.PriceSource riskValues() {
        java.util.Map<Integer, Long> risk = new java.util.HashMap<>();
        risk.put(GHRAZI_RAPIER, 1_000_000L);
        risk.put(AMULET_OF_TORTURE, 30_000_000L);
        risk.put(AMULET_OF_BLOOD_FURY, 4_000_000L);
        risk.put(RING_OF_SUFFERING_I, 20_000_000L);
        risk.put(RING_OF_SUFFERING, 500_000L);
        return id -> risk.getOrDefault(id, 0L);
    }

    /**
     * The canonical request: rapier + torture + suffering (i) worn, the
     * cheaper amulet/ring alternatives owned in the bank, budget 0 with
     * everything unpurchasable — an owned-only search where only the
     * expensive-item allowance varies.
     */
    private static GearOptimizer.Request capped(int expensiveItemCount) {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = GHRAZI_RAPIER;
        live[AMULET_SLOT] = AMULET_OF_TORTURE;
        live[RING_SLOT] = RING_OF_SUFFERING_I;

        Set<Integer> owned = new HashSet<>(Arrays.asList(
                GHRAZI_RAPIER, AMULET_OF_TORTURE, AMULET_OF_BLOOD_FURY,
                RING_OF_SUFFERING_I, RING_OF_SUFFERING));

        return GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .owned(owned)
                .priceSource(id -> 100_000_000L) // nothing buyable — owned candidates only
                .riskValueSource(riskValues())
                .expensiveItemThreshold(THRESHOLD)
                .expensiveItemCount(expensiveItemCount)
                .build();
    }

    private static int itemIdInSlot(GearOptimizer.Result result, int slotOrdinal) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == slotOrdinal) {
                return choice.itemId();
            }
        }
        return -1;
    }

    private static int premiumItemsIn(GearOptimizer.Result result) {
        int count = 0;
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.itemId() > 0 && riskValues().priceFor(choice.itemId()) > THRESHOLD) {
                count++;
            }
        }
        return count;
    }

    /**
     * The core allocation case: with ONE premium slot allowed and two premium
     * items worn, the allowance must be spent on the torture (real DPS) and
     * the DPS-dead suffering (i) demoted to the plain suffering — both
     * outcomes risk exactly one premium item, so keeping the torture is
     * strictly better. A slot-order walk demotes the amulet first (slot 2
     * precedes the ring's slot 12) and never revisits the choice.
     */
    @Test
    public void capDerisk_oneAllowed_spendsTheAllowanceOnTheTortureNotTheDpsDeadRing() {
        GearOptimizer.Result result = GearOptimizer.optimize(capped(1));

        assertEquals("the one allowed premium slot must go to the torture — demoting it buys no "
                        + "extra safety while the DPS-dead suffering (i) stays premium",
                AMULET_OF_TORTURE, itemIdInSlot(result, AMULET_SLOT));
        assertEquals("the zero-offensive-stat ring is the free demotion, so it must be the sacrifice",
                RING_OF_SUFFERING, itemIdInSlot(result, RING_SLOT));
        assertTrue("the allowance itself must still be respected",
                premiumItemsIn(result) <= 1);

        // Demoting the ring costs no DPS at all, so the constrained result
        // must score exactly what the uncapped torture + suffering (i)
        // loadout scores.
        GearOptimizer.Result unconstrained = GearOptimizer.optimize(capped(2));
        assertEquals("a DPS-free demotion must concede no DPS versus the roomier allowance",
                unconstrained.dps().dps(), result.dps().dps(), 1e-9);
    }

    /**
     * Guard against over-correction: an allowance that covers both premium
     * items must keep them BOTH — nothing demoted, nothing bought (budget 0,
     * so any spend would also be a budget violation).
     */
    @Test
    public void capDerisk_allowanceCoversBoth_keepsTortureAndImbuedRingUntouched() {
        for (int allowance : new int[] {2, 4}) {
            GearOptimizer.Result result = GearOptimizer.optimize(capped(allowance));

            assertEquals("allowance " + allowance + " covers the torture — it must stay",
                    AMULET_OF_TORTURE, itemIdInSlot(result, AMULET_SLOT));
            assertEquals("allowance " + allowance + " covers the suffering (i) — it must stay",
                    RING_OF_SUFFERING_I, itemIdInSlot(result, RING_SLOT));
            assertEquals("a satisfied cap must never trigger a purchase",
                    0L, result.totalSpend());
        }
    }

    /**
     * The fix must not degenerate into "never sacrifice the amulet": when the
     * amulet demotion genuinely IS the cheapest DPS concession — the only
     * other premium item is the weapon, whose sole fallback is junk — the
     * amulet must still be the one demoted.
     */
    @Test
    public void capDerisk_amuletDemotionCheapest_stillDemotesTheAmulet() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = GHRAZI_RAPIER;
        live[AMULET_SLOT] = AMULET_OF_TORTURE;

        Set<Integer> owned = new HashSet<>(Arrays.asList(
                GHRAZI_RAPIER, BRONZE_SWORD, AMULET_OF_TORTURE, AMULET_OF_BLOOD_FURY));

        java.util.Map<Integer, Long> risk = new java.util.HashMap<>();
        risk.put(GHRAZI_RAPIER, 30_000_000L);      // over the ceiling — junk fallback only
        risk.put(BRONZE_SWORD, 100L);
        risk.put(AMULET_OF_TORTURE, 30_000_000L);  // over the ceiling — near-tier fallback
        risk.put(AMULET_OF_BLOOD_FURY, 4_000_000L);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .owned(owned)
                .priceSource(id -> 100_000_000L)
                .riskValueSource(id -> risk.getOrDefault(id, 0L))
                .expensiveItemThreshold(THRESHOLD)
                .expensiveItemCount(1)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("the rapier's only fallback is junk, so the premium slot must go to the weapon",
                GHRAZI_RAPIER, itemIdInSlot(result, WEAPON_SLOT));
        assertEquals("the amulet is the cheapest DPS concession here — it must be the sacrifice",
                AMULET_OF_BLOOD_FURY, itemIdInSlot(result, AMULET_SLOT));
    }
}
