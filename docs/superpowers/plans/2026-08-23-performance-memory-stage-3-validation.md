# Performance and Memory Hardening — Stage 3 Validation

## Scope

Stage 3 bounds long-lived browser, storage, mapper, WebView, and loopback state. It implements the automated portions of R10–R15 while retaining the Stage 1 and Stage 2 controls. Device profiling was not performed because installation, launch, emulator, and device interaction require separate authorization.

## Requirement matrix

| Requirement | Status | Observed evidence | Expected threshold | Next action / owner |
| --- | --- | --- | --- | --- |
| R1 — Context reuse | PASS | The complete gate retained the catalog/trainer identity controls: unchanged battle and save projections reuse one object graph, and only the applicable graph changes after trainer or catalog replacement. | One projection graph per unchanged input generation. | Retain as a release regression control. |
| R2 — Unchanged state | PASS | The complete gate retained HTTP 204 with zero body bytes, zero JSON parsing, and zero client update callback for an equal state version. | Zero state body and client replacement for unchanged versions. | Retain as a release regression control. |
| R3 — Live polling | PASS | The complete gate retained the 25 ms/40-cycle steady cadence, one receive buffer per transport, and one-pass 1,024-byte packet decoding controls. | At most 40 scheduled cycles/s outside discovery; no packet regex/buffer churn. | Retain as a release regression control. |
| R4 — Hot-path evidence | PASS | Context identities, response/callback counts, cadence, buffer construction, packet drain, and decoded-byte counts remain deterministic tests and do not appear on normal pages. | Every R1–R3 metric remains repeatable and non-UI. | Retain in all later gates. |
| R5 — Request and archive bounds | PASS | Fixed/chunked requests still spool through one 65,536-byte transfer buffer; compressed input is capped at 64 MiB and extracted ROM data at 32 MiB. Raw, ZIP, and 7z Unbound controls converge on SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`. | Disk-backed request; 64 MiB compressed and 32 MiB extracted limits; explicit cleanup/rejection. | Retain request and archive controls. |
| R6 — Map rendering | PASS | The complete gate retained one render plus two hits for identical requests, byte-LRU eviction, the 32 MiB production budget, render coalescing, and rendering outside the runtime monitor. | 32 MiB encoded LRU; one in-flight render/key; no runtime-lock rendering. | Retain map cache and exact raster controls. |
| R7 — Map residency | PASS | The 100-placement scene control still mounts only current plus two adjacent placements: 3 rasters and 12,582,912 decoded bytes. | Current/adjacent only and no more than 33,554,432 decoded bytes. | Retain during all UI lifecycle changes. |
| R8 — Catalog sections | PASS | Ordered cursor-to-GZIP streaming, missing/duplicate rejection, 262,144-byte writer buffering, and zero bytes for an unchanged checkpoint all remain green. | Incremental decode, bounded write buffer, fail-closed ordering, and zero unchanged payload. | Retain catalog persistence controls. |
| R9 — Peak evidence | PASS | The Stage 2 request, map, and catalog byte/count evidence remains green in the complete gate and is not exposed as normal UI copy. | Repeatable high-water evidence for every declared Stage 2 bound. | Retain in release gates. |
| R10 — Pokédex rows | PASS | A 900-species catalog mounted at most 60 rows before and after scrolling, preserved `10 / 100`, narrowed to one row for `MON 0899`, and marked every mounted sprite `loading=lazy` and `decoding=async`. | At most 60 mounted rows/images with counts, search, and scroll behavior preserved. | Retain the 900-record virtualization control. |
| R11 — Change-first background work | PASS | The second unchanged save poll performed no source read, parse, snapshot-repository read/write, or association lookup beyond the first accepted observation. The second unchanged direct-ROM index reused the prior entry with one total identity read across both runs; direct and SAF paths key reuse by source ID, size, and modification time. | Unchanged inputs perform zero hashing, parse-context, SQLite, or snapshot-deserialization work. | Retain save and direct/SAF index controls. |
| R12 — Mapper bound | PASS | The 33rd snapshot evicted snapshot 1 and retained exactly snapshots 2–33. Nine 2 MiB captures retained 8 snapshots, exactly 16,777,216 raw bytes. Persistence appended one row for each of the first 32 writes and compacted to 32 rows only after eviction. | At most 32 snapshots and 16 MiB raw; append until eviction requires compaction. | Retain count, byte, and journal controls. |
| R13 — WebView ownership | PASS | Ownership tests observed one active surface: docked resume/pause/release counts were 1/1/1 when overlay ownership replaced it; overlay resumed once and released once. Re-activating the same paused surface resumed it without destruction. Activity and overlay surfaces call WebView resume/pause timer and destroy/release paths through the shared arbiter. | One active visible surface; hidden JavaScript paused; superseded WebView destroyed. | Device instance/PSS confirmation remains part of R15. |
| R14 — Loopback ownership | PASS | Eight simultaneous connections were retained within four workers; the ninth received HTTP 503. The capacity snapshot remained at no more than 4 workers and 8 active connections, and shutdown tests close tracked sockets/executor work. | Four workers, eight connections, bounded rejection, and complete shutdown release. | Retain capacity and shutdown controls. |
| R15 — Long-session evidence | BLOCKER | Automated controls prove the declared row, history, cache, worker, socket, and surface bounds. The 52m54s complete Gradle run finished without OOM or worker restart; sampled host parser residency varied from about 407–521 MiB under `-Xmx512m`, and the Android unit worker was observed around 507 MiB resident. These are host JVM observations, not Android profiling. Required device Java heap, native/PSS, GC count/pause, CPU, WebView-instance, and renderer-PSS measurements by ROM SHA and phase were not collected. | Automated bounds plus all named Android runtime metrics for official Emerald, Odyssey 4.1.1, and Unbound 2.1.1.1. | Separately authorized device profiling is required before R15, Stage 3, and release can pass. |

R1–R14 pass. R15 is the only blocker; no requirement is unassigned.

## Real-data controls

| Control | Result |
| --- | --- |
| Official Gen I–III parser regressions | PASS — Red, Blue, Yellow, Gold, Silver, Crystal Rev 1, Ruby Rev 2, Sapphire Rev 2, Emerald, FireRed Rev 1, and LeafGreen Rev 1 were configured in the complete gate. |
| Modern Emerald 3.5 | PASS — encounter, world/local map, trainer/theme, live clock, party/battle, save-block pointers, and exact save-runtime ABI controls passed. |
| Odyssey 4.1.1 | PASS — complete catalog/theme/trainer and persistence controls passed. |
| Unbound 2.1.1.1 | PASS — complete catalog/theme/trainer, persistence, and raw/ZIP/7z identity controls passed. |

The first exhaustive run exposed stale controls rather than output regressions: Crystal had moved to the canonical Rev 1 ROM; trainer/local-map/POI and Modern Emerald save-runtime support had expanded; and the original Yellow location fingerprint preserved an old off-by-one encounter heuristic. The refreshed Yellow control now asserts source-defined map 12 at `(2,10)` and map 30 at `(6,15)`. The focused six-control real-ROM gate passed in 5m59s, and the corrected complete gate passed afterward.

## Automated gate

| Command | Result |
| --- | --- |
| `npm test -- --run` in `companion-web` | PASS — 26 files, 183 tests, 0 failures. |
| `npm run build` in `companion-web` | PASS — TypeScript and Vite production build. |
| Focused corrected real-ROM controls | PASS — Yellow, Crystal Rev 1, official Emerald POIs, Modern Emerald runtime ABI, Unbound/Odyssey/official theme controls; 5m59s. |
| `gradlew verifySecureBuildDependencies test :app:lintDebug :app:assembleRelease -PdualdexVersionName=1.1.0-rc.48 -PdualdexVersionCode=1010048` | PASS — 1,691 tests, 0 failures, 0 errors (129 skipped); lint 0 errors (51 warnings); release build assembled in 52m54s. |
| `node --test tools/release/*.test.mjs` | PASS — 18 tests, 0 failures. |

The unsigned Stage 3 gate artifact is `app-release-unsigned.apk`, 17,342,483 bytes, SHA-256 `6EFD25130B88E262375A54B1D11726493B1333769C406D1DF00E248FEF4D04BE`. It is build evidence only. It was not signed, installed, launched, or published.

## Specification cross-check

Every R1–R15 requirement is represented exactly once. R1–R14 meet their declared automated thresholds. R15 cannot pass from host tests alone because the specification explicitly requires Android runtime measurements and separately protects device interaction. No prerelease is published while that blocker remains.
