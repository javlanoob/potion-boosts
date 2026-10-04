package com.potionboost;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("potionboost")
public interface PotionBoostConfig extends Config
{
	enum Number
	{
		BOOST,
		LEVEL
	}

	enum Which
	{
		TOP_LEFT,
		SMALLEST_DOSE
	}

	@ConfigItem(
		keyName = "number",
		name = "Number",
		description = "Show how much the potion adds, or the level it takes you to",
		position = 1
	)
	default Number number()
	{
		return Number.BOOST;
	}

	@ConfigItem(
		keyName = "which",
		name = "Show it on",
		description = "Which one it goes on when you are carrying several of the same potion",
		position = 2
	)
	default Which which()
	{
		return Which.SMALLEST_DOSE;
	}
}
