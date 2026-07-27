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

	@Test
	public void isTonalzticsOfRalosUncharged_unchargedId_returnsTrue()
	{
		org.junit.Assert.assertTrue(GearVariants.isTonalzticsOfRalosUncharged(28919));
	}

	@Test
	public void isTonalzticsOfRalosUncharged_chargedIdOrEmptySlot_returnsFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isTonalzticsOfRalosUncharged(28922));
		org.junit.Assert.assertFalse(GearVariants.isTonalzticsOfRalosUncharged(-1)); // empty slot
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

	/**
	 * The uncharged scythe ids (22486 / 25738 / 25741) are deliberately IN
	 * {@code isScytheOfVitur} — charging buys stats only, not the multi-hit
	 * cascade. This pins BOTH halves of that claim so neither can rot:
	 *
	 * <ol>
	 * <li>the uncharged ids still report the cascade, and</li>
	 * <li>they are genuinely the weaker CHARGE STATE, not a cosmetic dye —
	 * their bundled stats are lower by exactly the deltas the OSRS Wiki
	 * enumerates for charging ("+20 stab/crush, +50 slash attack bonus
	 * ... and +25 strength bonus").</li>
	 * </ol>
	 *
	 * <p>Half (2) is what makes this test discriminating. Without it a future
	 * reader could "fix" a PR-review finding by dropping the uncharged ids on
	 * the theory that they only hit once, and half (1) alone would look like
	 * an arbitrary assertion defending a bug. The stat deltas are the
	 * evidence that the weaker uncharged DPS already falls out of its own
	 * stats, so keeping the cascade does not inflate it. (PR #24, rounds 4-5.)
	 */
	@Test
	public void scytheChargeState_unchargedKeepsCascadeAndIsWeakerOnStatsAlone()
	{
		// {charged, uncharged} per cosmetic variant, bundled equipment_stats.min.json.
		int[][] chargedUnchargedPairs = {
			{22325, 22486}, // Scythe of vitur
			{25736, 25738}, // Holy scythe of vitur
			{25739, 25741}  // Sanguine scythe of vitur
		};
		com.ospulse.combat.EquipmentStatsRepository stats =
			com.ospulse.combat.EquipmentStatsRepository.getInstance();

		for (int[] pair : chargedUnchargedPairs)
		{
			int charged = pair[0];
			int uncharged = pair[1];

			// (1) Both charge states cascade — the passive is not bought by charging.
			org.junit.Assert.assertTrue("charged id=" + charged, GearVariants.isScytheOfVitur(charged));
			org.junit.Assert.assertTrue("uncharged id=" + uncharged, GearVariants.isScytheOfVitur(uncharged));

			// (2) The uncharged form is weaker purely on stats, by the wiki's own deltas.
			com.ospulse.combat.EquipmentStatsRepository.Stats c = stats.statsFor(charged);
			com.ospulse.combat.EquipmentStatsRepository.Stats u = stats.statsFor(uncharged);
			org.junit.Assert.assertNotNull("charged stats id=" + charged, c);
			org.junit.Assert.assertNotNull("uncharged stats id=" + uncharged, u);

			assertEquals("stab delta, id=" + uncharged, 20, c.astab() - u.astab());
			assertEquals("slash delta, id=" + uncharged, 50, c.aslash() - u.aslash());
			assertEquals("crush delta, id=" + uncharged, 20, c.acrush() - u.acrush());
			assertEquals("strength delta, id=" + uncharged, 25, c.str() - u.str());

			// Same attack speed: charging changes bonuses, not the weapon's tick rate.
			assertEquals("speed, id=" + uncharged, c.aspeed(), u.aspeed());
		}
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

	// ==== Revenant weapons ==================================================================

	@Test
	public void revenantWeaponFor_allEightChargedIds()
	{
		// Ids verified against the bundled equipment_index.min.json 2026-07-27.
		// Only the CHARGED id of each Uncharged/Charged pair carries the
		// +50% Wilderness bonus - see revenantWeaponFor_allEightUnchargedIds_returnNone.
		assertEquals(com.ospulse.combat.RevenantWeapon.CRAWS_BOW, GearVariants.revenantWeaponFor(22550)); // Craw's bow
		assertEquals(com.ospulse.combat.RevenantWeapon.CRAWS_BOW, GearVariants.revenantWeaponFor(27655)); // Webweaver bow
		assertEquals(com.ospulse.combat.RevenantWeapon.VIGGORAS_CHAINMACE, GearVariants.revenantWeaponFor(22545)); // Viggora's chainmace
		assertEquals(com.ospulse.combat.RevenantWeapon.VIGGORAS_CHAINMACE, GearVariants.revenantWeaponFor(27660)); // Ursine chainmace
		assertEquals(com.ospulse.combat.RevenantWeapon.THAMMARONS_SCEPTRE, GearVariants.revenantWeaponFor(22555)); // Thammaron's sceptre
		assertEquals(com.ospulse.combat.RevenantWeapon.THAMMARONS_SCEPTRE, GearVariants.revenantWeaponFor(27788)); // Thammaron's sceptre (a)
		assertEquals(com.ospulse.combat.RevenantWeapon.THAMMARONS_SCEPTRE, GearVariants.revenantWeaponFor(27665)); // Accursed sceptre
		assertEquals(com.ospulse.combat.RevenantWeapon.THAMMARONS_SCEPTRE, GearVariants.revenantWeaponFor(27679)); // Accursed sceptre (a)
	}

	@Test
	public void revenantWeaponFor_allEightUnchargedIds_returnNone()
	{
		// P1 fix: the Uncharged form of each revenant-cave weapon has
		// IDENTICAL combat stats to its Charged counterpart in
		// equipment_stats.min.json, but the wiki is explicit the +50%
		// Wilderness bonus requires the weapon to be charged with revenant
		// ether - so an Uncharged id must resolve to NONE. This must fail
		// if any of these ids is re-added to REVENANT_WEAPON_IDS.
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(22547)); // Craw's bow (uncharged)
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(27652)); // Webweaver bow (uncharged)
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(22542)); // Viggora's chainmace (uncharged)
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(27657)); // Ursine chainmace (uncharged)
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(22552)); // Thammaron's sceptre (uncharged)
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(27785)); // Thammaron's sceptre (a) (uncharged)
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(27662)); // Accursed sceptre (uncharged)
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(27676)); // Accursed sceptre (a) (uncharged)
	}

	@Test
	public void revenantWeaponFor_nonRevenantWeapon_returnsNone()
	{
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(4151)); // Abyssal whip
		assertEquals(com.ospulse.combat.RevenantWeapon.NONE, GearVariants.revenantWeaponFor(-1));
	}

	// ==== Powered staves (Thammaron's / Accursed sceptre) =================================

	/**
	 * P1 fix: the charged Thammaron's sceptre and Accursed sceptre are
	 * classified {@code "powered staff"} in the bundled {@code
	 * weapon_categories.min.json} (verified directly against that file), so
	 * {@code poweredStaffFor} must resolve them instead of leaving the
	 * optimizer to evaluate impossible spellbook casts for them. Hard-coded
	 * against the specific enum constants (not a generic "not NONE" check) so
	 * this fails if the ids were ever swapped between the two constants.
	 */
	@Test
	public void poweredStaffFor_chargedThammaronsAndAccursedSceptre()
	{
		assertEquals(com.ospulse.combat.PoweredStaff.THAMMARONS_SCEPTRE, GearVariants.poweredStaffFor(22555)); // Thammaron's sceptre
		assertEquals(com.ospulse.combat.PoweredStaff.ACCURSED_SCEPTRE, GearVariants.poweredStaffFor(27665)); // Accursed sceptre
	}

	/**
	 * The uncharged forms cannot attack (same reasoning as every other
	 * uncharged powered staff), and the "(a)" cosmetic variants are
	 * classified plain {@code "staff"} (NOT {@code "powered staff"}) in the
	 * bundled data — verified directly against {@code
	 * weapon_categories.min.json} before writing this test. All four must
	 * stay {@code NONE}.
	 */
	@Test
	public void poweredStaffFor_unchargedAndCosmeticSceptreVariants_returnNone()
	{
		assertEquals(com.ospulse.combat.PoweredStaff.NONE, GearVariants.poweredStaffFor(22552)); // Thammaron's sceptre (uncharged)
		assertEquals(com.ospulse.combat.PoweredStaff.NONE, GearVariants.poweredStaffFor(27662)); // Accursed sceptre (uncharged)
		assertEquals(com.ospulse.combat.PoweredStaff.NONE, GearVariants.poweredStaffFor(27788)); // Thammaron's sceptre (a) - plain "staff" category
		assertEquals(com.ospulse.combat.PoweredStaff.NONE, GearVariants.poweredStaffFor(27679)); // Accursed sceptre (a) - plain "staff" category
	}

	// ==== Crystal armour set + crystal bow / Bow of Faerdhinen ============================

	@Test
	public void isActiveCrystalArmourSet_fullOverworldSet_returnsTrue()
	{
		// The pre-rework single "Crystal helm/body/legs" ids - genuine
		// overworld, unlimited-use crystal armour (not Gauntlet-instance-only).
		org.junit.Assert.assertTrue(GearVariants.isActiveCrystalArmourSet(23971, 23975, 23979));
	}

	@Test
	public void isActiveCrystalArmourSet_mixedActiveVariants_stillReturnsTrue()
	{
		// Mixing the pre-rework helm with an Elf clan cosmetic body/legs - the
		// wiki does not require matching cosmetic variants, only that each
		// piece is a genuine (non-Gauntlet, non-inactive) overworld piece.
		org.junit.Assert.assertTrue(GearVariants.isActiveCrystalArmourSet(23971, 27697, 27701)); // Hefin body/legs
	}

	@Test
	public void isActiveCrystalArmourSet_oneInactivePiece_returnsFalse()
	{
		// Id 23973 is the INACTIVE (all-zero-stat) "Crystal helm" companion id
		// - the exact prior P1-class trap this mechanic must not repeat.
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalArmourSet(23973, 23975, 23979));
	}

	@Test
	public void isActiveCrystalArmourSet_missingPiece_returnsFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalArmourSet(-1, 23975, 23979));
	}

	/**
	 * Review finding (confirmed real): ids 23886-23894 are The Gauntlet's
	 * INSTANCE-ONLY basic/attuned/perfected crystal armour tiers — {@link
	 * com.ospulse.ui.sections.gear.ItemEligibility#isGauntletOnlyItem}
	 * already treats this exact id range as unusable outside the Gauntlet
	 * for optimiser-candidate purposes. A live-equipped read while literally
	 * inside the Gauntlet must NOT credit the overworld set's +15%/+30%
	 * bonus — even a full "matching" basic/attuned/perfected trio.
	 */
	@Test
	public void isActiveCrystalArmourSet_gauntletOnlyTiers_neverCountTowardsTheOverworldSet()
	{
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalArmourSet(23886, 23889, 23892)); // basic tier
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalArmourSet(23887, 23890, 23893)); // attuned tier
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalArmourSet(23888, 23891, 23894)); // perfected tier
		// Even mixed with genuine overworld pieces, a single Gauntlet-only
		// piece must deny the whole set (matching the missing-piece contract).
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalArmourSet(23886, 23975, 23979));
	}

	@Test
	public void isActiveCrystalBowOrFaerdhinen_chargedBowOfFaerdhinen_returnsTrue()
	{
		org.junit.Assert.assertTrue(GearVariants.isActiveCrystalBowOrFaerdhinen(25865));
	}

	@Test
	public void isActiveCrystalBowOrFaerdhinen_unchargedBowOfFaerdhinen_returnsFalse()
	{
		// Id 25862 is the UNCHARGED (all-zero-stat) Bow of Faerdhinen.
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalBowOrFaerdhinen(25862));
	}

	@Test
	public void isActiveCrystalBowOrFaerdhinen_plainCrystalBow_returnsTrue()
	{
		org.junit.Assert.assertTrue(GearVariants.isActiveCrystalBowOrFaerdhinen(23983)); // pre-rework "Crystal bow"
	}

	@Test
	public void isActiveCrystalBowOrFaerdhinen_gauntletOnlyTiers_returnFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalBowOrFaerdhinen(23901)); // Crystal bow (basic)
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalBowOrFaerdhinen(23902)); // Crystal bow (attuned)
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalBowOrFaerdhinen(23903)); // Crystal bow (perfected)
	}

	@Test
	public void isActiveCrystalBowOrFaerdhinen_nonCrystalWeapon_returnsFalse()
	{
		org.junit.Assert.assertFalse(GearVariants.isActiveCrystalBowOrFaerdhinen(861)); // Magic shortbow
	}
}
