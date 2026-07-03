package com.ospulse;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.ospulse.integration.PriceTrendService;
import com.ospulse.integration.SessionTracker;
import com.ospulse.sync.DashboardSyncService;
import com.ospulse.sync.PairingClient;
import com.ospulse.ui.OSPulsePanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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
import java.awt.image.BufferedImage;

/**
 * OSPulse — accurate OSRS session profit + net-worth tracker.
 *
 * <p>Owns the RuneLite lifecycle and wiring: it feeds live game events into the
 * {@link SessionTracker} (which drives the pure session/GE/XP engines), renders
 * the result in {@link OSPulsePanel}, and — only when the user opts in — hands
 * snapshots to {@link DashboardSyncService} for upload to a dashboard they host.
 */
@Slf4j
@PluginDescriptor(
	name = "OSPulse",
	description = "Accurate session profit (banking-aware), loot feed, net worth, XP and GE "
		+ "flip P&L, valued with RuneLite's GE prices. Optional off-by-default dashboard sync.",
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

	private SessionTracker tracker;
	private OSPulsePanel panel;
	private DashboardSyncService syncService;
	private PairingClient pairingClient;
	private PriceTrendService priceTrendService;
	private NavigationButton navButton;

	/** Last observed bank-interface-open state, to fire transitions once. */
	private boolean lastBankOpen;

	@Override
	protected void startUp()
	{
		tracker = new SessionTracker(client, itemManager, config, configManager, gson);

		priceTrendService = new PriceTrendService(okHttpClient, config, gson);

		panel = new OSPulsePanel(config, itemManager, configManager, priceTrendService, skillIconManager,
			spriteManager, this, client, overlayManager);
		panel.setResetCallback(tracker::resetSession);
		tracker.addListener(panel);

		syncService = new DashboardSyncService(okHttpClient, config, gson);
		tracker.addListener(syncService);

		pairingClient = new PairingClient(okHttpClient, gson);

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
		}
		if (tracker != null)
		{
			// Persist the last-known bank before tearing down so a plugin toggle
			// or client close doesn't lose it.
			tracker.flush();
			tracker.removeListener(panel);
			tracker.removeListener(syncService);
		}
		if (syncService != null)
		{
			syncService.shutdown();
			syncService = null;
		}
		pairingClient = null;
		tracker = null;
		panel = null;
		priceTrendService = null;
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
		final Widget bankWidget = client.getWidget(WidgetInfo.BANK_CONTAINER);
		final boolean bankOpen = bankWidget != null && !bankWidget.isHidden();
		if (bankOpen != lastBankOpen)
		{
			lastBankOpen = bankOpen;
			tracker.onBankOpenChanged(bankOpen);
		}

		// Keep the account name current for optional sync (null until loaded).
		final Player local = client.getLocalPlayer();
		if (local != null && local.getName() != null)
		{
			syncService.setAccount(local.getName());
		}

		tracker.onTick();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		tracker.onItemContainerChanged(event.getContainerId());
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
	 * Watches for the user typing a pairing code into config and, once it looks
	 * like a complete 6-digit code, redeems it for a sync token + ingest URL and
	 * fills those into the (advanced) manual sync fields automatically.
	 *
	 * <p>Filtered to only react to the {@code pairingCode} key so that the
	 * config writes this triggers (syncToken/syncUrl/syncEnabled/pairingCode
	 * itself being cleared) don't re-enter this handler.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!OSPulseConfig.GROUP.equals(event.getGroup()) || !"pairingCode".equals(event.getKey()))
		{
			return;
		}

		String code = event.getNewValue();
		if (code == null || !code.matches("\\d{6}"))
		{
			return;
		}

		String serverUrl = config.pairingServerUrl();
		if (serverUrl == null || serverUrl.trim().isEmpty())
		{
			log.warn("Pairing code entered but no dashboard URL is set - set the Dashboard URL "
				+ "field above the pairing code first");
			configManager.setConfiguration(OSPulseConfig.GROUP, "pairingCode", "");
			return;
		}

		pairingClient.redeem(serverUrl, code, new PairingClient.ResultCallback()
		{
			@Override
			public void onSuccess(String token, String ingestUrl)
			{
				configManager.setConfiguration(OSPulseConfig.GROUP, "syncToken", token);
				configManager.setConfiguration(OSPulseConfig.GROUP, "syncUrl", ingestUrl);
				configManager.setConfiguration(OSPulseConfig.GROUP, "syncEnabled", true);
				configManager.setConfiguration(OSPulseConfig.GROUP, "pairingCode", "");
				log.info("OSPulse pairing code redeemed successfully; sync configured automatically");
			}

			@Override
			public void onFailure(String message)
			{
				log.warn("OSPulse pairing code redemption failed: {}", message);
				configManager.setConfiguration(OSPulseConfig.GROUP, "pairingCode", "");
			}
		});
	}

	@Provides
	OSPulseConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OSPulseConfig.class);
	}
}
