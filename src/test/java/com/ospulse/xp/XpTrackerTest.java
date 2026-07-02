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
	public void currentXpReflectsBaselineThenLatestObservation()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		tracker.start(baseline);

		assertEquals(1_000_000L, tracker.currentXp("MAGIC"));
		assertEquals(0L, tracker.currentXp("ATTACK")); // never seen

		tracker.update("MAGIC", 1_050_000L);
		assertEquals(1_050_000L, tracker.currentXp("MAGIC"));
	}

	@Test
	public void lastActionXpIsZeroBeforeFirstGainEvent()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		tracker.start(baseline);

		assertEquals(0L, tracker.lastActionXp("MAGIC"));

		// First observation of an unbaselined skill establishes its baseline
		// and must NOT register as an action.
		tracker.update("WOODCUTTING", 2_500_000L);
		assertEquals(0L, tracker.lastActionXp("WOODCUTTING"));
	}

	@Test
	public void lastActionXpTracksMostRecentPositiveDelta()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		tracker.start(baseline);

		tracker.update("MAGIC", 1_000_042L);
		assertEquals(42L, tracker.lastActionXp("MAGIC"));

		tracker.update("MAGIC", 1_000_142L);
		assertEquals(100L, tracker.lastActionXp("MAGIC"));

		// A no-change or negative reading must not disturb the last action.
		tracker.update("MAGIC", 1_000_142L);
		assertEquals(100L, tracker.lastActionXp("MAGIC"));
		tracker.update("MAGIC", 900_000L);
		assertEquals(100L, tracker.lastActionXp("MAGIC"));
	}

	@Test
	public void restartingClearsLastActionXp()
	{
		Map<String, Long> baseline = new HashMap<>();
		baseline.put("MAGIC", 1_000_000L);
		tracker.start(baseline);
		tracker.update("MAGIC", 1_000_042L);
		assertEquals(42L, tracker.lastActionXp("MAGIC"));

		Map<String, Long> newBaseline = new HashMap<>();
		newBaseline.put("MAGIC", 1_000_042L);
		tracker.start(newBaseline);

		assertEquals(0L, tracker.lastActionXp("MAGIC"));
		assertEquals(1_000_042L, tracker.currentXp("MAGIC"));
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
