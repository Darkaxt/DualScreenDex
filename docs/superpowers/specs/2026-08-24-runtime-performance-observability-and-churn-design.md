# Runtime Performance Observability and Churn Hardening Design

## Objective

DualDex must produce bounded, privacy-safe execution metrics for every catalog load and during long-running sessions, then use those measurements to remove the live-state and core-memory churn found after the unified transient-state migration. Diagnostics remain confined to logcat, app-private storage, and the explicitly labelled Debug section in Settings. Normal pages must never expose ROM identities, parser stages, capability codes, memory metrics, or implementation details.

This work extends the existing performance and memory contract rather than replacing it. The previously proven cache, archive, map, catalog, Pokédex, mapper, WebView, and loopback bounds remain mandatory regression controls.

## Confirmed current findings

| ID | Finding | Current consequence |
| --- | --- | --- |
| PF-01 | `LiveClockState.seconds` participates in unified snapshot equality. A seconds-only change notifies every resolved-state consumer. | Player, Party/progression, overworld, and battle work is reconsidered for a change that only initializes or advances the clock. |
| PF-02 | `applyResolvedPlayerState` dispatches whenever live seen/caught sets are available, even if neither the Trainer Card nor either set changed. | `CompanionGateway` increments the state version and the next web heartbeat returns a complete state body for a semantic no-op. |
| PF-03 | Active battle state is rebuilt and dispatched whenever any unified section changes. | Clock or location changes can trigger unrelated battle projection and web-state work. |
| PF-04 | Coalesced core-memory reads allocate a request-sized payload for every matched packet and copy completed logical-region arrays. | The RC48 direct-to-destination allocation contract has partially regressed. |
| PF-05 | Gen III player codecs decode Trainer, Pokédex, Party, Bag, and event flags for every complete live sample. | Allocation and CPU cost continue even when the corresponding source bytes are unchanged. Material impact requires runtime evidence. |
| PF-06 | R15 device evidence was never collected. | Java heap, native heap/PSS, GC, CPU, WebView instance, and renderer-PSS behavior remain unproven on Android. |

## Requirements

### Instrumentation and privacy

- **RP-01 — Structured event contract:** profiler output is newline-delimited JSON with a schema version, session ID, monotonic elapsed time, event kind, optional stage, and a metric snapshot. Field names and meanings are stable and unit-tested.
- **RP-02 — Load coverage:** each load records `LOAD_STARTED`, `CACHE_DECISION`, every parser work-module transition that actually runs, `CATALOG_READY`, `WAITING_FOR_GAME_ACCESS`, `GAME_ACCESS_READY`, and `LOAD_FAILED` when applicable. A cache hit records no parser-module events.
- **RP-03 — Runtime cadence:** an existing application heartbeat, not a new scheduler or cancellation timeout, emits at most one `RUNTIME_MINUTE` sample per monotonic minute while a session is active. Catch-up never emits missed historical buckets.
- **RP-04 — Metric coverage:** every event records Java heap used/committed, native heap allocated, own-process PSS, process CPU time, ART GC count/time when available, and thread count. Component counters include state dispatch/no-op counts, state response body bytes, live-memory packets/bytes/samples, section decode/reuse counts, map cache entries/bytes/hits/renders/evictions, active WebView surfaces, loopback workers/connections, and mapper retained snapshots/bytes when those components are available.
- **RP-05 — Bounded retention:** logcat receives each event and app-private rolling storage retains at most 1 MiB total. Rotation cannot block load/runtime state locks and a logging failure never fails a ROM load or live session.
- **RP-06 — Data minimization:** events may contain the active ROM SHA-256 prefix, generation, cache decision, parser-module name, and numeric metrics. They never contain filenames, filesystem/document paths, player/save identity, raw ROM/save/memory bytes, decoded game text, or exception stack traces in the persisted NDJSON.
- **RP-07 — Debug-only access:** the retained profiler log is exportable only from the Debug section of Settings. No profiler text, indicator, status, or metric appears in Setup, loading, Pokédex, Party, Trainer, Battle, Atlas, or map pages.
- **RP-08 — Renderer evidence boundary:** the application records its owned WebView surface count. Sandboxed WebView renderer PSS is collected by a documented read-only ADB command during validation and is not guessed from the app process.

### Unified-state churn

- **RP-09 — Section changes:** unified publications identify which resolved sections changed: recovery, player/Pokédex, party/progression, overworld/clock, and battle. Runtime consumers execute only for their changed sections.
- **RP-10 — Reducer no-op safety net:** if reducing an action produces state equal to the current state, `CompanionGateway` does not increment the version or notify listeners.
- **RP-11 — Clock granularity:** live seconds remain available to the one-way game-initialization gate. After access is ready, seconds-only changes do not change the user-facing clock, gateway version, HTTP state body, or client state.
- **RP-12 — Exact player and battle dispatch:** Trainer/Pokédex dispatch occurs only for a changed Trainer Card or newly merged seen/caught knowledge. Battle dispatch occurs only when the projected battle state changes. Unrelated section changes do not dispatch either action.

### Live-memory allocation

- **RP-13 — Packet allocation:** one scratch payload is allocated per core-memory read session and reused for every matched coalesced packet. Reply parsing remains a single byte pass without strings or regex tokenization.
- **RP-14 — Completion ownership:** completed logical-region buffers transfer exactly once to the consumer and are never mutated afterward. Completion does not clone every region. Tests prove stable identity and immutability-by-ownership after terminal state.
- **RP-15 — Section decode reuse:** Gen III live decoding caches translated section values by runtime-context generation and a privacy-safe fingerprint of the exact source slices. Unchanged sections reuse their translated values. A relevant source-byte change invalidates only its section; a session/context change invalidates all section caches. No raw prior live-memory regions are retained.

### Regression and evidence

- **RP-16 — Existing bounds:** the original R1-R14 controls remain green: context reuse, HTTP 204/client suppression, 40-cycle steady polling ceiling, bounded ROM/archive ingestion, 32 MiB encoded and decoded map budgets, incremental catalog sections, 60 Pokédex rows, change-first ROM/save work, 32/16 MiB mapper limits, one active WebView, and four/eight loopback worker/connection limits.
- **RP-17 — Reference compatibility:** automated gates retain official Gen I-III, Modern Emerald, Odyssey 4.1.1, and Unbound 2.1.1.1 controls without changing parsed catalogs, SaveRAM interpretation, Organic discovery, battle selection, map behavior, or navigation.
- **RP-18 — Android evidence:** cold parse, cached reopen, waiting-for-game, and steady-runtime profiles are captured by ROM SHA and phase. Official Emerald, Odyssey, and Unbound receive long-session Java/native/PSS, GC, CPU, owned-WebView, and renderer-PSS evidence. Red, Crystal, and Modern Emerald receive cold/cached load evidence.
- **RP-19 — Stage matrices:** after every implementation stage, a committed matrix maps RP-01 through RP-19 exactly once to `PASS`, `DEFERRED`, or `BLOCKER`, records observed evidence and commands, and names the next stage for every deferred item. A stage cannot continue with a blocker.

## Architecture

### Performance recorder

`PerformanceRecorder` is a small application-owned coordinator. It accepts a monotonic clock, an Android metric sampler, a bounded event sink, and component counter providers. Production sends sampling, serialization, logcat, and file work through one bounded off-thread dispatcher so profiler work cannot hold a load or runtime-state lock. Production uses `AndroidPerformanceSampler`, `AndroidPerformanceLog`, and logcat. Unit tests use deterministic clocks, samplers, dispatchers, and in-memory sinks.

Events are snapshots rather than traces. Parser work transitions close the previous stage and begin the next one. The existing RetroArch setup heartbeat calls `runtimePerformanceHeartbeat()`; the recorder emits only when the monotonic minute bucket advances.

The rolling store uses two app-private files capped at 512 KiB each. Rotation deletes the older segment, renames the active segment, and creates a new active segment. If rotation cannot complete, the new record is dropped rather than exceeding the bound. Export flushes queued profiler work, concatenates older then active content, and therefore remains at or below 1 MiB.

Stage 1 exposes process metrics plus counters already owned by map caching, WebView ownership, loopback capacity, and the memory mapper. Counters created by the Stage 2 state-dispatch work and Stage 3 memory/decode work become mandatory with RP-09 through RP-15; before then they are absent, never fabricated as zero.

### Unified change propagation

`TransientGameStateListener` receives a `ResolvedGameStateUpdate` containing the full current snapshot and an `EnumSet`-equivalent set of changed sections. Recovery and live inputs still resolve through one `UnifiedGameStateDecoder`; consumers do not create independent fallback pipelines.

The runtime invokes only the relevant application functions. `CompanionGateway` equality suppression remains a final defensive boundary so an accidental semantic no-op cannot advance the public state version.

Seconds remain in `LiveClockState` and in readiness evaluation. The user-facing `GameClock` continues to project hours, minutes, phase, and phase progress only, so seconds cannot create visible churn after readiness changes once.

### Allocation-light memory decoding

`CoreMemoryReadSession` owns its destination buffers and one reusable scratch array. A completed session transfers those buffers to `CoreMemoryReadState.Complete`; the terminal session performs no later writes. The consumer treats the arrays as immutable owned input and discards them after decoding.

`UnifiedGameStateDecoder` stores only compact section fingerprints and translated section values. Fingerprints include runtime-context generation and the exact ABI-defined input slices for each section. They are performance hints backed by deterministic source-slice tests; session replacement clears every cached value. Raw prior regions are never retained.

## Staged delivery

### Stage 1 — Runtime observability

Implement RP-01 through RP-08 without changing parsing, live-state semantics, navigation, or normal UI. Complete a Stage 1 matrix before continuing.

### Stage 2 — Unified-state change isolation

Implement RP-09 through RP-12. Prove seconds-only and unrelated-section changes cause no cross-section work or web-state replacement. Complete a Stage 2 matrix before continuing.

### Stage 3 — Live-memory allocation and decode reuse

Implement RP-13 through RP-15. Use Stage 1 counters to record the before/after packet allocation and section-decode changes. Complete a Stage 3 matrix before continuing.

### Stage 4 — Full regression and Android evidence

Verify RP-16 through RP-18, close every earlier deferred item, and produce the final RP-19 matrix. Build/release evidence remains separate from device behavior evidence.

## Stage validation rules

Each matrix uses exactly these statuses:

- `PASS`: implementation and the named evidence satisfy the requirement.
- `DEFERRED`: the requirement is assigned to a specific later stage and the current risk is recorded.
- `BLOCKER`: the current stage should satisfy the requirement but implementation or evidence is missing/unsafe.

Every matrix includes exact commands, observed values, expected invariants, and next action. A successful build is not performance evidence. Missing requirements discovered during validation are added to this specification before being classified.

## Safety constraints

- No gameplay writes or emulator commands are introduced.
- No parser selection by filename, ROM name, SHA, fixed offsets, or source symbols is introduced.
- No cancellation timeout is introduced.
- No raw memory or save bytes enter profiler logs.
- Profiling failure is fail-open for diagnostics only and cannot fail the application workflow.
- Device installation, launch, and interaction remain separate from automated build evidence.

## Completion criteria

The work is complete when RP-01 through RP-19 are `PASS`, the original R1-R15 performance matrix has no blocker, full automated compatibility gates pass, Android profiling evidence is recorded where authorized, the worktree is clean, and any published prerelease independently verifies version, checksum, signer, and provenance.
