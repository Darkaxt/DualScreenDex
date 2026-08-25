# Pre-UI State Trace and Pokédex Controls Design

## Objective

DualDex must preserve enough bounded, privacy-safe evidence to explain every resolved transient-state change before it reaches the UI. The immediate acceptance case is the brief first-starter transition in which the Pokédex shows several caught Pokémon and then corrects to one. The same release also moves the Pokédex search/count row to the bottom of the screen so the filter tabs remain at the top.

The diagnostic trace is never rendered on a normal application page. It extends app-private/ADB diagnostics only and does not change live-memory, SaveRAM, checkpoint, knowledge, or UI projection precedence.

## Confirmed baseline

- The installed `1.1.0-rc.64` performance log records timings, memory/GC/CPU samples, decoder counters, and cumulative changed-section publication counts.
- During the reported acquisition window, cumulative `PLAYER` and `PARTY` publications advanced while the cumulative `RECOVERY` count did not.
- The current event schema cannot identify the producer of an individual publication or record before/after field sources and counts. It therefore cannot distinguish an incorrect live decode from a temporary fallback to an already-held recovery value.
- `UnifiedGameStateDecoder.publishResolved()` computes the final snapshot and changed sections immediately before notifying `ProductionCompanionRuntime`; this is the authoritative pre-UI boundary.

## Requirements

### Structured pre-UI trace

- **ST-01:** Every semantically changed resolved snapshot emits one trace event immediately before listeners are notified.
- **ST-02:** Events have a monotonic revision and identify the trigger: live sample, recovery application/status, recovery clear, live suspend, battle tracking, session begin, or session end.
- **ST-03:** Events record ROM SHA prefix, generation, live sample ID, recovery application ID/kind, changed sections, and privacy-safe before/after field summaries.
- **ST-04:** Pokédex summaries record seen/caught source and count. Party/stored summaries record source and count. Other resolved fields record source, availability, and a compact non-sensitive summary sufficient to tell whether the value changed.
- **ST-05:** Events never contain player name, public Trainer ID, save identity, filenames/paths, raw ROM/save/RAM bytes, decoded text, or stack traces.
- **ST-06:** The event is immutable before the UI callback. Persistence/log serialization may run through the existing bounded diagnostics dispatcher, but event creation and sequence assignment occur at the decoder boundary.
- **ST-07:** The trace uses a dedicated `DualDexState` logcat tag and the existing two-segment app-private diagnostics retention. High-frequency position changes remain bounded by rotation and cannot grow storage without limit.
- **ST-08:** Trace failure is diagnostic-only: it cannot fail or delay state publication, ROM loading, or live polling.
- **ST-09:** Tests reproduce `RECOVERY caught=N -> LIVE caught=1`, `LIVE caught=N -> temporary RECOVERY caught=M`, and `LIVE caught=N -> LIVE caught=1`, proving the trace identifies the exact trigger/source transition in each case.
- **ST-10:** Existing performance events remain compatible. State-change events do not take an Android heap/PSS/GC sample for every coordinate update.

### Pokédex control placement

- **PX-01:** The filter tabs remain directly below the normal Pokédex header.
- **PX-02:** The species list is the only expanding/scrolling middle region.
- **PX-03:** Search and the dynamic counter occupy a non-scrolling bottom dock.
- **PX-04:** Existing counter behavior is unchanged: caught shows one total; other searchable tabs show `owned / found`; Team shows neither search nor counter.
- **PX-05:** Moving the controls does not change filtering, search reset, virtualized-row bounds, Organic discovery, accessibility labels, or header navigation.
- **PX-06:** Layout tests assert DOM order (`header -> filters -> results -> search dock`) and Team-tab omission. Existing Pokédex behavior tests remain green.

## Design

### Trace boundary and event model

`UnifiedGameStateDecoder` receives an optional no-op-by-default trace sink. Each state-producing entry point supplies a typed trigger to `publishResolved`. After resolving and diffing the snapshot, but before `notifyListeners`, the decoder creates a compact event from the previous and next snapshots and submits it to the sink inside a fail-open boundary.

The event contains field-level changes rather than serialized snapshots. Set/list fields use source and count plus a deterministic digest when necessary to distinguish equal counts without disclosing member IDs. Scalar fields expose availability/source and only non-identifying game-state values. Identity and save selectors expose presence/source only.

Production adapts the event into the existing bounded diagnostic event stream without invoking the expensive performance sampler. It also emits the same JSON to `DualDexState` for read-only ADB capture. Tests use an in-memory sink.

### Pokédex layout

`PokedexBrowse` separates the current combined tools block into a top filter strip and a conditional bottom search dock. The screen grid becomes `header / filters / minmax(0, 1fr) results / search-dock`. Because only `.species-list` scrolls, both filter tabs and the search dock stay visible. The Team tab omits the final row without reserving visible space.

## Validation

- Focused decoder tests prove ordering, triggers, source transitions, counts, privacy, no-op suppression, and sink fail-open behavior.
- Performance-log tests prove state events remain bounded/rotatable and do not sample process metrics.
- Pokédex component tests prove order, bottom docking, Team omission, counter semantics, search, and the 60-row virtualization ceiling.
- Run focused web and JVM suites, then the relevant app unit-test task and release-policy/version gates. Device reproduction remains user-driven; ADB diagnosis only reads version and `DualDexState`/`DualDexPerf` logs unless the user explicitly requests installation or UI operation.

## Completion criteria

The implementation is complete when a new prerelease can capture the one-frame starter transition as an exact pre-UI source/count sequence, normal UI contains no diagnostics, Pokédex controls match PX-01 through PX-06, relevant regressions pass, and the signed release artifact is published and independently verified.
