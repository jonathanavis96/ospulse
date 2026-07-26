package com.ospulse.ui.sections.gear;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterGearOverride;
import com.ospulse.combat.MonsterGearOverrideRepository;
import com.ospulse.combat.optimizer.GearOptimizer;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which items the gear optimiser may never suggest, which it MUST include,
 * and price-source rules for untradeable-but-buyable items (issue #11 batch
 * extraction — pure logic keyed only off the shared {@link
 * EquipmentIndexRepository}/{@link MonsterGearOverrideRepository} singletons,
 * no {@code GearSection} state).
 */
public final class ItemEligibility
{
	private ItemEligibility()
	{
	}

	/**
	 * Untradeable weapons that are nonetheless "buyable" because they are
	 * crafted directly from ONE tradeable GE ingredient — priced at that
	 * ingredient, overriding the blanket untradeable-=-unpurchasable rule in
	 * {@link #resolveOptimizerPriceSource}. Currently just the Scorching bow
	 * (crafted from a Tormented synapse, id 29580, + a ~1k Magic longbow (u)
	 * at 74 Fletching — the synapse IS the price; both ids verified against
	 * the OSRS Wiki 2026-07-07). The other two synapse weapons are
	 * deliberately NOT mapped: Emberlight's base item (Arclight) is itself
	 * untradeable, and the Purging staff is a magic weapon with no optimiser
	 * demand yet — add entries here only when the full craft cost is
	 * genuinely ~one tradeable ingredient.
	 */
	public static final Map<Integer, Integer> UNTRADEABLE_CRAFT_INGREDIENT =
		Map.of(29591 /* Scorching bow */, 29580 /* Tormented synapse */);

	/**
	 * Wraps a raw (unowned-item) GE price lookup with two rules (bug D):
	 * <ul>
	 *   <li><b>untradeable = unpurchasable:</b> an UNOWNED item flagged
	 *       untradeable by the client-thread-precomputed {@code
	 *       PriceLookup#untradeableIds()} can never be bought, whatever price
	 *       the raw lookup reports — RuneLite's {@code
	 *       ItemManager.getItemPrice} routes some untradeables through {@code
	 *       ItemMapping} to a tradeable proxy (e.g. every trouver-locked
	 *       item, including Dragon defender (l) 24143 and Fire cape (l)
	 *       24223, "costs" the Trouver parchment's ~1m GE price), which made
	 *       the optimiser recommend buying items that cannot be bought.
	 *       Tradeability comes from the precomputed set (the source of
	 *       truth), NOT from a hand-maintained per-item override. Owned
	 *       untradeables never reach this path — they are priced 0 via
	 *       {@code .owned()} directly;</li>
	 *   <li>a resolved price &lt;= 0 means untradeable/unpriced, not free —
	 *       an UNOWNED item you cannot buy is unaffordable ({@link
	 *       Long#MAX_VALUE}), not a bargain;</li>
	 *   <li><b>craftable-from-one-ingredient exception:</b> the few
	 *       untradeables in {@link #UNTRADEABLE_CRAFT_INGREDIENT} price at
	 *       their tradeable ingredient's GE cost INSTEAD of the two rules
	 *       above (checked first) — e.g. the untradeable Scorching bow
	 *       "costs" a Tormented synapse, so the optimiser can recommend it to
	 *       a non-owner and the spend readout shows the real acquisition
	 *       cost rather than the bogus ~1m ItemMapping value or a blanket
	 *       "unbuyable".</li>
	 * </ul>
	 */
	public static GearOptimizer.PriceSource resolveOptimizerPriceSource(GearOptimizer.PriceSource rawPriceSource,
		Set<Integer> untradeableIds)
	{
		return itemId ->
		{
			Integer ingredientId = UNTRADEABLE_CRAFT_INGREDIENT.get(itemId);
			if (ingredientId != null)
			{
				long ingredientPrice = rawPriceSource.priceFor(ingredientId);
				return ingredientPrice > 0 ? ingredientPrice : Long.MAX_VALUE;
			}
			if (untradeableIds.contains(itemId))
			{
				return Long.MAX_VALUE;
			}
			long resolved = rawPriceSource.priceFor(itemId);
			return resolved > 0 ? resolved : Long.MAX_VALUE;
		};
	}

	/**
	 * Matches a parenthesised game-mode marker in an item's display name —
	 * Deadman Mode, Bounty Hunter, Last Man Standing/LMS, or a league beta
	 * cosmetic. These items are not usable by a normal-mode player and must
	 * never be suggested by the optimiser (bug C).
	 */
	private static final Pattern MODE_LOCKED_NAME_PATTERN = Pattern.compile(
		"(?i)\\((deadman mode|deadman|bh|lms|last man standing|beta)\\)");

	/** True when an item's indexed display name carries a mode-locked marker — see {@link #MODE_LOCKED_NAME_PATTERN}. */
	public static boolean isModeLockedItem(String name)
	{
		return name != null && MODE_LOCKED_NAME_PATTERN.matcher(name).find();
	}

	/**
	 * Name suffixes of The Gauntlet's instance-only weapon/armour tiers —
	 * "Crystal/Corrupted X (basic|attuned|perfected)" (ids 23840-23903 +
	 * 30340). These are REAL main-game items but exist only INSIDE the
	 * Gauntlet (made from crystal shards in the instance, unusable/lost the
	 * moment the player leaves), so they can never be equipped against a
	 * normal overworld target and must never be optimiser candidates. Their
	 * names carry no "(deadman)/(lms)"-style mode marker, so {@link
	 * #MODE_LOCKED_NAME_PATTERN} cannot catch them — the suffix IS the
	 * marker. The suffix-less main-game crystal armour ("Crystal helm/body/
	 * legs", distinct ids 23971-23981) is untouched by this rule.
	 */
	private static final String[] GAUNTLET_ONLY_NAME_SUFFIXES = { " (basic)", " (attuned)", " (perfected)" };

	/** True when an item's indexed display name is a Gauntlet-instance-only tier — see {@link #GAUNTLET_ONLY_NAME_SUFFIXES}. */
	public static boolean isGauntletOnlyItem(String name)
	{
		if (name == null)
		{
			return false;
		}
		for (String suffix : GAUNTLET_ONLY_NAME_SUFFIXES)
		{
			if (name.length() >= suffix.length()
				&& name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Every indexed item id the player can never actually use against a
	 * normal target — added to the optimiser's exclude set every search,
	 * regardless of price or ownership:
	 * <ul>
	 *   <li>mode-locked names (bug C): Deadman/BH/LMS/beta-only items, which
	 *       the user (not being in those modes) cannot use;</li>
	 *   <li>Gauntlet-instance-only tiers — see
	 *       {@link #GAUNTLET_ONLY_NAME_SUFFIXES}.</li>
	 * </ul>
	 */
	public static Set<Integer> restrictedItemIds()
	{
		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		Set<Integer> ids = new HashSet<>();
		for (Integer id : index.allItemIds())
		{
			EquipmentIndexRepository.Entry entry = index.entryFor(id);
			if (entry != null && (isModeLockedItem(entry.name()) || isGauntletOnlyItem(entry.name())))
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/**
	 * Item ids the optimiser search MUST use for {@code target} (via {@code
	 * GearOptimizer.Request.Builder#include}) — the curated {@link
	 * MonsterGearOverrideRepository} entries for that monster, so a
	 * mechanic-critical item (e.g. Insulated boots vs Rune dragons) can never
	 * be dropped by DPS ranking. A user's explicit slot exclusion still wins
	 * (an item present in {@code exclusions} is left out of the forced set
	 * rather than fighting the exclude list).
	 *
	 * <p>Outside owned-only mode this always forces the primary {@link
	 * MonsterGearOverride#itemId()} — an unaffordable force-include there is
	 * a legitimate purchase suggestion, and the player may simply buy it. In
	 * owned-only mode it forces whichever id {@link
	 * OwnedOnlyMandatoryOverrideGate#ownedOnlySatisfyingItemId} says actually
	 * satisfies the requirement — the owned alternative (e.g. a Slayer
	 * helmet the player owns for a Dust devil's Facemask requirement) when
	 * the primary isn't owned, so {@code GearOptimizer.applyForcedIncludes}
	 * can never force-equip an unowned item that {@link
	 * OwnedOnlyMandatoryOverrideGate#blockingOverride} agreed the target was
	 * satisfied for. When neither the primary nor any alternative is owned,
	 * this still returns the primary — but that case is exactly what {@link
	 * OwnedOnlyMandatoryOverrideGate#blockingOverride} refuses the target
	 * for before a result reaches the player, so the unowned id is never
	 * actually shown/equipped.
	 *
	 * @param ownedOnly whether the ironman "owned gear only" mode is active
	 * @param ownedIds every item id the player currently owns — only
	 *                  consulted when {@code ownedOnly} is true
	 */
	public static Set<Integer> mandatoryOverrideItemIds(Monster target, Set<Integer> exclusions,
		boolean ownedOnly, Set<Integer> ownedIds)
	{
		if (target == null)
		{
			return Collections.emptySet();
		}
		Set<Integer> ids = new LinkedHashSet<>();
		for (MonsterGearOverride override : MonsterGearOverrideRepository.getInstance().forMonster(target.name()))
		{
			int id = ownedOnly
				? OwnedOnlyMandatoryOverrideGate.ownedOnlySatisfyingItemId(override, ownedIds)
				: override.itemId();
			if (!exclusions.contains(id))
			{
				ids.add(id);
			}
		}
		return ids;
	}
}
