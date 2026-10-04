package com.potionboost;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Potion Boost",
	description = "Shows what a potion would boost on the potion itself",
	tags = {"potion", "boost", "level", "skill", "stat", "inventory", "icons"}
)
public class PotionBoostPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PotionBoostOverlay overlay;

	@Provides
	PotionBoostConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PotionBoostConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
	}
}
