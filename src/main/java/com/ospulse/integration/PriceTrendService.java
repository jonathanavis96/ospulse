package com.ospulse.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ospulse.OSPulseConfig;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Optional, off-by-default source of per-item price trend percentages, backed
 * by the OSRS Wiki prices API's 24h timeseries endpoint.
 *
 * <p>Entirely opt-in: while {@link OSPulseConfig#priceTrendEnabled()} is
 * {@code false} this class performs no HTTP calls at all and every lookup
 * returns {@link OptionalDouble#empty()}. When enabled, fetches are async
 * (okhttp {@code enqueue}, never {@code execute}) so nothing ever blocks the
 * RuneLite client/EDT thread, results are cached per item id with a ~1h TTL,
 * and in-flight requests are de-duplicated so pagination or repeated
 * {@link #prefetch(Collection)} calls don't pile up duplicate requests.
 *
 * <p>All failure modes (disabled config, network errors, malformed JSON,
 * non-2xx responses) degrade to "no trend" for that item; nothing here ever
 * throws back into the caller.
 */
@Slf4j
public final class PriceTrendService
{
	private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1);
	private static final String USER_AGENT = "OSPulse RuneLite plugin (github.com/jonathanavis96/ospulse)";
	private static final String ENDPOINT =
		"https://prices.runescape.wiki/api/v1/osrs/timeseries?timestep=24h&id=";

	private final OkHttpClient httpClient;
	private final OSPulseConfig config;
	private final Gson gson;

	private final Map<Integer, CacheEntry> cache = new ConcurrentHashMap<>();
	private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();
	private final Set<Call> activeCalls = ConcurrentHashMap.newKeySet();

	private volatile Runnable onUpdate = () -> {};
	private volatile boolean stopped;

	public PriceTrendService(OkHttpClient httpClient, OSPulseConfig config, Gson gson)
	{
		this.httpClient = httpClient;
		this.config = config;
		this.gson = Objects.requireNonNull(gson, "gson");
	}

	/**
	 * Registers the (single) callback to invoke after a fetch completes,
	 * success or failure, so a UI observer can re-render. Called from
	 * whichever thread the underlying HTTP callback lands on; the caller is
	 * responsible for hopping back to the EDT if it touches Swing.
	 */
	public void setOnUpdate(Runnable r)
	{
		this.onUpdate = r == null ? () -> {} : r;
	}

	/**
	 * Kicks off async fetches for any of {@code itemIds} that aren't already
	 * cached (and fresh) or already in flight. No-ops entirely - no HTTP -
	 * while price trends are disabled.
	 */
	public void prefetch(Collection<Integer> itemIds)
	{
		if (!config.priceTrendEnabled() || itemIds == null || itemIds.isEmpty())
		{
			return;
		}

		long now = System.currentTimeMillis();
		for (Integer itemId : itemIds)
		{
			if (itemId == null || itemId <= 0)
			{
				continue;
			}

			CacheEntry entry = cache.get(itemId);
			if (entry != null && now - entry.fetchedAtMs < CACHE_TTL_MS)
			{
				continue;
			}

			if (!inFlight.add(itemId))
			{
				// Already being fetched by another prefetch() call.
				continue;
			}

			fetch(itemId);
		}
	}

	/**
	 * The cached trend percentage for {@code itemId}, or empty when price
	 * trends are disabled, the item is unknown, the fetch is still in
	 * flight, or the last fetch failed.
	 */
	public OptionalDouble getTrendPercent(int itemId)
	{
		if (!config.priceTrendEnabled())
		{
			return OptionalDouble.empty();
		}

		CacheEntry entry = cache.get(itemId);
		return entry == null ? OptionalDouble.empty() : entry.value;
	}

	private void fetch(int itemId)
	{
		Request request = new Request.Builder()
			.url(ENDPOINT + itemId)
			.header("User-Agent", USER_AGENT)
			.build();

		Call call = httpClient.newCall(request);
		activeCalls.add(call);
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				activeCalls.remove(call);
				log.debug("Price trend fetch failed for item {}", itemId, e);
				completeWith(itemId, OptionalDouble.empty());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				activeCalls.remove(call);
				OptionalDouble trend = OptionalDouble.empty();
				try (Response r = response)
				{
					if (r.isSuccessful() && r.body() != null)
					{
						trend = parseTrend(r.body().string());
					}
					else
					{
						log.debug("Price trend fetch got non-2xx for item {}: {}", itemId, r.code());
					}
				}
				catch (IOException e)
				{
					log.debug("Price trend fetch failed reading body for item {}", itemId, e);
				}
				catch (Exception e)
				{
					// Malformed JSON etc. - never let a parse failure propagate.
					log.debug("Price trend parse failed for item {}", itemId, e);
				}
				completeWith(itemId, trend);
			}
		});
	}

	private void completeWith(int itemId, OptionalDouble trend)
	{
		cache.put(itemId, new CacheEntry(trend, System.currentTimeMillis()));
		inFlight.remove(itemId);
		// Don't fire the UI callback after shutdown(): a late okhttp completion
		// must not run Swing work (HoldingsSection::render) against a panel that
		// has already been detached by the plugin's shutDown().
		if (!stopped)
		{
			onUpdate.run();
		}
	}

	/**
	 * Cancels every in-flight fetch and permanently stops firing the update
	 * callback, so a late HTTP completion after the plugin's {@code shutDown()}
	 * never touches a detached panel. Idempotent; the plugin calls this from
	 * {@code shutDown()}.
	 */
	public void shutdown()
	{
		stopped = true;
		for (Call call : activeCalls)
		{
			call.cancel();
		}
		activeCalls.clear();
		inFlight.clear();
	}

	/**
	 * {@code mid = avg(avgHighPrice, avgLowPrice)} (skipping nulls) for the
	 * latest point, versus the point closest to the configured window's
	 * start, expressed as a percentage change.
	 */
	private OptionalDouble parseTrend(String body)
	{
		if (body == null || body.isEmpty())
		{
			return OptionalDouble.empty();
		}

		JsonObject root = gson.fromJson(body, JsonObject.class);
		if (root == null || !root.has("data") || !root.get("data").isJsonArray())
		{
			return OptionalDouble.empty();
		}

		JsonArray data = root.getAsJsonArray("data");
		if (data.size() == 0)
		{
			return OptionalDouble.empty();
		}

		long windowDays = Math.max(1, config.priceTrendWindow().days());
		long nowSec = System.currentTimeMillis() / 1000L;
		long targetSec = nowSec - TimeUnit.DAYS.toSeconds(windowDays);

		Double latestMid = null;
		Double windowStartMid = null;
		long bestDeltaSec = Long.MAX_VALUE;

		for (int i = 0; i < data.size(); i++)
		{
			if (!data.get(i).isJsonObject())
			{
				continue;
			}
			JsonObject point = data.get(i).getAsJsonObject();
			Double mid = mid(point);
			if (mid == null)
			{
				continue;
			}

			// Data is ascending by timestamp, so the last valid mid we see is
			// the latest one.
			latestMid = mid;

			long ts = numeric(point, "timestamp") == null ? 0L : numeric(point, "timestamp").longValue();
			long deltaSec = Math.abs(ts - targetSec);
			if (deltaSec < bestDeltaSec)
			{
				bestDeltaSec = deltaSec;
				windowStartMid = mid;
			}
		}

		if (latestMid == null || windowStartMid == null || windowStartMid == 0.0)
		{
			return OptionalDouble.empty();
		}

		double pct = (latestMid - windowStartMid) / windowStartMid * 100.0;
		return OptionalDouble.of(pct);
	}

	private static Double mid(JsonObject point)
	{
		Double high = numeric(point, "avgHighPrice");
		Double low = numeric(point, "avgLowPrice");
		if (high != null && low != null)
		{
			return (high + low) / 2.0;
		}
		return high != null ? high : low;
	}

	private static Double numeric(JsonObject point, String key)
	{
		if (!point.has(key) || point.get(key).isJsonNull())
		{
			return null;
		}
		try
		{
			return point.get(key).getAsDouble();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static final class CacheEntry
	{
		private final OptionalDouble value;
		private final long fetchedAtMs;

		private CacheEntry(OptionalDouble value, long fetchedAtMs)
		{
			this.value = value;
			this.fetchedAtMs = fetchedAtMs;
		}
	}
}
