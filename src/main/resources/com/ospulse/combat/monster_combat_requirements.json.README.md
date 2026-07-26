# monster_combat_requirements.json — provenance & curation

Hand-curated (NOT derived from the cache or any wiki dump) list of monsters whose
combat style or weapon choice is *restricted by a mechanic* rather than merely
discouraged by defence bonuses — cases the DPS optimiser would otherwise get
actively wrong.

Sibling of `monster_gear_overrides.json`, which covers the different question of
"a specific item is needed for a mechanics reason" (mirror shield, nose peg).
A **defensive** requirement belongs there, not here.

## Shape

```json
{
  "requirements": [
    {
      "monsters": ["Zulrah"],
      "type": "WEAPON_GATE",
      "allowedStyles": ["RANGED", "MAGIC"],
      "allowedItemIds": [3190, 3192],
      "allowedAmmoIds": [],
      "note": "Zulrah sits across the water — only a halberd has the reach."
    }
  ]
}
```

- `monsters`: display name(s) this applies to. Matching is **exact first, then
  base name** — see `MonsterCombatRequirementRepository#forMonster`, which tries
  the full lowercased name and only then strips one trailing parenthetical via
  `MonsterNameKey.baseName`. So `"Zulrah"` covers `"Zulrah (Magma)"`,
  `"(Serpentine)"` and `"(Tanzanite)"` at once, while
  `"Tumeken's Warden (Active)"` binds to **that phase only** and leaves
  `"(Damaged)"`, `"(Enraged)"` and the rest ungated. Use an exact phase name
  whenever a mechanic is phase-scoped.
- `type`:
  - `WEAPON_GATE` — only the listed styles/items can damage this monster at all.
  - `FINISHER` — an item is needed to land the killing blow (note only; the
    optimiser does not force it).
- `allowedStyles`: `STAB` / `SLASH` / `CRUSH` / `RANGED` / `MAGIC`. Any style
  listed here is permitted outright.
- `allowedItemIds`: specific weapons permitted **regardless of style** — the
  exception mechanism (a halberd at Zulrah). Verified against
  `equipment_index.min.json`, never guessed from the wiki.
- `allowedAmmoIds`: for ranged gates such as broad ammunition.
- `note`: one short sentence, shown verbatim in the panel to explain why a style
  is greyed out.

A gate must permit *something* — an entry with no styles and no items would prune
every candidate. The data test enforces this.

## Adding an entry

1. Confirm the monster name against `monsters.min.json.gz` (`MonsterRepository`).
   Do not assume a naming pattern — `The Hueycoatl` splits into `(Normal)`,
   `(Body)`, `(Shielded)`, `(Tail)` and `(Tail (broken))`, and Verzik has
   separate Entry/Normal/Hard mode phase names.
2. Look ids up in `equipment_index.min.json` (or `EquipmentIndexRepository.idForName`).
3. Add the object. `MonsterCombatRequirementDataTest` validates every name and id;
   a typo silently disables a gate rather than failing loudly, which is exactly
   what that test exists to catch.

No regeneration script — this file is hand-maintained curated data.

## ⚠ Defence bonuses do NOT encode immunity

**Dusk is completely immune to ranged and magic, yet its bundled defence bonuses
read a flat `dmagic: 0, drange: 0`.** Tekton, immune to ranged, is the same. The
bonus fields carry *graduated resistance* only.

So "the DPS data already steers away from that style" is **never** a valid reason
to skip a gate. Conversely, a monster that is merely *resistant* should NOT get a
gate — encoding graduated resistance as a hard block is wrong in the other
direction.

## Checked and deliberately NOT gated

Swept all 2820 bundled monster names against the wiki. These were confirmed to
need no entry — recorded so they are not re-investigated:

| Monster(s) | Why not |
|---|---|
| Dagannoth Rex / Prime / Supreme | No immunity — extreme graduated defence (255-550), already in the bonus data |
| Kalphite Queen (both phases) | "Improved defence, not full immunity"; the two phases are already separate records |
| Vet'ion / Calvar'ion | Crush-preferring resistance, already a clean bonus differential |
| Great Olm (head / claws) | Real 66% off-style mitigation, but the per-part bonuses already carry a 4x differential that steers correctly |
| Wallasalki | "Use ranged" is a recommendation from `drange -10` vs `dmagic 250` |
| Akkha | Rotates protection prayers through all three styles symmetrically — no style is favoured |
| Abyssal Sire, Cerberus, Zilyana, Graardor, K'ril, Skotizo, Vorkath, Nechryael, Fire giant, Bloodveld, Smoke devils, Phantom Muspah, Nex, Duke Sucellus, Vardorvis, Whisperer, Sol Heredit, Scurrius, Araxxor, Amoxliatl, Yama, Kephri, Zebak, Ba-Ba | No style immunity or hard weapon requirement; ordinary resistances or non-combat-style mechanics only |
| Skeletal / Fossil Island wyverns | The elemental/mind/dragonfire shield is a **defensive** requirement — belongs in `monster_gear_overrides.json` |
| Aberrant spectres | Nose peg is a gear override, not a style gate |

Aerial-Fishing "krakens" (`Armoured`, `Pygmy`, `Spined`, `Vampyre`, `Veiled`) are
a different family fought on land and are correctly untouched by the `Kraken` and
`Cave kraken` entries — lookup is keyed, not substring.
