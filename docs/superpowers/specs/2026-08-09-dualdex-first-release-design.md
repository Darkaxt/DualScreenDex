# DualDex First Production Release Design

| Field | Value |
| --- | --- |
| Status | Approved design; pending written-spec review |
| Date | 2026-08-09 |
| Release | DualDex `1.0.0` |
| Production application ID | `com.darkaxt.dualdex` |
| Primary runtime source | Parsed ROM plus periodically refreshed RetroArch SaveRAM |
| Optional diagnostic source | Read-only RetroArch memory snapshots, disabled by default |
| Builds on | [ROM parser design](2026-08-08-dualdex-rom-parser-companion-design.md), [browser POC design](2026-08-09-dualdex-web-ui-simulator-poc-design.md), and [broader live-companion design](2026-08-09-dualdex-v1-passive-companion-design.md) |

## 1. Release definition

DualDex 1.0.0 is a passive, local, second-screen Pokédex companion for mainline-family Pokémon games from Game Boy through Game Boy Advance running in RetroArch. It parses the player's own ROM into a complete game-specific catalog and refreshes player knowledge from RetroArch's ordinary battery-save file. It does not depend on OCR, screenshots, Accessibility, cheat codes, memory writes, hand-authored ROM profiles, or a bundled modern Pokédex database.

This document deliberately narrows the first production release relative to the broader live-companion design. Live battle targeting and automatic memory-layout inference remain the intended next stage, but they are not required for 1.0.0. The release must provide a useful Pokédex even if no core memory can be read.

The optional **Memory Mapper Lab** ships in the same APK for collecting read-only evidence for that next stage. It is disabled by default, does not provide production battle features, and cannot gate or modify the main Pokédex pipeline.

This document is authoritative when it conflicts with the release boundary in `2026-08-09-dualdex-v1-passive-companion-design.md`. The older document remains the product direction for the later live-memory milestone.

## 2. Product contract

### 2.1 Zero per-ROM profiles

- Official and structurally compatible derived ROMs compete through the existing family parsers automatically.
- The player never enters addresses, imports CSV files, chooses a base-game profile, or repairs a parser result manually.
- Exact official layouts may be fast paths, but every dataset retains its own validation and capability state.
- A modified ROM may expose a partial catalog. Available datasets remain usable while missing datasets are reported honestly.

### 2.2 ROM-authoritative catalog

The active ROM is the source of truth for:

- species and forms;
- names and internal identifiers;
- front sprites and capture-ball artwork;
- Pokédex descriptions;
- types, type presentation, and type charts;
- base stats;
- moves, move mechanics, descriptions, and acquisition methods;
- learnsets and alternative rulesets;
- abilities and decoded ability mechanics;
- evolutions; and
- encounter areas and level ranges.

DualDex does not silently substitute an external database when ROM data is missing. Static capability states are `AVAILABLE`, `NOT_FOUND`, or `NOT_APPLICABLE`, so reports and UI can distinguish a failed extraction from a feature the game does not contain.

### 2.3 Save-authoritative player state

The most recent checksum-valid SaveRAM snapshot is the source of truth for:

- seen and caught Pokédex flags;
- current party;
- current map or area when the save format preserves it;
- owned individuals in the party and storage boxes;
- the best innate-quality owned individual per species or form;
- that preferred individual's capture ball where the game stores it; and
- the local save identity used to isolate Organic-mode knowledge.

Save states are not used as the normal player-state source. DualDex reads ordinary RetroArch SaveRAM files such as `.srm` and never modifies them.

### 2.4 Passive and local

- ROMs, saves, parsed catalogs, settings, and diagnostics remain on the device.
- DualDex never uploads ROM bytes, extracted assets, trainer data, saves, or memory dumps.
- The normal app never writes to a ROM, SaveRAM file, core memory, or RetroArch input state.
- Configuration changes are limited to explicit, user-approved RetroArch settings in a user-selected public folder.
- Cocoon may launch RetroArch and DualDex together, but DualDex has no Cocoon API dependency and behaves the same with any launcher.

### 2.5 Main-path independence from memory mapping

The following invariant governs the release architecture:

> The general Pokédex must load and remain usable without issuing a single core-memory command.

Turning the Memory Mapper Lab off prevents all `READ_CORE_MEMORY` or equivalent requests. Turning it on cannot change the parsed catalog, selected ruleset, save snapshot, knowledge policy, or production UI state. Mapper failure is visible only inside its debug page.

## 3. Release goals

### 3.1 General Pokédex

- Browse the validated catalog of a direct or ZIP-contained GB, GBC, or GBA ROM.
- Search by ROM-native species name or identifier.
- Show compact All, Caught, Seen, Team, and Area filters when their dependencies are available.
- Navigate between list, species detail, move detail, ability detail, and evolution entries.
- Render ROM-derived species and ball sprites; the production deliverable contains no emoji substitutes.
- Use ROM-derived type colors when validated, then a detected-family palette, then an accessible deterministic fallback.
- Show the loaded ROM name and short CRC in the persistent app header or status area.
- Keep primary information readable on the AYN Thor's small lower display.

### 3.2 Species pages

Species detail retains the browser POC's four-tab structure:

1. **Entry** — identity, ROM sprite, seen/caught icons, types, Pokédex description, height, and weight when permitted and available.
2. **Stats** — ROM base stats and the already approved IV/DV effect visualization. A blue reference line represents the typical Level 50, zero-training value; red and green overlays show the innate low/high variance and the numeric range. This is explicitly not a wild-level chart.
3. **Moves** — normalized learnset rows, ruleset selection, acquisition methods, and links to focused move pages. Duplicate initial and later learn events are rendered as one move with combined acquisition metadata rather than repeated rows.
4. **More** — abilities, evolutions, and encounter locations. Ability and evolution rows are navigable. Locations include wild level ranges and encounter rates when parsed.

Unavailable tabs are disabled or omitted rather than displaying duplicate placeholder content. In particular, Organic uncaptured entries do not render the same knowledge-withheld card under both Entry and Stats.

### 3.3 Move and ability reference

A move detail page shows every validated global property that is constant within the active ROM and ruleset:

- type and damage class;
- power;
- accuracy or always-hit behavior;
- PP;
- priority;
- decoded effect description; and
- additional parser-extracted mechanics such as status chance, stat stages, recoil, drain, multi-hit bounds, weather interaction, or other structured values when confidently decoded.

An ability detail page shows the ROM description plus decoded structured mechanics where available. Raw string-table identifiers, music labels, or untranslated pointer targets must never be presented as move or ability effects. An empty ability ID such as `#0` is filtered unless the ROM explicitly defines it as a real ability.

Move properties are global to the ROM/ruleset; they do not change between Pokémon. A move page may therefore be opened from any captured species learnset or other permitted catalog location.

### 3.4 Save-backed recruitment information

For each owned species/form, DualDex selects the individual with the best validated innate statistics:

- Generation III ranks the exact six-IV sum.
- Generations I and II rank the normalized five-value DV vector consisting of derived HP, Attack, Defense, Speed, and Special once.
- Ties resolve deterministically by party position followed by box and slot order.
- Level, EVs, Stat Experience, and current calculated stats do not affect innate-quality selection.

The preferred individual supplies the caught marker's ball artwork in Generation III when the individual structure retains the capture-ball ID. Generation I and II report capture-ball identity as `NOT_APPLICABLE` and use the ROM's generic Poké Ball sprite without implying that it was the original capture ball.

The Stats page may show the preferred owned individual's qualitative innate tier, but not exact IV/DV values by default:

| Normalized average | Tier |
| ---: | --- |
| `0–9` | `FODDER` |
| `10–17` | `STANDARD` |
| `18–23` | `TRAINED` |
| `24–27` | `VETERAN` |
| `28–29` | `ELITE` |
| `30–31` | `ACE` |

The live opponent level prefixes `WEAK`, `ORDINARY`, `COMPETENT`, `STRONG`, and `MAJOR` are deferred because 1.0.0 has no production live-opponent source.

### 3.5 Lazy activation

The UI does not wait for a full ROM parse before showing useful state:

1. Open the last compatible SQLite catalog immediately when one exists.
2. Display the active ROM identity and a translucent `Loading`, `Loading.`, `Loading..`, `Loading...` status while required work continues.
3. Publish catalog sections after their transaction commits and capability validation completes.
4. Remove the loading state only after the cache is durable and the current page's required data is available.

Loading animation is a presentation heartbeat, not a cancellation timeout. Parser work is not terminated because it exceeds an arbitrary duration.

## 4. Information policies

The parser and SaveRAM reader always produce the most complete validated local model they can. The selected policy controls presentation only.

| Policy | Pokédex list | Uncaught species | Captured species |
| --- | --- | --- | --- |
| `Discovered` | Complete validated ROM index | Show validated static ROM information with seen/unseen state | Show validated static ROM information and owned-state additions |
| `Organic` (default) | Only species/forms marked seen or caught by the current save | Show identity and the approved knowledge-withheld Entry state; disable Stats and More; Moves is empty in 1.0.0 because no production live observer can create qualified observations | Unlock all validated static catalog tabs and deterministic ROM knowledge |
| `Hidden` | Only captured species/forms | Uncaught species are excluded from general browsing | Show validated static catalog and owned-state additions |

Because 1.0.0 deliberately has no production live battle observer, it cannot create opponent-move or effectiveness observations. An uncaught Organic Moves tab is therefore empty. The Memory Mapper Lab does not create those observations and does not feed this tab.

The player can switch policies at any time. Policy changes do not reparse the ROM or SaveRAM and do not duplicate SQLite data.

## 5. Supported content and capability model

### 5.1 Engine scope

The first release supports mainline-family engines and structurally compatible derivatives for:

- Generation I on Game Boy;
- Generation II on Game Boy Color; and
- Generation III on Game Boy Advance.

Pinball, Trading Card Game, Puzzle Challenge, and other non-mainline official games are outside this release and excluded from the compatibility report. Mystery Dungeon remains a possible v2 engine family and is also excluded rather than reported as a failed mainline match.

### 5.2 Direct files and ZIP entries

The ROM input contract accepts direct `.gb`, `.gbc`, and `.gba` files plus those entries inside ZIP archives. ZIP entries are streamed without extracting a permanent ROM copy. The immutable selected entry may be materialized in memory because cross-linked ROM tables require random access.

The Android host uses `ContentResolver` streams and does not require a raw filesystem path. The same pure-Kotlin parser contract remains usable by the CLI, loopback server, tests, and Android app.

### 5.3 Rulesets

Every independently validated ruleset found in a ROM is stored in the same catalog database. Switching rulesets changes only the active ruleset key.

- `Auto` is the default.
- Without a validated live ruleset selector, `Auto` uses the parser's primary/default ruleset.
- Settings exposes every validated alternative for manual selection.
- Switching does not reread the ROM or recreate the database.
- A future memory source may replace the manual selection only after its selector validates.

### 5.4 Capability evidence

Every parsed or save-derived dataset stores:

- `AVAILABLE`, `NOT_FOUND`, or `NOT_APPLICABLE`;
- the selected resolver/family;
- confidence and validation evidence;
- source offsets or structural identifiers suitable for local diagnostics; and
- a parser/save-schema version.

A numeric family score cannot override a failed dataset validator. A `100` family match does not imply that every possible feature exists, and the UI/report must not render `-` where `N/F` or `N/A` is known.

## 6. Android architecture

```mermaid
flowchart TD
    SAF[Persisted folder grants] --> CFG[RetroArch configuration adapter]
    SAF --> RI[ROM index]
    SAF --> SD[Save discovery]
    RA[RetroArch] -->|GET_STATUS only| SM[Session monitor]
    CFG -->|restart and verify| SM
    SM --> RR[ROM resolver]
    RI --> RR
    RR --> PC[Pure-Kotlin parser competition]
    PC --> DB[(ROM catalog SQLite)]
    SD --> SP[Save parser competition]
    SP --> SS[Last valid save snapshot]
    DB --> KP[Knowledge policy]
    SS --> KP
    KP --> API[Loopback companion API]
    API --> WEB[Bundled WebView UI]
    RA -. debug setting enabled .->|READ_CORE_MEMORY| ML[Memory Mapper Lab]
    ML --> DX[Private diagnostic store/export]
```

### 6.1 Module boundaries

The production implementation uses focused modules rather than adapting the inherited OCR activity in place:

- **parser-core** — pure-Kotlin ROM parsing and catalog materialization.
- **save-core** — new pure-Kotlin SaveRAM identification, checksum validation, and normalized snapshots for Gen I–III families.
- **catalog-store** — Android SQLite persistence and cache migration.
- **retroarch-session** — status/config transport that never exposes memory-write commands.
- **companion-core** — knowledge policy, preferred-individual selection, and presentation models.
- **companion-server** — loopback API and packaged frontend serving.
- **memory-mapper-lab** — optional read-only diagnostic transport and dump storage, with no dependency from the preceding production modules.
- **app** — permissions, setup, lifecycle, display selection, WebView host, and settings.

`companion-core` may depend on normalized catalog and save interfaces. It must not depend on a raw memory transport or address type. The Memory Mapper Lab may inspect session identity but cannot publish a production presentation model.

### 6.2 Replacement of the inherited application

The Android host removes or disables the inherited OCR direction:

- bundled CSV Pokédex assets;
- ML Kit text recognition;
- Accessibility service declaration and permission flow;
- screenshot capture;
- manual ROM profile creation; and
- the inherited global SQLite schema seeded from CSV files.

The existing parser, server, simulator, and browser POC are retained as foundations. The encounter simulator remains a development/test fixture and is not reachable in the production APK.

### 6.3 Loopback web host

The first APK reuses the approved browser UI rather than rewriting it as native views:

- The production frontend is built into versioned application assets.
- A loopback-only server binds to `127.0.0.1` on an available local port.
- The embedded WebView opens only the app's local origin and approved external help links.
- API state comes from the real ROM catalog and SaveRAM snapshot; simulator controls are absent.
- Server startup failure produces a native recovery page rather than a blank WebView.
- No external network listener is created.

The API remains transport-neutral so a later native UI can replace the WebView without changing parser or save behavior.

## 7. First-run setup and RetroArch configuration

### 7.1 Folder grants

The setup wizard requests persistent Storage Access Framework read/write access to the public RetroArch directory, normally:

```text
/storage/emulated/0/RetroArch
```

The grant covers public configuration, saves, playlists, and related RetroArch content under that tree. If the ROM library lives elsewhere, the wizard requests a second persistent read-only grant to the smallest useful ROM-library folder. It does not request broad storage, Accessibility, screenshot, root, ADB, or `Android/data` access.

The setup page reports each grant and allows it to be replaced later.

### 7.2 Public configuration editing

DualDex discovers candidate public `retroarch.cfg` files inside the selected tree and presents the exact file it will update. With explicit setup consent, it changes only known keys required by the companion:

- `network_cmd_enable = "true"`;
- `network_cmd_port = "55355"` unless a supported custom port is already selected;
- a nonzero `autosave_interval`, defaulting to `10` seconds when absent or disabled;
- a public `savefile_directory` under the selected RetroArch folder when the effective save directory is otherwise inaccessible; and
- core-sorted save behavior when already used by the installation.

The editor preserves comments, unknown keys, line endings, and unrelated values. It replaces the final effective occurrence of an exact key or appends a missing key. It does not translate legacy-looking names by guesswork or rewrite the entire file.

Configuration writes use a recoverable sibling-document replacement supported by the selected document provider. The app retains the original bytes until the replacement verifies, then removes the transient recovery document. It does not accumulate undated backup files.

### 7.3 Effective-file verification

RetroArch's stock Android default may reside under its app-specific external-files directory, while a launcher can pass a public override through the `CONFIGFILE` intent extra. DualDex therefore does not assume that a public file wins solely because it exists.

After updating the public file, the wizard asks the player to restart RetroArch through the normal launcher. A status heartbeat then verifies:

- the Network Command Interface is reachable;
- `GET_STATUS` returns a valid state;
- queryable effective settings match the requested public save directory and port where supported; and
- an active ROM can be identified when content is loaded.

Successful verification records the selected configuration URI and a non-secret fingerprint. If verification fails, the wizard reports that the selected file was not proven active and shows the exact RetroArch menu breadcrumbs for the remaining manual setting. It never claims setup succeeded based only on a successful file write.

### 7.4 Lifecycle detection

The app does not require another application's PID. Android process enumeration is not a production control-flow dependency. RetroArch session state is inferred from its localhost control interface:

- reachable and valid response — running/configured;
- temporarily unreachable — stopped, starting, or unavailable;
- reachable with no content — RetroArch active, no supported game loaded.

Heartbeats monitor state without imposing cancellation timeouts on parsing, save reading, or app startup.

### 7.5 Cocoon boundary

Cocoon is one possible dual-launch mechanism. End-to-end testing may use it because that reflects the Thor workflow, but:

- no Cocoon package, API, PID, broadcast, or private file is required;
- DualDex does not ask Cocoon for the ROM or configuration path; and
- launching RetroArch and DualDex separately produces the same behavior.

## 8. Active ROM resolution and catalog cache

### 8.1 Session identity

When Network Commands are available, `GET_STATUS` supplies the active system, content basename, and CRC32. DualDex combines those values with the indexed, user-granted ROM sources.

Resolution order is:

1. exact cached SHA-256 catalog previously associated with the reported CRC32 and size;
2. exact direct-file CRC32 match in granted ROM roots;
3. ZIP central-directory entry CRC32 and supported extension match;
4. unambiguous basename plus platform match, followed by streamed CRC verification; and
5. one-time user file selection when automatic resolution remains ambiguous.

A selection is remembered by content identity. CRC32 is useful for RetroArch interoperability but is not the authoritative cache key.

When Network Commands are unavailable or no game is loaded, the player may browse the last cached catalog or manually choose a ROM. The main Pokédex therefore remains usable even when live session detection is absent.

### 8.2 Database identity

Each distinct parsed ROM has one SQLite database keyed by:

```text
SHA-256 ROM digest + parser-schema version
```

The database header also stores CRC32, byte size, console, selected family, ROM title, parser version, and creation/completion state. CRC32 may be used in the filename for readability but cannot be the only identity boundary.

All validated rulesets and local ROM-derived images share the same database. A ruleset switch changes one preference row rather than generating another catalog.

### 8.3 Transaction and recovery rules

- A new catalog writes to an incomplete cache identity and becomes active only after required schema and capability transactions commit.
- The previous valid catalog remains usable while a replacement parses.
- App interruption leaves an identifiable incomplete cache that is resumed when safe or rebuilt without affecting other ROMs.
- Parser-schema changes migrate compatible data or invalidate only the affected cache.
- Cache deletion is an explicit Settings action and never deletes ROMs, saves, or knowledge ledgers.

## 9. Save discovery, polling, and parsing

### 9.1 Save discovery

The effective public save root is obtained from the verified RetroArch configuration when possible. DualDex supports ordinary RetroArch layouts including:

- one shared save directory;
- saves sorted by core;
- saves sorted by content directory; and
- saves written beside content when that accessible mode is explicitly configured.

Candidate names derive from the active content basename. A candidate is accepted only after its size, engine-family structure, slot generation, and checksums agree with the active ROM family. Filename similarity alone is insufficient.

If multiple valid candidates remain, Settings shows their paths and freshness and asks for one selection. The choice is remembered for that ROM identity and can be changed without reparsing the ROM.

### 9.2 Polling contract

The app observes the selected document's metadata on a lifecycle-aware monitoring heartbeat. On a possible change it:

1. reads the complete candidate through `ContentResolver` into a new immutable buffer;
2. validates size, save-family structure, active slot/section generation, and all applicable checksums;
3. parses a new normalized snapshot away from the active UI state;
4. commits the snapshot and knowledge update in one local database transaction; and
5. publishes one presentation-state change.

A short or partially flushed read is rejected. The last checksum-valid snapshot remains visible and the next heartbeat retries. There is no crash, arbitrary cancellation timeout, or exposure of half-updated party/box state.

The UI shows the last successful SaveRAM modification and refresh times. It warns when the effective autosave interval is disabled or could not be verified, but it does not label a file stale solely because its timestamp has not changed: without live battle/gameplay inspection, DualDex cannot know whether a save-relevant change occurred. It does not force RetroArch to write SaveRAM.

### 9.3 Save-family parsing

The pure-Kotlin save parser competes compatible family readers rather than selecting only by extension:

- Generation I readers validate the applicable primary/backup blocks and checksums.
- Generation II readers validate the applicable region/version blocks, checksums, Pokédex flags, party, boxes, and current map.
- Generation III readers select the newest complete valid save slot, reconstruct section order, and decode encrypted/checksummed Pokémon substructures without modifying them.

Each reader emits independent save capabilities. A valid seen/caught block can remain available even if a hack's expanded boxes are not yet understood.

### 9.4 Normalized save model

```text
SaveSnapshot
  romIdentity
  saveIdentity
  saveGeneration
  lastModified
  currentArea?
  seenSpecies[]
  caughtSpecies[]
  party[]
  storedIndividuals[]
  capabilities[]

OwnedIndividual
  stableLocation
  speciesId
  formId?
  level
  isEgg
  ivsOrDvs?
  captureBallId?
```

The save identity uses stable local trainer/save fields plus the ROM SHA-256. Raw trainer names are not required in presentation or diagnostics.

### 9.5 Save-derived filters

- **Caught** intersects the ROM catalog with validated caught flags.
- **Seen** includes seen or caught entries and excludes completely unknown species in Organic mode.
- **Team** uses the validated current non-Egg party.
- **Area** uses the saved current map plus parsed encounter tables and shows the species available there, including uncaught tracking permitted by the selected policy.

If current area is unavailable or the ROM's encounter map cannot join to the save map, Area is disabled with a capability explanation rather than guessed from save filename or last browsed location.

## 10. Memory Mapper Lab

### 10.1 Purpose and default state

The Memory Mapper Lab accelerates future live-memory support by collecting reproducible evidence from RetroArch cores. It is part of the DualDex APK because the device cannot display two companion applications simultaneously.

- The setting is under an explicit Debug section.
- It is off by default after every fresh install.
- Enabling it displays a privacy warning and the active core/content identity.
- Disabling it stops memory reads and closes the lab without affecting the main Pokédex.

### 10.2 Strict isolation

The lab has its own storage namespace and API. It may read:

- session/core identity;
- RetroArch memory descriptors;
- bounded read-only memory regions; and
- user-entered labels for capture moments.

It may write only app-private diagnostic sessions or an explicit user-selected export. It cannot write:

- the ROM catalog database;
- SaveRAM snapshots;
- seen/caught or Organic knowledge;
- active ruleset preferences;
- simulator state; or
- any RetroArch memory/input command.

No production module imports mapper address models. Automated architecture tests enforce this dependency direction.

### 10.3 Capture workflow

The user controls RetroArch normally. The lab provides labeled snapshot actions suitable for later comparison:

- `OVERWORLD`;
- `WILD BATTLE START`;
- `MOVE SELECTED`;
- `MOVE EXECUTED`;
- `TARGET CHANGED`;
- `OPPONENT SWITCHED`;
- `BATTLE END`; and
- a free-form local label.

It can compare snapshots, identify changed ranges, and export a manifest containing ROM/core identities, memory descriptors, labels, hashes, and selected raw regions. Raw dumps may include private save/trainer data, so export requires an explicit confirmation and is never included in GitHub Actions artifacts or automatic diagnostics.

### 10.4 Failure behavior

Unsupported commands, missing descriptors, short reads, core changes, or invalid sessions end only the current mapper capture and explain the cause inside the lab. They do not navigate away from the Pokédex, invalidate a cache, or change setup completion.

## 11. UI and settings

### 11.1 Thor-first layout

- The general Pokédex is the home page.
- List and detail are separate screens.
- There is no persistent bottom navigation bar.
- Back navigation is explicit and preserves search/filter position.
- Species, move, ability, and evolution links open focused pages.
- Auto density is the default and accounts for usable display size plus Android font scale.
- Only designated lists and long text regions scroll; headers, primary identity, and selected tab remain visible.
- The production UI contains no simulator seed, opponent generator, selected-attack reference input, or `SIMULATED` label.

### 11.2 Status presentation

The app presents, without crowding the content:

- loaded ROM title and CRC32;
- RetroArch connected, disconnected, or no-content state;
- catalog ready/loading/partial state;
- save matched, last refreshed, stale, or unavailable state; and
- active ruleset when more than one exists.

An unavailable save does not cover the Pokédex with an error. It removes save-dependent filters and Organic knowledge while retaining Discovered browsing.

### 11.3 Settings pages

Settings includes:

- information policy: Discovered, Organic, Hidden;
- ruleset: Auto or a validated manual variant;
- font size and density: Auto, Comfortable, Compact;
- theme and game-matching presentation;
- display mode: Docked or Overlay, with Docked as the default;
- companion display target: Auto, Handheld, External when Android exposes those displays;
- RetroArch folder and ROM-library grants;
- verified configuration and Network Command status;
- active ROM, catalog, and SaveRAM diagnostics;
- catalog-cache controls and mapper-diagnostic export/clear controls;
- the disabled-by-default Memory Mapper Lab toggle; and
- release version, production package identity, and signing-certificate fingerprint.

Reset actions are scoped and explicit. Clearing a catalog cache does not touch ROMs or SaveRAM, and clearing mapper sessions does not affect catalogs or save-derived state. DualDex offers no control that purports to reset the save's seen or caught flags.

### 11.4 Docked and overlay display modes

Docked is the default and renders DualDex as the normal Android activity. Overlay is an explicit opt-in for testing beside RetroArch and for handhelds without a second display. Selecting it requests `Display over other apps` only when needed, starts a foreground service, backgrounds the normal activity, and shows a draggable Poké Ball using the active ROM's parsed ball artwork when available. Tapping the ball toggles the same companion UI in a fixed 4:3 panel while RetroArch remains the focused application.

Selecting Docked from the floating panel removes the panel and ball and returns to the normal DualDex activity. The overlay does not cover the whole display, intercept controls outside its visible bounds, inject input, or modify RetroArch state. Permission denial or service failure reverts the setting to Docked and cannot affect the parsed catalog, save polling, or manual Pokédex.

## 12. Application identity, signing, and releases

### 12.1 Permanent identities

The inherited upstream application ID `com.enrpau.dualscreendex` is not used for fork releases.

- Production: `com.darkaxt.dualdex`
- Local/emulator debug: `com.darkaxt.dualdex.debug`

The production ID is fixed before the first signed APK. Debug builds can coexist with production and cannot update or overwrite it.

### 12.2 Signing-key lifecycle

One RSA 4096-bit signing key with a validity of at least 100 years is generated once before the first release under alias `dualdex-release`. It is never regenerated for later versions.

Private material is not committed. The repository contains only:

- the exported public certificate;
- its expected SHA-256 certificate fingerprint; and
- signing instructions that do not contain secrets.

The protected GitHub `release-signing` environment is the production signing authority and holds the keystore plus its credentials. DualDex does not require a user-managed recovery phrase and provides no local production-signing path.

### 12.3 GitHub-only production signing

Production signing occurs only in the GitHub Actions release workflow. A protected GitHub environment named `release-signing` holds:

- `DUALDEX_RELEASE_KEYSTORE_B64`;
- `DUALDEX_RELEASE_STORE_PASSWORD`;
- `DUALDEX_RELEASE_KEY_ALIAS`; and
- `DUALDEX_RELEASE_KEY_PASSWORD`.

Base64 is transport encoding; secrecy comes from GitHub environment-secret encryption and access controls.

Pull-request and ordinary branch workflows never receive these secrets. The release workflow:

1. runs only from a pre-created `v1.*` source tag that matches the requested release identity and protected-environment tag policy;
2. runs the complete non-secret test/build pipeline;
3. enters the protected signing environment only after tests pass;
4. reconstructs the keystore in the ephemeral runner workspace;
5. verifies alias and expected certificate fingerprint before signing;
6. signs the already-tested unsigned production APK handed off by the non-secret job;
7. verifies package ID, version code/name, APK signature schemes, and signer fingerprint with Android build tools;
8. generates SHA-256 checksums and build provenance;
9. creates a new non-replacing RC prerelease and attaches the signed APK, checksums, public certificate, compatibility report, provenance, and release notes; it may start as a draft for independent verification, then be published so the validation device can install it, but it is never marked stable or latest; and
10. rejects the final tag until a committed authorization record proves the GitHub-signed candidate passed both dedicated-AVD and Thor validation.

The ephemeral keystore is never uploaded as an artifact. Runner teardown removes it.

### 12.4 Version and update contract

- Release tags use `vMAJOR.MINOR.PATCH-rc.N` for candidates and `vMAJOR.MINOR.PATCH` for the final release.
- `versionName` matches the tag without `v`.
- `versionCode` is derived as `major * 1,000,000 + minor * 10,000 + patch * 100 + qualifier`; RC qualifiers are 1–98 and the final qualifier is 99, so every accepted candidate-to-final update is monotonic.
- The workflow rejects a mismatched tag, reused/lower version code, wrong package ID, or wrong signer.
- A signed release candidate must update the previous production-signed candidate in place while preserving settings, folder grants where Android permits, catalog caches, and save associations.

## 13. Test environments

### 13.1 Dedicated Android emulator

The existing `emulator-5554` is outside the project test scope and must remain untouched.

A new persistent AVD is created specifically for DualDex, with:

- a unique name such as `DualDex_RA_API35`;
- its own AVD and virtual-device storage on `D:`;
- no symlinks to the existing AVD;
- an independent RetroArch installation and configuration;
- private local copies of permitted ROM/save test inputs; and
- clean, configured, and upgrade-test snapshots.

Automation never invokes an ambiguous `adb` command. It resolves the dedicated AVD by name, verifies the returned serial, and supplies `adb -s <serial>` for every operation. It likewise addresses the Thor only as `adb -s bfa98654` after verifying the model.

### 13.2 Emulator responsibilities

Automated emulator validation covers:

- first-run folder grants and denial/retry paths;
- RetroArch public-configuration patch and restart verification;
- direct and ZIP ROM resolution;
- fresh parse, progressive loading, cache reopen, and ruleset switch;
- SaveRAM discovery, partial-write rejection, refresh, and autosave-configuration warnings;
- Organic/Discovered/Hidden presentation;
- debug and GitHub-signed production package coexistence;
- signed in-place production updates;
- WebView/server startup and recovery; and
- Memory Mapper Lab enable/disable isolation.

The developer handles these repetitive tests. The user is not expected to perform every build/install cycle manually.

### 13.3 Physical Thor responsibilities

The AYN Thor remains the authoritative device for:

- real lower-screen size and readability;
- dual-display launch and display targeting;
- interaction with the installed RetroArch build and mGBA save layout;
- real Storage Access Framework behavior for the public RetroArch folder;
- lifecycle behavior when both displays are active;
- Cocoon dual-launch validation without a Cocoon dependency; and
- final hands-on acceptance of a GitHub-signed release candidate.

Device mutation occurs only during the explicit install/test stage. ROMs, saves, and existing RetroArch configuration are not deleted or replaced as test cleanup.

## 14. Testing strategy

### 14.1 ROM parser regression

- Preserve the existing pure-Kotlin parser test suite.
- Pin every supported family resolver and independent dataset validator.
- Validate direct-file and streamed ZIP equivalence.
- Run the private mainline-family ROM corpus and publish a names-first compatibility report that excludes out-of-scope spin-offs.
- Report each dataset as found, not found, or not applicable.
- Confirm that Modern Emerald and other source-available derived samples resolve every claimed dataset from their actual structures.
- Never commit ROMs, extracted descriptions, or sprite dumps as fixtures.

### 14.2 Save parser tests

- Minimal original synthetic fixtures for every checksum, slot-generation, Pokédex-flag, party, box, map, DV/IV, and capture-ball decoder.
- Corrupt checksums, truncated files, half-written sections, stale backup slots, expanded hack layouts, invalid species, eggs, empty records, and duplicate owned species.
- Exact tier boundaries and deterministic preferred-individual ties.
- Generation I/II capture ball `NOT_APPLICABLE` behavior.
- Candidate discovery across shared, core-sorted, content-sorted, and content-directory layouts.

The private local integration corpus at `H:\My Drive\Roms\Android\saves\mGBA` currently includes three 128 KiB Pokémon SaveRAM samples:

- `Pokemon - Emerald Rogue.srm`;
- `Pokemon - Modern Emerald Version v3.5 (USA, Europe).srm`; and
- `Pokémon - Odyssey (USA).srm`.

These provide immediate derived-Gen-III evidence for slot selection, checksums, flags, party/box decoding, IVs, and capture-ball fields. Other non-Pokémon `.srm` files in that directory may be used only as negative family-detection fixtures; they do not appear in the Pokémon compatibility report. Private real saves are never committed, copied into build outputs, or uploaded as workflow artifacts.

### 14.3 Catalog and knowledge tests

- SHA-256 identity separates CRC collisions and different hacks with similar titles.
- Cache schema changes cannot expose partial transactions as ready.
- Ruleset switching reuses the same database.
- Discovered, Organic, and Hidden policies obey section 4 exactly.
- Captured state unlocks all permitted static catalog information.
- Uncaught Organic Stats and More are disabled and do not duplicate Entry.
- Mapper sessions cannot add seen/caught flags, observations, or catalog rows.
- Reset controls affect only their named storage domain and cannot alter save-backed seen/caught state.

### 14.4 Setup and session tests

- Persisted folder grant success, denial, revocation, and replacement.
- Public config patch preserves unrelated content and cleans its transient recovery document.
- Verification distinguishes a written candidate from the effective RetroArch configuration.
- Network unavailable, no content, unsupported system, content change, and reconnect.
- No PID query is required for correct behavior.
- Cached/manual Pokédex remains available without Network Commands.

### 14.5 UI and accessibility tests

- Exact Thor lower-display viewport and Android font-scale matrix.
- Auto, Comfortable, and Compact density.
- No unintended horizontal overflow or page-level vertical overflow.
- Long ROM-native names, descriptions, location names, abilities, and move mechanics.
- Correct open/slashed-eye and ROM-derived ball artwork instead of text badges or emoji.
- Navigable move, ability, evolution, and Pokédex links.
- All feature/capability combinations, including no SaveRAM and partially parsed catalogs.
- No simulator-only controls in production assets.

### 14.6 Release-pipeline tests

- Branch and pull-request jobs cannot access production signing secrets.
- Wrong keystore, alias, fingerprint, package ID, tag, or version code fails closed.
- `apksigner` verifies the produced APK for all supported Android versions.
- Release asset checksum matches the downloaded file.
- GitHub-signed candidate updates the previous GitHub-signed candidate without data loss.
- Draft assets contain no keystore, secret, ROM, save, raw memory dump, or private corpus report path.

## 15. Failure and recovery behavior

### No RetroArch folder grant

Open the last cached catalog or ROM picker. Explain which automatic setup/save features are unavailable. Do not request Accessibility or show a blank screen.

### Public configuration is not effective

Keep the chosen file unchanged after its successful patch, report that runtime verification failed, and show manual RetroArch breadcrumbs. Manual ROM browsing and cached catalogs remain available.

### Active ROM cannot be resolved

Show basename, CRC, and searched grants; allow one-time file selection. Do not parse an ambiguous same-name file.

### ROM parser is partial

Activate every independently validated dataset. Show capability diagnostics for missing pages. Do not import external replacement data.

### Save missing or invalid

Keep static Discovered browsing. Hide or disable save-dependent filters and show the last valid refresh state. Never reset Organic knowledge from a corrupt transient read.

### Save changes during read

Reject the candidate, retain the last valid immutable snapshot, and retry on the next monitoring heartbeat.

### Loopback UI host fails

Show a small native recovery page with restart, diagnostics, and cache-safe retry. Do not leave an empty WebView.

### Mapper fails

End the debug capture only. The main Pokédex and SaveRAM monitor continue unchanged.

### Content changes

Clear session-specific presentation, resolve the new ROM, open its cache or parse it, then locate its save. Never join one ROM's save or knowledge to another ROM by basename alone.

## 16. Privacy, safety, and licensing

- All network control traffic is localhost-only.
- No telemetry or cloud account is included.
- WebView navigation outside app assets is blocked except explicit user-opened documentation links.
- Save-derived trainer identifiers are hashed into local identity keys and are not shown in ordinary diagnostics.
- Raw memory export requires a privacy warning because dumps may contain trainer/save data.
- DualDex exposes no memory-write, cheat, input-injection, or save-state command in production or debug transports.
- The repository may use public disassemblies and decompilations as structural references while keeping implementation original and respecting their licenses.
- Kanto Gear and UnboundDex remain product/data-structure references; their code and assets are not copied unless a compatible license and explicit attribution permit a separately reviewed use.
- No ROM, extracted catalog database, or copyrighted sprite pack is bundled with the APK or repository.

## 17. First-release acceptance criteria

DualDex 1.0.0 is ready for publication only when all of the following are true:

1. The production application ID is `com.darkaxt.dualdex`; local debug builds use `com.darkaxt.dualdex.debug`.
2. The GitHub release workflow alone signs production APKs with the pinned certificate, and a signed candidate updates its predecessor in place.
3. The existing `emulator-5554` remains untouched and all automated Android work uses the dedicated DualDex AVD by verified serial.
4. A fresh user can grant the public RetroArch folder, approve the exact configuration edit, restart RetroArch, and see whether the edited file was proven effective.
5. DualDex identifies an active supported ROM through RetroArch status plus user-granted content, or permits manual/cached browsing when status is unavailable.
6. Direct and ZIP-contained supported ROMs produce the same validated catalog and reuse a SHA-256-keyed SQLite cache after first parse.
7. Species sprites, ball artwork, entries, types, stats, moves, learnsets, abilities, evolutions, and encounters render from the ROM whenever their capabilities are available; no emoji or bundled fallback Pokédex is used.
8. A checksum-valid SaveRAM refresh updates seen, caught, Team, Area, preferred owned individual, qualitative IV/DV tier, and capture-ball presentation where applicable.
9. A partial or invalid SaveRAM write never replaces the last valid snapshot or crashes the app.
10. Discovered, Organic, and Hidden policies match section 4; captured species are statically omniscient, while uncaught Organic entries do not leak full catalog details.
11. The POC's approved focused move/ability/evolution navigation and small-screen layout survive the APK integration without simulator controls or overflow.
12. Network Commands, save mapping, or core-memory capability failures never prevent manual or cached general Pokédex access.
13. The Memory Mapper Lab is disabled by default, performs only read-only operations when enabled, never feeds production state, and can fail or be disabled without changing the Pokédex.
14. No OCR, screenshot capture, Accessibility service, CSV profile flow, cheat, input injection, ROM write, SaveRAM write, or core-memory write remains reachable.
15. Automated tests pass, the named private corpus report contains only in-scope mainline-family ROMs, and the GitHub-signed candidate passes dedicated-emulator and physical-Thor validation.
16. Settings defaults to Docked and can opt into a draggable ROM-derived Poké Ball that toggles a fixed 4:3 overlay while RetroArch remains focused; returning to Docked removes the overlay and restores the normal activity.

## 18. Deferred after 1.0.0

The following remain explicit later milestones:

- automatic live battle detection and opponent identity;
- selected move and target tracking, including double battles;
- live matchup discovery and observed opponent-move history;
- live opponent IV/DV rarity and relative-level prefix;
- dynamic active-ruleset selection from memory;
- generated runtime profiles promoted from validated mapper evidence;
- Area-filter encounter rows with ROM-styled sun/day-only or moon/night-only markers. No marker means both only when the ROM's time-slot capability is `Available`; `Not Found` or `Not Applicable` must remain distinguishable and no emoji artwork is permitted;
- a user-resizable 4:3 overlay that defaults to the widest inferred letterbox or pillarbox edge instead of covering the game, uses core/screen aspect only as an initial hint, preserves readable and system-inset-aware limits, persists size and position per display, and provides an explicit reset action;
- Mystery Dungeon or other non-mainline engine families.

The first live-memory milestone must consume validated normalized events and knowledge interfaces; it may not bypass the isolation boundaries established here.

## 19. References

- [RetroArch Network Control Interface](https://docs.libretro.com/development/retroarch/network-control-interface/)
- [RetroArch Android configuration selection](https://github.com/libretro/RetroArch/blob/master/pkg/android/phoenix-common/src/com/retroarch/browser/preferences/util/UserPreferences.java)
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions secrets](https://docs.github.com/en/actions/concepts/security/secrets)
- [GitHub deployment environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [DualScreenDex upstream](https://github.com/enrique-paulino/DualScreenDex)
- [DualDex fork](https://github.com/Darkaxt/DualScreenDex)
- [Kanto Gear](https://github.com/AverageConsumer/kanto-gear)
- [UnboundDex](https://github.com/alpacaonthehill/unbounddex)
- [Parser compatibility report](../../../reports/dualdex-parser-compatibility.md)
