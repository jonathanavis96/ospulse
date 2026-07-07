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
 *       price &lt;= 0 must be treated as unaffordable (Long.MAX_VALUE); and an
 *       unowned item flagged UNTRADEABLE by the client-thread-precomputed
 *       tradeability set must be unaffordable even when RuneLite reports a
 *       positive price for it (ItemMapping "prices" trouver-locked
 *       untradeables — e.g. Dragon defender (l)/Fire cape (l) — at the
 *       Trouver parchment's ~1m GE cost);</li>
 *   <li><b>Gauntlet-only tiers</b> — "Crystal/Corrupted X
 *       (basic|attuned|perfected)" exist only inside The Gauntlet instance
 *       and must never be optimiser candidates, while the suffix-less
 *       main-game crystal armour stays in the pool.</li>
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

	private static final int DRAGON_DEFENDER = 12954;
	private static final int DRAGON_DEFENDER_L = 24143; // trouver-locked
	private static final int FIRE_CAPE = 6570;
	private static final int FIRE_CAPE_L = 24223;       // trouver-locked

	private static final int CRYSTAL_LEGS_PERFECTED = 23894; // Gauntlet-instance-only
	private static final int CRYSTAL_LEGS_PLAIN = 23979;     // real main-game crystal armour — must stay

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

			GearOptimizer.PriceSource resolved =
				section.resolveOptimizerPriceSourceForTest(id -> 0L, java.util.Set.of());
			// Some arbitrary unowned, non-special item — must be unaffordable when
			// the raw resolved price is <= 0 (untradeable, not "free").
			int arbitraryUnownedId = 4587; // Dragon scimitar
			assertEquals(Long.MAX_VALUE, resolved.priceFor(arbitraryUnownedId));
		});
	}

	/**
	 * The general untradeable = unpurchasable rule: an id in the precomputed
	 * untradeable set must resolve to Long.MAX_VALUE even when the raw price
	 * lookup reports a positive cost for it — RuneLite's ItemMapping "prices"
	 * every trouver-locked untradeable (Dragon defender (l), Fire cape (l), …)
	 * at the tradeable Trouver parchment's ~1m, which used to make the
	 * optimiser recommend "buying" items that cannot be bought.
	 */
	@Test
	public void unownedUntradeableItem_resolvesToUnaffordable_evenWithAPositiveRawPrice()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));

			java.util.Set<Integer> untradeable = java.util.Set.of(
				DRAGON_DEFENDER, DRAGON_DEFENDER_L, FIRE_CAPE, FIRE_CAPE_L,
				AVERNIC_DEFENDER, AVERNIC_DEFENDER_L);
			GearOptimizer.PriceSource resolved =
				section.resolveOptimizerPriceSourceForTest(id -> 1_000_000L, untradeable);

			for (int id : untradeable)
			{
				assertEquals("untradeable id " + id + " must be unpurchasable despite a raw ~1m price",
					Long.MAX_VALUE, resolved.priceFor(id));
			}
			assertEquals("a tradeable id must keep its raw price",
				1_000_000L, resolved.priceFor(4587 /* Dragon scimitar */));
		});
	}

	/**
	 * End-to-end (bugs 1 + 3): the player converted their dragon defender into
	 * an Avernic defender (owned, sitting in the bank at 0 GE value). The
	 * optimiser must (a) never suggest buying the untradeable dragon defender
	 * or fire cape — whatever trouver-parchment price RuneLite reports for
	 * them — and (b) use the owned Avernic defender in the shield slot instead.
	 */
	@Test
	public void untradeableDefenderAndFireCape_neverRecommendedAsPurchases_ownedAvernicWins()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(DRAGON_DEFENDER, 1_000_000L);   // the ItemMapping/trouver-parchment leak
			prices.put(DRAGON_DEFENDER_L, 1_000_000L);
			prices.put(FIRE_CAPE, 1_000_000L);
			prices.put(FIRE_CAPE_L, 1_000_000L);
			java.util.Set<Integer> untradeable = java.util.Set.of(
				DRAGON_DEFENDER, DRAGON_DEFENDER_L, FIRE_CAPE, FIRE_CAPE_L);

			java.util.Map<Integer, com.ospulse.model.ItemStack> allHoldings = new java.util.HashMap<>();
			allHoldings.put(AVERNIC_DEFENDER,
				new com.ospulse.model.ItemStack(AVERNIC_DEFENDER, "Avernic defender", 1, 0L));
			WealthSnapshot wealth = WealthSnapshot.builder().allHoldings(allHoldings).build();

			GearSection section = new GearSection(NO_STORE, null, null, null, null,
				fakeResolver(prices, untradeable));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), wealth));
			pickCerberus(section);
			section.setBudgetTextForTest("50m");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			boolean avernicChosen = false;
			for (GearOptimizer.SlotChoice choice : result.loadout())
			{
				assertFalse("the untradeable dragon defender must never be suggested",
					choice.itemId() == DRAGON_DEFENDER || choice.itemId() == DRAGON_DEFENDER_L);
				assertFalse("the untradeable fire cape must never be suggested",
					choice.itemId() == FIRE_CAPE || choice.itemId() == FIRE_CAPE_L);
				if (choice.itemId() == AVERNIC_DEFENDER)
				{
					avernicChosen = true;
					assertTrue("the Avernic defender must be used as OWNED, not bought", choice.owned());
				}
			}
			assertTrue("the owned Avernic defender must be picked for the shield slot", avernicChosen);
			assertEquals("nothing purchasable here — no GP may be spent", 0L, result.totalSpend());
		});
	}

	// ============================================ untradeable craft-ingredient pricing

	private static final int SCORCHING_BOW = 29591;      // untradeable, crafted from a Tormented synapse
	private static final int TORMENTED_SYNAPSE = 29580;  // the tradeable GE ingredient (not equipment)

	/**
	 * The Scorching bow is untradeable (a straight untradeable rule would
	 * make it unbuyable at any budget) and RuneLite's ItemMapping path can
	 * report a bogus proxy price for it — but it is crafted directly from a
	 * tradeable Tormented synapse, so it must price at the SYNAPSE's GE cost:
	 * recommendable to a non-owner, with the spend readout showing the real
	 * acquisition cost.
	 */
	@Test
	public void unownedScorchingBow_pricesAtTheTormentedSynapse_notUnbuyableOrProxyPriced()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));

			java.util.Map<Integer, Long> rawPrices = new java.util.HashMap<>();
			rawPrices.put(TORMENTED_SYNAPSE, 32_000_000L);
			rawPrices.put(SCORCHING_BOW, 1_000_000L); // a bogus ItemMapping-style proxy price that must be ignored

			GearOptimizer.PriceSource resolved = section.resolveOptimizerPriceSourceForTest(
				id -> rawPrices.getOrDefault(id, 0L),
				java.util.Set.of(SCORCHING_BOW)); // flagged untradeable, as the real client precompute would

			assertEquals("the bow must cost exactly its craft ingredient's GE price",
				32_000_000L, resolved.priceFor(SCORCHING_BOW));
		});
	}

	/** With the ingredient itself unpriced (resolver returned nothing for it), the bow must fall back to unaffordable — never free. */
	@Test
	public void unownedScorchingBow_withUnpricedSynapse_staysUnaffordable()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));

			GearOptimizer.PriceSource resolved = section.resolveOptimizerPriceSourceForTest(
				id -> 0L, java.util.Set.of(SCORCHING_BOW));

			assertEquals(Long.MAX_VALUE, resolved.priceFor(SCORCHING_BOW));
		});
	}

	// ======================================================== Gauntlet-only tiers

	@Test
	public void isGauntletOnlyItem_matchesTheThreeTierSuffixes_andNothingElse()
	{
		assertTrue(GearSection.isGauntletOnlyItem("Crystal legs (perfected)"));
		assertTrue(GearSection.isGauntletOnlyItem("Crystal helm (basic)"));
		assertTrue(GearSection.isGauntletOnlyItem("Corrupted bow (attuned)"));
		assertTrue(GearSection.isGauntletOnlyItem("Crystal dagger (perfected)"));
		assertFalse("plain main-game crystal armour must not be excluded",
			GearSection.isGauntletOnlyItem("Crystal legs"));
		assertFalse(GearSection.isGauntletOnlyItem("Crystal body"));
		assertFalse("the suffix must be at the END of the name",
			GearSection.isGauntletOnlyItem("(perfected) oddity"));
		assertFalse(GearSection.isGauntletOnlyItem("Abyssal whip"));
		assertFalse(GearSection.isGauntletOnlyItem(null));
	}

	/**
	 * End-to-end: even priced dirt cheap, a Gauntlet-instance-only tier
	 * ("Crystal legs (perfected)") must never appear in the optimiser result —
	 * it cannot exist outside the Gauntlet, so it can never be equipped
	 * against a normal target.
	 */
	@Test
	public void gauntletOnlyItem_isNeverSuggestedByTheOptimizer()
	{
		onEdt(() ->
		{
			java.util.Map<Integer, Long> prices = new java.util.HashMap<>();
			prices.put(CRYSTAL_LEGS_PERFECTED, 1L); // absurdly cheap — would dominate the legs slot if it were a candidate

			GearSection section = new GearSection(NO_STORE, null, null, null, null, fakeResolver(prices));
			section.apply(snapshotWith(gearFor(loadout(BRONZE_SWORD)), null));
			pickCerberus(section);
			section.setBudgetTextForTest("50m");
			section.runOptimizerSyncForTest();

			GearOptimizer.Result result = section.lastOptimizerResultForTest();
			for (GearOptimizer.SlotChoice choice : result.loadout())
			{
				assertFalse("a Gauntlet-only tier must never appear in the result",
					choice.itemId() == CRYSTAL_LEGS_PERFECTED);
			}
		});
	}

	/**
	 * The per-search exclusion id set (mode-locked + Gauntlet-only) must
	 * contain every suffixed Gauntlet tier but NOT the suffix-less main-game
	 * crystal armour — the ids are distinct in the bundled index (23894
	 * "Crystal legs (perfected)" vs 23979 "Crystal legs"), and only the
	 * former may be swept up.
	 */
	@Test
	public void restrictedItemIds_excludeGauntletTiers_butKeepPlainCrystalArmour()
	{
		java.util.Set<Integer> restricted = GearSection.restrictedItemIds();
		assertTrue("Crystal legs (perfected) must be excluded from the candidate pool",
			restricted.contains(CRYSTAL_LEGS_PERFECTED));
		assertFalse("plain main-game Crystal legs must survive the filter",
			restricted.contains(CRYSTAL_LEGS_PLAIN));
	}

	/** A synchronous fake resolver — calls {@code onResolved} inline with a fixed price map, no threading involved. */
	private static GearSection.OptimizerPriceResolver fakeResolver(java.util.Map<Integer, Long> prices)
	{
		return fakeResolver(prices, java.util.Set.of());
	}

	/** As above, with a fixed untradeable-id set mirroring the client-thread tradeability precompute. */
	private static GearSection.OptimizerPriceResolver fakeResolver(java.util.Map<Integer, Long> prices,
		java.util.Set<Integer> untradeableIds)
	{
		return (ids, onResolved) -> onResolved.accept(new GearSection.PriceLookup(prices, untradeableIds));
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
