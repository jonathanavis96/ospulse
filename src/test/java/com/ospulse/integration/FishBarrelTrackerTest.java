package com.ospulse.integration;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FishBarrelTracker}'s catch-message / inventory-delta /
 * widget-resync inference. Uses a Mockito-stubbed {@link Client} standing in
 * for the live game client (see the {@code testImplementation} note in
 * build.gradle for why: {@code Client} has 200+ methods, impractical to
 * hand-stub, and only a handful matter per scenario here).
 */
public class FishBarrelTrackerTest
{
	private Client client;
	private FishBarrelTracker tracker;
	private int tick;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		tick = 0;
		when(client.getTickCount()).thenAnswer(inv -> tick);
		// Empty inventory/equipment by default; individual tests override.
		// Build both stand-in containers BEFORE stubbing (Mockito's "unfinished
		// stubbing" state is thread-local and doesn't tolerate a nested
		// mock()/when() call as an argument expression inside an outer when()).
		ItemContainer emptyInventory = emptyContainer();
		ItemContainer emptyEquipment = emptyContainer();
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(emptyInventory);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(emptyEquipment);

		tracker = new FishBarrelTracker(client);
		tracker.reset();
		tracker.primeContainers();
	}

	private static ItemContainer emptyContainer()
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(new Item[0]);
		return container;
	}

	private static ItemContainer containerWith(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}

	/** Puts an open fish barrel in the stubbed inventory so barrel-gated checks pass. */
	private void openBarrelInInventory()
	{
		ItemContainer withBarrel = containerWith(new Item(ItemID.FISH_BARREL_OPEN, 1));
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(withBarrel);
		tracker.onItemContainerChanged(InventoryID.INVENTORY.getId(), withBarrel);
	}

	// ------------------------------------------------------------- basic catch

	@Test
	public void singleCatchMessageCreditsOneFishToBarrel()
	{
		openBarrelInInventory();

		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();

		// A clean per-tick credit only confirms the DELTA, not the running
		// total — mirrors molo-pl's own onGameTick, which never clears its
		// "unknown" flag from an ordinary catch either. The count is still
		// tracked (and grows) while unknown; only a Check resync, an Empty, or
		// hitting Full positively confirms the whole barrel. See
		// FishBarrelTracker#onTick's javadoc note on this.
		assertTrue(tracker.isUnknown());
		assertEquals(Map.of(ItemID.RAW_SHARK, 1), tracker.holdingSnapshot());
	}

	@Test
	public void multipleCatchesAccumulateAcrossTicks()
	{
		openBarrelInInventory();

		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();
		tick++;
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();
		tick++;
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a lobster.");
		tracker.onTick();

		assertEquals(Integer.valueOf(2), tracker.holdingSnapshot().get(ItemID.RAW_SHARK));
		assertEquals(Integer.valueOf(1), tracker.holdingSnapshot().get(ItemID.RAW_LOBSTER));
	}

	@Test
	public void checkResyncAfterCatchesConfirmsTheAccumulatedCount()
	{
		// The realistic end-to-end flow: fresh login (unknown), a run of clean
		// catches (still unknown, per molo-pl's semantics), then the player
		// right-clicks "Check" and the resulting widget text confirms the
		// tracker's running count was right all along.
		openBarrelInInventory();
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();
		tick++;
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();
		assertTrue(tracker.isUnknown());

		tracker.onBarrelMenuAction(ItemID.FISH_BARREL_OPEN, "Check");
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn(String.join("<br>", "The barrel contains:", "2 x Raw shark"));
		when(client.getWidget(193, 2)).thenReturn(widget);
		tracker.onWidgetLoaded(193);

		assertFalse(tracker.isUnknown());
		assertEquals(Map.of(ItemID.RAW_SHARK, 2), tracker.holdingSnapshot());
	}

	@Test
	public void catchWithoutOpenBarrelIsIgnored()
	{
		// No barrel (open or closed) in inventory/equipment at all: SPAM catch
		// messages aren't attributable to a barrel and must be ignored.
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();

		assertTrue(tracker.holdingSnapshot().isEmpty());
	}

	// ------------------------------------------------------------- double catch

	@Test
	public void radaBlessingDoublesTheLastCaughtSpecies()
	{
		openBarrelInInventory();

		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onChatMessage(ChatMessageType.SPAM, "Rada's blessing enabled you to catch an extra fish.");
		tracker.onTick();

		// A resolved double-catch is just another attributable credit this
		// tick — doesn't confirm the whole barrel any more than a single catch
		// does (see #singleCatchMessageCreditsOneFishToBarrel).
		assertEquals(Integer.valueOf(2), tracker.holdingSnapshot().get(ItemID.RAW_SHARK));
	}

	@Test
	public void spiritFlakesDoublesTheLastCaughtSpecies()
	{
		openBarrelInInventory();

		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a lobster.");
		tracker.onChatMessage(ChatMessageType.SPAM, "The spirit flakes enabled you to catch an extra fish.");
		tracker.onTick();

		assertEquals(Integer.valueOf(2), tracker.holdingSnapshot().get(ItemID.RAW_LOBSTER));
	}

	@Test
	public void cormorantCatchCannotBeAttributedAndMarksUnknown()
	{
		openBarrelInInventory();

		tracker.onChatMessage(ChatMessageType.SPAM, "Your cormorant returns with its catch.");
		tracker.onTick();

		assertTrue(tracker.isUnknown());
	}

	// ------------------------------------------------------------- infernal harpoon

	@Test
	public void cookingXpDropOffsetsOneCatchFromBarrelCredit()
	{
		openBarrelInInventory();

		// Two catches, one auto-cooked by an infernal harpoon before reaching the
		// barrel: only one fish should land in the barrel.
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onCookingXpGained();
		tracker.onTick();

		assertEquals(Integer.valueOf(1), tracker.holdingSnapshot().get(ItemID.RAW_SHARK));
	}

	// ------------------------------------------------------------- empty to bank

	@Test
	public void emptyToBankClearsHolding()
	{
		openBarrelInInventory();
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();
		assertFalse(tracker.holdingSnapshot().isEmpty());

		tracker.onBarrelMenuAction(ItemID.FISH_BARREL_OPEN, "Empty to bank");
		tracker.onTick();

		assertTrue(tracker.holdingSnapshot().isEmpty());
		assertFalse(tracker.isUnknown());
	}

	@Test
	public void bankEmptyChatMessageClearsHolding()
	{
		openBarrelInInventory();
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();

		tracker.onChatMessage(ChatMessageType.GAMEMESSAGE, "You empty all of your containers into the bank.");

		assertTrue(tracker.holdingSnapshot().isEmpty());
		assertFalse(tracker.isUnknown());
	}

	@Test
	public void bankFullMessageMarksUnknown()
	{
		openBarrelInInventory();
		tracker.onChatMessage(ChatMessageType.GAMEMESSAGE, "Your bank could not hold your fish.");
		assertTrue(tracker.isUnknown());
	}

	// ------------------------------------------------------------- unknown-state freeze / resync

	@Test
	public void freshTrackerStartsUnknown()
	{
		assertTrue(tracker.isUnknown());
	}

	@Test
	public void widgetResyncRestoresKnownStateWithExactContents()
	{
		openBarrelInInventory();
		// Force into an unknown state (cormorant catch can't be attributed).
		tracker.onChatMessage(ChatMessageType.SPAM, "Your cormorant returns with its catch.");
		tracker.onTick();
		assertTrue(tracker.isUnknown());

		tracker.onBarrelMenuAction(ItemID.FISH_BARREL_OPEN, "Check");

		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn(String.join("<br>", "The barrel contains:", "12 x Raw shark"));
		when(client.getWidget(193, 2)).thenReturn(widget);

		tracker.onWidgetLoaded(193);

		assertFalse(tracker.isUnknown());
		assertEquals(Map.of(ItemID.RAW_SHARK, 12), tracker.holdingSnapshot());
	}

	@Test
	public void widgetLoadedForUnrelatedGroupIsIgnored()
	{
		openBarrelInInventory();
		tracker.onBarrelMenuAction(ItemID.FISH_BARREL_OPEN, "Check");

		tracker.onWidgetLoaded(42); // not the Check interface's group id

		Mockito.verify(client, Mockito.never()).getWidget(193, 2);
	}

	@Test
	public void checkResyncOutsideActionWindowIsIgnored()
	{
		openBarrelInInventory();
		// No "Check" menu action recorded recently.
		tracker.onWidgetLoaded(193);

		Mockito.verify(client, Mockito.never()).getWidget(eq(193), eq(2));
	}

	// ------------------------------------------------------------- reset

	@Test
	public void resetClearsHoldingAndReturnsToUnknown()
	{
		openBarrelInInventory();
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();
		assertFalse(tracker.holdingSnapshot().isEmpty());

		tracker.reset();

		assertTrue(tracker.holdingSnapshot().isEmpty());
		assertTrue(tracker.isUnknown());
	}

	// ------------------------------------------------- caught-to-barrel feed

	@Test
	public void drainCaughtToBarrelReturnsThisTickCatchesThenClears()
	{
		openBarrelInInventory();
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();

		// The session tracker drains this once per tick to synthesise the
		// storage-routed loot receipts that lift Loot + net worth.
		assertEquals(Map.of(ItemID.RAW_SHARK, 1), tracker.drainCaughtToBarrel());
		// A second drain in the same tick yields nothing (already consumed).
		assertTrue(tracker.drainCaughtToBarrel().isEmpty());
	}

	@Test
	public void cookedCatchIsExcludedFromTheBarrelFeed()
	{
		openBarrelInInventory();
		// Two catches, one auto-cooked by an infernal harpoon: only one raw fish
		// actually reaches the barrel, so only one is booked as loot.
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onCookingXpGained();
		tracker.onTick();

		assertEquals(Map.of(ItemID.RAW_SHARK, 1), tracker.drainCaughtToBarrel());
	}

	@Test
	public void catchesOverflowingToInventoryAreNotInTheBarrelFeed()
	{
		// Barrel full: a catch lands in the inventory instead of the barrel, so
		// it must NOT be fed as storage-routed loot — the engine's normal
		// inventory diff books it, and double-booking here would over-count Loot.
		openBarrelInInventory();
		ItemContainer barrelPlusShark = containerWith(
			new Item(ItemID.FISH_BARREL_OPEN, 1), new Item(ItemID.RAW_SHARK, 1));
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(barrelPlusShark);
		tracker.onItemContainerChanged(InventoryID.INVENTORY.getId(), barrelPlusShark);
		tracker.onChatMessage(ChatMessageType.SPAM, "You catch a shark.");
		tracker.onTick();

		assertTrue("a catch that overflowed to the inventory is not barrel loot",
			tracker.drainCaughtToBarrel().isEmpty());
	}
}
