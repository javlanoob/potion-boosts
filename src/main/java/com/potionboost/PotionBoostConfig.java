package com.potionboost;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("potionboost")
public interface PotionBoostConfig extends Config
{
	enum Number
	{
		BOOST,
		LEVEL
	}

	/**
	 * The corner of the inventory to work in from, for picking between potions that are down to the
	 * same dose as each other.
	 */
	enum Prioritize
	{
		TOP_LEFT(false, false),
		TOP_RIGHT(false, true),
		BOTTOM_LEFT(true, false),
		BOTTOM_RIGHT(true, true);

		private final boolean bottom;
		private final boolean right;

		Prioritize(boolean bottom, boolean right)
		{
			this.bottom = bottom;
			this.right = right;
		}

		boolean isBottom()
		{
			return bottom;
		}

		boolean isRight()
		{
			return right;
		}
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
		description = "Label what only heals or restores too, not only what boosts",
		position = 4
	)
	default boolean food()
	{
		return false;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "minimum",
		name = "Minimum",
		description = "Leave out a skill that would move by less than this",
		position = 5
	)
	default int minimum()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "prioritize",
		name = "Prioritize",
		description = "Which one it goes on when several are down to the same dose",
		position = 6
	)
	default Prioritize prioritize()
	{
		return Prioritize.TOP_LEFT;
	}
}
