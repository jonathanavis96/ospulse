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

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
	/**
	 * Last-seen SourceLoot per category id, retained so a paused row keeps
	 * showing its frozen figures. ConcurrentHashMap because the canvas overlay's
	 * line supplier reads this on the CLIENT (render) thread while section
	 * rebuilds mutate it on the EDT — a plain HashMap can corrupt / CME under
	 * that concurrent get/put.
	 */
	private final Map<String, SourceLoot> lastSeenBySource = new ConcurrentHashMap<>();
	/**
	 * "Hide item" / "Hide loot" state: persistent hides distinct from {@link
	 * #hiddenSources}' reset-until-next-pickup semantics. See {@link
	 * LootHiddenState} javadoc.
	 */
	private final LootHiddenState hiddenState = new LootHiddenState();

	/**
	 * Whether the grayed "hidden items" tray is revealed at the bottom of the
	 * feed. Toggled by a source menu's "View hidden items" — a clickable toggle
	 * rather than a submenu list, so restoring an item is a single ✕ click on
	 * its grayed icon.
	 */
	private boolean showHiddenTray = false;
	/**
	 * Signature of the last rendered feed. A matching signature short-circuits
	 * {@link #rebuild} so an idle snapshot (identical loot) doesn't tear down
	 * and recreate every cell under the mouse — which previously made a hovered
	 * tooltip flicker/regenerate and often fail to appear at all.
	 */
	private String lastRenderSignature = null;

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

	/** One source's resolved display model for a render pass (post source/item hides). */
	private static final class DisplaySource
	{
		final SourceLoot src;
		final long shownValue;
		final boolean collapsed;

		DisplaySource(SourceLoot src, long shownValue, boolean collapsed)
		{
			this.src = src;
			this.shownValue = shownValue;
			this.collapsed = collapsed;
		}
	}

	private void rebuild(List<SourceLoot> sources)
	{
		lastLootSources = sources == null ? List.of() : sources;

		// Tolerate a null config the same way the pre-two-pass render did (it
		// only read config inside the non-empty branch) — some section tests
		// construct the feed without one.
		int minLootValue = config != null ? config.minLootValue() : 0;

		// ---- Pass 1: resolve the display model + a render signature WITHOUT
		// touching Swing. Paused sources are frozen at their last-seen figures;
		// reset/manually-hidden sources are dropped. Per-item "Hide item" hides
		// are subtracted from BOTH the grid and the value, so hiding an item
		// recalculates the loot value rather than only removing its icon.
		List<DisplaySource> display = new ArrayList<>();
		long newTotal = 0L;
		StringBuilder sig = new StringBuilder("min=").append(minLootValue)
			.append(";tray=").append(showHiddenTray).append(';');
		for (SourceLoot src : lastLootSources)
		{
			String source = src.getSource();
			String catId = categoryId(source);
			if (hiddenSources.contains(catId) || hiddenState.isSourceHidden(catId))
			{
				continue;
			}
			SourceLoot toShow = categorySupport.controller().isPaused(catId)
				? lastSeenBySource.getOrDefault(catId, src)
				: src;

			long hiddenValue = 0L;
			for (ItemStack item : toShow.getItems())
			{
				if (hiddenState.isItemFiltered(item))
				{
					hiddenValue += item.value();
				}
			}
			long shownValue = toShow.getTotalValue() - hiddenValue;
			boolean collapsed = collapsedSources.contains(source);

			display.add(new DisplaySource(toShow, shownValue, collapsed));
			newTotal += shownValue;

			sig.append(source).append('#').append(toShow.getCount())
				.append('|').append(shownValue).append('|').append(collapsed ? 'C' : 'E').append('|');
			if (!collapsed)
			{
				for (ItemStack item : toShow.getItems())
				{
					if (item.value() < minLootValue || hiddenState.isItemFiltered(item))
					{
						continue;
					}
					sig.append(item.getId()).append('x').append(item.getQuantity())
						.append('=').append(item.value()).append(',');
				}
			}
			sig.append(';');
		}
		if (showHiddenTray)
		{
			sig.append("hiddenItems=").append(hiddenState.hiddenItems().keySet())
				.append(";hiddenSrc=").append(hiddenState.hiddenSources().keySet());
		}

		// ---- Short-circuit: nothing visible changed, so leave the existing
		// components (and any hovered tooltip) exactly as they are.
		String signature = sig.toString();
		if (signature.equals(lastRenderSignature))
		{
			return;
		}
		lastRenderSignature = signature;

		// ---- Pass 2: render.
		total = newTotal;
		totalValue.setText(GpFormat.format(total));
		lootListPanel.removeAll();

		boolean trayHasContent = showHiddenTray && !hiddenState.isEmpty();
		if (display.isEmpty() && !trayHasContent)
		{
			lootListPanel.add(PanelWidgets.emptyRowLabel("No loot yet."));
		}
		else
		{
			for (DisplaySource ds : display)
			{
				lootListPanel.add(sourceHeaderRow(ds.src, ds.shownValue));
				if (ds.collapsed)
				{
					continue;
				}

				JPanel grid = new JPanel(new GridLayout(0, ICON_GRID_COLUMNS, 2, 2));
				grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				grid.setAlignmentX(Component.LEFT_ALIGNMENT);

				int shown = 0;
				for (ItemStack item : ds.src.getItems())
				{
					if (item.value() < minLootValue || hiddenState.isItemFiltered(item))
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

			if (showHiddenTray)
			{
				appendHiddenTray();
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

		// Format: "[ITEM NAME] x [qty]" / "GE: total (each ea)" / "HA: total (each ea)".
		long qty = item.getQuantity();
		StringBuilder tooltip = new StringBuilder("<html><b>")
			.append(item.getName())
			.append("</b> x ")
			.append(String.format("%,d", qty))
			.append("<br>GE: ")
			.append(GpFormat.format(item.value()))
			.append(" (").append(GpFormat.format(item.getUnitValue())).append(" ea)");

		// HA price is precomputed on the client thread (see SessionTracker.mergeItem);
		// -1 means it couldn't be resolved. Never call getItemComposition here — this
		// runs on the EDT and that method asserts the client thread.
		if (item.getHaPrice() >= 0)
		{
			long haEach = item.getHaPrice();
			tooltip.append("<br>HA: ").append(GpFormat.format(haEach * qty))
				.append(" (").append(GpFormat.format(haEach)).append(" ea)");
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
	private JPanel sourceHeaderRow(SourceLoot src, long shownValue)
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

		// Value shown excludes any per-item "Hide item" hides in this source.
		JLabel rightLabel = new JLabel(GpFormat.format(shownValue));
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

		// Attach the collapse toggle to the labels as well as the row: the
		// labels fill the whole header (BorderLayout CENTER/EAST) and, because
		// they carry a component popup menu, Swing delivers their mouse events
		// to them rather than bubbling to the parent row — so a listener only on
		// `row` never fires on a click over the label text (that was the "can't
		// minimize" regression). Mirror the popup-menu attachment: all three.
		MouseAdapter collapseToggle = new MouseAdapter()
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
		};
		row.addMouseListener(collapseToggle);
		leftLabel.addMouseListener(collapseToggle);
		rightLabel.addMouseListener(collapseToggle);
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

		// A clickable toggle (not a submenu list): reveals/hides the grayed
		// "hidden items" tray at the bottom of the feed, where each hidden item
		// is a grayed icon carrying a ✕ that adds it straight back to the feed.
		JMenuItem viewHidden = new JMenuItem(showHiddenTray ? "Hide hidden items" : "View hidden items");
		viewHidden.addActionListener(e ->
		{
			showHiddenTray = !showHiddenTray;
			applyResetsThenRebuild();
		});
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

	/** RuneLite item sprite footprint; the grayed tray cells are sized to it. */
	private static final int ITEM_SPRITE_W = 36;
	private static final int ITEM_SPRITE_H = 32;

	/**
	 * Appends the grayed "hidden items" tray to the bottom of the feed: one
	 * grayed icon per "Hide item" hide (a top-right ✕ adds it straight back to
	 * the feed) plus a ✕ chip per "Hide loot" source hide. Shown only while
	 * {@link #showHiddenTray} is on (toggled from a source's "View hidden
	 * items"). Item names are the retained last-seen names from {@link
	 * LootHiddenState} — never re-resolved via {@code
	 * ItemManager#getItemComposition}, which asserts the client thread (see the
	 * warning in {@link #iconCell}).
	 */
	private void appendHiddenTray()
	{
		JLabel heading = PanelWidgets.emptyRowLabel("Hidden items");
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		heading.setToolTipText("Items hidden from the feed — click a ✕ to add one back");
		heading.setBorder(new EmptyBorder(4, 0, 1, 0));
		lootListPanel.add(heading);

		Map<Integer, String> items = hiddenState.hiddenItems();
		Map<String, String> srcs = hiddenState.hiddenSources();
		if (items.isEmpty() && srcs.isEmpty())
		{
			lootListPanel.add(PanelWidgets.emptyRowLabel("Nothing hidden."));
			return;
		}

		if (!items.isEmpty())
		{
			JPanel grid = new JPanel(new GridLayout(0, ICON_GRID_COLUMNS, 2, 2));
			grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			grid.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (Map.Entry<Integer, String> entry : items.entrySet())
			{
				int itemId = entry.getKey();
				String name = entry.getValue() == null || entry.getValue().isEmpty()
					? ("Item #" + itemId) : entry.getValue();
				grid.add(buildHiddenItemCell(itemId, name));
			}
			grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
			lootListPanel.add(grid);
		}

		for (Map.Entry<String, String> entry : srcs.entrySet())
		{
			lootListPanel.add(buildHiddenSourceChip(entry.getKey(), entry.getValue()));
		}
	}

	/**
	 * One grayed icon cell for the hidden-items tray: the item sprite dimmed by
	 * a translucent overlay (marking it removed from the feed) with a small ✕
	 * pinned top-right (via {@link OverlayLayout}) that un-hides it. Mirrors the
	 * excluded-gear viewer's cell in {@code GearSection}.
	 */
	private JPanel buildHiddenItemCell(int itemId, String name)
	{
		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.CENTER);
		icon.setAlignmentX(0.5f);
		icon.setAlignmentY(0.5f);
		if (itemManager != null && itemId > 0)
		{
			itemManager.getImage(itemId, 1, false).addTo(icon);
		}

		// A translucent gray panel the size of the sprite, painted over it so
		// the hidden item reads as grayed-out regardless of async image load.
		JPanel dim = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(new Color(30, 30, 30, 150));
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		dim.setOpaque(false);
		dim.setAlignmentX(0.5f);
		dim.setAlignmentY(0.5f);
		dim.setPreferredSize(new Dimension(ITEM_SPRITE_W, ITEM_SPRITE_H));
		dim.setMaximumSize(new Dimension(ITEM_SPRITE_W, ITEM_SPRITE_H));

		JButton restore = new JButton("✕");
		restore.setFont(FontManager.getRunescapeSmallFont());
		restore.setFocusPainted(false);
		restore.setBorder(null);
		restore.setMargin(new Insets(0, 0, 0, 0));
		restore.setContentAreaFilled(false);
		restore.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		restore.setToolTipText("Add " + name + " back to the loot feed");
		restore.addActionListener(e ->
		{
			hiddenState.unhideItem(itemId);
			applyResetsThenRebuild();
		});

		JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		topRight.setOpaque(false);
		topRight.add(restore);

		JPanel overlay = new JPanel(new BorderLayout());
		overlay.setOpaque(false);
		overlay.setAlignmentX(0.5f);
		overlay.setAlignmentY(0.5f);
		overlay.setPreferredSize(new Dimension(ITEM_SPRITE_W, ITEM_SPRITE_H));
		overlay.setMaximumSize(new Dimension(ITEM_SPRITE_W, ITEM_SPRITE_H));
		overlay.setToolTipText(name + " — hidden from the loot feed");
		overlay.add(topRight, BorderLayout.NORTH);

		JPanel cell = new JPanel();
		cell.setLayout(new OverlayLayout(cell));
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Added front-to-back: ✕ overlay on top, dim over the icon, sprite below.
		cell.add(overlay);
		cell.add(dim);
		cell.add(icon);
		return cell;
	}

	/**
	 * A single ✕ chip for a whole hidden loot source ("Hide loot"), restoring
	 * the entire source to the feed on click. Sources have no single icon, so
	 * they render as a grayed name row rather than an icon cell.
	 */
	private JPanel buildHiddenSourceChip(String catId, String name)
	{
		JPanel chip = new JPanel(new BorderLayout(4, 0));
		chip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		chip.setBorder(new EmptyBorder(1, 0, 1, 0));
		chip.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel label = new JLabel(name);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());

		JButton restore = new JButton("✕");
		restore.setFont(FontManager.getRunescapeSmallFont());
		restore.setFocusPainted(false);
		restore.setBorder(null);
		restore.setMargin(new Insets(0, 0, 0, 0));
		restore.setContentAreaFilled(false);
		restore.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		restore.setToolTipText("Add the " + name + " loot back to the feed");
		restore.addActionListener(e ->
		{
			hiddenState.unhideSource(catId);
			applyResetsThenRebuild();
		});

		chip.add(label, BorderLayout.CENTER);
		chip.add(restore, BorderLayout.EAST);
		chip.setMaximumSize(new Dimension(Integer.MAX_VALUE, chip.getPreferredSize().height));
		return chip;
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
		showHiddenTray = false;
		lastRenderSignature = null;
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
