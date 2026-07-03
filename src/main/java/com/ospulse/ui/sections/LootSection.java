package com.ospulse.ui.sections;

import com.ospulse.OSPulseConfig;
import com.ospulse.model.ItemStack;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.session.SourceLoot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.ui.category.CategoryOverlay;
import com.ospulse.ui.category.CategorySectionSupport;

import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loot feed: a "Loot Tracker"-style list grouped by source (boss/NPC/activity),
 * each source header itself collapsible. Collapsed section summary shows total
 * loot value.
 *
 * <p>Each source header carries an XP-Tracker-style right-click menu (see
 * {@code com.ospulse.ui.category.CategoryContextMenu}, ported from RuneLite's
 * XP Tracker plugin). Loot sources are aggregated by the single {@code
 * SessionEngine} accumulator rather than each having independent state, so
 * "Reset"/"Reset others"/"Reset all" here hide the source(s) from this
 * section's display (a UI-level filter) rather than clearing any
 * engine-level totals; "Pause" freezes a source's row at its last-seen
 * figures instead of stopping the engine from tallying new loot from it.
 */
public final class LootSection extends CollapsibleSection
{
	public static final String KEY = "loot";
	private static final int LOOT_ROW_CAP = 30;
	private static final int ICON_GRID_COLUMNS = 5;

	private final OSPulseConfig config;
	private final ItemManager itemManager;
	private final CategorySectionSupport categorySupport;

	private final JLabel totalValue;
	private final JPanel lootListPanel;

	/** Latest source-grouped loot, retained so header clicks can re-render. */
	private List<SourceLoot> lastLootSources = List.of();
	/** Sources the user has collapsed (by name); persists across updates. */
	private final Set<String> collapsedSources = new HashSet<>();
	/** Sources hidden via "Reset"/"Reset others"/"Reset all" (by category id). */
	private final Set<String> hiddenSources = new HashSet<>();
	/** Last-seen SourceLoot per category id, retained so a paused row keeps showing its frozen figures. */
	private final Map<String, SourceLoot> lastSeenBySource = new HashMap<>();
	/**
	 * "Hide item" / "Hide loot" state: persistent hides distinct from {@link
	 * #hiddenSources}' reset-until-next-pickup semantics. See {@link
	 * LootHiddenState} javadoc.
	 */
	private final LootHiddenState hiddenState = new LootHiddenState();

	private long total;

	public LootSection(CollapseStore store, OSPulseConfig config, ItemManager itemManager,
		Plugin plugin, Client client, OverlayManager overlayManager)
	{
		super(KEY, "Loot feed", store);
		this.config = config;
		this.itemManager = itemManager;
		this.categorySupport = new CategorySectionSupport(plugin, client, overlayManager);

		totalValue = PanelWidgets.statRow(body(), "Total value");
		lootListPanel = new JPanel();
		lootListPanel.setLayout(new javax.swing.BoxLayout(lootListPanel, javax.swing.BoxLayout.Y_AXIS));
		lootListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		lootListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(lootListPanel);
	}

	private static String categoryId(String source)
	{
		return "loot:" + source;
	}

	/** Reset-epoch last observed per category id, so a "Reset" click is detected exactly once. */
	private final Map<String, Integer> lastSeenEpoch = new HashMap<>();

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		List<SourceLoot> sources = snapshot.getLootSources();
		for (SourceLoot src : sources == null ? List.<SourceLoot>of() : sources)
		{
			String catId = categoryId(src.getSource());
			detectReset(catId);
			if (!categorySupport.controller().isPaused(catId))
			{
				lastSeenBySource.put(catId, src);
			}
			// Register/refresh the canvas line supplier for this source
			// every apply, since sources can appear mid-session (XP Tracker
			// instead pre-registers one per Skill; loot sources are dynamic,
			// so we lazily register here).
			categorySupport.setLinesSupplier(catId, () -> canvasLines(catId));
		}
		rebuild(sources);
	}

	/** Marks {@code catId} hidden the moment its reset epoch advances (a "Reset"/"Reset others"/"Reset all" click). */
	private void detectReset(String catId)
	{
		int epoch = categorySupport.controller().resetEpoch(catId);
		Integer lastEpoch = lastSeenEpoch.get(catId);
		if (lastEpoch == null)
		{
			lastSeenEpoch.put(catId, epoch);
			return;
		}
		if (epoch != lastEpoch)
		{
			hiddenSources.add(catId);
			lastSeenEpoch.put(catId, epoch);
		}
	}

	/** Re-applies the latest known sources, e.g. after a collapse-toggle click that doesn't change reset/pause state. */
	private void applyResetsThenRebuild()
	{
		rebuild(lastLootSources);
	}

	private List<CategoryOverlay.Line> canvasLines(String catId)
	{
		SourceLoot src = lastSeenBySource.get(catId);
		if (src == null)
		{
			return List.of();
		}
		return List.of(new CategoryOverlay.Line("Value", GpFormat.format(src.getTotalValue())));
	}

	private void rebuild(List<SourceLoot> sources)
	{
		lastLootSources = sources == null ? List.of() : sources;
		lootListPanel.removeAll();

		// Build the display list: paused sources are frozen at their
		// last-seen figures (not the just-applied live ones); reset sources
		// are hidden entirely until they next appear with a genuinely new
		// pickup (handled naturally since hiddenSources is keyed by id and
		// cleared only by unpausing / the source reappearing after a client
		// restart clears in-memory state).
		List<SourceLoot> display = new ArrayList<>();
		for (SourceLoot src : lastLootSources)
		{
			String catId = categoryId(src.getSource());
			if (hiddenSources.contains(catId) || hiddenState.isSourceHidden(catId))
			{
				continue;
			}
			SourceLoot toShow = categorySupport.controller().isPaused(catId)
				? lastSeenBySource.getOrDefault(catId, src)
				: src;
			display.add(toShow);
		}

		total = 0L;
		for (SourceLoot src : display)
		{
			total += src.getTotalValue();
		}
		totalValue.setText(GpFormat.format(total));

		if (display.isEmpty())
		{
			lootListPanel.add(PanelWidgets.emptyRowLabel("No loot yet."));
		}
		else
		{
			int minLootValue = config.minLootValue();
			for (SourceLoot src : display)
			{
				lootListPanel.add(sourceHeaderRow(src));
				if (collapsedSources.contains(src.getSource()))
				{
					continue;
				}

				JPanel grid = new JPanel(new GridLayout(0, ICON_GRID_COLUMNS, 2, 2));
				grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				grid.setAlignmentX(Component.LEFT_ALIGNMENT);

				int shown = 0;
				for (ItemStack item : src.getItems())
				{
					if (item.value() < minLootValue)
					{
						continue;
					}
					if (hiddenState.isItemFiltered(item))
					{
						continue;
					}
					grid.add(iconCell(item));
					shown++;
					if (shown >= LOOT_ROW_CAP)
					{
						break;
					}
				}

				if (shown > 0)
				{
					// Don't let BoxLayout stretch the grid to fill leftover
					// vertical space (which would stretch every cell with it).
					grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
					lootListPanel.add(grid);
				}
			}
		}

		lootListPanel.revalidate();
		lootListPanel.repaint();
		refreshSummary();
	}

	/**
	 * A single loot-tracker-style icon cell: just the item sprite with its
	 * stack quantity overlaid (RuneLite draws the overlay for us via
	 * {@link ItemManager#getImage(int, int, boolean)}). Full details (name,
	 * quantity, GE value, and HA value where available) show as a tooltip on
	 * hover, since the icon alone has no room for them.
	 */
	private JLabel iconCell(ItemStack item)
	{
		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.CENTER);

		if (itemManager != null && item.getId() > 0)
		{
			int quantity = (int) item.getQuantity();
			itemManager.getImage(item.getId(), quantity, quantity > 1).addTo(icon);
		}

		StringBuilder tooltip = new StringBuilder("<html><b>")
			.append(item.getName())
			.append("</b><br>Qty: ")
			.append(String.format("%,d", item.getQuantity()))
			.append("<br>GE: ")
			.append(GpFormat.format(item.value()));

		// HA price is precomputed on the client thread (see SessionTracker.mergeItem);
		// -1 means it couldn't be resolved. Never call getItemComposition here — this
		// runs on the EDT and that method asserts the client thread.
		if (item.getHaPrice() >= 0)
		{
			long haValue = item.getHaPrice() * item.getQuantity();
			tooltip.append("<br>HA: ").append(GpFormat.format(haValue));
		}
		tooltip.append("</html>");
		icon.setToolTipText(tooltip.toString());

		icon.setComponentPopupMenu(buildItemMenu(item));

		return icon;
	}

	/** Right-click menu for a single item cell: just "Hide item". */
	private JPopupMenu buildItemMenu(ItemStack item)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		JMenuItem hideItem = new JMenuItem("Hide item");
		hideItem.addActionListener(e ->
		{
			hiddenState.hideItem(item);
			applyResetsThenRebuild();
		});
		menu.add(hideItem);

		return menu;
	}

	/**
	 * A clickable collapsible header for one loot source, e.g.
	 * "▾ Cerberus x206 … 24m". Clicking toggles the source's expanded state.
	 */
	private JPanel sourceHeaderRow(SourceLoot src)
	{
		boolean collapsed = collapsedSources.contains(src.getSource());
		String triangle = collapsed ? "▸" : "▾";
		String left = triangle + " " + src.getSource()
			+ (src.getCount() > 1 ? " x" + String.format("%,d", src.getCount()) : "");

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel leftLabel = new JLabel(left);
		leftLabel.setForeground(ColorScheme.BRAND_ORANGE);
		leftLabel.setFont(FontManager.getRunescapeSmallFont());

		JLabel rightLabel = new JLabel(GpFormat.format(src.getTotalValue()));
		rightLabel.setForeground(Color.WHITE);
		rightLabel.setFont(FontManager.getRunescapeSmallFont());
		rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(leftLabel, BorderLayout.CENTER);
		row.add(rightLabel, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

		final String source = src.getSource();
		final String catId = categoryId(source);

		JPopupMenu popupMenu = buildSourceMenu(catId, source);
		row.setComponentPopupMenu(popupMenu);
		leftLabel.setComponentPopupMenu(popupMenu);
		rightLabel.setComponentPopupMenu(popupMenu);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				// Right-click (popup trigger) opens the context menu instead
				// of toggling collapse - matches XpInfoBox, where the
				// right-click menu and left-click collapse toggle coexist on
				// the same component.
				if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e))
				{
					return;
				}
				if (!collapsedSources.remove(source))
				{
					collapsedSources.add(source);
				}
				applyResetsThenRebuild();
			}
		});
		return row;
	}

	/**
	 * Loot-tailored right-click menu for a source header: "Hide loot",
	 * "Reset", "View hidden items", and "Add to canvas"/"Remove from
	 * canvas". Deliberately does not reuse {@code categorySupport.buildMenu}
	 * (the generic XP-Tracker-style menu) since "Open Wise Old Man", "Reset
	 * others", "Reset all" and "Pause" don't fit a loot source.
	 */
	private JPopupMenu buildSourceMenu(String catId, String source)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		JMenuItem hideLoot = new JMenuItem("Hide loot");
		hideLoot.addActionListener(e ->
		{
			hiddenState.hideSource(catId, source);
			applyResetsThenRebuild();
		});
		menu.add(hideLoot);

		JMenuItem reset = new JMenuItem("Reset");
		reset.addActionListener(e -> categorySupport.controller().reset(catId, System.currentTimeMillis()));
		menu.add(reset);

		JMenuItem viewHidden = new JMenuItem("View hidden items");
		viewHidden.addActionListener(e -> showHiddenItemsMenu(viewHidden));
		menu.add(viewHidden);

		JMenuItem canvas = new JMenuItem(
			categorySupport.controller().isOnCanvas(catId) ? "Remove from canvas" : "Add to canvas");
		canvas.addActionListener(e ->
			categorySupport.controller().setOnCanvas(catId, !categorySupport.controller().isOnCanvas(catId)));
		menu.add(canvas);

		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
			{
				canvas.setText(categorySupport.controller().isOnCanvas(catId)
					? "Remove from canvas" : "Add to canvas");
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e)
			{
			}
		});

		return menu;
	}

	/**
	 * Shows a small popup listing every currently-hidden item and
	 * manually-hidden source, each with an "Unhide" action. Item names are
	 * taken from {@link LootHiddenState}'s retained last-seen names rather
	 * than re-resolved via {@code ItemManager#getItemComposition} (which
	 * asserts the client thread - see the warning in {@link #iconCell}).
	 */
	private void showHiddenItemsMenu(Component invoker)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		if (hiddenState.isEmpty())
		{
			JMenuItem none = new JMenuItem("Nothing hidden.");
			none.setEnabled(false);
			menu.add(none);
		}
		else
		{
			for (Map.Entry<Integer, String> entry : hiddenState.hiddenItems().entrySet())
			{
				int itemId = entry.getKey();
				String name = entry.getValue() == null || entry.getValue().isEmpty()
					? ("Item #" + itemId) : entry.getValue();
				JMenuItem unhide = new JMenuItem("Unhide item: " + name);
				unhide.addActionListener(e ->
				{
					hiddenState.unhideItem(itemId);
					applyResetsThenRebuild();
				});
				menu.add(unhide);
			}

			for (Map.Entry<String, String> entry : hiddenState.hiddenSources().entrySet())
			{
				String catId = entry.getKey();
				String name = entry.getValue();
				JMenuItem unhide = new JMenuItem("Unhide loot: " + name);
				unhide.addActionListener(e ->
				{
					hiddenState.unhideSource(catId);
					applyResetsThenRebuild();
				});
				menu.add(unhide);
			}
		}

		menu.show(invoker, 0, invoker.getHeight());
	}

	/**
	 * Clears every "Hide item" / "Hide loot" hide, e.g. as part of a
	 * panel-wide full reset. Does not affect {@link #hiddenSources} (the
	 * separate Reset/Reset-others/Reset-all "hidden until next pickup" set).
	 */
	public void clearHidden()
	{
		hiddenState.clear();
	}

	@Override
	public void removeAllCategoryOverlays()
	{
		categorySupport.removeAllOverlays();
	}

	/**
	 * Full panel reset (feature 11): clears every retained loot state — the
	 * "hidden until next pickup" source set, the persistent "Hide item"/"Hide
	 * loot" hides, collapsed-source and frozen paused-figure maps, the reset
	 * epochs and the running total — and re-renders an empty feed. The next
	 * snapshot repopulates it from scratch, so nothing stays hidden or frozen
	 * across a reset.
	 */
	@Override
	public void resetState()
	{
		hiddenSources.clear();
		collapsedSources.clear();
		lastSeenBySource.clear();
		lastSeenEpoch.clear();
		hiddenState.clear();
		total = 0;
		categorySupport.clearAll();
		rebuild(List.of());
	}

	@Override
	protected String summaryText()
	{
		return GpFormat.format(total);
	}

	// ------------------------------------------------- test seams (package)

	boolean isSourceHiddenForTest(String source)
	{
		return hiddenSources.contains(categoryId(source));
	}

	boolean hasHiddenItemsForTest()
	{
		return !hiddenState.isEmpty();
	}

	/** Simulates "Reset"-ing a source (the "hidden until next pickup" set) plus collapsing it. */
	void hideSourceForTest(String source)
	{
		hiddenSources.add(categoryId(source));
		collapsedSources.add(source);
		categorySupport.controller().setPaused(categoryId(source), true);
	}

	/** Simulates the per-item "Hide item" action (the persistent hide set). */
	void hideItemForTest(int itemId, String name)
	{
		hiddenState.hideItem(new com.ospulse.model.ItemStack(itemId, name, 1, 0L));
	}

	int lootListRowCountForTest()
	{
		return lootListPanel.getComponentCount();
	}
}
