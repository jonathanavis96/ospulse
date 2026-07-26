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

- `maxHitCap` — the flat ceiling. `-1`/absent means no cap.
- `maxHitCapWhenCrushHighest` — alternative ceiling used when the loadout's crush
  attack bonus beats both its stab and slash bonuses.
- `maxHitCapByStyle` — optional per-`CombatStyle` override (keys: `STAB` / `SLASH`
  / `CRUSH` / `RANGED` / `MAGIC`). Takes priority over both fields above for any
  style it lists; a style it does NOT list falls back to the flat/crush-highest
  value. Absent/empty means no per-style split — every entry written before this
  field existed is unaffected. See `TargetDamageRule#maxHitCapFor` for the exact
  resolution order.
- `capMode` — `CLAMP` or `REROLL` (absent defaults to `CLAMP`, so every entry
  written before this field existed keeps its original behaviour byte-for-byte).
  **These are mechanically different distributions, not two names for the same
  thing:**
  - `CLAMP` — the roll stays uniform `0..uncappedMaxHit` and every result above
    the cap lands ON the cap, piling probability mass there. Implemented by
    `CombatMath.cappedAverageDamagePerAttack` / `cappedExpectedOverkill`.
  - `REROLL` — a hit above the cap is re-rolled uniformly into `0..cap`.
    Implemented by `CombatMath.rerolledAverageDamagePerAttack` /
    `rerolledExpectedOverkill`.

    ⚠ **Do NOT collapse this to `maxHit = min(maxHit, cap)` through the ordinary
    formulas.** Ignoring OSRS's "a rolled 0 becomes 1" correction the re-rolled
    distribution *is* flat over `0..cap` (`P(d) = 1/(M+1) + [(M-cap)/(M+1)]·[1/(cap+1)]`,
    independent of `d` — proved from first principles in
    `CombatMathRerollEquivalenceTest`), and that is exactly what makes the shortcut
    look safe. But the ordinary formulas *carry* that correction, and **a re-rolled
    0 is a genuine result that must keep its probability mass.** The bump belongs to
    the damage roll, so it applies before the monster re-rolls and survives only on
    values that were never re-rolled:

    ```text
    rerollShare = (M - cap) / ((M + 1)(cap + 1))
    P(0) = rerollShare
    P(1) = 2/(M+1) + rerollShare        <- the surviving bump
    P(v) = 1/(M+1) + rerollShare          for v in 2..cap
    ```

    giving `E = cap/2 + 1/(M+1)`, valid only for `1 <= cap <= M` — keyed to the
    TRUE max, not `cap/2 + 1/(cap+1)`. At Verzik's ranged/magic cap of 3 the
    shortcut overstates by ~15% and hands the overkill DP a zero-free
    distribution, so TTK is wrong too. Once `cap >= M` nothing is ever
    re-rolled, so the uncapped maximum applies instead (the ordinary formulas,
    unmodified). (At `cap == 0` even the bumped 1 re-rolls away and the true
    mean is exactly 0; the functions are guarded.) **This shortcut shipped once
    and was caught in review — the flatness result is about the shape only.**

  **Why the split exists:** the OSRS wiki documents these as two different
  in-game mechanics, not a stylistic choice by this codebase. Verzik Vitur phase
  1's cap is explicitly a re-roll ("re-rolled to 0-10 damage" / "re-rolled to 0-3
  damage" — <https://oldschool.runescape.wiki/w/Maximum_damage_cap>), which is
  why its entry is `REROLL`. The Hueycoatl's tail has **no published wording**
  either way — `CLAMP` there is a reasonable default reading (the original
  implementation, unchanged by this feature), not a verified mechanic. Do not
  treat Hueycoatl's `CLAMP` as confirmation that every future cap should default
  to clamp semantics; check the source for each new entry.
- `allowedStyles`: `STAB` / `SLASH` / `CRUSH` / `RANGED` / `MAGIC`. Any style
  listed here is permitted outright. (Not used by any shipped `DAMAGE_CAP` entry
  today — `DAMAGE_CAP` never gates, so this field is inert for this type; it
  exists on the shared shape for `WEAPON_GATE`.)
- `allowedItemIds`: for `DAMAGE_CAP`, weapons **wholly exempt from the cap**
  regardless of style — e.g. Dawnbringer at Verzik ("has no damage cap on the
  boss"). Checked before the per-style map or flat value; see
  `TargetDamageRule#maxHitCapFor`. Verified against `equipment_index.min.json`,
  never guessed from the wiki. (For `WEAPON_GATE` this same field instead means
  "specific weapons permitted regardless of style" — the exception mechanism, a
  halberd at Zulrah.)
- `allowedAmmoIds`: for ranged gates such as broad ammunition.
- `note`: one short sentence, shown verbatim in the panel to explain why a style
  is greyed out. For Verzik this also flags an UNMODELLED effect: the wiki
  documents a real accuracy penalty on non-Dawnbringer weapons ("a much lower
  chance of hitting") but publishes no number for it, so it is deliberately not
  applied — the note says so, so the readout is not mistaken for a complete
  model of the fight.

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

~~**Per-style damage caps.**~~ Fixed — see `maxHitCapByStyle` above. **Verzik
Vitur phase 1** ships as the first user: melee capped at 10, ranged/magic at 3,
via `REROLL` mode (a re-roll into `0..cap`, per the wiki's own wording), with
Dawnbringer listed in `allowedItemIds` as wholly exempt. The Hueycoatl's tail is
untouched — still one flat cap (plus crush-highest), `CLAMP` mode, no per-style
map — proving the new optional fields don't disturb an entry that predates them.
