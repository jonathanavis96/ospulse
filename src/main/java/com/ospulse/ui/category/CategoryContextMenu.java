package com.ospulse.ui.category;

import net.runelite.client.util.LinkBrowser;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.border.EmptyBorder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the per-row right-click {@link JPopupMenu} that RuneLite's own XP
 * Tracker plugin shows on each skill box ({@code
 * net.runelite.client.plugins.xptracker.XpInfoBox}, constructor body around
 * the {@code openXpTracker}/{@code reset}/{@code resetOthers}/{@code
 * pauseSkill}/{@code canvasItem} menu items — BSD-2-Clause, see repo {@code
 * NOTICE}), generalised from "one menu per {@code Skill}" to "one menu per
 * arbitrary category id" so the same builder attaches to session-stat rows,
 * loot-source rows and XP-skill rows.
 *
 * <p>Differences from the original:
 * <ul>
 *   <li>"Reset/hr" is folded into plain "Reset" — our categories don't track
 *   a separate per-hour-only counter the way {@code XpStateSingle} does.
 *   <li>"Pause all" is exposed as its own menu item rather than only via a
 *   panel-level control, since each section builds its own menu instance.
 *   <li>The Wise Old Man link takes an arbitrary RSN + optional metric
 *   instead of being skill-specific, so loot/session categories can link to
 *   the player's overall WOM profile.
 * </ul>
 */
public final class CategoryContextMenu
{
	private CategoryContextMenu()
	{
	}

	/** Supplies the RSN to use for the "Open Wise Old Man" link; may return null/blank if unknown. */
	public interface RsnSupplier
	{
		String getRsn();
	}

	/**
	 * Builds a popup menu wired to {@code controller} for the single category
	 * {@code categoryId}. The caller (a {@code CollapsibleSection} row) is
	 * responsible for attaching it via {@code JComponent#setComponentPopupMenu}.
	 *
	 * @param controller   the shared registry backing reset/pause/canvas state
	 * @param categoryId   the category this menu instance controls
	 * @param rsnSupplier  provides the current player's RSN for the WOM link
	 * @param womMetric    optional Wise Old Man metric segment (e.g. a skill
	 *                     name like {@code "woodcutting"}); {@code null} or
	 *                     blank links to the player's overall gains instead
	 * @param nowMs        current time, stamped on reset actions
	 */
	public static JPopupMenu build(CategoryController controller, String categoryId,
		RsnSupplier rsnSupplier, String womMetric, java.util.function.LongSupplier nowMs)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		JMenuItem openWom = new JMenuItem("Open Wise Old Man");
		openWom.addActionListener(e -> LinkBrowser.browse(buildWiseOldManUrl(
			rsnSupplier == null ? null : rsnSupplier.getRsn(), womMetric)));
		menu.add(openWom);

		JMenuItem reset = new JMenuItem("Reset");
		reset.addActionListener(e -> controller.reset(categoryId, nowMs.getAsLong()));
		menu.add(reset);

		JMenuItem resetOthers = new JMenuItem("Reset others");
		resetOthers.addActionListener(e -> controller.resetOthers(categoryId, nowMs.getAsLong()));
		menu.add(resetOthers);

		JMenuItem resetAll = new JMenuItem("Reset all");
		resetAll.addActionListener(e -> controller.resetAll(nowMs.getAsLong()));
		menu.add(resetAll);

		JMenuItem pause = new JMenuItem(controller.isPaused(categoryId) ? "Unpause" : "Pause");
		pause.addActionListener(e -> controller.setPaused(categoryId, !controller.isPaused(categoryId)));
		menu.add(pause);

		JMenuItem pauseAll = new JMenuItem("Pause all");
		pauseAll.addActionListener(e -> controller.setPausedAll(true));
		menu.add(pauseAll);

		JMenuItem canvas = new JMenuItem(
			controller.isOnCanvas(categoryId) ? "Remove from canvas" : "Add to canvas");
		canvas.addActionListener(e -> controller.toggleOnCanvas(categoryId));
		menu.add(canvas);

		// Refresh the toggle-style labels (Pause/Unpause, Add/Remove canvas)
		// each time the menu is about to show, exactly as XpInfoBox's
		// PopupMenuListener refreshes canvasItem's text on
		// popupMenuWillBecomeVisible - state can change between menu opens
		// without the menu itself being rebuilt.
		menu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
				pause.setText(controller.isPaused(categoryId) ? "Unpause" : "Pause");
				canvas.setText(controller.isOnCanvas(categoryId) ? "Remove from canvas" : "Add to canvas");
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});

		return menu;
	}

	/**
	 * Builds a wiseoldman.net gains URL for {@code rsn}, adapted from {@code
	 * XpPanel#buildXpTrackerUrl} (BSD-2-Clause) but without the seasonal/league
	 * host switch, since that requires a {@code Client} world-type read the
	 * generic category menu doesn't have; players on a league world simply
	 * get linked to the normal wiseoldman.net host instead of league.wiseoldman.net.
	 * Returns {@code ""} if {@code rsn} is null/blank (mirrors the null-player
	 * case in the original, which also returns "").
	 */
	static String buildWiseOldManUrl(String rsn, String metric)
	{
		if (rsn == null || rsn.trim().isEmpty())
		{
			return "";
		}
		String encodedName = URLEncoder.encode(rsn.trim(), StandardCharsets.UTF_8);
		String metricParam = metric == null || metric.trim().isEmpty()
			? "overall"
			: URLEncoder.encode(metric.trim().toLowerCase(java.util.Locale.ROOT), StandardCharsets.UTF_8);
		return "https://wiseoldman.net/players/" + encodedName + "/gained"
			+ "?metric=" + metricParam + "&period=week";
	}
}
