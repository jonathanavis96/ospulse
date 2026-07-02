package com.ospulse.xp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure tracker of per-skill XP gained since session start.
 *
 * <p>If a skill wasn't present in the baseline supplied to {@link #start},
 * its baseline is taken to be the first value observed via {@link #update}
 * for that skill, so no negative or artificially huge delta is reported.
 *
 * <p>Besides the session-total gain, the tracker also retains each skill's
 * absolute current XP and the size of the most recent single gain event
 * (the "last action" XP — the positive delta between two consecutive
 * {@link #update} readings), which drives the actions-left estimate.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class XpTracker
{
	private final Map<String, Long> baseline = new LinkedHashMap<>();
	private final Map<String, Long> current = new LinkedHashMap<>();
	private final Map<String, Long> lastAction = new LinkedHashMap<>();

	public void start(Map<String, Long> baselineXpBySkill)
	{
		baseline.clear();
		current.clear();
		lastAction.clear();
		if (baselineXpBySkill != null)
		{
			baseline.putAll(baselineXpBySkill);
			current.putAll(baselineXpBySkill);
		}
	}

	public void update(String skill, long currentXp)
	{
		baseline.computeIfAbsent(skill, k -> currentXp);
		Long previous = current.get(skill);
		if (previous != null && currentXp > previous)
		{
			lastAction.put(skill, currentXp - previous);
		}
		current.put(skill, currentXp);
	}

	/**
	 * The skill's absolute current XP as last observed (or supplied to
	 * {@link #start}); 0 if the skill has never been seen.
	 */
	public long currentXp(String skill)
	{
		return current.getOrDefault(skill, 0L);
	}

	/**
	 * XP of the most recent single gain event for the skill — the positive
	 * delta between the two latest observed readings. 0 until a gain has
	 * been observed this session.
	 */
	public long lastActionXp(String skill)
	{
		return lastAction.getOrDefault(skill, 0L);
	}

	/**
	 * Skill -> xp gained since baseline, for skills with a positive gain
	 * only.
	 */
	public Map<String, Long> gained()
	{
		Map<String, Long> result = new LinkedHashMap<>();
		for (Map.Entry<String, Long> entry : current.entrySet())
		{
			String skill = entry.getKey();
			long currentXp = entry.getValue();
			long baselineXp = baseline.getOrDefault(skill, currentXp);
			long delta = currentXp - baselineXp;
			if (delta > 0)
			{
				result.put(skill, delta);
			}
		}
		return result;
	}

	/**
	 * Sum of all positive per-skill gains.
	 */
	public long totalGained()
	{
		long total = 0L;
		for (long value : gained().values())
		{
			total += value;
		}
		return total;
	}
}
