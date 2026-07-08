<p align="center">
  <img width="150" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/branding/ospulse-logo-nobg.png" alt="OSPulse logo">
</p>

<h1 align="center">OSPulse</h1>

<p align="center">
  <b>An all‑in‑one information dashboard for Old School RuneScape, in a single RuneLite side panel.</b><br>
  A live gear <b>DPS / max‑hit calculator</b> alongside banking‑aware <b>session profit</b>, a complete <b>loot feed</b>, total <b>net worth</b>, <b>XP</b> gained and Grand Exchange <b>flip P&amp;L</b> — everything valued with RuneLite's own Grand Exchange prices, with no third‑party service required.
</p>

<p align="center">
  <img width="260" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-dps.png" alt="Live gear DPS and max-hit calculator">
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
| 🧠 **Gear optimiser** | "Find best setup" searches your owned and affordable items for the highest DPS‑per‑GP. |
| 💰 **Session profit** | Banking‑aware, so bank trips are never counted as losses. |
| 🎒 **Loot feed** | Built from wealth changes, so it catches *everything* you gain — not just NPC drops. |
| 📈 **Net worth & XP** | Bank + inventory + equipment + GE, plus XP and levels gained this session. |
| 🔁 **GE flip P&L** | Realised, average‑cost, reconciled so flips are never double‑counted as loot. |
| 🔒 **Private by default** | No account data leaves the client; one optional, off‑by‑default price‑trend lookup. |

---

## ⚔️ Gear DPS & max hit

<img align="left" width="220" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-preview.png" alt="What-if gear swap preview showing DPS delta vs worn gear">

The gear calculator is auto‑filled from your worn gear, live boosted stats and active prayers — no manual entry, always reflecting your real current setup. Where a standalone DPS calculator makes you type in your gear, levels and boosts, OSPulse reads them from the client and recomputes as you swap gear, sip a potion or flick a prayer.

- Live **max hit, accuracy, average hit, DPS and time‑to‑kill**, across the weapon's real in‑game attack styles, ranked by DPS with the best auto‑selected.
- A searchable target monster (per‑style defence applied), plus on‑task, potion and prayer toggles.
- Variant/effect handling: Salve (e)(i), Slayer helm/black mask (i), Void, demonbane and dragon hunter weapons, powered staves and standard/ancient spells, the elemental **tomes** (fire/water/earth) and the **blowpipe** (dart set by right‑clicking the weapon in the gear panel).
- **What‑if swaps:** click any slot, search an item, and see the DPS delta before committing.
- **Monster‑mechanic overrides:** pins items required by mechanics rather than raw DPS (e.g. **Insulated boots vs Rune dragons**) and shows the reason.

Damage is calculated player‑vs‑monster (PvM); PvP is not modelled.

<br clear="all">

### 🧠 Gear optimiser

<img align="right" width="230" hspace="20" vspace="6" src="https://raw.githubusercontent.com/jonathanavis96/ospulse/master/screenshots/gear-optimiser.png" alt="Find best setup optimiser results with suggested swaps and exclude menu">

**"Find best setup"** searches your owned and affordable items (with a GP budget) for the highest DPS, ranked by **DPS‑per‑GP**; apply the result straight to the readout.

- It ranks the five damage‑type selectors (Stab/Slash/Crush/Ranged/Magic) by the best achievable DPS for each, so the strongest style is offered first.
- Ammo is **weapon‑compatible**: arrows only with bows, bolts with crossbows and so on (never javelins on a bow), and each candidate weapon is trialled with its own best compatible ammo.
- Include/exclude lists let you steer the search; right‑click any suggestion to exclude it and instantly re‑optimise.

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

<details>
<summary>Version history — newest first</summary>

<br>

- **0.1.1** — Fish barrel: caught fish count as loot the moment they're stored, and emptying at a bank or deposit box stays accurate (no double-count, no drop); full-barrel overflow catches now count as loot. Fishing bait and feathers count as supplies. Depositing any item at a bank deposit box is treated as banking, so it's never booked as a loss.
- **0.1.0** — Initial release: gear DPS / max-hit calculator and optimiser, banking-aware session profit, loot feed, net worth, XP tracking and Grand Exchange flip P&L.

</details>

---

## Credits & license

Built on the [RuneLite](https://github.com/runelite/runelite) client API, with a UI pattern adapted from its XP Tracker plugin. Combat/monster data lineage traces to [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) and the [OSRS Wiki](https://oldschool.runescape.wiki); equipment stats are derived clean‑room from the game cache. Full attribution in [`NOTICE`](NOTICE).

BSD‑2‑Clause — see [`LICENSE`](LICENSE).
