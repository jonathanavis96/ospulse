# monster_gear_overrides.json — provenance & regeneration

Hand-curated (NOT derived from the cache or any wiki dump) list of
monster-mechanic gear requirements: cases where a specific item matters for a
MECHANICS reason (special-attack mitigation, safespotting, immunity, etc.)
rather than raw DPS, so the DPS optimiser would never surface it on its own.

## Shape
```json
{
  "overrides": [
    {
      "monsters": ["Rune dragon", "Rune dragon (Construction)"],
      "slot": "BOOTS",
      "itemId": 7159,
      "itemName": "Insulated boots",
      "reason": "Halves the lightning special-attack damage from Rune dragons."
    }
  ]
}
```
- `monsters`: exact display name(s) this override applies to (matched
  case-insensitively against `Monster.name()` — a monster's several
  combat-instance variants are listed individually since matching is by name,
  not npc id).
- `slot`: `EquipmentInventorySlot` name (`HEAD`, `CAPE`, `AMULET`, `WEAPON`,
  `BODY`, `SHIELD`, `LEGS`, `GLOVES`, `BOOTS`, `RING`, `AMMO` — see
  `MonsterGearOverride.Slot`), not the raw ordinal, for readability.
- `itemId` / `itemName`: verified against this repo's own bundled
  `equipment_index.min.json` (cache-derived — see that file's README), never
  guessed from the wiki. This is the PRIMARY item — shown in the advisory note
  and what the optimiser force-includes.
- `reason`: one short sentence shown verbatim in the advisory note.
- `alternativeItemIds` (optional array of ints): other item ids that satisfy
  this requirement equally well as `itemId` — e.g. every Slayer helmet
  variant/recolour substitutes for a plain face-protection item like a
  Facemask (see `MonsterGearOverride#satisfiedBy`, which is what
  `GearSection#isSlotInvalidForTarget` checks — an equipped item red-crosses
  the slot only when it matches NEITHER `itemId` NOR any id in this list).
  Omit entirely (or leave empty) when the requirement has no known
  substitute. Only relaxes the red-cross display check; the optimiser still
  only force-includes the primary `itemId`.

Multiple overrides per monster are allowed (repeat the monster name in another
entry's `monsters` array) — extend by adding one more object to `overrides`.

## Adding an entry
1. Look up the item's id + slot in `equipment_index.min.json` (or via
   `EquipmentIndexRepository.idForName`) so the id is verified against this
   codebase's own data, not guessed.
2. Add one object to `overrides` with the exact monster name(s) as they
   appear in `monsters.min.json.gz` (`MonsterRepository`).
3. No regeneration script — this file is hand-maintained curated data, not
   extracted from an upstream source.
