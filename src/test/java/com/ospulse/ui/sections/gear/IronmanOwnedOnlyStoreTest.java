package com.ospulse.ui.sections.gear;

import com.ospulse.OSPulseConfig;

import net.runelite.client.config.ConfigManager;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link IronmanOwnedOnlyStore}'s {@link ConfigManager} read/write
 * scoping and echo-latch mechanics directly (a finer grain than {@code
 * OSPulsePluginTest}'s wiring-level tests) — see {@link
 * IronmanOwnedOnlyResolverTest} for the underlying pure decisions.
 */
public class IronmanOwnedOnlyStoreTest
{
	private static ConfigManager mockConfigManager(String profileKey)
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn(profileKey);
		return configManager;
	}

	@Test
	public void rawProfileValue_returnsNull_whenNotLoggedIn()
	{
		ConfigManager configManager = mockConfigManager(null);
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);

		assertTrue("must never call getRSProfileConfiguration with no RS profile key",
			store.rawProfileValue() == null);
		Mockito.verify(configManager, Mockito.never())
			.getRSProfileConfiguration(Mockito.anyString(), Mockito.anyString());
	}

	@Test
	public void writeAutoDetected_writesOnlyThePerProfileKey()
	{
		ConfigManager configManager = mockConfigManager("profile-1");
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);

		store.writeAutoDetected(true);

		Mockito.verify(configManager).setRSProfileConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY, true);
		Mockito.verify(configManager, Mockito.never())
			.setConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.any());
	}

	@Test
	public void writeUserToggle_loggedIn_writesProfileValueAndGlobalDefault()
	{
		ConfigManager configManager = mockConfigManager("profile-1");
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);

		store.writeUserToggle(true);

		Mockito.verify(configManager).setRSProfileConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY, true);
		Mockito.verify(configManager).setConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.DEFAULT_KEY, true);
	}

	@Test
	public void writeUserToggle_notLoggedIn_writesOnlyGlobalDefault()
	{
		ConfigManager configManager = mockConfigManager(null);
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);

		store.writeUserToggle(true);

		Mockito.verify(configManager, Mockito.never())
			.setRSProfileConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.any());
		Mockito.verify(configManager).setConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.DEFAULT_KEY, true);
	}

	@Test
	public void mirrorToClientWide_writesAndArmsLatch_whenValueDiffers()
	{
		ConfigManager configManager = mockConfigManager("profile-1");
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY))
			.thenReturn("true");
		Mockito.when(configManager.getConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY))
			.thenReturn("false");
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);

		store.mirrorToClientWide();

		Mockito.verify(configManager).setConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY, true);
		assertTrue("the echo latch must be armed after a real mirror write", store.isEchoLatchArmedForTest());
	}

	@Test
	public void mirrorToClientWide_writesNothingAndLeavesLatchUnarmed_whenValueAlreadyMatches()
	{
		ConfigManager configManager = mockConfigManager("profile-1");
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY))
			.thenReturn("true");
		Mockito.when(configManager.getConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY))
			.thenReturn("true");
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);

		store.mirrorToClientWide();

		Mockito.verify(configManager, Mockito.never())
			.setConfiguration(Mockito.eq(OSPulseConfig.GROUP), Mockito.eq(IronmanOwnedOnlyStore.KEY), Mockito.any());
		assertFalse("an unnecessary write must never arm the latch (it would never be consumed)",
			store.isEchoLatchArmedForTest());
	}

	@Test
	public void consumeMirrorEcho_consumesArmedMatchingValue_andDisarmsTheLatch()
	{
		ConfigManager configManager = mockConfigManager("profile-1");
		Mockito.when(configManager.getRSProfileConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY))
			.thenReturn("true");
		Mockito.when(configManager.getConfiguration(OSPulseConfig.GROUP, IronmanOwnedOnlyStore.KEY))
			.thenReturn("false");
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(configManager);
		store.mirrorToClientWide();

		assertTrue("the matching echo must be consumed", store.consumeMirrorEcho("true"));
		assertFalse("the latch must be disarmed after being consumed", store.isEchoLatchArmedForTest());
		assertFalse("a second event must no longer be treated as the echo", store.consumeMirrorEcho("true"));
	}

	@Test
	public void consumeMirrorEcho_unarmed_neverConsumesAnything()
	{
		IronmanOwnedOnlyStore store = new IronmanOwnedOnlyStore(mockConfigManager("profile-1"));

		assertFalse(store.consumeMirrorEcho("true"));
		assertFalse(store.consumeMirrorEcho("false"));
	}
}
