package com.potionboosts;

import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.itemstats.stats.Stats;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class LevelsTest
{
	private int ingame = 1;
	private int build = 0;

	private final Client fake = (Client) Proxy.newProxyInstance(
		Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getVarbitValue":
					return (int) (Integer) args[0] == VarbitID.BR_INGAME ? ingame : build;
				case "getRealSkillLevel":
					return 60;
				case "getBoostedSkillLevel":
					return 61;
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});

	@Test
	public void maxBuild()
	{
		build = 0;
		Levels levels = new Levels(fake);
		assertEquals(99, levels.real(Skill.ATTACK));
		assertEquals(85, levels.real(Skill.DEFENCE));
		assertEquals(99, levels.real(Skill.HITPOINTS));
		assertEquals(60, levels.real(Skill.PRAYER));
		assertEquals(85, Stats.DEFENCE.getMaximum(levels.client()));
		assertEquals(61, Stats.DEFENCE.getValue(levels.client()));
	}

	@Test
	public void pureBuild()
	{
		build = 2;
		Levels levels = new Levels(fake);
		assertEquals(75, levels.real(Skill.ATTACK));
		assertEquals(1, levels.real(Skill.DEFENCE));
		assertEquals(99, levels.real(Skill.STRENGTH));
	}

	@Test
	public void outside()
	{
		ingame = 0;
		Levels levels = new Levels(fake);
		assertEquals(60, levels.real(Skill.ATTACK));
		assertEquals(60, levels.real(Skill.DEFENCE));
		assertSame(fake, levels.client());
	}
}
