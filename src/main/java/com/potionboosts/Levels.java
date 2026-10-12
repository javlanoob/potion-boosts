package com.potionboosts;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;

/**
 * The levels a potion is read against, which are the game's own everywhere but Last Man Standing.
 *
 * <p>Last Man Standing hands you a build's levels for the game and boosts a potion from those, but the
 * client works a base level out from your experience and the minigame leaves your experience alone. The
 * levels the client reports are therefore your account's while the ones the game boosts from are the
 * build's, so a max main is told a super combat gives what 99 defence gives where the game gives what
 * the build's 85, 50 or 1 gives, and an account under 99 is told a potion gives less than it does.
 *
 * <p>Item Stats asks the client for a base level itself rather than being handed one, so the levels are
 * put in front of it rather than worked out here: {@link #client()} hands it a client that answers with
 * the build's levels for as long as a game is running.
 */
class Levels implements InvocationHandler
{
	/** The attack the three builds are given, in the order the game numbers them: max, zerker, pure. */
	private static final int[] ATTACK = {99, 80, 75};

	/** The defence those builds are given, which is what tells one from another. */
	private static final int[] DEFENCE = {85, 50, 1};

	private static final String REAL = "getRealSkillLevel";

	private final Client client;

	/** The client with the build's levels in front of it, made once rather than for every reading. */
	private final Client view;

	@Inject
	Levels(Client client)
	{
		this.client = client;
		this.view = (Client) Proxy.newProxyInstance(
			Client.class.getClassLoader(), new Class<?>[]{Client.class}, this);
	}

	/**
	 * The client to work a boost out against, which is the view of it only while a game is running, so
	 * that nothing is read through the view where there is nothing to put right.
	 */
	Client client()
	{
		return playing() ? view : client;
	}

	/** The base level of a skill, which is the build's while a game is running. */
	int real(Skill skill)
	{
		int given = playing() ? given(skill) : 0;

		return given > 0 ? given : client.getRealSkillLevel(skill);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
	{
		if (REAL.equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Skill)
		{
			return real((Skill) args[0]);
		}

		try
		{
			return method.invoke(client, args);
		}
		catch (InvocationTargetException e)
		{
			throw e.getCause();
		}
	}

	/** Whether a game is being played, rather than the lobby being stood in or the island being left. */
	boolean playing()
	{
		return client.getVarbitValue(VarbitID.BR_INGAME) != 0;
	}

	/**
	 * The level the build is given in a skill, or nothing for a skill the minigame leaves alone. Strength,
	 * magic, ranged and hitpoints are 99 on all three builds, attack and defence are the build's own, and
	 * everything else, prayer included, is left to the client: prayer is never drained in there, so a
	 * restore has nothing to put back whichever level it is read against.
	 */
	private int given(Skill skill)
	{
		int build = Math.max(0, Math.min(client.getVarbitValue(VarbitID.BR_ACTIVE_BUILD_PLAYER), ATTACK.length - 1));

		switch (skill)
		{
			case ATTACK:
				return ATTACK[build];
			case DEFENCE:
				return DEFENCE[build];
			case STRENGTH:
			case MAGIC:
			case RANGED:
			case HITPOINTS:
				return Experience.MAX_REAL_LEVEL;
			default:
				return 0;
		}
	}
}
