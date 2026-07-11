# OSPulse — developer & data notes

Technical detail, data provenance and build instructions for OSPulse. For the user‑facing overview see [`README.md`](README.md).

---

## Privacy & network access (full detail)

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

Full attribution is in [`NOTICE`](NOTICE); in summary:

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

# Produce a SIDELOADED TESTING jar named "OSPulse (dev)" in the client
./gradlew -Pdev shadowJar
```

To test in the client, launch RuneLite in developer mode and load the jar from your `.runelite/sideloaded-plugins/` directory. RuneLite scans that directory only at startup, so relaunch the client after replacing the jar.

### The `-Pdev` flag — name the test build "OSPulse (dev)"

Once the plugin is live on the Plugin Hub, a machine running RuneLite ends up
with **two** copies: the Hub-installed one and your sideloaded testing jar. To
tell them apart, build the sideloaded jar with **`-Pdev`** — its `@PluginDescriptor`
name becomes **"OSPulse (dev)"** while the plain build (what the Plugin Hub
compiles) stays **"OSPulse"**.

How it works: the `generateBuildInfo` Gradle task (in `build.gradle`) emits
`com.ospulse.BuildInfo.PLUGIN_NAME` as a compile-time constant — `"OSPulse (dev)"`
when `-Pdev` is present, `"OSPulse"` otherwise — and `@PluginDescriptor(name = ...)`
references it. The Plugin Hub runs a clean `./gradlew` with **no** `-Pdev`, so the
shipped plugin is always plain "OSPulse". **Never** hard-code "(dev)" into the
descriptor source — it would leak into the Hub build.

**Deploy gotcha:** RuneLite loads *every* `.jar` in `sideloaded-plugins/`, and the
jar filename is version-stamped (`ospulse-<version>-all.jar`), so `cp` does **not**
overwrite a previous version. Always delete the old jar after a version bump, or
you will run two copies at once and see stale behaviour.

The build uses only dependencies already transitive to the RuneLite client (OkHttp and Gson are `compileOnly`); no additional HTTP or JSON library is introduced.
