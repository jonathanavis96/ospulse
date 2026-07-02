package com.phatwang.sync;

import com.google.gson.Gson;
import com.phatwang.PhatWangConfig;
import com.phatwang.session.SessionSnapshot;
import com.phatwang.wealth.WealthSnapshot;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link DashboardSyncService}'s gating and throttling logic.
 *
 * <p>No real network calls are made: the OkHttp client is never exercised in
 * the disabled/blank-url paths under test here, and the background executor
 * is replaced with a counting fake so we can assert whether work was queued
 * without actually running it.
 */
public class DashboardSyncServiceTest
{
	private static final Gson GSON = new Gson();

	/** Real OkHttpClient instance; never actually issues a call in these tests. */
	private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

	private static PhatWangConfig configWith(boolean enabled, String url, int intervalSeconds)
	{
		return new PhatWangConfig()
		{
			@Override
			public boolean syncEnabled()
			{
				return enabled;
			}

			@Override
			public String syncUrl()
			{
				return url;
			}

			@Override
			public int syncIntervalSeconds()
			{
				return intervalSeconds;
			}
		};
	}

	private static SessionSnapshot emptySnapshot()
	{
		return new SessionSnapshot(
			0L, 0L, 0L, 0L, 0L, 0L, true,
			null, null, 0L,
			WealthSnapshot.builder().build());
	}

	/**
	 * Counting fake executor: records how many tasks were submitted but never
	 * runs them, so we can assert whether {@code onSessionUpdate} queued
	 * background work without ever touching the network.
	 */
	private static final class CountingExecutor extends AbstractExecutorService
	{
		final AtomicInteger submitted = new AtomicInteger();
		private volatile boolean shutdown;

		@Override
		public void execute(Runnable command)
		{
			submitted.incrementAndGet();
		}

		@Override
		public void shutdown()
		{
			shutdown = true;
		}

		@Override
		public List<Runnable> shutdownNow()
		{
			shutdown = true;
			return List.of();
		}

		@Override
		public boolean isShutdown()
		{
			return shutdown;
		}

		@Override
		public boolean isTerminated()
		{
			return shutdown;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit)
		{
			return true;
		}
	}

	@Test
	public void disabledConfigDoesNotQueueAnyWork()
	{
		CountingExecutor executor = new CountingExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(false, "https://example.com/ingest", 60), GSON, executor);

		service.onSessionUpdate(emptySnapshot());

		assertEquals(0, executor.submitted.get());
	}

	@Test
	public void blankUrlDoesNotQueueAnyWork()
	{
		CountingExecutor executor = new CountingExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "   ", 60), GSON, executor);

		service.onSessionUpdate(emptySnapshot());

		assertEquals(0, executor.submitted.get());
	}

	@Test
	public void enabledWithUrlQueuesBackgroundWork()
	{
		CountingExecutor executor = new CountingExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "https://example.com/ingest", 60), GSON, executor);

		service.onSessionUpdate(emptySnapshot());

		assertEquals(1, executor.submitted.get());
	}

	@Test
	public void shutdownStopsTheExecutor()
	{
		CountingExecutor executor = new CountingExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(false, "", 60), GSON, executor);

		service.shutdown();

		assertTrue(executor.isShutdown());
	}

	// ---------------------------------------------------- shouldSend(...) throttle

	@Test
	public void shouldSendIsFalseBeforeIntervalElapses()
	{
		long lastSendAtMs = 10_000L;
		long intervalMs = 60_000L;
		long now = lastSendAtMs + 30_000L;

		assertFalse(DashboardSyncService.shouldSend(lastSendAtMs, now, intervalMs));
	}

	@Test
	public void shouldSendIsTrueExactlyAtIntervalBoundary()
	{
		long lastSendAtMs = 10_000L;
		long intervalMs = 60_000L;
		long now = lastSendAtMs + intervalMs;

		assertTrue(DashboardSyncService.shouldSend(lastSendAtMs, now, intervalMs));
	}

	@Test
	public void shouldSendIsTrueAfterIntervalElapses()
	{
		long lastSendAtMs = 10_000L;
		long intervalMs = 60_000L;
		long now = lastSendAtMs + 61_000L;

		assertTrue(DashboardSyncService.shouldSend(lastSendAtMs, now, intervalMs));
	}

	@Test
	public void shouldSendIsTrueOnFirstSendOnceIntervalWorthOfTimeHasPassedSinceEpoch()
	{
		// lastSendAtMs == 0 represents "never sent yet"; in real usage nowMs
		// is the current wall-clock epoch millis, so this is always far past
		// the interval on the very first check. Verify the same holds for the
		// pure decision once the elapsed time exceeds the interval.
		assertTrue(DashboardSyncService.shouldSend(0L, 70_000L, 60_000L));
	}

	@Test
	public void shouldSendIsFalseOnFirstCheckBeforeIntervalWorthOfTimeHasPassed()
	{
		assertFalse(DashboardSyncService.shouldSend(0L, 5_000L, 60_000L));
	}
}
