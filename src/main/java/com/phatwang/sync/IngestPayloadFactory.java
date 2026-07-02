package com.phatwang.sync;

import com.google.gson.Gson;
import com.phatwang.model.ItemStack;
import com.phatwang.session.LootEntry;
import com.phatwang.session.SessionSnapshot;
import com.phatwang.wealth.WealthSnapshot;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure translation from the plugin's internal domain types to the JSON wire
 * shape expected by the companion dashboard's ingest endpoint.
 *
 * <p>No RuneLite imports, no network, no clock reads: the timestamp is passed
 * in by the caller so this class is fully deterministic and unit-testable.
 */
public final class IngestPayloadFactory
{
	private static final int SCHEMA_VERSION = 1;

	private IngestPayloadFactory()
	{
	}

	/**
	 * Builds the ingest JSON body for a single session snapshot.
	 *
	 * @param snapshot  the session snapshot to serialise
	 * @param account   the player name, or {@code null} if not yet known
	 * @param timestamp the instant to stamp the payload with
	 * @param gson      the Gson instance to serialise with
	 * @return the JSON request body as a string
	 */
	public static String build(SessionSnapshot snapshot, String account, Instant timestamp, Gson gson)
	{
		// account is nullable and must still be a present, named field (not
		// merely absent) so the dashboard's pydantic model always sees it -
		// force null serialisation for this call regardless of the caller's
		// Gson configuration, while keeping any other adapters/config it set up.
		Gson nullSafeGson = gson.newBuilder().serializeNulls().create();
		return nullSafeGson.toJson(toPayload(snapshot, account, timestamp));
	}

	/**
	 * Builds the ingest payload DTO without serialising it, for callers that
	 * want to inspect or re-serialise it themselves.
	 */
	public static IngestPayload toPayload(SessionSnapshot snapshot, String account, Instant timestamp)
	{
		String isoTimestamp = OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC)
			.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

		return new IngestPayload(
			SCHEMA_VERSION,
			account,
			isoTimestamp,
			toSessionPayload(snapshot),
			toWealthPayload(snapshot.getWealth()));
	}

	private static SessionPayload toSessionPayload(SessionSnapshot snapshot)
	{
		List<LootPayload> loot = new ArrayList<>(snapshot.getLoot().size());
		for (LootEntry entry : snapshot.getLoot())
		{
			loot.add(new LootPayload(
				entry.getItemId(),
				entry.getName(),
				entry.getQuantity(),
				entry.getValue(),
				entry.getTimestampMs()));
		}

		return new SessionPayload(
			snapshot.getStartMs(),
			snapshot.getElapsedMs(),
			snapshot.getProfit(),
			snapshot.getProfitPerHour(),
			snapshot.getGeRealizedPnl(),
			snapshot.getNetWorthDelta(),
			snapshot.isBankKnown(),
			loot,
			snapshot.getXpGained(),
			snapshot.getXpTotal());
	}

	private static WealthPayload toWealthPayload(WealthSnapshot wealth)
	{
		if (wealth == null)
		{
			wealth = WealthSnapshot.builder().build();
		}

		List<HoldingPayload> topHoldings = new ArrayList<>(wealth.getTopHoldings().size());
		for (ItemStack item : wealth.getTopHoldings())
		{
			topHoldings.add(new HoldingPayload(
				item.getId(),
				item.getName(),
				item.getQuantity(),
				item.value()));
		}

		return new WealthPayload(
			wealth.getInventoryValue(),
			wealth.getEquipmentValue(),
			wealth.getGeInFlightValue(),
			wealth.getPouchValue(),
			wealth.getBankValue(),
			wealth.isBankKnown(),
			wealth.tracked(),
			wealth.netWorth(),
			topHoldings);
	}

	/**
	 * Top-level ingest request body.
	 */
	public static final class IngestPayload
	{
		final int schemaVersion;
		final String account;
		final String timestamp;
		final SessionPayload session;
		final WealthPayload wealth;

		IngestPayload(int schemaVersion, String account, String timestamp, SessionPayload session, WealthPayload wealth)
		{
			this.schemaVersion = schemaVersion;
			this.account = account;
			this.timestamp = timestamp;
			this.session = session;
			this.wealth = wealth;
		}
	}

	/**
	 * The {@code session} object of the ingest payload.
	 */
	public static final class SessionPayload
	{
		final long startMs;
		final long elapsedMs;
		final long profit;
		final long profitPerHour;
		final long geRealizedPnl;
		final long netWorthDelta;
		final boolean bankKnown;
		final List<LootPayload> loot;
		final Map<String, Long> xp;
		final long xpTotal;

		SessionPayload(
			long startMs,
			long elapsedMs,
			long profit,
			long profitPerHour,
			long geRealizedPnl,
			long netWorthDelta,
			boolean bankKnown,
			List<LootPayload> loot,
			Map<String, Long> xp,
			long xpTotal)
		{
			this.startMs = startMs;
			this.elapsedMs = elapsedMs;
			this.profit = profit;
			this.profitPerHour = profitPerHour;
			this.geRealizedPnl = geRealizedPnl;
			this.netWorthDelta = netWorthDelta;
			this.bankKnown = bankKnown;
			this.loot = loot;
			this.xp = xp;
			this.xpTotal = xpTotal;
		}
	}

	/**
	 * A single entry of {@code session.loot}.
	 */
	public static final class LootPayload
	{
		final int itemId;
		final String name;
		final long quantity;
		final long value;
		final long timestampMs;

		LootPayload(int itemId, String name, long quantity, long value, long timestampMs)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.value = value;
			this.timestampMs = timestampMs;
		}
	}

	/**
	 * The {@code wealth} object of the ingest payload.
	 */
	public static final class WealthPayload
	{
		final long inventory;
		final long equipment;
		final long geInFlight;
		final long pouch;
		final long bank;
		final boolean bankKnown;
		final long tracked;
		final long netWorth;
		final List<HoldingPayload> topHoldings;

		WealthPayload(
			long inventory,
			long equipment,
			long geInFlight,
			long pouch,
			long bank,
			boolean bankKnown,
			long tracked,
			long netWorth,
			List<HoldingPayload> topHoldings)
		{
			this.inventory = inventory;
			this.equipment = equipment;
			this.geInFlight = geInFlight;
			this.pouch = pouch;
			this.bank = bank;
			this.bankKnown = bankKnown;
			this.tracked = tracked;
			this.netWorth = netWorth;
			this.topHoldings = topHoldings;
		}
	}

	/**
	 * A single entry of {@code wealth.topHoldings}.
	 */
	public static final class HoldingPayload
	{
		final int itemId;
		final String name;
		final long quantity;
		final long value;

		HoldingPayload(int itemId, String name, long quantity, long value)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.value = value;
		}
	}
}
