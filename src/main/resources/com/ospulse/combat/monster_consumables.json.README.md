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
      "note": "Vorkath's dragonfire is stronger than a normal dragon's: a shield only gives full protection when paired with a super antifire potion, and two-handed setups can reduce the damage but never fully block it, even with Protect from Magic and a super antifire potion. His venomous dragonfire, part of the normal attack rotation, can poison you — bring antivenom(+).",
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
lava/frost/reanimated dragons plus the metal-dragon tier (dragonfire).
Deliberately nothing speculative beyond these — the dataset grows by request
and by verification, matching how `monster_gear_overrides.json` was built.
Baby dragons were originally included in the chromatic/brutal entry by name
pattern but were removed in a later correction (see below) once the wiki
confirmed they do not breathe fire at all.

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

## Corrections (round 3) — dragonfire accuracy fixes from a bot review, verified against the OSRS Wiki

A PR review raised three factual-accuracy findings against the round-1
dragonfire entries. All three were checked directly against the OSRS Wiki's
`Dragonfire` article (`Protection` section, `Damage reduction` tables) rather
than trusting the reviewer, the original note, or recall of the game — the
tables give exact max-hit numbers per combination of Anti-dragon shield /
Protect from Magic / (super) antifire potion, cross-checked against the
in-table Mod Ash Twitter citations (e.g. "the max hit of that attack is 80
... The super antifire potion would reduce it by 20" for Vorkath, which the
table's own numbers reproduce exactly: 80 → 60).

- **Vorkath — real finding, note rewritten.** The Wiki's damage-reduction
  table shows Vorkath is the *only* row in the Chromatic/Vorkath/KBD/Elvarg
  table where shield + Protect from Magic + an ordinary antifire potion
  still leaves a max hit of 10 (not 0) — full (0 max hit) protection needs
  a super antifire potion paired with a shield (Anti-dragon shield,
  Dragonfire shield, or Dragonfire ward); Protect from Magic doesn't change
  that outcome once shield + super antifire are both present. With no
  shield (two-handed), no combination in the table reaches 0 — the best is
  Protect from Magic + super antifire potion, which still caps the max hit
  at 10. The old note's claim that "Protect from Magic plus an antifire
  potion covers it, no shield needed" was wrong (that combo's own table row
  shows a 20 max hit); the note now says a shield needs the super antifire
  potion for full protection, and that a shieldless setup can only reduce,
  never fully block, the damage.
- **Baby dragons — real finding, names removed.** The Wiki pages for each
  baby dragon colour state directly that they do not breathe dragonfire:
  Baby red dragon — "They do not attack with dragonfire"; Baby blue/green
  dragon — "Unlike their adult forms, they do not breathe fire"; Baby black
  dragon — "Unlike their adult form, they are too young to breathe flames,
  so no anti-dragon shield or other protection from dragonbreath is needed
  to fight them." `"Baby red dragon"`, `"Baby blue dragon"`, `"Baby green
  dragon"`, and `"Baby black dragon"` were removed from the chromatic/
  brutal-dragon entry's `monsters` array (the adult and brutal tiers were
  left untouched — the note is still correct for them, per the same Wiki
  table). `MonsterConsumablesRepositoryTest#babyDragons_doNotResolveTheDragonfireReminder`
  guards this using the exact bundled dataset names from
  `monsters.min.json.gz` (e.g. `"Baby red dragon (1)"`).
- **Metal dragons — real finding, `equipmentItemIds` and note both corrected.**
  The Wiki has a *separate* metallic-dragon damage-reduction table (metal
  dragons, Drakes, and Galvek are explicitly "unaffected by Protect from
  Magic"). That table's own row for "Anti-dragon shield + Antifire
  potion(4)" shows a max hit of 0 for metallic dragons — a fully-protective,
  inexpensive combo, exactly as the reviewer said. This reverses the earlier
  round-1 caution that deliberately omitted the anti-dragon shield id
  (`1540`) pending confirmation: `1540` and its `(nz)` variant `11710` are
  now restored to `equipmentItemIds` (both verified present in
  `equipment_index.min.json`), and the note no longer implies the upgraded
  dragonfire shield/ward or super antifire potion is mandatory.

## Correction (round 4) — Vorkath's venom was attributed to the wrong attack

A second review round on the round-3 Vorkath rewrite flagged that the note's
"He also poisons you during the acid phase" line misattributed the venom.
Checked against the OSRS Wiki `Vorkath/Strategies` article (raw wikitext via
the wiki API, `action=parse&page=Vorkath/Strategies`):

- **What the acid pools actually do**: "Standing on or running over an acid
  pool will deal up to 10 damage, which Vorkath will
  [[life leech|leech]] as health." — contact damage plus a Vorkath heal.
  Nothing in the Rapid Fire/acid-pool attack table row or its surrounding
  text mentions poison or venom.
- **Where the venom actually comes from**: "Vorkath is capable of
  [[Venom|envenoming]] players with its venom dragonfire; as such, bring
  appropriate venom protection." — and the attack table itself: "Venomous
  Dragonfire || Inflicts [[venom]] if not immune. Dragonfire protection does
  negate damage inflicted by this attack, but does not protect against the
  infliction of venom." The `Vorkath` article's Fight overview confirms this
  is one of Vorkath's three ordinary dragonfire types cycled through every
  six regular attacks ("a standard dragonfire, a venomous dragonfire, and a
  prayer-disabling dragonfire") — part of the normal rotation, not a
  phase-specific mechanic tied to the acid special attack.

Reviewer's finding was REAL. The antivenom recommendation itself was never
in question — only the attribution — so the note now credits the venomous
dragonfire (normal rotation) instead of "the acid phase," and no longer
implies the acid pools poison you.

## Correction (round 5) — Vorkath, two-handed setups

The round-3 wording said a two-handed setup "can reduce the damage but never fully block it".
Accurate but misleading in effect: it reads as a warning without saying how small the residual is,
and a player would reasonably infer their bowfa setup is unsafe.

Per the [Dragonfire](https://oldschool.runescape.wiki/w/Dragonfire) damage-reduction table, Vorkath's
dragonfire maxima are: **80** unprotected, **60** with super antifire alone, **10** with Protect from
Magic + super antifire, and **0** with any dragonfire-protection shield + super antifire. The green
and purple breaths take a further **-5** (Mod Ash, 27 Dec 2020), so the venomous breath caps at 5 for
a two-handed setup.

A max hit of 10 is what the overwhelming majority of Vorkath players accept in order to use a
two-handed weapon. The note now states both working options with the real numbers and lets the
player choose, rather than implying only the shield route is viable.

Note also (footnote in the same table): **dragonfire protection does not defend against Vorkath's
one-hit fireball at all.** No potion or shield choice changes that, so it is deliberately left out of
a *consumables* reminder - it is a movement problem, not a supply one.

