package com.ospulse.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Obligator's Thrust/Smash split and charged passive. See {@link ObligatorThrust}. */
public class ObligatorThrustTest
{
	static { BundledGson.set(new com.google.gson.Gson()); }

	private static final double DELTA = 1e-9;
	private static final int CHARGED = 34715;
	private static final int UNCHARGED = 34717;

	private static Monster target(int size, int dstab, int dslash, int dcrush)
	{
		return Monster.builder().name("Dummy").hitpoints(500).defenceLevel(1).size(size)
			.defenceBonuses(dstab, dslash, dcrush, 0, 0).build();
	}

	private static EquipmentStats gear()
	{
		return EquipmentStats.builder()
			.add(22, -8, 120, 0, 0, 0, 16, 0, 3, 8, 40, 0, 0.0, 3)
			.weaponSpeedTicks(5)
			.build();
	}

	private static PlayerCombat player(Stance stance)
	{
		return PlayerCombat.builder()
			.attack(99, 99).strength(99, 99).defence(99, 99).ranged(99, 99)
			.magic(99, 99).prayer(99, 99).hitpoints(99, 99)
			.assumeBestPotion(false).assumeBestPrayer(false).onSlayerTask(false)
			.stance(stance)
			.build();
	}

	private static DpsResult dps(int weaponId, Stance stance, Monster target)
	{
		return DpsCalculator.compute(gear(), player(stance), CombatStyle.CRUSH, target, 0, weaponId);
	}

	@Test
	public void passiveNeedsTheChargedWeaponAndAThrustStance()
	{
		assertTrue(ObligatorThrust.appliesTo(CHARGED, Stance.AGGRESSIVE));
		assertTrue(ObligatorThrust.appliesTo(CHARGED, Stance.ACCURATE));
		assertFalse(ObligatorThrust.appliesTo(CHARGED, Stance.SMASH));
		assertFalse(ObligatorThrust.appliesTo(UNCHARGED, Stance.AGGRESSIVE));
	}

	@Test
	public void sizeBonusFollowsTheWiki()
	{
		assertEquals(50, ObligatorThrust.boostedMaxHit(50, 1));
		assertEquals(60, ObligatorThrust.boostedMaxHit(50, 2));
		assertEquals(70, ObligatorThrust.boostedMaxHit(50, 3));
		assertEquals(70, ObligatorThrust.boostedMaxHit(50, 5));
	}

	/** The wiki's own examples: Phosani's Nightmare gets four rolls, General Graardor two. */
	@Test
	public void rollCountFollowsTheTargetsDefences()
	{
		assertEquals(4, ObligatorThrust.damageRolls(target(1, 150, 150, 40)));
		assertEquals(3, ObligatorThrust.damageRolls(target(1, 150, 10, 40)));
		assertEquals(2, ObligatorThrust.damageRolls(target(1, 90, 90, 90)));
	}

	@Test
	public void bestOfTwoFairCoins()
	{
		double[] best = ObligatorThrust.bestOf(new double[]{0.5, 0.5}, 2);
		assertEquals(0.25, best[0], DELTA);
		assertEquals(0.75, best[1], DELTA);
	}

	@Test
	public void thrustAverageIsTheBestOfTwoRolls()
	{
		Monster graardorShaped = target(1, 0, 0, 0);
		DpsResult thrust = dps(CHARGED, Stance.AGGRESSIVE, graardorShaped);
		DpsResult uncharged = dps(UNCHARGED, Stance.AGGRESSIVE, graardorShaped);

		assertEquals("no size bonus on a 1x1 target", uncharged.maxHit(), thrust.maxHit());
		int m = thrust.maxHit();
		double[] oneRoll = DamageDistribution.perHitDistribution(1.0, m);
		double expected = 0.0;
		double[] best = ObligatorThrust.bestOf(oneRoll, 2);
		for (int d = 0; d <= m; d++)
		{
			expected += d * best[d];
		}
		assertEquals(expected * thrust.accuracy(), thrust.avgHit(), DELTA);
		assertTrue("best of two must beat one roll", thrust.dps() > uncharged.dps());
	}

	@Test
	public void smashAttacksEveryThreeTicksWithAggressiveStrengthAndNoPassive()
	{
		Monster large = target(3, 150, 150, 0);
		DpsResult smash = dps(CHARGED, Stance.SMASH, large);
		DpsResult uncharged = dps(UNCHARGED, Stance.AGGRESSIVE, large);

		assertEquals("same stance bonus as an aggressive hit, no size bonus", uncharged.maxHit(), smash.maxHit());
		assertEquals("3 ticks vs 5 is the only difference", uncharged.dps() * 5.0 / 3.0, smash.dps(), DELTA);
	}

	@Test
	public void bestOfStaysAValidDistributionUnderACap()
	{
		double[] outcome = ObligatorThrust.bestOf(DamageDistribution.cappedPerHitDistribution(1.0, 40, 10), 4);
		double total = 0.0;
		for (double p : outcome)
		{
			assertTrue(p >= -DELTA);
			total += p;
		}
		assertEquals(1.0, total, DELTA);
		assertEquals(11, outcome.length);
	}
}
