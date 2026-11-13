package com.ospulse.ui;

import com.ospulse.OSPulseConfig;
import com.ospulse.ui.category.CategorySectionSupport;
import com.ospulse.ui.category.CategoryOverlay;
import com.ospulse.ui.sections.SessionSection;

import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayManager;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Switching a panel section off must take its canvas overlays down, not leave them frozen on screen. */
public class SectionToggleOverlayTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

	@Test
	public void switchingSessionSectionOffRemovesItsCanvasOverlay() throws Exception
	{
		OSPulseConfig config = mock(OSPulseConfig.class);
		when(config.showSessionSection()).thenReturn(true);
		OverlayManager overlayManager = mock(OverlayManager.class);
		Exception[] failure = new Exception[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				OSPulsePanel panel = new OSPulsePanel(config, null, null, null, null, null,
					mock(Plugin.class), mock(Client.class), overlayManager, null);
				CategorySectionSupport support = sessionSupport(panel);
				support.controller().setOnCanvas("session:profit", true);
				verify(overlayManager).add(any(CategoryOverlay.class));
				verify(overlayManager, never()).remove(any());

				when(config.showSessionSection()).thenReturn(false);
				panel.applySectionVisibility();

				verify(overlayManager).remove(any(CategoryOverlay.class));
			}
			catch (Exception e)
			{
				failure[0] = e;
			}
		});
		if (failure[0] != null)
		{
			throw failure[0];
		}
	}

	private static CategorySectionSupport sessionSupport(OSPulsePanel panel) throws Exception
	{
		Field listField = OSPulsePanel.class.getDeclaredField("sectionList");
		listField.setAccessible(true);
		SessionSection session = (SessionSection) ((List<?>) listField.get(panel)).get(0);
		Field supportField = SessionSection.class.getDeclaredField("categorySupport");
		supportField.setAccessible(true);
		return (CategorySectionSupport) supportField.get(session);
	}
}
