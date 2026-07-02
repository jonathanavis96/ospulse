package com.ospulse.ui;

import com.ospulse.OSPulseConfig;
import com.ospulse.ge.GeOfferView;
import com.ospulse.model.ItemStack;
import com.ospulse.session.LootEntry;
import com.ospulse.session.SessionListener;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.wealth.WealthSnapshot;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-side view of the session tracker: a RuneLite side panel showing the
 * current session's profit, net-worth breakdown, top holdings, loot feed and
 * XP gains at a glance.
 *
 * <p>Snapshots arrive via {@link #onSessionUpdate(SessionSnapshot)} on the
 * RuneLite client thread; every update is marshalled onto the Swing EDT via
 * {@link SwingUtilities#invokeLater(Runnable)} before any component is
 * touched.
 */
public class OSPulsePanel extends PluginPanel implements SessionListener
{
	private static final int LOOT_ROW_CAP = 30;

	private final OSPulseConfig config;
	private Runnable resetCallback = () -> {};

	private final JPanel emptyStatePanel;
	private final JPanel contentPanel;
	private final CardHolder cardHolder;

	// Session summary
	private final JLabel elapsedValueLabel;
	private final JLabel profitValueLabel;
	private final JLabel profitPerHourValueLabel;
	private final JLabel netWorthDeltaValueLabel;
	private final JLabel geRealizedPnlValueLabel;

	// Net worth breakdown
	private final JLabel netWorthValueLabel;
	private final JLabel trackedValueLabel;
	private final JLabel inventoryValueLabel;
	private final JLabel equipmentValueLabel;
	private final JLabel geInFlightValueLabel;
	private final JLabel pouchValueLabel;
	private final JLabel bankValueLabel;

	// Dynamic list containers, rebuilt on every snapshot
	private final JPanel holdingsListPanel;
	private final JPanel lootListPanel;
	private final JLabel xpTotalValueLabel;
	private final JPanel xpBreakdownPanel;
	private final JPanel geListPanel;

	/**
	 * Builds the full component tree once. Subsequent updates only mutate
	 * label text/colour (or rebuild the small dynamic list panels), they
	 * never reconstruct this tree.
	 */
	public OSPulsePanel(OSPulseConfig config)
	{
		super(false);
		this.config = Objects.requireNonNull(config, "config");

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		add(buildHeader(), BorderLayout.NORTH);

		emptyStatePanel = buildEmptyState();

		JPanel sections = new JPanel();
		sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
		sections.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// --- Session summary -------------------------------------------------
		JPanel summarySection = section("Session");
		elapsedValueLabel = statRow(summarySection, "Elapsed");
		profitValueLabel = statRow(summarySection, "Profit");
		profitPerHourValueLabel = statRow(summarySection, "Profit/hr");
		netWorthDeltaValueLabel = statRow(summarySection, "Net worth Δ");
		geRealizedPnlValueLabel = statRow(summarySection, "GE flip P&L");
		sections.add(summarySection);
		sections.add(spacer());

		// --- Net worth breakdown ---------------------------------------------
		JPanel wealthSection = section("Net worth breakdown");
		netWorthValueLabel = statRow(wealthSection, "Net worth");
		trackedValueLabel = statRow(wealthSection, "Tracked");
		inventoryValueLabel = statRow(wealthSection, "Inventory");
		equipmentValueLabel = statRow(wealthSection, "Equipment");
		geInFlightValueLabel = statRow(wealthSection, "GE in-flight");
		pouchValueLabel = statRow(wealthSection, "Pouch");
		bankValueLabel = statRow(wealthSection, "Bank");
		sections.add(wealthSection);
		sections.add(spacer());

		// --- Loot feed -----------------------------------------------------------
		JPanel lootSection = section("Loot feed");
		lootListPanel = new JPanel();
		lootListPanel.setLayout(new BoxLayout(lootListPanel, BoxLayout.Y_AXIS));
		lootListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		lootSection.add(lootListPanel);
		sections.add(lootSection);
		sections.add(spacer());

		// --- XP ----------------------------------------------------------------
		JPanel xpSection = section("XP gained");
		xpTotalValueLabel = statRow(xpSection, "Total");
		xpBreakdownPanel = new JPanel();
		xpBreakdownPanel.setLayout(new BoxLayout(xpBreakdownPanel, BoxLayout.Y_AXIS));
		xpBreakdownPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		xpSection.add(xpBreakdownPanel);
		sections.add(xpSection);
		sections.add(spacer());

		// --- Grand Exchange ----------------------------------------------------
		JPanel geSection = section("Grand Exchange");
		geListPanel = new JPanel();
		geListPanel.setLayout(new BoxLayout(geListPanel, BoxLayout.Y_AXIS));
		geListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		geSection.add(geListPanel);
		sections.add(geSection);
		sections.add(spacer());

		// --- Top holdings ------------------------------------------------------
		JPanel holdingsSection = section("Top holdings");
		holdingsListPanel = new JPanel();
		holdingsListPanel.setLayout(new BoxLayout(holdingsListPanel, BoxLayout.Y_AXIS));
		holdingsListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holdingsSection.add(holdingsListPanel);
		sections.add(holdingsSection);

		JScrollPane scrollPane = new JScrollPane(sections);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

		contentPanel = new JPanel(new BorderLayout());
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		contentPanel.add(scrollPane, BorderLayout.CENTER);

		cardHolder = new CardHolder(this, emptyStatePanel, contentPanel);
		showEmptyState();
	}

	/**
	 * Wires the Reset button to {@code resetCallback}. Defaults to a no-op;
	 * the plugin assembles this to the tracker's {@code resetSession()}.
	 */
	public void setResetCallback(Runnable resetCallback)
	{
		this.resetCallback = resetCallback == null ? () -> {} : resetCallback;
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

		elapsedValueLabel.setText(formatElapsed(snapshot.getElapsedMs()));
		setSignedGpLabel(profitValueLabel, snapshot.getProfit());
		setSignedGpLabel(profitPerHourValueLabel, snapshot.getProfitPerHour());
		setSignedGpLabel(netWorthDeltaValueLabel, snapshot.getNetWorthDelta());
		setSignedGpLabel(geRealizedPnlValueLabel, snapshot.getGeRealizedPnl());

		WealthSnapshot wealth = snapshot.getWealth();
		applyWealth(wealth);
		rebuildLoot(snapshot.getLoot());
		applyXp(snapshot.getXpTotal(), snapshot.getXpGained());
		rebuildGeOffers(snapshot.getGeOffers());
		rebuildHoldings(wealth);
	}

	private void applyWealth(WealthSnapshot wealth)
	{
		if (wealth == null)
		{
			netWorthValueLabel.setText("—");
			trackedValueLabel.setText("—");
			inventoryValueLabel.setText("—");
			equipmentValueLabel.setText("—");
			geInFlightValueLabel.setText("—");
			pouchValueLabel.setText("—");
			bankValueLabel.setText("—");
			resetLabelColor(netWorthValueLabel);
			return;
		}

		setSignedGpLabel(netWorthValueLabel, wealth.netWorth());
		gpLabel(trackedValueLabel, wealth.tracked());
		gpLabel(inventoryValueLabel, wealth.getInventoryValue());
		gpLabel(equipmentValueLabel, wealth.getEquipmentValue());
		gpLabel(geInFlightValueLabel, wealth.getGeInFlightValue());
		gpLabel(pouchValueLabel, wealth.getPouchValue());

		if (wealth.isBankKnown())
		{
			gpLabel(bankValueLabel, wealth.getBankValue());
		}
		else
		{
			bankValueLabel.setText("— (unknown)");
			resetLabelColor(bankValueLabel);
		}
	}

	private void rebuildHoldings(WealthSnapshot wealth)
	{
		holdingsListPanel.removeAll();

		List<ItemStack> topHoldings = wealth == null ? List.of() : wealth.getTopHoldings();
		if (topHoldings.isEmpty())
		{
			holdingsListPanel.add(emptyRowLabel("No holdings tracked yet."));
		}
		else
		{
			for (ItemStack stack : topHoldings)
			{
				String label = stack.getName() + " x" + String.format("%,d", stack.getQuantity());
				holdingsListPanel.add(listRow(label, GpFormat.format(stack.value())));
			}
		}

		holdingsListPanel.revalidate();
		holdingsListPanel.repaint();
	}

	private void rebuildGeOffers(List<GeOfferView> offers)
	{
		geListPanel.removeAll();

		if (offers == null || offers.isEmpty())
		{
			geListPanel.add(emptyRowLabel("No active offers."));
		}
		else
		{
			boolean first = true;
			for (GeOfferView offer : offers)
			{
				if (!first)
				{
					geListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
				}
				first = false;

				String arrow = offer.isBuying() ? "▲" : "▼"; // ▲ buy / ▼ sell
				String header = arrow + " " + offer.getItemName();
				String qty = String.format("%,d / %,d",
					offer.getQuantityTransacted(), offer.getTotalQuantity());
				JPanel headerRow = listRow(header, qty);
				tintLeft(headerRow, offer.isBuying()
					? ColorScheme.PROGRESS_COMPLETE_COLOR
					: ColorScheme.PROGRESS_ERROR_COLOR);
				geListPanel.add(headerRow);

				String gpLabelText = offer.isBuying() ? "spent" : "received";
				String gp = GpFormat.format(offer.getGpProgress())
					+ " / " + GpFormat.format(offer.getGpPotential());
				geListPanel.add(listRow("   " + gpLabelText, gp));
			}
		}

		geListPanel.revalidate();
		geListPanel.repaint();
	}

	private void rebuildLoot(List<LootEntry> loot)
	{
		lootListPanel.removeAll();

		int minLootValue = config.minLootValue();
		int shown = 0;
		if (loot != null)
		{
			for (LootEntry entry : loot)
			{
				if (entry.getValue() < minLootValue)
				{
					continue;
				}
				String label = entry.getName() + " x" + String.format("%,d", entry.getQuantity());
				lootListPanel.add(listRow(label, GpFormat.format(entry.getValue())));
				shown++;
				if (shown >= LOOT_ROW_CAP)
				{
					break;
				}
			}
		}

		if (shown == 0)
		{
			lootListPanel.add(emptyRowLabel("No loot yet."));
		}

		lootListPanel.revalidate();
		lootListPanel.repaint();
	}

	private void applyXp(long xpTotal, Map<String, Long> xpGained)
	{
		xpTotalValueLabel.setText(String.format("%,d", xpTotal));

		xpBreakdownPanel.removeAll();
		boolean any = false;
		if (xpGained != null)
		{
			List<Map.Entry<String, Long>> entries = new ArrayList<>(xpGained.entrySet());
			entries.removeIf(e -> e.getValue() == null || e.getValue() <= 0);
			entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
			for (Map.Entry<String, Long> entry : entries)
			{
				xpBreakdownPanel.add(listRow(entry.getKey(), String.format("%,d", entry.getValue())));
				any = true;
			}
		}

		if (!any)
		{
			xpBreakdownPanel.add(emptyRowLabel("No XP gained yet."));
		}

		xpBreakdownPanel.revalidate();
		xpBreakdownPanel.repaint();
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

	private static JPanel section(String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.darker()),
			new EmptyBorder(6, 8, 6, 8)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel(title);
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		panel.add(header);

		return panel;
	}

	private static JLabel statRow(JPanel container, String labelText)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());

		JLabel value = new JLabel("-");
		value.setForeground(Color.WHITE);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(label, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		container.add(row);
		return value;
	}

	private static JPanel listRow(String left, String right)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel leftLabel = new JLabel(left);
		leftLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		leftLabel.setFont(FontManager.getRunescapeSmallFont());

		JLabel rightLabel = new JLabel(right);
		rightLabel.setForeground(Color.WHITE);
		rightLabel.setFont(FontManager.getRunescapeSmallFont());
		rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(leftLabel, BorderLayout.WEST);
		row.add(rightLabel, BorderLayout.EAST);
		return row;
	}

	/** Recolours the left-hand (WEST) label of a {@link #listRow} panel. */
	private static void tintLeft(JPanel row, Color color)
	{
		Component west = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.WEST);
		if (west instanceof JLabel)
		{
			((JLabel) west).setForeground(color);
		}
	}

	private static JLabel emptyRowLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static Component spacer()
	{
		return Box.createRigidArea(new Dimension(0, 6));
	}

	private void setSignedGpLabel(JLabel label, long value)
	{
		label.setText(GpFormat.format(value));
		label.setForeground(value >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
	}

	private void gpLabel(JLabel label, long value)
	{
		label.setText(GpFormat.format(value));
		resetLabelColor(label);
	}

	private static void resetLabelColor(JLabel label)
	{
		label.setForeground(Color.WHITE);
	}

	private static String formatElapsed(long elapsedMs)
	{
		long totalSeconds = Math.max(0, elapsedMs) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return String.format("%d:%02d:%02d", hours, minutes, seconds);
	}

	/**
	 * Minimal two-card switcher. {@link java.awt.CardLayout} requires a
	 * fixed container to hold both cards; this keeps that container private
	 * to the panel while exposing a simple {@code show} call.
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
