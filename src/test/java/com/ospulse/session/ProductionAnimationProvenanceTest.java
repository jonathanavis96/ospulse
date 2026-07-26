package com.ospulse.session;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Re-verifies {@link ProductionActivity}'s curated animation-id set against
 * the runelite-api jar and the class's own source comments, so that a wrong
 * or mislabelled id (the {@code 1250}/{@code KALPHITE_QUEEN_RANGED_ATTACK}
 * false positive, and the {@code 1249} fletching mislabel that was really
 * {@code CRAFTING_LEATHER}) fails the build instead of silently
 * mis-classifying play.
 *
 * <p>Reaches {@code PRODUCTION_ANIMATION_IDS} by reflecting the private
 * field (rather than driving {@link ProductionActivity#isProductionAnimation}
 * over ids parsed from source) so assertion 1 below is a genuine membership
 * check against the field itself, independent of the source-comment parse
 * used for assertion 3.
 */
public class ProductionAnimationProvenanceTest
{
	/** Constant names that must NOT appear on a curated id, checked before the allow-list. */
	private static final String[] DENIED_NAME_FRAGMENTS = {
		"WOODCRAFTING",
		"_LEATHER_HIT_",
		"SKILLCAPES",
		"VFX_",
		"_NPC",
		"_IDLE",
		"_ENTER",
		"NO_ITEMS",
		"FARMING",
	};

	/**
	 * Skill-name fragments that make a constant name shape-eligible, once the deny list clears it.
	 *
	 * <p>{@code SPINNINGWHEEL} is an addition beyond the literal skill-name tokens:
	 * {@code HUMAN_SPINNINGWHEEL_90}/{@code _60} (ids 13138/13139, filed under the
	 * source's Crafting section) are named after the spinning-wheel object rather
	 * than the Crafting skill, so they don't contain the substring {@code CRAFTING}.
	 * A spinning wheel is unambiguously a Crafting facility in OSRS (spinning wool
	 * into ball of wool / flax into bow string), so this is a gap in the literal
	 * fragment list rather than a bad curated id — flagged for review.
	 */
	private static final String[] ALLOWED_NAME_FRAGMENTS = {
		"HERBLORE",
		"HERBING",
		"CRAFTING",
		"SPINNINGWHEEL",
		"FLETCHING",
		"SMITHING",
		"COOKING",
	};

	/** {@code id -> constant names} from {@code net.runelite.api.AnimationID} (no HUMAN_ requirement). */
	private static Map<Integer, Set<String>> legacyIdsToNames;

	/** {@code id -> constant names} from {@code net.runelite.api.gameval.AnimationID} (HUMAN_-prefixed only). */
	private static Map<Integer, Set<String>> gamevalIdsToNames;

	@BeforeClass
	public static void resolveRuneliteAnimationConstants() throws Exception
	{
		legacyIdsToNames = new HashMap<>();
		gamevalIdsToNames = new HashMap<>();
		collectIntConstants(Class.forName("net.runelite.api.AnimationID"), legacyIdsToNames);
		collectIntConstants(Class.forName("net.runelite.api.gameval.AnimationID"), gamevalIdsToNames);
		assertTrue("expected a large number of resolved RuneLite animation constants",
			legacyIdsToNames.size() + gamevalIdsToNames.size() > 1000);
	}

	/** {@code id -> every constant name across BOTH AnimationID classes}, for resolution checks only. */
	private static boolean idResolves(int id)
	{
		return legacyIdsToNames.containsKey(id) || gamevalIdsToNames.containsKey(id);
	}

	private static void collectIntConstants(Class<?> animationIdClass, Map<Integer, Set<String>> out)
		throws IllegalAccessException
	{
		for (Field field : animationIdClass.getFields())
		{
			int modifiers = field.getModifiers();
			if (field.getType() != int.class
				|| !Modifier.isStatic(modifiers)
				|| !Modifier.isFinal(modifiers))
			{
				continue;
			}
			int value = field.getInt(null);
			out.computeIfAbsent(value, k -> new HashSet<>()).add(field.getName());
		}
	}

	@SuppressWarnings("unchecked")
	private static Set<Integer> curatedIds() throws Exception
	{
		Field field = ProductionActivity.class.getDeclaredField("PRODUCTION_ANIMATION_IDS");
		field.setAccessible(true);
		return (Set<Integer>) field.get(null);
	}

	/**
	 * Mirrors {@link ProductionActivity}'s inclusion rule from its javadoc:
	 * deny-list beats allow-list (so {@code WOODCRAFTING_*}, which contains
	 * {@code CRAFTING}, is rejected rather than let through), and a gameval
	 * name must additionally be {@code HUMAN_}-prefixed.
	 */
	private static boolean matchesProductionSkillShape(String constantName, boolean isGamevalName)
	{
		for (String denied : DENIED_NAME_FRAGMENTS)
		{
			if (constantName.contains(denied))
			{
				return false;
			}
		}
		if (isGamevalName && !constantName.startsWith("HUMAN_"))
		{
			return false;
		}
		for (String allowed : ALLOWED_NAME_FRAGMENTS)
		{
			if (constantName.contains(allowed))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void everyCuratedIdResolvesInRuneliteAnimationId() throws Exception
	{
		for (int id : curatedIds())
		{
			assertTrue("id " + id + " does not name any net.runelite.api(.gameval).AnimationID constant",
				idResolves(id));
		}
	}

	@Test
	public void everyCuratedIdNamesAProductionSkillAnimation() throws Exception
	{
		for (int id : curatedIds())
		{
			Set<String> legacyNames = legacyIdsToNames.getOrDefault(id, Collections.emptySet());
			Set<String> gamevalNames = gamevalIdsToNames.getOrDefault(id, Collections.emptySet());
			assertTrue("id " + id + " does not resolve; run everyCuratedIdResolvesInRuneliteAnimationId first",
				!legacyNames.isEmpty() || !gamevalNames.isEmpty());

			boolean matchesShape = false;
			for (String name : legacyNames)
			{
				if (matchesProductionSkillShape(name, false))
				{
					matchesShape = true;
					break;
				}
			}
			if (!matchesShape)
			{
				for (String name : gamevalNames)
				{
					if (matchesProductionSkillShape(name, true))
					{
						matchesShape = true;
						break;
					}
				}
			}
			assertTrue("id " + id + " (legacy names=" + legacyNames + ", gameval names=" + gamevalNames
					+ ") matches none of the allowed production-skill name shapes,"
					+ " or is caught by a deny-listed fragment",
				matchesShape);
		}
	}

	@Test
	public void deniedFragmentBeatsAllowedFragment_woodcraftingIsRejectedDespiteContainingCrafting()
	{
		// This is the ordering guard: WOODCRAFTING contains CRAFTING (an allowed
		// fragment), so if the allow-list were checked first this would wrongly
		// pass. The deny list must win.
		assertFalse("HUMAN_WOODCRAFTING_AXE_BRONZE (id 2327) must be rejected: it is woodcutting, not Crafting",
			matchesProductionSkillShape("HUMAN_WOODCRAFTING_AXE_BRONZE", true));
	}

	@Test
	public void sourceCommentsMatchTheRuneliteConstantTheyName() throws Exception
	{
		File sourceFile = findProductionActivitySource();
		assertTrue("could not find ProductionActivity.java source file to verify comment provenance"
				+ " against - this test must FAIL, not skip, when the source can't be located",
			sourceFile != null && sourceFile.isFile());

		String source = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);

		// Matches lines like:
		//   363,   // AnimationID.HERBLORE_POTIONMAKING
		//   11094,   // gameval.AnimationID.HUMAN_HERBING_VIAL_RESTART
		// including the terminal entry that lacks a trailing comma:
		//   11735   // gameval.AnimationID.HUMAN_COOKING_LOOP
		Pattern idCommentPattern = Pattern.compile(
			"(\\d+),?\\s*//\\s*(gameval\\.)?AnimationID\\.([A-Za-z0-9_]+)");
		Matcher matcher = idCommentPattern.matcher(source);

		int pairsChecked = 0;
		while (matcher.find())
		{
			int id = Integer.parseInt(matcher.group(1));
			boolean isGameval = matcher.group(2) != null;
			String constantName = matcher.group(3);

			Class<?> animationIdClass = Class.forName(
				isGameval ? "net.runelite.api.gameval.AnimationID" : "net.runelite.api.AnimationID");
			Field constantField = animationIdClass.getField(constantName);
			int actualValue = constantField.getInt(null);

			assertEquals("source comment claims " + (isGameval ? "gameval." : "") + "AnimationID."
					+ constantName + " == " + id + ", but the runelite-api jar says it is " + actualValue,
				id, actualValue);
			pairsChecked++;
		}

		assertTrue("expected to find and check id/comment pairs in the source file, found none - "
				+ "the parsing regex may no longer match the file's format",
			pairsChecked >= 80);
	}

	private static File findProductionActivitySource()
	{
		String relativePath = "src/main/java/com/ospulse/session/ProductionActivity.java";
		File fromWorkingDir = new File(relativePath);
		if (fromWorkingDir.isFile())
		{
			return fromWorkingDir;
		}
		// Gradle sometimes runs tests from a different working directory; walk up
		// from here looking for the project root.
		File dir = new File("").getAbsoluteFile();
		while (dir != null)
		{
			File candidate = new File(dir, relativePath);
			if (candidate.isFile())
			{
				return candidate;
			}
			dir = dir.getParentFile();
		}
		return null;
	}

	@Test
	public void regression_kalphiteQueenRangedAttackIsNotProduction()
	{
		// 1250 is KALPHITE_QUEEN_RANGED_ATTACK - a monster attack animation, not
		// something the local player performs. Previously misclassified as a
		// production animation; must never be true again.
		assertFalse(ProductionActivity.isProductionAnimation(1250));
	}

	@Test
	public void regression_herblorePotionmakingIsProduction()
	{
		assertTrue(ProductionActivity.isProductionAnimation(363));
	}

	@Test
	public void regression_noAnimationSentinelIsNotProduction()
	{
		assertFalse(ProductionActivity.isProductionAnimation(-1));
	}
}
