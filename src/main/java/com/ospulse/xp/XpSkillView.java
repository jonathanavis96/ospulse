package com.ospulse.xp;

/**
 * Immutable per-skill view of session XP progress, mirroring the fields the
 * RuneLite XP Tracker shows per skill: gained, rate, distance to the next
 * level, actions remaining at the last-observed action size, and fractional
 * progress through the current level.
 *
 * <p>Pure DTO — no RuneLite types — computed by the integration layer and
 * carried on the session snapshot for the panel to render.
 */
@lombok.Getter
@lombok.AllArgsConstructor
public final class XpSkillView
{
	private final String skillName;
	private final long gained;
	private final long xpPerHour;
	private final long currentXp;
	/** Real level 1..99, or virtual level 100..126 once 99 is reached (see VirtualLevelTable). */
	private final int currentLevel;
	/** XP still needed to reach the next (real or virtual) level; 0 once maxed at 126. */
	private final long xpLeft;
	/** Actions to the next level at the last action's xp; -1 when unknown or maxed at 126. */
	private final long actionsLeft;
	/** Fraction of the current level completed, 0..1; 1.0 once maxed at 126. */
	private final double progressToNextLevel;

}
