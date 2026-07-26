package com.ospulse.session;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Recognises the two signals that open a "production episode" — a stretch of
 * play where the player is converting owned inputs into owned outputs rather
 * than acquiring loot (see {@link SessionEngine}'s episode ledger).
 *
 * <p>Two independent triggers, deliberately OR-ed rather than AND-ed:
 * <ul>
 *   <li><b>XP in a production skill.</b> The obvious signal, and the reliable
 *       one — but not sufficient on its own.</li>
 *   <li><b>A production animation.</b> Load-bearing, not a nicety. Herblore
 *       step 1 (clean herb + Vial of water &rarr; unf potion) awards
 *       <b>zero XP</b>, and that step is exactly where the expensive herb
 *       goes. An XP-only trigger would leave the most costly input of a
 *       herblore session uncharged — i.e. it would reproduce the very bug the
 *       episode ledger exists to fix.</li>
 * </ul>
 *
 * <p>Pure domain type: no RuneLite imports, unit-testable without a game
 * client. The integration layer maps its client events onto these two
 * questions; the engine only ever consumes the boolean answers, so a wrong or
 * missing animation id can never corrupt the engine's accounting logic — it
 * can only fail to open an episode.
 */
public final class ProductionActivity
{
	private ProductionActivity()
	{
	}

	/**
	 * Skills whose XP means "converting inputs into outputs".
	 *
	 * <p>Firemaking and Farming are deliberately EXCLUDED. Both burn real
	 * inputs, but Wintertodt/Tithe-style content is drop-heavy, and an episode
	 * that spans genuine drops leans harder on the loot-receipt correlation
	 * than it needs to. Revisit once the model is proven on the five below.
	 *
	 * <p>Stored as RuneLite {@code Skill.name()} strings so this class stays
	 * free of client imports.
	 */
	private static final Set<String> PRODUCTION_SKILLS = Collections.unmodifiableSet(
		new HashSet<>(Arrays.asList(
			"HERBLORE",
			"CRAFTING",
			"FLETCHING",
			"SMITHING",
			"COOKING")));

	/**
	 * Animation ids that mean "a production action is in progress".
	 *
	 * <p><b>Provenance: every id below is a named constant in RuneLite's own
	 * {@code net.runelite.api.AnimationID} or {@code net.runelite.api.gameval.AnimationID}.</b>
	 * None is guessed, wiki-sourced or observed. The constant name is quoted
	 * beside each id, and {@link com.ospulse.session.ProductionAnimationProvenanceTest}
	 * re-resolves all of them against the runelite-api jar on every build — so an id
	 * that stops naming a production action fails the suite rather than silently
	 * mis-classifying play.
	 *
	 * <p><b>Inclusion rule</b> (apply it when extending this set):
	 * <ol>
	 *   <li>The id must have a named constant in one of the two RuneLite
	 *       {@code AnimationID} classes.</li>
	 *   <li>The constant name must identify the <i>local player</i> performing a
	 *       <i>material-consuming step</i> in one of {@link #PRODUCTION_SKILLS}.</li>
	 *   <li>Excluded by name, deliberately: {@code WOODCRAFTING_*} (that is
	 *       woodcutting, not Crafting), {@code *_LEATHER_HIT_*} (combat blocks),
	 *       {@code SKILLCAPES_*}, {@code VFX_*}, {@code *_NPC}, {@code *_IDLE},
	 *       {@code *_ENTER}, {@code *NO_ITEMS*} (nothing is consumed) and
	 *       {@code FARMING_*} (Farming is excluded by design — see
	 *       {@link #PRODUCTION_SKILLS}).</li>
	 * </ol>
	 *
	 * <p>Narrow one-off and quest craft animations (Rancor, Rupture, Confliction,
	 * bone claws, noxious halberd, Armadylean, spiked vambraces, shield crafting,
	 * spider silk, ogre fletching) are omitted rather than included. That is the
	 * safe direction: a missing id is a false negative, which degrades gracefully
	 * (see below). They can be added on request.
	 *
	 * <p>How the two possible errors behave, so the risk is legible:
	 * <ul>
	 *   <li><b>A missing id (false negative)</b> degrades gracefully: the episode
	 *       simply does not open from animation. XP still opens it, so every
	 *       XP-granting production step keeps working. The only loss is a
	 *       zero-XP step.</li>
	 *   <li><b>An id that is not really production (false positive)</b> is the one
	 *       to be careful about. Its blast radius is small but real: because
	 *       episode P&L is defined as (tracked change − loot booked), a
	 *       wrongly-opened episode leaves Profit's ARITHMETIC unchanged and merely
	 *       relabels an acquisition out of the Loot feed. The genuine hazard is
	 *       narrower — parking items outside tracked wealth (rune pouch, coal bag)
	 *       during a falsely-open episode would be charged as an input. Prefer
	 *       omitting a doubtful id over including it.</li>
	 * </ul>
	 */
	private static final Set<Integer> PRODUCTION_ANIMATION_IDS = Collections.unmodifiableSet(
		new HashSet<>(Arrays.asList(
			// --- Herblore ------------------------------------------------
			363,   // AnimationID.HERBLORE_POTIONMAKING
			364,   // AnimationID.HERBLORE_PESTLE_AND_MORTAR
			5249,   // AnimationID.HERBLORE_MAKE_TAR
			11604,   // AnimationID.HERBLORE_MIXOLOGY_REFINER
			11634,   // AnimationID.HERBLORE_MIXOLOGY_HOMOGENIZE
			11639,   // AnimationID.HERBLORE_MIXOLOGY_CRYSTALIZE
			11644,   // AnimationID.HERBLORE_MIXOLOGY_CONCENTRATE
			11094,   // gameval.AnimationID.HUMAN_HERBING_VIAL_RESTART
			11095,   // gameval.AnimationID.HUMAN_HERBING_GRIND_RESTART
			14416,   // gameval.AnimationID.HUMAN_HERBING_VIAL_ONLY
			// --- Crafting ------------------------------------------------
			883,   // AnimationID.CRAFTING_POTTERS_WHEEL
			884,   // AnimationID.CRAFTING_GLASSBLOWING
			894,   // AnimationID.CRAFTING_SPINNING
			1249,   // AnimationID.CRAFTING_LEATHER
			2270,   // AnimationID.CRAFTING_LOOM
			7531,   // AnimationID.CRAFTING_BATTLESTAVES
			11099,   // AnimationID.CRAFTING_CRUSH_BLESSED_BONES
			24975,   // AnimationID.CRAFTING_POTTERY_OVEN
			10794,   // gameval.AnimationID.HUMAN_CRAFTING
			13138,   // gameval.AnimationID.HUMAN_SPINNINGWHEEL_90
			13139,   // gameval.AnimationID.HUMAN_SPINNINGWHEEL_60
			// --- Fletching -----------------------------------------------
			1248,   // AnimationID.FLETCHING_BOW_CUTTING
			4436,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_BRONZE_LIMBS
			4437,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_BLURITE_LIMBS
			4438,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_IRON_LIMBS
			4439,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_STEEL_LIMBS
			4440,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_MITHRIL_LIMBS
			4441,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_ADAMANTITE_LIMBS
			4442,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_RUNITE_LIMBS
			6678,   // AnimationID.FLETCHING_STRING_NORMAL_SHORTBOW
			6679,   // AnimationID.FLETCHING_STRING_OAK_SHORTBOW
			6680,   // AnimationID.FLETCHING_STRING_WILLOW_SHORTBOW
			6681,   // AnimationID.FLETCHING_STRING_MAPLE_SHORTBOW
			6682,   // AnimationID.FLETCHING_STRING_YEW_SHORTBOW
			6683,   // AnimationID.FLETCHING_STRING_MAGIC_SHORTBOW
			6684,   // AnimationID.FLETCHING_STRING_NORMAL_LONGBOW
			6685,   // AnimationID.FLETCHING_STRING_OAK_LONGBOW
			6686,   // AnimationID.FLETCHING_STRING_WILLOW_LONGBOW
			6687,   // AnimationID.FLETCHING_STRING_MAPLE_LONGBOW
			6688,   // AnimationID.FLETCHING_STRING_YEW_LONGBOW
			6689,   // AnimationID.FLETCHING_STRING_MAGIC_LONGBOW
			7860,   // AnimationID.FLETCHING_ATTACH_STOCK_TO_DRAGON_LIMBS
			8472,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_BRONZE_BOLT
			8473,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_IRON_BROAD_BOLT
			8474,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_BLURITE_BOLT
			8475,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_STEEL_BOLT
			8476,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_MITHRIL_BOLT
			8477,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_ADAMANT_BOLT
			8478,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_RUNE_BOLT
			8479,   // AnimationID.FLETCHING_ATTACH_BOLT_TIPS_TO_DRAGON_BOLT
			8480,   // AnimationID.FLETCHING_ATTACH_HEADS
			8481,   // AnimationID.FLETCHING_ATTACH_FEATHERS_TO_ARROWSHAFT
			5244,   // gameval.AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS
			8463,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_BRONZE
			8464,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_IRON
			8465,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_SILVER
			8466,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_BLURITE
			8467,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_STEEL
			8468,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_MITHRIL
			8469,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_ADAMANT
			8470,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_RUNE
			8471,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_DRAGON
			8482,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_BRONZE
			8483,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_IRON
			8484,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_STEEL
			8485,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_MITHRIL
			8486,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_ADAMANT
			8487,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_RUNE
			8488,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_DRAGON
			9108,   // gameval.AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_AMETHYST
			11096,   // gameval.AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS_SINGLE
			11097,   // gameval.AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS_CHISEL
			11098,   // gameval.AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS_CHISEL_SINGLE
			11100,   // gameval.AnimationID.HUMAN_FLETCHING_SINGLE
			13150,   // gameval.AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS_CHISEL_SINGLE_QUICK
			// --- Smithing ------------------------------------------------
			827,   // AnimationID.SMITHING_CANNONBALL
			898,   // AnimationID.SMITHING_ANVIL
			899,   // AnimationID.SMITHING_SMELTING
			8911,   // AnimationID.SMITHING_IMCANDO_HAMMER
			8894,   // gameval.AnimationID.HUMAN_SMITHING_NOREPLACE
			// --- Cooking -------------------------------------------------
			896,   // AnimationID.COOKING_RANGE
			897,   // AnimationID.COOKING_FIRE
			7529,   // AnimationID.COOKING_WINE
			11735   // gameval.AnimationID.HUMAN_COOKING_LOOP
			)));

	/** Whether XP in {@code skillName} (a RuneLite {@code Skill.name()}) opens an episode. */
	public static boolean isProductionSkill(String skillName)
	{
		return skillName != null && PRODUCTION_SKILLS.contains(skillName);
	}

	/**
	 * Whether {@code animationId} is a production animation. {@code -1} (the
	 * client's "no animation") and every unrecognised id answer {@code false}.
	 */
	public static boolean isProductionAnimation(int animationId)
	{
		return PRODUCTION_ANIMATION_IDS.contains(animationId);
	}
}
