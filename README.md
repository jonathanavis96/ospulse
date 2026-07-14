<p align="center">
  <img width="150" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/branding/ospulse-logo-nobg.png" alt="OSPulse logo">
</p>

<h1 align="center">OSPulse</h1>

<p align="center">
  <b>An all‑in‑one information dashboard for Old School RuneScape, in a single RuneLite side panel.</b><br>
  A live gear <b>DPS / max‑hit calculator</b> alongside banking‑aware <b>session profit</b>, a complete <b>loot feed</b>, total <b>net worth</b>, <b>XP</b> gained and Grand Exchange <b>flip P&amp;L</b> — everything valued with RuneLite's own Grand Exchange prices, with no third‑party service required.
</p>

<p align="center">
  <img width="270" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-dps-hero.png" alt="Live gear DPS and max-hit calculator panel showing worn gear, ranked attack styles and the max hit / accuracy / DPS / time-to-kill readout">
</p>

<p align="center">
  🧪 <b>OSPulse is in beta.</b> Hit a bug, or a number that looks wrong?<br>
  <b><a href="https://github.com/jonathanavis96/ospulse/issues">Please report it here</a></b> — your reports genuinely help make it better.
</p>

---

## ✨ At a glance

| | |
| --- | --- |
| ⚔️ **Gear DPS & max hit** | Live max hit, accuracy, DPS and time‑to‑kill, auto‑filled from your worn gear, boosted stats and prayers. |
| 🧠 **Gear optimiser** | **Find Best** searches your owned and affordable items for the highest DPS‑per‑GP, respects each monster's required items, and highlights the picks in your bank. |
| 💰 **Session profit** | Banking‑aware, so bank trips are never counted as losses. |
| 🎒 **Loot feed** | Built from wealth changes, so it catches *everything* you gain — not just NPC drops. |
| 📈 **Net worth & XP** | Bank + inventory + equipment + GE, plus XP and levels gained this session. |
| 🔁 **GE flip P&L** | Realised, average‑cost, reconciled so flips are never double‑counted as loot. |
| 🔒 **Private by default** | No account data leaves the client; one optional, off‑by‑default price‑trend lookup. |

---

## ⚔️ Gear DPS & max hit

<img align="left" width="230" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-dps-full.png" alt="Full Gear DPS panel: worn-gear grid, Find Best / Reset, a searchable target, attack styles ranked by DPS, and the max hit / accuracy / DPS / time-to-kill readout with the optimiser's suggested swaps below">

Where a standalone **DPS calculator** makes you type in your gear, levels and boosts, OSPulse reads them straight from the client and recomputes as you swap gear, sip a potion or flick a prayer — always reflecting your real current setup, with no manual entry.

- Live **max hit, accuracy, average hit, DPS and time‑to‑kill**, across the weapon's real in‑game attack styles, ranked by DPS with the best auto‑selected.
- A searchable **target monster** (per‑style defence applied), plus on‑task, potion and prayer toggles.
- Variant/effect handling: Salve (e)(i), Slayer helm/black mask (i), Void, demonbane and dragon hunter weapons, powered staves and standard/ancient spells, the elemental **tomes** (fire/water/earth) and the **blowpipe** (dart set by right‑clicking the weapon in the gear panel).
- **What‑if swaps:** click any slot, search an item, and see the DPS delta versus your worn gear before committing.

Damage is calculated player‑vs‑monster (PvM); PvP is not modelled.

<br clear="all">

### 🎯 Knows each monster's requirements

<img align="right" width="200" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-required-item.png" alt="Monstrous basilisk target with the mechanic-required mirror / V's shield pinned into the recommended setup">

Some kills need a specific item, not just the highest DPS. OSPulse pins the piece a monster's mechanic **requires** — a mirror / V's shield for **basilisks**, Insulated boots for **Rune dragons** — and shows the reason, so the "best setup" is one that actually works.

<img align="left" width="200" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-incompatible-weapon.png" alt="Kurask target with an incompatible weapon crossed out by a red X">

It also **crosses out** styles and weapons that can't damage the target — an unusable attack style, a weapon a monster is immune to (a leaf‑bladed requirement on **Kurasks**) — so you never set out with gear that can't land a hit.

<br clear="all">

### 🧠 Gear optimiser — best DPS per GP

<img align="right" width="200" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-optimiser-budget.png" alt="Gear optimiser with a custom GP budget, listing suggested swaps and their cost with most slots already owned">

**"Find Best"** searches your owned and affordable items for the highest DPS, ranked by **DPS‑per‑GP**, and applies the result straight to the readout.

- Set a **GP budget** and it finds the cheapest route to more DPS — most swaps come from gear you already own, and each paid upgrade shows its price and the exact DPS it buys.
- It ranks the five damage‑type selectors (Stab/Slash/Crush/Ranged/Magic) by the best achievable DPS for each, so the strongest style is offered first.
- Ammo is **weapon‑compatible**: arrows only with bows, bolts with crossbows (never javelins on a bow), and each candidate weapon is trialled with its own best compatible ammo.

<img align="left" width="230" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-optimiser-exclude.png" alt="Excluded-from-suggestions list, each item with a per-item remove button">

**Steer the search:** right‑click any suggestion to exclude it and instantly re‑optimise. The excluded list is editable, so you can drop items you'll never buy and keep the recommendations realistic.

<br clear="all">

### 🏦 Highlights the upgrades in your bank

<img align="right" width="230" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/bank-highlighting.png" alt="Bank view with the optimiser's recommended gear pieces highlighted">

Once you've settled on a setup, OSPulse **highlights the recommended pieces in your bank**, so you can grab the exact upgrade without cross‑referencing item names — the swap you just planned, marked where you'll actually pick it up.

<br clear="all">

---

## 📊 Session, wealth & XP tracking

The same side panel gathers, as collapsible and individually hideable sections:

| Section | What it shows |
| --- | --- |
| **Session** | Live session **profit**, computed banking‑aware, plus loot value earned vs supplies consumed and net‑of‑supplies profit. |
| **Loot** | A per‑item **loot feed** built from wealth changes, so it catches *everything* you gain — not just NPC drops. Sorted by value, hover any row for name × quantity and per‑item GE / high‑alch value, with per‑item and per‑source right‑click "hide" menus. |
| **XP** | XP and levels gained this session, tracking virtual levels to 126. Right‑click reset/pause and an optional movable on‑canvas overlay per skill. |
| **Grand Exchange** | Realised **flip P&L** (average‑cost), reconciled against the loot feed so flips are never double‑counted as loot. Progress bars for in‑flight offers. |
| **Wealth** | **Net worth** = bank + inventory + equipment + Grand Exchange, with an "Unrealized P/L" line for price movement on items you're carrying. |
| **Top Holdings** | Your most valuable items with per‑holding value drift, paged. |

<p align="center">
  <img width="210" hspace="6" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/session-loot.png" alt="Session profit and loot feed">
  <img width="210" hspace="6" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/xp.png" alt="XP gained per skill this session">
  <img width="210" hspace="6" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/top-holdings.png" alt="Top holdings with per-item value drift">
</p>
<p align="center">
  <img width="210" hspace="6" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/grand-exchange.png" alt="Grand Exchange offers with progress bars">
  <img width="210" hspace="6" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/net-worth.png" alt="Net worth breakdown across bank, inventory, equipment and GE">
</p>

<details>
<summary><b>How is session profit calculated?</b></summary>

<br>

Profit uses a **banking‑aware wealth‑delta** model rather than only crediting NPC drops:

- **Away from the bank**, any change in your carried wealth (inventory + equipment + GE‑in‑flight + pouches) is real profit or loss — so pickups, consumables and untradeables are all accounted for.
- **With the bank open**, inventory↔bank moves are transfers (zero‑sum) and excluded; when the bank closes the baseline shifts by the net deposit/withdrawal, so banking never registers as profit.
- The Grand Exchange reconciler removes GE‑driven item movements from the loot feed and surfaces realised flip spread separately, so a flip isn't counted twice.

</details>

---

## 🔒 Privacy & network access

OSPulse makes **almost no network calls**, and is trivial for a reviewer to verify.

- **Grand Exchange prices are *not* a plugin connection.** All valuation uses RuneLite's own `ItemManager` — OSPulse opens no socket for pricing, exactly like Bank, Ground Items or Loot Tracker.
- **One optional, off‑by‑default feature.** When *you* enable "Price trends", it fetches a price series from the public OSRS Wiki API, sending **only an item id** — no account name, no player data. It reuses RuneLite's injected `OkHttpClient`; no new dependency is added.
- **No telemetry, analytics, or author‑hosted sync of any kind.**

Full technical detail (the single class involved, threading, provenance and build) is in [`DEVELOPMENT.md`](DEVELOPMENT.md).

---

## ⚙️ Settings

- **Min loot value** — hide loot‑feed items below this GE value.
- **Include rune pouch / looting bag** — count pouch/bag contents in tracked wealth.
- **Enable price trends** — off by default; the only setting that can cause a network call (see above).
- **Trend window** / **Holdings page size** — trend look‑back and rows per Top Holdings page.
- **Panel sections** — independently show/hide each of the seven sections (Session, Loot, XP, Gear, Grand Exchange, Wealth, Top Holdings), applied live without restarting.

The blowpipe's loaded dart is set by **right‑clicking the blowpipe in the gear panel** ("Set darts ▸").

---

## 🔗 Links

- **Source code:** [github.com/jonathanavis96/ospulse](https://github.com/jonathanavis96/ospulse)
- **Report a bug:** [github.com/jonathanavis96/ospulse/issues](https://github.com/jonathanavis96/ospulse/issues)
- **Developer & data‑provenance notes:** [`DEVELOPMENT.md`](DEVELOPMENT.md)

---

## 📝 Changelog

<!-- Unreleased: staged notes for the next Plugin Hub update. On release, rename this
     heading to the new version (e.g. "### 0.2.1 — …") and bump the manifest PR. -->
### Unreleased

**✨ New**

- 💰 **Session net worth you can actually trust.** Your net‑worth change is now a sum you can check yourself — **Profit + GE flip + GE positions + Bank** — with GE positions and Bank each toggleable. Banking your loot no longer reads as a huge loss: moving gp between your pack and your bank doesn't change what you're worth. Open offers are valued at what you paid, so shifting your own coins into the GE doesn't inflate the figure either, and price drift on gear you're holding or wearing no longer leaks into the total.
- ⚖️ **The risk cap now sacrifices the right item.** When you limit how many expensive items to allow, the optimiser used to give up whichever item happened to sit earliest in the slot order — demoting a DPS‑carrying amulet while a DPS‑dead ring kept its expensive spot. It now gives up whatever costs you the least DPS, so your allowed expensive slots go to the gear that earns them.
- 💍 **Charge variants grouped into one entry.** Amulet of glory (1)/(2)/(3)/(4)/eternal were listed as separate items, so you'd see eight near‑identical amulets and be told to "buy" a charge of one already in your bank. Now it's a single entry using your highest owned charge — and under an active risk cap it steps down to the best charge you're allowed to take. Same for glory (t).
- 🛡️ **Protected items are flagged.** Rare untradeables you can only keep on death with a Trouver parchment are now tinted, with a "must be protected (Trouver parchment)" tooltip, so you can see what you're really risking.
- 🔢 **Clearer numbers everywhere.** DPS, Accuracy, Avg hit, TTK, Overkill and your gp figures all read the same way: the number and its unit (`k`/`m`/`b`/`%`) stay bright while the decimals dim back, so **1.98** can't be mistaken for *198* — all at normal text size.
- 🏹 **The ammo slot names your ammo.** Reads "Rada's blessing 4 — Ammo slot (live)" instead of generic slot text, so similar‑looking ammo is no longer indistinguishable.
- 🎭 **Recommendations name the variant you own.** Shows "Masori mask (f)" rather than the plain "Masori mask", with the icon, preview and bank highlight all agreeing.

**🔧 Fixed**

- **Cheap untradeables are no longer treated as 575k of risk.** Every rare untradeable was priced at the cost of a Trouver parchment, so Barrows gloves (~130k to reclaim) were risked at 575k. They're now valued at whichever is cheaper — parchment or real reclaim cost — and cheap‑reclaim items are no longer wrongly flagged "must be protected".
- **Items priced through a crafting ingredient always read as unaffordable** — a Scorching bow (valued via a Tormented synapse) could never be recommended no matter your budget.
- **Items sharing a name could read as not owned.** Imbued rings have a separate id per reward source, and the name lookup silently dropped all but one of them.
- **Attack‑style DPS was cut in half by the panel edge.** The styles list could render past its own right‑hand edge, so a style's DPS showed as "5." with the rest hidden under the scroll bar — worst on the ranged, slash and crush lists. The list now always fits the panel width, so every DPS reads in full.
- **Attack‑style names are no longer cut short.** "Longrange", "Pummel" and "Pound" could render as "Longran…". Styles still pair into two compact columns wherever they fit, but a lone odd style now takes the full bottom row — so a bow reads Accurate and Rapid side by side with Longrange spanning beneath — and any weapon whose names genuinely can't fit two columns (chinchompas) gets a full‑width list instead. The best style no longer carries a ★, since its name and DPS are already orange and it starts selected.
- **Session toggles tidied up.** The tick boxes for "GE positions", "Bank" and "Show breakdown" now follow their names instead of leading them, so every name lines up with "Profit" and "GE flip" above and all three boxes share one column. The boxes are smaller and the rows now sit exactly as tight as the rest of the breakdown.

### 0.2.0 — Smarter gear, per‑monster

**✨ New**

- 🎯 **Knows each monster's requirements.** Recommends and pins the weapons, ammunition, styles and special items a target actually needs — a mirror / V's shield for **basilisks**, Insulated boots for **Rune dragons** — with clear warnings for gear a monster is immune to, so the "best setup" is one that can really land hits.
- 🧠 **Smarter optimiser.** Now factors in combat style, weapon‑compatible ammunition, your player level, shield compatibility and an expensive‑item risk cap — so it never suggests gear you can't use, can't afford, or shouldn't take into danger.
- 💸 **Budget & faster picks.** Set a GP budget for the cheapest DPS boost; picking a target auto‑runs **Find Best** and auto‑selects your strongest style, with owned‑vs‑buy items colour‑coded at a glance.
- 🏦 **Highlights upgrades in your bank.** After **Find Best**, the recommended pieces are highlighted in your bank, so you can grab the exact swap without hunting for item names.
- 🎨 **Redesigned gear controls.** New layout for the buttons, budget display, style selection and optimiser previews — green **Find Best** / red **Reset**, a tidy 2‑column style grid and a clearer budget / risk block.
- 📊 **Slayer & Agility aware.** Gear snapshots now capture your Slayer and Agility levels alongside your combat stats.

**🔧 Fixed**

- Better matching for monsters with variant names (e.g. *Kurask (Normal)*), so requirements always resolve.
- No more duplicate economic updates within the same game tick — session numbers stay accurate.
- Smarter handling of two‑handed weapons and of slots that don't change your DPS.
- Only the weapon a monster can't be hurt by is crossed out now — not your whole setup.
- Bank highlights no longer flicker or reappear when the OSPulse panel is closed.
- Plays nicely with **Bank Tag Layouts**, and the suggested‑swap labels no longer clip on the right.

<details>
<summary>Older versions</summary>

<br>

- **0.1.1** — Fish barrel: caught fish count as loot the moment they're stored, and emptying at a bank or deposit box stays accurate (no double-count, no drop); full-barrel overflow catches now count as loot. Fishing bait and feathers count as supplies. Depositing any item at a bank deposit box is treated as banking, so it's never booked as a loss.
- **0.1.0** — Initial release: gear DPS / max-hit calculator and optimiser, banking-aware session profit, loot feed, net worth, XP tracking and Grand Exchange flip P&L.

</details>

---

## Credits & license

Built on the [RuneLite](https://github.com/runelite/runelite) client API, with a UI pattern adapted from its XP Tracker plugin. Combat/monster data lineage traces to [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) and the [OSRS Wiki](https://oldschool.runescape.wiki); equipment stats are derived clean‑room from the game cache. Full attribution in [`NOTICE`](NOTICE).

BSD‑2‑Clause — see [`LICENSE`](LICENSE).
