package com.ospulse;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.ospulse.combat.BundledGson;
import com.ospulse.integration.PriceTrendService;
import com.ospulse.integration.RuneLiteItemValuation;
import com.ospulse.integration.SessionTracker;
import com.ospulse.ui.OSPulsePanel;
import com.ospulse.ui.sections.GearSection;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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
	name = "OSPulse",
	description = "Accurate session profit (banking-aware), loot feed, net worth, XP and GE "
		+ "flip P&L, valued with RuneLite's GE prices.",
	tags = {"profit", "loot", "wealth", "gp", "session", "tracker", "ge", "flipping", "xp"}
)
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

	private SessionTracker tracker;
	private OSPulsePanel panel;
	private PriceTrendService priceTrendService;
	private NavigationButton navButton;
	private com.ospulse.integration.BankRecommendationHighlighter bankHighlighter;

	/** Last observed bank-interface-open state, to fire transitions once. */
	private boolean lastBankOpen;

	@Override
	protected void startUp()
	{
		// Seed the shared Gson holder FIRST: the static combat repositories
		// parse their bundled JSON through the client's injected Gson (the
		// Plugin Hub forbids fresh instances), and anything below may trigger
		// their lazy load.
		BundledGson.set(gson);

		tracker = new SessionTracker(client, itemManager, config, configManager, gson);

		priceTrendService = new PriceTrendService(okHttpClient, config, gson);

		bankHighlighter = new com.ospulse.integration.BankRecommendationHighlighter(
			bankTagsService, tagManager, clientThread);

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
			GearSection.PriceLookup lookup = new GearSection.PriceLookup(m, untradeable);
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
	}

	@Provides
	OSPulseConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OSPulseConfig.class);
	}
}
