package com.ospulse.ui.sections.gear;

import com.ospulse.model.ItemStack;
import com.ospulse.session.GearSnapshot;
import com.ospulse.wealth.WealthSnapshot;
import com.ospulse.combat.EquipmentIndexRepository;
import java.util.*;

/**
 * The indexed item ids the player PHYSICALLY holds — banked/valued holdings
 * plus worn gear — with NO synthetic variant credit mixed in.
 *
 * <p>This exists because {@code GearSection.ownedPriceMap()} deliberately
 * blends two different facts into one map: ids the player really has, and
 * ids they are merely CREDITED with because they hold a variant of them
 * ({@code addVariantPlainForm} puts the suffix-stripped plain form in at
 * price 0). That blend is exactly right for the optimiser, which only needs
 * to know "can this be equipped for free". It is wrong for the DISPLAY
 * layer, which has to answer a different question: <i>which item does the
 * player actually go and take out of the bank?</i> A credited plain id is
 * not in the bank, so nothing can highlight it and the swap row must not
 * name it as the thing to equip.
 *
 * <p>Deliberately a separate, pure class rather than another private method
 * on {@code GearSection}: that file is already ~6.6k lines, and this is
 * unit-testable without a Swing component.
 */
public final class HeldItemIds
{
	private HeldItemIds()
	{
	}

	/**
	 * Every indexed id present in {@code wealth}'s holdings or worn in
	 * {@code gear}. Mirrors {@code GearSection.ownedPriceMap()}'s two
	 * sources and its {@code getAllHoldings()}-then-{@code getTopHoldings()}
	 * fallback exactly, so "held" can never disagree with "owned" about
	 * where an id came from — it only drops the variant-credit additions.
	 * Ids absent from {@link EquipmentIndexRepository} are skipped for the
	 * same reason the price map skips them: they are not equipment the
	 * optimiser can reason about.
	 */
	public static Set<Integer> from(WealthSnapshot wealth, GearSnapshot gear, EquipmentIndexRepository index)
	{
		Set<Integer> held = allFrom(wealth, gear);
		held.removeIf(id -> index.entryFor(id) == null);
		return held;
	}

	/**
	 * Every id present in {@code wealth}'s holdings or worn in {@code gear},
	 * indexed or not. Mechanic items with no combat stats (Earmuffs, Facemask,
	 * Nose peg, Reinforced goggles, Lit bug lantern) have no index row, so
	 * this is the only way to tell that the player has one.
	 */
	public static Set<Integer> allFrom(WealthSnapshot wealth, GearSnapshot gear)
	{
		Set<Integer> held = new HashSet<>();
		if (wealth != null)
		{
			Collection<ItemStack> stacks = !wealth.getAllHoldings().isEmpty()
				? wealth.getAllHoldings().values()
				: wealth.getTopHoldings();
			for (ItemStack stack : stacks == null ? Collections.<ItemStack>emptyList() : stacks)
			{
				if (stack != null)
				{
					held.add(stack.getId());
				}
			}
		}
		if (gear != null)
		{
			for (int id : gear.equippedItemIds())
			{
				if (id > 0)
				{
					held.add(id);
				}
			}
		}
		return held;
	}
}
