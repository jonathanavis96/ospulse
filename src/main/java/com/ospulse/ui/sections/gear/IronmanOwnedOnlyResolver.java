package com.ospulse.ui.sections.gear;

/**
 * Pure decision logic for the per-account (per-RS-profile) scheme behind
 * {@link com.ospulse.OSPulseConfig#ironmanOwnedOnly()} — the fix for the
 * "ironman alt leaks the mode onto a main sharing one client" bug the
 * original {@code ironmanOwnedOnly} {@code @ConfigItem} had ({@link
 * IronmanAutoDetect} covers the one-time auto-enable decision this scheme
 * feeds).
 *
 * <p>{@code @ConfigItem} storage is inherently client-wide, so three raw
 * values live under the {@code ospulse} config group:
 * <ul>
 *   <li>a per-RS-profile {@code ironmanOwnedOnly} value (via {@code
 *       ConfigManager#getRSProfileConfiguration}/{@code
 *       setRSProfileConfiguration}) — the authoritative per-account value,
 *       {@code null} meaning "this account has never been decided";</li>
 *   <li>the client-wide {@code ironmanOwnedOnly} {@code @ConfigItem} itself —
 *       now only an edit surface / per-account display mirror, never read as
 *       the source of truth for behaviour;</li>
 *   <li>a client-wide, hidden {@code ironmanOwnedOnlyDefault} key (no {@code
 *       @ConfigItem}) — the user's global preference, consulted only for
 *       accounts with no per-profile value yet.</li>
 * </ul>
 *
 * <p>This class holds only the merged-read and toggle/echo decisions given
 * raw string inputs, never touching {@code ConfigManager} or any other
 * RuneLite type directly, so it is trivially unit-testable. {@link
 * IronmanOwnedOnlyStore} owns the actual reads/writes (including the
 * mutable echo-latch state) and calls into this class for the decisions.
 */
public final class IronmanOwnedOnlyResolver
{
	private IronmanOwnedOnlyResolver()
	{
	}

	/**
	 * The merged three-way read: the per-profile value wins if set, else the
	 * global default if set, else {@code false} (a never-decided account with
	 * no global preference either).
	 *
	 * @param rawProfileValue the current account's raw per-profile value
	 *                        ({@code null} = never decided for this account)
	 * @param rawGlobalDefault the raw {@code ironmanOwnedOnlyDefault} value
	 *                         ({@code null} = the user has no global
	 *                         preference recorded yet)
	 */
	public static boolean resolve(String rawProfileValue, String rawGlobalDefault)
	{
		if (rawProfileValue != null)
		{
			return Boolean.parseBoolean(rawProfileValue);
		}
		if (rawGlobalDefault != null)
		{
			return Boolean.parseBoolean(rawGlobalDefault);
		}
		return false;
	}

	/**
	 * Whether the client-wide mirror actually needs (re-)writing: only when
	 * the resolved effective value differs from what the client-wide box
	 * currently shows. Callers must skip the write entirely (never arm an
	 * echo latch) when this is {@code false} — see {@link
	 * IronmanOwnedOnlyStore}'s echo-latch javadoc for why an unnecessary arm
	 * is dangerous (it would never be consumed, and could swallow a later
	 * genuine user toggle).
	 */
	public static boolean needsMirrorWrite(String rawClientWideValue, boolean effective)
	{
		return Boolean.parseBoolean(rawClientWideValue) != effective;
	}

	/**
	 * Whether a {@code ConfigChanged} event on the client-wide key's raw new
	 * value matches the value a just-armed mirror write was expected to
	 * produce — i.e. this event is that write echoing back through the event
	 * bus, not an independent change. Compared as parsed booleans (not raw
	 * strings) so serialisation-format differences can't cause a false
	 * negative.
	 *
	 * <p>{@code armedExpectedValue == null} means the latch isn't armed (no
	 * mirror write is in flight), so nothing can ever match it.
	 */
	public static boolean isMirrorEcho(String armedExpectedValue, String eventNewValue)
	{
		return armedExpectedValue != null
			&& Boolean.parseBoolean(armedExpectedValue) == Boolean.parseBoolean(eventNewValue);
	}
}
