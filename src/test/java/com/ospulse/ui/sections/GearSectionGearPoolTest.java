package com.ospulse.ui.sections;

import com.ospulse.combat.EquipmentIndexRepository;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.optimizer.GearOptimizer;
import com.ospulse.combat.optimizer.LoadoutOverride;
import com.ospulse.combat.optimizer.WhatIfLoadout;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.wealth.WealthSnapshot;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers three related gear-optimiser candidate/ownership/pricing bugs (QA
 * pass #4):
 * <ul>
 *   <li><b>B</b> — variant-aware ownership: owning an upgraded variant (e.g.
 *       "Masori body (f)") must mark the PLAIN form as owned too, so the
 *       optimiser never suggests buying gear the player effectively already
 *       has;</li>
 *   <li><b>C</b> — Deadman/PvP-only items (names carrying a "(Deadman Mode)",
 *       "(bh)", "(lms)", "(beta)" etc. marker) must never appear as optimiser
 *       candidates;</li>
 *   <li><b>D</b> — untradeable pricing: an unowned item with a resolved GE
 *       price &lt;= 0 must be treated as unaffordable (Long.MAX_VALUE), except
 *       for a small assembled-item -&gt; tradeable-component override (the
 *       Avernic defender -&gt; Avernic defender hilt).
 * </ul>
 */
public class GearSectionGearPoolTest
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

	private static final int BRONZE_SWORD = 1277;

	// -------------------------------------------------------- confirmed ids
	private static final int MASORI_BODY_F = 27238;
	private static final int MASORI_BODY = 27229;
	private static final int MASORI_CHAPS_F = 27241;
	private static final int MASORI_CHAPS = 27232;
	private static final int BERSERKER_RING_I_1 = 11773;
	private static final int BERSERKER_RING_I_2 = 25264;
	private static final int BERSERKER_RING_I_3 = 26770;
	private static final int BERSERKER_RING = 6737;

	private static final int AVERNIC_DEFENDER_L = 24186; // "Avernic defender" (assembled, untradeable)
	private static final int AVERNIC_DEFENDER = 22322;   // "Avernic defender" (assembled, untradeable)
	private static final int AVERNIC_DEFENDER_HILT = net.runelite.api.ItemID.AVERNIC_DEFENDER_HILT;

	private static int[] loadout(int weaponId)
	{
		int[] ids = new int[GearSnapshot.EQUIPMENT_SLOT_COUNT];
		Arrays.fill(ids, -1);
		ids[3] = weaponId;
		return ids;
	}

	private static GearSnapshot gearFor(int[] itemIds)
	{
		EquipmentStats stats = WhatIfLoadout.buildEquipmentStats(itemIds, LoadoutOverride.empty());
		return GearSnapshot.builder()
			.equippedItemIds(itemIds)
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

	private static SessionSnapshot snapshotWith(GearSnapshot gear, WealthSnapshot wealth)
	{
		return new SessionSnapshot(0L, 0L, 0L, 0L, 0L, 0L, false,
			null, null, 0L, wealth, null, null, null, 0L, gear, 0L, null, 0L);
	}

	// ======================================================== B: ownership

	/**
	 * Wearing "Masori body (f)" must add the plain "Masori body" id to the
	 * owned set (price 0) — the general "(f)"/"(i)" suffix-stripping rule,
	 * not a per-item special case.
	 */
	@Test
	public void ownedFortifiedVariant_addsPlainFormToOwnedSet()
	{
		onEdt(() ->
		{
			int[] ids = loadout(BRONZE_SWORD);
			ids[4] = MASORI_BODY_F; // BODY slot ordinal 4
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(ids), null));

			Map<Integer, Long> owned = section.ownedPriceMapForTest();
			assertTrue("the worn fortified item itself must be owned", owned.containsKey(MASORI_BODY_F));
			assertTrue("the plain form must also be marked owned via the (f) suffix rule",
				owned.containsKey(MASORI_BODY));
			assertEquals(0L, (long) owned.get(MASORI_BODY));
		});
	}

	/** Same rule for Masori chaps (f) -> Masori chaps, and for the imbued Berserker ring -> plain Berserker ring, across all 3 (i) id families. */
	@Test
	public void ownedImbuedBerserkerRing_addsPlainRingToOwnedSet_forAllThreeIdFamilies()
	{
		for (int imbuedId : new int[] { BERSERKER_RING_I_1, BERSERKER_RING_I_2, BERSERKER_RING_I_3 })
		{
			onEdt(() ->
			{
				int[] ids = loadout(BRONZE_SWORD);
				ids[12] = imbuedId; // RING slot ordinal 12
				GearSection section = new GearSection(NO_STORE, null, null);
				section.apply(snapshotWith(gearFor(ids), null));

				Map<Integer, Long> owned = section.ownedPriceMapForTest();
				assertTrue("plain Berserker ring must be owned via imbued id " + imbuedId,
					owned.containsKey(BERSERKER_RING));
			});
		}
	}

	@Test
	public void ownedMasoriChapsFortified_addsPlainChapsToOwnedSet()
	{
		onEdt(() ->
		{
			int[] ids = loadout(BRONZE_SWORD);
			ids[7] = MASORI_CHAPS_F; // LEGS slot ordinal 7
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(ids), null));

			Map<Integer, Long> owned = section.ownedPriceMapForTest();
			assertTrue(owned.containsKey(MASORI_CHAPS));
		});
	}

	/**
	 * End-to-end: once the imbued Berserker ring is owned, the optimiser must
	 * never propose BUYING the plain Berserker ring (it's already effectively
	 * owned) — it competes on real DPS instead of being suggested as an
	 * upgrade purchase.
	 */
	@Test
	public void ownedImbuedRing_optimizerNeverSuggestsBuyingThePlainRing()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(BERSERKER_RING, 5_000_000L); // if this were "unowned", it'd look buyable

			int[] ids = loadout(BRONZE_SWORD);
			ids[12] = BERSERKER_RING_I_1;
			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(ids), null));
			pickCerberus(section);
			section.setBudgetTextForTest("0"); // no budget: only owned items can be picked
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			assertEquals("no GP should be spent buying the already-effectively-owned plain ring",
				0L, result.totalSpend());
		});
	}

	// ======================================================== C: mode-locked exclusion

	/**
	 * End-to-end: even with an enormous budget and a resolver that prices the
	 * Deadman-mode Armadyl godsword dirt cheap, the optimiser must never pick
	 * it — it must never even be considered a candidate, regardless of price
	 * or ownership.
	 */
	@Test
	public void deadmanNamedItem_isNeverSuggestedByTheOptimizer()
	{
		onEdt(() ->
		{
			int deadmanAgs = findIdByName("Armadyl godsword (deadman)");
			assertTrue("fixture sanity: deadman AGS must exist in the index", deadmanAgs > 0);

			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(deadmanAgs, 1L); // absurdly cheap — would dominate every real weapon if it were a candidate

			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("50m");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			for (GearOptimizer.SlotChoice choice : result.loadout())
			{
				assertFalse("a Deadman-mode item must never appear in the result",
					choice.itemId() == deadmanAgs);
			}
		});
	}

	/** Same behaviour for a bounty-hunter-only ("(bh)") item. */
	@Test
	public void bountyHunterNamedItem_isNeverSuggestedByTheOptimizer()
	{
		onEdt(() ->
		{
			int bhDagger = findIdByName("Abyssal dagger (bh)");
			assertTrue("fixture sanity: (bh) item must exist in the index", bhDagger > 0);

			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(bhDagger, 1L);

			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("50m");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			for (GearOptimizer.SlotChoice choice : result.loadout())
			{
				assertFalse("a bounty-hunter-only item must never appear in the result",
					choice.itemId() == bhDagger);
			}
		});
	}

	@Test
	public void isModeLockedItem_matchesEveryDocumentedMarker()
	{
		assertTrue(GearSection.isModeLockedItem("Zuriel's robe top (Deadman Mode)"));
		assertTrue(GearSection.isModeLockedItem("Armadyl godsword (deadman)"));
		assertTrue(GearSection.isModeLockedItem("Abyssal dagger (bh)"));
		assertTrue(GearSection.isModeLockedItem("Some item (lms)"));
		assertTrue(GearSection.isModeLockedItem("Some item (Last Man Standing)"));
		assertTrue(GearSection.isModeLockedItem("Black d'hide chaps (beta)"));
		assertFalse("a normal item must not be excluded", GearSection.isModeLockedItem("Abyssal whip"));
		assertFalse("case must not matter for a real item name", GearSection.isModeLockedItem("Rune platebody"));
	}

	private static int findIdByName(String name)
	{
		EquipmentIndexRepository index = EquipmentIndexRepository.getInstance();
		for (Integer id : index.allItemIds())
		{
			EquipmentIndexRepository.Entry e = index.entryFor(id);
			if (e != null && e.name().equals(name))
			{
				return id;
			}
		}
		return -1;
	}

	// ======================================================== D: untradeable pricing

	@Test
	public void unownedZeroPriceItem_resolvesToUnaffordable()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));

			GearOptimizer.PriceSource resolved = section.resolveOptimizerPriceSourceForTest(id -> 0L);
			// Some arbitrary unowned, non-special item — must be unaffordable when
			// the raw resolved price is <= 0 (untradeable, not "free").
			int arbitraryUnownedId = 4587; // Dragon scimitar
			assertEquals(Long.MAX_VALUE, resolved.priceFor(arbitraryUnownedId));
		});
	}

	@Test
	public void avernicDefenderAssembledIds_priceFromTheHiltComponent()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));

			long hiltPrice = 31_800_000L;
			GearOptimizer.PriceSource resolved = section.resolveOptimizerPriceSourceForTest(id ->
			{
				if (id == AVERNIC_DEFENDER_HILT)
				{
					return hiltPrice;
				}
				return 0L; // the assembled/untradeable ids themselves resolve to 0 via the real GE lookup
			});

			assertEquals("the assembled Avernic defender must price from the hilt component",
				hiltPrice, resolved.priceFor(AVERNIC_DEFENDER_L));
			assertEquals("both assembled ids must map to the same hilt component",
				hiltPrice, resolved.priceFor(AVERNIC_DEFENDER));
		});
	}

	/** A synchronous fake resolver — calls {@code onResolved} inline with a fixed price map, no threading involved. */
	private static GearSection.OptimizerPriceResolver fakeResolver(java.util.Map<Integer, Long> prices)
	{
		return (ids, onResolved) -> onResolved.accept(prices);
	}

	private static void pickCerberus(GearSection section)
	{
		section.searchFieldForTest().setText("cerberus");
		javax.swing.ListModel<String> model = section.monsterListForTest().getModel();
		int index = -1;
		for (int i = 0; i < model.getSize(); i++)
		{
			if (model.getElementAt(i).equals("Cerberus"))
			{
				index = i;
				break;
			}
		}
		assertTrue("Cerberus must appear in the filtered list", index >= 0);
		section.monsterListForTest().setSelectedIndex(index);
	}
}
