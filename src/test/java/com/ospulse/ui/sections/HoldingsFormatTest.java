package com.ospulse.ui.sections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Covers QA fix 9: the "Top holdings" collapsed-header summary must show a
 * compact, uppercase-suffix value (e.g. {@code 2.1B}, {@code 950M}) short
 * enough - combined with the separate trend badge - to fit the fixed panel
 * width instead of overflowing/getting cut off.
 */
public class HoldingsFormatTest
{
	@Test
	public void zeroFormatsAsZero()
	{
		assertEquals("0", HoldingsFormat.compact(0L));
	}

	@Test
	public void subThousandValuesHaveNoSuffix()
	{
		assertEquals("999", HoldingsFormat.compact(999L));
	}

	@Test
	public void thousandsUseUppercaseKSuffix()
	{
		assertEquals("1K", HoldingsFormat.compact(1_000L));
		assertEquals("150K", HoldingsFormat.compact(150_000L));
	}

	@Test
	public void millionsUseUppercaseMSuffix()
	{
		assertEquals("950M", HoldingsFormat.compact(950_000_000L));
		assertEquals("1M", HoldingsFormat.compact(1_000_000L));
	}

	@Test
	public void billionsUseUppercaseBSuffixWithOneDecimal()
	{
		assertEquals("2.1B", HoldingsFormat.compact(2_100_000_000L));
		assertEquals("1B", HoldingsFormat.compact(1_000_000_000L));
	}

	@Test
	public void negativeValuesKeepSign()
	{
		assertEquals("-2.1B", HoldingsFormat.compact(-2_100_000_000L));
	}

	@Test
	public void roundingCarriesIntoNextMagnitude()
	{
		assertEquals("1M", HoldingsFormat.compact(999_950L));
		assertEquals("1B", HoldingsFormat.compact(999_999_999L));
	}
}
