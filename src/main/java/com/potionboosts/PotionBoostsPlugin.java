package com.potionboosts;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Potion Boosts",
	description = "Shows what a potion would boost on the potion itself",
	tags = {"potion", "boost", "level", "skill", "stat", "inventory", "icons"}
)
public class PotionBoostsPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PotionBoostsOverlay overlay;

	@Provides
	PotionBoostsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PotionBoostsConfig.class);
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

	/**
	 * What the overlay keeps from one tick to the next is what the settings had left of an item, so a
	 * setting changing has to throw that away rather than wait for the tick to do it.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (PotionBoostsConfig.GROUP.equals(event.getGroup()))
		{
			overlay.reset();
		}
	}
}
