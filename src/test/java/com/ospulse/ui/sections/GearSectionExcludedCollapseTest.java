package com.ospulse.ui.sections;

import com.ospulse.OSPulseConfig;
import com.ospulse.ui.CollapsibleSection;

import net.runelite.client.config.ConfigManager;

import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers issue #11's collapsible "Excluded from suggestions" list: a
 * clickable "▾"/"▸" heading (the {@code LootSection} idiom, adapted to a
 * single section-wide toggle instead of one triangle per row — see {@link
 * com.ospulse.ui.sections.gear.CollapsibleHeading}), persisted via a raw
 * config key (unlike {@code LootSection}'s in-memory-only collapse set) so it
 * survives a client restart, and composed with {@code excludedItemsPanel}'s
 * pre-existing empty-list self-hide rather than fighting it.
 */
public class GearSectionExcludedCollapseTest
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

	private static final int DRAGON_SCIMITAR = 4587;

	// ------------------------------------------------- empty-list self-hide

	@Test
	public void emptyList_hidesWholePanel_regardlessOfCollapseState()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);

			assertFalse("nothing excluded yet — the whole panel must self-hide",
				section.excludedItemsPanel.isVisible());

			// Collapsing an already-empty list changes nothing observable: the
			// panel (heading included) stays hidden either way — empty always
			// wins over collapse state.
			section.toggleExcludedItemsCollapsed();
			assertTrue(section.excludedItemsCollapsed);
			assertFalse("still nothing to show, even collapsed",
				section.excludedItemsPanel.isVisible());
		});
	}

	@Test
	public void nonEmptyList_notCollapsed_showsHeadingAndBody()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.excludeFromSuggestions(DRAGON_SCIMITAR);

			assertTrue(section.excludedItemsPanel.isVisible());
			assertFalse(section.excludedItemsCollapsed);
			assertTrue("expanded: search box must show", section.excludedSearchField.isVisible());
			assertTrue("expanded: grid must show", section.excludedScroll.isVisible());
			assertEquals("▾ Excluded from suggestions", section.excludedHeading.getText());
		});
	}

	@Test
	public void nonEmptyList_collapsed_keepsHeadingButHidesBody()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.excludeFromSuggestions(DRAGON_SCIMITAR);

			section.toggleExcludedItemsCollapsed();

			assertTrue("collapsed but non-empty: the panel (heading) stays up",
				section.excludedItemsPanel.isVisible());
			assertTrue(section.excludedItemsCollapsed);
			assertFalse("collapsed: search box must hide", section.excludedSearchField.isVisible());
			assertFalse("collapsed: grid must hide", section.excludedScroll.isVisible());
			assertEquals("▸ Excluded from suggestions", section.excludedHeading.getText());
		});
	}

	// ------------------------------------------------------------ persistence

	@Test
	public void collapseState_persistsAcrossARebuild()
	{
		onEdt(() ->
		{
			ConfigManager configManager = Mockito.mock(ConfigManager.class);
			GearSection first = new GearSection(NO_STORE, null, null, null, configManager);
			first.excludeFromSuggestions(DRAGON_SCIMITAR);
			assertFalse(first.excludedItemsCollapsed);

			first.toggleExcludedItemsCollapsed();
			assertTrue(first.excludedItemsCollapsed);
			Mockito.verify(configManager)
				.setConfiguration(OSPulseConfig.GROUP, "excludedItemsCollapsed", "true");

			// Simulate a client restart: a fresh GearSection reading from the
			// same (now-persisted) config store. loadExcludedItemsPref/
			// loadExcludedItemsCollapsedPref both read once at construction —
			// exactly like every other GearSection pref — so re-stubbing the
			// mock and constructing a second instance is the correct way to
			// simulate "after a rebuild" here.
			Mockito.when(configManager.getConfiguration(OSPulseConfig.GROUP, "excludedItemsCollapsed"))
				.thenReturn("true");
			Mockito.when(configManager.getConfiguration(OSPulseConfig.GROUP, "optimizerExcludedItemIds"))
				.thenReturn(String.valueOf(DRAGON_SCIMITAR));

			GearSection rebuilt = new GearSection(NO_STORE, null, null, null, configManager);

			assertTrue("collapse state must survive a rebuild", rebuilt.excludedItemsCollapsed);
			assertTrue("the excluded item itself must also survive (pre-existing behaviour)",
				rebuilt.excludedItemsPanel.isVisible());
			assertFalse("rebuilt instance must start with the body hidden, matching the persisted state",
				rebuilt.excludedSearchField.isVisible());
		});
	}

	@Test
	public void collapseState_defaultsToExpanded_whenNeverPersisted()
	{
		onEdt(() ->
		{
			ConfigManager configManager = Mockito.mock(ConfigManager.class);
			GearSection section = new GearSection(NO_STORE, null, null, null, configManager);
			section.excludeFromSuggestions(DRAGON_SCIMITAR);

			assertFalse("no persisted collapse value — must default to expanded, not collapsed",
				section.excludedItemsCollapsed);
			assertTrue(section.excludedSearchField.isVisible());
		});
	}
}
