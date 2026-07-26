package com.ospulse.combat.optimizer;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterCombatRequirement;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.session.GearSnapshot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Proves the review finding this stage fixes: before it, {@link GearOptimizer}
 * gated candidates on {@code Request.combatRequirement()} but scored them by
 * calling {@link com.ospulse.combat.DpsCalculator#compute} without it, so
 * {@code DpsCalculator} independently re-resolved a (possibly different)
 * requirement from {@code target.name()} via {@code
 * MonsterCombatRequirementRepository}. A {@code DAMAGE_PENALTY}/{@code
 * DAMAGE_CAP} requirement never gates ({@code permitsWeapon}/{@code
 * permitsAmmo} are no-ops for those types — see {@link
 * MonsterCombatRequirement#permits}), so the two sources of truth could
 * silently disagree with nothing to catch it. Now the optimiser threads
 * {@code request.combatRequirement} straight into {@code DpsCalculator}'s
 * caller-resolved overloads, so scoring always matches whatever requirement
 * the caller supplied — never a second, independent lookup.
 */
public class GearOptimizerCallerResolvedRequirementTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    private static final int RUNE_SCIMITAR = 1333; // one-handed slash weapon

    private static int[] emptyLoadout() {
        int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
        Arrays.fill(ids, -1);
        return ids;
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

    /** Every item is unaffordable except the ones in {@code prices}, isolating the scenario. */
    private static GearOptimizer.PriceSource everyItemExpensiveExcept(java.util.Map<Integer, Long> prices) {
        return id -> prices.containsKey(id) ? prices.get(id) : 100_000_000L;
    }

    private static int slotIdIn(GearOptimizer.Result result, int slotOrdinal) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == slotOrdinal) {
                return choice.itemId();
            }
        }
        return -1;
    }

    /**
     * Resolves to NO curated {@link MonsterCombatRequirement} at all — not in
     * {@code monster_combat_requirements.json} under any name or base name
     * (mirrors {@code TargetDamageRuleTest.regressionMonster}). Any
     * requirement the optimiser scores against must therefore come from the
     * request, never from a repository re-lookup keyed on this name.
     */
    private static Monster unaffectedMonster() {
        return Monster.builder()
                .name("Unaffected Test Monster")
                .hitpoints(200)
                .defenceLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .magicLevel(1)
                .build();
    }

    /**
     * Resolves to a REAL bundled {@code DAMAGE_CAP} requirement (flat max hit
     * 4) via {@code monster_combat_requirements.json} — see {@code
     * HueycoatlDamageCapRegressionTest}, which pins its DpsCalculator numbers
     * directly. Used here only to prove a caller-supplied {@code null}
     * overrides that bundled default rather than the repository silently
     * winning.
     */
    private static Monster hueycoatlTail() {
        return Monster.builder()
                .name("The Hueycoatl (Tail)")
                .hitpoints(300)
                .defenceLevel(1)
                .defenceBonuses(0, 0, 0, 0, 0)
                .magicLevel(1)
                .build();
    }

    /**
     * THE regression that matters: a request whose {@code combatRequirement}
     * is a {@code DAMAGE_CAP} of 1 for a target whose NAME resolves to no
     * bundled requirement. Before this fix, {@code DpsCalculator} ignored the
     * request's rule entirely (it re-resolved "Unaffected Test Monster" from
     * the repository and found nothing), so the optimiser's chosen result
     * would carry the loadout's true, uncapped max hit. After the fix the
     * cap is authoritative: the optimiser's own scoring reflects it.
     */
    @Test
    public void callerSuppliedCap_appliesEvenWhenTargetsNameHasNoBundledRule() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = RUNE_SCIMITAR;

        HashMap<Integer, Long> fixed = new HashMap<>();
        fixed.put(RUNE_SCIMITAR, 0L);
        GearOptimizer.PriceSource prices = everyItemExpensiveExcept(fixed);

        MonsterCombatRequirement flatCapOfOne = MonsterCombatRequirement.damageCap(1, -1, "test-only flat cap of 1");

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, unaffectedMonster(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(prices)
                .style(CombatStyle.SLASH)
                .combatRequirement(flatCapOfOne)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("the weapon-slot gate never blocks a DAMAGE_CAP requirement, so the scimitar is still chosen",
                RUNE_SCIMITAR, slotIdIn(result, WhatIfLoadout.WEAPON_SLOT));
        assertEquals("scoring must reflect the caller-supplied cap, not the target name's (nonexistent) bundled rule",
                1, result.dps().maxHit());
    }

    /**
     * The reverse companion: a request explicitly supplying {@code null} (the
     * default when a caller never calls {@code .combatRequirement(...)}) for
     * a target whose NAME DOES have a bundled cap. If the repository were
     * still allowed to win, this would silently score capped at 4 despite the
     * caller never asking for that; instead the null must be authoritative,
     * so scoring is uncapped.
     */
    @Test
    public void callerSuppliedNull_scoresWithoutTheTargetsBundledRule() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = RUNE_SCIMITAR;

        HashMap<Integer, Long> fixed = new HashMap<>();
        fixed.put(RUNE_SCIMITAR, 0L);
        GearOptimizer.PriceSource prices = everyItemExpensiveExcept(fixed);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, hueycoatlTail(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(prices)
                .style(CombatStyle.SLASH)
                // combatRequirement deliberately left unset (null) — proves the
                // caller's null is authoritative, not a silent repository win.
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertTrue("a maxed player with a rune scimitar swings for well over Hueycoatl's bundled cap of 4 "
                        + "when that cap is not applied",
                result.dps().maxHit() > 4);
    }
}
