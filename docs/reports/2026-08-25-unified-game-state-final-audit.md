# Unified Game State Final Audit

Date: 2026-08-25

Specification: `docs/superpowers/specs/2026-08-25-unified-game-state-single-authority-design.md`

Plan: `docs/superpowers/plans/2026-08-25-unified-game-state-single-authority.md`

Machine-readable evidence: `docs/reports/2026-08-25-unified-game-state-compatibility.json`

## Result

The migration is complete. One long-lived `UnifiedGameStateDecoder` and one immutable `ResolvedGameSnapshot` are the sole authority for game-originating transient data. The Android runtime subscribes once and publishes one complete `ResolvedGameStateChanged` projection. The old section actions, battle callback, mirrored Trainer fields, Party merge, and normal-UI ledger fallbacks have been deleted.

These percentages cover exactly nine unified transient live-state field groups: Trainer, Pokédex, Party, Battle, area, position, clock, bag, and event flags. They do not represent static parser-table coverage, map completeness, or THUMB mechanic coverage.

## Exact live-state matrix

| ROM | Trainer | Pokédex | Party | Battle | Area | Position | Clock | Bag | Flags | Available |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Red | 0% | 0% | 0% | 100% | 100% | 100% | 0% | 0% | 0% | 33.33% |
| Blue | 0% | 0% | 0% | 100% | 100% | 100% | 0% | 0% | 0% | 33.33% |
| Yellow | 0% | 0% | 0% | 100% | 100% | 100% | 0% | 0% | 0% | 33.33% |
| Gold | 0% | 0% | 0% | 100% | 100% | 100% | 100% | 0% | 0% | 44.44% |
| Silver | 0% | 0% | 0% | 100% | 100% | 100% | 100% | 0% | 0% | 44.44% |
| Crystal Rev 1 | 0% | 0% | 0% | 100% | 100% | 100% | 100% | 0% | 0% | 44.44% |
| Ruby Rev 2 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100.00% |
| Sapphire Rev 2 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100.00% |
| Emerald | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100.00% |
| FireRed Rev 1 | 100% | 100% | 100% | 100% | 100% | 100% | 0% | 100% | 100% | 88.89% |
| LeafGreen Rev 1 | 100% | 100% | 100% | 100% | 100% | 100% | 0% | 100% | 100% | 88.89% |
| Modern Emerald 3.5 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100.00% |
| Unbound 2.1.1.1 | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100.00% |
| Odyssey 4.1.1 | 100% | 100% | 100% | 100% | 100% | 100% | 0% | 100% | 100% | 88.89% |

## Numeric summaries

| Cohort | Available groups | Total groups | Percentage |
|---|---:|---:|---:|
| Official Gen I | 9 | 27 | 33.33% |
| Official Gen II | 12 | 27 | 44.44% |
| Official Gen III | 43 | 45 | 95.56% |
| Modern Emerald, Unbound, Odyssey | 26 | 27 | 96.30% |
| All official ROMs | 64 | 99 | 64.65% |
| All Gen III controls | 69 | 72 | 95.83% |
| All 14 controls | 90 | 126 | 71.43% |

| Field | ROMs available | ROMs tested | Percentage |
|---|---:|---:|---:|
| Trainer | 8 | 14 | 57.14% |
| Pokédex | 8 | 14 | 57.14% |
| Party | 8 | 14 | 57.14% |
| Battle | 14 | 14 | 100.00% |
| Area | 14 | 14 | 100.00% |
| Position | 14 | 14 | 100.00% |
| Clock | 8 | 14 | 57.14% |
| Bag | 8 | 14 | 57.14% |
| Event flags | 8 | 14 | 57.14% |

The 0% Gen I clock result is absence of an in-game day/night clock. The 0% FireRed, LeafGreen, and Odyssey clock result means no source-proven live clock address is published; the field stays explicitly unavailable. The 0% Gen I/II Trainer, Pokédex, Party, bag, and flag results identify the remaining live-decoder work and are not reported as implemented.

## Recovery and checkpoint behavior

| Behavior | Passed | Total | Percentage |
|---|---:|---:|---:|
| Gen III live values remain authoritative after recovery is submitted | 8 | 8 | 100.00% |
| Gen III recovery fills a field that is unavailable live | 8 | 8 | 100.00% |
| Mismatched ROM/save recovery is rejected | 1 | 1 | 100.00% |
| Disconnect removes live authority and reveals matching recovery | 1 | 1 | 100.00% |
| Valid live empty Pokédex/Party state retracts stale recovery | 2 | 2 | 100.00% |
| Checkpoint writes occur only on validated same-playthrough `CHANGED` save observations | 4 | 4 | 100.00% |

The real unified-state matrix uses synthetic typed recovery values against each parsed Gen III ABI; it does not claim that a Gen I/II save file was supplied for each official ROM. Gen I/II SaveRAM evidence remains documented separately in `docs/reports/gen1-gen2-saveram-compatibility.md`.

## Read-window and memory evidence

| ROM | Read windows | Bytes per complete sample | Passive added bytes | Retained raw bytes |
|---|---:|---:|---:|---:|
| Ruby Rev 2 | 5 | 17,838 | 0 | 0 |
| Sapphire Rev 2 | 5 | 17,838 | 0 | 0 |
| Emerald | 7 | 20,250 | 0 | 0 |
| FireRed Rev 1 | 6 | 20,205 | 0 | 0 |
| LeafGreen Rev 1 | 6 | 20,205 | 0 | 0 |
| Modern Emerald 3.5 | 7 | 20,250 | 0 | 0 |
| Unbound 2.1.1.1 | 8 | 32,150 | 11,940 | 0 |
| Odyssey 4.1.1 | 6 | 20,205 | 0 | 0 |

The migration adds no poller, transport, timeout, full-memory copy, or retained raw-memory array. Current automated bounds for streaming ROM ingestion, map raster caching, catalog chunks, Pokédex virtualization, mapper history, WebView ownership, connection workers, and performance logging all passed in the complete Gradle/web gate. Android heap/PSS profiling was not performed because this plan explicitly excludes APK installation, launch, emulator use, and device control.

## Plan-to-spec trace

| Requirement | Evidence | Result |
|---|---|---|
| One decoder and snapshot authority | Structural test counts one production decoder construction and one `TransientGameStateSource` subscription. | Met |
| Canonical Pokédex IDs | One flag maps to one base-form representative; Modern Emerald Treecko resolves to one species, not 52 aliases. | Met |
| Live replaces recovery | Live Pokédex and Party tests retract stale recovery, including authoritative empty collections. | Met |
| Recovery fills only unavailable fields | Field-level decoder tests and all eight Gen III controls preserve live values while recovering Trainer stars. | Met |
| Organic knowledge remains separate | Battle observations, seen-by-area, visited areas, and POI discovery remain in the Organic ledger and do not become mirrored game-state fields. | Met |
| Every game-originating section uses one route | Trainer, Pokédex, Party, ownership, bag, flags, battle, observations, area, position, clock, and readiness are derived before one atomic projection dispatch. | Met |
| No technical normal-UI copy | Web normal-screen tests reject technical failures/provenance; Debug Settings remains the diagnostic surface. | Met |
| No legacy production route | Cross-module structural scan rejects the old section actions and battle callbacks. The companion server simulator also uses the atomic projection action. | Met |
| Save persistence boundary | INITIAL and UNCHANGED observations do not write; live samples do not write; only validated same-playthrough CHANGED freezes and writes. | Met |
| Lifecycle behavior | ROM switches clear old authorities, mismatch is rejected, disconnect reveals recovery, reconnecting live values replace it, and catalog transitions cannot expose the previous identity. | Met |
| Privacy and Organic behavior | Raw opponent move lists remain private; only observed moves are published. Organic fog and POI discovery tests remain green. | Met |
| Navigation, battle focus, and map tracking | Navigation stack, wild rarity initial/late promotion, fog, recenter/follow/gliding, zoom preservation, and manual tracking break tests all pass. | Met |

## Complete verification

- Gradle: 1,804 tests, 0 failures, 0 errors, 225 skipped by explicit environment/fixture assumptions.
- Android lint: 0 errors and 51 warnings.
- Web: 26 files and 191 tests, 0 failures.
- Production web build: passed.
- Release policy: 18 tests, 0 failures.
- Android deployment-safety tools: passed.
- Secure build dependency gate: passed.
- RC63 unsigned APK: `com.darkaxt.dualdex`, version name `1.1.0-rc.63`, version code `1010063`, 17,700,771 bytes, SHA-256 `BF5A2F6A3B7CAD0745790515BD15C2C76F0B924113084278CFD50577FC171DEC`.
- `git diff --check`: passed.

## Deferred

- Gen I/II live Trainer, Pokédex, Party, bag, and event-flag decoders.
- A source-proven clock for FireRed, LeafGreen, and Odyssey if those games expose a compatible live clock.
- Separately authorized physical-device heap/PSS and long-session profiling.

## Blockers

None for RC63 publication.
