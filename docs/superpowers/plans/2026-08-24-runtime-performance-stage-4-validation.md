# Runtime Performance Stage 4 Validation

**Date:** 2026-08-24

**Stage:** 4 — Complete regression and Android evidence

**Specification:** `docs/superpowers/specs/2026-08-24-runtime-performance-observability-and-churn-design.md`

**Result:** PRE-RELEASE PASS — RC55 is eligible for publication; Android runtime acceptance remains deferred to user testing

## Automated gate evidence

| Gate | Observed result |
|---|---|
| `npm test -- --run` | 26 files, 190/190 tests passed. |
| `npm run build` | Production bundle passed; JS 116.80 kB and CSS 81.43 kB before gzip. |
| `./gradlew.bat verifySecureBuildDependencies test :app:lintDebug :app:assembleRelease ...` | Fresh post-merge invocation: `BUILD SUCCESSFUL in 18m 17s`; 124 tasks; 1,793 tests, 0 failures, 0 errors, 222 conditional skips. |
| Android lint | 0 errors, 51 warnings. |
| `node --test tools/release/*.test.mjs` | 18/18 release-policy tests passed. |
| Unsigned RC55 candidate | `app/build/outputs/apk/release/app-release-unsigned.apk`; 17,573,455 bytes; SHA-256 `1475B9B3BC50C6A0593757833D631D1E5D9FCB13C62E28754F8B322B955D0174`; package `com.darkaxt.dualdex`; version `1.1.0-rc.55`; code `1010055`. This is not the protected signed/public artifact. |

## Explicit real-ROM gate

The optional controls were rerun with exact paths under `D:\Temp\PokemonHacks`, with `--rerun-tasks`, so their results were neither inherited nor skipped for missing inputs.

| Control | Result |
|---|---|
| Unified official/hack identity and live-state projection | 2/2 passed, 0 skipped. Covers all 11 official Gen I–III ROMs plus Modern Emerald 3.5, Unbound 2.1.1.1, and Odyssey 4.1.1. |
| World/local map persistence and API controls | All 15 in-scope official/Modern/Unbound/Odyssey cases passed. Seven unrelated hack cases without supplied environment paths remained skipped and are outside this specification. |
| Unbound/Odyssey deterministic map reconstruction | 2/2 passed, 0 skipped. |
| Emerald/FireRed/Modern/Classic/Unbound/Odyssey nature resolution | 3/3 aggregate controls passed, 0 skipped. |
| Forced gate aggregate | `BUILD SUCCESSFUL in 26m 42s`; 43/43 tasks executed. |

## Existing R1–R15 matrix

| Requirement | Status | Current evidence |
|---|---|---|
| R1 — Context reuse | PASS | Complete app tests retain object-identity/invalidation controls; Stage 3 adds context-scoped translated caches. |
| R2 — State responses | PASS | Semantic no-op, HTTP 204, zero body, and client parse-suppression controls pass. |
| R3 — Live polling | PASS | The 25 ms/40-cycle steady ceiling, one receive buffer, and one-pass packet parser controls pass. |
| R4 — Hot-path evidence | PASS | Deterministic counters cover state work, packet/byte/sample ownership, and section decode/reuse without normal-page diagnostics. |
| R5 — Request/archive bounds | PASS | Full bounds controls retain disk-backed input, 64 MiB compressed, 32 MiB extracted, one extracted payload, explicit rejection, and cleanup. |
| R6 — Map rendering | PASS | 32 MiB encoded LRU, render coalescing, eviction, and outside-runtime-lock controls pass. |
| R7 — Map residency | PASS | Current/adjacent mounting and the 32 MiB decoded-pixel browser budget controls pass. |
| R8 — Catalog persistence | PASS | Incremental bounded catalog-section persistence and reopen controls pass. |
| R9 — Catalog projection | PASS | Bounded API materialization and exact real-ROM persisted projections pass. |
| R10 — Pokédex rows | PASS | The 900-species virtualization control retains at most 60 rows/images with search, counters, and scroll behavior. |
| R11 — Change-first background work | PASS | Unchanged ROM/save controls perform no hash, parse, SQLite, or snapshot-deserialization work. |
| R12 — Mapper bound | PASS | At most 32 snapshots and 16 MiB raw bytes; append-until-eviction/compaction controls pass. |
| R13 — WebView ownership | PASS | One-active-surface ownership/pause/release lifecycle controls pass. |
| R14 — Loopback ownership | PASS | Four workers, eight active connections, bounded ninth-connection rejection, and shutdown release controls pass. |
| R15 — Android long-session evidence | DEFERRED | Automated bounds pass, but no RC55 Android process or profiler run exists. Java/native/PSS, GC, CPU, owned WebView, renderer PSS, cold/cache phases, and long sessions for Emerald/Odyssey/Unbound remain post-publication acceptance work. |

## RP-01 through RP-19 matrix

| Requirement | Status | Stage 4 evidence and disposition |
|---|---|---|
| RP-01 | PASS | Structured NDJSON schema tests pass. |
| RP-02 | PASS | Cold/cache/parser/readiness/failure event coverage tests pass. |
| RP-03 | PASS | Existing-heartbeat minute cadence and no-catch-up tests pass. |
| RP-04 | PASS | All mandatory process/component/state/live-memory/decode counters exist and are covered by tests. Runtime values remain part of RP-18. |
| RP-05 | PASS | Two 512 KiB segments, newest-retaining dispatch, failed-rotation drop, and failure isolation tests pass. |
| RP-06 | PASS | Persisted event privacy controls pass; the Stage 3 additions are numeric aggregates only. |
| RP-07 | PASS | Export remains Debug-only with no profiler output on normal pages. |
| RP-08 | PASS | Owned WebView count is explicit; renderer PSS is not inferred from app PSS. Runtime capture remains RP-18. |
| RP-09 | PASS | Exact changed-section propagation and clearing controls pass. |
| RP-10 | PASS | Semantic gateway no-op suppression controls pass. |
| RP-11 | PASS | Seconds participate only in readiness; post-ready web state/body suppression controls pass. |
| RP-12 | PASS | Exact Trainer/Pokédex and battle projection routing controls pass. |
| RP-13 | PASS | One reusable session scratch buffer and one-pass matched-packet parsing are proven. |
| RP-14 | PASS | Completion transfers owned region arrays once with zero clones and stable terminal identities. |
| RP-15 | PASS | Exact source-slice fingerprints, context invalidation, translated reuse, counters, and no raw-region retention are proven. |
| RP-16 | PASS | R1–R14 are represented above and their complete automated controls passed. |
| RP-17 | PASS | All 14 named official/hack unified controls and all 15 in-scope map/API controls executed from exact ROM paths; Unbound/Odyssey maps and all selected nature controls passed without skips. |
| RP-18 | DEFERRED | `adb` found Thor `bfa98654`, but installed DualDex is RC53 and no DualDex process is running. RC55 publication supplies the candidate needed for user-driven profiling; no Android acceptance claim is made yet. |
| RP-19 | PASS | RP-01 through RP-19 and R1 through R15 each appear exactly once; pending Android acceptance is explicitly deferred rather than omitted or mislabeled. |

## Read-only Android observation

| Observation | Result |
|---|---|
| Connected target | `bfa98654`, AYN Thor. |
| Installed DualDex | `1.1.0-rc.53`, version code `1010053`. |
| App process | Not running; `dumpsys meminfo com.darkaxt.dualdex` returned no process. |
| WebView processes | Only global `webview_zygote` and `com.android.webview:webview_service` were present; neither can be attributed to DualDex. |
| Profiler logcat | No `DualDexPerf` records; expected because the installed RC53 predates this implementation and the app is not running. |

## Missing-feature classification

### Deferred

- RP-18 and original R15: user-driven RC55 cold/cache runs for Red, Crystal, Emerald, Modern Emerald, Odyssey, and Unbound, plus long-session evidence for Emerald, Odyssey, and Unbound.
- Final Android acceptance: Java/native/PSS, GC, CPU, owned WebView, renderer PSS, stage durations, cache decisions, and runtime-minute records from the published signed candidate.

### Blockers

- None for prerelease publication. RC55 must remain a prerelease and must not be described as Android-runtime validated until the deferred evidence exists.

## Stage decision

The automated Stage 4 gate passes for prerelease publication. RP-01 through RP-17 and RP-19 pass; RP-18 and original R15 remain explicit post-publication acceptance items. The protected workflow may sign and publish RC55; installation, launch, gameplay interaction, and Android-runtime acceptance remain outside this release action.
