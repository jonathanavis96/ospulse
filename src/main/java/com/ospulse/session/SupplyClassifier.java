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

	private SupplyClassifier()
	{
	}

	/**
	 * @return true if {@code itemName} looks like a consumable supply item
	 *         (potion, food, ammunition or rune) worth tracking as "supplies
	 *         used" when its quantity decreases.
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
			|| FOOD_PATTERN.matcher(trimmed).matches();
	}
}
