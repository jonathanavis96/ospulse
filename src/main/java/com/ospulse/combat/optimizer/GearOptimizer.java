package com.ospulse.combat.optimizer;

import com.ospulse.combat.AmmoCompatibility;
import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentRequirementsRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterCombatRequirement;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.Spell;
import com.ospulse.combat.WeaponCategoryRepository;
import com.ospulse.combat.WeaponStyle;
import com.ospulse.session.GearMapper;

import net.runelite.api.gameval.ItemID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 3 gear optimiser (design spec section 3): given a starting loadout, a
 * target, and a candidate pool (owned items + anything affordable within a
 * gp budget), searches for the best-DPS loadout and ranks the upgrades over
 * the owned-only baseline by DPS-per-gp.
 *
 * <p>Pure and EDT-safe: every candidate lookup is bundled data
 * ({@link EquipmentIndexRepository} for the slot/name/2h index,
 * {@link BundledSlotStatsLookup} for numeric bonuses via
 * {@link GearMapper#buildEquipmentStats}), so this never needs the client
 * thread and can be driven straight from a Swing action handler (though
 * callers should still run a large search off the EDT — see the class-level
 * "Threading" note below — since the search itself can take tens of
 * milliseconds per slot at the default candidate width).
 *
 * <h2>Search strategy</h2>
 * <p>Per the design spec: prune each slot's candidates to the top-N by a
 * cheap per-style proxy bonus (kept small enough that {@code owned} and
 * {@code included} items are never pruned away, since those are mandatory
 * "you already have it / you demanded it" candidates regardless of how they
 * rank), then greedy-seed (pick each slot's best candidate independently,
 * ignoring interactions) followed by local search — repeatedly try every
 * single-slot swap and keep the first improving move, until a full pass
 * finds no improving swap (bounded by {@link #MAX_LOCAL_SEARCH_PASSES} to
 * guarantee termination). This is a heuristic, NOT an exhaustive search: set
 * effects (Void) are evaluated correctly because {@code EquipmentStats} is
 * rebuilt from the FULL loadout on every candidate evaluation (no per-slot
 * additive shortcut), but the local search can in principle miss a
 * multi-slot-simultaneous improvement that no single-slot swap reveals
 * (e.g. two pieces that only help DPS when equipped together and neither
 * alone). This is the same trade-off the design spec calls out ("greedy seed
 * then local search") to keep the search responsive in a side panel.
 *
 * <h2>Weapon slot special-casing</h2>
 * <p>A weapon candidate changes the AVAILABLE attack styles/spells, unlike
 * every other slot which only changes bonuses under a fixed style. So for
 * the weapon slot specifically, each candidate weapon is evaluated at its
 * OWN best style/spell (mirroring {@code GearSection}'s ranked style
 * picker), not the incumbent weapon's style — an upgrade search that only
 * compared different weapons under one fixed style would systematically
 * undervalue a weapon whose best style differs from the current one (e.g.
 * comparing a stab weapon only via a Slash style). Non-weapon slots use
 * whatever style/spell the WEAPON side of the current candidate loadout
 * resolves to.
 */
public final class GearOptimizer {
    /** Equipment slot ordinals actually searched — mirrors GearSection.SLOT_GRID's non-decorative entries. */
    public static final int[] SEARCHABLE_SLOTS = {0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13};

    /** Default per-slot candidate width after pruning (owned/included items are exempt — see class javadoc). */
    public static final int DEFAULT_CANDIDATES_PER_SLOT = 12;

    /** Ammo equipment slot ordinal — mirrors net.runelite.api.EquipmentInventorySlot.AMMO (see GearMapper). */
    private static final int AMMO_SLOT = 13;

    private static final int MAX_LOCAL_SEARCH_PASSES = 6;

    private GearOptimizer() {
    }

    /**
     * One resolved candidate: an item id with its GE price (0 for
     * owned/untradeable — owned items are always "affordable" regardless of
     * price since no purchase is needed).
     */
    public static final class Candidate {
        private final int itemId;
        private final long price;
        private final boolean owned;

        public Candidate(int itemId, long price, boolean owned) {
            this.itemId = itemId;
            this.price = price;
            this.owned = owned;
        }

        public int itemId() {
            return itemId;
        }

        /** GE price (0 if owned/untradeable); irrelevant to affordability when {@link #owned()}. */
        public long price() {
            return price;
        }

        public boolean owned() {
            return owned;
        }
    }

    /**
     * Resolves an item id's GE price. The design spec says "reuse whatever
     * price source the existing code already uses" — that's
     * {@code ItemManager.getItemPrice}/{@code RuneLiteItemValuation.unitValue},
     * both client-thread-only. This seam lets the caller supply a
     * pre-resolved price map (or any other source) so the optimiser itself
     * stays free of any {@code ItemManager}/client-thread dependency and can
     * run entirely off bundled data plus this one caller-supplied function.
     */
    @FunctionalInterface
    public interface PriceSource {
        /**
         * GE price for {@code itemId}. {@code 0} (or negative) is treated as
         * FREE/affordable regardless of budget — the convention owned/untradeable
         * items use (see {@link Candidate}'s javadoc and
         * {@code buildCandidatesForSlot}'s {@code price > request.budget} check,
         * which a {@code 0} price always passes). To mark a non-owned item as
         * unaffordable (e.g. unknown/unpriced), return a value greater than any
         * realistic budget, such as {@code Long.MAX_VALUE} — returning {@code 0}
         * for that case would incorrectly make the item look free.
         */
        long priceFor(int itemId);

        /** A price source that treats every item as free — useful for tests and an owned-only search. */
        static PriceSource zero() {
            return itemId -> 0L;
        }
    }

    /**
     * Search inputs (design spec section 3 "Inputs"). Immutable; build with
     * {@link Builder}.
     */
    public static final class Request {
        private final int[] liveItemIds;
        private final Monster target;
        private final long budget;
        private final Set<Integer> owned;
        private final Set<Integer> exclude;
        private final Set<Integer> include;
        private final int candidatesPerSlot;
        private final PlayerCombat.Builder playerTemplate;
        private final PriceSource priceSource;
        private final PriceSource riskValueSource;
        private final int expensiveItemCount;
        private final long expensiveItemThreshold;
        private final CombatStyle style;
        private final Spell.SpellBook spellBook;
        private final MonsterCombatRequirement combatRequirement;
        private final java.util.Map<String, Integer> playerBaseLevels;
        private final java.util.Map<Integer, Long> ownedItemPrices;

        private Request(Builder b) {
            this.liveItemIds = b.liveItemIds.clone();
            this.target = b.target;
            this.budget = b.budget;
            this.owned = Collections.unmodifiableSet(new HashSet<>(b.owned));
            this.exclude = Collections.unmodifiableSet(new HashSet<>(b.exclude));
            this.include = Collections.unmodifiableSet(new HashSet<>(b.include));
            this.candidatesPerSlot = b.candidatesPerSlot;
            this.playerTemplate = b.playerTemplate;
            this.priceSource = b.priceSource;
            // Default to the budget priceSource when the caller never wires a
            // dedicated risk source, so every existing caller/test (single
            // price concept) behaves unchanged — see Builder#riskValueSource.
            this.riskValueSource = b.riskValueSource != null ? b.riskValueSource : b.priceSource;
            this.expensiveItemCount = b.expensiveItemCount;
            this.expensiveItemThreshold = b.expensiveItemThreshold;
            this.style = b.style;
            this.spellBook = b.spellBook;
            this.combatRequirement = b.combatRequirement;
            this.playerBaseLevels = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(b.playerBaseLevels));
            this.ownedItemPrices = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(b.ownedItemPrices));
        }

        public static Builder builder(int[] liveItemIds, Monster target, PlayerCombat.Builder playerTemplate) {
            return new Builder(liveItemIds, target, playerTemplate);
        }

        public static final class Builder {
            private final int[] liveItemIds;
            private final Monster target;
            private final PlayerCombat.Builder playerTemplate;
            private long budget = 0L;
            private final Set<Integer> owned = new HashSet<>();
            private final Set<Integer> exclude = new HashSet<>();
            private final Set<Integer> include = new HashSet<>();
            private int candidatesPerSlot = DEFAULT_CANDIDATES_PER_SLOT;
            private PriceSource priceSource = PriceSource.zero();
            private PriceSource riskValueSource = null;
            private int expensiveItemCount = 0;
            private long expensiveItemThreshold = 0L;
            private CombatStyle style;
            private Spell.SpellBook spellBook;
            private MonsterCombatRequirement combatRequirement;
            private java.util.Map<String, Integer> playerBaseLevels = java.util.Collections.emptyMap();
            private java.util.Map<Integer, Long> ownedItemPrices = java.util.Collections.emptyMap();

            private Builder(int[] liveItemIds, Monster target, PlayerCombat.Builder playerTemplate) {
                this.liveItemIds = liveItemIds;
                this.target = target;
                this.playerTemplate = playerTemplate;
                // The player's own worn gear is always "owned" — free to use in the search.
                for (int id : liveItemIds) {
                    if (id > 0) {
                        owned.add(id);
                    }
                }
            }

            /** GP budget for GE purchases beyond owned gear (design spec: 0 = owned-only search). */
            public Builder budget(long budget) {
                this.budget = Math.max(0, budget);
                return this;
            }

            /** Adds owned item ids (e.g. from the player's bank) — always affordable regardless of price. */
            public Builder owned(Set<Integer> ownedItemIds) {
                this.owned.addAll(ownedItemIds);
                return this;
            }

            /** "Don't use this" — never appears in the result, even if owned. */
            public Builder exclude(Set<Integer> excludeItemIds) {
                this.exclude.addAll(excludeItemIds);
                return this;
            }

            /** "Must use this" — forced into the result (best loadout built around it). */
            public Builder include(Set<Integer> includeItemIds) {
                this.include.addAll(includeItemIds);
                return this;
            }

            public Builder candidatesPerSlot(int n) {
                this.candidatesPerSlot = Math.max(1, n);
                return this;
            }

            /** Supplies GE prices for affordability filtering + spend/DPS-per-gp reporting (default: everything free). */
            public Builder priceSource(PriceSource priceSource) {
                this.priceSource = priceSource != null ? priceSource : PriceSource.zero();
                return this;
            }

            /**
             * Supplies each item's real gp "risk value" for the expensive-item
             * cap ({@link #expensiveItemCount}/{@link #expensiveItemThreshold})
             * ONLY — completely separate from {@link #priceSource}, which drives
             * budget/affordability. This decoupling is the fix for the
             * under-counting bug where an OWNED item (free via {@link #owned})
             * or an untradeable item (unpurchasable via {@link #priceSource})
             * could dodge the risk cap despite being worth real gp: a tradeable
             * item's risk value is its GE price; an untradeable item's risk
             * value is the value of the tradeable components it represents
             * (see {@code com.ospulse.combat.RiskValuation}), regardless of
             * ownership or purchasability. {@code null} (the default) falls
             * back to {@link #priceSource} unchanged, preserving prior
             * behaviour for every existing caller/test that never wires a
             * dedicated risk source.
             */
            public Builder riskValueSource(PriceSource riskValueSource) {
                this.riskValueSource = riskValueSource;
                return this;
            }

            /**
             * How many "expensive" items (see {@link #expensiveItemThreshold}) the
             * caller wants allowed in the result, e.g. for wilderness/PvP risk
             * budgeting. Enforced by the search (see {@link Request#expensiveItemCount()})
             * only when the cap can bind — a positive threshold and an allowance
             * below the number of searchable slots.
             */
            public Builder expensiveItemCount(int count) {
                this.expensiveItemCount = Math.max(0, count);
                return this;
            }

            /**
             * The gp value at/above which an item counts as "expensive" for
             * {@link #expensiveItemCount}. A threshold of 0 (the default) means
             * "no item is expensive" and disables the cap (see
             * {@link Request#expensiveItemThreshold()}).
             */
            public Builder expensiveItemThreshold(long threshold) {
                this.expensiveItemThreshold = Math.max(0, threshold);
                return this;
            }

            /**
             * Constrains the magic DPS evaluation to a single spellbook — the
             * Standard/Ancient tab the user picked in the magic view — so the
             * gear search's magic DPS follows the chosen book instead of
             * silently scoring whichever book's best spell is globally higher.
             * {@code null} leaves it unconstrained (best of Standard/Ancient).
             */
            public Builder spellBook(Spell.SpellBook spellBook) {
                this.spellBook = spellBook;
                return this;
            }

            /**
             * Constrains the search to loadouts that deal the given damage type
             * (item #6e's 5-way Ranged/Magic/Crush/Slash/Stab selector):
             * <ul>
             *   <li>weapon-slot candidates are filtered to weapons whose real
             *       combat options (see {@link WeaponCategoryRepository}) include
             *       a style of this {@link CombatStyle};</li>
             *   <li>every candidate loadout is evaluated ONLY at styles of this
             *       type (a loadout whose weapon cannot attack with it evaluates
             *       to {@code null}/unusable rather than silently falling back to
             *       a different type);</li>
             *   <li>the per-slot pruning proxy ranks by this style's relevant
             *       bonuses (e.g. magic attack + magic damage for MAGIC) instead
             *       of the style-agnostic all-bonus sum, so e.g. magic armour is
             *       never pruned away by melee armour's bigger raw stat totals.</li>
             * </ul>
             * {@code null} (the default) keeps the historical unconstrained
             * behaviour: every weapon evaluated at its own best style.
             */
            public Builder style(CombatStyle style) {
                this.style = style;
                return this;
            }

            /**
             * Constrains the weapon/ammo slot candidates to those permitted by the
             * given monster's combat gate (e.g. Kurask's leaf-bladed-weapon-or-magic
             * restriction) at the requested {@link #style(CombatStyle)}. {@code null}
             * (the default) leaves the search unconstrained by any monster gate.
             */
            public Builder combatRequirement(MonsterCombatRequirement combatRequirement) {
                this.combatRequirement = combatRequirement;
                return this;
            }

            /**
             * The player's BASE (real, un-boosted) skill levels as lowercase
             * {@code skill name → level} (e.g. {@code {"attack": 70, ...}}), used
             * to drop candidate items the player cannot equip at their current
             * levels (see {@link EquipmentRequirementsRepository}). {@code null}
             * or empty (the default) disables the level gate entirely — every
             * item is assumed wearable, preserving the historical behaviour.
             */
            public Builder playerBaseLevels(java.util.Map<String, Integer> playerBaseLevels) {
                this.playerBaseLevels = playerBaseLevels != null ? playerBaseLevels : java.util.Collections.emptyMap();
                return this;
            }

            /**
             * Known GE/bank values for OWNED item ids, keyed by item id — used
             * exclusively by the expensive-item risk cap ({@link #expensiveItemCount}/
             * {@link #expensiveItemThreshold}) to price owned/equipped gear,
             * taking priority over {@link #priceSource} for any id present here.
             * Owned ids are commonly excluded from an async price-source lookup
             * (no need to check affordability on something already owned), so
             * that lookup either doesn't know an owned id at all or — worse,
             * once wrapped as "unresolved = unaffordable" — reports it as
             * {@link Long#MAX_VALUE}; without this map taking priority the risk
             * count would then price every such owned item as maximally
             * expensive regardless of its real value. An owned id absent from
             * this map still falls back to {@link #priceSource}. {@code null}/
             * empty (the default) leaves every owned item priced by {@link
             * #priceSource} unchanged, preserving historical behaviour.
             */
            public Builder ownedItemPrices(java.util.Map<Integer, Long> ownedItemPrices) {
                this.ownedItemPrices = ownedItemPrices != null ? ownedItemPrices : java.util.Collections.emptyMap();
                return this;
            }

            public Request build() {
                return new Request(this);
            }
        }

        /** The damage-type constraint for the search, or {@code null} for the unconstrained best-of-any-style search. */
        public CombatStyle style() {
            return style;
        }

        /** The monster combat gate constraining weapon/ammo candidates, or {@code null} if unconstrained. */
        public MonsterCombatRequirement combatRequirement() {
            return combatRequirement;
        }

        /** The player's base skill levels for the equip-requirement gate (empty = gate disabled). */
        public java.util.Map<String, Integer> playerBaseLevels() {
            return playerBaseLevels;
        }

        /**
         * The number of "expensive" items (see {@link #expensiveItemThreshold()})
         * the caller wants allowed in the result (e.g. for wilderness/PvP risk
         * budgeting). Enforced by {@link GearOptimizer#optimize}: the returned
         * loadout holds no more than this many items priced ABOVE the threshold
         * whenever that's achievable from the candidate pool — the local search
         * de-risks even a live loadout that starts over the cap. Counts owned
         * items too (an owned high-value item is still riskable). Inert unless
         * the threshold is positive and this allowance is below the searchable
         * slot count (an allowance that large can never bind).
         */
        public int expensiveItemCount() {
            return expensiveItemCount;
        }

        /**
         * The gp value STRICTLY ABOVE which an item is considered "expensive"
         * for {@link #expensiveItemCount()} — a price exactly equal to this
         * threshold is "spend up to X", i.e. within the ceiling. A threshold
         * of 0 (the default) means "no item is expensive" and disables the
         * cap in {@link GearOptimizer#optimize}.
         */
        public long expensiveItemThreshold() {
            return expensiveItemThreshold;
        }

        /**
         * Known GE/bank values for owned item ids — see {@link Builder#ownedItemPrices}.
         * No longer consulted by the expensive-item risk cap (see {@link #riskValueSource()}),
         * which now owns that job exclusively; this map/getter is left in place
         * for any other budget-side caller and for backward compatibility.
         */
        public java.util.Map<Integer, Long> ownedItemPrices() {
            return ownedItemPrices;
        }

        /**
         * The per-item risk-value source for the expensive-item cap — see
         * {@link Builder#riskValueSource}. Never {@code null}: defaults to
         * {@link #priceSource()} when the caller doesn't set one.
         */
        public PriceSource riskValueSource() {
            return riskValueSource;
        }
    }

    /** One slot's chosen item in the result loadout, with its role for the DPS-per-gp readout. */
    public static final class SlotChoice {
        private final int slotOrdinal;
        private final int itemId;
        private final boolean owned;
        private final long price;

        SlotChoice(int slotOrdinal, int itemId, boolean owned, long price) {
            this.slotOrdinal = slotOrdinal;
            this.itemId = itemId;
            this.owned = owned;
            this.price = price;
        }

        public int slotOrdinal() {
            return slotOrdinal;
        }

        public int itemId() {
            return itemId;
        }

        public boolean owned() {
            return owned;
        }

        /** GE price if this is a purchase (0 if owned or the slot is empty). */
        public long price() {
            return price;
        }
    }

    /** The optimiser's result: the best loadout found, its DPS, total spend, and the upgrade-vs-owned-only comparison. */
    public static final class Result {
        private final List<SlotChoice> loadout;
        private final DpsResult dps;
        private final WeaponStyle style;
        private final Spell spell;
        private final long totalSpend;
        private final double ownedOnlyDps;

        Result(List<SlotChoice> loadout, DpsResult dps, WeaponStyle style, Spell spell, long totalSpend, double ownedOnlyDps) {
            this.loadout = Collections.unmodifiableList(loadout);
            this.dps = dps;
            this.style = style;
            this.spell = spell;
            this.totalSpend = totalSpend;
            this.ownedOnlyDps = ownedOnlyDps;
        }

        public List<SlotChoice> loadout() {
            return loadout;
        }

        public DpsResult dps() {
            return dps;
        }

        /** The (possibly {@code null} for a spell-driven magic result) style the DPS above was computed with. */
        public WeaponStyle style() {
            return style;
        }

        /** The spell driving the DPS above, or {@code null} for a non-magic result. */
        public Spell spell() {
            return spell;
        }

        /** Total GE spend of every non-owned slot choice. */
        public long totalSpend() {
            return totalSpend;
        }

        /** DPS of the best owned-items-only loadout (budget=0 equivalent) — the baseline for the upgrade comparison. */
        public double ownedOnlyDps() {
            return ownedOnlyDps;
        }

        /** DPS gained over the owned-only baseline. */
        public double deltaDps() {
            return dps.dps() - ownedOnlyDps;
        }

        /** DPS gained per gp spent, or {@code 0} if nothing was spent (avoids a divide-by-zero / Infinity surprise). */
        public double dpsPerGp() {
            return totalSpend <= 0 ? 0.0 : deltaDps() / totalSpend;
        }
    }

    /**
     * Runs the search. Intended to be called off the EDT for anything beyond
     * a trivial candidate pool (see class javadoc) — it is pure computation
     * with no I/O, so any executor/background thread works; the caller
     * publishes the {@link Result} back to the EDT.
     */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GearOptimizer.class);

    public static Result optimize(Request request) {
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();

        // Build each slot's pruned candidate list once. The AMMO slot is
        // built FIRST because the weapon slot's DPS-ranked pruning pairs each
        // candidate weapon with its best affordable compatible ammo (a bow is
        // meaningless without arrows — see buildWeaponCandidates).
        List<Candidate> ammoCandidates = buildCandidatesForSlot(index, AMMO_SLOT, request, Collections.emptyList());
        List<List<Candidate>> candidatesBySlot = new ArrayList<>();
        int[] slotForIndex = new int[SEARCHABLE_SLOTS.length];
        int n = 0;
        for (int slot : SEARCHABLE_SLOTS) {
            slotForIndex[n++] = slot;
            candidatesBySlot.add(slot == AMMO_SLOT
                    ? ammoCandidates
                    : buildCandidatesForSlot(index, slot, request, ammoCandidates));
        }

        // Greedy seeding below is restricted to OWNED candidates (spend 0),
        // so the ammo paired into a weapon trial there must be owned too.
        List<Candidate> ownedAmmoCandidates = ownedOnly(ammoCandidates);

        // Seed: the live/owned loadout as the starting point for local search
        // (guarantees the search never returns worse than the player's own gear
        // when combined with the greedy weapon pass below).
        int[] current = request.liveItemIds.clone();
        applyForcedIncludes(current, request, index);

        // Greedy seed: independently pick each slot's best OWNED candidate
        // (weapon slot evaluated at its own best style — see class javadoc),
        // holding every other slot at its CURRENT (live) value. Seeding from
        // owned-only candidates (price 0) guarantees the seed's total spend
        // is always 0 <= budget, so the loadout the budget-guarded local
        // search below starts from is always feasible; a slot with no owned
        // candidate is left at its current/live value, same as before. This
        // purposefully ignores slot interactions; local search below fixes
        // that (within the budget).
        for (int i = 0; i < slotForIndex.length; i++) {
            int slot = slotForIndex[i];
            if (request.include.contains(current[slot])) {
                continue; // forced include already applied (incl. the weapon slot) — never second-guessed here
            }
            List<Candidate> ownedForSlot = new ArrayList<>();
            for (Candidate c : candidatesBySlot.get(i)) {
                if (c.owned()) {
                    ownedForSlot.add(c);
                }
            }
            if (!ownedForSlot.isEmpty()) {
                current = bestSingleSlotChoice(current, slot, ownedForSlot, request, ownedAmmoCandidates);
            }
        }

        // Local search: repeat full passes over every slot, applying the first
        // improving single-slot swap found, until a pass makes no change. A
        // swap is only accepted if it BOTH improves the objective AND keeps
        // the loadout's total (non-owned) spend within budget — this is what
        // bounds cumulative spend across slots (the per-item price check in
        // buildCandidatesForSlot only rules out items that could never be
        // affordable alone; it does not bound the sum across multiple slots).
        Evaluation currentEval = evaluate(current, request);
        for (int pass = 0; pass < MAX_LOCAL_SEARCH_PASSES; pass++) {
            boolean improved = false;
            for (int i = 0; i < slotForIndex.length; i++) {
                int slot = slotForIndex[i];
                if (request.include.contains(current[slot])) {
                    continue; // forced — never swapped away by local search
                }
                // Expensive-item risk of the loadout as it stands entering this
                // slot (recomputed per slot since an earlier slot's swap this
                // pass may have changed it) — a swap is judged first on whether
                // it moves this toward/away from the cap, then on DPS.
                int currentOverflow = expensiveOverflowOf(current, request);
                for (Candidate c : candidatesBySlot.get(i)) {
                    if (c.itemId() == current[slot]) {
                        continue;
                    }
                    int[] trial = applySlotChoice(current, slot, c.itemId());
                    if (slot == WhatIfLoadout.WEAPON_SLOT) {
                        // A weapon swap carries its ammo requirement with it: a
                        // bow tried while (say) a blessing or nothing is worn
                        // would otherwise score without any arrow ranged
                        // strength and lose to the incumbent — the classic
                        // two-slot interaction single-slot local search misses.
                        trial = withBestCompatibleAmmo(trial, ammoCandidates, request);
                    }
                    if (!withinBudget(trial, request)) {
                        continue; // would push cumulative spend over budget — reject regardless of DPS
                    }
                    Evaluation trialEval = evaluate(trial, request);
                    if (trialEval == null) {
                        continue;
                    }
                    int trialOverflow = expensiveOverflowOf(trial, request);
                    boolean better;
                    if (trialOverflow != currentOverflow) {
                        // Feasibility dominates DPS: de-risk toward the cap
                        // (accept a lower overflow), never add risk (reject a
                        // higher one). Equal overflow falls through to the
                        // DPS/tie-break decision below; an inactive cap keeps
                        // every overflow 0, so this is a no-op there.
                        better = trialOverflow < currentOverflow;
                    } else if (slot == AMMO_SLOT) {
                        // Ammo compares (DPS, then prayer on a DPS tie): worn
                        // ammo a weapon doesn't fire is DPS-invisible (see
                        // AmmoCompatibility/GearMapper), so among DPS-tied
                        // candidates the freed slot goes to the best blessing.
                        double currentDps = currentEval == null ? Double.NEGATIVE_INFINITY : currentEval.dps.dps();
                        double diff = trialEval.dps.dps() - currentDps;
                        better = diff > 1e-9
                                || (Math.abs(diff) <= 1e-9 && prayerBonusOf(c.itemId()) > prayerBonusOf(current[slot]));
                    } else if (currentEval == null) {
                        better = true;
                    } else {
                        // Same DPS tie-break as the greedy seed (B9-2): fill a
                        // DPS-neutral slot with the best-stat wearable item rather
                        // than leaving whatever the seed held (possibly empty).
                        double diff = trialEval.dps.dps() - currentEval.dps.dps();
                        better = diff > 1e-9
                                || (Math.abs(diff) <= 1e-9
                                    && tieBreakScore(c.itemId()) > tieBreakScore(current[slot]));
                    }
                    if (better) {
                        current = trial;
                        currentEval = trialEval;
                        improved = true;
                        break;
                    }
                }
            }
            if (!improved) {
                break;
            }
        }

        // Owned-only baseline: same search restricted to owned items (budget
        // effectively 0), for the DPS-per-gp comparison.
        double ownedOnlyDps = ownedOnlyBestDps(request, index);

        List<SlotChoice> loadoutOut = new ArrayList<>();
        long totalSpend = 0L;
        for (int slot : SEARCHABLE_SLOTS) {
            int itemId = current[slot];
            if (itemId <= 0) {
                continue;
            }
            boolean owned = request.owned.contains(itemId);
            long price = owned ? 0L : priceOf(itemId, request);
            if (!owned) {
                totalSpend += price;
            }
            loadoutOut.add(new SlotChoice(slot, itemId, owned, price));
        }

        // TEMP dev diagnostic (dev jar only; remove before Hub push): when the
        // expensive-item cap is active, dump per-slot the chosen id, the price
        // the cap actually sees, whether it counts as expensive, the candidate
        // pool size and how many of those candidates were at/below threshold —
        // so a "cap not working / junk picks" report can be pinned to either a
        // starved sub-threshold pool or a correctly-enforced-but-DPS-neutral
        // pick. Fires once per search, only when the cap is on.
        if (expensiveCapActive(request) && log.isInfoEnabled()) {
            long thr = request.expensiveItemThreshold();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < slotForIndex.length; i++) {
                int slot = slotForIndex[i];
                int id = current[slot];
                long capPrice;
                boolean exempt = FREE_REOBTAINABLE.contains(id);
                if (id <= 0 || exempt) {
                    capPrice = 0L;
                } else {
                    // Mirror expensiveItemCountOf exactly (now riskValueSource-driven)
                    // so the diagnostic shows the value the cap actually counts by.
                    capPrice = Math.max(0L, request.riskValueSource().priceFor(id));
                }
                List<Candidate> cands = candidatesBySlot.get(i);
                int subThr = 0;
                for (Candidate c : cands) {
                    if (c.price() <= thr) {
                        subThr++;
                    }
                }
                sb.append(String.format(java.util.Locale.ROOT,
                        " [slot=%d id=%d cap$=%d exp=%b%s cands=%d subThr=%d]",
                        slot, id, capPrice, !exempt && id > 0 && capPrice > thr,
                        exempt ? "(free)" : "", cands.size(), subThr));
            }
            log.info("optimizer-cap: threshold={} cap={} budget={} totalSpend={} expensiveInResult={}/{} |{}",
                    thr, request.expensiveItemCount(), request.budget, totalSpend,
                    expensiveItemCountOf(current, request), request.expensiveItemCount(), sb);
        }

        DpsResult finalDps = currentEval != null ? currentEval.dps : zeroDps();
        WeaponStyle finalStyle = currentEval != null ? currentEval.style : null;
        Spell finalSpell = currentEval != null ? currentEval.spell : null;
        return new Result(loadoutOut, finalDps, finalStyle, finalSpell, totalSpend, ownedOnlyDps);
    }

    // ------------------------------------------------------------- internals

    private static long priceOf(int itemId, Request request) {
        long price = request.priceSource.priceFor(itemId);
        return Math.max(0L, price);
    }

    /**
     * Total GE spend of every non-owned, non-empty slot in {@code itemIds} —
     * the same accounting {@link #optimize} uses to build the final
     * {@link Result#totalSpend()}, exposed here so the local search can
     * reject any swap that would push cumulative spend over budget (see
     * {@link #withinBudget}). Owned items are always 0 regardless of price.
     */
    private static long totalSpendOf(int[] itemIds, Request request) {
        long total = 0L;
        for (int slot : SEARCHABLE_SLOTS) {
            int itemId = slot < itemIds.length ? itemIds[slot] : -1;
            if (itemId <= 0 || request.owned.contains(itemId)) {
                continue;
            }
            total += priceOf(itemId, request);
            if (total < 0) {
                // Overflow guard: an absurd combination of prices summed past
                // Long.MAX_VALUE would wrap negative — clamp to MAX_VALUE so
                // the budget comparison below still correctly reads "over".
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    /**
     * True when {@code itemIds}' cumulative non-owned spend fits within
     * {@code request.budget}. Compares via subtraction-free addition (see
     * {@link #totalSpendOf}'s overflow guard) rather than assuming both sides
     * fit safely in a {@code long} sum, so an unbounded/very large budget
     * (e.g. {@code Long.MAX_VALUE}, however unlikely given
     * {@link Request.Builder#budget} clamps to non-negative) never
     * overflows into a false rejection.
     */
    private static boolean withinBudget(int[] itemIds, Request request) {
        return totalSpendOf(itemIds, request) <= request.budget;
    }

    /**
     * True when the expensive-item cap is an ACTIVE constraint: a positive
     * threshold (0 means "no item is expensive" — the disabled default) and an
     * allowance below the number of searchable slots (an allowance >= slots can
     * never bind, so the whole search runs unchanged). When inactive, every
     * {@link #expensiveOverflowOf} is 0 and the local search behaves exactly as
     * the pre-cap DPS-only search.
     */
    private static boolean expensiveCapActive(Request request) {
        return request.expensiveItemThreshold() > 0
                && request.expensiveItemCount() < SEARCHABLE_SLOTS.length;
    }

    /**
     * Untradeable items that are FREE to reclaim if lost (diary/quest re-obtainables),
     * so they carry no death risk and must never count against the expensive-item
     * ceiling ({@link #expensiveItemCountOf}). Extend as more are identified.
     *
     * <p>Ids verified against the decompiled {@code net.runelite.api.gameval.ItemID}
     * constants shipped in the {@code net.runelite:runelite-api} jar resolved on this
     * project's classpath (checked both {@code 1.12.31.1} and {@code 1.12.32} from the
     * local Gradle cache — identical in both). The RuneLite-generated constant names
     * don't match the in-game item names exactly: the Ardougne diary cloak is named
     * {@code ARDY_CAPE_*} (not {@code ARDY_CLOAK_*}), and Rada's blessing is named
     * {@code ZEAH_BLESSING_*} (Rada's blessing is the Kourend/Zeah region's diary
     * reward; {@code EASY/MEDIUM/HARD/ELITE} map to blessing tiers 1-4).
     *
     * <p><b>Rada's blessing is currently inert here:</b> it occupies the OSRS
     * "pocket" equipment slot, which post-dates and isn't modelled by
     * {@link net.runelite.api.EquipmentInventorySlot} (14 slots, no pocket entry) or
     * this optimizer's {@code itemIds[]} arrays ({@code GearSnapshot.EQUIPMENT_SLOT_COUNT
     * == 14}, {@link #SEARCHABLE_SLOTS} spans only those 14 indices). A pocket-slot item
     * can never appear in {@code itemIds[]}, so these 4 entries can never match in
     * {@link #expensiveItemCountOf}'s loop today — kept for correctness/documentation
     * and to future-proof this set if pocket-slot support is ever added to the
     * optimizer's equipment model. Ardougne cloak tiers (cape slot, index 1, which IS
     * searchable) are fully active.
     */
    private static final Set<Integer> FREE_REOBTAINABLE = Set.of(
            ItemID.ARDY_CAPE_EASY, ItemID.ARDY_CAPE_MEDIUM, ItemID.ARDY_CAPE_HARD, ItemID.ARDY_CAPE_ELITE,
            ItemID.ZEAH_BLESSING_EASY, ItemID.ZEAH_BLESSING_MEDIUM, ItemID.ZEAH_BLESSING_HARD, ItemID.ZEAH_BLESSING_ELITE
    );

    /**
     * Count of non-empty slots in {@code itemIds} holding an "expensive" item —
     * GE value STRICTLY ABOVE {@link Request#expensiveItemThreshold()} (an
     * item priced exactly at the threshold is "spend up to X", i.e. within
     * the ceiling, not exceeding it). Unlike
     * {@link #totalSpendOf} this counts OWNED items too: an owned high-value
     * item is still riskable gear in the wilderness/PvP, which is exactly what
     * the expensive-item cap protects against, so ownership (= free to equip)
     * must not exempt it from the risk count. The one exception is
     * {@link #FREE_REOBTAINABLE}: those ids are skipped (and never priced)
     * before this rule applies, since they carry no death risk at all.
     *
     * <p>Every item — owned or not, tradeable or not — is priced via {@link
     * Request#riskValueSource()} ONLY, completely separate from the budget
     * path ({@link Request#priceSource()}/{@link Request#ownedItemPrices()}).
     * This decoupling fixes two under-counting bugs the risk cap used to be
     * vulnerable to: (1) an owned item could be marked "owned at price 0" by
     * the caller (e.g. a variant's plain form) and dodge the cap entirely
     * even though it's genuinely worth real gp; (2) an untradeable item has
     * no budget price at all ({@link Request#priceSource()} treats it as
     * unpurchasable), which used to leave it unpriced/free for risk purposes
     * too. {@code riskValueSource} is built from real gp worth regardless of
     * ownership or purchasability (see {@code com.ospulse.combat.RiskValuation}),
     * so neither escape hatch works anymore; it defaults to {@link
     * Request#priceSource()} when the caller never sets a dedicated one,
     * preserving prior behaviour for callers that only supply one price
     * concept.
     */
    private static int expensiveItemCountOf(int[] itemIds, Request request) {
        long threshold = request.expensiveItemThreshold();
        int count = 0;
        for (int slot : SEARCHABLE_SLOTS) {
            int itemId = slot < itemIds.length ? itemIds[slot] : -1;
            if (itemId <= 0) {
                continue;
            }
            if (FREE_REOBTAINABLE.contains(itemId)) {
                // No death risk (free to reclaim), so it's never priced for the cap either.
                continue;
            }
            long price = Math.max(0L, request.riskValueSource.priceFor(itemId));
            if (price > threshold) {
                count++;
            }
        }
        return count;
    }

    /**
     * By how much {@code itemIds} exceeds the expensive-item allowance (0 when
     * within the cap, or the cap is inactive). The local search treats this as
     * a feasibility "violation" that dominates DPS — it drives the overflow
     * toward 0 first, then optimises DPS among equally-feasible loadouts. This
     * is what lets a starting loadout that already over-risks (the player's own
     * worn gear can, since the seed is never rejected up front) be DE-RISKED
     * rather than leaving the search stuck: a hard "reject any over-cap
     * loadout" gate would reject every single-slot step of a multi-item de-risk
     * too, since intermediate loadouts are still over the cap.
     */
    private static int expensiveOverflowOf(int[] itemIds, Request request) {
        if (!expensiveCapActive(request)) {
            return 0;
        }
        return Math.max(0, expensiveItemCountOf(itemIds, request) - request.expensiveItemCount());
    }

    private static int[] applySlotChoice(int[] itemIds, int slot, int itemId) {
        LoadoutOverride single = LoadoutOverride.empty().withSlot(slot, itemId);
        // Reuse WhatIfLoadout's 2H/shield exclusivity so a weapon/shield swap
        // during the search obeys the same rule the what-if UI enforces.
        if (slot == WhatIfLoadout.WEAPON_SLOT) {
            single = WhatIfLoadout.equipWeapon(LoadoutOverride.empty(), itemId);
        } else if (slot == WhatIfLoadout.SHIELD_SLOT) {
            single = WhatIfLoadout.equipShield(LoadoutOverride.empty(), itemIds, itemId);
        }
        int[] next = WhatIfLoadout.effectiveItemIds(itemIds, single);
        return next;
    }

    /**
     * The best single choice for one slot, holding every other slot fixed.
     * Weapon-slot trials get their best compatible ammo from
     * {@code ammoCandidates} paired in (see {@link #withBestCompatibleAmmo});
     * ammo-slot candidates compare by (DPS, then prayer bonus on a DPS tie) —
     * worn ammo a weapon doesn't fire is DPS-invisible (blowpipes ignore the
     * slot entirely, incompatible consumables are skipped by
     * {@code GearMapper}/{@link com.ospulse.combat.AmmoCompatibility}), so a
     * pure-DPS ranking would keep whichever candidate happened to be worn
     * instead of freeing the slot for the best blessing.
     */
    private static int[] bestSingleSlotChoice(int[] itemIds, int slot, List<Candidate> candidates, Request request,
                                              List<Candidate> ammoCandidates) {
        int[] best = itemIds;
        Evaluation bestEval = evaluate(itemIds, request);
        int bestPrayer = slot == AMMO_SLOT ? prayerBonusOf(slotItemId(itemIds, slot)) : 0;
        for (Candidate c : candidates) {
            int[] trial = applySlotChoice(itemIds, slot, c.itemId());
            if (slot == WhatIfLoadout.WEAPON_SLOT) {
                trial = withBestCompatibleAmmo(trial, ammoCandidates, request);
            }
            Evaluation trialEval = evaluate(trial, request);
            if (trialEval == null) {
                continue;
            }
            boolean better;
            int trialPrayer = slot == AMMO_SLOT ? prayerBonusOf(c.itemId()) : 0;
            if (bestEval == null) {
                better = true;
            } else if (slot == AMMO_SLOT) {
                double diff = trialEval.dps.dps() - bestEval.dps.dps();
                better = diff > 1e-9 || (Math.abs(diff) <= 1e-9 && trialPrayer > bestPrayer);
            } else {
                // DPS tie-break (B9-2): a DPS-neutral slot (legs, ring, most
                // armour under a fixed style) never RAISES DPS, so a pure ">"
                // test would leave it at its seed value — empty when starting
                // from naked gear ("recommend no legs"). On a DPS tie prefer the
                // higher-stat wearable item so the slot gets filled with the best
                // owned piece instead of being left empty.
                double diff = trialEval.dps.dps() - bestEval.dps.dps();
                better = diff > 1e-9
                        || (Math.abs(diff) <= 1e-9
                            && tieBreakScore(c.itemId()) > tieBreakScore(slotItemId(best, slot)));
            }
            if (better) {
                best = trial;
                bestEval = trialEval;
                bestPrayer = trialPrayer;
            }
        }
        return best;
    }

    /**
     * Pairs a weapon trial with the best affordable ammo it can actually
     * fire: when the trial's weapon consumes an ammo class the worn ammo
     * doesn't match, the highest-ranked in-class candidate that keeps the
     * loadout within budget is swapped into the ammo slot ({@code
     * ammoCandidates} arrives per-class best-first from
     * {@link #buildCandidatesForSlot}). Without this, a consuming weapon is
     * systematically undervalued at every seeding/swap step — a Scorching
     * bow tried over a blowpipe loadout would score without any arrow
     * ranged strength and always lose. No-op for weapons that use no worn
     * ammo, for already-compatible ammo, and when no in-class candidate fits
     * the budget.
     */
    private static int[] withBestCompatibleAmmo(int[] itemIds, List<Candidate> ammoCandidates, Request request) {
        AmmoCompatibility.AmmoClass needed =
                AmmoCompatibility.consumedClass(slotItemId(itemIds, WhatIfLoadout.WEAPON_SLOT));
        if (needed == null || AmmoCompatibility.classify(slotItemId(itemIds, AMMO_SLOT)) == needed) {
            return itemIds;
        }
        for (Candidate c : ammoCandidates) {
            if (AmmoCompatibility.classify(c.itemId()) != needed) {
                continue;
            }
            int[] trial = applySlotChoice(itemIds, AMMO_SLOT, c.itemId());
            if (withinBudget(trial, request)) {
                return trial;
            }
        }
        return itemIds;
    }

    /** The owned subset of {@code candidates}, order preserved (per-class best-first for ammo lists). */
    private static List<Candidate> ownedOnly(List<Candidate> candidates) {
        List<Candidate> owned = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.owned()) {
                owned.add(c);
            }
        }
        return owned;
    }

    private static int slotItemId(int[] itemIds, int slot) {
        return slot >= 0 && slot < itemIds.length ? itemIds[slot] : -1;
    }

    private static void applyForcedIncludes(int[] itemIds, Request request, EquipmentIndexRepository index) {
        for (int includedId : request.include) {
            EquipmentIndexRepository.Entry entry = index.entryFor(includedId);
            if (entry == null) {
                continue;
            }
            int[] forced = applySlotChoice(itemIds, entry.slotOrdinal(), includedId);
            System.arraycopy(forced, 0, itemIds, 0, itemIds.length);
        }
    }

    /** True if any force-included item occupies the shield slot (so a 2H weapon would evict it). */
    private static boolean forcesShieldSlot(Request request, EquipmentIndexRepository index) {
        for (int includedId : request.include) {
            EquipmentIndexRepository.Entry entry = index.entryFor(includedId);
            if (entry != null && entry.slotOrdinal() == WhatIfLoadout.SHIELD_SLOT) {
                return true;
            }
        }
        return false;
    }

    private static List<Candidate> buildCandidatesForSlot(EquipmentIndexRepository index, int slot, Request request,
                                                          List<Candidate> ammoCandidates) {
        List<EquipmentIndexRepository.Entry> all = index.forSlot(slot);
        List<Candidate> affordable = new ArrayList<>();
        boolean weaponSlotWithForcedShield =
                slot == WhatIfLoadout.WEAPON_SLOT && forcesShieldSlot(request, index);
        for (EquipmentIndexRepository.Entry e : all) {
            if (request.exclude.contains(e.itemId())) {
                continue;
            }
            if (slot == WhatIfLoadout.WEAPON_SLOT && !weaponSupportsStyle(e.itemId(), request.style)) {
                // Style-constrained search (item #6e): a weapon that cannot
                // attack with the requested damage type is never a candidate —
                // it would only ever evaluate to null under the constraint.
                continue;
            }
            MonsterCombatRequirement combatReq = request.combatRequirement;
            if (combatReq != null) {
                if (slot == WhatIfLoadout.WEAPON_SLOT
                        && !combatReq.permitsWeapon(e.itemId(), request.style,
                                AmmoCompatibility.consumedClass(e.itemId()) != null)) {
                    // Monster combat gate (e.g. Kurask): a weapon the monster
                    // cannot be damaged by is never a candidate. A self-supplying
                    // ranged weapon (blowpipe) can't satisfy a broad-ammo gate via
                    // the worn ammo slot, so it is gated out here too.
                    continue;
                }
                if (slot == AMMO_SLOT && !combatReq.permitsAmmo(e.itemId(), request.style)) {
                    continue;
                }
            }
            if (!request.playerBaseLevels.isEmpty()
                    && !EquipmentRequirementsRepository.getInstance().canEquip(e.itemId(), request.playerBaseLevels)) {
                // B9-5: never recommend gear the player can't equip at their
                // current (base) levels — a level-40-Attack player must not be
                // told to wear a Dragon scimitar (60 Attack). Applied to every
                // candidate (owned or not) since ownership doesn't grant the
                // level to wield it. Disabled when no levels are supplied.
                continue;
            }
            boolean owned = request.owned.contains(e.itemId());
            boolean included = request.include.contains(e.itemId());
            long price = owned ? 0L : priceOf(e.itemId(), request);
            if (!owned && !included && price > request.budget) {
                continue;
            }
            affordable.add(new Candidate(e.itemId(), price, owned));
        }

        if (weaponSlotWithForcedShield) {
            // A forced shield-slot item (mirror shield vs Basilisk) can't coexist
            // with a two-handed weapon. Prefer a one-handed weapon + the shield by
            // dropping 2H candidates — but ONLY when a usable one-handed weapon of
            // this style actually exists. Otherwise keep the 2H options so the
            // search doesn't collapse to zero swaps when the player only owns 2H
            // weapons of that style (Tumeken's shadow, bow of faerdhinen, ...);
            // the advisory shield then yields (equipWeapon empties the shield
            // slot for a 2H weapon). See B8-5.
            boolean hasOneHanded = false;
            for (Candidate c : affordable) {
                if (!WhatIfLoadout.isTwoHanded(c.itemId())) {
                    hasOneHanded = true;
                    break;
                }
            }
            if (hasOneHanded) {
                affordable.removeIf(c -> WhatIfLoadout.isTwoHanded(c.itemId()));
            }
        }

        if (slot == AMMO_SLOT) {
            return pruneAmmoCandidates(affordable, request);
        }
        if (slot == WhatIfLoadout.WEAPON_SLOT && request.target != null) {
            return pruneWeaponCandidatesByDps(affordable, request, ammoCandidates);
        }

        // Non-weapon slots (and the degenerate no-target case): prune to
        // top-N by a cheap proxy bonus (see proxyOffensiveScore) — the
        // requested style's relevant bonuses when the search is style-
        // constrained, else the style-agnostic sum of every offensive bonus.
        // These slots contribute additively under a fixed style, so the
        // proxy ranking is faithful enough; owned/included items are exempt
        // from pruning as always.
        affordable.sort(Comparator.comparingInt((Candidate c) -> -proxyOffensiveScore(c.itemId(), request.style)));
        List<Candidate> pruned = new ArrayList<>();
        Set<Integer> keptIds = new HashSet<>();
        for (Candidate c : affordable) {
            boolean mandatory = c.owned() || request.include.contains(c.itemId());
            if (mandatory || pruned.size() < request.candidatesPerSlot) {
                pruned.add(c);
                keptIds.add(c.itemId());
            }
        }
        retainSubCeilingCandidates(affordable, pruned, keptIds, request);
        return pruned;
    }

    /**
     * When the expensive-item cap ({@link #expensiveCapActive}) is active,
     * the top-N-by-stat/DPS pruning above can starve a slot of every
     * AT/BELOW-ceiling item: the highest scorers are exactly the "expensive"
     * ones a capped, non-premium slot must avoid, so a genuinely good cheap
     * item can be pruned away entirely — leaving the local search a choice
     * only between an over-ceiling item and whatever junk happened to
     * survive, unable to actually de-risk into anything good. This appends
     * up to {@code request.candidatesPerSlot} MORE items (not already in
     * {@code keptIds}) priced AT/BELOW the threshold, walking {@code
     * sortedBest} in the order the caller already ranked it (proxy stat for
     * non-weapon slots, real evaluated DPS for the weapon slot), so the
     * search always has a genuine sub-ceiling option to fall back to.
     *
     * <p>A no-op when the cap is inactive, so an inactive cap's candidate
     * list — and therefore its result — is unchanged from before this fix.
     */
    private static void retainSubCeilingCandidates(List<Candidate> sortedBest, List<Candidate> pruned,
                                                    Set<Integer> keptIds, Request request) {
        if (!expensiveCapActive(request)) {
            return;
        }
        long threshold = request.expensiveItemThreshold();
        int kept = 0;
        for (Candidate c : sortedBest) {
            if (kept >= request.candidatesPerSlot) {
                break;
            }
            if (keptIds.contains(c.itemId())) {
                continue;
            }
            if (c.price() <= threshold) {
                pruned.add(c);
                keptIds.add(c.itemId());
                kept++;
            }
        }
    }

    /**
     * Weapon-slot pruning by REAL evaluated DPS: each affordable weapon is
     * scored by {@link #evaluate} at its own best style/spell against the
     * actual target, applied over the live loadout with its best affordable
     * compatible ammo paired in ({@link #withBestCompatibleAmmo}) — then the
     * top {@code candidatesPerSlot} survive (owned/included are exempt, as
     * everywhere).
     *
     * <p>This replaces the old target-blind linear proxy ({@code arange +
     * 2*rstr} for ranged), which systematically mis-ranked everything whose
     * value isn't a raw bonus sum: attack SPEED (a 3-tick blowpipe's whole
     * advantage — measured: it ranked ~50th of the ranged pool and was
     * always pruned), target passives (demonbane/dragonbane/Twisted bow),
     * ammo the weapon brings vs. ammo it wears, and powered-staff scaling.
     * A per-candidate evaluation is a few microseconds of pure arithmetic;
     * even the unconstrained ~1500-weapon pool costs low tens of
     * milliseconds once per search, within the class javadoc's stated
     * budget. Weapons that evaluate to {@code null} (cannot attack under the
     * style/spellbook constraint) are dropped unless owned/included.
     */
    private static List<Candidate> pruneWeaponCandidatesByDps(List<Candidate> affordable, Request request,
                                                              List<Candidate> ammoCandidates) {
        final class Scored {
            final Candidate candidate;
            final double dps;

            Scored(Candidate candidate, double dps) {
                this.candidate = candidate;
                this.dps = dps;
            }
        }
        List<Scored> scored = new ArrayList<>(affordable.size());
        for (Candidate c : affordable) {
            int[] trial = applySlotChoice(request.liveItemIds, WhatIfLoadout.WEAPON_SLOT, c.itemId());
            trial = withBestCompatibleAmmo(trial, ammoCandidates, request);
            Evaluation eval = evaluate(trial, request);
            scored.add(new Scored(c, eval == null ? Double.NEGATIVE_INFINITY : eval.dps.dps()));
        }
        scored.sort((a, b) -> Double.compare(b.dps, a.dps));
        List<Candidate> pruned = new ArrayList<>();
        Set<Integer> keptIds = new HashSet<>();
        List<Candidate> viableByDps = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            boolean mandatory = s.candidate.owned() || request.include.contains(s.candidate.itemId());
            if (mandatory || (pruned.size() < request.candidatesPerSlot && s.dps != Double.NEGATIVE_INFINITY)) {
                pruned.add(s.candidate);
                keptIds.add(s.candidate.itemId());
            }
            if (s.dps != Double.NEGATIVE_INFINITY) {
                // Already DPS-sorted; weapons that can't attack under the
                // style/spellbook constraint are excluded so the expensive-
                // item retention below never resurrects an unusable weapon.
                viableByDps.add(s.candidate);
            }
        }
        retainSubCeilingCandidates(viableByDps, pruned, keptIds, request);
        return pruned;
    }

    /**
     * Ammo-slot pruning, per {@link com.ospulse.combat.AmmoCompatibility}
     * class: the searched weapon changes during the search (bow vs crossbow
     * vs blowpipe), so instead of one flat top-N (which the highest-rstr
     * class — javelins — would monopolise), the list keeps the top
     * {@code candidatesPerSlot} of EVERY consumable class ranked by the
     * ranged proxy, plus the top blessings ranked by prayer bonus, so
     * whatever weapon the search lands on finds its own class's best ammo
     * (and non-consuming weapons find a blessing). Owned/included are exempt
     * from pruning as always. Within each class the order is best-first,
     * which {@link #withBestCompatibleAmmo} relies on.
     */
    private static List<Candidate> pruneAmmoCandidates(List<Candidate> affordable, Request request) {
        java.util.Map<AmmoCompatibility.AmmoClass, List<Candidate>> byClass = new java.util.LinkedHashMap<>();
        for (AmmoCompatibility.AmmoClass ammoClass : AmmoCompatibility.AmmoClass.values()) {
            byClass.put(ammoClass, new ArrayList<>());
        }
        List<Candidate> unclassified = new ArrayList<>();
        for (Candidate c : affordable) {
            AmmoCompatibility.AmmoClass ammoClass = AmmoCompatibility.classify(c.itemId());
            (ammoClass == null ? unclassified : byClass.get(ammoClass)).add(c);
        }

        List<Candidate> pruned = new ArrayList<>();
        for (java.util.Map.Entry<AmmoCompatibility.AmmoClass, List<Candidate>> entry : byClass.entrySet()) {
            List<Candidate> group = entry.getValue();
            if (entry.getKey().isConsumable()) {
                // Ranged strength drives consumable ammo's DPS contribution.
                group.sort(Comparator.comparingInt((Candidate c) ->
                        -proxyOffensiveScore(c.itemId(), CombatStyle.RANGED)));
            } else {
                // Blessings are DPS-invisible — prayer bonus is their value.
                group.sort(Comparator.comparingInt((Candidate c) -> -prayerBonusOf(c.itemId())));
            }
            addPrunedGroup(pruned, group, request);
        }
        // Unknown/unclassifiable ammo (not expected with current data): keep
        // the old proxy ranking so nothing silently disappears.
        unclassified.sort(Comparator.comparingInt((Candidate c) ->
                -proxyOffensiveScore(c.itemId(), CombatStyle.RANGED)));
        addPrunedGroup(pruned, unclassified, request);
        return pruned;
    }

    /** Appends {@code group}'s top {@code candidatesPerSlot} entries to {@code out} (owned/included always kept). */
    private static void addPrunedGroup(List<Candidate> out, List<Candidate> group, Request request) {
        int kept = 0;
        for (Candidate c : group) {
            boolean mandatory = c.owned() || request.include.contains(c.itemId());
            if (mandatory || kept < request.candidatesPerSlot) {
                out.add(c);
                kept++;
            }
        }
    }

    /**
     * A DPS-tie tie-break score: the sum of an item's every offensive, defensive
     * and prayer bonus. Used to fill DPS-neutral slots (B9-2) with the best
     * wearable owned piece on a DPS tie; an empty/unknown slot scores lowest so
     * any real item beats "wear nothing".
     */
    private static int tieBreakScore(int itemId) {
        if (itemId <= 0) {
            return Integer.MIN_VALUE;
        }
        com.ospulse.combat.EquipmentStatsRepository.Stats s =
                com.ospulse.combat.EquipmentStatsRepository.getInstance().statsFor(itemId);
        if (s == null) {
            return Integer.MIN_VALUE + 1;
        }
        return s.astab() + s.aslash() + s.acrush() + s.amagic() + s.arange()
                + s.dstab() + s.dslash() + s.dcrush() + s.dmagic() + s.drange()
                + s.str() + s.rstr() + (int) s.mdmg() + s.prayer();
    }

    /** The prayer bonus of one item (0 if unknown), for the ammo-slot-ignored-by-weapon ranking exception. */
    private static int prayerBonusOf(int itemId) {
        com.ospulse.combat.EquipmentStatsRepository.Stats s = com.ospulse.combat.EquipmentStatsRepository.getInstance().statsFor(itemId);
        return s == null ? 0 : s.prayer();
    }

    /** True when the weapon id's real combat options include a style of {@code type} ({@code null} type = no constraint). */
    private static boolean weaponSupportsStyle(int weaponId, CombatStyle type) {
        if (type == null) {
            return true;
        }
        for (WeaponStyle style : WeaponCategoryRepository.getInstance().stylesForItem(weaponId)) {
            if (style.type() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cheap pruning proxy for one item. Unconstrained ({@code style ==
     * null}): the historical sum of every offensive bonus. Style-constrained:
     * only the bonuses that actually feed that style's DPS — otherwise e.g. a
     * MAGIC-constrained search would prune Ancestral (small raw totals) in
     * favour of melee armour whose big slash/str numbers are irrelevant to the
     * constraint. Weights: magic damage is a percentage (1% is worth far more
     * than 1 accuracy point) and ranged strength scales the max hit directly
     * (keeps ammo/blessing candidates ranked above accuracy-only picks).
     */
    private static int proxyOffensiveScore(int itemId, CombatStyle style) {
        com.ospulse.combat.EquipmentStatsRepository.Stats s = com.ospulse.combat.EquipmentStatsRepository.getInstance().statsFor(itemId);
        if (s == null) {
            return Integer.MIN_VALUE;
        }
        if (style == null) {
            return s.astab() + s.aslash() + s.acrush() + s.arange() + s.amagic() + s.str() + s.rstr() + (int) s.mdmg();
        }
        switch (style) {
            case STAB:
                return s.astab() + s.str();
            case SLASH:
                return s.aslash() + s.str();
            case CRUSH:
                return s.acrush() + s.str();
            case RANGED:
                return s.arange() + 2 * s.rstr();
            case MAGIC:
                return s.amagic() + (int) (10 * s.mdmg());
            default:
                return s.astab() + s.aslash() + s.acrush() + s.arange() + s.amagic() + s.str() + s.rstr() + (int) s.mdmg();
        }
    }

    /** One fully-evaluated candidate loadout: its best style/spell and resulting DPS. */
    private static final class Evaluation {
        final DpsResult dps;
        final WeaponStyle style;
        final Spell spell;

        Evaluation(DpsResult dps, WeaponStyle style, Spell spell) {
            this.dps = dps;
            this.style = style;
            this.spell = spell;
        }
    }

    /**
     * Evaluates one full loadout: resolves its weapon's real styles/spells
     * (see {@link WeaponCategoryRepository}) and returns the BEST-DPS
     * style/spell for it — mirroring {@code GearSection}'s ranked picker so
     * the optimiser never undervalues a weapon whose best style differs from
     * another candidate's. Returns {@code null} if nothing is computable
     * (e.g. no target).
     */
    private static Evaluation evaluate(int[] itemIds, Request request) {
        if (request.target == null) {
            return null;
        }
        EquipmentStats stats = GearMapper.buildEquipmentStats(itemIds, WhatIfLoadout.WEAPON_SLOT, BundledSlotStatsLookup.INSTANCE);
        int weaponId = itemIds.length > WhatIfLoadout.WEAPON_SLOT ? itemIds[WhatIfLoadout.WEAPON_SLOT] : -1;
        List<WeaponStyle> styles = WeaponCategoryRepository.getInstance().stylesForItem(weaponId);

        DpsResult best = null;
        WeaponStyle bestStyle = null;
        Spell bestSpell = null;

        boolean poweredStaff = stats.poweredStaff().applies();
        int ammoId = itemIds.length > AMMO_SLOT ? itemIds[AMMO_SLOT] : -1;
        boolean weaponUsesWornAmmo = AmmoCompatibility.consumedClass(weaponId) != null;
        for (WeaponStyle style : styles) {
            if (request.style != null && style.type() != request.style) {
                // Style-constrained search (item #6e): only styles of the
                // requested damage type may drive the DPS. A weapon with no
                // such style yields best == null -> this loadout is unusable
                // under the constraint (never silently scored at another type).
                continue;
            }
            if (request.combatRequirement != null
                    && (!request.combatRequirement.permitsWeapon(weaponId, style.type(), weaponUsesWornAmmo)
                        || !request.combatRequirement.permitsAmmo(ammoId, style.type()))) {
                // Monster combat gate (B9-1): a (weapon, style, worn-ammo) combo
                // the monster cannot be damaged by scores no DPS. The candidate
                // pruning already drops gated weapons/ammo, but the SEED loadout
                // carries the player's live worn ammo, which pruning never sees —
                // so without this check a bow + non-broad worn arrows would score
                // a bogus positive ranged DPS vs Kurask and wrongly offer Ranged.
                // Mirrors GearSection's readout gate (permits()) so the two agree.
                continue;
            }
            PlayerCombat player = request.playerTemplate.stance(style.stance()).build();
            if (style.type() == CombatStyle.MAGIC) {
                if (poweredStaff) {
                    DpsResult r = DpsCalculator.compute(stats, player, CombatStyle.MAGIC, request.target, (Spell) null);
                    if (best == null || r.dps() > best.dps()) {
                        best = r;
                        bestStyle = style;
                        bestSpell = null;
                    }
                } else {
                    for (Spell spell : Spell.values()) {
                        if (spell.book() != Spell.SpellBook.STANDARD && spell.book() != Spell.SpellBook.ANCIENT) {
                            continue;
                        }
                        if (request.spellBook != null && spell.book() != request.spellBook) {
                            // Constrained to the magic view's selected spellbook
                            // tab: the gear DPS follows the book the user chose,
                            // not whichever book's best spell is globally higher.
                            continue;
                        }
                        if (!spell.isCastableWith(weaponId)) {
                            // Mirrors GearSection's ranked spell picker: e.g. Iban
                            // Blast must never inflate a non-Iban's-staff weapon.
                            continue;
                        }
                        DpsResult r = DpsCalculator.compute(stats, player, CombatStyle.MAGIC, request.target, spell);
                        if (best == null || r.dps() > best.dps()) {
                            best = r;
                            bestStyle = style;
                            bestSpell = spell;
                        }
                    }
                }
            } else {
                DpsResult r = DpsCalculator.compute(stats, player, style.type(), request.target, 0);
                if (best == null || r.dps() > best.dps()) {
                    best = r;
                    bestStyle = style;
                    bestSpell = null;
                }
            }
        }
        return best == null ? null : new Evaluation(best, bestStyle, bestSpell);
    }

    private static double ownedOnlyBestDps(Request request, EquipmentIndexRepository index) {
        int[] current = request.liveItemIds.clone();
        // Owned ammo, best-first by the ranged proxy, for weapon-trial ammo
        // pairing (see withBestCompatibleAmmo) — an owned bow's baseline DPS
        // must include the player's own best arrows.
        List<Candidate> ownedAmmo = new ArrayList<>();
        for (EquipmentIndexRepository.Entry e : index.forSlot(AMMO_SLOT)) {
            if (request.owned.contains(e.itemId()) && !request.exclude.contains(e.itemId())) {
                ownedAmmo.add(new Candidate(e.itemId(), 0L, true));
            }
        }
        ownedAmmo.sort(Comparator.comparingInt((Candidate c) ->
                -proxyOffensiveScore(c.itemId(), CombatStyle.RANGED)));
        for (int slot : SEARCHABLE_SLOTS) {
            List<Candidate> ownedCandidates = new ArrayList<>();
            for (EquipmentIndexRepository.Entry e : index.forSlot(slot)) {
                if (request.owned.contains(e.itemId()) && !request.exclude.contains(e.itemId())) {
                    ownedCandidates.add(new Candidate(e.itemId(), 0L, true));
                }
            }
            current = bestSingleSlotChoice(current, slot, ownedCandidates, request, ownedAmmo);
        }
        // One local-search pass for interactions among owned items too (cheap — owned pools are small).
        for (int pass = 0; pass < MAX_LOCAL_SEARCH_PASSES; pass++) {
            boolean improved = false;
            for (int slot : SEARCHABLE_SLOTS) {
                List<Candidate> ownedCandidates = new ArrayList<>();
                for (EquipmentIndexRepository.Entry e : index.forSlot(slot)) {
                    if (request.owned.contains(e.itemId()) && !request.exclude.contains(e.itemId())) {
                        ownedCandidates.add(new Candidate(e.itemId(), 0L, true));
                    }
                }
                Evaluation before = evaluate(current, request);
                int[] afterArr = bestSingleSlotChoice(current, slot, ownedCandidates, request, ownedAmmo);
                Evaluation after = evaluate(afterArr, request);
                if (after != null && (before == null || after.dps.dps() > before.dps.dps() + 1e-9)) {
                    current = afterArr;
                    improved = true;
                }
            }
            if (!improved) {
                break;
            }
        }
        Evaluation eval = evaluate(current, request);
        return eval == null ? 0.0 : eval.dps.dps();
    }

    private static DpsResult zeroDps() {
        return new DpsResult(0, 0, 0, 0, 0, 0, false);
    }
}
