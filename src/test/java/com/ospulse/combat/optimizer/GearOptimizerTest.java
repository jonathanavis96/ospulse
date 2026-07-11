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
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

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

    // -------------------------------------------------- expensive-item plumbing (GearSection item #1)

    /**
     * {@code expensiveItemCount}/{@code expensiveItemThreshold} default to 0
     * when never set on the builder (a 0 threshold disables the cap), and the
     * values round-trip through the builder/getter so the UI (GearSection) can
     * capture and persist them. Enforcement of a live cap is covered by the
     * {@code expensiveCap_*} tests above.
     */
    @Test
    public void expensiveItemSettings_defaultToZeroWhenNotSet() {
        int[] live = emptyLoadout();
        live[3] = BRONZE_SWORD;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .build();

        assertEquals(0, request.expensiveItemCount());
        assertEquals(0L, request.expensiveItemThreshold());
    }

    @Test
    public void expensiveItemSettings_roundTripThroughBuilder() {
        int[] live = emptyLoadout();
        live[3] = BRONZE_SWORD;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .expensiveItemCount(2)
                .expensiveItemThreshold(10_000_000L)
                .build();

        assertEquals(2, request.expensiveItemCount());
        assertEquals(10_000_000L, request.expensiveItemThreshold());
    }

    @Test
    public void expensiveItemSettings_negativeInputsClampToZero() {
        int[] live = emptyLoadout();
        live[3] = BRONZE_SWORD;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .expensiveItemCount(-5)
                .expensiveItemThreshold(-1L)
                .build();

        assertEquals(0, request.expensiveItemCount());
        assertEquals(0L, request.expensiveItemThreshold());
    }

    // ------------------------------------ expensive-item cap ENFORCEMENT (wildy/PvP risk)

    // The whip out-DPSes the scimitar against Cerberus, so an unconstrained
    // search always keeps the whip — only an ACTIVE expensive-item cap should
    // force the cheaper scimitar in. Both are owned (budget 0), so purchase
    // cost is 0 for each; only their GE *value* differs, which is what the cap
    // weighs (an owned high-value item is still riskable gear).
    private static final long WHIP_VALUE = 100_000_000L;
    private static final long SCIM_VALUE = 1_000_000L;

    private static GearOptimizer.PriceSource whipVsScimPrices() {
        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(ABYSSAL_WHIP, WHIP_VALUE);
        fixed.put(DRAGON_SCIMITAR, SCIM_VALUE);
        return everyWeaponExpensiveExcept(fixed);
    }

    private static int expensiveItemsIn(GearOptimizer.Result result, long threshold, GearOptimizer.PriceSource prices) {
        int count = 0;
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.itemId() > 0 && prices.priceFor(choice.itemId()) >= threshold) {
                count++;
            }
        }
        return count;
    }

    /**
     * The core enforcement case: the live loadout ALREADY exceeds the
     * allowance (a pricey worn whip, zero expensive items permitted), so the
     * search must DE-RISK the seed down to the cheaper scimitar — accepting the
     * DPS loss — rather than reject every over-cap swap and stay stuck on the
     * infeasible starting gear.
     */
    @Test
    public void expensiveCap_forcesCheaperWeaponEvenAtLowerDps_whenLiveGearExceedsAllowance() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP;

        Set<Integer> owned = new HashSet<>(Arrays.asList(ABYSSAL_WHIP, DRAGON_SCIMITAR));
        GearOptimizer.PriceSource prices = whipVsScimPrices();

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .owned(owned)
                .priceSource(prices)
                .expensiveItemThreshold(50_000_000L) // whip is expensive, scimitar is not
                .expensiveItemCount(0)               // ...and zero expensive items are allowed
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("a zero expensive-item allowance must de-risk the pricey whip to the cheaper scimitar",
                DRAGON_SCIMITAR, weaponIdIn(result));
        assertEquals("the returned loadout must hold no items at/above the expensive threshold",
                0, expensiveItemsIn(result, 50_000_000L, prices));
    }

    /** A cap the whip fits under must NOT over-constrain — the best-DPS weapon stays. */
    @Test
    public void expensiveCap_keepsBestWeapon_whenAllowanceCoversIt() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP;

        Set<Integer> owned = new HashSet<>(Arrays.asList(ABYSSAL_WHIP, DRAGON_SCIMITAR));

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .owned(owned)
                .priceSource(whipVsScimPrices())
                .expensiveItemThreshold(50_000_000L)
                .expensiveItemCount(1) // one expensive item allowed — the whip fits
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("with the whip within the allowance the best-DPS weapon must be kept",
                ABYSSAL_WHIP, weaponIdIn(result));
    }

    /** A zero threshold means "no item is expensive" — the cap is disabled, even at count 0. */
    @Test
    public void expensiveCap_disabledAtZeroThreshold_keepsBestWeaponEvenAtZeroCount() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = ABYSSAL_WHIP;

        Set<Integer> owned = new HashSet<>(Arrays.asList(ABYSSAL_WHIP, DRAGON_SCIMITAR));

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .owned(owned)
                .priceSource(whipVsScimPrices())
                .expensiveItemThreshold(0L) // disabled
                .expensiveItemCount(0)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("a zero threshold disables the cap, so the best-DPS whip is kept",
                ABYSSAL_WHIP, weaponIdIn(result));
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

    /**
     * Reproduces the per-item-only budget bug: several UNOWNED candidates
     * (weapon whip, shield defender, boots) are each individually priced
     * UNDER the 50m budget, but their SUM (46m + 40m + 6m = 92m) blows way
     * past it. A search that only prunes items whose OWN price exceeds the
     * budget (the old {@code buildCandidatesForSlot} behaviour) will happily
     * combine all three since each passes the per-item check individually —
     * the cumulative spend was never bounded. The fix must track/guard
     * total spend across slots so the returned loadout's total is <= budget.
     */
    @Test
    public void resultLoadout_totalSpendAcrossMultipleSlots_neverExceedsBudget() {
        int[] live = emptyLoadout(); // nothing owned, bare fists/no gear

        final int DRAGON_DEFENDER = 12954; // shield slot (5)
        final int DRAGON_BOOTS = 11840;    // boots slot (10)

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(ABYSSAL_WHIP, 46_000_000L);
        fixed.put(DRAGON_DEFENDER, 40_000_000L);
        fixed.put(DRAGON_BOOTS, 6_000_000L);
        // Everything else effectively unaffordable/irrelevant so the search
        // is forced to choose among (or skip) these three fixtures.
        GearOptimizer.PriceSource prices = id -> fixed.containsKey(id) ? fixed.get(id) : 100_000_000L;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(50_000_000L)
                .priceSource(prices)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertTrue("total spend across all purchased slots must not exceed the budget (was "
                        + result.totalSpend() + ")",
                result.totalSpend() <= 50_000_000L);
    }

    private static int weaponIdIn(GearOptimizer.Result result) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == WhatIfLoadout.WEAPON_SLOT) {
                return choice.itemId();
            }
        }
        return -1;
    }

    private static final int AMMO_SLOT = 13;

    private static int itemIdInSlot(GearOptimizer.Result result, int slotOrdinal) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == slotOrdinal) {
                return choice.itemId();
            }
        }
        return -1;
    }

    // -------------------------------------------------- bug B: demonbane weapon pruning (Scorching bow)

    private static final int SCORCHING_BOW = 29591; // ranged demonbane: +30% acc/dmg vs demons
    private static final int DRAGON_DART = 11230;   // thrown weapon, no demonbane — plain ranged-strength option

    /**
     * Reproduces the Scorching bow bug: {@code buildCandidatesForSlot} used to
     * prune weapon candidates purely by a target-blind proxy score
     * ({@code arange + 2*rstr}), so the Scorching bow — unremarkable on raw
     * stats next to darts/other bows — was discarded before its target-
     * specific +30% demonbane accuracy/damage was ever computed by {@code
     * DpsCalculator}. Against a demon (Cerberus) with a generous budget, the
     * bow must survive pruning and out-rank a plain thrown dart.
     */
    @Test
    public void demonTarget_rangedStyle_scorchingBowSurvivesPruningAndOutranksPlainDart() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = DRAGON_DART;

        Set<Integer> owned = new HashSet<>(Arrays.asList(SCORCHING_BOW, DRAGON_DART));
        GearOptimizer.PriceSource prices = id -> owned.contains(id) ? 0L : 100_000_000L;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(200_000_000L)
                .owned(owned)
                .priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.RANGED)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("the demonbane Scorching bow must out-rank a plain dart on a demon target",
                SCORCHING_BOW, weaponIdIn(result));
        assertEquals(com.ospulse.combat.CombatStyle.RANGED, result.style().type());
        assertTrue("the bow's demon-boosted DPS must be computed (nonzero)", result.dps().dps() > 0);
    }

    // -------------------------------------------------- bug C: ammo slot ignored by blowpipe

    private static final int TOXIC_BLOWPIPE = 12926;
    private static final int DRAGON_JAVELIN = 19484;      // ammo slot, huge ranged strength (150) but ignored by a blowpipe
    private static final int RADAS_BLESSING_4 = 22947;    // ammo slot, no ranged strength but +2 prayer

    /**
     * Reproduces the blowpipe-ammo bug: a blowpipe loads its dart internally
     * and {@code GearMapper.buildEquipmentStats} already skips worn ammo for
     * one, so every ammo candidate is a full-DPS tie — but the optimiser's
     * ammo-slot ranking used to be DPS/proxy-driven (ranged strength), which
     * either never swaps on a tie (leaving whatever ammo was already
     * equipped) or prunes the zero-rstr prayer item away first. With a
     * blowpipe equipped, the freed ammo slot must go to the prayer-bonus item
     * instead of the higher-ranged-strength javelin.
     */
    @Test
    public void blowpipeWeapon_ammoSlotPrefersPrayerBonusOverRangedStrengthJavelin() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = TOXIC_BLOWPIPE;
        live[AMMO_SLOT] = DRAGON_JAVELIN; // starts on the highest-rstr ammo, as the bug describes

        Set<Integer> owned = new HashSet<>(Arrays.asList(TOXIC_BLOWPIPE, DRAGON_JAVELIN, RADAS_BLESSING_4));
        GearOptimizer.PriceSource prices = id -> owned.contains(id) ? 0L : 100_000_000L;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .owned(owned)
                .priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.RANGED)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("a blowpipe loadout must prefer the prayer-bonus ammo over the ranged-strength javelin",
                RADAS_BLESSING_4, itemIdInSlot(result, AMMO_SLOT));
    }

    // -------------------------------------------------- weapon-ammo compatibility

    private static final int DRAGON_ARROW = 11212;
    private static final int CRYSTAL_BOW = 23983;

    /**
     * The reported Cerberus bug, end-to-end: the player owns a Scorching bow
     * (ranged demonbane, +30% vs the demon Cerberus), a blowpipe, dragon
     * arrows, dragon javelins and a blessing, and is sitting on the blowpipe
     * with javelins worn. The RANGED search must recommend the Scorching bow
     * WITH the dragon arrows it can actually fire — before the compatibility
     * + ammo-pairing fixes it recommended javelins in the ammo slot of a bow
     * (their +150 rstr was summed as if a bow could fire them) and the
     * blowpipe incumbent could never lose to a bow tried without arrows.
     */
    @Test
    public void cerberusRanged_ownedScorchingBow_recommendedWithArrowsNeverJavelins() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = TOXIC_BLOWPIPE;
        live[AMMO_SLOT] = DRAGON_JAVELIN;

        Set<Integer> owned = new HashSet<>(Arrays.asList(
                SCORCHING_BOW, TOXIC_BLOWPIPE, DRAGON_ARROW, DRAGON_JAVELIN, RADAS_BLESSING_4));
        GearOptimizer.PriceSource prices = id -> owned.contains(id) ? 0L : 100_000_000L;

        GearOptimizer.Result result = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).owned(owned).priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.RANGED)
                .build());

        assertEquals("the demonbane Scorching bow must beat the blowpipe on a demon target",
                SCORCHING_BOW, weaponIdIn(result));
        assertEquals("the bow must be paired with the arrows it fires, never ballista javelins",
                DRAGON_ARROW, itemIdInSlot(result, AMMO_SLOT));
    }

    /**
     * A consuming weapon with NO compatible ammo available: worn javelins are
     * DPS-invisible behind a bow (GearMapper skips them), so the freed slot
     * must go to the prayer-bonus blessing on the DPS tie — a bow must never
     * be shown wearing ammo it cannot fire.
     */
    @Test
    public void bowWithoutOwnedArrows_ammoSlotFallsBackToBlessingNeverKeepsJavelins() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = SCORCHING_BOW;
        live[AMMO_SLOT] = DRAGON_JAVELIN;

        Set<Integer> owned = new HashSet<>(Arrays.asList(SCORCHING_BOW, DRAGON_JAVELIN, RADAS_BLESSING_4));
        GearOptimizer.PriceSource prices = id -> owned.contains(id) ? 0L : 100_000_000L;

        GearOptimizer.Result result = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).owned(owned).priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.RANGED)
                .build());

        assertEquals(SCORCHING_BOW, weaponIdIn(result));
        assertEquals("with no arrows owned, the DPS-invisible slot must hold the blessing, not javelins",
                RADAS_BLESSING_4, itemIdInSlot(result, AMMO_SLOT));
    }

    /**
     * A self-supplying bow (crystal bow) uses no worn ammo at all: worn
     * arrows contribute nothing, so — exactly like the blowpipe case above —
     * the ammo slot must fall to the best prayer blessing.
     */
    @Test
    public void crystalBow_ammoSlotPrefersBlessingOverArrowsItNeverFires() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = CRYSTAL_BOW;
        live[AMMO_SLOT] = DRAGON_ARROW;

        Set<Integer> owned = new HashSet<>(Arrays.asList(CRYSTAL_BOW, DRAGON_ARROW, RADAS_BLESSING_4));
        GearOptimizer.PriceSource prices = id -> owned.contains(id) ? 0L : 100_000_000L;

        GearOptimizer.Result result = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).owned(owned).priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.RANGED)
                .build());

        assertEquals(CRYSTAL_BOW, weaponIdIn(result));
        assertEquals(RADAS_BLESSING_4, itemIdInSlot(result, AMMO_SLOT));
    }

    // -------------------------------------------------- speed-blind pruning (blowpipe)

    /**
     * The blowpipe-pruning bug: the weapon slot used to be pruned to the top
     * {@link GearOptimizer#DEFAULT_CANDIDATES_PER_SLOT} by the target-blind
     * linear proxy {@code arange + 2*rstr}, which scores the 3-tick Toxic
     * blowpipe (raw 30 + 2*20 = 70) below EVERY slow high-bonus bow/crossbow/
     * ballista — its entire value is attack speed, invisible to the proxy —
     * so it was discarded before its real DPS was ever computed. Here it
     * competes against 13 affordable ranged weapons that ALL out-score it on
     * the raw proxy; ranked by real evaluated DPS versus a low-defence target
     * (where the fast blowpipe is genuinely best-in-slot, as in game) it must
     * survive pruning and win.
     */
    @Test
    public void blowpipeSurvivesWeaponPruning_andWinsOnALowDefenceTarget() {
        int[] live = emptyLoadout(); // nothing owned — every pick is a purchase

        final int HEAVY_BALLISTA = 19481;
        final int LIGHT_BALLISTA = 19478;
        final int ZARYTE_CROSSBOW = 26374;
        final int ARMADYL_CROSSBOW = 11785;
        final int RUNE_CROSSBOW = 9185;
        final int DRAGON_CROSSBOW = 21902;
        final int DRAGON_HUNTER_CROSSBOW = 21012;
        final int KARILS_CROSSBOW = 4734;
        final int HUNTERS_SUNLIGHT_CROSSBOW = 28869;
        final int THIRD_AGE_BOW = 12424;
        final int DARK_BOW = 11235;
        final int TWISTED_BOW = 20997;
        final int VENATOR_BOW = 27610;
        // Ammo for the competitors, so none of them is handicapped:
        final int RUNITE_BOLTS = 9144;

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        for (int weapon : new int[] { TOXIC_BLOWPIPE, HEAVY_BALLISTA, LIGHT_BALLISTA, ZARYTE_CROSSBOW,
                ARMADYL_CROSSBOW, RUNE_CROSSBOW, DRAGON_CROSSBOW, DRAGON_HUNTER_CROSSBOW, KARILS_CROSSBOW,
                HUNTERS_SUNLIGHT_CROSSBOW, THIRD_AGE_BOW, DARK_BOW, TWISTED_BOW, VENATOR_BOW }) {
            fixed.put(weapon, 100_000L);
        }
        fixed.put(RUNITE_BOLTS, 10_000L);
        fixed.put(DRAGON_ARROW, 10_000L);
        fixed.put(DRAGON_JAVELIN, 10_000L);
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);

        Monster dustDevil = MonsterRepository.getInstance()
                .byName("Dust devil (Catacombs of Kourend)").get();

        GearOptimizer.Result result = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, dustDevil, maxedPlayerTemplate())
                .budget(10_000_000L)
                .priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.RANGED)
                .build());

        assertEquals("the fast blowpipe must survive pruning and beat every slow high-bonus alternative",
                TOXIC_BLOWPIPE, weaponIdIn(result));
    }

    // -------------------------------------------------- style constraint (item #6e)

    private static final int ABYSSAL_BLUDGEON = 13263; // crush weapon
    private static final int MAGIC_SHORTBOW = 861;     // ranged weapon

    /**
     * The per-style optimum proof the 5-way selector rests on: with a slash
     * weapon (whip) AND a crush weapon (bludgeon) both owned, constraining the
     * search to SLASH must pick the whip and constraining to CRUSH must pick
     * the bludgeon — each result's driving style is of the constrained type.
     */
    @Test
    public void styleConstraint_crushAndSlashPickDifferentBestWeapons() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP;

        Set<Integer> owned = new HashSet<>(Arrays.asList(ABYSSAL_WHIP, ABYSSAL_BLUDGEON));
        GearOptimizer.PriceSource prices = id -> owned.contains(id) ? 0L : 100_000_000L;

        GearOptimizer.Result slash = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).owned(owned).priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.SLASH)
                .build());
        GearOptimizer.Result crush = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).owned(owned).priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.CRUSH)
                .build());

        assertEquals("SLASH constraint must pick the whip", ABYSSAL_WHIP, weaponIdIn(slash));
        assertEquals(com.ospulse.combat.CombatStyle.SLASH, slash.style().type());
        assertEquals("CRUSH constraint must pick the bludgeon", ABYSSAL_BLUDGEON, weaponIdIn(crush));
        assertEquals(com.ospulse.combat.CombatStyle.CRUSH, crush.style().type());
        assertTrue("both constrained results must actually compute a DPS",
                slash.dps().dps() > 0 && crush.dps().dps() > 0);
    }

    /**
     * The #6g scenario in engine terms: melee (whip) out-DPSes the owned bow,
     * so the UNCONSTRAINED search rightly picks the whip — but a RANGED
     * constraint must anchor to the bow, never "helpfully" fall back to melee.
     */
    @Test
    public void styleConstraint_rangedKeepsRangedWeaponEvenWhenMeleeDpsIsHigher() {
        int[] live = emptyLoadout();
        live[3] = MAGIC_SHORTBOW;

        Set<Integer> owned = new HashSet<>(Arrays.asList(ABYSSAL_WHIP, MAGIC_SHORTBOW));
        GearOptimizer.PriceSource prices = id -> owned.contains(id) ? 0L : 100_000_000L;

        GearOptimizer.Result unconstrained = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).owned(owned).priceSource(prices)
                .build());
        GearOptimizer.Result ranged = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).owned(owned).priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.RANGED)
                .build());

        assertEquals("sanity: unconstrained, the higher-DPS whip wins", ABYSSAL_WHIP, weaponIdIn(unconstrained));
        assertEquals("RANGED constraint must keep the bow", MAGIC_SHORTBOW, weaponIdIn(ranged));
        assertEquals(com.ospulse.combat.CombatStyle.RANGED, ranged.style().type());
        assertTrue("the constrained result's DPS is legitimately lower than melee's, never swapped for it",
                ranged.dps().dps() <= unconstrained.dps().dps());
    }

    /** Nothing owned/affordable can attack with the constrained type: the result is explicitly unusable (no style, zero DPS), not a silent fallback. */
    @Test
    public void styleConstraint_noWeaponOfThatTypeAvailable_yieldsNoStyleAndZeroDps() {
        int[] live = emptyLoadout();
        live[3] = ABYSSAL_WHIP;

        GearOptimizer.PriceSource prices = id -> id == ABYSSAL_WHIP ? 0L : 100_000_000L;

        GearOptimizer.Result result = GearOptimizer.optimize(GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L).priceSource(prices)
                .style(com.ospulse.combat.CombatStyle.MAGIC)
                .build());

        assertTrue("no magic weapon available -> zero DPS", result.dps().dps() == 0.0);
        assertEquals("no driving style must be reported", null, result.style());
    }
}
