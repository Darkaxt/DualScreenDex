# RC65 Pre-UI State Trace and Pokédex Controls Validation

## Outcome

The RC65 implementation satisfies the pre-UI trace and Pokédex control-placement specification. It records a privacy-safe, revisioned field transition immediately before UI publication, persists the same immutable event through the bounded diagnostics log without per-change process sampling, and keeps Pokédex filters at the top while docking search and the dynamic counter below the results.

The original one-frame starter symptom remains a user-driven runtime validation. RC65 is designed to make that validation decisive: `DualDexState` will identify whether the transient count came from an incorrect live sample or a temporary recovery projection before the value reaches the UI.

## Specification cross-check

| Requirement | Status | Evidence |
| --- | --- | --- |
| ST-01 | PASS | `UnifiedGameStateDecoder.publishResolved` builds/submits the event before `notifyListeners`; ordering is asserted by `trace runs before listeners suppresses semantic no-ops and fails open`. |
| ST-02 | PASS | `ResolvedStateTraceTrigger` covers session, live, recovery, suspension, battle, and end transitions; decoder call sites supply typed triggers and a monotonic revision. |
| ST-03 | PASS | `ResolvedStateTraceEvent` contains ROM SHA prefix, generation, sample/recovery identifiers, recovery kind, changed sections, and field diffs. |
| ST-04 | PASS | `ResolvedStateTrace.kt` flattens seen/caught and party/stored into source, count, and fingerprint summaries; other sections expose availability/source/fingerprint. |
| ST-05 | PASS | `trace omits player save and raw species identities` rejects player name, public ID, save identity/path, URI, and raw species identifiers. Production search confirms no trace strings are rendered in normal UI. |
| ST-06 | PASS | The event is constructed synchronously at the decoder boundary; app-private persistence is dispatched only after the immutable event reaches the injected sink. |
| ST-07 | PASS | Production writes `DualDexState`; `AndroidPerformanceLogTest.state changes share the bounded log without exposing private values` proves the existing two-segment bound. |
| ST-08 | PASS | Trace submission is fail-open; a throwing sink still publishes the resolved snapshot to listeners. |
| ST-09 | PASS | Decoder tests cover hidden recovery 52 to live 1, live 1 to recovery 52 to live 1, and live 52 to live 1 with exact source/count assertions. |
| ST-10 | PASS | `PerformanceRecorderTest.state changes retain the pre-ui event without sampling process metrics` proves the sampler count does not advance and metrics remain empty. |
| PX-01 | PASS | `.browse-tools` contains only the filter strip directly after the normal header. |
| PX-02 | PASS | `.pokedex-screen` uses `auto auto minmax(0, 1fr) auto`; `.species-list` remains the expanding scroll region. |
| PX-03 | PASS | Search/count render after `.species-list` inside the non-scrolling `.pokedex-search-dock`. |
| PX-04 | PASS | Existing caught-total and `owned / found` projection is unchanged; Team omits the dock. |
| PX-05 | PASS | The production component only moved existing controls; the complete Pokédex behavior suite and full web suite remain green, including virtualization bounds. |
| PX-06 | PASS | The component test asserts `header -> browse-tools -> species-list -> pokedex-search-dock` and explicit Team omission. |

## Automated evidence

- Focused unified-decoder and performance tests: PASS.
- Pokédex component tests: 22/22 PASS.
- Complete companion web suite: 26 files, 192/192 tests PASS.
- Production companion web build: PASS.
- Android debug lint and release lint-vital: PASS.
- Android RC65 release assembly with `versionName=1.1.0-rc.65` and `versionCode=1010065`: PASS.
- Diff whitespace and diagnostics-scope searches: PASS.

The broad `:app:testDebugUnitTest` task was also attempted. Its unchanged `UnifiedGameStateRealControlTest` began reparsing the 14-ROM official/hack matrix and spent more than 14 CPU-minutes in the Gen I detached-species resolver without failure or completion. The task-owned invocation was stopped rather than turning this trace/UI release into another full parser campaign. All tests in the modified decoder, diagnostics, and UI surfaces passed independently.

## Deferred user validation

- Reproduce the starter acquisition once on RC65 and capture `DualDexState`. This is not an implementation blocker: the required instrumentation and exact transition controls are present, but the one-frame device symptom cannot truthfully be claimed as reproduced by automated tests.
- Confirm the bottom search/count dock is comfortable on the physical AYN Thor display. DOM order, responsive styles, semantics, and theme selectors are automated; physical readability remains user validation.

## Publication

Publication provenance, signed APK identity, SHA-256, signer certificate, and release URL are recorded in the follow-up RC65 publication report after the protected workflow completes.
