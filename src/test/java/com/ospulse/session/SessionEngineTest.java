package com.ospulse.session;

import com.ospulse.ge.GeAttributions;
import com.ospulse.ge.GeOfferState;
import com.ospulse.ge.GeReconciler;
import com.ospulse.model.ItemStack;
import com.ospulse.wealth.WealthSnapshot;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionEngineTest
{
	private static final int DRAGON_BONES = 536;
	private static final int RUNE_ITEM = 561;
	private static final int RUNE_SQ_SHIELD = 1201;
	private static final int BIRD_NEST = 5075;
	private static final int PRAYER_POT = 2434;
	private static final int BARLEY_SEED = 5305;

	private SessionEngine engine;

	@Before
	public void setUp()
	{
		engine = new SessionEngine();
	}

	private static WealthSnapshot snap(long inventoryValue, Map<Integer, ItemStack> trackedItems,
		long bankValue, boolean bankKnown, long ts)
	{
		return WealthSnapshot.builder()
			.inventoryValue(inventoryValue)
			.equipmentValue(0L)
			.geInFlightValue(0L)
			.pouchValue(0L)
			.bankValue(bankValue)
			.bankKnown(bankKnown)
			.timestampMs(ts)
			.trackedItems(trackedItems)
			.build();
	}

	@Test
	public void awayLootGainRecordsProfitAndLootEntry()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot current = snap(11_000_000L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(1_000_000L, result.getLootValue());
		assertEquals(1, result.getLoot().size());
		LootEntry loot = result.getLoot().get(0);
		assertEquals(DRAGON_BONES, loot.getItemId());
		assertEquals(1_000_000L, loot.getValue());
		assertEquals(100L, loot.getQuantity());
	}

	@Test
	public void pureGeFlipIsNotLootAndReconciles()
	{
		// Start at 10M tracked, no bank.
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// A GE flip nets +500k: tracked wealth rises by the proceeds, and the
		// reconciler reports the flip P&L separately. No ground loot, no supplies.
		WealthSnapshot afterFlip = snap(10_500_000L, Collections.emptyMap(), 0L, false, 1000L);
		engine.update(afterFlip, Collections.emptySet(), 1000L);

		SessionSnapshot s = engine.snapshot(afterFlip, 500_000L, Collections.emptyMap(), 0L, 1000L);

		assertEquals("flip winnings are not loot", 0L, s.getLootValue());
		assertEquals(500_000L, s.getGeRealizedPnl());
		assertEquals("profit = loot - supplies", 0L, s.getNetProfit());
		assertEquals("net worth rose by the flip", 500_000L, s.getNetWorthDelta());
	}

	@Test
	public void lootPlusFlipReconcilesAndLootExcludesFlip()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);
		GeReconciler ge = new GeReconciler();

		// Buy 200,000 coins' worth: 250 sharks @800 (200,000) then collect them.
		ge.onOfferUpdate(0, GeOfferState.BOUGHT, 385, "Shark", 250, 250, 200_000L, 800L, 500L);
		ge.onOfferUpdate(0, GeOfferState.EMPTY, 385, "Shark", 250, 250, 200_000L, 800L, 600L);

		// Ground loot: 30 dragon bones (300k). The 250 bought sharks are also now
		// in the inventory (collected) and must be attributed to the GE, not loot.
		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 30L, 10_000L)); // 300k
		tracked.put(385, new ItemStack(385, "Shark", 250L, 800L)); // 200k bought
		WealthSnapshot current = snap(10_500_000L, tracked, 0L, false, 1000L);
		ge.expireAttributions(1000L);
		engine.update(current, ge, 1000L);

		SessionSnapshot s = engine.snapshot(current, ge.realizedPnl(),
			Collections.emptyMap(), 0L, 1000L);
		assertEquals("loot excludes GE-bought items", 300_000L, s.getLootValue());
		assertEquals("profit = loot - supplies", 300_000L, s.getNetProfit());
	}

	@Test
	public void bankDepositIsNeutralised()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 0L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Deposited 50M: tracked drops by 50M while bank is open.
		WealthSnapshot afterDeposit = snap(50_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.setBankOpen(false, afterDeposit, 200L);

		SessionSnapshot result = engine.snapshot(afterDeposit, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(0L, result.getLootValue());
	}

	@Test
	public void bankWithdrawIsNeutralised()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 20_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 20M supplies: tracked rises by 20M, bank drops by 20M.
		WealthSnapshot afterWithdraw = snap(120_000_000L, Collections.emptyMap(), 0L, true, 200L);
		engine.setBankOpen(false, afterWithdraw, 200L);

		SessionSnapshot result = engine.snapshot(afterWithdraw, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(0L, result.getLootValue());
	}

	@Test
	public void withdrawThenConsumeShowsRealLoss()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 20_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 20M worth of runes: tracked rises by 20M, bank drops by 20M.
		WealthSnapshot afterWithdraw = snap(120_000_000L, items(new ItemStack(RUNE_ITEM, "Nature rune", 100_000L, 200L)), 0L, true, 200L);
		engine.setBankOpen(false, afterWithdraw, 200L);

		// Away: used the runes, tracked wealth drops by 20M with no
		// corresponding item gain (negative deltas aren't loot).
		WealthSnapshot afterConsume = snap(100_000_000L, Collections.emptyMap(), 0L, true, 300L);
		engine.update(afterConsume, Collections.emptySet(), 300L);

		SessionSnapshot result = engine.snapshot(afterConsume, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals("consuming runes books supplies", 20_000_000L, result.getSuppliesUsed());
		assertEquals("profit = loot - supplies", -20_000_000L, result.getNetProfit());
		assertEquals(0L, result.getLootValue());
		assertTrue(result.getLoot().isEmpty());
	}

	@Test
	public void geProceedsCollectedToBankWhileBankClosedIsNotBookedAsLoss()
	{
		// A GE sell has filled: 200k of proceeds sit in the collection box
		// (geCollectableValue), already counted as tracked wealth so net worth
		// didn't dip on fill. The bank has been opened earlier this session
		// (bankKnown), but is NOT open now — the player is on the GE screen.
		WealthSnapshot initial = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.geCollectableValue(200_000L)
			.bankValue(50_000_000L)
			.bankKnown(true)
			.timestampMs(0L)
			.build();
		engine.startSession(initial, 0L);

		// Collect the proceeds straight to the BANK (the collection box "Bank"
		// button): coins bypass the inventory, so geCollectable drops to 0 and
		// the bank rises by 200k, all while bankOpen stays false.
		WealthSnapshot afterCollectToBank = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.geCollectableValue(0L)
			.bankValue(50_200_000L)
			.bankKnown(true)
			.timestampMs(1000L)
			.build();
		engine.update(afterCollectToBank, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(afterCollectToBank, 0L, Collections.emptyMap(), 0L, 1000L);
		// Moving your own GE proceeds into the bank is a zero-sum transfer.
		assertEquals(0L, result.getLootValue());
		assertTrue(result.getLoot().isEmpty());
	}

	@Test
	public void geProceedsCollectedToBankWithLaggedBankUpdateIsNotBookedAsLoss()
	{
		WealthSnapshot initial = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.geCollectableValue(200_000L)
			.bankValue(50_000_000L)
			.bankKnown(true)
			.timestampMs(0L)
			.build();
		engine.startSession(initial, 0L);

		// Collect to bank: geCollectable drops immediately, but the bank
		// container reading lags a few ticks (observed ~3.6s) so the bank rise
		// isn't visible yet. The transfer must not dip profit meanwhile.
		WealthSnapshot afterCollect = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.geCollectableValue(0L)
			.bankValue(50_000_000L)
			.bankKnown(true)
			.timestampMs(1000L)
			.build();
		engine.update(afterCollect, Collections.emptySet(), 1000L);
		SessionSnapshot midLag = engine.snapshot(afterCollect, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals(0L, midLag.getLootValue());

		// The lagged bank rise finally lands.
		WealthSnapshot bankCaughtUp = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.geCollectableValue(0L)
			.bankValue(50_200_000L)
			.bankKnown(true)
			.timestampMs(4000L)
			.build();
		engine.update(bankCaughtUp, Collections.emptySet(), 4000L);

		SessionSnapshot result = engine.snapshot(bankCaughtUp, 0L, Collections.emptyMap(), 0L, 4000L);
		assertEquals(0L, result.getLootValue());
		assertTrue(result.getLoot().isEmpty());
	}

	@Test
	public void geProceedsCollectedToInventoryDoesNotCreatePhantomGainOrLoss()
	{
		// Guards the collect-to-BANK fix from over-holding: collecting to the
		// inventory is offset by an inventory rise (tracked unchanged), so no
		// deposit must be held and no phantom profit created. The coin arrival
		// is GE-attributed, so it is excluded from loot.
		WealthSnapshot initial = WealthSnapshot.builder()
			.inventoryValue(1_000_000L)
			.geCollectableValue(200_000L)
			.bankValue(50_000_000L)
			.bankKnown(true)
			.timestampMs(0L)
			.build();
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> withCoins = new LinkedHashMap<>();
		withCoins.put(COINS, new ItemStack(COINS, "Coins", 200_000L, 1L));
		WealthSnapshot afterCollectToInv = WealthSnapshot.builder()
			.inventoryValue(1_200_000L)
			.geCollectableValue(0L)
			.bankValue(50_000_000L)
			.bankKnown(true)
			.timestampMs(1000L)
			.trackedItems(withCoins)
			.build();
		engine.update(afterCollectToInv, new HashSet<>(Collections.singletonList(COINS)), 1000L);

		SessionSnapshot result = engine.snapshot(afterCollectToInv, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals(0L, result.getLootValue());
		assertTrue(result.getLoot().isEmpty());
	}

	@Test
	public void depositWithBankStillOpenIsZeroSumImmediately()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Deposited 30M while the bank is STILL OPEN: inventory down 30M,
		// bank up 30M. Zero-sum transfer — nothing may move, not even
		// transiently before the bank closes.
		WealthSnapshot midDeposit = snap(70_000_000L, Collections.emptyMap(), 80_000_000L, true, 200L);
		engine.update(midDeposit, Collections.emptySet(), 200L);

		SessionSnapshot result = engine.snapshot(midDeposit, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals("deposit must not read as a loss while the bank is open",
			0L, result.getLootValue());
		assertEquals(0L, result.getProfitPerHour());
		assertEquals(0L, result.getNetWorthDelta());
	}

	@Test
	public void withdrawalWithBankStillOpenIsZeroSumImmediately()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Withdrew 30M while the bank is STILL OPEN: inventory up 30M, bank
		// down 30M. Must not read as a gain.
		WealthSnapshot midWithdraw = snap(130_000_000L, Collections.emptyMap(), 20_000_000L, true, 200L);
		engine.update(midWithdraw, Collections.emptySet(), 200L);

		SessionSnapshot result = engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals("withdrawal must not read as a gain while the bank is open",
			0L, result.getLootValue());
		assertEquals(0L, result.getProfitPerHour());
		assertEquals(0L, result.getNetWorthDelta());
	}

	@Test
	public void realGainWhileBankOpenStillCountsLiveAndAfterClose()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// A genuine 5M gain lands while the bank happens to be open (e.g. a
		// collected GE sale): tracked up 5M, bank untouched. This is real
		// profit and must be counted live...
		WealthSnapshot midGain = snap(105_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.update(midGain, Collections.emptySet(), 200L);

		SessionSnapshot live = engine.snapshot(midGain, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(0L, live.getLootValue());
		assertEquals(5_000_000L, live.getNetWorthDelta());

		// ...and must survive the close reconciliation (not be swallowed by
		// the bank-visit neutralisation, and not be double-counted).
		engine.setBankOpen(false, midGain, 300L);
		SessionSnapshot closed = engine.snapshot(midGain, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(0L, closed.getLootValue());
		assertEquals(5_000_000L, closed.getNetWorthDelta());
	}

	@Test
	public void depositPlusRealGainWhileBankOpenIsContinuousAcrossClose()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		// Same visit: deposited 30M (inventory -30M, bank +30M) AND gained a
		// genuine 5M. Only the 5M is profit, live and repeatedly.
		WealthSnapshot mid = snap(75_000_000L, Collections.emptyMap(), 80_000_000L, true, 200L);
		engine.update(mid, Collections.emptySet(), 200L);

		SessionSnapshot first = engine.snapshot(mid, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(0L, first.getLootValue());
		SessionSnapshot second = engine.snapshot(mid, 0L, Collections.emptyMap(), 0L, 250L);
		assertEquals("repeated snapshots while the bank is open must not drift",
			0L, second.getLootValue());

		// Closing must not change the figure (no jump, no double-count)...
		engine.setBankOpen(false, mid, 300L);
		SessionSnapshot closed = engine.snapshot(mid, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(0L, closed.getLootValue());
		assertEquals(5_000_000L, closed.getNetWorthDelta());

		// ...and a quiet away-tick after the close keeps it stable.
		WealthSnapshot after = snap(75_000_000L, Collections.emptyMap(), 80_000_000L, true, 400L);
		engine.update(after, Collections.emptySet(), 400L);
		SessionSnapshot later = engine.snapshot(after, 0L, Collections.emptyMap(), 0L, 400L);
		assertEquals(0L, later.getLootValue());
	}

	@Test
	public void secondBankVisitReanchorsTransferOffset()
	{
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		// Visit 1: deposit 30M, close. Profit stays 0.
		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);
		WealthSnapshot afterDeposit = snap(70_000_000L, Collections.emptyMap(), 80_000_000L, true, 200L);
		engine.setBankOpen(false, afterDeposit, 200L);
		assertEquals(0L, engine.snapshot(afterDeposit, 0L, Collections.emptyMap(), 0L, 200L).getLootValue());

		// Visit 2: withdraw 10M mid-visit. The offset must be measured from
		// THIS visit's opening bank value, not the first visit's.
		engine.setBankOpen(true, snap(70_000_000L, Collections.emptyMap(), 80_000_000L, true, 300L), 300L);
		WealthSnapshot midWithdraw = snap(80_000_000L, Collections.emptyMap(), 70_000_000L, true, 400L);
		engine.update(midWithdraw, Collections.emptySet(), 400L);
		assertEquals(0L, engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 400L).getLootValue());

		engine.setBankOpen(false, midWithdraw, 500L);
		assertEquals(0L, engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 500L).getLootValue());
	}

	@Test
	public void withdrawalWhoseBankUpdateLagsTheCloseIsStillNeutralised()
	{
		// Regression (real session, 10:23:10): a 22.98M withdrawal landed in
		// the inventory while the bank was still open, but the BANK container
		// update arrived ~2s AFTER the widget-closed event. The close-time
		// reanchor therefore measured a bank change of 0 and neutralised
		// nothing, booking the whole withdrawal as +22.98M phantom profit —
		// permanently, since the late bank update (while closed) only moved
		// netWorthDelta. The late tail of the visit's transfer must be folded
		// into the baseline exactly as the close reanchor would have.
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);

		// Withdrew 20M: the inventory reflects it while the bank is still
		// open, but the bank value hasn't refreshed yet.
		WealthSnapshot invUpdated = snap(120_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.update(invUpdated, Collections.emptySet(), 200L);

		// Bank closes before the bank container catches up: the close-time
		// shift sees a 0 bank change.
		engine.setBankOpen(false, invUpdated, 300L);

		// The bank container update lands ~2s after the close.
		WealthSnapshot bankCaughtUp = snap(120_000_000L, Collections.emptyMap(), 30_000_000L, true, 2_300L);
		engine.update(bankCaughtUp, Collections.emptySet(), 2_300L);

		SessionSnapshot result = engine.snapshot(bankCaughtUp, 0L, Collections.emptyMap(), 0L, 2_300L);
		assertEquals("a withdrawal whose bank update lags the close must not read as profit",
			0L, result.getLootValue());
		assertEquals(0L, result.getNetWorthDelta());
		assertTrue(result.getLoot().isEmpty());

		// Still neutral on later quiet ticks (the reconciliation must not
		// re-apply itself once the anchor has caught up).
		WealthSnapshot later = snap(120_000_000L, Collections.emptyMap(), 30_000_000L, true, 10_000L);
		engine.update(later, Collections.emptySet(), 10_000L);
		assertEquals(0L, engine.snapshot(later, 0L, Collections.emptyMap(), 0L, 10_000L).getLootValue());
	}

	@Test
	public void depositWhoseBankUpdateLagsIsNotAPhantomLossWhileBankOpen()
	{
		// Regression (real session, 12:27:2x): RuneLite updates the inventory
		// container instantly but the bank-container value can lag by up to
		// ~3.6s and arrives as a separate ItemContainerChanged. A 365,183
		// deposit therefore produced a snapshot where tracked wealth had
		// already dropped while the bank value was still stale — and because
		// the live transfer offset is derived from the (stale) bank side only,
		// profit dipped by the full deposit (-365,183, realised 312,552 ->
		// -52,631) for ~3s until the bank caught up. The in-flight half of a
		// transfer must not read as a loss, not even transiently.
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);

		// Deposited 365,183: the inventory reflects it instantly, the bank
		// value is still stale.
		WealthSnapshot invOnly = snap(99_634_817L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.update(invOnly, Collections.emptySet(), 200L);

		SessionSnapshot midLag = engine.snapshot(invOnly, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals("an in-flight deposit must not dip profit while the bank value lags",
			0L, midLag.getLootValue());
		assertEquals(0L, midLag.getProfitPerHour());
		assertEquals("in-flight wealth is still owned — net worth must not dip",
			0L, midLag.getNetWorthDelta());

		// The bank container catches up ~3.6s later, bank still open.
		WealthSnapshot caughtUp = snap(99_634_817L, Collections.emptyMap(), 50_365_183L, true, 3_800L);
		engine.update(caughtUp, Collections.emptySet(), 3_800L);
		SessionSnapshot settled = engine.snapshot(caughtUp, 0L, Collections.emptyMap(), 0L, 3_800L);
		assertEquals("the catch-up must not move profit either", 0L, settled.getLootValue());
		assertEquals(0L, settled.getNetWorthDelta());

		// Continuous across the close and later quiet ticks.
		engine.setBankOpen(false, caughtUp, 4_000L);
		assertEquals(0L, engine.snapshot(caughtUp, 0L, Collections.emptyMap(), 0L, 4_000L).getLootValue());
		WealthSnapshot later = snap(99_634_817L, Collections.emptyMap(), 50_365_183L, true, 8_000L);
		engine.update(later, Collections.emptySet(), 8_000L);
		assertEquals(0L, engine.snapshot(later, 0L, Collections.emptyMap(), 0L, 8_000L).getLootValue());
	}

	@Test
	public void rapidBankShuffleWithLaggedBankUpdatesDoesNotOscillateProfit()
	{
		// Regression (real session, 12:27:27-12:28:05): the user opened/closed
		// the bank every 1-2s while shuffling 57,149,628 back and forth. The
		// bank-container reading lags ~2s, longer than a visit cycle, so each
		// movement's reading landed during the WRONG visit's open window or
		// closed gap. The close-time anchor diff and the closed-state grace
		// reconciler each booked the same movements against different visits,
		// so the baseline flip-flopped between 274,550,507 and 331,700,135
		// repeatedly and profit swung by +-57.1M for seconds at a time. A pure
		// shuffle conserves total net worth: profit must never dip negative
		// and must settle at exactly zero.
		long w = 57_149_628L;
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 200_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		// Visit 1: withdraw 57.1M. Inventory updates instantly; the bank
		// reading stays stale for the whole visit.
		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 200_000_000L, true, 1_000L), 1_000L);
		engine.update(snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L, true, 1_500L),
			Collections.emptySet(), 1_500L);
		engine.setBankOpen(false, snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L, true, 2_000L), 2_000L);

		// Visit 2: deposit the 57.1M back. Visit 1's bank reading is STILL in
		// flight when this visit opens.
		engine.setBankOpen(true, snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L, true, 2_500L), 2_500L);
		engine.update(snap(100_000_000L, Collections.emptyMap(), 200_000_000L, true, 3_000L),
			Collections.emptySet(), 3_000L);

		// Visit 1's withdrawal reading finally lands — during visit 2's open
		// window. Total net worth is conserved (the deposit is in flight, the
		// withdrawal reading just caught up): profit must read 0, not -57.1M.
		engine.update(snap(100_000_000L, Collections.emptyMap(), 200_000_000L - w, true, 3_500L),
			Collections.emptySet(), 3_500L);
		SessionSnapshot midVisit2 = engine.snapshot(
			snap(100_000_000L, Collections.emptyMap(), 200_000_000L - w, true, 3_500L),
			0L, Collections.emptyMap(), 0L, 3_500L);
		assertEquals("a lagged withdrawal reading landing in the next visit must not dip profit",
			0L, midVisit2.getLootValue());

		WealthSnapshot atClose2 = snap(100_000_000L, Collections.emptyMap(), 200_000_000L - w, true, 4_000L);
		engine.setBankOpen(false, atClose2, 4_000L);
		SessionSnapshot afterClose2 = engine.snapshot(atClose2, 0L, Collections.emptyMap(), 0L, 4_000L);
		assertEquals("closing the mis-paired visit must not book a phantom -57.1M",
			0L, afterClose2.getLootValue());

		// Visit 3: withdraw again; visit 2's deposit reading lands mid-visit.
		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 200_000_000L - w, true, 4_500L), 4_500L);
		engine.update(snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L - w, true, 5_000L),
			Collections.emptySet(), 5_000L);
		SessionSnapshot midVisit3 = engine.snapshot(
			snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L - w, true, 5_000L),
			0L, Collections.emptyMap(), 0L, 5_000L);
		assertEquals(0L, midVisit3.getLootValue());
		engine.update(snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L, true, 5_500L),
			Collections.emptySet(), 5_500L);
		engine.setBankOpen(false, snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L, true, 6_000L), 6_000L);

		// Visit 3's withdrawal reading lands 1s after its close.
		WealthSnapshot settledSnap = snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L - w, true, 7_000L);
		engine.update(settledSnap, Collections.emptySet(), 7_000L);
		SessionSnapshot settled = engine.snapshot(settledSnap, 0L, Collections.emptyMap(), 0L, 7_000L);
		assertEquals("a pure shuffle must settle at exactly zero profit", 0L, settled.getLootValue());
		assertEquals(0L, settled.getNetWorthDelta());
		assertEquals(0L, settled.getSuppliesUsed());
		assertTrue(settled.getLoot().isEmpty());

		// Stable on later quiet ticks — no re-application, no oscillation.
		WealthSnapshot quiet = snap(100_000_000L + w, Collections.emptyMap(), 200_000_000L - w, true, 8_000L);
		engine.update(quiet, Collections.emptySet(), 8_000L);
		assertEquals(0L, engine.snapshot(quiet, 0L, Collections.emptyMap(), 0L, 8_000L).getLootValue());
	}

	@Test
	public void depositCatchUpArrivingAfterCloseGraceWindowIsStillNeutralised()
	{
		// The close grace window alone cannot cover a deposit whose bank
		// update lags past it: the movement was then folded into startNetWorth
		// as a "revaluation", leaving the deposit's tracked-side drop booked
		// as a permanent phantom loss. The deposit's in-flight expectation
		// (created when the tracked drop was observed with no matching bank
		// rise) must settle the late catch-up regardless of the grace window.
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);
		WealthSnapshot invOnly = snap(99_634_817L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.update(invOnly, Collections.emptySet(), 200L);
		engine.setBankOpen(false, invOnly, 1_000L);

		// The bank catch-up lands 6.5s after the close — outside the 5s grace
		// window but well inside the deposit's settle expectation.
		WealthSnapshot caughtUp = snap(99_634_817L, Collections.emptyMap(), 50_365_183L, true, 7_500L);
		engine.update(caughtUp, Collections.emptySet(), 7_500L);

		SessionSnapshot result = engine.snapshot(caughtUp, 0L, Collections.emptyMap(), 0L, 7_500L);
		assertEquals("a deposit catch-up beyond the grace window must still be neutralised",
			0L, result.getLootValue());
		assertEquals(0L, result.getNetWorthDelta());

		// Still neutral later — the settlement must not re-apply.
		WealthSnapshot later = snap(99_634_817L, Collections.emptyMap(), 50_365_183L, true, 20_000L);
		engine.update(later, Collections.emptySet(), 20_000L);
		assertEquals(0L, engine.snapshot(later, 0L, Collections.emptyMap(), 0L, 20_000L).getLootValue());
	}

	@Test
	public void staleBankRereadAfterWithdrawalDoesNotBookPhantomProfit()
	{
		// Regression (real session, verbose diag trace): a 58,388,197
		// withdrawal was observed and booked correctly (both halves, bank
		// open), the bank was closed and reopened within seconds, and
		// RuneLite then briefly re-served the PRE-withdrawal state of BOTH
		// containers — the inventory AND the bank rewound to their exact
		// pre-withdrawal values in one observation. The engine read the bank
		// rise as a deposit and folded the baseline back down; when the
		// inventory then snapped FORWARD to the true withdrawn state while
		// the bank reading stayed stale-high, the snapshot double-counted the
		// withdrawal and realised profit jumped by +58.38M. If the bank is
		// closed during the spike no correcting read arrives, and the
		// phantom sticks until the next visit.
		long inv = 22_475_227L;
		long bank = 1_019_158_757L;
		long w = 58_388_197L;
		engine.startSession(snap(inv, Collections.emptyMap(), bank, true, 0L), 0L);

		// Withdraw w, both halves observed while open — zero-sum.
		engine.setBankOpen(true, snap(inv, Collections.emptyMap(), bank, true, 1_000L), 1_000L);
		WealthSnapshot withdrawn = snap(inv + w, Collections.emptyMap(), bank - w, true, 2_000L);
		engine.update(withdrawn, Collections.emptySet(), 2_000L);
		assertEquals(0L, engine.snapshot(withdrawn, 0L, Collections.emptyMap(), 0L, 2_000L).getLootValue());

		// Rapid close -> reopen.
		engine.setBankOpen(false, withdrawn, 3_000L);
		engine.setBankOpen(true, withdrawn, 4_000L);

		// Stale re-read: both containers momentarily rewind to the exact
		// pre-withdrawal snapshot. Indistinguishable from re-depositing the
		// withdrawal, and internally consistent either way: profit holds.
		WealthSnapshot stale = snap(inv, Collections.emptyMap(), bank, true, 4_600L);
		engine.update(stale, Collections.emptySet(), 4_600L);
		assertEquals(0L, engine.snapshot(stale, 0L, Collections.emptyMap(), 0L, 4_600L).getLootValue());

		// The inventory snaps forward to the true withdrawn state while the
		// bank reading stays stale-high: the snapshot now carries the
		// withdrawal twice. This must NOT book as +58.38M profit.
		WealthSnapshot bounced = snap(inv + w, Collections.emptyMap(), bank, true, 5_200L);
		engine.update(bounced, Collections.emptySet(), 5_200L);
		SessionSnapshot spike = engine.snapshot(bounced, 0L, Collections.emptyMap(), 0L, 5_200L);
		assertEquals("a stale bank re-read must not book the withdrawal as fresh profit",
			0L, spike.getLootValue());
		assertEquals(0L, spike.getNetWorthDelta());

		// The bank closes during the spike, so no correcting read arrives
		// for the rest of the away stretch. The phantom must not stick.
		engine.setBankOpen(false, bounced, 6_000L);
		WealthSnapshot closedStale = snap(inv + w, Collections.emptyMap(), bank, true, 20_000L);
		engine.update(closedStale, Collections.emptySet(), 20_000L);
		SessionSnapshot heldClosed = engine.snapshot(closedStale, 0L, Collections.emptyMap(), 0L, 20_000L);
		assertEquals("closing on the stale reading must not lock in the phantom",
			0L, heldClosed.getLootValue());
		assertEquals(0L, heldClosed.getNetWorthDelta());

		// The next visit's first read serves the true bank value: the
		// correction must be swallowed, not booked as a fresh withdrawal.
		WealthSnapshot corrected = snap(inv + w, Collections.emptyMap(), bank - w, true, 60_000L);
		engine.setBankOpen(true, corrected, 60_000L);
		SessionSnapshot settled = engine.snapshot(corrected, 0L, Collections.emptyMap(), 0L, 60_000L);
		assertEquals(0L, settled.getLootValue());
		assertEquals(0L, settled.getNetWorthDelta());

		// Stable on later quiet ticks.
		engine.setBankOpen(false, corrected, 61_000L);
		WealthSnapshot quiet = snap(inv + w, Collections.emptyMap(), bank - w, true, 70_000L);
		engine.update(quiet, Collections.emptySet(), 70_000L);
		assertEquals(0L, engine.snapshot(quiet, 0L, Collections.emptyMap(), 0L, 70_000L).getLootValue());
	}

	@Test
	public void staleBankRereadCorrectingWithinTheVisitStaysNeutral()
	{
		// Same bounce, but the bank container corrects a few ticks later with
		// the bank still open (the self-correcting shape actually seen in the
		// trace). The correction must land on zero — neither the phantom gain
		// nor an over-corrected phantom loss.
		long inv = 22_475_227L;
		long bank = 1_019_158_757L;
		long w = 58_388_197L;
		engine.startSession(snap(inv, Collections.emptyMap(), bank, true, 0L), 0L);

		engine.setBankOpen(true, snap(inv, Collections.emptyMap(), bank, true, 1_000L), 1_000L);
		WealthSnapshot withdrawn = snap(inv + w, Collections.emptyMap(), bank - w, true, 2_000L);
		engine.update(withdrawn, Collections.emptySet(), 2_000L);
		engine.setBankOpen(false, withdrawn, 3_000L);
		engine.setBankOpen(true, withdrawn, 4_000L);

		WealthSnapshot stale = snap(inv, Collections.emptyMap(), bank, true, 4_600L);
		engine.update(stale, Collections.emptySet(), 4_600L);
		WealthSnapshot bounced = snap(inv + w, Collections.emptyMap(), bank, true, 5_200L);
		engine.update(bounced, Collections.emptySet(), 5_200L);

		// The bank reading corrects mid-visit.
		WealthSnapshot corrected = snap(inv + w, Collections.emptyMap(), bank - w, true, 5_800L);
		engine.update(corrected, Collections.emptySet(), 5_800L);
		SessionSnapshot settled = engine.snapshot(corrected, 0L, Collections.emptyMap(), 0L, 5_800L);
		assertEquals("the correcting read must land on zero, not a phantom loss",
			0L, settled.getLootValue());
		assertEquals(0L, settled.getNetWorthDelta());

		// Continuous across the close and later quiet ticks.
		engine.setBankOpen(false, corrected, 6_000L);
		WealthSnapshot quiet = snap(inv + w, Collections.emptyMap(), bank - w, true, 12_000L);
		engine.update(quiet, Collections.emptySet(), 12_000L);
		assertEquals(0L, engine.snapshot(quiet, 0L, Collections.emptyMap(), 0L, 12_000L).getLootValue());
	}

	@Test
	public void genuineRedepositAfterWithdrawalIsNotMistakenForAStaleReread()
	{
		// Guard against over-correction: withdrawing and then genuinely
		// depositing the exact amount straight back produces an observation
		// identical to a stale re-read's first half. The re-deposit must stay
		// zero-sum, and nothing may linger from the suspicion — a genuine
		// gain afterwards still counts in full.
		long inv = 22_475_227L;
		long bank = 1_019_158_757L;
		long w = 58_388_197L;
		engine.startSession(snap(inv, Collections.emptyMap(), bank, true, 0L), 0L);

		engine.setBankOpen(true, snap(inv, Collections.emptyMap(), bank, true, 1_000L), 1_000L);
		WealthSnapshot withdrawn = snap(inv + w, Collections.emptyMap(), bank - w, true, 2_000L);
		engine.update(withdrawn, Collections.emptySet(), 2_000L);
		engine.setBankOpen(false, withdrawn, 3_000L);
		engine.setBankOpen(true, withdrawn, 4_000L);

		// Changed mind: the whole withdrawal goes straight back in.
		WealthSnapshot redeposited = snap(inv, Collections.emptyMap(), bank, true, 4_600L);
		engine.update(redeposited, Collections.emptySet(), 4_600L);
		assertEquals("a genuine re-deposit must stay zero-sum",
			0L, engine.snapshot(redeposited, 0L, Collections.emptyMap(), 0L, 4_600L).getLootValue());

		engine.setBankOpen(false, redeposited, 5_000L);
		SessionSnapshot closed = engine.snapshot(redeposited, 0L, Collections.emptyMap(), 0L, 5_000L);
		assertEquals(0L, closed.getLootValue());
		assertEquals(0L, closed.getNetWorthDelta());

		// A genuine 5M loot gain much later counts in full.
		Map<Integer, ItemStack> looted = new LinkedHashMap<>();
		looted.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 500L, 10_000L));
		WealthSnapshot after = snap(inv + 5_000_000L, looted, bank, true, 30_000L);
		engine.update(after, Collections.emptySet(), 30_000L);
		SessionSnapshot result = engine.snapshot(after, 0L, Collections.emptyMap(), 0L, 30_000L);
		assertEquals(5_000_000L, result.getLootValue());
		assertEquals(5_000_000L, result.getNetWorthDelta());
	}

	@Test
	public void genuineGainWhileOpenAfterARedepositShapedObservationStillCounts()
	{
		// Guard against over-correction, live variant: with the suspicion
		// armed by a re-deposit-shaped observation, a genuine gain landing
		// while the bank is still open (e.g. a GE fill) does not match the
		// stale snap-back shape and must keep counting immediately.
		long inv = 22_475_227L;
		long bank = 1_019_158_757L;
		long w = 58_388_197L;
		engine.startSession(snap(inv, Collections.emptyMap(), bank, true, 0L), 0L);

		engine.setBankOpen(true, snap(inv, Collections.emptyMap(), bank, true, 1_000L), 1_000L);
		WealthSnapshot withdrawn = snap(inv + w, Collections.emptyMap(), bank - w, true, 2_000L);
		engine.update(withdrawn, Collections.emptySet(), 2_000L);
		engine.setBankOpen(false, withdrawn, 3_000L);
		engine.setBankOpen(true, withdrawn, 4_000L);

		WealthSnapshot redeposited = snap(inv, Collections.emptyMap(), bank, true, 4_600L);
		engine.update(redeposited, Collections.emptySet(), 4_600L);

		// A genuine 5M gain while the bank is still open: tracked up 5M,
		// bank untouched. Real profit, counted live.
		WealthSnapshot midGain = snap(inv + 5_000_000L, Collections.emptyMap(), bank, true, 5_200L);
		engine.update(midGain, Collections.emptySet(), 5_200L);
		SessionSnapshot live = engine.snapshot(midGain, 0L, Collections.emptyMap(), 0L, 5_200L);
		assertEquals("a genuine gain must not be swallowed by the stale-read suspicion",
			0L, live.getLootValue());
		assertEquals(5_000_000L, live.getNetWorthDelta());

		// ...and it survives the close unchanged.
		engine.setBankOpen(false, midGain, 6_000L);
		SessionSnapshot closed = engine.snapshot(midGain, 0L, Collections.emptyMap(), 0L, 6_000L);
		assertEquals(0L, closed.getLootValue());
		assertEquals(5_000_000L, closed.getNetWorthDelta());
	}

	@Test
	public void staleBankRereadSnapForwardWhileBankClosedDoesNotBookPhantomProfit()
	{
		// The previously un-intercepted edge of the stale re-read fix: the
		// inventory's forward snap-back arrives AFTER the bank has fully
		// closed, so it flows through the closed-bank loot diff rather than
		// trackOpenTrackedSwing (which carried the confirm logic). The confirm
		// must fire on the closed path too — otherwise the withdrawal books as
		// +w phantom profit that sticks until the next visit corrects the bank
		// reading.
		long inv = 22_475_227L;
		long bank = 1_019_158_757L;
		long w = 58_388_197L;
		engine.startSession(snap(inv, Collections.emptyMap(), bank, true, 0L), 0L);

		// Withdraw w, both halves observed while open — zero-sum.
		engine.setBankOpen(true, snap(inv, Collections.emptyMap(), bank, true, 1_000L), 1_000L);
		WealthSnapshot withdrawn = snap(inv + w, Collections.emptyMap(), bank - w, true, 2_000L);
		engine.update(withdrawn, Collections.emptySet(), 2_000L);
		assertEquals(0L, engine.snapshot(withdrawn, 0L, Collections.emptyMap(), 0L, 2_000L).getLootValue());

		// Rapid close -> reopen, then the stale re-read arms the suspicion.
		engine.setBankOpen(false, withdrawn, 3_000L);
		engine.setBankOpen(true, withdrawn, 4_000L);
		WealthSnapshot stale = snap(inv, Collections.emptyMap(), bank, true, 4_600L);
		engine.update(stale, Collections.emptySet(), 4_600L);
		assertEquals(0L, engine.snapshot(stale, 0L, Collections.emptyMap(), 0L, 4_600L).getLootValue());

		// The bank CLOSES before the inventory snaps forward.
		engine.setBankOpen(false, stale, 5_000L);

		// The inventory snaps forward to the true withdrawn state while the
		// bank reading stays stale-high — now with the bank CLOSED, so it runs
		// the closed-path loot diff. This must NOT book +w as profit.
		WealthSnapshot bounced = snap(inv + w, Collections.emptyMap(), bank, true, 5_600L);
		engine.update(bounced, Collections.emptySet(), 5_600L);
		SessionSnapshot spike = engine.snapshot(bounced, 0L, Collections.emptyMap(), 0L, 5_600L);
		assertEquals("a stale snap-forward while the bank is closed must not book the withdrawal as profit",
			0L, spike.getLootValue());
		assertEquals(0L, spike.getNetWorthDelta());

		// Held stable while the bank stays closed on the stale reading.
		WealthSnapshot closedStale = snap(inv + w, Collections.emptyMap(), bank, true, 20_000L);
		engine.update(closedStale, Collections.emptySet(), 20_000L);
		SessionSnapshot held = engine.snapshot(closedStale, 0L, Collections.emptyMap(), 0L, 20_000L);
		assertEquals("closing on the stale reading after a closed snap-forward must not lock in the phantom",
			0L, held.getLootValue());
		assertEquals(0L, held.getNetWorthDelta());

		// The next visit's first read serves the true bank value: the
		// correction must be swallowed, not booked as a fresh withdrawal.
		WealthSnapshot corrected = snap(inv + w, Collections.emptyMap(), bank - w, true, 60_000L);
		engine.setBankOpen(true, corrected, 60_000L);
		SessionSnapshot settled = engine.snapshot(corrected, 0L, Collections.emptyMap(), 0L, 60_000L);
		assertEquals(0L, settled.getLootValue());
		assertEquals(0L, settled.getNetWorthDelta());
	}

	@Test
	public void unmatchedTrackedDropWhileBankOpenBooksAsLossAfterSettleWindow()
	{
		// Guard: the in-flight hold is bounded. A tracked-wealth drop while
		// the bank is open whose bank-side counterpart NEVER arrives (e.g. an
		// item genuinely lost/dropped mid-visit) is held as in-flight only for
		// the settle window, then books as the real loss it is.
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);
		WealthSnapshot dropped = snap(90_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.update(dropped, Collections.emptySet(), 200L);

		// While the drop could still be a lagging transfer, profit holds.
		assertEquals(0L, engine.snapshot(dropped, 0L, Collections.emptyMap(), 0L, 200L).getLootValue());

		// Far past any plausible container lag, the bank never moved: loss.
		WealthSnapshot stale = snap(90_000_000L, Collections.emptyMap(), 50_000_000L, true, 12_000L);
		engine.update(stale, Collections.emptySet(), 12_000L);
		SessionSnapshot result = engine.snapshot(stale, 0L, Collections.emptyMap(), 0L, 12_000L);
		assertEquals(0L, result.getLootValue());
		assertEquals(-10_000_000L, result.getNetWorthDelta());
	}

	@Test
	public void restartWhileBankOpenKeepsPostResetTransfersZeroSum()
	{
		// Session running, bank open, mid-visit (mirrors the "reset while the
		// bank interface is open" edge: SessionTracker.resetSession() restarts
		// the engine and then must re-apply bank-open mode via setBankOpen,
		// exactly like this test does, rather than leaving the fresh engine
		// thinking the bank is closed).
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 0L);
		engine.startSession(initial, 0L);
		engine.setBankOpen(true, snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 100L), 100L);

		// Reset fired while the bank is still open: restart the engine...
		WealthSnapshot atReset = snap(100_000_000L, Collections.emptyMap(), 50_000_000L, true, 200L);
		engine.startSession(atReset, 200L);
		// ...the engine alone would now report bank-closed:
		assertTrue("startSession must not leave bank-open mode active on its own",
			!engine.isBankOpen());
		// ...so the reset path must re-apply bank-open mode, anchored to the
		// same snapshot the engine was just restarted with.
		engine.setBankOpen(true, atReset, 200L);
		assertTrue(engine.isBankOpen());

		// A withdrawal made AFTER the reset but before the next open/close
		// event: tracked rises by 10M, bank drops by 10M. Must stay zero-sum
		// immediately (live, bank still open)...
		WealthSnapshot midWithdraw = snap(110_000_000L, Collections.emptyMap(), 40_000_000L, true, 300L);
		engine.update(midWithdraw, Collections.emptySet(), 300L);
		SessionSnapshot live = engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals("post-reset transfer must not read as profit while the bank is open",
			0L, live.getLootValue());
		assertEquals(0L, live.getProfitPerHour());
		assertEquals(0L, live.getNetWorthDelta());

		// ...and stay zero-sum once the bank actually closes.
		engine.setBankOpen(false, midWithdraw, 400L);
		SessionSnapshot closed = engine.snapshot(midWithdraw, 0L, Collections.emptyMap(), 0L, 400L);
		assertEquals("post-reset transfer must still be neutral after the real close",
			0L, closed.getLootValue());
	}

	@Test
	public void geBuyIsExcludedFromLootFeed()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 1000L, 200L));
		WealthSnapshot current = snap(10_200_000L, tracked, 0L, false, 1000L);

		Set<Integer> geAttributed = new HashSet<>();
		geAttributed.add(RUNE_ITEM);

		engine.update(current, geAttributed, 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);
		assertTrue("GE-attributed item gain must not appear as loot", result.getLoot().isEmpty());
		// A GE buy is not loot — it is cost basis for a future flip, not profit.
		assertEquals("a GE buy is not loot", 0L, result.getLootValue());
	}

	@Test
	public void profitPerHourMath()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot current = snap(3_600_000L, items(new ItemStack(DRAGON_BONES, "Dragon bones", 3_600L, 1_000L)), 0L, false, 1_800_000L);
		engine.update(current, Collections.emptySet(), 1_800_000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1_800_000L);

		assertEquals(3_600_000L, result.getLootValue());
		assertEquals(1_800_000L, result.getElapsedMs());
		assertEquals(7_200_000L, result.getProfitPerHour());
	}

	/**
	 * Net profit is the true bottom line: gross realised profit minus the
	 * consumable spend burned to earn it. Looting 1,000,000 while eating an
	 * 800gp shark nets 999,200 — the supply cost is subtracted.
	 */
	@Test
	public void netProfitIsGrossProfitMinusSuppliesUsed()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(SHARK, new ItemStack(SHARK, "Shark", 5L, 800L));
		WealthSnapshot initial = snap(4_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Looted 100 dragon bones @10k (1,000,000 profit) and ate one shark
		// (800gp supply) in the same interval, away from the bank.
		Map<Integer, ItemStack> after = new LinkedHashMap<>();
		after.put(SHARK, new ItemStack(SHARK, "Shark", 4L, 800L));
		after.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot current = snap(1_003_200L, after, 0L, false, 1_800_000L);
		engine.update(current, Collections.emptySet(), 1_800_000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1_800_000L);

		assertEquals("gross profit is loot only", 1_000_000L, result.getLootValue());
		assertEquals("shark eaten is supplies used", 800L, result.getSuppliesUsed());
		assertEquals("net profit subtracts supplies from gross profit",
			999_200L, result.getNetProfit());
	}

	/**
	 * Profit/hr must extrapolate NET profit, not gross: supplies burned to
	 * earn the gains are a real session cost. 999,200 net over half an hour
	 * is 1,998,400/hr — not the 2,000,000/hr the gross figure would imply.
	 */
	@Test
	public void profitPerHourIsNetOfSuppliesUsed()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(SHARK, new ItemStack(SHARK, "Shark", 5L, 800L));
		WealthSnapshot initial = snap(4_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> after = new LinkedHashMap<>();
		after.put(SHARK, new ItemStack(SHARK, "Shark", 4L, 800L));
		after.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot current = snap(1_003_200L, after, 0L, false, 1_800_000L);
		engine.update(current, Collections.emptySet(), 1_800_000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1_800_000L);

		assertEquals(1_000_000L, result.getLootValue());
		assertEquals(800L, result.getSuppliesUsed());
		assertEquals("profit/hr extrapolates net profit (999,200) over 0.5h",
			1_998_400L, result.getProfitPerHour());
	}

	@Test
	public void bankRevaluationWhileClosedDoesNotMoveNetWorthDeltaOrProfit()
	{
		// The bank is valued at live prices, so a GE price reload can move a
		// closed bank by millions with no transfer at all (observed in the
		// wild: +4.09M on a ~1.1B bank in a single update, every wealth
		// component repricing at once with zero quantity changes). That paper
		// drift on cold storage is not session activity: it must not read as
		// a net-worth gain, and the accounting identity
		// netWorthDelta == profit - suppliesUsed + unrealizedPnl must survive
		// the reload (profit and unrealized both exclude the bank by design,
		// so counting the reload in netWorthDelta breaks the books by the
		// full drift amount).
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 1_000_000_000L, true, 0L);
		engine.startSession(initial, 0L);

		// Ten minutes in — no bank visit anywhere near — prices reload and
		// the bank revalues +4M. Tracked wealth unchanged.
		WealthSnapshot repriced = snap(10_000_000L, Collections.emptyMap(), 1_004_000_000L, true, 600_000L);
		engine.update(repriced, Collections.emptySet(), 600_000L);

		SessionSnapshot result = engine.snapshot(repriced, 0L, Collections.emptyMap(), 0L, 600_000L);
		assertEquals(0L, result.getLootValue());
		assertEquals("closed-bank revaluation is not a session gain",
			0L, result.getNetWorthDelta());
		assertEquals("identity must hold across a bank repricing",
			result.getLootValue() - result.getSuppliesUsed() + result.getUnrealizedPnl(),
			result.getNetWorthDelta());
		assertTrue(result.isBankKnown());
	}

	@Test
	public void firstBankOpenDoesNotInflateNetWorthDelta()
	{
		// Session starts before the bank is opened: bank value is unknown.
		WealthSnapshot initial = snap(100_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Bank opened for the first time; it holds 1.6B. Net worth only "jumps"
		// because the bank was previously invisible — it must NOT be reported as
		// a session gain.
		WealthSnapshot atOpen = snap(100_000_000L, Collections.emptyMap(), 1_600_000_000L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		SessionSnapshot result = engine.snapshot(atOpen, 0L, Collections.emptyMap(), 0L, 100L);
		assertEquals("first bank sighting must not register as a net-worth gain",
			0L, result.getNetWorthDelta());
		assertTrue(result.isBankKnown());

		// A genuine gain AFTER the bank is known is still reflected.
		WealthSnapshot later = snap(150_000_000L, Collections.emptyMap(), 1_600_000_000L, true, 200L);
		engine.update(later, Collections.emptySet(), 200L);
		SessionSnapshot after = engine.snapshot(later, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals(50_000_000L, after.getNetWorthDelta());
	}

	@Test
	public void netWorthAboveIntMaxIsNotTruncated()
	{
		// 3B exceeds Integer.MAX_VALUE (~2.147B): the whole pipeline must stay
		// in long arithmetic and never wrap negative.
		long huge = 3_000_000_000L;
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot current = snap(huge, items(new ItemStack(DRAGON_BONES, "Dragon bones", 3_000_000L, 1_000L)), 0L, false, 1000L);
		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(huge, result.getLootValue());
		assertEquals(huge, result.getNetWorthDelta());
	}

	@Test
	public void profitPerHourIsZeroWhenElapsedIsZero()
	{
		WealthSnapshot initial = snap(1_000_000L, Collections.emptyMap(), 0L, false, 500L);
		engine.startSession(initial, 500L);

		SessionSnapshot result = engine.snapshot(initial, 0L, Collections.emptyMap(), 0L, 500L);
		assertEquals(0L, result.getProfitPerHour());
	}

	@Test
	public void lootIsAggregatedPerItemAcrossPickups()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Same item (bird nest) looted many times, one per update: must collapse
		// into a single summarised row with summed quantity and value, not one
		// row per pickup.
		long qtySoFar = 0;
		WealthSnapshot current = initial;
		for (int i = 1; i <= 300; i++)
		{
			qtySoFar = i;
			Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
			tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Bird nest", qtySoFar, 5_000L));
			current = snap(qtySoFar * 5_000L, tracked, 0L, false, i);
			engine.update(current, Collections.emptySet(), i);
		}

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 300L);
		assertEquals(1, result.getLoot().size());
		LootEntry nest = result.getLoot().get(0);
		assertEquals(300L, nest.getQuantity());
		assertEquals(300L * 5_000L, nest.getValue());
	}

	@Test
	public void lootSummaryIsOrderedByValueDescending()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(1, new ItemStack(1, "Cheap", 1L, 10_000L));       // 10k
		tracked.put(2, new ItemStack(2, "Pricey", 1L, 5_000_000L));   // 5m
		tracked.put(3, new ItemStack(3, "Mid", 1L, 500_000L));        // 500k
		WealthSnapshot current = snap(5_510_000L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(3, result.getLoot().size());
		assertEquals("Pricey", result.getLoot().get(0).getName());
		assertEquals("Mid", result.getLoot().get(1).getName());
		assertEquals("Cheap", result.getLoot().get(2).getName());
	}

	@Test
	public void multipleNewItemsInSameUpdateAllRecordedAsLoot()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(1, new ItemStack(1, "Item A", 1L, 500_000L));
		tracked.put(2, new ItemStack(2, "Item B", 2L, 250_000L));
		WealthSnapshot current = snap(1_000_000L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(2, result.getLoot().size());
	}

	// ---------------------------------------------------- realised vs unrealized

	@Test
	public void purePriceDriftMovesUnrealizedNotProfit()
	{
		// Held 100 dragon bones @10k when the session started...
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot initial = snap(1_000_000L, held, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// ...and the live price drifts to 13k with NO trade: quantity unchanged.
		Map<Integer, ItemStack> drifted = new LinkedHashMap<>();
		drifted.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 13_000L));
		WealthSnapshot current = snap(1_300_000L, drifted, 0L, false, 1000L);
		engine.update(current, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals("price drift alone must not move realised profit", 0L, result.getLootValue());
		assertEquals(0L, result.getProfitPerHour());
		assertEquals("price drift belongs in unrealized P/L", 300_000L, result.getUnrealizedPnl());

		assertEquals(1, result.getHoldingPnls().size());
		HoldingPnl row = result.getHoldingPnls().get(0);
		assertEquals(DRAGON_BONES, row.getItemId());
		assertEquals(100L, row.getQuantity());
		assertEquals(1_000_000L, row.getCostBasis());
		assertEquals(1_300_000L, row.getCurrentValue());
		assertEquals(300_000L, row.unrealized());
	}

	@Test
	public void lootRaisesProfitNotUnrealized()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Picked up 100 dragon bones @10k: a genuine quantity gain.
		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot current = snap(1_000_000L, tracked, 0L, false, 1000L);
		engine.update(current, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals("loot is realised profit", 1_000_000L, result.getLootValue());
		assertEquals("loot valued at pickup price has no paper gain yet",
			0L, result.getUnrealizedPnl());
		// Its cost basis is the pickup price, so the breakdown row is flat.
		assertEquals(1, result.getHoldingPnls().size());
		assertEquals(1_000_000L, result.getHoldingPnls().get(0).getCostBasis());
		assertEquals(1_000_000L, result.getHoldingPnls().get(0).getCurrentValue());
	}

	@Test
	public void geSaleAtProfitRealisesTheGain()
	{
		// Start holding 100 units @1k (basis 100k)...
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 100L, 1_000L));
		WealthSnapshot initial = snap(100_000L, held, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// ...price drifts to 1.5k: 50k paper gain, no realised profit yet.
		Map<Integer, ItemStack> drifted = new LinkedHashMap<>();
		drifted.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 100L, 1_500L));
		WealthSnapshot afterDrift = snap(150_000L, drifted, 0L, false, 1000L);
		engine.update(afterDrift, Collections.emptySet(), 1000L);

		SessionSnapshot paper = engine.snapshot(afterDrift, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals(0L, paper.getLootValue());
		assertEquals(50_000L, paper.getUnrealizedPnl());

		// Sold the lot at 1.5k: coins in, item out. The paper gain is realised.
		// Nature runes match SupplyClassifier's rune pattern, so — matching
		// how GeReconciler really behaves for a GE sale — this interval's
		// update() call must mark the item GE-attributed; otherwise the item
		// vanishing entirely from tracked items would be indistinguishable
		// from the whole stack being used up (see the supplies-used tests).
		WealthSnapshot afterSale = snap(150_000L, Collections.emptyMap(), 0L, false, 2000L);
		engine.update(afterSale, Collections.singleton(RUNE_ITEM), 2000L);

		SessionSnapshot result = engine.snapshot(afterSale, 0L, Collections.emptyMap(), 0L, 2000L);
		assertEquals("selling a pre-owned holding is not loot", 0L, result.getLootValue());
		assertEquals("the realised gain lands on net worth", 50_000L, result.getNetWorthDelta());
		assertEquals(0L, result.getUnrealizedPnl());
		assertTrue(result.getHoldingPnls().isEmpty());
	}

	@Test
	public void costBasisAveragesAcrossBuysAndPartialSells()
	{
		// 10k coins, no holdings. (Coins are implicit in inventoryValue.)
		WealthSnapshot initial = snap(10_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);
		Set<Integer> geAttributed = Collections.singleton(RUNE_ITEM);

		// Buy 10 @100: coins 9k + items 1k. Basis 1,000. Nothing realised.
		Map<Integer, ItemStack> afterBuy1Items = new LinkedHashMap<>();
		afterBuy1Items.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 10L, 100L));
		WealthSnapshot afterBuy1 = snap(10_000L, afterBuy1Items, 0L, false, 1000L);
		engine.update(afterBuy1, geAttributed, 1000L);
		SessionSnapshot s1 = engine.snapshot(afterBuy1, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals(0L, s1.getLootValue());
		assertEquals(0L, s1.getUnrealizedPnl());

		// Price doubles to 200 and buy 10 more @200: coins 7k + items 4k.
		// Basis 1,000 + 2,000 = 3,000 (avg 150). The first 10 units' price
		// rise is a paper gain only.
		Map<Integer, ItemStack> afterBuy2Items = new LinkedHashMap<>();
		afterBuy2Items.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 20L, 200L));
		WealthSnapshot afterBuy2 = snap(11_000L, afterBuy2Items, 0L, false, 2000L);
		engine.update(afterBuy2, geAttributed, 2000L);
		SessionSnapshot s2 = engine.snapshot(afterBuy2, 0L, Collections.emptyMap(), 0L, 2000L);
		assertEquals(0L, s2.getLootValue());
		assertEquals(1_000L, s2.getUnrealizedPnl());

		// Sell 10 @200: coins 9k + items 2k. Sold units carried avg basis 150,
		// so 10 * (200 - 150) = 500 is realised; the kept 10 retain basis 1,500.
		Map<Integer, ItemStack> afterSellItems = new LinkedHashMap<>();
		afterSellItems.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 10L, 200L));
		WealthSnapshot afterSell = snap(11_000L, afterSellItems, 0L, false, 3000L);
		engine.update(afterSell, geAttributed, 3000L);
		SessionSnapshot s3 = engine.snapshot(afterSell, 0L, Collections.emptyMap(), 0L, 3000L);
		assertEquals("a GE flip is not loot", 0L, s3.getLootValue());
		assertEquals(500L, s3.getUnrealizedPnl());
		assertEquals(1, s3.getHoldingPnls().size());
		assertEquals(1_500L, s3.getHoldingPnls().get(0).getCostBasis());
		assertEquals(2_000L, s3.getHoldingPnls().get(0).getCurrentValue());
	}

	@Test
	public void holdingBreakdownSumsToUnrealizedLine()
	{
		// Two holdings: A 10 @1k (10k) and B 5 @2k (10k).
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(1, new ItemStack(1, "Item A", 10L, 1_000L));
		held.put(2, new ItemStack(2, "Item B", 5L, 2_000L));
		WealthSnapshot initial = snap(20_000L, held, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// A drifts up to 1.3k (+3k paper), B down to 1.6k (-2k paper).
		Map<Integer, ItemStack> drifted = new LinkedHashMap<>();
		drifted.put(1, new ItemStack(1, "Item A", 10L, 1_300L));
		drifted.put(2, new ItemStack(2, "Item B", 5L, 1_600L));
		WealthSnapshot current = snap(21_000L, drifted, 0L, false, 1000L);
		engine.update(current, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals(0L, result.getLootValue());
		assertEquals(1_000L, result.getUnrealizedPnl());

		assertEquals(2, result.getHoldingPnls().size());
		long sum = 0L;
		for (HoldingPnl row : result.getHoldingPnls())
		{
			sum += row.unrealized();
		}
		assertEquals("breakdown rows must sum to the unrealized line",
			result.getUnrealizedPnl(), sum);
		// Ordered by absolute unrealized amount descending: A (+3k) before B (-2k).
		assertEquals("Item A", result.getHoldingPnls().get(0).getName());
		assertEquals(3_000L, result.getHoldingPnls().get(0).unrealized());
		assertEquals(-2_000L, result.getHoldingPnls().get(1).unrealized());
	}

	@Test
	public void bankDepositOfAppreciatedHoldingIsLootNeutral()
	{
		// Banking is loot-neutral: depositing an appreciated holding does NOT
		// fabricate loot. The paper gain shows on net worth (already there) and
		// the unrealized line drops to 0 as the basis leaves tracked wealth.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 100L, 1_000L));
		WealthSnapshot initial = snap(100_000L, held, 0L, true, 0L);
		engine.startSession(initial, 0L);

		// Price drifts to 1.5k: 50k paper gain.
		Map<Integer, ItemStack> drifted = new LinkedHashMap<>();
		drifted.put(RUNE_ITEM, new ItemStack(RUNE_ITEM, "Nature rune", 100L, 1_500L));
		WealthSnapshot afterDrift = snap(150_000L, drifted, 0L, true, 1000L);
		engine.update(afterDrift, Collections.emptySet(), 1000L);
		assertEquals(50_000L,
			engine.snapshot(afterDrift, 0L, Collections.emptyMap(), 0L, 1000L).getUnrealizedPnl());

		// Deposit the whole stack: tracked -> 0, bank +150k, bank still open.
		engine.setBankOpen(true, afterDrift, 1100L);
		WealthSnapshot afterDeposit = snap(0L, Collections.emptyMap(), 150_000L, true, 1200L);
		engine.update(afterDeposit, Collections.emptySet(), 1200L);

		SessionSnapshot live = engine.snapshot(afterDeposit, 0L, Collections.emptyMap(), 0L, 1200L);
		assertEquals(0L, live.getLootValue());
		assertEquals(0L, live.getUnrealizedPnl());

		// The figure survives the bank close unchanged.
		engine.setBankOpen(false, afterDeposit, 1300L);
		SessionSnapshot closed = engine.snapshot(afterDeposit, 0L, Collections.emptyMap(), 0L, 1300L);
		assertEquals(0L, closed.getLootValue());
		assertEquals(0L, closed.getUnrealizedPnl());
		assertEquals("the appreciation lives on net worth, not loot", 50_000L, closed.getNetWorthDelta());
	}

	@Test
	public void zeroValueItemsAreNotLoot()
	{
		WealthSnapshot initial = snap(0L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(1, new ItemStack(1, "Junk clue scroll", 1L, 0L));
		WealthSnapshot current = snap(0L, tracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertTrue(result.getLoot().isEmpty());
	}

	// ------------------------------------------------------------ supplies used

	private static final int PRAYER_POTION = 2434;
	private static final int ADAMANT_ARROW = 890;
	private static final int SHARK = 385;
	private static final int COOKED_KARAMBWAN = 3144;
	private static final int TELEPORT_TO_HOUSE = 8013;

	@Test
	public void consumingPotionDosesIncreasesSuppliesUsed()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(PRAYER_POTION, new ItemStack(PRAYER_POTION, "Prayer potion(4)", 5L, 1_000L));
		WealthSnapshot initial = snap(5_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Drank down from 5 doses to 2 doses (3 doses consumed) while away
		// from the bank.
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		afterTracked.put(PRAYER_POTION, new ItemStack(PRAYER_POTION, "Prayer potion(4)", 2L, 1_000L));
		WealthSnapshot current = snap(2_000L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(3_000L, result.getSuppliesUsed());
		assertEquals(3_000L, engine.getSuppliesUsed());
		// Regression: drinking a potion dose must not itself register as a
		// profit loss — the spend belongs in suppliesUsed only.
		assertEquals("drinking a potion dose must not reduce profit",
			0L, result.getLootValue());
	}

	@Test
	public void eatingASharkCountsAsSuppliesUsedNotProfitLoss()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(SHARK, new ItemStack(SHARK, "Shark", 5L, 800L));
		WealthSnapshot initial = snap(4_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Ate one shark from the inventory (5 -> 4) while away from the bank.
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		afterTracked.put(SHARK, new ItemStack(SHARK, "Shark", 4L, 800L));
		WealthSnapshot current = snap(3_200L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals("eating a shark must count as supplies used", 800L, result.getSuppliesUsed());
		assertEquals("eating a shark must not register as a profit loss", 0L, result.getLootValue());
	}

	@Test
	public void eatingACookedKarambwanCountsAsSuppliesUsedNotProfitLoss()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(COOKED_KARAMBWAN, new ItemStack(COOKED_KARAMBWAN, "Cooked karambwan", 10L, 400L));
		WealthSnapshot initial = snap(4_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Ate one cooked karambwan (10 -> 9) while away from the bank.
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		afterTracked.put(COOKED_KARAMBWAN, new ItemStack(COOKED_KARAMBWAN, "Cooked karambwan", 9L, 400L));
		WealthSnapshot current = snap(3_600L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals("eating a karambwan must count as supplies used", 400L, result.getSuppliesUsed());
		assertEquals("eating a karambwan must not register as a profit loss", 0L, result.getLootValue());
	}

	@Test
	public void drinkingASinglePrayerPotionDoseCountsAsSuppliesUsedNotProfitLoss()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(PRAYER_POTION, new ItemStack(PRAYER_POTION, "Prayer potion(4)", 1L, 1_000L));
		WealthSnapshot initial = snap(1_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Drank the final dose: the stack disappears from the inventory
		// entirely (quantity drops to zero / item removed from tracked
		// items), mirroring a 4->empty potion transition in-client.
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		WealthSnapshot current = snap(0L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals("drinking the last dose must count as supplies used", 1_000L, result.getSuppliesUsed());
		assertEquals("drinking the last dose must not register as a profit loss", 0L, result.getLootValue());
	}

	@Test
	public void usingAHouseTeleportTabletCountsAsSuppliesUsedNotProfitLoss()
	{
		// Regression: an in-client QA report showed using a House Teleport
		// tablet (~747gp) dinged Profit by the full value while "Supplies
		// used" stayed at 0 — the tablet fell through SupplyClassifier
		// unclassified and was treated as ordinary lost wealth instead of a
		// supply. Teleport tablets must get the same "fold into baseline,
		// don't touch profit" treatment as potions/food/ammo/runes.
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(TELEPORT_TO_HOUSE,
			new ItemStack(TELEPORT_TO_HOUSE, "Teleport to house", 1L, 747L));
		WealthSnapshot initial = snap(747L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Used the tablet: it is consumed entirely, vanishing from the
		// tracked items (mirrors the last-dose-of-a-potion transition).
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		WealthSnapshot current = snap(0L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals("using a House Teleport tablet must count as supplies used",
			747L, result.getSuppliesUsed());
		assertEquals("using a House Teleport tablet must not register as a profit loss",
			0L, result.getLootValue());
	}

	@Test
	public void firingArrowsIncreasesSuppliesUsedByTheirValue()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(ADAMANT_ARROW, new ItemStack(ADAMANT_ARROW, "Adamant arrow", 1000L, 50L));
		WealthSnapshot initial = snap(50_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Fired 200 arrows (quiver dropped from 1000 to 800) while away from
		// the bank.
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		afterTracked.put(ADAMANT_ARROW, new ItemStack(ADAMANT_ARROW, "Adamant arrow", 800L, 50L));
		WealthSnapshot current = snap(40_000L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(200L * 50L, result.getSuppliesUsed());
	}

	@Test
	public void depositingPotionsInTheBankDoesNotCountAsSuppliesUsed()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(PRAYER_POTION, new ItemStack(PRAYER_POTION, "Prayer potion(4)", 5L, 1_000L));
		WealthSnapshot initial = snap(5_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Open the bank, then deposit all 5 potions: the tracked-item quantity
		// drops to zero, but this is a transfer, not consumption.
		WealthSnapshot atOpen = snap(5_000L, initialTracked, 0L, true, 100L);
		engine.setBankOpen(true, atOpen, 100L);

		Map<Integer, ItemStack> afterDepositTracked = new LinkedHashMap<>();
		WealthSnapshot afterDeposit = snap(0L, afterDepositTracked, 5_000L, true, 200L);
		// While the bank is open, update() is a no-op besides bookkeeping —
		// mirrors bankDepositIsNeutralised above.
		engine.update(afterDeposit, Collections.emptySet(), 200L);
		engine.setBankOpen(false, afterDeposit, 200L);

		SessionSnapshot result = engine.snapshot(afterDeposit, 0L, Collections.emptyMap(), 0L, 200L);
		assertEquals("depositing potions must not count as supplies used",
			0L, result.getSuppliesUsed());
		assertEquals(0L, result.getLootValue());
	}

	@Test
	public void geSaleOfConsumableDoesNotCountAsSuppliesUsed()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(PRAYER_POTION, new ItemStack(PRAYER_POTION, "Prayer potion(4)", 5L, 1_000L));
		WealthSnapshot initial = snap(5_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Sold 5 potions on the GE while away from the bank: tracked
		// quantity drops to zero, but the item id is GE-attributed this
		// interval, so it must be excluded from supplies-used (same
		// exclusion the loot feed already relies on).
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		WealthSnapshot afterSale = snap(0L, afterTracked, 0L, false, 1000L);

		Set<Integer> geAttributed = new HashSet<>();
		geAttributed.add(PRAYER_POTION);
		engine.update(afterSale, geAttributed, 1000L);

		SessionSnapshot result = engine.snapshot(afterSale, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals("a GE-attributed decrease must not count as supplies used",
			0L, result.getSuppliesUsed());
	}

	@Test
	public void nonConsumableItemDecreaseDoesNotCountAsSuppliesUsed()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot initial = snap(1_000_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Dragon bones aren't a recognised consumable pattern (bones aren't
		// potions/food/ammo/runes) — a decrease (e.g. burying/using them)
		// still reduces profit but must not be double-surfaced as supplies.
		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		afterTracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 50L, 10_000L));
		WealthSnapshot current = snap(500_000L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals(0L, result.getSuppliesUsed());
		assertEquals(0L, result.getLootValue());
		assertEquals("the loss lands on net worth, not loot/profit", -500_000L, result.getNetWorthDelta());
	}

	// ------------------------------------------------- equip / unequip zero-sum

	private static final int WHIP = 4151;
	private static final int WORN_WHIP = 4152;
	private static final int BLACK_CHIN = 11959;

	private static WealthSnapshot snapInvEquip(long inventoryValue, long equipmentValue,
		Map<Integer, ItemStack> trackedItems, long ts)
	{
		return WealthSnapshot.builder()
			.inventoryValue(inventoryValue)
			.equipmentValue(equipmentValue)
			.geInFlightValue(0L)
			.pouchValue(0L)
			.bankValue(0L)
			.bankKnown(false)
			.timestampMs(ts)
			.trackedItems(trackedItems)
			.build();
	}

	@Test
	public void equippingGearSameIdIsZeroProfit()
	{
		// An 11M item moves from the inventory component to the equipment
		// component of tracked wealth. Same item id, same total — a pure
		// internal transfer: zero profit, zero unrealized, no loot, no supplies.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(WHIP, new ItemStack(WHIP, "Abyssal whip", 1L, 11_000_000L));
		WealthSnapshot initial = snapInvEquip(11_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		WealthSnapshot equipped = snapInvEquip(0L, 11_000_000L, held, 1000L);
		engine.update(equipped, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(equipped, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals("pure equip must be zero profit", 0L, result.getLootValue());
		assertEquals(0L, result.getUnrealizedPnl());
		assertTrue("pure equip must not appear as loot", result.getLoot().isEmpty());
		assertEquals(0L, result.getSuppliesUsed());
	}

	@Test
	public void equipWithDifferentWornIdIsZeroProfitAndNotLoot()
	{
		// The worn form of the item has a DIFFERENT id than the inventory form
		// (mapped to the same GE value): the delta map sees id A disappear and
		// id B appear in the same interval. Still a pure transfer.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(WHIP, new ItemStack(WHIP, "Abyssal whip", 1L, 11_000_000L));
		WealthSnapshot initial = snapInvEquip(11_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> worn = new LinkedHashMap<>();
		worn.put(WORN_WHIP, new ItemStack(WORN_WHIP, "Abyssal whip", 1L, 11_000_000L));
		WealthSnapshot equipped = snapInvEquip(0L, 11_000_000L, worn, 1000L);
		engine.update(equipped, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(equipped, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals("id-swap equip must be zero profit", 0L, result.getLootValue());
		assertEquals(0L, result.getUnrealizedPnl());
		assertTrue("id-swap equip must not appear as loot", result.getLoot().isEmpty());
		assertEquals(0L, result.getSuppliesUsed());
	}

	@Test
	public void equipConsumableStackWithDifferentWornIdIsZeroProfit()
	{
		// The reported phantom-11M bug, single-interval variant: an 11M stack
		// of ammunition (name matches SupplyClassifier) whose worn id differs
		// from the inventory id. Without a fix the vanish is charged as 11M
		// supplies (baseline shifted down 11M) and the appearance is recorded
		// as 11M loot — profit jumps by the full stack value.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 2750L, 4_000L));
		WealthSnapshot initial = snapInvEquip(11_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> worn = new LinkedHashMap<>();
		worn.put(BLACK_CHIN + 1, new ItemStack(BLACK_CHIN + 1, "Black chinchompa", 2750L, 4_000L));
		WealthSnapshot equipped = snapInvEquip(0L, 11_000_000L, worn, 1000L);
		engine.update(equipped, Collections.emptySet(), 1000L);

		SessionSnapshot result = engine.snapshot(equipped, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals("equipping ammo must not create phantom profit", 0L, result.getLootValue());
		assertEquals(0L, result.getUnrealizedPnl());
		assertTrue(result.getLoot().isEmpty());
		assertEquals("equipping ammo is not consumption", 0L, result.getSuppliesUsed());
	}

	@Test
	public void equipTransientVanishThenReappearIsZeroProfit()
	{
		// The reported phantom-11M bug, two-event variant: RuneLite fires one
		// ItemContainerChanged per container, so mid-equip the stack can be in
		// NEITHER container for one refresh (inventory already updated,
		// equipment not yet). The engine sees vanish (charged as 11M supplies,
		// baseline shifted) then reappear (recorded as 11M loot) — profit ends
		// +11M permanently even though wealth never moved.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 2750L, 4_000L));
		WealthSnapshot initial = snapInvEquip(11_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		// Refresh 1: stack in neither container.
		WealthSnapshot gone = snapInvEquip(0L, 0L, Collections.emptyMap(), 1000L);
		engine.update(gone, Collections.emptySet(), 1000L);

		// Refresh 2 (same client tick, ms later): stack now worn.
		WealthSnapshot equipped = snapInvEquip(0L, 11_000_000L, held, 1050L);
		engine.update(equipped, Collections.emptySet(), 1050L);

		SessionSnapshot result = engine.snapshot(equipped, 0L, Collections.emptyMap(), 0L, 1050L);
		assertEquals("equip transient must not create phantom profit", 0L, result.getLootValue());
		assertEquals(0L, result.getUnrealizedPnl());
		assertTrue("equip transient must not appear as loot", result.getLoot().isEmpty());
		assertEquals("equip transient must not count as supplies used",
			0L, result.getSuppliesUsed());
	}

	@Test
	public void equipTransientDuplicateThenCorrectIsZeroProfit()
	{
		// Opposite event order: equipment container updates first, so for one
		// refresh the stack is counted in BOTH components (quantity doubled),
		// then the inventory event corrects it. The doubled reading is
		// recorded as 11M loot and the correction as 11M supplies — profit
		// again ends +11M permanently.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 2750L, 4_000L));
		WealthSnapshot initial = snapInvEquip(11_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		// Refresh 1: stack visible in both containers.
		Map<Integer, ItemStack> doubled = new LinkedHashMap<>();
		doubled.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 5500L, 4_000L));
		WealthSnapshot both = snapInvEquip(11_000_000L, 11_000_000L, doubled, 1000L);
		engine.update(both, Collections.emptySet(), 1000L);

		// Refresh 2: inventory catches up.
		WealthSnapshot equipped = snapInvEquip(0L, 11_000_000L, held, 1050L);
		engine.update(equipped, Collections.emptySet(), 1050L);

		SessionSnapshot result = engine.snapshot(equipped, 0L, Collections.emptyMap(), 0L, 1050L);
		assertEquals("equip transient must not create phantom profit", 0L, result.getLootValue());
		assertEquals(0L, result.getUnrealizedPnl());
		assertTrue("equip transient must not leave a phantom loot row", result.getLoot().isEmpty());
		assertEquals(0L, result.getSuppliesUsed());
	}

	@Test
	public void equipWithDifferentWornIdPreservesUnrealizedDrift()
	{
		// A drifted holding keeps its paper gain across an id-swap equip: the
		// cost basis must migrate to the worn id, not be re-entered at the
		// live price (which would silently convert the drift into realised
		// phantom profit).
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(WHIP, new ItemStack(WHIP, "Abyssal whip", 1L, 8_000_000L));
		WealthSnapshot initial = snapInvEquip(8_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		// Price drifts to 11M: +3M unrealized, no profit.
		Map<Integer, ItemStack> drifted = new LinkedHashMap<>();
		drifted.put(WHIP, new ItemStack(WHIP, "Abyssal whip", 1L, 11_000_000L));
		WealthSnapshot afterDrift = snapInvEquip(11_000_000L, 0L, drifted, 1000L);
		engine.update(afterDrift, Collections.emptySet(), 1000L);
		assertEquals(3_000_000L,
			engine.snapshot(afterDrift, 0L, Collections.emptyMap(), 0L, 1000L).getUnrealizedPnl());

		// Equip: worn id differs, same value.
		Map<Integer, ItemStack> worn = new LinkedHashMap<>();
		worn.put(WORN_WHIP, new ItemStack(WORN_WHIP, "Abyssal whip", 1L, 11_000_000L));
		WealthSnapshot equipped = snapInvEquip(0L, 11_000_000L, worn, 2000L);
		engine.update(equipped, Collections.emptySet(), 2000L);

		SessionSnapshot result = engine.snapshot(equipped, 0L, Collections.emptyMap(), 0L, 2000L);
		assertEquals("drift must not be realised by an equip", 0L, result.getLootValue());
		assertEquals("paper gain must survive the equip", 3_000_000L, result.getUnrealizedPnl());
		assertTrue(result.getLoot().isEmpty());
	}

	@Test
	public void equipTransientPreservesUnrealizedDrift()
	{
		// Same drift-preservation guarantee for the two-event transient: the
		// vanish/reappear flicker must not reset the cost basis to the live
		// price (which converts the paper gain into realised phantom profit).
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 2750L, 4_000L));
		WealthSnapshot initial = snapInvEquip(11_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		// Price drifts to 5k: +2.75M unrealized.
		Map<Integer, ItemStack> drifted = new LinkedHashMap<>();
		drifted.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 2750L, 5_000L));
		WealthSnapshot afterDrift = snapInvEquip(13_750_000L, 0L, drifted, 1000L);
		engine.update(afterDrift, Collections.emptySet(), 1000L);
		assertEquals(2_750_000L,
			engine.snapshot(afterDrift, 0L, Collections.emptyMap(), 0L, 1000L).getUnrealizedPnl());

		// Equip flicker: gone, then worn.
		WealthSnapshot gone = snapInvEquip(0L, 0L, Collections.emptyMap(), 2000L);
		engine.update(gone, Collections.emptySet(), 2000L);
		WealthSnapshot equipped = snapInvEquip(0L, 13_750_000L, drifted, 2050L);
		engine.update(equipped, Collections.emptySet(), 2050L);

		SessionSnapshot result = engine.snapshot(equipped, 0L, Collections.emptyMap(), 0L, 2050L);
		assertEquals("flicker must not realise the drift", 0L, result.getLootValue());
		assertEquals("paper gain must survive the flicker", 2_750_000L, result.getUnrealizedPnl());
		assertTrue(result.getLoot().isEmpty());
		assertEquals(0L, result.getSuppliesUsed());
	}

	@Test
	public void genuineConsumptionAfterAnEquipStillCounts()
	{
		// Guard: the transient-reversal netting must not swallow genuine
		// consumption that happens OUTSIDE the reversal window.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 2750L, 4_000L));
		WealthSnapshot initial = snapInvEquip(11_000_000L, 0L, held, 0L);
		engine.startSession(initial, 0L);

		// Clean equip (single interval, same id).
		WealthSnapshot equipped = snapInvEquip(0L, 11_000_000L, held, 1000L);
		engine.update(equipped, Collections.emptySet(), 1000L);

		// Much later: threw 750 chins at things.
		Map<Integer, ItemStack> spent = new LinkedHashMap<>();
		spent.put(BLACK_CHIN, new ItemStack(BLACK_CHIN, "Black chinchompa", 2000L, 4_000L));
		WealthSnapshot afterThrowing = snapInvEquip(0L, 8_000_000L, spent, 60_000L);
		engine.update(afterThrowing, Collections.emptySet(), 60_000L);

		SessionSnapshot result = engine.snapshot(afterThrowing, 0L, Collections.emptyMap(), 0L, 60_000L);
		assertEquals(750L * 4_000L, result.getSuppliesUsed());
		assertEquals("consumption folds into supplies, not profit", 0L, result.getLootValue());
	}

	// --------------------------------------------- parked-stack vanish/return

	private static final int STEEL_CANNONBALL = 2;

	@Test
	public void cannonballStackReturnedMinutesLaterIsNotLootAndUnchargesSupplies()
	{
		// Regression (real session, 10:04:19 -> 10:04:22): loading a dwarf
		// multicannon moves the whole cannonball stack out of the inventory
		// and picking the cannon up returns the unfired remainder — seconds
		// to many minutes later, far outside the one-tick transient window.
		// Observed: a 34,805-ball (9.19M) load rebooked as 9.19M of fresh
		// "loot" on return. Cannonballs classify as consumable supplies, so
		// without netting the round trip books the full stack as supplies AND
		// as loot, inflating profit by the whole stack value. The return must
		// net against the recorded vanish: no loot row, supplies un-charged
		// for the quantity that came back, profit flat.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(STEEL_CANNONBALL, new ItemStack(STEEL_CANNONBALL, "Steel cannonball", 34_805L, 264L));
		engine.startSession(snap(34_805L * 264L, held, 0L, false, 0L), 0L);

		// Cannon loaded: the whole stack leaves tracked wealth.
		engine.update(snap(0L, new LinkedHashMap<>(), 0L, false, 1_000L), Collections.emptySet(), 1_000L);
		assertEquals(34_805L * 264L, engine.getSuppliesUsed());

		// Cannon picked up 3 minutes later: 500 balls were fired in between,
		// so 34,305 return to the inventory.
		Map<Integer, ItemStack> returned = new LinkedHashMap<>();
		returned.put(STEEL_CANNONBALL, new ItemStack(STEEL_CANNONBALL, "Steel cannonball", 34_305L, 264L));
		WealthSnapshot afterPickup = snap(34_305L * 264L, returned, 0L, false, 181_000L);
		engine.update(afterPickup, Collections.emptySet(), 181_000L);

		SessionSnapshot result = engine.snapshot(afterPickup, 0L, Collections.emptyMap(), 0L, 181_000L);
		assertTrue("returned cannonballs must not book as loot", result.getLoot().isEmpty());
		assertEquals("only the fired balls remain booked as supplies",
			500L * 264L, result.getSuppliesUsed());
		assertEquals("a load/pick-up round trip must not move profit", 0L, result.getLootValue());
		assertEquals("identity must hold across the round trip",
			result.getLootValue() - result.getSuppliesUsed() + result.getUnrealizedPnl(),
			result.getNetWorthDelta());
	}

	@Test
	public void cannonPartsReturnedOutsideTransientWindowAreNotLoot()
	{
		// Same pattern for the non-consumable cannon parts (real session:
		// assembled 10:09:02-07, picked up 10:19:49 — 10.7 minutes later):
		// each ~190k part vanishes on assembly (an untracked vanish, so
		// profit dips by its value while the cannon is out) and reappears on
		// pickup. The reappearance must net against the recorded vanish, not
		// book as fresh loot.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(6, new ItemStack(6, "Cannon base", 1L, 196_376L));
		held.put(8, new ItemStack(8, "Cannon stand", 1L, 180_764L));
		engine.startSession(snap(377_140L, held, 0L, false, 0L), 0L);

		// Cannon set up: the parts leave the inventory.
		engine.update(snap(0L, new LinkedHashMap<>(), 0L, false, 1_000L), Collections.emptySet(), 1_000L);

		// Picked back up ~10 minutes later.
		WealthSnapshot back = snap(377_140L, new LinkedHashMap<>(held), 0L, false, 645_000L);
		engine.update(back, Collections.emptySet(), 645_000L);

		SessionSnapshot result = engine.snapshot(back, 0L, Collections.emptyMap(), 0L, 645_000L);
		assertTrue("cannon parts returning must not book as loot", result.getLoot().isEmpty());
		assertEquals(0L, result.getLootValue());
		assertEquals(0L, result.getSuppliesUsed());
	}

	@Test
	public void equalStackLootedBeyondReturnWindowStillCountsAsLoot()
	{
		// Guard: the parked-stack netting is bounded. A whole stack genuinely
		// consumed, followed by an identical stack genuinely looted far
		// outside the vanish-return window, is new wealth and must book as
		// loot (and the original consumption stays charged as supplies).
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(SHARK, new ItemStack(SHARK, "Shark", 5L, 800L));
		engine.startSession(snap(4_000L, held, 0L, false, 0L), 0L);

		// The whole stack is eaten...
		engine.update(snap(0L, new LinkedHashMap<>(), 0L, false, 1_000L), Collections.emptySet(), 1_000L);
		assertEquals(4_000L, engine.getSuppliesUsed());

		// ...and an identical stack is looted 40 minutes later.
		WealthSnapshot after = snap(4_000L, new LinkedHashMap<>(held), 0L, false, 2_401_000L);
		engine.update(after, Collections.emptySet(), 2_401_000L);

		SessionSnapshot result = engine.snapshot(after, 0L, Collections.emptyMap(), 0L, 2_401_000L);
		assertEquals(1, result.getLoot().size());
		assertEquals(4_000L, result.getLoot().get(0).getValue());
		assertEquals("the eaten stack stays charged as supplies", 4_000L, result.getSuppliesUsed());
		assertEquals("genuine loot beyond the window counts as profit", 4_000L, result.getLootValue());
	}

	// ------------------------------------------- dropping looted items lowers Loot

	@Test
	public void droppingALootedItemRemovesItFromLoot()
	{
		// A single looted item dropped and left on the ground (the player walks
		// away, well beyond the 600ms transient window) is no longer held, so
		// the bottom-up Loot must fall back by its value. Mirrors the live
		// report: a 27k rune sq shield dropped for space then abandoned.
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> looted = new LinkedHashMap<>();
		looted.put(RUNE_SQ_SHIELD, new ItemStack(RUNE_SQ_SHIELD, "Rune sq shield", 1L, 27_000L));
		engine.update(snap(10_027_000L, looted, 0L, false, 1_000L), Collections.emptySet(), 1_000L);
		assertEquals(27_000L,
			engine.snapshot(snap(10_027_000L, looted, 0L, false, 1_000L), 0L,
				Collections.emptyMap(), 0L, 1_000L).getLootValue());

		// Dropped a few seconds later and abandoned.
		WealthSnapshot afterDrop = snap(10_000_000L, Collections.emptyMap(), 0L, false, 5_000L);
		engine.update(afterDrop, Collections.emptySet(), 5_000L);

		SessionSnapshot result = engine.snapshot(afterDrop, 0L, Collections.emptyMap(), 0L, 5_000L);
		assertEquals("dropping a looted item removes its value from Loot",
			0L, result.getLootValue());
	}

	@Test
	public void droppingThenRepickingALootedItemNetsToTheOriginalLoot()
	{
		// Dropping a looted item for space and picking it back up (within the
		// 30-min return window) must net to zero: Loot returns to the original
		// value, never doubled. This is the exact live scenario — drop a single
		// item, stand ~2s, pick it up — that previously inflated Loot.
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> looted = new LinkedHashMap<>();
		looted.put(RUNE_SQ_SHIELD, new ItemStack(RUNE_SQ_SHIELD, "Rune sq shield", 1L, 27_000L));
		engine.update(snap(10_027_000L, looted, 0L, false, 1_000L), Collections.emptySet(), 1_000L);

		// Dropped...
		engine.update(snap(10_000_000L, Collections.emptyMap(), 0L, false, 3_000L),
			Collections.emptySet(), 3_000L);

		// ...and picked back up two seconds later.
		WealthSnapshot afterRepick = snap(10_027_000L, looted, 0L, false, 5_000L);
		engine.update(afterRepick, Collections.emptySet(), 5_000L);

		SessionSnapshot result = engine.snapshot(afterRepick, 0L, Collections.emptyMap(), 0L, 5_000L);
		assertEquals("drop then re-pickup of looted item nets to the original Loot",
			27_000L, result.getLootValue());
	}

	@Test
	public void droppingThenRepickingAPreOwnedItemNeverCountsAsLoot()
	{
		// An item never looted this session (withdrawn from the bank / owned at
		// session start) that is dropped and re-picked must stay out of Loot
		// entirely — the re-pickup is not fresh loot, and the drop cannot push
		// Loot below zero.
		Map<Integer, ItemStack> owned = new LinkedHashMap<>();
		owned.put(RUNE_SQ_SHIELD, new ItemStack(RUNE_SQ_SHIELD, "Rune sq shield", 1L, 27_000L));
		WealthSnapshot initial = snap(10_027_000L, owned, 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Dropped...
		engine.update(snap(10_000_000L, Collections.emptyMap(), 0L, false, 3_000L),
			Collections.emptySet(), 3_000L);

		// ...and picked back up.
		WealthSnapshot afterRepick = snap(10_027_000L, owned, 0L, false, 5_000L);
		engine.update(afterRepick, Collections.emptySet(), 5_000L);

		SessionSnapshot result = engine.snapshot(afterRepick, 0L, Collections.emptyMap(), 0L, 5_000L);
		assertEquals("re-picking a never-looted item is not loot",
			0L, result.getLootValue());
	}

	@Test
	public void droppingThenRepickingPartOfALootedStackNetsToTheOriginalLoot()
	{
		// The live scenario: bird nests aggregate by item id (non-stackable but
		// merged), so dropping one of several is a PARTIAL decrease (fullSwing
		// false), not a whole-stack vanish. Dropping one looted nest and picking
		// it back up must net to zero, not inflate Loot by another nest's value.
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> two = new LinkedHashMap<>();
		two.put(BIRD_NEST, new ItemStack(BIRD_NEST, "Bird nest", 2L, 4_644L));
		engine.update(snap(10_009_288L, two, 0L, false, 1_000L), Collections.emptySet(), 1_000L);
		assertEquals(2L * 4_644L,
			engine.snapshot(snap(10_009_288L, two, 0L, false, 1_000L), 0L,
				Collections.emptyMap(), 0L, 1_000L).getLootValue());

		// Drop one (2 -> 1)...
		Map<Integer, ItemStack> one = new LinkedHashMap<>();
		one.put(BIRD_NEST, new ItemStack(BIRD_NEST, "Bird nest", 1L, 4_644L));
		engine.update(snap(10_004_644L, one, 0L, false, 3_000L), Collections.emptySet(), 3_000L);

		// ...and pick it back up (1 -> 2).
		WealthSnapshot afterRepick = snap(10_009_288L, two, 0L, false, 5_000L);
		engine.update(afterRepick, Collections.emptySet(), 5_000L);

		SessionSnapshot result = engine.snapshot(afterRepick, 0L, Collections.emptyMap(), 0L, 5_000L);
		assertEquals("dropping and re-picking one of a looted stack must not inflate Loot",
			2L * 4_644L, result.getLootValue());
	}

	@Test
	public void suppliesUsedResetsOnNewSession()
	{
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(PRAYER_POTION, new ItemStack(PRAYER_POTION, "Prayer potion(4)", 5L, 1_000L));
		WealthSnapshot initial = snap(5_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		afterTracked.put(PRAYER_POTION, new ItemStack(PRAYER_POTION, "Prayer potion(4)", 2L, 1_000L));
		WealthSnapshot current = snap(2_000L, afterTracked, 0L, false, 1000L);
		engine.update(current, Collections.emptySet(), 1000L);
		assertEquals(3_000L, engine.getSuppliesUsed());

		// Restarting the session must clear the running total.
		engine.startSession(current, 1000L);
		assertEquals(0L, engine.getSuppliesUsed());
	}

	private static final int SUPER_ANTIFIRE_4 = 21978;
	private static final int SUPER_ANTIFIRE_3 = 21981;
	private static final int SUPER_ANTIFIRE_1 = 21987;

	@Test
	public void drinkingAPotionDoseDownIsNotBookedAsLoot()
	{
		// Regression: drinking one dose of a 4-dose potion drops the (4) stack
		// (correctly booked as supplies) but a (3) stack of a DIFFERENT item id
		// appears in the same interval. The existing Section 2 transfer pairing
		// only matches equal unit values, so the appearing (3) stack (worth
		// less than the vanished (4) stack) was falling through to loot,
		// inflating profit. Only the value of the dose actually drunk
		// (4000 - 3000 = 1000) may move, and it must land in suppliesUsed, not
		// profit/loot.
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(SUPER_ANTIFIRE_4,
			new ItemStack(SUPER_ANTIFIRE_4, "Super antifire potion(4)", 1L, 4_000L));
		WealthSnapshot initial = snap(4_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		afterTracked.put(SUPER_ANTIFIRE_3,
			new ItemStack(SUPER_ANTIFIRE_3, "Super antifire potion(3)", 1L, 3_000L));
		WealthSnapshot current = snap(3_000L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals("drinking a dose must not register as profit", 0L, result.getLootValue());
		assertEquals("drinking a dose must not appear as loot", 0, result.getLoot().size());
		assertEquals("only the consumed dose value counts as supplies used",
			1_000L, result.getSuppliesUsed());
		assertEquals(1_000L, engine.getSuppliesUsed());
	}

	@Test
	public void drinkingTheFinalDoseOfADosePotionStillCountsAsSuppliesUsed()
	{
		// Edge case: drinking the LAST dose makes the item vanish entirely
		// (no lower-dose item appears at all). This must keep going through
		// the existing full-vanish supplies path unaffected by the new
		// dose-swap pairing.
		Map<Integer, ItemStack> initialTracked = new LinkedHashMap<>();
		initialTracked.put(SUPER_ANTIFIRE_1,
			new ItemStack(SUPER_ANTIFIRE_1, "Super antifire potion(1)", 1L, 1_000L));
		WealthSnapshot initial = snap(1_000L, initialTracked, 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> afterTracked = new LinkedHashMap<>();
		WealthSnapshot current = snap(0L, afterTracked, 0L, false, 1000L);

		engine.update(current, Collections.emptySet(), 1000L);
		SessionSnapshot result = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);

		assertEquals("drinking the last dose must still count as supplies used",
			1_000L, result.getSuppliesUsed());
		assertEquals("drinking the last dose must not register as a profit loss",
			0L, result.getLootValue());
		assertTrue("drinking the last dose must not appear as loot", result.getLoot().isEmpty());
	}

	@Test
	public void verboseDiagnostics_promotesPerUpdateAttributionToInfo()
	{
		ch.qos.logback.classic.Logger logger =
			(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SessionEngine.class);
		ch.qos.logback.classic.Level originalLevel = logger.getLevel();
		ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
			new ch.qos.logback.core.read.ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		// Pin the logger at INFO so DEBUG is definitively off (independent of
		// logback-test.xml): only the verbose(INFO) diagnostics can then surface.
		logger.setLevel(ch.qos.logback.classic.Level.INFO);
		try
		{
			// Verbose OFF: the loot attribution logs at DEBUG -> filtered at INFO.
			engine.setVerboseDiagnostics(false);
			runLootUpdate(engine);
			assertTrue("no INFO diagnostics should appear when verbose is off",
				appender.list.stream().noneMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO));

			// Verbose ON: the same per-update/[attrib] diagnostics now log at INFO
			// (this is what lets a phantom gear-swap gain be captured from the
			// default INFO client.log). Guards against the isDebugEnabled()
			// short-circuit that previously suppressed them even in verbose mode.
			appender.list.clear();
			SessionEngine verbose = new SessionEngine();
			verbose.setVerboseDiagnostics(true);
			runLootUpdate(verbose);
			assertTrue("verbose must surface the per-update attribution diagnostics at INFO",
				appender.list.stream().anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO
					&& (e.getFormattedMessage().contains("[attrib]")
						|| e.getFormattedMessage().startsWith("update:"))));
		}
		finally
		{
			logger.detachAppender(appender);
			logger.setLevel(originalLevel);
		}
	}

	/** Drives a startSession + a loot-producing update (triggers a LOOT [attrib] + the update: breakdown). */
	private static void runLootUpdate(SessionEngine engine)
	{
		engine.startSession(snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L), 0L);
		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		engine.update(snap(11_000_000L, tracked, 0L, false, 1000L), Collections.emptySet(), 1000L);
	}

	// ------------------------------------------------------- GE collection lag

	private static final int COINS = GeReconciler.COINS_ITEM_ID;
	private static final int DRAGON_JAVELIN = 19484;

	/** Snapshot with an explicit GE-in-flight component (escrow/locked offers). */
	private static WealthSnapshot snapGe(long inventoryValue, long geInFlightValue,
		Map<Integer, ItemStack> trackedItems, long ts)
	{
		return WealthSnapshot.builder()
			.inventoryValue(inventoryValue)
			.equipmentValue(0L)
			.geInFlightValue(geInFlightValue)
			.pouchValue(0L)
			.bankValue(0L)
			.bankKnown(false)
			.timestampMs(ts)
			.trackedItems(trackedItems)
			.build();
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

	/** Expires + updates in the order the live integration layer does. */
	private static void tick(SessionEngine engine, GeReconciler ge, WealthSnapshot current, long ts)
	{
		ge.expireAttributions(ts);
		engine.update(current, ge, ts);
	}

	@Test
	public void geBuyCollectedTicksAfterFillIsNotLoot()
	{
		// A completed buy sits in the collection box until the player clicks
		// Collect, so the offer-fill event and the inventory arrival land in
		// DIFFERENT updates (potentially minutes apart). The arrival must be
		// attributed to the GE, not booked as phantom loot.
		GeReconciler ge = new GeReconciler();
		WealthSnapshot initial = snapGe(302_500L, 0L,
			items(new ItemStack(COINS, "Coins", 302_500L, 1L)), 0L);
		engine.startSession(initial, 0L);

		// Buy offer placed: the coins move into GE escrow (tracked unchanged).
		tick(engine, ge, snapGe(0L, 302_500L, Collections.emptyMap(), 1_000L), 1_000L);

		// Offer fills: escrow released towards the collection box.
		ge.onOfferUpdate(0, GeOfferState.BOUGHT, DRAGON_JAVELIN, "Dragon javelin",
			500L, 500L, 302_500L, 605L, 2_000L);
		tick(engine, ge, snapGe(0L, 0L, Collections.emptyMap(), 2_000L), 2_000L);

		// Collected two minutes later: the javelins finally enter the inventory.
		WealthSnapshot collected = snapGe(302_500L, 0L,
			items(new ItemStack(DRAGON_JAVELIN, "Dragon javelin", 500L, 605L)), 122_000L);
		tick(engine, ge, collected, 122_000L);

		SessionSnapshot result = engine.snapshot(collected, 0L, Collections.emptyMap(), 0L, 122_000L);
		assertTrue("a collected GE buy must not appear as loot", result.getLoot().isEmpty());
		assertEquals("a flat buy-and-collect round trip is profit-neutral",
			0L, result.getLootValue());
		assertEquals(0L, result.getSuppliesUsed());
	}

	@Test
	public void geSaleProceedsCoinsAreNotLootAndPlacementIsNotSupplies()
	{
		// Selling consumable-classified items (javelins are ammunition): the
		// placement-time inventory removal must not charge supplies, and the
		// collected coin proceeds must not book as loot. Only the GE tax may
		// move profit (a real cost of the sale).
		GeReconciler ge = new GeReconciler();
		WealthSnapshot initial = snapGe(302_500L, 0L,
			items(new ItemStack(DRAGON_JAVELIN, "Dragon javelin", 500L, 605L)), 0L);
		engine.startSession(initial, 0L);

		// Sell offer placed: the javelins leave the inventory into the exchange.
		ge.onOfferUpdate(0, GeOfferState.SELLING, DRAGON_JAVELIN, "Dragon javelin",
			500L, 0L, 0L, 605L, 1_000L);
		tick(engine, ge, snapGe(0L, 302_500L, Collections.emptyMap(), 1_000L), 1_000L);

		// Offer fills: 302,500 gross, minus per-item tax (605/50 = 12) x 500.
		ge.onOfferUpdate(0, GeOfferState.SOLD, DRAGON_JAVELIN, "Dragon javelin",
			500L, 500L, 302_500L, 605L, 2_000L);
		tick(engine, ge, snapGe(0L, 0L, Collections.emptyMap(), 2_000L), 2_000L);

		// Proceeds collected several ticks later.
		WealthSnapshot collected = snapGe(296_500L, 0L,
			items(new ItemStack(COINS, "Coins", 296_500L, 1L)), 8_000L);
		tick(engine, ge, collected, 8_000L);

		SessionSnapshot result = engine.snapshot(collected, 0L, Collections.emptyMap(), 0L, 8_000L);
		assertTrue("GE sale proceeds must not appear as loot", result.getLoot().isEmpty());
		assertEquals("placing a sell offer must not charge supplies",
			0L, result.getSuppliesUsed());
		assertEquals("a pre-owned sale is not loot", 0L, result.getLootValue());
		assertEquals("the GE tax is a net-worth loss", -6_000L, result.getNetWorthDelta());
	}

	@Test
	public void genuineLootBeyondGeExpectedQuantityStillCounts()
	{
		// Quantity-capped attribution: collecting 100 bought bones alongside a
		// genuine 30-bone drop in the same interval attributes exactly the 100
		// and keeps the 30 as loot; once the expectation is exhausted, later
		// gains of the same item are all loot again.
		GeReconciler ge = new GeReconciler();
		WealthSnapshot initial = snapGe(100_000L, 0L,
			items(new ItemStack(COINS, "Coins", 100_000L, 1L)), 0L);
		engine.startSession(initial, 0L);

		tick(engine, ge, snapGe(0L, 100_000L, Collections.emptyMap(), 1_000L), 1_000L);
		ge.onOfferUpdate(0, GeOfferState.BOUGHT, DRAGON_BONES, "Dragon bones",
			100L, 100L, 100_000L, 1_000L, 2_000L);
		tick(engine, ge, snapGe(0L, 0L, Collections.emptyMap(), 2_000L), 2_000L);

		// Collect the 100 bought bones + a genuine 30-bone drop, same interval.
		WealthSnapshot collected = snapGe(130_000L, 0L,
			items(new ItemStack(DRAGON_BONES, "Dragon bones", 130L, 1_000L)), 3_000L);
		tick(engine, ge, collected, 3_000L);

		SessionSnapshot afterCollect = engine.snapshot(collected, 0L, Collections.emptyMap(), 0L, 3_000L);
		assertEquals(1, afterCollect.getLoot().size());
		assertEquals("only the drop beyond the bought quantity is loot",
			30L, afterCollect.getLoot().get(0).getQuantity());
		assertEquals(30_000L, afterCollect.getLoot().get(0).getValue());

		// The expectation is exhausted: 20 more bones are pure loot.
		WealthSnapshot moreDrops = snapGe(150_000L, 0L,
			items(new ItemStack(DRAGON_BONES, "Dragon bones", 150L, 1_000L)), 4_000L);
		tick(engine, ge, moreDrops, 4_000L);

		SessionSnapshot result = engine.snapshot(moreDrops, 0L, Collections.emptyMap(), 0L, 4_000L);
		assertEquals(1, result.getLoot().size());
		assertEquals(50L, result.getLoot().get(0).getQuantity());
		assertEquals(50_000L, result.getLoot().get(0).getValue());
	}

	@Test
	public void geExpectationCollectedToBankExpiresAndLaterDropsAreLoot()
	{
		// Collect-to-bank never produces a tracked-inventory arrival: once the
		// slot clears and the settle window lapses, the stale expectation must
		// be gone so a later genuine drop of the same item still counts.
		GeReconciler ge = new GeReconciler();
		WealthSnapshot initial = snapGe(100_000L, 0L,
			items(new ItemStack(COINS, "Coins", 100_000L, 1L)), 0L);
		engine.startSession(initial, 0L);

		tick(engine, ge, snapGe(0L, 100_000L, Collections.emptyMap(), 1_000L), 1_000L);
		ge.onOfferUpdate(0, GeOfferState.BOUGHT, DRAGON_BONES, "Dragon bones",
			100L, 100L, 100_000L, 1_000L, 2_000L);
		tick(engine, ge, snapGe(0L, 0L, Collections.emptyMap(), 2_000L), 2_000L);

		// Collected straight to bank: the slot clears, nothing enters tracking.
		ge.onOfferUpdate(0, GeOfferState.EMPTY, 0, null, 0L, 0L, 0L, 0L, 3_000L);
		tick(engine, ge, snapGe(0L, 0L, Collections.emptyMap(), 20_000L), 20_000L);

		// A genuine 100-bone drop long after must be loot, not swallowed.
		WealthSnapshot drops = snapGe(100_000L, 0L,
			items(new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 1_000L)), 21_000L);
		tick(engine, ge, drops, 21_000L);

		SessionSnapshot result = engine.snapshot(drops, 0L, Collections.emptyMap(), 0L, 21_000L);
		assertEquals(1, result.getLoot().size());
		assertEquals(100L, result.getLoot().get(0).getQuantity());
	}

	@Test
	public void lootedItemSoldOnGeRealisesAfterTax()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);
		GeReconciler ge = new GeReconciler();

		// Tick 1: loot a shield (GE value 22,400).
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(RUNE_SQ_SHIELD, new ItemStack(RUNE_SQ_SHIELD, "Rune sq shield", 1L, 22_400L));
		WealthSnapshot looted = snap(10_022_400L, held, 0L, false, 1000L);
		ge.expireAttributions(1000L);
		engine.update(looted, ge, 1000L);
		assertEquals(22_400L, engine.snapshot(looted, ge.realizedPnl(),
			Collections.emptyMap(), 0L, 1000L).getLootValue());

		// Tick 2: sell it on the GE at 22,400 (fills fully); the shield leaves the
		// inventory the same tick and the proceeds are still in the collection box.
		ge.onOfferUpdate(0, GeOfferState.SOLD, RUNE_SQ_SHIELD, "Rune sq shield",
			1, 1, 22_400L, 22_400L, 2000L);
		WealthSnapshot afterSale = snap(10_000_000L, Collections.emptyMap(), 0L, false, 2000L);
		ge.expireAttributions(2000L);
		engine.update(afterSale, ge, 2000L);

		SessionSnapshot s = engine.snapshot(afterSale, ge.realizedPnl(),
			Collections.emptyMap(), 0L, 2000L);
		assertEquals("loot realised to after-tax proceeds", 21_952L, s.getLootValue());
	}

	@Test
	public void bankedLootStaysCountedAndSellingRealises()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);
		GeReconciler ge = new GeReconciler();

		// Loot a shield.
		Map<Integer, ItemStack> held = new LinkedHashMap<>();
		held.put(RUNE_SQ_SHIELD, new ItemStack(RUNE_SQ_SHIELD, "Rune sq shield", 1L, 22_400L));
		WealthSnapshot looted = snap(10_022_400L, held, 0L, false, 1000L);
		ge.expireAttributions(1000L);
		engine.update(looted, ge, 1000L);

		// Open bank, deposit the shield (tracked drops, bank rises), close bank.
		WealthSnapshot atOpen = snap(10_022_400L, held, 0L, true, 1100L);
		engine.setBankOpen(true, atOpen, 1100L);
		WealthSnapshot afterDeposit = snap(10_000_000L, Collections.emptyMap(), 22_400L, true, 1200L);
		engine.setBankOpen(false, afterDeposit, 1200L);

		SessionSnapshot afterBank = engine.snapshot(afterDeposit, ge.realizedPnl(),
			Collections.emptyMap(), 0L, 1200L);
		assertEquals("banking is loot-neutral", 22_400L, afterBank.getLootValue());
	}

	@Test
	public void skillingGainWithNoLootEventStillCountsAsLoot()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);
		GeReconciler ge = new GeReconciler();

		// A redwood log appears in the inventory with no GE activity (skilling).
		Map<Integer, ItemStack> logs = new LinkedHashMap<>();
		logs.put(19_669, new ItemStack(19_669, "Redwood logs", 10L, 300L)); // 3,000
		WealthSnapshot chopped = snap(10_003_000L, logs, 0L, false, 1000L);
		ge.expireAttributions(1000L);
		engine.update(chopped, ge, 1000L);

		assertEquals(3_000L, engine.snapshot(chopped, ge.realizedPnl(),
			Collections.emptyMap(), 0L, 1000L).getLootValue());
	}

	@Test
	public void signalledUpdateWithNoSignalsMatchesLegacyLoot()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		Map<Integer, ItemStack> tracked = new HashMap<>();
		tracked.put(DRAGON_BONES, new ItemStack(DRAGON_BONES, "Dragon bones", 100L, 10_000L));
		WealthSnapshot current = snap(11_000_000L, tracked, 0L, false, 1000L);
		engine.update(current, (GeAttributions) null, MovementSignals.NONE, 1000L);

		SessionSnapshot s = engine.snapshot(current, 0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals(1_000_000L, s.getLootValue());
	}

	@Test
	public void droppedConsumableIsNotSuppliesUsed()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Pre-owned prayer potions present, then dropped (NOT drunk).
		Map<Integer, ItemStack> owned = items(new ItemStack(PRAYER_POT, "Prayer potion(4)", 3L, 9_000L));
		engine.update(snap(10_000_000L, owned, 0L, false, 1000L), Collections.emptySet(), 1000L);

		MovementSignals dropped = MovementSignals.builder().dropped(PRAYER_POT).build();
		engine.update(snap(10_000_000L, Collections.emptyMap(), 0L, false, 2000L),
			(GeAttributions) null, dropped, 2000L);

		SessionSnapshot s = engine.snapshot(snap(10_000_000L, Collections.emptyMap(), 0L, false, 2000L),
			0L, Collections.emptyMap(), 0L, 2000L);
		assertEquals("dropping a supply is not consumption", 0L, s.getSuppliesUsed());
		assertEquals("pre-owned drop touches no loot", 0L, s.getLootValue());
	}

	@Test
	public void droppedLootedItemReducesLootByLootedPortion()
	{
		WealthSnapshot initial = snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L);
		engine.startSession(initial, 0L);

		// Loot 10 bird nests @ 5k = 50k.
		Map<Integer, ItemStack> looted = items(new ItemStack(BIRD_NEST, "Bird nest", 10L, 5_000L));
		engine.update(snap(10_050_000L, looted, 0L, false, 1000L), Collections.emptySet(), 1000L);
		SessionSnapshot afterLoot = engine.snapshot(snap(10_050_000L, looted, 0L, false, 1000L),
			0L, Collections.emptyMap(), 0L, 1000L);
		assertEquals(50_000L, afterLoot.getLootValue());

		// Drop 4 of them (partial stack).
		MovementSignals dropped = MovementSignals.builder().dropped(BIRD_NEST).build();
		Map<Integer, ItemStack> after = items(new ItemStack(BIRD_NEST, "Bird nest", 6L, 5_000L));
		engine.update(snap(10_030_000L, after, 0L, false, 2000L), (GeAttributions) null, dropped, 2000L);

		SessionSnapshot s = engine.snapshot(snap(10_030_000L, after, 0L, false, 2000L),
			0L, Collections.emptyMap(), 0L, 2000L);
		assertEquals("loot drops by the 4 dropped nests", 30_000L, s.getLootValue());
		assertEquals(0L, s.getSuppliesUsed());
	}

	@Test
	public void droppedThenRepickedLootNetsToOriginal()
	{
		engine.startSession(snap(10_000_000L, Collections.emptyMap(), 0L, false, 0L), 0L);
		Map<Integer, ItemStack> looted = items(new ItemStack(BIRD_NEST, "Bird nest", 10L, 5_000L));
		engine.update(snap(10_050_000L, looted, 0L, false, 1000L), Collections.emptySet(), 1000L);

		MovementSignals dropped = MovementSignals.builder().dropped(BIRD_NEST).build();
		engine.update(snap(10_000_000L, Collections.emptyMap(), 0L, false, 2000L),
			(GeAttributions) null, dropped, 2000L);

		// Re-pick all 10 (no drop signal on pickup).
		engine.update(snap(10_050_000L, looted, 0L, false, 3000L), Collections.emptySet(), 3000L);

		SessionSnapshot s = engine.snapshot(snap(10_050_000L, looted, 0L, false, 3000L),
			0L, Collections.emptyMap(), 0L, 3000L);
		assertEquals("round trip nets to original loot", 50_000L, s.getLootValue());
		assertEquals(0L, s.getSuppliesUsed());
	}

	@Test
	public void preOwnedPartialDropThenRepickStaysZeroLoot()
	{
		// Seed the pre-owned stack via startSession's INITIAL tracked items
		// (not a subsequent update), so it is never booked as an in-session
		// increase/loot.
		Map<Integer, ItemStack> owned = items(new ItemStack(BARLEY_SEED, "Barley seed", 51L, 4L));
		engine.startSession(snap(10_000_204L, owned, 0L, false, 0L), 0L);

		MovementSignals dropped = MovementSignals.builder().dropped(BARLEY_SEED).build();
		engine.update(snap(10_000_000L, Collections.emptyMap(), 0L, false, 2000L),
			(GeAttributions) null, dropped, 2000L);
		engine.update(snap(10_000_204L, owned, 0L, false, 3000L), Collections.emptySet(), 3000L);

		SessionSnapshot s = engine.snapshot(snap(10_000_204L, owned, 0L, false, 3000L),
			0L, Collections.emptyMap(), 0L, 3000L);
		assertEquals("pre-owned drop/re-pick never becomes loot", 0L, s.getLootValue());
	}
}
