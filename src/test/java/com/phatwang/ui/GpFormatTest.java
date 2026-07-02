package com.phatwang.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GpFormatTest
{
	@Test
	public void zeroFormatsAsZero()
	{
		assertEquals("0", GpFormat.format(0L));
	}

	@Test
	public void smallValuesUseThousandsSeparators()
	{
		assertEquals("1,234", GpFormat.format(1234L));
		assertEquals("99,999", GpFormat.format(99_999L));
	}

	@Test
	public void negativeValuesKeepSign()
	{
		assertEquals("-1,234", GpFormat.format(-1234L));
		assertEquals("-1.5m", GpFormat.format(-1_500_000L));
	}

	@Test
	public void hundredThousandBoundarySwitchesToKSuffix()
	{
		assertEquals("100k", GpFormat.format(100_000L));
	}

	@Test
	public void kValuesRoundToOneDecimal()
	{
		assertEquals("150k", GpFormat.format(150_000L));
		assertEquals("999.5k", GpFormat.format(999_500L));
	}

	@Test
	public void millionBoundarySwitchesToMSuffix()
	{
		assertEquals("1m", GpFormat.format(1_000_000L));
		assertEquals("1.5m", GpFormat.format(1_500_000L));
	}

	@Test
	public void billionBoundarySwitchesToBSuffix()
	{
		assertEquals("2b", GpFormat.format(2_000_000_000L));
	}

	@Test
	public void roundingCarriesIntoNextMagnitude()
	{
		// 999,950 rounds to 1000.0k, which should promote to 1m rather than
		// display as "1000k".
		assertEquals("1m", GpFormat.format(999_950L));
		// 999,999,999 rounds to 1000.0m, which should promote to 1b.
		assertEquals("1b", GpFormat.format(999_999_999L));
	}

	@Test
	public void roundsHalfUpToOneDecimal()
	{
		assertEquals("1.3m", GpFormat.format(1_250_000L));
	}
}
