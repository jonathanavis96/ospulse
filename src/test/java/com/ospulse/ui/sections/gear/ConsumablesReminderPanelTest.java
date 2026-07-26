package com.ospulse.ui.sections.gear;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Independent unit tests for {@link ConsumablesReminderPanel} — constructed
 * and driven with only a monster name, no {@code GearSection} involved,
 * proving the class holds no dependency on it.
 */
public class ConsumablesReminderPanelTest
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
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			throw new RuntimeException(cause);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	@Test
	public void noTarget_rendersNothing()
	{
		onEdt(() ->
		{
			ConsumablesReminderPanel panel = new ConsumablesReminderPanel();
			panel.refresh(null);
			assertFalse("no target selected must render nothing", panel.isVisible());
			assertTrue(panel.noteTextsForTest().isEmpty());
		});
	}

	@Test
	public void monsterWithNoCuratedReminder_rendersNothing()
	{
		onEdt(() ->
		{
			ConsumablesReminderPanel panel = new ConsumablesReminderPanel();
			panel.refresh("Cow");
			assertFalse("a monster with no curated reminder must render nothing — no empty row, no placeholder",
				panel.isVisible());
			assertTrue(panel.noteTextsForTest().isEmpty());
		});
	}

	@Test
	public void zulrah_rendersItsCuratedNote()
	{
		onEdt(() ->
		{
			ConsumablesReminderPanel panel = new ConsumablesReminderPanel();
			panel.refresh("Zulrah (Serpentine)");
			assertTrue("Zulrah must show its reminder", panel.isVisible());
			List<String> texts = panel.noteTextsForTest();
			assertEquals(1, texts.size());
			assertTrue(texts.get(0).toLowerCase(java.util.Locale.ROOT).contains("antivenom"));
		});
	}

	@Test
	public void switchingFromAKnownTargetToAnUnknownOne_clearsThePanel()
	{
		onEdt(() ->
		{
			ConsumablesReminderPanel panel = new ConsumablesReminderPanel();
			panel.refresh("Zulrah");
			assertTrue(panel.isVisible());

			panel.refresh("Cow");
			assertFalse("switching to a monster with no reminder must hide the panel again", panel.isVisible());
			assertTrue(panel.noteTextsForTest().isEmpty());
		});
	}
}
