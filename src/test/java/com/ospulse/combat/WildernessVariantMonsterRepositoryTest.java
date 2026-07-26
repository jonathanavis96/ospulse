package com.ospulse.combat;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the bundled, hand-curated {@code wilderness_variant_monsters.json}
 * (both-locations monsters — see {@link WildernessMonsterRepositoryTest} for
 * the separate Wilderness-EXCLUSIVE set) parses, every {@code baseMonster}
 * resolves against the real bundled {@code monsters.min.json.gz}, every
 * {@code displayName} is unique and does NOT collide with a real bundled
 * name, and that {@link MonsterRepository} actually synthesizes and
 * correctly wires each twin — this is the director-mandated fix replacing
 * the earlier stage's "exclude anything ambiguous" rule: a both-locations
 * monster is now an explicitly SELECTABLE separate target rather than
 * either silently assumed or silently omitted.
 */
public class WildernessVariantMonsterRepositoryTest {
    static { BundledGson.set(new com.google.gson.Gson()); }

    @Test
    public void loadsBundledResource() {
        assertTrue("expected at least the director's own named example (Black dragon)",
                WildernessVariantMonsterRepository.getInstance().size() >= 1);
    }

    @Test
    public void everyBaseMonster_resolvesAgainstTheRealBundledMonsterRepository() {
        MonsterRepository monsters = MonsterRepository.getInstance();
        for (WildernessVariantMonsterRepository.Variant v : WildernessVariantMonsterRepository.getInstance().all()) {
            Optional<Monster> resolved = monsters.byName(v.baseMonster());
            assertTrue("curated baseMonster '" + v.baseMonster()
                    + "' does not resolve against the bundled monster data - check for a typo", resolved.isPresent());
        }
    }

    @Test
    public void everyDisplayName_isUnique_andDoesNotCollideWithARealBundledName() {
        MonsterRepository monsters = MonsterRepository.getInstance();
        Set<String> seen = new HashSet<>();
        for (WildernessVariantMonsterRepository.Variant v : WildernessVariantMonsterRepository.getInstance().all()) {
            assertTrue("duplicate synthetic displayName: " + v.displayName(),
                    seen.add(v.displayName().toLowerCase(java.util.Locale.ROOT)));
            // A collision would mean the synthetic twin silently overwrites
            // (or is indistinguishable from) a REAL bundled monster of the
            // same name - exactly the kind of identity confusion this whole
            // design exists to avoid.
            boolean collidesWithRealMonster = monsters.byName(v.displayName()).isPresent()
                    && !monsters.byName(v.displayName()).get().lookupName().equalsIgnoreCase(v.baseMonster());
            assertFalse("synthetic displayName '" + v.displayName() + "' collides with an unrelated real monster",
                    collidesWithRealMonster);
        }
    }

    /** The Black dragon (Level 227) at the Lava Maze Dungeon is the director's own named example. */
    @Test
    public void blackDragonLevel227_hasAWildernessVariant() {
        List<WildernessVariantMonsterRepository.Variant> variants = WildernessVariantMonsterRepository.getInstance().all();
        boolean found = variants.stream().anyMatch(v ->
                v.baseMonster().equalsIgnoreCase("Black dragon (Level 227)")
                        && v.displayName().equalsIgnoreCase("Black dragon (Wilderness)"));
        assertTrue("Black dragon (Level 227) must have a 'Black dragon (Wilderness)' variant", found);
    }

    /**
     * Every {@code displayName} must resolve as a Wilderness target via
     * {@link MonsterRepository}, while its {@code baseMonster} (the
     * ordinary, non-Wilderness entry) must NOT — proving the twin is
     * genuinely a SEPARATE selectable target, not a mutation of the base.
     */
    @Test
    public void everyVariant_synthesizesATwinThatIsWildernessTarget_baseIsNot() {
        MonsterRepository monsters = MonsterRepository.getInstance();
        for (WildernessVariantMonsterRepository.Variant v : WildernessVariantMonsterRepository.getInstance().all()) {
            Monster twin = monsters.byName(v.displayName()).orElse(null);
            Monster base = monsters.byName(v.baseMonster()).orElse(null);
            assertTrue(v.displayName() + " must resolve via MonsterRepository", twin != null);
            assertTrue(v.baseMonster() + " must resolve via MonsterRepository", base != null);
            assertTrue(v.displayName() + " must be a Wilderness target", twin.isWildernessTarget());
            assertFalse(v.baseMonster() + " (the plain entry) must NOT be a Wilderness target", base.isWildernessTarget());
        }
    }

    /**
     * The reverse map: a synthetic twin's {@link Monster#lookupName()} must
     * point back to its real base monster's name, while its {@link
     * Monster#name()} stays the decorated display name — the exact
     * distinction every OTHER consumer (combat requirements, gear
     * overrides, consumables) resolves through.
     */
    @Test
    public void twinLookupNameResolvesToTheBase_nameStaysDecorated() {
        MonsterRepository monsters = MonsterRepository.getInstance();
        Monster twin = monsters.byName("Black dragon (Wilderness)").orElseThrow(AssertionError::new);
        assertEquals("Black dragon (Wilderness)", twin.name());
        assertEquals("Black dragon (Level 227)", twin.lookupName());
    }

    /**
     * Every stat field on the twin must be an EXACT copy of the base
     * monster's — the only differences are name/lookupName/wildernessTarget.
     * A DPS discrepancy between the two (beyond the revenant-weapon bonus
     * itself) would mean the clone silently diverged from its source.
     */
    @Test
    public void twinCopiesEveryStatFieldFromItsBaseMonster() {
        MonsterRepository monsters = MonsterRepository.getInstance();
        Monster twin = monsters.byName("Black dragon (Wilderness)").orElseThrow(AssertionError::new);
        Monster base = monsters.byName("Black dragon (Level 227)").orElseThrow(AssertionError::new);

        assertEquals(base.hitpoints(), twin.hitpoints());
        assertEquals(base.defenceLevel(), twin.defenceLevel());
        assertEquals(base.dstab(), twin.dstab());
        assertEquals(base.dslash(), twin.dslash());
        assertEquals(base.dcrush(), twin.dcrush());
        assertEquals(base.dmagic(), twin.dmagic());
        assertEquals(base.drange(), twin.drange());
        assertEquals(base.magicLevel(), twin.magicLevel());
        assertEquals(base.size(), twin.size());
        assertEquals(base.attributes(), twin.attributes());
        assertEquals(base.attackSpeedTicks(), twin.attackSpeedTicks());
        assertEquals(base.demonbaneResistPercent(), twin.demonbaneResistPercent());
        assertEquals(base.weaknessElement(), twin.weaknessElement());
        assertEquals(base.weaknessSeverity(), twin.weaknessSeverity());
    }

    /**
     * Cross-check against {@link WildernessMonsterRepositoryTest}'s own
     * "no Wilderness location at all" list: none of THOSE names should ever
     * appear as a {@code baseMonster} here (a monster either has no
     * Wilderness presence, is Wilderness-exclusive, or is both-locations -
     * never more than one of the three).
     */
    @Test
    public void noBaseMonsterIsAlsoInTheWildernessExclusiveSet() {
        WildernessMonsterRepository exclusive = WildernessMonsterRepository.getInstance();
        for (WildernessVariantMonsterRepository.Variant v : WildernessVariantMonsterRepository.getInstance().all()) {
            assertFalse(v.baseMonster() + " cannot be both a both-locations base AND Wilderness-exclusive",
                    exclusive.isWilderness(v.baseMonster()));
        }
    }
}
