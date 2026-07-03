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

	private final ConfigManager configManager;

	private final JPanel emptyStatePanel;
	private final JPanel contentPanel;
	private final CardHolder cardHolder;

	private final List<CollapsibleSection> sectionList = new ArrayList<>();

	private Runnable resetCallback = () -> {};

	public OSPulsePanel(OSPulseConfig config, ItemManager itemManager, ConfigManager configManager,
		PriceTrendService priceTrendService, SkillIconManager skillIconManager,
		net.runelite.client.game.SpriteManager spriteManager,
		Plugin plugin, Client client, OverlayManager overlayManager,
		GearSection.OptimizerPriceResolver optimizerPriceResolver)
	{
		super(false);
		Objects.requireNonNull(config, "config");
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		add(buildHeader(), BorderLayout.NORTH);

		emptyStatePanel = buildEmptyState();

		CollapsibleSection.CollapseStore store = new ConfigCollapseStore();

		sectionList.add(new SessionSection(store, plugin, client, overlayManager));
		sectionList.add(new LootSection(store, config, itemManager, plugin, client, overlayManager));
		sectionList.add(new XpSection(store, skillIconManager, plugin, client, overlayManager));
		sectionList.add(new GearSection(store, itemManager, skillIconManager, spriteManager, configManager,
			optimizerPriceResolver));
		sectionList.add(new GeSection(store, itemManager));
		sectionList.add(new WealthSection(store));
		sectionList.add(new HoldingsSection(store, itemManager, config, priceTrendService));

		// A width-tracking column so nothing is laid out wider than the fixed
		// side-panel width; the widest row ellipsizes within its row instead of
		// pushing content past the (horizontally non-scrolling) viewport edge.
		JPanel sections = new ScrollablePanel();
		sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
		sections.setBackground(ColorScheme.DARK_GRAY_COLOR);

		boolean first = true;
		for (CollapsibleSection section : sectionList)
		{
			if (!first)
			{
				sections.add(PanelWidgets.spacer());
			}
			first = false;
			sections.add(section);
		}

		JScrollPane scrollPane = new JScrollPane(sections);
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

	@Override
	public void onSessionUpdate(SessionSnapshot snapshot)
	{
		// Called on the RuneLite client thread; marshal to the EDT before
		// touching any Swing component.
		SwingUtilities.invokeLater(() -> applySnapshot(snapshot));
	}

	private void applySnapshot(SessionSnapshot snapshot)
	{
		if (snapshot == null)
		{
			showEmptyState();
			return;
		}

		showContent();
		for (CollapsibleSection section : sectionList)
		{
			section.apply(snapshot);
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
		resetButton.addActionListener(e -> resetCallback.run());
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
