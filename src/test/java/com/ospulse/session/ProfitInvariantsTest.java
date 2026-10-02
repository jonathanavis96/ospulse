package com.ospulse.session;

import com.ospulse.ge.GeAttributions;
import com.ospulse.ge.GeOfferState;
import com.ospulse.ge.GeReconciler;
import com.ospulse.model.ItemStack;
import com.ospulse.wealth.WealthSnapshot;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Accounting invariants of the session panel, checked across whole play
 * scenarios rather than single methods.
 *
 * <ul>
 *   <li>Profit is exactly Loot - Supplies + Episode P&amp;L.</li>
 *   <li>Net worth change is exactly Profit + GE flip + GE positions + Bank.</li>
 *   <li>While the bank is unknown, "Bank" has nothing legitimate to hold, so
 *       every gp of movement must land in a named component: Bank stays 0.</li>
 *   <li>Net worth change plus unrealized P/L equals the real change in net
 *       worth: no value is computed and then dropped.</li>
 *   <li>A session reset zeroes every component.</li>
 * </ul>
 */
public class ProfitInvariantsTest
{
	private static final int COINS = GeReconciler.COINS_ITEM_ID;
	private static final int BONES = 526;
	private static final int SHARK = 385;
	private static final int WHIP = 4151;
	private static final int CLEAN_TOADFLAX = 2998;
	private static final int TOADFLAX_UNF = 3002;

	private SessionEngine engine;

	@Before
	public void setUp()
	{
		engine = new SessionEngine();
	}

	private static Map<Integer, ItemStack> items(ItemStack... stacks)
	{
		Map<Integer, ItemStack> map = new LinkedHashMap<>();
		for (ItemStack stack : stacks)
		{
			map.put(stack.getId(), stack);
		}
		return map;
	}

	/** Tracked wealth = the listed items, plus GE pools; bank unknown. */
	private static WealthSnapshot wealth(long geInFlight, long geCollectable, long ts, ItemStack... stacks)
	{
		long inv = 0L;
		for (ItemStack s : stacks)
		{
			inv += s.value();
		}
		return WealthSnapshot.builder()
			.inventoryValue(inv)
			.geInFlightValue(geInFlight)
			.geCollectableValue(geCollectable)
			.bankKnown(false)
			.timestampMs(ts)
			.trackedItems(items(stacks))
			.build();
	}

	private static WealthSnapshot wealth(long ts, ItemStack... stacks)
	{
		return wealth(0L, 0L, ts, stacks);
	}

	private SessionSnapshot snapshot(WealthSnapshot current, long geRealized, long ts)
	{
		return engine.snapshot(current, geRealized, Collections.emptyMap(), 0L, ts);
	}

	/**
	 * Checks every invariant for a snapshot taken while the bank is unknown.
	 * {@code realChange} is the true change in net worth since session start.
	 */
	private static void assertBalanced(String msg, SessionSnapshot s, long realChange)
	{
		assertEquals(msg + ": Profit = Loot - Supplies + Episode",
			s.getLootValue() - s.getSuppliesUsed() + s.getEpisodePnl(), s.getNetProfit());
		assertEquals(msg + ": Net worth change = Profit + GE flip + GE positions + Bank",
			s.getNetProfit() + s.getGeRealizedPnl() + s.getGePositions() + s.getBankDelta(),
			s.getNetWorthDelta());
		assertEquals(msg + ": with the bank unknown, nothing may fall into the Bank residual",
			0L, s.getBankDelta());
		assertEquals(msg + ": net worth change + unrealized must equal the real change",
			realChange, s.getNetWorthDelta() + s.getUnrealizedPnl());
	}

	private static void assertAllZero(String msg, SessionSnapshot s)
	{
		assertEquals(msg + ": loot", 0L, s.getLootValue());
		assertEquals(msg + ": supplies", 0L, s.getSuppliesUsed());
		assertEquals(msg + ": episode", 0L, s.getEpisodePnl());
		assertEquals(msg + ": profit", 0L, s.getNetProfit());
		assertEquals(msg + ": profit/hr", 0L, s.getProfitPerHour());
		assertEquals(msg + ": GE flip", 0L, s.getGeRealizedPnl());
		assertEquals(msg + ": GE positions", 0L, s.getGePositions());
		assertEquals(msg + ": bank", 0L, s.getBankDelta());
		assertEquals(msg + ": net worth change", 0L, s.getNetWorthDelta());
		assertEquals(msg + ": unrealized", 0L, s.getUnrealizedPnl());
		assertTrue(msg + ": loot feed", s.getLoot().isEmpty());
	}

	@Test
	public void lootThenPriceDriftSplitsIntoLootAndUnrealized()
	{
		ItemStack coins = new ItemStack(COINS, "Coins", 1_000_000L, 1L);
		engine.startSession(wealth(0L, coins), 0L);

		WealthSnapshot looted = wealth(1_000L, coins, new ItemStack(BONES, "Bones", 100L, 100L));
		engine.update(looted, (GeAttributions) null, MovementSignals.NONE, 1_000L);
		assertBalanced("after pickup", snapshot(looted, 0L, 1_000L), 10_000L);

		WealthSnapshot drifted = wealth(2_000L, coins, new ItemStack(BONES, "Bones", 100L, 120L));
		engine.update(drifted, (GeAttributions) null, MovementSignals.NONE, 2_000L);
		SessionSnapshot s = snapshot(drifted, 0L, 2_000L);
		assertBalanced("after price drift", s, 12_000L);
		assertEquals(10_000L, s.getLootValue());
		assertEquals(2_000L, s.getUnrealizedPnl());
	}

	@Test
	public void eatingFoodIsChargedAsSupplies()
	{
		engine.startSession(wealth(0L, new ItemStack(SHARK, "Shark", 10L, 1_000L)), 0L);

		WealthSnapshot ate = wealth(1_000L, new ItemStack(SHARK, "Shark", 7L, 1_000L));
		engine.update(ate, (GeAttributions) null, MovementSignals.NONE, 1_000L);
		SessionSnapshot s = snapshot(ate, 0L, 1_000L);
		assertBalanced("after eating", s, -3_000L);
		assertEquals(3_000L, s.getSuppliesUsed());
	}

	@Test
	public void craftingAtALossLandsInEpisodePnl()
	{
		engine.startSession(wealth(0L, new ItemStack(CLEAN_TOADFLAX, "Clean toadflax", 10L, 8_000L)), 0L);

		WealthSnapshot crafted = wealth(1_000L, new ItemStack(TOADFLAX_UNF, "Toadflax potion(unf)", 10L, 6_000L));
		engine.update(crafted, (GeAttributions) null,
			MovementSignals.builder().productionXp().productionAnimation().build(), 1_000L);
		SessionSnapshot s = snapshot(crafted, 0L, 1_000L);
		assertBalanced("after crafting", s, -20_000L);
		assertEquals(-20_000L, s.getEpisodePnl());
	}

	@Test
	public void sellingLootThroughTheGeRealisesItsProceedsAsLoot()
	{
		GeReconciler ge = new GeReconciler();
		ItemStack coins = new ItemStack(COINS, "Coins", 1_000_000L, 1L);
		engine.startSession(wealth(0L, coins), 0L);

		WealthSnapshot looted = wealth(1_000L, coins, new ItemStack(BONES, "Bones", 100L, 100L));
		engine.update(looted, ge, MovementSignals.NONE, 1_000L);

		// Sell offer placed: the bones leave the inventory into the exchange.
		ge.onOfferUpdate(0, GeOfferState.SELLING, BONES, "Bones", 100L, 0L, 0L, 100L, 2_000L);
		WealthSnapshot placed = wealth(10_000L, 0L, 2_000L, coins);
		engine.update(placed, ge, MovementSignals.NONE, 2_000L);

		// Fills at 100 each; tax 2/ea leaves 9,800 to collect.
		ge.onOfferUpdate(0, GeOfferState.SOLD, BONES, "Bones", 100L, 100L, 10_000L, 100L, 3_000L);
		WealthSnapshot filled = wealth(0L, ge.collectableValue(id -> 100L), 3_000L, coins);
		engine.update(filled, ge, MovementSignals.NONE, 3_000L);

		WealthSnapshot collected = wealth(4_000L, new ItemStack(COINS, "Coins", 1_009_800L, 1L));
		engine.update(collected, ge, MovementSignals.NONE, 4_000L);
		SessionSnapshot s = snapshot(collected, ge.realizedPnl(), 4_000L);
		assertBalanced("after selling loot", s, 9_800L);
		assertEquals(9_800L, s.getLootValue());
	}

	/**
	 * A GE buy that fills BELOW its offer price refunds the difference. The
	 * flip's cost basis is what was actually paid, so the whole margin belongs
	 * to GE flip; none of it may leak into the Bank residual.
	 */
	@Test
	public void geFlipBoughtBelowTheOfferPriceBooksTheWholeMarginAsGeFlip()
	{
		GeReconciler ge = new GeReconciler();
		engine.startSession(wealth(0L, new ItemStack(COINS, "Coins", 1_000_000L, 1L)), 0L);

		// Buy offer for 10 whips at up to 100,000 each: 1,000,000 escrowed.
		ge.onOfferUpdate(0, GeOfferState.BUYING, WHIP, "Abyssal whip", 10L, 0L, 0L, 100_000L, 1_000L);
		WealthSnapshot escrowed = wealth(1_000_000L, 0L, 1_000L);
		engine.update(escrowed, ge, MovementSignals.NONE, 1_000L);

		// Fills at 90,000 each: 900,000 spent, 100,000 refunded to the box.
		ge.onOfferUpdate(0, GeOfferState.BOUGHT, WHIP, "Abyssal whip", 10L, 10L, 900_000L, 100_000L, 2_000L);
		WealthSnapshot filled = wealth(0L, ge.collectableValue(id -> 90_000L), 2_000L);
		engine.update(filled, ge, MovementSignals.NONE, 2_000L);

		ge.onOfferUpdate(0, GeOfferState.EMPTY, -1, "", 0L, 0L, 0L, 0L, 3_000L);
		WealthSnapshot collected = wealth(3_000L,
			new ItemStack(COINS, "Coins", 100_000L, 1L), new ItemStack(WHIP, "Abyssal whip", 10L, 90_000L));
		engine.update(collected, ge, MovementSignals.NONE, 3_000L);

		// Sell all 10 at 100,000: tax 2,000/ea leaves 980,000.
		ge.onOfferUpdate(1, GeOfferState.SELLING, WHIP, "Abyssal whip", 10L, 0L, 0L, 100_000L, 4_000L);
		WealthSnapshot placed = wealth(900_000L, 0L, 4_000L, new ItemStack(COINS, "Coins", 100_000L, 1L));
		engine.update(placed, ge, MovementSignals.NONE, 4_000L);

		ge.onOfferUpdate(1, GeOfferState.SOLD, WHIP, "Abyssal whip", 10L, 10L, 1_000_000L, 100_000L, 5_000L);
		WealthSnapshot sold = wealth(0L, ge.collectableValue(id -> 90_000L), 5_000L,
			new ItemStack(COINS, "Coins", 100_000L, 1L));
		engine.update(sold, ge, MovementSignals.NONE, 5_000L);

		WealthSnapshot done = wealth(6_000L, new ItemStack(COINS, "Coins", 1_080_000L, 1L));
		engine.update(done, ge, MovementSignals.NONE, 6_000L);
		SessionSnapshot s = snapshot(done, ge.realizedPnl(), 6_000L);

		assertEquals("flip = (98,000 net - 90,000 paid) x 10", 80_000L, s.getGeRealizedPnl());
		assertBalanced("after the flip", s, 80_000L);
	}

	/**
	 * A session that starts (login or manual reset) while a CANCELLED offer
	 * still waits in the collection box. Its goods were owned before the
	 * session, so collecting them is a transfer, not loot.
	 */
	@Test
	public void collectingACancelledOfferFromBeforeTheSessionIsNotLoot()
	{
		GeReconciler ge = new GeReconciler();
		// Slot 0: sell of 10 whips cancelled with 4 sold at 100,000 (tax 2,000/ea).
		ge.primeCollectable(0, GeOfferState.CANCELLED_SELL, WHIP, 10L, 4L, 400_000L, 100_000L);
		// Slot 1: buy of 5 whips at up to 100,000, cancelled after 2 filled for 180,000.
		ge.primeCollectable(1, GeOfferState.CANCELLED_BUY, WHIP, 5L, 2L, 180_000L, 100_000L);
		WealthSnapshot start = wealth(0L, ge.collectableValue(id -> 90_000L), 0L);
		engine.startSession(start, 0L);

		ge.onOfferUpdate(0, GeOfferState.EMPTY, -1, "", 0L, 0L, 0L, 0L, 1_000L);
		ge.onOfferUpdate(1, GeOfferState.EMPTY, -1, "", 0L, 0L, 0L, 0L, 1_000L);
		WealthSnapshot collected = wealth(1_000L,
			new ItemStack(COINS, "Coins", 392_000L + 320_000L, 1L),
			new ItemStack(WHIP, "Abyssal whip", 6L + 2L, 90_000L));
		engine.update(collected, ge, MovementSignals.NONE, 1_000L);
		SessionSnapshot s = snapshot(collected, ge.realizedPnl(), 1_000L);

		assertEquals("collecting pre-session goods is not loot", 0L, s.getLootValue());
		assertBalanced("after collecting", s, 0L);
	}

	/**
	 * A reset while owned value sits outside tracked wealth (a deployed
	 * cannon, items held by Death) keeps that value owned: getting it back
	 * after the reset is not loot.
	 */
	@Test
	public void reclaimingACannonOrDeathItemsAfterAResetIsNotLoot()
	{
		ItemStack base = new ItemStack(6, "Cannon base", 1L, 200_000L);
		ItemStack whip = new ItemStack(WHIP, "Abyssal whip", 1L, 1_000_000L);
		engine.startSession(wealth(0L, base, whip), 0L);

		// Cannon set up, then the player dies with the whip.
		WealthSnapshot deployed = wealth(1_000L, whip);
		engine.update(deployed, (GeAttributions) null, MovementSignals.NONE, 1_000L);
		WealthSnapshot died = wealth(2_000L);
		engine.update(died, (GeAttributions) null, MovementSignals.builder().died(true).build(), 2_000L);

		engine.startSession(died, 3_000L);
		assertAllZero("right after reset", snapshot(died, 0L, 3_000L));

		WealthSnapshot reclaimed = wealth(4_000L, base, whip);
		engine.update(reclaimed, (GeAttributions) null, MovementSignals.NONE, 4_000L);
		assertAllZero("after reclaiming", snapshot(reclaimed, 0L, 4_000L));
	}

	@Test
	public void resetZeroesEveryComponent()
	{
		GeReconciler ge = new GeReconciler();
		ItemStack coins = new ItemStack(COINS, "Coins", 1_000_000L, 1L);
		engine.startSession(wealth(0L, coins,
			new ItemStack(SHARK, "Shark", 10L, 1_000L),
			new ItemStack(CLEAN_TOADFLAX, "Clean toadflax", 10L, 8_000L)), 0L);

		// Loot, eat, and start a craft whose output has not landed yet.
		WealthSnapshot busy = wealth(1_000L, coins,
			new ItemStack(SHARK, "Shark", 7L, 1_000L),
			new ItemStack(CLEAN_TOADFLAX, "Clean toadflax", 5L, 8_000L),
			new ItemStack(BONES, "Bones", 100L, 120L));
		engine.update(busy, ge, MovementSignals.builder().productionAnimation().build(), 1_000L);
		WealthSnapshot drifted = wealth(2_000L, coins,
			new ItemStack(SHARK, "Shark", 7L, 1_000L),
			new ItemStack(CLEAN_TOADFLAX, "Clean toadflax", 5L, 8_000L),
			new ItemStack(BONES, "Bones", 100L, 150L));
		engine.update(drifted, ge, MovementSignals.NONE, 2_000L);

		engine.startSession(drifted, 3_000L);
		assertAllZero("right after reset", snapshot(drifted, 0L, 3_000L));

		// Nothing pending from before the reset may book afterwards.
		engine.update(drifted, ge, MovementSignals.NONE, 400_000L);
		assertAllZero("long after reset", snapshot(drifted, 0L, 400_000L));
	}
}
