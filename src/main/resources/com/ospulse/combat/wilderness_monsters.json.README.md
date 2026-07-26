# wilderness_monsters.json — provenance & regeneration

Hand-curated (NOT derived from the cache or any wiki dump) list of monster
display names that are fought in the OSRS Wilderness — the ONE new *input*
the six §9 mechanics need that the engine does not otherwise have. The
bundled monster snapshot (`monsters.min.json.gz`, see its own README) has no
location field at all, so a monster's wilderness status cannot be derived
from anything already bundled; it has to be curated, exactly like
`monster_gear_overrides.json`/`monster_combat_requirements.json` already are.

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

## Why these names specifically

Every name below was copied verbatim from the bundled `monsters.min.json.gz`
(`WildernessMonsterRepositoryTest` asserts each one actually resolves via
`MonsterRepository.byName`, so a typo here fails the build rather than
silently doing nothing):

- The Wilderness bosses reported as under-modelled at exactly this class of
  weapon: Callisto/Artio, Venenatis/Spindel, Vet'ion/Calvar'ion (both Normal
  and Enraged forms), Chaos Elemental, Chaos Fanatic, Crazy archaeologist,
  Scorpia, King Black Dragon.
- Every Revenant creature (the other common Wilderness target for these
  exact weapons, since Craw's bow/Viggora's chainmace/Thammaron's sceptre are
  themselves obtained from Revenants): cyclops, dark beast, demon, dragon,
  goblin, hellhound, hobgoblin, imp, knight, maledictus, ork, pyrefiend.

Deliberately NOT included: minion/pet entries (e.g. the Skeleton Hellhound
summons, Callisto's cub) and non-combat Wilderness NPCs — the DPS calculator
only needs this list for monsters a player would actually select as a DPS
target.

## Regeneration

No automated regeneration — this is a small, stable, hand-curated list.
Extend it the same way as any other curated dataset here: copy the exact
name from `monsters.min.json.gz`, add a test asserting the name resolves via
`MonsterRepository.byName`, and add it to the array above.
