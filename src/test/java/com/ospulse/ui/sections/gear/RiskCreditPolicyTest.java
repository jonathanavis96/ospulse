package com.ospulse.ui.sections.gear;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;

import static org.junit.Assert.assertEquals;

/**
 * Each condition in {@link RiskCreditPolicy} exists to rule out a case where
 * withdrawing the credit would make things WORSE, so each gets its own case —
 * a policy tested only on its happy path is a policy whose guards are
 * decorative.
 */
public class RiskCreditPolicyTest
{
	private static final int PLAIN = 21791;
	private static final int VARIANT = 29617;
	private static final long THRESHOLD = 10_000_000L;

	private static Map<Integer, Integer> credit()
	{
		Map<Integer, Integer> credits = new HashMap<>();
		credits.put(PLAIN, VARIANT);
		return credits;
	}

	private static LongUnaryOperator risk(long variantRisk, long plainRisk)
	{
		return id -> id == VARIANT ? variantRisk : plainRisk;
	}

	private static Set<Integer> withdraw(LongUnaryOperator risk, long plainPrice, boolean capActive, long budget)
	{
		return RiskCreditPolicy.withdrawnForSaferPurchase(credit(), risk, id -> plainPrice,
			capActive, THRESHOLD, budget);
	}

	/** The case the finding describes: risky held variant, safe affordable counterpart. */
	@Test
	public void riskyVariant_safeAffordableCounterpart_withdrawsSoItCanBeBought()
	{
		assertEquals(Set.of(PLAIN), withdraw(risk(40_000_000L, 1_000_000L), 2_000_000L, true, 5_000_000L));
	}

	/** No cap means nothing to de-risk for — withdrawing would only make free gear cost money. */
	@Test
	public void capInactive_keepsTheCredit()
	{
		assertEquals(Set.of(), withdraw(risk(40_000_000L, 1_000_000L), 2_000_000L, false, 5_000_000L));
	}

	/** A variant already under the threshold is safe AND free, which beats buying. */
	@Test
	public void variantUnderThreshold_keepsTheCredit()
	{
		assertEquals(Set.of(), withdraw(risk(1_000_000L, 1_000_000L), 2_000_000L, true, 5_000_000L));
	}

	/** Buying an equally risky copy solves nothing and spends the budget for it. */
	@Test
	public void counterpartAlsoOverThreshold_keepsTheCredit()
	{
		assertEquals(Set.of(), withdraw(risk(40_000_000L, 20_000_000L), 2_000_000L, true, 5_000_000L));
	}

	/** An unaffordable purchase is not an alternative — withdrawing would empty the slot for nothing. */
	@Test
	public void counterpartUnaffordable_keepsTheCredit()
	{
		assertEquals(Set.of(), withdraw(risk(40_000_000L, 1_000_000L), 9_000_000L, true, 5_000_000L));
	}

	/** Exactly at the threshold is not "over" it — the cap counts strictly above. */
	@Test
	public void variantExactlyAtThreshold_keepsTheCredit()
	{
		assertEquals(Set.of(), withdraw(risk(THRESHOLD, 1_000_000L), 2_000_000L, true, 5_000_000L));
	}
}
