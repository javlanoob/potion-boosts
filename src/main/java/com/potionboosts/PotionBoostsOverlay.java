package com.potionboosts;

import com.google.common.collect.ImmutableSet;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.plugins.itemstats.ItemStatConfig;
import net.runelite.client.plugins.itemstats.Positivity;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.StatsChanges;
import net.runelite.client.plugins.itemstats.stats.Stat;
import net.runelite.client.plugins.itemstats.stats.Stats;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

class PotionBoostsOverlay extends Overlay
{
	/**
	 * The inventory, and the inventory as it is drawn beside an open bank, which is a different widget
	 * holding the same twenty-eight slots.
	 */
	private static final int[] CONTAINERS = {
		InterfaceID.Inventory.ITEMS,
		InterfaceID.Bankside.ITEMS
	};

	private static final int COLUMNS = 4;

	private static final int ROWS = 7;

	private static final int SLOTS = COLUMNS * ROWS;

	/**
	 * What is left when the skills you do not fight with are left out: the seven a fight is had with, and
	 * run energy, which is not a skill to be choosing between in the first place and is run down fighting
	 * the same as anywhere else.
	 */
	private static final Set<Stat> FIGHTING = ImmutableSet.of(
		Stats.ATTACK,
		Stats.STRENGTH,
		Stats.DEFENCE,
		Stats.RANGED,
		Stats.MAGIC,
		Stats.HITPOINTS,
		Stats.PRAYER,
		Stats.RUN_ENERGY);

	/**
	 * The doses a potion has left, which is the end of its name and the only thing telling two of the
	 * same potion apart.
	 */
	private static final Pattern DOSE = Pattern.compile("\\((\\d+)\\)$");

	/**
	 * How tall a row is allowed to get, so that a potion boosting the one skill is not labelled in an
	 * icon half the size of the slot, and how small the icon in a row may be squeezed before the rows
	 * are left to run over each other instead.
	 */
	private static final int MAX_ROW = 12;

	private static final int MIN_ICON = 6;

	/**
	 * How many rows a slot holds one under the other before they go up two abreast instead. Four rows
	 * fit, but only at the size the icons stop being drawn any smaller, and two by two is the same four
	 * rows with the icons half as big again.
	 */
	private static final int CROWDED = 3;

	/**
	 * The colosseum invocation that lets nothing heal you past your hitpoints, held as how many of it you
	 * took. A brew is worked out as being able to carry you a tenth over your level, which is what it does
	 * anywhere else, so with this one on it was promising hitpoints a brew would not give.
	 */
	private static final int FRAILTY = VarbitID.COLOSSEUM_MODIFIER_FRAILTY_STACKS_CLIENT;

	/**
	 * How far an item has to be taken before the game is carrying it rather than letting it sit where it
	 * is, so that a click on a potion is a click and not a drag of five pixels.
	 */
	private static final int CARRIED = 5;

	/**
	 * The room between one slot and the next, which a row two abreast is allowed to run into. A slot is
	 * thirty six wide and they are forty two apart, and a number in the small font is eleven wide without
	 * its sign, so the pair of them only go side by side with the gap thrown in.
	 */
	private static final int GUTTER = 6;

	/**
	 * How light a pixel has to be to want an outline pixel of its own next to it. The skill icons are
	 * drawn with a dark edge already, and going around that as well is what reads as two pixels thick.
	 */
	private static final int DARK = 48;

	/**
	 * How opaque a pixel has to be to count as part of the icon rather than as room around it, so that
	 * the edge of one is in a definite place for the outline to go around.
	 */
	private static final int SOLID = 128;

	private final Client client;
	private final ItemManager itemManager;
	private final ItemStatChanges statChanges;
	private final SpriteManager spriteManager;
	private final PotionBoostsConfig config;

	/**
	 * The colours Item Stats puts its own numbers in, read from that plugin rather than kept here, so a
	 * colour changed there is the colour a potion is labelled in.
	 */
	private final ItemStatConfig colours;

	/**
	 * The outlined icons, by sprite and by the height they were prepared at, since outlining one is work
	 * that only has to happen once and finding it again is work for every row of every frame.
	 */
	private final Map<Integer, BufferedImage> icons = new HashMap<>();

	/**
	 * What each item in the inventory would show, and the tick that was worked out on. The answer depends
	 * on the levels you are at and on the settings, so it is thrown away on a tick or a setting rather
	 * than worked out again for every slot of every frame.
	 */
	private final Map<Integer, Item> items = new HashMap<>();

	private int itemsTick = -1;

	/**
	 * Whether a setting has changed since the rows were worked out. Set rather than cleared on the spot,
	 * because the settings are changed from the side of the client that draws the panel rather than the
	 * side that draws the game, and the clearing belongs where the reading is.
	 */
	private volatile boolean stale;

	/**
	 * The name of an item with the doses taken off the end of it, and the doses that were taken off, which
	 * is the same answer for as long as the client is open.
	 */
	private final Map<Integer, Named> names = new HashMap<>();

	/** Where the mouse took hold of an item, or nothing when it has hold of nothing. */
	private Point grabbed;

	/** Whether what it has hold of has been taken far enough for the game to be carrying it. */
	private boolean carrying;

	/** The skills to show, out of the setting they were written in. */
	private final Listing showing = new Listing();

	/** The skills to leave out, out of the setting they were written in. */
	private final Listing hiding = new Listing();

	@Inject
	PotionBoostsOverlay(
		Client client,
		ItemManager itemManager,
		ItemStatChanges statChanges,
		SpriteManager spriteManager,
		ConfigManager configManager,
		PotionBoostsConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);

		// Drawn as the inventory itself is drawn rather than over the lot of it afterwards, and before
		// anything else that labels a slot, so that a dose count in the corner of one ends up on top of
		// this rather than under it. The item overlays that draw those counts sit at a priority of
		// nothing, and this has to come out under them
		setLayer(OverlayLayer.MANUAL);
		drawAfterInterface(InterfaceID.INVENTORY);
		drawAfterInterface(InterfaceID.BANKSIDE);
		setPriority(PRIORITY_LOW - 1f);

		this.client = client;
		this.itemManager = itemManager;
		this.statChanges = statChanges;
		this.spriteManager = spriteManager;
		this.colours = configManager.getConfig(ItemStatConfig.class);
		this.config = config;
	}

	/**
	 * Marked for working out again when a setting changes, since what is kept is the rows the settings had
	 * left rather than everything the item would do.
	 */
	void reset()
	{
		stale = true;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget container = container();

		if (container == null)
		{
			return null;
		}

		List<Potion> potions = potionsIn(container);

		if (potions.isEmpty())
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();

		// Put back afterwards, since the inventory is still being drawn around this
		Composite composite = graphics.getComposite();
		int transparency = config.transparency();

		if (transparency > 0)
		{
			graphics.setComposite(
				AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - transparency / 100f));
		}

		for (Potion potion : chosen(potions))
		{
			draw(graphics, metrics, potion);
		}

		graphics.setComposite(composite);

		return null;
	}

	/**
	 * The inventory as it is on screen, or nothing when it is not. A widget counts as hidden while the
	 * tab it is on is closed, so this is also what keeps the icons off a bank or a quest journal that
	 * happens to be in front of it.
	 */
	private Widget container()
	{
		for (int id : CONTAINERS)
		{
			Widget widget = client.getWidget(id);

			if (widget != null && !widget.isHidden())
			{
				return widget;
			}
		}

		return null;
	}

	/**
	 * Every slot holding something there is a row to put on, in the order the slots are filled, which
	 * reads left to right from the top.
	 */
	private List<Potion> potionsIn(Widget container)
	{
		List<Potion> potions = new ArrayList<>();

		if (stale || itemsTick != client.getTickCount())
		{
			items.clear();
			itemsTick = client.getTickCount();
			stale = false;
		}

		Widget dragged = client.getDraggedWidget();
		Point carried = carried(dragged);

		for (int slot = 0; slot < SLOTS; slot++)
		{
			Widget child = container.getChild(slot);

			if (child == null || child.getItemId() <= 0)
			{
				continue;
			}

			Item item = items.computeIfAbsent(child.getItemId(), this::itemOf);

			if (item.rows.isEmpty())
			{
				continue;
			}

			Rectangle bounds = child.getBounds();

			if (child == dragged && carried != null)
			{
				bounds.translate(carried.x, carried.y);
				within(bounds, container.getBounds());
			}

			potions.add(new Potion(bounds, item.rows, child.getItemId(), slot, item.potion));
		}

		return potions;
	}

	/** Moves a rectangle back inside another one, as far as it has to go and no further. */
	private static void within(Rectangle bounds, Rectangle room)
	{
		bounds.x = Math.min(Math.max(bounds.x, room.x), room.x + room.width - bounds.width);
		bounds.y = Math.min(Math.max(bounds.y, room.y), room.y + room.height - bounds.height);
	}

	/**
	 * How far the item the mouse has hold of has been carried from the slot it belongs to, or nothing
	 * when nothing is being carried. A slot keeps its item until the mouse has held it for a moment and
	 * taken it somewhere, so the rows wait for the same before they go anywhere, and once they have gone
	 * they stay with the mouse however near it comes back to where it started.
	 */
	private Point carried(Widget dragged)
	{
		if (dragged == null)
		{
			grabbed = null;
			carrying = false;
			return null;
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();

		if (grabbed == null)
		{
			grabbed = new Point(mouse.getX(), mouse.getY());
		}

		Point carried = new Point(mouse.getX() - grabbed.x, mouse.getY() - grabbed.y);

		if (!carrying)
		{
			if (client.getDragTime() <= dragged.getDragDeadTime()
				|| carried.distanceSq(0, 0) < CARRIED * CARRIED)
			{
				return null;
			}

			carrying = true;
		}

		return carried;
	}

	/**
	 * What drinking an item now would do to the skills it touches, asked of the game rather than kept
	 * here as a list of potions, so the mixes, the divines and the brews are read the same way a plain
	 * super strength is. Whether a skill it would leave where it already is has a row of its own is for
	 * the settings to say, so they are all kept here.
	 *
	 * <p>Whether it is a potion at all is worked out here as well, from what it touches rather than from
	 * how far it moves it. Food heals your hitpoints and does nothing else but put your run back, so a
	 * guthix rest is food for all that it carries doses in its name. Everything else is a potion, which
	 * is a super restore for the prayer it puts back and a stamina potion for the running.
	 *
	 * <p>The skills touched are read whether the item would move them this tick or not, so a potion
	 * does not turn into food for as long as you have nothing to restore.
	 */
	private Item itemOf(int id)
	{
		Effect effect = statChanges.get(id);

		if (effect == null)
		{
			return Item.NOTHING;
		}

		StatsChanges stats = effect.calculate(client);

		if (stats == null || stats.getStatChanges() == null)
		{
			return Item.NOTHING;
		}

		List<StatChange> changed = new ArrayList<>(stats.getStatChanges().length);
		boolean heals = false;
		boolean skills = false;

		for (StatChange change : stats.getStatChanges())
		{
			if (change == null)
			{
				continue;
			}

			Stat stat = change.getStat();

			heals |= stat == Stats.HITPOINTS;
			skills |= stat != Stats.HITPOINTS && stat != Stats.RUN_ENERGY;

			if (stat == Stats.HITPOINTS && client.getVarbitValue(FRAILTY) > 0)
			{
				unhealed(change);
			}

			if (SkillIcons.of(stat) != SkillIcons.NONE)
			{
				changed.add(change);
			}
		}

		return new Item(shown(changed), skills || !heals);
	}

	/**
	 * Takes the overheal out of a hitpoints row, for the invocation that allows none. What a brew would
	 * have given is left as it was, so the row still reads as a boost going to waste rather than as a boost
	 * you would get all of, and a bar that is already full reads as nothing to gain.
	 */
	private void unhealed(StatChange change)
	{
		int max = client.getRealSkillLevel(Skill.HITPOINTS);

		if (change.getAbsolute() <= max)
		{
			return;
		}

		int relative = Math.max(0, max - client.getBoostedSkillLevel(Skill.HITPOINTS));

		change.setAbsolute(change.getAbsolute() - change.getRelative() + relative);
		change.setRelative(relative);
		change.setPositivity(relative > 0 ? Positivity.BETTER_CAPPED : Positivity.NO_CHANGE);
	}

	/**
	 * The rows left once the settings have had the skills you do not care about out of them, the ones a
	 * potion takes away rather than gives, and the ones it would hardly move.
	 *
	 * <p>A skill a potion would not move at all has a setting of its own rather than a minimum of
	 * nothing, since the minimum is about a skill that would hardly move. It is the last of the colours
	 * Item Stats has a setting for: the row reads in the no change colour.
	 */
	private List<StatChange> shown(List<StatChange> changed)
	{
		List<StatChange> shown = new ArrayList<>(changed.size());
		int minimum = config.minimum();

		for (StatChange change : changed)
		{
			int relative = change.getRelative();
			boolean worth = relative == 0
				? config.noChange()
				: (relative > 0 || config.drains()) && Math.abs(relative) >= minimum;

			if (worth && wanted(change.getStat()))
			{
				shown.add(change);
			}
		}

		return shown;
	}

	/**
	 * Whether a skill is one you asked to see. Writing out the skills to show takes the place of the
	 * combat setting rather than being read on top of it, since the two together would leave you writing
	 * out a skill and still not being shown it. The skills to leave out are read after either of them, so
	 * a skill written into both lists is left out, and a list that names nothing is no list at all.
	 */
	private boolean wanted(Stat stat)
	{
		String name = flattened(stat.getName());

		if (hiding.of(config.hiddenSkills()).contains(name))
		{
			return false;
		}

		Set<String> shown = showing.of(config.shownSkills());

		if (!shown.isEmpty())
		{
			return shown.contains(name);
		}

		return !config.combatOnly() || FIGHTING.contains(stat);
	}

	/**
	 * The skills named in a setting, taken apart as it was last written rather than for every row of
	 * every frame. Spaces and capitals are ignored, so a run energy is found however it was typed.
	 */
	private static class Listing
	{
		private Set<String> names = Set.of();

		private String from = null;

		private Set<String> of(String written)
		{
			if (!written.equals(from))
			{
				from = written;
				names = new HashSet<>();

				for (String name : written.split(","))
				{
					String trimmed = flattened(name);

					if (!trimmed.isEmpty())
					{
						names.add(trimmed);
					}
				}
			}

			return names;
		}
	}

	private static String flattened(String name)
	{
		return name.replace(" ", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * One of each potion, since labelling all four doses of a super strength says the same thing four
	 * times over. A potion is the same potion as another when their names match but for the doses left
	 * in them, and the one labelled is the one with the least left in it, which is the one worth
	 * drinking first. Only when two are down to the same dose is there a choice left to make, and that
	 * one is the setting: whichever of them is nearer the corner it names.
	 *
	 * <p>Unless food is asked for, this is also where food goes, which is anything that heals you and
	 * does nothing else but put your run back.
	 */
	private List<Potion> chosen(List<Potion> potions)
	{
		Map<String, Potion> kept = new LinkedHashMap<>();

		for (Potion potion : potions)
		{
			if (!config.food() && !potion.potion)
			{
				continue;
			}

			Named named = names.computeIfAbsent(potion.item, this::nameOf);

			potion.dose = named.dose;

			Potion held = kept.get(named.name);

			if (held == null
				|| potion.dose < held.dose
				|| potion.dose == held.dose && corner(potion.slot) < corner(held.slot))
			{
				kept.put(named.name, potion);
			}
		}

		return new ArrayList<>(kept.values());
	}

	/**
	 * An item name with the doses taken off the end of it, so that the four doses of a super strength are
	 * the one potion, and the doses themselves, which say which of them is nearest gone.
	 */
	private Named nameOf(int item)
	{
		String name = itemManager.getItemComposition(item).getName();
		Matcher dose = DOSE.matcher(name);

		return dose.find()
			? new Named(name.substring(0, dose.start()), Integer.parseInt(dose.group(1)))
			: new Named(name, 0);
	}

	/**
	 * How far a slot is from the corner the setting works in from, counted the way the slots are filled
	 * from that corner instead of from the top left, so that the nearer of two is the smaller number
	 * whichever corner was asked for.
	 */
	private int corner(int slot)
	{
		PotionBoostsConfig.Prioritize from = config.prioritize();
		int row = slot / COLUMNS;
		int column = slot % COLUMNS;

		return (from.isBottom() ? ROWS - 1 - row : row) * COLUMNS
			+ (from.isRight() ? COLUMNS - 1 - column : column);
	}

	/**
	 * A row for each skill, down the middle of the slot with the icon on the left of its number. The
	 * number is in the colour Item Stats would have given it, so a boost that would go to waste reads as
	 * one at a glance.
	 *
	 * <p>Past a few skills the rows go up two abreast, since a slot is wide enough for two of them and a
	 * brew moving six skills down one column leaves each row too short to read. They read across before
	 * they read down, the way a list does.
	 */
	private void draw(Graphics2D graphics, FontMetrics metrics, Potion potion)
	{
		Rectangle bounds = potion.bounds;
		int count = potion.changes.size();

		int columns = count > CROWDED ? 2 : 1;
		int rows = (count + columns - 1) / columns;
		int width = columns == 1 ? bounds.width : (bounds.width + GUTTER) / columns;
		int room = Math.min(MAX_ROW, bounds.height / rows);
		String[] labels = new String[count];
		int widest = 0;

		for (int at = 0; at < count; at++)
		{
			labels[at] = label(potion.changes.get(at), columns);
			widest = Math.max(widest, metrics.stringWidth(labels[at]));
		}

		// Whichever is the less of the room a row has and the room left beside the longest number
		int iconHeight = Math.max(MIN_ICON, Math.min(room - 2, width - widest));

		// A row takes all the room it is given rather than only what its icon needs, so that two skills
		// side by side are read as a square of four and not as two lines of numbers
		int height = room;
		int top = bounds.y + (bounds.height - rows * height) / 2;
		BufferedImage[] marks = new BufferedImage[count];
		int[] reach = new int[columns];

		for (int at = 0; at < count; at++)
		{
			marks[at] = icon(SkillIcons.of(potion.changes.get(at).getStat()), iconHeight);
			reach[at % columns] = Math.max(reach[at % columns],
				(marks[at] == null ? 0 : marks[at].getWidth()) + metrics.stringWidth(labels[at]));
		}

		// The second column begins where the first one ends rather than halfway along the slot, so that a
		// column of numbers narrower than the widest of them is not held apart from the one beside it. It
		// gives way when that would carry it past the gap to the next slot.
		int beside = Math.min(reach[0], bounds.width + GUTTER - reach[columns - 1]);

		for (int at = 0; at < count; at++)
		{
			BufferedImage icon = marks[at];
			int x = bounds.x + at % columns * beside;
			int y = top + at / columns * height;

			if (icon != null)
			{
				graphics.drawImage(icon, x, y + (height - icon.getHeight()) / 2, null);
				x += icon.getWidth();
			}

			int baseline = y + (height + metrics.getAscent()) / 2 - 1;

			graphics.setColor(Color.BLACK);
			graphics.drawString(labels[at], x + 1, baseline + 1);
			graphics.setColor(colour(potion.changes.get(at)));
			graphics.drawString(labels[at], x, baseline);
		}
	}

	/**
	 * The colour Item Stats would give a row, except that a boost most of which you would get is told
	 * apart from one you would hardly get any of. Item Stats has a colour between its best and its worst
	 * that it never puts to use, since anything at all held back by your level is the one colour there, so
	 * all three are put to use here: all of the boost, most of it, little of it.
	 */
	private Color colour(StatChange change)
	{
		Positivity positivity = change.getPositivity();

		if (positivity == Positivity.BETTER_CAPPED && change.getRelative() * 2 >= change.getTheoretical())
		{
			positivity = Positivity.BETTER_SOMECAPPED;
		}

		return Positivity.getColor(colours, positivity);
	}

	/**
	 * How much it would give, or the level it would take you to, which is the setting. Side by side there
	 * is no room for the plus in front of a boost, which is seven of the twenty one pixels a column has, and
	 * nothing is lost by it: what it was saying is said again by the colour, and a drain keeps its minus.
	 */
	private String label(StatChange change, int columns)
	{
		if (config.number() == PotionBoostsConfig.Number.LEVEL)
		{
			return Integer.toString(change.getAbsolute());
		}

		String relative = change.getFormattedRelative();

		return columns > 1 && relative.startsWith("+") ? relative.substring(1) : relative;
	}

	/**
	 * A skill icon with a black outline around it, which is what keeps it off the potion behind it. The
	 * outline has to go somewhere, so the sprite is given a pixel of room on each side for it first.
	 */
	private BufferedImage icon(int sprite, int height)
	{
		int key = sprite << 8 | height;
		BufferedImage icon = icons.get(key);

		if (icon != null)
		{
			return icon;
		}

		BufferedImage image = spriteManager.getSprite(sprite, 0);

		if (image == null || image.getWidth() < 1 || image.getHeight() < 1)
		{
			// Still being loaded, so there is nothing to prepare yet
			return null;
		}

		// Scaled before it is outlined, so the outline is a pixel wide at the size it ends up drawn at
		icon = outlined(scaled(image, height - 2));

		icons.put(key, icon);

		return icon;
	}

	/**
	 * The sprite at the height the icons are drawn at, keeping its shape, taken a pixel at a time so
	 * that every pixel of it stays the colour it was drawn in. Blending them instead leaves an edge
	 * that fades out over a pixel or two, which is nowhere in particular for an outline to go.
	 */
	private static BufferedImage scaled(BufferedImage sprite, int height)
	{
		if (height == sprite.getHeight())
		{
			return sprite;
		}

		int width = Math.max(1, Math.round(sprite.getWidth() * height / (float) sprite.getHeight()));
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				// The middle of what the pixel covers of the sprite, so that it lines up the same way
				// in from either edge
				scaled.setRGB(x, y, sprite.getRGB(
					Math.min((x * 2 + 1) * sprite.getWidth() / (width * 2), sprite.getWidth() - 1),
					Math.min((y * 2 + 1) * sprite.getHeight() / (height * 2), sprite.getHeight() - 1)));
			}
		}

		return scaled;
	}

	/**
	 * Black put in around a sprite, wherever the sprite does not have something dark enough there
	 * already, so the icon ends up with an edge of one pixel all the way round rather than two in the
	 * places the artwork was already outlined.
	 */
	private static BufferedImage outlined(BufferedImage sprite)
	{
		int width = sprite.getWidth() + 2;
		int height = sprite.getHeight() + 2;

		// A pixel of room on each side, for the outline to have somewhere to go
		BufferedImage image = ImageUtil.resizeCanvas(sprite, width, height);
		BufferedImage outlined = ImageUtil.resizeCanvas(sprite, width, height);

		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				// Read from the sprite throughout, so a pixel just filled in cannot grow the outline
				if (empty(image, x, y) && bordersLight(image, x, y))
				{
					outlined.setRGB(x, y, Color.BLACK.getRGB());
				}
			}
		}

		return outlined;
	}

	private static boolean empty(BufferedImage image, int x, int y)
	{
		return (image.getRGB(x, y) >>> 24) < SOLID;
	}

	/**
	 * Whether any of the eight pixels around this one is part of the icon and light enough to need an
	 * outline of its own.
	 */
	private static boolean bordersLight(BufferedImage image, int x, int y)
	{
		for (int alongX = Math.max(x - 1, 0); alongX <= Math.min(x + 1, image.getWidth() - 1); alongX++)
		{
			for (int alongY = Math.max(y - 1, 0); alongY <= Math.min(y + 1, image.getHeight() - 1); alongY++)
			{
				int pixel = image.getRGB(alongX, alongY);

				if ((pixel >>> 24) >= SOLID && !dark(pixel))
				{
					return true;
				}
			}
		}

		return false;
	}

	private static boolean dark(int pixel)
	{
		int lightest = Math.max((pixel >> 16) & 0xff, Math.max((pixel >> 8) & 0xff, pixel & 0xff));

		return lightest < DARK;
	}

	/**
	 * What an item in the inventory would show, worked out once for the tick rather than once for each of
	 * the slots holding one.
	 */
	private static class Item
	{
		private static final Item NOTHING = new Item(List.of(), false);

		private final List<StatChange> rows;

		/**
		 * Whether it moves a skill rather than only healing you, which is what tells a potion from food.
		 */
		private final boolean potion;

		private Item(List<StatChange> rows, boolean potion)
		{
			this.rows = rows;
			this.potion = potion;
		}
	}

	/** An item name without its doses, and the doses it was holding. */
	private static class Named
	{
		private final String name;
		private final int dose;

		private Named(String name, int dose)
		{
			this.name = name;
			this.dose = dose;
		}
	}

	private static class Potion
	{
		private final Rectangle bounds;
		private final List<StatChange> changes;
		private final int item;
		private final int slot;
		private final boolean potion;
		private int dose;

		private Potion(Rectangle bounds, List<StatChange> changes, int item, int slot, boolean potion)
		{
			this.bounds = bounds;
			this.changes = changes;
			this.item = item;
			this.slot = slot;
			this.potion = potion;
		}
	}
}
