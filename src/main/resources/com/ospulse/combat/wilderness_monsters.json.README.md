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

## Honest scope: this is NOT "every Wilderness NPC"

`RevenantWeapon`'s javadoc describes the weapons' passive as "+50% accuracy
and damage vs any NPC in the Wilderness" because that is the wiki's own
wording for the mechanic — but `isWilderness` cannot honestly claim to cover
"any" Wilderness NPC, and does not try to. It covers:

1. Every boss/miniboss reported as under-modelled at this class of weapon.
2. Every Revenant creature.
3. Ordinary Wilderness combat NPCs whose bundled DISPLAY NAME either (a) is
   unique to the Wilderness, or (b) is explicitly location-tagged as a
   Wilderness spot in the bundled data (e.g. "(Wilderness Slayer Cave)").

A monster whose bundled name is shared with a non-Wilderness location (the
common case: many OSRS monsters spawn in several places, and this engine's
`Monster` has no location field to disambiguate which specific spawn a
player picked) is a **false-positive risk if wrongly included** — it would
overstate DPS and could send someone into the Wilderness expecting a damage
boost they will not get. A monster wrongly OMITTED only under-sells the
weapon. Given that asymmetry, every ambiguous name below was resolved by
excluding it, not by guessing in the set's favour — see "Deliberately
excluded" for the specific calls and the wiki evidence behind each one.

**If more Wilderness NPCs need covering later**, the fix is to find (or
confirm the absence of) a location-disambiguating detail — the bundled
data's `size`/`attributes`/combat stats occasionally make one spawn
distinguishable from another even when the display name doesn't — not to
loosen this file's matching to a name/substring guess.

## Included, and why

Every name below was copied verbatim from the bundled `monsters.min.json.gz`
(`WildernessMonsterRepositoryTest` asserts each one actually resolves via
`MonsterRepository.byName`, so a typo here fails the build rather than
silently doing nothing):

**Bosses** — the Wilderness bosses reported as under-modelled at exactly
this class of weapon: Callisto/Artio, Venenatis/Spindel, Vet'ion/Calvar'ion
(both Normal and Enraged forms), Chaos Elemental, Chaos Fanatic, Crazy
archaeologist, Scorpia, King Black Dragon.

**Revenants** — the other common Wilderness target for these exact weapons,
since Craw's bow/Viggora's chainmace/Thammaron's sceptre are themselves
obtained from Revenants: cyclops, dark beast, demon, dragon, goblin,
hellhound, hobgoblin, imp, knight, maledictus, ork, pyrefiend.

**Wilderness-exclusive (single confirmed location, verified against the OSRS
Wiki 2026-07-27):**
- `Lava dragon` — "Lava Dragon Isle... the only place where lava dragons
  spawn" (OSRS Wiki, Lava Dragon Isle).
- `Elder Chaos druid` — a single named NPC "located outside the Chaos
  Temple in level 38 Wilderness" (OSRS Wiki, Elder Chaos druid (NPC)). Not
  to be confused with the plain "Chaos druid" (excluded below — genuinely a
  different, multi-location monster).
- `Mammoth (Normal)` — "Killing a Mammoth is a task in the Easy **Wilderness**
  Diary"; both named habitats (south-east of Ferox Enclave, west of the
  Chaos Temple) are Wilderness (OSRS Wiki, Mammoth).
- `Green dragon (Level 79)` — the OSRS Wiki's own Locations table lists
  every Level 79 spawn point (four field spawns plus the Wilderness Slayer
  Cave) as Wilderness; the Wiki explicitly separates this from the Level 88
  variant (see excluded list) rather than leaving it ambiguous.
- `Black dragon (Level 247)` — the Wiki's Locations table lists exactly ONE
  spawn for Level 247: the Wilderness Slayer Cave. (Level 227 spawns in
  seven places, six of them non-Wilderness — excluded below.)
- Every `(Wilderness Slayer Cave)`-tagged bundled entry: `Abyssal demon`,
  `Dust devil`, `Greater Nechryael`, `Ice giant` (all three numbered
  spawns), `Lesser demon (Level 94 (Wilderness Slayer Cave))`, `Greater
  demon (Level 104 (Wilderness Slayer Cave))` — the bundled display name
  itself disambiguates these from their same-named counterparts elsewhere
  (e.g. plain `Lesser demon (Level 94)` with no cave tag is a DIFFERENT,
  non-Wilderness spawn and is excluded).
- `Ent (Wilderness)` and every `Zombie (Wilderness) (Level 18/24, N)` /
  `Skeleton (Wilderness Agility Course) (N)` entry — the bundled name
  itself is location-tagged.

**Overwhelmingly Wilderness, minor documented exception (false-positive risk
accepted and disclosed, per the "overwhelmingly Wilderness" branch of the
fail-safe rule):**
- `Earth warrior` — "exclusively found in the Wilderness, except for rare
  spawns in the Chaos Tunnels" (OSRS Wiki, Earth warrior). The Chaos
  Tunnels spawn is a real, if minor, false-positive surface for this entry.
- `Bandit (Bandit Camp) (Level 57)` / `(Level 74)` — the bundled name is
  explicitly tagged to the Wilderness Bandit Camp (levels 17-24 Wilderness),
  distinct from the untagged `Bandit (Level 22)`/`(Level 130)` (excluded —
  a same-named Desert Bandit Camp also exists and the bundled data does not
  disambiguate those two untagged entries from it).
- `Rogue (Level 15)` / `(Level 135)` — both are the Rogues' Castle garrison
  (deep Wilderness, levels 52-56); no other named location for the generic
  "Rogue" NPC was found.

## Deliberately excluded (ambiguous or majority non-Wilderness — verified, not assumed)

- **`Green dragon (Level 88)`** — per the Wiki's own Locations table, its
  ONLY location is Corsair Cove Dungeon / Myths' Guild (basement),
  requiring Dragon Slayer II. Never Wilderness. Including it would be a
  pure false positive, not a judgment call.
- **`Black dragon (Level 227)`** — spawns in seven locations (Charred
  Dungeon, Corsair Cove/Myths' Guild, Evil Chicken's Lair, Lava Maze
  Dungeon, Mynydd, Taverley Dungeon x2); only Lava Maze Dungeon is
  Wilderness. Majority non-Wilderness — excluded under the fail-safe rule.
- **`Black dragon (Echo)`** — an Echo-boss instanced variant, not a
  Wilderness field spawn.
- **`Chaos druid`, `Chaos druid warrior`, `Reanimated chaos druid`** — the
  plain "Chaos druid" spawns in Edgeville Dungeon (partially Wilderness, low
  level), Taverley Dungeon, Yanille Agility Dungeon, and the Chaos Druid
  Tower — majority non-Wilderness, and the bundled name doesn't disambiguate
  which spawn. "Chaos druid warrior" is confirmed Yanille Agility Dungeon or
  the Slepe church roof — zero Wilderness locations. "Reanimated" is a
  Necromancy-summoned copy, location-independent.
- **`Hill Giant` (every bracketed/cosmetic variant)** — the dominant,
  heavily-populated spawn is the (non-Wilderness) Edgeville Dungeon; only
  two unnamed Hill Giants exist in Wilderness levels 17-18, and the bundled
  data has no tag distinguishing them from the dungeon ones.
- **`Ankou` (every level) and `Dark Ankou`** — spawns in the Stronghold
  Slayer Cave, Catacombs of Kourend, Sepulcher of Death and Deepfin Mine
  (all non-Wilderness) as well as the Wilderness Slayer Cave and Forgotten
  Cemetery; majority non-Wilderness with no bundled tag to separate them.
  "Dark Ankou" is a Legends' Quest unique, non-Wilderness.
- **`Greater demon (Level 92/100/101/113)`, `(The Scar)`, `Scarred greater
  demon`** — Level 92 alone spawns in at least four places (Wilderness
  Demonic Ruins, Brimhaven Dungeon, Chasm of Fire, Karuulm Slayer Dungeon);
  Levels 100/101/113 are the Catacombs of Kourend. Only the explicitly
  `(Wilderness Slayer Cave)`-tagged Level 104 entry is included (see above).
  "The Scar" is a Desert Treasure II area, non-Wilderness.
- **`Black demon` (every level/variant)** — Taverley Dungeon (the most
  heavily populated spawn) and Chasm of Fire dominate; a Wilderness Slayer
  Cave spawn exists but the bundled data has no location-tagged variant to
  isolate it from the others, unlike Greater demon's Level 104 case. NMZ,
  The Grand Tree and The Scar variants are unambiguously non-Wilderness.
- **`Bandit (Level 22)`, `(Level 130)`** (untagged) — a same-named Desert
  Bandit Camp exists and the bundled data doesn't disambiguate; only the
  explicitly `(Bandit Camp)`-tagged ids are included.
- **`Ice giant (1)/(2)/(3)`, `(Asgarnia Ice Dungeon 1/2)`, `(Varlamore
  1/2)`** — all non-Wilderness; only the `(Wilderness Slayer Cave N)` tagged
  ids are included.
- **`Lesser demon (Level 82)`, `(Level 87)`, `(Level 94)`** (untagged),
  `(Melzar's Maze)`, every `(The Scar)` variant, every `Scarred lesser
  demon` — non-Wilderness; only the `(Level 94 (Wilderness Slayer Cave))`
  tagged id is included.
- **`Dust devil (Catacombs of Kourend)`, `(Smoke Dungeon)`** — non-Wilderness;
  only the `(Wilderness Slayer Cave)` tagged id is included.
- **`Abyssal demon (Catacombs of Kourend)`, `(Standard)`, `Greater abyssal
  demon`** — non-Wilderness; only the `(Wilderness Slayer Cave)` tagged id
  is included.

Also NOT included on general principle (unchanged from the original
narrower set): minion/pet entries (e.g. the Skeleton Hellhound summons,
Callisto's cub) and non-combat Wilderness NPCs — the DPS calculator only
needs this list for monsters a player would actually select as a DPS
target.

## Regeneration

No automated regeneration — this is a hand-curated list. Extend it the same
way as any other curated dataset here: copy the exact name from
`monsters.min.json.gz`, verify the monster's real-game location(s) against
the OSRS Wiki (not just the bundled data, which has no location field),
apply the fail-safe rule above for any name shared with a non-Wilderness
spawn, add a test asserting the name resolves via `MonsterRepository.byName`,
and add it to the array above.
