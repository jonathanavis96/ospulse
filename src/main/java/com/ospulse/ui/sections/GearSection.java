package com.ospulse.ui.sections;

import com.ospulse.OSPulseConfig;
import com.ospulse.combat.AttackStyleIcons;
import com.ospulse.combat.BlowpipeDart;
import com.ospulse.combat.CombatIcons;
import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.Monster;
import com.ospulse.combat.MonsterGearOverride;
import com.ospulse.combat.MonsterGearOverrideRepository;
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
import com.ospulse.session.GearVariants;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.wealth.WealthSnapshot;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.swing.AbstractListModel;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.OverlayLayout;
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
import java.awt.event.MouseListener;
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
	private static final int ITEM_SUPER_STRENGTH_POTION = 2440; // Super strength potion(4)
	private static final int ITEM_SUPER_ATTACK_POTION = 2436;   // Super attack potion(4)
	private static final int ITEM_RANGING_POTION = 2444;
	private static final int ITEM_BASTION_POTION = 22461;       // Bastion potion(4)
	private static final int ITEM_DIVINE_RANGING_POTION = 23733; // Divine ranging potion(4)
	private static final int ITEM_IMBUED_HEART = 20724;
	private static final int ITEM_SATURATED_HEART = 27641;
	private static final int ITEM_ANCIENT_BREW = 26340; // Ancient brew(4)

	/**
	 * Side length for the style-aware prayer indicator icon. Matches the
	 * rendered footprint of the potion/slayer-helm indicator icons, which are
	 * native item sprites (~32px, unscaled — see {@link #iconToggle}) inside
	 * the same {@link #SLOT_H}-tall toggle button; previously this was 18,
	 * which made the prayer icon look tiny with excess whitespace next to
	 * those two.
	 */
	private static final int INDICATOR_ICON_SIZE = 32;

	/** Side length for the attack-style-row and spell-row icons. */
	private static final int STYLE_ICON_SIZE = 18;

	/** Columns in the item-picker icon grid (design ask: "4 icons per row"). */
	private static final int ITEM_GRID_COLUMNS = 4;
	/** One grid cell's square side length — matches the worn-gear grid's slot size so the two grids read consistently. */
	private static final int ITEM_GRID_CELL_SIZE = SLOT_H;

	/**
	 * Delta colours for a positive/negative DPS comparison (item #6b) — reuse
	 * the same green/red the panel already applies to gp gains elsewhere (see
	 * {@link PanelWidgets#setSignedGpLabel}) instead of the literal ▲/▼
	 * triangle glyphs the "vs owned only" / "vs worn gear" rows used before.
	 */
	private static final java.awt.Color DELTA_UP_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final java.awt.Color DELTA_DOWN_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;

	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;
	private final SpriteManager spriteManager;
	/** {@code null} in tests that don't exercise persistence (see the no-config-manager constructors) — every read/write of it is guarded. */
	private final ConfigManager configManager;
	/** Nullable collaborator wired post-construction by {@link com.ospulse.ui.OSPulsePanel#setBankHighlighter} — see {@link #setBankHighlighter}. */
	private com.ospulse.integration.BankRecommendationHighlighter bankHighlighter;
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
	private final HintableToggleButton bestPotionToggle;
	private final JToggleButton bestPrayerToggle;
	private final JToggleButton onSlayerTaskToggle;
	private final JLabel maxHitValue;
	private final JLabel accuracyValue;
	private final JLabel avgHitValue;
	private final JLabel dpsValue;
	private final JLabel ttkValue;
	private final JLabel overkillValue;
	private final JLabel baseEstimateNote;
	/**
	 * Container for the mechanic-override advisory rows (e.g. "vs Rune dragon:
	 * equip Insulated boots") — one label per {@link MonsterGearOverride} on the
	 * selected target, rebuilt in {@link #updateGearOverrideNote()} whenever the
	 * target changes. Hidden entirely (zero height) when the target has none.
	 */
	private final JPanel gearOverrideNotePanel;

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
	 * The potion variant the right-click swap menu has picked, PER combat
	 * style ({@code CombatStyle.MELEE_KEY}/{@code RANGED}/{@code MAGIC} — see
	 * {@link #styleKeyFor}), persisted to RuneLite config (see
	 * {@link #loadPotionVariantPrefs}/{@link #savePotionVariantPref}) so a
	 * choice like "Saturated heart on Magic" survives a client restart. Absent
	 * = follow {@link CombatIcons#bestPotion} (the style's default variant).
	 * Only the MAGIC entry actually reaches {@link DpsCalculator} (via
	 * {@link PlayerCombat#magicPotionVariant()}) since melee/ranged variants
	 * are cosmetic-only (see {@link CombatIcons.BoostPotion} javadoc) — the
	 * melee/ranged entries only drive which icon/tooltip is shown.
	 */
	private final Map<String, CombatIcons.BoostPotion> potionVariantByStyle = new HashMap<>();

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
	private final JButton closeItemSearchButton;
	/** The search-field + close-button row — shown/hidden together as one unit (see {@link #toggleItemSearch}/{@link #closeItemSearch}). */
	private final JPanel itemSearchRow;
	/** 4-columns-wide scrollable icon grid of {@link #filteredItems} — see {@link #populateItemList}/{@link #ItemGridCell}. */
	private final JPanel itemGridPanel;
	private final JScrollPane itemGridScroll;
	private List<EquipmentIndexRepository.Entry> filteredItems = Collections.emptyList();
	private final JButton resetAllButton;
	private final JLabel whatIfLabel;
	private final JLabel whatIfDeltaValue;
	private JPanel whatIfRow;

	// -------------------------------------------- Phase 3: optimiser ("Best Setup")
	/** Owned-item values (worn + top holdings incl. bank), refreshed each {@link #apply}; source for the optimiser's owned pool + GE prices. */
	private WealthSnapshot lastWealth;
	/** Budget's numeric entry (unit picked by {@link #budgetKToggle}/{@link #budgetMToggle}) — see {@link #resolvedBudget}. */
	private final javax.swing.JTextField budgetField;
	private final JToggleButton budgetKToggle;
	private final JToggleButton budgetMToggle;
	/** "Expensive items to allow" count (wilderness/PvP) — plumbed into {@link GearOptimizer.Request#expensiveItemCount()}, not yet enforced by the search. */
	private final javax.swing.JTextField expensiveCountField;
	/** GP value at/above which an item counts as "expensive" — see {@link #expensiveCountField}. */
	private final javax.swing.JTextField expensiveThresholdField;
	private final JToggleButton expensiveThresholdKToggle;
	private final JToggleButton expensiveThresholdMToggle;
	private static final String EXPENSIVE_COUNT_TOOLTIP =
		"The amount of 'expensive' items you want to have in your setup, for wilderness or pvp world activities.";
	private static final String EXPENSIVE_THRESHOLD_TOOLTIP = "The value of when an item is considered expensive.";
	private final JButton findBestSetupButton;
	private final JLabel optimizerStatusLabel;
	private final JPanel optimizerResultPanel;
	private final JLabel optimizerResultStyle;
	private final JLabel optimizerResultDps;
	private final JLabel optimizerResultDelta;
	private final JLabel optimizerResultSpend;
	private final JLabel optimizerResultDpsPerGp;
	/** One row per proposed slot swap (icon current -&gt; icon suggested) — see {@link #renderOptimizerSwapList}. */
	private final JPanel optimizerSwapList;
	private final JButton applyOptimizerResultButton;
	/** Filters the open bank to the result's item ids via {@link #bankHighlighter} — see {@link #setBankHighlighter}. */
	private final JToggleButton showInBankButton;
	private final JButton clearOptimizerPreviewButton;
	/** The excluded-items viewer container (heading + search + scrollable icon grid); hidden when nothing is excluded — see {@link #renderExcludedItemsList}. */
	private final JPanel excludedItemsPanel;
	/** Icon-only grid ({@link #ITEM_GRID_COLUMNS} per row) of excluded items, each cell carrying a top-right ✕ — see {@link #buildExcludedCell}. */
	private final JPanel excludedItemsList;
	/** Filters {@link #excludedItemsList} by item name (case-insensitive substring). */
	private final IconTextField excludedSearchField;
	private GearOptimizer.Result lastOptimizerResult;
	/**
	 * Item ids the user right-clicked "Exclude from suggestions" on (item #6a)
	 * — never suggested by the optimiser (wired into
	 * {@link GearOptimizer.Request.Builder#exclude}), persisted via
	 * {@link #loadExcludedItemsPref}/{@link #saveExcludedItemsPref}.
	 */
	private final java.util.Set<Integer> excludedItemIds = new java.util.LinkedHashSet<>();

	/**
	 * Item #6e: the Best-setup optimiser's 5-way damage-type selector, in
	 * button order. The optimiser search is CONSTRAINED to the selected type
	 * (see {@link GearOptimizer.Request.Builder#style}) — different types have
	 * genuinely different best-in-slot answers (e.g. Scythe+Inquisitor's on
	 * Crush vs Scythe+Torva+Bellator on Slash), so "best setup" is only
	 * meaningful per damage type.
	 */
	private static final CombatStyle[] OPTIMIZER_STYLE_ORDER = {
		CombatStyle.RANGED, CombatStyle.MAGIC, CombatStyle.CRUSH, CombatStyle.SLASH, CombatStyle.STAB,
	};
	private final JToggleButton[] optimizerStyleButtons = new JToggleButton[OPTIMIZER_STYLE_ORDER.length];
	/**
	 * The panel built by {@link #buildOptimizerStyleSelector()}, holding the
	 * five style buttons — kept so {@link #reorderSelectorsByDps} can re-add
	 * them in a new visual order after a "Find best setup" 5-style ranking.
	 */
	private JPanel optimizerStyleSelectorPanel;
	/**
	 * The damage type the optimiser searches for. Until the user clicks one of
	 * the five buttons ({@link #optimizerStyleUserPicked}) this FOLLOWS the
	 * equipped weapon's current combat style (item #6g: previewing with a
	 * wand/bow equipped must optimise magic/ranged, never default to melee) —
	 * re-detected in {@link #syncOptimizerStyleSelector} on every re-rank.
	 */
	private CombatStyle optimizerStyle;
	/** True once the user clicked a selector button; cleared when the (effective) weapon changes, like the style lock. */
	private boolean optimizerStyleUserPicked;

	/** Gold marker for a suggested item the player does NOT own (border + price label) — RuneLite's GE-gold tone. */
	private static final java.awt.Color NOT_OWNED_GOLD = new java.awt.Color(240, 207, 123);

	/**
	 * Client-thread-precomputed pricing + tradeability for a candidate id set,
	 * delivered by an {@link OptimizerPriceResolver}. Both views are plain
	 * collections so the (EDT/background) optimiser code never needs
	 * {@code ItemManager}/the client thread itself:
	 * <ul>
	 *   <li>{@link #prices()} — GE unit price per id (absent = unpriced);</li>
	 *   <li>{@link #untradeableIds()} — ids that CANNOT be traded/bought
	 *       ({@code ItemComposition.isTradeable() == false}). An unowned
	 *       untradeable is never purchasable regardless of any price RuneLite
	 *       reports for it (e.g. trouver-locked items are "priced" at the
	 *       Trouver parchment's GE cost via {@code ItemMapping}) — see
	 *       {@link #resolveOptimizerPriceSource}.</li>
	 * </ul>
	 */
	public static final class PriceLookup
	{
		private final java.util.Map<Integer, Long> prices;
		private final java.util.Set<Integer> untradeableIds;

		public PriceLookup(java.util.Map<Integer, Long> prices, java.util.Set<Integer> untradeableIds)
		{
			this.prices = prices == null
				? java.util.Collections.emptyMap()
				: java.util.Collections.unmodifiableMap(new HashMap<>(prices));
			this.untradeableIds = untradeableIds == null
				? java.util.Collections.emptySet()
				: java.util.Collections.unmodifiableSet(new java.util.HashSet<>(untradeableIds));
		}

		public java.util.Map<Integer, Long> prices()
		{
			return prices;
		}

		public java.util.Set<Integer> untradeableIds()
		{
			return untradeableIds;
		}
	}

	/**
	 * Resolves GE prices + tradeability for candidate item ids ON THE CLIENT
	 * THREAD, then delivers the {@link PriceLookup} back to the given callback
	 * (impl marshals the callback onto the EDT). Null in tests / when
	 * unavailable → optimiser falls back to an owned-only search.
	 */
	@FunctionalInterface
	public interface OptimizerPriceResolver
	{
		void resolve(java.util.Set<Integer> itemIds, java.util.function.Consumer<PriceLookup> onResolved);
	}

	/** Nullable — see {@link OptimizerPriceResolver}; {@code null} means owned-only search (no client-thread pricing available). */
	private final OptimizerPriceResolver priceResolver;

	public GearSection(CollapseStore store, ItemManager itemManager, SkillIconManager skillIconManager)
	{
		this(store, itemManager, skillIconManager, null, null);
	}

	public GearSection(CollapseStore store, ItemManager itemManager, SkillIconManager skillIconManager,
		SpriteManager spriteManager)
	{
		this(store, itemManager, skillIconManager, spriteManager, null);
	}

	public GearSection(CollapseStore store, ItemManager itemManager, SkillIconManager skillIconManager,
		SpriteManager spriteManager, ConfigManager configManager)
	{
		this(store, itemManager, skillIconManager, spriteManager, configManager, null);
	}

	public GearSection(CollapseStore store, ItemManager itemManager, SkillIconManager skillIconManager,
		SpriteManager spriteManager, ConfigManager configManager, OptimizerPriceResolver priceResolver)
	{
		super(KEY, "Gear DPS", store);
		this.itemManager = itemManager;
		this.skillIconManager = skillIconManager;
		this.spriteManager = spriteManager;
		this.configManager = configManager;
		this.priceResolver = priceResolver;
		loadPotionVariantPrefs();

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
		// search's search-field + collapsible-result-list UX for consistency,
		// but renders candidates as a 4-columns-wide scrollable ICON grid
		// (populateItemList/ItemGridCell) instead of a text JList — a picker
		// full of item names all cost the user a squint-and-read; icons are
		// recognisable at a glance and match how the equipment tab itself
		// looks. The text search box is kept alongside for filtering by name.
		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemSearchField = new IconTextField();
		itemSearchField.setIcon(IconTextField.Icon.SEARCH);
		itemSearchField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemSearchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		itemSearchField.setPreferredSize(new Dimension(100, 24));
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
		// A dedicated close/X button — there was previously no way to dismiss
		// the picker once open besides re-clicking the same gear-grid slot.
		closeItemSearchButton = new JButton("✕");
		closeItemSearchButton.setToolTipText("Close item picker");
		closeItemSearchButton.setFont(FontManager.getRunescapeSmallFont());
		closeItemSearchButton.setFocusPainted(false);
		closeItemSearchButton.setMargin(new Insets(0, 6, 0, 6));
		closeItemSearchButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
		closeItemSearchButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		closeItemSearchButton.addActionListener(e -> closeItemSearch());
		searchRow.add(itemSearchField, BorderLayout.CENTER);
		searchRow.add(closeItemSearchButton, BorderLayout.EAST);
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		searchRow.setVisible(false);
		body().add(searchRow);
		body().add(Box.createRigidArea(new Dimension(0, 2)));
		this.itemSearchRow = searchRow;

		itemGridPanel = new JPanel(new GridLayout(0, ITEM_GRID_COLUMNS, 2, 2));
		itemGridPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemGridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		itemGridScroll = new JScrollPane(itemGridPanel);
		itemGridScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		itemGridScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		itemGridScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		itemGridScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemGridScroll.getVerticalScrollBar().setUnitIncrement(ITEM_GRID_CELL_SIZE);
		// Capped-height viewport (design: compact in the narrow side panel) —
		// enough for a couple of rows before it scrolls, matching the
		// attack-style list's STYLES_VISIBLE_ROWS pattern.
		int gridViewportHeight = ITEM_GRID_CELL_SIZE * 2 + 4;
		itemGridScroll.setPreferredSize(new Dimension(0, gridViewportHeight));
		itemGridScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, gridViewportHeight));
		itemGridScroll.setVisible(false);
		body().add(itemGridScroll);
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
		// TODO: prefer the player's actual autocast spell for the secondary.
		// GearSnapshot.autocastSpellId() now exposes the raw autocast varbit (276),
		// and SessionTracker logs it ([autocast] ...). Still needed: the
		// value->Spell mapping (cache data), captured from one in-client pass, to
		// resolve that raw id to a named spell here. Until then, secondary stays
		// the next-best-DPS fallback below.
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
		// button opens a swap menu filtered to whatever style is CURRENTLY
		// selected (melee: Super combat/strength/attack; ranged: Ranging/
		// Bastion/Divine ranging; magic: Saturated heart/Imbued heart/Ancient
		// brew — see CombatIcons.variantsFor/buildPotionVariantPopup), each
		// choice persisted per-style to config (loadPotionVariantPrefs/
		// savePotionVariantPref) so it survives a client restart. The small
		// orange "*" painted in the icon's corner (HintableToggleButton) hints
		// that right-click has more options.
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
		bestPotionToggle.addItemListener(e -> onBoostToggleChanged());
		bestPrayerToggle.addItemListener(e -> onBoostToggleChanged());
		onSlayerTaskToggle.addItemListener(e -> onBoostToggleChanged());
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
				resetBankHighlightToggle();
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

		// Monster-mechanic gear override advisory (e.g. Insulated boots vs Rune
		// dragons) — a curated, DPS-blind requirement the optimiser would never
		// suggest on its own. Empty/invisible until updateGearOverrideNote()
		// finds one for the selected target.
		gearOverrideNotePanel = new JPanel();
		gearOverrideNotePanel.setLayout(new BoxLayout(gearOverrideNotePanel, BoxLayout.Y_AXIS));
		gearOverrideNotePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		gearOverrideNotePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		gearOverrideNotePanel.setVisible(false);
		body().add(gearOverrideNotePanel);
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

		// Item #6e: 5-way damage-type selector (Ranged/Magic/Crush/Slash/Stab).
		// Defaults to the equipped weapon's current combat style (item #6g) and
		// re-runs a visible search immediately when the user picks another type.
		body().add(buildOptimizerStyleSelector());
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		// Budget: a numeric field + a K/M segmented toggle (was a single "10m"/
		// "500k" free-text field — split so the number entry never has to deal
		// with a trailing unit letter itself). budgetUnitIsMillions defaults to
		// true (M) since most upgrade budgets are in the millions.
		JPanel budgetRow = new JPanel(new BorderLayout(4, 0));
		budgetRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		budgetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel budgetLabel = new JLabel("Budget");
		budgetLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		budgetLabel.setFont(FontManager.getRunescapeSmallFont());
		budgetField = new javax.swing.JTextField("0");
		budgetField.setToolTipText("Extra GP to spend on upgrades beyond your owned gear (blank/0 = owned gear only)");
		budgetField.setFont(FontManager.getRunescapeSmallFont());
		budgetField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		budgetField.setForeground(java.awt.Color.WHITE);
		budgetKToggle = new JToggleButton("K");
		budgetMToggle = new JToggleButton("M");
		JPanel budgetUnitToggle = unitToggle(budgetKToggle, budgetMToggle, true);
		JPanel budgetFieldRow = new JPanel(new BorderLayout(4, 0));
		budgetFieldRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		budgetFieldRow.add(budgetField, BorderLayout.CENTER);
		budgetFieldRow.add(budgetUnitToggle, BorderLayout.EAST);
		budgetRow.add(budgetLabel, BorderLayout.WEST);
		budgetRow.add(budgetFieldRow, BorderLayout.CENTER);
		budgetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		body().add(budgetRow);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		// Expensive-items count + threshold — wilderness/PvP risk budgeting.
		// Plumbed into GearOptimizer.Request (expensiveItemCount/
		// expensiveItemThreshold) but not yet enforced by the search itself
		// (see that class's javadoc) — a later pass consumes them.
		expensiveCountField = new javax.swing.JTextField("0");
		expensiveCountField.setToolTipText(EXPENSIVE_COUNT_TOOLTIP);
		expensiveCountField.setFont(FontManager.getRunescapeSmallFont());
		expensiveCountField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		expensiveCountField.setForeground(java.awt.Color.WHITE);
		JPanel expensiveCountRow = labelledFieldRow("Expensive items", EXPENSIVE_COUNT_TOOLTIP, expensiveCountField);
		body().add(expensiveCountRow);
		body().add(Box.createRigidArea(new Dimension(0, 2)));

		expensiveThresholdField = new javax.swing.JTextField("0");
		expensiveThresholdField.setToolTipText(EXPENSIVE_THRESHOLD_TOOLTIP);
		expensiveThresholdField.setFont(FontManager.getRunescapeSmallFont());
		expensiveThresholdField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		expensiveThresholdField.setForeground(java.awt.Color.WHITE);
		expensiveThresholdKToggle = new JToggleButton("K");
		expensiveThresholdMToggle = new JToggleButton("M");
		JPanel expensiveThresholdUnitToggle = unitToggle(expensiveThresholdKToggle, expensiveThresholdMToggle, true);
		JPanel expensiveThresholdFieldRow = new JPanel(new BorderLayout(4, 0));
		expensiveThresholdFieldRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		expensiveThresholdFieldRow.add(expensiveThresholdField, BorderLayout.CENTER);
		expensiveThresholdFieldRow.add(expensiveThresholdUnitToggle, BorderLayout.EAST);
		JPanel expensiveThresholdRow = labelledFieldRow("Expensive threshold", EXPENSIVE_THRESHOLD_TOOLTIP,
			expensiveThresholdFieldRow);
		body().add(expensiveThresholdRow);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		loadOptimizerPrefs();
		loadExcludedItemsPref();
		java.awt.event.ActionListener persistOptimizerPrefs = e -> saveOptimizerPrefs();
		budgetField.addActionListener(persistOptimizerPrefs);
		expensiveCountField.addActionListener(persistOptimizerPrefs);
		expensiveThresholdField.addActionListener(persistOptimizerPrefs);
		budgetKToggle.addActionListener(e -> saveOptimizerPrefs());
		budgetMToggle.addActionListener(e -> saveOptimizerPrefs());
		expensiveThresholdKToggle.addActionListener(e -> saveOptimizerPrefs());
		expensiveThresholdMToggle.addActionListener(e -> saveOptimizerPrefs());

		findBestSetupButton = new JButton("Find best setup");
		findBestSetupButton.setFont(FontManager.getRunescapeSmallFont());
		findBestSetupButton.setFocusPainted(false);
		findBestSetupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		findBestSetupButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		findBestSetupButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		findBestSetupButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, findBestSetupButton.getPreferredSize().height));
		findBestSetupButton.addActionListener(e -> runOptimizerAndRankStyles());
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
		optimizerResultStyle = PanelWidgets.statRow(optimizerResultPanel, "Optimised for");
		optimizerResultStyle.setToolTipText("The damage type this setup was optimised for — change it with the selector above");
		optimizerResultDps = PanelWidgets.statRow(optimizerResultPanel, "Best DPS found");
		optimizerResultDelta = PanelWidgets.statRow(optimizerResultPanel, "vs owned-only");
		optimizerResultSpend = PanelWidgets.statRow(optimizerResultPanel, "Total spend");
		optimizerResultDpsPerGp = PanelWidgets.statRow(optimizerResultPanel, "DPS per gp spent");

		// The proposed swaps themselves — "Slot: current -> suggested (+X DPS)" —
		// so the user sees exactly what the optimiser is suggesting instead of
		// only an aggregate DPS number (design ask: results clarity).
		optimizerResultPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		JLabel swapListHeading = PanelWidgets.emptyRowLabel("Suggested swaps");
		swapListHeading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		optimizerResultPanel.add(swapListHeading);
		optimizerSwapList = new JPanel();
		optimizerSwapList.setLayout(new BoxLayout(optimizerSwapList, BoxLayout.Y_AXIS));
		optimizerSwapList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		optimizerSwapList.setAlignmentX(Component.LEFT_ALIGNMENT);
		optimizerResultPanel.add(optimizerSwapList);
		optimizerResultPanel.add(Box.createRigidArea(new Dimension(0, 4)));

		// "Preview these swaps" (was the unclear "Apply to readout (what-if)")
		// plus a one-line explanation of exactly what it does — it only loads
		// the suggestion into the what-if readout below as a preview; it never
		// touches the player's real worn gear.
		JLabel previewExplanation = PanelWidgets.emptyRowLabel(
			"Loads these swaps into the readout above as a preview — your real gear is not changed.");
		previewExplanation.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		previewExplanation.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
		optimizerResultPanel.add(previewExplanation);
		applyOptimizerResultButton = new JButton("Preview these swaps");
		applyOptimizerResultButton.setFont(FontManager.getRunescapeSmallFont());
		applyOptimizerResultButton.setFocusPainted(false);
		applyOptimizerResultButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		applyOptimizerResultButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		applyOptimizerResultButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		applyOptimizerResultButton.setToolTipText("Loads this result into the what-if slots above as a preview — your real gear is unaffected");
		applyOptimizerResultButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, applyOptimizerResultButton.getPreferredSize().height));
		applyOptimizerResultButton.addActionListener(e -> applyOptimizerResultToOverride());
		optimizerResultPanel.add(applyOptimizerResultButton);

		// Filters the open bank down to this result's item ids (via bank tags),
		// so the player can see what they're missing without leaving the bank
		// interface — same reserved-tag mechanism the Inventory Setups plugin
		// uses. Deselecting (or any of the reset/clear paths below) drops the
		// filter back to normal.
		showInBankButton = new JToggleButton("Show in bank");
		showInBankButton.setFont(FontManager.getRunescapeSmallFont());
		showInBankButton.setFocusPainted(false);
		showInBankButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		showInBankButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		showInBankButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		showInBankButton.setToolTipText("Filters your open bank to this result's items using a reserved bank tag");
		showInBankButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, showInBankButton.getPreferredSize().height));
		showInBankButton.addActionListener(e ->
		{
			if (bankHighlighter == null)
			{
				return;
			}
			if (showInBankButton.isSelected() && lastOptimizerResult != null)
			{
				java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
				for (GearOptimizer.SlotChoice choice : lastOptimizerResult.loadout())
				{
					if (choice.itemId() > 0)
					{
						ids.add(choice.itemId());
					}
				}
				bankHighlighter.showInBank(ids);
			}
			else
			{
				bankHighlighter.clear();
			}
		});
		optimizerResultPanel.add(showInBankButton);

		// The preview is otherwise sticky with no way to cancel it — this
		// reuses the same resetAllOverrides() the "Reset all to worn gear"
		// button uses, so it clears the overrides AND hides this whole panel.
		clearOptimizerPreviewButton = new JButton("Clear preview / revert to worn gear");
		clearOptimizerPreviewButton.setFont(FontManager.getRunescapeSmallFont());
		clearOptimizerPreviewButton.setFocusPainted(false);
		clearOptimizerPreviewButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		clearOptimizerPreviewButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clearOptimizerPreviewButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		clearOptimizerPreviewButton.setToolTipText("Cancels the preview above and any what-if swaps, going back to your real worn gear");
		clearOptimizerPreviewButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, clearOptimizerPreviewButton.getPreferredSize().height));
		clearOptimizerPreviewButton.addActionListener(e -> resetAllOverrides());
		optimizerResultPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		optimizerResultPanel.add(clearOptimizerPreviewButton);

		optimizerResultPanel.setVisible(false);
		body().add(optimizerResultPanel);
		body().add(Box.createRigidArea(new Dimension(0, 4)));

		// Excluded-from-suggestions viewer: the items the user has right-clicked
		// to exclude, shown as an icon-only grid (4 per row, ~2 rows before it
		// scrolls) with a search box, mirroring the item-picker grid above so
		// the two read as one design. Each icon carries a top-right ✕ to stop
		// excluding it. Persisted across reloads (loadExcludedItemsPref above
		// already populated the set), so this renders whatever was excluded in a
		// previous session. The whole panel hides itself when nothing is
		// excluded (renderExcludedItemsList).
		excludedItemsPanel = new JPanel();
		excludedItemsPanel.setLayout(new BoxLayout(excludedItemsPanel, BoxLayout.Y_AXIS));
		excludedItemsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		excludedItemsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel excludedHeading = PanelWidgets.emptyRowLabel("Excluded from suggestions");
		excludedHeading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		excludedHeading.setToolTipText("Items you've excluded from optimiser suggestions — click a ✕ to stop excluding one");
		excludedItemsPanel.add(excludedHeading);

		excludedSearchField = new IconTextField();
		excludedSearchField.setIcon(IconTextField.Icon.SEARCH);
		excludedSearchField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		excludedSearchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		excludedSearchField.setPreferredSize(new Dimension(100, 24));
		excludedSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		excludedSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		excludedSearchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				renderExcludedItemsList();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				renderExcludedItemsList();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				renderExcludedItemsList();
			}
		});
		excludedItemsPanel.add(excludedSearchField);
		excludedItemsPanel.add(Box.createRigidArea(new Dimension(0, 2)));

		excludedItemsList = new JPanel(new GridLayout(0, ITEM_GRID_COLUMNS, 2, 2));
		excludedItemsList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		excludedItemsList.setAlignmentX(Component.LEFT_ALIGNMENT);
		JScrollPane excludedScroll = new JScrollPane(excludedItemsList);
		excludedScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		excludedScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		excludedScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		excludedScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		excludedScroll.getVerticalScrollBar().setUnitIncrement(ITEM_GRID_CELL_SIZE);
		// ~2 rows visible before scrolling — matches the item-picker grid above.
		int excludedViewportHeight = ITEM_GRID_CELL_SIZE * 2 + 4;
		excludedScroll.setPreferredSize(new Dimension(0, excludedViewportHeight));
		excludedScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, excludedViewportHeight));
		excludedItemsPanel.add(excludedScroll);
		body().add(excludedItemsPanel);
		body().add(Box.createRigidArea(new Dimension(0, 4)));
		renderExcludedItemsList();

		// Show the full monster list, but with NO pre-selected target — the
		// user must explicitly pick one before any numbers are shown (see
		// populateMonsterList). Auto-detect-opponent and favourites are
		// deferred to a later phase.
		populateMonsterList("");
	}

	/**
	 * Wires the bank-tags collaborator the "Show in bank" toggle drives.
	 * Defaults to {@code null} (toggle is a no-op) until the plugin assembles
	 * this — mirrors {@link com.ospulse.ui.OSPulsePanel#setResetCallback}.
	 */
	public void setBankHighlighter(com.ospulse.integration.BankRecommendationHighlighter bankHighlighter)
	{
		this.bankHighlighter = bankHighlighter;
	}

	/**
	 * Un-toggles "Show in bank" and drops any active bank-tag filter. Called
	 * from every place the optimiser result is invalidated or superseded
	 * (reset/clear, a new search starting, the result being applied to the
	 * what-if readout, target change) so the highlight never outlives the
	 * result it was generated from.
	 */
	private void resetBankHighlightToggle()
	{
		if (showInBankButton != null)
		{
			showInBankButton.setSelected(false);
		}
		if (bankHighlighter != null)
		{
			bankHighlighter.clear();
		}
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
					// Right-click (popup trigger) on a cell showing a real item
					// offers "Exclude from suggestions" — Jonathan wanted this while
					// previewing an optimiser setup. Left-click still opens the
					// item-swap search. (Popup trigger fires on press on Linux and
					// release on Windows, so it is checked in both handlers.)
					if (maybeShowSlotExcludePopup(e, clickedSlot))
					{
						return;
					}
					if (SwingUtilities.isLeftMouseButton(e))
					{
						toggleItemSearch(clickedSlot);
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					maybeShowSlotExcludePopup(e, clickedSlot);
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
		// Owned-item lookup for the preview tooltips below — only needed while
		// at least one slot is overridden (i.e. a what-if/optimiser preview).
		java.util.Map<Integer, Long> ownedIds = override.isEmpty() ? null : ownedPriceMap();
		EquipmentIndexRepository index = override.isEmpty() ? null : EquipmentIndexRepository.getInstance();
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
			// Item #6f (second site): the cell tooltip must describe what the
			// cell is actually SHOWING. While previewing it names the previewed
			// item + the real slot + whether the item is even owned, instead of
			// the misleading static "<slot> slot (live)" text over preview
			// content. Refreshed before the icon diff-guard below since the
			// override/owned state can change without the item id changing.
			if (overridden)
			{
				boolean ownedItem = id <= 0 || (ownedIds != null && ownedIds.containsKey(id));
				cell.setToolTipText(itemDisplayName(index, id) + " — " + SLOT_NAMES[slot] + " slot (preview"
					+ (ownedItem ? "" : ", not owned") + ") — click to try a different item");
			}
			else
			{
				cell.setToolTipText(SLOT_NAMES[slot] + " slot (live) — click to try a different item");
			}
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

	/** Uniform height the bundled damage-type icons are scaled to for the selector row. */
	private static final int STYLE_ICON_HEIGHT = 18;

	/** Lazily-loaded, scaled damage-type icons for the optimiser style selector, keyed by style. */
	private static final java.util.Map<CombatStyle, ImageIcon> STYLE_ICONS = new java.util.EnumMap<>(CombatStyle.class);

	/**
	 * The bundled damage-type icon (Stab/Slash/Crush/Magic/Ranged sprite) for the
	 * optimiser style selector, scaled to a uniform {@link #STYLE_ICON_HEIGHT} so
	 * the segmented buttons align. Cached per style; returns {@code null} if the
	 * resource is missing so callers fall back to the text {@link #typeLabel}.
	 */
	private static ImageIcon styleIcon(CombatStyle type)
	{
		if (STYLE_ICONS.containsKey(type))
		{
			return STYLE_ICONS.get(type);
		}
		String file = styleIconFile(type);
		ImageIcon icon = null;
		if (file != null)
		{
			BufferedImage img = ImageUtil.loadImageResource(GearSection.class, "/com/ospulse/ui/style/" + file);
			if (img != null)
			{
				int w = Math.max(1, img.getWidth() * STYLE_ICON_HEIGHT / img.getHeight());
				icon = new ImageIcon(img.getScaledInstance(w, STYLE_ICON_HEIGHT, Image.SCALE_SMOOTH));
			}
		}
		STYLE_ICONS.put(type, icon);
		return icon;
	}

	private static String styleIconFile(CombatStyle type)
	{
		switch (type)
		{
			case STAB:
				return "stab.png";
			case SLASH:
				return "slash.png";
			case CRUSH:
				return "crush.png";
			case RANGED:
				return "ranged.png";
			case MAGIC:
				return "magic.png";
			default:
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
			// The optimiser's 5-way selector follows the same rule (item #6e/#6g):
			// a manual damage-type pick is dropped on a weapon change so the
			// selector re-detects from whatever is now actually wielded.
			optimizerStyleUserPicked = false;
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
			renderMagicView(weaponId, styles, poweredStaff, canRank);
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

		// Keep the optimiser's 5-way selector tracking the (possibly what-if)
		// equipped weapon's current style unless the user picked one (item #6g).
		syncOptimizerStyleSelector();

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
	private void renderMagicView(int weaponId, List<WeaponStyle> styles, PoweredStaff poweredStaff, boolean canRank)
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

		List<Spell> candidates = spellsFor(selectedBook, weaponId);
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

	/**
	 * The offensive spells of a spellbook tab (empty for Lunar/Arceuus — OSRS
	 * has no offensive nukes there), filtered to only those actually castable
	 * with the given weapon (see {@link Spell#isCastableWith(int)}) — e.g.
	 * Iban Blast never appears/ranks unless Iban's staff is equipped.
	 */
	private static List<Spell> spellsFor(BookTab tab, int weaponId)
	{
		if (tab.book() == null)
		{
			return Collections.emptyList();
		}
		List<Spell> spells = new ArrayList<>();
		for (Spell spell : Spell.values())
		{
			if (spell.book() == tab.book() && spell.isCastableWith(weaponId))
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
				// If gear suggestions are already on screen, re-run so their DPS
				// follows the newly-selected spellbook too — not just the spell
				// readout that rankAndRender refreshes.
				if (lastOptimizerResult != null)
				{
					runOptimizer();
				}
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
			magicPotionVariantForCalc());
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
			case SUPER_STRENGTH: return ITEM_SUPER_STRENGTH_POTION;
			case SUPER_ATTACK: return ITEM_SUPER_ATTACK_POTION;
			case RANGING: return ITEM_RANGING_POTION;
			case BASTION: return ITEM_BASTION_POTION;
			case DIVINE_RANGING: return ITEM_DIVINE_RANGING_POTION;
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
	 * The potion variant the potion toggle should show/apply for {@code style}:
	 * the user's right-click swap pick for that style if one was ever made
	 * (restored from config at startup — see {@link #loadPotionVariantPrefs}),
	 * else {@link CombatIcons#bestPotion}'s default for that style.
	 */
	private CombatIcons.BoostPotion effectivePotionFor(CombatStyle style)
	{
		String key = styleKeyFor(style);
		if (key == null)
		{
			return null;
		}
		CombatIcons.BoostPotion picked = potionVariantByStyle.get(key);
		return picked != null ? picked : CombatIcons.bestPotion(style);
	}

	/**
	 * The MAGIC-only variant actually fed to {@link DpsCalculator} — see
	 * {@link PlayerCombat#magicPotionVariant()}. Melee/Ranged variants never
	 * reach the calculator since their boost math is identical regardless of
	 * which variant is picked (see {@link CombatIcons.BoostPotion} javadoc).
	 */
	private CombatIcons.BoostPotion magicPotionVariantForCalc()
	{
		return potionVariantByStyle.get(styleKeyFor(CombatStyle.MAGIC));
	}

	/** Stable config-key fragment per style ("melee"/"ranged"/"magic"), or {@code null} for a style with no swappable variant. */
	private static String styleKeyFor(CombatStyle style)
	{
		if (style == null)
		{
			return null;
		}
		if (style.isMelee())
		{
			return "melee";
		}
		if (style == CombatStyle.RANGED)
		{
			return "ranged";
		}
		if (style == CombatStyle.MAGIC)
		{
			return "magic";
		}
		return null;
	}

	/** Config key for a style's persisted potion-variant pick (raw key, not declared on {@link OSPulseConfig}). */
	private static String potionVariantConfigKey(String styleKey)
	{
		return "potionVariant." + styleKey;
	}

	/**
	 * Restores each style's potion-variant pick from config (see
	 * {@link #savePotionVariantPref}) so a choice like "Saturated heart on
	 * Magic" survives a client restart. No-ops without a {@link ConfigManager}
	 * (headless tests / the no-config-manager constructor).
	 */
	private void loadPotionVariantPrefs()
	{
		if (configManager == null)
		{
			return;
		}
		for (String styleKey : new String[] {"melee", "ranged", "magic"})
		{
			String raw = configManager.getConfiguration(OSPulseConfig.GROUP, potionVariantConfigKey(styleKey));
			if (raw == null || raw.isEmpty())
			{
				continue;
			}
			try
			{
				potionVariantByStyle.put(styleKey, CombatIcons.BoostPotion.valueOf(raw));
			}
			catch (IllegalArgumentException e)
			{
				// Stale/unknown enum name (e.g. an older plugin version) — ignore, fall back to the style default.
			}
		}
	}

	/** Persists one style's potion-variant pick to config so it survives a client restart. */
	private void savePotionVariantPref(String styleKey, CombatIcons.BoostPotion variant)
	{
		if (configManager == null)
		{
			return;
		}
		configManager.setConfiguration(OSPulseConfig.GROUP, potionVariantConfigKey(styleKey), variant.name());
	}

	/**
	 * Reads the currently-selected blowpipe dart straight from config (mirrors
	 * {@link #loadPotionVariantPrefs}'s load pattern), falling back to
	 * {@link BlowpipeDart#DRAGON} — {@link OSPulseConfig#blowpipeDart}'s own
	 * default — when unset/stale/no {@link ConfigManager} (headless tests).
	 * Used only to mark the current pick in the right-click "Set darts"
	 * submenu (see {@link #buildExcludeItemPopup}); the LIVE readout itself
	 * reads the config value fresh via {@code SessionTracker}/{@code
	 * GearMapper} on the next gear snapshot, same as any other live gear change.
	 */
	private BlowpipeDart currentBlowpipeDart()
	{
		return BlowpipeDart.fromConfig(configManager);
	}

	/**
	 * Persists the picked blowpipe dart to config so it survives a client
	 * restart (mirrors {@link #savePotionVariantPref}), then re-ranks the
	 * readout immediately (mirrors the potion-swap/exclude-item pattern —
	 * see {@link #populatePotionVariantPopup}). No-ops without a {@link
	 * ConfigManager} (headless tests / the no-config-manager constructor).
	 */
	private void pickBlowpipeDart(BlowpipeDart dart)
	{
		if (configManager != null)
		{
			configManager.setConfiguration(OSPulseConfig.GROUP, BlowpipeDart.CONFIG_KEY, dart.name());
		}
		rankAndRender();
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
			boolean swappable = CombatIcons.variantsFor(style).length > 0;
			bestPotionToggle.setIcon(potionIcon(potion));
			bestPotionToggle.setToolTipText("Boosting potion applied: " + displayName(potion)
				+ (swappable ? " (right-click to swap)" : ""));
			bestPotionToggle.setRightClickHint(swappable);
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
			magicPotionVariantForCalc());
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
		updateGearOverrideNote();
	}

	/**
	 * Rebuilds {@link #gearOverrideNotePanel} from {@link MonsterGearOverrideRepository}
	 * for the currently selected target — one small orange advisory line per
	 * override (e.g. "vs Rune dragon: equip Insulated boots (Boots) — Halves
	 * the lightning special-attack damage."), hidden entirely when the target
	 * has none or no target is selected. Called whenever {@link #selectedMonster}
	 * changes (every {@link #updateTargetLabel} call site).
	 */
	private void updateGearOverrideNote()
	{
		gearOverrideNotePanel.removeAll();
		List<MonsterGearOverride> overrides = selectedMonster == null
			? Collections.emptyList()
			: MonsterGearOverrideRepository.getInstance().forMonster(selectedMonster.name());
		for (MonsterGearOverride override : overrides)
		{
			String raw = "⚠ vs " + selectedMonster.name() + ": equip "
				+ override.itemName() + " (" + slotDisplayName(override.slot()) + ") — " + override.reason();
			// A plain JLabel does not wrap, so the long advisory line was clipped
			// in the narrow side panel (Jonathan saw "...equip in selected boot..."
			// cut off). Render as width-constrained HTML so the FULL reason wraps
			// onto multiple lines. Escape the data-driven text first.
			JLabel line = PanelWidgets.emptyRowLabel("<html><div style='width:200px'>" + escapeHtml(raw) + "</div></html>");
			line.setForeground(ColorScheme.BRAND_ORANGE);
			gearOverrideNotePanel.add(line);
		}
		gearOverrideNotePanel.setVisible(!overrides.isEmpty());
		gearOverrideNotePanel.revalidate();
		gearOverrideNotePanel.repaint();
	}

	/** Human-readable slot name for the advisory note, e.g. {@code BOOTS} -&gt; {@code "Boots"}. */
	private static String slotDisplayName(MonsterGearOverride.Slot slot)
	{
		String raw = slot.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
	}

	/** Minimal HTML escaping for text placed inside an {@code <html>} JLabel (ampersand first). */
	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
		itemSearchRow.setVisible(true);
		itemSearchField.setToolTipText("Search " + SLOT_NAMES[slotOrdinal] + " items to try (what-if — your real gear is unaffected)");
		itemSearchField.requestFocusInWindow();
		populateItemList();
		body().revalidate();
		body().repaint();
	}

	/** Closes the item picker (search row + icon grid) — the X button, re-clicking the same slot, or picking an item all route here. */
	private void closeItemSearch()
	{
		searchOpenForSlot = -1;
		itemSearchRow.setVisible(false);
		itemGridScroll.setVisible(false);
		body().revalidate();
		body().repaint();
	}

	/** Refilters {@link #searchOpenForSlot}'s candidate items by the search box's text and rebuilds the icon grid. */
	private void populateItemList()
	{
		if (searchOpenForSlot < 0)
		{
			return;
		}
		filteredItems = EquipmentIndexRepository.getInstance().searchSlot(searchOpenForSlot, itemSearchField.getText());
		renderItemGrid();
		itemGridScroll.setVisible(!filteredItems.isEmpty());
		itemGridScroll.revalidate();
		body().revalidate();
		body().repaint();
	}

	/**
	 * Rebuilds the icon grid ({@link #itemGridPanel}, {@link #ITEM_GRID_COLUMNS}
	 * per row) from {@link #filteredItems} — one {@link ItemGridCell} per
	 * candidate: icon via the EDT-safe async {@code ItemManager.getImage(int)},
	 * tooltip = item name, click applies the override (same
	 * {@link #applyOverride} path the old text list used) and closes the
	 * picker.
	 */
	private void renderItemGrid()
	{
		itemGridPanel.removeAll();
		for (EquipmentIndexRepository.Entry entry : filteredItems)
		{
			itemGridPanel.add(new ItemGridCell(entry));
		}
		itemGridPanel.revalidate();
		itemGridPanel.repaint();
	}

	/**
	 * One clickable icon cell in the item-picker grid — icon only (no text
	 * label, to stay compact at {@link #ITEM_GRID_COLUMNS} per row in the
	 * narrow side panel), tooltip = the item's display name, click applies it
	 * as a what-if override for {@link #searchOpenForSlot} and closes the
	 * picker.
	 */
	private final class ItemGridCell extends JLabel
	{
		private ItemGridCell(EquipmentIndexRepository.Entry entry)
		{
			setOpaque(true);
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setHorizontalAlignment(SwingConstants.CENTER);
			setVerticalAlignment(SwingConstants.CENTER);
			setPreferredSize(new Dimension(ITEM_GRID_CELL_SIZE, ITEM_GRID_CELL_SIZE));
			setToolTipText(entry.name());
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
			if (itemManager != null)
			{
				// EDT-safe: getImage is async (AsyncBufferedImage), the same
				// pattern used by the worn-gear grid and boost toggles.
				itemManager.getImage(entry.itemId()).addTo(this);
			}
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					applyOverride(searchOpenForSlot, entry.itemId());
					closeItemSearch();
				}
			});
		}
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
		resetBankHighlightToggle();
		userPickedStyle = false;
		userPickedSpell = false;
		closeItemSearch();
		updateGearGrid(lastGear);
		rankAndRender();
	}

	/**
	 * Full panel reset (feature 11): returns the gear panel to a fresh state —
	 * no target monster, no locked style/spell, no what-if overrides and no
	 * optimiser preview — while deliberately KEEPING the user's persisted
	 * preferences ({@link #potionVariantByStyle} and {@link #excludedItemIds}),
	 * which are config-backed choices, not session-tracking state.
	 *
	 * <p>The live gear/DPS readout itself is not cleared: equipment is live
	 * (re-supplied every {@link #apply}), so it re-defaults to the best style
	 * for the worn weapon rather than blanking out. Clearing
	 * {@link #lastRankedWeaponId} forces that re-default; {@link #resetAllOverrides}
	 * then drops the override, hides the optimiser result, closes the item
	 * search and re-renders.
	 */
	@Override
	public void resetState()
	{
		suppressListEvents = true;
		try
		{
			monsterSearchField.setText("");
			populateMonsterList("");
			monsterList.clearSelection();
		}
		finally
		{
			suppressListEvents = false;
		}
		selectedMonster = null;
		selectedStyle = null;
		lastRankedWeaponId = Integer.MIN_VALUE;
		lastRankedTarget = null;
		selectedSpell = null;
		selectedBook = BookTab.STANDARD;
		magicCastStyle = null;
		currentWeaponCategory = null;
		lastDps = 0.0;
		baselineDps = 0.0;
		lastWealth = null;
		optimizerStyle = null;
		optimizerStyleUserPicked = false;
		updateTargetLabel();
		// Reuse the shared what-if/optimiser teardown: it also clears
		// userPickedStyle/userPickedSpell, hides the optimiser panel, closes the
		// item search and re-renders the grid + readout from the live gear.
		resetAllOverrides();
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
		// Item #6b: the literal triangle glyph (▲/▼) is gone — the number
		// itself is coloured green/red (DELTA_UP_COLOR/DELTA_DOWN_COLOR) and a
		// plain "->" arrow separates the two compared values, matching the
		// panel's existing green/red gain styling elsewhere.
		java.awt.Color color = delta > 1e-9 ? DELTA_UP_COLOR
			: delta < -1e-9 ? DELTA_DOWN_COLOR : java.awt.Color.WHITE;
		whatIfDeltaValue.setForeground(color);
		whatIfDeltaValue.setText(String.format(Locale.ROOT, "%.2f -> %.2f", baselineDps, lastDps));
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
	 * Combines a plain numeric field's text with a K/M segmented toggle's
	 * current selection into the same "10m"/"500k" shape {@link #parseBudget}
	 * has always accepted, then parses it — so the budget/expensive-threshold
	 * number fields feed {@link GearOptimizer.Request} exactly as the old
	 * single free-text budget field did. Neither toggle selected (shouldn't
	 * normally happen — see {@link #unitToggle}) is treated as a plain number
	 * (no unit multiplier).
	 */
	private static long parseUnitAmount(String numberText, JToggleButton kToggle, JToggleButton mToggle)
	{
		String suffix = mToggle.isSelected() ? "m" : kToggle.isSelected() ? "k" : "";
		return parseBudget((numberText == null ? "" : numberText.trim()) + suffix);
	}

	/** The optimiser budget from {@link #budgetField} + {@link #budgetKToggle}/{@link #budgetMToggle}. */
	private long resolvedBudget()
	{
		return parseUnitAmount(budgetField.getText(), budgetKToggle, budgetMToggle);
	}

	/** The "expensive item" gp threshold from {@link #expensiveThresholdField} + its K/M toggle. */
	private long resolvedExpensiveThreshold()
	{
		return parseUnitAmount(expensiveThresholdField.getText(), expensiveThresholdKToggle, expensiveThresholdMToggle);
	}

	/** The "expensive items to allow" count from {@link #expensiveCountField} — blank/unparseable/negative treated as 0. */
	private int resolvedExpensiveCount()
	{
		try
		{
			return Math.max(0, Integer.parseInt(expensiveCountField.getText().trim()));
		}
		catch (NumberFormatException | NullPointerException e)
		{
			return 0;
		}
	}

	/**
	 * A compact two-button segmented K/M toggle (mirrors {@link #bookTabButton}'s
	 * selected/unselected styling) backing a numeric field's unit — exactly one
	 * of the two is ever selected via a shared {@link ButtonGroup}, and neither
	 * button can be clicked off (a segmented toggle always has a selection).
	 * {@code defaultMillions} picks the initially-selected button before
	 * {@link #loadOptimizerPrefs} may override it from persisted config.
	 */
	private JPanel unitToggle(JToggleButton kToggle, JToggleButton mToggle, boolean defaultMillions)
	{
		ButtonGroup group = new ButtonGroup();
		group.add(kToggle);
		group.add(mToggle);
		Border selectedBorder = BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE);
		Border unselectedBorder = BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR);
		for (JToggleButton button : new JToggleButton[] {kToggle, mToggle})
		{
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setFocusPainted(false);
			button.setMargin(new Insets(2, 6, 2, 6));
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
		}
		mToggle.setSelected(defaultMillions);
		kToggle.setSelected(!defaultMillions);

		JPanel panel = new JPanel(new GridLayout(1, 2, 1, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.add(kToggle);
		panel.add(mToggle);
		return panel;
	}

	/**
	 * A "label [icon w/ tooltip] [field]" row for the optimiser's expensive-
	 * items settings — {@code tooltip} is attached to both the info icon and
	 * the row itself so hovering anywhere on the row explains the field, not
	 * just a tiny icon target.
	 */
	private JPanel labelledFieldRow(String labelText, String tooltip, java.awt.Component field)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setToolTipText(tooltip);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		JPanel labelPanel = new JPanel(new BorderLayout(3, 0));
		labelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		JLabel infoIcon = new JLabel("ⓘ"); // circled "i" — info/help marker
		infoIcon.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		infoIcon.setFont(FontManager.getRunescapeSmallFont());
		infoIcon.setToolTipText(tooltip);
		// A tiny bare JLabel is an unreliable tooltip target (bug 7b: the "ⓘ"
		// help text never appeared on hover): make sure the ToolTipManager is
		// really watching it, give the glyph a slightly larger hover target,
		// and force the tooltip machinery awake on mouse-enter — some
		// enter-only hovers (no intermediate MOUSE_MOVED inside the tiny
		// bounds) otherwise never trip the manager's show timer.
		infoIcon.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
		javax.swing.ToolTipManager.sharedInstance().registerComponent(infoIcon);
		infoIcon.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				javax.swing.ToolTipManager.sharedInstance().mouseMoved(new java.awt.event.MouseEvent(
					infoIcon, java.awt.event.MouseEvent.MOUSE_MOVED, e.getWhen(), 0,
					e.getX(), e.getY(), 0, false));
			}
		});
		labelPanel.add(label, BorderLayout.WEST);
		labelPanel.add(infoIcon, BorderLayout.EAST);

		row.add(labelPanel, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	// ------------------------------- optimiser settings persistence (budget/expensive)

	private static final String CONFIG_KEY_BUDGET_AMOUNT = "optimizerBudgetAmount";
	private static final String CONFIG_KEY_BUDGET_UNIT_MILLIONS = "optimizerBudgetUnitMillions";
	private static final String CONFIG_KEY_EXPENSIVE_COUNT = "optimizerExpensiveCount";
	private static final String CONFIG_KEY_EXPENSIVE_THRESHOLD_AMOUNT = "optimizerExpensiveThresholdAmount";
	private static final String CONFIG_KEY_EXPENSIVE_THRESHOLD_UNIT_MILLIONS = "optimizerExpensiveThresholdUnitMillions";

	/**
	 * Restores the budget amount/unit + expensive-items count/threshold from
	 * config (see {@link #saveOptimizerPrefs}) so they survive a client
	 * restart, mirroring {@link #loadPotionVariantPrefs}'s pattern. No-op
	 * without a {@link ConfigManager} (headless tests / the no-config-manager
	 * constructors).
	 */
	private void loadOptimizerPrefs()
	{
		if (configManager == null)
		{
			return;
		}
		String budgetAmount = configManager.getConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_BUDGET_AMOUNT);
		if (budgetAmount != null && !budgetAmount.isEmpty())
		{
			budgetField.setText(budgetAmount);
		}
		String budgetUnit = configManager.getConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_BUDGET_UNIT_MILLIONS);
		if (budgetUnit != null)
		{
			budgetMToggle.setSelected(Boolean.parseBoolean(budgetUnit));
			budgetKToggle.setSelected(!Boolean.parseBoolean(budgetUnit));
		}
		String expensiveCount = configManager.getConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXPENSIVE_COUNT);
		if (expensiveCount != null && !expensiveCount.isEmpty())
		{
			expensiveCountField.setText(expensiveCount);
		}
		String thresholdAmount = configManager.getConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXPENSIVE_THRESHOLD_AMOUNT);
		if (thresholdAmount != null && !thresholdAmount.isEmpty())
		{
			expensiveThresholdField.setText(thresholdAmount);
		}
		String thresholdUnit = configManager.getConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXPENSIVE_THRESHOLD_UNIT_MILLIONS);
		if (thresholdUnit != null)
		{
			expensiveThresholdMToggle.setSelected(Boolean.parseBoolean(thresholdUnit));
			expensiveThresholdKToggle.setSelected(!Boolean.parseBoolean(thresholdUnit));
		}
	}

	/** Persists the budget amount/unit + expensive-items count/threshold to config — see {@link #loadOptimizerPrefs}. */
	private void saveOptimizerPrefs()
	{
		if (configManager == null)
		{
			return;
		}
		configManager.setConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_BUDGET_AMOUNT, budgetField.getText());
		configManager.setConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_BUDGET_UNIT_MILLIONS,
			String.valueOf(budgetMToggle.isSelected()));
		configManager.setConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXPENSIVE_COUNT, expensiveCountField.getText());
		configManager.setConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXPENSIVE_THRESHOLD_AMOUNT,
			expensiveThresholdField.getText());
		configManager.setConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXPENSIVE_THRESHOLD_UNIT_MILLIONS,
			String.valueOf(expensiveThresholdMToggle.isSelected()));
	}

	private static final String CONFIG_KEY_EXCLUDED_ITEM_IDS = "optimizerExcludedItemIds";

	/** Restores {@link #excludedItemIds} from a comma-separated config value. No-op without a {@link ConfigManager}. */
	private void loadExcludedItemsPref()
	{
		if (configManager == null)
		{
			return;
		}
		String raw = configManager.getConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXCLUDED_ITEM_IDS);
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		excludedItemIds.clear();
		for (String part : raw.split(","))
		{
			try
			{
				excludedItemIds.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException e)
			{
				// Stale/corrupt entry — skip it rather than fail the whole restore.
			}
		}
	}

	/** Persists {@link #excludedItemIds} as a comma-separated config value — see {@link #loadExcludedItemsPref}. */
	private void saveExcludedItemsPref()
	{
		if (configManager == null)
		{
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (int id : excludedItemIds)
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(id);
		}
		configManager.setConfiguration(OSPulseConfig.GROUP, CONFIG_KEY_EXCLUDED_ITEM_IDS, sb.toString());
	}

	/**
	 * A boost/task toggle (best potion, best prayer, on-task) changed: re-rank
	 * the worn-gear list AND — if an optimiser search is already on screen —
	 * re-run it, so the "Best setup for this target" numbers track the toggles
	 * instead of showing stale figures until the next "Find best setup" click.
	 * Mirrors the exclude-item / style-selector re-optimise pattern (see
	 * {@link #excludeItemFromSuggestions} / {@link #runOptimizer}). Only re-runs
	 * when a result exists, so idle toggling never kicks off a search.
	 */
	private void onBoostToggleChanged()
	{
		rankAndRender();
		if (lastOptimizerResult != null)
		{
			runOptimizer();
		}
	}

	/**
	 * Adds {@code itemId} to {@link #excludedItemIds}, persists it, and — if a
	 * search is already on screen — immediately re-runs the optimiser so the
	 * excluded item drops out and the next-best suggestion takes its place
	 * without the user having to click "Find best setup" again. Re-rendering the
	 * cached {@link #lastOptimizerResult} would just show the stale result (which
	 * still contains the excluded item), which is why we re-optimise here — the
	 * same pattern the style selector uses (see {@link #runOptimizer}).
	 */
	private void excludeItemFromSuggestions(int itemId)
	{
		if (itemId <= 0 || !excludedItemIds.add(itemId))
		{
			return;
		}
		saveExcludedItemsPref();
		renderExcludedItemsList();
		if (lastOptimizerResult != null)
		{
			runOptimizer();
		}
	}

	/**
	 * Removes {@code itemId} from {@link #excludedItemIds}, persists the change,
	 * refreshes the viewer, and — if a search is on screen — re-optimises so the
	 * item can immediately reappear as a suggestion. The counterpart to
	 * {@link #excludeItemFromSuggestions}.
	 */
	private void removeExcludedItem(int itemId)
	{
		if (!excludedItemIds.remove(itemId))
		{
			return;
		}
		saveExcludedItemsPref();
		renderExcludedItemsList();
		if (lastOptimizerResult != null)
		{
			runOptimizer();
		}
	}

	/**
	 * Rebuilds the excluded-items viewer ({@link #excludedItemsList}) from
	 * {@link #excludedItemIds} — one icon-only cell per excluded item (a ✕ in
	 * the top-right corner stops excluding it), filtered by the
	 * {@link #excludedSearchField} text. The whole {@link #excludedItemsPanel}
	 * hides when nothing is excluded (regardless of the current filter) so it
	 * never adds empty clutter, but a filter that matches nothing still leaves
	 * the panel (and its search box) up so the user can clear the filter. Only
	 * the user's manual exclusions appear here; mode-based
	 * {@code restrictedItemIds()} (Deadman/LMS filters) are deliberately not shown.
	 */
	private void renderExcludedItemsList()
	{
		if (excludedItemsList == null)
		{
			return; // called before construction finished — nothing to do yet
		}
		excludedItemsList.removeAll();
		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		String filter = excludedSearchField == null ? "" : excludedSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
		for (int itemId : excludedItemIds)
		{
			String name = itemDisplayName(index, itemId);
			if (!filter.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).contains(filter))
			{
				continue;
			}
			excludedItemsList.add(buildExcludedCell(itemId, name));
		}
		excludedItemsPanel.setVisible(!excludedItemIds.isEmpty());
		excludedItemsPanel.revalidate();
		excludedItemsPanel.repaint();
	}

	/**
	 * One icon-only cell for the excluded-items grid: the item sprite filling
	 * the cell with a small ✕ overlaid in the top-right corner (via
	 * {@link OverlayLayout}) that stops excluding the item. The name is carried
	 * only as a tooltip — see {@link #renderExcludedItemsList}.
	 */
	private JPanel buildExcludedCell(int itemId, String name)
	{
		JLabel icon = swapItemIcon(itemId, name);
		icon.setAlignmentX(0.5f);
		icon.setAlignmentY(0.5f);

		// A transparent overlay the exact same size + alignment as the icon, so
		// OverlayLayout stacks the two dead-centre on each other; the ✕ is then
		// pinned to this overlay's own top-right via BorderLayout.NORTH. (Giving
		// the two cell children mismatched alignments instead would offset them,
		// since OverlayLayout aligns by each child's alignment *point*.)
		JButton remove = new JButton("✕"); // ✕ — stop excluding this item
		remove.setFont(FontManager.getRunescapeSmallFont());
		remove.setFocusPainted(false);
		remove.setBorder(null);
		remove.setMargin(new Insets(0, 0, 0, 0));
		remove.setContentAreaFilled(false);
		remove.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		remove.setToolTipText("Stop excluding " + name + " — allow it in suggestions again");
		remove.addActionListener(e -> removeExcludedItem(itemId));

		JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		topRight.setOpaque(false);
		topRight.add(remove);

		JPanel overlay = new JPanel(new BorderLayout());
		overlay.setOpaque(false);
		overlay.setAlignmentX(0.5f);
		overlay.setAlignmentY(0.5f);
		overlay.setPreferredSize(new Dimension(ITEM_GRID_CELL_SIZE, ITEM_GRID_CELL_SIZE));
		overlay.setMaximumSize(new Dimension(ITEM_GRID_CELL_SIZE, ITEM_GRID_CELL_SIZE));
		overlay.setToolTipText(name + " — excluded from optimiser suggestions");
		overlay.add(topRight, BorderLayout.NORTH);

		JPanel cell = new JPanel();
		cell.setLayout(new OverlayLayout(cell));
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// The overlay is added first so it (and its ✕) paints on top of the icon.
		cell.add(overlay);
		cell.add(icon);
		return cell;
	}

	/**
	 * Fortified/imbued variant name suffixes (space-prefixed, as they appear
	 * at the end of an {@link EquipmentIndexRepository.Entry#name()}) whose
	 * plain form should also count as owned — see {@link #addVariantPlainForm}.
	 */
	private static final String[] OWNED_VARIANT_SUFFIXES = { " (f)", " (i)" };

	/**
	 * The owned-item pool + GE prices for the optimiser: every worn item
	 * (always owned, price irrelevant) plus the wealth snapshot's COMPLETE
	 * owned-item map ({@link WealthSnapshot#getAllHoldings()} — inventory +
	 * equipment + bank + pouches, see {@link #lastWealth}) filtered to items
	 * the {@link EquipmentIndexRepository} actually indexes (so the optimiser
	 * never tries to equip e.g. raw materials). Built fresh per search so it
	 * always reflects the latest snapshot.
	 *
	 * <p><b>Membership-based ownership:</b> ownership is "do you have the item
	 * at all", fully decoupled from GE value. The previous source —
	 * {@link WealthSnapshot#getTopHoldings()}, a top-50-BY-VALUE view — wrongly
	 * dropped owned low/zero-value items (e.g. an untradeable fire cape in the
	 * bank never makes a by-value cut), making them look purchasable-only.
	 * {@code getAllHoldings()} has no such truncation; {@code getTopHoldings()}
	 * remains only as a legacy fallback for snapshots built without the full
	 * map (older tests/callers).
	 *
	 * <p><b>Variant-aware ownership (bug B):</b> owning an upgraded variant —
	 * a name ending in " (f)" (fortified, e.g. "Masori body (f)") or " (i)"
	 * (imbued, e.g. "Berserker ring (i)") — also marks the PLAIN form (e.g.
	 * "Masori body"/"Berserker ring") as owned at price 0, resolved generically
	 * via {@link EquipmentIndexRepository#idForName}. Without this the
	 * optimiser has no idea an owned upgrade already supersedes the plain
	 * item, and will happily suggest buying the plain form as an "upgrade".
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
					addVariantPlainForm(prices, index, id);
				}
			}
		}
		if (lastWealth != null)
		{
			java.util.Collection<ItemStack> ownedStacks = !lastWealth.getAllHoldings().isEmpty()
				? lastWealth.getAllHoldings().values()
				: lastWealth.getTopHoldings();
			for (ItemStack stack : ownedStacks)
			{
				if (index.entryFor(stack.getId()) != null)
				{
					prices.merge(stack.getId(), Math.max(0L, stack.getUnitValue()), Math::min);
					addVariantPlainForm(prices, index, stack.getId());
				}
			}
		}
		return prices;
	}

	/**
	 * If {@code ownedItemId}'s indexed name ends in one of
	 * {@link #OWNED_VARIANT_SUFFIXES}, looks up the plain (suffix-stripped)
	 * name's item id and marks it owned at price 0 too — see
	 * {@link #ownedPriceMap}'s javadoc for why.
	 */
	private static void addVariantPlainForm(java.util.Map<Integer, Long> prices, EquipmentIndexRepository index,
		int ownedItemId)
	{
		EquipmentIndexRepository.Entry entry = index.entryFor(ownedItemId);
		if (entry == null)
		{
			return;
		}
		String name = entry.name();
		for (String suffix : OWNED_VARIANT_SUFFIXES)
		{
			if (name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length()))
			{
				String plainName = name.substring(0, name.length() - suffix.length());
				Integer plainId = index.idForName(plainName);
				if (plainId != null)
				{
					prices.putIfAbsent(plainId, 0L);
				}
				return;
			}
		}
	}

	/**
	 * Builds the 5-way Ranged/Magic/Crush/Slash/Stab selector (item #6e) —
	 * a segmented control mirroring {@link #unitToggle}'s styling. Exactly one
	 * button is ever selected (shared {@link ButtonGroup}); a USER click on a
	 * different type re-runs any visible search for that type immediately
	 * (programmatic {@code setSelected} from {@link #syncOptimizerStyleSelector}
	 * only fires the ItemListener restyle, never the ActionListener, so the
	 * follow-the-weapon auto-sync can never trigger a search by itself).
	 */
	private JPanel buildOptimizerStyleSelector()
	{
		ButtonGroup group = new ButtonGroup();
		Border selectedBorder = BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE);
		Border unselectedBorder = BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR);
		JPanel panel = new JPanel(new GridLayout(1, OPTIMIZER_STYLE_ORDER.length, 1, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setToolTipText("Optimise the best setup for this damage type — follows your equipped weapon's "
			+ "current style until you pick one yourself");
		for (int i = 0; i < OPTIMIZER_STYLE_ORDER.length; i++)
		{
			CombatStyle style = OPTIMIZER_STYLE_ORDER[i];
			ImageIcon icon = styleIcon(style);
			// Show the damage-type icon; fall back to the text label if the
			// bundled sprite is missing so the control is never blank.
			JToggleButton button = icon != null ? new JToggleButton(icon) : new JToggleButton(typeLabel(style));
			button.setToolTipText("Find the best " + typeLabel(style)
				+ " setup (owned gear + anything affordable within the budget)");
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setFocusPainted(false);
			button.setMargin(new Insets(2, 0, 2, 0));
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
			button.addActionListener(e ->
			{
				if (optimizerStyle == style)
				{
					return; // re-clicking the active type: nothing to do (group keeps it selected)
				}
				optimizerStyle = style;
				optimizerStyleUserPicked = true;
				if (lastOptimizerResult != null)
				{
					// A search is on screen — re-run it for the new type so the
					// selector always describes the result being shown.
					runOptimizer();
				}
			});
			group.add(button);
			optimizerStyleButtons[i] = button;
			panel.add(button);
		}
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		optimizerStyleSelectorPanel = panel;
		return panel;
	}

	/**
	 * The combat style the optimiser should target when the user has not
	 * explicitly picked one (item #6g): the live readout's currently selected
	 * style — which {@link #rankAndRender} always resolves from the EQUIPPED
	 * (or what-if) weapon's real combat options, so a wand detects MAGIC and a
	 * bow RANGED — falling back to the weapon's first style pre-target.
	 */
	private CombatStyle detectedCombatStyle()
	{
		if (selectedStyle != null)
		{
			return selectedStyle.type();
		}
		List<WeaponStyle> styles = weaponRepo.stylesForItem(effectiveWeaponId());
		return styles.isEmpty() ? null : styles.get(0).type();
	}

	/** The damage-type constraint for the next optimiser run: the user's pick, else the detected current style. */
	private CombatStyle optimizerConstraint()
	{
		return optimizerStyle != null ? optimizerStyle : detectedCombatStyle();
	}

	/**
	 * Re-asserts the 5-way selector from {@link #optimizerStyle}, first
	 * re-detecting it from the equipped weapon unless the user has picked one
	 * manually. Called on every {@link #rankAndRender} so the selector tracks
	 * weapon swaps live (the manual pick is dropped on a weapon change, same
	 * as the readout's own style lock).
	 */
	private void syncOptimizerStyleSelector()
	{
		if (!optimizerStyleUserPicked)
		{
			CombatStyle detected = detectedCombatStyle();
			if (detected != null)
			{
				optimizerStyle = detected;
			}
		}
		for (int i = 0; i < OPTIMIZER_STYLE_ORDER.length; i++)
		{
			JToggleButton button = optimizerStyleButtons[i];
			boolean selected = OPTIMIZER_STYLE_ORDER[i] == optimizerStyle;
			if (button != null && button.isSelected() != selected)
			{
				button.setSelected(selected);
			}
		}
	}

	/** Callback invoked once a budget/owned-prices/price-source triple is ready — see {@link #withResolvedPrices}. */
	@FunctionalInterface
	private interface PriceReady
	{
		void run(long budget, java.util.Map<Integer, Long> ownedPrices, GearOptimizer.PriceSource priceSource);
	}

	/**
	 * Shared price-resolution scaffolding for both the single-style
	 * ({@link #runOptimizer}) and all-styles ({@link #runOptimizerAndRankStyles})
	 * search paths: guards on a selected target, flips the button/status UI
	 * into "Searching...", persists prefs, resolves the budget/owned-prices,
	 * and — once a {@link GearOptimizer.PriceSource} is available, either
	 * immediately (no resolver wired) or asynchronously (client-thread GE
	 * lookup) — hands them to {@code consumer}.
	 */
	private void withResolvedPrices(PriceReady consumer)
	{
		if (lastGear == null || selectedMonster == null)
		{
			optimizerStatusLabel.setText("Pick a target above first");
			optimizerStatusLabel.setVisible(true);
			optimizerResultPanel.setVisible(false);
			resetBankHighlightToggle();
			return;
		}

		findBestSetupButton.setEnabled(false);
		optimizerStatusLabel.setText("Searching...");
		optimizerStatusLabel.setVisible(true);
		optimizerResultPanel.setVisible(false);
		resetBankHighlightToggle();
		saveOptimizerPrefs();

		long budget = resolvedBudget();
		java.util.Map<Integer, Long> ownedPrices = ownedPriceMap();

		if (priceResolver == null)
		{
			// No client-thread price source available (headless test / not wired) —
			// legitimate owned-only fallback: an unpriced non-owned item resolves to
			// Long.MAX_VALUE (unaffordable at any budget); owned items are separately
			// marked affordable via .owned(...) regardless of price.
			consumer.run(budget, ownedPrices,
				resolveOptimizerPriceSource(id -> ownedPrices.getOrDefault(id, 0L), java.util.Collections.emptySet()));
			return;
		}

		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		java.util.Set<Integer> candidateIds = new java.util.HashSet<>(index.allItemIds());
		candidateIds.removeAll(ownedPrices.keySet());

		priceResolver.resolve(candidateIds, lookup ->
		{
			// .owned() already makes owned items free/affordable — no need to
			// special-case ownedPrices here. Anything the resolver didn't price
			// (unknown) or flagged untradeable falls back to Long.MAX_VALUE =
			// unaffordable — see resolveOptimizerPriceSource (bug D).
			consumer.run(budget, ownedPrices,
				resolveOptimizerPriceSource(id -> lookup.prices().getOrDefault(id, 0L), lookup.untradeableIds()));
		});
	}

	/** Runs {@link GearOptimizer} off the EDT for the currently selected style and publishes the result back via {@link #onOptimizerResult}. */
	private void runOptimizer()
	{
		withResolvedPrices((budget, ownedPrices, priceSource) ->
			runOptimizerSearch(buildOptimizerRequest(budget, ownedPrices, priceSource, optimizerConstraint())));
	}

	/**
	 * The "Find best setup" button's action (item #6e): runs the optimiser for
	 * ALL FIVE damage types (not just the selected one), renders the selected
	 * style's result exactly as {@link #runOptimizer} would, and then reorders
	 * the style selector buttons left-to-right by best-achievable DPS. Unlike
	 * {@link #runOptimizer}, this is deliberately NOT used by the toggle/
	 * exclude-item/style-selector re-runs — those stay single-style so they
	 * stay responsive.
	 */
	private void runOptimizerAndRankStyles()
	{
		withResolvedPrices((budget, ownedPrices, priceSource) ->
		{
			CombatStyle selected = optimizerConstraint();
			java.util.Map<CombatStyle, GearOptimizer.Request> requests = new java.util.LinkedHashMap<>();
			for (CombatStyle style : OPTIMIZER_STYLE_ORDER)
			{
				requests.put(style, buildOptimizerRequest(budget, ownedPrices, priceSource, style));
			}

			new javax.swing.SwingWorker<java.util.Map<CombatStyle, GearOptimizer.Result>, Void>()
			{
				@Override
				protected java.util.Map<CombatStyle, GearOptimizer.Result> doInBackground()
				{
					java.util.Map<CombatStyle, GearOptimizer.Result> results = new java.util.LinkedHashMap<>();
					for (java.util.Map.Entry<CombatStyle, GearOptimizer.Request> entry : requests.entrySet())
					{
						results.put(entry.getKey(), GearOptimizer.optimize(entry.getValue()));
					}
					return results;
				}

				@Override
				protected void done()
				{
					try
					{
						java.util.Map<CombatStyle, GearOptimizer.Result> results = get();
						onOptimizerResult(results.get(selected));
						reorderSelectorsByDps(results);
					}
					catch (java.util.concurrent.ExecutionException | InterruptedException e)
					{
						optimizerStatusLabel.setText("Search failed");
						findBestSetupButton.setEnabled(true);
					}
				}
			}.execute();
		});
	}

	/**
	 * Reorders the visual layout of {@link #optimizerStyleSelectorPanel} so
	 * the five style buttons read left-to-right by best-achievable DPS
	 * (highest first), stable on ties (keeping {@link #OPTIMIZER_STYLE_ORDER}'s
	 * order). A style with no usable weapon at all ({@code result.style() ==
	 * null}, or a missing result) sorts last. Only the VISUAL order changes —
	 * {@link #OPTIMIZER_STYLE_ORDER} and {@link #optimizerStyleButtons} stay
	 * untouched so selection logic ({@link #syncOptimizerStyleSelector}) keeps
	 * working unmodified.
	 */
	private void reorderSelectorsByDps(java.util.Map<CombatStyle, GearOptimizer.Result> results)
	{
		if (optimizerStyleSelectorPanel == null)
		{
			return;
		}

		Integer[] order = new Integer[OPTIMIZER_STYLE_ORDER.length];
		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) ->
		{
			double dpsA = bestDps(results.get(OPTIMIZER_STYLE_ORDER[a]));
			double dpsB = bestDps(results.get(OPTIMIZER_STYLE_ORDER[b]));
			// Arrays.sort on an Integer[] (object array) is a stable mergesort,
			// so equal-dps styles keep their original OPTIMIZER_STYLE_ORDER
			// position without an explicit tiebreak.
			return Double.compare(dpsB, dpsA);
		});

		optimizerStyleSelectorPanel.removeAll();
		for (int idx : order)
		{
			optimizerStyleSelectorPanel.add(optimizerStyleButtons[idx]);
		}
		optimizerStyleSelectorPanel.revalidate();
		optimizerStyleSelectorPanel.repaint();
	}

	/** The DPS to rank a style by in {@link #reorderSelectorsByDps} — unusable styles sort last. */
	private static double bestDps(GearOptimizer.Result result)
	{
		return (result == null || result.style() == null) ? Double.NEGATIVE_INFINITY : result.dps().dps();
	}

	/**
	 * Untradeable weapons that are nonetheless "buyable" because they are
	 * crafted directly from ONE tradeable GE ingredient — priced at that
	 * ingredient, overriding the blanket untradeable-=-unpurchasable rule
	 * below. Currently just the Scorching bow (crafted from a Tormented
	 * synapse, id 29580, + a ~1k Magic longbow (u) at 74 Fletching — the
	 * synapse IS the price; both ids verified against the OSRS Wiki
	 * 2026-07-07). The other two synapse weapons are deliberately NOT
	 * mapped: Emberlight's base item (Arclight) is itself untradeable, and
	 * the Purging staff is a magic weapon with no optimiser demand yet —
	 * add entries here only when the full craft cost is genuinely ~one
	 * tradeable ingredient.
	 */
	private static final java.util.Map<Integer, Integer> UNTRADEABLE_CRAFT_INGREDIENT =
		java.util.Map.of(29591 /* Scorching bow */, 29580 /* Tormented synapse */);

	/**
	 * Wraps a raw (unowned-item) GE price lookup with two rules (bug D):
	 * <ul>
	 *   <li><b>untradeable = unpurchasable:</b> an UNOWNED item flagged
	 *       untradeable by the client-thread-precomputed
	 *       {@link PriceLookup#untradeableIds()} can never be bought, whatever
	 *       price the raw lookup reports — RuneLite's
	 *       {@code ItemManager.getItemPrice} routes some untradeables through
	 *       {@code ItemMapping} to a tradeable proxy (e.g. every
	 *       trouver-locked item, including Dragon defender (l) 24143 and Fire
	 *       cape (l) 24223, "costs" the Trouver parchment's ~1m GE price),
	 *       which made the optimiser recommend buying items that cannot be
	 *       bought. Tradeability comes from the precomputed set (the source of
	 *       truth), NOT from a hand-maintained per-item override. Owned
	 *       untradeables never reach this path — they are priced 0 via
	 *       {@code .owned()} directly;</li>
	 *   <li>a resolved price &lt;= 0 means untradeable/unpriced, not free —
	 *       an UNOWNED item you cannot buy is unaffordable
	 *       ({@link Long#MAX_VALUE}), not a bargain;</li>
	 *   <li><b>craftable-from-one-ingredient exception:</b> the few
	 *       untradeables in {@link #UNTRADEABLE_CRAFT_INGREDIENT} price at
	 *       their tradeable ingredient's GE cost INSTEAD of the two rules
	 *       above (checked first) — e.g. the untradeable Scorching bow "costs"
	 *       a Tormented synapse, so the optimiser can recommend it to a
	 *       non-owner and the spend readout shows the real acquisition cost
	 *       rather than the bogus ~1m ItemMapping value or a blanket
	 *       "unbuyable".</li>
	 * </ul>
	 */
	private static GearOptimizer.PriceSource resolveOptimizerPriceSource(GearOptimizer.PriceSource rawPriceSource,
		java.util.Set<Integer> untradeableIds)
	{
		return itemId ->
		{
			Integer ingredientId = UNTRADEABLE_CRAFT_INGREDIENT.get(itemId);
			if (ingredientId != null)
			{
				long ingredientPrice = rawPriceSource.priceFor(ingredientId);
				return ingredientPrice > 0 ? ingredientPrice : Long.MAX_VALUE;
			}
			if (untradeableIds.contains(itemId))
			{
				return Long.MAX_VALUE;
			}
			long resolved = rawPriceSource.priceFor(itemId);
			return resolved > 0 ? resolved : Long.MAX_VALUE;
		};
	}

	/**
	 * Matches a parenthesised game-mode marker in an item's display name —
	 * Deadman Mode, Bounty Hunter, Last Man Standing/LMS, or a league beta
	 * cosmetic. These items are not usable by a normal-mode player and must
	 * never be suggested by the optimiser (bug C).
	 */
	private static final java.util.regex.Pattern MODE_LOCKED_NAME_PATTERN = java.util.regex.Pattern.compile(
		"(?i)\\((deadman mode|deadman|bh|lms|last man standing|beta)\\)");

	/** True when an item's indexed display name carries a mode-locked marker — see {@link #MODE_LOCKED_NAME_PATTERN}. */
	static boolean isModeLockedItem(String name)
	{
		return name != null && MODE_LOCKED_NAME_PATTERN.matcher(name).find();
	}

	/**
	 * Name suffixes of The Gauntlet's instance-only weapon/armour tiers —
	 * "Crystal/Corrupted X (basic|attuned|perfected)" (ids 23840–23903 +
	 * 30340). These are REAL main-game items but exist only INSIDE the
	 * Gauntlet (made from crystal shards in the instance, unusable/lost the
	 * moment the player leaves), so they can never be equipped against a
	 * normal overworld target and must never be optimiser candidates. Their
	 * names carry no "(deadman)/(lms)"-style mode marker, so
	 * {@link #MODE_LOCKED_NAME_PATTERN} cannot catch them — the suffix IS the
	 * marker. The suffix-less main-game crystal armour ("Crystal helm/body/
	 * legs", distinct ids 23971–23981) is untouched by this rule.
	 */
	private static final String[] GAUNTLET_ONLY_NAME_SUFFIXES = { " (basic)", " (attuned)", " (perfected)" };

	/** True when an item's indexed display name is a Gauntlet-instance-only tier — see {@link #GAUNTLET_ONLY_NAME_SUFFIXES}. */
	static boolean isGauntletOnlyItem(String name)
	{
		if (name == null)
		{
			return false;
		}
		for (String suffix : GAUNTLET_ONLY_NAME_SUFFIXES)
		{
			if (name.length() >= suffix.length()
				&& name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Every indexed item id the player can never actually use against a
	 * normal target — added to the optimiser's exclude set every search,
	 * regardless of price or ownership:
	 * <ul>
	 *   <li>mode-locked names (bug C): Deadman/BH/LMS/beta-only items, which
	 *       the user (not being in those modes) cannot use;</li>
	 *   <li>Gauntlet-instance-only tiers — see
	 *       {@link #GAUNTLET_ONLY_NAME_SUFFIXES}.</li>
	 * </ul>
	 */
	static java.util.Set<Integer> restrictedItemIds()
	{
		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (Integer id : index.allItemIds())
		{
			EquipmentIndexRepository.Entry entry = index.entryFor(id);
			if (entry != null && (isModeLockedItem(entry.name()) || isGauntletOnlyItem(entry.name())))
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/**
	 * Item ids the optimiser search MUST use for {@code target} (via
	 * {@link GearOptimizer.Request.Builder#include}) — the curated
	 * {@link MonsterGearOverrideRepository} entries for that monster, so a
	 * mechanic-critical item (e.g. Insulated boots vs Rune dragons) can never
	 * be dropped by DPS ranking. A user's explicit slot exclusion still wins
	 * (an item present in {@code exclusions} is left out of the forced set
	 * rather than fighting the exclude list).
	 */
	private static java.util.Set<Integer> mandatoryOverrideItemIds(Monster target, java.util.Set<Integer> exclusions)
	{
		if (target == null)
		{
			return Collections.emptySet();
		}
		java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
		for (MonsterGearOverride override : MonsterGearOverrideRepository.getInstance().forMonster(target.name()))
		{
			if (!exclusions.contains(override.itemId()))
			{
				ids.add(override.itemId());
			}
		}
		return ids;
	}

	/**
	 * Builds the {@link GearOptimizer.Request} shared by both the resolver and
	 * no-resolver price paths ({@link #withResolvedPrices}), for the given
	 * {@code styleConstraint} — the single-style caller passes
	 * {@link #optimizerConstraint()}; the all-styles ranker
	 * ({@link #runOptimizerAndRankStyles}) passes each of
	 * {@link #OPTIMIZER_STYLE_ORDER} in turn.
	 */
	private GearOptimizer.Request buildOptimizerRequest(long budget, java.util.Map<Integer, Long> ownedPrices,
		GearOptimizer.PriceSource priceSource, CombatStyle styleConstraint)
	{
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
			.magicPotionVariant(magicPotionVariantForCalc());

		java.util.Set<Integer> exclusions = new java.util.LinkedHashSet<>(excludedItemIds);
		exclusions.addAll(restrictedItemIds());

		return GearOptimizer.Request
			.builder(liveIds, target, template)
			.budget(budget)
			.owned(ownedPrices.keySet())
			.exclude(exclusions)
			.include(mandatoryOverrideItemIds(target, exclusions))
			.priceSource(priceSource)
			.expensiveItemCount(resolvedExpensiveCount())
			.expensiveItemThreshold(resolvedExpensiveThreshold())
			// Items #6e/#6g: anchor the search to the requested damage type,
			// which (for the single-style caller) defaults to the EQUIPPED
			// weapon's current style — never an implicit best-of-any-style
			// (i.e. usually melee) search.
			.style(styleConstraint)
			// Magic view only: pin the gear DPS to the selected spellbook tab so
			// swapping Standard/Ancient swaps the optimiser's magic spell too.
			.spellBook(magicView ? selectedBook.book() : null)
			.build();
	}

	/** Runs the given request off the EDT via {@code SwingWorker} and publishes the result back via {@link #onOptimizerResult}. */
	private void runOptimizerSearch(GearOptimizer.Request request)
	{
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
		lastOptimizerResult = result;

		CombatStyle constraint = optimizerConstraint();
		optimizerResultStyle.setText(result.style() != null
			? typeLabel(result.style().type())
			: (constraint != null ? typeLabel(constraint) : "-"));

		boolean anyChange = hasAnySlotChange(result);
		if (result.style() == null)
		{
			// Style-constrained search found NO loadout that can attack with the
			// requested type at all (e.g. Magic selected but no magic weapon is
			// owned or affordable) — say that, not a misleading "no upgrade".
			optimizerStatusLabel.setText("No usable " + (constraint != null ? typeLabel(constraint) : "")
				+ " weapon owned or affordable within budget");
			optimizerStatusLabel.setVisible(true);
		}
		else if (!anyChange)
		{
			// Fix 5: say so explicitly instead of leaving the user staring at a
			// "Best DPS found" panel that matches their current loadout with no
			// indication of whether that's a bug or just "you're already best".
			optimizerStatusLabel.setText("No upgrade found within budget / owned + affordable pool");
			optimizerStatusLabel.setVisible(true);
		}
		else
		{
			optimizerStatusLabel.setVisible(false);
		}

		optimizerResultDps.setText(String.format(Locale.ROOT, "%.2f", result.dps().dps()));
		double delta = result.deltaDps();
		// Item #6b: no literal triangle glyph — "owned-only DPS -> best-found
		// DPS", the whole readout coloured green (upgrade) / red (downgrade),
		// matching the what-if row's styling (updateWhatIfDelta) and the
		// panel's existing green/red gain colours elsewhere.
		java.awt.Color deltaColor = delta > 1e-9 ? DELTA_UP_COLOR
			: delta < -1e-9 ? DELTA_DOWN_COLOR : java.awt.Color.WHITE;
		optimizerResultDelta.setForeground(deltaColor);
		optimizerResultDelta.setText(String.format(Locale.ROOT, "%.2f -> %.2f", result.ownedOnlyDps(), result.dps().dps()));
		optimizerResultSpend.setText(formatGp(result.totalSpend()));
		optimizerResultDpsPerGp.setText(result.totalSpend() > 0
			? String.format(Locale.ROOT, "%.6f", result.dpsPerGp())
			: "-");
		renderOptimizerSwapList(result);
		// Nothing to preview/clear when the suggestion equals what's already worn.
		applyOptimizerResultButton.setVisible(anyChange);
		clearOptimizerPreviewButton.setVisible(anyChange);
		showInBankButton.setVisible(true);
		optimizerResultPanel.setVisible(true);
		optimizerResultPanel.revalidate();
		body().revalidate();
		body().repaint();
	}

	/** True if the optimiser's proposed loadout differs from the currently worn gear in at least one slot. */
	private boolean hasAnySlotChange(GearOptimizer.Result result)
	{
		int[] liveIds = lastGear == null ? new int[GearSnapshot.EQUIPMENT_SLOT_COUNT] : lastGear.equippedItemIds();
		for (GearOptimizer.SlotChoice choice : result.loadout())
		{
			int liveId = choice.slotOrdinal() < liveIds.length ? liveIds[choice.slotOrdinal()] : -1;
			if (liveId != choice.itemId())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Renders "current item icon -&gt; suggested item icon" for every slot the
	 * optimiser actually wants to change (slots where the suggestion matches
	 * what's already worn are omitted — nothing to show there) — design ask:
	 * show the actual swaps, not just an aggregate "Best DPS found" number.
	 * Icons (not text names) so a swap reads at a glance, matching the
	 * worn-gear grid / item-picker's icon-first style; the slot + item names
	 * + spend move to the row's tooltip and a small caption underneath
	 * instead of inline text. Right-clicking the SUGGESTED icon offers
	 * "Exclude from suggestions" (item #6a).
	 */
	private void renderOptimizerSwapList(GearOptimizer.Result result)
	{
		optimizerSwapList.removeAll();
		int[] liveIds = lastGear == null ? new int[GearSnapshot.EQUIPMENT_SLOT_COUNT] : lastGear.equippedItemIds();
		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		boolean anyRow = false;
		for (GearOptimizer.SlotChoice choice : result.loadout())
		{
			int liveId = choice.slotOrdinal() < liveIds.length ? liveIds[choice.slotOrdinal()] : -1;
			if (liveId == choice.itemId())
			{
				continue; // unchanged — nothing to report for this slot
			}
			anyRow = true;
			optimizerSwapList.add(buildSwapRow(index, choice.slotOrdinal(), liveId, choice.itemId(), choice));
			optimizerSwapList.add(Box.createRigidArea(new Dimension(0, 2)));
		}
		if (!anyRow)
		{
			JLabel none = PanelWidgets.emptyRowLabel("No slot changes — your current loadout is already best");
			none.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			optimizerSwapList.add(none);
		}
	}

	/** One "current icon -&gt; suggested icon (spend)" swap row — see {@link #renderOptimizerSwapList}. */
	private JPanel buildSwapRow(EquipmentIndexRepository index, int slotOrdinal, int currentItemId,
		int suggestedItemId, GearOptimizer.SlotChoice choice)
	{
		String slotName = slotOrdinal >= 0 && slotOrdinal < SLOT_NAMES.length && !SLOT_NAMES[slotOrdinal].isEmpty()
			? SLOT_NAMES[slotOrdinal] : ("Slot " + slotOrdinal);
		String currentName = itemDisplayName(index, currentItemId);
		String suggestedName = itemDisplayName(index, suggestedItemId);
		String spend = choice.owned() ? "owned" : (formatGp(choice.price()) + " — not owned");

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel iconsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		iconsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		iconsPanel.add(swapItemIcon(currentItemId, currentName));
		JLabel arrow = new JLabel("→"); // "->" glyph between the two item icons
		arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		arrow.setFont(FontManager.getRunescapeSmallFont());
		arrow.setVerticalAlignment(SwingConstants.CENTER);
		iconsPanel.add(arrow);
		JLabel suggestedIcon = swapItemIcon(suggestedItemId, suggestedName + " (" + spend + ")");
		suggestedIcon.setComponentPopupMenu(buildExcludeItemPopup(suggestedItemId, suggestedName, -1));
		if (choice.owned())
		{
			iconsPanel.add(suggestedIcon);
		}
		else
		{
			// Item #6e rendering ask: a suggested item the player does NOT own
			// must be visually distinct from owned ones AND show its gp value
			// right by the item — gold border on the icon + a gold price label
			// directly beneath it (owned suggestions keep the plain icon).
			suggestedIcon.setBorder(BorderFactory.createLineBorder(NOT_OWNED_GOLD));
			JPanel notOwnedCell = new JPanel();
			notOwnedCell.setLayout(new BoxLayout(notOwnedCell, BoxLayout.Y_AXIS));
			notOwnedCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			suggestedIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
			notOwnedCell.add(suggestedIcon);
			JLabel price = new JLabel(formatGp(choice.price()));
			price.setName("notOwnedPrice"); // test hook + self-documenting component name
			price.setFont(FontManager.getRunescapeSmallFont());
			price.setForeground(NOT_OWNED_GOLD);
			price.setAlignmentX(Component.CENTER_ALIGNMENT);
			price.setToolTipText(suggestedName + " — not owned; GE price " + formatGp(choice.price()));
			notOwnedCell.add(price);
			iconsPanel.add(notOwnedCell);
		}
		row.add(iconsPanel, BorderLayout.WEST);

		JLabel caption = new JLabel(slotName + (choice.owned() ? " (owned)" : " (" + formatGp(choice.price()) + ")"));
		caption.setForeground(choice.owned() ? ColorScheme.MEDIUM_GRAY_COLOR : NOT_OWNED_GOLD);
		caption.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
		caption.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(caption, BorderLayout.EAST);

		row.setToolTipText(slotName + ": " + currentName + " -> " + suggestedName + " (" + spend
			+ ") — right-click the suggested icon to exclude it from future suggestions");
		// Height is content-driven now: a not-owned suggestion adds a price
		// label under its icon, so the old fixed ITEM_GRID_CELL_SIZE cap would
		// clip it.
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** One item-icon label for a swap row (see {@link #buildSwapRow}) — same async icon source as the worn-gear grid/item picker. */
	private JLabel swapItemIcon(int itemId, String tooltip)
	{
		JLabel icon = new JLabel();
		icon.setOpaque(true);
		icon.setBackground(ColorScheme.DARK_GRAY_COLOR);
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(ITEM_GRID_CELL_SIZE, ITEM_GRID_CELL_SIZE));
		icon.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		icon.setToolTipText(tooltip);
		if (itemManager != null && itemId > 0)
		{
			itemManager.getImage(itemId).addTo(icon);
		}
		return icon;
	}

	/**
	 * If {@code e} is a right-click/popup trigger over a slot cell currently
	 * SHOWING a real item (the previewed optimiser suggestion, a what-if swap,
	 * or live gear), pops the "Exclude from suggestions" menu for that item and
	 * returns {@code true}. The shown item id is read from {@link #renderedSlotIds}
	 * (what the cell last drew). Returns {@code false} for a non-popup click or
	 * an empty cell, so the caller falls through to the left-click swap search.
	 */
	private boolean maybeShowSlotExcludePopup(MouseEvent e, int slot)
	{
		if (!e.isPopupTrigger())
		{
			return false;
		}
		int shownId = slot >= 0 && slot < renderedSlotIds.length ? renderedSlotIds[slot] : -1;
		if (shownId <= 0)
		{
			return false;
		}
		String name = itemDisplayName(EquipmentIndexRepository.getInstance(), shownId);
		buildExcludeItemPopup(shownId, name, slot).show(e.getComponent(), e.getX(), e.getY());
		return true;
	}

	/**
	 * Right-click menu for a suggested-swap icon (item #6a): an "Exclude from
	 * suggestions" action, plus — for the WEAPON slot showing a blowpipe (see
	 * {@link GearVariants#isBlowpipe}) — a nested "Set darts" submenu (see
	 * {@link #populateBlowpipeDartSubmenu}) letting the loaded dart be picked
	 * by right-click instead of the (now-hidden) settings-panel dropdown.
	 */
	private javax.swing.JPopupMenu buildExcludeItemPopup(int itemId, String itemName, int slot)
	{
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem exclude = new javax.swing.JMenuItem("Exclude " + itemName + " from suggestions");
		exclude.addActionListener(e -> excludeItemFromSuggestions(itemId));
		menu.add(exclude);

		if (slot == WhatIfLoadout.WEAPON_SLOT && GearVariants.isBlowpipe(itemId))
		{
			javax.swing.JMenu dartsMenu = new javax.swing.JMenu("Set darts");
			populateBlowpipeDartSubmenu(dartsMenu);
			menu.add(dartsMenu);
		}
		return menu;
	}

	/**
	 * Fills {@code menu} with one item per {@link BlowpipeDart}, the currently
	 * selected one shown checked (see {@link #currentBlowpipeDart}). Picking a
	 * dart persists it and re-ranks (see {@link #pickBlowpipeDart}). Built as a
	 * nested {@link javax.swing.JMenu} added straight to the popup — NOT a
	 * second {@link javax.swing.JPopupMenu} shown manually via {@code
	 * menu.show(...)}, since clicking the parent item hides its popup first and
	 * {@code invoker.getLocationOnScreen()} then throws {@code
	 * IllegalComponentStateException} (the exact bug a manual second popup hit
	 * elsewhere in this project) — a nested JMenu cascades natively instead.
	 */
	private void populateBlowpipeDartSubmenu(javax.swing.JMenu menu)
	{
		BlowpipeDart current = currentBlowpipeDart();
		for (BlowpipeDart dart : BlowpipeDart.values())
		{
			javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(dart.toString());
			item.setState(dart == current);
			item.addActionListener(e -> pickBlowpipeDart(dart));
			menu.add(item);
		}
	}

	/** Display name for an item id via the bundled equipment index, or a placeholder for an empty/unindexed slot. */
	private static String itemDisplayName(EquipmentIndexRepository index, int itemId)
	{
		if (itemId <= 0)
		{
			return "(empty)";
		}
		EquipmentIndexRepository.Entry entry = index.entryFor(itemId);
		return entry != null ? entry.name() : ("item " + itemId);
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
		resetBankHighlightToggle();

		// Item #6g: the preview must show the DPS the optimiser actually
		// computed — i.e. lock the readout to the RESULT's style/spell instead
		// of letting rankAndRender re-default to the new weapon's best-of-any-
		// style (which could flip a Ranged/Magic-constrained preview to melee).
		// Pre-setting lastRankedWeaponId suppresses the weapon-change auto-reset
		// of these locks (and of the 5-way selector's user pick) for this
		// deliberate, style-aware swap.
		lastRankedWeaponId = effectiveWeaponId();
		if (lastOptimizerResult.style() != null)
		{
			selectedStyle = lastOptimizerResult.style();
			userPickedStyle = true;
		}
		if (lastOptimizerResult.spell() != null)
		{
			selectedSpell = lastOptimizerResult.spell();
			userPickedSpell = true;
			selectedBook = lastOptimizerResult.spell().book() == Spell.SpellBook.ANCIENT
				? BookTab.ANCIENT : BookTab.STANDARD;
			lastRankedTarget = selectedMonster; // keep the spell lock through the re-rank
		}

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
		// DPS itself isn't computable without a target (that's why we're here),
		// but the boost indicators (prayer/potion) are purely a function of the
		// EQUIPPED weapon's own best style, which rankAndRender() already
		// resolved into selectedStyle regardless of target — so still refresh
		// them here instead of blanking them, letting the potion icon track a
		// weapon swap even with no monster picked yet.
		updateBoostIndicators(selectedStyle != null ? selectedStyle.type() : null);
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

	/**
	 * An icon-toggle button that can paint a small "*" in its top-right corner
	 * as a "more options on right-click" hint (see {@link #setRightClickHint}) —
	 * used by the potion (and, if it ever gains options, prayer) boost toggle
	 * so the right-click swap menu is discoverable instead of hidden.
	 */
	private static final class HintableToggleButton extends JToggleButton
	{
		private boolean rightClickHint;

		void setRightClickHint(boolean hint)
		{
			if (hint != rightClickHint)
			{
				rightClickHint = hint;
				repaint();
			}
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			super.paintComponent(g);
			if (!rightClickHint)
			{
				return;
			}
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
					java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(ColorScheme.BRAND_ORANGE);
				Font markerFont = FontManager.getRunescapeBoldFont().deriveFont(11f);
				g2.setFont(markerFont);
				String marker = "*";
				java.awt.FontMetrics fm = g2.getFontMetrics();
				int x = getWidth() - fm.stringWidth(marker) - 2;
				int y = fm.getAscent();
				// A thin dark outline keeps the marker legible over a bright item icon.
				g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
				g2.drawString(marker, x - 1, y);
				g2.drawString(marker, x + 1, y);
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.drawString(marker, x, y);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** Bare icon-toggle button (border/background styling, no icon yet). */
	private HintableToggleButton newToggle(String tooltip)
	{
		HintableToggleButton button = new HintableToggleButton();
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
	private HintableToggleButton iconToggle(int itemId, String tooltip)
	{
		HintableToggleButton button = newToggle(tooltip);
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
	 * The potion toggle's right-click swap menu — rebuilt on every open (via
	 * the {@code PopupMenuListener} below) from {@link CombatIcons#variantsFor}
	 * for whatever combat style is CURRENTLY selected, so right-clicking on
	 * melee offers Super combat/strength/attack, ranged offers Ranging/
	 * Bastion/Divine ranging, and magic offers Saturated heart/Imbued heart/
	 * Ancient brew — never a style-inappropriate list (the original bug: the
	 * menu always showed the magic variants regardless of style). Picking an
	 * item sets that style's entry in {@link #potionVariantByStyle}, persists
	 * it to config (see {@link #savePotionVariantPref}) so it survives a
	 * client restart, and re-ranks so the swap immediately feeds
	 * {@link DpsCalculator} (Magic only — see {@link #magicPotionVariantForCalc}).
	 */
	private javax.swing.JPopupMenu buildPotionVariantPopup()
	{
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
			{
				populatePotionVariantPopup(menu);
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e)
			{
			}
		});
		return menu;
	}

	/** Rebuilds {@code menu}'s items from {@link CombatIcons#variantsFor} for the currently selected combat style. */
	private void populatePotionVariantPopup(javax.swing.JPopupMenu menu)
	{
		menu.removeAll();
		CombatStyle style = selectedStyle != null ? selectedStyle.type() : null;
		String styleKey = styleKeyFor(style);
		CombatIcons.BoostPotion[] variants = CombatIcons.variantsFor(style);
		for (CombatIcons.BoostPotion variant : variants)
		{
			javax.swing.JMenuItem item = new javax.swing.JMenuItem(displayName(variant));
			item.addActionListener(e ->
			{
				if (styleKey != null)
				{
					potionVariantByStyle.put(styleKey, variant);
					savePotionVariantPref(styleKey, variant);
				}
				rankAndRender();
			});
			menu.add(item);
		}
		if (variants.length == 0)
		{
			javax.swing.JMenuItem none = new javax.swing.JMenuItem("Pick a target/style first");
			none.setEnabled(false);
			menu.add(none);
		}
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

	/** Number of icon cells currently rendered in the item-picker grid — mirrors {@link #filteredItems}' size once populated. */
	int itemGridCellCountForTest()
	{
		return itemGridPanel.getComponentCount();
	}

	/** Simulates a real mouse click on the icon cell at {@code index} in the item-picker grid (exercises {@link ItemGridCell}'s own click handler, not just the {@link #filteredItems} seam). */
	void clickItemGridCellForTest(int index)
	{
		Component cell = itemGridPanel.getComponent(index);
		for (MouseListener listener : cell.getMouseListeners())
		{
			listener.mousePressed(new MouseEvent(cell, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 0, 0, 1, false));
		}
	}

	boolean itemGridVisibleForTest()
	{
		return itemGridScroll.isVisible();
	}

	boolean itemSearchRowVisibleForTest()
	{
		return itemSearchRow.isVisible();
	}

	/** Simulates clicking the item picker's close (X) button. */
	void clickCloseItemSearchForTest()
	{
		closeItemSearchButton.doClick();
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

	/** Item #6b: the coloured delta text's actual colour, so tests can assert green/red without a literal ▲/▼ glyph. */
	java.awt.Color whatIfDeltaColorForTest()
	{
		return whatIfDeltaValue.getForeground();
	}

	String optimizerResultDeltaTextForTest()
	{
		return optimizerResultDelta.getText();
	}

	java.awt.Color optimizerResultDeltaColorForTest()
	{
		return optimizerResultDelta.getForeground();
	}

	double baselineDpsForTest()
	{
		return baselineDps;
	}

	int renderedSlotIdForTest(int slotOrdinal)
	{
		return renderedSlotIds[slotOrdinal];
	}

	/**
	 * Test hook mirroring the right-click "Exclude from suggestions" on a slot
	 * cell: excludes whatever item that cell is currently SHOWING (read from
	 * {@link #renderedSlotIds}, exactly as {@link #maybeShowSlotExcludePopup}
	 * does), or no-ops for an empty cell.
	 */
	void rightClickExcludeSlotForTest(int slot)
	{
		int shownId = slot >= 0 && slot < renderedSlotIds.length ? renderedSlotIds[slot] : -1;
		if (shownId > 0)
		{
			excludeItemFromSuggestions(shownId);
		}
	}

	/**
	 * Test seam mirroring a real right-click on the WEAPON slot cell: builds
	 * the exact popup {@link #maybeShowSlotExcludePopup} would show for
	 * whatever item that cell is currently rendering (read from {@link
	 * #renderedSlotIds}), so tests can assert the "Set darts" submenu is
	 * present for a blowpipe and absent otherwise without driving real mouse
	 * events.
	 */
	javax.swing.JPopupMenu weaponSlotPopupForTest()
	{
		int shownId = renderedSlotIds[WhatIfLoadout.WEAPON_SLOT];
		String name = itemDisplayName(EquipmentIndexRepository.getInstance(), shownId);
		return buildExcludeItemPopup(shownId, name, WhatIfLoadout.WEAPON_SLOT);
	}

	/** Test seam: the currently-selected blowpipe dart (see {@link #currentBlowpipeDart}). */
	BlowpipeDart currentBlowpipeDartForTest()
	{
		return currentBlowpipeDart();
	}

	/** Test seam mirroring a real "Set darts" submenu pick (see {@link #pickBlowpipeDart}). */
	void pickBlowpipeDartForTest(BlowpipeDart dart)
	{
		pickBlowpipeDart(dart);
	}

	// --------------------------------------- Phase 3 optimiser test seams

	/**
	 * Test seam accepting the OLD single-field "10m"/"500k"/"0" shape
	 * {@link #parseBudget} has always parsed, splitting it into the new
	 * numeric-field + K/M-toggle pair ({@link #budgetField} +
	 * {@link #budgetKToggle}/{@link #budgetMToggle}) so existing tests
	 * written against the pre-redesign single-field contract keep working
	 * unchanged. Mirrors {@link #parseUnitAmount}'s suffix convention.
	 */
	void setBudgetTextForTest(String text)
	{
		String trimmed = text == null ? "" : text.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.endsWith("m"))
		{
			budgetField.setText(trimmed.substring(0, trimmed.length() - 1));
			budgetMToggle.setSelected(true);
		}
		else if (lower.endsWith("k"))
		{
			budgetField.setText(trimmed.substring(0, trimmed.length() - 1));
			budgetKToggle.setSelected(true);
		}
		else
		{
			budgetField.setText(trimmed);
		}
	}

	/**
	 * Runs the optimizer SYNCHRONOUSLY for tests (bypassing the real
	 * {@code SwingWorker}, whose background thread + {@code invokeLater}
	 * hand-off is awkward to await deterministically in a headless test) by
	 * mirroring {@link #runOptimizer}'s resolver-vs-owned-only branching and
	 * calling {@link #onOptimizerResult} directly on the calling (EDT)
	 * thread. If a fake {@link OptimizerPriceResolver} was injected via the
	 * 6-arg constructor, this exercises it too — as long as it calls back
	 * synchronously (as a test fake should), no threading is involved.
	 */
	void runOptimizerSyncForTest()
	{
		long budget = resolvedBudget();
		java.util.Map<Integer, Long> ownedPrices = ownedPriceMap();

		if (priceResolver == null)
		{
			GearOptimizer.Request request = buildOptimizerRequest(budget, ownedPrices,
				resolveOptimizerPriceSource(id -> ownedPrices.getOrDefault(id, 0L), java.util.Collections.emptySet()),
				optimizerConstraint());
			onOptimizerResult(GearOptimizer.optimize(request));
			return;
		}

		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		java.util.Set<Integer> candidateIds = new java.util.HashSet<>(index.allItemIds());
		candidateIds.removeAll(ownedPrices.keySet());
		// Mirror withResolvedPrices: craft-ingredient ids must be priced too.
		candidateIds.addAll(UNTRADEABLE_CRAFT_INGREDIENT.values());

		priceResolver.resolve(candidateIds, lookup ->
		{
			GearOptimizer.Request request = buildOptimizerRequest(budget, ownedPrices,
				resolveOptimizerPriceSource(id -> lookup.prices().getOrDefault(id, 0L), lookup.untradeableIds()),
				optimizerConstraint());
			onOptimizerResult(GearOptimizer.optimize(request));
		});
	}

	GearOptimizer.Result lastOptimizerResultForTest()
	{
		return lastOptimizerResult;
	}

	/** Test seam: {@link #ownedPriceMap()} (bug B — variant-aware ownership). */
	java.util.Map<Integer, Long> ownedPriceMapForTest()
	{
		return ownedPriceMap();
	}

	/** Test seam: {@link #resolveOptimizerPriceSource} (bug D — untradeable = unpurchasable pricing). */
	GearOptimizer.PriceSource resolveOptimizerPriceSourceForTest(GearOptimizer.PriceSource rawPriceSource,
		java.util.Set<Integer> untradeableIds)
	{
		return resolveOptimizerPriceSource(rawPriceSource, untradeableIds);
	}

	/** Test seam for item #1's budget K/M-toggle + expensive-items fields — see {@link #resolvedBudget}. */
	long resolvedBudgetForTest()
	{
		return resolvedBudget();
	}

	int resolvedExpensiveCountForTest()
	{
		return resolvedExpensiveCount();
	}

	long resolvedExpensiveThresholdForTest()
	{
		return resolvedExpensiveThreshold();
	}

	void setExpensiveCountTextForTest(String text)
	{
		expensiveCountField.setText(text);
	}

	/** Mirrors {@link #setBudgetTextForTest} for the expensive-threshold field's own K/M toggle. */
	void setExpensiveThresholdTextForTest(String text)
	{
		String trimmed = text == null ? "" : text.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.endsWith("m"))
		{
			expensiveThresholdField.setText(trimmed.substring(0, trimmed.length() - 1));
			expensiveThresholdMToggle.setSelected(true);
		}
		else if (lower.endsWith("k"))
		{
			expensiveThresholdField.setText(trimmed.substring(0, trimmed.length() - 1));
			expensiveThresholdKToggle.setSelected(true);
		}
		else
		{
			expensiveThresholdField.setText(trimmed);
		}
	}

	void setBudgetUnitMillionsForTest(boolean millions)
	{
		budgetMToggle.setSelected(millions);
		budgetKToggle.setSelected(!millions);
	}

	// ------------------------------- item #6e/#6g optimiser-style test seams

	/** The damage type the next optimiser run will be constrained to (user pick, else detected from the equipped weapon). */
	CombatStyle optimizerStyleForTest()
	{
		return optimizerConstraint();
	}

	boolean optimizerStyleUserPickedForTest()
	{
		return optimizerStyleUserPicked;
	}

	/** Simulates a user click on the 5-way selector's button for {@code style}. */
	void clickOptimizerStyleForTest(CombatStyle style)
	{
		for (int i = 0; i < OPTIMIZER_STYLE_ORDER.length; i++)
		{
			if (OPTIMIZER_STYLE_ORDER[i] == style)
			{
				optimizerStyleButtons[i].doClick();
				return;
			}
		}
		throw new IllegalArgumentException("no selector button for " + style);
	}

	String optimizerResultStyleTextForTest()
	{
		return optimizerResultStyle.getText();
	}

	/** How many not-owned price labels the suggested-swaps list currently renders (item #6e's owned-vs-not-owned rendering). */
	int notOwnedPriceLabelCountForTest()
	{
		return countComponentsNamed(optimizerSwapList, "notOwnedPrice");
	}

	private static int countComponentsNamed(java.awt.Container root, String name)
	{
		int count = 0;
		for (Component c : root.getComponents())
		{
			if (name.equals(c.getName()))
			{
				count++;
			}
			if (c instanceof java.awt.Container)
			{
				count += countComponentsNamed((java.awt.Container) c, name);
			}
		}
		return count;
	}

	/** The worn-gear grid cell's current tooltip for {@code slotOrdinal} (item #6f's live-vs-preview tooltip). */
	String slotTooltipForTest(int slotOrdinal)
	{
		return slotLabels[slotOrdinal].getToolTipText();
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

	/** Number of rows currently in the suggested-swaps list (item #6c: one row per changed slot, or one "no changes" row). */
	int optimizerSwapRowCountForTest()
	{
		return optimizerSwapList.getComponentCount();
	}

	/** Item #6a: the current exclude set (read-only copy), for asserting persistence/wiring. */
	java.util.Set<Integer> excludedItemIdsForTest()
	{
		return new java.util.LinkedHashSet<>(excludedItemIds);
	}

	/** Item #6a: simulates the "Exclude from suggestions" right-click action for {@code itemId} without driving real mouse/popup events. */
	void excludeItemFromSuggestionsForTest(int itemId)
	{
		excludeItemFromSuggestions(itemId);
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

	/**
	 * Item #6d: simulates a REAL mouse press on the style row's CENTER child
	 * label — the area users actually click, and the exact component that used
	 * to swallow the press (its tooltip's ToolTipManager listener made it the
	 * mouse-event target instead of the row). Dispatches to the child's own
	 * listeners only, exactly like Swing's deepest-target dispatch does.
	 */
	void pressStyleRowLabelForTest(int index)
	{
		StyleRow row = styleRows.get(index);
		Component child = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER);
		for (MouseListener listener : child.getMouseListeners())
		{
			listener.mousePressed(new MouseEvent(child, MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, 1, 1, 1, false));
		}
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
		potionVariantByStyle.put(styleKeyFor(CombatStyle.MAGIC), variant);
		savePotionVariantPref(styleKeyFor(CombatStyle.MAGIC), variant);
		rankAndRender();
	}

	CombatIcons.BoostPotion magicPotionVariantForTest()
	{
		return magicPotionVariantForCalc();
	}

	/** Simulates right-clicking the potion toggle and picking {@code variant} for {@code style} from the swap menu. */
	void pickPotionVariantForTest(CombatStyle style, CombatIcons.BoostPotion variant)
	{
		String key = styleKeyFor(style);
		potionVariantByStyle.put(key, variant);
		savePotionVariantPref(key, variant);
		rankAndRender();
	}

	/** The currently-picked variant for {@code style} (falls back to the style's default, same as the toggle icon). */
	CombatIcons.BoostPotion potionVariantForTest(CombatStyle style)
	{
		return effectivePotionFor(style);
	}

	/** Rebuilds and returns the right-click swap menu's current item labels for the currently selected style (test seam — mirrors what a real right-click would show). */
	List<String> potionVariantPopupLabelsForTest()
	{
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		populatePotionVariantPopup(menu);
		List<String> labels = new ArrayList<>();
		for (java.awt.Component c : menu.getComponents())
		{
			if (c instanceof javax.swing.JMenuItem)
			{
				labels.add(((javax.swing.JMenuItem) c).getText());
			}
		}
		return labels;
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

	/**
	 * Installs a single-press action on a clickable row AND every child
	 * component that could steal the press from it (item #6d). Children with a
	 * tooltip have a ToolTipManager MouseListener, which makes THEM the mouse
	 * -event target over their bounds — Swing mouse events never bubble to the
	 * parent — so without this the row's own listener only covers the padding
	 * pixels. One shared adapter on the row and each child guarantees a single
	 * click anywhere in the row always fires the action exactly once (each
	 * press is dispatched to exactly one component).
	 */
	private static void installRowPressListener(JPanel row, Runnable action, JLabel... children)
	{
		MouseAdapter press = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				action.run();
			}
		};
		row.addMouseListener(press);
		for (JLabel child : children)
		{
			child.addMouseListener(press);
		}
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
			// Item #6d: the listener must ALSO be on the child labels, not just
			// the row — see StyleRow's constructor comment for why (tooltip
			// registration makes a child swallow the press).
			installRowPressListener(this, () -> selectSpell(SpellRow.this.spell), name, dps);
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
			// Item #6d ("style buttons need multiple clicks"): the press
			// listener must be installed on the CHILD labels as well as the
			// row. Both labels carry tooltips, and setToolTipText registers
			// the label with ToolTipManager, which adds a MouseListener to it
			// — that makes the label itself the mouse-event target for almost
			// the entire row's area (Swing dispatches to the DEEPEST component
			// with a mouse listener and never bubbles to the parent), so a
			// row-only listener only ever fired on the few border/padding
			// pixels around the text. That is exactly the "sometimes takes
			// several clicks" symptom: clicks on the text did nothing.
			installRowPressListener(this, () -> selectStyle(StyleRow.this.style), name, dps);
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
