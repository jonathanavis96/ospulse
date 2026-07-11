package com.ospulse.ui.sections;

import com.ospulse.combat.optimizer.LoadoutOverride;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import org.junit.Test;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Review finding 4 (minor): {@code updateGearGrid()} evaluates weapon-slot
 * validity ({@link GearSection#isSlotInvalidForTarget}) using {@code
 * selectedStyle}, but only {@code rankAndRender()} re-selects that style for
 * a NEWLY equipped weapon. Flows that called {@code updateGearGrid()} BEFORE
 * {@code rankAndRender()} (e.g. {@code applyOverride()}, {@code apply()})
 * therefore red-crossed a genuinely valid new weapon using the PREVIOUS
 * weapon's stale style — most visibly when the previous weapon left {@code
 * selectedStyle} {@code null} (every style gated out, e.g. an Abyssal whip
 * vs Kurask), since the invalid check short-circuits on {@code selectedStyle
 * == null} regardless of the newly shown weapon's own id.
 */
public class GearSectionSlotRefreshOrderTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }

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

	// Kurask's WEAPON_GATE (monster_combat_requirements.json): only leaf-bladed
	// weapons / broad ammo / Magic Dart can harm it.
	private static final int ABYSSAL_WHIP = 4151;        // ordinary slash — gated OUT entirely (every style fails)
	private static final int LEAF_BLADED_SPEAR = 4158;   // "Leaf-bladed spear" — explicitly allowed by Kurask's weapon gate

	private static int[] weaponSlot(int weaponId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[WhatIfLoadout.WEAPON_SLOT] = weaponId;
		return ids;
	}

	private static GearSnapshot gearWithWeapon(int weaponId)
	{
		com.ospulse.combat.EquipmentStats stats =
			WhatIfLoadout.buildEquipmentStats(weaponSlot(weaponId), LoadoutOverride.empty());
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

	private static int indexOfContaining(ListModel<String> model, String name)
	{
		String needle = name.toLowerCase(java.util.Locale.ROOT);
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).toLowerCase(java.util.Locale.ROOT).contains(needle))
			{
				return i;
			}
		}
		return -1;
	}

	private static void pickMonster(GearSection section, String name)
	{
		section.searchFieldForTest().setText(name);
		int index = indexOfContaining(section.monsterListForTest().getModel(), name);
		assertTrue(name + " must appear in the filtered list", index >= 0);
		section.monsterListForTest().setSelectedIndex(index);
		section.updateGearGridForTest();
	}

	/**
	 * The core finding 4 regression: swapping FROM a weapon that gates out
	 * every style (leaving {@code selectedStyle == null}) TO a genuinely
	 * valid weapon must clear the red-cross IMMEDIATELY, in the same {@code
	 * applyOverride()} call — not merely on some later, unrelated refresh.
	 */
	@Test
	public void swappingToAValidWeapon_clearsTheRedCrossImmediately()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(ABYSSAL_WHIP)));
			pickMonster(section, "Kurask");

			assertEquals("the whip gates out every style vs Kurask, so the weapon slot must start red-crossed",
				GearSection.INVALID_BORDER, section.slotBorderForTest(WhatIfLoadout.WEAPON_SLOT));

			// The what-if picker's real action — mirrors clicking the leaf-bladed
			// spear in the item search grid. Now overridden (a what-if pick), the
			// slot's border is either a compound of INVALID_BORDER + the
			// recommendation border (still crossed out) or the plain
			// recommendation border alone (not owned here, so OVERRIDE_BORDER) —
			// the finding 4 bug showed the former; the fix must show the latter.
			section.applyOverrideForTest(WhatIfLoadout.WEAPON_SLOT, LEAF_BLADED_SPEAR);

			assertEquals("a newly-equipped, genuinely valid weapon must not stay crossed out "
					+ "against the PREVIOUS weapon's stale (null) style — expected the plain "
					+ "not-owned override border, not one compounded with the invalid red-cross",
				GearSection.OVERRIDE_BORDER, section.slotBorderForTest(WhatIfLoadout.WEAPON_SLOT));
		});
	}

	/** Symmetric case: a live gear change delivered via a fresh {@code apply()} snapshot must refresh the same way. */
	@Test
	public void freshApplySnapshot_reevaluatesTheNewWeaponsStyleBeforeRegridding()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(ABYSSAL_WHIP)));
			pickMonster(section, "Kurask");
			assertEquals(GearSection.INVALID_BORDER, section.slotBorderForTest(WhatIfLoadout.WEAPON_SLOT));

			// A new snapshot arrives (e.g. the player re-equipped in-game) while
			// Kurask is still the selected target.
			section.apply(snapshotWith(gearWithWeapon(LEAF_BLADED_SPEAR)));

			assertNull("a live weapon change to a valid weapon must not stay crossed out",
				section.slotBorderForTest(WhatIfLoadout.WEAPON_SLOT));
		});
	}
}
