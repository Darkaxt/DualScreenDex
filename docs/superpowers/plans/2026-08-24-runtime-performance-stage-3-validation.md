# Runtime Performance Stage 3 Validation

**Date:** 2026-08-24

**Stage:** 3 — Live-memory allocation and decode reuse

**Specification:** `docs/superpowers/specs/2026-08-24-runtime-performance-observability-and-churn-design.md`

**Result:** PASS — no blocker; Stage 4 may start

## Gate evidence

| Gate | Observed result |
|---|---|
| `./gradlew.bat :retroarch-session:test :battle-memory:test :app:testDebugUnitTest` | Fresh run: `BUILD SUCCESSFUL in 13m 15s`; retroarch-session 9 suites/34 tests/0 failures/0 errors; battle-memory 10 suites/43 tests/0 failures/0 errors; app 45 suites/262 tests/0 failures/0 errors/27 existing conditional skips; combined 339 tests/0 failures/0 errors. |
| Packet ownership fixture before | At Stage 2 commit `b27d579`, the two-packet/two-region fixture allocated 2 request-sized payload arrays, 2 destination region arrays, and cloned both region arrays at completion. |
| Packet ownership fixture after | Exactly 2 matched packets/8 payload bytes, 1 session scratch buffer, 2 owned region buffers, and 0 completion-region clones. Repeated terminal heartbeats return the exact same terminal object and region-array identities. |
| Section fingerprint control | A Trainer byte changes only `PLAYER`; a Party byte only `PARTY`; a clock byte only `OVERWORLD`; an event-flag byte only `PROGRESSION`. Fingerprints are SHA-256 hex strings over bounded ABI/context slices. Shared encryption-key dependencies are deliberately included in each section that consumes the key. |
| Section reuse control | An identical Gen III sample decodes each section once and reuses all four translated results. A clock-only mutation rebuilds only `OVERWORLD`; suspension and runtime-context replacement rebuild every section. |
| Retention control | The cache stores fingerprint strings plus translated section values. Tests prove it never retains a source `ByteArray`; raw regions remain owned by the completed read consumer only. |
| Profiler counters | Cumulative numeric counters now include live-memory packets, payload bytes, completed samples, scratch buffers, region buffers, completion clones, and decode/reuse totals for Player, Party, Overworld, and progression. No decoded values or raw bytes are logged. |

## Complete specification matrix

| Requirement | Status | Stage 3 evidence and invariant | Next action |
|---|---|---|---|
| RP-01 | PASS | Stage 1 structured NDJSON controls remain green in the full app gate. | Preserve in Stage 4. |
| RP-02 | PASS | Load/cache/parser-stage/readiness/failure coverage remains green. | Preserve in Stage 4. |
| RP-03 | PASS | Existing-heartbeat minute cadence and no-catch-up controls remain green. | Preserve in Stage 4. |
| RP-04 | PASS | Existing process/component counters remain; Stage 3 adds exact live-memory packet/byte/sample/allocation and section decode/reuse counters. | Capture representative Android values in Stage 4. |
| RP-05 | PASS | The bounded off-thread two-segment profiler and failure isolation controls remain green. | Preserve in Stage 4. |
| RP-06 | PASS | New metrics are numeric aggregates only; raw memory and translated values never enter events. | Preserve in Stage 4. |
| RP-07 | PASS | Profiler access remains confined to Settings Debug with no normal-page UI. | Preserve in Stage 4. |
| RP-08 | PASS | Owned WebView surface count remains explicit; renderer PSS is still correctly reserved for read-only ADB evidence. | Close renderer evidence in Stage 4. |
| RP-09 | PASS | Exact five-section updates and null/session clearing remain green in the full app gate. | Preserve in Stage 4. |
| RP-10 | PASS | Semantic gateway no-ops still preserve version/object identity and suppress listeners. | Preserve in Stage 4. |
| RP-11 | PASS | Seconds still feed initialization readiness without reaching post-ready web state. | Preserve in Stage 4. |
| RP-12 | PASS | Player and battle consumers remain isolated from unrelated section changes. | Preserve in Stage 4. |
| RP-13 | PASS | Each core-memory read session constructs one bounded scratch array and parses every matched packet directly into it in one byte pass. The two-packet fixture proves one construction. | Verify aggregate counters on Android in Stage 4. |
| RP-14 | PASS | Completed region arrays transfer without cloning; terminal state is immutable by ownership and repeated terminal heartbeats return identical objects. The fixture reports 0 completion clones. | Preserve in Stage 4. |
| RP-15 | PASS | Runtime-context-scoped SHA-256 fingerprints reuse translated Player/Pokédex, Party, Overworld, and Bag/event-flag sections. Exact slice mutations and context invalidation are tested; caches retain no prior raw regions. | Capture decode/reuse ratios on reference runs in Stage 4. |
| RP-16 | DEFERRED | The Stage 3 unit gate is green, but the complete original R1-R14 bounds audit belongs to final integration. | Run and record every bound in Stage 4. |
| RP-17 | DEFERRED | The app gate exercised its source-backed catalog controls; the named official/hack compatibility matrix remains the final reference gate. | Run Red, Crystal, Emerald, Modern Emerald, Odyssey, and Unbound controls in Stage 4. |
| RP-18 | DEFERRED | Host tests cannot establish Android Java/native/PSS, GC, CPU, or sandboxed renderer behavior. | Capture authorized device evidence in Stage 4; absence is a release blocker. |
| RP-19 | PASS | This matrix contains RP-01 through RP-19 exactly once. Every incomplete item is assigned to Stage 4 and no Stage 3 blocker remains. | Recreate the complete final matrix after Stage 4. |

## Missing-feature classification

### Deferred

- Complete original R1-R14 bounds audit: Stage 4, RP-16.
- Named official Gen I-III, Modern Emerald, Odyssey, and Unbound compatibility controls: Stage 4, RP-17.
- Android cold/cache/long-session process, WebView, renderer, and profiler evidence: Stage 4, RP-18.

### Blockers

- None.

## Stage decision

Stage 3 satisfies RP-01 through RP-15. RP-19 is complete for this checkpoint, all remaining work has an exact Stage 4 destination, and Stage 4 is authorized by the specification.
