package com.ospulse.combat.optimizer;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStatsRepository;
import com.ospulse.session.GearMapper;

/**
 * An EDT-safe {@link GearMapper.SlotStatsLookup} backed entirely by bundled
 * data ({@link EquipmentStatsRepository} for numeric bonuses,
 * {@link EquipmentIndexRepository} for the two-handed flag) — no
 * {@code ItemManager}/{@code Client} call, so (unlike
 * {@code SessionTracker.lookupSlotStats}, which needs the client thread for
 * RuneLite's {@code getItemStats}) this can resolve an ARBITRARY item id,
 * owned or not, from the Swing EDT.
 *
 * <p>This is what lets Phase 2's what-if swap picker and Phase 3's optimiser
 * search recompute {@code EquipmentStats} for a hypothetical loadout entirely
 * on the EDT, reusing {@link GearMapper#buildEquipmentStats} unchanged.
 *
 * <p>Trade-off vs the live lookup: an item present in RuneLite's item stats
 * but absent from the bundled cache data resolves to {@code null} here (no
 * wiki-derived fallback, since that requires {@code ItemManager}) — in
 * practice {@code equipment_stats.min.json} covers the full cache-derived
 * item set (3501 items at last regen), so this only matters for a brand-new
 * item release before the bundled data refreshes.
 */
public final class BundledSlotStatsLookup implements GearMapper.SlotStatsLookup {
    public static final BundledSlotStatsLookup INSTANCE = new BundledSlotStatsLookup();

    private BundledSlotStatsLookup() {
    }

    @Override
    public GearMapper.SlotStats statsFor(int itemId) {
        EquipmentStatsRepository.Stats cache = EquipmentStatsRepository.getInstance().statsFor(itemId);
        if (cache == null) {
            return null;
        }
        EquipmentIndexRepository.Entry indexEntry = EquipmentIndexRepository.getInstance().entryFor(itemId);
        boolean isTwoHanded = indexEntry != null && indexEntry.isTwoHanded();
        return new GearMapper.SlotStats(
                cache.astab(), cache.aslash(), cache.acrush(), cache.amagic(), cache.arange(),
                cache.dstab(), cache.dslash(), cache.dcrush(), cache.dmagic(), cache.drange(),
                cache.str(), cache.rstr(), cache.mdmg(), cache.prayer(), cache.aspeed(), isTwoHanded);
    }
}
