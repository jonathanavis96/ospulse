package com.ospulse.combat.optimizer;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterCombatRequirement;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.session.GearSnapshot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Task 5: the optimiser's weapon/ammo candidate search must be filtered by
 * the target monster's {@link MonsterCombatRequirement} gate (Task 1's pure
 * {@code permitsWeapon}/{@code permitsAmmo} predicates), not just report it.
 * Mirrors {@link GearOptimizerTest}'s fixtures/setup style.
 */
public class GearOptimizerCombatGateTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    private static final int ABYSSAL_WHIP = 4151;       // slash melee — the gated-out weapon
    private static final int DRAGON_SCIMITAR = 4587;    // slash melee — the only permitted weapon here

    private static int[] emptyLoadout() {
        int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
        Arrays.fill(ids, -1);
        return ids;
    }

    private static Monster cerberus() {
        // The gate is driven entirely by the explicit .combatRequirement(...)
        // below, not by the target's own bundled requirement data, so any
        // indexed monster works as the search target.
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

    /** Every weapon is unaffordable except the two fixtures, isolating the scenario. */
    private static GearOptimizer.PriceSource everyWeaponExpensiveExcept(java.util.Map<Integer, Long> prices) {
        return id -> prices.containsKey(id) ? prices.get(id) : 100_000_000L;
    }

    private static int weaponIdIn(GearOptimizer.Result result) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == WhatIfLoadout.WEAPON_SLOT) {
                return choice.itemId();
            }
        }
        return -1;
    }

    @Test
    public void combatGate_neverRecommendsAGatedOutWeapon_evenWhenCheaperAndHigherDps() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = DRAGON_SCIMITAR;

        // Kurask-style gate: only the scimitar (or MAGIC) can damage this
        // target — the whip is gated out even though it would otherwise win
        // on DPS/price (cheap + owned).
        MonsterCombatRequirement kurask = MonsterCombatRequirement.weaponGate(
                new HashSet<>(Arrays.asList(DRAGON_SCIMITAR)),
                Collections.emptySet(),
                EnumSet.of(CombatStyle.MAGIC),
                "note");

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(DRAGON_SCIMITAR, 0L);
        fixed.put(ABYSSAL_WHIP, 100L); // dirt cheap and objectively the stronger weapon
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(1_000_000L)
                .priceSource(prices)
                .style(CombatStyle.SLASH)
                .combatRequirement(kurask)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertFalse("the gated-out whip must never be chosen even though it is cheap and higher-DPS",
                weaponIdIn(result) == ABYSSAL_WHIP);
        assertEquals("the only permitted weapon (scimitar) must be chosen",
                DRAGON_SCIMITAR, weaponIdIn(result));

        // Cross-check: the pure predicate agrees with the optimiser's behaviour.
        assertTrue(kurask.permitsWeapon(DRAGON_SCIMITAR, CombatStyle.SLASH));
        assertFalse(kurask.permitsWeapon(ABYSSAL_WHIP, CombatStyle.SLASH));
    }
}
