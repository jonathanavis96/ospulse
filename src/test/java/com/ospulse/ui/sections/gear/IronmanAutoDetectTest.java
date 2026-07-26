package com.ospulse.ui.sections.gear;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure unit tests for {@link IronmanAutoDetect} (issue #11's ironman
 * auto-detect) — no {@code Client}/{@code ConfigManager} mocking needed.
 * Covers the two discrimination requirements explicitly called out in the
 * spec: "never set" vs "explicitly false" must never be conflated, and the
 * per-profile "already evaluated" bookkeeping is independent of the flag's
 * own value.
 */
public class IronmanAutoDetectTest
{
	@Test
	public void needsEvaluation_true_whenMarkerNeverSet()
	{
		assertTrue(IronmanAutoDetect.needsEvaluation(null));
	}

	@Test
	public void needsEvaluation_false_onceMarkerIsSet_regardlessOfItsValue()
	{
		assertFalse(IronmanAutoDetect.needsEvaluation("true"));
		assertFalse(IronmanAutoDetect.needsEvaluation("false"));
	}

	@Test
	public void shouldAutoEnable_true_whenUnsetAndIronman()
	{
		assertTrue(IronmanAutoDetect.shouldAutoEnable(null, true));
	}

	@Test
	public void shouldAutoEnable_false_whenUnsetButNotIronman()
	{
		assertFalse(IronmanAutoDetect.shouldAutoEnable(null, false));
	}

	@Test
	public void shouldAutoEnable_false_whenExplicitlyFalse_evenForIronman()
	{
		// The critical discrimination: a user who deliberately turned it off
		// must never have it silently re-enabled just because "false" looks
		// unset-ish. Only a raw null counts as unset.
		assertFalse(IronmanAutoDetect.shouldAutoEnable("false", true));
	}

	@Test
	public void shouldAutoEnable_false_whenAlreadyExplicitlyTrue()
	{
		assertFalse(IronmanAutoDetect.shouldAutoEnable("true", true));
	}
}
