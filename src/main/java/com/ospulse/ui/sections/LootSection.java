package com.ospulse.ui.sections;

import com.ospulse.OSPulseConfig;
import com.ospulse.model.ItemStack;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.session.SourceLoot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.JLabel;
import javax.swing.JPanel;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loot feed: a "Loot Tracker"-style list grouped by source (boss/NPC/activity),
 * each source header itself collapsible. Collapsed section summary shows total
 * loot value.
 */
public final class LootSection extends CollapsibleSection
{
	public static final String KEY = "loot";
	private static final int LOOT_ROW_CAP = 30;
	private static final int ICON_GRID_COLUMNS = 5;

	private final OSPulseConfig config;
	private final ItemManager itemManager;

	private final JLabel totalValue;
	private final JPanel lootListPanel;

	/** Latest source-grouped loot, retained so header clicks can re-render. */
	private List<SourceLoot> lastLootSources = List.of();
	/** Sources the user has collapsed (by name); persists across updates. */
	private final Set<String> collapsedSources = new HashSet<>();

	private long total;

	public LootSection(CollapseStore store, OSPulseConfig config, ItemManager itemManager)
	{
		super(KEY, "Loot feed", store);
		this.config = config;
		this.itemManager = itemManager;

		totalValue = PanelWidgets.statRow(body(), "Total value");
		lootListPanel = new JPanel();
		lootListPanel.setLayout(new javax.swing.BoxLayout(lootListPanel, javax.swing.BoxLayout.Y_AXIS));
		lootListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		lootListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(lootListPanel);
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		rebuild(snapshot.getLootSources());
	}

	private void rebuild(List<SourceLoot> sources)
	{
		lastLootSources = sources == null ? List.of() : sources;
		lootListPanel.removeAll();

		total = 0L;
		for (SourceLoot src : lastLootSources)
		{
			total += src.getTotalValue();
		}
		totalValue.setText(GpFormat.format(total));

		if (lastLootSources.isEmpty())
		{
			lootListPanel.add(PanelWidgets.emptyRowLabel("No loot yet."));
		}
		else
		{
			int minLootValue = config.minLootValue();
			for (SourceLoot src : lastLootSources)
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

		return icon;
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
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!collapsedSources.remove(source))
				{
					collapsedSources.add(source);
				}
				rebuild(lastLootSources);
			}
		});
		return row;
	}

	@Override
	protected String summaryText()
	{
		return GpFormat.format(total);
	}
}
