package com.ospulse.ui.sections;

import com.ospulse.combat.CombatStyle;
import com.ospulse.combat.DpsCalculator;
import com.ospulse.combat.DpsResult;
import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.MonsterRepository;
import com.ospulse.combat.PlayerCombat;
import com.ospulse.combat.WeaponStyle;
import com.ospulse.session.GearMapper;
import com.ospulse.session.GearSnapshot;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import org.junit.Test;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the ranked attack-style picker: the equipped weapon's real styles are
 * listed, computed and sorted by DPS (best first, auto-selected), and clicking
 * a row re-points the readout — all driven headlessly through the real Swing
 * component (null ItemManager, which it tolerates by skipping icons).
 */
public class GearSectionStyleRankingTest
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
			.add(82, 82, 82, 0, 0,
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

	private static double dpsFor(GearSnapshot gear, WeaponStyle style)
	{
		PlayerCombat player = GearMapper.toPlayerCombat(gear, style.stance(), false, false, false);
		DpsResult r = DpsCalculator.compute(gear.equipmentStats(), player, style.type(),
			MonsterRepository.getInstance().byName("Cerberus").get(), 20);
		return r.dps();
	}

	@Test
	public void equippedWeaponStylesAreListedRankedAndBestSelected()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324); // Ghrazi rapier -> stab sword (4 styles)
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			List<WeaponStyle> ranked = section.rankedStylesForTest();
			assertEquals("stab sword exposes four distinct styles", 4, ranked.size());

			// Rows must be sorted by DPS descending, matching an independent compute.
			double prev = Double.MAX_VALUE;
			for (WeaponStyle style : ranked)
			{
				double dps = dpsFor(gear, style);
				assertTrue("styles must be ranked DPS-descending (" + dps + " after " + prev + ")",
					dps <= prev + 1e-9);
				prev = dps;
			}

			// The best (row 0) is auto-selected and drives the readout.
			assertEquals(ranked.get(0), section.selectedStyleForTest());
			assertEquals(String.format(Locale.ROOT, "%.2f", dpsFor(gear, ranked.get(0))),
				section.dpsTextForTest());
		});
	}

	/**
	 * Regression test for a Codex-flagged bug: the best-ranked row's name +
	 * star render in BRAND_ORANGE, and the row's DPS label's foreground is
	 * ALSO set to BRAND_ORANGE — but an HTML {@code <font color>} tag inside
	 * the label's text always wins over {@code setForeground}, so the
	 * fragment's own integer colour has to match the row, not default to
	 * white, or the number visually reads white/grey on an orange row.
	 */
	@Test
	public void bestStyleRowDpsUsesTheRowsOrangeNotTheDefaultWhite()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324); // Ghrazi rapier -> stab sword (4 styles)
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			String brandOrange = String.format(Locale.ROOT, "#%02X%02X%02X",
				net.runelite.client.ui.ColorScheme.BRAND_ORANGE.getRed(),
				net.runelite.client.ui.ColorScheme.BRAND_ORANGE.getGreen(),
				net.runelite.client.ui.ColorScheme.BRAND_ORANGE.getBlue());
			String dullOrange = com.ospulse.ui.CentFormat.dim(brandOrange);

			String bestRaw = section.styleRowDpsRawTextForTest(0);
			assertTrue("best row's integer must be the row's own orange, not white, got: " + bestRaw,
				bestRaw.contains("color='" + brandOrange + "'"));
			assertFalse("best row must not fall back to the default white integer, got: " + bestRaw,
				bestRaw.contains("color='" + com.ospulse.ui.CentFormat.WHITE + "'"));
			assertTrue("best row's decimal must be dimmed to match the orange, got: " + bestRaw,
				bestRaw.contains("<font color='" + dullOrange + "'"));

			// A non-best row (still ranked, index > 0) keeps the plain default.
			String otherRaw = section.styleRowDpsRawTextForTest(1);
			assertTrue("non-best rows must keep the default white integer, got: " + otherRaw,
				otherRaw.contains("color='" + com.ospulse.ui.CentFormat.WHITE + "'"));
			assertFalse("non-best rows must not use the orange row colour, got: " + otherRaw,
				otherRaw.contains(brandOrange));
		});
	}

	@Test
	public void clickingAStyleRowRepointsTheReadout()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			int last = section.styleRowCountForTest() - 1;
			WeaponStyle worst = section.rankedStylesForTest().get(last);
			section.clickStyleRowForTest(last);

			assertEquals(worst, section.selectedStyleForTest());
			assertEquals(String.format(Locale.ROOT, "%.2f", dpsFor(gear, worst)),
				section.dpsTextForTest());
		});
	}

	/**
	 * Item #6d regression ("attack-style buttons need multiple clicks"): the
	 * row's child name label has a tooltip, whose ToolTipManager registration
	 * adds a MouseListener to the LABEL — making the label (which covers
	 * nearly the whole row) the mouse-event target instead of the row, and
	 * Swing never bubbles the press up to the row's own listener. A press
	 * dispatched to the child label — what a real click on the style text
	 * does — must therefore select the style directly from the label's own
	 * listeners, first time, every time.
	 */
	@Test
	public void pressOnStyleRowChildLabel_selectsThatStyleFirstTime()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			int last = section.styleRowCountForTest() - 1;
			WeaponStyle worst = section.rankedStylesForTest().get(last);
			assertTrue("fixture sanity: the worst style must not already be selected",
				!worst.equals(section.selectedStyleForTest()));

			section.pressStyleRowLabelForTest(last);

			assertEquals("a single press on the row's text label must switch the style",
				worst, section.selectedStyleForTest());
		});
	}

	@Test
	public void unarmedFallsBackToThreeCrushStyles()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(-1))); // no weapon
			pickCerberus(section);

			List<WeaponStyle> ranked = section.rankedStylesForTest();
			assertEquals(3, ranked.size());
			for (WeaponStyle s : ranked)
			{
				assertEquals(CombatStyle.CRUSH, s.type());
			}
		});
	}

	@Test
	public void whipExposesItsThreeSlashStyles()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			section.apply(snapshotWith(gearWithWeapon(4151))); // Abyssal whip -> whip (3 slash styles)
			pickCerberus(section);

			List<WeaponStyle> ranked = section.rankedStylesForTest();
			assertEquals(3, ranked.size());
			for (WeaponStyle s : ranked)
			{
				assertEquals(CombatStyle.SLASH, s.type());
			}
			// A pure-melee weapon never shows the spell picker.
			assertTrue("melee weapon must not show the spell picker",
				!section.spellPickerForTest().isVisible());
		});
	}

	// ---- magic: spell picker + powered staves ------------------------------------------

	private static GearSnapshot gearWithMagicWeapon(int weaponId, com.ospulse.combat.PoweredStaff poweredStaff)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.add(0, 0, 0, 60, 0,
				0, 0, 0, 0, 0,
				0, 0, 0.0, 0)
			.weaponSpeedTicks(4)
			.poweredStaff(poweredStaff)
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

	/** DPS of one autocast spell (5-tick, "Spell"/STANDARD stance) against Cerberus, computed independently. */
	private static double spellDps(GearSnapshot gear, com.ospulse.combat.Spell spell)
	{
		PlayerCombat player = GearMapper.toPlayerCombat(gear, com.ospulse.combat.Stance.STANDARD,
			false, false, false);
		return DpsCalculator.compute(gear.equipmentStats(), player, CombatStyle.MAGIC,
			MonsterRepository.getInstance().byName("Cerberus").get(), spell).dps();
	}

	@Test
	public void autocastStaffGetsMagicFirstSpellbookView()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Staff of fire 1387 -> "Staff" category. Magic-first view: the 3 crush
			// styles are dropped entirely; spellbook tabs + ranked spell rows replace
			// them, and the legacy spell combo stays hidden.
			GearSnapshot gear = gearWithMagicWeapon(1387, com.ospulse.combat.PoweredStaff.NONE);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			assertTrue("staff must route to the magic-first view", section.magicViewForTest());
			assertTrue("legacy spell picker stays hidden in the magic view",
				!section.spellPickerForTest().isVisible());
			assertEquals("melee style rows are dropped entirely", 0, section.styleRowCountForTest());
			assertTrue("spellbook tabs must show", section.bookTabsVisibleForTest());

			// Standard book (default tab): rows ranked DPS-descending, matching an
			// independent engine compute.
			List<com.ospulse.combat.Spell> ranked = section.rankedSpellsForTest();
			assertTrue("standard book must list its offensive spells", ranked.size() >= 20);
			double prev = Double.MAX_VALUE;
			for (com.ospulse.combat.Spell spell : ranked)
			{
				assertEquals(com.ospulse.combat.Spell.SpellBook.STANDARD, spell.book());
				double dps = spellDps(gear, spell);
				assertTrue("spells must be ranked DPS-descending (" + dps + " after " + prev + ")",
					dps <= prev + 1e-9);
				prev = dps;
			}

			// The best spell is auto-selected and drives the readout + primary line.
			assertEquals(ranked.get(0), section.selectedSpellForTest());
			assertEquals(String.format(Locale.ROOT, "%.2f", spellDps(gear, ranked.get(0))),
				section.dpsTextForTest());
			assertTrue("primary readout must name the best spell, got: " + section.primaryTextForTest(),
				section.primaryTextForTest().startsWith(ranked.get(0).displayName()));
			assertTrue("secondary readout must name the next-best spell, got: " + section.secondaryTextForTest(),
				section.secondaryTextForTest().startsWith(ranked.get(1).displayName()));
		});
	}

	// ---- QA fix: Iban Blast is castable ONLY with Iban's staff equipped -------------------

	@Test
	public void ibanBlastIsExcludedFromCandidatesWithoutIbansStaffEquipped()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Staff of fire (1387) is a regular "staff" category weapon, not Iban's staff.
			GearSnapshot gear = gearWithMagicWeapon(1387, com.ospulse.combat.PoweredStaff.NONE);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			List<com.ospulse.combat.Spell> ranked = section.rankedSpellsForTest();
			assertTrue("Iban Blast must never appear/rank without Iban's staff equipped",
				ranked.stream().noneMatch(s -> s == com.ospulse.combat.Spell.IBAN_BLAST));
		});
	}

	@Test
	public void ibanBlastIsIncludedInCandidatesWithIbansStaffEquipped()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Iban's staff, item id 1409.
			GearSnapshot gear = gearWithMagicWeapon(1409, com.ospulse.combat.PoweredStaff.NONE);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			List<com.ospulse.combat.Spell> ranked = section.rankedSpellsForTest();
			assertTrue("Iban Blast must be a candidate when Iban's staff is equipped",
				ranked.stream().anyMatch(s -> s == com.ospulse.combat.Spell.IBAN_BLAST));
		});
	}

	@Test
	public void ancientTabRanksAncientsAndASpellClickLocksTheReadout()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithMagicWeapon(1387, com.ospulse.combat.PoweredStaff.NONE);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			section.clickBookTabForTest(1); // Ancient
			List<com.ospulse.combat.Spell> ranked = section.rankedSpellsForTest();
			assertEquals("all 16 Ancient Magicks are offensive", 16, ranked.size());
			for (com.ospulse.combat.Spell spell : ranked)
			{
				assertEquals(com.ospulse.combat.Spell.SpellBook.ANCIENT, spell.book());
			}
			// Ice Barrage (base 30, the book's hardest hitter) auto-selects; the
			// readout derives max hit from it (no magic-damage gear worn -> 30).
			assertEquals(com.ospulse.combat.Spell.ICE_BARRAGE, section.selectedSpellForTest());
			assertEquals("30", section.maxHitTextForTest());

			// Clicking a worse row locks the main readout to it; the primary/
			// secondary lines keep showing the best/next-best casts.
			int last = ranked.size() - 1;
			section.clickSpellRowForTest(last);
			assertEquals(ranked.get(last), section.selectedSpellForTest());
			assertEquals(String.format(Locale.ROOT, "%.2f", spellDps(gear, ranked.get(last))),
				section.dpsTextForTest());
			assertTrue(section.primaryTextForTest().startsWith(ranked.get(0).displayName()));
		});
	}

	@Test
	public void onlyStandardAndAncientBookTabsExist()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithMagicWeapon(1387, com.ospulse.combat.PoweredStaff.NONE);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			// Lunar/Arceuus have no offensive spells in OSRS, so they are not
			// offered as tabs at all — only Standard(0) and Ancient(1).
			assertEquals(2, com.ospulse.ui.sections.GearSection.BookTab.values().length);
		});
	}

	@Test
	public void poweredStaffDerivesMaxHitFromMagicLevelAndHidesPicker()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Trident of the seas 11907 -> powered staff; at 99 magic: floor(99/3)-5 = 28.
			GearSnapshot gear = gearWithMagicWeapon(11907,
				com.ospulse.combat.PoweredStaff.TRIDENT_OF_THE_SEAS);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			assertTrue("powered staff needs no spell picker",
				!section.spellPickerForTest().isVisible());

			List<WeaponStyle> ranked = section.rankedStylesForTest();
			assertTrue("powered staff exposes magic styles", ranked.size() >= 1);
			assertEquals(CombatStyle.MAGIC, ranked.get(0).type());
			assertEquals("28", section.maxHitTextForTest());
		});
	}

	@Test
	public void overkillIsSurfacedInTheReadout()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			String overkill = section.overkillTextForTest();
			assertTrue("overkill must be a number once a target is picked, got: " + overkill,
				overkill.matches("\\d+\\.\\d"));
		});
	}

	/**
	 * Item #10: accuracy, avg hit, TTK and overkill all get the same "cent"
	 * number styling as DPS (bright white integer and suffix, dim decimal
	 * only, no size change) — the raw label text carries the HTML markup,
	 * but the plain-text accessors (used elsewhere to assert the displayed
	 * VALUE) must still strip it back down to the same plain numbers the
	 * readout always showed.
	 */
	@Test
	public void accuracyAvgHitAndTtkAreCentStyledAndStripCleanly()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			GearSnapshot gear = gearWithWeapon(22324);
			section.apply(snapshotWith(gear));
			pickCerberus(section);

			String accuracyRaw = section.accuracyRawTextForTest();
			assertTrue("accuracy must actually carry the cent HTML markup, got: " + accuracyRaw,
				accuracyRaw.startsWith("<html>") && accuracyRaw.contains("<font color='#8C8C8C'>"));

			String accuracy = section.accuracyTextForTest();
			assertTrue("accuracy must be a plain N.N% once stripped, got: " + accuracy,
				accuracy.matches("\\d+\\.\\d%"));

			String avgHit = section.avgHitTextForTest();
			assertTrue("avg hit must be a plain N.NN once stripped, got: " + avgHit,
				avgHit.matches("\\d+\\.\\d\\d"));

			String ttk = section.ttkTextForTest();
			assertTrue("ttk must be a plain duration once stripped, got: " + ttk,
				ttk.matches("\\d+\\.\\ds|\\d+:\\d\\d"));
		});
	}

	// ---- QA fix 1: the potion toggle's right-click swap must actually feed a
	// different magic-potion boost into the DPS readout, not just repaint an icon ----

	@Test
	public void potionVariantSwapChangesTheMagicReadout()
	{
		onEdt(() ->
		{
			GearSection section = new GearSection(NO_STORE, null, null);
			// Trident of the seas 11907 -> powered staff; base magic 99, so
			// assumeBestPotion's boosted level differs per variant (see
			// PotionBoosts.*BoostedLevel), which floor(boosted/3)-5 turns into a
			// different max hit for each swap pick.
			GearSnapshot gear = gearWithMagicWeapon(11907,
				com.ospulse.combat.PoweredStaff.TRIDENT_OF_THE_SEAS);
			section.apply(snapshotWith(gear));
			pickCerberus(section);
			section.bestPotionToggleForTest().setSelected(true);

			// Default (no swap yet) == Imbued heart: boosted = 99+1+9=109, floor(109/3)-5=31.
			assertEquals(null, section.magicPotionVariantForTest());
			assertEquals("31", section.maxHitTextForTest());

			section.pickMagicPotionVariantForTest(com.ospulse.combat.CombatIcons.BoostPotion.SATURATED_HEART);
			assertEquals(com.ospulse.combat.CombatIcons.BoostPotion.SATURATED_HEART,
				section.magicPotionVariantForTest());
			// Saturated heart: boosted = 99+4+9=112, floor(112/3)-5=32.
			assertEquals("32", section.maxHitTextForTest());

			section.pickMagicPotionVariantForTest(com.ospulse.combat.CombatIcons.BoostPotion.ANCIENT_BREW);
			// Ancient brew: boosted = 99+2+4=105, floor(105/3)-5=30.
			assertEquals("30", section.maxHitTextForTest());
		});
	}
}
