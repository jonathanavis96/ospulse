package com.ospulse.xp;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XpTrackerTest
{
	private XpTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new XpTracker();
	}

	@Test
	public void gainedIsZeroImmediatelyAfterStart()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		baseline.put("ATTACK", 500_000L);
		tracker.start(baseline);

		assertTrue(tracker.gained().isEmpty());
		assertEquals(0L, tracker.totalGained());
	}

	@Test
	public void updateComputesGainAgainstBaseline()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		tracker.start(baseline);

		tracker.update("MAGIC", 1_050_000L);

		assertEquals(50_000L, (long) tracker.gained().get("MAGIC"));
		assertEquals(50_000L, tracker.totalGained());
	}

	@Test
	public void skillNotInBaselineUsesFirstSeenValueAsBaseline()
	{
		tracker.start(new HashMap<>());

		// First observation of WOODCUTTING should NOT itself count as gain.
		tracker.update("WOODCUTTING", 2_500_000L);
		assertTrue(tracker.gained().isEmpty());

		tracker.update("WOODCUTTING", 2_500_500L);
		assertEquals(500L, (long) tracker.gained().get("WOODCUTTING"));
	}

	@Test
	public void multipleSkillsTrackedIndependently()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		baseline.put("ATTACK", 500_000L);
		tracker.start(baseline);

		tracker.update("MAGIC", 1_010_000L);
		tracker.update("ATTACK", 500_000L); // no change

		Map<String, Long> gained = tracker.gained();
		assertEquals(1, gained.size());
		assertEquals(10_000L, (long) gained.get("MAGIC"));
		assertFalse(gained.containsKey("ATTACK"));
		assertEquals(10_000L, tracker.totalGained());
	}

	@Test
	public void restartingResetsBaselineAndCurrent()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		tracker.start(baseline);
		tracker.update("MAGIC", 1_100_000L);
		assertEquals(100_000L, tracker.totalGained());

		Map<String, Long> newBaseline = new HashMap<>();
		newBaseline.put("MAGIC", 1_100_000L);
		tracker.start(newBaseline);

		assertTrue(tracker.gained().isEmpty());
		assertEquals(0L, tracker.totalGained());
	}

	@Test
	public void neverGoesNegativeForXpDrops()
	{
		// XP should never drop in OSRS, but guard against it anyway: no
		// negative entries should ever appear in gained().
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		tracker.start(baseline);

		tracker.update("MAGIC", 900_000L);

		assertFalse(tracker.gained().containsKey("MAGIC"));
		assertEquals(0L, tracker.totalGained());
	}
}
