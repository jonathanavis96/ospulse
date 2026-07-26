package com.ospulse.ui.sections.gear;

import com.ospulse.combat.MonsterConsumablesReminder;
import com.ospulse.combat.MonsterConsumablesRepository;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The "don't forget" consumables/gear reminder row(s) below the
 * potion/prayer/slayer toggles in the Gear section — a single curated
 * advisory line naming what a target demands (venom, dragonfire, etc.) that
 * the DPS optimiser cannot infer, sourced from {@link
 * MonsterConsumablesRepository}. Renders through the same {@link
 * AdvisoryNoteRenderer#wrappingNote} every other Gear-section advisory note
 * uses, so it wraps and reads identically to the mechanic-override and
 * combat-requirement notes it sits beside.
 *
 * <p>Deliberately free of any {@code GearSection} state: {@link #refresh}
 * takes only a monster name (or {@code null}), so this class is
 * independently constructible and testable without a {@code GearSection}
 * instance. It is a pure keyed-lookup render step, never touching {@code
 * GearOptimizer} — picking a target with no curated entry costs one map
 * lookup and renders nothing, not an empty placeholder row.
 *
 * <p><b>The caller must pass {@code Monster.lookupName()}, not {@code
 * Monster.name()}.</b> A synthetic Wilderness-variant target (see {@code
 * WildernessVariantMonsterRepository}) has a decorated DISPLAY name (e.g.
 * "Black dragon (Wilderness)") that this repository's curated data was
 * never authored against — the reminder must resolve against the real
 * underlying monster's identity so a Wilderness Black dragon still gets the
 * same dragonfire-shield note the ordinary Black dragon does.
 */
public final class ConsumablesReminderPanel extends JPanel
{
	public ConsumablesReminderPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setVisible(false);
	}

	/**
	 * Rebuilds this panel for the given target's LOOKUP name (see the class
	 * javadoc — {@code Monster.lookupName()}, not {@code Monster.name()}).
	 * {@code null} (no target selected) and a name with no curated reminder
	 * both leave the panel empty and invisible — no empty row, no placeholder.
	 *
	 * <p>Named {@code refresh}, not {@code update}, to avoid an overload
	 * clash/shadow with {@code JComponent.update(Graphics)}.
	 */
	public void refresh(String monsterName)
	{
		removeAll();
		boolean show = false;
		if (monsterName != null)
		{
			Optional<MonsterConsumablesReminder> reminder =
				MonsterConsumablesRepository.getInstance().forMonster(monsterName);
			if (reminder.isPresent() && !reminder.get().note().isEmpty())
			{
				add(AdvisoryNoteRenderer.wrappingNote(reminder.get().note(), ColorScheme.BRAND_ORANGE));
				show = true;
			}
		}
		setVisible(show);
		revalidate();
		repaint();
	}

	/** Test seam: the rendered text of every current advisory line, in order. */
	public List<String> noteTextsForTest()
	{
		List<String> texts = new ArrayList<>();
		for (Component c : getComponents())
		{
			if (c instanceof JTextArea)
			{
				texts.add(((JTextArea) c).getText());
			}
		}
		return texts;
	}
}
