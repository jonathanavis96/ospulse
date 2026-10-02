package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.optimizer.GearOptimizer;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.ui.sections.gear.ItemEligibility;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Test-only helpers that drive {@link GearSection}'s budget/expensive-threshold
 * text fields the way a real K/M-suffixed user entry would, without a
 * production accessor seam. Lives in src/test because the Plugin Hub's
 * token guard only counts src/main.
 */
final class GearSectionTestOps
{
	private GearSectionTestOps()
	{
	}

	static void setBudgetText(GearSection section, String text)
	{
		String trimmed = text == null ? "" : text.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.endsWith("m"))
		{
			section.budgetField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.budgetMToggle.setSelected(true);
		}
		else if (lower.endsWith("k"))
		{
			section.budgetField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.budgetKToggle.setSelected(true);
		}
		else
		{
			section.budgetField.setText(trimmed);
		}
	}

	static void setExpensiveThresholdText(GearSection section, String text)
	{
		String trimmed = text == null ? "" : text.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.endsWith("m"))
		{
			section.expensiveThresholdField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.expensiveThresholdMToggle.setSelected(true);
		}
		else if (lower.endsWith("k"))
		{
			section.expensiveThresholdField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.expensiveThresholdKToggle.setSelected(true);
		}
		else
		{
			section.expensiveThresholdField.setText(trimmed);
		}
	}

	/** Simulates a real mouse click on the icon cell at {@code index} in the item-picker grid (exercises ItemGridCell's own click handler, not just the filteredItems seam). */
	static void clickItemGridCellForTest(GearSection s, int index)
	{
		Component cell = s.itemGridPanel.getComponent(index);
		for (MouseListener listener : cell.getMouseListeners())
		{
			listener.mousePressed(new MouseEvent(cell, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 0, 0, 1, false));
		}
	}

	/**
	 * Test hook mirroring the right-click "Exclude from suggestions" on a slot
	 * cell: excludes whatever item that cell is currently SHOWING (read from
	 * renderedSlotIds, exactly as maybeShowSlotExcludePopup does), or no-ops
	 * for an empty cell.
	 */
	static void rightClickExcludeSlotForTest(GearSection s, int slot)
	{
		int shownId = slot >= 0 && slot < s.renderedSlotIds.length ? s.renderedSlotIds[slot] : -1;
		if (shownId > 0)
		{
			s.excludeFromSuggestions(shownId);
		}
	}

	/**
	 * Test seam mirroring a real right-click on the WEAPON slot cell: builds
	 * the exact popup maybeShowSlotExcludePopup would show for whatever item
	 * that cell is currently rendering (read from renderedSlotIds), so tests
	 * can assert the "Set darts" submenu is present for a blowpipe and absent
	 * otherwise without driving real mouse events.
	 */
	static JPopupMenu weaponSlotPopupForTest(GearSection s)
	{
		int shownId = s.renderedSlotIds[WhatIfLoadout.WEAPON_SLOT];
		String name = GearSection.itemDisplayName(EquipmentIndexRepository.getInstance(), shownId);
		return s.buildExcludePopup(shownId, name, WhatIfLoadout.WEAPON_SLOT);
	}

	/**
	 * Runs the optimizer SYNCHRONOUSLY for tests (bypassing the real
	 * {@code SwingWorker}, whose background thread + {@code invokeLater}
	 * hand-off is awkward to await deterministically in a headless test) by
	 * mirroring runOptimizer's resolver-vs-owned-only branching and calling
	 * onOptimizerResult directly on the calling (EDT) thread. If a fake
	 * OptimizerPriceResolver was injected via the 6-arg constructor, this
	 * exercises it too — as long as it calls back synchronously (as a test
	 * fake should), no threading is involved.
	 */
	static void runOptimizerSyncForTest(GearSection s)
	{
		long budget = s.resolvedBudget();
		Map<Integer, Long> ownedPrices = s.ownedPriceMap();

		if (s.priceResolver == null)
		{
			GearOptimizer.Request request = s.buildRequest(budget, ownedPrices,
				ItemEligibility.resolveOptimizerPriceSource(id -> ownedPrices.getOrDefault(id, 0L), Collections.emptySet()),
				Collections.emptyMap(), Collections.emptySet(), s.optimizerConstraint());
			s.lastOptimizerNeedsProtection = Collections.emptySet();
			s.onOptimizerResult(GearOptimizer.optimize(request));
			return;
		}

		s.priceResolver.resolve(candidateIds(ownedPrices), lookup ->
		{
			GearOptimizer.Request request = s.buildRequest(budget, ownedPrices,
				ItemEligibility.resolveOptimizerPriceSource(id -> lookup.prices().getOrDefault(id, 0L), lookup.untradeableIds()),
				lookup.riskValues(), lookup.needsProtection(), s.optimizerConstraint());
			s.lastOptimizerNeedsProtection = lookup.needsProtection();
			s.onOptimizerResult(GearOptimizer.optimize(request));
		});
	}

	private static Set<Integer> candidateIds(Map<Integer, Long> ownedPrices)
	{
		Set<Integer> candidateIds = new HashSet<>(EquipmentIndexRepository.getInstance().allItemIds());
		candidateIds.addAll(ownedPrices.keySet());
		// Mirror withResolvedPrices: craft-ingredient ids must be priced too.
		candidateIds.addAll(ItemEligibility.UNTRADEABLE_CRAFT_INGREDIENT.values());
		return candidateIds;
	}

	/**
	 * Mirrors runAndRankStyles synchronously for tests (bypassing the real
	 * {@code SwingWorker}, which isn't awaitable headless — same reason
	 * runOptimizerSyncForTest exists): optimises all five styles on the
	 * calling thread and hands them to the same applyRankedStyleResults the
	 * async path uses.
	 */
	static void runOptimizerAndRankStylesSyncForTest(GearSection s)
	{
		long budget = s.resolvedBudget();
		Map<Integer, Long> ownedPrices = s.ownedPriceMap();
		CombatStyle selected = s.optimizerConstraint();

		if (s.priceResolver == null)
		{
			GearOptimizer.PriceSource priceSource = ItemEligibility.resolveOptimizerPriceSource(
				id -> ownedPrices.getOrDefault(id, 0L), Collections.emptySet());
			s.lastOptimizerNeedsProtection = Collections.emptySet();
			s.applyRankedStyleResults(
				optimizeAllStyles(s, budget, ownedPrices, priceSource, Collections.emptyMap(), Collections.emptySet()),
				selected);
			return;
		}

		s.priceResolver.resolve(candidateIds(ownedPrices), lookup ->
		{
			GearOptimizer.PriceSource priceSource = ItemEligibility.resolveOptimizerPriceSource(
				id -> lookup.prices().getOrDefault(id, 0L), lookup.untradeableIds());
			s.lastOptimizerNeedsProtection = lookup.needsProtection();
			s.applyRankedStyleResults(
				optimizeAllStyles(s, budget, ownedPrices, priceSource, lookup.riskValues(), lookup.needsProtection()),
				selected);
		});
	}

	/** Optimises every STYLE_ORDER style with one resolved price/risk-value pair. */
	private static Map<CombatStyle, GearOptimizer.Result> optimizeAllStyles(GearSection s,
		long budget, Map<Integer, Long> ownedPrices, GearOptimizer.PriceSource priceSource,
		Map<Integer, Long> riskValues, Set<Integer> needsProtection)
	{
		Map<CombatStyle, GearOptimizer.Result> results = new LinkedHashMap<>();
		for (CombatStyle style : GearSection.STYLE_ORDER)
		{
			results.put(style,
				GearOptimizer.optimize(s.buildRequest(budget, ownedPrices, priceSource, riskValues,
					needsProtection, style)));
		}
		return results;
	}

	/** Simulates a user click on the 5-way selector's button for {@code style}. */
	static void clickOptimizerStyleForTest(GearSection s, CombatStyle style)
	{
		for (int i = 0; i < GearSection.STYLE_ORDER.length; i++)
		{
			if (GearSection.STYLE_ORDER[i] == style)
			{
				s.styleButtons[i].doClick();
				return;
			}
		}
		throw new IllegalArgumentException("no selector button for " + style);
	}

	static int countComponentsNamed(Container root, String name)
	{
		int count = 0;
		for (Component c : root.getComponents())
		{
			if (name.equals(c.getName()))
			{
				count++;
			}
			if (c instanceof Container)
			{
				count += countComponentsNamed((Container) c, name);
			}
		}
		return count;
	}

	/**
	 * Test-only inspection seam for the needsProtection highlight/tooltip
	 * (items #5/#6): the suggested-item icon {@link JLabel} of swap row
	 * {@code rowIndex} (0-based; the rigid-area spacers renderSwapList
	 * interleaves between rows are skipped automatically since they aren't
	 * {@link JPanel}s) — see buildSwapRow.
	 */
	static JLabel suggestedIconForTest(GearSection s, int rowIndex)
	{
		int seen = 0;
		for (Component c : s.swapList.getComponents())
		{
			if (!(c instanceof JPanel))
			{
				continue;
			}
			if (seen != rowIndex)
			{
				seen++;
				continue;
			}
			JPanel row = (JPanel) c;
			Component west = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.WEST);
			JPanel iconsPanel = (JPanel) west;
			Component last = iconsPanel.getComponent(iconsPanel.getComponentCount() - 1);
			return last instanceof JLabel ? (JLabel) last : (JLabel) ((JPanel) last).getComponent(0);
		}
		throw new IllegalArgumentException("no swap row at index " + rowIndex);
	}

	/**
	 * Item #6d: simulates a REAL mouse press on the style row's CENTER child
	 * label — the area users actually click, and the exact component that used
	 * to swallow the press (its tooltip's ToolTipManager listener made it the
	 * mouse-event target instead of the row). Dispatches to the child's own
	 * listeners only, exactly like Swing's deepest-target dispatch does.
	 */
	static void pressStyleRowLabelForTest(GearSection s, int index)
	{
		GearSection.StyleRow row = s.styleRows.get(index);
		Component child = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER);
		for (MouseListener listener : child.getMouseListeners())
		{
			listener.mousePressed(new MouseEvent(child, MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, 1, 1, 1, false));
		}
	}

	/**
	 * Strips the HTML markup that styles "cent" numbers (DPS, accuracy, avg
	 * hit, TTK, overkill — see CentFormat; "cent" meaning the
	 * fractional/decimal part, by analogy with money cents) so a test
	 * asserts the displayed VALUE, not its presentation. Plain (non-HTML)
	 * text — e.g. the "-" placeholder — passes through unchanged.
	 */
	static String plainTextForTest(String text)
	{
		if (text == null || !text.startsWith("<html>"))
		{
			return text;
		}
		return text.replaceAll("<[^>]*>", "")
			.replace("&middot;", "·")
			.replace("&nbsp;", " ")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&amp;", "&");
	}

	/** Rebuilds and returns the right-click swap menu's current item labels for the currently selected style (mirrors what a real right-click would show). */
	static List<String> potionVariantPopupLabelsForTest(GearSection s)
	{
		JPopupMenu menu = new JPopupMenu();
		s.populatePotionVariantPopup(menu);
		List<String> labels = new ArrayList<>();
		for (Component c : menu.getComponents())
		{
			if (c instanceof JMenuItem)
			{
				labels.add(((JMenuItem) c).getText());
			}
		}
		return labels;
	}

	/** The rendered text of every current gearOverrideNotePanel advisory line, in order. */
	static List<String> gearOverrideNoteTextsForTest(GearSection s)
	{
		List<String> texts = new ArrayList<>();
		for (Component c : s.gearOverrideNotePanel.getComponents())
		{
			if (c instanceof JTextArea)
			{
				texts.add(((JTextArea) c).getText());
			}
		}
		return texts;
	}
}
