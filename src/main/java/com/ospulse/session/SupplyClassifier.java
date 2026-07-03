package com.ospulse.session;

import java.util.regex.Pattern;

/**
 * Classifies tracked items as "supplies" (consumables typically burned through
 * during a session: potions, food, ammunition, runes) using a small,
 * data-light set of name patterns rather than a large hardcoded item-id list.
 *
 * <p>This is deliberately a heuristic, not an exhaustive taxonomy:
 * <ul>
 *   <li>Potions/food/ammo/runes are common enough, and named consistently
 *       enough, that regex matching on the item name catches the vast
 *       majority without needing per-item ids.</li>
 *   <li>False negatives (a consumable this doesn't recognise) simply aren't
 *       counted as supplies — they still reduce profit as before, just
 *       without a line item in "supplies used".</li>
 *   <li>False positives are guarded against by keeping the patterns narrow
 *       (e.g. "rune" alone would match "runite ore"/"rune platebody"; we
 *       require a known rune-name prefix instead).</li>
 * </ul>
 *
 * <p>To extend: add a new pattern (or a name) to the relevant list below.
 * Keep patterns anchored/specific enough to avoid catching gear or raw
 * materials that merely share a substring with a consumable name.
 */
public final class SupplyClassifier
{
	/** Potions: "<name> potion(#)", "<name> mix(#)", "<name> flask(#)", etc. */
	private static final Pattern POTION_PATTERN = Pattern.compile(
		"(?i).*\\b(potion|mix|flask|brew|barbarian mix)\\s*\\(?\\d?\\)?$");

	/** Ammunition: arrows, bolts, darts, javelins, thrown knives/axes, and tips. */
	private static final Pattern AMMO_PATTERN = Pattern.compile(
		"(?i).*\\b(arrow|bolts?|dart|javelin|throwing knife|thrown knife|"
			+ "chinchompa|bolt tips?|dragonfire|knife)s?$");

	/** Runes: elemental/catalytic/combination runes by their canonical names. */
	private static final Pattern RUNE_PATTERN = Pattern.compile(
		"(?i)^(air|water|earth|fire|mind|body|cosmic|chaos|nature|law|death|"
			+ "blood|soul|astral|wrath|mist|dust|smoke|steam|lava|"
			+ "old school)\\s+rune$");

	/**
	 * Food: broad net of common consumable-food name patterns. Kept separate
	 * from potions since food names don't share the "(N)" dose suffix.
	 */
	private static final Pattern FOOD_PATTERN = Pattern.compile(
		"(?i).*\\b(shark|lobster|swordfish|monkfish|karambwan|anglerfish|"
			+ "manta ray|tuna|salmon|trout|pike|herring|sardine|shrimp|"
			+ "anchovies|cake|pie|stew|bread|cheese|tomato|potato|"
			+ "kebab|jug of wine|pizza|curry|crab|mackerel|cod|bass|"
			+ "meat|rocktail|cooked)s?$");

	/**
	 * Teleport tablets and other single-use teleport consumables: single-charge
	 * items burned on use exactly like a potion dose or a piece of food (e.g.
	 * "Teleport to house", "Varrock teleport", "Ardougne teleport", "Teleport to
	 * target"). Deliberately requires the "teleport" word itself so it doesn't
	 * catch teleport jewellery (rings/amulets/capes), which are equipped gear
	 * with charges, not a tracked-wealth item whose whole stack is consumed the
	 * way a tablet is; those are out of scope for this classifier the same way
	 * other equippable-with-charges items are.
	 */
	private static final Pattern TELEPORT_PATTERN = Pattern.compile(
		"(?i)^(teleport to [\\w' ]+|[\\w' ]+ teleport)\\s*(tablet)?$");

	/**
	 * Generic fallback for any 1-4 dose consumable (potions, brews, restores,
	 * etc.) whose base name isn't otherwise recognised by {@link
	 * #POTION_PATTERN}. Bounded to doses 1-4 deliberately: charged jewellery
	 * (e.g. "Amulet of glory(6)", "Ring of dueling(8)") uses higher charge
	 * counts in the same "(n)" suffix position and must NOT be swept up here —
	 * those are equippable gear with charges, not a consumed tracked-wealth
	 * stack.
	 */
	private static final Pattern DOSE_PATTERN = Pattern.compile("(?i).*\\([1-4]\\)$");

	private SupplyClassifier()
	{
	}

	/**
	 * @return true if {@code itemName} looks like a consumable supply item
	 *         (potion, food, ammunition, rune or teleport tablet) worth
	 *         tracking as "supplies used" when its quantity decreases.
	 */
	public static boolean isConsumable(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return false;
		}
		String trimmed = itemName.trim();
		return POTION_PATTERN.matcher(trimmed).matches()
			|| AMMO_PATTERN.matcher(trimmed).matches()
			|| RUNE_PATTERN.matcher(trimmed).matches()
			|| FOOD_PATTERN.matcher(trimmed).matches()
			|| TELEPORT_PATTERN.matcher(trimmed).matches()
			|| DOSE_PATTERN.matcher(trimmed).matches();
	}
}
