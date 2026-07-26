package com.ospulse.combat.optimizer;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.session.GearSnapshot;
import com.ospulse.ui.sections.gear.OwnedVariantResolver;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Issue #11 Stage 3: investigates a reported bug where the optimiser
 * recommends a plain MA1 Saradomin cape (id 2412, mdmg 0) for a MAGIC search
 * while the player owns an Imbued saradomin cape (deadman) (id 29617,
 * amagic +15, dmagic +15, mdmg +20 — per {@code equipment_stats.min.json}) —
 * a strictly better item — sitting in their bank. Mirrors
 * {@link GearOptimizerTest} / {@link GearOptimizerCombatGateTest}'s fixture
 * and setup style.
 *
 * <p><b>Finding (see the two tests below): the suffix gap in {@link
 * OwnedVariantResolver#SUFFIXES} is NOT what lets {@link GearOptimizer}
 * itself pick the plain cape over an owned 29617</b> — {@link
 * #magicSearch_ownedDeadmanCape_neverRecommendsWorsePlainCape} is a direct,
 * literal reproduction attempt (owned = {29617}, no exclusions) and PASSES
 * on the very first run, unmodified: 29617's id reaches the optimiser
 * exactly as owned, survives candidate pruning (owned items are exempt —
 * {@code mandatoryCandidate()}), and {@code CandidateScore.tieBreakScore}
 * (used to break DPS ties) sums EVERY offensive/defensive/prayer bonus
 * regardless of the search's style constraint — so 29617's higher magic
 * stats win outright on a MAGIC search, and even on a non-magic style (where
 * a cape contributes nothing to DPS) the tie-break still correctly prefers
 * 29617's higher total stat sum over 2412's. There is no "arbitrary
 * iteration/file order" tie-break in this codebase for this scenario.
 *
 * <p><b>The real end-to-end mechanism</b> — confirmed by {@link
 * #magicSearch_modeLockedExclusion_ownershipCrossMapDeterminesFallback} —
 * lives ABOVE {@link GearOptimizer}, in {@code GearSection}:
 * <ol>
 *   <li>{@code GearSection.restrictedItemIds()} adds every item whose name
 *   matches a "(deadman)/(bh)/(lms)/(beta)" mode-locked marker to the
 *   search's exclude set on EVERY optimiser search, "regardless of price or
 *   ownership" (its own javadoc) — a deliberate, already-tested policy from
 *   a prior QA pass ("bug C": {@code GearSectionGearPoolTest
 *   #deadmanNamedItem_isNeverSuggestedByTheOptimizer}). 29617 matches this
 *   pattern, so it can NEVER be the optimiser's own recommendation, no
 *   matter what {@code OwnedVariantResolver.SUFFIXES} contains — this part
 *   is intentional and out of Stage 3's scope to change.</li>
 *   <li>{@code GearSection.addVariantPlainForm} is the ONLY mechanism that
 *   credits the player with owning a NON-excluded, real counterpart item
 *   (here, "Imbued saradomin cape", ids 21791/24248) when they own a
 *   variant whose name carries a known suffix — but {@code SUFFIXES} lacked
 *   " (deadman)", so owning 29617 never cross-mapped to owning 21791/24248.
 *   Without that credit, the optimiser has no signal that the player
 *   already effectively owns a top-tier magic cape, and falls back to
 *   whatever IS an eligible, affordable candidate for the slot — the cheap
 *   plain cape, exactly as reported.</li>
 * </ol>
 * Once " (deadman)" is added to {@code SUFFIXES}, {@code
 * OwnedVariantResolver.plainFormId(29617)} resolves to the real (non-mode-
 * locked) "Imbued saradomin cape" id, {@code addVariantPlainForm} marks it
 * owned at 0gp, and the optimiser correctly recommends THAT cape instead of
 * the plain one — which is exactly what the second test below proves,
 * using the real {@link OwnedVariantResolver#plainFormId} call (not a
 * hand-rolled substitute) to build the owned set, mirroring {@code
 * GearSection.ownedPriceMap()}'s own construction.
 */
public class GearOptimizerOwnedDeadmanCapeTest {
    static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

    private static final int PLAIN_SARADOMIN_CAPE = 2412;             // mdmg 0
    private static final int IMBUED_SARADOMIN_CAPE_DEADMAN = 29617;   // amagic+15, dmagic+15, mdmg+20
    private static final int CAPE_SLOT = 1;

    // Self-cast magic weapon (no spellbook/rune plumbing needed — DpsCalculator
    // tries a null Spell against it, same as MagicDpsTest's fixtures) so the
    // MAGIC style search has a legal, nonzero-DPS weapon to anchor on.
    private static final int TRIDENT_OF_THE_SEAS = 11905;

    private static int[] emptyLoadout() {
        int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
        Arrays.fill(ids, -1);
        return ids;
    }

    private static Monster zulrahSerpentine() {
        return MonsterRepository.getInstance().byName("Zulrah (Serpentine)").get();
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

    private static int slotIdIn(GearOptimizer.Result result, int slotOrdinal) {
        for (GearOptimizer.SlotChoice choice : result.loadout()) {
            if (choice.slotOrdinal() == slotOrdinal) {
                return choice.itemId();
            }
        }
        return -1;
    }

    /**
     * Control: {@link GearOptimizer}'s OWN candidate/DPS/tie-break logic,
     * given a literal owned set with no exclusions, never picks the
     * strictly worse plain cape over an owned 29617 — passes unmodified,
     * proving the suffix gap is not what lets GearOptimizer itself get this
     * wrong. See the class javadoc.
     */
    @Test
    public void magicSearch_ownedDeadmanCape_neverRecommendsWorsePlainCape() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = TRIDENT_OF_THE_SEAS;

        Set<Integer> owned = new HashSet<>(Arrays.asList(
                TRIDENT_OF_THE_SEAS, IMBUED_SARADOMIN_CAPE_DEADMAN));

        Map<Integer, Long> fixed = new HashMap<>();
        fixed.put(TRIDENT_OF_THE_SEAS, 0L);
        fixed.put(IMBUED_SARADOMIN_CAPE_DEADMAN, 0L);
        fixed.put(PLAIN_SARADOMIN_CAPE, 500L); // cheap, buyable, strictly worse on magic stats
        GearOptimizer.PriceSource prices = id -> fixed.containsKey(id) ? fixed.get(id) : 100_000_000L;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, zulrahSerpentine(), maxedPlayerTemplate())
                .budget(1_000_000L)
                .owned(owned)
                .priceSource(prices)
                .style(CombatStyle.MAGIC)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertEquals("a MAGIC search must actually produce a usable result",
                CombatStyle.MAGIC, result.style() == null ? null : result.style().type());
        assertFalse("the plain Saradomin cape (mdmg 0) must never be recommended over the owned "
                        + "Imbued saradomin cape (deadman) (mdmg +20) on a MAGIC search",
                slotIdIn(result, CAPE_SLOT) == PLAIN_SARADOMIN_CAPE);
        assertEquals("the strictly better, owned deadman cape must be the cape-slot recommendation",
                IMBUED_SARADOMIN_CAPE_DEADMAN, slotIdIn(result, CAPE_SLOT));
    }

    /**
     * The REAL reported pick, reproduced end-to-end at the {@link
     * GearOptimizer} level by mirroring {@code GearSection}'s own wiring
     * rather than hand-waving it away:
     * <ul>
     *   <li>the owned set is built the same way {@code
     *   GearSection.ownedPriceMap()}/{@code addVariantPlainForm} build it —
     *   starting from the raw owned id (29617) and calling the REAL {@link
     *   OwnedVariantResolver#plainFormId} to (maybe) add its cross-mapped
     *   plain-form counterpart, exactly like production code;</li>
     *   <li>29617 is placed in the exclude set, mirroring {@code
     *   GearSection.restrictedItemIds()}'s unconditional, ownership-blind
     *   mode-locked-item exclusion (confirmed real and by-design via {@code
     *   GearSection.isModeLockedItem("Imbued saradomin cape (deadman)")} and
     *   {@code GearSectionGearPoolTest}'s existing coverage of the same
     *   policy for other "(deadman)" items).</li>
     * </ul>
     * Before " (deadman)" is added to {@code SUFFIXES}, {@code
     * plainFormId(29617)} returns null (no matching suffix), so the owned
     * set never gains the real, non-excluded "Imbued saradomin cape" —
     * leaving only the cheap plain cape eligible, exactly reproducing the
     * report. Adding the suffix makes {@code plainFormId} resolve the real
     * counterpart, which then wins on stats.
     */
    @Test
    public void magicSearch_modeLockedExclusion_ownershipCrossMapDeterminesFallback() {
        int[] live = emptyLoadout();
        live[WhatIfLoadout.WEAPON_SLOT] = TRIDENT_OF_THE_SEAS;

        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();

        // Exactly GearSection.ownedPriceMap()/addVariantPlainForm's own construction:
        // start from the raw owned deadman cape id, then cross-map its plain form
        // (if OwnedVariantResolver recognises the suffix) at price 0.
        Map<Integer, Long> ownedPrices = new HashMap<>();
        ownedPrices.put(TRIDENT_OF_THE_SEAS, 0L);
        ownedPrices.put(IMBUED_SARADOMIN_CAPE_DEADMAN, 0L);
        Integer crossMappedPlainId = OwnedVariantResolver.plainFormId(index, IMBUED_SARADOMIN_CAPE_DEADMAN);
        if (crossMappedPlainId != null) {
            ownedPrices.putIfAbsent(crossMappedPlainId, 0L);
        }

        // GearSection.restrictedItemIds()'s unconditional mode-locked exclusion —
        // 29617 ("Imbued saradomin cape (deadman)") always lands here, regardless
        // of price or ownership; that part of GearSection's policy is deliberate
        // and out of scope here, so it's mirrored directly rather than re-derived.
        Set<Integer> exclude = new HashSet<>(Arrays.asList(IMBUED_SARADOMIN_CAPE_DEADMAN));

        Map<Integer, Long> fixed = new HashMap<>();
        fixed.put(TRIDENT_OF_THE_SEAS, 0L);
        fixed.put(IMBUED_SARADOMIN_CAPE_DEADMAN, 0L);
        fixed.put(PLAIN_SARADOMIN_CAPE, 500L); // cheap, buyable
        if (crossMappedPlainId != null) {
            fixed.put(crossMappedPlainId, 0L);
        }
        GearOptimizer.PriceSource prices = id -> fixed.containsKey(id) ? fixed.get(id) : 100_000_000L;

        GearOptimizer.Request request = GearOptimizer.Request
                .builder(live, zulrahSerpentine(), maxedPlayerTemplate())
                .budget(1_000_000L)
                .owned(ownedPrices.keySet())
                .exclude(exclude)
                .priceSource(prices)
                .style(CombatStyle.MAGIC)
                .build();

        GearOptimizer.Result result = GearOptimizer.optimize(request);

        assertFalse("the plain Saradomin cape must never be the fallback once the deadman cape's "
                        + "ownership correctly cross-maps to its real, non-excluded counterpart",
                slotIdIn(result, CAPE_SLOT) == PLAIN_SARADOMIN_CAPE);
    }
}
