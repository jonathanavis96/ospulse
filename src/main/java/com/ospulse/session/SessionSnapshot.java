package com.ospulse.session;

import com.ospulse.ge.GeOfferView;
import com.ospulse.wealth.WealthSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable output DTO summarising the current state of a tracked session.
 * The {@link #getLoot()} list is aggregated per item and ordered by total
 * value descending (index 0 = most valuable item this session).
 */
public final class SessionSnapshot
{
	private final long startMs;
	private final long elapsedMs;
	private final long profit;
	private final long profitPerHour;
	private final long geRealizedPnl;
	private final long netWorthDelta;
	private final boolean bankKnown;
	private final List<LootEntry> loot;
	private final Map<String, Long> xpGained;
	private final long xpTotal;
	private final WealthSnapshot wealth;
	private final List<GeOfferView> geOffers;

	/**
	 * Backward-compatible constructor: no active-offer breakdown. Delegates to
	 * the full constructor with an empty {@code geOffers} list.
	 */
	public SessionSnapshot(
		long startMs,
		long elapsedMs,
		long profit,
		long profitPerHour,
		long geRealizedPnl,
		long netWorthDelta,
		boolean bankKnown,
		List<LootEntry> loot,
		Map<String, Long> xpGained,
		long xpTotal,
		WealthSnapshot wealth)
	{
		this(startMs, elapsedMs, profit, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, Collections.emptyList());
	}

	public SessionSnapshot(
		long startMs,
		long elapsedMs,
		long profit,
		long profitPerHour,
		long geRealizedPnl,
		long netWorthDelta,
		boolean bankKnown,
		List<LootEntry> loot,
		Map<String, Long> xpGained,
		long xpTotal,
		WealthSnapshot wealth,
		List<GeOfferView> geOffers)
	{
		this.startMs = startMs;
		this.elapsedMs = elapsedMs;
		this.profit = profit;
		this.profitPerHour = profitPerHour;
		this.geRealizedPnl = geRealizedPnl;
		this.netWorthDelta = netWorthDelta;
		this.bankKnown = bankKnown;
		this.loot = loot == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(loot));
		this.xpGained = xpGained == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(xpGained));
		this.xpTotal = xpTotal;
		this.wealth = wealth;
		this.geOffers = geOffers == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(geOffers));
	}

	public long getStartMs()
	{
		return startMs;
	}

	public long getElapsedMs()
	{
		return elapsedMs;
	}

	public long getProfit()
	{
		return profit;
	}

	public long getProfitPerHour()
	{
		return profitPerHour;
	}

	public long getGeRealizedPnl()
	{
		return geRealizedPnl;
	}

	public long getNetWorthDelta()
	{
		return netWorthDelta;
	}

	public boolean isBankKnown()
	{
		return bankKnown;
	}

	/**
	 * Loot aggregated per item (one row per distinct item, quantities and
	 * values summed across the session), ordered by total value descending.
	 */
	public List<LootEntry> getLoot()
	{
		return loot;
	}

	public Map<String, Long> getXpGained()
	{
		return xpGained;
	}

	public long getXpTotal()
	{
		return xpTotal;
	}

	public WealthSnapshot getWealth()
	{
		return wealth;
	}

	/**
	 * Active Grand Exchange offers (buy/sell), for the panel's GE breakdown.
	 * Empty when there are no active offers.
	 */
	public List<GeOfferView> getGeOffers()
	{
		return geOffers;
	}
}
