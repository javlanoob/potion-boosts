package com.potionboost;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.plugins.itemstats.stats.Stat;
import net.runelite.client.plugins.itemstats.stats.Stats;

/**
 * The icon the skills tab draws for a stat, and the boot the settings tab draws for run energy, which
 * is the one thing a potion moves that is not a skill. Looked up by the stat itself rather than by its
 * name, since the item data hands back these very instances.
 */
final class SkillIcons
{
	private static final Map<Stat, Integer> SPRITES = ImmutableMap.<Stat, Integer>builder()
		.put(Stats.ATTACK, SpriteID.Staticons.ATTACK)
		.put(Stats.STRENGTH, SpriteID.Staticons.STRENGTH)
		.put(Stats.DEFENCE, SpriteID.Staticons.DEFENCE)
		.put(Stats.RANGED, SpriteID.Staticons.RANGED)
		.put(Stats.PRAYER, SpriteID.Staticons.PRAYER)
		.put(Stats.MAGIC, SpriteID.Staticons.MAGIC)
		.put(Stats.HITPOINTS, SpriteID.Staticons.HITPOINTS)
		.put(Stats.AGILITY, SpriteID.Staticons.AGILITY)
		.put(Stats.HERBLORE, SpriteID.Staticons.HERBLORE)
		.put(Stats.THIEVING, SpriteID.Staticons.THIEVING)
		.put(Stats.CRAFTING, SpriteID.Staticons.CRAFTING)
		.put(Stats.FLETCHING, SpriteID.Staticons.FLETCHING)
		.put(Stats.MINING, SpriteID.Staticons.MINING)
		.put(Stats.SMITHING, SpriteID.Staticons.SMITHING)
		.put(Stats.FISHING, SpriteID.Staticons.FISHING)
		.put(Stats.COOKING, SpriteID.Staticons.COOKING)
		.put(Stats.FIREMAKING, SpriteID.Staticons.FIREMAKING)
		.put(Stats.WOODCUTTING, SpriteID.Staticons.WOODCUTTING)
		.put(Stats.RUNECRAFT, SpriteID.Staticons2.RUNECRAFT)
		.put(Stats.SLAYER, SpriteID.Staticons2.SLAYER)
		.put(Stats.FARMING, SpriteID.Staticons2.FARMING)
		.put(Stats.HUNTER, SpriteID.Staticons2.HUNTER)
		.put(Stats.CONSTRUCTION, SpriteID.Staticons2.CONSTRUCTION)
		.put(Stats.SAILING, SpriteID.Staticons2.SAILING)
		.put(Stats.RUN_ENERGY, SpriteID.OptionsIconsSmall.RUN_ENERGY)
		.build();

	static final int NONE = -1;

	private SkillIcons()
	{
	}

	static int of(Stat stat)
	{
		Integer sprite = SPRITES.get(stat);

		return sprite == null ? NONE : sprite;
	}
}
