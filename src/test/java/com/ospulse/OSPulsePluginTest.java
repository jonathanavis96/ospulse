package com.ospulse;

import net.runelite.api.Client;
import net.runelite.api.GameState;

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
}
