# Performance and Memory Hardening — Stage 2 Validation

## Scope

Stage 2 bounds peak memory during ROM ingestion, map rendering/browser residency, and catalog persistence. It implements R5–R9 while retaining the Stage 1 R1–R4 controls. Long-lived UI, background, mapper, WebView, and server ownership remain assigned to Stage 3.

## Requirement matrix

| Requirement | Status | Observed evidence | Expected threshold | Next action / owner |
| --- | --- | --- | --- | --- |
| R1 — Context reuse | PASS | The complete gate retained the Stage 1 identity/invalidation tests: unchanged save and battle projections reuse one object graph; trainer/catalog replacement invalidates only the applicable graph. | One projection graph per unchanged input generation. | Retain as a Stage 3 regression control. |
| R2 — Unchanged state | PASS | The complete gate retained HTTP 204 with zero body bytes and zero client JSON/update callbacks for an equal state version. | Zero state body and client replacement for unchanged versions. | Retain as a Stage 3 regression control. |
| R3 — Live polling | PASS | The complete gate retained the 25 ms/40-cycle steady cadence, one receive buffer per transport, and one-pass 1,024-byte packet decoding controls. | At most 40 scheduled cycles/s outside discovery; no packet regex/buffer churn. | Retain as a Stage 3 regression control. |
| R4 — Hot-path evidence | PASS | Context identities, unchanged response/callback counts, cadence, receive-buffer construction, packet drain, and decoded byte counts remain deterministic tests and are not visible on normal pages. | Every R1–R3 metric remains repeatable and non-UI. | Retain as a Stage 3 regression control. |
| R5 — Request and archive bounds | PASS | Fixed (512-byte) and chunked (513-byte) uploads both used owned spool files and deleted them on success; the rejected upload also deleted its spool. Transfer memory is one 65,536-byte buffer. Request limits are 64 MiB compressed, 32 MiB raw/extracted, and 1 MiB for control bodies. Raw, ZIP, and 7z Unbound controls all produced the same 33,554,432-byte ROM, SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f`, and CRC32 `4B3D4957`; the temporary ZIP control was removed after use. | Body on disk; compressed at most 64 MiB; extracted at most 32 MiB; one complete extracted array; explicit rejection and cleanup. | Retain raw/ZIP/7z identity and cleanup tests. |
| R6 — Map rendering | PASS | Concurrent plus successive identical requests produced 1 render and 2 cache hits. A 10-byte test budget with two 6-byte assets retained 1 entry and performed 2 LRU evictions. The production default is 32 MiB encoded. A runtime seam proved the renderer does not hold the runtime monitor. Official Emerald and Modern Emerald preserved their exact normalized raster/API fingerprints through the cached path. | 32 MiB byte-bounded LRU; one in-flight render/key; render outside runtime lock. | Retain cache statistics, lock, and real-ROM raster controls. |
| R7 — Map residency | PASS | A revealed scene containing 100 1024×1024 placements mounted exactly the current placement and its two edge-adjacent placements: 3 rasters and 12,582,912 decoded bytes. Only those mounted timed URLs changed. POIs/labels for unmounted placements were also unmounted. | Current plus adjacent only and at most 33,554,432 decoded bytes. | Stage 3 must preserve this window during lifecycle ownership changes. |
| R8 — Catalog sections | PASS | The reader pulls ordered rows directly from a live database cursor into GZIP/Gson; it retains neither a chunk list nor an assembled compressed payload. Missing and duplicate indices fail closed. The writer uses one 262,144-byte output buffer and stores a 32-byte section digest. A repeated identical catalog wrote 0 chunk bytes. Complete persisted/reopened controls passed for official Emerald, Odyssey 4.1.1, and Unbound 2.1.1.1. | Incremental decode; bounded output; reject corrupt ordering; unchanged section writes zero payload bytes. | Retain corruption, checkpoint, and three real-ROM reopen controls. |
| R9 — Peak evidence | PASS | Request high-water is a 65,536-byte transfer buffer plus disk spool and one at-most-32-MiB extracted ROM array. Map evidence reports 1 render/2 hits and 3 mounted rasters/12,582,912 decoded bytes. The large catalog control reports a 4,194,304-byte raw raster asset encoded into 5,572,821 compressed section bytes across 22 bounded chunks; initial fixture checkpoint payload was 3,801 bytes and the identical checkpoint wrote 0 bytes. Evidence exists only in tests/runtime counters and invisible DOM attributes, not normal UI copy. | Named request/archive, map, and catalog byte/count metrics with every declared bound satisfied. | Carry these reference values into the final Stage 3 matrix. |
| R10 — Pokédex rows | DEFERRED | Large filtered catalogs can still mount every matching row and sprite. | At most 60 mounted rows with behavior preserved. | Stage 3 Pokédex virtualization workstream. |
| R11 — Change-first background work | DEFERRED | ROM/save monitors can still enter hashing, context, database, or deserialize work before a cheap identity rejection. | Zero expensive operations for unchanged inputs. | Stage 3 background-work workstream. |
| R12 — Mapper bound | DEFERRED | Mapper capture count/bytes and persistence rewrites remain unbounded. | At most 32 snapshots and 16 MiB raw; append until eviction compaction. | Stage 3 mapper workstream. |
| R13 — WebView ownership | DEFERRED | Activity and overlay can still retain two active WebViews/catalog graphs. | One active visible surface; hidden work paused; superseded WebView destroyed. | Stage 3 lifecycle workstream. |
| R14 — Loopback ownership | DEFERRED | The server still uses an unbounded cached client pool and active socket set. | Four workers and eight connections with bounded rejection and full shutdown release. | Stage 3 server-ownership workstream. |
| R15 — Long-session evidence | DEFERRED | Stage 3 bounds and authorized device profiling do not yet exist. | Automated lifecycle bounds plus Java/native/PSS, GC, CPU, WebView, and renderer evidence. | Stage 3 validation; device portions still require separate authorization. |

No requirement is unassigned and Stage 2 has no `BLOCKER`.

## Real-data controls

| Control | Result |
| --- | --- |
| Official Emerald complete catalog persistence/reopen | PASS — exact catalog references and trainer world sprites survived SQLite round trip. |
| Modern Emerald map materialization/API path | PASS — exact normalized world/local map controls survived cached rendering. |
| Odyssey 4.1.1 complete catalog persistence/reopen | PASS — 409 navigable entries, descriptions, battle-only species semantics, trainer assets, and cross-references survived. |
| Unbound 2.1.1.1 complete catalog persistence/reopen | PASS — 922 move descriptions, trainer assets, and catalog cross-references survived. |
| Unbound raw/ZIP/7z source convergence | PASS — identical 32 MiB ROM SHA/CRC and selected-entry behavior. |

## Automated gate

| Command | Result |
| --- | --- |
| `npm test -- --run` in `companion-web` | PASS — 26 files, 182 tests, 0 failures. |
| `npm run build` in `companion-web` | PASS — TypeScript and Vite production build. |
| `gradlew verifySecureBuildDependencies test :app:lintDebug :app:assembleRelease -PdualdexVersionName=1.1.0-rc.48 -PdualdexVersionCode=1010048` | PASS — 1,684 tests, 0 failures, 0 errors (216 skipped); lint 0 errors (51 warnings); unsigned release APK assembled. |
| `node --test tools/release/*.test.mjs` | PASS — 18 tests, 0 failures. |

The unsigned Stage 2 gate artifact is `app-release-unsigned.apk`, 17,329,543 bytes, SHA-256 `F37AECFB20CFB52339F1D5EC12BCC8510D27A5530332AA2EAAB05D87C626FC4F`. It is build evidence only and was not signed, installed, or published.

The first full lint pass exposed the newly introduced API-34-only `Path.of` fallback. Root-cause correction `ab254aa` replaced it with the established API-30-compatible `File.createTempFile(...).toPath()` pattern. The complete command above then passed from the corrected source.

## Specification cross-check

Every R1–R15 requirement is represented exactly once. R5–R9 meet their Stage 2 thresholds, R1–R4 retain PASS evidence, and R10–R15 have explicit Stage 3 owners. No omitted peak-memory requirement was found. Stage 3 may start because this matrix contains no blocker.
