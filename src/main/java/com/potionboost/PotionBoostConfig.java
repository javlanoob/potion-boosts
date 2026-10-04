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

	enum Prioritize
	{
		TOP_LEFT,
		BOTTOM_RIGHT
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
		keyName = "combatOnly",
		name = "Combat only",
		description = "Leave out the skills you do not fight with",
		position = 2
	)
	default boolean combatOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drains",
		name = "Show drains",
		description = "Show what a potion takes away as well as what it gives",
		position = 3
	)
	default boolean drains()
	{
		return false;
	}

	@ConfigItem(
		keyName = "food",
		name = "Include food",
		description = "Label food and drink too, not only the potions you brew",
		position = 4
	)
	default boolean food()
	{
		return false;
	}

	@ConfigItem(
		keyName = "prioritize",
		name = "Prioritize",
		description = "Which one it goes on when several are down to the same dose",
		position = 5
	)
	default Prioritize prioritize()
	{
		return Prioritize.TOP_LEFT;
	}
}
