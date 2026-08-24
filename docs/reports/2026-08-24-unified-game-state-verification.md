# Unified transient game-state verification — 2026-08-24

## Scope

This report verifies the single `UnifiedGameStateDecoder` boundary against 11 exact official Gen I–III ROM identities plus Modern Emerald 3.5, Unbound 2.1.1.1, and Odyssey 4.1.1. ROM bytes were read in place and were not copied into the repository or report. The identity manifest records byte length, internal title, game code, revision, and SHA-256; this report intentionally excludes local paths, save bytes, player names, Trainer IDs, money values, and other playthrough data.

Each percentage below is live-memory availability for that exact control. `0%` means the game or the structurally proven runtime descriptor does not publish that field through the current live path; it does not relabel a missing field as “not applicable,” and it does not imply that validated save recovery is unavailable.

## Exact live-field matrix

| Control | Trainer | Pokédex | Party | Battle | Area | Position | Clock | Bag | Event flags |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Red | 0% | 0% | 0% | 100% | 100% | 100% | 0% | 0% | 0% |
| Blue | 0% | 0% | 0% | 100% | 100% | 100% | 0% | 0% | 0% |
| Yellow | 0% | 0% | 0% | 100% | 100% | 100% | 0% | 0% | 0% |
| Gold | 0% | 0% | 0% | 100% | 100% | 100% | 100% | 0% | 0% |
| Silver | 0% | 0% | 0% | 100% | 100% | 100% | 100% | 0% | 0% |
| Crystal | 0% | 0% | 0% | 100% | 100% | 100% | 100% | 0% | 0% |
| Ruby Rev 2 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% |
| Sapphire Rev 2 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% |
| Emerald | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% |
| FireRed Rev 1 | 100% | 100% | 100% | 100% | 100% | 100% | 0% | 100% | 100% |
| LeafGreen Rev 1 | 100% | 100% | 100% | 100% | 100% | 100% | 0% | 100% | 100% |
| Modern Emerald 3.5 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% |
| Unbound 2.1.1.1 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% |
| Odyssey 4.1.1 | 100% | 100% | 100% | 100% | 100% | 100% | 0% | 100% | 100% |

The three official Gen I games have no in-game clock. Official FireRed and LeafGreen also have no RTC clock. Gold, Silver, and Crystal publish their supported phase value rather than invented numeric hours. Ruby, Sapphire, and Emerald publish numeric RTC time without requiring day/night-palette code. Modern Emerald additionally proves its `06:00`/`21:00` schedule. Unbound publishes its source-backed CFRU numeric clock from the compiled `struct Clock`; its custom phase boundaries remain withheld because the current resolver has not independently proved them from the binary. Odyssey exposes no independently proven clock descriptor from its currently available documentation-only source material.

Across the eight Gen III controls, Trainer, Pokédex, Party, battle, area, position, bag, and event flags are each `100%` live. Numeric clock availability is `62.50%` across all eight Gen III controls and `100%` across the five Gen III controls with a source-proven live clock descriptor. Across all 14 controls, battle, area, and position are each `100%` live.

## Read and memory measurements

| Control | Logical windows | Unique bytes per complete sample | Passive extended-save bytes | Raw bytes retained after decode |
| --- | ---: | ---: | ---: | ---: |
| Ruby Rev 2 | 5 | 17,838 | 0 | 0 |
| Sapphire Rev 2 | 5 | 17,838 | 0 | 0 |
| Emerald | 7 | 20,250 | 0 | 0 |
| FireRed Rev 1 | 6 | 20,205 | 0 | 0 |
| LeafGreen Rev 1 | 6 | 20,205 | 0 | 0 |
| Modern Emerald 3.5 | 7 | 20,250 | 0 | 0 |
| Unbound 2.1.1.1 | 8 | 32,150 | 11,940 | 0 |
| Odyssey 4.1.1 | 6 | 20,205 | 0 | 0 |

The memory reader coalesces two overlapping four-byte logical windows into one six-byte physical read and scatters the result back into both logical buffers. Adjacent four-byte SaveBlock pointer globals likewise become one eight-byte physical read. `Reading.totalBytes` reports unique physical bytes rather than double-counted logical bytes. The singleton retains one immutable translated live snapshot and one validated recovery projection; reflection and post-decode mutation regressions prove that it retains no ROM, SaveRAM, WRAM, EWRAM, or logical-region `ByteArray`.

The Unbound sample is larger because its validated extended-save descriptor adds exactly 11,940 bytes. Adding the five-byte normalized clock window adds exactly five requested bytes and no retained raw bytes.

## Authority, recovery, and privacy

- Live fields win independently over recovery fields; recovery fills only unavailable fields.
- ROM identity mismatch rejects recovery before any field is merged.
- Disconnecting removes stale live authority and falls back only to matching validated recovery.
- One owner instance supplies Trainer Card, Pokédex, Party, progression, battle, Atlas location/position/clock/readiness, bag, and event flags.
- Gen I/II battle, area, coordinates, observed-move behavior, and Gen II phase remain available through that same interface.
- Opponent move privacy remains observation-based; the migration does not expose an unread opponent loadout.
- Ordinary UI routes receive translated values only and do not receive provenance, addresses, parser diagnostics, or raw-memory details.

## Cache contract

Parser schema `35` invalidates schema-34 catalogs exactly once because Ruby/Sapphire direct save descriptors and source-proven Hoenn/CFRU clocks materially change persisted runtime metadata. A complete schema-35 catalog remains cacheable; UI-only version changes do not invalidate it.

## Verification gates

- Real identity and unified-state matrix: 14/14 identities and 14/14 decode controls passed.
- Gradle save, battle, parser, catalog, companion, app unit, and release-lint gate: 65 tasks, zero failures (`BUILD SUCCESSFUL`).
- Companion web tests: 26/26 files and 188/188 tests passed.
- Companion production build: 30 modules built successfully.
- Diff whitespace validation: zero errors.
