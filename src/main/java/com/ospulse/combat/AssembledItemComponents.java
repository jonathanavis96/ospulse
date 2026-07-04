package com.ospulse.combat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Curated map of an assembled (untradeable) piece of equipment to the tradeable
 * component whose Grand Exchange price stands in for "buying" it.
 *
 * <p>Some best-in-slot items can't be bought off the GE directly because the
 * final item is untradeable, but they are assembled from a tradeable part the
 * player buys and combines with something they already own. The flagship case:
 * an <b>Avernic defender</b> (untradeable) is made by using an <b>Avernic
 * defender hilt</b> (tradeable, ~31.8M) on a dragon defender the player already
 * has. Left as a plain untradeable, the gear optimiser can never recommend it
 * as an upgrade even when the player can clearly afford the hilt.
 *
 * <p>The optimiser therefore prices the assembled item at its component's GE
 * price while keeping the assembled item's own id (and hence its own name in
 * the readout), so it can recommend "Avernic defender" at the hilt's cost.
 * Jonathan's call: don't over-explain in the UI — just show the assembled
 * item's name at the component price; players understand it means "buy the
 * hilt". Only tradeable components with a real price are used; anything else
 * falls back to the normal untradeable = unbuyable rule.
 *
 * <p>Item ids verified against the RuneLite {@code net.runelite.api.ItemID}
 * constants ({@code AVERNIC_DEFENDER = 22322}, {@code AVERNIC_DEFENDER_HILT =
 * 22477}), not the wiki. Extend by adding one line per assembled/component
 * pair.
 */
public final class AssembledItemComponents
{
	/** assembled (untradeable) item id -> tradeable component item id whose price to use. */
	private static final Map<Integer, Integer> PRICE_SOURCE;

	static
	{
		Map<Integer, Integer> m = new HashMap<>();
		m.put(22322, 22477); // Avernic defender <- Avernic defender hilt
		PRICE_SOURCE = Collections.unmodifiableMap(m);
	}

	private AssembledItemComponents()
	{
	}

	/**
	 * @param assembledId an equipment item id
	 * @return the tradeable component item id whose GE price stands in for
	 *         buying {@code assembledId}, or {@code null} if this item is not a
	 *         curated assembled-from-component case
	 */
	public static Integer priceSourceComponent(int assembledId)
	{
		return PRICE_SOURCE.get(assembledId);
	}
}
