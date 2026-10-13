package com.potionboosts;

import com.potionboosts.Lms.Boost;
import com.potionboosts.Lms.Kind;
import net.runelite.client.plugins.itemstats.Positivity;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.stats.Stats;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class LmsTest
{
	@Test
	public void boostsOffTheBuild()
	{
		StatChange max = new Boost(Stats.ATTACK, Kind.UP, .15, 5).change(99, 99);
		assertEquals(19, max.getRelative());
		assertEquals(118, max.getAbsolute());
		assertEquals(Positivity.BETTER_UNCAPPED, max.getPositivity());

		StatChange pure = new Boost(Stats.ATTACK, Kind.UP, .15, 5).change(75, 75);
		assertEquals(16, pure.getRelative());
		assertEquals(91, pure.getAbsolute());
	}

	@Test
	public void nothingOnDefence()
	{
		StatChange change = new Boost(Stats.DEFENCE, Kind.UP, 0, 0).change(85, 85);
		assertEquals(0, change.getRelative());
		assertEquals(0, change.getTheoretical());
		assertEquals(Positivity.NO_CHANGE, change.getPositivity());
	}

	@Test
	public void fedToTheBuild()
	{
		StatChange shark = new Boost(Stats.HITPOINTS, Kind.FED, 0, 20).change(99, 70);
		assertEquals(20, shark.getRelative());

		StatChange capped = new Boost(Stats.HITPOINTS, Kind.FED, 0, 20).change(99, 90);
		assertEquals(9, capped.getRelative());
		assertEquals(99, capped.getAbsolute());
		assertEquals(Positivity.BETTER_CAPPED, capped.getPositivity());
	}

	@Test
	public void drainsOffWhereYouAre()
	{
		StatChange brewed = new Boost(Stats.STRENGTH, Kind.DOWN, .10, 2).change(99, 118);
		assertEquals(-13, brewed.getRelative());
		assertEquals(105, brewed.getAbsolute());
		assertEquals(Positivity.WORSE, brewed.getPositivity());

		assertNull(new Boost(Stats.STRENGTH, Kind.DOWN, .10, 2).change(1, 1));
	}

	@Test
	public void restoresUpToTheBuild()
	{
		StatChange back = new Boost(Stats.ATTACK, Kind.BACK, .25, 8).change(99, 80);
		assertEquals(19, back.getRelative());
		assertEquals(99, back.getAbsolute());

		assertNull(new Boost(Stats.ATTACK, Kind.BACK, .25, 8).change(99, 99));
	}
}
