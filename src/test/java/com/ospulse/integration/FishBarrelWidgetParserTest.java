package com.ospulse.integration;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for the fish barrel "Check" interface text parser. Pure/stateless:
 * no RuneLite runtime dependency.
 */
public class FishBarrelWidgetParserTest
{
	private final Map<String, Integer> fishTypesByName = FishBarrelTracker.fishTypesByName();

	@Test
	public void invalidMessageReturnsNull()
	{
		assertNull(FishBarrelWidgetParser.parse("Hello, World!", fishTypesByName));
	}

	@Test
	public void entryWithoutHeaderReturnsNull()
	{
		assertNull(FishBarrelWidgetParser.parse("28 x Raw swordfish", fishTypesByName));
	}

	@Test
	public void blankOrNullMessageReturnsNull()
	{
		assertNull(FishBarrelWidgetParser.parse(null, fishTypesByName));
		assertNull(FishBarrelWidgetParser.parse("   ", fishTypesByName));
	}

	@Test
	public void emptyBarrelParsesToEmptyMap()
	{
		Map<Integer, Integer> parsed = FishBarrelWidgetParser.parse("The barrel is empty.", fishTypesByName);
		assertEquals(Map.of(), parsed);
	}

	@Test
	public void singleEntryParsesToItemIdAndQuantity()
	{
		Map<Integer, Integer> parsed = FishBarrelWidgetParser.parse(
			String.join("<br>", "The barrel contains:", "27 x Raw anglerfish"), fishTypesByName);

		assertEquals(Map.of(net.runelite.api.gameval.ItemID.RAW_ANGLERFISH, 27), parsed);
	}

	@Test
	public void multiEntryParsesEverySpecies()
	{
		Map<Integer, Integer> parsed = FishBarrelWidgetParser.parse(String.join("<br>",
			"The barrel contains:",
			"1 x Raw anglerfish, 2 x Raw monkfish, 3 x Raw",
			"shrimps, 1 x Raw anchovies, 1 x Raw salmon, 1 x Raw",
			"cod, 1 x Raw tuna, 1 x Raw bass"), fishTypesByName);

		int total = parsed.values().stream().mapToInt(Integer::intValue).sum();
		assertEquals(11, total);
		assertEquals(Integer.valueOf(1), parsed.get(net.runelite.api.gameval.ItemID.RAW_ANGLERFISH));
		assertEquals(Integer.valueOf(2), parsed.get(net.runelite.api.gameval.ItemID.RAW_MONKFISH));
		assertEquals(Integer.valueOf(3), parsed.get(net.runelite.api.gameval.ItemID.RAW_SHRIMP));
		assertEquals(Integer.valueOf(1), parsed.get(net.runelite.api.gameval.ItemID.RAW_ANCHOVIES));
		assertEquals(Integer.valueOf(1), parsed.get(net.runelite.api.gameval.ItemID.RAW_SALMON));
		assertEquals(Integer.valueOf(1), parsed.get(net.runelite.api.gameval.ItemID.RAW_COD));
		assertEquals(Integer.valueOf(1), parsed.get(net.runelite.api.gameval.ItemID.RAW_TUNA));
		assertEquals(Integer.valueOf(1), parsed.get(net.runelite.api.gameval.ItemID.RAW_BASS));
	}

	@Test
	public void unrecognisedSpeciesFailsTheWholeParse()
	{
		// A species we can't map back to an item id must not silently vanish or
		// get lumped into an "unknown" bucket — the whole parse should fail so
		// the caller keeps its previous (still-attributed) state.
		Map<Integer, Integer> parsed = FishBarrelWidgetParser.parse(
			String.join("<br>", "The barrel contains:", "5 x Raw wibblefish"), fishTypesByName);
		assertNull(parsed);
	}

	@Test
	public void duplicateEntriesForSameSpeciesAreSummed()
	{
		Map<Integer, Integer> parsed = FishBarrelWidgetParser.parse(
			String.join("<br>", "The barrel contains:", "2 x Raw shark, 3 x Raw shark"), fishTypesByName);
		assertEquals(Integer.valueOf(5), parsed.get(net.runelite.api.gameval.ItemID.RAW_SHARK));
	}
}
