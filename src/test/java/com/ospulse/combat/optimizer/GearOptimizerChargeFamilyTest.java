package com.ospulse.combat.optimizer;

import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.session.GearSnapshot;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end coverage of the charge-variant fuzzy grouping (item #7) through
 * {@link GearOptimizer#optimize}: owning any charge of a family recommends the
 * highest owned charge, the family collapses to a single candidate, and
 * non-charge items are untouched.
 */
public class GearOptimizerChargeFamilyTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    private static final int AMULET_SLOT = EquipmentInventorySlot.AMULET.getSlotIdx(); // 2
    private static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx(); // 3

    private static final int ABYSSAL_WHIP = 4151;
    private static final int DRAGON_SCIMITAR = 4587;
    private static final int AMULET_OF_FURY = 6585; // a non-charge amulet control

    private static final int GLORY_UNCHARGED = ItemID.AMULET_OF_GLORY;   // 1704
    private static final int GLORY_1 = ItemID.AMULET_OF_GLORY_1;         // 1706
    private static final int GLORY_2 = ItemID.AMULET_OF_GLORY_2;         // 1708
    private static final int GLORY_3 = ItemID.AMULET_OF_GLORY_3;         // 1710
    private static final int GLORY_4 = ItemID.AMULET_OF_GLORY_4;         // 1712
    private static final int GLORY_ETERNAL = ItemID.AMULET_OF_GLORY_INF; // 19707 (~50m, high risk value)

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

    /** Budget-0 owned-only search: an owned whip + a bank of amulets, nothing else affordable. */
    private static GearOptimizer.Request.Builder ownedOnly(int[] live, Set<Integer> ownedAmulets) {
        Set<Integer> owned = new HashSet<>(ownedAmulets);
        owned.add(ABYSSAL_WHIP);
        return GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(id -> (id == ABYSSAL_WHIP || ownedAmulets.contains(id)) ? 0L : 100_000_000L)
                .owned(owned);
    }

    private static int amuletIdIn(GearOptimizer.Result result) {
        for (GearOptimizer.SlotChoice c : result.loadout()) {
            if (c.slotOrdinal() == AMULET_SLOT) {
                return c.itemId();
            }
        }
        return -1;
    }

    // -------------------------------------------------- (a) highest owned charge

    @Test
    public void owningLowCharge_recommendsHighestOwnedCharge() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = ABYSSAL_WHIP;

        GearOptimizer.Result result = GearOptimizer.optimize(ownedOnly(live,
                new HashSet<>(Arrays.asList(GLORY_1, GLORY_4))).build());

        assertEquals("owning glory (1) and (4) must recommend the (4)", GLORY_4, amuletIdIn(result));
    }

    @Test
    public void wearingLowCharge_butOwningHigher_upgradesToHighestOwned() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = ABYSSAL_WHIP;
        live[AMULET_SLOT] = GLORY_1; // already WEARING the (1)

        // Owns the (4) too (worn (1) is auto-owned by the builder).
        GearOptimizer.Result result = GearOptimizer.optimize(ownedOnly(live,
                new HashSet<>(Arrays.asList(GLORY_4))).build());

        assertEquals("wearing the (1) while owning the (4) must upgrade the recommendation to the (4)",
                GLORY_4, amuletIdIn(result));
    }

    @Test
    public void selectionIsDeterministicHighestOwned_notTieLuck() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = ABYSSAL_WHIP;
        Set<Integer> bank = new HashSet<>(Arrays.asList(GLORY_UNCHARGED, GLORY_1, GLORY_2, GLORY_3, GLORY_4));

        // Excluding the top charges must walk DOWN the ladder deterministically.
        assertEquals(GLORY_4, amuletIdIn(GearOptimizer.optimize(ownedOnly(live, bank).build())));
        assertEquals(GLORY_3, amuletIdIn(GearOptimizer.optimize(ownedOnly(live, bank)
                .exclude(new HashSet<>(Arrays.asList(GLORY_4))).build())));
        assertEquals(GLORY_2, amuletIdIn(GearOptimizer.optimize(ownedOnly(live, bank)
                .exclude(new HashSet<>(Arrays.asList(GLORY_4, GLORY_3))).build())));
    }

    // ---------------------------------------- (a') lowest-risk among owned charges

    @Test
    public void owningHighRiskAndLowRiskCharge_prefersLowRiskSameDpsMember() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = ABYSSAL_WHIP;
        Set<Integer> owned = new HashSet<>(Arrays.asList(GLORY_ETERNAL, GLORY_4, ABYSSAL_WHIP));

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(id -> owned.contains(id) ? 0L : 100_000_000L)
                // Eternal glory carries a ~50m risk value; the normal glory ~15k.
                .riskValueSource(id -> id == GLORY_ETERNAL ? 50_000_000L : 15_000L)
                .owned(owned)
                .build();

        assertEquals("a 50m eternal glory must not be surfaced when an identical-DPS ~15k glory is owned",
                GLORY_4, amuletIdIn(GearOptimizer.optimize(request)));

        // The single collapsed candidate is the low-risk member.
        List<Integer> glories = new java.util.ArrayList<>();
        for (int id : GearOptimizer.candidateIdsForSlotForTest(AMULET_SLOT, request)) {
            if (ChargeFamilies.isMember(id)) {
                glories.add(id);
            }
        }
        assertEquals(1, glories.size());
        assertEquals(Integer.valueOf(GLORY_4), glories.get(0));
    }

    @Test
    public void wearingHighRiskCharge_seedNormalisesToLowRiskOwnedMember() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = ABYSSAL_WHIP;
        live[AMULET_SLOT] = GLORY_ETERNAL; // wearing the 50m eternal glory
        Set<Integer> ownedAmulets = new HashSet<>(Arrays.asList(GLORY_4)); // also hold a cheap glory

        Set<Integer> owned = new HashSet<>(ownedAmulets);
        owned.add(ABYSSAL_WHIP);
        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(id -> (id == ABYSSAL_WHIP || id == GLORY_ETERNAL || ownedAmulets.contains(id)) ? 0L : 100_000_000L)
                .riskValueSource(id -> id == GLORY_ETERNAL ? 50_000_000L : 15_000L)
                .owned(owned)
                .build();

        assertEquals("the worn 50m eternal glory must be seeded down to the identical-DPS ~15k owned glory",
                GLORY_4, amuletIdIn(GearOptimizer.optimize(request)));
    }

    // ------------------------------------------------ (b) collapses to one candidate

    @Test
    public void chargeFamilyCollapsesToASingleCandidate() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = ABYSSAL_WHIP;
        Set<Integer> bank = new HashSet<>(Arrays.asList(GLORY_UNCHARGED, GLORY_1, GLORY_2, GLORY_3, GLORY_4));

        GearOptimizer.Request request = ownedOnly(live, bank).build();
        List<Integer> amuletCandidates = GearOptimizer.candidateIdsForSlotForTest(AMULET_SLOT, request);

        List<Integer> gloryCandidates = new java.util.ArrayList<>();
        for (int id : amuletCandidates) {
            if (ChargeFamilies.isMember(id)) {
                gloryCandidates.add(id);
            }
        }
        assertEquals("all five owned glory charges must collapse to ONE candidate",
                1, gloryCandidates.size());
        assertEquals("the single representative must be the highest owned charge",
                Integer.valueOf(GLORY_4), gloryCandidates.get(0));
    }

    // ------------------------------------------------------ (c) non-charge untouched

    @Test
    public void nonChargeAmulet_isUnaffected() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = ABYSSAL_WHIP;

        GearOptimizer.Request request = ownedOnly(live,
                new HashSet<>(Arrays.asList(AMULET_OF_FURY))).build();

        // Fury appears as its own candidate and is recommended unchanged.
        assertTrue("a non-charge amulet is still a candidate",
                GearOptimizer.candidateIdsForSlotForTest(AMULET_SLOT, request).contains(AMULET_OF_FURY));
        assertEquals(AMULET_OF_FURY, amuletIdIn(GearOptimizer.optimize(request)));
    }

    @Test
    public void nonChargeWeapons_areNotCollapsed() {
        int[] live = emptyLoadout();
        live[WEAPON_SLOT] = DRAGON_SCIMITAR;
        Set<Integer> owned = new HashSet<>(Arrays.asList(ABYSSAL_WHIP, DRAGON_SCIMITAR));

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(id -> owned.contains(id) ? 0L : 100_000_000L)
                .owned(owned)
                .build();

        List<Integer> weaponCandidates = GearOptimizer.candidateIdsForSlotForTest(WEAPON_SLOT, request);
        assertTrue(weaponCandidates.contains(ABYSSAL_WHIP));
        assertTrue("both owned weapons remain distinct candidates (no charge collapse)",
                weaponCandidates.contains(DRAGON_SCIMITAR));
    }
}
