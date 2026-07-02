package com.ospulse.session;

import com.ospulse.ge.GeOfferView;
import com.ospulse.wealth.WealthSnapshot;
import com.ospulse.xp.XpSkillView;

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
	private final List<SourceLoot> lootSources;
	private final List<XpSkillView> xpSkills;
	private final long xpPerHour;

	/**
	 * Backward-compatible constructor: no active-offer / source-loot breakdown.
	 * Delegates to the full constructor with empty lists.
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
			bankKnown, loot, xpGained, xpTotal, wealth, Collections.emptyList(),
			Collections.emptyList());
	}

	/**
	 * Backward-compatible constructor: no per-skill XP views / overall XP rate.
	 * Delegates to the full constructor with empty defaults.
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
		WealthSnapshot wealth,
		List<GeOfferView> geOffers,
		List<SourceLoot> lootSources)
	{
		this(startMs, elapsedMs, profit, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, geOffers, lootSources,
			Collections.emptyList(), 0L);
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
		List<GeOfferView> geOffers,
		List<SourceLoot> lootSources,
		List<XpSkillView> xpSkills,
		long xpPerHour)
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
		this.lootSources = lootSources == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(lootSources));
		this.xpSkills = xpSkills == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(xpSkills));
		this.xpPerHour = xpPerHour;
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

	/**
	 * Loot grouped by source (NPC/boss/activity), ordered by total value
	 * descending — the collapsible Loot-Tracker-style feed. Empty when nothing
	 * has been looted yet this session.
	 */
	public List<SourceLoot> getLootSources()
	{
		return lootSources;
	}

	/**
	 * Per-skill XP progress views (gained, rate, xp/actions left, level
	 * progress), ordered by XP gained descending. Empty until the integration
	 * layer supplies them (e.g. before the first XP gain of the session).
	 */
	public List<XpSkillView> getXpSkills()
	{
		return xpSkills;
	}

	/** Overall XP gained per hour across all skills; 0 while elapsed is 0. */
	public long getXpPerHour()
	{
		return xpPerHour;
	}
}
