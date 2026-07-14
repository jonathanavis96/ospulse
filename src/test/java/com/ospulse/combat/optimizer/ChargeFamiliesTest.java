package com.ospulse.combat.optimizer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.runelite.api.gameval.ItemID;

import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ChargeFamilies} — the clean-room charge-variant model.
 *
 * <p>The two data-integrity tests are the load-bearing ones: they prove the
 * family membership+order can be trusted for a live plugin. {@link
 * #everyMemberIsIndexedAndStatBearing} pins each declared member to the bundled
 * data (so a representative id is always evaluable), and {@link
 * #everyFamilyShares_dpsRelevantStats} proves the collapse is DPS-neutral — the
 * whole reason only stat-identical families are included.
 */
public class ChargeFamiliesTest {
    private static final int GLORY_1 = ItemID.AMULET_OF_GLORY_1;   // 1706
    private static final int GLORY_4 = ItemID.AMULET_OF_GLORY_4;   // 1712
    private static final int GLORY_6 = ItemID.AMULET_OF_GLORY_6;   // 11978
    private static final int GLORY_ETERNAL = ItemID.AMULET_OF_GLORY_INF; // 19707
    private static final int GLORY_UNCHARGED = ItemID.AMULET_OF_GLORY;   // 1704
    private static final int GLORY_T_4 = ItemID.TRAIL_AMULET_OF_GLORY_4; // 10354
    private static final int WHIP = 4151; // a non-charge item

    // ---------------------------------------------------------------- membership

    @Test
    public void recognisesChargeMembers_andRejectsNonMembers() {
        assertTrue(ChargeFamilies.isMember(GLORY_1));
        assertTrue(ChargeFamilies.isMember(GLORY_ETERNAL));
        assertTrue(ChargeFamilies.isMember(GLORY_T_4));
        assertFalse("a whip is not a charge-variant", ChargeFamilies.isMember(WHIP));
        assertFalse(ChargeFamilies.isMember(-1));
    }

    @Test
    public void regularAndTrimmedGloryAreSeparateFamilies() {
        assertFalse("plain glory and glory (t) must not share a family",
                sameFamily(GLORY_4, GLORY_T_4));
        assertTrue(sameFamily(GLORY_1, GLORY_4));
        assertTrue("eternal glory belongs with the regular glory family",
                sameFamily(GLORY_ETERNAL, GLORY_4));
    }

    // ------------------------------------------------- highest-charge selection

    @Test
    public void bestOwnedMember_picksHighestChargeOwned() {
        Set<Integer> owned = new HashSet<>(Arrays.asList(GLORY_1, GLORY_4)); // own (1) and (4)
        assertEquals("owning (1) and (4) resolves to (4)",
                Integer.valueOf(GLORY_4), ChargeFamilies.bestOwnedMember(GLORY_1, owned, null));
        // Resolving from ANY member id gives the same family answer.
        assertEquals(Integer.valueOf(GLORY_4), ChargeFamilies.bestOwnedMember(GLORY_UNCHARGED, owned, null));
    }

    @Test
    public void bestOwnedMember_prefersEternalOverEveryCharge() {
        Set<Integer> owned = new HashSet<>(Arrays.asList(GLORY_4, GLORY_6, GLORY_ETERNAL));
        assertEquals(Integer.valueOf(GLORY_ETERNAL), ChargeFamilies.bestOwnedMember(GLORY_4, owned, null));
    }

    @Test
    public void bestOwnedMember_skipsExcludedCharges() {
        Set<Integer> owned = new HashSet<>(Arrays.asList(GLORY_1, GLORY_4));
        Set<Integer> excluded = new HashSet<>(Arrays.asList(GLORY_4)); // exclude the (4)
        assertEquals("with (4) excluded, fall back to the next owned charge (1)",
                Integer.valueOf(GLORY_1), ChargeFamilies.bestOwnedMember(GLORY_1, owned, excluded));
    }

    @Test
    public void bestOwnedMember_nullWhenNothingOwnedOrNotAMember() {
        assertNull(ChargeFamilies.bestOwnedMember(GLORY_1, new HashSet<>(), null));
        assertNull(ChargeFamilies.bestOwnedMember(WHIP, new HashSet<>(Arrays.asList(WHIP)), null));
    }

    // --------------------------------------------------------- ordering integrity

    @Test
    public void familiesAreStrictlyOrdered_uncharedLastEternalFirst() {
        int[] glory = ChargeFamilies.familyOf(GLORY_4);
        assertNotNull(glory);
        // Highest-charge first: eternal, then (6), and uncharged dead last.
        assertEquals("eternal glory heads the regular family", GLORY_ETERNAL, glory[0]);
        assertEquals("uncharged glory is the lowest-ranked member", GLORY_UNCHARGED, glory[glory.length - 1]);
        // No duplicate ids within a family.
        for (int[] family : ChargeFamilies.familiesForTest()) {
            Set<Integer> seen = new HashSet<>();
            for (int id : family) {
                assertTrue("duplicate id " + id + " in a family", seen.add(id));
            }
        }
    }

    // ------------------------------------------------------ data-integrity gates

    @Test
    public void everyMemberIsIndexedAndStatBearing() {
        Map<String, List<Object>> index = loadIndex();
        Map<String, List<Double>> stats = loadStats();
        for (int[] family : ChargeFamilies.familiesForTest()) {
            for (int id : family) {
                assertTrue("family member " + id + " missing from equipment_index (would never be a candidate)",
                        index.containsKey(String.valueOf(id)));
                assertTrue("family member " + id + " missing from equipment_stats (representative must be evaluable)",
                        stats.containsKey(String.valueOf(id)));
            }
        }
    }

    /**
     * The invariant that makes the whole feature safe: within a family, every
     * charge exposes IDENTICAL DPS-relevant bonuses (the 15 leading values —
     * indices 0..14 astab..speed; the trailing slotId is not a combat stat), so
     * collapsing the family to one representative can never change a DPS result.
     */
    @Test
    public void everyFamilyShares_dpsRelevantStats() {
        Map<String, List<Double>> stats = loadStats();
        for (int[] family : ChargeFamilies.familiesForTest()) {
            List<Double> baseline = null;
            int baselineId = -1;
            for (int id : family) {
                List<Double> row = stats.get(String.valueOf(id));
                assertNotNull("no stats for " + id, row);
                List<Double> dpsRelevant = row.subList(0, 15); // astab..speed
                if (baseline == null) {
                    baseline = dpsRelevant;
                    baselineId = id;
                } else {
                    assertEquals("charge " + id + " has different combat stats to " + baselineId
                                    + " — it is NOT a pure charge family and must not be collapsed",
                            baseline, dpsRelevant);
                }
            }
        }
    }

    // ------------------------------------------------------------------- helpers

    private static boolean sameFamily(int a, int b) {
        int[] fa = ChargeFamilies.familyOf(a);
        return fa != null && fa == ChargeFamilies.familyOf(b);
    }

    private static Map<String, List<Object>> loadIndex() {
        Type t = new TypeToken<Map<String, List<Object>>>() { }.getType();
        return readResource("/com/ospulse/combat/equipment_index.min.json", t);
    }

    private static Map<String, List<Double>> loadStats() {
        Type t = new TypeToken<Map<String, List<Double>>>() { }.getType();
        return readResource("/com/ospulse/combat/equipment_stats.min.json", t);
    }

    private static <T> T readResource(String path, Type type) {
        try (Reader r = new InputStreamReader(
                ChargeFamiliesTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, type);
        } catch (Exception e) {
            throw new RuntimeException("failed reading " + path, e);
        }
    }
}
