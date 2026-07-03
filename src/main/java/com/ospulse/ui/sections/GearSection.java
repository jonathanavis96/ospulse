package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.Stance;
import com.ospulse.session.GearMapper;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.PanelWidgets;

import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.AbstractListModel;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Gear / DPS calculator (Phase 1 — live readout): reads the player's current
 * worn gear + boosted levels + active prayers (via {@link GearSnapshot}, fed
 * live by {@code SessionTracker} on the client thread) and computes max hit /
 * accuracy / DPS against a selected {@link Monster} using the
 * {@code com.ospulse.combat} engine, for a user-toggleable {@link CombatStyle}.
 *
 * <p>The section is always active (it makes no network calls). Its body leads
 * with a live equipment-tab-style grid of the player's worn item icons so it
 * is unmistakable that the numbers below refer to the CURRENT worn gear.
 * Combat style and the simulation toggles (best potion / best offensive
 * prayer / on Slayer task) are icon buttons with tooltips; the target is
 * picked via a search box over a fully scrollable {@link MonsterRepository}
 * result list.
 *
 * <p>Deferred to a later phase (see the design spec): live opponent
 * auto-detection, favourites, and gear upgrade suggestions.
 *
 * <p><b>Threading:</b> this is an EDT Swing component. It never reads the
 * RuneLite {@code Client} directly and never calls any
 * {@code getBoostedSkillLevel}/{@code isPrayerActive}/{@code getItemContainer}
 * -style API — all of that happens in {@code SessionTracker} on the client
 * thread and is published as {@link GearSnapshot}, including the precomputed
 * {@link GearSnapshot#equipmentStats()}. The <b>only</b> {@code ItemManager}
 * method this class calls is {@code getImage(int)}, which is explicitly
 * EDT-safe (async sprite load); {@code ItemManager.getItemStats} /
 * {@code getItemComposition} assert the client thread internally and must
 * never be called from here.
 */
public final class GearSection extends CollapsibleSection
{
	public static final String KEY = "gear";

	/**
	 * TODO Tier-A/magic simplification: Phase 1 has no spellbook UI, so magic
	 * DPS uses a placeholder base spell max hit (roughly a mid-tier
	 * standard-spellbook bolt spell) rather than the player's actually-selected
	 * spell. Revisit once a spell picker exists.
	 */
	private static final int DEFAULT_MAGIC_BASE_MAX_HIT = 20;

	/** Native RuneLite item sprite size is 36x32; cells pad that slightly. */
	private static final int SLOT_W = 38;
	private static final int SLOT_H = 36;

	/**
	 * The classic OSRS equipment-tab layout as {@code EquipmentInventorySlot}
	 * ordinals (== EQUIPMENT container slot indices), 3 columns x 5 rows;
	 * {@code -1} = decorative filler (no slot). 0=HEAD 1=CAPE 2=AMULET
	 * 3=WEAPON 4=BODY 5=SHIELD 7=LEGS 9=GLOVES 10=BOOTS 12=RING 13=AMMO
	 * (6/8/11 are the internal ARMS/HAIR/JAW slots, never worn items).
	 */
	private static final int[] SLOT_GRID = {
		-1, 0, -1,
		1, 2, 13,
		3, 4, 5,
		-1, 7, -1,
		9, 10, 12,
	};

	private static final String[] SLOT_NAMES = {
		"Head", "Cape", "Amulet", "Weapon", "Body", "Shield", "", "Legs",
		"", "Gloves", "Boots", "", "Ring", "Ammo",
	};

	// Representative item ids for the icon buttons (item images are the
	// EDT-safe icon source — see class javadoc). Ranged/Magic use the actual
	// skill icons (via SkillIconManager) instead, since no single item reads as
	// "the ranged/magic style" the way a stab weapon does.
	private static final int ICON_STAB = 22324;    // Ghrazi rapier (a stab weapon)
	private static final int ICON_SLASH = 4587;    // Dragon scimitar
	private static final int ICON_CRUSH = 13576;   // Dragon warhammer
	private static final int ICON_POTION = 12695;  // Super combat potion(4)
	private static final int ICON_PRAYER = 1718;   // Holy symbol
	private static final int ICON_SLAYER = 11864;  // Slayer helmet

	/** Skill-icon side length for the Ranged/Magic style buttons. */
	private static final int STYLE_ICON_SIZE = 25;

	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;

	private final JLabel[] slotLabels = new JLabel[GearSnapshot.EQUIPMENT_SLOT_COUNT];
	private final int[] renderedSlotIds = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];

	private final IconTextField monsterSearchField;
	private final JScrollPane listScroll;
	private final JList<String> monsterList;
	private final MonsterListModel monsterListModel = new MonsterListModel();
	private final JLabel targetLabel;
	private final JToggleButton bestPotionToggle;
	private final JToggleButton bestPrayerToggle;
	private final JToggleButton onSlayerTaskToggle;
	private final JLabel maxHitValue;
	private final JLabel accuracyValue;
	private final JLabel avgHitValue;
	private final JLabel dpsValue;
	private final JLabel ttkValue;
	private final JLabel baseEstimateNote;

	private List<Monster> filteredMonsters = Collections.emptyList();
	private Monster selectedMonster;
	private CombatStyle selectedStyle = CombatStyle.STAB;
	private GearSnapshot lastGear;
	private double lastDps;
	private boolean suppressListEvents;
	/** Last observed "slayer helm / black mask worn" state — drives edge-triggered auto-tick. */
	private boolean lastSlayerHeadgearWorn;

	public GearSection(CollapseStore store, ItemManager itemManager, SkillIconManager skillIconManager)
	{
		super(KEY, "Gear DPS", store);
		this.itemManager = itemManager;
		this.skillIconManager = skillIconManager;

		// ------------------------------------------------ worn-gear header
		JLabel heading = PanelWidgets.emptyRowLabel("Live DPS · your worn gear");
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		heading.setToolTipText("Computed live from the equipment you are currently wearing");
		body().add(heading);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		body().add(buildGearGrid());
		body().add(Box.createRigidArea(new Dimension(0, 6)));

		// ------------------------------------------- style + boost toggles
		body().add(buildStyleRow());
		body().add(Box.createRigidArea(new Dimension(0, 3)));
		JPanel boostRow = new JPanel(new GridLayout(1, 3, 2, 0));
		boostRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		boostRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		bestPotionToggle = iconToggle(ICON_POTION,
			"Simulate best boosting potion (e.g. super combat / ranging / saturated heart)");
		bestPrayerToggle = iconToggle(ICON_PRAYER,
			"Simulate best offensive prayer (e.g. Piety / Rigour / Augury)");
		onSlayerTaskToggle = iconToggle(ICON_SLAYER,
			"On Slayer task — applies the slayer helmet / black mask bonus (auto-ticks "
				+ "while one is worn; untick if you are actually off-task)");
		boostRow.add(bestPotionToggle);
		boostRow.add(bestPrayerToggle);
		boostRow.add(onSlayerTaskToggle);
		// The boost toggles must recompute whenever they change — on a user
		// click OR the programmatic slayer-helm auto-tick. An ItemListener fires
		// on both; an ActionListener would silently miss setSelected(), which is
		// why ticking a boost previously left Max hit unchanged.
		bestPotionToggle.addItemListener(e -> recompute());
		bestPrayerToggle.addItemListener(e -> recompute());
		onSlayerTaskToggle.addItemListener(e -> recompute());
		boostRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, boostRow.getPreferredSize().height));
		body().add(boostRow);
		body().add(Box.createRigidArea(new Dimension(0, 6)));

		// -------------------------------------------------- target picker
		monsterSearchField = new IconTextField();
		monsterSearchField.setIcon(IconTextField.Icon.SEARCH);
		monsterSearchField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		monsterSearchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		monsterSearchField.setToolTipText("Search the monster to compute DPS against");
		monsterSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		monsterSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		monsterSearchField.setPreferredSize(new Dimension(100, 24));
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

		monsterList = new JList<>(monsterListModel);
		monsterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		monsterList.setFont(FontManager.getRunescapeSmallFont());
		monsterList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		monsterList.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		monsterList.setSelectionBackground(ColorScheme.BRAND_ORANGE);
		monsterList.setSelectionForeground(ColorScheme.DARK_GRAY_COLOR);
		monsterList.setVisibleRowCount(6);
		// Fixed cell size so the (up to ~2830-row) list never measures every
		// cell — keeps filtering-while-typing snappy.
		monsterList.setPrototypeCellValue("Abyssal demon (Catacombs of Kourend)");
		monsterList.addListSelectionListener(e ->
		{
			if (suppressListEvents || e.getValueIsAdjusting())
			{
				return;
			}
			int index = monsterList.getSelectedIndex();
			if (index >= 0 && index < filteredMonsters.size())
			{
				selectedMonster = filteredMonsters.get(index);
				updateTargetLabel();
				recompute();
				// Collapse the result list once a target is picked — the choice
				// shows in the Target line below; typing in the search box again
				// re-opens it (see onSearchChanged).
				setListOpen(false);
			}
		});

		listScroll = new JScrollPane(monsterList);
		listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		listScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		listScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, listScroll.getPreferredSize().height));
		body().add(listScroll);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		targetLabel = PanelWidgets.emptyRowLabel("Target: -");
		targetLabel.setForeground(java.awt.Color.WHITE);
		targetLabel.setToolTipText("The monster the DPS numbers below are computed against");
		body().add(targetLabel);
		body().add(Box.createRigidArea(new Dimension(0, 6)));

		// ------------------------------------------------------- outputs
		maxHitValue = PanelWidgets.statRow(body(), "Max hit");
		accuracyValue = PanelWidgets.statRow(body(), "Accuracy");
		avgHitValue = PanelWidgets.statRow(body(), "Avg hit");
		dpsValue = PanelWidgets.statRow(body(), "DPS");
		ttkValue = PanelWidgets.statRow(body(), "Time to kill");

		baseEstimateNote = PanelWidgets.emptyRowLabel("~ base estimate (some effects not yet modelled)");
		baseEstimateNote.setVisible(false);
		body().add(baseEstimateNote);

		JLabel comingSoon = PanelWidgets.emptyRowLabel("Gear upgrade suggestions — coming soon");
		comingSoon.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		comingSoon.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
		body().add(Box.createRigidArea(new Dimension(0, 4)));
		body().add(comingSoon);

		// Show the full monster list, but with NO pre-selected target — the
		// user must explicitly pick one before any numbers are shown (see
		// populateMonsterList). Auto-detect-opponent and favourites are
		// deferred to a later phase.
		populateMonsterList("");
	}

	// ----------------------------------------------------------- gear grid

	/**
	 * A centered, equipment-tab-shaped grid of the player's worn item icons —
	 * populated live in {@link #apply} from {@link GearSnapshot#equippedItemIds()}
	 * via the EDT-safe async {@code ItemManager.getImage(int)}.
	 */
	private JPanel buildGearGrid()
	{
		JPanel grid = new JPanel(new GridLayout(5, 3, 2, 2));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (int slotOrdinal : SLOT_GRID)
		{
			if (slotOrdinal < 0)
			{
				JLabel filler = new JLabel();
				filler.setOpaque(false);
				grid.add(filler);
				continue;
			}
			JLabel cell = new JLabel();
			cell.setOpaque(true);
			cell.setBackground(ColorScheme.DARK_GRAY_COLOR);
			cell.setHorizontalAlignment(SwingConstants.CENTER);
			cell.setVerticalAlignment(SwingConstants.CENTER);
			cell.setPreferredSize(new Dimension(SLOT_W, SLOT_H));
			cell.setToolTipText(SLOT_NAMES[slotOrdinal] + " slot (live)");
			slotLabels[slotOrdinal] = cell;
			renderedSlotIds[slotOrdinal] = Integer.MIN_VALUE;
			grid.add(cell);
		}

		// Center the grid within the (BoxLayout, left-aligned) section body.
		JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.add(grid);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
		return wrapper;
	}

	/** Diff-updates the worn-gear icon grid; only touched slots reload their sprite. */
	private void updateGearGrid(GearSnapshot gear)
	{
		int[] ids = gear == null ? null : gear.equippedItemIds();
		for (int slot = 0; slot < slotLabels.length; slot++)
		{
			JLabel cell = slotLabels[slot];
			if (cell == null)
			{
				continue;
			}
			int id = ids == null || slot >= ids.length ? -1 : ids[slot];
			if (id == renderedSlotIds[slot])
			{
				continue;
			}
			renderedSlotIds[slot] = id;
			if (id > 0 && itemManager != null)
			{
				// EDT-safe: getImage is async (AsyncBufferedImage), the ONLY
				// ItemManager call permitted off the client thread.
				itemManager.getImage(id).addTo(cell);
			}
			else
			{
				cell.setIcon(null);
			}
		}
	}

	// ------------------------------------------------------- icon controls

	/** Five exclusive icon buttons, one per {@link CombatStyle}, with tooltips. */
	private JPanel buildStyleRow()
	{
		JPanel row = new JPanel(new GridLayout(1, 5, 2, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		ButtonGroup group = new ButtonGroup();
		CombatStyle[] styles = { CombatStyle.STAB, CombatStyle.SLASH, CombatStyle.CRUSH, CombatStyle.RANGED, CombatStyle.MAGIC };
		String[] tooltips = { "Stab", "Slash", "Crush", "Ranged", "Magic" };
		for (int i = 0; i < styles.length; i++)
		{
			CombatStyle style = styles[i];
			JToggleButton button = styleToggle(style, tooltips[i] + " attack style");
			button.setSelected(style == selectedStyle);
			button.addActionListener(e ->
			{
				selectedStyle = style;
				recompute();
			});
			group.add(button);
			row.add(button);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * The toggle for one attack style: a representative <em>item</em> icon for
	 * the melee styles (Ghrazi rapier / Dragon scimitar / Dragon warhammer) and
	 * the actual <em>skill</em> icon for Ranged / Magic.
	 */
	private JToggleButton styleToggle(CombatStyle style, String tooltip)
	{
		switch (style)
		{
			case RANGED:
				return iconToggle(skillImage(Skill.RANGED), tooltip);
			case MAGIC:
				return iconToggle(skillImage(Skill.MAGIC), tooltip);
			case SLASH:
				return iconToggle(ICON_SLASH, tooltip);
			case CRUSH:
				return iconToggle(ICON_CRUSH, tooltip);
			default:
				return iconToggle(ICON_STAB, tooltip);
		}
	}

	/** A skill icon scaled to {@link #STYLE_ICON_SIZE}, or {@code null} if unavailable. */
	private Image skillImage(Skill skill)
	{
		if (skillIconManager == null)
		{
			return null;
		}
		try
		{
			Image full = skillIconManager.getSkillImage(skill, false);
			return full == null ? null : full.getScaledInstance(STYLE_ICON_SIZE, STYLE_ICON_SIZE, Image.SCALE_SMOOTH);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Bare icon-toggle button (border/background styling, no icon yet). */
	private JToggleButton newToggle(String tooltip)
	{
		JToggleButton button = new JToggleButton();
		button.setToolTipText(tooltip);
		button.setFocusPainted(false);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setPreferredSize(new Dimension(40, SLOT_H));

		Border selectedBorder = BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE);
		Border unselectedBorder = BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR);
		Runnable restyle = () ->
		{
			button.setBorder(button.isSelected() ? selectedBorder : unselectedBorder);
			button.setBackground(button.isSelected()
				? ColorScheme.MEDIUM_GRAY_COLOR
				: ColorScheme.DARKER_GRAY_COLOR);
		};
		restyle.run();
		button.addItemListener(e -> restyle.run());
		return button;
	}

	/**
	 * An icon-only toggle button whose sprite loads via the async, EDT-safe
	 * {@code ItemManager.getImage(int)}.
	 */
	private JToggleButton iconToggle(int itemId, String tooltip)
	{
		JToggleButton button = newToggle(tooltip);
		if (itemManager != null)
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			button.setIcon(new ImageIcon(image));
			image.onLoaded(() ->
			{
				button.setIcon(new ImageIcon(image));
				button.repaint();
			});
		}
		return button;
	}

	/** An icon-only toggle button from an already-loaded {@link Image} (e.g. a skill icon). */
	private JToggleButton iconToggle(Image image, String tooltip)
	{
		JToggleButton button = newToggle(tooltip);
		if (image != null)
		{
			button.setIcon(new ImageIcon(image));
		}
		return button;
	}

	// ------------------------------------------------------- target picker

	private void onSearchChanged()
	{
		// Typing re-opens the (collapsed-after-pick) result list.
		setListOpen(true);
		populateMonsterList(monsterSearchField.getText());
	}

	/** Shows/hides the scrollable result list and reflows the panel around it. */
	private void setListOpen(boolean open)
	{
		if (listScroll.isVisible() == open)
		{
			return;
		}
		listScroll.setVisible(open);
		listScroll.revalidate();
		body().revalidate();
		body().repaint();
	}

	/**
	 * Refilters the full (uncapped) monster list and swaps the backing list of
	 * the {@link JList} model in one shot — no per-item model mutation, so
	 * typing stays responsive even when the filter matches thousands of names.
	 */
	private void populateMonsterList(String query)
	{
		filteredMonsters = MonsterRepository.getInstance().search(query == null ? "" : query.trim());
		suppressListEvents = true;
		monsterListModel.setMonsters(filteredMonsters);

		int keepIndex = -1;
		if (selectedMonster != null)
		{
			for (int i = 0; i < filteredMonsters.size(); i++)
			{
				if (filteredMonsters.get(i).name().equalsIgnoreCase(selectedMonster.name()))
				{
					keepIndex = i;
					break;
				}
			}
		}
		// Deliberately NO silent default target: computing against an
		// arbitrary first entry (previously "A corpse", 90 HP) produced
		// convincing-but-wrong numbers when the user hadn't picked yet. Until
		// the user selects a monster the outputs stay "-" with an explicit
		// "pick a target" hint instead.
		if (keepIndex >= 0)
		{
			monsterList.setSelectedIndex(keepIndex);
			monsterList.ensureIndexIsVisible(keepIndex);
		}
		else
		{
			// Selection filtered out — keep the last target (label still shows it).
			monsterList.clearSelection();
		}
		suppressListEvents = false;
		updateTargetLabel();
		recompute();
	}

	private void updateTargetLabel()
	{
		targetLabel.setText(selectedMonster == null
			? "Target: pick a monster above"
			: "Target: " + selectedMonster.name());
		targetLabel.setForeground(selectedMonster == null
			? ColorScheme.LIGHT_GRAY_COLOR
			: java.awt.Color.WHITE);
	}

	// ------------------------------------------------------------- compute

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		lastGear = snapshot.getGear();
		updateGearGrid(lastGear);
		autoToggleSlayerFromGear();
		recompute();
		refreshSummary();
	}

	/**
	 * Auto-ticks "On Slayer task" when the player puts on a slayer helmet /
	 * black mask (any variant), and auto-unticks it when the headgear comes
	 * off. Edge-triggered: it only acts when the worn-state actually flips, so
	 * a manual untick while the helm is still worn is respected (the common
	 * "wearing the helm but off-task" case). Detection uses the client-thread-
	 * computed {@link GearSnapshot#equipmentStats()} — {@code slayerHeadgear()}
	 * is already resolved there, so no {@code Client}/{@code ItemManager} lookup
	 * happens on the EDT. The slayer bonus only applies while the headgear is
	 * worn anyway, so this keeps the toggle honest by default.
	 */
	private void autoToggleSlayerFromGear()
	{
		boolean worn = lastGear != null
			&& lastGear.equipmentStats() != null
			&& lastGear.equipmentStats().slayerHeadgear().wornAtAll();
		if (worn != lastSlayerHeadgearWorn)
		{
			lastSlayerHeadgearWorn = worn;
			onSlayerTaskToggle.setSelected(worn); // fires ItemListener -> recompute + restyle
		}
	}

	/** Recomputes max hit / accuracy / DPS from the latest gear snapshot + current UI selections. */
	private void recompute()
	{
		if (lastGear == null || selectedMonster == null || lastGear.equipmentStats() == null)
		{
			clearOutputs();
			return;
		}

		EquipmentStats gearStats = lastGear.equipmentStats();
		Stance stance = defaultStanceFor(selectedStyle);
		PlayerCombat player = GearMapper.toPlayerCombat(lastGear, stance,
			bestPotionToggle.isSelected(), bestPrayerToggle.isSelected(), onSlayerTaskToggle.isSelected());

		DpsResult result = DpsCalculator.compute(gearStats, player, selectedStyle, selectedMonster, DEFAULT_MAGIC_BASE_MAX_HIT);

		maxHitValue.setText(String.valueOf(result.maxHit()));
		accuracyValue.setText(String.format(Locale.ROOT, "%.1f%%", result.accuracy() * 100.0));
		avgHitValue.setText(String.format(Locale.ROOT, "%.2f", result.avgHit()));
		lastDps = result.dps();
		dpsValue.setText(String.format(Locale.ROOT, "%.2f", lastDps));
		ttkValue.setText(formatTtk(result.ttkSeconds()));
		baseEstimateNote.setVisible(result.baseEstimate());

		refreshSummary();
	}

	private void clearOutputs()
	{
		maxHitValue.setText("-");
		accuracyValue.setText("-");
		avgHitValue.setText("-");
		dpsValue.setText("-");
		ttkValue.setText("-");
		baseEstimateNote.setVisible(false);
		lastDps = 0.0;
	}

	/** Formats a time-to-kill (seconds) as "12.3s" or "1:05" for a minute or more; "-" when non-positive. */
	private static String formatTtk(double ttkSeconds)
	{
		if (ttkSeconds <= 0 || Double.isInfinite(ttkSeconds) || Double.isNaN(ttkSeconds))
		{
			return "-";
		}
		if (ttkSeconds < 60)
		{
			return String.format(Locale.ROOT, "%.1fs", ttkSeconds);
		}
		int total = (int) Math.round(ttkSeconds);
		return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
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

	@Override
	protected String summaryText()
	{
		if (selectedMonster == null)
		{
			return "no target";
		}
		return "DPS " + String.format(Locale.ROOT, "%.2f", lastDps);
	}

	// ------------------------------------------------- test seams (package)

	IconTextField searchFieldForTest()
	{
		return monsterSearchField;
	}

	JList<String> monsterListForTest()
	{
		return monsterList;
	}

	Monster selectedMonsterForTest()
	{
		return selectedMonster;
	}

	String dpsTextForTest()
	{
		return dpsValue.getText();
	}

	String maxHitTextForTest()
	{
		return maxHitValue.getText();
	}

	JToggleButton bestPotionToggleForTest()
	{
		return bestPotionToggle;
	}

	JToggleButton onSlayerTaskToggleForTest()
	{
		return onSlayerTaskToggle;
	}

	String ttkTextForTest()
	{
		return ttkValue.getText();
	}

	String targetTextForTest()
	{
		return targetLabel.getText();
	}

	/**
	 * A {@link JList} model over the current filtered {@link Monster} list.
	 * Refiltering swaps the backing list and fires two coarse events instead
	 * of mutating a {@code DefaultListModel} item-by-item (which fires one
	 * event per element — noticeably slow at ~2.8k rows per keystroke).
	 */
	private static final class MonsterListModel extends AbstractListModel<String>
	{
		private List<Monster> monsters = Collections.emptyList();

		void setMonsters(List<Monster> newMonsters)
		{
			int oldSize = monsters.size();
			monsters = newMonsters == null ? Collections.emptyList() : newMonsters;
			if (oldSize > 0)
			{
				fireIntervalRemoved(this, 0, oldSize - 1);
			}
			if (!monsters.isEmpty())
			{
				fireIntervalAdded(this, 0, monsters.size() - 1);
			}
		}

		@Override
		public int getSize()
		{
			return monsters.size();
		}

		@Override
		public String getElementAt(int index)
		{
			return monsters.get(index).name();
		}
	}
}
