package com.ospulse.ui.sections.gear;

import java.util.List;

/**
 * Decides how the ranked attack-style rows are arranged: a compact 2-column
 * grid (with the last row spanning the full width when there is an odd one
 * out), or a plain 1-column list when the names simply do not fit.
 *
 * <p><b>Why this is not a fixed choice.</b> The styles list lives in
 * RuneLite's fixed 225px panel — about 211px usable, so ~103px per cell in a
 * 2-column grid. A row must hold an 18px icon, the style name, and the DPS,
 * which leaves roughly 46px of text. Measured against the real font
 * (2026-07-15), 4 of the 33 style names blow that budget outright —
 * {@code Longrange} (52px) and the three chinchompa fuses, {@code Short fuse}
 * (54), {@code Medium fuse} (62), {@code Long fuse} (49). The rest fit.
 *
 * <p>So neither layout is right for every weapon: forcing 2 columns truncates
 * those names, and forcing 1 column doubles the height of the block for the
 * ~29 names that were fine. This picks per weapon, from the rows' own measured
 * widths, so it can never drift when a name or a font changes.
 *
 * <p><b>The odd-row span</b> (Jonathan's suggestion, 2026-07-15) is what saves
 * the common case: a bow ranks Accurate/Rapid/Longrange, and Longrange is
 * almost always the worst of the three, so it lands last and takes the full
 * bottom row where 52px is no problem. Only the PAIRED rows are width-checked
 * — the spanning row gets the whole width and always fits. If a long name ever
 * did rank into a paired slot, the check catches it and falls back to 1 column
 * on its own.
 *
 * <p>Chinchompas are the case the span cannot rescue: all three fuse names are
 * too wide, and only one of them can span. They fall back to 1 column.
 */
public final class StyleGrid
{
	private StyleGrid()
	{
	}

	/**
	 * Whether {@code rowWidths} (in ranked order, each row's own preferred
	 * pixel width) fit a 2-column grid of {@code availableWidth}.
	 *
	 * <p>A single row is reported as NOT fitting: one lonely half-width cell
	 * beside a gap looks like a bug, and full width is both simpler and always
	 * correct for it.
	 */
	public static boolean fitsTwoColumns(List<Integer> rowWidths, int availableWidth, int hgap)
	{
		if (rowWidths.size() < 2)
		{
			return false;
		}
		int cellWidth = (availableWidth - hgap) / 2;
		// The odd row out spans the full width, so it is exempt from the check.
		int paired = rowWidths.size() - (rowWidths.size() % 2);
		for (int i = 0; i < paired; i++)
		{
			if (rowWidths.get(i) > cellWidth)
			{
				return false;
			}
		}
		return true;
	}

	/** Visual row count for {@code rowCount} rows in the chosen layout — drives the viewport height. */
	public static int visualRows(int rowCount, boolean twoColumns)
	{
		return twoColumns ? rowCount / 2 + (rowCount % 2) : rowCount;
	}

	/** Index of the row that spans the full width, or -1 when none does. */
	public static int spanningRowIndex(int rowCount, boolean twoColumns)
	{
		return twoColumns && rowCount % 2 == 1 ? rowCount - 1 : -1;
	}
}
