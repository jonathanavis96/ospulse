# wilderness_variant_monsters.json — provenance & regeneration

Both-locations monsters: fought BOTH in and out of the Wilderness (e.g.
Black dragon — Lava Maze Dungeon *and* several non-Wilderness dungeons).
Each entry pairs the real bundled monster name (`baseMonster`) with a
synthetic, selectable "(Wilderness)" display name (`displayName`) that
`MonsterRepository` synthesizes a twin `Monster` for at load time (see
`WildernessVariantMonsterRepository`/`Monster.lookupName()`). Selecting the
twin applies the revenant-weapon bonus; selecting the ordinary
`baseMonster` entry does not.

This is the director-mandated replacement for the original ("round 2")
rule of excluding anything ambiguous. Excluding a both-locations monster
entirely silently withheld a real, legitimate Wilderness DPS number (the
reported case: Black dragon at the Lava Maze Dungeon). Making the
Wilderness instance separately selectable removes the guesswork instead of
hiding it: the player says which one they mean.

## Shape
```json
{
  "variants": [
    {"baseMonster": "Black dragon (Level 227)", "displayName": "Black dragon (Wilderness)"}
  ]
}
```
- `baseMonster`: the exact, real bundled name (verified by
  `WildernessVariantMonsterRepositoryTest` against `monsters.min.json.gz`).
- `displayName`: the synthetic name shown to the player. Usually
  `<species> (Wilderness)` with any trailing `(Level N)` tag stripped (the
  director's own naming example: `Black dragon (Wilderness)`, not
  `Black dragon (Level 227) (Wilderness)`) — EXCEPT where a species has
  more than one both-locations level (e.g. Lesser demon 82 AND 94), in
  which case the level is kept in the display name to avoid two different
  synthetic twins colliding on the same name (`WildernessVariantMonsterRepositoryTest`
  asserts every `displayName` is unique and never collides with a real
  bundled name).

## Generation

**Enumeration source:** `list=categorymembers` over
`Category:Wilderness slayer drop table monsters` — 86 monster SPECIES pages
(a Slayer-task-monster category, so it does not include non-slayer-
assignable bosses like King Black Dragon; those are hand-verified
separately and already live in `wilderness_monsters.json`).

**Per-species location data:** each species page's raw wikitext is parsed
for every `{{LocLine ...}}` block's `levels` and `location` fields — the
same structured data source the interactive Wilderness/world maps use.

**The one hard lesson this pipeline is built around:** `leagueRegion` on a
`LocLine` is the *Leagues game-mode region* that unlocks the spawn, **not**
its real-world location — verified directly: Green dragon's Corsair
Cove/Myths' Guild spawn (definitely not the Wilderness) is tagged
`leagueRegion = Wilderness` anyway. The reliable signal is the free-text
`location` field, checked against a small, hand-verified list of
Wilderness-specific location/dungeon names.

A second, subtler bug found while building this: a naive non-greedy
`\{\{LocLine(.*?)\}\}` regex truncates at the first NESTED template's own
closing `}}` — e.g. `location = [[Rogues' Castle]] ({{FloorNumber|uk=0}})`
ends the "block" right after `{{FloorNumber|uk=0}}`, silently dropping
every field that comes after (Rogue's `levels = 15, 135` was lost this way,
misclassifying the level-135 Rogue). The generator instead balances nested
`{{`/`}}` pairs explicitly.

### The script (reproduce by running `generate.py <bundled_monsters.json>`,
where `bundled_monsters.json` is `monsters.min.json.gz` decompressed to
plain JSON)

```python
#!/usr/bin/env python3
"""Generate the Wilderness monster sets from the OSRS Wiki, intersected
against the bundled monsters.min.json.gz."""
import json
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "ospulse-wilderness-gen/1.0 (dev tooling)"

WILDERNESS_LOCATION_KEYWORDS = [
    "wilderness",
    "lava maze",
    "demonic ruins",
    "rogues' castle",
    "bandit camp (wilderness)",
    "chaos temple",
    "graveyard of shadows",
    "frozen waste plateau",
    "forgotten cemetery",
    "lava dragon isle",
    "mage arena",
    "scorpion pit",
    "king black dragon",
    "ferox enclave",
    "dark warriors' fortress",
    "bone yard",
    "edgeville dungeon (deep",  # the members-only, Wilderness-side deep section only
]


def api_get(params):
    params = dict(params)
    params.setdefault("format", "json")
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def category_members(category, limit=500):
    data = api_get({
        "action": "query", "list": "categorymembers",
        "cmtitle": "Category:" + category, "cmlimit": limit,
    })
    return [m["title"] for m in data["query"]["categorymembers"]]


def raw_wikitext(title):
    url = API.replace("api.php", "index.php") + "?" + urllib.parse.urlencode({
        "title": title, "action": "raw",
    })
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8", errors="replace")


def find_balanced_templates(wikitext, name):
    """Finds every {{name ... }} block, correctly balancing NESTED {{ }}
    braces (see the module docstring for why a naive regex breaks here)."""
    out = []
    marker = "{{" + name
    i = 0
    while True:
        start = wikitext.find(marker, i)
        if start == -1:
            break
        depth = 0
        j = start
        while j < len(wikitext):
            if wikitext.startswith("{{", j):
                depth += 1
                j += 2
                continue
            if wikitext.startswith("}}", j):
                depth -= 1
                j += 2
                if depth == 0:
                    break
                continue
            j += 1
        out.append(wikitext[start + 2 + len(name):j - 2])
        i = j
    return out


FIELD_RE = re.compile(r"\|\s*(\w+)\s*=\s*(.*?)(?=\n\s*\||\Z)", re.DOTALL)
WIKILINK_RE = re.compile(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]")


def plain_text(s):
    return WIKILINK_RE.sub(r"\1", s)


def is_wilderness_location(location_text):
    plain = plain_text(location_text).lower()
    return any(kw in plain for kw in WILDERNESS_LOCATION_KEYWORDS)


def parse_loclines(wikitext):
    out = []
    for block in find_balanced_templates(wikitext, "LocLine"):
        fields = {}
        for name, value in FIELD_RE.findall(block):
            fields[name.strip().lower()] = value.strip()
        out.append((fields.get("levels", ""), fields.get("location", "")))
    return out


def levels_from_str(levels_str):
    return [int(x) for x in re.findall(r"\d+", levels_str)]


def species_wilderness_levels(title):
    try:
        wt = raw_wikitext(title)
    except Exception as e:
        print(f"  ! fetch failed for {title}: {e}", file=sys.stderr)
        return set(), set(), False
    lines = parse_loclines(wt)
    wild, nonwild = set(), set()
    for levels_str, location in lines:
        lv = levels_from_str(levels_str)
        wilderness = is_wilderness_location(location)
        for l in lv:
            (wild if wilderness else nonwild).add(l)
        if not lv:
            (wild if wilderness else nonwild).add(0)
    return wild, nonwild, bool(lines)


def main():
    with open(sys.argv[1]) as f:
        bundled = json.load(f)
    bundled_names = [m["name"] for m in bundled]

    species = category_members("Wilderness slayer drop table monsters")
    print(f"{len(species)} species from the category", file=sys.stderr)

    exclusive, variants, no_wild, unmatched_species = [], [], [], []
    per_species = {}

    for sp in species:
        wild_lv, nonwild_lv, had_lines = species_wilderness_levels(sp)
        time.sleep(0.25)  # be polite to the API
        per_species[sp] = {"wild": sorted(wild_lv), "nonwild": sorted(nonwild_lv)}
        if not had_lines:
            unmatched_species.append(sp)
            continue
        candidates = [n for n in bundled_names if n.lower().startswith(sp.lower())]
        for name in candidates:
            nums = re.findall(r"\d+", name)
            if nums:
                entry_levels = set(int(x) for x in nums)
                is_wild = bool(entry_levels & wild_lv)
                is_nonwild = bool(entry_levels & nonwild_lv)
            else:
                # No level number in the bundled name (e.g. plain "Hill
                # Giant") - fall back to the species' AGGREGATE wild/
                # non-wild status.
                is_wild = bool(wild_lv)
                is_nonwild = bool(nonwild_lv)
            if not is_wild and not is_nonwild:
                continue
            if is_wild and is_nonwild:
                variants.append(name)
            elif is_wild:
                exclusive.append(name)
            else:
                no_wild.append(name)

    print(f"species with no parseable LocLine data: {unmatched_species}", file=sys.stderr)
    print(f"exclusive candidates: {len(set(exclusive))}", file=sys.stderr)
    print(f"variant candidates: {len(set(variants))}", file=sys.stderr)
    print(f"no-wilderness candidates: {len(set(no_wild))}", file=sys.stderr)

    json.dump(sorted(set(exclusive)), open("generated_exclusive.json", "w"), indent=2)
    json.dump(sorted(set(variants)), open("generated_variants.json", "w"), indent=2)
    json.dump(sorted(set(no_wild)), open("generated_no_wild.json", "w"), indent=2)
    json.dump(per_species, open("per_species_debug.json", "w"), indent=2)


if __name__ == "__main__":
    main()
```

### Post-processing override pass (`postprocess.py`)

The per-level number-matching heuristic above can conflate two DIFFERENT
populations that happen to share a combat level (e.g. a Braindeath Island
zombie pirate and a genuine Wilderness one both being level 22, or the
`{0}`-fallback sweeping an untagged sibling of an already Wilderness-cave-
tagged entry into "variant" even though that sibling's OWN name says it is
the "(Regular)"/"(Standard)" one). Where a bundled name already carries its
own location tag, that tag is authoritative and overrides the level-based
guess — a small, stable, hand-verified list of known Wilderness and known
non-Wilderness name tags, run as a final pass over the generator's raw
output:

```python
KNOWN_WILDERNESS_NAME_TAGS = [
    "(wilderness slayer cave", "(wilderness agility course", "(wilderness)",
]
KNOWN_NON_WILDERNESS_NAME_TAGS = [
    "(pollnivneach)", "(shayzien)", "(varlamore", "(harmony island)",
    "(braindeath island)", "(catacombs of kourend)", "(the scar)",
    "(standard)", "(nightmare zone)", "(melzar's maze)", "(construction)",
    "(task only", "(ruins of tapoyauik", "(asgarnia ice dungeon",
    "(smoke dungeon)", "(kharidian desert)", "(desert)", "(myths' guild",
    "(beta)", "(echo)",
]
# A name matching a WILDERNESS tag is forced into "exclusive" regardless of
# the level-heuristic's verdict; a name matching a NON-WILDERNESS tag is
# forced into "no_wild" regardless. See the repo's scratch history for the
# full post-processing script; the override list above is the part that
# matters for reproduction.
```

## What made it into the curated file, and the confidence bar

The automated pass (generator + overrides) produced far more raw "variant"
candidates than are in the shipped file — many from the untagged-sibling/
`{0}`-fallback pattern above, applied to species where I could not get a
clean, specific-level signal (as opposed to a broad species-aggregate
guess). Given a false positive here means a player can select a synthetic
"X (Wilderness)" target that does not actually correspond to a real
Wilderness fight, only the following were kept, each backed by either a
**clean per-level LocLine match** (a bundled name with its own digit that
intersects both the wild and non-wild level sets, not the `{0}` aggregate
fallback) or **direct prior research with a wiki citation**:

| baseMonster | displayName | Confidence basis |
|---|---|---|
| `Black dragon (Level 227)` | `Black dragon (Wilderness)` | Wiki's own Locations table: 7 spawns, 6 non-Wilderness + Lava Maze Dungeon. The director's own named example. |
| `Green dragon (Level 79)` | `Green dragon (Wilderness)` | Wiki's own Locations table: 4 Wilderness field spawns + the Corsair Cove/Myths' Guild spawn share this exact level (see the correction note in `wilderness_monsters.json.README.md`). |
| `Chaos druid` | `Chaos druid (Wilderness)` | Direct research: Edgeville Dungeon's Wilderness-adjacent section + Taverley/Yanille/Tower (non-Wilderness). |
| `Hill Giant` | `Hill Giant (Wilderness)` | Direct research: "two Hill Giants in level 17-18 Wilderness" alongside the dominant (non-Wilderness) Edgeville Dungeon population, same combat level (28) confirmed via the generator's own per-level data. |
| `Ankou (Level 86)` | `Ankou (Wilderness)` | Clean per-level LocLine match (86 appears in both the wild and non-wild sets; 98 is Wilderness-only and lives in the exclusive file instead). |
| `Black demon (Level 172)` | `Black demon (Wilderness)` | Clean per-level LocLine match (172 both; 188 Wilderness-only, exclusive file). |
| `Greater demon (Level 92)` | `Greater demon (Wilderness)` | Clean per-level LocLine match (92 both — Demonic Ruins plus several non-Wilderness dungeons; 104 is the already-tagged Wilderness Slayer Cave entry, exclusive file). |
| `Lesser demon (Level 82)` | `Lesser demon (Level 82) (Wilderness)` | Clean per-level LocLine match. Level kept in the display name because... |
| `Lesser demon (Level 94)` | `Lesser demon (Level 94) (Wilderness)` | ...both 82 AND 94 are both-locations levels for this species — stripping both to a bare "Lesser demon (Wilderness)" would collide. |
| `Hellhound (Level 122)` | `Hellhound (Wilderness)` | Clean per-level LocLine match (122 both; 136 Wilderness-only, exclusive file). |

## Verified 2026-07-27: 24 of the candidates confirmed and shipped

The candidates the original ("round 2") draft of this file listed as
"could NOT confidently classify" were re-investigated — not with the
generator's untagged/`{0}`-fallback aggregate signal that produced the
false-uncertainty in the first place, but with a direct per-page check
against the wiki's own `{{LocLine}}` data: for each species page, every
`{{LocLine}}` block's `location` field was parsed, the location resolved
to its own wiki page, and treated as Wilderness only if that page's
infobox states `location = Wilderness` or its intro text gives a
Wilderness level — checking first for an explicit negation. That
negation check is exactly what correctly rules out the King Black Dragon
Lair, which sits inside the Wilderness map area but is textually
confirmed non-Wilderness. The level was then mapped to a bundled variant
via the monster infobox's `versionN`/`combatN` field pairs, not via
level-number pattern matching against the bundled name.

Controls confirmed the method both ways: Hill Giant (already shipped,
known-Wilderness) was correctly re-detected, and King Black Dragon
(known non-Wilderness) was correctly NOT detected.

24 species/levels were confirmed by this method and are now shipped in
`wilderness_variant_monsters.json`:

- **Aviansie** — Levels 69, 71, 84, 94, 131, 137 (Wilderness God Wars Dungeon)
- **Spiritual mage** — Saradomin, Armadyl (Wilderness God Wars Dungeon)
- **Spiritual ranger** — Bandos, Zamorak, Saradomin, Armadyl (Wilderness God Wars Dungeon)
- **Spiritual warrior** — Zamorak, Armadyl, Saradomin, Bandos (Wilderness God Wars Dungeon)
- **Bloodveld (GWD)** — Wilderness God Wars Dungeon
- **Fire giant (Level 86)**, **Shadow spider** — Deep Wilderness Dungeon
- **Deadly red spider** — Lava Maze + Ruins (east)
- **Giant spider (Level 27)** — Lava Dragon Isle
- **Grizzly bear (Level 21)** — Dark Warriors' Fortress + Ferox Enclave
- **King Scorpion** — Lava Maze
- **Poison spider (Level 64)** — Demonic Ruins + Lava Maze Dungeon

## Still unresolved: 12 cases confirmed Wilderness, variant undetermined

These are NOT "probably fine, add them" — the same `{{LocLine}}` method
above confirms each species/level DOES have a Wilderness location, but
which specific bundled variant corresponds to that spawn could not be
determined mechanically (no clean wiki version data, or an ambiguous
match against the bundled name's own variants). Each needs a targeted,
manual check before it can be added:

| case | why unresolved |
|---|---|
| Aviansie L79, L97 | wiki has two same-level versions ("Level 79 (1)"/"(2)"), cannot tell which is the Wilderness GWD spawn |
| Spiritual mage L121 | two gods share level 121 (Zamorak, Bandos) |
| Moss giant L42 | wiki versions "Level 42" and "Level 42 (Varlamore)"; location is Wilderness Pond so Varlamore is almost certainly wrong, but not confirmed |
| Spider L1 | versions "Common" / "Underground Pass"; location is Wilderness / Ruins (west) |
| Black Knight L33 | wiki page has no version fields; bundled has 6 state/cosmetic variants |
| Ice warrior L57 | no wiki versions; bundled has 4 location variants |
| Ice spider L61 | no wiki versions; bundled has 3 location variants |
| Jelly L78 | no wiki versions; bundled has 9 colour/charge variants |
| Skeleton L22, L25 | no wiki versions; bundled has many location variants |
| Greater Nechryael L200 | already covered by `Greater Nechryael (Wilderness Slayer Cave)` in `wilderness_monsters.json` — deliberately NOT added here, listed only so it is not re-investigated |

If any of these is later resolved (a wiki edit adds distinguishing
version data, or manual disambiguation via drop tables/quest requirements
pins down the right variant), add it following the same pattern as the
24 above: `baseMonster` = the exact bundled name, `displayName` =
`<species/tag> (Wilderness)`, kept unique per
`WildernessVariantMonsterRepositoryTest`.

## Regeneration

Run `generate.py <bundled_monsters.json>` (with `monsters.min.json.gz`
decompressed to plain JSON), then apply the override pass described above
to `generated_variants.json`, then hand-verify every remaining
"low-confidence" candidate against the OSRS Wiki's own `{{LocLine}}` data
(not a skim of the prose — see the Green dragon/Bandit corrections above
for why) before adding it to `wilderness_variant_monsters.json`. Re-run
whenever `monsters.min.json.gz` is regenerated, since a bundled name change
could silently break a `baseMonster` match
(`WildernessVariantMonsterRepositoryTest` catches that).
