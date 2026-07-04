# OSPulse

**Accurate, banking-aware session tracking for Old School RuneScape** — session profit, a complete loot feed, net worth, XP/levels, Grand Exchange flip P&L, and a live gear/DPS calculator with a gear optimiser. Everything is valued with RuneLite's own Grand Exchange prices, works fully offline of any third‑party service, and is presented in a single side panel.

- **Plugin name:** OSPulse
- **License:** BSD‑2‑Clause (see [`LICENSE`](LICENSE))
- **Third‑party data & code attribution:** see [`NOTICE`](NOTICE)

---

## What it does

OSPulse adds one side panel made up of collapsible, individually hideable sections:

| Section | What it shows |
| --- | --- |
| **Session** | Live session **profit**, computed banking‑aware (see [How profit is calculated](#how-profit-is-calculated)), plus loot value earned vs supplies consumed and net‑of‑supplies profit. |
| **Loot** | A per‑item **loot feed** built from wealth changes, so it catches *everything* you gain — not just NPC drops. Per‑item and per‑source right‑click "hide" menus. |
| **XP** | XP and levels gained this session, tracking virtual levels to 126. Right‑click reset/pause and an optional movable on‑canvas overlay per skill (a generic version of RuneLite's own XP Tracker overlay). |
| **Grand Exchange** | Realised **flip P&L** (average‑cost), reconciled against the loot feed so flips are never double‑counted as loot. Progress bars for in‑flight offers. |
| **Wealth** | **Net worth** = bank + inventory + equipment + Grand Exchange, with an "Unrealized P/L" line for mark‑to‑market price movement on items you're carrying. |
| **Top Holdings** | Your most valuable items with per‑holding value drift, paged. |
| **Gear / DPS** | A live combat calculator and gear optimiser — see below. |

### Gear / DPS calculator and optimiser

Auto‑filled from your **worn gear, live boosted stats, and active prayers** — no manual entry:

- Live readout of **max hit, accuracy, average hit, DPS and time‑to‑kill** across the weapon's real in‑game attack styles, ranked by DPS with the best auto‑selected.
- A searchable target monster (per‑style defence applied), plus on‑task, potion and prayer toggles.
- Variant/effect handling: Salve (e)(i), Slayer helm/black mask (i), Void, demonbane and dragon hunter weapons, powered staves and standard/ancient spells.
- **What‑if swaps:** click any slot, search an item, see the DPS delta before committing.
- **Gear optimiser:** "Find best setup" searches your owned and affordable items (with a GP budget) for the highest DPS, ranked by DPS‑per‑GP; include/exclude lists; apply the result to the readout.
- **Monster‑mechanic overrides:** a small curated table pins items required by mechanics rather than raw DPS (e.g. **Insulated boots vs Rune dragons**, which reduce the lightning special‑attack damage) and shows the reason.

---

## Privacy & network access

OSPulse is designed to make almost no network calls, and to be trivial for a reviewer to verify.

- **Grand Exchange prices are *not* a plugin connection.** All item valuation uses RuneLite's `ItemManager` (`getItemPrice`), which is populated by the client itself. OSPulse opens no socket for pricing — exactly like Bank, Ground Items or Loot Tracker.
- **One optional, off‑by‑default network feature.** The "Price trends" setting, when **you** turn it on, fetches historical price series from the public OSRS Wiki real‑time‑prices API:

  ```
  https://prices.runescape.wiki/api/v1/osrs/timeseries?timestep=24h&id=<itemId>
  ```

  It is implemented in a single class ([`PriceTrendService`](src/main/java/com/ospulse/integration/PriceTrendService.java)):
  - The enabled flag (`priceTrendEnabled`, **default `false`**) is checked *before every request*.
  - Requests are asynchronous (`OkHttpClient.enqueue`, never `execute`) and never block the client thread.
  - It reuses RuneLite's injected `OkHttpClient` — **no new HTTP or JSON dependency is added**.
  - Only an item id is ever sent. No account name, no player data, no identifiers.
- **No other egress.** There is no telemetry, no analytics, and no sync to any author‑hosted service. `PriceTrendService` is the only class in the plugin that performs a network request.

---

## Settings

**Session**
- **Min loot value** — hide loot‑feed items below this GE value.
- **Include rune pouch / looting bag** — count pouch/bag contents in tracked wealth.

**Price trends (optional)** — *the only section that can cause a network call.*
- **Enable price trends** — off by default; turns on the OSRS Wiki timeseries fetch described above.
- **Trend window** — how far back the trend line looks.
- **Holdings page size** — rows per page in Top Holdings.

**Panel sections** — independently show/hide each of the seven sections (Session, Loot, XP, Gear, Grand Exchange, Wealth, Top Holdings). Toggles apply live, without restarting the plugin.

---

## How it works

### How profit is calculated

Profit uses a **banking‑aware wealth‑delta** model rather than only crediting NPC drops:

- Tracked wealth = inventory + equipment + Grand‑Exchange‑in‑flight + pouches. The bank is treated as separate cold storage; net worth = tracked wealth + bank.
- **Away from the bank**, any change in tracked wealth is real profit or loss, so pickups, consumables and untradeables are all accounted for.
- **With the bank open**, inventory↔bank moves are transfers (zero‑sum) and excluded; when the bank closes the engine shifts its baseline by the net deposit/withdrawal, so banking never registers as profit.
- The Grand Exchange reconciler removes GE‑driven item movements from the loot feed and surfaces realised flip spread separately, so a flip isn't counted twice.

The core engine is **pure Java** with no `net.runelite.*` imports, which keeps it unit‑testable; a thin integration layer feeds it from RuneLite events.

### Client‑thread safety

Anything that must run on the client thread (equipment stats, item compositions, tradeability, GE prices) is resolved on the client thread and handed to the Swing UI and the background optimiser as plain data. The gear optimiser's search runs off the Event Dispatch Thread and never touches `ItemManager` directly.

---

## Data sources, provenance & licensing

Full detail is in [`NOTICE`](NOTICE); in summary:

- **Plugin code** — BSD‑2‑Clause. Compiled against the RuneLite client API (BSD‑2‑Clause).
- **`com.ospulse.ui.category.*`** — the per‑category resettable/pausable state, right‑click menu and movable canvas overlay are adapted from RuneLite's own **XP Tracker** plugin (BSD‑2‑Clause), generalised from "one instance per skill" to "one instance per panel category".
- **Equipment stats** (`equipment_stats.min.json`, `equipment_index.min.json`) — derived **clean‑room from the OSRS game cache** (via OpenRS2 + `net.runelite:cache`), not from any wiki compilation.
- **Monster combat stats** (`monsters.min.json`) and the **weapon‑category map** (`weapon_categories.min.json`) — derived from the [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) dataset (GPL‑3.0), whose figures originate from the [OSRS Wiki](https://oldschool.runescape.wiki) (CC BY‑NC‑SA 3.0). Credited in `NOTICE`; each bundled file has a sibling `.README.md` documenting provenance and regeneration.
- **Hand‑transcribed game facts** (spell max hits, weapon percentages, item ids) are individual facts verified against the live game/cache, not copied compilations.

Bundled data is refreshed from a regeneration script so it doesn't fall behind game updates.

---

## Building from source

Requires a JDK 17+ to build (the plugin targets Java 11 at runtime).

```bash
# Compile and run the unit tests
./gradlew build

# Produce the shadowed plugin jar (build/libs/ospulse-<version>-all.jar)
./gradlew shadowJar
```

To test in the client, launch RuneLite in developer mode and load the jar from your `.runelite/sideloaded-plugins/` directory. RuneLite scans that directory only at startup, so relaunch the client after replacing the jar.

The build uses only dependencies already transitive to the RuneLite client (OkHttp and Gson are `compileOnly`); no additional HTTP or JSON library is introduced.

---

## Credits

- The [RuneLite](https://github.com/runelite/runelite) project and its contributors (client API and the XP Tracker plugin this borrows a UI pattern from).
- [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) and the [OSRS Wiki](https://oldschool.runescape.wiki) for the combat/monster data lineage.

## License

BSD‑2‑Clause. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
