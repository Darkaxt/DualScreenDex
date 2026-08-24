# DualDex 1.1.0-rc.54

RC54 gives every feature one live-first transient game-state source and converges the completed Generation I/II local-map work.

## Unified live game state

- One `UnifiedGameStateDecoder` now supplies Trainer Card, Pokédex, Party, progression, battle, Atlas location and position, game clock, readiness, bag, and event flags.
- Supported live fields are authoritative independently; validated recovery fills only unavailable fields and is rejected when the ROM identity does not match.
- Disconnecting clears stale live authority instead of leaving an old playthrough visible.
- Ordinary UI routes receive translated values only. Memory addresses, provenance, parser stages, and other diagnostics remain outside the normal interface.

## Expanded real-ROM coverage

- Official Ruby and Sapphire now expose live Trainer, Pokédex, Party, battle, area, position, numeric clock, bag, and event flags through source-defined direct save blocks.
- Official Ruby, Sapphire, and Emerald expose their numeric RTC time without requiring a day/night palette schedule.
- Unbound 2.1.1.1 exposes its source-backed CFRU numeric clock. Its custom phase boundaries remain withheld until structurally proven.
- Red, Blue, Yellow, FireRed, and LeafGreen correctly expose no live clock because the official games do not implement one. Gold, Silver, and Crystal expose their supported phase value.
- Odyssey 4.1.1 keeps clock unavailable because no independent runtime descriptor has been proven; its other measured Gen III live sections remain available.

## Generation I/II maps

- The converged map work builds connected Generation I/II local scenes from structurally parsed map data.
- ROM-derived Generation I/II overworld trainer assets are available to the shared map presentation.
- The unified live gender selection and the single-valid-asset fallback coexist, preserving unusual ROM layouts without guessing.

## Memory and cache behavior

- Overlapping and adjacent live-memory windows are coalesced before reading, and decoded snapshots retain no raw ROM, SaveRAM, WRAM, or EWRAM byte arrays.
- The exact Unbound control requests 32,150 unique bytes per complete sample, including 11,940 source-defined extended-save bytes, and retains zero raw bytes after decode.
- Parser schema 35 intentionally revalidates schema-34 catalogs once because persisted Ruby/Sapphire runtime and Gen I/II map descriptors changed. Complete schema-35 catalogs remain cacheable; UI-only updates do not force another parse.

## Validation

- Exact identity and live-field controls passed for all 14 targets: 11 official Gen I–III ROMs plus Modern Emerald 3.5, Unbound 2.1.1.1, and Odyssey 4.1.1.
- Trainer, Pokédex, Party, battle, area, position, bag, and event flags are 100% live across all eight measured Gen III controls.
- Battle, area, and position are 100% live across all 14 controls.
- The complete pre-convergence Gradle gate passed 65 tasks with zero failures; all 188 companion-web tests and the production web build passed. Focused post-merge catalog, API, and 24-test map presentation gates also passed.

## Delivery

- RC54 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010054`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
