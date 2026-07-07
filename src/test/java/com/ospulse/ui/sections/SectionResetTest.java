package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Feature 11 — panel-wide full Reset. Each section overrides
 * {@link CollapsibleSection#resetState()} to drop its retained UI state so a
 * reset actually returns the panel to fresh. These tests exercise the money /
 * hidden-figure sections directly (GearSection has its own reset test).
 *
 * <p>The headline case is the "phantom profit" regression: SessionSection shows
 * {@code raw - baseline}; a stale per-row reset baseline left non-zero when the
 * engine re-anchors would render a large phantom of the opposite sign.
 */
public class SectionResetTest
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

	/** An 11-arg snapshot carrying only a profit figure (all other stats zero). */
	private static SessionSnapshot profitSnapshot(long profit)
	{
		return new SessionSnapshot(0L, 0L, profit, 0L, 0L, 0L, false,
			List.of(), Map.of(), 0L, null);
	}

	// --------------------------------------------------- SessionSection phantom

	@Test
	public void fullResetClearsStaleBaselineSoNoPhantomProfit()
	{
		onEdt(() ->
		{
			SessionSection s = new SessionSection(NO_STORE, null, null, null);

			// Session runs up to 5M profit (baseline still 0 -> displayed 5M).
			s.apply(profitSnapshot(5_000_000L));
			assertEquals(5_000_000L, s.displayedProfitForTest());

			// User clicks the profit row's "Reset". The bumped epoch captures
			// baseline = raw during the NEXT apply (after that apply computed the
			// pre-rebase figure), so the row reads 0 from the apply after that.
			s.resetCategoryForTest("session:profit");
			s.apply(profitSnapshot(5_000_000L)); // captures baseline = 5M
			s.apply(profitSnapshot(5_000_000L)); // 5M - 5M = 0
			assertEquals(0L, s.displayedProfitForTest());

			// Panel-wide Reset re-anchors the engine, so raw returns to 0. Without
			// clearing the baseline this would render 0 - 5M = -5M (the phantom).
			s.resetState();
			s.apply(profitSnapshot(0L));
			assertEquals("no phantom profit after full reset", 0L, s.displayedProfitForTest());
		});
	}

	@Test
	public void fullResetUnpausesFrozenSessionRows()
	{
		onEdt(() ->
		{
			SessionSection s = new SessionSection(NO_STORE, null, null, null);
			s.apply(profitSnapshot(1_000_000L));

			s.pauseCategoryForTest("session:profit", true);
			assertTrue(s.isPausedForTest("session:profit"));

			s.resetState();
			assertFalse("full reset unpauses categories", s.isPausedForTest("session:profit"));
		});
	}

	// ------------------------------------------------------- LootSection hides

	@Test
	public void fullResetClearsLootHidesAndFeed()
	{
		onEdt(() ->
		{
			LootSection loot = new LootSection(NO_STORE, null, null, null, null, null);

			loot.hideSourceForTest("Cerberus");
			loot.hideItemForTest(4151, "Abyssal whip");
			assertTrue(loot.isSourceHiddenForTest("Cerberus"));
			assertTrue(loot.hasHiddenItemsForTest());

			loot.resetState();

			assertFalse("reset clears reset-hidden sources", loot.isSourceHiddenForTest("Cerberus"));
			assertFalse("reset clears persistent item hides", loot.hasHiddenItemsForTest());
			// The feed re-renders to its empty state (a single placeholder row),
			// i.e. no retained loot rows survive the reset.
			assertTrue("reset re-renders an empty feed", loot.lootListRowCountForTest() <= 1);
		});
	}

	// --------------------------------------------------------- XpSection hides

	@Test
	public void fullResetClearsXpHidesAndBreakdown()
	{
		onEdt(() ->
		{
			XpSection xp = new XpSection(NO_STORE, null, null, null, null);

			xp.hideSkillForTest("Attack");
			assertTrue(xp.isSkillHiddenForTest("Attack"));

			xp.resetState();

			assertFalse("reset un-hides skills", xp.isSkillHiddenForTest("Attack"));
			assertEquals("reset empties the skill breakdown", 0, xp.breakdownRowCountForTest());
		});
	}
}
