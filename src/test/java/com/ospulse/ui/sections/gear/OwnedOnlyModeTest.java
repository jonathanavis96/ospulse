package com.ospulse.ui.sections.gear;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure unit tests for {@link OwnedOnlyMode} (issue #11's ironman owned-only
 * optimiser mode) — no Swing/RuneLite needed, exactly the kind of
 * dependency-light logic the class exists to isolate from
 * {@code GearSection}.
 */
public class OwnedOnlyModeTest
{
	@Test
	public void effectiveBudget_forcesZero_whenOwnedOnly()
	{
		assertEquals(0L, OwnedOnlyMode.effectiveBudget(true, 50_000_000L));
	}

	@Test
	public void effectiveBudget_returnsStoredValue_whenNotOwnedOnly()
	{
		assertEquals(50_000_000L, OwnedOnlyMode.effectiveBudget(false, 50_000_000L));
	}

	@Test
	public void effectiveBudget_zeroStoredBudget_staysZeroEitherWay()
	{
		assertEquals(0L, OwnedOnlyMode.effectiveBudget(true, 0L));
		assertEquals(0L, OwnedOnlyMode.effectiveBudget(false, 0L));
	}

	@Test
	public void upgradeUiVisible_hiddenOnlyWhenOwnedOnly()
	{
		assertFalse(OwnedOnlyMode.upgradeUiVisible(true));
		assertTrue(OwnedOnlyMode.upgradeUiVisible(false));
	}

	@Test
	public void upgradeStatRowsVisible_hiddenWhenOwnedOnly_evenWithUsableResult()
	{
		assertFalse(OwnedOnlyMode.upgradeStatRowsVisible(true, true));
	}

	@Test
	public void upgradeStatRowsVisible_hiddenWhenNoUsableResult_regardlessOfMode()
	{
		assertFalse(OwnedOnlyMode.upgradeStatRowsVisible(false, false));
		assertFalse(OwnedOnlyMode.upgradeStatRowsVisible(true, false));
	}

	@Test
	public void upgradeStatRowsVisible_visibleOnlyWhenUsableAndNotOwnedOnly()
	{
		assertTrue(OwnedOnlyMode.upgradeStatRowsVisible(false, true));
	}
}
