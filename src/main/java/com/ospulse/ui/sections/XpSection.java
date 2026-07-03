package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.ui.ThinProgressBar;
import com.ospulse.xp.VirtualLevelTable;
import com.ospulse.xp.XpSkillView;

import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.SkillColor;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * XP gained this session, in this plugin's own compact card style rather than
 * a copy of RuneLite's built-in XP Tracker widget: an "Overall" summary row,
 * then per trained skill a small icon-row/progress-bar/detail-row stack built
 * from the same {@link PanelWidgets} + {@link ThinProgressBar} pieces the
 * Grand Exchange section uses, so the whole panel reads as one design.
 *
 * <p>Levels past 99 keep climbing as RuneLite's virtual levels (see {@link
 * VirtualLevelTable}), up to the 126 cap, instead of flatlining the progress
 * bar and "next level" target at 99.
 *
 * <p>Falls back to the plain gained-per-skill list when the snapshot carries
 * no {@link XpSkillView}s (e.g. one produced via the older constructors).
 */
public final class XpSection extends CollapsibleSection
{
	public static final String KEY = "xp";

	private static final int ICON_SIZE = 22;

	private final JPanel breakdownPanel;
	private final SkillIconManager skillIconManager;

	private long xpTotal;
	private long elapsedMs;

	public XpSection(CollapseStore store, SkillIconManager skillIconManager)
	{
		super(KEY, "XP gained", store);
		this.skillIconManager = skillIconManager;
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

	/** "Overall" summary row + one compact card per skill. */
	private void renderSkillViews(List<XpSkillView> skills, long overallXpPerHour)
	{
		breakdownPanel.add(PanelWidgets.listRow("Overall XP",
			GpFormat.format(xpTotal) + " · " + GpFormat.format(overallXpPerHour) + " xp/hr"));
		breakdownPanel.add(PanelWidgets.spacer());

		boolean first = true;
		for (XpSkillView skill : skills)
		{
			if (!first)
			{
				breakdownPanel.add(PanelWidgets.spacer());
			}
			first = false;
			breakdownPanel.add(skillCard(skill));
		}
	}

	/**
	 * One skill's own compact card: an icon + name/level header, a labelled 2x2
	 * stat grid (XP Gained / XP/hr / XP left / Actions), then a skill-coloured
	 * {@link ThinProgressBar} annotated below with current level · % complete ·
	 * next level. The four stats are labelled inline (rather than bare numbers)
	 * so each figure is self-describing; the 2x2 grid keeps them on-screen at
	 * the fixed ~215px side-panel width where a single labelled row would clip.
	 */
	private JPanel skillCard(XpSkillView view)
	{
		Skill skill = parseSkill(view.getSkillName());
		Color accent = skill != null ? SkillColor.find(skill).getColor() : ColorScheme.BRAND_ORANGE;
		boolean maxed = view.getCurrentLevel() >= VirtualLevelTable.MAX_LEVEL;

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		String name = prettySkillName(view.getSkillName());
		card.add(PanelWidgets.iconRow(skillIcon(skill), name, "Lvl " + view.getCurrentLevel(), accent));

		card.add(rigidSpacer(3));
		card.add(statGrid(view, maxed));
		card.add(rigidSpacer(3));

		ThinProgressBar bar = new ThinProgressBar();
		bar.setForeground(accent);
		bar.setProgress(view.getProgressToNextLevel());
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(bar);
		card.add(rigidSpacer(1));
		card.add(barAnnotation(view, maxed));

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** Labelled 2x2 grid: XP Gained / XP/hr on the first row, XP left / Actions on the second. */
	private static JPanel statGrid(XpSkillView view, boolean maxed)
	{
		JPanel grid = new JPanel(new GridLayout(2, 2, 8, 1));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		String xpLeft = maxed ? "0" : GpFormat.format(view.getXpLeft());
		String actions = maxed ? "Maxed"
			: view.getActionsLeft() < 0 ? "?" : String.format(Locale.ROOT, "%,d", view.getActionsLeft());

		grid.add(statCell("XP Gained", GpFormat.format(view.getGained())));
		grid.add(statCell("XP/hr", GpFormat.format(view.getXpPerHour())));
		grid.add(statCell("XP left", xpLeft));
		grid.add(statCell("Actions", actions));

		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		return grid;
	}

	/** A "Label value" cell: grey label pinned left, white value pinned right. */
	private static JPanel statCell(String label, String value)
	{
		JPanel cell = new JPanel(new BorderLayout(4, 0));
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel labelLabel = new JLabel(label);
		labelLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		labelLabel.setFont(FontManager.getRunescapeSmallFont());

		JLabel valueLabel = new JLabel(value);
		valueLabel.setForeground(Color.WHITE);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		cell.add(labelLabel, BorderLayout.WEST);
		cell.add(valueLabel, BorderLayout.CENTER);
		return cell;
	}

	/** Under-bar annotation row: current level (left) · % complete (centre) · next level or "Max" (right). */
	private static JPanel barAnnotation(XpSkillView view, boolean maxed)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel current = miniLabel(String.valueOf(view.getCurrentLevel()), SwingConstants.LEFT);
		JLabel percent = miniLabel(Math.round(view.getProgressToNextLevel() * 100.0) + "%", SwingConstants.CENTER);
		JLabel next = miniLabel(maxed ? "Max" : String.valueOf(view.getCurrentLevel() + 1), SwingConstants.RIGHT);

		row.add(current, BorderLayout.WEST);
		row.add(percent, BorderLayout.CENTER);
		row.add(next, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static JLabel miniLabel(String text, int alignment)
	{
		JLabel label = new JLabel(text, alignment);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static Component rigidSpacer(int height)
	{
		return Box.createRigidArea(new Dimension(0, height));
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
					GpFormat.format(entry.getValue())));
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
	 * A small ({@value #ICON_SIZE}px) skill icon for a skill, or {@code null}
	 * if unavailable — the row then renders without an icon rather than
	 * failing. Deliberately smaller than RuneLite's 35x35 XpInfoBox icon to
	 * keep this section's rows compact.
	 */
	private Image skillIcon(Skill skill)
	{
		if (skillIconManager == null || skill == null)
		{
			return null;
		}
		try
		{
			Image full = skillIconManager.getSkillImage(skill, false);
			return full == null ? null : full.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
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
		return GpFormat.format(xpTotal) + " · " + GpFormat.format(xpPerHour()) + " xp/hr";
	}
}
