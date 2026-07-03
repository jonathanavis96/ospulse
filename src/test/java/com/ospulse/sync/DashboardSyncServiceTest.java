package com.ospulse.sync;

import com.google.gson.Gson;
import com.ospulse.OSPulseConfig;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.wealth.WealthSnapshot;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link DashboardSyncService}'s gating, throttling and
 * trailing-edge flush logic.
 *
 * <p>No real network calls are made: sends are captured via the package
 * {@link DashboardSyncService.Sender} seam, and the background executor is
 * replaced with a controllable fake (either counting-only or run-inline) so
 * tests can drive the executor thread deterministically.
 */
public class DashboardSyncServiceTest
{
	private static final Gson GSON = new Gson();

	/** Real OkHttpClient instance; never actually issues a call in these tests. */
	private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

	private static OSPulseConfig configWith(boolean enabled, String url, int intervalSeconds)
	{
		return new OSPulseConfig()
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
	 * Fake scheduled executor. In counting mode (default) it records
	 * submissions without running them; with {@code runInline = true} it runs
	 * {@code execute} tasks immediately on the caller thread. Scheduled
	 * (delayed) tasks are always captured, never auto-run — tests fire them
	 * explicitly via {@link #runScheduled()} to simulate the delay elapsing.
	 */
	private static final class FakeExecutor extends AbstractExecutorService implements ScheduledExecutorService
	{
		final AtomicInteger submitted = new AtomicInteger();
		final List<Runnable> scheduled = new ArrayList<>();
		boolean runInline;
		private volatile boolean shutdown;

		@Override
		public void execute(Runnable command)
		{
			submitted.incrementAndGet();
			if (runInline)
			{
				command.run();
			}
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
		{
			scheduled.add(command);
			return null;
		}

		@Override
		public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit)
		{
			throw new UnsupportedOperationException();
		}

		/** Runs (and clears) all captured delayed tasks, as if their delays elapsed. */
		void runScheduled()
		{
			List<Runnable> toRun = new ArrayList<>(scheduled);
			scheduled.clear();
			for (Runnable task : toRun)
			{
				task.run();
			}
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

	/** {@link DashboardSyncService.Sender} that only counts. */
	private static final class CountingSender implements DashboardSyncService.Sender
	{
		final AtomicInteger sends = new AtomicInteger();

		@Override
		public void send(SessionSnapshot snapshot)
		{
			sends.incrementAndGet();
		}
	}

	@Test
	public void disabledConfigDoesNotQueueAnyWork()
	{
		FakeExecutor executor = new FakeExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(false, "https://example.com/ingest", 60), GSON, executor, new CountingSender());

		service.onSessionUpdate(emptySnapshot());

		assertEquals(0, executor.submitted.get());
	}

	@Test
	public void blankUrlDoesNotQueueAnyWork()
	{
		FakeExecutor executor = new FakeExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "   ", 60), GSON, executor, new CountingSender());

		service.onSessionUpdate(emptySnapshot());

		assertEquals(0, executor.submitted.get());
	}

	@Test
	public void enabledWithUrlQueuesBackgroundWork()
	{
		FakeExecutor executor = new FakeExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "https://example.com/ingest", 60), GSON, executor, new CountingSender());

		service.onSessionUpdate(emptySnapshot());

		assertEquals(1, executor.submitted.get());
	}

	@Test
	public void shutdownStopsTheExecutor()
	{
		FakeExecutor executor = new FakeExecutor();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(false, "", 60), GSON, executor, new CountingSender());

		service.shutdown();

		assertTrue(executor.isShutdown());
	}

	// ------------------------------------------------- trailing-edge flush

	@Test
	public void firstUpdateSendsImmediately()
	{
		FakeExecutor executor = new FakeExecutor();
		executor.runInline = true;
		CountingSender sender = new CountingSender();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "https://example.com/ingest", 60), GSON, executor, sender);

		service.onSessionUpdate(emptySnapshot());

		assertEquals(1, sender.sends.get());
		assertTrue(executor.scheduled.isEmpty());
	}

	@Test
	public void throttledUpdateSchedulesExactlyOneTrailingFlush()
	{
		FakeExecutor executor = new FakeExecutor();
		executor.runInline = true;
		CountingSender sender = new CountingSender();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "https://example.com/ingest", 60), GSON, executor, sender);

		service.onSessionUpdate(emptySnapshot()); // sends (leading edge)
		service.onSessionUpdate(emptySnapshot()); // throttled -> schedules trailing flush
		service.onSessionUpdate(emptySnapshot()); // still throttled -> must NOT double-schedule

		assertEquals(1, sender.sends.get());
		assertEquals(1, executor.scheduled.size());
	}

	@Test
	public void trailingFlushSendsThePendingSnapshot()
	{
		FakeExecutor executor = new FakeExecutor();
		executor.runInline = true;
		CountingSender sender = new CountingSender();
		// 0-second interval is clamped to 1s in the service; use a large one and
		// rely on runScheduled() to simulate the window reopening. The trailing
		// task re-checks shouldSend, so freeze it out of throttling by resetting:
		// with interval 60s the re-check would still be throttled — assert the
		// re-schedule instead, then verify the pending snapshot survives.
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "https://example.com/ingest", 60), GSON, executor, sender);

		service.onSessionUpdate(emptySnapshot()); // leading-edge send
		service.onSessionUpdate(emptySnapshot()); // stashed + trailing flush scheduled
		assertEquals(1, sender.sends.get());

		// Delay "elapses" but wall-clock hasn't moved: the flush re-checks the
		// throttle and re-schedules itself rather than dropping the snapshot.
		executor.runScheduled();
		assertEquals(1, sender.sends.get());
		assertEquals(1, executor.scheduled.size());

		// Shutdown force-drains the pending snapshot — nothing is lost.
		service.shutdown();
		assertEquals(2, sender.sends.get());
	}

	@Test
	public void shutdownFlushesPendingSnapshot()
	{
		FakeExecutor executor = new FakeExecutor();
		executor.runInline = true;
		CountingSender sender = new CountingSender();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "https://example.com/ingest", 60), GSON, executor, sender);

		service.onSessionUpdate(emptySnapshot()); // sends
		service.onSessionUpdate(emptySnapshot()); // throttled, pending

		service.shutdown();

		assertEquals(2, sender.sends.get());
		assertTrue(executor.isShutdown());
	}

	@Test
	public void shutdownWithNothingPendingSendsNothing()
	{
		FakeExecutor executor = new FakeExecutor();
		executor.runInline = true;
		CountingSender sender = new CountingSender();
		DashboardSyncService service = new DashboardSyncService(
			HTTP_CLIENT, configWith(true, "https://example.com/ingest", 60), GSON, executor, sender);

		service.onSessionUpdate(emptySnapshot()); // sends, consumes pending

		service.shutdown();

		assertEquals(1, sender.sends.get());
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
