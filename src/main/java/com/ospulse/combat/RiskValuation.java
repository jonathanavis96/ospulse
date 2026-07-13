package com.ospulse.combat;

import net.runelite.client.game.ItemMapping;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/**
 * Pure per-item "risk value" — the real gp worth of an item id, used ONLY by
 * the gear optimiser's expensive-item wilderness/PvP risk cap (see {@code
 * GearOptimizer.Request.Builder#riskValueSource}). Deliberately decoupled
 * from the BUDGET/affordability price: owning an item keeps it free to equip
 * (the budget path, via {@code GearOptimizer.Request.owned}), but it is still
 * real gp at risk if lost, so the risk value here is always the item's true
 * worth regardless of ownership.
 *
 * <ul>
 *   <li>A tradeable item's risk value is simply its own GE price.</li>
 *   <li>An untradeable item's risk value is the summed GE price of the
 *       tradeable component(s) it represents, resolved via {@link
 *       ItemMapping} — RuneLite's own bundled price-checking data, covering
 *       Barrows pieces (degraded/undamaged tiers, echo variants), imbued
 *       rings, and other GE-restricted-but-valuable items automatically, at
 *       any scale, with no per-item hand-listing required.</li>
 *   <li>A small curated fallback ({@link #CREATED_ITEM_COMPONENTS}) covers
 *       created/untradeable items {@link ItemMapping} does not (yet) map.
 *       Add entries only once the exact tradeable component ids are verified
 *       against a concrete source (RuneLite's generated {@code ItemID}
 *       constants, or the OSRS wiki) — see the field's javadoc.</li>
 *   <li>Anything else (unmapped, no curated entry) risks at 0 — unknown
 *       value can't be judged "expensive", so it never wrongly counts
 *       against the cap.</li>
 * </ul>
 *
 * <p><b>No client-thread dependency:</b> {@link ItemMapping#map(int)} is a
 * static enum lookup over data bundled in the {@code net.runelite:client}
 * jar itself (verified via {@code javap} against {@code client-1.12.32.jar}
 * on this project's classpath) — it does NOT touch {@code ItemManager} or
 * any other client-thread-only API, so this class runs anywhere, including
 * plain unit tests, given a caller-supplied {@code isTradeable}/{@code
 * gePrice} pair (see {@link RiskValuationTest}).
 */
public final class RiskValuation
{
	private RiskValuation()
	{
	}

	/**
	 * Curated fallback for created/untradeable items {@link ItemMapping} does
	 * not cover: item id -&gt; tradeable component item ids whose summed GE
	 * price stands in for the created item's risk value.
	 *
	 * <p><b>Scorching bow (29591) does NOT need an entry here.</b> Verified
	 * via {@code javap}-inspection of the decompiled {@code
	 * net.runelite.client.game.ItemMapping} enum shipped in {@code
	 * net.runelite:client:1.12.32} (this project's pinned classpath
	 * dependency — see {@code build.gradle}): {@code ItemMapping.map(29591)}
	 * already resolves to a single entry with {@code tradeableItem =
	 * TORMENTED_SYNAPSE (29580)}, {@code quantity = 1} — RuneLite's own
	 * price-checking convention for the three Doom-of-Mokhaiotl weapons
	 * (Scorching bow 29591, Emberlight 29589, Purging staff 29594, all keyed
	 * off the same synapse mapping). The {@link ItemMapping} branch in {@link
	 * #riskValue} therefore handles it automatically. This map intentionally
	 * starts EMPTY; add an entry only for a created item confirmed absent
	 * from {@link ItemMapping} (re-run the same {@code javap}/reflection
	 * check before adding one — do not guess a component id from memory).
	 */
	private static final Map<Integer, int[]> CREATED_ITEM_COMPONENTS;

	static
	{
		Map<Integer, int[]> m = new HashMap<>();
		CREATED_ITEM_COMPONENTS = Collections.unmodifiableMap(m);
	}

	/**
	 * The real gp "risk value" of {@code itemId} for the expensive-item cap.
	 * NEVER used for budget/affordability — see class javadoc.
	 *
	 * @param itemId      the equipment item id to value
	 * @param isTradeable whether {@code itemId} itself can be bought/sold on
	 *                    the GE
	 * @param gePrice     GE unit price for a (tradeable) item id; 0 if none
	 */
	public static long riskValue(int itemId, IntPredicate isTradeable, IntToLongFunction gePrice)
	{
		if (isTradeable.test(itemId))
		{
			return Math.max(0L, gePrice.applyAsLong(itemId));
		}

		Collection<ItemMapping> mappings = ItemMapping.map(itemId);
		if (mappings != null && !mappings.isEmpty())
		{
			long sum = 0L;
			for (ItemMapping mapping : mappings)
			{
				long componentPrice = Math.max(0L, gePrice.applyAsLong(mapping.getTradeableItem()));
				sum += componentPrice * mapping.getQuantity();
			}
			if (sum > 0L)
			{
				return sum;
			}
		}

		int[] components = CREATED_ITEM_COMPONENTS.get(itemId);
		if (components != null)
		{
			long sum = 0L;
			for (int componentId : components)
			{
				sum += Math.max(0L, gePrice.applyAsLong(componentId));
			}
			return sum;
		}

		return 0L;
	}
}
