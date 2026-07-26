package com.ospulse.ui.sections.gear;

import com.ospulse.OSPulseConfig;
import net.runelite.client.config.ConfigManager;

/**
 * Owns every {@link ConfigManager} read/write for the ironman "owned gear
 * only" flag's per-account scheme — see {@link IronmanOwnedOnlyResolver} for
 * the pure merged-read/echo decisions this class calls into.
 *
 * <p>Three raw keys under {@link OSPulseConfig#GROUP}:
 * <ul>
 *   <li>{@link #KEY} scoped per-RS-profile (the authoritative per-account
 *       value) via {@code getRSProfileConfiguration}/{@code
 *       setRSProfileConfiguration};</li>
 *   <li>{@link #KEY} client-wide (the {@code @ConfigItem} checkbox) — an
 *       edit surface / display mirror only, via plain {@code
 *       getConfiguration}/{@code setConfiguration};</li>
 *   <li>{@link #DEFAULT_KEY} client-wide, hidden (no {@code @ConfigItem}) —
 *       the user's global preference for accounts with no per-profile value
 *       yet.</li>
 * </ul>
 *
 * <p><b>Echo latch:</b> {@link #mirrorToClientWide} writes the resolved
 * effective value into the client-wide checkbox so the settings panel shows
 * the truth for whichever account is logged in. That write fires its own
 * {@code ConfigChanged} (indistinguishable by {@code getProfile()} from a
 * real user toggle — both are client-wide writes with a {@code null}
 * profile, verified against the {@code ConfigManager} bytecode; only a
 * profile-SCOPED write carries a non-null profile). {@link
 * #mirrorToClientWide} arms the value it's about to write immediately before
 * writing it, and {@link #consumeMirrorEcho} lets the caller swallow the
 * first matching client-wide event as that write's own echo rather than a
 * user toggle. The latch is only armed when {@link
 * IronmanOwnedOnlyResolver#needsMirrorWrite} is true (see {@link
 * #mirrorToClientWide}) — arming it for a write that would be a no-op would
 * never see a matching event, leaving the latch stuck armed to wrongly
 * swallow a later genuine toggle. If the latch is ever consumed by
 * coincidence rather than the real echo (e.g. some other write races in with
 * the exact same value), the swallowed value already equals what's stored,
 * so there is no visible harm — the checkbox already reads correctly either
 * way.
 */
public final class IronmanOwnedOnlyStore
{
	/** Mirrors {@link OSPulseConfig#ironmanOwnedOnly()}'s {@code keyName} — used both per-profile and client-wide (see class javadoc). */
	public static final String KEY = "ironmanOwnedOnly";
	/** Client-wide, hidden (no {@code @ConfigItem}) global-preference key — see class javadoc. */
	public static final String DEFAULT_KEY = "ironmanOwnedOnlyDefault";

	private final ConfigManager configManager;

	/** Echo latch: the raw value a {@link #mirrorToClientWide} write is expecting to see echo back, or {@code null} when unarmed. */
	private String armedMirrorValue;

	public IronmanOwnedOnlyStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** The current account's raw per-profile value, or {@code null} if not logged in or never decided for this profile. */
	public String rawProfileValue()
	{
		String profileKey = configManager.getRSProfileKey();
		return profileKey != null ? configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, KEY) : null;
	}

	/** The merged effective read for whichever account is currently logged in (or the global default alone, logged out) — see {@link IronmanOwnedOnlyResolver#resolve}. */
	public boolean resolveEffective()
	{
		String rawDefault = configManager.getConfiguration(OSPulseConfig.GROUP, DEFAULT_KEY);
		return IronmanOwnedOnlyResolver.resolve(rawProfileValue(), rawDefault);
	}

	/**
	 * Auto-detect's one-time write (issue #11): the per-profile key ONLY,
	 * never the client-wide value — this is what keeps an ironman alt's
	 * auto-enable from leaking onto a main sharing the same client. Caller
	 * ({@code OSPulsePlugin#checkIronmanAutoDetect}) already gates this on
	 * {@link IronmanAutoDetect#needsEvaluation}/{@link
	 * IronmanAutoDetect#shouldAutoEnable}.
	 */
	public void writeAutoDetected(boolean value)
	{
		configManager.setRSProfileConfiguration(OSPulseConfig.GROUP, KEY, value);
	}

	/**
	 * A genuine user toggle of the client-wide checkbox (a real, non-echo
	 * {@code ConfigChanged} on {@link #KEY} — the caller is responsible for
	 * having already ruled out a profile-scoped write and a mirror echo, see
	 * class javadoc): writes the current account's per-profile value AND the
	 * global default, so the next never-decided account still inherits the
	 * user's latest explicit choice. When not logged in (no RS profile key),
	 * only the global default is written.
	 */
	public void writeUserToggle(boolean value)
	{
		String profileKey = configManager.getRSProfileKey();
		if (profileKey != null)
		{
			configManager.setRSProfileConfiguration(OSPulseConfig.GROUP, KEY, value);
		}
		configManager.setConfiguration(OSPulseConfig.GROUP, DEFAULT_KEY, value);
	}

	/**
	 * Re-mirrors the resolved effective value into the client-wide checkbox
	 * (called on login and RS profile change — {@code OSPulsePlugin}) so the
	 * settings panel reflects the truth for the account now logged in. Only
	 * writes when the stored client-wide value actually differs (see {@link
	 * IronmanOwnedOnlyResolver#needsMirrorWrite}), arming the echo latch
	 * immediately beforehand — the write's own {@code ConfigChanged} fires
	 * synchronously within {@code setConfiguration}, so by the time this
	 * method returns the latch has already been consumed by the nested
	 * {@code onConfigChanged} call.
	 */
	public void mirrorToClientWide()
	{
		boolean effective = resolveEffective();
		String rawClientWide = configManager.getConfiguration(OSPulseConfig.GROUP, KEY);
		if (!IronmanOwnedOnlyResolver.needsMirrorWrite(rawClientWide, effective))
		{
			return;
		}
		armedMirrorValue = String.valueOf(effective);
		configManager.setConfiguration(OSPulseConfig.GROUP, KEY, effective);
	}

	/**
	 * Consumes a client-wide (profile-{@code null}) {@code ConfigChanged}
	 * event on {@link #KEY} if it matches the armed {@link
	 * #mirrorToClientWide} echo: disarms the latch and returns {@code true}
	 * (caller must NOT treat this as a user toggle). Returns {@code false}
	 * (latch left as-is) for anything else, meaning the caller should treat
	 * it as a genuine user toggle.
	 */
	public boolean consumeMirrorEcho(String eventNewValue)
	{
		if (IronmanOwnedOnlyResolver.isMirrorEcho(armedMirrorValue, eventNewValue))
		{
			armedMirrorValue = null;
			return true;
		}
		return false;
	}

	/** Test seam: whether the echo latch is currently armed. */
	boolean isEchoLatchArmedForTest()
	{
		return armedMirrorValue != null;
	}
}
