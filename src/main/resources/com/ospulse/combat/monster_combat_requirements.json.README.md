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
  - `DAMAGE_PENALTY` — a style still works but deals reduced damage. Never gates.
  - `DAMAGE_CAP` — max hit is flatly capped regardless of gear. Never gates.

### `DAMAGE_PENALTY` fields

- `damageMultiplier` — applied to max hit. `0.5` halves it. Default `1.0`.
- `penalisedStyles` — which styles suffer it. **Empty means every style**, so list
  them explicitly unless that is really what you want.
- `allowedItemIds` — weapons that can escape the penalty.
- `exemptStyles` — the styles on which `allowedItemIds` actually grants the escape.
  Empty means "exempt on any penalised style".

That last field exists because exemption is often style-sensitive. Corporeal Beast's
rule is *"50% damage reduction against any weapon that is not a corpbane weapon **on
stab attack style**"* — so a Zamorakian spear earns full damage on stab and is still
halved on slash. Exempting purely on item id would overstate that loadout, and
penalising only `STAB` would let slash, crush and ranged through at full damage.
Magic is not listed as penalised at all: at Corp it deals full damage and is merely
inaccurate.

`exemptStyles` is a set, not a single style, because one entry can cover weapons that
are corpbane on different styles. Corp lists both `STAB` and `RANGED`: the melee
corpbane weapons attack on stab, but King's barrage is a crossbow and RANGED is the
only style it has, so a stab-only exemption would have been inert for it. This is safe
only because `allowedItemIds` is the other half of the gate — a weapon escapes the
penalty when it is *both* listed *and* on an exempt style, and no spear can attack on
ranged nor a crossbow on stab. **When adding a weapon here, check that at least one
style it can actually attack with is in `exemptStyles`**, or the listing does nothing;
`TargetDamageRuleTest.everyShippedCorpbaneWeaponIsExemptOnAStyleItCanActuallyUse`
enforces that. If a future entry ever needs "exempt on stab" and "exempt on ranged" to
apply to *overlapping* weapons with different answers, split it into two entries rather
than widening this set.

### `DAMAGE_CAP` fields

- `maxHitCap` — the ceiling. `-1`/absent means no cap.
- `maxHitCapWhenCrushHighest` — alternative ceiling used when the loadout's crush
  attack bonus beats both its stab and slash bonuses.
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
read a flat `dmagic: 0, drange: 0`.** Tekton's ranged immunity is the same. The
bonus fields carry *graduated resistance* only.

So "the DPS data already steers away from that style" is **never** a valid reason
to skip a gate.

**The converse matters just as much.** A monster that is merely *resistant* must
NOT get a gate — encoding graduated resistance as a hard block greys out a style
that genuinely works. Tekton is the worked example of both halves at once: its
ranged immunity **is** gated, while its 80%-reduced magic is **not**, because
magic still lands. That reduction is expressed as a damage penalty instead.

Rule of thumb: if the style deals *zero* damage, gate it. If it deals *less*
damage, model the reduction.

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

## Known gaps this schema cannot express yet

**One requirement per monster name.** The loader keys a `Map` by lowercased name and
`put`s — a second entry for the same monster **silently overwrites** the first. So a
monster can carry exactly one requirement, of one type. Two real cases are blocked:

- **Tekton** — has the `WEAPON_GATE` for its ranged immunity, so its separate 80%
  reduced-magic damage cannot also be expressed.
- Any monster needing both a gate and a penalty/cap.

Fix when needed: either store a list per monster and apply all matches, or fold
optional `damageMultiplier`/`maxHitCap` fields into `WEAPON_GATE`.

**Per-style damage caps.** `DAMAGE_CAP` carries one cap (plus a crush-highest variant),
but some caps differ by style. **Verzik Vitur phase 1** caps melee at 10 and ranged/magic
at **3** — a single value would overstate ranged and magic by more than 3x, so no entry
is shipped rather than a wrong one. Needs a per-style cap field.
