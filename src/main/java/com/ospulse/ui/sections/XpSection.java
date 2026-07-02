package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.xp.XpSkillView;

import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.SkillColor;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * XP gained this session, rendered at visual parity with RuneLite's built-in
 * XP Tracker: an Overall row (total gained + overall XP/hr) followed by, per
 * trained skill, an {@code XpInfoBox}-style block — a 35x35 skill icon beside a
 * 2x2 grid of stats (XP, XP/hr, XP left, actions left), above a skill-coloured
 * progress bar through the current level labelled {@code Lvl. N} / percent /
 * {@code Lvl. N+1}. The skill is identified by its icon alone (no text name),
 * exactly like the built-in tracker.
 *
 * <p>Falls back to the plain gained-per-skill list when the snapshot carries
 * no {@link XpSkillView}s (e.g. one produced via the older constructors).
 */
public final class XpSection extends CollapsibleSection
{
	public static final String KEY = "xp";

	/** The XP Tracker's progress-bar trough colour (dark brown). */
	private static final Color PROGRESS_BAR_BG = new Color(61, 56, 49);

	private final JLabel totalValue;
	private final JPanel breakdownPanel;
	private final SkillIconManager skillIconManager;

	private long xpTotal;
	private long elapsedMs;

	public XpSection(CollapseStore store, SkillIconManager skillIconManager)
	{
		super(KEY, "XP gained", store);
		this.skillIconManager = skillIconManager;
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

	/** The xptracker-parity rendering: Overall row + per-skill {@link #skillBox} blocks. */
	private void renderSkillViews(List<XpSkillView> skills, long overallXpPerHour)
	{
		breakdownPanel.add(PanelWidgets.listRow("Overall",
			String.format("%,d · %,d/hr", xpTotal, overallXpPerHour)));

		for (XpSkillView skill : skills)
		{
			breakdownPanel.add(skillBox(skill));
		}
	}

	/**
	 * One XpInfoBox-parity block for a single skill: header (icon + 2x2 stats)
	 * over a skill-coloured progress bar. Mirrors RuneLite's {@code XpInfoBox}.
	 */
	private JPanel skillBox(XpSkillView view)
	{
		Skill skill = parseSkill(view.getSkillName());

		JPanel container = new JPanel(new BorderLayout());
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		container.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		container.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Header: 35x35 skill icon (WEST) + 2x2 stat grid (CENTER).
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		Image icon = skillIcon(skill);
		if (icon != null)
		{
			JLabel iconLabel = new JLabel(new ImageIcon(icon));
			iconLabel.setPreferredSize(new Dimension(35, 35));
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			iconLabel.setVerticalAlignment(SwingConstants.CENTER);
			header.add(iconLabel, BorderLayout.WEST);
		}

		JPanel stats = new JPanel(new DynamicGridLayout(2, 2));
		stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stats.setBorder(BorderFactory.createEmptyBorder(9, 2, 9, 2));
		String actions = view.getActionsLeft() < 0
			? "?"
			: QuantityFormatter.quantityToRSDecimalStack((int) Math.min(view.getActionsLeft(), Integer.MAX_VALUE), true);
		stats.add(statLabel("XP: ", rsStack(view.getGained())));       // top-left
		stats.add(statLabel("XP/hr: ", rsStack(view.getXpPerHour()))); // top-right
		stats.add(statLabel("Left: ", rsStack(view.getXpLeft())));     // bottom-left
		stats.add(statLabel("Actions: ", actions));                    // bottom-right
		header.add(stats, BorderLayout.CENTER);

		// Progress bar: skill-coloured, Lvl. N / percent / Lvl. N+1.
		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(100);
		bar.setValue((int) Math.round(clamp01(view.getProgressToNextLevel()) * 100));
		bar.setBackground(PROGRESS_BAR_BG);
		bar.setForeground(skill != null ? SkillColor.find(skill).getColor() : ColorScheme.BRAND_ORANGE);
		bar.setLeftLabel("Lvl. " + view.getCurrentLevel());
		bar.setRightLabel(view.getCurrentLevel() >= 99 ? "99" : "Lvl. " + (view.getCurrentLevel() + 1));
		bar.setCenterLabel((int) Math.round(clamp01(view.getProgressToNextLevel()) * 100) + "%");

		JPanel progressWrapper = new JPanel(new BorderLayout());
		progressWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressWrapper.setBorder(BorderFactory.createEmptyBorder(0, 7, 7, 7));
		progressWrapper.add(bar, BorderLayout.CENTER);

		container.add(header, BorderLayout.NORTH);
		container.add(progressWrapper, BorderLayout.SOUTH);

		// Fill the panel width in the Y_AXIS BoxLayout while keeping its height.
		container.setMaximumSize(new Dimension(Integer.MAX_VALUE, container.getPreferredSize().height));
		return container;
	}

	private static JLabel statLabel(String key, String value)
	{
		JLabel label = new JLabel(String.format(
			"<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>",
			ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, value));
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static String rsStack(long value)
	{
		return QuantityFormatter.quantityToRSDecimalStack((int) Math.min(value, Integer.MAX_VALUE), true);
	}

	private static double clamp01(double v)
	{
		if (v < 0)
		{
			return 0;
		}
		return Math.min(v, 1.0);
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

	/** The RuneLite Skill enum for a skill name (e.g. "WOODCUTTING"), or null. */
	private static Skill parseSkill(String name)
	{
		if (name == null || name.isEmpty())
		{
			return null;
		}
		try
		{
			return Skill.valueOf(name.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/**
	 * The large (35x35) RuneLite skill icon for a skill, or {@code null} if
	 * unavailable — the header then renders without an icon rather than failing.
	 */
	private Image skillIcon(Skill skill)
	{
		if (skillIconManager == null || skill == null)
		{
			return null;
		}
		try
		{
			return skillIconManager.getSkillImage(skill, false);
		}
		catch (RuntimeException e)
		{
			return null;
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
