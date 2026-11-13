package com.ospulse.integration;

import com.google.gson.Gson;
import com.ospulse.OSPulseConfig;
import com.ospulse.PriceTrendWindow;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cache behaviour of {@link PriceTrendService}, with the HTTP client stubbed. */
public class PriceTrendServiceTest
{
	private static final int ITEM = 4151;

	private OSPulseConfig config;
	private PriceTrendService service;
	private final List<Callback> pending = new ArrayList<>();
	private long nowMs = 1_700_000_000_000L;

	@Before
	public void setUp()
	{
		OkHttpClient http = mock(OkHttpClient.class);
		when(http.newCall(any(Request.class))).thenAnswer(inv ->
		{
			Call call = mock(Call.class);
			when(call.request()).thenReturn(inv.getArgument(0));
			doAnswer(e ->
			{
				pending.add(e.getArgument(0));
				return null;
			}).when(call).enqueue(any(Callback.class));
			return call;
		});
		config = mock(OSPulseConfig.class);
		when(config.priceTrendEnabled()).thenReturn(true);
		when(config.priceTrendWindow()).thenReturn(PriceTrendWindow.WEEK);
		service = new PriceTrendService(http, config, new Gson());
		service.clock = () -> nowMs;
	}

	/** Mid 100 a week ago, 150 a day ago, 200 now. */
	private String series()
	{
		long now = nowMs / 1000L;
		return "{\"data\":["
			+ point(now - TimeUnit.DAYS.toSeconds(7), 100) + ","
			+ point(now - TimeUnit.DAYS.toSeconds(1), 150) + ","
			+ point(now, 200) + "]}";
	}

	private static String point(long ts, int price)
	{
		return "{\"timestamp\":" + ts + ",\"avgHighPrice\":" + price + ",\"avgLowPrice\":" + price + "}";
	}

	private void respond(int code, String body) throws IOException
	{
		Callback cb = pending.remove(0);
		Request req = new Request.Builder().url("https://example.invalid/").build();
		Response response = new Response.Builder()
			.request(req)
			.protocol(Protocol.HTTP_1_1)
			.code(code)
			.message("x")
			.body(ResponseBody.create(MediaType.parse("application/json"), body))
			.build();
		cb.onResponse(mock(Call.class), response);
	}

	@Test
	public void changingTheWindowRefetchesInsteadOfShowingTheOldWindowsTrend() throws IOException
	{
		service.prefetch(Collections.singletonList(ITEM));
		respond(200, series());
		assertEquals(100.0, service.getTrendPercent(ITEM).getAsDouble(), 1e-9);

		when(config.priceTrendWindow()).thenReturn(PriceTrendWindow.DAY);
		assertFalse("a 7-day trend must not be shown as the 24h one",
			service.getTrendPercent(ITEM).isPresent());
		service.prefetch(Collections.singletonList(ITEM));
		assertEquals("the new window triggers a fresh fetch", 1, pending.size());
		respond(200, series());

		assertEquals(100.0 / 3, service.getTrendPercent(ITEM).getAsDouble(), 1e-9);
	}

	@Test
	public void aFailedFetchIsRetriedWellBeforeTheHourIsUp() throws IOException
	{
		service.prefetch(Collections.singletonList(ITEM));
		pending.remove(0).onFailure(mock(Call.class), new IOException("network blip"));
		assertFalse(service.getTrendPercent(ITEM).isPresent());

		nowMs += TimeUnit.MINUTES.toMillis(10);
		service.prefetch(Collections.singletonList(ITEM));

		assertEquals("a failed fetch must be retried", 1, pending.size());
		respond(200, series());
		OptionalDouble trend = service.getTrendPercent(ITEM);
		assertEquals(100.0, trend.getAsDouble(), 1e-9);
	}

	@Test
	public void aSuccessfulFetchIsStillCachedForTheHour() throws IOException
	{
		service.prefetch(Collections.singletonList(ITEM));
		respond(200, series());

		nowMs += TimeUnit.MINUTES.toMillis(10);
		service.prefetch(Collections.singletonList(ITEM));

		assertEquals(0, pending.size());
	}
}
