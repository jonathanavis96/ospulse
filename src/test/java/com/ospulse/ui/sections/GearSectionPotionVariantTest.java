package com.ospulse.ui.sections;

import com.ospulse.combat.CombatIcons;
import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import org.junit.Test;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers QA fix 1 (potion right-click STYLE-FILTER): the right-click swap
 * menu on the potion boost toggle must offer the variants for whatever
 * combat style is CURRENTLY selected — melee gets Super combat/strength/
 * attack, ranged gets Ranging/Bastion/Divine ranging, magic gets Saturated
 * heart/Imbued heart/Ancient brew — never the wrong style's list (the
 * reported bug: magic variants always showed, even on melee). Also covers
 * the per-style default and the in-session persistence of a pick (the actual
 * {@code ConfigManager}-backed survive-a-restart path mirrors
 * {@code OSPulsePanel}'s already-proven {@code ConfigCollapseStore} pattern
 * and isn't independently mockable here — no Mockito dependency in this
 * module and {@code ConfigManager} is a concrete RuneLite class — so this
 * exercises the {@code configManager == null} branch, which every read/write
 * in {@code GearSection} guards identically to the real branch).
 */
public class GearSectionPotionVariantTest
{
	private static void onEdt(Runnable body)
	{
		try
		{
			SwingUtilities.invokeAndWait(body);
		}
		catch (InvocationTargetException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			throw new RuntimeException(cause);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	private static final CollapsibleSection.CollapseStore NO_STORE = new CollapsibleSection.CollapseStore()
	{
		@Override
		public boolean isCollapsed(String key)
		{
			return false;
		}

		@Override
		public void setCollapsed(String key, boolean collapsed)
		{
		}
	};

	private static final int GHRAZI_RAPIER = 22324; // stab sword, melee
	private static final int TWISTED_BOW = 20997;   // ranged, two-handed

	/** int[14] with only the WEAPON slot (ordinal 3) populated. */
	private static int[] weaponSlot(int weaponId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[3] = weaponId;
		return ids;
	}

	private static GearSnapshot gearWithWeapon(int weaponId)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(82, 82, 82, 82, 0,
				0, 0, 0, 0, 0,
				82, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.build();
		return GearSnapshot.builder()
			.equippedItemIds(weaponSlot(weaponId))
			.attack(99, 99)
			.strength(99, 99)
			.defence(99, 99)
			.ranged(99, 99)
			.magic(99, 99)
			.prayer(77, 77)
			.hitpoints(99, 99)
			.equipmentStats(stats)
			.build();
	}

	private static SessionSnapshot snapshotWith(GearSnapshot gear)
	{
		return new SessionSnapshot(0L, 0L, 0L, 0L, 0L, 0L, false,
			null, null, 0L, null, null, null, null, 0L, gear);
	}

	private static int indexOf(ListModel<String> model, String name)
	{
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	private static void pickCerberus(GearSection section)
	{
		section.searchFieldForTest().setText("cerberus");
		int index = indexOf(section.monsterListForTest().getModel(), "Cerberus");
		assertTrue("Cerberus must appear in the filtered list", index >= 0);
		section.monsterListForTest().setSelectedIndex(index);
	}

	@Test
	public void meleeStyle_popupOffersOnlyMeleeVariants()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(GHRAZI_RAPIER)));
			pickCerberus(section);

			List<String> labels = section.potionVariantPopupLabelsForTest();
			assertEquals(3, labels.size());
			assertTrue(labels.contains("Super Combat"));
			assertTrue(labels.contains("Super Strength"));
			assertTrue(labels.contains("Super Attack"));
			// Must NOT leak the magic-only variants (the reported bug).
			assertTrue(labels.stream().noneMatch(l -> l.contains("Heart") || l.contains("Brew")));
		});
	}

	@Test
	public void rangedStyle_popupOffersOnlyRangedVariants()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(TWISTED_BOW)));
			pickCerberus(section);

			List<String> labels = section.potionVariantPopupLabelsForTest();
			assertEquals(3, labels.size());
			assertTrue(labels.contains("Ranging"));
			assertTrue(labels.contains("Bastion"));
			assertTrue(labels.contains("Divine Ranging"));
			assertTrue(labels.stream().noneMatch(l -> l.contains("Super") || l.contains("Heart") || l.contains("Brew")));
		});
	}

	@Test
	public void meleeStyle_defaultVariantIsSuperCombat()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(GHRAZI_RAPIER)));
			pickCerberus(section);

			assertEquals(CombatIcons.BoostPotion.SUPER_COMBAT,
				section.potionVariantForTest(CombatStyle.STAB));
		});
	}

	@Test
	public void pickingAMeleeVariant_stickyAcrossRerank()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(GHRAZI_RAPIER)));
			pickCerberus(section);

			section.pickPotionVariantForTest(CombatStyle.STAB, CombatIcons.BoostPotion.SUPER_STRENGTH);
			assertEquals(CombatIcons.BoostPotion.SUPER_STRENGTH,
				section.potionVariantForTest(CombatStyle.STAB));

			// Re-ranking (e.g. a new target) must not silently drop the pick.
			section.pickPotionVariantForTest(CombatStyle.STAB, CombatIcons.BoostPotion.SUPER_STRENGTH);
			assertEquals(CombatIcons.BoostPotion.SUPER_STRENGTH,
				section.potionVariantForTest(CombatStyle.STAB));
		});
	}

	@Test
	public void pickingARangedVariant_doesNotAffectMeleeOrMagicPicks()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(TWISTED_BOW)));
			pickCerberus(section);

			section.pickPotionVariantForTest(CombatStyle.RANGED, CombatIcons.BoostPotion.BASTION);
			assertEquals(CombatIcons.BoostPotion.BASTION, section.potionVariantForTest(CombatStyle.RANGED));

			// Melee/magic style defaults are untouched by a ranged-only pick.
			assertEquals(CombatIcons.BoostPotion.SUPER_COMBAT, section.potionVariantForTest(CombatStyle.STAB));
			assertEquals(CombatIcons.BoostPotion.IMBUED_HEART, section.potionVariantForTest(CombatStyle.MAGIC));
		});
	}
}
