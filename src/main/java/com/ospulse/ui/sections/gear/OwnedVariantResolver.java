package com.ospulse.ui.sections.gear;

import com.ospulse.combat.EquipmentIndexRepository;

import java.util.Map;

/**
 * Fortified/imbued cosmetic-or-charged variant suffixes (e.g. Masori
 * armour's " (f)" fortify, a ring's " (i)" imbue) and the two-way mapping
 * between a variant's name and its plain base form that {@link
 * com.ospulse.ui.sections.GearSection}'s ownership/recommendation logic
 * needs:
 * <ul>
 *   <li><b>ownership</b> — owning the VARIANT also counts as owning the
 *   plain base form, so the optimiser doesn't suggest "upgrading" to an item
 *   the player already effectively has (see {@link #plainFormId}, used by
 *   {@code GearSection#addVariantPlainForm});</li>
 *   <li><b>display</b> — when a recommendation resolves to the plain base
 *   form because that's the id the ownership check above marked owned, the
 *   UI should still show the actual OWNED variant's name/icon, not the
 *   plain one — {@link #preferOwnedVariant} is the reverse lookup for
 *   that.</li>
 * </ul>
 */
public final class OwnedVariantResolver
{
	/** Space-prefixed variant suffixes, as they appear at the end of an {@link EquipmentIndexRepository.Entry#name()}. */
	public static final String[] SUFFIXES = { " (f)", " (i)" };

	private OwnedVariantResolver()
	{
	}

	/**
	 * The plain (suffix-stripped) form's item id for {@code variantItemId}, or
	 * {@code null} if the item isn't indexed, its name doesn't end in a known
	 * variant suffix, or the plain form itself isn't indexed.
	 */
	public static Integer plainFormId(EquipmentIndexRepository index, int variantItemId)
	{
		EquipmentIndexRepository.Entry entry = index.entryFor(variantItemId);
		if (entry == null)
		{
			return null;
		}
		String name = entry.name();
		for (String suffix : SUFFIXES)
		{
			if (name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length()))
			{
				return index.idForName(name.substring(0, name.length() - suffix.length()));
			}
		}
		return null;
	}

	/**
	 * The item id that should actually be DISPLAYED for a recommendation that
	 * resolved to {@code itemId}: if {@code itemId} is a plain base form and
	 * {@code ownedIds} genuinely contains one of its variants' ids, returns
	 * the owned variant's id instead — so a recommendation that's really "you
	 * already own Masori mask (f)" shows that name/icon, not the plain
	 * "Masori mask" the optimiser matched candidates against. Falls back to
	 * {@code itemId} unchanged when no owned variant applies (including when
	 * {@code itemId} is already a variant, or owns nothing extra).
	 */
	public static int preferOwnedVariant(EquipmentIndexRepository index, int itemId, Map<Integer, Long> ownedIds)
	{
		if (ownedIds == null || ownedIds.isEmpty())
		{
			return itemId;
		}
		EquipmentIndexRepository.Entry entry = index.entryFor(itemId);
		if (entry == null)
		{
			return itemId;
		}
		String name = entry.name();
		for (String suffix : SUFFIXES)
		{
			Integer variantId = index.idForName(name + suffix);
			if (variantId != null && ownedIds.containsKey(variantId))
			{
				return variantId;
			}
		}
		return itemId;
	}
}
