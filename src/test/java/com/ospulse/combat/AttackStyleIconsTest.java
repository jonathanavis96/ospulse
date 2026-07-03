package com.ospulse.combat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit coverage for {@link AttackStyleIcons}: the native Combat Options
 * sprite-id table (QA fix 2 — the ranked attack-style picker must show the
 * real in-game weapon-type icon, not custom text). Spot-checks a handful of
 * exact ids against the hand-verified {@code net.runelite.api.gameval.SpriteID.Combaticons*}
 * constants, then sweeps every {@link WeaponCategory}'s real styles to prove
 * the mapping never falls through to a missing/zero id.
 */
public class AttackStyleIconsTest {

    @Test
    public void exactNativeSpriteIdsForACoupleOfWellKnownWeaponTypes() {
        // Axe: Chop/Hack/Smash/Block -> AXE_CHOP=234 / AXE_HACK=235 / AXE_SMASH=236 / AXE_BLOCK=233.
        assertEquals(234, spriteFor(WeaponCategory.AXE, "Chop", CombatStyle.SLASH, Stance.ACCURATE));
        assertEquals(235, spriteFor(WeaponCategory.AXE, "Hack", CombatStyle.SLASH, Stance.AGGRESSIVE));
        assertEquals(236, spriteFor(WeaponCategory.AXE, "Smash", CombatStyle.CRUSH, Stance.AGGRESSIVE));
        assertEquals(233, spriteFor(WeaponCategory.AXE, "Block", CombatStyle.SLASH, Stance.DEFENSIVE));

        // Whip: Flick/Lash -> WHIP_FLICK=286 / WHIP_LASH=287.
        assertEquals(286, spriteFor(WeaponCategory.WHIP, "Flick", CombatStyle.SLASH, Stance.ACCURATE));
        assertEquals(287, spriteFor(WeaponCategory.WHIP, "Lash", CombatStyle.SLASH, Stance.CONTROLLED));

        // Bow: Accurate/Rapid/Longrange -> BOW_ACCURATE=268 / BOW_RAPID=269 / BOW_LONGRANGE=270.
        assertEquals(268, spriteFor(WeaponCategory.BOW, "Accurate", CombatStyle.RANGED, Stance.ACCURATE));
        assertEquals(269, spriteFor(WeaponCategory.BOW, "Rapid", CombatStyle.RANGED, Stance.RAPID));
        assertEquals(270, spriteFor(WeaponCategory.BOW, "Longrange", CombatStyle.RANGED, Stance.LONGRANGE));

        // Claws: Chop/Slash/Lunge/Block -> CLAWS_CHOP=279 / CLAWS_SLASH=278 / CLAWS_LUNGE=277 / CLAWS_BLOCK=280.
        assertEquals(279, spriteFor(WeaponCategory.CLAW, "Chop", CombatStyle.SLASH, Stance.ACCURATE));
        assertEquals(278, spriteFor(WeaponCategory.CLAW, "Slash", CombatStyle.SLASH, Stance.AGGRESSIVE));
        assertEquals(277, spriteFor(WeaponCategory.CLAW, "Lunge", CombatStyle.STAB, Stance.CONTROLLED));
        assertEquals(280, spriteFor(WeaponCategory.CLAW, "Block", CombatStyle.SLASH, Stance.DEFENSIVE));

        // Whip is unmapped for the salamander's own moves; salamander gets its own dedicated set.
        assertEquals(289, spriteFor(WeaponCategory.SALAMANDER, "Scorch", CombatStyle.SLASH, Stance.AGGRESSIVE));
        assertEquals(290, spriteFor(WeaponCategory.SALAMANDER, "Flare", CombatStyle.RANGED, Stance.RAPID));
        assertEquals(291, spriteFor(WeaponCategory.SALAMANDER, "Blaze", CombatStyle.MAGIC, Stance.STANDARD));
    }

    @Test
    public void everyRealWeaponStyleResolvesToAPositiveSpriteId() {
        for (WeaponCategory category : WeaponCategory.values()) {
            List<WeaponStyle> styles = WeaponStyles.forCategory(category);
            for (WeaponStyle style : styles) {
                int spriteId = AttackStyleIcons.spriteIdFor(category, style);
                assertTrue(category + "/" + style.name() + " must resolve to a real sprite id, got " + spriteId,
                        spriteId > 0);
            }
        }
    }

    @Test
    public void nullCategoryOrStyleFallsBackRatherThanThrowing() {
        WeaponStyle stab = new WeaponStyle("Stab", CombatStyle.STAB, Stance.ACCURATE);
        assertTrue("unknown category must still resolve via the generic fallback",
                AttackStyleIcons.spriteIdFor(null, stab) > 0);
        assertTrue("null style must not throw", AttackStyleIcons.spriteIdFor(WeaponCategory.AXE, null) > 0);
    }

    @Test
    public void genericFallbackMatchesDamageTypeForAnUnmappedCategoryStylePair() {
        // A style name that does not exist on the real Axe combat tab (e.g. a
        // hypothetical "Whack" crush move) must still resolve via the
        // same-damage-type generic fallback rather than an arbitrary sprite.
        WeaponStyle hypotheticalCrush = new WeaponStyle("Whack", CombatStyle.CRUSH, Stance.AGGRESSIVE);
        int fallback = AttackStyleIcons.spriteIdFor(WeaponCategory.AXE, hypotheticalCrush);
        int genericCrush = AttackStyleIcons.spriteIdFor(null, hypotheticalCrush);
        assertEquals("unmapped name on a known category must use the same generic fallback as no category at all",
                genericCrush, fallback);
    }

    private static int spriteFor(WeaponCategory category, String name, CombatStyle type, Stance stance) {
        return AttackStyleIcons.spriteIdFor(category, new WeaponStyle(name, type, stance));
    }
}
