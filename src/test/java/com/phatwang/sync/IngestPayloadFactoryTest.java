package com.phatwang.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.phatwang.model.ItemStack;
import com.phatwang.session.LootEntry;
import com.phatwang.session.SessionSnapshot;
import com.phatwang.wealth.WealthSnapshot;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies {@link IngestPayloadFactory} produces JSON matching the
 * dashboard's ingest contract exactly - field names, nesting and values -
 * without touching the network or RuneLite.
 */
public class IngestPayloadFactoryTest
{
	private static final Instant FIXED_INSTANT = Instant.parse("2026-07-02T17:30:00Z");
	private static final Gson GSON = new Gson();

	private SessionSnapshot buildSnapshot()
	{
		List<LootEntry> loot = Arrays.asList(
			new LootEntry(4151, "Abyssal whip", 1L, 1_500_000L, 1_000L),
			new LootEntry(995, "Coins", 5000L, 5000L, 900L));

		Map<String, Long> xp = new LinkedHashMap<>();
		xp.put("ATTACK", 1234L);
		xp.put("STRENGTH", 5678L);

		List<ItemStack> topHoldings = Arrays.asList(
			new ItemStack(4151, "Abyssal whip", 1L, 1_500_000L),
			new ItemStack(995, "Coins", 100_000L, 1L));

		WealthSnapshot wealth = WealthSnapshot.builder()
			.inventoryValue(200_000L)
			.equipmentValue(300_000L)
			.geInFlightValue(10_000L)
			.pouchValue(5_000L)
			.bankValue(1_000_000L)
			.bankKnown(true)
			.timestampMs(1_700_000L)
			.topHoldings(topHoldings)
			.build();

		return new SessionSnapshot(
			1_600_000L,
			120_000L,
			515_000L,
			15_450_000L,
			100_000L,
			1_615_000L,
			true,
			loot,
			xp,
			6912L,
			wealth);
	}

	@Test
	public void serialisesEveryFieldPerContractWithAccount()
	{
		SessionSnapshot snapshot = buildSnapshot();

		String json = IngestPayloadFactory.build(snapshot, "PhatWang", FIXED_INSTANT, GSON);
		JsonObject root = GSON.fromJson(json, JsonObject.class);

		assertEquals(1, root.get("schemaVersion").getAsInt());
		assertEquals("PhatWang", root.get("account").getAsString());
		assertEquals("2026-07-02T17:30:00Z", root.get("timestamp").getAsString());

		JsonObject session = root.getAsJsonObject("session");
		assertEquals(1_600_000L, session.get("startMs").getAsLong());
		assertEquals(120_000L, session.get("elapsedMs").getAsLong());
		assertEquals(515_000L, session.get("profit").getAsLong());
		assertEquals(15_450_000L, session.get("profitPerHour").getAsLong());
		assertEquals(100_000L, session.get("geRealizedPnl").getAsLong());
		assertEquals(1_615_000L, session.get("netWorthDelta").getAsLong());
		assertTrue(session.get("bankKnown").getAsBoolean());
		assertEquals(6912L, session.get("xpTotal").getAsLong());

		JsonObject xp = session.getAsJsonObject("xp");
		assertEquals(1234L, xp.get("ATTACK").getAsLong());
		assertEquals(5678L, xp.get("STRENGTH").getAsLong());

		JsonArray loot = session.getAsJsonArray("loot");
		assertEquals(2, loot.size());
		JsonObject firstLoot = loot.get(0).getAsJsonObject();
		assertEquals(4151, firstLoot.get("itemId").getAsInt());
		assertEquals("Abyssal whip", firstLoot.get("name").getAsString());
		assertEquals(1L, firstLoot.get("quantity").getAsLong());
		assertEquals(1_500_000L, firstLoot.get("value").getAsLong());
		assertEquals(1_000L, firstLoot.get("timestampMs").getAsLong());
		JsonObject secondLoot = loot.get(1).getAsJsonObject();
		assertEquals(995, secondLoot.get("itemId").getAsInt());
		assertEquals("Coins", secondLoot.get("name").getAsString());
		assertEquals(5000L, secondLoot.get("quantity").getAsLong());
		assertEquals(5000L, secondLoot.get("value").getAsLong());
		assertEquals(900L, secondLoot.get("timestampMs").getAsLong());

		JsonObject wealth = root.getAsJsonObject("wealth");
		assertEquals(200_000L, wealth.get("inventory").getAsLong());
		assertEquals(300_000L, wealth.get("equipment").getAsLong());
		assertEquals(10_000L, wealth.get("geInFlight").getAsLong());
		assertEquals(5_000L, wealth.get("pouch").getAsLong());
		assertEquals(1_000_000L, wealth.get("bank").getAsLong());
		assertTrue(wealth.get("bankKnown").getAsBoolean());
		// tracked = inventory + equipment + geInFlight + pouch
		assertEquals(515_000L, wealth.get("tracked").getAsLong());
		// netWorth = tracked + bank (bank known)
		assertEquals(1_515_000L, wealth.get("netWorth").getAsLong());

		JsonArray topHoldings = wealth.getAsJsonArray("topHoldings");
		assertEquals(2, topHoldings.size());
		JsonObject firstHolding = topHoldings.get(0).getAsJsonObject();
		assertEquals(4151, firstHolding.get("itemId").getAsInt());
		assertEquals("Abyssal whip", firstHolding.get("name").getAsString());
		assertEquals(1L, firstHolding.get("quantity").getAsLong());
		assertEquals(1_500_000L, firstHolding.get("value").getAsLong());
		JsonObject secondHolding = topHoldings.get(1).getAsJsonObject();
		assertEquals(995, secondHolding.get("itemId").getAsInt());
		assertEquals("Coins", secondHolding.get("name").getAsString());
		assertEquals(100_000L, secondHolding.get("quantity").getAsLong());
		assertEquals(100_000L, secondHolding.get("value").getAsLong());
	}

	@Test
	public void nullAccountIsSerialisedAsExplicitJsonNull()
	{
		SessionSnapshot snapshot = buildSnapshot();

		String json = IngestPayloadFactory.build(snapshot, null, FIXED_INSTANT, GSON);
		JsonObject root = GSON.fromJson(json, JsonObject.class);

		assertTrue("account key must be present even when null", root.has("account"));
		assertTrue(root.get("account").isJsonNull());
	}

	@Test
	public void emptyLootAndXpSerialiseAsEmptyCollectionsNotNull()
	{
		WealthSnapshot wealth = WealthSnapshot.builder().build();
		SessionSnapshot snapshot = new SessionSnapshot(
			0L, 0L, 0L, 0L, 0L, 0L, false, null, null, 0L, wealth);

		String json = IngestPayloadFactory.build(snapshot, null, FIXED_INSTANT, GSON);
		JsonObject root = GSON.fromJson(json, JsonObject.class);

		JsonObject session = root.getAsJsonObject("session");
		assertEquals(0, session.getAsJsonArray("loot").size());
		assertEquals(0, session.getAsJsonObject("xp").entrySet().size());
		assertFalse(session.get("bankKnown").getAsBoolean());

		JsonObject wealthJson = root.getAsJsonObject("wealth");
		assertEquals(0, wealthJson.getAsJsonArray("topHoldings").size());
		assertEquals(0L, wealthJson.get("tracked").getAsLong());
		assertEquals(0L, wealthJson.get("netWorth").getAsLong());
	}
}
