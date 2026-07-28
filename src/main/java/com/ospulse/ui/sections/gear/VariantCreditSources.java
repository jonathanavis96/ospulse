package com.ospulse.ui.sections.gear;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.model.ItemStack;
import com.ospulse.session.GearSnapshot;
import com.ospulse.wealth.WealthSnapshot;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * For each plain item id the player owns only by CREDIT, the id of the held
 * variant that credit came from — the inverse of {@code
 * GearSection.addVariantPlainForm}'s synthetic ownership entry.
 *
 * <p>The credit is deliberately one-way in the ownership map: owning "Imbued
 * saradomin cape (deadman)" marks the plain cape owned at price 0, and the
 * optimiser needs nothing more than that to treat it as free. But two things
 * downstream need to know <b>which physical item</b> the credit stands for:
 *
 * <ul>
 *   <li>the display, which must name the item the player actually holds (see
 *   {@link HeldItemIds} and {@code GearSection#resolvedItemId});</li>
 *   <li>the expensive-item risk cap, which must price what the player is
 *   actually told to <b>risk</b>. A tradeable cosmetic — Deadman AGS, Dark
 *   bow, Bowfa — can be worth a different amount from its ordinary
 *   counterpart, and the cap looking up the credited plain id counts the
 *   wrong item's gp. With a threshold between the two values, a
 *   wilderness/PvP search silently violates the user's own expensive-item
 *   limit.</li>
 * </ul>
 *
 * <p>A plain id the player <b>physically holds</b> is never listed here: it
 * prices itself, and no substitution happens for it anyway.
 *
 * <p><b>Exclusions are honoured for the same reason the display honours
 * them.</b> {@code preferOwnedVariant} never returns a variant the player
 * excluded from suggestions, so an excluded variant's credit leaves the plain
 * id on screen and in the preview — and the cap must then price the plain
 * item, the one that will actually be equipped. A credit map that ignored
 * exclusions would charge for an item the player has told the panel not to
 * use, which can reject a valid setup or wave through an over-threshold one
 * depending on which side of the threshold the two values fall.
 */
public final class VariantCreditSources
{
	private VariantCreditSources()
	{
	}

	/**
	 * Credited plain id -&gt; the held variant id backing it. Mirrors {@code
	 * GearSection.ownedPriceMap()}'s two sources and its {@code
	 * getAllHoldings()}-then-{@code getTopHoldings()} fallback, so the credit
	 * map can never disagree with the ownership map about what was credited.
	 *
	 * <p>When two held variants credit the same plain id the first wins,
	 * matching the ownership map's own {@code putIfAbsent} semantics.
	 */
	public static Map<Integer, Integer> from(WealthSnapshot wealth, GearSnapshot gear,
		EquipmentIndexRepository index, Set<Integer> excludedItemIds)
	{
		Set<Integer> held = HeldItemIds.from(wealth, gear, index);
		Map<Integer, Integer> credits = new HashMap<>();
		if (wealth != null)
		{
			Collection<ItemStack> stacks = !wealth.getAllHoldings().isEmpty()
				? wealth.getAllHoldings().values()
				: wealth.getTopHoldings();
			for (ItemStack stack : stacks == null ? Collections.<ItemStack>emptyList() : stacks)
			{
				if (stack != null)
				{
					record(credits, held, index, excludedItemIds, stack.getId());
				}
			}
		}
		if (gear != null)
		{
			for (int id : gear.equippedItemIds())
			{
				record(credits, held, index, excludedItemIds, id);
			}
		}
		return credits;
	}

	private static void record(Map<Integer, Integer> credits, Set<Integer> held,
		EquipmentIndexRepository index, Set<Integer> excludedItemIds, int heldItemId)
	{
		if (heldItemId <= 0 || index.entryFor(heldItemId) == null
			|| (excludedItemIds != null && excludedItemIds.contains(heldItemId)))
		{
			return;
		}
		Integer plainId = OwnedVariantResolver.plainFormId(index, heldItemId);
		if (plainId != null && !held.contains(plainId))
		{
			credits.putIfAbsent(plainId, heldItemId);
		}
	}
}
