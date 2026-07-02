package com.ospulse.sync;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link PairingClient#parseResponse(String, Gson)}, the pure
 * JSON-parsing seam of the pairing redeem flow. No network calls are made:
 * this exercises exactly what {@code onResponse} does with a response body it
 * has already read into a string.
 */
public class PairingClientTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void parsesTokenAndIngestUrlFromWireShape()
	{
		String body = "{\"token\":\"abc123\",\"ingest_url\":\"http://100.67.160.92:8701/api/ingest/session\"}";

		PairingClient.RedeemResponse parsed = PairingClient.parseResponse(body, GSON);

		assertEquals("abc123", parsed.token);
		assertEquals("http://100.67.160.92:8701/api/ingest/session", parsed.ingestUrl);
	}

	@Test
	public void ignoresUnknownFields()
	{
		String body = "{\"token\":\"tok\",\"ingest_url\":\"http://host/api/ingest/session\",\"extra\":\"ignored\"}";

		PairingClient.RedeemResponse parsed = PairingClient.parseResponse(body, GSON);

		assertEquals("tok", parsed.token);
		assertEquals("http://host/api/ingest/session", parsed.ingestUrl);
	}

	@Test
	public void missingFieldsParseAsNullRatherThanThrowing()
	{
		String body = "{\"token\":\"tok\"}";

		PairingClient.RedeemResponse parsed = PairingClient.parseResponse(body, GSON);

		assertEquals("tok", parsed.token);
		assertNull(parsed.ingestUrl);
	}

	@Test
	public void malformedJsonReturnsNullRatherThanThrowing()
	{
		PairingClient.RedeemResponse parsed = PairingClient.parseResponse("not json", GSON);

		assertNull(parsed);
	}

	@Test
	public void emptyBodyReturnsNullRatherThanThrowing()
	{
		PairingClient.RedeemResponse parsed = PairingClient.parseResponse("", GSON);

		assertNull(parsed);
	}
}
