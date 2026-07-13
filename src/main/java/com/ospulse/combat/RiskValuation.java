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
 *   <li>An "assembled" best-in-slot item (e.g. an Avernic defender, made
 *       from a tradeable hilt on a defender the player already owns) is
 *       valued at its curated {@link AssembledItemComponents} tradeable
 *       component's GE price when {@link ItemMapping} doesn't cover it.</li>
 *   <li>A small curated fallback ({@link #CREATED_ITEM_COMPONENTS}) covers
 *       created/untradeable items {@link ItemMapping} does not (yet) map.
 *       Add entries only once the exact tradeable component ids are verified
 *       against a concrete source (RuneLite's generated {@code ItemID}
 *       constants, or the OSRS wiki) — see the field's javadoc.</li>
 *   <li>{@link #classify} additionally supports a last-resort Trouver
 *       parchment fallback for rare untradeables with no tradeable
 *       equivalent anywhere above (Fire cape, Fighter torso, imbued capes,
 *       etc.) — the parchment is the real gp cost to protect one on death,
 *       so it stands in as the item's risk value rather than letting it
 *       silently escape the cap at 0. Free-to-reclaim items (Ardougne
 *       cloak, Rada's blessing) are exempted from this fallback entirely.
 *       The back-compat {@link #riskValue} entry point does not use this
 *       fallback (unmapped/no curated entry still risks at 0 there).</li>
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
	 * Which rule produced a {@link Risk}'s value — surfaced so callers (the
	 * optimizer price resolver, and eventually the UI/exclude wave) can tell a
	 * genuinely-priced item apart from one that only "counts" because of the
	 * {@link #classify parchment fallback}.
	 */
	public enum Source
	{
		/** The item is itself tradeable; valued at its own GE price. */
		TRADEABLE,
		/** Untradeable; valued via a real {@link ItemMapping} entry. */
		MAPPING,
		/** Untradeable; valued via {@link AssembledItemComponents#priceSourceComponent(int)}. */
		ASSEMBLED,
		/** Untradeable; valued via the curated {@link #CREATED_ITEM_COMPONENTS} fallback. */
		CURATED,
		/**
		 * Untradeable with no real value found anywhere above; stood in for by
		 * the Trouver parchment price (the cost to protect it on death) so it
		 * still counts as "expensive" for the risk cap.
		 */
		PARCHMENT,
		/** No value could be determined (and it isn't free-reobtainable either) — 0. */
		NONE
	}

	/** A risk value paired with which rule ({@link Source}) produced it. */
	public static final class Risk
	{
		public final long value;
		public final Source source;

		public Risk(long value, Source source)
		{
			this.value = value;
			this.source = source;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof Risk))
			{
				return false;
			}
			Risk other = (Risk) o;
			return value == other.value && source == other.source;
		}

		@Override
		public int hashCode()
		{
			return java.util.Objects.hash(value, source);
		}

		@Override
		public String toString()
		{
			return "Risk{value=" + value + ", source=" + source + '}';
		}
	}

	/**
	 * The real gp "risk value" of {@code itemId} for the expensive-item cap,
	 * plus which rule produced it — see {@link Source}. NEVER used for
	 * budget/affordability — see class javadoc.
	 *
	 * <p>Order of resolution for an UNTRADEABLE item: (1) a real {@link
	 * ItemMapping} entry ({@link Source#MAPPING}); (2) {@link
	 * AssembledItemComponents#priceSourceComponent(int)}, for best-in-slot
	 * items assembled from a tradeable part ({@link Source#ASSEMBLED} — fixes
	 * the Avernic defender reading 0); (3) the curated {@link
	 * #CREATED_ITEM_COMPONENTS} fallback ({@link Source#CURATED}); (4) the
	 * Trouver parchment price, standing in for "cost to protect this on
	 * death" ({@link Source#PARCHMENT}) — but only when {@code
	 * isFreeReobtainable} is false (a free-to-reclaim item, e.g. Ardougne
	 * cloak or Rada's blessing, carries no death risk at all and must never
	 * be priced) and {@code parchmentPrice > 0}. A TRADEABLE item is always
	 * just its own GE price ({@link Source#TRADEABLE}), regardless of
	 * free-reobtainable status.
	 *
	 * @param itemId              the equipment item id to value
	 * @param isTradeable         whether {@code itemId} itself can be bought/sold on the GE
	 * @param gePrice             GE unit price for a (tradeable) item id; 0 if none
	 * @param parchmentPrice      the Trouver parchment's own GE price (0 if unknown/untradeable
	 *                            right now), used only as the last-resort fallback
	 * @param isFreeReobtainable  whether {@code itemId} is free to reclaim if lost (no death
	 *                            risk); checked first, before any other rule
	 */
	public static Risk classify(int itemId, IntPredicate isTradeable, IntToLongFunction gePrice,
		long parchmentPrice, IntPredicate isFreeReobtainable)
	{
		if (isFreeReobtainable.test(itemId))
		{
			return new Risk(0L, Source.NONE);
		}

		if (isTradeable.test(itemId))
		{
			return new Risk(Math.max(0L, gePrice.applyAsLong(itemId)), Source.TRADEABLE);
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
				return new Risk(sum, Source.MAPPING);
			}
		}

		Integer assembledComponent = AssembledItemComponents.priceSourceComponent(itemId);
		if (assembledComponent != null)
		{
			long componentPrice = Math.max(0L, gePrice.applyAsLong(assembledComponent));
			if (componentPrice > 0L)
			{
				return new Risk(componentPrice, Source.ASSEMBLED);
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
			return new Risk(sum, Source.CURATED);
		}

		long parchment = Math.max(0L, parchmentPrice);
		if (parchment > 0L)
		{
			return new Risk(parchment, Source.PARCHMENT);
		}

		return new Risk(0L, Source.NONE);
	}

	/**
	 * Backward-compatible entry point: delegates to {@link #classify} with no
	 * parchment fallback ({@code parchmentPrice = 0}) and no free-reobtainable
	 * exemption (always {@code false}), so existing callers/tests keep their
	 * prior behaviour unchanged — untradeable items with no {@link
	 * ItemMapping}/{@link AssembledItemComponents}/{@link
	 * #CREATED_ITEM_COMPONENTS} value still resolve to 0.
	 *
	 * @param itemId      the equipment item id to value
	 * @param isTradeable whether {@code itemId} itself can be bought/sold on
	 *                    the GE
	 * @param gePrice     GE unit price for a (tradeable) item id; 0 if none
	 */
	public static long riskValue(int itemId, IntPredicate isTradeable, IntToLongFunction gePrice)
	{
		return classify(itemId, isTradeable, gePrice, 0L, id -> false).value;
	}
}
