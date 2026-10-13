package com.potionboosts;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.itemstats.Positivity;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.StatsChanges;
import net.runelite.client.plugins.itemstats.stats.Stat;
import net.runelite.client.plugins.itemstats.stats.Stats;

/**
 * What the few things a Last Man Standing game hands you would do in there, where the levels Item Stats
 * reads a potion against are not the levels you fight at.
 *
 * <p>The minigame gives you the build's levels without touching your experience, and the client works a
 * base level out of experience, so it keeps reporting the levels your account has while the game boosts
 * from the ones it gave you. A super combat on a 75 attack account reads as +16 in there, where the game
 * would put 99 attack up to 118.
 *
 * <p>So the rows for those few items are worked out here instead, against the levels the build is given,
 * and everything else anywhere else is left to Item Stats as it was. The items are the minigame's own,
 * with ids of their own, which is what keeps the list this short.
 */
class Lms
{
	/** The attack the three builds are given, in the order the game numbers them: max, zerker, pure. */
	private static final int[] ATTACK = {99, 80, 75};

	/** The defence those builds are given, which is what tells one from another. */
	private static final int[] DEFENCE = {85, 50, 1};

	/** How the level a dose is worked out against is read, and what it is allowed to do to it. */
	enum Kind
	{
		/** A boost off the base level, which may leave you above it. */
		UP,
		/** A heal off the base level, which stops at it and is read whether it would heal or not. */
		FED,
		/** A restore off the base level, which stops at it and is read only while there is some to have back. */
		BACK,
		/** A drain off the level you are at, which is read only while there is some to take. */
		DOWN
	}

	/** The skills a brew takes from, which are the skills a restore has any of to put back in a game. */
	private static final Stat[] RESTORED = {
		Stats.ATTACK, Stats.STRENGTH, Stats.DEFENCE, Stats.RANGED, Stats.MAGIC
	};

	private static final Map<Integer, Boost[]> ITEMS = items();

	private final Client client;

	@Inject
	Lms(Client client)
	{
		this.client = client;
	}

	/** Whether a game is being played, rather than the lobby being stood in or the island being left. */
	boolean playing()
	{
		return client.getVarbitValue(VarbitID.BR_INGAME) != 0;
	}

	/** The hitpoints you can have, which are the minigame's while a game is running. */
	int hitpoints()
	{
		return playing() ? Experience.MAX_REAL_LEVEL : client.getRealSkillLevel(Skill.HITPOINTS);
	}

	/**
	 * What taking one of the minigame's own items would do in a game, or nothing at all when a game is
	 * not being played or the item is not one of them, which leaves the item to Item Stats.
	 */
	StatsChanges changes(int id)
	{
		Boost[] boosts = playing() ? ITEMS.get(id) : null;

		if (boosts == null)
		{
			return null;
		}

		StatChange[] rows = new StatChange[boosts.length];
		int kept = 0;
		Positivity positivity = Positivity.NO_CHANGE;

		for (Boost boost : boosts)
		{
			StatChange row = boost.change(base(boost.stat), boost.stat.getValue(client));

			if (row == null)
			{
				continue;
			}

			rows[kept++] = row;

			if (row.getPositivity().compareTo(positivity) > 0)
			{
				positivity = row.getPositivity();
			}
		}

		StatsChanges changes = new StatsChanges(0);
		changes.setStatChanges(Arrays.copyOf(rows, kept));
		changes.setPositivity(positivity);

		return changes;
	}

	/** The level the build is given in a skill, which is 99 in all of them bar attack and defence. */
	private int base(Stat stat)
	{
		int build = Math.max(0, Math.min(client.getVarbitValue(VarbitID.BR_ACTIVE_BUILD_PLAYER), ATTACK.length - 1));

		if (stat == Stats.ATTACK)
		{
			return ATTACK[build];
		}

		if (stat == Stats.DEFENCE)
		{
			return DEFENCE[build];
		}

		return Experience.MAX_REAL_LEVEL;
	}

	/**
	 * A dose of something, as a share of a level and a flat amount on top of it, which is how every one
	 * of them is worked out.
	 */
	static final class Boost
	{
		private final Stat stat;
		private final Kind kind;
		private final double share;
		private final int flat;

		Boost(Stat stat, Kind kind, double share, int flat)
		{
			this.stat = stat;
			this.kind = kind;
			this.share = share;
			this.flat = flat;
		}

		/**
		 * The row this comes to, worked out the way Item Stats works one out, or nothing when it is a row
		 * Item Stats would not have given at all.
		 */
		StatChange change(int base, int value)
		{
			if ((kind == Kind.BACK && value >= base) || (kind == Kind.DOWN && value <= 1))
			{
				return null;
			}

			int delta = (int) ((kind == Kind.DOWN ? value : base) * share) + flat;

			if (kind == Kind.DOWN)
			{
				delta = -delta;
			}

			int ceiling = Math.max(kind == Kind.UP && delta > 0 ? base + delta : base, value);
			int next = Math.max(0, Math.min(value + delta, ceiling));
			int relative = next - value;

			StatChange row = new StatChange();
			row.setStat(stat);
			row.setAbsolute(next);
			row.setRelative(relative);
			row.setTheoretical(delta);

			if (relative > 0)
			{
				row.setPositivity(value + delta > ceiling ? Positivity.BETTER_CAPPED : Positivity.BETTER_UNCAPPED);
			}
			else
			{
				row.setPositivity(relative == 0 ? Positivity.NO_CHANGE : Positivity.WORSE);
			}

			return row;
		}
	}

	/**
	 * The minigame's items and what a dose of each of them does, which are the numbers the game and Item
	 * Stats both have for them, read here against the build's levels rather than your account's.
	 *
	 * <p>The defence a brew gives is left out, since nothing puts your defence up in a game. The row is
	 * kept with nothing in it rather than dropped, so the setting for a skill with nothing to gain is
	 * what says whether it is drawn, and so what the brew takes off the rest is still read beside it.
	 */
	private static Map<Integer, Boost[]> items()
	{
		Boost[] combat = {
			new Boost(Stats.ATTACK, Kind.UP, .15, 5),
			new Boost(Stats.STRENGTH, Kind.UP, .15, 5)
		};

		Boost[] ranging = {
			new Boost(Stats.RANGED, Kind.UP, .10, 4)
		};

		Boost[] brew = {
			new Boost(Stats.HITPOINTS, Kind.UP, .15, 2),
			new Boost(Stats.DEFENCE, Kind.UP, 0, 0),
			new Boost(Stats.ATTACK, Kind.DOWN, .10, 2),
			new Boost(Stats.STRENGTH, Kind.DOWN, .10, 2),
			new Boost(Stats.RANGED, Kind.DOWN, .10, 2),
			new Boost(Stats.MAGIC, Kind.DOWN, .10, 2)
		};

		Boost[] shark = {
			new Boost(Stats.HITPOINTS, Kind.FED, 0, 20)
		};

		Boost[] karambwan = {
			new Boost(Stats.HITPOINTS, Kind.FED, 0, 18)
		};

		Map<Integer, Boost[]> items = new HashMap<>();

		add(items, combat, ItemID.BR_4DOSE2COMBAT, ItemID.BR_3DOSE2COMBAT, ItemID.BR_2DOSE2COMBAT,
			ItemID.BR_1DOSE2COMBAT);
		add(items, ranging, ItemID.BR_4DOSERANGERSPOTION, ItemID.BR_3DOSERANGERSPOTION,
			ItemID.BR_2DOSERANGERSPOTION, ItemID.BR_1DOSERANGERSPOTION);
		add(items, brew, ItemID.BR_4DOSEPOTIONOFSARADOMIN, ItemID.BR_3DOSEPOTIONOFSARADOMIN,
			ItemID.BR_2DOSEPOTIONOFSARADOMIN, ItemID.BR_1DOSEPOTIONOFSARADOMIN);
		add(items, restore(.25, 8), ItemID.BR_4DOSE2RESTORE, ItemID.BR_3DOSE2RESTORE,
			ItemID.BR_2DOSE2RESTORE, ItemID.BR_1DOSE2RESTORE);
		add(items, restore(.30, 4), ItemID.BR_SANFEW_SALVE_4_DOSE, ItemID.BR_SANFEW_SALVE_3_DOSE,
			ItemID.BR_SANFEW_SALVE_2_DOSE, ItemID.BR_SANFEW_SALVE_1_DOSE);
		add(items, shark, ItemID.BR_SHARK);
		add(items, karambwan, ItemID.BR_TBWT_COOKED_KARAMBWAN);

		return Collections.unmodifiableMap(items);
	}

	/**
	 * A restore, which is the same share of every skill a brew takes from. The prayer it puts back is left
	 * out, since a game leaves your prayer where it is and there is never any of it to have back.
	 */
	private static Boost[] restore(double share, int flat)
	{
		Boost[] boosts = new Boost[RESTORED.length];

		for (int i = 0; i < RESTORED.length; i++)
		{
			boosts[i] = new Boost(RESTORED[i], Kind.BACK, share, flat);
		}

		return boosts;
	}

	private static void add(Map<Integer, Boost[]> items, Boost[] boosts, int... ids)
	{
		for (int id : ids)
		{
			items.put(id, boosts);
		}
	}
}
