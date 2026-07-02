package com.phatwang.integration;

import com.phatwang.PhatWangConfig;
import com.phatwang.ge.GeOfferState;
import com.phatwang.ge.GeReconciler;
import com.phatwang.model.ItemStack;
import com.phatwang.session.SessionEngine;
import com.phatwang.session.SessionListener;
import com.phatwang.session.SessionService;
import com.phatwang.session.SessionSnapshot;
import com.phatwang.wealth.WealthSnapshot;
import com.phatwang.xp.XpTracker;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.client.game.ItemManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wires live RuneLite game state into the pure {@code com.phatwang.session}
 * / {@code com.phatwang.ge} / {@code com.phatwang.xp} engines and publishes
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
public class SessionTracker implements SessionService
{
	private static final int TOP_HOLDINGS_LIMIT = 20;

	private final Client client;
	private final RuneLiteItemValuation valuation;
	private final PhatWangConfig config;

	private final SessionEngine engine = new SessionEngine();
	private final GeReconciler geReconciler = new GeReconciler();
	private final XpTracker xpTracker = new XpTracker();
	private final CopyOnWriteArrayList<SessionListener> listeners = new CopyOnWriteArrayList<>();

	private volatile SessionSnapshot latest;

	/** Last bank value observed while the bank container was populated. */
	private volatile long lastKnownBankValue = 0L;
	/** Whether the bank has ever been observed (opened) this client session. */
	private volatile boolean bankEverSeen = false;

	private volatile boolean loggedIn = false;
	/** True once {@link SessionEngine#startSession} has been called for the current login. */
	private volatile boolean started = false;

	public SessionTracker(Client client, ItemManager itemManager, PhatWangConfig config)
	{
		this.client = client;
		this.config = config;
		this.valuation = new RuneLiteItemValuation(itemManager);
	}

	// ------------------------------------------------------------- lifecycle

	public void onLogin()
	{
		loggedIn = true;
		// Force a fresh baseline on the next tick, so a re-login (e.g. world
		// hop) starts a new session rather than continuing the old one.
		started = false;
	}

	public void onLogout()
	{
		loggedIn = false;
		// Keep the session/latest snapshot as-is; we simply stop producing
		// new ones until the next login.
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

		refresh(ts);
	}

	public void onItemContainerChanged(int containerId)
	{
		if (!loggedIn || !started)
		{
			return;
		}
		// Refresh eagerly on container changes (inventory/equipment/bank) so
		// the panel feels responsive rather than waiting up to a full game
		// tick; onTick will simply refresh again (idempotent) on the next tick.
		refresh(System.currentTimeMillis());
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

	public void onStatChanged(Skill skill, int xp)
	{
		if (skill == null || skill == Skill.OVERALL)
		{
			return;
		}
		xpTracker.update(skill.name(), xp);
	}

	public void onBankOpenChanged(boolean open)
	{
		if (!loggedIn || !started)
		{
			return;
		}

		long ts = System.currentTimeMillis();
		WealthSnapshot current = buildWealth(ts);
		engine.setBankOpen(open, current, ts);
		publish(engine.snapshot(current, geReconciler.realizedPnl(), xpTracker.gained(), xpTracker.totalGained(), ts));
	}

	/** Restarts the session baseline from current wealth (panel reset button). */
	public void resetSession()
	{
		if (!loggedIn)
		{
			return;
		}

		long ts = System.currentTimeMillis();
		xpTracker.start(captureXpBaseline());
		WealthSnapshot current = buildWealth(ts);
		engine.startSession(current, ts);
		started = true;
		publish(engine.snapshot(current, geReconciler.realizedPnl(), xpTracker.gained(), xpTracker.totalGained(), ts));
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
		xpTracker.start(captureXpBaseline());
		WealthSnapshot initial = buildWealth(ts);
		engine.startSession(initial, ts);
		started = true;
		publish(engine.snapshot(initial, geReconciler.realizedPnl(), xpTracker.gained(), xpTracker.totalGained(), ts));
	}

	private void refresh(long ts)
	{
		WealthSnapshot current = buildWealth(ts);
		Set<Integer> geAttributedItemIds = geReconciler.drainAttributedItemIds();
		engine.update(current, geAttributedItemIds, ts);
		publish(engine.snapshot(current, geReconciler.realizedPnl(), xpTracker.gained(), xpTracker.totalGained(), ts));
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
			client.getItemContainer(InventoryID.INVENTORY), tracked, allHoldings);
		long equipmentValue = accumulateContainer(
			client.getItemContainer(InventoryID.EQUIPMENT), tracked, allHoldings);

		// Bank is only populated once opened this client session; reuse the
		// last known value on ticks where the container isn't available.
		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		long bankValue;
		if (bankContainer != null)
		{
			// Bank contents feed topHoldings only, never trackedItems.
			bankValue = accumulateContainer(bankContainer, null, allHoldings);
			lastKnownBankValue = bankValue;
			bankEverSeen = true;
		}
		else
		{
			bankValue = lastKnownBankValue;
		}

		long geValue = accumulateGrandExchange(allHoldings);

		long pouchValue = 0L;
		if (config.includePouches())
		{
			pouchValue = accumulateRunePouch(tracked, allHoldings);
		}
		// TODO pouch valuation: looting bag contents are not reliably
		// readable client-side and are intentionally skipped.

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
			.pouchValue(pouchValue)
			.bankValue(bankValue)
			.bankKnown(bankEverSeen)
			.timestampMs(tsMs)
			.topHoldings(topHoldings)
			.trackedItems(tracked)
			.build();
	}

	/**
	 * Sums the value of a container's items, merging each into
	 * {@code allHoldings} (and {@code trackedOrNull}, if non-null) keyed by
	 * canonical item id.
	 */
	private long accumulateContainer(
		ItemContainer container, Map<Integer, ItemStack> trackedOrNull, Map<Integer, ItemStack> allHoldings)
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
			long qty = item.getQuantity();
			int canonicalId = valuation.canonical(itemId);
			long unit = valuation.unitValue(itemId);
			String name = valuation.name(itemId);

			total += unit * qty;
			if (trackedOrNull != null)
			{
				mergeItem(trackedOrNull, canonicalId, name, qty, unit);
			}
			mergeItem(allHoldings, canonicalId, name, qty, unit);
		}
		return total;
	}

	/**
	 * Values active Grand Exchange offers (see {@link GeValuation} for the
	 * approximation used) and folds their items into {@code allHoldings}.
	 * GE items are never added to trackedItems (loot diffing excludes them;
	 * {@link GeReconciler#drainAttributedItemIds()} handles that instead).
	 * Cancelled offers still hold uncollected coins/items, so they're valued
	 * the same way as their non-cancelled counterpart (buy-shaped /
	 * sell-shaped) until collected.
	 */
	private long accumulateGrandExchange(Map<Integer, ItemStack> allHoldings)
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
			int canonicalId = valuation.canonical(itemId);
			String name = valuation.name(itemId);

			long value;
			long qtyForHoldings;
			switch (state)
			{
				case BUYING:
				case BOUGHT:
				case CANCELLED_BUY:
					value = GeValuation.buyOfferValue(offer.getPrice(), offer.getTotalQuantity());
					qtyForHoldings = offer.getTotalQuantity();
					break;
				case SELLING:
				case SOLD:
				case CANCELLED_SELL:
				default:
					value = GeValuation.sellOfferValue(
						unit, offer.getTotalQuantity(), offer.getQuantitySold(), offer.getSpent());
					qtyForHoldings = Math.max(0, offer.getTotalQuantity() - offer.getQuantitySold());
					break;
			}

			total += value;
			if (qtyForHoldings > 0)
			{
				mergeItem(allHoldings, canonicalId, name, qtyForHoldings, unit);
			}
		}
		return total;
	}

	/**
	 * Best-effort rune pouch valuation via the rune-pouch varbits (3 rune
	 * slots; the divine/expanded 4th slot and looting bag are not covered).
	 */
	private long accumulateRunePouch(Map<Integer, ItemStack> tracked, Map<Integer, ItemStack> allHoldings)
	{
		int[] runeVarbits = {Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3};
		int[] amountVarbits = {Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3};

		long total = 0L;
		for (int i = 0; i < runeVarbits.length; i++)
		{
			int itemId = client.getVarbitValue(runeVarbits[i]);
			int amount = client.getVarbitValue(amountVarbits[i]);
			if (itemId <= 0 || amount <= 0)
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

	private void mergeItem(Map<Integer, ItemStack> map, int canonicalId, String name, long qty, long unitValue)
	{
		ItemStack existing = map.get(canonicalId);
		long newQty = qty + (existing == null ? 0L : existing.getQuantity());
		map.put(canonicalId, new ItemStack(canonicalId, name, newQty, unitValue));
	}
}
