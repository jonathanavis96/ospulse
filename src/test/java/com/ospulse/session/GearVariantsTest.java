package com.ospulse.session;

import com.ospulse.combat.DemonbaneWeapon;
import com.ospulse.combat.SalveType;
import com.ospulse.combat.SlayerHeadgear;
import com.ospulse.combat.VoidSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the pure item-id -&gt; Tier-A gear-variant detection in
 * {@link GearVariants}. No RuneLite runtime dependency: everything here is
 * plain ints in, enums out.
 */
public class GearVariantsTest
{
	static { com.ospulse.combat.BundledGson.set(new com.google.gson.Gson()); }
	// ==== Salve amulet ===================================================================

	@Test
	public void salveTypeFor_plainAmulet()
	{
		assertEquals(SalveType.SALVE, GearVariants.salveTypeFor(4081));
	}

	@Test
	public void salveTypeFor_eVariant()
	{
		assertEquals(SalveType.SALVE_E, GearVariants.salveTypeFor(10588));
	}

	@Test
	public void salveTypeFor_iVariant_allRewardSources()
	{
		assertEquals(SalveType.SALVE_I, GearVariants.salveTypeFor(12017)); // base-game imbue
		assertEquals(SalveType.SALVE_I, GearVariants.salveTypeFor(25250)); // Soul Wars
		assertEquals(SalveType.SALVE_I, GearVariants.salveTypeFor(26763)); // PvP Arena
	}

	@Test
	public void salveTypeFor_eiVariant_allRewardSources()
	{
		assertEquals(SalveType.SALVE_EI, GearVariants.salveTypeFor(12018));
		assertEquals(SalveType.SALVE_EI, GearVariants.salveTypeFor(25278));
		assertEquals(SalveType.SALVE_EI, GearVariants.salveTypeFor(26782));
	}

	@Test
	public void salveTypeFor_nonSalveAmulet_returnsNone()
	{
		assertEquals(SalveType.NONE, GearVariants.salveTypeFor(1712)); // amulet of glory, arbitrary non-salve id
		assertEquals(SalveType.NONE, GearVariants.salveTypeFor(-1)); // empty slot
	}

	// ==== Slayer headgear =================================================================

	@Test
	public void slayerHeadgearFor_plainBlackMask()
	{
		assertEquals(SlayerHeadgear.STANDARD, GearVariants.slayerHeadgearFor(8901)); // Black mask (10)
		assertEquals(SlayerHeadgear.STANDARD, GearVariants.slayerHeadgearFor(8921)); // Black mask (uncharged)
	}

	@Test
	public void slayerHeadgearFor_imbuedBlackMask_allRewardSources()
	{
		assertEquals(SlayerHeadgear.IMBUED, GearVariants.slayerHeadgearFor(11784)); // base-game
		assertEquals(SlayerHeadgear.IMBUED, GearVariants.slayerHeadgearFor(25276)); // Soul Wars
		assertEquals(SlayerHeadgear.IMBUED, GearVariants.slayerHeadgearFor(26781)); // PvP Arena
	}

	@Test
	public void slayerHeadgearFor_plainSlayerHelmet()
	{
		assertEquals(SlayerHeadgear.STANDARD, GearVariants.slayerHeadgearFor(11864));
	}

	@Test
	public void slayerHeadgearFor_imbuedSlayerHelmet()
	{
		assertEquals(SlayerHeadgear.IMBUED, GearVariants.slayerHeadgearFor(11865));
	}

	@Test
	public void slayerHeadgearFor_imbuedBossVariant_zuk()
	{
		// Tzkal (Inferno/Zuk) slayer helmet (i) - a boss-kit recolour, must still resolve to IMBUED.
		assertEquals(SlayerHeadgear.IMBUED, GearVariants.slayerHeadgearFor(25912));
	}

	@Test
	public void slayerHeadgearFor_plainBossVariant_hydra()
	{
		assertEquals(SlayerHeadgear.STANDARD, GearVariants.slayerHeadgearFor(23073));
	}

	@Test
	public void slayerHeadgearFor_nonSlayerHead_returnsNone()
	{
		assertEquals(SlayerHeadgear.NONE, GearVariants.slayerHeadgearFor(1053)); // arbitrary unrelated head slot item
		assertEquals(SlayerHeadgear.NONE, GearVariants.slayerHeadgearFor(-1)); // empty slot
	}

	// ==== Void Knight set ==================================================================

	private static final int VOID_MELEE_HELM = 11665;
	private static final int VOID_RANGE_HELM = 11664;
	private static final int VOID_MAGE_HELM = 11663;
	private static final int VOID_TOP = 8839;
	private static final int VOID_ROBE = 8840;
	private static final int VOID_GLOVES = 8842;
	private static final int ELITE_VOID_TOP = 13072;
	private static final int ELITE_VOID_ROBE = 13073;
	private static final int RANDOM_NON_VOID_ITEM = -1;

	@Test
	public void voidSetFor_fullNormalMeleeSet()
	{
		assertEquals(VoidSet.MELEE,
			GearVariants.voidSetFor(VOID_MELEE_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_fullNormalRangedSet()
	{
		assertEquals(VoidSet.RANGED,
			GearVariants.voidSetFor(VOID_RANGE_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_fullEliteRangedSet_requiresBothEliteTopAndRobe()
	{
		assertEquals(VoidSet.RANGED_ELITE,
			GearVariants.voidSetFor(VOID_RANGE_HELM, ELITE_VOID_TOP, ELITE_VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_fullEliteMagicSet()
	{
		assertEquals(VoidSet.MAGIC_ELITE,
			GearVariants.voidSetFor(VOID_MAGE_HELM, ELITE_VOID_TOP, ELITE_VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_mixedTierStillGrantsBaseBonus_butNotEliteBonus()
	{
		// Elite top + normal robe: still a "full" base set (wiki: top OR elite top,
		// robe OR elite robe, independently) but NOT the elite bonus since both
		// pieces must be elite together.
		assertEquals(VoidSet.RANGED,
			GearVariants.voidSetFor(VOID_RANGE_HELM, ELITE_VOID_TOP, VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_meleeGetsNoEliteBonus_evenWithFullElitePieces()
	{
		// VoidSet has no MELEE_ELITE constant - melee gets no extra elite benefit in OSRS.
		assertEquals(VoidSet.MELEE,
			GearVariants.voidSetFor(VOID_MELEE_HELM, ELITE_VOID_TOP, ELITE_VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_missingGloves_returnsNone()
	{
		assertEquals(VoidSet.NONE,
			GearVariants.voidSetFor(VOID_MELEE_HELM, VOID_TOP, VOID_ROBE, RANDOM_NON_VOID_ITEM));
	}

	@Test
	public void voidSetFor_missingRobe_returnsNone()
	{
		assertEquals(VoidSet.NONE,
			GearVariants.voidSetFor(VOID_MELEE_HELM, VOID_TOP, RANDOM_NON_VOID_ITEM, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_missingTop_returnsNone()
	{
		assertEquals(VoidSet.NONE,
			GearVariants.voidSetFor(VOID_MELEE_HELM, RANDOM_NON_VOID_ITEM, VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_noHelmet_returnsNone()
	{
		assertEquals(VoidSet.NONE,
			GearVariants.voidSetFor(RANDOM_NON_VOID_ITEM, VOID_TOP, VOID_ROBE, VOID_GLOVES));
	}

	@Test
	public void voidSetFor_partialSetOnly_returnsNoneEvenWithGlovesAndTop()
	{
		assertEquals(VoidSet.NONE,
			GearVariants.voidSetFor(RANDOM_NON_VOID_ITEM, VOID_TOP, RANDOM_NON_VOID_ITEM, VOID_GLOVES));
	}

	// ==== Demonbane weapons ===============================================================

	@Test
	public void demonbaneWeaponFor_scorchingBow()
	{
		assertEquals(DemonbaneWeapon.SCORCHING_BOW, GearVariants.demonbaneWeaponFor(29591));
	}

	@Test
	public void demonbaneWeaponFor_nonDemonbane_returnsNone()
	{
		assertEquals(DemonbaneWeapon.NONE, GearVariants.demonbaneWeaponFor(20997)); // Twisted bow
		assertEquals(DemonbaneWeapon.NONE, GearVariants.demonbaneWeaponFor(-1)); // empty slot
	}

	// ==== Blowpipe ========================================================================

	@Test
	public void isBlowpipe_toxicBlowpipe_returnsTrue()
	{
		org.junit.Assert.assertTrue(GearVariants.isBlowpipe(12926)); // Toxic blowpipe
	}

	@Test
	public void isBlowpipe_nonBlowpipeWeapon_returnsFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isBlowpipe(861)); // Magic shortbow
		org.junit.Assert.assertFalse(GearVariants.isBlowpipe(-1)); // empty slot
	}

	// ==== Tonalztics of Ralos (charged dual-hit passive) =================================

	@Test
	public void isTonalzticsOfRalosCharged_chargedId_returnsTrue()
	{
		// Id verified against the bundled equipment_index.min.json 2026-07-26:
		// both 28919 and 28922 share the display name "Tonalztics of ralos".
		org.junit.Assert.assertTrue(GearVariants.isTonalzticsOfRalosCharged(28922));
	}

	@Test
	public void isTonalzticsOfRalosCharged_unchargedId_returnsFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isTonalzticsOfRalosCharged(28919));
		org.junit.Assert.assertFalse(GearVariants.isTonalzticsOfRalosCharged(-1)); // empty slot
	}

	// ==== Scythe of Vitur family ==========================================================

	@Test
	public void isScytheOfVitur_allSixIds_returnTrue()
	{
		// Ids verified against the bundled equipment_index.min.json 2026-07-26.
		int[] scytheIds = {22325, 22486, 25736, 25738, 25739, 25741};
		for (int id : scytheIds)
		{
			org.junit.Assert.assertTrue("id=" + id, GearVariants.isScytheOfVitur(id));
		}
	}

	@Test
	public void isScytheOfVitur_nonScytheWeapon_returnsFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isScytheOfVitur(26219)); // Osmumten's fang
		org.junit.Assert.assertFalse(GearVariants.isScytheOfVitur(-1));
	}

	// ==== Colossal blade ===================================================================

	@Test
	public void isColossalBlade_correctId_returnsTrue()
	{
		org.junit.Assert.assertTrue(GearVariants.isColossalBlade(27021));
	}

	@Test
	public void isColossalBlade_nonColossalBladeWeapon_returnsFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isColossalBlade(4151)); // Abyssal whip
		org.junit.Assert.assertFalse(GearVariants.isColossalBlade(-1));
	}

	// ==== Keris partisan family ============================================================

	@Test
	public void kerisPartisanFor_allFiveIds()
	{
		// Ids verified against the bundled equipment_index.min.json 2026-07-26.
		assertEquals(com.ospulse.combat.KerisPartisan.PARTISAN, GearVariants.kerisPartisanFor(25979));
		assertEquals(com.ospulse.combat.KerisPartisan.OF_AMASCUT, GearVariants.kerisPartisanFor(30891));
		assertEquals(com.ospulse.combat.KerisPartisan.OF_BREACHING, GearVariants.kerisPartisanFor(25981));
		assertEquals(com.ospulse.combat.KerisPartisan.OF_CORRUPTION, GearVariants.kerisPartisanFor(27287));
		assertEquals(com.ospulse.combat.KerisPartisan.OF_THE_SUN, GearVariants.kerisPartisanFor(27291));
	}

	@Test
	public void kerisPartisanFor_nonKerisWeapon_returnsNone()
	{
		assertEquals(com.ospulse.combat.KerisPartisan.NONE, GearVariants.kerisPartisanFor(27021)); // Colossal blade
		assertEquals(com.ospulse.combat.KerisPartisan.NONE, GearVariants.kerisPartisanFor(-1));
	}
}
