package com.ospulse.ui.sections.gear;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;

/**
 * When a variant's ownership credit should be WITHDRAWN so the ordinary
 * counterpart can be bought instead.
 *
 * <p>One item id cannot carry two different (ownership, risk) pairs. The
 * credit marks the plain id owned and free, and {@code VariantCreditSources}
 * makes it inherit the held variant's risk — correct, because that variant is
 * what the player would actually wear and lose. But it collapses a real
 * choice: with the held variant above the expensive-item threshold and the
 * ordinary counterpart below it <b>and affordable</b>, the optimiser can no
 * longer represent "buy a separate ordinary copy". The single candidate is
 * owned-but-over-cap, so the cap can only drop the slot or take worse gear,
 * even though the budget would cover the safe alternative.
 *
 * <p>Rather than teach the optimiser to hold two candidates per slot — its
 * per-slot candidates are bare item ids — the credit is withdrawn in exactly
 * the case where it does more harm than good. The plain id then behaves as an
 * ordinary purchase: priced at its own gp, risked at its own value, and shown
 * as the not-owned suggestion it is. When the condition does not hold nothing
 * changes, so the credit keeps working for every player it helps.
 *
 * <p>All four conditions must hold, and each rules out a case where
 * withdrawing would make things worse:
 * <ul>
 *   <li><b>the cap is active</b> — with no cap there is nothing to de-risk
 *   for, and withdrawing would only make free gear cost money;</li>
 *   <li><b>the held variant is over the threshold</b> — otherwise the credit
 *   is already safe and free, which is strictly better than buying;</li>
 *   <li><b>the counterpart is at or under the threshold</b> — buying an
 *   equally risky copy solves nothing and just spends the budget;</li>
 *   <li><b>the counterpart is affordable</b> — a purchase the budget cannot
 *   cover is not an alternative at all, and withdrawing would leave the slot
 *   empty for no gain.</li>
 * </ul>
 */
public final class RiskCreditPolicy
{
	private RiskCreditPolicy()
	{
	}

	/**
	 * The credited plain ids whose credit should be withdrawn so they can be
	 * purchased instead — see the class javadoc for why each condition is
	 * required.
	 *
	 * @param creditSources credited plain id -&gt; held variant id, from
	 *                      {@link VariantCreditSources}
	 * @param riskValueOf   an id's gp risk value (the variant's own value for
	 *                      a variant id; the counterpart's own value for the
	 *                      plain id — NOT the credit-remapped lookup, which
	 *                      would report the variant's value for both and make
	 *                      the comparison vacuous)
	 * @param priceOf       an id's purchase price
	 * @param capActive     whether the expensive-item cap is switched on
	 * @param threshold     the cap's gp threshold
	 * @param budget        the search's gp budget
	 */
	public static Set<Integer> withdrawnForSaferPurchase(Map<Integer, Integer> creditSources,
		LongUnaryOperator riskValueOf, LongUnaryOperator priceOf, boolean capActive, long threshold, long budget)
	{
		Set<Integer> withdrawn = new HashSet<>();
		if (!capActive || creditSources.isEmpty())
		{
			return withdrawn;
		}
		for (Map.Entry<Integer, Integer> credit : creditSources.entrySet())
		{
			int plainId = credit.getKey();
			int variantId = credit.getValue();
			if (riskValueOf.applyAsLong(variantId) > threshold
				&& riskValueOf.applyAsLong(plainId) <= threshold
				&& priceOf.applyAsLong(plainId) <= budget)
			{
				withdrawn.add(plainId);
			}
		}
		return withdrawn;
	}
}
