package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Covers {@link RiskValuation#riskValue}: a pure helper with no client-thread
 * dependency, exercised here purely against fake {@code isTradeable}/{@code
 * gePrice} functions (see class javadoc for why {@link
 * net.runelite.client.game.ItemMapping#map(int)} — a static enum lookup over
 * bundled data — works fine in a plain unit test).
 */
public class RiskValuationTest
{
	// 4708 = BARROWS_AHRIM_HEAD ("Ahrim's hood", undamaged/restored, tradeable).
	private static final int AHRIMS_HOOD = 4708;

	/**
	 * 4859 = BARROWS_AHRIM_HEAD_25 ("Ahrim's hood 25", a degraded/untradeable
	 * Barrows piece). Verified via {@code javap}-inspection of the decompiled
	 * {@code net.runelite.client.game.ItemMapping} enum shipped in {@code
	 * net.runelite:client:1.12.32} (this project's pinned classpath
	 * dependency): {@code ItemMapping.map(4859)} resolves to a single entry
	 * with {@code tradeableItem = BARROWS_AHRIM_HEAD (4708)}, {@code quantity
	 * = 1} — RuneLite's own "price a degraded Barrows piece as its restored
	 * form" convention.
	 */
	private static final int AHRIMS_HOOD_DEGRADED = 4859;

	/**
	 * 7462 = Barrows gloves — a real untradeable id with NO {@link
	 * net.runelite.client.game.ItemMapping} entry at all (verified the same
	 * way: {@code ItemMapping.map(7462)} returns no mappings), used here as
	 * the "genuinely unmapped" case.
	 */
	private static final int BARROWS_GLOVES = 7462;

	@Test
	public void tradeableItem_usesItsOwnGePrice()
	{
		long value = RiskValuation.riskValue(AHRIMS_HOOD, id -> true, id -> id == AHRIMS_HOOD ? 21_000_000L : 0L);
		assertEquals(21_000_000L, value);
	}

	@Test
	public void tradeableItem_withNonPositivePrice_clampsToZero()
	{
		long value = RiskValuation.riskValue(AHRIMS_HOOD, id -> true, id -> -5L);
		assertEquals(0L, value);
	}

	/**
	 * The core untradeable case: a real {@link net.runelite.client.game.ItemMapping}
	 * entry sums its tradeable component(s) — here a single component at
	 * quantity 1, so the degraded hood's risk value equals the undamaged
	 * hood's GE price.
	 */
	@Test
	public void untradeableItem_withRealItemMappingEntry_sumsComponentPrices()
	{
		long value = RiskValuation.riskValue(AHRIMS_HOOD_DEGRADED,
			id -> id != AHRIMS_HOOD_DEGRADED,
			id -> id == AHRIMS_HOOD ? 21_000_000L : 0L);
		assertEquals(21_000_000L, value);
	}

	/** No ItemMapping entry and no curated fallback entry -> 0, never a false "expensive" flag. */
	@Test
	public void untradeableItem_unmappedAnywhere_returnsZero()
	{
		long value = RiskValuation.riskValue(BARROWS_GLOVES, id -> false, id -> 5_000_000L);
		assertEquals(0L, value);
	}

	// -------------------------------------------------------- classify() tests

	/** 22322 = Avernic defender (untradeable, no ItemMapping entry), curated in {@link AssembledItemComponents}. */
	private static final int AVERNIC_DEFENDER = 22322;

	/** 22477 = Avernic defender hilt, the tradeable component {@link AssembledItemComponents} prices it at. */
	private static final int AVERNIC_DEFENDER_HILT = 22477;

	/** 24187 = Trouver parchment (verified via javap against the pinned net.runelite:client 1.12.32 jar). */
	private static final int TROUVER_PARCHMENT = 24187;

	/**
	 * 6570 = Fire cape — a real untradeable id with no {@link net.runelite.client.game.ItemMapping}
	 * entry, no {@link AssembledItemComponents} entry, and no curated fallback entry; the
	 * "uncoverable rare untradeable" case that must fall to the Trouver parchment price.
	 */
	private static final int FIRE_CAPE = 6570;

	/**
	 * 13124 = ARDY_CAPE_ELITE (Ardougne cloak 4) — untradeable and free to reclaim on
	 * death (diary reward). Verified via the same {@code javap}-inspection of {@code
	 * net.runelite.api.gameval.ItemID} used elsewhere in this project (e.g. {@code
	 * GearOptimizer.FREE_REOBTAINABLE}).
	 */
	private static final int ARDY_CLOAK_4 = 13124;

	@Test
	public void classify_assembledItem_usesComponentPrice()
	{
		RiskValuation.Risk risk = RiskValuation.classify(AVERNIC_DEFENDER,
			id -> id != AVERNIC_DEFENDER,
			id -> id == AVERNIC_DEFENDER_HILT ? 31_800_000L : 0L,
			1_000_000L,
			id -> false);
		assertEquals(31_800_000L, risk.value);
		assertEquals(RiskValuation.Source.ASSEMBLED, risk.source);
	}

	@Test
	public void classify_uncoverableUntradeable_fallsBackToParchmentPrice()
	{
		RiskValuation.Risk risk = RiskValuation.classify(FIRE_CAPE,
			id -> id == TROUVER_PARCHMENT,
			id -> id == TROUVER_PARCHMENT ? 1_200_000L : 0L,
			1_200_000L,
			id -> false);
		assertEquals(1_200_000L, risk.value);
		assertEquals(RiskValuation.Source.PARCHMENT, risk.source);
	}

	@Test
	public void classify_freeReobtainableUntradeable_isNeverPricedEvenWithParchmentAvailable()
	{
		RiskValuation.Risk risk = RiskValuation.classify(ARDY_CLOAK_4,
			id -> false,
			id -> 0L,
			1_200_000L,
			id -> id == ARDY_CLOAK_4);
		assertEquals(0L, risk.value);
		assertEquals(RiskValuation.Source.NONE, risk.source);
	}

	@Test
	public void classify_tradeableItem_usesOwnGePriceAndTradeableSource()
	{
		RiskValuation.Risk risk = RiskValuation.classify(AHRIMS_HOOD,
			id -> true,
			id -> id == AHRIMS_HOOD ? 21_000_000L : 0L,
			1_200_000L,
			id -> false);
		assertEquals(21_000_000L, risk.value);
		assertEquals(RiskValuation.Source.TRADEABLE, risk.source);
	}
}
