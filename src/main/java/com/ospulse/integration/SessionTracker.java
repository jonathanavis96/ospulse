package com.ospulse.integration;

import com.ospulse.OSPulseConfig;
import com.ospulse.combat.BlowpipeDart;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.EquipmentStatsRepository;
import com.ospulse.combat.OffensivePrayer;
import com.ospulse.ge.GeOfferState;
import com.ospulse.ge.GeOfferView;
import com.ospulse.ge.GeReconciler;
import com.ospulse.model.ItemStack;
import com.ospulse.session.GearMapper;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.MovementSignals;
import com.ospulse.session.SessionEngine;
import com.ospulse.session.SessionListener;
import com.ospulse.session.SessionService;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.session.SourceLoot;
import com.ospulse.wealth.BankCache;
import com.ospulse.wealth.WealthSnapshot;
import com.ospulse.xp.LevelTable;
import com.ospulse.xp.VirtualLevelTable;
import com.ospulse.xp.XpSkillView;
import com.ospulse.xp.XpTracker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

import java.util.EnumSet;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wires live RuneLite game state into the pure {@code com.ospulse.session}
 * / {@code com.ospulse.ge} / {@code com.ospulse.xp} engines and publishes
 * {@link SessionSnapshot}s to registered listeners (the plugin's panel, and
 * eventually the sync layer).
 *
 * <p>All public methods here are expected to be called from the RuneLite
 * client thread (the plugin's {@code @Subscribe} handlers already run there,
 * and {@code onTick} is only ever driven from {@code onGameTick}), so
 * {@link #buildWealth(long)} reading the live {@link Client} state is safe
 * without further synchronization. Listener notification also happens on the
 * calling (client) thread; listeners that touch Swing must marshal to the
 * EDT themselves.
 */
@Slf4j
public class SessionTracker implements SessionService
{
	private static final int TOP_HOLDINGS_LIMIT = 50;
	/** RS-profile config key under which the per-account bank cache is stored. */
	private static final String BANK_CACHE_KEY = "bankCache";
	/**
	 * RS-profile config key under which the GE cost-basis buy ledger (see
	 * {@link GeReconciler#exportCostBasis()}) is stored, so a buy made in an
	 * earlier session still has its cost basis after a relog.
	 */
	private static final String GE_LEDGER_KEY = "geCostBasis";

	private final Client client;
	private final ItemManager itemManager;
	private final RuneLiteItemValuation valuation;
	private final OSPulseConfig config;
	private final ConfigManager configManager;
	private final Gson gson;

	private final SessionEngine engine;
	private final GeReconciler geReconciler = new GeReconciler();
	private final XpTracker xpTracker = new XpTracker();
	private final FishBarrelTracker fishBarrelTracker;
	private final CopyOnWriteArrayList<SessionListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Loot aggregated by source (NPC/boss/activity), fed by RuneLite's
	 * {@code LootReceived} events — the collapsible Loot-Tracker-style feed.
	 * source name -&gt; running per-item totals + how many drops were received.
	 */
	private final Map<String, SourceAgg> lootBySource = new LinkedHashMap<>();

	private static final class SourceAgg
	{
		long count;
		/**
		 * Monotonic recency stamp — the value of {@link #lootSeq} at this source's
		 * most recent drop. The feed orders sources by this descending so the most
		 * recently-updated source sits at the top (newest drops first), rather than
		 * by total value (which buried a fresh small kill under an older big one).
		 */
		long lastSeq;
		final Map<Integer, ItemStack> items = new LinkedHashMap<>();
	}

	/** Ever-increasing counter stamped onto a {@link SourceAgg} on each drop, for recency ordering. */
	private long lootSeq;

	private volatile SessionSnapshot latest;

	/**
	 * Deliberate item-movement signals (drop/destroy) accumulated from
	 * {@code MenuOptionClicked} events since the last {@link #refresh}, drained
	 * into the engine each tick. Not volatile: only ever touched on the client
	 * thread (menu-click handling and {@code onTick}/{@code refresh} both run
	 * there).
	 */
	private MovementSignals.Builder pendingSignals = MovementSignals.builder();

	/** Last bank value observed (live or restored from the persisted cache). */
	private volatile long lastKnownBankValue = 0L;
	/** Whether the bank is known — observed live this session OR restored from cache. */
	private volatile boolean bankEverSeen = false;
	/**
	 * Last-observed bank contents keyed by canonical item id. Populated live
	 * whenever the bank container is readable and restored from the per-account
	 * cache on login; used to value the bank and feed top holdings while the
	 * bank is closed, and re-priced from live GE prices on every read.
	 */
	private volatile Map<Integer, ItemStack> cachedBankItems = new LinkedHashMap<>();

	private volatile boolean loggedIn = false;
	/** True once {@link SessionEngine#startSession} has been called for the current login. */
	private volatile boolean started = false;
	/**
	 * Latest bank-open/closed state reported via {@link #onBankOpenChanged},
	 * independent of the engine's own {@code bankOpen} (which is wiped back to
	 * {@code false} by every {@link SessionEngine#startSession}). Lets
	 * {@link #resetSession()} restore bank-open mode on the fresh engine when
	 * the bank interface is still open across a reset.
	 */
	private volatile boolean lastBankOpenState = false;

	public SessionTracker(Client client, ItemManager itemManager, OSPulseConfig config,
		ConfigManager configManager, Gson gson)
	{
		this(client, itemManager, config, configManager, gson, new SessionEngine());
	}

	/** Test seam: inject the engine so its update/snapshot scheduling can be verified. */
	SessionTracker(Client client, ItemManager itemManager, OSPulseConfig config,
		ConfigManager configManager, Gson gson, SessionEngine engine)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
		this.valuation = new RuneLiteItemValuation(itemManager);
		this.configManager = configManager;
		this.gson = gson;
		this.engine = engine;
		this.fishBarrelTracker = new FishBarrelTracker(client);
		engine.setVerboseDiagnostics(config.verboseDiagnostics());
	}

	// ------------------------------------------------------------- lifecycle

	public void onLogin()
	{
		// RuneLite fires GameState.LOGGED_IN not only on a genuine login/world
		// hop but on every region/zone load (teleport, instance entry, crossing
		// a map chunk) while already logged in. Only a genuine login — i.e. a
		// transition from a logged-out state — should reset the session
		// baseline; a mere zone load must keep the running session (elapsed,
		// loot, XP and profit) intact.
		boolean wasLoggedOut = !loggedIn;
		loggedIn = true;
		if (wasLoggedOut)
		{
			started = false;
		}
	}

	public void onLogout()
	{
		loggedIn = false;
		// Persist the latest bank contents so the next login restores the bank
		// value, net worth and top holdings without reopening the bank.
		saveBankCache();
		// Persist the GE cost-basis ledger alongside the bank cache so a buy
		// made this session still has its basis after the next relog.
		saveGeLedger();
		// Keep the session/latest snapshot as-is; we simply stop producing
		// new ones until the next login.
	}

	/**
	 * Persists the current bank cache and GE cost-basis ledger. Called by the
	 * plugin on shutdown so a clean plugin toggle / client close doesn't lose
	 * the last-known bank or open GE positions.
	 */
	public void flush()
	{
		saveBankCache();
		saveGeLedger();
	}

	/**
	 * Drive the tracker forward. Must be called on the client thread (e.g.
	 * from the plugin's {@code onGameTick}); no-ops while logged out.
	 */
	public void onTick()
	{
		if (!loggedIn)
		{
			return;
		}

		long ts = System.currentTimeMillis();
		if (!started)
		{
			bootstrapSession(ts);
			return;
		}

		// Drain the barrel tracker's per-tick catch/inventory/action signals
		// exactly once per real game tick — NOT from #refresh, which also runs
		// eagerly (and potentially several times per tick) off container-change
		// events; draining there would double-count a tick's catches.
		fishBarrelTracker.onTick();
		if (config.includeFishBarrel())
		{
			// Book fish stored into the barrel this tick as storage-routed loot:
			// they auto-store directly (never touching the inventory diff and firing
			// no LootReceived), so a synthetic receipt routes them through the same
			// stored-loot ledger as a herb sack — lifting Loot AND net worth together
			// with no separate net-worth row. Added to pendingSignals here (once per
			// tick) so the eager container-change refresh never re-counts them.
			for (Map.Entry<Integer, Integer> caught : fishBarrelTracker.drainCaughtToBarrel().entrySet())
			{
				int canonicalId = valuation.canonical(caught.getKey());
				long unit = valuation.unitValue(caught.getKey());
				pendingSignals.lootReceived(
					new com.ospulse.session.LootReceipt(canonicalId, caught.getValue(), unit, true));
			}
			// Barrel emptied into the bank/deposit box this tick: tell the engine the
			// exact stored-loot entries that materialise in the bank (keyed the same
			// canonical way as the catches above), so the bank-value rise draws those
			// precise entries instead of an id-blind, qty-desyncing value heuristic.
			for (Map.Entry<Integer, Integer> emptied : fishBarrelTracker.drainEmptied().entrySet())
			{
				pendingSignals.storageEmptied(valuation.canonical(emptied.getKey()), emptied.getValue());
			}
		}
		// Authoritative once-per-tick commit: drains this tick's signals + advances
		// the baseline exactly once, however many eager events fired in between.
		refresh(ts, true);
	}

	public void onItemContainerChanged(int containerId, ItemContainer container)
	{
		if (!loggedIn || !started)
		{
			return;
		}
		fishBarrelTracker.onItemContainerChanged(containerId, container);
		// Preview eagerly on container changes (inventory/equipment/bank) so the
		// panel feels responsive rather than waiting up to a full game tick — but
		// do NOT commit: onTick books the tick's single economic transaction with
		// all its signals intact (see #refresh). Committing here fragmented a
		// multi-event tick (die+reclaim, search-all nests) into mis-attributed
		// transactions.
		previewRefresh(System.currentTimeMillis());
	}

	public void onGrandExchangeOfferChanged(int slot, GrandExchangeOffer offer)
	{
		if (offer == null)
		{
			return;
		}

		GeOfferState mapped = GeOfferStateMapper.map(offer.getState());
		String itemName = mapped == GeOfferState.EMPTY ? "" : valuation.name(offer.getItemId());

		geReconciler.onOfferUpdate(
			slot,
			mapped,
			offer.getItemId(),
			itemName,
			offer.getTotalQuantity(),
			offer.getQuantitySold(),
			offer.getSpent(),
			offer.getPrice(),
			System.currentTimeMillis());
	}

	/**
	 * Records a batch of loot received from a named source (NPC kill, event,
	 * pickpocket, ...), aggregating it under that source for the collapsible
	 * loot feed. {@code items} are RuneLite game item stacks (id + quantity).
	 * Priced via the same valuation as everything else. Publishes an updated
	 * snapshot so the panel reflects the drop immediately.
	 */
	public void onLootReceived(String source, int amount, Collection<net.runelite.client.game.ItemStack> items)
	{
		if (!loggedIn || !started || items == null)
		{
			return;
		}

		String key = (source == null || source.isEmpty()) ? "Unknown" : source;
		SourceAgg agg = lootBySource.computeIfAbsent(key, k -> new SourceAgg());
		agg.count += Math.max(1, amount);
		agg.lastSeq = ++lootSeq;

		for (net.runelite.client.game.ItemStack it : items)
		{
			if (it == null || it.getId() <= 0 || it.getQuantity() <= 0)
			{
				continue;
			}
			int canonicalId = valuation.canonical(it.getId());
			long unit = valuation.unitValue(it.getId());
			String name = valuation.name(it.getId());
			mergeItem(agg.items, canonicalId, name, it.getQuantity(), unit);
			pendingSignals.lootReceived(new com.ospulse.session.LootReceipt(canonicalId, it.getQuantity(), unit));
		}

		// Accumulate the receipt now, preview immediately, but let onTick commit it
		// with the tick's full net inventory delta (see #refresh). Draining it on
		// this eager event snapped the baseline forward and could swallow a
		// same-tick follow-up (e.g. a second bird-nest search) that had not yet
		// materialised in the inventory diff.
		previewRefresh(System.currentTimeMillis());
	}

	/**
	 * Records a "Drop" menu click on an inventory item, accumulated into the
	 * current tick's {@link MovementSignals} and drained on the next
	 * {@link #refresh}. See {@code com.ospulse.OSPulsePlugin#onMenuOptionClicked}.
	 */
	public void recordDrop(int itemId)
	{
		pendingSignals.dropped(itemId);
	}

	/**
	 * Records a "Destroy" menu click on an inventory item. See
	 * {@code com.ospulse.OSPulsePlugin#onMenuOptionClicked} for the
	 * confirm-dialog timing caveat.
	 */
	public void recordDestroy(int itemId)
	{
		pendingSignals.destroyed(itemId);
	}

	/**
	 * Records the local player's death, accumulated into the current tick's
	 * {@link MovementSignals} and drained on the next {@link #refresh}. See
	 * {@code com.ospulse.OSPulsePlugin#onActorDeath}.
	 */
	public void recordDeath()
	{
		pendingSignals.died(true);
	}

	public void onStatChanged(Skill skill, int xp)
	{
		if (skill == null || skill == Skill.OVERALL)
		{
			return;
		}
		xpTracker.update(skill.name(), xp);
		if (skill == Skill.COOKING && xp > 0)
		{
			// Each Cooking StatChanged fire (real xp gain) may correspond to an
			// infernal harpoon auto-cooking a fish before it reaches the barrel —
			// see FishBarrelTracker#onTick for how this offsets credited catches.
			fishBarrelTracker.onCookingXpGained();
		}
	}

	/**
	 * Forwards a chat message to the fish-barrel tracker. See
	 * {@code com.ospulse.OSPulsePlugin#onChatMessage}.
	 */
	public void onChatMessage(net.runelite.api.ChatMessageType type, String message)
	{
		fishBarrelTracker.onChatMessage(type, message);
	}

	/**
	 * Forwards the fish barrel's "Check" interface load so the tracker can
	 * resynchronise its exact contents. See
	 * {@code com.ospulse.OSPulsePlugin#onWidgetLoaded}.
	 */
	public void onWidgetLoaded(int groupId)
	{
		fishBarrelTracker.onWidgetLoaded(groupId);
	}

	/**
	 * Forwards a "Fill"/"Empty"/"Empty to bank"/"Check" menu click on the fish
	 * barrel item, so the tracker can correlate the action with the
	 * container/widget change that follows within a few ticks. See
	 * {@code com.ospulse.OSPulsePlugin#onMenuOptionClicked}.
	 */
	public void onBarrelMenuAction(int itemId, String menuOption)
	{
		fishBarrelTracker.onBarrelMenuAction(itemId, menuOption);
	}

	public void onBankOpenChanged(boolean open)
	{
		lastBankOpenState = open;

		if (!loggedIn || !started)
		{
			return;
		}

		long ts = System.currentTimeMillis();
		WealthSnapshot current = buildWealth(ts);
		engine.setBankOpen(open, current, ts);
		if (!open)
		{
			// Bank just closed: its final contents are captured in cachedBankItems
			// by buildWealth above — persist them for the next login, along with
			// the GE cost-basis ledger.
			saveBankCache();
			saveGeLedger();
		}
		publish(buildSnapshot(current, ts, true));
	}

	/**
	 * Restarts the session baseline from current wealth (panel reset button).
	 *
	 * <p>If the bank interface is currently open, {@link SessionEngine#startSession}
	 * would otherwise wipe the fresh engine back to bank-closed, so any
	 * inventory/bank transfer made after the reset but before the next
	 * open/close event would be mis-read as loot. Re-apply the last-known
	 * bank-open state (tracked via {@link #onBankOpenChanged}) through the
	 * engine's own {@link SessionEngine#setBankOpen} right after the restart,
	 * anchored to the same freshly-built {@code current} snapshot — which
	 * already reflects the live bank container, since it actually is open —
	 * so post-reset transfers net out correctly from the first tick.
	 */
	public void resetSession()
	{
		if (!loggedIn)
		{
			return;
		}

		long ts = System.currentTimeMillis();
		xpTracker.start(captureXpBaseline());
		lootBySource.clear();
		geReconciler.reset();
		// Restore the GE cost-basis ledger immediately after the reset, mirroring
		// bootstrapSession — a manual "reset session" must not wipe the cost basis
		// of GE buys still open, or the next save (logout/flush/bank-close) exports
		// the emptied ledger over the good one and a later sale credits zero flip
		// P&L instead of sale-minus-basis-minus-tax.
		loadGeLedger();
		// Drop any signals accumulated in the tick preceding this reset so they
		// don't leak into the fresh session's first #refresh.
		pendingSignals = MovementSignals.builder();
		primeGeOffers();
		WealthSnapshot current = buildWealth(ts);
		engine.startSession(current, ts);
		if (lastBankOpenState)
		{
			engine.setBankOpen(true, current, ts);
		}
		started = true;
		publish(buildSnapshot(current, ts, true));
	}

	// ------------------------------------------------------------- SessionService

	@Override
	public SessionSnapshot getLatest()
	{
		return latest;
	}

	@Override
	public void addListener(SessionListener listener)
	{
		if (listener != null)
		{
			listeners.add(listener);
		}
	}

	@Override
	public void removeListener(SessionListener listener)
	{
		listeners.remove(listener);
	}

	// ------------------------------------------------------------- internals

	private void bootstrapSession(long ts)
	{
		loadBankCache();
		xpTracker.start(captureXpBaseline());
		lootBySource.clear();
		geReconciler.reset();
		// Restore the GE cost-basis ledger immediately after the reset, so a buy
		// made in an earlier session (before this relog/login) still has its
		// cost basis when it's eventually sold — otherwise the reset above would
		// wipe it and a weeks-old buy sold after this relog would credit zero
		// flip P&L instead of the correct sale-minus-basis-minus-tax profit.
		loadGeLedger();
		// A genuine login (not a mere zone hop, and not the panel's manual
		// session-value reset — see resetSession()'s javadoc) is the only point
		// the barrel's inferred contents should be wiped: unlike bank/pouches,
		// there's no server-side state to re-derive them from afterwards, so
		// starting "unknown" is safer than carrying over a stale guess from a
		// different login. Prime its inventory/equipment shadow copies from the
		// live containers immediately after, so the first onItemContainerChanged
		// diff is against real contents rather than empty maps.
		fishBarrelTracker.reset();
		fishBarrelTracker.primeContainers();
		// Start the fresh session with an empty signal builder (see #refresh).
		pendingSignals = MovementSignals.builder();
		primeGeOffers();
		WealthSnapshot initial = buildWealth(ts);
		engine.startSession(initial, ts);
		started = true;
		publish(buildSnapshot(initial, ts, true));
	}

	/**
	 * Recompute + publish the panel snapshot for responsiveness WITHOUT advancing
	 * the engine baseline or draining movement signals — used on eager
	 * container-change / loot events that can fire several times within one game
	 * tick.
	 */
	private void previewRefresh(long ts)
	{
		refresh(ts, false);
	}

	/**
	 * Drive the engine forward.
	 *
	 * <p>Only the authoritative per-tick call ({@code commit == true}, from
	 * {@link #onTick()}) drains the tick's accumulated {@link MovementSignals} and
	 * advances the engine baseline — i.e. books exactly ONE economic transaction
	 * per game tick. Eager container/loot events ({@code commit == false}) only
	 * recompute a read-only preview snapshot; they must not commit, because a
	 * single tick's inventory effects can span several {@code ItemContainerChanged}
	 * events (die-then-reclaim, search-all bird nests, drop/destroy across ticks).
	 * Committing per-event fragmented one transaction into several, draining the
	 * {@code died}/drop signals on the first event and snapping the baseline
	 * forward so later events (e.g. reclaimed gear) mis-booked as fresh loot. This
	 * mirrors the barrel-signal discipline already documented in {@link #onTick()}.
	 */
	private void refresh(long ts, boolean commit)
	{
		WealthSnapshot current = buildWealth(ts);
		if (commit)
		{
			// Let overdue GE attribution expectations lapse before the engine
			// consumes from the ledger; the ledger itself persists across ticks
			// so a collection landing seconds (or minutes) after its offer
			// filled is still attributed to the GE instead of booking as loot.
			geReconciler.expireAttributions(ts);
			// Keep the engine's verbose-diagnostics flag current so the config
			// toggle takes effect live (no restart) for the per-update log.
			engine.setVerboseDiagnostics(config.verboseDiagnostics());
			// Drain this tick's accumulated Drop/Destroy/Death signals (see
			// #recordDrop / #recordDestroy / #recordDeath) into the engine, then
			// reset the builder so the next tick starts empty.
			MovementSignals signals = pendingSignals.build();
			pendingSignals = MovementSignals.builder();
			engine.update(current, geReconciler, signals, ts);
		}
		publish(buildSnapshot(current, ts, commit));
	}

	/**
	 * Seeds the GE reconciler's per-slot incremental tracking from the live
	 * offers at session start (fresh login, world hop, manual reset), so the
	 * already-transacted portion of a pre-session offer is never re-counted
	 * as new session movement. Without this, the first offer event after a
	 * reset reads the offer's whole cumulative fill as a fresh increment —
	 * resurrecting pre-session flip P&amp;L and planting phantom collection
	 * expectations that could swallow genuine loot. MUST be called on the
	 * client thread, right after {@link GeReconciler#reset()}.
	 */
	private void primeGeOffers()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}
		for (int slot = 0; slot < offers.length; slot++)
		{
			GrandExchangeOffer offer = offers[slot];
			if (offer == null)
			{
				continue;
			}
			geReconciler.primeCollectable(slot, GeOfferStateMapper.map(offer.getState()),
				offer.getItemId(), offer.getQuantitySold(), offer.getSpent(), offer.getPrice());
		}
	}

	/**
	 * Builds the published snapshot: the engine's wealth/loot view decorated
	 * with the per-skill XP progress views and overall XP rate computed from
	 * the {@link XpTracker} and the {@link LevelTable}.
	 *
	 * <p>Review finding 5: {@code commit} is forwarded to {@link
	 * SessionEngine#snapshot(WealthSnapshot, long, List, List, Map, long, long, boolean)}
	 * unchanged — {@code false} (an eager, non-committing preview — see
	 * {@link #previewRefresh}) must not let the engine advance its
	 * bookkeeping (start net worth / baseline / stale-bank-drop tracking /
	 * diagnostic log baseline) ahead of the tick's one authoritative commit.
	 */
	private SessionSnapshot buildSnapshot(WealthSnapshot current, long ts, boolean commit)
	{
		SessionSnapshot base = engine.snapshot(current, geReconciler.realizedPnl(),
			buildGeOffers(), buildLootSources(), xpTracker.gained(), xpTracker.totalGained(), ts, commit);

		long elapsedMs = base.getElapsedMs();
		long overallXpPerHour = elapsedMs > 0
			? base.getXpTotal() * 3_600_000L / elapsedMs
			: 0L;

		return new SessionSnapshot(
			base.getStartMs(),
			elapsedMs,
			base.getLootValue(),
			base.getProfitPerHour(),
			base.getGeRealizedPnl(),
			base.getNetWorthDelta(),
			base.isBankKnown(),
			base.getLoot(),
			base.getXpGained(),
			base.getXpTotal(),
			base.getWealth(),
			base.getGeOffers(),
			base.getLootSources(),
			buildXpSkillViews(base.getXpGained(), elapsedMs),
			overallXpPerHour,
			buildGear(),
			base.getUnrealizedPnl(),
			base.getHoldingPnls(),
			base.getSuppliesUsed());
	}

	/**
	 * Builds a fresh {@link GearSnapshot} from the live client state: worn
	 * equipment ids (by {@link EquipmentInventorySlot} ordinal — the
	 * EQUIPMENT container's slot index matches the ordinal), boosted+base
	 * combat levels, and active offensive prayers. MUST be called on the
	 * client thread (mirrors {@link #buildWealth(long)}).
	 *
	 * <p>Reuses the existing EQUIPMENT container read ({@link #buildWealth}
	 * already reads it for gp value) but keeps per-slot item ids instead of
	 * just summing value.
	 */
	private GearSnapshot buildGear()
	{
		int[] equippedItemIds = new int[EquipmentInventorySlot.values().length];
		java.util.Arrays.fill(equippedItemIds, -1);

		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment != null)
		{
			Item[] items = equipment.getItems();
			for (int slot = 0; slot < items.length && slot < equippedItemIds.length; slot++)
			{
				Item item = items[slot];
				if (item != null && item.getId() > 0 && item.getQuantity() > 0)
				{
					equippedItemIds[slot] = item.getId();
				}
			}
		}

		Set<OffensivePrayer> activePrayers = EnumSet.noneOf(OffensivePrayer.class);
		for (OffensivePrayer prayer : OffensivePrayer.values())
		{
			// Names are shared 1:1 between com.ospulse.combat.OffensivePrayer and
			// net.runelite.api.Prayer for every offensive prayer we model (verified
			// against the resolved 1.12.31.1 client jar).
			try
			{
				Prayer runeLitePrayer = Prayer.valueOf(prayer.name());
				if (client.isPrayerActive(runeLitePrayer))
				{
					activePrayers.add(prayer);
				}
			}
			catch (IllegalArgumentException e)
			{
				// No matching RuneLite prayer for this name - skip.
			}
		}

		GearMapper.SlotStatsLookup lookup = this::lookupSlotStats;
		EquipmentStats equipmentStats = GearMapper.buildEquipmentStats(
			equippedItemIds, EquipmentInventorySlot.WEAPON.ordinal(), lookup,
			BlowpipeDart.fromConfig(configManager).rangedStrength());

		// The player's set autocast spell, as the game's internal autocast id
		// (VarbitID.AUTOCAST_SPELL, 276). We don't yet have the value->Spell
		// mapping (it's cache data), so capture the raw value on the snapshot and
		// log it: an in-client pass that autocasts each spell reveals the mapping,
		// which then lets the magic readout show the actual current cast instead
		// of the next-best-DPS fallback (GearSection secondary readout TODO).
		int autocastSpellId = client.getVarbitValue(net.runelite.api.gameval.VarbitID.AUTOCAST_SPELL);
		if (log.isDebugEnabled())
		{
			log.debug("[autocast] varbit276={} spellbook={} weaponId={}",
				autocastSpellId,
				client.getVarbitValue(net.runelite.api.gameval.VarbitID.SPELLBOOK),
				equippedItemIds[EquipmentInventorySlot.WEAPON.ordinal()]);
		}

		return GearSnapshot.builder()
			.equippedItemIds(equippedItemIds)
			.attack(client.getRealSkillLevel(Skill.ATTACK), client.getBoostedSkillLevel(Skill.ATTACK))
			.strength(client.getRealSkillLevel(Skill.STRENGTH), client.getBoostedSkillLevel(Skill.STRENGTH))
			.defence(client.getRealSkillLevel(Skill.DEFENCE), client.getBoostedSkillLevel(Skill.DEFENCE))
			.ranged(client.getRealSkillLevel(Skill.RANGED), client.getBoostedSkillLevel(Skill.RANGED))
			.magic(client.getRealSkillLevel(Skill.MAGIC), client.getBoostedSkillLevel(Skill.MAGIC))
			.prayer(client.getRealSkillLevel(Skill.PRAYER), client.getBoostedSkillLevel(Skill.PRAYER))
			.hitpoints(client.getRealSkillLevel(Skill.HITPOINTS), client.getBoostedSkillLevel(Skill.HITPOINTS))
			.slayer(client.getRealSkillLevel(Skill.SLAYER), client.getBoostedSkillLevel(Skill.SLAYER))
			.agility(client.getRealSkillLevel(Skill.AGILITY), client.getBoostedSkillLevel(Skill.AGILITY))
			.activePrayers(activePrayers)
			// TODO Phase 2+: on-task Slayer detection has no confirmed live read yet.
			.onSlayerTask(false)
			.equipmentStats(equipmentStats)
			.autocastSpellId(autocastSpellId)
			.build();
	}

	/**
	 * Real {@link ItemManager}-backed lookup for {@link GearMapper#buildEquipmentStats}.
	 * MUST be called on the client thread — {@link ItemManager#getItemStats(int)}
	 * internally calls {@code getItemComposition}, which asserts the client
	 * thread and throws if invoked from the EDT (this is exactly what {@link
	 * #buildGear()} exists to avoid: it runs here, on the client thread, once
	 * per tick, so the UI layer never needs to call this itself).
	 */
	private GearMapper.SlotStats lookupSlotStats(int itemId)
	{
		if (itemManager == null || itemId <= 0)
		{
			return null;
		}

		// Numeric bonuses come from the bundled clean-room cache data
		// ({@link EquipmentStatsRepository}) — licence-free and shareable with a
		// future non-RuneLite build. RuneLite's wiki-derived getItemStats() is
		// retained only for the two-handed flag (not cache-derivable) and as the
		// fallback for the rare item absent from the cache data.
		EquipmentStatsRepository.Stats cache = EquipmentStatsRepository.getInstance().statsFor(itemId);

		ItemStats stats = itemManager.getItemStats(itemId);
		ItemEquipmentStats eq = (stats != null && stats.isEquipable()) ? stats.getEquipment() : null;

		if (cache != null)
		{
			boolean isTwoHanded = eq != null && eq.isTwoHanded();
			return new GearMapper.SlotStats(
				cache.astab(), cache.aslash(), cache.acrush(), cache.amagic(), cache.arange(),
				cache.dstab(), cache.dslash(), cache.dcrush(), cache.dmagic(), cache.drange(),
				cache.str(), cache.rstr(), cache.mdmg(), cache.prayer(), cache.aspeed(), isTwoHanded);
		}

		// Cache miss — fall back to RuneLite's wiki-derived numbers (unchanged behaviour).
		if (eq == null)
		{
			return null;
		}
		return new GearMapper.SlotStats(
			eq.getAstab(), eq.getAslash(), eq.getAcrush(), eq.getAmagic(), eq.getArange(),
			eq.getDstab(), eq.getDslash(), eq.getDcrush(), eq.getDmagic(), eq.getDrange(),
			eq.getStr(), eq.getRstr(), eq.getMdmg(), eq.getPrayer(), eq.getAspeed(), eq.isTwoHanded());
	}

	/**
	 * Computes the RuneLite-xptracker-style per-skill views for every skill
	 * with a positive gain this session, ordered by XP gained descending.
	 * Actions-left uses the XP of the most recently observed gain event as the
	 * per-action size, so it reads -1 (unknown) until the first action of the
	 * session has been seen for that skill.
	 *
	 * <p>Once a skill reaches real level 99, progress keeps going via {@link
	 * VirtualLevelTable}'s virtual levels (100..126) instead of flatlining at
	 * 99, matching RuneLite's own tracker.
	 */
	private List<XpSkillView> buildXpSkillViews(Map<String, Long> gained, long elapsedMs)
	{
		List<XpSkillView> views = new ArrayList<>();
		for (Map.Entry<String, Long> entry : gained.entrySet())
		{
			String skill = entry.getKey();
			long skillGained = entry.getValue();
			long xpPerHour = elapsedMs > 0 ? skillGained * 3_600_000L / elapsedMs : 0L;
			long currentXp = xpTracker.currentXp(skill);
			int level = LevelTable.levelForXp(currentXp);

			long xpLeft;
			long actionsLeft;
			double progress;
			if (level >= LevelTable.MAX_LEVEL)
			{
				// Past 99: keep climbing via virtual levels, up to the 126 cap.
				level = VirtualLevelTable.levelForXp(currentXp);
				if (level >= VirtualLevelTable.MAX_LEVEL)
				{
					xpLeft = 0L;
					actionsLeft = -1L;
					progress = 1.0;
				}
				else
				{
					long levelFloor = VirtualLevelTable.xpForLevel(level);
					long levelCeiling = VirtualLevelTable.xpForLevel(level + 1);
					xpLeft = levelCeiling - currentXp;
					progress = (double) (currentXp - levelFloor) / (levelCeiling - levelFloor);
					long lastActionXp = xpTracker.lastActionXp(skill);
					actionsLeft = lastActionXp > 0
						? (xpLeft + lastActionXp - 1) / lastActionXp
						: -1L;
				}
			}
			else
			{
				long levelFloor = LevelTable.xpForLevel(level);
				long levelCeiling = LevelTable.xpForLevel(level + 1);
				xpLeft = levelCeiling - currentXp;
				progress = (double) (currentXp - levelFloor) / (levelCeiling - levelFloor);
				long lastActionXp = xpTracker.lastActionXp(skill);
				actionsLeft = lastActionXp > 0
					? (xpLeft + lastActionXp - 1) / lastActionXp
					: -1L;
			}

			views.add(new XpSkillView(skill, skillGained, xpPerHour, currentXp,
				level, xpLeft, actionsLeft, progress));
		}
		views.sort((a, b) -> Long.compare(b.getGained(), a.getGained()));
		return views;
	}

	private void publish(SessionSnapshot snapshot)
	{
		this.latest = snapshot;
		for (SessionListener listener : listeners)
		{
			listener.onSessionUpdate(snapshot);
		}
	}

	private Map<String, Long> captureXpBaseline()
	{
		Map<String, Long> baseline = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			// OVERALL is a pseudo-skill (sum of all others); it has no
			// meaningful independent xp reading for baselining.
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			baseline.put(skill.name(), (long) client.getSkillExperience(skill));
		}
		return baseline;
	}

	/**
	 * Builds a fresh {@link WealthSnapshot} from the live client state.
	 * MUST be called on the client thread.
	 */
	private WealthSnapshot buildWealth(long tsMs)
	{
		Map<Integer, ItemStack> tracked = new LinkedHashMap<>();
		Map<Integer, ItemStack> allHoldings = new LinkedHashMap<>();

		long inventoryValue = accumulateContainer(
			client.getItemContainer(InventoryID.INVENTORY), tracked, allHoldings, null);
		long equipmentValue = accumulateContainer(
			client.getItemContainer(InventoryID.EQUIPMENT), tracked, allHoldings, null);

		// The bank container is only readable while the bank is open. When it is,
		// capture its contents (for the panel AND the per-account cache); when it
		// isn't, fall back to the cached contents so the bank value and top
		// holdings survive relogs without the player reopening the bank. Bank
		// contents feed topHoldings only, never trackedItems.
		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		long bankValue;
		if (bankContainer != null)
		{
			Map<Integer, ItemStack> liveBank = new LinkedHashMap<>();
			bankValue = accumulateContainer(bankContainer, null, allHoldings, liveBank);
			cachedBankItems = liveBank;
			lastKnownBankValue = bankValue;
			bankEverSeen = true;
		}
		else if (!cachedBankItems.isEmpty())
		{
			// Re-price the cached bank from live GE prices and fold it into the
			// top holdings so the panel shows the bank even while it's closed.
			long total = 0L;
			for (ItemStack stack : cachedBankItems.values())
			{
				long unit = valuation.unitValue(stack.getId());
				total += unit * stack.getQuantity();
				mergeItem(allHoldings, stack.getId(), stack.getName(), stack.getQuantity(), unit);
			}
			bankValue = total;
			lastKnownBankValue = total;
			bankEverSeen = true;
		}
		else
		{
			bankValue = lastKnownBankValue;
		}

		long geValue = accumulateGrandExchange();
		// Sale proceeds / bought goods sitting in the GE collection box, awaiting
		// collection. Folded into tracked wealth so net worth doesn't dip the
		// instant a sell fills and only recover when the coins are collected (the
		// still-locked geValue drops to 0 on a full fill; this replaces it until
		// the goods land in the inventory). See GeReconciler#collectableValue.
		long geCollectableValue = geReconciler.collectableValue(valuation::unitValue);

		long pouchValue = 0L;
		if (config.includePouches())
		{
			pouchValue = accumulateRunePouch(tracked, allHoldings);
			pouchValue += accumulateEssencePouches(tracked, allHoldings);
		}
		// TODO pouch valuation: looting bag contents are not reliably
		// readable client-side and are intentionally skipped.
		//
		// Fish-barrel contents are deliberately NOT folded into tracked wealth
		// here: they are booked as storage-routed loot in #onTick (see
		// FishBarrelTracker#drainCaughtToBarrel), so the engine's stored-loot
		// ledger lifts both Loot and net worth without a separate net-worth row.

		List<ItemStack> topHoldings = new ArrayList<>(allHoldings.values());
		topHoldings.sort((a, b) -> Long.compare(b.value(), a.value()));
		if (topHoldings.size() > TOP_HOLDINGS_LIMIT)
		{
			topHoldings = topHoldings.subList(0, TOP_HOLDINGS_LIMIT);
		}

		return WealthSnapshot.builder()
			.inventoryValue(inventoryValue)
			.equipmentValue(equipmentValue)
			.geInFlightValue(geValue)
			.geCollectableValue(geCollectableValue)
			.pouchValue(pouchValue)
			.bankValue(bankValue)
			.bankKnown(bankEverSeen)
			.timestampMs(tsMs)
			.topHoldings(topHoldings)
			.trackedItems(tracked)
			// The FULL owned-item map (inventory + equipment + bank + pouches),
			// untruncated — the gear optimiser's membership-based ownership
			// source (a 0-value untradeable in the bank never makes the top-50
			// cut but is still owned).
			.allHoldings(allHoldings)
			.build();
	}

	/**
	 * Sums the value of a container's items, merging each into
	 * {@code allHoldings} (and {@code trackedOrNull}, if non-null) keyed by
	 * canonical item id.
	 */
	private long accumulateContainer(
		ItemContainer container, Map<Integer, ItemStack> trackedOrNull,
		Map<Integer, ItemStack> allHoldings, Map<Integer, ItemStack> captureOrNull)
	{
		if (container == null)
		{
			return 0L;
		}

		long total = 0L;
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}

			int itemId = item.getId();
			if (valuation.isPlaceholder(itemId))
			{
				continue; // bank placeholder: reserved empty slot, not actually owned
			}
			long qty = item.getQuantity();
			int canonicalId = valuation.canonical(itemId);
			long unit = valuation.unitValue(itemId);
			String name = valuation.name(itemId);

			total += unit * qty;
			if (trackedOrNull != null)
			{
				mergeItem(trackedOrNull, canonicalId, name, qty, unit);
			}
			if (captureOrNull != null)
			{
				mergeItem(captureOrNull, canonicalId, name, qty, unit);
			}
			mergeItem(allHoldings, canonicalId, name, qty, unit);
		}
		return total;
	}

	/**
	 * Values active Grand Exchange offers by the wealth still <em>locked in</em>
	 * the exchange (see {@link GeValuation} for the conservative rule) and folds
	 * the still-locked sell items into {@code allHoldings}. GE items are never
	 * added to trackedItems (loot diffing excludes them;
	 * {@link GeReconciler#drainAttributedItemIds()} handles that instead).
	 *
	 * <p>Only the not-yet-filled portion of an offer is counted, never the
	 * collectable side (bought items / sale proceeds), because a partial collect
	 * is invisible to us and counting it would double-count anything already
	 * pulled back into the inventory. Consequently a fully-bought/-sold or
	 * cancelled offer contributes 0 here: its value is sitting in the collection
	 * box and is picked up again as soon as the player collects it.
	 *
	 * <p>GE offers are shown in their own "Grand Exchange" breakdown (see
	 * {@link #buildGeOffers()}) and are deliberately NOT folded into the top
	 * holdings, so holdings reflects only inventory/equipment/bank/pouch items.
	 */
	private long accumulateGrandExchange()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return 0L;
		}

		long total = 0L;
		for (GrandExchangeOffer offer : offers)
		{
			if (offer == null)
			{
				continue;
			}

			GeOfferState state = GeOfferStateMapper.map(offer.getState());
			if (state == GeOfferState.EMPTY)
			{
				continue;
			}

			int itemId = offer.getItemId();
			long unit = valuation.unitValue(itemId);

			switch (state)
			{
				case BUYING:
				case BOUGHT:
					// Only the coins escrowed for the not-yet-bought quantity are
					// still locked; bought items are collectable.
					total += GeValuation.buyOfferValue(
						offer.getPrice(), offer.getTotalQuantity(), offer.getQuantitySold());
					break;
				case SELLING:
				case SOLD:
					// The unsold items are still locked in the exchange; proceeds
					// from the sold portion are collectable and not counted.
					total += GeValuation.sellOfferValue(
						unit, offer.getTotalQuantity(), offer.getQuantitySold());
					break;
				case CANCELLED_BUY:
				case CANCELLED_SELL:
				default:
					// Nothing is locked any more: the returned coins / unsold items
					// sit in the collection box awaiting collection, at which point
					// they reappear in the inventory. Counting them here would risk
					// double-counting an already-collected amount.
					break;
			}
		}
		return total;
	}

	/**
	 * Builds the per-slot Grand Exchange breakdown shown in the panel: every
	 * non-empty offer, with its buy/sell direction, quantity progress and gp
	 * progress (gp moved so far out of the gp a full fill would move). Purely a
	 * display view — it does not affect wealth or loot maths. MUST be called on
	 * the client thread.
	 */
	private List<GeOfferView> buildGeOffers()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return List.of();
		}

		List<GeOfferView> views = new ArrayList<>();
		for (GrandExchangeOffer offer : offers)
		{
			if (offer == null)
			{
				continue;
			}

			GeOfferState state = GeOfferStateMapper.map(offer.getState());
			if (state == GeOfferState.EMPTY)
			{
				continue;
			}

			boolean buying = state == GeOfferState.BUYING
				|| state == GeOfferState.BOUGHT
				|| state == GeOfferState.CANCELLED_BUY;

			long price = offer.getPrice();
			long totalQty = offer.getTotalQuantity();
			long transacted = offer.getQuantitySold();

			views.add(new GeOfferView(
				buying,
				offer.getItemId(),
				valuation.name(offer.getItemId()),
				totalQty,
				transacted,
				price,
				offer.getSpent(),
				price * totalQty));
		}
		return views;
	}

	/**
	 * Rune pouch valuation across all six type/amount varbit slots (covers the
	 * regular, divine and expanded pouches; unused slots read 0 and are skipped).
	 *
	 * <p>Critically, the {@code RUNE_POUCH_RUNE*} varbits do NOT hold an item id —
	 * they hold a KEY into the {@link EnumID#RUNEPOUCH_RUNE} game enum, which maps
	 * to the actual rune item id. Treating the raw varbit value as an item id
	 * (as an earlier version did) mis-identifies and mis-prices the rune, which
	 * is how a handful of runes turned into a multi-billion "pouch" holding. This
	 * mirrors RuneLite's own {@code RunepouchOverlay}.
	 */
	private long accumulateRunePouch(Map<Integer, ItemStack> tracked, Map<Integer, ItemStack> allHoldings)
	{
		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runepouchEnum == null)
		{
			return 0L;
		}

		int[] runeVarbits = {
			Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3,
			Varbits.RUNE_POUCH_RUNE4, Varbits.RUNE_POUCH_RUNE5, Varbits.RUNE_POUCH_RUNE6,
		};
		int[] amountVarbits = {
			Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3,
			Varbits.RUNE_POUCH_AMOUNT4, Varbits.RUNE_POUCH_AMOUNT5, Varbits.RUNE_POUCH_AMOUNT6,
		};

		long total = 0L;
		for (int i = 0; i < runeVarbits.length; i++)
		{
			int runeKey = client.getVarbitValue(runeVarbits[i]);
			int amount = client.getVarbitValue(amountVarbits[i]);
			if (runeKey <= 0 || amount <= 0)
			{
				continue;
			}

			int itemId = runepouchEnum.getIntValue(runeKey);
			if (itemId <= 0)
			{
				continue;
			}

			int canonicalId = valuation.canonical(itemId);
			long unit = valuation.unitValue(itemId);
			String name = valuation.name(itemId);

			total += unit * amount;
			mergeItem(tracked, canonicalId, name, amount, unit);
			mergeItem(allHoldings, canonicalId, name, amount, unit);
		}
		return total;
	}

	/**
	 * Essence-pouch capacities (small/medium/large/giant/colossal), each keyed to
	 * its held-amount varbit. There is no per-slot rune/type varbit to resolve
	 * here (unlike the rune pouch) — a pouch just holds a count of essence.
	 */
	private enum EssencePouch
	{
		SMALL(Varbits.ESSENCE_POUCH_SMALL_AMOUNT),
		MEDIUM(Varbits.ESSENCE_POUCH_MEDIUM_AMOUNT),
		LARGE(Varbits.ESSENCE_POUCH_LARGE_AMOUNT),
		GIANT(Varbits.ESSENCE_POUCH_GIANT_AMOUNT),
		COLOSSAL(Varbits.ESSENCE_POUCH_COLOSSAL_AMOUNT);

		private final int amountVarbit;

		EssencePouch(int amountVarbit)
		{
			this.amountVarbit = amountVarbit;
		}

		int amountVarbit()
		{
			return amountVarbit;
		}
	}

	/**
	 * Essence-pouch contents are readable client-side via the pouch amount varbits
	 * (small/medium/large/giant/colossal), so their held essence is folded into
	 * tracked wealth rather than being invisible until emptied. Mirrors
	 * {@link #accumulateRunePouch}. Returns the total essence value added.
	 *
	 * <p><b>Limitation:</b> pouches don't expose a per-slot type varbit the way
	 * the rune pouch does, and the essence-type encoding (pure vs regular vs
	 * daeyalt) is not currently confirmed in-client, so all pouch contents are
	 * priced as pure essence ({@link ItemID#BLANKRUNE_HIGH}) regardless of what
	 * is actually stored. This keeps the common case (pure essence, which is
	 * what most players store and what nearly all pouches hold post-Priff)
	 * correct and empty-neutral; a pouch filled with regular or daeyalt essence
	 * may not net perfectly neutral on empty until the type varbit is confirmed
	 * and this is refined to price per-slot like the rune pouch does.
	 */
	private long accumulateEssencePouches(Map<Integer, ItemStack> tracked, Map<Integer, ItemStack> allHoldings)
	{
		long total = 0L;
		for (EssencePouch pouch : EssencePouch.values())
		{
			int amount = client.getVarbitValue(pouch.amountVarbit());
			if (amount <= 0)
			{
				continue;
			}

			int canonicalId = valuation.canonical(ItemID.BLANKRUNE_HIGH);
			long unit = valuation.unitValue(ItemID.BLANKRUNE_HIGH);
			String name = valuation.name(ItemID.BLANKRUNE_HIGH);

			total += unit * amount;
			mergeItem(tracked, canonicalId, name, amount, unit);
			mergeItem(allHoldings, canonicalId, name, amount, unit);
		}
		return total;
	}

	/**
	 * Snapshots the per-source loot aggregate into immutable {@link SourceLoot}
	 * views: items within each source sorted by value descending, and sources
	 * themselves ordered most-recently-updated first (newest drops at the top of
	 * the feed) via each source's {@link SourceAgg#lastSeq} recency stamp.
	 */
	private List<SourceLoot> buildLootSources()
	{
		// Order sources by recency (newest drop first) rather than by value, so a
		// fresh kill always appears at the top instead of being buried under an
		// older, higher-value source.
		List<Map.Entry<String, SourceAgg>> entries = new ArrayList<>(lootBySource.entrySet());
		entries.sort((a, b) -> Long.compare(b.getValue().lastSeq, a.getValue().lastSeq));

		List<SourceLoot> out = new ArrayList<>();
		for (Map.Entry<String, SourceAgg> e : entries)
		{
			List<ItemStack> items = new ArrayList<>(e.getValue().items.values());
			items.sort((a, b) -> Long.compare(b.value(), a.value()));
			long total = 0L;
			for (ItemStack s : items)
			{
				total += s.value();
			}
			out.add(new SourceLoot(e.getKey(), total, e.getValue().count, items));
		}
		return out;
	}

	private void mergeItem(Map<Integer, ItemStack> map, int canonicalId, String name, long qty, long unitValue)
	{
		ItemStack existing = map.get(canonicalId);
		long newQty = qty + (existing == null ? 0L : existing.getQuantity());
		// HA price resolved here on the client thread (getItemComposition asserts
		// it) so the EDT-side loot tooltip can read it without threading errors.
		map.put(canonicalId, new ItemStack(canonicalId, name, newQty, unitValue, valuation.haPrice(canonicalId)));
	}

	// --------------------------------------------------------- bank persistence

	/**
	 * Restores the per-account bank cache into memory at the start of a session,
	 * re-pricing every line from live GE prices so a stale cache is still valued
	 * correctly. Resets to "unknown" first so switching accounts never leaks the
	 * previous account's bank. Keyed automatically to the logged-in RS profile.
	 */
	private void loadBankCache()
	{
		cachedBankItems = new LinkedHashMap<>();
		lastKnownBankValue = 0L;
		bankEverSeen = false;

		if (configManager == null || gson == null || client.getAccountHash() == -1L)
		{
			return;
		}

		try
		{
			String json = configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, BANK_CACHE_KEY);
			if (json == null || json.isEmpty())
			{
				return;
			}

			BankCache cache = gson.fromJson(json, BankCache.class);
			if (cache == null || cache.getItems().isEmpty())
			{
				return;
			}

			long total = 0L;
			for (BankCache.Entry e : cache.getItems())
			{
				if (e.getId() <= 0 || e.getQuantity() <= 0)
				{
					continue;
				}
				int canonicalId = valuation.canonical(e.getId());
				long unit = valuation.unitValue(e.getId());
				String name = valuation.name(e.getId());
				mergeItem(cachedBankItems, canonicalId, name, e.getQuantity(), unit);
				total += unit * e.getQuantity();
			}

			lastKnownBankValue = total;
			bankEverSeen = !cachedBankItems.isEmpty();
		}
		catch (Exception ex)
		{
			// Corrupt/incompatible cache — treat the bank as unknown until the
			// player next opens it, rather than failing the session bootstrap.
			log.debug("Failed to load OSPulse bank cache; ignoring", ex);
			cachedBankItems = new LinkedHashMap<>();
			lastKnownBankValue = 0L;
			bankEverSeen = false;
		}
	}

	/** Persists the current bank contents to the logged-in account's RS profile. */
	private void saveBankCache()
	{
		if (configManager == null || gson == null
			|| client.getAccountHash() == -1L || cachedBankItems.isEmpty())
		{
			return;
		}

		try
		{
			BankCache cache = new BankCache(System.currentTimeMillis(), cachedBankItems.values());
			configManager.setRSProfileConfiguration(OSPulseConfig.GROUP, BANK_CACHE_KEY, gson.toJson(cache));
		}
		catch (Exception ex)
		{
			log.debug("Failed to save OSPulse bank cache", ex);
		}
	}

	// ------------------------------------------------------ GE ledger persistence

	/**
	 * Restores the GE cost-basis buy ledger (see
	 * {@link GeReconciler#exportCostBasis()}) into the reconciler at the start
	 * of a session. Keyed automatically to the logged-in RS profile, mirroring
	 * {@link #loadBankCache()}. MUST be called right after
	 * {@link GeReconciler#reset()} so the restore isn't immediately wiped.
	 */
	private void loadGeLedger()
	{
		if (configManager == null || gson == null || client.getAccountHash() == -1L)
		{
			return;
		}

		try
		{
			String json = configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, GE_LEDGER_KEY);
			if (json == null || json.isEmpty())
			{
				return;
			}

			Type type = new TypeToken<Map<Integer, GeReconciler.CostBasisSnapshot>>()
			{
			}.getType();
			Map<Integer, GeReconciler.CostBasisSnapshot> snapshot = gson.fromJson(json, type);
			geReconciler.importCostBasis(snapshot);
		}
		catch (Exception ex)
		{
			// Corrupt/incompatible ledger — treat as empty (no restored cost basis)
			// rather than failing session bootstrap; the reconciler was just reset
			// so this is a no-op, not a data loss beyond what a fresh session has.
			log.debug("Failed to load OSPulse GE cost-basis ledger; ignoring", ex);
		}
	}

	/**
	 * Persists the reconciler's current GE cost-basis buy ledger to the
	 * logged-in account's RS profile, mirroring {@link #saveBankCache()}. Not
	 * gated on the ledger being non-empty (unlike the bank cache): a ledger
	 * that has genuinely gone to zero (every open position sold) is a real
	 * state and must overwrite a stale non-empty cache.
	 */
	private void saveGeLedger()
	{
		if (configManager == null || gson == null || client.getAccountHash() == -1L)
		{
			return;
		}

		try
		{
			Map<Integer, GeReconciler.CostBasisSnapshot> snapshot = geReconciler.exportCostBasis();
			configManager.setRSProfileConfiguration(OSPulseConfig.GROUP, GE_LEDGER_KEY, gson.toJson(snapshot));
		}
		catch (Exception ex)
		{
			log.debug("Failed to save OSPulse GE cost-basis ledger", ex);
		}
	}
}
