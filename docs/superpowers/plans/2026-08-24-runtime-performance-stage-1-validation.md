# Runtime Performance Stage 1 Validation

**Date:** 2026-08-24

**Stage:** 1 — Runtime observability

**Specification:** `docs/superpowers/specs/2026-08-24-runtime-performance-observability-and-churn-design.md`

**Result:** PASS — no blocker; Stage 2 may start

## Gate evidence

| Gate | Observed result |
|---|---|
| `./gradlew.bat :app:testDebugUnitTest` | `BUILD SUCCESSFUL in 10m 44s`; 45 suites, 257 tests, 0 failures, 0 errors, 27 existing conditional skips |
| `npm test -- --run` in `companion-web` | 26 files and 190 tests passed |
| `npm run build` in `companion-web` | TypeScript and Vite production build passed; 30 modules transformed |
| Focused profiler tests | Recorder ordering/cadence/privacy, Android metrics, bounded dispatch, two-segment retention, component counters, runtime cold/cache/failure/readiness hooks, and surface ownership passed |
| Debug export tests | Exact native route and Settings Debug-only placement passed; query-bearing route rejected |

The full JVM gate actively parsed the real-catalog controls in `UnifiedGameStateRealControlTest`; it was not replaced by a synthetic-only matrix.

## Complete specification matrix

| Requirement | Status | Stage 1 evidence and invariant | Next action |
|---|---|---|---|
| RP-01 | PASS | `PerformanceEvent` schema v1 is serialized as NDJSON; deterministic recorder and persisted-contract tests cover stable keys and event ordering. | Preserve in every later gate. |
| RP-02 | PASS | Runtime tests prove cold MISS stage events, cached HIT with zero parser stages, catalog/wait/access transitions, and load failure. | Preserve in every later gate. |
| RP-03 | PASS | Recorder tests prove current-minute-only emission with no historical catch-up; `RetroArchSetupCoordinator.monitorHeartbeat()` drives the existing heartbeat. | Preserve in every later gate. |
| RP-04 | PASS | Android sampler covers Java/native/PSS/CPU/GC/thread metrics. Available map-cache, WebView, loopback, and mapper counters use stable names and omit unavailable values rather than inventing zeros. | Stage 2 adds state/body counters; Stage 3 adds packet/decode counters under their owning requirements. |
| RP-05 | PASS | Sampling and persistence use one 128-item newest-retaining off-thread dispatcher. Two 512 KiB segments cap storage at 1 MiB; rotation failure drops the incoming event; profiler failures cannot fail the workflow. | Preserve in every later gate. |
| RP-06 | PASS | Persisted-contract tests exclude paths, filenames, player/save identity, raw memory, and stack traces; ROM identity is a validated lowercase twelve-character SHA-256 prefix. | Preserve in every later gate. |
| RP-07 | PASS | `EXPORT PERFORMANCE LOG` exists only inside the Settings `.mapper-setting` Debug section and uses the exact native create-document route. No public state or normal-page profiler UI was added. | Preserve in every later gate. |
| RP-08 | PASS | Owned active WebView surfaces are counted by `CompanionSurfaceOwnership`; the app does not claim renderer PSS. Actual sandboxed renderer PSS remains correctly assigned to RP-18 device evidence. | Collect renderer PSS in Stage 4 with read-only ADB. |
| RP-09 | DEFERRED | Exact changed-section publication is not part of Stage 1. | Implement and verify in Stage 2. |
| RP-10 | DEFERRED | Gateway semantic no-op suppression and its dispatch/body counters are not part of Stage 1. | Implement and verify in Stage 2. |
| RP-11 | DEFERRED | Seconds-only readiness without post-ready public-state churn is not part of Stage 1. | Implement and verify in Stage 2. |
| RP-12 | DEFERRED | Exact Trainer/Pokédex and battle dispatch isolation is not part of Stage 1. | Implement and verify in Stage 2. |
| RP-13 | DEFERRED | One scratch allocation per core-memory read session is not part of Stage 1. | Implement and verify in Stage 3. |
| RP-14 | DEFERRED | Completion ownership without region cloning and packet/byte counters are not part of Stage 1. | Implement and verify in Stage 3. |
| RP-15 | DEFERRED | Section fingerprints, translated-value reuse, and decode/reuse counters are not part of Stage 1. | Implement and verify in Stage 3. |
| RP-16 | DEFERRED | The Stage 1 full unit/web gates passed, but the complete original R1-R14 bound audit belongs to final integration. | Re-run and record the full bounds matrix in Stage 4. |
| RP-17 | DEFERRED | Existing real-catalog automated controls passed in the unit gate; the explicit official Gen I-III, Modern Emerald, Odyssey, and Unbound compatibility matrix remains a final gate. | Run the reference compatibility matrix in Stage 4. |
| RP-18 | DEFERRED | Android cold/cache/wait/runtime and renderer-PSS evidence requires the Stage 4 device validation boundary. | Capture authorized read-only ADB evidence in Stage 4. |
| RP-19 | PASS | This matrix contains RP-01 through RP-19 exactly once. Every missing feature is assigned to Stage 2, 3, or 4, and no blocker remains. | Recreate the complete matrix after Stage 2. |

## Missing-feature classification

### Deferred

- State dispatch/no-op and response-body counters: Stage 2, with RP-09/RP-10/RP-12.
- Live packet/byte/sample and section decode/reuse counters: Stage 3, with RP-13/RP-14/RP-15.
- Android renderer PSS and ROM-specific cold/cache/runtime measurements: Stage 4, with RP-18.
- Full original R1-R14 and named-ROM compatibility audit: Stage 4, with RP-16/RP-17.

### Blockers

- None.

## Stage decision

Stage 1 satisfies RP-01 through RP-08. RP-19 is complete for this checkpoint, all other requirements have an exact destination, and Stage 2 is authorized by the specification.
