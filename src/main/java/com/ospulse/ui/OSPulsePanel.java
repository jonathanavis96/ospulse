package com.ospulse.ui;

import com.ospulse.OSPulseConfig;
import com.ospulse.integration.PriceTrendService;
import com.ospulse.session.SessionListener;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.sections.GearSection;
import com.ospulse.ui.sections.GeSection;
import com.ospulse.ui.sections.HoldingsSection;
import com.ospulse.ui.sections.LootSection;
import com.ospulse.ui.sections.SessionSection;
import com.ospulse.ui.sections.WealthSection;
import com.ospulse.ui.sections.XpSection;

import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-side view of the session tracker: a RuneLite side panel showing the
 * current session's profit, net-worth breakdown, loot feed, XP gains, Grand
 * Exchange offers and top holdings — each in a {@link CollapsibleSection} the
 * user can minimize.
 *
 * <p>This class is a thin assembler: it builds the sections once, owns the
 * scroll/empty-state chrome, persists which sections are collapsed via
 * {@link ConfigManager}, and on every snapshot dispatches to each section's
 * {@link CollapsibleSection#apply(SessionSnapshot)} on the Swing EDT.
 */
public class OSPulsePanel extends PluginPanel implements SessionListener
{
	/** Config key (raw, not declared on the interface) for collapsed sections. */
	private static final String COLLAPSED_KEY = "collapsedSections";

	private final OSPulseConfig config;
	private final ConfigManager configManager;

	private final JPanel emptyStatePanel;
	private final JPanel contentPanel;
	private final CardHolder cardHolder;

	/** Every section, always constructed (order = display order). Visibility is a
	 *  layout decision, not a construction one — see {@link #rebuildSectionsColumn()}. */
	private final List<CollapsibleSection> sectionList = new ArrayList<>();

	/** The BoxLayout column that holds the currently-visible sections + spacers. */
	private final JPanel sectionsColumn;

	/** Most recent snapshot, retained so a section turned on live can be fed at once. */
	private SessionSnapshot lastSnapshot;

	/** Retained (not just in {@link #sectionList}) so {@link #setBankHighlighter} has a typed target to forward to. */
	private final GearSection gearSection;

	private Runnable resetCallback = () -> {};

	public OSPulsePanel(OSPulseConfig config, ItemManager itemManager, ConfigManager configManager,
		PriceTrendService priceTrendService, SkillIconManager skillIconManager,
		net.runelite.client.game.SpriteManager spriteManager,
		Plugin plugin, Client client, OverlayManager overlayManager,
		GearSection.OptimizerPriceResolver optimizerPriceResolver)
	{
		super(false);
		Objects.requireNonNull(config, "config");
		this.config = config;
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		add(buildHeader(), BorderLayout.NORTH);

		emptyStatePanel = buildEmptyState();

		CollapsibleSection.CollapseStore store = new ConfigCollapseStore();

		// Feature: hideable panel sections (OSPulseConfig "Panel sections"). Every
		// section is constructed unconditionally (construction is inert — no
		// threads/overlays), so a config toggle is purely a layout decision that
		// {@link #applySectionVisibility()} can apply live without destroying any
		// section's retained state. Which ones are actually shown is decided by
		// {@link #rebuildSectionsColumn()} reading the {@code show*Section} flags.
		sectionList.add(new SessionSection(store, plugin, client, overlayManager));
		sectionList.add(new LootSection(store, config, itemManager, plugin, client, overlayManager));
		sectionList.add(new XpSection(store, skillIconManager, plugin, client, overlayManager));
		gearSection = new GearSection(store, itemManager, skillIconManager, spriteManager, configManager,
			optimizerPriceResolver);
		sectionList.add(gearSection);
		sectionList.add(new GeSection(store, itemManager));
		sectionList.add(new WealthSection(store));
		sectionList.add(new HoldingsSection(store, itemManager, config, priceTrendService, configManager));

		// A width-tracking column so nothing is laid out wider than the fixed
		// side-panel width; the widest row ellipsizes within its row instead of
		// pushing content past the (horizontally non-scrolling) viewport edge.
		sectionsColumn = new ScrollablePanel();
		sectionsColumn.setLayout(new BoxLayout(sectionsColumn, BoxLayout.Y_AXIS));
		sectionsColumn.setBackground(ColorScheme.DARK_GRAY_COLOR);
		rebuildSectionsColumn();

		JScrollPane scrollPane = new JScrollPane(sectionsColumn);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Never scroll horizontally — the side panel has a fixed width; rows must
		// fit and long names truncate rather than push the panel wider.
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		contentPanel = new JPanel(new BorderLayout());
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		contentPanel.add(scrollPane, BorderLayout.CENTER);

		cardHolder = new CardHolder(this, emptyStatePanel, contentPanel);
		showEmptyState();
	}

	/**
	 * Wires the Reset button to {@code resetCallback}. Defaults to a no-op; the
	 * plugin assembles this to the tracker's {@code resetSession()}.
	 */
	public void setResetCallback(Runnable resetCallback)
	{
		this.resetCallback = resetCallback == null ? () -> {} : resetCallback;
	}

	/** Pass-through to {@link GearSection#setBankHighlighter} — the panel doesn't otherwise know about bank tags. */
	public void setBankHighlighter(com.ospulse.integration.BankRecommendationHighlighter bankHighlighter)
	{
		gearSection.setBankHighlighter(bankHighlighter);
	}

	/**
	 * Feature 11 — full Reset. Wipes every section's retained UI state
	 * <em>first</em> (baselines, frozen/hidden figures, what-if/optimiser
	 * selections), then runs the engine {@link #resetCallback} which re-anchors
	 * the session and pushes a fresh snapshot. Order matters: clearing the
	 * section baselines before the re-anchored snapshot arrives is what stops
	 * the stale-baseline "phantom profit" (see {@code SessionSection.resetState}).
	 * Runs on the Swing EDT (the button's action); the engine reset marshals its
	 * snapshot back to the EDT later, by which point the baselines are already
	 * cleared.
	 */
	void onReset()
	{
		resetSections();
		resetCallback.run();
	}

	/** Dispatches {@link CollapsibleSection#resetState()} to every section. */
	private void resetSections()
	{
		for (CollapsibleSection section : sectionList)
		{
			section.resetState();
		}
	}

	/** Whether {@code section} is switched on by its {@code show*Section} config flag. */
	private boolean isSectionEnabled(CollapsibleSection section)
	{
		if (section instanceof SessionSection)
		{
			return config.showSessionSection();
		}
		if (section instanceof LootSection)
		{
			return config.showLootSection();
		}
		if (section instanceof XpSection)
		{
			return config.showXpSection();
		}
		if (section instanceof GearSection)
		{
			return config.showGearSection();
		}
		if (section instanceof GeSection)
		{
			return config.showGeSection();
		}
		if (section instanceof WealthSection)
		{
			return config.showWealthSection();
		}
		if (section instanceof HoldingsSection)
		{
			return config.showHoldingsSection();
		}
		return true;
	}

	/**
	 * (Re)populates {@link #sectionsColumn} with only the enabled sections, in
	 * {@link #sectionList} order, spacer-separated. Sections are re-parented, not
	 * recreated, so their retained UI state survives a toggle. Must run on the EDT.
	 */
	private void rebuildSectionsColumn()
	{
		sectionsColumn.removeAll();
		boolean first = true;
		for (CollapsibleSection section : sectionList)
		{
			if (!isSectionEnabled(section))
			{
				continue;
			}
			if (!first)
			{
				sectionsColumn.add(PanelWidgets.spacer());
			}
			first = false;
			sectionsColumn.add(section);
		}
	}

	/**
	 * Live-applies the {@code show*Section} toggles without a plugin restart:
	 * re-lays-out the visible sections and feeds any newly-shown section the last
	 * snapshot so it isn't blank until the next game tick. Called from the plugin's
	 * {@code onConfigChanged}. Runs on the EDT.
	 */
	public void applySectionVisibility()
	{
		rebuildSectionsColumn();
		if (lastSnapshot != null)
		{
			for (CollapsibleSection section : sectionList)
			{
				if (isSectionEnabled(section))
				{
					section.apply(lastSnapshot);
				}
			}
		}
		sectionsColumn.revalidate();
		sectionsColumn.repaint();
	}

	/**
	 * Removes every category canvas overlay any section has added (via its
	 * "Add to canvas" menu item), so a plugin toggle or client shutdown
	 * doesn't leak stale XP-Tracker-style overlays. Delegates to each
	 * section that owns a {@link com.ospulse.ui.category.CategoryController}.
	 */
	public void removeAllCategoryOverlays()
	{
		for (CollapsibleSection section : sectionList)
		{
			section.removeAllCategoryOverlays();
		}
	}

	/**
	 * Flushes any section-owned persisted state (currently just
	 * {@link HoldingsSection}'s "since last login" Unrealized P/L snapshot,
	 * feature 7) to the RuneLite config on plugin shutdown, so the very
	 * latest value is what's read back next login even if it postdates the
	 * last {@link #onSessionUpdate} snapshot.
	 */
	public void persistState()
	{
		for (CollapsibleSection section : sectionList)
		{
			if (section instanceof HoldingsSection)
			{
				((HoldingsSection) section).shutdown();
			}
		}
	}

	@Override
	public void onSessionUpdate(SessionSnapshot snapshot)
	{
		// Called on the RuneLite client thread; marshal to the EDT before
		// touching any Swing component.
		SwingUtilities.invokeLater(() -> applySnapshot(snapshot));
	}

	private void applySnapshot(SessionSnapshot snapshot)
	{
		lastSnapshot = snapshot;
		if (snapshot == null)
		{
			showEmptyState();
			return;
		}

		showContent();
		for (CollapsibleSection section : sectionList)
		{
			// Skip hidden sections: a disabled section stays inert (e.g. Holdings
			// must not prefetch prices while switched off).
			if (isSectionEnabled(section))
			{
				section.apply(snapshot);
			}
		}
	}

	private void showEmptyState()
	{
		cardHolder.show(emptyStatePanel);
	}

	private void showContent()
	{
		cardHolder.show(contentPanel);
	}

	// --------------------------------------------------------------- helpers

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 8, 0));

		JLabel title = new JLabel("OSPulse");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		header.add(title, BorderLayout.WEST);

		JButton resetButton = new JButton("Reset");
		resetButton.setFont(FontManager.getRunescapeSmallFont());
		resetButton.addActionListener(e -> onReset());
		header.add(resetButton, BorderLayout.EAST);

		return header;
	}

	private JPanel buildEmptyState()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel label = new JLabel("<html><div style='text-align:center;'>Log in to start tracking.</div></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeFont());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		panel.add(label, BorderLayout.NORTH);

		return panel;
	}

	/**
	 * A {@link CollapsibleSection.CollapseStore} backed by a single comma-joined
	 * config value, so the set of collapsed sections survives a client restart.
	 */
	private final class ConfigCollapseStore implements CollapsibleSection.CollapseStore
	{
		private final Set<String> collapsed;

		private ConfigCollapseStore()
		{
			this.collapsed = load();
		}

		private Set<String> load()
		{
			Set<String> result = new LinkedHashSet<>();
			if (configManager == null)
			{
				return result;
			}
			String raw = configManager.getConfiguration(OSPulseConfig.GROUP, COLLAPSED_KEY);
			if (raw != null && !raw.isEmpty())
			{
				result.addAll(Arrays.stream(raw.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.collect(Collectors.toList()));
			}
			return result;
		}

		@Override
		public boolean isCollapsed(String key)
		{
			return collapsed.contains(key);
		}

		@Override
		public void setCollapsed(String key, boolean isCollapsed)
		{
			boolean changed = isCollapsed ? collapsed.add(key) : collapsed.remove(key);
			if (!changed || configManager == null)
			{
				return;
			}
			configManager.setConfiguration(OSPulseConfig.GROUP, COLLAPSED_KEY,
				String.join(",", collapsed));
		}
	}

	/**
	 * A {@link BoxLayout} column that reports it tracks the scroll viewport's
	 * width, clamping every row to the fixed side-panel width so long names
	 * ellipsize instead of being clipped past the (non-scrolling) edge.
	 */
	private static final class ScrollablePanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/**
	 * Minimal two-card switcher. {@link java.awt.CardLayout} requires a fixed
	 * container to hold both cards; this keeps that container private to the
	 * panel while exposing a simple {@code show} call.
	 */
	private static final class CardHolder
	{
		private final OSPulsePanel owner;
		private final JPanel empty;
		private final JPanel content;
		private Component current;

		private CardHolder(OSPulsePanel owner, JPanel empty, JPanel content)
		{
			this.owner = owner;
			this.empty = empty;
			this.content = content;
		}

		private void show(Component toShow)
		{
			if (current == toShow)
			{
				return;
			}
			if (current != null)
			{
				owner.remove(current);
			}
			owner.add(toShow, BorderLayout.CENTER);
			current = toShow;
			owner.revalidate();
			owner.repaint();
		}
	}
}
