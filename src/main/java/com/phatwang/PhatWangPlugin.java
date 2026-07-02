package com.phatwang;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;

/**
 * Phat Wang — accurate OSRS session profit + net-worth tracker.
 *
 * <p>Skeleton entry point. The event wiring, engine feeding, panel, persistence
 * and sync are added by their respective modules; this class owns the RuneLite
 * lifecycle and dependency wiring.</p>
 */
@Slf4j
@PluginDescriptor(
	name = "Phat Wang",
	description = "Accurate session profit (banking-aware), loot feed, net worth, XP and GE "
		+ "flip P&L, valued with RuneLite's GE prices. Optional off-by-default dashboard sync.",
	tags = {"profit", "loot", "wealth", "gp", "session", "tracker", "ge", "flipping", "xp"}
)
public class PhatWangPlugin extends Plugin
{
	@Inject
	private PhatWangConfig config;

	@Override
	protected void startUp()
	{
		log.info("Phat Wang plugin started");
	}

	@Override
	protected void shutDown()
	{
		log.info("Phat Wang plugin stopped");
	}

	@Provides
	PhatWangConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PhatWangConfig.class);
	}
}
