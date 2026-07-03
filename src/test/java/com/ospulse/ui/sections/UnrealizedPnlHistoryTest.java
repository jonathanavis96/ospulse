package com.ospulse.ui.sections;

import org.junit.Test;

import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers QA fix 7: Unrealized P/L should remember its value from the
 * player's last login and show a "since last login" delta. This exercises
 * the pure delta/parsing/formatting logic in isolation from
 * {@code ConfigManager} and Swing.
 */
public class UnrealizedPnlHistoryTest
{
	@Test
	public void noPriorValueMeansNoDelta()
	{
		assertTrue(UnrealizedPnlHistory.delta(500_000L, OptionalLong.empty()).isEmpty());
	}

	@Test
	public void positiveDeltaWhenValueIncreasedSincePreviousLogin()
	{
		OptionalLong delta = UnrealizedPnlHistory.delta(1_500_000L, OptionalLong.of(1_000_000L));
		assertEquals(500_000L, delta.getAsLong());
	}

	@Test
	public void negativeDeltaWhenValueDecreasedSincePreviousLogin()
	{
		OptionalLong delta = UnrealizedPnlHistory.delta(800_000L, OptionalLong.of(1_000_000L));
		assertEquals(-200_000L, delta.getAsLong());
	}

	@Test
	public void zeroDeltaWhenUnchanged()
	{
		OptionalLong delta = UnrealizedPnlHistory.delta(1_000_000L, OptionalLong.of(1_000_000L));
		assertEquals(0L, delta.getAsLong());
	}

	@Test
	public void parseStoredHandlesNullBlankAndGarbageAsFirstLogin()
	{
		assertTrue(UnrealizedPnlHistory.parseStored(null).isEmpty());
		assertTrue(UnrealizedPnlHistory.parseStored("").isEmpty());
		assertTrue(UnrealizedPnlHistory.parseStored("   ").isEmpty());
		assertTrue(UnrealizedPnlHistory.parseStored("not-a-number").isEmpty());
	}

	@Test
	public void parseStoredParsesValidLong()
	{
		assertEquals(1_234_567L, UnrealizedPnlHistory.parseStored("1234567").getAsLong());
		assertEquals(-42L, UnrealizedPnlHistory.parseStored(" -42 ").getAsLong());
	}

	@Test
	public void labelForNoDeltaShowsDash()
	{
		assertEquals("since last login: —", UnrealizedPnlHistory.label(OptionalLong.empty()));
	}

	@Test
	public void labelForPositiveDeltaHasPlusSign()
	{
		// Delegates gp formatting to GpFormat, which switches to a compact
		// suffix at the 100k threshold - matches every other badge in this panel.
		assertEquals("since last login: +500k", UnrealizedPnlHistory.label(OptionalLong.of(500_000L)));
	}

	@Test
	public void labelForNegativeDeltaKeepsMinusSign()
	{
		assertEquals("since last login: -200k", UnrealizedPnlHistory.label(OptionalLong.of(-200_000L)));
	}

	@Test
	public void labelForSmallPositiveDeltaUsesCommaFormat()
	{
		assertEquals("since last login: +1,234", UnrealizedPnlHistory.label(OptionalLong.of(1_234L)));
	}
}
