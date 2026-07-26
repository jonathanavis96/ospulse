package com.ospulse;

import com.ospulse.integration.SessionTracker;
import com.ospulse.ui.OSPulsePanel;
import com.ospulse.ui.sections.gear.IronmanOwnedOnlyStore;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.vars.AccountType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers issue #11's P2 fix (b): {@code OSPulsePlugin} only armed ironman
 * auto-detect's {@code pendingIronmanAutoDetect} flag from {@code
 * onGameStateChanged}'s {@code LOGGED_IN} case, which never fires when the
 * plugin is enabled while the client is ALREADY logged in — so auto-detect
 * silently never ran for that session. {@link OSPulsePlugin#startUp()} now
 * shares {@link OSPulsePlugin#armPendingIronmanAutoDetectIfLoggedIn()} with
 * {@code onGameStateChanged} (the established "already logged in at plugin
 * start" pattern also used by {@code tracker.onLogin()}), so both paths stay
 * in sync. Tests the shared method directly (not the full heavyweight
 * {@code startUp()}, which constructs the whole panel/section tree) with a
 * reflection-injected mock {@link Client} — {@code OSPulsePlugin}'s fields
 * are plain {@code @Inject} with no Guice container in a unit test.
 */
public class OSPulsePluginTest
{
	private static void setClient(OSPulsePlugin plugin, Client client) throws Exception
	{
		Field field = OSPulsePlugin.class.getDeclaredField("client");
		field.setAccessible(true);
		field.set(plugin, client);
	}

	private static void setField(OSPulsePlugin plugin, String name, Object value) throws Exception
	{
		Field field = OSPulsePlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	/**
	 * Builds a plugin with just enough reflection-injected state to exercise
	 * {@code checkIronmanAutoDetect}/{@code onConfigChanged} directly, without
	 * the heavyweight {@code startUp()} (same rationale as the class javadoc:
	 * these are plain {@code @Inject} fields with no Guice container here).
	 */
	private static OSPulsePlugin pluginWith(Client client, ConfigManager configManager) throws Exception
	{
		OSPulsePlugin plugin = new OSPulsePlugin();
		setClient(plugin, client);
		setField(plugin, "configManager", configManager);
		setField(plugin, "ownedOnlyStore", new IronmanOwnedOnlyStore(configManager));
		setField(plugin, "panel", Mockito.mock(OSPulsePanel.class));
		return plugin;
	}

	/**
	 * {@link #pluginWith} plus a mocked {@link SessionTracker}, needed to
	 * drive {@link OSPulsePlugin#onGameTick} directly ({@code
	 * tracker.onTick()} runs unconditionally on every tick) without the
	 * heavyweight {@code startUp()}.
	 */
	private static OSPulsePlugin pluginWithTracker(Client client, ConfigManager configManager) throws Exception
	{
		OSPulsePlugin plugin = pluginWith(client, configManager);
		setField(plugin, "tracker", Mockito.mock(SessionTracker.class));
		return plugin;
	}

	private static ConfigChanged configChanged(String key, String profile, String newValue)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(OSPulseConfig.GROUP);
		event.setKey(key);
		event.setProfile(profile);
		event.setNewValue(newValue);
		return event;
	}

	@Test
	public void armPendingIronmanAutoDetectIfLoggedIn_arms_whenAlreadyLoggedIn() throws Exception
	{
		OSPulsePlugin plugin = new OSPulsePlugin();
		Client client = Mockito.mock(Client.class);
		Mockito.when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		setClient(plugin, client);

		plugin.armPendingIronmanAutoDetectIfLoggedIn();

		assertTrue("a plugin (re)started while already logged in must still schedule auto-detect for the next tick",
			plugin.pendingIronmanAutoDetectForTest());
	}

	@Test
	public void armPendingIronmanAutoDetectIfLoggedIn_doesNotArm_whenNotLoggedIn()
		throws Exception
	{
		OSPulsePlugin plugin = new OSPulsePlugin();
		Client client = Mockito.mock(Client.class);
		Mockito.when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		setClient(plugin, client);

		plugin.armPendingIronmanAutoDetectIfLoggedIn();

		assertFalse(plugin.pendingIronmanAutoDetectForTest());
	}

	// ---------------------------- issue #11 leak fix: per-account writes

	/**
	 * The core of the leak fix: auto-detect for an ironman account whose
	 * per-profile value has never been decided must write ONLY the
	 * per-profile key, never the client-wide {@code @ConfigItem} — asserted
	 * directly on the {@link ConfigManager} mock interaction (mirrors this
	 * class's existing idiom of testing plugin-level behaviour via a
	 * reflection-injected mock rather than the full {@code startUp()}).
	 */
	@Test
	public void checkIronmanAutoDetect_ironmanNeverDecided_writesOnlyThePerProfileKey() throws Exception
	{
		Client client = Mockito.mock(Client.class);
		Mockito.when(client.getAccountHash()).thenReturn(123L);
		Mockito.when(client.getAccountType()).thenReturn(AccountType.IRONMAN);

		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		// needsEvaluation: the "already evaluated this profile" marker is unset.
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnlyAutoDetectSeen"))
			.thenReturn(null);
		// shouldAutoEnable: this account's own per-profile value was never decided.
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly"))
			.thenReturn(null);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("ironman-alt-profile");

		OSPulsePlugin plugin = pluginWith(client, configManager);
		plugin.checkIronmanAutoDetectForTest();

		Mockito.verify(configManager).setRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly", true);
		Mockito.verify(configManager, Mockito.never())
			.setConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly", true);
	}

	/**
	 * A genuine user toggle of the client-wide settings-panel checkbox (a
	 * {@code ConfigChanged} on the key with a {@code null} profile, and not
	 * matching an armed mirror echo) must persist to BOTH the current
	 * account's per-profile value and the global default.
	 */
	@Test
	public void onConfigChanged_userToggle_writesBothProfileValueAndGlobalDefault() throws Exception
	{
		Client client = Mockito.mock(Client.class);
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("main-profile");

		OSPulsePlugin plugin = pluginWith(client, configManager);

		plugin.onConfigChanged(configChanged("ironmanOwnedOnly", null, "true"));

		Mockito.verify(configManager).setRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly", true);
		Mockito.verify(configManager).setConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnlyDefault", true);
	}

	/**
	 * The mirror's own echo (this plugin's {@code mirrorToClientWide} writing
	 * the client-wide checkbox, then seeing its own {@code ConfigChanged}
	 * come back) must write NOTHING — neither the per-profile value nor the
	 * global default — since it isn't a user action at all.
	 */
	@Test
	public void onConfigChanged_mirrorEcho_writesNeitherProfileNorGlobalDefault() throws Exception
	{
		Client client = Mockito.mock(Client.class);
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("main-profile");
		// resolveEffective() inside mirrorToClientWide: profile value true, current client-wide box false -> a write is needed.
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly"))
			.thenReturn("true");
		Mockito.when(configManager.getConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly"))
			.thenReturn("false");

		OSPulsePlugin plugin = pluginWith(client, configManager);
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);
		setField(plugin, "ownedOnlyStore", store);
		// Arms the echo latch to "true" (a mock ConfigManager doesn't actually
		// fire ConfigChanged, so this simulates the real write's side effect
		// deterministically instead of relying on a live event bus).
		store.mirrorToClientWide();

		plugin.onConfigChanged(configChanged("ironmanOwnedOnly", null, "true"));

		Mockito.verify(configManager, Mockito.never())
			.setRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly", true);
		Mockito.verify(configManager, Mockito.never())
			.setConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnlyDefault", true);
	}

	/**
	 * A profile-scoped {@code ConfigChanged} (non-null {@code getProfile()})
	 * on the key is always our own write (auto-detect, or the profile half of
	 * a user toggle), never a user toggle of the client-wide checkbox — must
	 * not trigger a SECOND round of profile/default writes.
	 */
	@Test
	public void onConfigChanged_profileScopedEvent_isNeverTreatedAsAUserToggle() throws Exception
	{
		Client client = Mockito.mock(Client.class);
		ConfigManager configManager = Mockito.mock(ConfigManager.class);

		OSPulsePlugin plugin = pluginWith(client, configManager);

		plugin.onConfigChanged(configChanged("ironmanOwnedOnly", "some-profile-key", "true"));

		Mockito.verify(configManager, Mockito.never())
			.setRSProfileConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.any());
		Mockito.verify(configManager, Mockito.never())
			.setConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnlyDefault", true);
	}

	// ---------------------------- P2 fix: keep auto-detect pending until account state is ready

	/**
	 * Codex P2 finding on PR #19, {@code OSPulsePlugin.java:347} ("Keep
	 * auto-detection pending until account state is ready"): if the first
	 * post-login {@link GameTick} still reports {@code
	 * Client.getAccountHash() == -1} (account state not yet populated),
	 * {@code onGameTick} must NOT clear {@code pendingIronmanAutoDetect} —
	 * the earlier bug cleared it unconditionally before {@code
	 * checkIronmanAutoDetect()}'s own readiness guard could even run, so no
	 * later tick in the same session ever retried (nothing else re-arms the
	 * flag short of another {@code LOGGED_IN} transition), and the ironman
	 * setting was silently never auto-enabled/mirrored for that session.
	 */
	@Test
	public void onGameTick_accountHashNotYetReady_leavesPendingArmedForRetryOnTheNextTick() throws Exception
	{
		Client client = Mockito.mock(Client.class);
		Mockito.when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Mockito.when(client.getAccountHash()).thenReturn(-1L); // account state not populated yet

		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		OSPulsePlugin plugin = pluginWithTracker(client, configManager);
		setField(plugin, "pendingIronmanAutoDetect", true);

		plugin.onGameTick(new GameTick());

		assertTrue("pendingIronmanAutoDetect must stay armed when account state isn't ready yet "
				+ "-- nothing else re-arms it before the next LOGGED_IN transition",
			plugin.pendingIronmanAutoDetectForTest());
		// checkIronmanAutoDetect()'s readiness guard must have refused to run at
		// all -- no bookkeeping write, since it never got past the account-hash check.
		Mockito.verify(configManager, Mockito.never())
			.setRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnlyAutoDetectSeen", true);
	}

	/**
	 * Companion to the test above: once account state IS ready, the tick
	 * both runs {@code checkIronmanAutoDetect()} to completion and clears
	 * the pending flag -- the fix must not leave it permanently armed
	 * either.
	 */
	@Test
	public void onGameTick_accountHashReady_runsTheCheckAndClearsPending() throws Exception
	{
		Client client = Mockito.mock(Client.class);
		Mockito.when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Mockito.when(client.getAccountHash()).thenReturn(123L);
		Mockito.when(client.getAccountType()).thenReturn(AccountType.IRONMAN);

		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnlyAutoDetectSeen"))
			.thenReturn(null);
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnly"))
			.thenReturn(null);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("ironman-profile");

		OSPulsePlugin plugin = pluginWithTracker(client, configManager);
		setField(plugin, "pendingIronmanAutoDetect", true);

		plugin.onGameTick(new GameTick());

		assertFalse("pendingIronmanAutoDetect must clear once the check actually ran",
			plugin.pendingIronmanAutoDetectForTest());
		Mockito.verify(configManager)
			.setRSProfileConfiguration(OSPulseConfig.GROUP, "ironmanOwnedOnlyAutoDetectSeen", true);
	}
}
