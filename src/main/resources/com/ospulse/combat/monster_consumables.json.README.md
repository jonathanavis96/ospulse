# monster_consumables.json — provenance & curation

Hand-curated (NOT derived from the cache or any wiki dump) "don't forget"
reminders: consumables/gear a target genuinely demands (venom, dragonfire,
etc.) that the DPS optimiser cannot infer and would never surface on its own.

Sibling of `monster_gear_overrides.json` (a specific EQUIPPED item needed for
a mechanics reason) and `monster_combat_requirements.json` (a weapon/style
gate or damage effect) — this file covers a different question again: "what
should the player bring/wear that isn't about DPS ranking at all."

**Why this can't be derived from the bundled cache**: the monster schema's
only relevant field is the `attributes` array, and its `DRAGON` tag fires on
94 monsters including Hydra, Alchemical Hydra, Drake, Wyrm, the Fossil Island
wyverns, Great Olm and The Hueycoatl — none of which breathe dragonfire (the
Alchemical Hydra ships its own entry below, but for its poison, not its
`DRAGON` tag). There is no poison/venom field of any kind. A rule keyed on
`attributes` would be wrong on both false positives and the total absence of
a poison signal, so every entry here is hand-curated by exact/base monster
name, exactly like its two siblings.

## Shape
```json
{
  "reminders": [
    {
      "monsters": ["Zulrah"],
      "note": "Zulrah poisons you on every hit — bring antivenom(+). A ring of suffering (ri) or ring of recoil chips some retaliation damage back."
    },
    {
      "monsters": ["Vorkath"],
      "note": "Vorkath breathes dragonfire (Protect from Magic plus an antifire potion covers it, no shield needed) and poisons you during the acid phase — bring antivenom(+). A dragonfire shield/ward or anti-dragon shield still helps if you'd rather not rely on potion doses.",
      "equipmentItemIds": [1540, 11710, 11283, 11284, 22002, 22003]
    }
  ]
}
```

- `monsters`: display name(s) this reminder applies to. Matching is **exact
  first, then base name** — see `MonsterConsumablesRepository#forMonster`,
  which mirrors `MonsterCombatRequirementRepository`'s convention (NOT
  `monster_gear_overrides.json`'s exact-only matching): `"Zulrah"` alone
  resolves `"Zulrah (Magma)"`/`"(Serpentine)"`/`"(Tanzanite)"` for free via
  `MonsterNameKey.baseName`, while an entry that must NOT spread to every
  phase (see Galvek, excluded below) keys on the exact dataset name instead.
- `note`: the payload. Shown verbatim (one advisory line) below the
  potion/prayer/slayer toggles when the selected target has an entry.
  Inventory consumables (antivenom, antifire potions, antipoison) are named
  in this prose only — see below for why.
- `equipmentItemIds` (optional array of ints): **only** for items that are
  genuinely equipment and therefore verifiable against
  `equipment_index.min.json` (`EquipmentIndexRepository.entryFor`) — a ring,
  a shield. Never a potion/consumable id. Omit the field entirely when a
  reminder is pure prose (every entry has at least a `note`; not every entry
  needs ids).

## Why ids are optional and text is the payload

Most of what a "don't forget" reminder needs to say is an INVENTORY
consumable (antivenom, antifire potion, antipoison) — `equipment_index.min.json`
indexes equippable items only, so these ids cannot be verified through the
one channel this codebase trusts, and guessing them from the wiki is exactly
what every curated dataset here forbids. So the note carries the full
information in prose, and `equipmentItemIds` is populated only when an id
actually clears verification.

## Adding an entry

1. Confirm the monster name against `monsters.min.json.gz` (`MonsterRepository`)
   — do not assume a naming pattern. Several dragon families have nested
   parentheses in one specific variant name (e.g. `"Steel dragon (Level 246
   (Task only))"`) that `MonsterNameKey.baseName`'s single-non-nested-group
   regex cannot strip, so that exact string must be listed alongside the
   plain `"Steel dragon"` base key or it silently falls through unmatched.
   `"Abyssal Sire (Phase 3 (stage 1))"` / `"(Phase 3 (stage 2))"` are the
   same trap the other way round: `"Abyssal Sire (Phase 1)"` and
   `"(Phase 2)"` DO strip to `"abyssal sire"` and would resolve via base-name
   fallback, but the two `(stage N)` phases do not, so all four phase names
   are listed explicitly rather than relying on any fallback at all.
2. Look any equipment ids up in `equipment_index.min.json` (or
   `EquipmentIndexRepository.idForName`) — never guess from the wiki.
   `MonsterConsumablesDataTest` enforces this over the whole file.
3. No regeneration script — this file is hand-maintained curated data.

## Ring of suffering — id deliberately omitted

`equipment_index.min.json` carries **two** ids under the identical display
name "Ring of suffering" (19550, 20655) and two under "Ring of suffering (i)"
(19710, 20657) — the indexed data doesn't disambiguate charge state in the
name, and this repo could not resolve from its own data which one is the
recoil-charged variant the Zulrah advice depends on. Per the standing rule
("never guess an id"), the id is omitted; the ring is named in the note's
prose only. Ring of recoil is absent from the equipment index entirely (no
row in `equipment_stats.min.json`), so it too is prose-only.

## Checked and deliberately NOT added (record so these are not re-investigated)

- **Galvek (Air) / (Earth) / (Water)** — share the base name with
  `"Galvek (Fire)"`, but only the Fire phase breathes actual dragonfire; the
  other three use a different elemental breath. Not one of the three cases
  the reporter named, so not shipped speculatively — would need its own
  exact-phase entry (`"Galvek (Fire)"`) if added later, precisely BECAUSE
  base-name matching would otherwise wrongly spread the note to all four
  phases.
- **Hydra / Alchemical Hydra / Colossal Hydra, Drake / Guardian Drake,
  Wyrm / Wyrmling / Shadow Wyrm, Great Olm, The Hueycoatl** — all carry the
  `DRAGON` attribute but do not breathe dragonfire (poison/elemental phase
  attacks, or no breath attack at all). False positives a naive
  attribute-keyed rule would have caught; excluded by name-level curation.
- **Skeletal / Fossil Island wyverns (Ancient / Long-tailed / Spitting /
  Taloned / Skeletal)** — their icy breath needs an elemental/mind/dragonfire/
  ancient-wyvern SHIELD, a defensive-gear requirement that belongs in
  `monster_gear_overrides.json`, not this consumables-only file.
- **Cerberus antifire** — investigated and dropped: its only source was a
  single 2021 social-media post with no published magnitude (LOW
  confidence). Cerberus's own **antipoison** reminder ships below — this
  exclusion is for the antifire claim specifically, not the boss as a whole.

## Starter data (round 1) — the three cases the reporter actually named

Zulrah (venom), Vorkath (dragonfire + acid-phase venom), and chromatic/brutal/
baby/lava/frost/reanimated dragons plus the metal-dragon tier (dragonfire).
Deliberately nothing speculative beyond these — the dataset grows by request
and by verification, matching how `monster_gear_overrides.json` was built.

## Starter data (round 2) — poison/venom entries recovered from a truncated research file

The first pass's research file was truncated before it reached the poison/
venom table its own author's report described, which is why these were
invisible round 1 (Cerberus, K'ril, Nex, Abyssal Sire weren't even in the
file to be curated from) and why the Alchemical Hydra appeared only as a
dragonfire *exclusion* rather than also getting its own poison entry. Added
once the gap was found and the monster names + wording were re-verified
directly against `monsters.min.json.gz`:

- **Alchemical Hydra** (`(Electric)` / `(Extinguished)` / `(Fire)` /
  `(Serpentine)`) — its poison phase is venom-tier and escalates; plain
  antipoison only knocks it back to poison, it doesn't cure it. Bring
  antivenom+.
- **Abyssal Sire** (`(Phase 1)` / `(Phase 2)` / `(Phase 3 (stage 1))` /
  `(Phase 3 (stage 2))`) — poison fumes in phase 1 start at 8 damage and
  keep ticking. Bring antipoison. `"Tentacle (Abyssal Sire)"` is a
  different bundled monster and is deliberately NOT included here.
- **K'ril Tsutsaroth** — melee poisons from 16 damage and lands even through
  Protect from Melee. Bring antidote++ or a sanfew serum.
- **Nex** — her Smoke phase (Smoke Rush + smoke clouds) can poison. Bring
  antipoison or antidote++. `"Blood Reaver (Nex's chamber)"` is a different
  monster in the same encounter and must not match this entry — covered by
  a repository test.
- **Cerberus** — antipoison for the tunnel-spider walk in; Cerberus herself
  does not poison you. Worded deliberately to put the hazard on the
  approach, not the boss, because that is what the evidence supports.

All five are prose-only: every item involved (antivenom+, antipoison,
antidote++, sanfew serum) is an inventory consumable, so none has a
verifiable equipment id — `equipmentItemIds` is correctly absent from all
five, not an oversight.
