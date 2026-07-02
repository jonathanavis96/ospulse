package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.ui.ThinProgressBar;
import com.ospulse.xp.XpSkillView;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * XP gained this session, at parity with RuneLite's XP Tracker: an Overall
 * row (total gained + overall XP/hr) plus, per trained skill, the XP gained
 * and rate, the XP and actions left to the next level, and a thin progress
 * bar through the current level. Collapsed summary shows total XP gained +
 * overall XP/hr.
 *
 * <p>Falls back to the plain gained-per-skill list when the snapshot carries
 * no {@link XpSkillView}s (e.g. one produced via the older constructors).
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

		totalValue.setText(String.format("%,d", xpTotal));

		breakdownPanel.removeAll();
		List<XpSkillView> skills = snapshot.getXpSkills();
		if (skills != null && !skills.isEmpty())
		{
			renderSkillViews(skills, snapshot.getXpPerHour());
		}
		else
		{
			renderGainedFallback(snapshot.getXpGained());
		}

		breakdownPanel.revalidate();
		breakdownPanel.repaint();
		refreshSummary();
	}

	/** The xptracker-parity rendering: Overall row + per-skill detail blocks. */
	private void renderSkillViews(List<XpSkillView> skills, long overallXpPerHour)
	{
		breakdownPanel.add(PanelWidgets.listRow("Overall",
			String.format("%,d · %,d/hr", xpTotal, overallXpPerHour)));

		for (XpSkillView skill : skills)
		{
			breakdownPanel.add(PanelWidgets.spacer());
			breakdownPanel.add(PanelWidgets.listRow(prettySkillName(skill.getSkillName()),
				String.format("%,d · %,d/hr", skill.getGained(), skill.getXpPerHour())));

			String actionsLeft = skill.getActionsLeft() < 0
				? "?"
				: String.format("%,d", skill.getActionsLeft());
			breakdownPanel.add(PanelWidgets.emptyRowLabel(String.format(
				"xp left: %,d · actions left: %s", skill.getXpLeft(), actionsLeft)));

			ThinProgressBar bar = new ThinProgressBar();
			bar.setForeground(ColorScheme.BRAND_ORANGE);
			bar.setAlignmentX(Component.LEFT_ALIGNMENT);
			bar.setProgress(skill.getProgressToNextLevel());
			breakdownPanel.add(bar);
		}
	}

	/** The pre-parity rendering: gained per skill, ordered by gain descending. */
	private void renderGainedFallback(Map<String, Long> xpGained)
	{
		boolean any = false;
		if (xpGained != null)
		{
			List<Map.Entry<String, Long>> entries = new ArrayList<>(xpGained.entrySet());
			entries.removeIf(e -> e.getValue() == null || e.getValue() <= 0);
			entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
			for (Map.Entry<String, Long> entry : entries)
			{
				breakdownPanel.add(PanelWidgets.listRow(prettySkillName(entry.getKey()),
					String.format("%,d", entry.getValue())));
				any = true;
			}
		}

		if (!any)
		{
			breakdownPanel.add(PanelWidgets.emptyRowLabel("No XP gained yet."));
		}
	}

	/** "WOODCUTTING" -&gt; "Woodcutting" (skill enum names are single words). */
	private static String prettySkillName(String name)
	{
		if (name == null || name.isEmpty())
		{
			return "";
		}
		String lower = name.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
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
