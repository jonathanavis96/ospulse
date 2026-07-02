package com.ospulse.ui.sections;

import com.ospulse.model.ItemStack;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.wealth.WealthSnapshot;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.List;

/**
 * Top holdings: the most valuable items in the player's wealth. Collapsed
 * summary shows the total value of the top holdings.
 *
 * <p>NOTE: this is the current baseline list. The price-trend badges,
 * 5-at-a-time pagination and value-weighted aggregate trend are layered on top
 * of this section by a follow-up change.
 */
public final class HoldingsSection extends CollapsibleSection
{
	public static final String KEY = "holdings";

	private final ItemManager itemManager;
	private final JPanel holdingsListPanel;

	private long total;

	public HoldingsSection(CollapseStore store, ItemManager itemManager)
	{
		super(KEY, "Top holdings", store);
		this.itemManager = itemManager;

		holdingsListPanel = new JPanel();
		holdingsListPanel.setLayout(new BoxLayout(holdingsListPanel, BoxLayout.Y_AXIS));
		holdingsListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holdingsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(holdingsListPanel);
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		WealthSnapshot wealth = snapshot.getWealth();
		holdingsListPanel.removeAll();

		List<ItemStack> topHoldings = wealth == null ? List.of() : wealth.getTopHoldings();
		total = 0L;
		if (topHoldings.isEmpty())
		{
			holdingsListPanel.add(PanelWidgets.emptyRowLabel("No holdings tracked yet."));
		}
		else
		{
			for (ItemStack stack : topHoldings)
			{
				total += stack.value();
				String label = stack.getName() + " x" + String.format("%,d", stack.getQuantity());
				holdingsListPanel.add(PanelWidgets.iconRow(itemManager, stack.getId(), label,
					GpFormat.format(stack.value()), ColorScheme.LIGHT_GRAY_COLOR));
			}
		}

		holdingsListPanel.revalidate();
		holdingsListPanel.repaint();
		refreshSummary();
	}

	@Override
	protected String summaryText()
	{
		return GpFormat.format(total);
	}
}
