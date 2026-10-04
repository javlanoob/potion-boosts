package com.potionboost;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.StatsChanges;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

class PotionBoostOverlay extends Overlay
{
	/**
	 * The inventory, and the inventory as it is drawn beside an open bank, which is a different widget
	 * holding the same twenty-eight slots.
	 */
	private static final int[] CONTAINERS = {
		InterfaceID.Inventory.ITEMS,
		InterfaceID.Bankside.ITEMS
	};

	private static final int SLOTS = 28;

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
	private final PotionBoostConfig config;

	/**
	 * The outlined icons, by sprite and by the height they were prepared at, since outlining one is work
	 * that only has to happen once and finding it again is work for every row of every frame.
	 */
	private final Map<Integer, BufferedImage> icons = new HashMap<>();

	/**
	 * What each item in the inventory would boost, and the tick that was worked out on. The answer
	 * depends on the levels you are at, so it is thrown away once a tick rather than kept, and worked
	 * out once a tick rather than once a frame.
	 */
	private final Map<Integer, List<StatChange>> boosts = new HashMap<>();

	private int boostsTick = -1;

	@Inject
	PotionBoostOverlay(
		Client client,
		ItemManager itemManager,
		ItemStatChanges statChanges,
		SpriteManager spriteManager,
		PotionBoostConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.client = client;
		this.itemManager = itemManager;
		this.statChanges = statChanges;
		this.spriteManager = spriteManager;
		this.config = config;
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

		for (Potion potion : chosen(potions))
		{
			draw(graphics, metrics, potion);
		}

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
	 * Every slot holding something that would put a skill above where it is trained to, in the order the
	 * slots are filled, which reads left to right from the top.
	 */
	private List<Potion> potionsIn(Widget container)
	{
		List<Potion> potions = new ArrayList<>();

		if (boostsTick != client.getTickCount())
		{
			boosts.clear();
			boostsTick = client.getTickCount();
		}

		for (int slot = 0; slot < SLOTS; slot++)
		{
			Widget child = container.getChild(slot);

			if (child == null || child.getItemId() <= 0)
			{
				continue;
			}

			List<StatChange> changes = boosts.computeIfAbsent(child.getItemId(), this::boostsOf);

			if (!changes.isEmpty())
			{
				potions.add(new Potion(child.getBounds(), changes, child.getItemId()));
			}
		}

		return potions;
	}

	/**
	 * What drinking an item now would raise above the level it is trained to. Anything the game has stats
	 * for is asked, so a boost is one that lands over the maximum rather than one off a list of potions,
	 * which leaves out food and the potions that only put back what has been used, and leaves out a
	 * boost you are already holding, since there is nothing left there to gain.
	 */
	private List<StatChange> boostsOf(int item)
	{
		Effect effect = statChanges.get(item);

		if (effect == null)
		{
			return List.of();
		}

		StatsChanges changes = effect.calculate(client);

		if (changes == null || changes.getStatChanges() == null)
		{
			return List.of();
		}

		List<StatChange> boosted = new ArrayList<>();

		for (StatChange change : changes.getStatChanges())
		{
			if (change != null
				&& change.getRelative() > 0
				&& change.getAbsolute() > change.getStat().getMaximum(client)
				&& SkillIcons.of(change.getStat()) != SkillIcons.NONE)
			{
				boosted.add(change);
			}
		}

		return boosted;
	}

	/**
	 * One of each potion, since labelling all four doses of a super strength says the same thing four
	 * times over. A potion is the same potion as another when their names match but for the doses left
	 * in them, and which one of those is kept is the setting: the first one, which is the one nearest the
	 * top left, or the one with the least left in it, which is the one worth drinking first.
	 */
	private List<Potion> chosen(List<Potion> potions)
	{
		Map<String, Potion> kept = new LinkedHashMap<>();
		boolean byDose = config.which() == PotionBoostConfig.Which.SMALLEST_DOSE;

		for (Potion potion : potions)
		{
			String name = itemManager.getItemComposition(potion.item).getName();
			Matcher dose = DOSE.matcher(name);
			boolean dosed = dose.find();

			potion.dose = dosed ? Integer.parseInt(dose.group(1)) : 0;

			String key = dosed ? name.substring(0, dose.start()) : name;
			Potion held = kept.get(key);

			// The potions arrive in slot order, so the one already held is the one nearer the top left
			if (held == null || byDose && potion.dose < held.dose)
			{
				kept.put(key, potion);
			}
		}

		return new ArrayList<>(kept.values());
	}

	/**
	 * A row for each skill, stacked down the middle of the slot with the icon on the left of its number.
	 */
	private void draw(Graphics2D graphics, FontMetrics metrics, Potion potion)
	{
		Rectangle bounds = potion.bounds;
		int rows = potion.changes.size();
		int height = Math.min(MAX_ROW, bounds.height / rows);
		int iconHeight = Math.max(MIN_ICON, height - 2);
		int y = bounds.y + (bounds.height - rows * height) / 2;

		for (StatChange change : potion.changes)
		{
			BufferedImage icon = icon(SkillIcons.of(change.getStat()), iconHeight);
			int x = bounds.x;

			if (icon != null)
			{
				graphics.drawImage(icon, x, y + (height - icon.getHeight()) / 2, null);
				x += icon.getWidth();
			}

			String label = config.number() == PotionBoostConfig.Number.LEVEL
				? Integer.toString(change.getAbsolute())
				: change.getFormattedRelative();

			int baseline = y + (height + metrics.getAscent()) / 2 - 1;

			graphics.setColor(Color.BLACK);
			graphics.drawString(label, x + 1, baseline + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(label, x, baseline);

			y += height;
		}
	}

	/**
	 * A skill's icon with a black outline around it, which is what keeps it off the potion behind it. The
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

	private static class Potion
	{
		private final Rectangle bounds;
		private final List<StatChange> changes;
		private final int item;
		private int dose;

		private Potion(Rectangle bounds, List<StatChange> changes, int item)
		{
			this.bounds = bounds;
			this.changes = changes;
			this.item = item;
		}
	}
}
