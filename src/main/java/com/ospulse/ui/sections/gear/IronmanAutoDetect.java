package com.ospulse.ui.sections.gear;

/**
 * Pure decision logic for auto-enabling the ironman "owned gear only"
 * optimiser mode (issue #11) the first time an ironman-type account is seen
 * on a given RS profile.
 *
 * <p>A {@code @ConfigItem} default is a compile-time constant, so it can't
 * itself depend on the runtime account type — a first-login write is the
 * only way to default the setting ON for an ironman. This class holds only
 * the decision, never touching any RuneLite API directly (no {@code Client}
 * / {@code ConfigManager}), so it is trivially unit-testable without
 * mocking either. The caller ({@code OSPulsePlugin}) owns the actual reads/
 * writes (via {@link IronmanOwnedOnlyStore}) and the RS-profile-scoped
 * bookkeeping key that gates how often {@link #shouldAutoEnable} is even
 * consulted.
 */
public final class IronmanAutoDetect
{
	private IronmanAutoDetect()
	{
	}

	/**
	 * Whether this RS profile still needs its one-time auto-detect
	 * evaluation. {@code rawAutoDetectMarker} is the raw, per-RS-profile
	 * bookkeeping value (the caller's own {@code getRSProfileConfiguration}
	 * read) — {@code null} the first time this profile is ever seen after
	 * login, non-null forever after regardless of outcome. This is NOT the
	 * {@code ironmanOwnedOnly} value itself; it only tracks whether the
	 * one-time check has already run for this profile, so an ironman alt and
	 * a main sharing one client each get their own independent evaluation
	 * instead of one profile's "already decided" state blocking the other's.
	 */
	public static boolean needsEvaluation(String rawAutoDetectMarker)
	{
		return rawAutoDetectMarker == null;
	}

	/**
	 * Whether the {@code ironmanOwnedOnly} flag should be auto-enabled for
	 * this account. Only ever meaningful when {@link #needsEvaluation} was
	 * true for this profile, and only ever true when the flag has never been
	 * explicitly set for THIS account ({@code rawOwnedOnlyValue == null} — an
	 * explicit {@code "false"} means the user deliberately turned it off and
	 * must never be silently re-enabled) and the logged-in account is some
	 * ironman variant.
	 *
	 * <p>{@code rawOwnedOnlyValue} is this account's raw PER-PROFILE value
	 * (see {@link IronmanOwnedOnlyStore#rawProfileValue()}) — per the
	 * per-account scheme, auto-detect only ever reads/writes the per-profile
	 * key, never the client-wide {@code @ConfigItem}, so an ironman alt's
	 * auto-enable can never leak onto a main sharing the same client.
	 */
	public static boolean shouldAutoEnable(String rawOwnedOnlyValue, boolean accountIsIronman)
	{
		return rawOwnedOnlyValue == null && accountIsIronman;
	}
}
