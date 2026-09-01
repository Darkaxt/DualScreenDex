# DualDex

DualDex is a passive Pokédex companion for mainline-family Pokémon games running in RetroArch on Android handhelds. It supports a normal docked activity for dual-screen devices and an optional floating overlay for single-screen play.

The game remains on the primary display. DualDex detects the active GB, GBC, or GBA content, parses the user's ROM into a local SQLite Pokédex, and reads supported transient game state through RetroArch's read-only Network Commands. One unified snapshot supplies every feature; live fields are authoritative and validated save/checkpoint recovery fills only fields that are unavailable live. It does this without OCR, screenshots, cheats, memory writes, or per-hack profiles. A separately isolated issue-report tool can export read-only evidence for unsupported layouts, but its dumps never feed the production Pokédex.

> [!IMPORTANT]
> The pure-Kotlin ROM parser, materialized SQLite catalog, Gen I–III SaveRAM readers, validated live-WRAM paths, loopback web host, Thor-first UI, passive RetroArch activation, Docked/Overlay modes, and isolated read-only issue reports are implemented. Stable `v1.0.0` provides the complete v1 baseline. Candidate `v1.1.0-rc.86` completes the Thor lower-display pass with consistent active destinations, accessibility-gated Local Map player emphasis, truthful habitat-only Atlas shortcuts, clearer Party experience bars, and compact Pokédex card alignment. Unsupported features remain explicit instead of aborting otherwise valid catalogs.
>
> Current static compatibility evidence comes from the exact [331-ROM Gen I–III full-corpus review](docs/reports/2026-08-26-gen1-gen3-full-corpus-status.md) and its [machine-readable JSON](docs/reports/2026-08-26-gen1-gen3-full-corpus-status.json): 256 catalogs select and persist, overall applicable-table coverage is **73.80%**, and shared-table coverage improves from RC21's **67.13%** to **73.91%**.

## Thor-first UI direction

DualDex targets the AYN Thor's 3.92-inch lower display as a physically small companion surface, not as a high-resolution tablet. Browsing and species details are separate pages, battle tabs show one question at a time, and redundant global bottom navigation is omitted. Settings stay in the header; battle context opens and closes automatically.

## Packaged feature tour

These captures come from the debug QA APK running on the owned Thor-profile emulator at the exact 538 × 445 CSS-pixel companion viewport. The catalog is parsed from an externally staged, exact Modern Emerald ROM, while sanitized raw-memory replay supplies deterministic live state. No ROM is bundled. These screenshots validate the packaged WebView layout; they are not signed-production or physical-Thor evidence.

### Local Map

DualDex reconstructs local maps from ROM graphics and event data, then combines them with live player coordinates. The map preserves Organic discovery and fog of war, exposes discovered POIs through category filters, follows the trainer until manual navigation takes control, and keeps the in-game clock visible for future day/night rendering.

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-local-map.webp" width="46%" alt="DualDex Local Map tracking the trainer in Oldale Town with live clock, controls, and ROM-derived points of interest">
  <img src="docs/images/live/dualdex-thor-qa-area-guide.webp" width="46%" alt="DualDex Area Guide showing the current map overview and four connected exits">
</p>

<p align="center">
  <sub><strong>Local Map</strong> — trainer tracking, clock, controls, and POIs · <strong>Area Guide</strong> — current-area summary and exits</sub>
</p>

### Wild Encounter

The encounter page opens automatically from validated live battle state and resolves the current opponent without OCR. Entry follows the active information policy; Attack explains the selected move and known effectiveness; Rarity evaluates recruitment from relative level and IV quality; and Moves remembers only attacks actually observed instead of exposing the opponent's hidden loadout. In this deterministic `move-selected` frame, the player has selected Leer but the opponent has not used a move yet, so the empty Moves tab is intentional.

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-battle-entry.webp" width="46%" alt="DualDex Wild Encounter Entry tab showing the ROM-derived Pokédex entry for Poochyena">
  <img src="docs/images/live/dualdex-thor-qa-battle-attack.webp" width="46%" alt="DualDex Wild Encounter Attack tab showing Leer metadata and neutral effectiveness">
</p>

<p align="center">
  <sub><strong>Entry</strong> — policy-filtered knowledge · <strong>Attack</strong> — selected move details</sub>
</p>

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-battle-rarity.webp" width="46%" alt="DualDex Wild Encounter Rarity tab rating a Poochyena as Ordinary Trained">
  <img src="docs/images/live/dualdex-thor-qa-battle-moves.webp" width="46%" alt="DualDex Wild Encounter Moves tab truthfully showing that no opponent moves have been recorded yet">
</p>

<p align="center">
  <sub><strong>Rarity</strong> — IV and level recruitment signal · <strong>Moves</strong> — no opponent attack observed yet</sub>
</p>

### Pokédex

The Pokédex joins the ROM-derived species catalog with live seen, caught, and team knowledge. Its browser and counters adapt to the selected list; Entry includes the ROM text and trainer-relative height; Stats combines base values with a Level 50 projection; Moves exposes the validated learnset; Area renders known habitats; and More surfaces parsed ability conditions, evolutions, and locations when Organic discovery permits them.

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-pokedex-browser.webp" width="46%" alt="DualDex Discovered Pokédex browser showing the 428-species Modern Emerald catalog and available knowledge filters">
</p>

<p align="center">
  <sub><strong>Browser</strong> — live knowledge lists, filters, and adaptive totals</sub>
</p>

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-pokedex-entry.webp" width="46%" alt="DualDex Pokédex Entry tab showing Treecko description, measurements, and height comparison">
  <img src="docs/images/live/dualdex-thor-qa-pokedex-more.webp" width="46%" alt="DualDex Pokédex More tab showing the owned specimen shortcut and parsed Overgrow ability details">
</p>

<p align="center">
  <sub><strong>Entry</strong> — ROM description, measurements, and scale · <strong>More</strong> — owned specimens and parsed mechanics</sub>
</p>

### Party

Party pages consume the same unified live snapshot as battle and the Trainer Card. The overview uses the six-slot game layout, while each Pokémon opens a detail view with rarity, HP, experience, held item, and current stats. Nature and Ability drill-downs add ROM-resolved stat effects, temperament, activation conditions, and mechanical power without duplicating hardcoded UI tables.

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-party-overview.webp" width="46%" alt="DualDex Party overview showing two sanitized live-memory party members in the compact grid">
  <img src="docs/images/live/dualdex-thor-qa-party-detail.webp" width="46%" alt="DualDex Party Pokémon detail showing rarity, nature, ability, experience, and current stats">
</p>

<p align="center">
  <sub><strong>Overview</strong> — live six-slot team · <strong>Pokémon detail</strong> — current individual state</sub>
</p>

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-nature-detail.webp" width="46%" alt="DualDex Nature detail showing Modest stat changes and flavor preferences">
  <img src="docs/images/live/dualdex-thor-qa-ability-detail.webp" width="46%" alt="DualDex Ability detail showing Overgrow effect, activation, power, and known captures">
</p>

<p align="center">
  <sub><strong>Nature detail</strong> — translated stat profile · <strong>Ability detail</strong> — parsed mechanics</sub>
</p>

### Trainer Card

The Trainer Card is a live-memory view rather than a separately maintained profile. It presents trainer identity and sprite, money, play time, Pokédex seen/caught totals, card progression, and badge state through the same snapshot used by the rest of DualDex, with validated recovery reserved for fields that are temporarily unavailable live.

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-trainer-card.webp" width="46%" alt="DualDex Trainer Card showing sanitized identity, play time, Pokédex totals, sprite, and badge state">
  <img src="docs/images/live/dualdex-thor-qa-trainer-progress.webp" width="46%" alt="DualDex Trainer Progress metrics showing game totals and tracked journey counters">
</p>

<p align="center">
  <sub><strong>Trainer Card</strong> — live identity, totals, and badges · <strong>Progress</strong> — game and journey metrics</sub>
</p>

### Thor controls

Readability controls include 85–135% font scaling, three density modes, and high contrast. Setup and recovery remain ordinary packaged routes with the same touch-target and text-floor requirements as the game views.

<p align="center">
  <img src="docs/images/live/dualdex-thor-qa-accessibility.webp" width="46%" alt="DualDex Accessibility settings showing font scale, density, and high-contrast controls">
  <img src="docs/images/live/dualdex-thor-qa-setup.webp" width="46%" alt="DualDex RetroArch setup route showing shared-storage and folder-fallback actions">
</p>

<p align="center">
  <sub><strong>Accessibility</strong> — scale, density, and contrast · <strong>Setup</strong> — bounded connection and folder recovery</sub>
</p>

Every species, sprite, type, move, map tile, ability, and nature shown comes from the ROM-derived catalog. Live-route values come from the sanitized QA snapshot; no bundled Pokédex database, ROM, or synthetic Pokémon artwork is used.

## Why this fork is different

The upstream project attempted to identify Pokémon from screenshots and supply game data through bundled databases or user-authored CSV profiles. This fork treats the ROM and live game state as authoritative.

| Concern | Previous OCR approach | DualDex direction |
| --- | --- | --- |
| Opponent detection | Repeated screenshots and text recognition | Catalog-validated species/form ID from passive RetroArch memory reads |
| Game data | Bundled database and imported CSV profiles | Parsed directly from the active ROM |
| ROM hacks | User creates and maintains a profile | Family competition, structural inference, and automatic generated mapping |
| Type mechanics | Selected external generation chart | Type chart and move mechanics extracted from the ROM |
| Double battles | Infer names from screen regions | Follow the game's selected target cursor |
| Opponent moves | OCR or generic expectations | For uncaught species/forms, only moves actually observed, ranked by use frequency; captured entries need no observation metric |
| Permissions | Accessibility and screenshot access | Localhost RetroArch Network Commands plus one Android All files access grant for multi-folder ROM and SaveRAM discovery |
| Failure behavior | OCR/profile tuning | Independent capability flags; static Pokédex remains usable |

The product contract is simple: a player may need to enable RetroArch Network Commands once, but must never have to enter memory addresses, import cheat codes, prepare CSV files, or create a profile for every mod.

## Companion experience

### Full Pokédex

Outside battle, DualDex is a fully navigable Pokédex built from the active ROM. It can expose every validated species, form, type, stat, sprite, description, evolution, move, learnset, ability, ability description, and type-chart entry that exists in that game. Moves and abilities open focused detail pages instead of making the small lower-screen layout dense. The Stats tab keeps each ROM base stat visible and adds a compact Level 50, zero-training IV/DV projection: a blue typical reference with red/green low/high variance and the resulting numeric range. Organic mode lists only species the player has seen or captured; for an uncaptured species, Entry explains the knowledge lock, Stats and More are disabled, and Moves contains only attacks that species has actually used against the player, frequency-ranked without revealing ROM learn levels or acquisition methods. Capture unlocks every static tab and the complete learnset. Discovered mode may expose the complete ROM index with unseen entries clearly marked. Capability-gated Team and Area filters help the player inspect the current party and track uncaptured species available at the current location. Where the save format records it, a captured marker uses the ROM's artwork for the ball belonging to the best-IV/DV owned individual of that species; otherwise it uses the game's generic Poké Ball artwork without claiming a capture-ball type.

### Automatic battle target

When a supported structure is independently validated, DualDex opens the current opponent automatically and returns to out-of-combat navigation when the battle ends. Generation I Red/Blue/Yellow use a WRAM-shape resolver validated against the labeled Yellow export; Generation II Gold/Silver/Crystal use a catalog-coupled WRAM resolver validated against the labeled Crystal Rev 1 export; and Generation III uses catalog-coupled `BattlePokemon` structure discovery. Generation III double battles follow a uniquely resolved game target and expose a manual target fallback only when the cursor cannot be resolved. Generation II correctly exposes a single opponent because those games do not contain double battles.

The hybrid target page has four focused tabs:

1. **Entry** — ROM-derived Pokédex information filtered by seen/caught knowledge.
2. **Attack** — the selected move's globally known metadata plus its discovered effectiveness against the current target.
3. **Rarity** — a qualitative recruitment signal based on relative level and average DV/IV quality.
4. **Moves** — for an uncaught target, previously observed moves ranked by use frequency with complete ROM-derived move details. Once captured, the frequency metric disappears and the linked Pokédex supplies the complete ROM learnset.

The current opponent's unrevealed four-move loadout is never read into the presentation model.

### Information policies

The parser knows the complete ROM, but the UI controls how much of that truth it reveals.

| Policy | Behavior |
| --- | --- |
| `Discovered` | Expose the complete validated ROM index, clearly mark unseen species, and show deterministic static information immediately. |
| `Organic` (default) | List only seen/caught species, remember facts learned through battle, and unlock complete static species knowledge after capture. |
| `Hidden` | Keep manual Pokédex access but hide battle assistance beyond minimal target identity and caught state. |

Organic ownership comes from checksum-valid SaveRAM plus checksum-validated live party records on structurally supported Gen III layouts. On supported live layouts, opponent moves are remembered by species and ranked by frequency, while a player's consumed PP unlocks the selected move's intrinsic matchup against that target. DualDex computes the result from the parsed move, active type chart, and validated live battler types; it does not infer effectiveness from HP loss. Once a species is captured, its static Pokédex becomes omniscient. Battle discoveries are persisted locally by ROM SHA-256 and survive app restarts.

### Recruitment-oriented rarity

The Rarity tab is intended to answer a practical question: **is this individual worth capturing?** It does not expose exact DVs/IVs and does not include EVs, Stat Experience, encounter rate, capture probability, or trainer importance. The same innate tier describes a save-owned individual when its validated DV/IV data is available.

Its label has two independent parts:

- a contextual level prefix relative to the median level of the player's party; and
- a stable innate tier derived from average IVs, or normalized DVs in Generations I/II.

Examples include `WEAK ACE`, `ORDINARY TRAINED`, and `STRONG STANDARD`—an under-levelled Pokémon can still have exceptional innate potential.

| Relative level | Prefix |
| ---: | --- |
| `-3` or lower | `WEAK` |
| `-2` through `+1` | `ORDINARY` |
| `+2` through `+3` | `COMPETENT` |
| `+4` through `+5` | `STRONG` |
| `+6` or higher | `MAJOR` |

| Average Gen III IV | Innate tier |
| ---: | --- |
| `0–9` | `FODDER` |
| `10–17` | `STANDARD` |
| `18–23` | `TRAINED` |
| `24–27` | `VETERAN` |
| `28–29` | `ELITE` |
| `30–31` | `ACE` |

The qualitative label is deliberately visible before capture in Organic mode because it exists to support recruitment decisions.

### Readable, configurable lower screen

The selected UI direction is a hybrid of full Pokédex navigation and automatic battle context. Each tab has one job instead of cramming every detail onto one screen.

The production settings include:

- `Discovered`, `Organic`, and `Hidden` information policies;
- independent Attack, Rarity, and Observed Moves switches;
- font-size controls;
- `Auto` density by default, with `Comfortable` and `Compact` overrides;
- high contrast;
- automatic target opening and independent Attack, Rarity, and Observed Moves switches for supported live layouts;
- `Docked` or `Overlay` display mode, with Docked as the default.

They also include Game/Dark/Light themes, Auto/Handheld/External display targeting, ruleset selection, scoped catalog maintenance, RetroArch setup/status, SaveRAM diagnostics, issue-report controls, and a bounded overlay-size control. The controller remains focused on the game; the assisted companion window is touch-operated.

Auto density responds to usable display size and Android font scale. It may wrap or scroll secondary content, but it may not make target identity, caught state, or the selected-attack result unreadably small.

## Zero-profile architecture

```mermaid
flowchart TD
    C[Android launcher starts both apps] --> RA[RetroArch]
    C --> DD[DualDex]
    RA -->|GET_STATUS| S[Session monitor]
    S --> R[Granted ROM resolver]
    R --> P[Competitive ROM parser]
    P --> CAT[SHA-256 SQLite catalog]
    RA -->|Periodic SaveRAM file| SV[Checksum-valid save reader]
    SV --> K[Per-save knowledge]
    CAT --> UI[Production Pokédex]
    K --> UI
    RA -->|Validated READ_CORE_MEMORY| BM[Production battle resolver]
    BM --> K
    BM --> UI
    RA -->|Optional diagnostic reads| LAB[Issue report tool]
    LAB --> JSON[User-selected raw JSON export]
```

Official layouts provide fast paths, not a compatibility ceiling. The production parser competes family-compatible ROM structures and independently validates each static dataset. The battle resolver applies the same principle to parsed ROM references and live-memory invariants, then caches only a mapping supported by the active catalog and battler shape.

An internal generated mapping is not a player profile. It is automatically produced, revalidated, and discarded when the ROM, core, or schema changes.

If a modified engine exposes only part of its familiar structure, capabilities degrade independently:

- a parsed Pokédex can work without battle mapping;
- opponent identity can work without automatic double-battle targeting;
- innate rarity can work without matchup context; and
- unknown or conflicting values are withheld rather than guessed.

There is no OCR fallback.

## What is implemented now

The release candidate contains:

- `parser-core`: a pure-Kotlin, Android-portable ROM parser;
- `parser-cli`: a read-only corpus scanner and report generator;
- `save-core`: pure-Kotlin checksum competition and normalized SaveRAM snapshots for Generations I–III;
- `companion-core`: immutable UI state, knowledge policies, recruitment ranking, and best-owned-individual selection;
- `companion-simulator`: deterministic one- or two-opponent battles using only level-plausible moves from the parsed learnsets;
- `companion-server`: a loopback-only HTTP/SSE gateway with direct-ROM and streaming-ZIP loading plus on-demand ROM sprite PNGs;
- `companion-web`: the packaged Preact/TypeScript Thor UI plus a development-only plausible encounter simulator;
- `retroarch-session`: passive Network Command status monitoring, safe configuration editing, ROM-library indexing, and active-content resolution;
- `battle-memory`: pure-Kotlin Generation I/III structure resolution, selected-target interpretation, PP/latch observation tracking, and independent capability states;
- `memory-mapper-lab`: an optional read-only issue-report subsystem that has no production-state output;
- `app`: the replacement Android host with packaged web assets, per-ROM SQLite catalogs and knowledge, setup wizard, normal Docked mode, and an opt-in floating Poké Ball that toggles the same UI in a bounded, resizable 4:3 overlay;
- competitive family parsers for Red/Blue, Yellow, Gold/Silver, Crystal, Ruby/Sapphire, Emerald, and FireRed/LeafGreen;
- dynamic structural resolution for common relocated and expanded Gen III layouts;
- direct ROM and streamed ZIP-entry inputs through the same parser contract;
- progressive background materialization that publishes navigable catalog snapshots before slower extended datasets complete;
- resident runtime-selectable learnset variants, with `Auto` plus diagnostic manual selection and no ROM reparse when switching;
- materialized species, forms, types, stats, sprites, descriptions, evolutions, moves, move descriptions, normalized learnsets, abilities, ability descriptions, encounters, type presentation, type matchups, and capture-ball artwork;
- normalized ROM-derived world maps with intrinsic rasters, semantic location geometry, fog, markers, pan, visible zoom controls, midpoint-preserving pinch zoom, recenter, and direct Area Pokédex navigation;
- dynamic Local-map lighting with compressed four-palette Gen II rasters and structurally proven timed Gen III rasters, rendered lazily from the shared game clock with static fallbacks;
- independent tri-state capability evidence (`AVAILABLE`, `NOT_FOUND`, `NOT_APPLICABLE`);
- checksum-valid per-ROM SaveRAM snapshots persisted in the catalog database, including seen/caught, Team, Area, preferred individual, IV/DV quality, and capture-ball provenance where applicable;
- Area-filter sun/moon markers derived from the parsed encounter windows;
- passive live battle context for validated Generation I and III structures, with frequency-only opponent move history and local discovery persistence;
- one unified transient-state boundary for Trainer Card, Pokédex, Party, progression, battle, Atlas, clock, readiness, bag, and event flags, with field-level live authority and identity-gated recovery;
- Discovered, Organic, and Hidden presentation policies;
- an in-app read-only compatibility report covering static capabilities, current map/runtime state, and map-render cache health, with copy and user-selected privacy-safe JSON export; and
- human-readable and machine-readable compatibility reports.

The current [Gen I–III full-corpus status](docs/reports/2026-08-26-gen1-gen3-full-corpus-status.md), with its [machine-readable evidence](docs/reports/2026-08-26-gen1-gen3-full-corpus-status.json), reruns both the current parser and RC21 against the same exact corpus:

- 333 input rows become **331** unique SHA-256 identities: Gen I 95, Gen II 27, and Gen III 209;
- the current parser selects **256**, leaves 72 explicit no-family matches and two ambiguous, and rejects one oversized identity before parsing;
- all 256 selected catalogs persist and reopen, with zero SQLite errors; and
- on the 23 table types shared with RC21, weighted static coverage rises from **67.13%** to **73.91%**: 125 ROMs improve, 187 remain equal, and 19 decrease.

| Static corpus coverage | Applicable table types | Current coverage | RC21 on the same corpus |
| --- | ---: | ---: | ---: |
| Generation I | 16 | **87.10%** | 58.81% |
| Generation II | 19 | **48.36%** | 48.36% |
| Generation III | 24 | **72.35%** | 71.77% across the 23 shared tables |
| All generations | 24 | **73.80%** across 7,029 applicable ROM/table cells | 67.13% across shared tables |

The one material input regression is tracked as `G3-INPUT-001`: Adventure Red's 33,555,563-byte image exceeds the GBA's 32 MiB addressable ROM window and now fails closed. The other 18 decreases are bounded Local-map fractions of 0.01–0.54 percentage points: their base catalogs still select and persist while malformed maps or POIs are skipped independently. Saffron remains the only selected identity with decoded cross-reference errors, unchanged at 533, under `G3-SAFFRON-001`.

The earlier frozen release gates remain useful narrower controls:

- **50/50** exact first-corpus ROMs select one base family, persist and reopen, and close every catalog reference;
- evolution data is complete and available on **50/50** exact first-corpus ROMs, with zero malformed rows, deterministic semantic edge maps, and exact SQLite reopen parity;
- normalized world maps are completely available on **26/50** exact first-corpus rows, producing 81 regions; the other 24 rows expose no map assets and retain the ordinary Pokédex/Area experience; and
- the five official Generation III ROMs expose source-backed implementation behavior for **77/77** named abilities. Decoded ROM comparisons link 55/77 in Ruby/Sapphire, 58/77 in Emerald, and 57/77 in FireRed/LeafGreen to the typed ability field; independently normalized numeric formulas remain exact Attack ×2 mechanics for abilities 37 and 74. The broader first-50 ARM7 survey remains **38/46** applicable production proofs.

These denominators are deliberately different. A selected base catalog is not counted as a working map or proven mechanic, `NOT_APPLICABLE` is excluded, and a fail-closed optional capability is never counted as a success. The [RC21 table report](docs/reports/2026-08-20-gen1-gen3-table-coverage.md) remains immutable historical evidence rather than being rewritten with current results.

The [unified transient-state audit](docs/reports/2026-08-25-unified-game-state-final-audit.md), with its [machine-readable JSON](docs/reports/2026-08-25-unified-game-state-compatibility.json), separately publishes exact percentages for nine live-state field groups across the official Gen I–III controls, Modern Emerald, Unbound, and Odyssey. It also records live-over-recovery behavior, checkpoint write boundaries, read-window bytes, and zero retained raw-memory bytes; those percentages are not static table or THUMB coverage.

Portable Challenges are measured separately in the [Stage 6 challenge-expansion audit](docs/reports/passive-insights-progress/challenge-expansion-audit.md) and [machine-readable compatibility report](docs/reports/passive-insights-progress/challenge-expansion-compatibility.json). Across the same 14 exact controls, 110/168 combined template slots are applicable (65.48%), 104/110 applicable slots are fully observable (94.55%), and 104/104 observable slots validate (100.00%). Organic mode shows completed tiers and the next unfinished tier, suppresses untouched off-area objectives, and reports both per-objective and overall progress without counting undiscovered entities. Gym Leader roles and Tier 3 mechanic adapters remain explicit `NOT_FOUND` evidence and generate no stock fallback.

The [cross-feature UI conformance audit](docs/reports/passive-insights-progress/ui-conformance-audit.md) validates the established and new passive-insight routes at the production 1024×768 viewport. Its [font matrix](docs/reports/passive-insights-progress/ui-conformance-font-matrix.md), [computed-style evidence](docs/reports/passive-insights-progress/ui-conformance-computed-styles.json), and [screenshot manifest](docs/reports/passive-insights-progress/ui-conformance-screenshots.json) cover 28 routes × 9 themes × 3 font scales: 756/756 rendered rows pass typography, contrast, focus, touch, overflow, scroll ownership, and ordinary-copy gates.

Numeric ability mechanics are tracked separately from names and descriptions. The production resolver follows decoded calls and use-def relationships from parser-selected layouts into typed battle fields, predicates, arithmetic, and writeback. It never substitutes familiar series values, names, hashes, symbols, or fixed routine addresses for missing proof.

Read the player-facing [ROM Hacks Compatibility report](reports/dualdex-rom-hacks-compatibility.md), with its [machine-readable JSON](reports/dualdex-rom-hacks-compatibility.json), for the reviewed first 50 ROMs grouped by generation and engine family. The separate [Parser Compatibility report](reports/dualdex-parser-compatibility.md) and [schema-11 JSON evidence](reports/dualdex-parser-compatibility.json) retain the reviewed RC24 evidence contract; the independent [exact first-50 base release gate](docs/reports/2026-08-13-base-first50-release-gate.md) records RC25's 50/50 result without rewriting that historical report. Optional capability evidence is published independently in the [exact first-50 evolution gate](docs/reports/2026-08-14-first50-evolution-completeness.md), [Celia Pokédex-description closure](docs/reports/2026-08-14-first50-celia-pokedex-descriptions.md), [world-map first-50 release gate](docs/reports/2026-08-13-map-first50-release-gate.md), and [ARM7TDMI first-50 survey](docs/reports/arm7-first50-compatibility-survey.md). The historical [full unique-ROM base audit](docs/reports/2026-08-13-base-full332-compatibility.md) preserves its original routing baseline. Reports contain structural evidence and hashes, but no decoded bulk tables, sprites, ROM bytes, saves, trainer data, or private paths.

SaveRAM evidence is reported separately for [Generations I/II](docs/reports/gen1-gen2-saveram-compatibility.md) and [Generation III](docs/reports/gen3-saveram-compatibility.md). These reports contain no ROM/save bytes, trainer data, or private filesystem paths.

## Project status

| Area | Status |
| --- | --- |
| Static GB/GBC/GBA ROM parser | [Current full corpus](docs/reports/2026-08-26-gen1-gen3-full-corpus-status.md) ([JSON](docs/reports/2026-08-26-gen1-gen3-full-corpus-status.json)): 256/331 selected and persisted/reopened; 72 explicit no-family matches, two ambiguous, one bounded oversized-input error |
| Direct and streamed ZIP input | Implemented |
| Decoded `ParsedCatalog` materialization | Implemented |
| Progressive partial-catalog loading | Implemented in the Android runtime with `Loading... (N%)` state |
| Per-ROM SQLite catalog cache | Implemented and reopen-validated on Android |
| Species and capture-ball sprite decoding | Implemented without AWT/Android dependencies |
| Area encounters, type colors, and type chart | Implemented and reported independently |
| Ability descriptions and focused detail pages | Implemented for validated ROMs |
| Ability implementation | Five official Gen III ROMs: 77/77 source-backed behavior records; binary linkage 55–58/77; normalized numeric mechanics remain 2/77 |
| Portable Challenges | [14-control Stage 6 report](docs/reports/passive-insights-progress/challenge-expansion-compatibility.json): 65.48% applicable template slots, 94.55% fully observable/applicable, 100.00% validated/observable, zero errors |
| ROM-derived maps | Current corpus coverage — World: Gen I 87.37%, Gen II 40.74%, Gen III 54.55%; Local: Gen I 54.74%, Gen II 18.52%, Gen III 59.35%; malformed optional rows fail closed independently |
| Packaged production UI | Implemented and exact-viewport browser/WebView validated |
| Browser-hosted plausible simulator | Retained as a development harness; absent from production assets |
| Loopback HTTP companion server | Implemented and bound only to `127.0.0.1` |
| Runtime memory transport | Implemented as a shared read-only RetroArch adapter used by production battle reads and isolated issue reports |
| Dynamic battle-memory resolver | Generation I–III production shapes implemented; Generation III double targets are inferred where possible; unsupported layouts degrade independently |
| SaveRAM readers and Organic discovery ledger | Implemented and persisted per ROM for Generations I–III, including supported live battle observations |
| Thor-first companion UI and settings | Implemented in the packaged Android companion |
| Passive RetroArch active-ROM activation | Implemented and live-validated against current nightly NCI responses; identical SHA-256 copies resolve deterministically |
| Multi-folder ROM/config/SaveRAM storage | Implemented with Android All files access; SAF folder grants remain fallbacks |
| Optional Docked / resizable 4:3 Overlay Android display modes | Implemented in the RC13 candidate; floating-ball/4:3 smoke passed, physical resizing acceptance pending |
| Replacement of inherited OCR Android app | Implemented through the current staged Android host |
| Final signed candidate target | `v1.1.0-rc.86` contains the completed Thor lower-display remediation and the exact source intended for protected candidate signing and stable `v1.1.0` authorization. Candidate and stable APKs are built only by the protected GitHub workflow; promotion revalidates the signed APK, certificate, provenance, source-bound workflow evidence, and immutable release asset set without replacing any asset. |

## Parser development

Requirements:

- JDK 17
- the included Gradle wrapper

Run the parser tests and install the CLI distribution on Windows:

```powershell
.\gradlew.bat :parser-core:test :parser-cli:test :parser-cli:installDist
```

Scan one or more user-owned ROM directories read-only:

```powershell
.\parser-cli\build\install\parser-cli\bin\parser-cli.bat `
  "D:\path\to\roms" `
  --json "D:\path\to\report.json" `
  --markdown "D:\path\to\report.md" `
  --jobs 8
```

The scanner accepts `.gb`, `.gbc`, and `.gba` files plus matching entries inside ZIP archives. It preflights each archive through the core extraction limits, loads each ROM only when bounded worker capacity is available, and never extracts temporary ROM files. Scans use up to four workers by default; `--jobs N` accepts positive values and caps the effective worker count at eight.

## Run the browser development harness

Requirements:

- JDK 17
- Node.js 20 or newer

Build the web client and install the local server distribution:

```powershell
Set-Location companion-web
npm install
npm test
npm run build
Set-Location ..
.\gradlew.bat :companion-server:test :companion-server:installDist
```

Start the passive loopback server with a direct ROM or ZIP path:

```powershell
.\companion-server\build\install\companion-server\bin\companion-server.bat `
  --rom "D:\path\to\pokemon-rom.zip" `
  --web-root "companion-web\dist"
```

Open `http://127.0.0.1:47831`. Opening `companion-web/index.html` directly is not supported because the UI requires the local catalog API. The development-only left panel shows the loaded archive/inner-ROM identity and can generate deterministic plausible encounters. The packaged Android production build omits that simulator panel. Neither path writes to the ROM.

## Android setup and release identity

The in-app **RetroArch Setup** page requests Android All files access once so sibling GB/GBC/GBA folders and RetroArch SaveRAM can be discovered without selecting every console directory. Android intentionally keeps `Android/data` and `Android/obb` protected: place ROM and save content in public shared storage or use the supported folder pickers rather than expecting access to app-private RetroArch paths. The setup page locates the public `RetroArch/retroarch.cfg`, explains the exact Network Commands and 10-second SaveRAM autosave settings, edits only those approved keys, verifies the saved file, and requests one RetroArch restart only when the file changed. Existing Storage Access Framework folder actions remain available as fallbacks. ROMs and saves remain read-only. When a validated save file changes, DualDex atomically records the current discovery ledger in a portable `<save>.dualdex.json` sibling; sources that cannot support atomic sibling writes use an isolated app-private fallback. A checkpoint is restored only when its ROM hash, save identity, save-file hash, size, and modification time all match, so another playthrough or an older internal ledger cannot leak discoveries into the active game. See the [save-synchronized checkpoint verification](docs/reports/save-synchronized-knowledge-checkpoints.md). If automatic activation is unavailable, manual ROM selection and the last valid cached catalog remain usable.

Production uses package `com.darkaxt.dualdex`; debug builds use `com.darkaxt.dualdex.debug` so they can coexist. Production APKs are signed only by the protected GitHub release workflow. The pinned certificate SHA-256 is [`C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA`](signing/dualdex-release-cert.sha256); the repository contains the public certificate but no keystore or credentials.

## Android display modes

Settings exposes `Docked` and `Overlay`. Docked is the default and uses the normal Android activity. Overlay is explicitly user-enabled, requests Android's `Display over other apps` permission, and moves DualDex into a foreground service with a draggable Poké Ball rendered from the active ROM. Tapping the ball shows or hides the same companion in a 4:3 panel while RetroArch remains focused. A visible resize handle scales the panel between 45% and 100% of its gutter-aware fit without changing the aspect ratio; choosing Docked removes both overlay windows and returns to the normal activity. The overlay remains passive and never injects input or changes emulator state.

Settings also persists the information policy, ruleset, font scale, density, theme, and companion-display target. `Auto` preserves the screen selected by the launcher; `Handheld` requests Android's default display and `External` requests a presentation/non-default display when one exists.

## Optional memory issue report

The issue reporter starts disabled on every app process. Enabling it is the single export confirmation for that session, opens an independent localhost UDP client, and permits only RetroArch `READ_CORE_MEMORY` commands. Disabling or failing it cannot unload or mutate the active catalog, SaveRAM snapshot, production battle reader, or discovery ledger.

Each session can label bounded snapshots as Overworld, Battle Start, Move Selected, Move Executed, Target Changed, Opponent Switched, Battle End, or a custom event. The user-selected JSON export includes core/content identity, descriptors, timestamps, region hashes, Base64 memory bytes, and bounded address-level before/after diffs. These are evidence—not automatically validated field mappings. A battle address becomes a generated mapping only after repeated captures and structural checks agree.

The labeled [Modern Emerald analysis](docs/reports/modern-emerald-memory-mapper-analysis.md) validates the Generation III single-opponent record, level and IV tier, highlighted player move, ROM-derived effectiveness, and Organic opponent move counts from PP decreases. The [Pokémon Yellow analysis](docs/reports/2026-08-10-pokemon-yellow-memory-validation.md) validates shape-based Red/Blue/Yellow addressing, Pikachu acquisition, rival Eevee identity, player PP consumption, and Gen I's executed-move latch fallback. The [Pokémon Crystal Rev 1 analysis](docs/reports/2026-08-10-pokemon-crystal-memory-validation.md) validates Gen II battle identity, selected and executed moves, PP/HP transitions, DV rarity, and automatic exit. The exported bytes remain diagnostic evidence; production resolves the same structures independently from live memory and the parsed catalog.

## Design documents

- [DualDex v1 passive companion specification](docs/superpowers/specs/2026-08-09-dualdex-v1-passive-companion-design.md)
- [DualDex first-release specification](docs/superpowers/specs/2026-08-09-dualdex-first-release-design.md)
- [Current release readiness](docs/current-readiness.md)
- [Historical RC9 / v1.0 requirement matrix](docs/archive/v1-requirement-matrix-rc9.md)
- [Web UI and plausible simulator POC specification](docs/superpowers/specs/2026-08-09-dualdex-web-ui-simulator-poc-design.md)
- [ROM parser and passive companion foundation](docs/superpowers/specs/2026-08-08-dualdex-rom-parser-companion-design.md)
- [ROM Hacks Compatibility](reports/dualdex-rom-hacks-compatibility.md)
- [Parser Compatibility](reports/dualdex-parser-compatibility.md)
- [Current Gen I–III full-corpus status](docs/reports/2026-08-26-gen1-gen3-full-corpus-status.md)
- [Historical RC21 Gen I–III table coverage](docs/reports/2026-08-20-gen1-gen3-table-coverage.md)
- [Save-synchronized knowledge checkpoint verification](docs/reports/save-synchronized-knowledge-checkpoints.md)

## Relationship to Kanto Gear

[Kanto Gear](https://github.com/AverageConsumer/kanto-gear) demonstrates how well a contextual companion can use a handheld's second screen. DualDex takes inspiration from its single-purpose pages, automatic battle context, return-to-browsing flow, themes, information levels, display selection, and optional controller navigation.

The architecture is different. Kanto Gear integrates deeply with Gen1Recomp and can move game controls and UI between displays. DualDex is a separate passive Android companion for RetroArch, targets multiple GB/GBC/GBA engine families, and never controls the game. No Kanto Gear code or artwork is included here.

## Privacy and safety

- ROM parsing, memory reads, catalogs, generated mappings, and discovery history remain local.
- DualDex uses only status and read-memory commands on localhost.
- It does not upload ROMs, saves, screenshots, extracted assets, or memory samples.
- It never sends write-memory, cheat, input, save-state, or content-control commands.
- Sanitized parser diagnostics contain structural metadata and validation outcomes, not ROM bytes or private save content.
- Raw mapper sessions are exported only through an explicit user-selected document after the lab has been enabled; they are never attached to CI reports or releases.
- Android cloud backup is disabled so private catalogs, save-derived state, and mapper sessions remain on the device unless the user explicitly exports a mapper document.

## Release engineering

Production APKs are signed only by the protected GitHub `release-signing` environment. A release run must start from a new `v1.*` source tag, completes all non-secret tests before the signing job can access the keystore, verifies the pinned certificate fingerprint before and after signing, and creates a non-replacing GitHub Release with checksums and provenance. Local Gradle release builds remain unsigned.

Downloaded candidates are independently checked with `tools/android/validate-signed-candidate.ps1` before installation. The tool validates SHA-256, `com.darkaxt.dualdex`, version name/code, and signer fingerprint, and requires an explicit `-Install` switch plus a named `DedicatedAvd` or `Thor` target.

## Project lineage and license

This work is based on [Enrique Paulino's original DualScreenDex project](https://github.com/enrique-paulino/DualScreenDex). The repository remains available under the [MIT License](LICENSE).

DualDex is an unofficial, free fan project. It is not affiliated with or endorsed by Nintendo, Game Freak, The Pokémon Company, RetroArch, Libretro, Kanto Gear, or the referenced ROM-hack projects. Pokémon and related names belong to their respective owners.
