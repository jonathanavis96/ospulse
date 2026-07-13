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
	private final long lootValue;
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
	private final long gePositions;
	private final long bankDelta;

	/**
	 * Backward-compatible constructor: no active-offer / source-loot breakdown.
	 * Delegates to the full constructor with empty lists.
	 */
	public SessionSnapshot(
		long startMs,
		long elapsedMs,
		long lootValue,
		long profitPerHour,
		long geRealizedPnl,
		long netWorthDelta,
		boolean bankKnown,
		List<LootEntry> loot,
		Map<String, Long> xpGained,
		long xpTotal,
		WealthSnapshot wealth)
	{
		this(startMs, elapsedMs, lootValue, profitPerHour, geRealizedPnl, netWorthDelta,
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
		long lootValue,
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
		this(startMs, elapsedMs, lootValue, profitPerHour, geRealizedPnl, netWorthDelta,
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
		long lootValue,
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
		this(startMs, elapsedMs, lootValue, profitPerHour, geRealizedPnl, netWorthDelta,
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
		long lootValue,
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
		this(startMs, elapsedMs, lootValue, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, geOffers, lootSources,
			xpSkills, xpPerHour, gear, 0L, Collections.emptyList());
	}

	public SessionSnapshot(
		long startMs,
		long elapsedMs,
		long lootValue,
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
		this(startMs, elapsedMs, lootValue, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, geOffers, lootSources,
			xpSkills, xpPerHour, gear, unrealizedPnl, holdingPnls, 0L);
	}

	/**
	 * Backward-compatible constructor: no GE-positions / Bank component
	 * breakdown. Delegates to the full constructor with both zeroed.
	 */
	public SessionSnapshot(
		long startMs,
		long elapsedMs,
		long lootValue,
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
		this(startMs, elapsedMs, lootValue, profitPerHour, geRealizedPnl, netWorthDelta,
			bankKnown, loot, xpGained, xpTotal, wealth, geOffers, lootSources,
			xpSkills, xpPerHour, gear, unrealizedPnl, holdingPnls, suppliesUsed, 0L, 0L);
	}

	/**
	 * Full constructor including {@code suppliesUsed} — the running session
	 * total (gp value) of consumable supplies (potions/food/ammo/runes)
	 * consumed, as distinct from {@code lootValue} (unchanged: realised
	 * activity only) — and the LOCKED session-panel model's {@code
	 * gePositions} / {@code bankDelta} components, whose sum with {@code
	 * lootValue - suppliesUsed} and {@code geRealizedPnl} defines {@code
	 * netWorthDelta} ("Net worth change" — see {@link SessionEngine#snapshot}).
	 */
	public SessionSnapshot(
		long startMs,
		long elapsedMs,
		long lootValue,
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
		long suppliesUsed,
		long gePositions,
		long bankDelta)
	{
		this.startMs = startMs;
		this.elapsedMs = elapsedMs;
		this.lootValue = lootValue;
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
		this.gePositions = gePositions;
		this.bankDelta = bankDelta;
	}

	public long getStartMs()
	{
		return startMs;
	}

	public long getElapsedMs()
	{
		return elapsedMs;
	}

	/**
	 * Session "Loot": gp value of items actually picked up — realised wealth
	 * gains with GE flip P&amp;L removed (flips are surfaced on their own line
	 * via {@link #getGeRealizedPnl()} and would otherwise double-count). Distinct
	 * from {@link #getLoot()}, which is the itemised loot FEED (all drops,
	 * event-based) and need not equal this value: looting nothing leaves this at
	 * zero even when the feed shows drops. Selling looted items is not a flip, so
	 * that value stays here.
	 */
	public long getLootValue()
	{
		return lootValue;
	}

	/**
	 * Session net profit: {@link #getLootValue() loot value} minus the
	 * {@link #getSuppliesUsed() consumable spend} burned to earn it. This is
	 * the true bottom line — what you actually walked away with — whereas
	 * {@code getLootValue()} is gross loot before supply cost.
	 * {@link #getProfitPerHour()} is the hourly extrapolation of THIS figure,
	 * so an hour that nets negative after supplies reads negative here too.
	 */
	public long getNetProfit()
	{
		return lootValue - suppliesUsed;
	}

	public long getProfitPerHour()
	{
		return profitPerHour;
	}

	public long getGeRealizedPnl()
	{
		return geRealizedPnl;
	}

	/**
	 * "Net worth change" — the LOCKED session-panel model: the SUM of {@code
	 * getNetProfit()} (Loot − Supplies), {@link #getGeRealizedPnl()} (GE flip),
	 * {@link #getGePositions()} (GE positions) and {@link #getBankDelta()}
	 * (Bank). Held/worn/inventory gear price drift deliberately contributes to
	 * none of the four and so never shows here — it is zeroed until the item
	 * transacts (sold via GE, banked, or consumed/looted). Always the FULL,
	 * untoggled sum; a session-panel include/exclude toggle on GE positions or
	 * Bank (default both on) is display-only and subtracts the relevant raw
	 * component when rendering, never mutating this value.
	 */
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
	 * never {@link #getLootValue()}, which is realised activity only.
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
	 * {@link #getLootValue()} — consuming a supply neither gains nor loses
	 * loot value, it just moves value from "held" to "used" — so this figure is
	 * the dedicated place that spend is visible, not a duplicate of a
	 * reduction already visible in loot value. Zero for snapshots built before
	 * this tracking existed (older constructors).
	 */
	public long getSuppliesUsed()
	{
		return suppliesUsed;
	}

	/**
	 * "GE positions" — the LOCKED session-panel model's unrealised
	 * mark-to-market on open/collectable Grand Exchange offers: current pool
	 * value ({@code geInFlight + geCollectable}) minus the pool's cost basis.
	 * Moving the player's own coins/items into an offer never moves this figure
	 * (entries are added to the cost basis at the same live value that left
	 * tracked wealth); only genuine repricing while the value sits in the GE
	 * (a favourable/unfavourable fill, a sell offer's unsold remainder
	 * repricing) does. One of the four components summed into {@link
	 * #getNetWorthDelta()}; a session-panel toggle (default on) may exclude it
	 * from the displayed "Net worth change" without affecting this raw value.
	 */
	public long getGePositions()
	{
		return gePositions;
	}

	/**
	 * "Bank" — the LOCKED session-panel model's bank component: current bank
	 * value minus the session's Bank-line anchor. A deposit/withdrawal (the
	 * player's own money moving in/out) cancels to zero here; price wobble on
	 * ALREADY-banked contents (a GE price reload while the bank sits closed)
	 * shows through, since that is real wealth movement on cold storage. One of
	 * the four components summed into {@link #getNetWorthDelta()}; a
	 * session-panel toggle (default on) may exclude it from the displayed "Net
	 * worth change" without affecting this raw value.
	 */
	public long getBankDelta()
	{
		return bankDelta;
	}
}
