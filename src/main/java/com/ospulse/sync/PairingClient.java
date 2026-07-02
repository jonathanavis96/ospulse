package com.ospulse.sync;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

/**
 * Redeems a 6-digit pairing code shown on the companion dashboard's "Connect
 * RuneLite" screen for a sync token + ingest URL, so the user never has to
 * hand-copy either value.
 *
 * <p>POSTs to {@code <serverBaseUrl>/api/pair/redeem} with body
 * {@code {"code": "123456"}} and expects back
 * {@code {"token": "...", "ingest_url": "..."}}. All failure modes (network
 * error, non-2xx response, malformed JSON) are reported through
 * {@link ResultCallback#onFailure(String)} rather than thrown, and the call is
 * always asynchronous - this never blocks the calling (client) thread.
 */
@Slf4j
public final class PairingClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final Gson gson;

	public PairingClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Outcome of a {@link #redeem(String, String, ResultCallback)} call.
	 * Invoked on an OkHttp dispatcher thread, never the calling thread.
	 */
	public interface ResultCallback
	{
		void onSuccess(String token, String ingestUrl);

		void onFailure(String message);
	}

	/**
	 * Asynchronously redeems {@code code} against {@code serverBaseUrl}.
	 * Never throws and never blocks: every failure path is funnelled through
	 * {@link ResultCallback#onFailure(String)}.
	 *
	 * @param serverBaseUrl the dashboard's base URL, e.g. {@code http://host:8701};
	 *                      a trailing slash is tolerated and stripped
	 * @param code          the 6-digit pairing code
	 * @param callback      notified of the outcome, on a background thread
	 */
	public void redeem(String serverBaseUrl, String code, ResultCallback callback)
	{
		String base = serverBaseUrl == null ? "" : serverBaseUrl.trim();
		while (base.endsWith("/"))
		{
			base = base.substring(0, base.length() - 1);
		}

		if (base.isEmpty())
		{
			callback.onFailure("Pairing server URL is blank");
			return;
		}

		String requestJson = gson.toJson(new RedeemRequest(code));

		Request request;
		try
		{
			request = new Request.Builder()
				.url(base + "/api/pair/redeem")
				.post(RequestBody.create(JSON, requestJson))
				.build();
		}
		catch (IllegalArgumentException e)
		{
			callback.onFailure("Invalid pairing server URL: " + e.getMessage());
			return;
		}

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				callback.onFailure("Could not reach pairing server: " + e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						callback.onFailure(describeHttpError(r.code()));
						return;
					}

					String body = r.body() != null ? r.body().string() : "";
					RedeemResponse parsed = parseResponse(body, gson);
					if (parsed == null || isBlank(parsed.token) || isBlank(parsed.ingestUrl))
					{
						callback.onFailure("Pairing server returned an unexpected response");
						return;
					}

					callback.onSuccess(parsed.token, parsed.ingestUrl);
				}
				catch (IOException e)
				{
					callback.onFailure("Failed to read pairing server response: " + e.getMessage());
				}
				catch (Exception e)
				{
					// Belt-and-braces: a pairing failure must never propagate
					// back into the game thread or interrupt play.
					log.warn("Unexpected error while redeeming pairing code", e);
					callback.onFailure("Unexpected error: " + e.getMessage());
				}
			}
		});
	}

	private static String describeHttpError(int code)
	{
		switch (code)
		{
			case 404:
				return "Pairing code not recognised - check the code and try again";
			case 400:
				return "Pairing code already used or expired - generate a new one on the dashboard";
			default:
				return "Pairing server returned HTTP " + code;
		}
	}

	/**
	 * Parses the {@code {"token", "ingest_url"}} response body. Package-visible
	 * and static so it can be unit-tested without a live server; returns
	 * {@code null} on malformed JSON rather than throwing.
	 */
	static RedeemResponse parseResponse(String body, Gson gson)
	{
		try
		{
			return gson.fromJson(body, RedeemResponse.class);
		}
		catch (JsonSyntaxException e)
		{
			return null;
		}
	}

	private static boolean isBlank(String s)
	{
		return s == null || s.trim().isEmpty();
	}

	private static final class RedeemRequest
	{
		final String code;

		RedeemRequest(String code)
		{
			this.code = code;
		}
	}

	/**
	 * Wire shape of a successful {@code /api/pair/redeem} response.
	 */
	static final class RedeemResponse
	{
		String token;

		@SerializedName("ingest_url")
		String ingestUrl;
	}
}
