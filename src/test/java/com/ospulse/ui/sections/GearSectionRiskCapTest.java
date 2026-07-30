package com.ospulse.ui.sections;

import com.ospulse.combat.Monster;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.sections.gear.IronmanOwnedOnlyStore;

import net.runelite.client.config.ConfigManager;

import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers issue #11's expensive-item risk cap: ironman owned-only mode used to
 * hide the whole budget/risk row, which made the cap unreachable while it
 * still silently applied its field defaults. Only the buy-side budget column
 * hides now — the risk cap's own column ({@link GearSection#riskColumnForTest})
 * stays visible, because the cap is about what the player is willing to
 * LOSE, which applies to an ironman too.
 */
public class GearSectionRiskCapTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

	private static void onEdt(Runnable body)
	{
		try
		{
			SwingUtilities.invokeAndWait(body);
		}
		catch (InvocationTargetException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			throw new RuntimeException(cause);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	private static final CollapsibleSection.CollapseStore NO_STORE = new CollapsibleSection.CollapseStore()
	{
		@Override
		public boolean isCollapsed(String key)
		{
			return false;
		}

		@Override
		public void setCollapsed(String key, boolean collapsed)
		{
		}
	};

	// Group-level config (e.g. optimizer prefs) and per-RS-profile config
	// (e.g. ironmanOwnedOnly) are two different ConfigManager surfaces in the
	// real client; back both with real maps so a value written through the
	// mock is actually read back by a later call/second GearSection instance
	// — a plain no-op mock would make every persistence-round-trip test a
	// false green.
	private final Map<String, String> groupConfig = new HashMap<>();
	private final Map<String, String> profileConfig = new HashMap<>();
	private final ConfigManager configManager = newConfigManager();

	private ConfigManager newConfigManager()
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn("test-profile");
		Mockito.when(configManager.getConfiguration(Mockito.eq(com.ospulse.OSPulseConfig.GROUP), Mockito.anyString()))
			.thenAnswer(invocation -> groupConfig.get((String) invocation.getArgument(1)));
		Mockito.doAnswer(invocation ->
			{
				groupConfig.put((String) invocation.getArgument(1), (String) invocation.getArgument(2));
				return null;
			})
			.when(configManager).setConfiguration(
				Mockito.eq(com.ospulse.OSPulseConfig.GROUP), Mockito.anyString(), Mockito.anyString());
		Mockito.when(configManager.getRSProfileConfiguration(Mockito.eq(com.ospulse.OSPulseConfig.GROUP), Mockito.anyString()))
			.thenAnswer(invocation -> profileConfig.get((String) invocation.getArgument(1)));
		return configManager;
	}

	private GearSection newSectionWithConfig()
	{
		return new GearSection(NO_STORE, null, null, null, configManager);
	}

	/** Writes the per-profile {@code ironmanOwnedOnly} value and drives the same live-refresh path the real config-change listener does. */
	private void setIronmanOwnedOnly(GearSection section, boolean ownedOnly)
	{
		profileConfig.put(IronmanOwnedOnlyStore.KEY, String.valueOf(ownedOnly));
		section.refreshIronmanOwnedOnlyMode();
	}

	/** A minimal {@link Monster} for gate tests — only the name and Wilderness flag matter here. */
	private static Monster monster(String name, boolean wilderness)
	{
		return Monster.builder().name(name).hitpoints(1).wildernessTarget(wilderness).build();
	}

	/**
	 * Selects {@code monster} as the target exactly as picking it from the search
	 * list would. Callers already run on the EDT (inside {@link #onEdt}), so this
	 * does not wrap again.
	 */
	private static void selectTarget(GearSection section, Monster monster)
	{
		section.selectTargetForTest(monster);
	}

	@Test
	public void ironmanOwnedOnly_hidesBudgetButKeepsRiskColumnVisible()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			setIronmanOwnedOnly(section, true);

			assertFalse("budget column must hide in owned-only mode", section.budgetColumnForTest().isVisible());
			assertTrue("risk column must stay reachable for ironmen (issue #11)", section.riskColumnForTest().isVisible());
		});
	}

	@Test
	public void leavingIronmanOwnedOnly_showsBudgetAgain_andRiskStaysVisible()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			setIronmanOwnedOnly(section, true);
			setIronmanOwnedOnly(section, false);

			assertTrue(section.budgetColumnForTest().isVisible());
			assertTrue(section.riskColumnForTest().isVisible());
		});
	}

	@Test
	public void wildernessTarget_appliesTheRiskCap()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			selectTarget(section, monster("Vet'ion", true));

			assertTrue(section.riskCapAppliesForTest());
		});
	}

	@Test
	public void nonWildernessTarget_doesNotApplyTheRiskCap()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			selectTarget(section, monster("Zulrah", false));

			assertFalse(section.riskCapAppliesForTest());
		});
	}

	@Test
	public void noTargetSelected_doesNotApplyTheRiskCap()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();

			assertFalse(section.riskCapAppliesForTest());
		});
	}

	@Test
	public void gateClosed_disablesTheCountAndThresholdControls()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			selectTarget(section, monster("Zulrah", false));

			assertFalse(section.expensiveCountFieldForTest().isEnabled());
			assertFalse(section.expensiveThresholdFieldForTest().isEnabled());
		});
	}

	@Test
	public void gateOpen_enablesTheCountAndThresholdControls()
	{
		onEdt(() ->
		{
			GearSection section = newSectionWithConfig();
			selectTarget(section, monster("Vet'ion", true));

			assertTrue(section.expensiveCountFieldForTest().isEnabled());
			assertTrue(section.expensiveThresholdFieldForTest().isEnabled());
		});
	}
}
