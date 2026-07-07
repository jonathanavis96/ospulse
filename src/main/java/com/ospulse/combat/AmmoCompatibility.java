package com.ospulse.combat;

import java.util.Locale;

/**
 * Weapon &lt;-&gt; worn-ammo compatibility, resolved purely from the bundled
 * data ({@link EquipmentIndexRepository} display names +
 * {@link WeaponCategoryRepository} categories) — no {@code Client}/
 * {@code ItemManager} read and no client thread, so the same lookup serves
 * the live DPS readout, the what-if picker, and the optimiser's candidate
 * pool identically (mirrors {@link WeaponCategoryRepository}'s rationale).
 *
 * <p>Two questions are answered here:
 * <ul>
 *   <li>{@link #consumedClass(int)} — which {@link AmmoClass} a weapon FIRES
 *       from the worn ammo slot ({@code null} when it doesn't use worn ammo
 *       at all: melee/magic weapons, self-supplying bows like the crystal
 *       bow family, blowpipes, standalone thrown weapons, chinchompas);</li>
 *   <li>{@link #wornAmmoContributes(int, int)} — whether one worn ammo-slot
 *       item's bonuses should count toward a loadout led by a given weapon.
 *       Per the wiki's DPS rules, CONSUMABLE ammo (arrows/bolts/javelins/…)
 *       only contributes its ranged strength when the weapon actually fires
 *       that class — dragon javelins worn behind a shortbow add nothing —
 *       while NON-consumable ammo-slot wearables (Rada's/god blessings) are
 *       passive and always count.</li>
 * </ul>
 *
 * <p>Deliberate approximations (documented, not bugs):
 * <ul>
 *   <li><b>Class-level only, no tier rules.</b> A bow is matched to ARROW,
 *       not to the specific arrow tiers it can fire (e.g. a Magic shortbow
 *       cannot really fire Dragon arrows). Tier tables are a much bigger
 *       data set for marginal optimiser gain — the realistic best-in-class
 *       ammo is usually usable by the best-in-class weapon. The two tier
 *       mismatches glaring enough to WIN searches wrongly get their own
 *       classes instead: Karil's crossbow / Bolt racks
 *       ({@link AmmoClass#RACK}) and the Hunters' crossbows / kebbit +
 *       antler bolts ({@link AmmoClass#HUNTER_BOLT}).</li>
 *   <li><b>Eclipse atlatl</b> fires Atlatl darts, which have no row in the
 *       bundled index — its class resolves to {@link AmmoClass#DART} with no
 *       indexed members, so the optimiser leaves its ammo slot alone.</li>
 *   <li><b>Unknown ammo-slot items</b> (not in the bundled index) fall back
 *       to "contributes" — the pre-compatibility behaviour.</li>
 * </ul>
 */
public final class AmmoCompatibility {
    /**
     * The bundled index's ammo-slot items partition cleanly by display name
     * (verified against equipment_index.min.json 2026-07-07: 73 arrows + 81
     * bolts + 1 bolt rack + 4 hunter bolts + 32 javelins + 5 tars + 11
     * blessings, zero unmatched).
     */
    public enum AmmoClass {
        /** Arrows, incl. the "brutal" comp-bow arrows. Consumable. */
        ARROW(true),
        /** Regular crossbow bolts. Consumable. */
        BOLT(true),
        /**
         * Barrows Bolt racks — split from BOLT because Karil's crossbow fires
         * ONLY racks and no other crossbow fires racks; class-level matching
         * would otherwise recommend (and credit) Karil's + dragon-tier bolts,
         * an impossible pairing strong enough to win real searches. Consumable.
         */
        RACK(true),
        /**
         * Hunter ammo ((long) kebbit bolts + sunlight/moonlight antler
         * bolts) — split from BOLT for the same reason: the Hunters'/Hunters'
         * sunlight crossbows fire only these, and no regular crossbow fires
         * them. Consumable.
         */
        HUNTER_BOLT(true),
        /** Javelins (ballista ammo). Consumable. */
        JAVELIN(true),
        /** Atlatl darts — real class, but no indexed members (see class javadoc). Consumable. */
        DART(true),
        /** Salamander fuel (Guam/Harralander/… tar). Consumable. */
        TAR(true),
        /** Rada's/god/Ancient blessings — passive prayer wearables, fired by nothing. */
        BLESSING(false);

        private final boolean consumable;

        AmmoClass(boolean consumable) {
            this.consumable = consumable;
        }

        /** True when the class is fired/used up by a weapon (everything except {@link #BLESSING}). */
        public boolean isConsumable() {
            return consumable;
        }
    }

    /**
     * BOW-category weapons that supply their own ammo (worn ammo slot stays
     * free for a blessing): the crystal bow family (incl. the Gauntlet's
     * corrupted tiers, harmless to list even though those are excluded from
     * the optimiser pool upstream), Bow of faerdhinen, and the wilderness
     * revenant bows. Name fragments, lower-case, matched by substring so
     * charge/cosmetic variants ("(c)", "(i)", clan colours) are all covered.
     */
    private static final String[] SELF_SUPPLYING_BOW_NAME_FRAGMENTS = {
        "crystal bow", "corrupted bow", "bow of faerdhinen", "craw's bow", "webweaver bow",
    };

    private AmmoCompatibility() {
    }

    /**
     * Classifies one AMMO-SLOT item by its indexed display name, or
     * {@code null} for an empty id, a non-ammo-slot item, or a name matching
     * no known class (treated as "unknown — always contributes").
     */
    public static AmmoClass classify(int itemId) {
        EquipmentIndexRepository.Entry entry = EquipmentIndexRepository.getInstance().entryFor(itemId);
        if (entry == null || entry.slotOrdinal() != 13) {
            return null;
        }
        String name = entry.name().toLowerCase(Locale.ROOT);
        if (name.contains("blessing")) {
            return AmmoClass.BLESSING;
        }
        if (name.contains("javelin")) {
            return AmmoClass.JAVELIN;
        }
        if (name.contains("bolt rack")) {
            return AmmoClass.RACK;
        }
        if (name.contains("kebbit") || name.contains("antler")) {
            return AmmoClass.HUNTER_BOLT;
        }
        if (name.contains("bolt")) {
            return AmmoClass.BOLT;
        }
        if (name.contains("arrow") || name.contains("brutal")) {
            return AmmoClass.ARROW;
        }
        if (name.endsWith("tar")) {
            return AmmoClass.TAR;
        }
        return null;
    }

    /**
     * The {@link AmmoClass} a WEAPON-slot item fires from the worn ammo slot,
     * or {@code null} when it doesn't use worn ammo at all. Resolution order:
     * <ol>
     *   <li>per-item name exceptions first: ballistae are categorised
     *       "crossbow" in the bundled data but fire JAVELINs; the Eclipse
     *       atlatl is categorised "bow" but fires (unindexed) atlatl DARTs;
     *       blowpipes are already category "thrown" (which resolves to
     *       {@code null} below) but are name-checked explicitly for safety
     *       since they load darts internally — same name rule as
     *       {@code GearVariants.isBlowpipe};</li>
     *   <li>then by {@link WeaponCategory}: BOW → ARROW (minus the
     *       self-supplying bows above), CROSSBOW → BOLT, SALAMANDER → TAR;</li>
     *   <li>everything else (melee, magic, thrown, chinchompas, unknown) →
     *       {@code null}.</li>
     * </ol>
     */
    public static AmmoClass consumedClass(int weaponItemId) {
        if (weaponItemId <= 0) {
            return null;
        }
        EquipmentIndexRepository.Entry entry = EquipmentIndexRepository.getInstance().entryFor(weaponItemId);
        String name = entry == null ? "" : entry.name().toLowerCase(Locale.ROOT);
        if (name.contains("blowpipe")) {
            return null;
        }
        if (name.contains("ballista")) {
            return AmmoClass.JAVELIN;
        }
        if (name.contains("atlatl")) {
            return AmmoClass.DART;
        }
        WeaponCategory category = WeaponCategoryRepository.getInstance().categoryFor(weaponItemId);
        if (category == WeaponCategory.BOW) {
            for (String fragment : SELF_SUPPLYING_BOW_NAME_FRAGMENTS) {
                if (name.contains(fragment)) {
                    return null;
                }
            }
            return AmmoClass.ARROW;
        }
        if (category == WeaponCategory.CROSSBOW) {
            if (name.contains("karil")) {
                return AmmoClass.RACK;
            }
            if (name.contains("hunters'")) {
                return AmmoClass.HUNTER_BOLT;
            }
            return AmmoClass.BOLT;
        }
        if (category == WeaponCategory.SALAMANDER) {
            return AmmoClass.TAR;
        }
        return null;
    }

    /** True when the weapon fires SOME worn-ammo class ({@link #consumedClass} non-null). */
    public static boolean usesWornAmmo(int weaponItemId) {
        return consumedClass(weaponItemId) != null;
    }

    /**
     * True when the worn ammo-slot item's bonuses count toward a loadout led
     * by {@code weaponItemId} (see class javadoc): non-consumable/unknown
     * ammo always counts; consumable ammo counts only when the weapon fires
     * exactly that class. Note the one caller-side exception: blowpipes skip
     * the worn ammo slot ENTIRELY (blessings included) in
     * {@code GearMapper.buildEquipmentStats}, which handles them before this
     * rule — the internally-loaded dart is modelled there instead.
     */
    public static boolean wornAmmoContributes(int weaponItemId, int ammoItemId) {
        AmmoClass ammoClass = classify(ammoItemId);
        if (ammoClass == null || !ammoClass.isConsumable()) {
            return true;
        }
        return consumedClass(weaponItemId) == ammoClass;
    }
}
