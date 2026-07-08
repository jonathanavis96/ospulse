package com.ospulse.integration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infers the contents of an OPEN {@code Fish barrel} / {@code Fish sack barrel}
 * from chat messages, XP drops and inventory deltas, since a barrel toggled
 * open auto-stores caught fish directly without ever passing through the
 * inventory or firing a {@code LootReceived} event, and there is no official
 * varbit exposing its contents.
 *
 * <p>Detection approach adapted from the community "Fish Barrel" plugin by
 * molo-pl (BSD-2-Clause, https://github.com/molo-pl/runelite-plugins, branch
 * {@code fish-barrel}) — see {@code NOTICE} for the full attribution. That
 * plugin only tracks a running total fish count; this tracker additionally
 * resolves WHICH fish was caught (via the same catch-message fish-name map)
 * because OSPulse needs a priced, per-item breakdown to fold into tracked
 * wealth/net worth, not just a headline count.
 *
 * <p>All public methods must be called on the RuneLite client thread — this
 * mirrors every other live-state reader in this package (see
 * {@link SessionTracker}'s class javadoc) since {@link #onWidgetLoaded} reads
 * a widget's text, which is only safe there.
 */
public class FishBarrelTracker
{
	/** Max fish the barrel can hold, matching the in-game "barrel is full" cap. */
	public static final int CAPACITY = 28;

	private static final Collection<Integer> BARREL_IDS = List.of(
		ItemID.FISH_BARREL_CLOSED,
		ItemID.FISH_BARREL_OPEN,
		ItemID.FISH_SACK_BARREL_CLOSED,
		ItemID.FISH_SACK_BARREL_OPEN);

	private static final Collection<Integer> OPEN_BARREL_IDS = List.of(
		ItemID.FISH_BARREL_OPEN,
		ItemID.FISH_SACK_BARREL_OPEN);

	/** Matches "You catch a/an/some X[.!]" possibly followed by the ice gloves suffix. */
	private static final Pattern FISH_CAUGHT_MESSAGE = Pattern.compile(
		"^You catch (an?|some)(?: raw)? ([a-zA-Z ]+)[.!]?"
			+ "( It hardens as you handle it with your ice gloves\\.)?$");

	private static final String RADA_DOUBLE_CATCH_MESSAGE =
		"Rada's blessing enabled you to catch an extra fish.";
	private static final String FLAKES_DOUBLE_CATCH_MESSAGE =
		"The spirit flakes enabled you to catch an extra fish.";
	private static final String CORMORANT_CATCH_MESSAGE = "Your cormorant returns with its catch.";

	private static final String BANK_FULL_MESSAGE = "Your bank could not hold your fish.";
	private static final String BARREL_FULL_MESSAGE = "The barrel is full. It may be emptied at a bank.";
	private static final String BANK_EMPTY_MESSAGE = "You empty all of your containers into the bank.";
	private static final String CONTAINERS_EMPTY_MESSAGE = "Your containers are already empty.";

	/** Fish name (as it appears in the catch chat message) -&gt; item id. */
	private static final Map<String, Integer> FISH_TYPES_BY_NAME = ImmutableMap.<String, Integer>builder()
		// singular 'shrimp' may occur when fishing for Karambwanji
		.put("shrimp", ItemID.RAW_SHRIMP)
		.put("shrimps", ItemID.RAW_SHRIMP)
		.put("sardine", ItemID.RAW_SARDINE)
		.put("herring", ItemID.RAW_HERRING)
		.put("anchovies", ItemID.RAW_ANCHOVIES)
		.put("mackerel", ItemID.RAW_MACKEREL)
		.put("trout", ItemID.RAW_TROUT)
		.put("cod", ItemID.RAW_COD)
		.put("pike", ItemID.RAW_PIKE)
		.put("slimy swamp eel", ItemID.MORT_SLIMEY_EEL)
		.put("salmon", ItemID.RAW_SALMON)
		.put("tuna", ItemID.RAW_TUNA)
		.put("rainbow fish", ItemID.HUNTING_RAW_FISH_SPECIAL)
		.put("cave eel", ItemID.RAW_CAVE_EEL)
		.put("lobster", ItemID.RAW_LOBSTER)
		.put("bass", ItemID.RAW_BASS)
		.put("leaping trout", ItemID.BRUT_SPAWNING_TROUT)
		.put("swordfish", ItemID.RAW_SWORDFISH)
		.put("lava eel", ItemID.RAW_LAVA_EEL)
		.put("leaping salmon", ItemID.BRUT_SPAWNING_SALMON)
		.put("monkfish", ItemID.RAW_MONKFISH)
		.put("Karambwan", ItemID.TBWT_RAW_KARAMBWAN)
		.put("leaping sturgeon", ItemID.BRUT_STURGEON)
		.put("shark", ItemID.RAW_SHARK)
		.put("infernal eel", ItemID.INFERNAL_EEL)
		.put("anglerfish", ItemID.RAW_ANGLERFISH)
		.put("dark crab", ItemID.RAW_DARK_CRAB)
		.put("sacred eel", ItemID.SNAKEBOSS_EEL)
		.put("swordtip squid", ItemID.RAW_SWORDTIP_SQUID)
		.put("jumbo squid", ItemID.RAW_JUMBO_SQUID)
		.build();

	/** Fish caught with a cormorant on Molch island — no catch message names them individually. */
	private static final Set<Integer> MOLCH_ISLAND_FISH_TYPES = ImmutableSet.of(
		ItemID.AERIAL_FISHING_BLUEGILL,
		ItemID.AERIAL_FISHING_COMMON_TENCH,
		ItemID.AERIAL_FISHING_MOTTLED_EEL,
		ItemID.AERIAL_FISHING_GREATER_SIREN);

	/** Other fish that aren't directly caught but can still be deposited into the barrel. */
	private static final Set<Integer> OTHER_FISH_TYPES = ImmutableSet.of(
		ItemID.RAW_SEATURTLE,
		ItemID.RAW_MANTARAY);

	private static final Set<Integer> ALL_FISH_TYPES = ImmutableSet.<Integer>builder()
		.addAll(FISH_TYPES_BY_NAME.values())
		.addAll(MOLCH_ISLAND_FISH_TYPES)
		.addAll(OTHER_FISH_TYPES)
		.build();

	private static final int CHECK_WIDGET_INTERFACE = 193;
	private static final int CHECK_WIDGET_COMPONENT = 2;

	/** Barrel "Check"/"Fill"/"Empty" menu-click timeout, in game ticks. */
	private static final int ACTION_TICK_MARGIN = 3;

	private final Client client;

	/** Fish itemId -> quantity currently believed to be in the barrel. */
	private final Map<Integer, Integer> holding = new LinkedHashMap<>();
	/** Total fish held (sum of {@link #holding} values), tracked in lockstep for capacity checks. */
	private int totalHolding;
	/**
	 * True once tracking has lost confidence in {@link #holding} (e.g. the bank
	 * couldn't take all the fish, or the barrel filled with fish whose exact
	 * type split we can't attribute). While true, callers must NOT let the
	 * barrel's wealth contribution move off its last-known value — see
	 * {@link SessionTracker#accumulateFishBarrel} — until a Check-widget resync
	 * (see {@link #onWidgetLoaded}) restores a known state.
	 */
	private boolean unknown = true;

	private final Map<Integer, Integer> inventoryItems = new HashMap<>();
	private final Map<Integer, Integer> equipmentItems = new HashMap<>();

	/** Most recent fish name resolved per pending catch message this tick, in order. */
	private final List<Integer> fishCaughtThisTick = new ArrayList<>();
	private int newFishInInventoryThisTick;
	private int cookingXpDropsThisTick;

	private final Map<BarrelAction, Integer> lastActionTick = new HashMap<>();

	private enum BarrelAction
	{
		FILL, EMPTY, CHECK
	}

	public FishBarrelTracker(Client client)
	{
		this.client = client;
	}

	/** Snapshot of fish itemId -> quantity currently believed held in the barrel. */
	public Map<Integer, Integer> holdingSnapshot()
	{
		return new LinkedHashMap<>(holding);
	}

	/**
	 * True while tracking has lost confidence in the exact barrel contents.
	 * Callers should freeze the barrel's wealth contribution at its last-known
	 * value rather than let it jump, to avoid a phantom profit/loss spike.
	 */
	public boolean isUnknown()
	{
		return unknown;
	}

	/** Resets all tracked state — call on a genuine login (not a zone hop). */
	public void reset()
	{
		holding.clear();
		totalHolding = 0;
		unknown = true;
		inventoryItems.clear();
		equipmentItems.clear();
		fishCaughtThisTick.clear();
		newFishInInventoryThisTick = 0;
		cookingXpDropsThisTick = 0;
		lastActionTick.clear();
	}

	/**
	 * Seeds the inventory/equipment shadow copies from the live client state.
	 * MUST be called on the client thread; call once after {@link #reset()} on
	 * login so the first {@link #onItemContainerChanged} diff is against real
	 * baseline contents rather than empty maps.
	 */
	public void primeContainers()
	{
		copyContainer(client.getItemContainer(InventoryID.INVENTORY), inventoryItems);
		copyContainer(client.getItemContainer(InventoryID.EQUIPMENT), equipmentItems);
	}

	public void onChatMessage(ChatMessageType type, String message)
	{
		if (message == null)
		{
			return;
		}

		if (type == ChatMessageType.GAMEMESSAGE && hasAnyBarrel())
		{
			if (BANK_FULL_MESSAGE.equals(message))
			{
				// Couldn't deposit all the fish into the bank — we no longer know
				// exactly how many (if any) remain in the barrel vs. the inventory.
				unknown = true;
			}
			else if (BARREL_FULL_MESSAGE.equals(message))
			{
				// Full, but we don't know the exact type breakdown from this message
				// alone; a subsequent Check resync will restore per-type confidence.
				capAtFull();
			}
			else if (BANK_EMPTY_MESSAGE.equals(message) || CONTAINERS_EMPTY_MESSAGE.equals(message))
			{
				holding.clear();
				totalHolding = 0;
				unknown = false;
			}
			return;
		}

		if (type == ChatMessageType.SPAM && hasAnyOpenBarrel())
		{
			Matcher matcher = FISH_CAUGHT_MESSAGE.matcher(message);
			if (matcher.matches())
			{
				String fishName = matcher.group(2);
				Integer fishId = FISH_TYPES_BY_NAME.get(fishName);
				if (fishId != null)
				{
					fishCaughtThisTick.add(fishId);
				}
			}
			else if (RADA_DOUBLE_CATCH_MESSAGE.equals(message) || FLAKES_DOUBLE_CATCH_MESSAGE.equals(message))
			{
				// Bonus catch is the same species as the most recent resolved catch
				// this tick (Rada's blessing / spirit flakes double an existing
				// catch, they don't introduce a new species).
				if (!fishCaughtThisTick.isEmpty())
				{
					fishCaughtThisTick.add(fishCaughtThisTick.get(fishCaughtThisTick.size() - 1));
				}
				else
				{
					// No resolved species to duplicate (shouldn't normally happen —
					// the bonus message follows the catch message) — we can't
					// attribute this extra fish to a specific item, so mark unknown
					// rather than silently drop it.
					unknown = true;
				}
			}
			else if (CORMORANT_CATCH_MESSAGE.equals(message))
			{
				// Molch island cormorant catches are one of several species with no
				// distinguishing chat text — the exact species can't be attributed
				// from chat alone, so flag unknown; a Check resync recovers it.
				unknown = true;
			}
		}
	}

	public void onItemContainerChanged(int containerId, ItemContainer container)
	{
		if (containerId == InventoryID.INVENTORY.getId())
		{
			if (!hasAnyBarrel())
			{
				copyContainer(container, inventoryItems);
				return;
			}

			Map<Integer, Integer> previous = new HashMap<>(inventoryItems);
			copyContainer(container, inventoryItems);

			for (Map.Entry<Integer, Integer> entry : inventoryItems.entrySet())
			{
				int itemId = entry.getKey();
				if (!ALL_FISH_TYPES.contains(itemId))
				{
					continue;
				}
				int before = previous.getOrDefault(itemId, 0);
				int after = entry.getValue();
				if (after > before)
				{
					newFishInInventoryThisTick += (after - before);
				}
			}

			int removedFish = 0;
			for (Map.Entry<Integer, Integer> entry : previous.entrySet())
			{
				int itemId = entry.getKey();
				if (!ALL_FISH_TYPES.contains(itemId))
				{
					continue;
				}
				int before = entry.getValue();
				int after = inventoryItems.getOrDefault(itemId, 0);
				if (after < before)
				{
					removedFish += (before - after);
				}
			}

			if (removedFish > 0 && recentlyActioned(BarrelAction.FILL))
			{
				boolean anyFishLeft = inventoryItems.entrySet().stream()
					.anyMatch(e -> ALL_FISH_TYPES.contains(e.getKey()) && e.getValue() > 0);
				if (anyFishLeft)
				{
					// Barrel couldn't take everything — full, but we don't know the
					// exact species split that ended up inside.
					capAtFull();
				}
				else
				{
					// Nothing left in inventory: the removed fish are exactly what
					// went into the barrel — but a plain inventory diff during a
					// "Fill" doesn't tell us their species breakdown (multiple
					// species may have been removed at once), so mark unknown until
					// a Check resync (see #onWidgetLoaded) recovers it. Mirrors
					// molo-pl's total-only tracking for the Fill path.
					unknown = true;
				}
			}
		}
		else if (containerId == InventoryID.EQUIPMENT.getId())
		{
			copyContainer(container, equipmentItems);
		}
	}

	public void onCookingXpGained()
	{
		cookingXpDropsThisTick++;
	}

	/**
	 * Records a barrel "Fill"/"Empty"/"Empty to bank"/"Check" menu click, keyed
	 * by the current game tick so {@link #recentlyActioned} can correlate it
	 * with the container/widget change that follows within a few ticks.
	 */
	public void onBarrelMenuAction(int itemId, String menuOption)
	{
		if (!BARREL_IDS.contains(itemId) || menuOption == null)
		{
			return;
		}
		BarrelAction action;
		if ("Fill".equalsIgnoreCase(menuOption))
		{
			action = BarrelAction.FILL;
		}
		else if ("Empty".equalsIgnoreCase(menuOption) || "Empty to bank".equalsIgnoreCase(menuOption))
		{
			action = BarrelAction.EMPTY;
		}
		else if ("Check".equalsIgnoreCase(menuOption))
		{
			action = BarrelAction.CHECK;
		}
		else
		{
			return;
		}
		lastActionTick.put(action, client.getTickCount());
	}

	/**
	 * Drains this tick's accumulated catch-message / inventory-delta / cooking-xp
	 * signals into the barrel state. Call once per game tick (mirrors {@link
	 * SessionTracker#onTick}'s per-tick drain of {@code MovementSignals}).
	 */
	public void onTick()
	{
		if (!fishCaughtThisTick.isEmpty())
		{
			if (newFishInInventoryThisTick == 0)
			{
				// All of this tick's catches went straight to the barrel. An
				// infernal harpoon can auto-cook a subset of them (each such fish
				// fires a cooking XP drop instead of landing raw), so subtract those
				// off before crediting the barrel — matches molo-pl's handling.
				//
				// Deliberately does NOT clear #unknown here, mirroring molo-pl's
				// own FishBarrelPlugin#onGameTick: a clean per-tick credit only
				// tells us the DELTA is right, not that the running total is —
				// #unknown is reserved for signals that positively confirm the
				// whole barrel (Check resync, Empty, hitting Full), so a fresh
				// login stays "unknown" (freezing the barrel's wealth
				// contribution — see SessionTracker#accumulateFishBarrel) until
				// one of those actually happens, however many clean catches occur
				// in between.
				int cookedAway = Math.min(cookingXpDropsThisTick, fishCaughtThisTick.size());
				List<Integer> toCredit = cookedAway > 0
					? fishCaughtThisTick.subList(0, fishCaughtThisTick.size() - cookedAway)
					: fishCaughtThisTick;
				for (int fishId : toCredit)
				{
					addToBarrel(fishId, 1);
				}
			}
			else
			{
				// Some catches landed in the inventory instead — the barrel is full
				// and we don't know which species make up the remainder.
				capAtFull();
			}
		}
		else if (recentlyActioned(BarrelAction.EMPTY))
		{
			holding.clear();
			totalHolding = 0;
			unknown = false;
		}

		fishCaughtThisTick.clear();
		newFishInInventoryThisTick = 0;
		cookingXpDropsThisTick = 0;
	}

	/**
	 * Parses the "Check" interface's widget text (interface 193, component 2)
	 * to resynchronise the exact per-species barrel contents, restoring
	 * {@link #isUnknown()} to {@code false} on a successful parse. MUST be
	 * called on the client thread.
	 */
	public void onWidgetLoaded(int groupId)
	{
		if (groupId != CHECK_WIDGET_INTERFACE || !recentlyActioned(BarrelAction.CHECK))
		{
			return;
		}

		Widget widget = client.getWidget(CHECK_WIDGET_INTERFACE, CHECK_WIDGET_COMPONENT);
		if (widget == null)
		{
			return;
		}

		Map<Integer, Integer> parsed = FishBarrelWidgetParser.parse(widget.getText(), FISH_TYPES_BY_NAME);
		if (parsed != null)
		{
			holding.clear();
			holding.putAll(parsed);
			totalHolding = parsed.values().stream().mapToInt(Integer::intValue).sum();
			unknown = false;
		}
	}

	private void addToBarrel(int fishId, int qty)
	{
		if (totalHolding >= CAPACITY)
		{
			return;
		}
		int room = CAPACITY - totalHolding;
		int add = Math.min(room, qty);
		holding.merge(fishId, add, Integer::sum);
		totalHolding += add;
		if (add < qty)
		{
			// Ran out of room mid-credit: the barrel is now full but we can't be
			// sure the overflow species is what actually got left out.
			unknown = true;
		}
	}

	/**
	 * Records that the barrel is known to be at capacity, but not necessarily
	 * with the exact species split we're currently holding — always marks
	 * {@link #unknown} so {@link SessionTracker#accumulateFishBarrel} freezes
	 * the wealth contribution rather than trusting a possibly-stale per-species
	 * breakdown, until a Check resync (see {@link #onWidgetLoaded}) restores it.
	 * Existing per-species entries are kept as-is (not wiped) so the resync has
	 * a sane multiset to replace rather than an empty one.
	 */
	private void capAtFull()
	{
		totalHolding = CAPACITY;
		unknown = true;
	}

	private boolean recentlyActioned(BarrelAction action)
	{
		Integer tick = lastActionTick.get(action);
		if (tick != null && tick > client.getTickCount() - ACTION_TICK_MARGIN)
		{
			lastActionTick.remove(action);
			return true;
		}
		return false;
	}

	private boolean hasAnyBarrel()
	{
		return containsAny(BARREL_IDS);
	}

	private boolean hasAnyOpenBarrel()
	{
		return containsAny(OPEN_BARREL_IDS);
	}

	private boolean containsAny(Collection<Integer> itemIds)
	{
		for (int itemId : itemIds)
		{
			if (inventoryItems.getOrDefault(itemId, 0) > 0 || equipmentItems.getOrDefault(itemId, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	private static void copyContainer(ItemContainer container, Map<Integer, Integer> target)
	{
		target.clear();
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			target.merge(item.getId(), item.getQuantity(), Integer::sum);
		}
	}

	/** Catch-message fish name -&gt; item id map, exposed package-private for tests. */
	static Map<String, Integer> fishTypesByName()
	{
		return FISH_TYPES_BY_NAME;
	}
}
