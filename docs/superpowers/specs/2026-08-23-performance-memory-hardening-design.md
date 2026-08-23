# Performance and Memory Hardening Design

## Objective

DualDex must remain responsive during long sessions without continuous allocation churn, avoid Android heap spikes while loading ROMs and maps, and keep retained memory bounded as catalogs, discoveries, and UI history grow. Performance diagnostics remain internal to tests, logcat, and Debug Settings; normal application pages expose no implementation details.

This work improves the existing architecture incrementally. It does not split the parser into a separate application, replace the loopback API, redesign the UI, change Organic knowledge rules, add gameplay writes, or introduce cancellation timeouts.

## Confirmed baseline risks

| ID | Confirmed mechanism | Baseline impact |
| --- | --- | --- |
| PM-01 | `ProductionCompanionRuntime.battleCatalogContext()` and `saveParseContext()` rebuild immutable species/move maps and ABIs on every live heartbeat. | At the current 5 ms default, a 900-species/800-move hack can allocate about 680,000 map entries per second. |
| PM-02 | The web client fetches `/api/state` every 750 ms, the server serializes the complete state, and equal versions replace client state. | 4,800 full serializations, JSON parses, and render attempts per unchanged hour. |
| PM-03 | Live memory reads allocate a 4 KiB UDP buffer per poll, convert replies to strings, regex-split them twice, and copy completed regions. | Empty 5 ms polling alone allocates about 0.8 MB/s; successful reads add thousands of temporary strings per packet. |
| PM-04 | Local-map rendering expands indexed rasters, ARGB pixels, raw RGBA, compression buffers, and PNG copies without a process cache. | At least about 13 bytes/pixel transient JVM heap; a permitted 2,000,000-pixel map can exceed 26 MB before WebView decoding. |
| PM-05 | Every revealed scene raster stays mounted and timed URLs invalidate all mounted maps each game minute. | A 100-map 256x256 scene retains about 25 MiB of decoded pixels before DOM and browser cache overhead. |
| PM-06 | Loopback requests allow a single 256 MiB body, while raw/ZIP/7z paths may retain compressed, decompressed, decoder, and parser buffers together. | A normal 32 MiB archive/ROM path can approach 160 MiB before catalog allocations; the request limit alone can exhaust an Android heap. |
| PM-07 | Pokédex browsing mounts and rediffs every visible species and sprite. | A 900-species hack can retain about 14 MiB of decoded 64x64 sprite pixels plus DOM and JavaScript objects. |
| PM-08 | Catalog chunk reads retain chunk arrays, an assembled stream, a second assembled byte array, and decoded objects. Parser checkpoints rewrite large sections repeatedly. | Roughly three compressed-section copies can coexist before GZIP/Gson expansion. |
| PM-09 | Memory Mapper retains every capture and rewrites/base64-encodes the full history after every snapshot. | 100 GBA captures retain at least 28.1 MiB raw and can cause roughly 1.9 GiB cumulative writes before diff overhead. |
| PM-10 | ROM indexing and save polling repeat hashing, context construction, SQLite open, and deserialization without first proving inputs changed. | Persistent background CPU, I/O, allocation, and battery cost while the game/save is unchanged. |
| PM-11 | Activity and overlay may retain two WebViews; loopback uses an unbounded cached thread pool and active socket set. | Duplicate catalog/UI graphs and unbounded worker/socket residency are possible. |

## Requirements

### Sustained hot paths

- **R1 — Context reuse:** one immutable battle/save context is built per active catalog/runtime-layout generation. Repeated heartbeats for unchanged inputs return the same context objects. A catalog or runtime-layout replacement invalidates them atomically.
- **R2 — Unchanged state:** an unchanged state version produces no JSON state body and no client state replacement or Preact commit. The 750 ms connection heartbeat may remain, but unchanged responses must be constant-size and allocation-light.
- **R3 — Live polling:** steady polling must not exceed 40 polls per second. Discovery and connected operation use explicit state rather than an ignored interval argument. Packet parsing performs one byte-level pass without regex tokenization, and the UDP receive buffer is reused by the client lifetime.
- **R4 — Hot-path evidence:** deterministic tests record context construction count, unchanged-state body bytes, client render/update count, polls/second, packets/second, and bytes read. Diagnostics are not rendered on normal pages.

### Peak heap and large data

- **R5 — Request and archive bounds:** loopback ROM ingestion streams into app-private temporary storage instead of allocating the complete request body. Accepted compressed input is capped at 64 MiB and extracted GB/GBC/GBA content at 32 MiB. Rejection is explicit and fail-closed. A valid archive retains at most one complete extracted-ROM array.
- **R6 — Map rendering:** rendered PNGs use a byte-bounded least-recently-used process cache keyed by ROM SHA, map asset key, and time phase. The default encoded cache budget is 32 MiB. Concurrent requests for the same key share one render. Rendering is performed outside the runtime-wide synchronized state lock.
- **R7 — Map residency:** the browser mounts only the current scene placement and immediately adjacent placements required for seamless panning. Timed URL changes affect only mounted maps. The mounted decoded-pixel budget is 32 MiB; exceeding it evicts the least-recently-visible placement.
- **R8 — Catalog sections:** catalog reads assemble/decode chunks incrementally without retaining a list plus two complete compressed payload copies. Checkpoints write only sections whose encoded content changed.
- **R9 — Peak evidence:** tests report request/archive stage high-water bytes, map render count/cache hits, mounted raster count/decoded pixels, catalog section compressed/uncompressed sizes, and checkpoint bytes written.

### Long-lived residency and background work

- **R10 — Pokédex rows:** at most 60 species rows and their images are mounted at once. Search, filters, counters, keyboard focus, and scroll restoration retain their current behavior.
- **R11 — Change-first background work:** ROM indexing and save polling compare cheap identity metadata before hashing, opening SQLite, creating parse contexts, or deserializing snapshots. Unchanged inputs perform none of those expensive operations.
- **R12 — Mapper bound:** Memory Mapper retains at most 32 snapshots and at most 16 MiB of raw snapshot bytes, evicting the oldest records. Persistence appends new records and compacts only when eviction is required; it never rewrites an unbounded history.
- **R13 — WebView ownership:** only one visible WebView actively polls and retains the bootstrap catalog. A hidden surface pauses JavaScript work; a superseded surface releases its WebView and catalog graph.
- **R14 — Loopback ownership:** loopback serving uses a fixed pool of four workers and accepts at most eight simultaneous connections. Excess local connections receive a bounded failure response without creating another worker. Socket closure and server shutdown release every tracked connection.
- **R15 — Long-session evidence:** automated lifecycle tests prove all queues, histories, caches, workers, sockets, and mounted rows stay within their declared bounds. Runtime profiling records Java heap, native/PSS memory, GC count/pause time, CPU, WebView instances, and renderer PSS by ROM SHA and phase.

## Staged delivery

### Stage 1 — Stop continuous churn

Implement R1–R4: cache immutable contexts, suppress unchanged state bodies/renders, restore the intended distinction between configurable discovery polling and a 25 ms steady-state floor, and replace per-packet regex/buffer churn. This stage must reduce steady allocation without changing parsed data or visible navigation.

### Stage 2 — Bound peak heap

Implement R5–R9: streaming bounded ingestion, byte-bounded map rendering/residency, and incremental catalog section handling. Existing raw, ZIP, and 7z behavior remains supported for valid GB/GBC/GBA inputs.

### Stage 3 — Bound retained state

Implement R10–R15: virtualized Pokédex rows, change-first background work, bounded mapper history, single-active-WebView ownership, and bounded loopback workers. Complete the long-session profiling report for official Emerald, Odyssey, and Unbound without placing diagnostics in normal UI.

## Stage validation contract

Every stage ends with a requirements matrix committed beside the plan. Each applicable requirement has exactly one status:

- `PASS`: implementation and named evidence satisfy the requirement.
- `DEFERRED`: the requirement is intentionally assigned to a later named stage, with its current risk and owner recorded.
- `BLOCKER`: the requirement should be satisfied at this stage but evidence fails or the implementation is unsafe.

A stage may proceed with documented `DEFERRED` requirements only when their target stage is explicit. It may not proceed with a `BLOCKER`. When validation discovers an omitted requirement, it is added to the specification and matrix before continuing; it is then assigned as `DEFERRED` or `BLOCKER` using the same rule.

Each matrix records the test/benchmark command, observed value, expected threshold, result, and next action. Build success alone is not performance evidence.

## Compatibility and safety constraints

- Catalog output, SaveRAM interpretation, live-memory privacy, Organic/Discovered behavior, navigation, battle selection, map discovery, and ROM identity semantics remain unchanged.
- Official Gen I–III ROMs remain parser regression controls. Official Emerald, Odyssey 4.1.1, and Unbound 2.1.1.1 are the performance reference ROMs where their features apply.
- The parser continues to fail closed per optional capability. No ROM-name, filename, fixed-offset, or SHA selection is introduced.
- No emulator/device installation or interaction is part of automated implementation validation unless the user separately authorizes it.
- No timeouts are added for cancellation. State transitions, bounded queues, streaming limits, and resource ownership provide termination and backpressure.

## Completion criteria

The work is complete when all R1–R15 requirements are `PASS`, all three stage matrices contain no `BLOCKER` or unassigned requirement, the full web/Gradle/release-policy suites pass, the worktree is clean, and the protected prerelease artifact independently verifies package identity, checksum, signer, and provenance. Device profiling that requires user authorization remains clearly identified until that authorization and evidence exist; it cannot be silently counted as passed.
