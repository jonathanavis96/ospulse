package com.ospulse.ui.sections.gear;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure unit tests for {@link IronmanOwnedOnlyResolver} — issue #11's leak
 * fix (per-account {@code ironmanOwnedOnly} scheme). {@link
 * #leakScenario_ironmanAltAutoEnable_neverAffectsMainWithNoProfileValue} is
 * the resolver-level regression guard for the actual reported bug: an
 * ironman alt auto-enabling the mode used to leave it enabled for a main
 * sharing the same client, because the value was stored client-wide instead
 * of per-account.
 */
public class IronmanOwnedOnlyResolverTest
{
	// ---------------------------------------------------------- resolve()

	@Test
	public void resolve_profileTrue_overridesConflictingGlobalDefaultFalse()
	{
		assertTrue(IronmanOwnedOnlyResolver.resolve("true", "false"));
	}

	@Test
	public void resolve_profileFalse_overridesConflictingGlobalDefaultTrue()
	{
		assertFalse(IronmanOwnedOnlyResolver.resolve("false", "true"));
	}

	@Test
	public void resolve_profileNull_fallsBackToGlobalDefaultTrue()
	{
		assertTrue(IronmanOwnedOnlyResolver.resolve(null, "true"));
	}

	@Test
	public void resolve_profileNull_fallsBackToGlobalDefaultFalse()
	{
		assertFalse(IronmanOwnedOnlyResolver.resolve(null, "false"));
	}

	@Test
	public void resolve_bothNull_isFalse()
	{
		assertFalse(IronmanOwnedOnlyResolver.resolve(null, null));
	}

	/**
	 * The leak scenario, at the resolver level: an ironman alt's auto-detect
	 * write sets ONLY that alt's own per-profile value — simulated here as
	 * {@code resolve("true", null)}. A main sharing the same client (no
	 * global default set either) has its OWN per-profile value, which is
	 * still {@code null} (never decided) regardless of what the alt just
	 * did — simulated as {@code resolve(null, null)}. The main must resolve
	 * to {@code false}, proving the alt's write cannot leak across accounts.
	 */
	@Test
	public void leakScenario_ironmanAltAutoEnable_neverAffectsMainWithNoProfileValue()
	{
		boolean altAfterAutoEnable = IronmanOwnedOnlyResolver.resolve("true", null);
		boolean mainNeverDecided = IronmanOwnedOnlyResolver.resolve(null, null);

		assertTrue("the ironman alt's own per-profile value must reflect its auto-enable",
			altAfterAutoEnable);
		assertFalse("a main sharing the client, with its own per-profile value still null, "
			+ "must NOT inherit the alt's auto-enable",
			mainNeverDecided);
	}

	// ---------------------------------------------------- needsMirrorWrite()

	@Test
	public void needsMirrorWrite_true_whenClientWideDiffersFromEffective()
	{
		assertTrue(IronmanOwnedOnlyResolver.needsMirrorWrite("false", true));
		assertTrue(IronmanOwnedOnlyResolver.needsMirrorWrite(null, true));
	}

	@Test
	public void needsMirrorWrite_false_whenClientWideAlreadyMatchesEffective()
	{
		assertFalse(IronmanOwnedOnlyResolver.needsMirrorWrite("true", true));
		assertFalse(IronmanOwnedOnlyResolver.needsMirrorWrite(null, false));
		assertFalse(IronmanOwnedOnlyResolver.needsMirrorWrite("false", false));
	}

	// ------------------------------------------------------- isMirrorEcho()

	@Test
	public void isMirrorEcho_true_whenEventValueMatchesArmedValue()
	{
		assertTrue(IronmanOwnedOnlyResolver.isMirrorEcho("true", "true"));
		// Serialisation-format tolerant: parsed-boolean comparison, not raw string.
		assertTrue(IronmanOwnedOnlyResolver.isMirrorEcho("true", "TRUE"));
	}

	@Test
	public void isMirrorEcho_false_whenEventValueDiffersFromArmedValue()
	{
		assertFalse(IronmanOwnedOnlyResolver.isMirrorEcho("true", "false"));
	}

	@Test
	public void isMirrorEcho_false_whenLatchUnarmed()
	{
		assertFalse(IronmanOwnedOnlyResolver.isMirrorEcho(null, "true"));
		assertFalse(IronmanOwnedOnlyResolver.isMirrorEcho(null, "false"));
	}
}
