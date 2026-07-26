package com.ospulse.ui.sections.gear;

/**
 * Pure glyph/visibility logic for a single clickable "▸"/"▾"
 * collapsible heading (issue #11's "Excluded from suggestions" list) —
 * mirrors {@code LootSection}'s per-row collapse-triangle idiom, but for one
 * section-wide toggle rather than a triangle per row.
 *
 * <p>Composes with a panel's pre-existing empty-list self-hide: an empty list
 * has nothing to collapse (the whole panel hides, same as before this
 * feature existed), while a collapsed non-empty list keeps its heading up
 * (so the user can expand it again) but hides the body below it. Kept free of
 * any Swing/{@code GearSection} state so it is trivially unit-testable.
 */
public final class CollapsibleHeading
{
	private CollapsibleHeading()
	{
	}

	private static final String EXPANDED_GLYPH = "▾"; // ▾
	private static final String COLLAPSED_GLYPH = "▸"; // ▸

	/** The heading text to display, e.g. {@code "▾ Excluded from suggestions"}. */
	public static String headingText(String label, boolean collapsed)
	{
		return (collapsed ? COLLAPSED_GLYPH : EXPANDED_GLYPH) + " " + label;
	}

	/**
	 * Whether the collapsible body (below the heading) should render. An
	 * empty list always hides the body (nothing to show); a non-empty list
	 * shows it unless collapsed.
	 */
	public static boolean bodyVisible(boolean hasItems, boolean collapsed)
	{
		return hasItems && !collapsed;
	}
}
