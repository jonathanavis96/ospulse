package com.phatwang.xp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure tracker of per-skill XP gained since session start.
 *
 * <p>If a skill wasn't present in the baseline supplied to {@link #start},
 * its baseline is taken to be the first value observed via {@link #update}
 * for that skill, so no negative or artificially huge delta is reported.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class XpTracker
{
	private final Map<String, Long> baseline = new LinkedHashMap<>();
	private final Map<String, Long> current = new LinkedHashMap<>();

	public void start(Map<String, Long> baselineXpBySkill)
	{
		baseline.clear();
		current.clear();
		if (baselineXpBySkill != null)
		{
			baseline.putAll(baselineXpBySkill);
			current.putAll(baselineXpBySkill);
		}
	}

	public void update(String skill, long currentXp)
	{
		baseline.computeIfAbsent(skill, k -> currentXp);
		current.put(skill, currentXp);
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
