package com.ospulse.ui.sections;

import com.ospulse.combat.AttackStyleIcons;
import com.ospulse.combat.CombatIcons;
import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.OffensivePrayer;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.PoweredStaff;
import com.ospulse.combat.Spell;
import com.ospulse.combat.Stance;
import com.ospulse.combat.WeaponCategory;
import com.ospulse.combat.WeaponCategoryRepository;
import com.ospulse.combat.WeaponStyle;
import com.ospulse.combat.optimizer.GearOptimizer;
import com.ospulse.combat.optimizer.LoadoutOverride;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.model.ItemStack;
import com.ospulse.session.GearMapper;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.wealth.WealthSnapshot;

import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.AbstractListModel;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * <p><b>Magic weapons get a magic-first view:</b> when the equipped weapon is
 * a staff/wand/powered staff the melee/ranged rows are dropped entirely and
 * replaced by spellbook tabs (Standard/Ancient/Lunar/Arceuus) over that book's
 * offensive {@link Spell}s, each computed through the real engine (5-tick
 * autocast) and ranked by DPS — the best is starred and auto-selected, and a
 * manual spell click locks the readout until the weapon or target changes. A
 * worn powered staff (which cannot autocast) instead ranks its built-in-spell
 * combat options, with the max hit derived from the boosted Magic level. Both
 * variants surface a primary (auto-selected best) / secondary (next-best DPS)
 * cast readout. Spell icons load via the async, EDT-safe
 * {@code SpriteManager.getSpriteAsync}.
 *
 * <p>The style-aware prayer/potion indicator icons (melee→Piety/Chivalry/
 * Ultimate-or-Superhuman-Strength, ranged→Rigour/Eagle-Eye/Hawk-Eye,
 * magic→Augury/Mystic-Might/Mystic-Lore, each degrading by the player's
 * Prayer level; per-style potion icons incl. imbued heart) sit next to the
 * boost toggles — see {@link #prayerIconLabel} / {@link #potionIconLabel},
 * driven by {@link CombatIcons} (pure mapping over the existing
 * OffensivePrayer/PotionBoosts model) so the icon always matches whichever
 * prayer/potion {@link DpsCalculator} is actually applying for the selected
 * style and toggle state.
 *
 * <p>Deferred to a later phase (see the design spec): live opponent
 * auto-detection, favourites, the current-cast spell as the secondary readout
 * (the snapshot does not expose it yet), and gear upgrade suggestions
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
	private static final int ICON_POTION = 12695;  // Super combat potion(4)
	private static final int ICON_PRAYER = 1718;   // Holy symbol
	private static final int ICON_SLAYER = 11864;  // Slayer helmet

	// Item ids backing the style-aware potion indicator (rendered via
	// ItemManager.getImage, same as the boost toggles above) — one per
	// CombatIcons.BoostPotion value.
	private static final int ITEM_SUPER_COMBAT_POTION = 12695;
	private static final int ITEM_RANGING_POTION = 2444;
	private static final int ITEM_IMBUED_HEART = 20724;
	private static final int ITEM_SATURATED_HEART = 27641;
	private static final int ITEM_ANCIENT_BREW = 26340; // Ancient brew(4)

	/** Small side length for the style-aware prayer/potion indicator icons. */
	private static final int INDICATOR_ICON_SIZE = 18;

	/** Side length for the attack-style-row and spell-row icons. */
	private static final int STYLE_ICON_SIZE = 18;

	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;
	private final SpriteManager spriteManager;
	private final WeaponCategoryRepository weaponRepo = WeaponCategoryRepository.getInstance();

	private final JLabel[] slotLabels = new JLabel[GearSnapshot.EQUIPMENT_SLOT_COUNT];
	private final int[] renderedSlotIds = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];

	/** Fixed row height (row content + the 2px inter-row gap) used to size {@link #stylesScroll}'s viewport. */
	private static final int STYLE_ROW_HEIGHT = 22;
	/** How many attack-style/spell rows are visible before {@link #stylesScroll} scrolls (design: ~5 rows). */
	private static final int STYLES_VISIBLE_ROWS = 5;

	private final JLabel stylesHeading;
	private final JPanel bookTabsPanel;
	private final JToggleButton[] bookTabButtons = new JToggleButton[BookTab.values().length];
	private final JPanel stylesPanel;
	private final JScrollPane stylesScroll;
	private final List<StyleRow> styleRows = new ArrayList<>();
	private final List<SpellRow> spellRows = new ArrayList<>();
	/** Per-sprite-id spell icon cache — each spellbook sprite is fetched (async) at most once. */
	private final Map<Integer, ImageIcon> spellIconCache = new HashMap<>();
	/** Per-sprite-id native attack-style icon cache — see {@link #attackStyleIcon}. */
	private final Map<Integer, ImageIcon> attackStyleIconCache = new HashMap<>();
	/** Per-sprite-id prayer icon cache for the style-aware prayer indicator — see {@link #prayerIcon}. */
	private final Map<Integer, ImageIcon> prayerIconCache = new HashMap<>();
	/** Per-item-id potion icon cache for the style-aware potion indicator — see {@link #potionIcon}. */
	private final Map<Integer, ImageIcon> potionIconCache = new HashMap<>();
	private final JPanel primaryRow;
	private final JLabel primaryValue;
	private final JPanel secondaryRow;
	private final JLabel secondaryValue;
	private final javax.swing.JComboBox<Spell> spellPicker;

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
	private final JLabel overkillValue;
	private final JLabel baseEstimateNote;

	private List<Monster> filteredMonsters = Collections.emptyList();
	private Monster selectedMonster;
	private WeaponStyle selectedStyle;
	/** True once the user clicks a specific style row — until then the readout follows the best-DPS style. */
	private boolean userPickedStyle;
	/** Weapon id the current ranking/selection was built for; a change re-defaults to the best style. */
	private int lastRankedWeaponId = Integer.MIN_VALUE;
	/** The effective weapon's {@link WeaponCategory}, refreshed once per {@link #rankAndRender} — feeds {@link StyleRow}'s native icon lookup ({@link AttackStyleIcons}). */
	private WeaponCategory currentWeaponCategory;
	/** True while the equipped weapon routes to the magic-first (spellbook) view. */
	private boolean magicView;
	/** The spellbook tab currently selected in the magic view. */
	private BookTab selectedBook = BookTab.STANDARD;
	/** The spell driving the readout in the magic view ({@code null} on Lunar/Arceuus or a powered staff). */
	private Spell selectedSpell;
	/** True once the user clicks a specific spell row — until then the readout follows the best-DPS spell. */
	private boolean userPickedSpell;
	/** Target the current spell ranking/lock was built for; a change re-defaults to the best spell. */
	private Monster lastRankedTarget;
	/** Stance carrier for spell computes: the weapon's own magic combat option (e.g. "Spell"/STANDARD). */
	private WeaponStyle magicCastStyle;
	private GearSnapshot lastGear;
	private double lastDps;
	private boolean suppressListEvents;
	/** Last observed "slayer helm / black mask worn" state — drives edge-triggered auto-tick. */
	private boolean lastSlayerHeadgearWorn;
	/**
	 * The magic-style potion variant the potion toggle's right-click swap menu
	 * has picked ({@code null} = follow {@link CombatIcons#bestPotion} as
	 * before — Imbued heart for Magic). Melee/Ranged styles ignore this; only
	 * fed to {@link DpsCalculator} via {@link PlayerCombat#magicPotionVariant()}
	 * when the active style is Magic.
	 */
	private CombatIcons.BoostPotion magicPotionVariant;

	// ---------------------------------------------- Phase 2: what-if overrides
	/**
	 * The current per-slot what-if state (design spec section 2) — NOT part of
	 * the live snapshot, purely UI state. Empty means every slot shows real
	 * worn gear; a non-empty override recomputes the readout from
	 * {@code liveLoadout} &#8746; overrides via {@link WhatIfLoadout}, entirely
	 * off bundled data so no {@code ItemManager} call is needed on the EDT.
	 */
	private LoadoutOverride override = LoadoutOverride.empty();
	/** DPS computed from the live (non-overridden) loadout, refreshed alongside every recompute. */
	private double baselineDps;
	/** The equipment slot ordinal the item-search panel below the grid is currently scoped to, or -1 if closed. */
	private int searchOpenForSlot = -1;
	private final IconTextField itemSearchField;
	private final JScrollPane itemListScroll;
	private final JList<String> itemList;
	private final ItemListModel itemListModel = new ItemListModel();
	private List<EquipmentIndexRepository.Entry> filteredItems = Collections.emptyList();
	private final JButton resetAllButton;
	private final JLabel whatIfLabel;
	private final JLabel whatIfDeltaValue;
	private JPanel whatIfRow;
	private boolean suppressItemListEvents;

	// -------------------------------------------- Phase 3: optimiser ("Best Setup")
	/** Owned-item values (worn + top holdings incl. bank), refreshed each {@link #apply}; source for the optimiser's owned pool + GE prices. */
	private WealthSnapshot lastWealth;
	private final javax.swing.JTextField budgetField;
	private final JButton findBestSetupButton;
	private final JLabel optimizerStatusLabel;
	private final JPanel optimizerResultPanel;
	private final JLabel optimizerResultDps;
	private final JLabel optimizerResultDelta;
	private final JLabel optimizerResultSpend;
	private final JLabel optimizerResultDpsPerGp;
	private final JButton applyOptimizerResultButton;
	private GearOptimizer.Result lastOptimizerResult;

	public GearSection(CollapseStore store, ItemManager itemManager, SkillIconManager skillIconManager)
	{
		this(store, itemManager, skillIconManager, null);
	}

	public GearSection(CollapseStore store, ItemManager itemManager, SkillIconManager skillIconManager,
		SpriteManager spriteManager)
	{
		super(KEY, "Gear DPS", store);
		this.itemManager = itemManager;
		this.skillIconManager = skillIconManager;
		this.spriteManager = spriteManager;

		// ------------------------------------------------ worn-gear header
		JLabel heading = PanelWidgets.emptyRowLabel("Live DPS · your worn gear");
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		heading.setToolTipText("Computed live from the equipment you are currently wearing");
		body().add(heading);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		body().add(buildGearGrid());
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		// --------------------------------- Phase 2: what-if item search + reset
		// Clicking a slot cell above opens this search (see toggleItemSearch),
		// scoped to that slot via searchOpenForSlot. Mirrors the monster
		// search's search-field + collapsible-result-list UX for consistency.
		itemSearchField = new IconTextField();
		itemSearchField.setIcon(IconTextField.Icon.SEARCH);
		itemSearchField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemSearchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		itemSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		itemSearchField.setPreferredSize(new Dimension(100, 24));
		itemSearchField.setVisible(false);
		itemSearchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				populateItemList();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				populateItemList();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				populateItemList();
			}
		});
		body().add(itemSearchField);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		itemList = new JList<>(itemListModel);
		itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		itemList.setFont(FontManager.getRunescapeSmallFont());
		itemList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemList.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		itemList.setSelectionBackground(ColorScheme.BRAND_ORANGE);
		itemList.setSelectionForeground(ColorScheme.DARK_GRAY_COLOR);
		itemList.setVisibleRowCount(6);
		itemList.setPrototypeCellValue("Ancient godsword (or)");
		itemList.addListSelectionListener(e ->
		{
			if (suppressItemListEvents || e.getValueIsAdjusting())
			{
				return;
			}
			int index = itemList.getSelectedIndex();
			if (index >= 0 && index < filteredItems.size() && searchOpenForSlot >= 0)
			{
				applyOverride(searchOpenForSlot, filteredItems.get(index).itemId());
				closeItemSearch();
			}
		});
		itemListScroll = new JScrollPane(itemList);
		itemListScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		itemListScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		itemListScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemListScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemListScroll.getPreferredSize().height));
		itemListScroll.setVisible(false);
		body().add(itemListScroll);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		resetAllButton = new JButton("Reset all to worn gear");
		resetAllButton.setFont(FontManager.getRunescapeSmallFont());
		resetAllButton.setFocusPainted(false);
		resetAllButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		resetAllButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		resetAllButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		resetAllButton.setToolTipText("Clear every what-if slot swap and go back to your real worn gear");
		resetAllButton.setVisible(false);
		resetAllButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, resetAllButton.getPreferredSize().height));
		resetAllButton.addActionListener(e -> resetAllOverrides());
		body().add(resetAllButton);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		// --------------------------------------- ranked attack-style picker
		stylesHeading = PanelWidgets.emptyRowLabel("Attack styles (best DPS first)");
		stylesHeading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		stylesHeading.setToolTipText("Your equipped weapon's attack styles, ranked by DPS "
			+ "against the selected target. Click one to lock the readout to it.");
		body().add(stylesHeading);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		// Spellbook tabs — a compact segmented control, only visible in the
		// magic-weapon view (see rankAndRender/renderMagicView).
		bookTabsPanel = new JPanel(new GridLayout(1, BookTab.values().length, 2, 0));
		bookTabsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bookTabsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (BookTab tab : BookTab.values())
		{
			JToggleButton button = bookTabButton(tab);
			bookTabButtons[tab.ordinal()] = button;
			bookTabsPanel.add(button);
		}
		bookTabsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, bookTabsPanel.getPreferredSize().height));
		bookTabsPanel.setVisible(false);
		body().add(bookTabsPanel);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		stylesPanel = new JPanel();
		stylesPanel.setLayout(new BoxLayout(stylesPanel, BoxLayout.Y_AXIS));
		stylesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stylesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Capped-height, scrollable viewport (design: ~5 rows) — the magic view's
		// Standard book alone has ~25 spell rows, which used to render in full and
		// push the whole panel taller; melee/ranged style lists (3-4 rows) simply
		// never need to scroll within the same fixed-height viewport.
		stylesScroll = new JScrollPane(stylesPanel);
		stylesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		stylesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		stylesScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		stylesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		stylesScroll.getVerticalScrollBar().setUnitIncrement(STYLE_ROW_HEIGHT);
		int viewportHeight = STYLE_ROW_HEIGHT * STYLES_VISIBLE_ROWS;
		stylesScroll.setPreferredSize(new Dimension(0, viewportHeight));
		stylesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, viewportHeight));
		body().add(stylesScroll);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		// -------------------------------- primary/secondary cast readout
		// Magic-weapon view only: primary = the auto-selected highest-DPS cast;
		// secondary = the next-best-DPS cast.
		// TODO: current-cast spell when the snapshot exposes it — the secondary
		// should then prefer the spell the player is actually autocasting.
		primaryValue = readoutValueLabel();
		primaryRow = readoutRow("Primary", primaryValue,
			"The auto-selected highest-DPS cast for this weapon and target");
		body().add(primaryRow);
		secondaryValue = readoutValueLabel();
		secondaryRow = readoutRow("Secondary", secondaryValue,
			"The next-best-DPS cast (will prefer your in-game autocast spell once tracked)");
		body().add(secondaryRow);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		// ------------------------------------------------- spell picker
		// Legacy combo, now only shown for the rare NON-magic-view weapon with a
		// magic style (the salamander's Blaze) — magic weapons proper get the
		// ranked spell rows instead. See renderStyleView.
		spellPicker = new javax.swing.JComboBox<>(Spell.values());
		spellPicker.setSelectedItem(Spell.FIRE_SURGE);
		spellPicker.setFont(FontManager.getRunescapeSmallFont());
		spellPicker.setBackground(ColorScheme.DARK_GRAY_COLOR);
		spellPicker.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		spellPicker.setToolTipText("The spell being autocast — its base max hit drives the Magic row");
		spellPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
		spellPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		spellPicker.setVisible(false);
		spellPicker.addActionListener(e -> rankAndRender());
		body().add(spellPicker);
		body().add(Box.createRigidArea(new Dimension(0, 6)));

		// ------------------------------------------------- boost toggles
		// The prayer/potion toggles double as the style-aware indicator: the
		// BUTTON itself shows the auto-selected prayer/potion icon for the
		// current attack style (Melee->Piety/Super combat, Ranged->Rigour/
		// Ranging, Magic->Augury/Imbued heart — degrading by the player's real
		// Prayer level; see CombatIcons), refreshed in updateBoostIndicators
		// (called from updateOutputs) so it always matches whatever prayer/
		// potion DpsCalculator is actually applying. Right-clicking the potion
		// button opens a swap menu for the magic-style potion variant (Imbued
		// heart / Saturated heart / Ancient brew) — see potionVariantPopup.
		JPanel boostRow = new JPanel(new GridLayout(1, 3, 2, 0));
		boostRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		boostRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		bestPotionToggle = iconToggle(ICON_POTION, "Simulate best boosting potion for this attack style");
		bestPrayerToggle = iconToggle(ICON_PRAYER, "Simulate best offensive prayer for this attack style");
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
		bestPotionToggle.setComponentPopupMenu(buildPotionVariantPopup());
		boostRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, boostRow.getPreferredSize().height));
		body().add(boostRow);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

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
		overkillValue = PanelWidgets.statRow(body(), "Overkill");
		overkillValue.setToolTipText("Expected damage wasted on the killing blow (rolled past the target's remaining HP)");

		baseEstimateNote = PanelWidgets.emptyRowLabel("~ approx — an unmodelled effect is present");
		baseEstimateNote.setVisible(false);
		body().add(baseEstimateNote);

		// ------------------------------------- Phase 2: what-if delta readout
		// Only shown once at least one slot is overridden (see updateWhatIfDelta) —
		// compares the current (possibly-overridden) DPS above against the DPS
		// your REAL worn gear would get, so a swap's value is obvious at a glance.
		whatIfDeltaValue = new JLabel("-");
		whatIfDeltaValue.setFont(FontManager.getRunescapeSmallFont());
		whatIfDeltaValue.setHorizontalAlignment(SwingConstants.RIGHT);
		JPanel whatIfRow = new JPanel(new BorderLayout());
		whatIfRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		whatIfRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		whatIfRow.setToolTipText("DPS with your what-if swap(s) vs your real worn gear");
		whatIfLabel = new JLabel("vs worn gear");
		whatIfLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		whatIfLabel.setFont(FontManager.getRunescapeSmallFont());
		whatIfRow.add(whatIfLabel, BorderLayout.WEST);
		whatIfRow.add(whatIfDeltaValue, BorderLayout.EAST);
		whatIfRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, whatIfRow.getPreferredSize().height));
		whatIfRow.setVisible(false);
		this.whatIfRow = whatIfRow;
		body().add(whatIfRow);
		body().add(Box.createRigidArea(new Dimension(0, 6)));

		// ------------------------------------- Phase 3: optimiser ("Best Setup")
		// Owned pool = worn gear (always free) + WealthSnapshot.topHoldings (worn
		// + inventory + bank, already GE-priced client-thread-side by
		// SessionTracker — see lastWealth/apply) filtered to equippable items;
		// budget = extra gp allowed for GE purchases beyond that pool. Search runs
		// off the EDT (SwingWorker) per the design spec's <500ms-in-a-side-panel
		// target — a pruned search over ~3000 items can still take tens of ms.
		JLabel optimizerHeading = PanelWidgets.emptyRowLabel("Best setup for this target");
		optimizerHeading.setForeground(ColorScheme.BRAND_ORANGE);
		optimizerHeading.setToolTipText("Searches your owned gear (worn + bank/inventory) plus anything "
			+ "affordable within the budget below for the highest-DPS loadout against your selected target");
		body().add(optimizerHeading);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		JPanel budgetRow = new JPanel(new BorderLayout(4, 0));
		budgetRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		budgetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel budgetLabel = new JLabel("Budget");
		budgetLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		budgetLabel.setFont(FontManager.getRunescapeSmallFont());
		budgetField = new javax.swing.JTextField("0");
		budgetField.setToolTipText("Extra GP to spend on upgrades, e.g. '10m' or '500k' (blank/0 = owned gear only)");
		budgetField.setFont(FontManager.getRunescapeSmallFont());
		budgetField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		budgetField.setForeground(java.awt.Color.WHITE);
		budgetRow.add(budgetLabel, BorderLayout.WEST);
		budgetRow.add(budgetField, BorderLayout.CENTER);
		budgetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		body().add(budgetRow);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		findBestSetupButton = new JButton("Find best setup");
		findBestSetupButton.setFont(FontManager.getRunescapeSmallFont());
		findBestSetupButton.setFocusPainted(false);
		findBestSetupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		findBestSetupButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		findBestSetupButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		findBestSetupButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, findBestSetupButton.getPreferredSize().height));
		findBestSetupButton.addActionListener(e -> runOptimizer());
		body().add(findBestSetupButton);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		optimizerStatusLabel = PanelWidgets.emptyRowLabel("");
		optimizerStatusLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		optimizerStatusLabel.setVisible(false);
		body().add(optimizerStatusLabel);

		optimizerResultPanel = new JPanel();
		optimizerResultPanel.setLayout(new BoxLayout(optimizerResultPanel, BoxLayout.Y_AXIS));
		optimizerResultPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		optimizerResultPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		optimizerResultDps = PanelWidgets.statRow(optimizerResultPanel, "Best DPS found");
		optimizerResultDelta = PanelWidgets.statRow(optimizerResultPanel, "vs owned-only");
		optimizerResultSpend = PanelWidgets.statRow(optimizerResultPanel, "Total spend");
		optimizerResultDpsPerGp = PanelWidgets.statRow(optimizerResultPanel, "DPS per gp spent");
		applyOptimizerResultButton = new JButton("Apply to readout (what-if)");
		applyOptimizerResultButton.setFont(FontManager.getRunescapeSmallFont());
		applyOptimizerResultButton.setFocusPainted(false);
		applyOptimizerResultButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		applyOptimizerResultButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		applyOptimizerResultButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		applyOptimizerResultButton.setToolTipText("Loads this result into the what-if slots above (real gear unaffected)");
		applyOptimizerResultButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, applyOptimizerResultButton.getPreferredSize().height));
		applyOptimizerResultButton.addActionListener(e -> applyOptimizerResultToOverride());
		optimizerResultPanel.add(applyOptimizerResultButton);
		optimizerResultPanel.setVisible(false);
		body().add(optimizerResultPanel);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

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
			cell.setToolTipText(SLOT_NAMES[slotOrdinal] + " slot (live) — click to try a different item");
			cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			slotLabels[slotOrdinal] = cell;
			renderedSlotIds[slotOrdinal] = Integer.MIN_VALUE;
			int clickedSlot = slotOrdinal;
			cell.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					toggleItemSearch(clickedSlot);
				}
			});
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

	/** Thin orange border marking a slot that currently shows a Phase 2 what-if override, not real worn gear. */
	private static final Border OVERRIDE_BORDER = BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 2);

	/**
	 * Diff-updates the worn-gear icon grid; only touched slots reload their
	 * sprite. Shows the what-if override's item (with an orange border) in any
	 * overridden slot instead of the real worn item — see {@link #override}.
	 */
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
			boolean overridden = override.hasOverride(slot);
			int liveId = ids == null || slot >= ids.length ? -1 : ids[slot];
			int id = overridden ? override.itemIdFor(slot) : liveId;
			cell.setBorder(overridden ? OVERRIDE_BORDER : null);
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
	 * Recomputes the equipped weapon's ranking (DPS-desc against the current
	 * target and boosts), rebuilds the clickable rows, re-selects the best (or
	 * keeps the user's pick if it survives), and refreshes the readout. Routes
	 * a magic weapon to the magic-first spellbook view and everything else to
	 * the classic ranked attack-style view. The single entry point whenever
	 * gear, target, boosts or the spellbook tab change.
	 */
	private void rankAndRender()
	{
		styleRows.clear();
		spellRows.clear();
		stylesPanel.removeAll();

		// effectiveWeaponId() honours a what-if weapon-slot swap (Phase 2) so a
		// hypothetical weapon re-ranks its own real styles/spells exactly like
		// equipping it for real would.
		int weaponId = effectiveWeaponId();
		if (weaponId != lastRankedWeaponId)
		{
			// A different weapon: its styles/spells differ, so any prior manual
			// pick no longer applies — fall back to auto-selecting the best.
			lastRankedWeaponId = weaponId;
			userPickedStyle = false;
			userPickedSpell = false;
		}
		if (selectedMonster != lastRankedTarget)
		{
			// A new target re-ranks the spells, so a manual spell lock only holds
			// until the weapon or target changes (unlike the style lock, which
			// deliberately survives a target change).
			lastRankedTarget = selectedMonster;
			userPickedSpell = false;
		}

		List<WeaponStyle> styles = new ArrayList<>(weaponRepo.stylesForItem(weaponId));
		currentWeaponCategory = weaponRepo.categoryFor(weaponId);
		EquipmentStats effectiveStats = effectiveEquipmentStats();
		PoweredStaff poweredStaff = effectiveStats != null ? effectiveStats.poweredStaff() : PoweredStaff.NONE;
		boolean canRank = lastGear != null && selectedMonster != null && effectiveStats != null;

		magicView = isMagicWeapon(weaponId, styles, poweredStaff);
		if (magicView)
		{
			// Magic weapon: magic-first view — melee/ranged rows are dropped and
			// the legacy spell combo is replaced by the ranked spell rows.
			spellPicker.setVisible(false);
			renderMagicView(styles, poweredStaff, canRank);
		}
		else
		{
			renderStyleView(styles, poweredStaff, canRank);
		}

		stylesPanel.revalidate();
		stylesPanel.repaint();
		stylesScroll.revalidate();
		stylesScroll.repaint();
		body().revalidate();
		body().repaint();

		updateOutputs();
	}

	/**
	 * True when the equipped weapon should get the magic-first view: any worn
	 * powered staff, or a weapon whose combat options include a magic style —
	 * except the salamander, whose "Blaze" is its own built-in attack (it
	 * cannot autocast spellbook spells), so it keeps the classic ranked view.
	 */
	private boolean isMagicWeapon(int weaponId, List<WeaponStyle> styles, PoweredStaff poweredStaff)
	{
		if (poweredStaff.applies())
		{
			return true;
		}
		if (weaponRepo.categoryFor(weaponId) == WeaponCategory.SALAMANDER)
		{
			return false;
		}
		for (WeaponStyle style : styles)
		{
			if (style.type() == CombatStyle.MAGIC)
			{
				return true;
			}
		}
		return false;
	}

	/** The classic ranked attack-style rows (melee/ranged weapons — and the salamander, see isMagicWeapon). */
	private void renderStyleView(List<WeaponStyle> styles, PoweredStaff poweredStaff, boolean canRank)
	{
		stylesHeading.setText("Attack styles (best DPS first)");
		bookTabsPanel.setVisible(false);
		primaryRow.setVisible(false);
		secondaryRow.setVisible(false);

		// The legacy spell combo only remains relevant for a non-magic-view
		// weapon that still carries a magic style (the salamander's Blaze).
		boolean hasMagicStyle = false;
		for (WeaponStyle style : styles)
		{
			hasMagicStyle |= style.type() == CombatStyle.MAGIC;
		}
		spellPicker.setVisible(hasMagicStyle && !poweredStaff.applies());

		renderRankedStyleRows(styles, canRank);
	}

	/**
	 * The magic-first view for a magic weapon: spellbook tabs + ranked spell
	 * rows (autocast weapons), or the weapon's built-in-spell combat options
	 * (powered staves, which cannot autocast), plus the primary/secondary cast
	 * readout in both variants.
	 */
	private void renderMagicView(List<WeaponStyle> styles, PoweredStaff poweredStaff, boolean canRank)
	{
		// The stance carrier for spell computes: the weapon's own magic combat
		// option ("Spell"/STANDARD on a staff, "Accurate" on a powered staff).
		magicCastStyle = null;
		for (WeaponStyle style : styles)
		{
			if (style.type() == CombatStyle.MAGIC)
			{
				magicCastStyle = style;
				break;
			}
		}
		if (magicCastStyle == null)
		{
			// e.g. a powered staff missing from the category data — cast anyway.
			magicCastStyle = new WeaponStyle("Spell", CombatStyle.MAGIC, Stance.STANDARD);
		}

		primaryRow.setVisible(true);
		secondaryRow.setVisible(true);

		if (poweredStaff.applies())
		{
			// Built-in spell: the spellbook is irrelevant (powered staves cannot
			// autocast), so rank the weapon's own magic combat options instead of
			// a spell list — the engine derives the max hit from the Magic level.
			stylesHeading.setText("Magic — built-in spell (best DPS first)");
			bookTabsPanel.setVisible(false);
			selectedSpell = null;

			List<WeaponStyle> magicStyles = new ArrayList<>();
			for (WeaponStyle style : styles)
			{
				if (style.type() == CombatStyle.MAGIC)
				{
					magicStyles.add(style);
				}
			}
			if (magicStyles.isEmpty())
			{
				magicStyles.add(magicCastStyle);
			}
			List<Ranked> ranked = renderRankedStyleRows(magicStyles, canRank);
			setPrimarySecondary(
				readout(ranked.isEmpty() ? null : ranked.get(0).style.name(),
					ranked.isEmpty() ? null : ranked.get(0).result),
				readout(ranked.size() > 1 ? ranked.get(1).style.name() : null,
					ranked.size() > 1 ? ranked.get(1).result : null));
			return;
		}

		// Autocast weapon: spellbook tabs + that book's offensive spells, ranked.
		stylesHeading.setText("Spells (best DPS first)");
		bookTabsPanel.setVisible(true);
		syncBookTabs();
		selectedStyle = magicCastStyle;

		List<Spell> candidates = spellsFor(selectedBook);
		if (candidates.isEmpty())
		{
			// Lunar/Arceuus: render the tab, but there is nothing to rank.
			selectedSpell = null;
			JLabel none = PanelWidgets.emptyRowLabel("No offensive spells on this spellbook");
			none.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			none.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
			stylesPanel.add(none);
			setPrimarySecondary("-", "-");
			return;
		}

		List<RankedSpell> ranked = new ArrayList<>(candidates.size());
		for (Spell spell : candidates)
		{
			ranked.add(new RankedSpell(spell, canRank ? computeSpell(spell) : null));
		}
		if (canRank)
		{
			// Stable sort: equal-DPS spells keep their spellbook order.
			ranked.sort(Comparator.comparingDouble((RankedSpell r) -> r.result.dps()).reversed());
		}

		// Follow the best-DPS spell (top of the ranking) by default; only honour
		// a prior selection when the user explicitly clicked a spell row and the
		// weapon, target and spellbook that produced it are unchanged.
		Spell keep = null;
		if (userPickedSpell)
		{
			for (RankedSpell r : ranked)
			{
				if (r.spell == selectedSpell)
				{
					keep = r.spell;
					break;
				}
			}
		}
		selectedSpell = keep != null ? keep : ranked.get(0).spell;

		for (int i = 0; i < ranked.size(); i++)
		{
			RankedSpell r = ranked.get(i);
			boolean best = canRank && i == 0;
			SpellRow row = new SpellRow(r.spell, r.result, best);
			spellRows.add(row);
			stylesPanel.add(row);
			stylesPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		}
		highlightSelectedSpellRow();

		// Primary = the auto-selected best; secondary = the next-best DPS.
		// TODO: current-cast spell when the snapshot exposes it — the secondary
		// should then prefer the spell actually being autocast in game.
		setPrimarySecondary(
			readout(ranked.get(0).spell.displayName(), ranked.get(0).result),
			ranked.size() > 1 ? readout(ranked.get(1).spell.displayName(), ranked.get(1).result) : "-");
	}

	/**
	 * Computes, ranks (DPS-desc) and renders one clickable row per style,
	 * re-selecting the best or keeping a surviving user pick. Shared by the
	 * classic style view and the powered-staff magic view.
	 */
	private List<Ranked> renderRankedStyleRows(List<WeaponStyle> styles, boolean canRank)
	{
		// Compute each style's DPS (null when we cannot yet), then rank desc.
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
		return ranked;
	}

	/** The offensive spells of a spellbook tab (empty for Lunar/Arceuus — OSRS has no offensive nukes there). */
	private static List<Spell> spellsFor(BookTab tab)
	{
		if (tab.book() == null)
		{
			return Collections.emptyList();
		}
		List<Spell> spells = new ArrayList<>();
		for (Spell spell : Spell.values())
		{
			if (spell.book() == tab.book())
			{
				spells.add(spell);
			}
		}
		return spells;
	}

	/** "Name  ·  1.23" (or just the name pre-target, or "-" when absent) for the primary/secondary readout. */
	private static String readout(String name, DpsResult result)
	{
		if (name == null)
		{
			return "-";
		}
		return result == null ? name : name + "  ·  " + String.format(Locale.ROOT, "%.2f", result.dps());
	}

	private void setPrimarySecondary(String primary, String secondary)
	{
		primaryValue.setText(primary);
		secondaryValue.setText(secondary);
	}

	// ------------------------------------------------- magic view widgets

	/** One tab of the spellbook segmented control; selection is driven by {@link #syncBookTabs}. */
	private JToggleButton bookTabButton(BookTab tab)
	{
		JToggleButton button = new JToggleButton(tab.label());
		button.setToolTipText(tab.label() + " spellbook");
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setMargin(new Insets(2, 1, 2, 1));

		Border selectedBorder = BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE);
		Border unselectedBorder = BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR);
		// Restyle on ANY selection change (user click or the programmatic
		// setSelected from syncBookTabs) — an ItemListener catches both.
		Runnable restyle = () ->
		{
			button.setBorder(button.isSelected() ? selectedBorder : unselectedBorder);
			button.setBackground(button.isSelected()
				? ColorScheme.MEDIUM_GRAY_COLOR
				: ColorScheme.DARKER_GRAY_COLOR);
			button.setForeground(button.isSelected()
				? ColorScheme.BRAND_ORANGE
				: ColorScheme.LIGHT_GRAY_COLOR);
		};
		restyle.run();
		button.addItemListener(e -> restyle.run());
		// Book-switch logic only on a USER click (an ActionListener does not fire
		// on programmatic setSelected, so syncBookTabs cannot recurse into here).
		button.addActionListener(e ->
		{
			if (selectedBook != tab)
			{
				selectedBook = tab;
				// A new book is a new ranking — follow its best spell again.
				userPickedSpell = false;
				rankAndRender();
			}
			else
			{
				// Clicking the active tab must not toggle it off.
				syncBookTabs();
			}
		});
		return button;
	}

	/** Re-asserts each tab button's selected state from {@link #selectedBook}. */
	private void syncBookTabs()
	{
		for (BookTab tab : BookTab.values())
		{
			JToggleButton button = bookTabButtons[tab.ordinal()];
			if (button != null && button.isSelected() != (tab == selectedBook))
			{
				button.setSelected(tab == selectedBook);
			}
		}
	}

	/** Right-aligned white value label for the primary/secondary cast readout. */
	private static JLabel readoutValueLabel()
	{
		JLabel value = new JLabel("-");
		value.setForeground(java.awt.Color.WHITE);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		return value;
	}

	/** "Label ......... value" row for the primary/secondary cast readout; starts hidden (magic view only). */
	private static JPanel readoutRow(String labelText, JLabel value, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setToolTipText(tooltip);

		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());

		row.add(label, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		row.setVisible(false);
		return row;
	}

	/** Applies the selected-row border/background to whichever spell row matches {@link #selectedSpell}. */
	private void highlightSelectedSpellRow()
	{
		for (SpellRow row : spellRows)
		{
			row.setSelected(row.spell == selectedSpell);
		}
	}

	/** User clicked a spell row: lock the readout to it (ranking order is unchanged). */
	private void selectSpell(Spell spell)
	{
		selectedSpell = spell;
		userPickedSpell = true;
		highlightSelectedSpellRow();
		updateOutputs();
	}

	/**
	 * DPS for one autocast spell against the current target + gear + toggles
	 * (cast via the weapon's own magic combat option, see
	 * {@link #magicCastStyle}), or {@code null} if not computable.
	 */
	private DpsResult computeSpell(Spell spell)
	{
		EquipmentStats gearStats = effectiveEquipmentStats();
		if (lastGear == null || selectedMonster == null || gearStats == null || spell == null)
		{
			return null;
		}
		Stance stance = magicCastStyle != null ? magicCastStyle.stance() : Stance.STANDARD;
		PlayerCombat player = GearMapper.toPlayerCombat(lastGear, stance,
			bestPotionToggle.isSelected(), bestPrayerToggle.isSelected(), onSlayerTaskToggle.isSelected(),
			magicPotionVariant);
		return DpsCalculator.compute(gearStats, player, CombatStyle.MAGIC, selectedMonster, spell);
	}

	/**
	 * The spell's spellbook icon, fetched at most once per sprite id via the
	 * async {@code SpriteManager.getSpriteAsync} (callback hops to the EDT).
	 * Returns a transparent placeholder-backed icon immediately so row layout
	 * is stable before the sprite arrives; {@code null} without a SpriteManager
	 * (headless tests).
	 */
	private ImageIcon spellIcon(Spell spell)
	{
		if (spriteManager == null)
		{
			return null;
		}
		return spellIconCache.computeIfAbsent(spell.spriteId(), spriteId ->
		{
			ImageIcon icon = new ImageIcon(
				new BufferedImage(STYLE_ICON_SIZE, STYLE_ICON_SIZE, BufferedImage.TYPE_INT_ARGB));
			spriteManager.getSpriteAsync(spriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					icon.setImage(sprite.getScaledInstance(STYLE_ICON_SIZE, STYLE_ICON_SIZE, Image.SCALE_SMOOTH));
					stylesPanel.repaint();
				}));
			return icon;
		});
	}

	/**
	 * The NATIVE Combat Options icon for one attack-style row — the same sprite
	 * OSRS itself draws on that weapon-type's combat-options button (see
	 * {@link AttackStyleIcons} for the sprite-id table and why it, not
	 * RuneLite's core {@code attackstyles} plugin, is the source). Fetched at
	 * most once per sprite id via the async {@code SpriteManager.getSpriteAsync}
	 * (same pattern as {@link #spellIcon}); {@code null} without a SpriteManager
	 * (headless tests) so callers fall back to the plain text label.
	 */
	private ImageIcon attackStyleIcon(WeaponCategory category, WeaponStyle style)
	{
		if (spriteManager == null || style == null)
		{
			return null;
		}
		int spriteId = AttackStyleIcons.spriteIdFor(category, style);
		return attackStyleIconCache.computeIfAbsent(spriteId, id ->
		{
			ImageIcon icon = new ImageIcon(
				new BufferedImage(STYLE_ICON_SIZE, STYLE_ICON_SIZE, BufferedImage.TYPE_INT_ARGB));
			spriteManager.getSpriteAsync(id, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					icon.setImage(sprite.getScaledInstance(STYLE_ICON_SIZE, STYLE_ICON_SIZE, Image.SCALE_SMOOTH));
					stylesPanel.repaint();
				}));
			return icon;
		});
	}

	/**
	 * {@code net.runelite.api.SpriteID} constant for an {@link OffensivePrayer}'s
	 * prayer-book icon. {@link OffensivePrayer} itself stays RuneLite-free (see
	 * its class javadoc), so this presentation-only mapping lives here.
	 */
	private static int prayerSpriteId(OffensivePrayer prayer)
	{
		switch (prayer)
		{
			case BURST_OF_STRENGTH: return net.runelite.api.SpriteID.PRAYER_BURST_OF_STRENGTH;
			case SUPERHUMAN_STRENGTH: return net.runelite.api.SpriteID.PRAYER_SUPERHUMAN_STRENGTH;
			case ULTIMATE_STRENGTH: return net.runelite.api.SpriteID.PRAYER_ULTIMATE_STRENGTH;
			case CLARITY_OF_THOUGHT: return net.runelite.api.SpriteID.PRAYER_CLARITY_OF_THOUGHT;
			case IMPROVED_REFLEXES: return net.runelite.api.SpriteID.PRAYER_IMPROVED_REFLEXES;
			case INCREDIBLE_REFLEXES: return net.runelite.api.SpriteID.PRAYER_INCREDIBLE_REFLEXES;
			case CHIVALRY: return net.runelite.api.SpriteID.PRAYER_CHIVALRY;
			case PIETY: return net.runelite.api.SpriteID.PRAYER_PIETY;
			case SHARP_EYE: return net.runelite.api.SpriteID.PRAYER_SHARP_EYE;
			case HAWK_EYE: return net.runelite.api.SpriteID.PRAYER_HAWK_EYE;
			case EAGLE_EYE: return net.runelite.api.SpriteID.PRAYER_EAGLE_EYE;
			case DEADEYE: return net.runelite.api.SpriteID.PRAYER_DEADEYE;
			case RIGOUR: return net.runelite.api.SpriteID.PRAYER_RIGOUR;
			case MYSTIC_WILL: return net.runelite.api.SpriteID.PRAYER_MYSTIC_WILL;
			case MYSTIC_LORE: return net.runelite.api.SpriteID.PRAYER_MYSTIC_LORE;
			case MYSTIC_MIGHT: return net.runelite.api.SpriteID.PRAYER_MYSTIC_MIGHT;
			case MYSTIC_VIGOUR: return net.runelite.api.SpriteID.PRAYER_MYSTIC_VIGOUR;
			case AUGURY: return net.runelite.api.SpriteID.PRAYER_AUGURY;
			default: return net.runelite.api.SpriteID.UNKNOWN_PRAYER_ICON;
		}
	}

	/**
	 * The style-aware offensive-prayer icon, fetched at most once per sprite id
	 * via the async, EDT-safe {@code SpriteManager.getSpriteAsync} (same pattern
	 * as {@link #spellIcon}). Returns a transparent placeholder-backed icon
	 * immediately so layout is stable before the sprite arrives; {@code null}
	 * without a SpriteManager (headless tests).
	 *
	 * <p>Prayer sprites are NOT all drawn on the same native canvas size —
	 * newer prayers (e.g. Augury, added long after the original prayer book)
	 * have different baked-in transparent padding than the classic icons, so a
	 * uniform scale-to-{@link #INDICATOR_ICON_SIZE} of the raw sprite makes
	 * some prayers (Augury in particular) render visibly smaller than the
	 * others. {@link #cropToOpaqueBounds} normalizes every sprite to its actual
	 * ink content before scaling so every prayer icon fills the same visual
	 * footprint.
	 */
	private ImageIcon prayerIcon(OffensivePrayer prayer)
	{
		if (spriteManager == null || prayer == null)
		{
			return null;
		}
		int spriteId = prayerSpriteId(prayer);
		return prayerIconCache.computeIfAbsent(spriteId, id ->
		{
			ImageIcon icon = new ImageIcon(
				new BufferedImage(INDICATOR_ICON_SIZE, INDICATOR_ICON_SIZE, BufferedImage.TYPE_INT_ARGB));
			spriteManager.getSpriteAsync(id, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					BufferedImage cropped = cropToOpaqueBounds(sprite);
					icon.setImage(cropped.getScaledInstance(INDICATOR_ICON_SIZE, INDICATOR_ICON_SIZE, Image.SCALE_SMOOTH));
					bestPrayerToggle.repaint();
				}));
			return icon;
		});
	}

	/**
	 * Crops {@code source} to the bounding box of its non-fully-transparent
	 * pixels, so sprites with inconsistent baked-in padding (see
	 * {@link #prayerIcon}) all scale to the same visual size afterwards.
	 * Returns {@code source} unchanged if it has no alpha channel or is
	 * entirely transparent (nothing sensible to crop to).
	 */
	private static BufferedImage cropToOpaqueBounds(BufferedImage source)
	{
		int width = source.getWidth();
		int height = source.getHeight();
		if (!source.getColorModel().hasAlpha())
		{
			return source;
		}

		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int alpha = (source.getRGB(x, y) >>> 24) & 0xFF;
				if (alpha != 0)
				{
					if (x < minX)
					{
						minX = x;
					}
					if (x > maxX)
					{
						maxX = x;
					}
					if (y < minY)
					{
						minY = y;
					}
					if (y > maxY)
					{
						maxY = y;
					}
				}
			}
		}

		if (maxX < minX || maxY < minY)
		{
			// Fully transparent sprite (shouldn't normally happen) — nothing to crop to.
			return source;
		}
		int cropWidth = maxX - minX + 1;
		int cropHeight = maxY - minY + 1;
		if (minX == 0 && minY == 0 && cropWidth == width && cropHeight == height)
		{
			// Already tight — avoid an unnecessary copy.
			return source;
		}
		return source.getSubimage(minX, minY, cropWidth, cropHeight);
	}

	/** Item id backing a {@link CombatIcons.BoostPotion}'s icon (rendered via {@code ItemManager.getImage}). */
	private static int potionItemId(CombatIcons.BoostPotion potion)
	{
		switch (potion)
		{
			case SUPER_COMBAT: return ITEM_SUPER_COMBAT_POTION;
			case RANGING: return ITEM_RANGING_POTION;
			case IMBUED_HEART: return ITEM_IMBUED_HEART;
			case SATURATED_HEART: return ITEM_SATURATED_HEART;
			case ANCIENT_BREW: return ITEM_ANCIENT_BREW;
			default: return -1;
		}
	}

	/**
	 * The style-aware boosting-potion icon, fetched at most once per item id via
	 * the async, EDT-safe {@code ItemManager.getImage(int)} (same pattern as
	 * {@link #iconToggle}). {@code null} without an ItemManager (headless tests).
	 */
	private ImageIcon potionIcon(CombatIcons.BoostPotion potion)
	{
		if (itemManager == null || potion == null)
		{
			return null;
		}
		int itemId = potionItemId(potion);
		return potionIconCache.computeIfAbsent(itemId, id ->
		{
			AsyncBufferedImage image = itemManager.getImage(id);
			ImageIcon icon = new ImageIcon(image);
			image.onLoaded(() ->
			{
				icon.setImage(image);
				bestPotionToggle.repaint();
			});
			return icon;
		});
	}

	/**
	 * The magic potion variant the potion toggle should show/apply for the
	 * current style: the user's right-click swap pick when the style is Magic
	 * (defaulting to Imbued heart if none chosen yet), or {@link CombatIcons#bestPotion}
	 * unchanged for every other style (melee/ranged are not swappable).
	 */
	private CombatIcons.BoostPotion effectivePotionFor(CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return magicPotionVariant != null ? magicPotionVariant : CombatIcons.BoostPotion.IMBUED_HEART;
		}
		return CombatIcons.bestPotion(style);
	}

	/**
	 * Refreshes the prayer/potion boost TOGGLE BUTTONS themselves so each one's
	 * icon always shows the SAME prayer/potion {@link DpsCalculator} is actually
	 * applying for {@code style}: the ladder-topped prayer for the player's real
	 * Prayer level (or the calculator's hardcoded top-tier prayer when the
	 * "best prayer" toggle is on), and the style's boosting potion (the user's
	 * right-click swap pick for Magic — see {@link #effectivePotionFor}).
	 * Falls back to the generic default icon with no target/gear selected yet
	 * (style is {@code null}) so the buttons are never blank.
	 */
	private void updateBoostIndicators(CombatStyle style)
	{
		if (style == null)
		{
			return;
		}

		OffensivePrayer prayer = CombatIcons.bestOffensivePrayer(
			style, lastGear != null ? lastGear.basePrayer() : 0, bestPrayerToggle.isSelected());
		if (prayer != null)
		{
			bestPrayerToggle.setIcon(prayerIcon(prayer));
			bestPrayerToggle.setToolTipText("Offensive prayer applied: " + displayName(prayer));
		}

		CombatIcons.BoostPotion potion = effectivePotionFor(style);
		if (potion != null)
		{
			bestPotionToggle.setIcon(potionIcon(potion));
			bestPotionToggle.setToolTipText("Boosting potion applied: " + displayName(potion)
				+ (style == CombatStyle.MAGIC ? " (right-click to swap)" : ""));
		}
	}

	/** "Piety" from {@code PIETY}, etc — title-cased enum name for tooltips. */
	private static String displayName(Enum<?> value)
	{
		String[] words = value.name().split("_");
		StringBuilder sb = new StringBuilder();
		for (String word : words)
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
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

	/**
	 * DPS for one style against the current target + gear (overrides applied,
	 * see {@link #effectiveEquipmentStats}) + toggles, or {@code null} if not
	 * computable.
	 */
	private DpsResult computeFor(WeaponStyle style)
	{
		return computeAgainst(effectiveEquipmentStats(), style);
	}

	/**
	 * DPS for one style against the current target + toggles, using the given
	 * {@link EquipmentStats} rather than always the live/overridden one — lets
	 * {@link #updateWhatIfDelta} compute the SAME style against real worn gear
	 * for the baseline comparison. {@code null} if not computable.
	 */
	private DpsResult computeAgainst(EquipmentStats gearStats, WeaponStyle style)
	{
		if (lastGear == null || selectedMonster == null || gearStats == null || style == null)
		{
			return null;
		}
		PlayerCombat player = GearMapper.toPlayerCombat(lastGear, style.stance(),
			bestPotionToggle.isSelected(), bestPrayerToggle.isSelected(), onSlayerTaskToggle.isSelected(),
			magicPotionVariant);
		if (style.type() == CombatStyle.MAGIC)
		{
			// Spell-aware path: a worn powered staff wins automatically; otherwise
			// the magic view's selected spell (or, outside the magic view, the
			// legacy picker's spell) is autocast at 5 ticks.
			Spell spell = magicView ? selectedSpell : currentSpell();
			if (spell == null && !gearStats.poweredStaff().applies())
			{
				// Magic view on a spell-less book (Lunar/Arceuus): nothing to cast.
				return null;
			}
			return DpsCalculator.compute(gearStats, player, CombatStyle.MAGIC, selectedMonster, spell);
		}
		return DpsCalculator.compute(gearStats, player, style.type(), selectedMonster, 0);
	}

	/** The spell currently picked in the selector (never {@code null} — the model always has a selection). */
	private Spell currentSpell()
	{
		Spell picked = (Spell) spellPicker.getSelectedItem();
		return picked != null ? picked : Spell.FIRE_SURGE;
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

	// ------------------------------------------------- Phase 2: what-if swaps

	/**
	 * Opens the item search scoped to {@code slotOrdinal} (closing it if that
	 * same slot's search was already open — a second click toggles it shut,
	 * matching the monster-list collapse UX).
	 */
	private void toggleItemSearch(int slotOrdinal)
	{
		if (searchOpenForSlot == slotOrdinal)
		{
			closeItemSearch();
			return;
		}
		searchOpenForSlot = slotOrdinal;
		itemSearchField.setText("");
		itemSearchField.setVisible(true);
		itemSearchField.setToolTipText("Search " + SLOT_NAMES[slotOrdinal] + " items to try (what-if — your real gear is unaffected)");
		itemSearchField.requestFocusInWindow();
		populateItemList();
		body().revalidate();
		body().repaint();
	}

	private void closeItemSearch()
	{
		searchOpenForSlot = -1;
		itemSearchField.setVisible(false);
		itemListScroll.setVisible(false);
		body().revalidate();
		body().repaint();
	}

	/** Refilters {@link #searchOpenForSlot}'s candidate items by the search box's text. */
	private void populateItemList()
	{
		if (searchOpenForSlot < 0)
		{
			return;
		}
		filteredItems = EquipmentIndexRepository.getInstance().searchSlot(searchOpenForSlot, itemSearchField.getText());
		suppressItemListEvents = true;
		itemListModel.setItems(filteredItems);
		itemList.clearSelection();
		suppressItemListEvents = false;
		itemListScroll.setVisible(!filteredItems.isEmpty());
		itemListScroll.revalidate();
		body().revalidate();
		body().repaint();
	}

	/**
	 * Sets a what-if override for {@code slotOrdinal} to {@code itemId},
	 * enforcing 2H/shield exclusivity for the weapon and shield slots (see
	 * {@link WhatIfLoadout#equipWeapon}/{@link WhatIfLoadout#equipShield}) and
	 * recomputing the whole readout from the new override.
	 */
	private void applyOverride(int slotOrdinal, int itemId)
	{
		int[] liveIds = lastGear == null ? new int[GearSnapshot.EQUIPMENT_SLOT_COUNT] : lastGear.equippedItemIds();
		if (slotOrdinal == WhatIfLoadout.WEAPON_SLOT)
		{
			override = WhatIfLoadout.equipWeapon(override, itemId);
		}
		else if (slotOrdinal == WhatIfLoadout.SHIELD_SLOT)
		{
			override = WhatIfLoadout.equipShield(override, liveIds, itemId);
		}
		else
		{
			override = override.withSlot(slotOrdinal, itemId);
		}
		// A weapon swap changes the available attack styles/spells entirely —
		// re-rank exactly like a real weapon change (rankAndRender already
		// re-defaults the style/spell selection whenever the effective weapon
		// id it sees changes, since it reads lastGear.itemIdAt(WEAPON_SLOT) —
		// see effectiveWeaponId()).
		updateGearGrid(lastGear);
		rankAndRender();
	}

	/**
	 * Clears every what-if override AND any optimiser-applied preview/highlight
	 * — the single "undo everything, go back to what I'm actually wearing"
	 * action (design spec section 3's "Clear preview"/"Revert" reuses this same
	 * method). Previously this only cleared {@link #override}, leaving the
	 * optimiser result panel (and its stale "Apply to readout" button) visible
	 * — from the user's perspective nothing appeared to happen, since the
	 * lingering panel looked identical to a still-applied preview. Also drops
	 * any manual style/spell lock so the readout re-defaults to the best style
	 * for whatever weapon the player is ACTUALLY wearing, rather than
	 * potentially "keeping" a style selection that happens to satisfy
	 * {@link WeaponStyle#equals} (type+stance only) on the real weapon too.
	 */
	private void resetAllOverrides()
	{
		override = LoadoutOverride.empty();
		lastOptimizerResult = null;
		optimizerResultPanel.setVisible(false);
		optimizerStatusLabel.setVisible(false);
		userPickedStyle = false;
		userPickedSpell = false;
		closeItemSearch();
		updateGearGrid(lastGear);
		rankAndRender();
	}

	/** Clears a single slot's override (the gear-grid cell's right-click / the future per-slot reset affordance). */
	private void resetSlotOverride(int slotOrdinal)
	{
		override = override.withoutSlot(slotOrdinal);
		updateGearGrid(lastGear);
		rankAndRender();
	}

	/**
	 * The weapon item id the readout should actually use: the override's
	 * weapon if set, else the live worn weapon. {@link #rankAndRender} keys its
	 * "did the weapon change" re-ranking logic off this instead of the raw live
	 * id, so a what-if weapon swap re-ranks styles/spells exactly like a real
	 * weapon change would.
	 */
	private int effectiveWeaponId()
	{
		if (override.hasOverride(WhatIfLoadout.WEAPON_SLOT))
		{
			return override.itemIdFor(WhatIfLoadout.WEAPON_SLOT);
		}
		return lastGear == null ? -1 : lastGear.itemIdAt(WEAPON_SLOT);
	}

	/**
	 * The {@link EquipmentStats} the readout should compute against: the
	 * override-applied what-if loadout when any slot is overridden, otherwise
	 * the live snapshot's precomputed stats unchanged (zero behaviour change
	 * with no overrides active).
	 */
	private EquipmentStats effectiveEquipmentStats()
	{
		if (lastGear == null)
		{
			return null;
		}
		if (override.isEmpty())
		{
			return lastGear.equipmentStats();
		}
		return WhatIfLoadout.buildEquipmentStats(lastGear.equippedItemIds(), override);
	}

	/**
	 * Refreshes the what-if delta row: hidden with no overrides active: shown
	 * comparing the current (possibly-overridden) DPS against what the SAME
	 * style/spell selection would do on real worn gear. Guards a style/spell
	 * that only exists on the what-if weapon (e.g. switched from melee to a
	 * bow) by falling back to "-" rather than a misleading comparison.
	 */
	private void updateWhatIfDelta()
	{
		if (override.isEmpty() || lastGear == null || selectedMonster == null)
		{
			whatIfRow.setVisible(false);
			resetAllButton.setVisible(!override.isEmpty());
			return;
		}
		resetAllButton.setVisible(true);

		DpsResult baseline = computeAgainst(lastGear.equipmentStats(), selectedStyle);
		if (baseline == null)
		{
			whatIfRow.setVisible(false);
			return;
		}
		baselineDps = baseline.dps();
		double delta = lastDps - baselineDps;
		String arrow = delta > 1e-9 ? " ▲" : delta < -1e-9 ? " ▼" : "";
		java.awt.Color color = delta > 1e-9 ? new java.awt.Color(0x3D, 0xC7, 0x54)
			: delta < -1e-9 ? new java.awt.Color(0xE0, 0x5A, 0x5A) : java.awt.Color.WHITE;
		whatIfDeltaValue.setForeground(color);
		whatIfDeltaValue.setText(String.format(Locale.ROOT, "%.2f -> %.2f%s", baselineDps, lastDps, arrow));
		whatIfRow.setVisible(true);
	}

	// ------------------------------------------------- Phase 3: optimiser

	/**
	 * Parses a budget string with an optional trailing k/m unit (design spec:
	 * "numeric + K/M unit toggle" — a suffix is a lighter-weight equivalent for
	 * a text field than a separate toggle button and reads naturally,
	 * matching how players already type prices in-game, e.g. GE search).
	 * Blank/unparseable input is treated as 0 (owned-only search) rather than
	 * rejected, since a budget field is not a validated form control here.
	 */
	static long parseBudget(String text)
	{
		if (text == null)
		{
			return 0L;
		}
		String trimmed = text.trim().toLowerCase(Locale.ROOT).replace(",", "");
		if (trimmed.isEmpty())
		{
			return 0L;
		}
		double multiplier = 1.0;
		if (trimmed.endsWith("m"))
		{
			multiplier = 1_000_000.0;
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		else if (trimmed.endsWith("k"))
		{
			multiplier = 1_000.0;
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		try
		{
			double value = Double.parseDouble(trimmed.trim());
			return value <= 0 ? 0L : Math.round(value * multiplier);
		}
		catch (NumberFormatException e)
		{
			return 0L;
		}
	}

	/**
	 * The owned-item pool + GE prices for the optimiser: every worn item
	 * (always owned, price irrelevant) plus {@link WealthSnapshot#getTopHoldings()}
	 * (worn + inventory + bank — see {@link #lastWealth}) filtered to items the
	 * {@link EquipmentIndexRepository} actually indexes (so the optimiser never
	 * tries to equip e.g. raw materials). Built fresh per search so it always
	 * reflects the latest snapshot.
	 */
	private java.util.Map<Integer, Long> ownedPriceMap()
	{
		java.util.Map<Integer, Long> prices = new HashMap<>();
		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		if (lastGear != null)
		{
			for (int id : lastGear.equippedItemIds())
			{
				if (id > 0 && index.entryFor(id) != null)
				{
					prices.put(id, 0L);
				}
			}
		}
		if (lastWealth != null)
		{
			for (ItemStack stack : lastWealth.getTopHoldings())
			{
				if (index.entryFor(stack.getId()) != null)
				{
					prices.merge(stack.getId(), Math.max(0L, stack.getUnitValue()), Math::min);
				}
			}
		}
		return prices;
	}

	/** Runs {@link GearOptimizer} off the EDT and publishes the result back via {@link #onOptimizerResult}. */
	private void runOptimizer()
	{
		if (lastGear == null || selectedMonster == null)
		{
			optimizerStatusLabel.setText("Pick a target above first");
			optimizerStatusLabel.setVisible(true);
			optimizerResultPanel.setVisible(false);
			return;
		}

		findBestSetupButton.setEnabled(false);
		optimizerStatusLabel.setText("Searching...");
		optimizerStatusLabel.setVisible(true);
		optimizerResultPanel.setVisible(false);

		long budget = parseBudget(budgetField.getText());
		java.util.Map<Integer, Long> ownedPrices = ownedPriceMap();
		int[] liveIds = lastGear.equippedItemIds();
		Monster target = selectedMonster;
		PlayerCombat.Builder template = PlayerCombat.builder()
			.attack(lastGear.baseAttack(), lastGear.boostedAttack())
			.strength(lastGear.baseStrength(), lastGear.boostedStrength())
			.defence(lastGear.baseDefence(), lastGear.boostedDefence())
			.ranged(lastGear.baseRanged(), lastGear.boostedRanged())
			.magic(lastGear.baseMagic(), lastGear.boostedMagic())
			.prayer(lastGear.basePrayer(), lastGear.boostedPrayer())
			.hitpoints(lastGear.baseHitpoints(), lastGear.boostedHitpoints())
			.activePrayers(lastGear.activePrayers())
			.assumeBestPotion(bestPotionToggle.isSelected())
			.assumeBestPrayer(bestPrayerToggle.isSelected())
			.onSlayerTask(onSlayerTaskToggle.isSelected())
			.magicPotionVariant(magicPotionVariant);

		GearOptimizer.Request request = GearOptimizer.Request
			.builder(liveIds, target, template)
			.budget(budget)
			.owned(ownedPrices.keySet())
			.priceSource(id -> ownedPrices.getOrDefault(id, Long.MAX_VALUE))
			.build();

		new javax.swing.SwingWorker<GearOptimizer.Result, Void>()
		{
			@Override
			protected GearOptimizer.Result doInBackground()
			{
				return GearOptimizer.optimize(request);
			}

			@Override
			protected void done()
			{
				try
				{
					onOptimizerResult(get());
				}
				catch (java.util.concurrent.ExecutionException | InterruptedException e)
				{
					optimizerStatusLabel.setText("Search failed");
					findBestSetupButton.setEnabled(true);
				}
			}
		}.execute();
	}

	/** Renders a completed {@link GearOptimizer.Result} — called on the EDT by the {@code SwingWorker} above. */
	private void onOptimizerResult(GearOptimizer.Result result)
	{
		findBestSetupButton.setEnabled(true);
		optimizerStatusLabel.setVisible(false);
		lastOptimizerResult = result;

		optimizerResultDps.setText(String.format(Locale.ROOT, "%.2f", result.dps().dps()));
		double delta = result.deltaDps();
		String arrow = delta > 1e-9 ? " ▲" : delta < -1e-9 ? " ▼" : "";
		optimizerResultDelta.setText(String.format(Locale.ROOT, "%+.2f%s", delta, arrow));
		optimizerResultSpend.setText(formatGp(result.totalSpend()));
		optimizerResultDpsPerGp.setText(result.totalSpend() > 0
			? String.format(Locale.ROOT, "%.6f", result.dpsPerGp())
			: "-");
		optimizerResultPanel.setVisible(true);
		optimizerResultPanel.revalidate();
		body().revalidate();
		body().repaint();
	}

	/** "1.2m" / "350k" / "0" — compact gp formatting for the spend readout. */
	private static String formatGp(long gp)
	{
		if (gp >= 1_000_000)
		{
			return String.format(Locale.ROOT, "%.1fm", gp / 1_000_000.0);
		}
		if (gp >= 1_000)
		{
			return String.format(Locale.ROOT, "%.0fk", gp / 1000.0);
		}
		return String.valueOf(gp);
	}

	/**
	 * Loads the last optimizer result into the what-if overrides (design
	 * spec's "apply to readout" handoff) — every slot the result touches
	 * becomes a {@link LoadoutOverride}, so the existing Phase 2 readout
	 * (styles/spells ranking, DPS/TTK/etc, delta-vs-worn-gear row) picks it up
	 * unchanged. Real gear is never touched.
	 */
	private void applyOptimizerResultToOverride()
	{
		if (lastOptimizerResult == null)
		{
			return;
		}
		LoadoutOverride next = LoadoutOverride.empty();
		for (GearOptimizer.SlotChoice choice : lastOptimizerResult.loadout())
		{
			next = next.withSlot(choice.slotOrdinal(), choice.itemId());
		}
		override = next;
		updateGearGrid(lastGear);
		rankAndRender();
	}

	// ------------------------------------------------------------- compute

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		lastGear = snapshot.getGear();
		lastWealth = snapshot.getWealth();
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
		overkillValue.setText(String.format(Locale.ROOT, "%.1f", result.overkillPerKill()));
		baseEstimateNote.setVisible(result.baseEstimate());
		updateBoostIndicators(selectedStyle != null ? selectedStyle.type() : null);
		updateWhatIfDelta();

		refreshSummary();
	}

	private void clearOutputs()
	{
		maxHitValue.setText("-");
		accuracyValue.setText("-");
		avgHitValue.setText("-");
		dpsValue.setText("-");
		ttkValue.setText("-");
		overkillValue.setText("-");
		baseEstimateNote.setVisible(false);
		lastDps = 0.0;
		updateBoostIndicators(null);
		whatIfRow.setVisible(false);
		resetAllButton.setVisible(!override.isEmpty());
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

	/**
	 * The potion toggle's right-click swap menu: one item per magic-style
	 * potion variant ({@link CombatIcons#MAGIC_POTION_VARIANTS} — Saturated
	 * heart / Imbued heart / Ancient brew), each setting {@link #magicPotionVariant}
	 * and re-ranking so the swap immediately feeds {@link DpsCalculator} via
	 * {@link PlayerCombat#magicPotionVariant()}. Melee/Ranged prayer/potion
	 * picks are calculator-fixed "best" and have nothing to swap, so only the
	 * potion toggle gets this menu (the prayer toggle does not).
	 */
	private javax.swing.JPopupMenu buildPotionVariantPopup()
	{
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		for (CombatIcons.BoostPotion variant : CombatIcons.MAGIC_POTION_VARIANTS)
		{
			javax.swing.JMenuItem item = new javax.swing.JMenuItem(displayName(variant));
			item.addActionListener(e ->
			{
				magicPotionVariant = variant;
				rankAndRender();
			});
			menu.add(item);
		}
		return menu;
	}

	// ------------------------------------------------- test seams (package)

	IconTextField searchFieldForTest()
	{
		return monsterSearchField;
	}

	// --------------------------------------- Phase 2 what-if test seams

	/** Simulates a click on a gear-grid slot cell, opening/closing its item search. */
	void clickSlotForTest(int slotOrdinal)
	{
		toggleItemSearch(slotOrdinal);
	}

	IconTextField itemSearchFieldForTest()
	{
		return itemSearchField;
	}

	int searchOpenForSlotForTest()
	{
		return searchOpenForSlot;
	}

	List<EquipmentIndexRepository.Entry> filteredItemsForTest()
	{
		return filteredItems;
	}

	/** Simulates picking the item at {@code index} in the currently-open item search result list. */
	void pickItemForTest(int index)
	{
		applyOverride(searchOpenForSlot, filteredItems.get(index).itemId());
		closeItemSearch();
	}

	void clickResetAllForTest()
	{
		resetAllOverrides();
	}

	LoadoutOverride overrideForTest()
	{
		return override;
	}

	boolean whatIfRowVisibleForTest()
	{
		return whatIfRow.isVisible();
	}

	String whatIfDeltaTextForTest()
	{
		return whatIfDeltaValue.getText();
	}

	double baselineDpsForTest()
	{
		return baselineDps;
	}

	int renderedSlotIdForTest(int slotOrdinal)
	{
		return renderedSlotIds[slotOrdinal];
	}

	// --------------------------------------- Phase 3 optimiser test seams

	void setBudgetTextForTest(String text)
	{
		budgetField.setText(text);
	}

	/**
	 * Runs the optimizer SYNCHRONOUSLY for tests (bypassing the real
	 * {@code SwingWorker}, whose background thread + {@code invokeLater}
	 * hand-off is awkward to await deterministically in a headless test) by
	 * building the same {@link GearOptimizer.Request} {@link #runOptimizer}
	 * would and calling {@link #onOptimizerResult} directly on the calling
	 * (EDT) thread.
	 */
	void runOptimizerSyncForTest()
	{
		long budget = parseBudget(budgetField.getText());
		java.util.Map<Integer, Long> ownedPrices = ownedPriceMap();
		int[] liveIds = lastGear.equippedItemIds();
		PlayerCombat.Builder template = PlayerCombat.builder()
			.attack(lastGear.baseAttack(), lastGear.boostedAttack())
			.strength(lastGear.baseStrength(), lastGear.boostedStrength())
			.defence(lastGear.baseDefence(), lastGear.boostedDefence())
			.ranged(lastGear.baseRanged(), lastGear.boostedRanged())
			.magic(lastGear.baseMagic(), lastGear.boostedMagic())
			.prayer(lastGear.basePrayer(), lastGear.boostedPrayer())
			.hitpoints(lastGear.baseHitpoints(), lastGear.boostedHitpoints())
			.activePrayers(lastGear.activePrayers())
			.assumeBestPotion(bestPotionToggle.isSelected())
			.assumeBestPrayer(bestPrayerToggle.isSelected())
			.onSlayerTask(onSlayerTaskToggle.isSelected())
			.magicPotionVariant(magicPotionVariant);
		GearOptimizer.Request request = GearOptimizer.Request
			.builder(liveIds, selectedMonster, template)
			.budget(budget)
			.owned(ownedPrices.keySet())
			.priceSource(id -> ownedPrices.getOrDefault(id, Long.MAX_VALUE))
			.build();
		onOptimizerResult(GearOptimizer.optimize(request));
	}

	GearOptimizer.Result lastOptimizerResultForTest()
	{
		return lastOptimizerResult;
	}

	boolean optimizerResultVisibleForTest()
	{
		return optimizerResultPanel.isVisible();
	}

	String optimizerResultDpsTextForTest()
	{
		return optimizerResultDps.getText();
	}

	String optimizerResultSpendTextForTest()
	{
		return optimizerResultSpend.getText();
	}

	void clickApplyOptimizerResultForTest()
	{
		applyOptimizerResultToOverride();
	}

	JButton findBestSetupButtonForTest()
	{
		return findBestSetupButton;
	}

	String optimizerStatusTextForTest()
	{
		return optimizerStatusLabel.getText();
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

	JToggleButton bestPrayerToggleForTest()
	{
		return bestPrayerToggle;
	}

	/** Simulates right-clicking the potion toggle and picking a magic potion variant from the swap menu. */
	void pickMagicPotionVariantForTest(CombatIcons.BoostPotion variant)
	{
		magicPotionVariant = variant;
		rankAndRender();
	}

	CombatIcons.BoostPotion magicPotionVariantForTest()
	{
		return magicPotionVariant;
	}

	JToggleButton onSlayerTaskToggleForTest()
	{
		return onSlayerTaskToggle;
	}

	String ttkTextForTest()
	{
		return ttkValue.getText();
	}

	String overkillTextForTest()
	{
		return overkillValue.getText();
	}

	javax.swing.JComboBox<Spell> spellPickerForTest()
	{
		return spellPicker;
	}

	String targetTextForTest()
	{
		return targetLabel.getText();
	}

	boolean magicViewForTest()
	{
		return magicView;
	}

	boolean bookTabsVisibleForTest()
	{
		return bookTabsPanel.isVisible();
	}

	/** Simulates a user click on the given spellbook tab (0=Standard 1=Ancient 2=Lunar 3=Arceuus). */
	void clickBookTabForTest(int tabOrdinal)
	{
		bookTabButtons[tabOrdinal].doClick();
	}

	Spell selectedSpellForTest()
	{
		return selectedSpell;
	}

	List<Spell> rankedSpellsForTest()
	{
		List<Spell> out = new ArrayList<>(spellRows.size());
		for (SpellRow row : spellRows)
		{
			out.add(row.spell);
		}
		return out;
	}

	void clickSpellRowForTest(int index)
	{
		selectSpell(spellRows.get(index).spell);
	}

	String primaryTextForTest()
	{
		return primaryValue.getText();
	}

	String secondaryTextForTest()
	{
		return secondaryValue.getText();
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
	 * A spellbook tab of the magic view's segmented control. Lunar and Arceuus
	 * are deliberately absent — OSRS has no offensive spells on either book, so
	 * they would only ever render an empty "no offensive spells" tab; only
	 * Standard and Ancient (which both have real offensive spells to rank) are
	 * offered.
	 */
	enum BookTab
	{
		STANDARD("Standard", Spell.SpellBook.STANDARD),
		ANCIENT("Ancient", Spell.SpellBook.ANCIENT);

		private final String label;
		private final Spell.SpellBook book;

		BookTab(String label, Spell.SpellBook book)
		{
			this.label = label;
			this.book = book;
		}

		String label()
		{
			return label;
		}

		Spell.SpellBook book()
		{
			return book;
		}
	}

	/** A {@link Spell} paired with its computed DPS ({@code null} before a target is picked). */
	private static final class RankedSpell
	{
		private final Spell spell;
		private final DpsResult result;

		private RankedSpell(Spell spell, DpsResult result)
		{
			this.spell = spell;
			this.result = result;
		}
	}

	/**
	 * One clickable row in the ranked spell list: the spell's spellbook icon +
	 * name on the left, its DPS on the right, a leading star for the best.
	 * Clicking locks the readout to this spell.
	 */
	private final class SpellRow extends JPanel
	{
		private final Spell spell;
		private final Border selectedBorder = BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE);
		private final Border unselectedBorder = BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR);

		private SpellRow(Spell spell, DpsResult result, boolean best)
		{
			super(new BorderLayout(4, 0));
			this.spell = spell;
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setBorder(BorderFactory.createCompoundBorder(unselectedBorder,
				BorderFactory.createEmptyBorder(2, 3, 2, 4)));

			JLabel name = new JLabel((best ? "★ " : "") + spell.displayName());
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(best ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
			ImageIcon icon = spellIcon(spell);
			if (icon != null)
			{
				name.setIcon(icon);
				name.setIconTextGap(4);
			}

			JLabel dps = new JLabel(result == null ? "—" : String.format(Locale.ROOT, "%.2f", result.dps()));
			dps.setFont(FontManager.getRunescapeSmallFont());
			dps.setForeground(best ? ColorScheme.BRAND_ORANGE : java.awt.Color.WHITE);
			dps.setToolTipText("DPS autocasting this spell");

			add(name, BorderLayout.CENTER);
			add(dps, BorderLayout.EAST);

			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					selectSpell(SpellRow.this.spell);
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

			// The native Combat Options icon leads (the actual in-game
			// weapon-type-specific attack-style sprite — see AttackStyleIcons);
			// the style name stays as a small secondary label rather than the
			// old custom text ("Chop"/"Slash"/etc used to BE the icon).
			JLabel name = new JLabel((best ? "★ " : "") + style.name());
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(best ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
			ImageIcon icon = attackStyleIcon(currentWeaponCategory, style);
			if (icon != null)
			{
				name.setIcon(icon);
				name.setIconTextGap(4);
			}
			name.setToolTipText(style.name() + " (" + typeLabel(style.type()) + ")");

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

	/**
	 * A {@link JList} model over the current filtered {@link EquipmentIndexRepository.Entry}
	 * list for the Phase 2 what-if item search — same coarse-refresh pattern as
	 * {@link MonsterListModel} (swap-the-backing-list, two events) for the same
	 * per-keystroke responsiveness reason.
	 */
	private static final class ItemListModel extends AbstractListModel<String>
	{
		private List<EquipmentIndexRepository.Entry> items = Collections.emptyList();

		void setItems(List<EquipmentIndexRepository.Entry> newItems)
		{
			int oldSize = items.size();
			items = newItems == null ? Collections.emptyList() : newItems;
			if (oldSize > 0)
			{
				fireIntervalRemoved(this, 0, oldSize - 1);
			}
			if (!items.isEmpty())
			{
				fireIntervalAdded(this, 0, items.size() - 1);
			}
		}

		@Override
		public int getSize()
		{
			return items.size();
		}

		@Override
		public String getElementAt(int index)
		{
			return items.get(index).name();
		}
	}
}
