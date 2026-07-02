package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.PanelWidgets;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XP gained this session: a total plus a per-skill breakdown ordered by gain.
 * Collapsed summary shows total XP gained + XP/hr.
 *
 * <p>NOTE: this is the current baseline behaviour. The RuneLite xptracker-parity
 * work (per-skill XP/hr, XP left, actions left, progress bar) is layered on top
 * of this section by a follow-up change.
 */
public final class XpSection extends CollapsibleSection
{
	public static final String KEY = "xp";

	private final JLabel totalValue;
	private final JPanel breakdownPanel;

	private long xpTotal;
	private long elapsedMs;

	public XpSection(CollapseStore store)
	{
		super(KEY, "XP gained", store);
		totalValue = PanelWidgets.statRow(body(), "Total");
		breakdownPanel = new JPanel();
		breakdownPanel.setLayout(new BoxLayout(breakdownPanel, BoxLayout.Y_AXIS));
		breakdownPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		breakdownPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(breakdownPanel);
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		xpTotal = snapshot.getXpTotal();
		elapsedMs = snapshot.getElapsedMs();
		Map<String, Long> xpGained = snapshot.getXpGained();

		totalValue.setText(String.format("%,d", xpTotal));

		breakdownPanel.removeAll();
		boolean any = false;
		if (xpGained != null)
		{
			List<Map.Entry<String, Long>> entries = new ArrayList<>(xpGained.entrySet());
			entries.removeIf(e -> e.getValue() == null || e.getValue() <= 0);
			entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
			for (Map.Entry<String, Long> entry : entries)
			{
				breakdownPanel.add(PanelWidgets.listRow(entry.getKey(),
					String.format("%,d", entry.getValue())));
				any = true;
			}
		}

		if (!any)
		{
			breakdownPanel.add(PanelWidgets.emptyRowLabel("No XP gained yet."));
		}

		breakdownPanel.revalidate();
		breakdownPanel.repaint();
		refreshSummary();
	}

	private long xpPerHour()
	{
		if (elapsedMs <= 0)
		{
			return 0;
		}
		return xpTotal * 3_600_000L / elapsedMs;
	}

	@Override
	protected String summaryText()
	{
		return String.format("%,d · %,d/hr", xpTotal, xpPerHour());
	}
}
