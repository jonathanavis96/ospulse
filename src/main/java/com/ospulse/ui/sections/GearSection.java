package com.ospulse.ui.sections;

import com.ospulse.OSPulseConfig;
import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.Stance;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.PanelWidgets;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ButtonGroup;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gear / DPS calculator (Phase 1 — live readout): reads the player's current
 * worn gear + boosted levels + active prayers (via {@link GearSnapshot}, fed
 * live by {@code SessionTracker} on the client thread) and computes max hit /
 * accuracy / DPS against a selected {@link Monster} using the
 * {@code com.ospulse.combat} engine, for a user-toggleable {@link CombatStyle}.
 *
 * <p>Deferred to a later phase (see the design spec): live opponent
 * auto-detection and favourites — target selection here is a simple
 * searchable picker over {@link MonsterRepository}, defaulting to the first
 * entry in the bundled data.
 *
 * <p><b>Threading:</b> this is an EDT Swing component. It never reads the
 * RuneLite {@code Client} directly and never calls any
 * {@code getBoostedSkillLevel}/{@code isPrayerActive}/{@code getItemContainer}
 * -style API — all of that happens in {@code SessionTracker} on the client
 * thread and is published as {@link GearSnapshot}. The one exception is
 * {@link ItemManager#getItemStats(int)}, which is thread-safe/cached and may
 * be called here to turn snapshot item ids into {@link EquipmentStats} and to
 * fetch item icons.
 */
public final class GearSection extends CollapsibleSection
{
	public static final String KEY = "gear";

	/** {@code EquipmentInventorySlot.WEAPON.ordinal()} — the slot whose aspeed/isTwoHanded drives the loadout. */
	private static final int WEAPON_SLOT_INDEX = EquipmentInventorySlot.WEAPON.ordinal();

	/**
	 * TODO Tier-A/magic simplification: Phase 1 has no spellbook UI, so magic
	 * DPS uses a placeholder base spell max hit (roughly a mid-tier
	 * standard-spellbook bolt spell) rather than the player's actually-selected
	 * spell. Revisit once a spell picker exists.
	 */
	private static final int DEFAULT_MAGIC_BASE_MAX_HIT = 20;

	private final ItemManager itemManager;
	private final OSPulseConfig config;

	private final JTextField monsterSearchField;
	private final JComboBox<String> monsterCombo;
	private final JCheckBox bestPotionCheckbox;
	private final JCheckBox bestPrayerCheckbox;
	private final JCheckBox onSlayerTaskCheckbox;
	private final JLabel maxHitValue;
	private final JLabel accuracyValue;
	private final JLabel dpsValue;
	private final JLabel baseEstimateNote;
	private final JLabel disabledNote;

	private List<Monster> comboMonsters = new ArrayList<>();
	private Monster selectedMonster;
	private CombatStyle selectedStyle = CombatStyle.STAB;
	private GearSnapshot lastGear;
	private double lastDps;
	private boolean suppressComboEvents;

	public GearSection(CollapseStore store, ItemManager itemManager, OSPulseConfig config)
	{
		super(KEY, "Gear", store);
		this.itemManager = itemManager;
		this.config = config;

		disabledNote = PanelWidgets.emptyRowLabel(
			"Disabled - enable \"Gear / DPS calculator\" in the plugin config to use this section.");
		body().add(disabledNote);

		body().add(styleToggleRow());
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		monsterSearchField = new JTextField();
		monsterSearchField.setFont(FontManager.getRunescapeSmallFont());
		monsterSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		monsterSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, monsterSearchField.getPreferredSize().height));
		monsterSearchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}
		});
		body().add(monsterSearchField);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		monsterCombo = new JComboBox<>();
		monsterCombo.setFont(FontManager.getRunescapeSmallFont());
		monsterCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
		monsterCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, monsterCombo.getPreferredSize().height));
		monsterCombo.addActionListener(e -> onMonsterSelected());
		body().add(monsterCombo);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		JPanel togglesRow = new JPanel();
		togglesRow.setLayout(new BoxLayout(togglesRow, BoxLayout.Y_AXIS));
		togglesRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		togglesRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		bestPotionCheckbox = new JCheckBox("Assume best potion");
		styleCheckbox(bestPotionCheckbox);
		bestPotionCheckbox.addActionListener(e -> recompute());
		togglesRow.add(bestPotionCheckbox);

		bestPrayerCheckbox = new JCheckBox("Assume best offensive prayer");
		styleCheckbox(bestPrayerCheckbox);
		bestPrayerCheckbox.addActionListener(e -> recompute());
		togglesRow.add(bestPrayerCheckbox);

		// Manual toggle (v1): Phase 1 has no live client read for on-task status
		// (see GearSnapshot's javadoc) — default OFF, same as the other simulation
		// toggles above.
		onSlayerTaskCheckbox = new JCheckBox("On Slayer task");
		styleCheckbox(onSlayerTaskCheckbox);
		onSlayerTaskCheckbox.addActionListener(e -> recompute());
		togglesRow.add(onSlayerTaskCheckbox);

		body().add(togglesRow);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		maxHitValue = PanelWidgets.statRow(body(), "Max hit");
		accuracyValue = PanelWidgets.statRow(body(), "Accuracy");
		dpsValue = PanelWidgets.statRow(body(), "DPS");

		baseEstimateNote = PanelWidgets.emptyRowLabel("~ base estimate (some effects not yet modelled)");
		baseEstimateNote.setVisible(false);
		body().add(baseEstimateNote);

		// Sensible default target: first entry in the bundled monster data
		// (auto-detect-opponent and favourites are deferred to a later phase).
		populateMonsterCombo("");
	}

	private static void styleCheckbox(JCheckBox checkbox)
	{
		checkbox.setFont(FontManager.getRunescapeSmallFont());
		checkbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		checkbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		checkbox.setFocusPainted(false);
	}

	/** Five exclusive toggle buttons, one per {@link CombatStyle}. */
	private JPanel styleToggleRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		ButtonGroup group = new ButtonGroup();
		CombatStyle[] styles = { CombatStyle.STAB, CombatStyle.SLASH, CombatStyle.CRUSH, CombatStyle.RANGED, CombatStyle.MAGIC };
		String[] labels = { "Stab", "Slash", "Crush", "Range", "Magic" };
		for (int i = 0; i < styles.length; i++)
		{
			CombatStyle style = styles[i];
			JToggleButton button = new JToggleButton(labels[i]);
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setFocusPainted(false);
			button.setMargin(new java.awt.Insets(1, 4, 1, 4));
			button.setSelected(style == selectedStyle);
			button.addActionListener(e ->
			{
				selectedStyle = style;
				recompute();
			});
			group.add(button);
			row.add(button);
		}
		return row;
	}

	private void onSearchChanged()
	{
		populateMonsterCombo(monsterSearchField.getText());
	}

	private void populateMonsterCombo(String query)
	{
		comboMonsters = MonsterRepository.getInstance().search(query == null ? "" : query.trim());
		suppressComboEvents = true;
		monsterCombo.removeAllItems();
		for (Monster m : comboMonsters)
		{
			monsterCombo.addItem(m.name());
		}
		if (!comboMonsters.isEmpty())
		{
			// Keep the previous selection if it's still in the filtered list.
			int keepIndex = 0;
			if (selectedMonster != null)
			{
				for (int i = 0; i < comboMonsters.size(); i++)
				{
					if (comboMonsters.get(i).name().equalsIgnoreCase(selectedMonster.name()))
					{
						keepIndex = i;
						break;
					}
				}
			}
			monsterCombo.setSelectedIndex(keepIndex);
			selectedMonster = comboMonsters.get(keepIndex);
		}
		suppressComboEvents = false;
		recompute();
	}

	private void onMonsterSelected()
	{
		if (suppressComboEvents)
		{
			return;
		}
		int index = monsterCombo.getSelectedIndex();
		if (index >= 0 && index < comboMonsters.size())
		{
			selectedMonster = comboMonsters.get(index);
			recompute();
		}
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		lastGear = snapshot.getGear();
		recompute();
		refreshSummary();
	}

	/** Recomputes max hit / accuracy / DPS from the latest gear snapshot + current UI selections. */
	private void recompute()
	{
		boolean enabled = config == null || config.gearSectionEnabled();
		disabledNote.setVisible(!enabled);
		if (!enabled)
		{
			clearOutputs();
			return;
		}

		if (lastGear == null || selectedMonster == null)
		{
			clearOutputs();
			return;
		}

		EquipmentStats gearStats = GearMapper.buildEquipmentStats(lastGear.equippedItemIds(), WEAPON_SLOT_INDEX,
			this::lookupSlotStats);
		Stance stance = defaultStanceFor(selectedStyle);
		PlayerCombat player = GearMapper.toPlayerCombat(lastGear, stance,
			bestPotionCheckbox.isSelected(), bestPrayerCheckbox.isSelected(), onSlayerTaskCheckbox.isSelected());

		DpsResult result = DpsCalculator.compute(gearStats, player, selectedStyle, selectedMonster, DEFAULT_MAGIC_BASE_MAX_HIT);

		maxHitValue.setText(String.valueOf(result.maxHit()));
		accuracyValue.setText(String.format(Locale.ROOT, "%.1f%%", result.accuracy() * 100.0));
		lastDps = result.dps();
		dpsValue.setText(String.format(Locale.ROOT, "%.2f", lastDps));
		baseEstimateNote.setVisible(result.baseEstimate());

		refreshSummary();
	}

	private void clearOutputs()
	{
		maxHitValue.setText("-");
		accuracyValue.setText("-");
		dpsValue.setText("-");
		baseEstimateNote.setVisible(false);
		lastDps = 0.0;
	}

	/**
	 * Default {@link Stance} per {@link CombatStyle}, since Phase 1 has no
	 * combat-options-tab UI: Aggressive for melee (maximises sustained melee
	 * DPS via the strength style bonus), Rapid for ranged (fastest attack
	 * speed, the common sustained-DPS choice), and Standard for magic
	 * (ordinary spell-casting has no style choice per {@link Stance}).
	 */
	private static Stance defaultStanceFor(CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return Stance.RAPID;
			case MAGIC:
				return Stance.STANDARD;
			default:
				return Stance.AGGRESSIVE;
		}
	}

	/** Real {@link ItemManager}-backed lookup — thread-safe/cached, callable on the EDT. */
	private GearMapper.SlotStats lookupSlotStats(int itemId)
	{
		if (itemManager == null || itemId <= 0)
		{
			return null;
		}
		ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null || !stats.isEquipable())
		{
			return null;
		}
		ItemEquipmentStats eq = stats.getEquipment();
		if (eq == null)
		{
			return null;
		}
		return new GearMapper.SlotStats(
			eq.getAstab(), eq.getAslash(), eq.getAcrush(), eq.getAmagic(), eq.getArange(),
			eq.getDstab(), eq.getDslash(), eq.getDcrush(), eq.getDmagic(), eq.getDrange(),
			eq.getStr(), eq.getRstr(), eq.getMdmg(), eq.getPrayer(), eq.getAspeed(), eq.isTwoHanded());
	}

	@Override
	protected String summaryText()
	{
		if (config != null && !config.gearSectionEnabled())
		{
			return "Disabled";
		}
		return "DPS " + String.format(Locale.ROOT, "%.2f", lastDps);
	}
}
