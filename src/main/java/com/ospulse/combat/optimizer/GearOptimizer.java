package com.ospulse.combat.optimizer;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.Spell;
import com.ospulse.combat.WeaponCategoryRepository;
import com.ospulse.combat.WeaponStyle;
import com.ospulse.session.GearMapper;
import com.ospulse.session.GearSnapshot;

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
        private final int expensiveItemCount;
        private final long expensiveItemThreshold;
        private final CombatStyle style;

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
            this.expensiveItemCount = b.expensiveItemCount;
            this.expensiveItemThreshold = b.expensiveItemThreshold;
            this.style = b.style;
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
            private int expensiveItemCount = 0;
            private long expensiveItemThreshold = 0L;
            private CombatStyle style;

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
             * How many "expensive" items (see {@link #expensiveItemThreshold}) the
             * caller wants allowed in the result, e.g. for wilderness/PvP risk
             * budgeting. Captured/plumbed through to {@link Request} only — the
             * search does not yet enforce this (see {@link Request#expensiveItemCount()}).
             */
            public Builder expensiveItemCount(int count) {
                this.expensiveItemCount = Math.max(0, count);
                return this;
            }

            /**
             * The gp value at/above which an item counts as "expensive" for
             * {@link #expensiveItemCount}. Captured/plumbed through to
             * {@link Request} only — not yet enforced by the search (see
             * {@link Request#expensiveItemThreshold()}).
             */
            public Builder expensiveItemThreshold(long threshold) {
                this.expensiveItemThreshold = Math.max(0, threshold);
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

            public Request build() {
                return new Request(this);
            }
        }

        /** The damage-type constraint for the search, or {@code null} for the unconstrained best-of-any-style search. */
        public CombatStyle style() {
            return style;
        }

        /**
         * The number of "expensive" items (see {@link #expensiveItemThreshold()})
         * the caller wants allowed in the result (e.g. for wilderness/PvP risk
         * budgeting). NOT YET enforced by {@link GearOptimizer#optimize} — captured
         * here so the UI can collect + persist the setting ahead of a later pass
         * that wires it into the search itself.
         */
        public int expensiveItemCount() {
            return expensiveItemCount;
        }

        /**
         * The gp value at/above which an item is considered "expensive" for
         * {@link #expensiveItemCount()}. NOT YET enforced by
         * {@link GearOptimizer#optimize} — see that method's javadoc note.
         */
        public long expensiveItemThreshold() {
            return expensiveItemThreshold;
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
    public static Result optimize(Request request) {
        EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();

        // Build each slot's pruned candidate list once.
        List<List<Candidate>> candidatesBySlot = new ArrayList<>();
        int[] slotForIndex = new int[SEARCHABLE_SLOTS.length];
        int n = 0;
        for (int slot : SEARCHABLE_SLOTS) {
            slotForIndex[n++] = slot;
            candidatesBySlot.add(buildCandidatesForSlot(index, slot, request));
        }

        // Seed: the live/owned loadout as the starting point for local search
        // (guarantees the search never returns worse than the player's own gear
        // when combined with the greedy weapon pass below).
        int[] current = request.liveItemIds.clone();
        applyForcedIncludes(current, request, index);

        // Greedy seed: independently pick each slot's best candidate (weapon
        // slot evaluated at its own best style — see class javadoc), holding
        // every other slot at its CURRENT (live) value. This purposefully
        // ignores slot interactions; local search below fixes that.
        for (int i = 0; i < slotForIndex.length; i++) {
            int slot = slotForIndex[i];
            if (request.include.contains(current[slot])) {
                continue; // forced include already applied (incl. the weapon slot) — never second-guessed here
            }
            current = bestSingleSlotChoice(current, slot, candidatesBySlot.get(i), request);
        }

        // Local search: repeat full passes over every slot, applying the first
        // improving single-slot swap found, until a pass makes no change.
        Evaluation currentEval = evaluate(current, request);
        for (int pass = 0; pass < MAX_LOCAL_SEARCH_PASSES; pass++) {
            boolean improved = false;
            for (int i = 0; i < slotForIndex.length; i++) {
                int slot = slotForIndex[i];
                if (request.include.contains(current[slot])) {
                    continue; // forced — never swapped away by local search
                }
                for (Candidate c : candidatesBySlot.get(i)) {
                    if (c.itemId() == current[slot]) {
                        continue;
                    }
                    int[] trial = applySlotChoice(current, slot, c.itemId());
                    Evaluation trialEval = evaluate(trial, request);
                    if (trialEval != null && (currentEval == null || trialEval.dps.dps() > currentEval.dps.dps() + 1e-9)) {
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

    private static int[] bestSingleSlotChoice(int[] itemIds, int slot, List<Candidate> candidates, Request request) {
        int[] best = itemIds;
        Evaluation bestEval = evaluate(itemIds, request);
        for (Candidate c : candidates) {
            int[] trial = applySlotChoice(itemIds, slot, c.itemId());
            Evaluation trialEval = evaluate(trial, request);
            if (trialEval != null && (bestEval == null || trialEval.dps.dps() > bestEval.dps.dps() + 1e-9)) {
                best = trial;
                bestEval = trialEval;
            }
        }
        return best;
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

    private static List<Candidate> buildCandidatesForSlot(EquipmentIndexRepository index, int slot, Request request) {
        List<EquipmentIndexRepository.Entry> all = index.forSlot(slot);
        List<Candidate> affordable = new ArrayList<>();
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
            boolean owned = request.owned.contains(e.itemId());
            boolean included = request.include.contains(e.itemId());
            long price = owned ? 0L : priceOf(e.itemId(), request);
            if (!owned && !included && price > request.budget) {
                continue;
            }
            affordable.add(new Candidate(e.itemId(), price, owned));
        }

        // Prune to top-N by a cheap proxy bonus (see proxyOffensiveScore): the
        // requested style's relevant bonuses when the search is style-
        // constrained, else the style-agnostic sum of every offensive bonus —
        // a fast ranking good enough to keep the true top candidates without
        // computing a full DPS for every one of ~1500 weapons per pass.
        affordable.sort(Comparator.comparingInt((Candidate c) -> -proxyOffensiveScore(c.itemId(), request.style)));

        List<Candidate> pruned = new ArrayList<>();
        for (Candidate c : affordable) {
            boolean mandatory = c.owned() || request.include.contains(c.itemId());
            if (mandatory || pruned.size() < request.candidatesPerSlot) {
                pruned.add(c);
            }
        }
        return pruned;
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
        for (WeaponStyle style : styles) {
            if (request.style != null && style.type() != request.style) {
                // Style-constrained search (item #6e): only styles of the
                // requested damage type may drive the DPS. A weapon with no
                // such style yields best == null -> this loadout is unusable
                // under the constraint (never silently scored at another type).
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
        for (int slot : SEARCHABLE_SLOTS) {
            List<Candidate> ownedCandidates = new ArrayList<>();
            for (EquipmentIndexRepository.Entry e : index.forSlot(slot)) {
                if (request.owned.contains(e.itemId()) && !request.exclude.contains(e.itemId())) {
                    ownedCandidates.add(new Candidate(e.itemId(), 0L, true));
                }
            }
            current = bestSingleSlotChoice(current, slot, ownedCandidates, request);
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
                int[] afterArr = bestSingleSlotChoice(current, slot, ownedCandidates, request);
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
