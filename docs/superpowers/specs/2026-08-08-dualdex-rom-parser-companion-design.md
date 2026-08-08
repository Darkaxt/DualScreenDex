# DualDex ROM Parser and Passive RetroArch Companion Design

**Status:** Approved for specification  
**Date:** 2026-08-08  
**Initial implementation scope:** ROM parser and compatibility-report harness only

## 1. Summary

DualDex is a passive second-screen Pokédex companion for Pokémon RPGs running in RetroArch on a dual-screen Android device. Cocoon launches RetroArch and DualDex together. DualDex waits for RetroArch to load a supported Game Boy, Game Boy Color, or Game Boy Advance ROM, parses that ROM without modifying it, and later reads live battle state through RetroArch's read-only network memory interface.

The first implementation milestone is deliberately narrower than the complete product: build a reusable pure-Kotlin ROM parser and a command-line harness, run it against the user's Pokémon ROM collection, and produce an evidence-based compatibility report. No OCR, Android UI rewrite, or runtime memory mapper will be implemented in that milestone.

The parser is not expected to accept or reject a ROM as one indivisible result. Every extracted dataset is independently validated and represented by a capability flag. A ROM hack can therefore provide a useful Pokédex even if, for example, descriptions or sprites cannot be located.

## 2. Goals

### Complete product

- Provide a full, manually navigable Pokédex whenever the active ROM has a compatible species catalog.
- Automatically show the current opponent during battle.
- In double battles, change the displayed opponent when the player selects a move target.
- Extract names, types, stats, moves, learnsets, evolutions, sprites, descriptions, abilities, and type effectiveness from the user's ROM whenever each dataset can be validated.
- Record moves that opponents actually execute and show them per species, ordered by observed frequency.
- Optionally show the quality of an opponent's DVs in Generations 1 and 2 or IVs in Generation 3 when live memory exposes them reliably.
- Remain entirely local, offline, passive, and read-only.

### Parser-first milestone

- Identify official and derived Pokémon RPG ROMs for GB, GBC, and GBA.
- Compete the plausible engine-family parsers against an unknown ROM and select the best structurally validated match.
- Extract every dataset that can be validated without assuming that all original offsets survived a ROM hack.
- Run against all Pokémon-named ROMs and archives in the three console directories under `H:\My Drive\Roms`.
- Produce machine-readable JSON and human-readable Markdown compatibility reports without including extracted copyrighted text, images, or ROM bytes.
- Keep the parser core directly reusable by the Kotlin Android application.

## 3. Non-goals

- Modifying, patching, rewriting, or redistributing ROMs.
- Bundling Pokémon ROM data, sprites, descriptions, or proprietary databases in the APK or repository.
- Adding dedicated engine parsers for Pokémon spin-offs such as Pinball, Mystery Dungeon, Puzzle Challenge, and Trading Card Game in the first milestone. The harness must still scan them, report that no mainline family matched, and retain any independently validated capability flags.
- Promising compatibility with every ROM hack. Major engine rewrites may expose only some capabilities or none.
- Reading an opponent's unrevealed moves. Move history contains only moves observed being executed.
- Controlling RetroArch, enabling cheats, injecting input, writing memory, changing saves, or manipulating save states.
- Implementing the runtime memory mapper or final companion UI during the parser-first milestone.

## 4. Supported platform and engine scope

“GB to GBA” means these three hardware platforms:

- Nintendo Game Boy
- Nintendo Game Boy Color
- Nintendo Game Boy Advance

The supported Pokémon RPG engine families are:

| Parser family | Official entries and revisions |
| --- | --- |
| Red/Blue | Pokémon Red and Blue |
| Yellow | Pokémon Yellow |
| Gold/Silver | Pokémon Gold and Silver |
| Crystal | Pokémon Crystal |
| Ruby/Sapphire | Pokémon Ruby and Sapphire |
| Emerald | Pokémon Emerald |
| FireRed/LeafGreen | Pokémon FireRed and LeafGreen |

Each official revision has a fingerprint/profile inside its family parser. Closely derived hacks use the same family logic but must rediscover and validate relocated structures.

The current local corpus contains all 11 English mainline entries, three in-scope 32 MiB GBA derivatives—Modern Emerald, Sword and Shield Ultimate Plus, and Unbound—and six Pokémon spin-offs with no dedicated parser in the first milestone. The spin-offs remain valid scan inputs: they are expected not to receive a false mainline-family label, while capability detection remains independent. There are no GB or GBC ROM-hack samples in the current library, so compatibility claims for derived GB/GBC games require later fixtures.

## 5. User experience

### Startup and waiting

Cocoon launches RetroArch and DualDex at the same time. DualDex does not launch or manage RetroArch. It starts its local status heartbeat and waits for RetroArch to report loaded content.

When no supported game is running, DualDex presents either:

- the full Pokédex for the last selected parsed ROM; or
- a waiting/library screen if no ROM has been parsed yet.

### ROM activation

When RetroArch reports a GB, GBC, or GBA game, DualDex uses the content basename and CRC to resolve the corresponding file or inner ZIP entry in its indexed ROM locations. A previously parsed content hash loads immediately from the catalog cache. A new content hash starts parser competition and displays parsing progress by dataset.

### Battle behavior

Once runtime mapping exists in a later phase:

- a single battle automatically opens the opponent's Pokédex page;
- a double battle shows the opponent currently selected by the move-target cursor;
- changing the selected target changes the displayed page;
- users may browse elsewhere without losing battle context, with a visible action returning to the current target;
- ending the battle returns to normal Pokédex navigation.

## 6. System architecture

```text
RetroArch status -> Session Monitor -> ROM Resolver
                                           |
ROM file/ZIP entry -> Parser Competition -> Parsed Catalog
                                           |
RetroArch memory -> Runtime Mapper --------+
                                           |
                                    Battle Interpreter
                                           |
                              +------------+------------+
                              |                         |
                     Observation Ledger             DualDex UI
```

### 6.1 Session Monitor

The monitor uses RetroArch's UDP Network Control Interface on localhost. It sends only:

- `GET_STATUS` to detect loaded content, its platform, basename, and CRC; and
- `READ_CORE_MEMORY` to read console addresses once a runtime mapper is available.

It never sends write, cheat, input, content-control, or save-state commands. Network polling is a lifecycle heartbeat, not a cancellation timeout. Loss of replies returns the session to a disconnected state without crashing or changing RetroArch.

### 6.2 ROM Resolver

The resolver indexes `.gb`, `.gbc`, and `.gba` files plus matching entries inside ZIP archives. It records the content CRC, SHA-256, uncompressed size, platform header, outer URI/path, and inner archive entry. It does not extract permanent duplicate ROM files.

### 6.3 Parser Core

The parser core is a pure Kotlin/JVM library with no Android framework dependencies. It consumes an immutable random-access `RomSource` and produces a normalized `ParsedCatalog`, a `RomCapabilities` set, the selected engine ancestry, and structured diagnostics.

The same library is called by:

- a Kotlin/JVM CLI during compatibility research and automated testing; and
- the Android app through an Android-backed `RomSource` using `ContentResolver` or ZIP streams.

### 6.4 Catalog Store

Successful parse results are cached by SHA-256 plus parser-schema version. Parser changes invalidate only incompatible cache versions. Caches remain private to DualDex and are never treated as redistributable game databases.

### 6.5 Runtime Mapper

The later runtime mapper uses the selected engine family as a starting hypothesis, but independently validates every live address. It combines known official layouts, references decoded from ROM code, and live-memory invariants. A valid static catalog does not imply valid battle-memory addresses.

### 6.6 Battle Interpreter

The interpreter converts memory snapshots into domain events such as battle start/end, opponent appearance, target change, executed move, switch, and faint. UI and history components never interpret raw addresses.

### 6.7 Observation Ledger

The ledger stores observations per ROM hash and exact species/form identifier. For each encountered individual, a particular move contributes at most once. The displayed frequency is:

```text
individuals of this species observed using the move
---------------------------------------------------
total encountered individuals of this species
```

An unseen species has no inferred moves. Its list begins after the first executed move is observed.

## 7. Capability contract

There is no global “fully compatible” switch. The parser and runtime mapper independently expose granular capabilities.

### 7.1 ROM capabilities

- `SPECIES_CATALOG`
- `SPECIES_NAMES`
- `SPECIES_TYPES`
- `TYPE_CHART`
- `BASE_STATS`
- `SPRITES`
- `POKEDEX_DESCRIPTIONS`
- `EVOLUTIONS`
- `MOVE_CATALOG`
- `MOVE_DETAILS`
- `LEARNSETS`
- `ABILITIES`

### 7.2 Runtime capabilities

- `BATTLE_STATE`
- `OPPONENT_SPECIES`
- `MULTIPLE_OPPONENTS`
- `SELECTED_TARGET`
- `EXECUTED_MOVE`
- `OPPONENT_LEVEL`
- `OPPONENT_HP_STATUS`
- `DV_IV_QUALITY`

### 7.3 Feature dependencies

| User-facing feature | Required capabilities |
| --- | --- |
| Pokédex navigation | `SPECIES_CATALOG` |
| Weakness/resistance display | `SPECIES_TYPES`, `TYPE_CHART` |
| Complete move reference | `MOVE_CATALOG`, `MOVE_DETAILS` |
| Species learnset | `SPECIES_CATALOG`, `MOVE_CATALOG`, `LEARNSETS` |
| Automatic battle page | `BATTLE_STATE`, `OPPONENT_SPECIES` |
| Double-battle target switching | automatic battle page plus `SELECTED_TARGET` |
| Observed move frequency | `OPPONENT_SPECIES`, `EXECUTED_MOVE`, corresponding ROM move entry |
| DV/IV quality | `OPPONENT_SPECIES`, `DV_IV_QUALITY` |

Each capability carries internal validation evidence and diagnostics. The UI consumes only the boolean availability and a short reason when unavailable.

## 8. Parser competition and selection

### 8.1 Fast path

An exact SHA-256 match against a supported official revision selects its profile immediately. The parser still validates the resulting datasets so corrupted or incorrectly labeled files do not become trusted catalogs.

### 8.2 Competitive probe path

For an unknown ROM, all platform-compatible family parsers run a read-only probe. A probe must not perform the complete extraction. It locates enough anchors to judge ancestry and dataset feasibility.

The probe score is structural rather than raw byte similarity:

| Evidence | Maximum points |
| --- | ---: |
| Platform and cartridge-header compatibility | 10 |
| Engine-specific code signatures and pointer references | 20 |
| Species-name table structure | 15 |
| Base-stat table structure and type references | 15 |
| Move table and learnset consistency | 15 |
| Cross-table pointer and identifier integrity | 15 |
| Successful sample sprite discovery/decompression | 10 |

A candidate is selectable only when it:

- passes the platform/header gate;
- finds at least two independent engine anchors;
- scores at least 75 out of 100; and
- leads the runner-up by at least 10 points.

If no candidate satisfies all ancestry rules, the ROM is reported as having no mainline-family match. If candidates are too close, ancestry is ambiguous and no family is selected automatically. Neither condition clears independently validated capability evidence. Thresholds are versioned parser configuration and must appear in the compatibility report so later empirical changes remain auditable.

Capability evidence may be retained from a non-winning probe only when that probe passed the platform gate and established at least two independent structural anchors. If multiple probes validate the same capability at conflicting locations, that capability remains unavailable with a conflict diagnostic rather than selecting one silently.

Raw binary similarity may be recorded as secondary evidence but never decides the parser. Expanded ROMs, recompiled code, unchanged artwork, or large text replacements can make byte similarity misleading.

### 8.3 Full extraction

The selected family parser attempts each dataset independently:

1. Use an exact official offset when the full hash matches.
2. Otherwise search for family-specific code references, pointer tables, and structural anchors.
3. Decode the candidate table using the family data layout.
4. Validate the decoded records and all cross-references.
5. Enable only the corresponding capability when validation succeeds.

Failure in one dataset does not invalidate unrelated successful datasets.

### 8.4 Generated profiles

A successful unknown-ROM parse emits a generated profile containing discovered offsets, record layouts, counts, evidence, and parser version. It is cached locally by ROM hash. Generated profiles are data, not executable plugins, and are revalidated before reuse after parser-schema changes.

## 9. Dataset validation

Validation must prevent plausible but incorrect output.

### Species catalog

- Count is positive and bounded by what the platform's identifier width can represent.
- IDs are unique within the normalized catalog.
- Names terminate correctly under the selected character encoding.
- A high proportion of name records contain valid characters and nonempty output.
- Base-stat and Pokédex-order references resolve to known species IDs.

### Types and type chart

- Species type identifiers point into the discovered type set.
- Type-effectiveness entries have valid attacker, defender, and multiplier encodings.
- Table termination or length is unambiguous.
- Invalid type references disable only the affected type capabilities.

### Base stats

- Record stride is consistent across the table.
- Stat values and catch/growth fields fit their encoded ranges.
- Species count agrees with the associated name or order table.

### Moves and learnsets

- Move IDs are unique and bounded.
- Names, PP, power, accuracy, type, target, priority, and effect fields fit the selected engine layout.
- Learnset move IDs resolve into the move catalog.
- Levels are ordered or otherwise valid for the engine's encoding.
- Hacks with a physical/special split expose category only if a category field or validated derivation is present.

### Evolutions

- Target species and referenced items/moves resolve to known records.
- Evolution methods and parameters match an allowed family encoding.
- Termination and per-species grouping are unambiguous.

### Sprites

- Pointers remain inside ROM bounds.
- A representative sample decompresses without overrun.
- Dimensions and decoded pixel/palette sizes match the platform format.
- Full sprite extraction begins only after the sample passes.

### Descriptions and abilities

- Pointer tables, terminators, character encoding, and record counts validate independently.
- `ABILITIES` is absent rather than emulated for official engines that do not implement abilities.

## 10. Normalized parser model

The normalized model preserves original numeric IDs so live memory can join directly to parsed data.

```text
ParsedCatalog
  romIdentity
  engineFamily
  capabilities
  speciesByInternalId
  movesByInternalId
  typesByInternalId
  typeChart
  diagnostics

Species
  internalId, dexNumber, formId, name
  typeIds, baseStats, catchRate, growthRate
  abilityIds, evolutions, learnset
  spriteRefs, description

Move
  internalId, name, typeId, category
  power, accuracy, pp, priority, target, effect
```

Fields unsupported by an engine remain absent. They are never synthesized from a modern external database because ROM-derived behavior is authoritative for hacks.

## 11. Runtime memory design

This section governs a later implementation phase.

RetroArch `GET_STATUS` identifies loaded content. `READ_CORE_MEMORY` reads console addresses through the active core's Libretro system-memory map. Core support is capability-tested at runtime; it is not inferred solely from a core name.

The mapper needs only live state that cannot be obtained from the ROM:

- battle-active state;
- opponent battler slots and species/form IDs;
- currently selected opponent target;
- executed move and its attacker;
- opponent level, HP, and status when available;
- DVs for Generation 1/2 or IVs for Generation 3 when reliably exposed.

EVs and Generation 1/2 Stat Experience describe training, not innate rarity, and are excluded from the quality indicator.

Each address is validated using range, structure, and state-transition invariants. Reads must be internally consistent across a snapshot; transient impossible combinations are discarded rather than rendered. Failure removes the affected runtime capability while leaving the static Pokédex usable.

## 12. Error handling and diagnostics

- Malformed ZIP files, encrypted entries, truncated ROMs, and invalid headers become per-file report entries; they do not abort a library scan.
- Parser exceptions are contained per candidate and per dataset.
- Ambiguous ancestry never selects a winner silently.
- Capability diagnostics include the failed validator and relevant offsets/counts but no extracted text, sprite data, or large byte samples.
- Cached catalogs are ignored when the content hash or parser schema differs.
- Loss of RetroArch status or memory access clears live state without deleting catalogs or observations.
- The application never falls back to OCR.

## 13. Privacy, safety, and licensing

- ROM access is read-only.
- Parsing, memory reads, catalogs, and observation history remain on device.
- No ROM or extracted asset is transmitted.
- Reports may contain filenames, hashes, sizes, engine ancestry, counts, offsets, scores, and capability results. They must not contain ROM bytes, extracted descriptions, or decoded sprites.
- DualDex's MIT repository must not copy GPLv3 implementation code from Universal Pokémon Randomizer ZX. That project may be used as behavioral prior art and an external comparison target. Parser implementation should be original and based on documented platform formats, permissively licensed decompilation projects, and independently verified structural rules.

## 14. Parser-first implementation and compatibility study

### 14.1 Language and packaging

Implement the parser as a pure Kotlin/JVM module. A small Kotlin/JVM CLI module supplies filesystem and report-generation adapters. Android later supplies URI and archive adapters around the same parser interfaces.

Python, native binaries, and JNI are excluded from the parser core because they would require a second implementation or a more complex APK integration.

### 14.2 Corpus scan

The harness scans these roots recursively and read-only:

- `H:\My Drive\Roms\Nintendo - Game Boy`
- `H:\My Drive\Roms\Nintendo - Game Boy Color`
- `H:\My Drive\Roms\Nintendo - Game Boy Advance`

It evaluates direct ROM files and ROM entries inside ZIP archives. Pokémon-named spin-offs are included to prove that ancestry detection does not produce false mainline matches and that capability flags remain populated independently. Non-ROM artwork, videos, manuals, saves, and save states are ignored.

### 14.3 Report contents

Each ROM result contains:

- source filename and archive entry;
- platform, size, CRC32, and SHA-256;
- cartridge title and game code where available;
- exact-profile match, if any;
- every candidate parser score and failed hard gate;
- selected family and runner-up margin;
- discovered counts and offsets for each dataset;
- capability flags with pass/fail evidence;
- deterministic diagnostics and total processing duration.

The Markdown summary groups results into official ancestry matches, derived ancestry matches, partial capability coverage, ambiguous ancestry, no mainline-family match, and malformed/unreadable input. The JSON report retains complete structured evidence.

### 14.4 Acceptance criteria

- Every official English mainline sample selects its correct family and revision.
- Every official sample enables all capabilities that genuinely exist in that engine.
- Every derived sample either selects the correct ancestor with independently validated capabilities or returns ambiguous/no-family-match ancestry while retaining explicit capability flags; false confident matches are release-blocking defects.
- Every spin-off sample is scanned without crashing or being mislabeled as a mainline engine; every capability is reported explicitly, including capabilities that were not detected.
- Repeated runs against unchanged files produce identical parser selection, counts, capability flags, and diagnostics apart from processing duration.
- No input ROM, ZIP, save, or sidecar file is modified.
- The parser report is sufficient to decide which family and dataset locators should be implemented or strengthened next.

## 15. Testing strategy

### Unit tests

- Header parsing and platform detection.
- GB/GBC banked-pointer and GBA little-endian pointer decoding.
- Character-table decoding and termination.
- Every dataset validator with valid, boundary, truncated, and adversarial synthetic fixtures.
- Candidate scoring, hard gates, minimum score, and runner-up margin.
- Capability dependency calculation.
- ZIP-entry and direct-file `RomSource` parity.

### Golden structural tests

Local golden manifests record only hashes, expected ancestry, counts, offsets, and capabilities for user-owned ROMs. ROM content is never committed. Tests skip clearly when the private corpus is unavailable.

### Corpus tests

Run the CLI against the complete Pokémon subset and review all ambiguous or partial results. An official-ROM regression or a new false positive fails the parser milestone.

### Future runtime tests

Recorded, sanitized memory-transition fixtures will test battle start/end, singles, doubles target changes, switches, executed moves, and transient inconsistent snapshots. End-to-end device validation must reproduce the actual Cocoon plus RetroArch dual-screen flow.

## 16. Delivery sequence

1. **Parser study:** pure Kotlin parser core, CLI harness, local corpus scan, and compatibility report.
2. **Parser hardening:** strengthen locators and validators based on report evidence until official coverage is complete and hack results are honest.
3. **Runtime mapper:** passive RetroArch session monitor, core memory capability probe, and per-family live battle adapters.
4. **Companion UI:** replace OCR/accessibility plumbing with catalog navigation and battle context.
5. **Integrated device validation:** Cocoon launches RetroArch and DualDex; validate supported ROM detection, double-battle targeting, observations, and failure recovery on the actual handheld.

Only step 1 is authorized as the next implementation task.

## 17. Success definition

The parser-first milestone succeeds when the compatibility report answers, with reproducible evidence, how much useful Pokédex data each local official or modified ROM can provide using competitive family parsers. The complete product succeeds when DualDex can use those validated catalogs and read-only RetroArch memory to present the correct opponent and target context without OCR, ROM modification, hidden-move disclosure, or emulator control.

## 18. References

- [DualScreenDex](https://github.com/enrique-paulino/DualScreenDex)
- [Kanto Gear](https://github.com/AverageConsumer/kanto-gear)
- [RetroArch Network Control Interface](https://docs.libretro.com/development/retroarch/network-control-interface/)
- [Libretro memory-monitoring compatibility](https://docs.libretro.com/guides/memorymonitoring/)
- [Universal Pokémon Randomizer ZX](https://github.com/Ajarmar/universal-pokemon-randomizer-zx)
- [pret Pokémon disassembly/decompilation projects](https://github.com/pret)
