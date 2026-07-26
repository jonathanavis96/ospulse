package com.ospulse.integration;

import com.google.gson.Gson;
import com.ospulse.OSPulseConfig;
import com.ospulse.ge.GeAttributions;
import com.ospulse.session.DiffLoot;
import com.ospulse.session.MovementSignals;
import com.ospulse.session.SessionEngine;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.session.SourceLoot;
import com.ospulse.wealth.WealthSnapshot;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the once-per-tick economic-commit discipline: eager container/loot
 * events must NOT advance the engine baseline or drain movement signals — only
 * the authoritative per-tick {@link SessionTracker#onTick()} commits.
 *
 * <p>Without this, a single game tick whose inventory effects span several
 * {@code ItemContainerChanged} events — die-then-reclaim, search-all bird nests,
 * drop/destroy spanning ticks — fragments into several mis-attributed
 * transactions: the {@code died} signal drains on the first event and the
 * baseline snaps forward, so reclaimed gear books as fresh loot (the live
 * phantom-53.9M-profit-on-death report) and follow-up nest searches are lost.
 */
public class SessionTrackerTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

	/** Bird nest contents — the auto-search case that fires no LootReceived. */
	private static final int SNAPDRAGON_SEED_ID = 5300;
	private static final long SNAPDRAGON_SEED_UNIT = 40_000L;
	/** An ordinary NPC drop, which RuneLite's Loot Tracker does report. */
	private static final int BONES_ID = 526;
	private static final long BONES_UNIT = 100L;

	private Client client;
	private ItemManager itemManager;
	private SessionEngine engine;
	private SessionTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		OSPulseConfig config = mock(OSPulseConfig.class);
		ConfigManager configManager = mock(ConfigManager.class);
		engine = spy(new SessionEngine());

		when(client.getItemContainer(any())).thenReturn(null);
		when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[0]);
		when(config.verboseDiagnostics()).thenReturn(false);
		when(config.includeFishBarrel()).thenReturn(false);
		when(config.includePouches()).thenReturn(false);

		tracker = new SessionTracker(client, itemManager, config, configManager, new Gson(), engine);
		tracker.onLogin();
		tracker.onTick();   // first tick bootstraps the session (engine.startSession), then returns
		reset(engine);      // ignore bootstrap interactions; assert only on post-bootstrap events
	}

	/**
	 * Teaches the mocked {@link ItemManager} about one item so the tracker's
	 * valuation can canonicalize, name and price it — the path
	 * {@link SessionTracker#onLootReceived} takes to build both the feed row and
	 * the {@code LootReceipt} the diff correlation matches against.
	 */
	private void priceItem(int itemId, String name, int unitValue)
	{
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.isTradeable()).thenReturn(true);
		when(comp.getName()).thenReturn(name);
		when(comp.getHaPrice()).thenReturn(0);
		when(itemManager.canonicalize(itemId)).thenReturn(itemId);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(unitValue);
	}

	/** The published feed's group for {@code source}, or null when absent. */
	private SourceLoot sourceNamed(String source)
	{
		for (SourceLoot s : tracker.getLatest().getLootSources())
		{
			if (s.getSource().equals(source))
			{
				return s;
			}
		}
		return null;
	}

	/**
	 * Stubs the engine's per-update residue accessor so a tick observes exactly
	 * these unexplained inventory appearances. Successive lists are returned on
	 * successive ticks, mirroring the engine clearing the residue each update.
	 */
	private void engineReportsDiffLoot(List<DiffLoot> first, Object... rest)
	{
		doReturn(first, rest).when(engine).lastUpdateDiffLoot();
	}

	/**
	 * THE CORE REGRESSION (bird nests searched by an auto mechanism): the wealth
	 * appears in the inventory but RuneLite's Loot Tracker never fires, so no
	 * {@code LootReceived} and no receipt exists. The engine's own diff already
	 * books it as LOOT; the feed must show it too, under a stable constant key.
	 */
	@Test
	public void unattributedInventoryAppearLandsInTheFeedUnderTheUnattributedSource()
	{
		engineReportsDiffLoot(Collections.singletonList(
			new DiffLoot(SNAPDRAGON_SEED_ID, "Snapdragon seed", 1L, SNAPDRAGON_SEED_UNIT)));

		tracker.onTick();

		SourceLoot s = sourceNamed(SessionTracker.UNATTRIBUTED_LOOT_SOURCE);
		assertNotNull("an inventory appear with no LootReceived must reach the feed", s);
		assertEquals(SNAPDRAGON_SEED_UNIT, s.getTotalValue());
		assertEquals(1, s.getItems().size());
		assertEquals("Snapdragon seed", s.getItems().get(0).getName());
		assertEquals(1L, s.getItems().get(0).getQuantity());
		assertNull("it must not be conflated with RuneLite's empty-source Unknown",
			sourceNamed(SessionTracker.UNKNOWN_LOOT_SOURCE));
	}

	/**
	 * THE CRITICAL RISK. A normal kill is seen TWICE: once as a {@code
	 * LootReceived} (which books the feed row) and once as the same tick's
	 * inventory appear (which the engine books as LOOT). The diff residue must be
	 * netted against the tick's receipts or every kill double-counts in the feed.
	 */
	@Test
	public void aKillWithAMatchingLootReceivedIsAttributedToTheNpcExactlyOnce()
	{
		priceItem(BONES_ID, "Bones", (int) BONES_UNIT);
		// Same tick: the drop event, then the inventory diff that carries it.
		tracker.onLootReceived("Goblin", 1, Collections.singletonList(
			new net.runelite.client.game.ItemStack(BONES_ID, 3)));
		engineReportsDiffLoot(Collections.singletonList(
			new DiffLoot(BONES_ID, "Bones", 3L, BONES_UNIT)));

		tracker.onTick();

		SourceLoot goblin = sourceNamed("Goblin");
		assertNotNull("the receipt still books the kill under its NPC source", goblin);
		assertEquals("the kill is worth 3 bones, NOT 6 — the diff must not re-book it",
			3 * BONES_UNIT, goblin.getTotalValue());
		assertEquals(3L, goblin.getItems().get(0).getQuantity());
		assertNull("the matched appear must not also land under the unattributed key",
			sourceNamed(SessionTracker.UNATTRIBUTED_LOOT_SOURCE));
	}

	/**
	 * Undercount guard: several unattributed appears in a row (nest after nest)
	 * must each register. They aggregate under the one stable key — a per-tick
	 * key would break the panel's persisted collapse/hide identity — but their
	 * quantities must accumulate, never collapse onto the last one seen.
	 */
	@Test
	public void rapidUnattributedAppearsEachRegisterInsteadOfCollapsing()
	{
		engineReportsDiffLoot(
			Collections.singletonList(new DiffLoot(SNAPDRAGON_SEED_ID, "Snapdragon seed", 1L, SNAPDRAGON_SEED_UNIT)),
			Collections.singletonList(new DiffLoot(SNAPDRAGON_SEED_ID, "Snapdragon seed", 2L, SNAPDRAGON_SEED_UNIT)),
			Collections.singletonList(new DiffLoot(SNAPDRAGON_SEED_ID, "Snapdragon seed", 1L, SNAPDRAGON_SEED_UNIT)));

		tracker.onTick();
		tracker.onTick();
		tracker.onTick();

		SourceLoot s = sourceNamed(SessionTracker.UNATTRIBUTED_LOOT_SOURCE);
		assertNotNull(s);
		assertEquals("all four seeds accumulate", 4L, s.getItems().get(0).getQuantity());
		assertEquals(4 * SNAPDRAGON_SEED_UNIT, s.getTotalValue());
		assertEquals("each search counts as its own drop", 3L, s.getCount());
	}

	/**
	 * Existing behaviour preserved: a {@code LootReceived} carrying a source still
	 * aggregates exactly as it does today, with no engine diff residue involved.
	 */
	@Test
	public void lootReceivedWithASourceStillAggregatesAsBefore()
	{
		priceItem(BONES_ID, "Bones", (int) BONES_UNIT);
		engineReportsDiffLoot(Collections.<DiffLoot>emptyList());

		tracker.onLootReceived("Goblin", 1, Collections.singletonList(
			new net.runelite.client.game.ItemStack(BONES_ID, 2)));
		tracker.onLootReceived("Goblin", 1, Collections.singletonList(
			new net.runelite.client.game.ItemStack(BONES_ID, 5)));
		tracker.onTick();

		SourceLoot goblin = sourceNamed("Goblin");
		assertNotNull(goblin);
		assertEquals(7L, goblin.getItems().get(0).getQuantity());
		assertEquals(7 * BONES_UNIT, goblin.getTotalValue());
		assertEquals("two kills", 2L, goblin.getCount());
	}

	/**
	 * A tick whose diff carries MORE than the receipt reported (a kill and an
	 * auto-search of the same id landing together) nets the receipt out and books
	 * only the genuine remainder — the netting is per-quantity, not all-or-nothing
	 * on an exact tuple match, so neither side double-counts nor disappears.
	 */
	@Test
	public void anAppearLargerThanItsReceiptBooksOnlyTheUnreceiptedRemainder()
	{
		priceItem(BONES_ID, "Bones", (int) BONES_UNIT);
		tracker.onLootReceived("Goblin", 1, Collections.singletonList(
			new net.runelite.client.game.ItemStack(BONES_ID, 2)));
		engineReportsDiffLoot(Collections.singletonList(
			new DiffLoot(BONES_ID, "Bones", 5L, BONES_UNIT)));

		tracker.onTick();

		assertEquals("the receipted 2 stay with the NPC",
			2 * BONES_UNIT, sourceNamed("Goblin").getTotalValue());
		SourceLoot un = sourceNamed(SessionTracker.UNATTRIBUTED_LOOT_SOURCE);
		assertNotNull("the unreceipted 3 are still real wealth and must be shown", un);
		assertEquals(3L, un.getItems().get(0).getQuantity());
	}

	/**
	 * Bot-review finding (P1): RuneLite's {@code LootReceived} for an ordinary
	 * ground drop fires when the NPC dies (the drop appears), while the
	 * inventory diff is only observed on whatever later tick the player
	 * actually walks over and picks it up — the two events are NOT guaranteed
	 * to land on the same tick the way {@link
	 * #aKillWithAMatchingLootReceivedIsAttributedToTheNpcExactlyOnce} assumes.
	 * The per-tick netting map in {@link SessionTracker#attributeDiffLoot} is
	 * rebuilt fresh from each tick's own {@code MovementSignals}, so a receipt
	 * booked on tick 1 cannot net against a diff that only appears on tick 2 —
	 * the delayed pickup would double-count as {@code Inventory (unattributed)}
	 * on top of the row {@code onLootReceived} already booked under the NPC.
	 */
	@Test
	public void aDelayedPickupOfAnEarlierGroundDropDoesNotDoubleCountItInTheFeed()
	{
		priceItem(BONES_ID, "Bones", (int) BONES_UNIT);

		// Tick 1: the NPC dies and LootReceived fires immediately — nothing has
		// reached the inventory yet, so this tick's diff is empty.
		tracker.onLootReceived("Goblin", 1, Collections.singletonList(
			new net.runelite.client.game.ItemStack(BONES_ID, 3)));
		engineReportsDiffLoot(Collections.<DiffLoot>emptyList());
		tracker.onTick();

		// Tick 2 (several ticks later in the real client): the player walks
		// over and picks up the same drop. No LootReceived fires this tick.
		engineReportsDiffLoot(Collections.singletonList(
			new DiffLoot(BONES_ID, "Bones", 3L, BONES_UNIT)));
		tracker.onTick();

		SourceLoot goblin = sourceNamed("Goblin");
		assertNotNull(goblin);
		assertEquals("the kill is still worth exactly 3 bones, not 6",
			3 * BONES_UNIT, goblin.getTotalValue());
		assertNull("the delayed pickup of the SAME drop must not also land as unattributed",
			sourceNamed(SessionTracker.UNATTRIBUTED_LOOT_SOURCE));
	}

	/**
	 * Round-2 bot-review Finding A: {@code outstandingReceipts} is the FIFO
	 * pool {@link #aDelayedPickupOfAnEarlierGroundDropDoesNotDoubleCountItInTheFeed}
	 * relies on to net a delayed pickup against the receipt booked several
	 * ticks earlier — but {@link SessionTracker#resetSession()} clears {@code
	 * lootBySource} (so the NPC's feed row is gone) without also clearing
	 * {@code outstandingReceipts}. If the receipt is still pending when the
	 * player hits the panel's reset button, the delayed pickup that lands in
	 * the FRESH session nets against the stale entry and is silently
	 * swallowed — the fresh session's loot feed never shows it at all.
	 */
	@Test
	public void resetSessionClearsOutstandingReceiptsSoAPostResetPickupIsNotSwallowed()
	{
		priceItem(BONES_ID, "Bones", (int) BONES_UNIT);

		// Tick 1: the kill's LootReceived fires but the pickup hasn't landed
		// yet — the receipt is parked in outstandingReceipts, still unmatched.
		tracker.onLootReceived("Goblin", 1, Collections.singletonList(
			new net.runelite.client.game.ItemStack(BONES_ID, 3)));
		engineReportsDiffLoot(Collections.<DiffLoot>emptyList());
		tracker.onTick();

		// The player resets the session (panel button) before the pickup lands.
		tracker.resetSession();

		// Now, in the FRESH session, the delayed pickup finally lands.
		engineReportsDiffLoot(Collections.singletonList(
			new DiffLoot(BONES_ID, "Bones", 3L, BONES_UNIT)));
		tracker.onTick();

		SourceLoot un = sourceNamed(SessionTracker.UNATTRIBUTED_LOOT_SOURCE);
		assertNotNull("the fresh session must see this pickup as real loot, not silently "
			+ "swallow it against a stale pre-reset receipt", un);
		assertEquals(3L, un.getItems().get(0).getQuantity());
		assertNull("the pre-reset NPC's row must not reappear either — it was cleared",
			sourceNamed("Goblin"));
	}

	/**
	 * Same finding, the other clearing site: {@link
	 * SessionTracker#bootstrapSession} (a genuine login after logout) also
	 * clears {@code lootBySource} without clearing {@code outstandingReceipts}.
	 */
	@Test
	public void reloginClearsOutstandingReceiptsSoAPostLoginPickupIsNotSwallowed()
	{
		priceItem(BONES_ID, "Bones", (int) BONES_UNIT);

		tracker.onLootReceived("Goblin", 1, Collections.singletonList(
			new net.runelite.client.game.ItemStack(BONES_ID, 3)));
		engineReportsDiffLoot(Collections.<DiffLoot>emptyList());
		tracker.onTick();

		// The player logs out and back in before the pickup lands.
		tracker.onLogout();
		tracker.onLogin();
		tracker.onTick(); // bootstraps the fresh session

		// The delayed pickup lands after the relogin.
		engineReportsDiffLoot(Collections.singletonList(
			new DiffLoot(BONES_ID, "Bones", 3L, BONES_UNIT)));
		tracker.onTick();

		SourceLoot un = sourceNamed(SessionTracker.UNATTRIBUTED_LOOT_SOURCE);
		assertNotNull("the fresh post-login session must see this pickup as real loot, "
			+ "not silently swallow it against a stale pre-login receipt", un);
		assertEquals(3L, un.getItems().get(0).getQuantity());
	}

	/**
	 * A BANK-id container change drives the eager refresh path without involving
	 * the fish-barrel inventory/equipment diff (which only handles INVENTORY /
	 * EQUIPMENT ids).
	 */
	private void eagerContainerEvent()
	{
		tracker.onItemContainerChanged(InventoryID.BANK.getId(), null);
	}

	@Test
	public void eagerContainerEventsDoNotCommitAnEconomicTransaction()
	{
		eagerContainerEvent();
		eagerContainerEvent();
		verify(engine, never()).update(any(WealthSnapshot.class), any(GeAttributions.class),
			any(MovementSignals.class), anyLong());
	}

	@Test
	public void diedSignalSurvivesMultipleEagerEventsUntilTheTickCommit()
	{
		// A death whose inventory + equipment clears arrive as several container
		// events within one tick, with the death recorded once.
		eagerContainerEvent();
		tracker.recordDeath();
		eagerContainerEvent();
		eagerContainerEvent();

		// The eager events must not have drained `died` nor advanced the baseline.
		verify(engine, never()).update(any(WealthSnapshot.class), any(GeAttributions.class),
			any(MovementSignals.class), anyLong());

		tracker.onTick();

		ArgumentCaptor<MovementSignals> signals = ArgumentCaptor.forClass(MovementSignals.class);
		verify(engine, times(1)).update(any(WealthSnapshot.class), any(GeAttributions.class),
			signals.capture(), anyLong());
		assertTrue("death survives the eager container events and commits exactly once on the tick",
			signals.getValue().diedThisTick());
	}

	/**
	 * Review finding 5: eager (non-commit) events must call {@link
	 * SessionEngine#snapshot} with {@code commit == false} — a read-only
	 * preview — so the engine's bookkeeping (start net worth / baseline /
	 * stale-bank-drop tracking) only ever advances on the tick's one
	 * authoritative {@code commit == true} call. Before the fix, {@code
	 * snapshot} had no {@code commit} parameter at all and unconditionally
	 * folded/reconciled/synced on every call, preview or not.
	 */
	@Test
	public void eagerContainerEventsPreviewSnapshotOnly_tickCommitsTheSnapshot()
	{
		eagerContainerEvent();
		eagerContainerEvent();

		verify(engine, times(2)).snapshot(any(WealthSnapshot.class), anyLong(),
			anyList(), anyList(), anyMap(), anyLong(), anyLong(), eq(false));
		verify(engine, never()).snapshot(any(WealthSnapshot.class), anyLong(),
			anyList(), anyList(), anyMap(), anyLong(), anyLong(), eq(true));

		tracker.onTick();

		verify(engine, times(1)).snapshot(any(WealthSnapshot.class), anyLong(),
			anyList(), anyList(), anyMap(), anyLong(), anyLong(), eq(true));
	}

	/**
	 * Regression: {@link SessionTracker#buildSnapshot} used to re-wrap the
	 * engine's {@link SessionSnapshot} through the backward-compatible
	 * constructor overload that defaults GE positions / Bank to {@code 0L},
	 * silently stripping the engine-computed values before
	 * {@code SessionSection} could ever read {@code getGePositions()} /
	 * {@code getBankDelta()} — the session panel's GE positions and Bank rows
	 * (and their toggles) showed 0 in the live game even though the engine
	 * unit tests, which call {@link SessionEngine#snapshot} directly and
	 * never touch this wrapping seam, kept passing. Proven here by stubbing
	 * the engine's tick-commit snapshot with known nonzero values and
	 * asserting the tracker's published snapshot still carries them.
	 */
	@Test
	public void tickCommitPreservesGePositionsAndBankFromTheEngineSnapshot()
	{
		SessionSnapshot canned = new SessionSnapshot(
			0L, 1000L, 0L, 0L, 0L, 130_000L, true,
			java.util.Collections.emptyList(), java.util.Collections.emptyMap(), 0L,
			WealthSnapshot.builder().build(),
			java.util.Collections.emptyList(), java.util.Collections.emptyList(),
			java.util.Collections.emptyList(), 0L, null, 0L,
			java.util.Collections.emptyList(), 0L,
			80_000L, 50_000L);
		// doReturn/when (not when/thenReturn) — engine is a spy, and
		// when(engine.snapshot(...)) would execute the REAL method with the
		// raw matcher placeholders as arguments before Mockito can register
		// the stub, throwing inside the real implementation.
		doReturn(canned).when(engine).snapshot(any(WealthSnapshot.class), anyLong(),
			anyList(), anyList(), anyMap(), anyLong(), anyLong(), eq(true));

		tracker.onTick();

		SessionSnapshot published = tracker.getLatest();
		assertEquals("GE positions must survive SessionTracker's snapshot wrapping",
			80_000L, published.getGePositions());
		assertEquals("Bank must survive SessionTracker's snapshot wrapping",
			50_000L, published.getBankDelta());
		assertEquals("Net worth change must survive the wrapping too",
			130_000L, published.getNetWorthDelta());
	}
}
