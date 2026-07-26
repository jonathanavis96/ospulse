package com.ospulse;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.ospulse.combat.BundledGson;
import com.ospulse.integration.PriceTrendService;
import com.ospulse.integration.RuneLiteItemValuation;
import com.ospulse.integration.SessionTracker;
import com.ospulse.ui.OSPulsePanel;
import com.ospulse.ui.sections.GearSection;
import com.ospulse.ui.sections.gear.IronmanAutoDetect;
import com.ospulse.ui.sections.gear.IronmanOwnedOnlyStore;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.vars.AccountType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;

/**
 * OSPulse — accurate OSRS session profit + net-worth tracker.
 *
 * <p>Owns the RuneLite lifecycle and wiring: it feeds live game events into the
 * {@link SessionTracker} (which drives the pure session/GE/XP engines) and
 * renders the result in {@link OSPulsePanel}.
 */
@Slf4j
@PluginDescriptor(
	// BuildInfo.PLUGIN_NAME is the committed default "OSPulse" (Plugin Hub
	// build); a local -Pdev sideloaded testing build overrides it with
	// "OSPulse (dev)" (see build.gradle) so the two are distinguishable in
	// the client's plugin list.
	name = BuildInfo.PLUGIN_NAME,
	description = "Accurate session profit (banking-aware), loot feed, net worth, XP and GE "
		+ "flip P&L, valued with RuneLite's GE prices.",
	tags = {"profit", "loot", "wealth", "gp", "session", "tracker", "ge", "flipping", "xp"}
)
// The "Show in bank" feature drives RuneLite's Bank Tags to filter the open
// bank to the optimiser's recommended items. BankTagsService/TagManager are
// bound inside the banktags plugin's own injector, so without declaring the
// dependency they inject as null and the feature silently no-ops.
@PluginDependency(BankTagsPlugin.class)
// The loot feed consumes Loot Tracker's LootReceived events to NAME a drop's
// source; declaring the dependency makes RuneLite start that plugin first so
// early kills are not missed. A nicety, not a requirement: the feed derives the
// loot itself from the inventory diff (see SessionTracker#attributeDiffLoot), so
// with Loot Tracker off the drops still appear, just unnamed.
@PluginDependency(LootTrackerPlugin.class)
public class OSPulsePlugin extends Plugin
{
	@Inject
	private OSPulseConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private net.runelite.client.game.SpriteManager spriteManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private net.runelite.client.callback.ClientThread clientThread;

	@com.google.inject.Inject(optional = true)
	private net.runelite.client.plugins.banktags.BankTagsService bankTagsService;

	@com.google.inject.Inject(optional = true)
	private net.runelite.client.plugins.banktags.TagManager tagManager;

	/** Mirrors {@link OSPulseConfig#ironmanOwnedOnly()}'s {@code keyName} — see {@link #checkIronmanAutoDetect()} and {@link IronmanOwnedOnlyStore#KEY}. */
	private static final String IRONMAN_OWNED_ONLY_KEY = IronmanOwnedOnlyStore.KEY;
	/** Per-RS-profile "have I already evaluated auto-detect for this profile" bookkeeping key — see {@link #checkIronmanAutoDetect()}. */
	private static final String IRONMAN_AUTO_DETECT_SEEN_KEY = "ironmanOwnedOnlyAutoDetectSeen";

	private SessionTracker tracker;
	private OSPulsePanel panel;
	private PriceTrendService priceTrendService;
	private NavigationButton navButton;
	private com.ospulse.integration.BankRecommendationHighlighter bankHighlighter;
	/** Owns every {@code ironmanOwnedOnly} read/write — the per-account merged-read scheme's reads/writes/mirror (issue #11 leak fix). */
	private IronmanOwnedOnlyStore ownedOnlyStore;

	/** Last observed bank-interface-open state, to fire transitions once. */
	private boolean lastBankOpen;

	/**
	 * Set on every {@code LOGGED_IN} transition; {@link #onGameTick} is where
	 * {@link #checkIronmanAutoDetect()} actually runs (see that method's
	 * javadoc for why account state can't be read on the LOGGED_IN event
	 * itself) — but only CLEARS this flag once {@link #checkIronmanAutoDetect()}
	 * reports it actually ran (P2 fix): if the first post-login tick still
	 * sees {@code Client.getAccountHash() == -1}, this stays armed and
	 * {@link #checkIronmanAutoDetect()} is retried on every subsequent tick
	 * until account state is ready, rather than being silently dropped for
	 * the whole session.
	 */
	private boolean pendingIronmanAutoDetect;

	@Override
	protected void startUp()
	{
		// Seed the shared Gson holder FIRST: the static combat repositories
		// parse their bundled JSON through the client's injected Gson (the
		// Plugin Hub forbids fresh instances), and anything below may trigger
		// their lazy load.
		BundledGson.set(gson);

		tracker = new SessionTracker(client, itemManager, config, configManager, gson);

		ownedOnlyStore = new IronmanOwnedOnlyStore(configManager);

		priceTrendService = new PriceTrendService(okHttpClient, config, gson);

		bankHighlighter = new com.ospulse.integration.BankRecommendationHighlighter(
			bankTagsService, tagManager, configManager, clientThread);
		if (bankTagsService == null || tagManager == null)
		{
			// With @PluginDependency(BankTagsPlugin) these should always resolve;
			// log loudly if not so the inert "Show in bank" is diagnosable.
			log.warn("OSPulse: Bank Tags unavailable (bankTagsService={}, tagManager={}) — 'Show in bank' will be inert",
				bankTagsService != null, tagManager != null);
		}

		RuneLiteItemValuation valuation = new RuneLiteItemValuation(itemManager);
		// Precomputes BOTH prices and tradeability on the client thread
		// (ItemManager.getItemComposition/isTradeable assert it) into plain
		// collections, so the background optimiser search never touches the
		// ItemManager itself. Tradeability matters because getItemPrice
		// "prices" some untradeables via ItemMapping's tradeable proxies
		// (e.g. trouver-locked items cost the Trouver parchment's ~1m) — an
		// unowned untradeable must be unpurchasable, whatever it "costs".
		GearSection.OptimizerPriceResolver optimizerPriceResolver = (ids, cb) -> clientThread.invoke(() ->
		{
			java.util.Map<Integer, Long> m = new java.util.HashMap<>();
			java.util.Set<Integer> untradeable = new java.util.HashSet<>();
			for (int id : ids)
			{
				if (!valuation.isTradeable(id))
				{
					// Curated exception: a few best-in-slot items are untradeable
					// but assembled from a tradeable component (e.g. an Avernic
					// defender is made from a tradeable Avernic defender hilt on a
					// dragon defender). Price such an item at its component's GE
					// cost so the optimiser can still recommend it; the readout
					// keeps the assembled item's own name.
					Integer component = com.ospulse.combat.AssembledItemComponents.priceSourceComponent(id);
					if (component != null && valuation.isTradeable(component))
					{
						long cv = valuation.unitValue(component);
						if (cv > 0)
						{
							m.put(id, cv);
							continue;
						}
					}
					untradeable.add(id);
					continue;
				}
				long v = valuation.unitValue(id);
				if (v > 0)
				{
					m.put(id, v);
				}
			}
			// Per-item real gp "risk value" for the expensive-item wilderness/
			// PvP cap ONLY (see GearOptimizer.Request.Builder#riskValueSource) —
			// deliberately independent of the budget map `m` above: a
			// tradeable item's risk value is its own GE price; an untradeable
			// item's risk value is the summed GE price of the tradeable
			// component(s) it represents (Barrows pieces, imbued rings, Doom-
			// of-Mokhaiotl weapons, etc.), an AssembledItemComponents component
			// price (e.g. Avernic defender), or — as a last resort, for rare
			// untradeables with no tradeable equivalent anywhere else, and never
			// for a free-reobtainable id — the Trouver parchment price (see
			// RiskValuation#classify), resolved for EVERY id here (owned ids
			// included, since the caller no longer excludes them — see
			// GearSection#withResolvedPrices), so an owned or untradeable
			// expensive item can no longer dodge the cap.
			//
			// 24187 = ItemID.TROUVER_PARCHMENT, fetched once per resolve rather
			// than per item (verified via javap against the pinned
			// net.runelite:client/runelite-api 1.12.32 jars on this project's
			// classpath).
			long parchmentPrice = Math.max(0L, valuation.unitValue(24187));
			java.util.Map<Integer, Long> riskValues = new java.util.HashMap<>();
			java.util.Set<Integer> needsProtection = new java.util.HashSet<>();
			for (int id : ids)
			{
				com.ospulse.combat.RiskValuation.Risk risk = com.ospulse.combat.RiskValuation.classify(
					id, valuation::isTradeable, valuation::unitValue, parchmentPrice,
					com.ospulse.combat.optimizer.GearOptimizer::isFreeReobtainable);
				if (risk.value > 0)
				{
					riskValues.put(id, risk.value);
				}
				if (risk.source == com.ospulse.combat.RiskValuation.Source.PARCHMENT)
				{
					needsProtection.add(id);
				}
			}
			GearSection.PriceLookup lookup = new GearSection.PriceLookup(m, untradeable, riskValues, needsProtection);
			javax.swing.SwingUtilities.invokeLater(() -> cb.accept(lookup));
		});

		panel = new OSPulsePanel(config, itemManager, configManager, priceTrendService, skillIconManager,
			spriteManager, this, client, overlayManager, optimizerPriceResolver);
		// resetSession() reads live item containers (buildWealth ->
		// client.getItemContainer), which asserts the client thread; the Reset
		// button fires on the Swing EDT, so marshal the engine reset onto the
		// client thread here. The panel's own resetSections() still runs first
		// on the EDT (clearing UI baselines before the re-anchored snapshot is
		// published back), preserving the phantom-profit-on-reset ordering.
		panel.setResetCallback(() -> clientThread.invoke(tracker::resetSession));
		panel.setBankHighlighter(bankHighlighter);
		tracker.addListener(panel);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("OSPulse")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// If the plugin is toggled on mid-session, start tracking immediately.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			tracker.onLogin();
		}
		armPendingIronmanAutoDetectIfLoggedIn();

		log.debug("OSPulse plugin started");
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (panel != null)
		{
			// Drop any category overlays the user added to the canvas so a
			// plugin toggle doesn't leak stale XP-Tracker-style overlays.
			panel.removeAllCategoryOverlays();
			// Flush HoldingsSection's Unrealized P/L snapshot so "since last
			// login" has the freshest possible baseline next time.
			panel.persistState();
		}
		if (tracker != null)
		{
			// Persist the last-known bank before tearing down so a plugin toggle
			// or client close doesn't lose it.
			tracker.flush();
			tracker.removeListener(panel);
		}
		tracker = null;
		panel = null;
		if (priceTrendService != null)
		{
			// Cancel in-flight price-trend fetches so a late callback can't run
			// Swing work against the now-detached panel.
			priceTrendService.shutdown();
		}
		priceTrendService = null;
		if (bankHighlighter != null)
		{
			bankHighlighter.clear();
		}
		bankHighlighter = null;
		lastBankOpen = false;

		log.debug("OSPulse plugin stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				tracker.onLogin();
				armPendingIronmanAutoDetectIfLoggedIn();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
			case CONNECTION_LOST:
				tracker.onLogout();
				lastBankOpen = false;
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (pendingIronmanAutoDetect)
		{
			// P2 fix: only consumed once checkIronmanAutoDetect() actually ran —
			// see that method's readiness guard/javadoc. Clearing the flag
			// unconditionally here (the earlier bug) would silently drop the
			// only pending attempt for the whole session whenever the FIRST
			// post-login tick still reported account state as not-yet-ready:
			// no other event re-arms pendingIronmanAutoDetect until the next
			// LOGGED_IN transition, so auto-detect would just never run.
			pendingIronmanAutoDetect = !checkIronmanAutoDetect();
		}

		// Detect bank open/close transitions before advancing the tracker so the
		// engine treats inventory<->bank moves as transfers while the bank is
		// open, and re-baselines on close.
		// Bankmain.UNIVERSE is the bank interface's root container (packed id
		// 786433 = group 12 child 1, the classic "bank container" component).
		// A bank DEPOSIT BOX (BankDepositbox.UNIVERSE, group 192) is banking too:
		// items go straight to the bank with no wealth loss, so it must count as
		// "bank open" — otherwise a deposit there books the tracked drop as a loss
		// while the offsetting bank rise is mis-classified. The server keeps the
		// InventoryID.BANK container synced through a deposit box, so the rise is
		// observed and settles the deposit exactly like an open-bank transfer.
		final Widget bankWidget = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		final Widget depositBoxWidget = client.getWidget(InterfaceID.BankDepositbox.UNIVERSE);
		final boolean bankOpen = (bankWidget != null && !bankWidget.isHidden())
			|| (depositBoxWidget != null && !depositBoxWidget.isHidden());
		if (bankOpen != lastBankOpen)
		{
			lastBankOpen = bankOpen;
			tracker.onBankOpenChanged(bankOpen);
			if (bankOpen)
			{
				bankHighlighter.reapplyIfArmed();
			}
		}

		tracker.onTick();
	}

	/**
	 * Arms {@link #pendingIronmanAutoDetect} if the client is already logged
	 * in — shared by {@link #startUp()} (plugin enabled while already
	 * logged in, which fires no {@code LOGGED_IN} {@link GameStateChanged})
	 * and {@link #onGameStateChanged}'s own LOGGED_IN case, so the two paths
	 * cannot drift (issue #11 P2 fix).
	 */
	void armPendingIronmanAutoDetectIfLoggedIn()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			pendingIronmanAutoDetect = true;
		}
	}

	/** Test seam: {@link #pendingIronmanAutoDetect}'s current value. */
	boolean pendingIronmanAutoDetectForTest()
	{
		return pendingIronmanAutoDetect;
	}

	/**
	 * One-time, per-RS-profile auto-enable of {@link OSPulseConfig#ironmanOwnedOnly()}
	 * for ironman accounts (issue #11). Deliberately run from the first
	 * {@link GameTick} after login rather than {@link #onGameStateChanged}
	 * itself: reading account state (e.g. {@link Client#getAccountType()}) on
	 * the LOGGED_IN transition has been observed elsewhere in this codebase
	 * (see the bank-cache/GE-ledger restore in {@code SessionTracker}) to run
	 * before the client has finished populating post-login state, so this
	 * mirrors {@code SessionTracker#onTick}'s own bootstrap-on-next-tick
	 * pattern by deferring exactly one tick.
	 *
	 * <p>{@code ironmanOwnedOnly} is per-account now (issue #11 leak fix): the
	 * write below goes through {@link #ownedOnlyStore}'s {@code
	 * writeAutoDetected}, which sets ONLY the current account's RS-profile-
	 * scoped value, never the client-wide {@code @ConfigItem} — an ironman
	 * alt auto-enabling can therefore never leak onto a main sharing the same
	 * client. The bookkeeping "have I already evaluated this profile" marker
	 * (separate from the value itself) is likewise RS-profile-scoped (via
	 * {@code getRSProfileConfiguration}/{@code setRSProfileConfiguration},
	 * mirroring {@code SessionTracker}'s bank-cache/GE-ledger persistence) —
	 * this guarantees an ironman alt and a main sharing one client each get
	 * their own independent one-time evaluation, rather than one profile's
	 * "already decided" state silently blocking the other's. See {@link
	 * IronmanAutoDetect} for the pure decision logic and {@link
	 * IronmanOwnedOnlyStore}/{@code com.ospulse.ui.sections.gear.IronmanOwnedOnlyResolver}
	 * for the full per-account merged-read scheme.
	 *
	 * @return {@code false} when the readiness guard below refused to run at
	 *         all (account state not yet available) — {@link #onGameTick}'s
	 *         P2 fix uses this to decide whether {@link
	 *         #pendingIronmanAutoDetect} may be cleared or must stay armed
	 *         for a retry on the next tick; {@code true} otherwise (the check
	 *         ran to completion, whatever it decided).
	 */
	private boolean checkIronmanAutoDetect()
	{
		if (configManager == null || client.getAccountHash() == -1L)
		{
			return false;
		}

		String seenMarker = configManager.getRSProfileConfiguration(
			OSPulseConfig.GROUP, IRONMAN_AUTO_DETECT_SEEN_KEY);
		if (IronmanAutoDetect.needsEvaluation(seenMarker))
		{
			configManager.setRSProfileConfiguration(OSPulseConfig.GROUP, IRONMAN_AUTO_DETECT_SEEN_KEY, true);

			String rawOwnedOnly = ownedOnlyStore.rawProfileValue();
			AccountType accountType = client.getAccountType();
			boolean isIronman = accountType != null && (accountType.isIronman() || accountType.isGroupIronman());
			if (IronmanAutoDetect.shouldAutoEnable(rawOwnedOnly, isIronman))
			{
				ownedOnlyStore.writeAutoDetected(true);
			}
		}

		// Mirror (issue #11 leak fix): every login re-syncs the client-wide
		// checkbox to this account's resolved effective value, so the settings
		// panel never shows a stale value left over from whichever account was
		// last logged in. Also re-run from onRuneScapeProfileChanged (an RS
		// profile switch without a full re-login, e.g. bank PIN or world hop
		// edge cases RuneLite itself treats as a profile change).
		ownedOnlyStore.mirrorToClientWide();
		return true;
	}

	/** Test seam: runs {@link #checkIronmanAutoDetect()} directly (private, no {@code pendingIronmanAutoDetect} plumbing needed in a test). */
	void checkIronmanAutoDetectForTest()
	{
		checkIronmanAutoDetect();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		tracker.onItemContainerChanged(event.getContainerId(), event.getItemContainer());
	}

	/**
	 * Forwards chat messages so {@link SessionTracker}'s fish-barrel tracker can
	 * infer barreled-fish contents from catch/full/empty messages — an open
	 * fish barrel auto-stores caught fish without ever passing them through the
	 * inventory, so this is the only signal available for those catches.
	 */
	@Subscribe
	public void onChatMessage(net.runelite.api.events.ChatMessage event)
	{
		tracker.onChatMessage(event.getType(), event.getMessage());
	}

	/**
	 * Forwards the fish barrel's "Check" interface load (group 193) so the
	 * tracker can resynchronise its exact per-species contents from the
	 * widget text. See {@code FishBarrelTracker#onWidgetLoaded}.
	 */
	@Subscribe
	public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded event)
	{
		tracker.onWidgetLoaded(event.getGroupId());
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		tracker.onGrandExchangeOfferChanged(event.getSlot(), event.getOffer());
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		tracker.onStatChanged(event.getSkill(), event.getXp());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		tracker.onLootReceived(event.getName(), event.getAmount(), event.getItems());
	}

	/**
	 * Forwards deliberate "Drop"/"Destroy" inventory item ops to the tracker as
	 * per-tick {@link com.ospulse.session.MovementSignals}. Shift-click drop
	 * fires the same "Drop" option, so it's covered without extra handling.
	 *
	 * <p><b>Destroy timing caveat:</b> "Destroy" fires on the menu click itself,
	 * BEFORE the confirmation dialog is shown — so a later-confirmed (or
	 * cancelled) destroy may not align with the item's actual vanish tick, since
	 * this is recorded as a per-tick signal at click time. Drop has no such
	 * confirmation step and is unaffected. This is a known limitation to verify
	 * in-client, not a blocker for this wiring.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (option == null)
		{
			return;
		}
		int itemId = event.getItemId();
		if (itemId <= 0)
		{
			return;
		}
		if ("Drop".equalsIgnoreCase(option))
		{
			tracker.recordDrop(itemId);
		}
		else if ("Destroy".equalsIgnoreCase(option))
		{
			tracker.recordDestroy(itemId);
		}
		// Fish barrel "Fill"/"Empty"/"Empty to bank"/"Check" ops — forwarded
		// unconditionally; the tracker itself filters to barrel item ids.
		tracker.onBarrelMenuAction(itemId, option);
	}

	/**
	 * Forwards the local player's death to the tracker as a per-tick
	 * {@link com.ospulse.session.MovementSignals#diedThisTick()} signal.
	 *
	 * <p><b>Timing caveat:</b> {@code ActorDeath} fires on the death animation,
	 * but the {@code died} signal is only drained on the next {@link
	 * SessionTracker#onTick} refresh (per-tick drain, same mechanism as
	 * Drop/Destroy). For the engine's death-parking to gate correctly, this
	 * event's tick must coincide with the tick the inventory actually clears.
	 * If in practice the inventory clears a tick or more after {@code
	 * ActorDeath}, the signal may miss its window — a known integration risk
	 * to verify in-client, not a blocker for this wiring. The per-tick drain
	 * design itself is unchanged to compensate for this.
	 */
	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			tracker.recordDeath();
		}
	}

	/**
	 * Tracks the local player's animation so the engine can open a skilling
	 * episode from it. Animation is not a nicety here: herblore's unf-making
	 * step grants no XP at all, so an XP-only trigger would never see the step
	 * the expensive herb is spent on. See {@code ProductionActivity} for the id
	 * table — every id resolved from RuneLite's own {@code AnimationID}
	 * constants and guarded by {@code ProductionAnimationProvenanceTest} — and
	 * for why a missing id degrades safely.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			tracker.onAnimationChanged(event.getActor().getAnimation());
		}
	}

	/**
	 * An RS profile switch without a full re-login (RuneLite fires this
	 * whenever its own profile resolution decides the "current" RS profile
	 * changed) — re-mirrors the newly-current account's resolved value into
	 * the client-wide checkbox (issue #11 leak fix), same as {@link
	 * #checkIronmanAutoDetect()} does on every login.
	 */
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		if (ownedOnlyStore == null)
		{
			return;
		}
		ownedOnlyStore.mirrorToClientWide();
		if (panel != null)
		{
			SwingUtilities.invokeLater(panel::refreshIronmanOwnedOnlyMode);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!OSPulseConfig.GROUP.equals(event.getGroup()) || panel == null)
		{
			return;
		}

		// Panel-section show/hide toggles apply live (no plugin restart): the panel
		// keeps every section constructed and just re-lays-out the visible set.
		final String key = event.getKey();
		if (key != null && key.startsWith("show") && key.endsWith("Section"))
		{
			SwingUtilities.invokeLater(panel::applySectionVisibility);
		}

		// Ironman owned-only mode (issue #11 P2 fix, then the issue #11 leak
		// fix's per-account scheme): every change on this key — the auto-
		// detect's own per-profile write, this plugin's own client-wide
		// mirror echoing back, or a genuine user toggle of the settings-panel
		// checkbox — must recompute the affected GearSection visibility live,
		// same as the show*Section keys above.
		if (IRONMAN_OWNED_ONLY_KEY.equals(key))
		{
			// A non-null profile means this is a profile-SCOPED write (verified
			// against the ConfigManager bytecode: setRSProfileConfiguration's
			// posted ConfigChanged always carries the resolved RS profile key;
			// every plain setConfiguration — whether ours or the settings
			// panel's — always carries a null profile) — i.e. auto-detect's own
			// write, never a user toggle of the client-wide checkbox. Refresh
			// only; never treat it as a toggle to persist.
			if (event.getProfile() == null)
			{
				// Client-wide write: either this plugin's own mirrorToClientWide
				// echoing back (swallowed by the echo latch — see
				// IronmanOwnedOnlyStore's javadoc) or a genuine user toggle,
				// which must persist to the current account's per-profile value
				// AND the global default.
				if (!ownedOnlyStore.consumeMirrorEcho(event.getNewValue()))
				{
					ownedOnlyStore.writeUserToggle(Boolean.parseBoolean(event.getNewValue()));
				}
			}
			SwingUtilities.invokeLater(panel::refreshIronmanOwnedOnlyMode);
		}
	}

	@Provides
	OSPulseConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OSPulseConfig.class);
	}
}
