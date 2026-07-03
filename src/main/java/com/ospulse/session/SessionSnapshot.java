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
	private final GearSnapshot gear;
	private final long unrealizedPnl;
	private final List<HoldingPnl> holdingPnls;
	private final long suppliesUsed;

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

	/**
	 * Backward-compatible constructor: no {@link GearSnapshot}. Delegates to the
	 * full constructor with {@code gear = null}, which is normalised to
	 * {@link GearSnapshot#empty()}.
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
		List<SourceLoot> lootSources,
		List<XpSkillView> xpSkills,
		long xpPerHour)
	{
		this(startMs, elapsedMs, profit, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, geOffers, lootSources,
			xpSkills, xpPerHour, null);
	}

	/**
	 * Backward-compatible constructor: no unrealized-P/L figures. Delegates to
	 * the full constructor with {@code unrealizedPnl = 0} and no per-holding
	 * breakdown.
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
		List<SourceLoot> lootSources,
		List<XpSkillView> xpSkills,
		long xpPerHour,
		GearSnapshot gear)
	{
		this(startMs, elapsedMs, profit, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, geOffers, lootSources,
			xpSkills, xpPerHour, gear, 0L, Collections.emptyList());
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
		long xpPerHour,
		GearSnapshot gear,
		long unrealizedPnl,
		List<HoldingPnl> holdingPnls)
	{
		this(startMs, elapsedMs, profit, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, geOffers, lootSources,
			xpSkills, xpPerHour, gear, unrealizedPnl, holdingPnls, 0L);
	}

	/**
	 * Full constructor including {@code suppliesUsed} — the running session
	 * total (gp value) of consumable supplies (potions/food/ammo/runes)
	 * consumed, as distinct from {@code profit} (unchanged: realised
	 * activity only). See {@link SessionEngine#update} for how this is
	 * detected and its heuristic's limitations.
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
		List<SourceLoot> lootSources,
		List<XpSkillView> xpSkills,
		long xpPerHour,
		GearSnapshot gear,
		long unrealizedPnl,
		List<HoldingPnl> holdingPnls,
		long suppliesUsed)
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
		this.gear = gear == null ? GearSnapshot.empty() : gear;
		this.unrealizedPnl = unrealizedPnl;
		this.holdingPnls = holdingPnls == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(holdingPnls));
		this.suppliesUsed = suppliesUsed;
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

	/**
	 * Live worn gear + boosted levels + active prayers for the Gear/DPS
	 * calculator section. Never {@code null} — defaults to
	 * {@link GearSnapshot#empty()} for snapshots built before that plumbing
	 * existed (older constructors) or before the first live read.
	 */
	public GearSnapshot getGear()
	{
		return gear;
	}

	/**
	 * Total unrealized P/L: current holdings valued at live price minus their
	 * session cost basis. Pure price drift on held items moves this figure —
	 * never {@link #getProfit()}, which is realised activity only.
	 */
	public long getUnrealizedPnl()
	{
		return unrealizedPnl;
	}

	/**
	 * Per-holding unrealized P/L breakdown (cost basis vs current value per
	 * held item), ordered by absolute unrealized amount descending — the rows
	 * sum to {@link #getUnrealizedPnl()}. Empty when nothing is held or the
	 * engine predates cost-basis tracking (older constructors).
	 */
	public List<HoldingPnl> getHoldingPnls()
	{
		return holdingPnls;
	}

	/**
	 * Running session total (gp value) of consumable supplies used
	 * (potions/food/ammo/runes drained from inventory while away from the
	 * bank and not attributable to a GE sale). This spend is excluded from
	 * {@link #getProfit()} — consuming a supply neither gains nor loses
	 * profit, it just moves value from "held" to "used" — so this figure is
	 * the dedicated place that spend is visible, not a duplicate of a
	 * reduction already visible in profit. Zero for snapshots built before
	 * this tracking existed (older constructors).
	 */
	public long getSuppliesUsed()
	{
		return suppliesUsed;
	}
}
