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

	public XpSkillView(
		String skillName,
		long gained,
		long xpPerHour,
		long currentXp,
		int currentLevel,
		long xpLeft,
		long actionsLeft,
		double progressToNextLevel)
	{
		this.skillName = skillName;
		this.gained = gained;
		this.xpPerHour = xpPerHour;
		this.currentXp = currentXp;
		this.currentLevel = currentLevel;
		this.xpLeft = xpLeft;
		this.actionsLeft = actionsLeft;
		this.progressToNextLevel = progressToNextLevel;
	}

	public String getSkillName()
	{
		return skillName;
	}

	public long getGained()
	{
		return gained;
	}

	public long getXpPerHour()
	{
		return xpPerHour;
	}

	public long getCurrentXp()
	{
		return currentXp;
	}

	public int getCurrentLevel()
	{
		return currentLevel;
	}

	public long getXpLeft()
	{
		return xpLeft;
	}

	public long getActionsLeft()
	{
		return actionsLeft;
	}

	public double getProgressToNextLevel()
	{
		return progressToNextLevel;
	}
}
