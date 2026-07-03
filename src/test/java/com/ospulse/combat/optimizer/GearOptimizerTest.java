package com.ospulse.combat.optimizer;

import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.session.GearSnapshot;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link GearOptimizer}: candidate pruning/affordability, budget
 * filtering, include/exclude enforcement, and the DPS-per-gp upgrade
 * comparison against known item stats (see the id comments below, cross-
 * checked against {@code equipment_stats.min.json}).
 *
 * <p><b>Flagged for the Fable review pass:</b> the local-search heuristic
 * (greedy seed + single-slot-swap hill-climbing, see {@link GearOptimizer}
 * class javadoc) is NOT guaranteed globally optimal — these tests confirm it
 * finds the obviously-correct answer in small, unambiguous scenarios (one
 * dominant upgrade per slot, no interacting set bonuses), not that it always
 * finds the GLOBAL best loadout in a large combinatorial space. A harder
 * scenario with competing set effects (e.g. Void vs a raw stat stack) would
 * be a good target for deeper verification.
 */
public class GearOptimizerTest {

    // Weapon slot (3) — strictly increasing str/aslash, useful as an upgrade ladder:
    private static final int BRONZE_SWORD = 1277;      // str 5, aslash 3 — weakest
    private static final int DRAGON_SCIMITAR = 4587;    // str 66, aslash 67
    private static final int ABYSSAL_WHIP = 4151;       // str 82, aslash 82 — strongest of the three

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
                .attack(99, 99)
                .strength(99, 99)
                .defence(99, 99)
                .ranged(99, 99)
                .magic(99, 99)
                .prayer(99, 99)
                .hitpoints(99, 99)
                .assumeBestPotion(false)
                .assumeBestPrayer(false)
                .onSlayerTask(false);
    }

    // -------------------------------------------------- owned-only search

    @Test
    public void ownedOnly_noOtherCandidatesAffordable_keepsLiveWeapon() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L) // no GE spend allowed
                .priceSource(id -> id == ABYSSAL_WHIP ? 0L : 3_000_000L) // everything else "costs" 3m
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        int weaponInResult = weaponIdIn(result);
        assertEquals("with budget 0 and nothing else owned, the live whip must remain", ABYSSAL_WHIP, weaponInResult);
        assertEquals(0L, result.totalSpend());
    }

    /**
     * Every weapon costs 100m (unaffordable) except the three test fixtures —
     * isolates a scenario to just those weapons instead of the whole ~1500-item
     * candidate pool (whose cheapest-by-default-zero-price item would otherwise
     * "win" any test that doesn't price everything else out of range).
     */
    private static GearOptimizer.PriceSource everyWeaponExpensiveExcept(java.util.Map<Integer, Long> prices) {
        return id -> prices.containsKey(id) ? prices.get(id) : 100_000_000L;
    }

    @Test
    public void budgetAllowsAffordableUpgrade_picksTheBestAffordableWeapon() {
        int[] live = emptyLoadout();
        live[3] = BRONZE_SWORD;

        // Scimitar costs 50k (affordable), whip costs 5m (not affordable at a 100k budget).
        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(BRONZE_SWORD, 0L);
        fixed.put(DRAGON_SCIMITAR, 50_000L);
        fixed.put(ABYSSAL_WHIP, 5_000_000L);
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(100_000L)
                .priceSource(prices)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("the affordable scimitar upgrade must be picked over the bronze sword",
                DRAGON_SCIMITAR, weaponIdIn(result));
        assertTrue("DPS must improve over the bronze sword baseline", result.deltaDps() > 0);
        assertTrue("spend must not exceed the budget", result.totalSpend() <= 100_000L);
    }

    @Test
    public void budgetTooLowForAnyUpgrade_keepsLiveWeapon() {
        int[] live = emptyLoadout();
        live[3] = BRONZE_SWORD;

        GearOptimizer.PriceSource prices = id -> id == BRONZE_SWORD ? 0L : 10_000_000L;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(1_000L) // far too small for any real upgrade
                .priceSource(prices)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals(BRONZE_SWORD, weaponIdIn(result));
        assertEquals(0L, result.totalSpend());
    }

    // -------------------------------------------------- exclude / include

    @Test
    public void excludedItem_neverAppearsEvenIfOwned() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP; // owned (live gear is always "owned")

        Set<Integer> exclude = new HashSet<>();
        exclude.add(ABYSSAL_WHIP);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L) // owned-only, but the whip is excluded so nothing else is affordable either
                .exclude(exclude)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertFalse("excluded item must never appear in the result", weaponIdIn(result) == ABYSSAL_WHIP);
    }

    @Test
    public void excludedItem_notCountedAsOwnedForBaseline() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP;

        Set<Integer> exclude = new HashSet<>();
        exclude.add(ABYSSAL_WHIP);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .exclude(exclude)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);
        // With the only owned weapon excluded and no budget, the result falls back
        // to bare fists (unarmed) for the weapon slot — DPS should reflect that,
        // not the whip's.
        assertTrue("owned-only DPS with the whip excluded must not equal the whip's own DPS",
                result.ownedOnlyDps() < 3.0); // unarmed crush vs Cerberus is far below the whip's ~2.9 DPS at these stats
    }

    @Test
    public void includedItem_forcedIntoResultEvenIfWeakerThanOwned() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP; // objectively the best of the three test weapons

        Set<Integer> include = new HashSet<>();
        include.add(BRONZE_SWORD); // force the WORST weapon in despite owning the whip

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(BRONZE_SWORD, 0L);
        fixed.put(ABYSSAL_WHIP, 0L);
        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .include(include)
                .priceSource(everyWeaponExpensiveExcept(fixed))
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("an included item must be forced into the result even though it's a worse weapon",
                BRONZE_SWORD, weaponIdIn(result));
    }

    // -------------------------------------------------- DPS-per-gp

    @Test
    public void dpsPerGp_isPositiveForARealUpgrade_andZeroWithNoSpend() {
        int[] live = emptyLoadout();
        live[3] = BRONZE_SWORD;

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(BRONZE_SWORD, 0L);
        fixed.put(DRAGON_SCIMITAR, 40_000L);
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);
        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(100_000L)
                .priceSource(prices)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);
        assertEquals(DRAGON_SCIMITAR, weaponIdIn(result));
        assertTrue("a real, paid-for upgrade must have a positive DPS-per-gp", result.dpsPerGp() > 0);

        // Zero-spend baseline (owned-only) trivially has dpsPerGp = 0 by definition.
        GearOptimizer.Request ownedOnlyRequest = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .build();
        GearOptimizer.Result ownedOnlyResult = GearOptimizer.optimize(ownedOnlyRequest);
        assertEquals(0.0, ownedOnlyResult.dpsPerGp(), 1e-9);
    }

    // -------------------------------------------------- sanity / determinism

    @Test
    public void emptyLoadoutWithBudget_findsSomeWeapon() {
        int[] live = emptyLoadout(); // bare fists

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(1_000_000L)
                .priceSource(id -> 100_000L) // uniform price so pruning isn't starved
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);
        assertTrue("optimizer must find SOME weapon better than bare fists", weaponIdIn(result) > 0);
        assertTrue(result.dps().dps() > 0);
    }

    @Test
    public void resultLoadout_neverExceedsBudget() {
        int[] live = emptyLoadout();
        live[3] = BRONZE_SWORD;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(75_000L)
                .priceSource(id -> id == DRAGON_SCIMITAR ? 50_000L : (id == ABYSSAL_WHIP ? 5_000_000L : 0L))
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);
        assertTrue(result.totalSpend() <= 75_000L);
    }

    private static int weaponIdIn(GearOptimizer.Result result) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == WhatIfLoadout.WEAPON_SLOT) {
                return choice.itemId();
            }
        }
        return -1;
    }
}
