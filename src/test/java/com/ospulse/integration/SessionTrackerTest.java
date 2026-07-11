package com.ospulse.integration;

import com.google.gson.Gson;
import com.ospulse.OSPulseConfig;
import com.ospulse.ge.GeAttributions;
import com.ospulse.session.MovementSignals;
import com.ospulse.session.SessionEngine;
import com.ospulse.wealth.WealthSnapshot;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
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

	private Client client;
	private SessionEngine engine;
	private SessionTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		ItemManager itemManager = mock(ItemManager.class);
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
}
