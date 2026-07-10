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

    private static final int TOXIC_BLOWPIPE = 12926;    // self-supplying ranged 2H (loads darts)
    private static final int RUNE_CROSSBOW = 9185;       // worn-ammo ranged 1H
    private static final int BROAD_BOLTS = 11875;        // Kurask-legal ranged ammo
    private static final int MIRROR_SHIELD = 4156;       // shield-slot force-include (anti-Basilisk)
    private static final int DRAGON_2H_SWORD = 7158;     // two-handed slash weapon
    private static final int RUNE_SCIMITAR = 1333;       // one-handed slash weapon
    private static final int TWISTED_BOW = 20997;        // two-handed ranged weapon
    private static final int RUNE_PLATELEGS = 1079;      // DPS-neutral legs (no offensive bonus)

    private static int slotIdIn(GearOptimizer.Result result, int slotOrdinal) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == slotOrdinal) {
                return choice.itemId();
            }
        }
        return -1;
    }

    private static int weaponIdIn(GearOptimizer.Result result) {
        return slotIdIn(result, WhatIfLoadout.WEAPON_SLOT);
    }

    /**
     * A3: with a shield-slot item force-included (e.g. mirror shield vs
     * Basilisk), the optimiser must never recommend a two-handed / dual-wield
     * weapon, which would silently evict the required shield.
     */
    @Test
    public void forcedShield_neverRecommendsATwoHandedWeapon() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = RUNE_SCIMITAR;   // a one-handed weapon already equipped

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(RUNE_SCIMITAR, 0L);
        fixed.put(DRAGON_2H_SWORD, 0L);   // cheap + stronger: without the fix it would be chosen, evicting the shield
        fixed.put(MIRROR_SHIELD, 0L);
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(1_000_000L)
                .priceSource(prices)
                .style(CombatStyle.SLASH)
                .include(new HashSet<>(Arrays.asList(MIRROR_SHIELD)))
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertFalse("a two-handed weapon must not be chosen when a shield is force-required",
                weaponIdIn(result) == DRAGON_2H_SWORD);
        assertEquals("the force-required shield must be retained",
                MIRROR_SHIELD, slotIdIn(result, WhatIfLoadout.SHIELD_SLOT));
    }

    /**
     * B8-5: when a shield is force-included but the player has NO usable
     * one-handed weapon of the chosen style (only 2H weapons — the norm for
     * maxed magic/ranged: shadow, bowfa, tbow), the optimiser must still
     * recommend the 2H weapon instead of collapsing to zero swaps. The forced
     * 2H-exclusion (A3) only applies when a one-handed option actually exists.
     */
    @Test
    public void forcedShield_keepsTwoHandedWeaponWhenNoOneHandedOptionExists() {
        int[] live = emptyLoadout();

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(TWISTED_BOW, 0L);     // the only usable weapon for this style — and it's 2H
        fixed.put(MIRROR_SHIELD, 0L);
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(1_000_000L)
                .priceSource(prices)
                .style(CombatStyle.RANGED)
                .include(new HashSet<>(Arrays.asList(MIRROR_SHIELD)))
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("with no one-handed option, the 2H weapon must still be recommended (no zero-swap collapse)",
                TWISTED_BOW, weaponIdIn(result));
        assertTrue("the search must produce a usable style, not collapse to null", result.style() != null);
    }

    /**
     * A4: a blowpipe fires its own darts (not the worn ammo slot), so it can
     * never satisfy Kurask's broad-ammo gate — it must be gated out of the
     * ranged weapon search even though the worn ammo slot holds broad bolts.
     */
    @Test
    public void broadAmmoGate_neverRecommendsASelfSupplyingBlowpipe() {
        int[] live = emptyLoadout();

        // Kurask-style ranged gate: no allowed weapon ids, broad bolts are the only legal ammo.
        MonsterCombatRequirement kurask = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(),
                new HashSet<>(Arrays.asList(BROAD_BOLTS)),
                EnumSet.noneOf(CombatStyle.class),
                "note");

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(TOXIC_BLOWPIPE, 0L);   // cheap + high DPS: without the fix it would win the ranged search
        fixed.put(RUNE_CROSSBOW, 0L);
        fixed.put(BROAD_BOLTS, 0L);
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(1_000_000L)
                .priceSource(prices)
                .style(CombatStyle.RANGED)
                .combatRequirement(kurask)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertFalse("a blowpipe (self-supplying ammo) must never be recommended against a broad-ammo gate",
                weaponIdIn(result) == TOXIC_BLOWPIPE);

        // Cross-check the pure predicate: blowpipe rejected, a worn-ammo crossbow deferred to the ammo gate.
        assertFalse(kurask.permitsWeapon(TOXIC_BLOWPIPE, CombatStyle.RANGED, false));
        assertTrue(kurask.permitsWeapon(RUNE_CROSSBOW, CombatStyle.RANGED, true));
    }

    /**
     * B9-1: a worn-ammo ranged weapon whose worn/ownable ammo cannot satisfy a
     * broad-ammo gate (Kurask) must NOT yield a usable ranged result — even
     * though the weapon itself passes the weapon-slot gate. The seed loadout's
     * ammo is only DPS-invisible to the OLD evaluate(), which ignored the gate;
     * evaluate() must now reject a (weapon, style, ammo) the monster can't be
     * damaged by, so the whole Ranged style collapses to null (unusable) and is
     * never offered — exactly as Magic already correctly collapses.
     */
    @Test
    public void broadAmmoGate_dropsRangedWhenNoLegalAmmoAvailable() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = RUNE_CROSSBOW; // worn-ammo ranged weapon, no legal ammo to fire

        MonsterCombatRequirement kurask = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(),
                new HashSet<>(Arrays.asList(BROAD_BOLTS)),
                EnumSet.noneOf(CombatStyle.class),
                "note");

        java.util.Map<Integer, Long> fixed = new java.util.HashMap<>();
        fixed.put(RUNE_CROSSBOW, 0L);
        // Broad bolts are NOT owned and NOT affordable — no legal ammo exists.
        GearOptimizer.PriceSource prices = everyWeaponExpensiveExcept(fixed);

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(prices)
                .style(CombatStyle.RANGED)
                .combatRequirement(kurask)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertTrue("with no legal ammo, the ranged search must collapse to an unusable (null-style) result",
                result.style() == null);
    }

    /**
     * B9-1 companion: when the player DOES own the gate's legal ammo (broad
     * bolts), the ranged search must succeed and put that ammo in the ammo slot.
     */
    @Test
    public void broadAmmoGate_recommendsBroadAmmoWhenOwned() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = RUNE_CROSSBOW;

        MonsterCombatRequirement kurask = MonsterCombatRequirement.weaponGate(
                Collections.emptySet(),
                new HashSet<>(Arrays.asList(BROAD_BOLTS)),
                EnumSet.noneOf(CombatStyle.class),
                "note");

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .owned(new HashSet<>(Arrays.asList(RUNE_CROSSBOW, BROAD_BOLTS)))
                .style(CombatStyle.RANGED)
                .combatRequirement(kurask)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertTrue("with broad bolts owned, a usable ranged result must be produced", result.style() != null);
        assertEquals("the ammo slot must hold the gate-legal broad bolts",
                BROAD_BOLTS, slotIdIn(result, 13)); // slot 13 = ammo
    }

    /**
     * B9-2: the optimiser must fill DPS-neutral slots (legs, ring, etc.) with
     * the best wearable owned item rather than leaving them empty. Starting
     * from a naked loadout, plate legs contribute no offensive bonus so
     * equipping them never RAISES DPS — the DPS-only search used to leave the
     * slot empty ("recommend no legs"). A tie-break now fills the slot on a DPS
     * tie so the recommended loadout is a complete setup.
     */
    @Test
    public void fillsDpsNeutralSlotWithBestOwnedItem() {
        int[] live = emptyLoadout(); // naked

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, cerberus(), maxedPlayerTemplate())
                .budget(0L)
                .priceSource(everyWeaponExpensiveExcept(new java.util.HashMap<>())) // only owned gear is affordable
                .owned(new HashSet<>(Arrays.asList(RUNE_SCIMITAR, RUNE_PLATELEGS)))
                .style(CombatStyle.SLASH)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("a DPS-neutral legs slot must be filled with the owned plate legs, not left empty",
                RUNE_PLATELEGS, slotIdIn(result, 7)); // slot 7 = legs
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
