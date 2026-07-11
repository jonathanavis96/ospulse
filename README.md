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

### 0.2.0 — Smarter gear, per‑monster

**✨ New**

- 🎯 **Knows each monster's requirements.** Pins the item a boss actually needs (mirror / V's shield for **basilisks**, Insulated boots for **Rune dragons**) and greys out styles or weapons a monster is immune to — so the "best setup" is one that can really land hits.
- 🏦 **Highlights upgrades in your bank.** After **Find Best**, the pieces it recommends are highlighted in your bank, so you can grab the exact swap without hunting for item names.
- 💸 **Budget & risk controls.** Set a GP budget for the cheapest DPS boost, plus a spend cap so it won't push wildly expensive gear — and it never suggests items above your level.
- 🧭 **Faster optimiser.** Picking a target auto‑runs Find Best and auto‑selects your strongest style; owned‑vs‑buy items are colour‑coded at a glance.
- 🎨 **Cleaner gear panel.** Redesigned layout — green **Find Best** / red **Reset** buttons, attack styles in a tidy 2‑column grid, and a clearer budget / risk block.

**🔧 Fixed**

- Only the weapon a monster can't be hurt by is crossed out now — not your whole setup.
- Bank highlights no longer flicker or reappear when the OSPulse panel isn't open.
- More accurate session tracking (one settled transaction per game tick).
- Plays nicely with Bank Tag Layouts, and the suggested‑swap labels no longer clip.

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
