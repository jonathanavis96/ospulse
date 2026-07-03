package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.Stance;
import com.ospulse.combat.WeaponCategoryRepository;
import com.ospulse.combat.WeaponStyle;
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
import javax.swing.BoxLayout;
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Gear / DPS calculator (Phase 1 — live readout): reads the player's current
 * worn gear + boosted levels + active prayers (via {@link GearSnapshot}, fed
 * live by {@code SessionTracker} on the client thread) and computes max hit /
 * accuracy / DPS against a selected {@link Monster} using the
 * {@code com.ospulse.combat} engine.
 *
 * <p>The section is always active (it makes no network calls). Its body leads
 * with a live equipment-tab-style grid of the player's worn item icons so it
 * is unmistakable that the numbers below refer to the CURRENT worn gear. It
 * then shows the equipped weapon's <b>real attack styles</b> (resolved from the
 * weapon id via {@link WeaponCategoryRepository}), each computed and
 * <b>ranked by DPS</b> against the picked target — the best is starred and
 * selected by default, and clicking any row locks the readout to that style.
 * The simulation toggles (best potion / best offensive prayer / on Slayer task)
 * are icon buttons with tooltips; the target is picked via a search box over a
 * fully scrollable {@link MonsterRepository} result list.
 *
 * <p>Magic styles are intentionally excluded from the ranking for now (magic
 * max hit still uses a placeholder base spell — see
 * {@link #DEFAULT_MAGIC_BASE_MAX_HIT}); a weapon whose only styles are magic
 * (powered staff/wand) falls back to a single magic placeholder row.
 *
 * <p>Deferred to a later phase (see the design spec): live opponent
 * auto-detection, favourites, magic spell picker, and gear upgrade suggestions
 * (the optimiser reuses this same weapon-id → styles path for weapons you do
 * not yet own).
 *
 * <p><b>Threading:</b> this is an EDT Swing component. It never reads the
 * RuneLite {@code Client} directly. It reads only precomputed snapshot fields
 * (including {@link GearSnapshot#equipmentStats()} and the equipped weapon id)
 * and the bundled, pure {@link WeaponCategoryRepository}; the <b>only</b>
 * {@code ItemManager} method it calls is {@code getImage(int)}, which is
 * EDT-safe (async sprite load).
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

	/** {@code EquipmentInventorySlot.WEAPON} ordinal — index into {@link GearSnapshot#equippedItemIds()}. */
	private static final int WEAPON_SLOT = 3;

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

	// Representative item ids for the per-style type icons (item images are the
	// EDT-safe icon source — see class javadoc). Ranged/Magic use the actual
	// skill icons (via SkillIconManager) instead, since no single item reads as
	// "the ranged/magic style" the way a stab weapon does.
	private static final int ICON_STAB = 22324;    // Ghrazi rapier (a stab weapon)
	private static final int ICON_SLASH = 4587;    // Dragon scimitar
	private static final int ICON_CRUSH = 13576;   // Dragon warhammer
	private static final int ICON_POTION = 12695;  // Super combat potion(4)
	private static final int ICON_PRAYER = 1718;   // Holy symbol
	private static final int ICON_SLAYER = 11864;  // Slayer helmet

	/** Skill-icon side length for the Ranged/Magic type icons. */
	private static final int STYLE_ICON_SIZE = 18;

	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;
	private final WeaponCategoryRepository weaponRepo = WeaponCategoryRepository.getInstance();

	/** Small per-{@link CombatStyle} type icon, index == {@code CombatStyle.ordinal()}; null-safe. */
	private final ImageIcon[] typeIcons = new ImageIcon[CombatStyle.values().length];

	private final JLabel[] slotLabels = new JLabel[GearSnapshot.EQUIPMENT_SLOT_COUNT];
	private final int[] renderedSlotIds = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];

	private final JLabel stylesHeading;
	private final JPanel stylesPanel;
	private final List<StyleRow> styleRows = new ArrayList<>();

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
	private WeaponStyle selectedStyle;
	/** True once the user clicks a specific style row — until then the readout follows the best-DPS style. */
	private boolean userPickedStyle;
	/** Weapon id the current ranking/selection was built for; a change re-defaults to the best style. */
	private int lastRankedWeaponId = Integer.MIN_VALUE;
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
		buildTypeIcons();

		// ------------------------------------------------ worn-gear header
		JLabel heading = PanelWidgets.emptyRowLabel("Live DPS · your worn gear");
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		heading.setToolTipText("Computed live from the equipment you are currently wearing");
		body().add(heading);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		body().add(buildGearGrid());
		body().add(Box.createRigidArea(new Dimension(0, 6)));

		// --------------------------------------- ranked attack-style picker
		stylesHeading = PanelWidgets.emptyRowLabel("Attack styles (best DPS first)");
		stylesHeading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		stylesHeading.setToolTipText("Your equipped weapon's attack styles, ranked by DPS "
			+ "against the selected target. Click one to lock the readout to it.");
		body().add(stylesHeading);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		stylesPanel = new JPanel();
		stylesPanel.setLayout(new BoxLayout(stylesPanel, BoxLayout.Y_AXIS));
		stylesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stylesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(stylesPanel);
		body().add(Box.createRigidArea(new Dimension(0, 6)));

		// ------------------------------------------------- boost toggles
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
		// The boost toggles must re-rank whenever they change — on a user click OR
		// the programmatic slayer-helm auto-tick. An ItemListener fires on both; an
		// ActionListener would silently miss setSelected(). Boosts shift every
		// style's DPS, so the whole ranking is recomputed, not just the readout.
		bestPotionToggle.addItemListener(e -> rankAndRender());
		bestPrayerToggle.addItemListener(e -> rankAndRender());
		onSlayerTaskToggle.addItemListener(e -> rankAndRender());
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
				rankAndRender();
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

	// ------------------------------------------------- attack-style picker

	/** Precomputes a small icon per damage type for the ranked style rows. */
	private void buildTypeIcons()
	{
		typeIcons[CombatStyle.STAB.ordinal()] = itemIcon(ICON_STAB);
		typeIcons[CombatStyle.SLASH.ordinal()] = itemIcon(ICON_SLASH);
		typeIcons[CombatStyle.CRUSH.ordinal()] = itemIcon(ICON_CRUSH);
		typeIcons[CombatStyle.RANGED.ordinal()] = skillIcon(Skill.RANGED);
		typeIcons[CombatStyle.MAGIC.ordinal()] = skillIcon(Skill.MAGIC);
	}

	private ImageIcon itemIcon(int itemId)
	{
		if (itemManager == null)
		{
			return null;
		}
		AsyncBufferedImage image = itemManager.getImage(itemId);
		ImageIcon icon = new ImageIcon(image.getScaledInstance(STYLE_ICON_SIZE, STYLE_ICON_SIZE, Image.SCALE_SMOOTH));
		// Re-scale once the async sprite actually arrives.
		image.onLoaded(() -> icon.setImage(image.getScaledInstance(STYLE_ICON_SIZE, STYLE_ICON_SIZE, Image.SCALE_SMOOTH)));
		return icon;
	}

	private ImageIcon skillIcon(Skill skill)
	{
		if (skillIconManager == null)
		{
			return null;
		}
		try
		{
			Image full = skillIconManager.getSkillImage(skill, false);
			return full == null ? null
				: new ImageIcon(full.getScaledInstance(STYLE_ICON_SIZE, STYLE_ICON_SIZE, Image.SCALE_SMOOTH));
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static String typeLabel(CombatStyle type)
	{
		switch (type)
		{
			case STAB:
				return "Stab";
			case SLASH:
				return "Slash";
			case CRUSH:
				return "Crush";
			case RANGED:
				return "Ranged";
			case MAGIC:
				return "Magic";
			default:
				return type.name();
		}
	}

	/**
	 * Recomputes the equipped weapon's attack-style ranking (DPS-desc against
	 * the current target and boosts), rebuilds the clickable rows, re-selects
	 * the best (or keeps the user's pick if it survives), and refreshes the
	 * readout. The single entry point whenever gear, target or boosts change.
	 */
	private void rankAndRender()
	{
		styleRows.clear();
		stylesPanel.removeAll();

		int weaponId = lastGear == null ? -1 : lastGear.itemIdAt(WEAPON_SLOT);
		if (weaponId != lastRankedWeaponId)
		{
			// A different weapon: its styles differ, so any prior manual pick no
			// longer applies — fall back to auto-selecting the best.
			lastRankedWeaponId = weaponId;
			userPickedStyle = false;
		}
		List<WeaponStyle> styles = new ArrayList<>(weaponRepo.stylesForItem(weaponId));
		// Magic max hit is a placeholder until a spell picker exists, so magic
		// styles would rank on fictional numbers — drop them from the ranking.
		styles.removeIf(s -> s.type() == CombatStyle.MAGIC);
		if (styles.isEmpty())
		{
			// Pure-magic weapon (powered staff/wand): keep a single placeholder so
			// the readout still works, honestly flagged as a base estimate.
			styles.add(new WeaponStyle("Magic", CombatStyle.MAGIC, Stance.STANDARD));
		}

		boolean canRank = lastGear != null && selectedMonster != null && lastGear.equipmentStats() != null;

		// Compute each style's DPS (NaN when we cannot yet), then rank desc.
		List<Ranked> ranked = new ArrayList<>(styles.size());
		for (WeaponStyle style : styles)
		{
			DpsResult result = canRank ? computeFor(style) : null;
			ranked.add(new Ranked(style, result));
		}
		if (canRank)
		{
			ranked.sort(Comparator.comparingDouble((Ranked r) -> r.result.dps()).reversed());
		}

		// Follow the best-DPS style (top of the ranking) by default; only honour a
		// prior selection when the user explicitly clicked a row and it survives.
		WeaponStyle keep = null;
		if (userPickedStyle)
		{
			for (Ranked r : ranked)
			{
				if (r.style.equals(selectedStyle))
				{
					keep = r.style;
					break;
				}
			}
		}
		selectedStyle = keep != null ? keep : (ranked.isEmpty() ? null : ranked.get(0).style);

		for (int i = 0; i < ranked.size(); i++)
		{
			Ranked r = ranked.get(i);
			boolean best = canRank && i == 0;
			StyleRow row = new StyleRow(r.style, r.result, best);
			styleRows.add(row);
			stylesPanel.add(row);
			stylesPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		}
		highlightSelectedRow();
		stylesPanel.revalidate();
		stylesPanel.repaint();

		updateOutputs();
	}

	/** Applies the selected-row border/background to whichever row matches {@link #selectedStyle}. */
	private void highlightSelectedRow()
	{
		for (StyleRow row : styleRows)
		{
			row.setSelected(row.style.equals(selectedStyle));
		}
	}

	/** User clicked a style row: lock the readout to it (ranking order is unchanged). */
	private void selectStyle(WeaponStyle style)
	{
		selectedStyle = style;
		userPickedStyle = true;
		highlightSelectedRow();
		updateOutputs();
	}

	/** DPS for one style against the current target + gear + toggles, or {@code null} if not computable. */
	private DpsResult computeFor(WeaponStyle style)
	{
		if (lastGear == null || selectedMonster == null || lastGear.equipmentStats() == null || style == null)
		{
			return null;
		}
		EquipmentStats gearStats = lastGear.equipmentStats();
		PlayerCombat player = GearMapper.toPlayerCombat(lastGear, style.stance(),
			bestPotionToggle.isSelected(), bestPrayerToggle.isSelected(), onSlayerTaskToggle.isSelected());
		return DpsCalculator.compute(gearStats, player, style.type(), selectedMonster, DEFAULT_MAGIC_BASE_MAX_HIT);
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
		rankAndRender();
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
		rankAndRender();
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
			onSlayerTaskToggle.setSelected(worn); // fires ItemListener -> rankAndRender + restyle
		}
	}

	/** Updates the max hit / accuracy / DPS readout for the currently selected style. */
	private void updateOutputs()
	{
		DpsResult result = computeFor(selectedStyle);
		if (result == null)
		{
			clearOutputs();
			return;
		}

		maxHitValue.setText(String.valueOf(result.maxHit()));
		accuracyValue.setText(String.format(Locale.ROOT, "%.1f%%", result.accuracy() * 100.0));
		avgHitValue.setText(String.format(Locale.ROOT, "%.2f", result.avgHit()));
		lastDps = result.dps();
		dpsValue.setText(String.format(Locale.ROOT, "%.2f", lastDps));
		ttkValue.setText(formatTtk(result.ttkSeconds()));
		// A magic placeholder row (pure-magic weapon) is always a base estimate.
		baseEstimateNote.setVisible(result.baseEstimate()
			|| (selectedStyle != null && selectedStyle.type() == CombatStyle.MAGIC));

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

	@Override
	protected String summaryText()
	{
		if (selectedMonster == null)
		{
			return "no target";
		}
		return "DPS " + String.format(Locale.ROOT, "%.2f", lastDps);
	}

	// ------------------------------------------------------- icon controls

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

	WeaponStyle selectedStyleForTest()
	{
		return selectedStyle;
	}

	int styleRowCountForTest()
	{
		return styleRows.size();
	}

	List<WeaponStyle> rankedStylesForTest()
	{
		List<WeaponStyle> out = new ArrayList<>(styleRows.size());
		for (StyleRow row : styleRows)
		{
			out.add(row.style);
		}
		return out;
	}

	void clickStyleRowForTest(int index)
	{
		selectStyle(styleRows.get(index).style);
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

	/** A {@link WeaponStyle} paired with its computed DPS ({@code null} before a target is picked). */
	private static final class Ranked
	{
		private final WeaponStyle style;
		private final DpsResult result;

		private Ranked(WeaponStyle style, DpsResult result)
		{
			this.style = style;
			this.result = result;
		}
	}

	/**
	 * One clickable row in the ranked attack-style list: a type icon + the
	 * style's name and damage type on the left, its DPS on the right, a leading
	 * star for the best. Clicking locks the readout to this style.
	 */
	private final class StyleRow extends JPanel
	{
		private final WeaponStyle style;
		private final Border selectedBorder = BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE);
		private final Border unselectedBorder = BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR);

		private StyleRow(WeaponStyle style, DpsResult result, boolean best)
		{
			super(new BorderLayout(4, 0));
			this.style = style;
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setBorder(BorderFactory.createCompoundBorder(unselectedBorder,
				BorderFactory.createEmptyBorder(2, 3, 2, 4)));

			JLabel name = new JLabel((best ? "★ " : "") + style.name() + "  ·  " + typeLabel(style.type()));
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(best ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
			ImageIcon icon = typeIcons[style.type().ordinal()];
			if (icon != null)
			{
				name.setIcon(icon);
				name.setIconTextGap(4);
			}

			JLabel dps = new JLabel(result == null ? "—" : String.format(Locale.ROOT, "%.2f", result.dps()));
			dps.setFont(FontManager.getRunescapeSmallFont());
			dps.setForeground(best ? ColorScheme.BRAND_ORANGE : java.awt.Color.WHITE);
			dps.setToolTipText("DPS with this attack style");

			add(name, BorderLayout.CENTER);
			add(dps, BorderLayout.EAST);

			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					selectStyle(StyleRow.this.style);
				}
			});
		}

		private void setSelected(boolean selected)
		{
			setBorder(BorderFactory.createCompoundBorder(selected ? selectedBorder : unselectedBorder,
				BorderFactory.createEmptyBorder(2, 3, 2, 4)));
			setBackground(selected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		}
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
