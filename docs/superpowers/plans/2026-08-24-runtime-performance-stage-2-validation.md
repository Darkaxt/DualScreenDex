# Runtime Performance Stage 2 Validation

**Date:** 2026-08-24

**Stage:** 2 — Unified-state change isolation

**Specification:** `docs/superpowers/specs/2026-08-24-runtime-performance-observability-and-churn-design.md`

**Result:** PASS — no blocker; Stage 3 may start

## Gate evidence

| Gate | Observed result |
|---|---|
| `./gradlew.bat :companion-core:test :app:testDebugUnitTest` | Fresh post-fix run: `BUILD SUCCESSFUL in 10m 55s`; app 45 suites/261 tests/0 failures/0 errors/27 existing conditional skips; companion-core 11 suites/73 tests/0 failures/0 errors |
| `npm test -- --run` in `companion-web` | 26 files and 190 tests passed |
| Changed-section controls | Initial state publishes all five sections; sample-ID-only changes publish nothing; seconds-only changes publish only `OVERWORLD`; Pokédex changes publish only `PLAYER`; a session clear publishes all five sections |
| No-op controls | A semantic reducer no-op returns the exact existing snapshot object/version and emits no listener callback; dispatch attempt/applied/no-op counters are exact |
| Clock/body controls | Post-ready seconds-only changes preserve the gateway version; `/api/state?sinceVersion=` returns 204 with zero bytes; the client performs zero JSON parse/state callbacks |
| Exact routing controls | Seconds-only work leaves player/party/battle section counters unchanged. An HP-only raw battle change increments the battle section count but causes zero gateway attempts because its UI projection is unchanged. |
| Session-clear control | The specification review found and fixed a stale Party path; ending a unified session now clears the Party projection and current-team ledger while leaving unrelated knowledge intact. |

## Complete specification matrix

| Requirement | Status | Stage 2 evidence and invariant | Next action |
|---|---|---|---|
| RP-01 | PASS | Stage 1 structured NDJSON tests remain green in the full app gate. | Preserve in every later gate. |
| RP-02 | PASS | Cold/cache/stage/readiness/failure profiler controls remain green. | Preserve in every later gate. |
| RP-03 | PASS | Existing-heartbeat minute cadence and no-catch-up controls remain green. | Preserve in every later gate. |
| RP-04 | PASS | Process/map/WebView/loopback/mapper metrics remain; Stage 2 adds section publication, gateway dispatch/no-op, state-response count, and streamed state-body-byte counters. | Stage 3 adds its packet/decode counters. |
| RP-05 | PASS | Bounded off-thread profiler dispatch, two-segment storage, failed-rotation drop, and failure isolation remain green. | Preserve in every later gate. |
| RP-06 | PASS | Persisted profiler data-minimization controls remain green; new counters contain only numeric aggregates. | Preserve in every later gate. |
| RP-07 | PASS | Debug-only export and normal-page exclusion controls remain green. | Preserve in every later gate. |
| RP-08 | PASS | Owned WebView count remains explicit and renderer PSS remains outside the app process claim. | Collect renderer PSS under RP-18 in Stage 4. |
| RP-09 | PASS | `ResolvedGameStateUpdate` carries the complete current snapshot plus exact `RECOVERY`, `PLAYER`, `PARTY`, `OVERWORLD`, and `BATTLE` changes. Sample identity is excluded; null/session transitions mark all sections and consumers clear stale state. | Preserve in Stage 3 and Stage 4. |
| RP-10 | PASS | `CompanionGateway` equality suppression occurs before versioning/listeners and is concurrency-safe through the existing atomic compare-and-set loop. Attempt/applied/no-op counters are exposed to the profiler. | Preserve in Stage 3 and Stage 4. |
| RP-11 | PASS | Live seconds still advance the Gen III readiness gate from `00:00:00` to `00:00:01`; after readiness, seconds-only changes do not alter projected clock, gateway version, HTTP body, or client state. | Preserve in Stage 3 and Stage 4. |
| RP-12 | PASS | Runtime routes only changed sections, computes exact seen/caught additions, and compares projected battle state before dispatch. Overworld-only changes do not invoke player/party/battle consumers; raw battle changes with an equal UI projection make no gateway attempt. | Preserve in Stage 3 and Stage 4. |
| RP-13 | DEFERRED | One scratch allocation per core-memory read session is not part of Stage 2. | Implement and verify in Stage 3. |
| RP-14 | DEFERRED | Completion ownership without region cloning and packet/byte counters are not part of Stage 2. | Implement and verify in Stage 3. |
| RP-15 | DEFERRED | Section fingerprints, translated-value reuse, and decode/reuse counters are not part of Stage 2. | Implement and verify in Stage 3. |
| RP-16 | DEFERRED | All current unit/web bounds remain green, but the complete original R1-R14 audit is assigned to final integration. | Run and record the bounds matrix in Stage 4. |
| RP-17 | DEFERRED | The full app gate again executed source-backed real-catalog controls; the named official/hack compatibility matrix remains the final reference gate. | Run the reference compatibility matrix in Stage 4. |
| RP-18 | DEFERRED | Android cold/cache/wait/runtime and sandboxed renderer-PSS measurements require Stage 4 device evidence. | Capture authorized read-only ADB evidence in Stage 4. |
| RP-19 | PASS | This matrix contains RP-01 through RP-19 exactly once. Every missing item has an exact later stage and no blocker remains. | Recreate the complete matrix after Stage 3. |

## Missing-feature classification

### Deferred

- One reusable packet scratch buffer and exact packet/byte/sample counters: Stage 3, RP-13/RP-14.
- Transfer-once completion buffer ownership: Stage 3, RP-14.
- Context-scoped live-section fingerprints, translated-value reuse, and decode/reuse counters: Stage 3, RP-15.
- Original R1-R14 bounds audit and named-ROM compatibility matrix: Stage 4, RP-16/RP-17.
- Android process/WebView profiles and renderer PSS: Stage 4, RP-18.

### Blockers

- None. The stale Party/team state found during this review was fixed and included in the fresh full gate.

## Stage decision

Stage 2 satisfies RP-01 through RP-12. RP-19 is complete for this checkpoint, all remaining requirements have an exact destination, and Stage 3 is authorized by the specification.
