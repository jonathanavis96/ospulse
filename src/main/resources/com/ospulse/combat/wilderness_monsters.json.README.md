# wilderness_monsters.json — provenance & regeneration

List of monster display names that are **exclusively** fought in the OSRS
Wilderness — one of the two curated inputs the revenant-weapon Wilderness
bonus (§9e) needs. The bundled monster snapshot (`monsters.min.json.gz`, see
its own README) has no location field at all, so a monster's Wilderness
status cannot be derived from anything already bundled.

See `wilderness_variant_monsters.json.README.md` for the companion set: a
monster that is fought BOTH in and out of the Wilderness (e.g. Black dragon)
does not belong in THIS file — it gets a separately-selectable
"(Wilderness)" twin instead, generated from the same pipeline documented
there. This file and that one are two halves of one generation pass; read
that README first for the methodology, then come back here for what ended
up Wilderness-exclusive specifically.

## Shape
```json
{
  "monsters": [
    "Callisto",
    "Artio"
  ]
}
```
- `monsters`: exact display name(s), matched case-insensitively against
  `Monster.name()` (mirrors `MonsterGearOverrideRepository`/
  `MonsterCombatRequirementRepository`'s own matching convention) — never a
  substring/pattern match, so a name typo here simply means "not treated as
  Wilderness" rather than silently matching something else.

## Director-mandated design (round 3, overriding the original "exclude
anything ambiguous" rule)

The revenant weapons' passive applies to Wilderness monsters generally, not
a hand-picked handful. Coverage is now driven by generating an exhaustive
candidate list from the OSRS Wiki and intersecting it against the bundled
monster names (see the companion README's "Generation" section for the
exact queries/script) rather than hand-curating monster-by-monster. A
monster whose bundled entry has ONLY Wilderness locations lands here,
always-boosted, no second entry needed. A monster with at least one
Wilderness location AND at least one non-Wilderness location is NOT
excluded any more (the original round-2 rule) — it becomes a separate,
explicitly-selectable target instead (`wilderness_variant_monsters.json`),
so the player states which instance they mean rather than the engine
guessing or silently omitting it.

A monster with **no** Wilderness location at all still gets nothing, in
either file — that is a fact, not a judgment call, and is the one case
still resolved by simple exclusion (e.g. `Green dragon (Level 88)`, whose
only location is Corsair Cove Dungeon / Myths' Guild).

**Two corrections made during this pass, from mis-reading the wiki's own
structured location data by eye instead of parsing it directly:**
- `Green dragon (Level 79)` was previously listed here as Wilderness-
  exclusive; the wiki's own `{{LocLine}}` table shows it ALSO spawns at
  Corsair Cove/Myths' Guild (non-Wilderness) — it is a both-locations
  monster and has moved to `wilderness_variant_monsters.json`.
  `Green dragon (Level 88)`'s ONLY location, per the same table, is the
  Wilderness Slayer Cave — the reverse of what was first assumed — so it
  moved here instead.
- `Bandit (Level 22)` / `(Level 130)` were previously excluded on the
  assumption of a same-named Desert Bandit Camp population; the Bandit
  wiki page's own location table shows both levels ONLY at the Wilderness
  Bandit Camp, with no non-Wilderness location documented at all. Both are
  included below.

## Included

Every name below was copied verbatim from the bundled `monsters.min.json.gz`
(`WildernessMonsterRepositoryTest` asserts each one actually resolves via
`MonsterRepository.byName`, so a typo here fails the build rather than
silently doing nothing):

**Bosses** — the Wilderness bosses reported as under-modelled at exactly
this class of weapon: Callisto/Artio, Venenatis/Spindel, Vet'ion/Calvar'ion
(both Normal and Enraged forms), Chaos Elemental, Chaos Fanatic, Crazy
archaeologist, Scorpia (+ its guardian and offspring). King Black Dragon is
NOT here — see the "Confirmed to have NO Wilderness location at all" section
below; it was a P1 finding caught after this pass first shipped.

**Revenants** — the other common Wilderness target for these exact weapons,
since Craw's bow/Viggora's chainmace/Thammaron's sceptre are themselves
obtained from Revenants: cyclops, dark beast, demon, dragon, goblin,
hellhound, hobgoblin, imp, knight, maledictus, ork, pyrefiend.

**Ordinary Wilderness-exclusive combat NPCs**, generated from
`Category:Wilderness slayer drop table monsters` plus a hand-verified
supplement for non-slayer-assignable bosses, then confirmed monster-by-
monster against the wiki's own `{{LocLine}}` location data (see the
companion README for the pipeline): Lava dragon, Elder Chaos druid,
Mammoth, Earth warrior (+ its champion), Green dragon (Level 88), Black
dragon (Level 247), the two Bandit Camp levels (57/74) plus the two
previously-miscalled Bandit levels (22/130), Bandit champion, Guard Bandit,
both Rogues' Castle levels (15/135), both Dark Warriors' Fortress levels
(8/145), Ankou (Level 98), Black demon (Level 188), Hellhound (Level 136),
Black Heather, Donny the lad, Speedy Keith, and the Wilderness-only Zombie
pirate levels (22/28/34) — all Bandit Camp/Wilderness-fortress-adjacent
named NPCs.

**Explicitly location-tagged in the bundled data** (the display name
itself disambiguates from a same-named non-Wilderness sibling): every
`(Wilderness Slayer Cave)`-tagged entry (Abyssal demon, Dust devil, Greater
Nechryael, Ice giant x3, Lesser demon (Level 94), Greater demon (Level
104)), `Ent (Wilderness)`, every `Zombie (Wilderness) (Level 18/24, N)`,
every `Skeleton (Wilderness Agility Course) (N)`.

## Confirmed to have NO Wilderness location at all (excluded from both files)

- `King Black Dragon` — the generation pass mis-classified this by wiki
  category membership: KBD's *entrance* (western Lava Maze Dungeon ladder)
  is inside level 42 Wilderness, but its own "King Black Dragon Lair" wiki
  page is explicit that the fight itself is not: "however the lair itself
  is not the Wilderness"; "The lair itself isn't in the Wilderness, but
  players are in the Wilderness until they pull the lever"; "As the lair
  itself is not considered the Wilderness, players can use any means of
  teleportation to leave." Caught as a P1 finding after this file first
  shipped — see `WildernessMonsterRepositoryTest#kingBlackDragonLair_isNotCurated`.
- `Black dragon (Echo)` — an Echo-boss instanced variant, not a field spawn.
- `Chaos druid warrior` — confirmed Yanille Agility Dungeon / Slepe church
  roof only.
- `Reanimated chaos druid` — a Necromancy-summoned copy, location-independent.
- `Dark Ankou` — a Legends' Quest unique, non-Wilderness.
- Every untagged non-`(Wilderness Slayer Cave)` Ice giant/Lesser demon/Dust
  devil/Abyssal demon/Greater demon variant not otherwise listed as a
  both-locations entry in `wilderness_variant_monsters.json`.

See `wilderness_variant_monsters.json.README.md`'s own exclusion list for
the full accounting of every candidate the generation pass considered and
where each one landed (exclusive here / both-locations there / no
Wilderness location at all), plus the honestly-disclosed residual gap
(species the automated pass could not classify with confidence).

## Regeneration

Generated, not hand-curated — see `wilderness_variant_monsters.json.README.md`
for the full pipeline (the same run produces both this file and that one).
To add a name found some other way: verify its ONLY location(s) against the
OSRS Wiki's own `{{LocLine}}` data (not the bundled data, which has no
location field, and not a skim of the prose, which is how the two
corrections above happened), add a test asserting the name resolves via
`MonsterRepository.byName`, and add it here only if EVERY location is
Wilderness (otherwise it belongs in the variant file instead).
