package com.ospulse.ui.sections.gear;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure unit tests for {@link CollapsibleHeading} (issue #11's collapsible
 * "Excluded from suggestions" list) — no Swing needed.
 */
public class CollapsibleHeadingTest
{
	@Test
	public void headingText_usesExpandedGlyph_whenNotCollapsed()
	{
		assertEquals("▾ Excluded from suggestions",
			CollapsibleHeading.headingText("Excluded from suggestions", false));
	}

	@Test
	public void headingText_usesCollapsedGlyph_whenCollapsed()
	{
		assertEquals("▸ Excluded from suggestions",
			CollapsibleHeading.headingText("Excluded from suggestions", true));
	}

	@Test
	public void bodyVisible_hidesOutright_whenListEmpty_regardlessOfCollapseState()
	{
		assertFalse(CollapsibleHeading.bodyVisible(false, false));
		assertFalse(CollapsibleHeading.bodyVisible(false, true));
	}

	@Test
	public void bodyVisible_composesWithNonEmptyList()
	{
		assertTrue(CollapsibleHeading.bodyVisible(true, false));
		assertFalse(CollapsibleHeading.bodyVisible(true, true));
	}
}
