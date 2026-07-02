package com.ospulse.ui.sections;

import com.ospulse.ge.GeOfferView;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.ui.ThinProgressBar;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

/**
 * Grand Exchange: the current active buy/sell offers. Collapsed summary shows
 * total received / total potential across sell offers (e.g. "0 / 50M sold").
 *
 * <p>Each active offer is rendered like RuneLite's own grandexchange side
 * panel offer card: item icon, name, buy/sell direction, "transacted / total"
 * quantity, a thin progress bar, and a spent/received gp line.
 */
public final class GeSection extends CollapsibleSection
{
	public static final String KEY = "ge";

	private final ItemManager itemManager;
	private final JPanel geListPanel;

	private long sellReceived;
	private long sellPotential;

	public GeSection(CollapseStore store, ItemManager itemManager)
	{
		super(KEY, "Grand Exchange", store);
		this.itemManager = itemManager;

		geListPanel = new JPanel();
		geListPanel.setLayout(new BoxLayout(geListPanel, BoxLayout.Y_AXIS));
		geListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		geListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(geListPanel);
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		List<GeOfferView> offers = snapshot.getGeOffers();
		geListPanel.removeAll();

		sellReceived = 0L;
		sellPotential = 0L;

		if (offers == null || offers.isEmpty())
		{
			geListPanel.add(PanelWidgets.emptyRowLabel("No active offers."));
		}
		else
		{
			boolean first = true;
			for (GeOfferView offer : offers)
			{
				if (!offer.isBuying())
				{
					sellReceived += offer.getGpProgress();
					sellPotential += offer.getGpPotential();
				}

				if (!first)
				{
					geListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
				}
				first = false;

				String arrow = offer.isBuying() ? "▲" : "▼"; // ▲ buy / ▼ sell
				String header = arrow + " " + offer.getItemName();
				String qty = String.format("%,d / %,d",
					offer.getQuantityTransacted(), offer.getTotalQuantity());
				Color dirColor = offer.isBuying()
					? ColorScheme.PROGRESS_COMPLETE_COLOR
					: ColorScheme.PROGRESS_ERROR_COLOR;
				geListPanel.add(PanelWidgets.iconRow(itemManager, offer.getItemId(), header, qty, dirColor));

				double progress = offer.getTotalQuantity() == 0L
					? 0.0
					: offer.getQuantityTransacted() / (double) offer.getTotalQuantity();
				ThinProgressBar progressBar = new ThinProgressBar();
				progressBar.setForeground(dirColor);
				progressBar.setProgress(progress);
				progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
				geListPanel.add(Box.createRigidArea(new Dimension(0, 2)));
				geListPanel.add(progressBar);
				geListPanel.add(Box.createRigidArea(new Dimension(0, 2)));

				String gpLabelText = offer.isBuying() ? "spent" : "received";
				String gp = GpFormat.format(offer.getGpProgress())
					+ " / " + GpFormat.format(offer.getGpPotential());
				geListPanel.add(PanelWidgets.listRow("   " + gpLabelText, gp));
			}
		}

		geListPanel.revalidate();
		geListPanel.repaint();
		refreshSummary();
	}

	@Override
	protected String summaryText()
	{
		return GpFormat.format(sellReceived) + " / " + GpFormat.format(sellPotential) + " sold";
	}
}
