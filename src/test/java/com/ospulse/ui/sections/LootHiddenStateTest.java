package com.ospulse.ui.sections;

import com.ospulse.model.ItemStack;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers feature #8 (loot feed right-click menus): the "Hide item" /
 * "Hide loot" filtering state, kept separate from Swing so it's
 * unit-testable without a running RuneLite client.
 */
public class LootHiddenStateTest
{
	private static ItemStack item(int id, String name)
	{
		return new ItemStack(id, name, 1, 100);
	}

	@Test
	public void newStateHidesNothing()
	{
		LootHiddenState state = new LootHiddenState();

		assertTrue(state.isEmpty());
		assertFalse(state.isItemHidden(1));
		assertFalse(state.isSourceHidden("loot:Cerberus"));
	}

	@Test
	public void hideItemFiltersThatItemOnly()
	{
		LootHiddenState state = new LootHiddenState();
		ItemStack crystalKey = item(985, "Crystal key");
		ItemStack coins = item(995, "Coins");

		state.hideItem(crystalKey);

		assertTrue(state.isItemFiltered(crystalKey));
		assertFalse(state.isItemFiltered(coins));
		assertFalse(state.isEmpty());
	}

	@Test
	public void hideItemRetainsLastSeenNameForHiddenList()
	{
		LootHiddenState state = new LootHiddenState();
		state.hideItem(item(985, "Crystal key"));

		Map<Integer, String> hidden = state.hiddenItems();
		assertEquals(1, hidden.size());
		assertEquals("Crystal key", hidden.get(985));
	}

	@Test
	public void unhideItemRemovesFilter()
	{
		LootHiddenState state = new LootHiddenState();
		ItemStack crystalKey = item(985, "Crystal key");
		state.hideItem(crystalKey);

		state.unhideItem(985);

		assertFalse(state.isItemFiltered(crystalKey));
		assertTrue(state.isEmpty());
	}

	@Test
	public void hideSourceIsIndependentOfHideItem()
	{
		LootHiddenState state = new LootHiddenState();
		state.hideSource("loot:Cerberus", "Cerberus");

		assertTrue(state.isSourceHidden("loot:Cerberus"));
		assertFalse(state.isSourceHidden("loot:Zulrah"));
		assertEquals("Cerberus", state.hiddenSources().get("loot:Cerberus"));
	}

	@Test
	public void unhideSourceRemovesIt()
	{
		LootHiddenState state = new LootHiddenState();
		state.hideSource("loot:Cerberus", "Cerberus");

		state.unhideSource("loot:Cerberus");

		assertFalse(state.isSourceHidden("loot:Cerberus"));
		assertTrue(state.isEmpty());
	}

	@Test
	public void clearRemovesBothItemAndSourceHides()
	{
		LootHiddenState state = new LootHiddenState();
		state.hideItem(item(985, "Crystal key"));
		state.hideSource("loot:Cerberus", "Cerberus");

		state.clear();

		assertTrue(state.isEmpty());
		assertFalse(state.isItemHidden(985));
		assertFalse(state.isSourceHidden("loot:Cerberus"));
	}
}
