package com.ospulse.sync;

import com.google.gson.Gson;
import com.ospulse.OSPulseConfig;
import com.ospulse.session.SessionListener;
import com.ospulse.session.SessionSnapshot;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Optional, off-by-default sync of {@link SessionSnapshot}s to a self-hosted
 * companion dashboard over HTTPS.
 *
 * <p>Implements {@link SessionListener} so it can be wired directly into the
 * session engine. {@link #onSessionUpdate(SessionSnapshot)} is expected to be
 * called on the RuneLite client thread; this class never performs network I/O
 * on the calling thread. Instead it stashes the latest snapshot and hands the
 * actual HTTP call off to a single background thread it owns, throttled to at
 * most one send per {@link OSPulseConfig#syncIntervalSeconds()}.
 *
 * <p>The throttle is leading edge <em>plus</em> a trailing-edge retry: an
 * update landing inside the throttle window schedules exactly one deferred
 * flush for when the window reopens, so the final snapshot before an idle
 * gap or logout is still shipped rather than silently dropped. On
 * {@link #shutdown()} any still-pending snapshot gets one best-effort send.
 *
 * <p>All failure modes (disabled config, blank URL, network errors, non-2xx
 * responses) are swallowed and logged - a sync problem must never propagate
 * back into the game thread or interrupt play.
 */
@Slf4j
public final class DashboardSyncService implements SessionListener
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	/**
	 * Sends one serialised snapshot. Package seam so tests can count sends
	 * without any network; production uses {@link #httpSend(SessionSnapshot)}.
	 */
	@FunctionalInterface
	interface Sender
	{
		void send(SessionSnapshot snapshot);
	}

	private final OkHttpClient httpClient;
	private final OSPulseConfig config;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Sender sender;

	/**
	 * Latest snapshot awaiting send, or {@code null} once consumed by a send.
	 * Overwritten on every update so a send always ships the freshest data,
	 * never a stale one; cleared on send so a trailing flush with nothing new
	 * to say sends nothing.
	 */
	private volatile SessionSnapshot pendingSnapshot;

	/**
	 * Player name to stamp onto outgoing payloads, or {@code null} if not yet
	 * known. Set via {@link #setAccount(String)}; the plugin lifecycle owner
	 * is responsible for keeping it current.
	 */
	private volatile String account;

	private volatile long lastSendAtMs;

	/**
	 * True while a trailing-edge flush is scheduled. Only read/written on the
	 * single executor thread, so no synchronisation is needed.
	 */
	private boolean trailingFlushScheduled;

	/**
	 * Builds a sync service with its own single-thread background executor.
	 *
	 * @param httpClient RuneLite's shared OkHttp client
	 * @param config     the plugin config (read live on every update/send)
	 * @param gson       Gson instance used to serialise outgoing payloads
	 */
	public DashboardSyncService(OkHttpClient httpClient, OSPulseConfig config, Gson gson)
	{
		this(httpClient, config, gson, Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "ospulse-sync");
			t.setDaemon(true);
			return t;
		}), null);
	}

	/**
	 * Visible for testing: allows injecting a controllable executor and a
	 * network-free {@link Sender} ({@code null} = real HTTP send).
	 */
	DashboardSyncService(OkHttpClient httpClient, OSPulseConfig config, Gson gson,
						ScheduledExecutorService executor, Sender sender)
	{
		this.httpClient = httpClient;
		this.config = config;
		this.gson = gson;
		this.executor = executor;
		this.sender = sender != null ? sender : this::httpSend;
	}

	/**
	 * Sets the player name to stamp onto outgoing payloads. May be
	 * {@code null} if unknown.
	 */
	public void setAccount(String account)
	{
		this.account = account;
	}

	/**
	 * Called on the RuneLite client thread whenever a fresh snapshot is
	 * available. Does no I/O inline: if sync is enabled and configured, the
	 * snapshot is stashed and a background flush attempt is queued.
	 */
	@Override
	public void onSessionUpdate(SessionSnapshot snapshot)
	{
		if (!config.syncEnabled() || isBlank(config.syncUrl()))
		{
			return;
		}

		pendingSnapshot = snapshot;
		try
		{
			executor.execute(this::flushIfDue);
		}
		catch (RejectedExecutionException e)
		{
			// Shutdown raced a late session update - never leak into the client thread.
			log.debug("Dashboard sync executor already shut down", e);
		}
	}

	/**
	 * Runs on the background executor thread. Re-checks gating and the
	 * throttle window; if due, POSTs the most recently stashed snapshot, and
	 * if throttled, schedules exactly one trailing-edge flush for the moment
	 * the window reopens (otherwise the last update before an idle gap or
	 * logout would only ever ship if ANOTHER update happened to arrive later).
	 */
	private void flushIfDue()
	{
		if (!config.syncEnabled() || isBlank(config.syncUrl()))
		{
			return;
		}

		long intervalMs = TimeUnit.SECONDS.toMillis(Math.max(1, config.syncIntervalSeconds()));
		long now = System.currentTimeMillis();
		if (!shouldSend(lastSendAtMs, now, intervalMs))
		{
			if (!trailingFlushScheduled)
			{
				trailingFlushScheduled = true;
				long delayMs = Math.max(1L, lastSendAtMs + intervalMs - now);
				try
				{
					executor.schedule(() ->
					{
						trailingFlushScheduled = false;
						flushIfDue();
					}, delayMs, TimeUnit.MILLISECONDS);
				}
				catch (RejectedExecutionException e)
				{
					trailingFlushScheduled = false;
					log.debug("Dashboard sync executor already shut down", e);
				}
			}
			return;
		}

		flushNow();
	}

	/**
	 * Runs on the background executor thread (steady state) or during
	 * {@link #shutdown()}'s final drain: consumes and sends the pending
	 * snapshot, ignoring the throttle window.
	 */
	private void flushNow()
	{
		if (!config.syncEnabled() || isBlank(config.syncUrl()))
		{
			return;
		}

		SessionSnapshot snapshot = pendingSnapshot;
		if (snapshot == null)
		{
			return;
		}

		pendingSnapshot = null;
		lastSendAtMs = System.currentTimeMillis();
		sender.send(snapshot);
	}

	/**
	 * Pure throttle decision, extracted for testability: are we due to send
	 * again given the last send time and the configured interval?
	 */
	static boolean shouldSend(long lastSendAtMs, long nowMs, long intervalMs)
	{
		return nowMs - lastSendAtMs >= intervalMs;
	}

	private void httpSend(SessionSnapshot snapshot)
	{
		try
		{
			String json = IngestPayloadFactory.build(snapshot, account, Instant.now(), gson);
			Request request = new Request.Builder()
				.url(config.syncUrl())
				.header("Authorization", "Bearer " + config.syncToken())
				.header("Content-Type", "application/json")
				.post(RequestBody.create(JSON, json))
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					log.debug("Dashboard sync got non-2xx response: {}", response.code());
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Dashboard sync request failed", e);
		}
		catch (Exception e)
		{
			// Never let a sync failure escape and affect the plugin/game thread.
			log.warn("Unexpected error during dashboard sync", e);
		}
	}

	/**
	 * Queues one best-effort final flush of any pending snapshot (so the tail
	 * of the session isn't lost), then stops the background executor. Safe to
	 * call from the plugin's {@code shutDown()}.
	 */
	public void shutdown()
	{
		try
		{
			executor.execute(this::flushNow);
		}
		catch (RejectedExecutionException ignored)
		{
			// Already shut down - nothing left to drain.
		}
		executor.shutdown();
		try
		{
			if (!executor.awaitTermination(2, TimeUnit.SECONDS))
			{
				executor.shutdownNow();
			}
		}
		catch (InterruptedException e)
		{
			// Teardown path: nothing downstream re-checks the interrupt flag, and the
			// RuneLite Plugin Hub bytecode scanner rejects Thread.interrupt() calls
			// (even the "restore the flag" idiom), so we swallow it here.
			executor.shutdownNow();
		}
	}

	private static boolean isBlank(String s)
	{
		return s == null || s.trim().isEmpty();
	}
}
