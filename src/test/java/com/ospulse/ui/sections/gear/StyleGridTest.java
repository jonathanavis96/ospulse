package com.ospulse.ui.sections.gear;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The 2-column-vs-1-column decision for the attack-styles list.
 *
 * <p>Widths here are real measurements taken against the RuneScape small font
 * (2026-07-15) of a whole StyleRow — 18px icon + 4px gap + name + 4px gap +
 * ~22px DPS + 9px of border. At the live viewport (211px, 4px gap) a cell is
 * 103px.
 */
public class StyleGridTest
{
	private static final int VIEWPORT = 211;
	private static final int HGAP = 4;

	/** Full row widths: name text + 18px icon + 4 gap + 22 dps + 4 gap + 9 border = name + 57. */
	private static int row(int nameWidth)
	{
		return nameWidth + 57;
	}

	private static final int ACCURATE = row(44);
	private static final int RAPID = row(28);
	private static final int LONGRANGE = row(52);
	private static final int SHORT_FUSE = row(54);
	private static final int MEDIUM_FUSE = row(62);
	private static final int LONG_FUSE = row(49);
	private static final int CHOP = row(25);
	private static final int BLOCK = row(26);

	/**
	 * The case Jonathan called: a bow ranks Accurate/Rapid/Longrange, and
	 * Longrange is almost always worst, so it lands last and takes the full
	 * bottom row — where its 52px name is no problem.
	 */
	@Test
	public void bowPairsTheTwoShortStylesAndSpansLongrangeAcrossTheBottom()
	{
		List<Integer> bow = Arrays.asList(RAPID, ACCURATE, LONGRANGE);
		assertTrue("Rapid+Accurate pair fine; Longrange is exempt because it spans",
			StyleGrid.fitsTwoColumns(bow, VIEWPORT, HGAP));
		assertEquals("Longrange spans the bottom row", 2, StyleGrid.spanningRowIndex(3, true));
		assertEquals("two paired + one spanning = 2 visual rows", 2, StyleGrid.visualRows(3, true));
	}

	/**
	 * Chinchompas are the case the span cannot rescue — all three fuse names
	 * are too wide and only one of them can span.
	 */
	@Test
	public void chinchompaFallsBackToOneColumnBecauseAllThreeFusesAreTooWide()
	{
		List<Integer> chin = Arrays.asList(MEDIUM_FUSE, SHORT_FUSE, LONG_FUSE);
		assertFalse("the two PAIRED fuses don't fit a 103px cell, so 2 columns is out",
			StyleGrid.fitsTwoColumns(chin, VIEWPORT, HGAP));
		assertEquals("1 column = one visual row each", 3, StyleGrid.visualRows(3, false));
		assertEquals("nothing spans in a 1-column list", -1, StyleGrid.spanningRowIndex(3, false));
	}

	/** A 4-style melee weapon: all short names, even count, so a clean 2x2 with no span. */
	@Test
	public void fourShortStylesPairIntoTwoRowsWithNoSpanningRow()
	{
		List<Integer> melee = Arrays.asList(CHOP, row(28), row(29), BLOCK);
		assertTrue(StyleGrid.fitsTwoColumns(melee, VIEWPORT, HGAP));
		assertEquals("even count -> nothing spans", -1, StyleGrid.spanningRowIndex(4, true));
		assertEquals(2, StyleGrid.visualRows(4, true));
	}

	/**
	 * The self-correcting case: the span only saves a long name while it ranks
	 * LAST. If Longrange ever out-ranked Rapid it would land in a paired slot,
	 * and the width check has to catch that rather than truncate it.
	 */
	@Test
	public void aLongNameRankedIntoAPairedSlotForcesOneColumn()
	{
		List<Integer> bowLongrangeBest = Arrays.asList(LONGRANGE, RAPID, ACCURATE);
		assertFalse("Longrange is now paired, not spanning, so 2 columns must be rejected",
			StyleGrid.fitsTwoColumns(bowLongrangeBest, VIEWPORT, HGAP));
	}

	/** One lonely half-width cell beside a gap reads as a bug — give it the full width. */
	@Test
	public void aSingleStyleTakesTheFullWidthRatherThanHalf()
	{
		assertFalse(StyleGrid.fitsTwoColumns(Collections.singletonList(row(36)), VIEWPORT, HGAP));
		assertFalse(StyleGrid.fitsTwoColumns(Collections.emptyList(), VIEWPORT, HGAP));
	}

	/** The decision must follow the real width, not a baked-in assumption about it. */
	@Test
	public void aNarrowerViewportRejectsWhatAWideOneAccepts()
	{
		List<Integer> melee = Arrays.asList(CHOP, BLOCK, CHOP, BLOCK);
		assertTrue(StyleGrid.fitsTwoColumns(melee, VIEWPORT, HGAP));
		assertFalse("half of a 120px viewport can't hold an 82px row",
			StyleGrid.fitsTwoColumns(melee, 120, HGAP));
	}
}
