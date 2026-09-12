# Localization Stage 6 Closure

**Decision:** `HOST_COMPLETE — PACKAGED_AVD_PENDING`

**Stage branch:** `feat/full-translation-system`

**Synchronized baseline:** `2fef59df95f6deb0ae861aad42cff52eff739a86` (`fork/master`)

**Executable verification source:** `5397f6e3b131cf0e15e16fff4f13b4e09761e46d`

**Specification:** `LNG-INV-004`, `LNG-INV-008`, Sections 3.5, 12, 13, 14.1, 14.3, and Stage 6 in Section 15 of `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`

## Delivered interface system

Stage 6 adds a device-global interface language setting with `AUTO`, English, French, German, Italian, and Spanish choices. `AUTO` follows a bundled language-only system locale and otherwise resolves to English. Explicit choices persist independently of the active ROM and ROM-content language.

The Preact interface owns one compile-time-complete `MessageValues` contract with **713 typed messages** in each production dictionary. Static messages and locale-owned message functions share that contract. Number, date, plural, and normalization helpers receive the correct interface or ROM-content locale rather than treating the two authorities as interchangeable. Reactive locale changes synchronize `document.documentElement.lang`.

Production navigation, setup, settings, Pokédex, Party/specimen, Battle, Trainer, map/Area Guide, loading, empty, recovery, error, diagnostic, dialog, status, live-region, alt-text, and accessibility copy now render through typed messages. Stable route, control, enum, and protocol values remain untranslated. ROM-native names and descriptions continue to render directly from the active overlay and never pass through the interface dictionaries.

Native startup, recovery, Toast, export, and debug-QA surfaces have matching Android packs. Every production locale defines the same **19 main keys** and **12 debug keys** as English. The pseudo-locale is test-only, expands copy with visible delimiters, and is not a persisted production choice.

## Implementation provenance

The Stage 6 implementation is published through these ordered checkpoints:

- `4bead00e` — interface-language foundation;
- `8f9b5cbc` — RetroArch setup flow;
- `dc1beebe` — companion shell chrome;
- `33993fd3` — Pokédex browse surfaces;
- `ba937527` — move, ability, and nature details;
- `22345710` — Pokédex detail views;
- `cfaf4a26` — Party and specimen views;
- `faaf509c` — Battle surfaces;
- `c0bd8110` — Trainer progress surfaces;
- `1edbaf22` — map and Area Guide;
- `3c1a6f95` — companion settings;
- `98a24930` — presentation fallbacks;
- `7adc0357` — diagnostic surfaces;
- `78454fec` — native Android surfaces;
- `3099af70` — compact localized acceptance;
- `da3f0b69` — acceptance-contract corrections;
- `5397f6e3` — localized debug-QA resources and parity coverage.

`3cba2c42` subsequently removed four temporary retained-evidence paths from public Stage 4 documentation without changing executable behavior.

## Browser verification

- Production Vite build: passed.
- Production application-shell Vitest selection: **39/39 passed**.
- Dictionary tests verify identical keys, explicit/regional/unsupported fallback, reactive state, HTML language synchronization, locale plurals, and pseudo expansion.
- The exact compact matrix contains six locales (`en`, `fr`, `de`, `it`, `es`, pseudo) × three text scales (85%, 100%, 135%) at **538×445 CSS pixels**. The initial run passed six cells and found twelve stale case-sensitive Settings sentinels; all twelve corrected cells passed on the focused rerun.
- The remaining relevant browser acceptance initially passed 34 cells and found two genuine test-contract issues: a subpixel 85% virtual-row overlap and an obsolete raw backend error string. The production contracts now use a two-CSS-pixel fractional-geometry tolerance and typed `API_INVALID_REQUEST`; both affected cells passed on the focused rerun.

Together, the initial executions and exact focused reruns account for every browser failure without rerunning already-passing cells. They cover containment, intentional scroll ownership, reachable controls, focus restoration, accessibility labels, readable expanded copy, and synchronized virtual-row geometry.

## JVM and Android host verification

The consolidated host attempt completed `companion-core` with **132 cases: 131 passed, one skipped, zero failures/errors**. The app module completed **673 cases: 624 passed, 34 skipped, 15 failed**:

- three failures are the pre-existing challenge/control-inventory assertions in `ChallengeHackControlsTest`, `ChallengeOfficialControlsTest`, and `WesternGen1SemanticCaptureTest`;
- twelve `WorldMapCatalogApiRealControlTest` failures stop at the explicit missing `DUALDEX_NATIVE_CONTROLS` guard before reading any private control.

Those fifteen failures do not exercise Stage 6 interface behavior and are neither localization blockers nor localization deferrals. They are reported rather than hidden or repeatedly rerun.

The first Android lint pass then found a genuine Stage 6 gap: eleven debug-only QA strings lacked localized source-set resources. Four complete debug locale packs and a key-parity unit regression corrected the root cause. The focused `InterfaceLanguageSettingsTest` and `:app:lintDebug` gate passed. A fresh final `:app:assembleDebug` completed with `BUILD SUCCESSFUL` in 18 seconds after the resource correction. Debug packaging used debug signing only; no production signing material was accessed.

`LocalizationPackagedAcceptanceInstrumentedTest` is present and compiles. Its execution remains intentionally pending because the required next command starts an Android emulator/device validation boundary:

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:qaApi35DebugAndroidTest --stacktrace
```

No emulator, physical device, ADB operation, installation, production signing, release, or tag occurred.

## Consolidated final source-bound corpus

Stage 5 changed parser/catalog contracts, so the deferred single final corpus was executed after Stage 6 from exact executable commit `5397f6e3b131cf0e15e16fff4f13b4e09761e46d`. The current read-only inventory contains 265 archives, 334 manifest payload rows, 332 unique payload identities, and 334 filesystem candidates; the scanner admitted 333 inputs under its documented non-mainline exclusions.

### Provenance

| Artifact | SHA-256 |
|---|---|
| Archive manifest | `676d9b374433ac5c9c3fab3440c4ec30c590d53fc9a9384ad06129614108a0ec` |
| ROM manifest | `7309933b458b3af54a6330abbb6080dcc5447f2f94687ec09964d7c0c8e2280a` |
| Completion manifest | `077b23463c64a8b8bd971d1197cfb1d5f3a94bb2a6b420b640463372eb9647dd` |
| Raw JSON report | `2af6c0ba444f555d6943a644665abd43e3d808df1694beb6ceb0190e5c593b36` |
| Markdown report | `ea9541d1d81973ec7eaea36f6c7c84440ef7b98b9e622694e977d919f971c418` |
| Execution receipt | `ac383a2a7f8be41b5876eb4b55dccda6f39f7d32f26762aabecf87a4af4c94e3` |
| Run log | `a624042b406a3b7e7b757d452fb7495c0dbf6f756520ab623912507d756b7d39` |
| Generator | `8de5fada53efdd248b02f36cc9a6e86c40cfaf0da1d9e8e90795704b80bb3017` |

The report and receipt both bind the exact source commit and generator. Receipt schema 1 binds report schema 16, input count 333, and the exact raw-report hash.

### Aggregate outcomes

| Measure | Stage 4 closure | Current final run | Classification |
|---|---:|---:|---|
| Scanner-eligible inputs | 334 | 333 | Current inventory has one fewer eligible payload |
| Selected | 277 | 276 | Same one-row aggregate reduction |
| Ambiguous | 2 | 2 | Exact parity |
| No family match | 55 | 55 | Exact parity |
| Resolved selected manifests | 262 | 261 | Same one-row aggregate reduction |
| `UNKNOWN` selected manifests | 15 | 15 | Exact parity |
| Successful persistence observations | 277 | 274 | Two current rows exhausted the process heap |
| Unique successful cache identities | 276 | 273 | One duplicated successful selected identity |
| Parser-result errors | 0 | 0 | Exact parity |
| Catalog/reference errors | 0 | 0 | Exact parity |
| Persistence errors | 0 | 2 | Both are `OutOfMemoryError: Java heap space` |

All 274 successful persistence observations have non-empty, equal before/after logical digests. The cache directory contains 274 SQLite files: 273 reported successful identities plus one unreported partial output from a failed persistence attempt. That partial file is not accepted as evidence.

The two failed rows were otherwise selected and produced catalog summaries. The six-GiB, eight-worker process emitted two JVM `GCLocker` allocation warnings while retaining a 3.46-GB JSON report, then recorded both persistence failures as heap exhaustion. This is a bounded host-capacity failure, not a changed language/family decision or hidden parser exception. The rows failed closed and the reports were retained for diagnosis. In accordance with the agreed single-final-corpus policy, no second full corpus run was started and these two observations are supplemented only by the bounded recovery below.

### Bounded persistence recovery

The two warning-adjacent rows were run individually and serially with `--jobs 1`, the same six-GiB ceiling, the same parser CLI generator, and the same exact executable source commit. This was not a second corpus: no other input was reparsed. Each one-input report selected the same family as the final corpus, produced one successful SQLite cache, recorded no persistence, parser, catalog, diagnostic, or reference error, and had equal non-empty before/after logical catalog digests.

| Artifact | First recovery | Second recovery |
|---|---|---|
| Raw JSON report | `ecf9c9b74765df0f7c6bc38e3d4846ce3d07a108674faaf841c4d306940a71aa` | `92717da1f0345ffd6d8a3319f7697406a116b9e173926fdc3dfe709fd62e0155` |
| Markdown report | `997fb5d647750864eba2d68ccab5212efb1a92acf8fb1b4628486f56f37c8449` | `22c5d88c84e44953df879017142b428510b4e3ec3aa75c7d94f658ed440c4459` |
| Execution receipt | `37bef316b17bfaa40a6cdac5e09ba870ff24af844bd2dcca7c31ad21c74a264a` | `b474b2f612711bbc31c8d78af1913d9b18cff2d87aa5ffe3fe59164479fefc8c` |
| Run log | `ab12639111c39cbbc047dfa16f7f91cf5a25b362bf323627f98b638b30b6f7f1` | `8e8939cbf3361d28312d784fb1086600b531ef1c359b8e678c61bdb544b69bae` |

Each receipt binds report schema 16, receipt schema 1, generator `8de5fada53efdd248b02f36cc9a6e86c40cfaf0da1d9e8e90795704b80bb3017`, input count one, the exact raw-report hash, and source `5397f6e3b131cf0e15e16fff4f13b4e09761e46d`. Combined with the 274 successes in the final corpus, all **276 selected rows** now have source-bound persistence/reopen evidence and matching logical digests. `LNG-B005` is closed without weakening evidence or repeating the 333-input corpus.

## Blocker and deferral audit

No Stage 6 implementation `STOP-SAFETY` or `STOP-CORE` defect remains from the host gates. The following acceptance work remains explicit:

| ID | Classification | Observed | Owner / target | Acceptance | Status |
|---|---|---|---|---|---|
| `LNG-B004` | `STOP-CORE` acceptance gate | Packaged Android/WebView localization instrumentation has compiled but has not executed under the required emulator boundary. | Stage 6 packaged-AVD acceptance | The exact packaged test passes without parser/network dependency and restores `AUTO`. | Open |
| `LNG-B005` | `STOP-CORE` evidence gap | Two selected corpus rows initially lacked successful persistence/reopen evidence because the final process exhausted its six-GiB heap. | Final-system acceptance | Both exact warning-adjacent rows pass separately source-bound, jobs-one persistence/reopen verification with matching logical digests. | Closed |
| `LNG-D001` | `POST-SYSTEM` | Expanded fan-translation/language-unresolved corpus support is not universally ratified. | First post-official localization corpus stage | Generic manifests/codecs pass sanitized source-backed corpus evidence without identity hacks. | Open |
| `LNG-D002` | `POST-SYSTEM` | Japanese and Korean interface packs are outside the initial interface set. | Post-Stage 6 interface expansion | Complete typed dictionaries, native resources, font/line-break, accessibility, and compact-layout gates pass. | Open |
| `LNG-D003` | `POST-SYSTEM` | No RTL interface locale is bundled. | First supported RTL locale | Direction-aware layout, navigation/icons, bidirectional text, and compact acceptance pass. | Open |
| `LNG-D004` | `POST-SYSTEM` | Non-production documents and store material remain English. | Separately commissioned documentation/release milestone | Locale set, owner, review, and publication path are implemented. | Open |

`LNG-B004` remains an acceptance gate, not an omitted product feature or post-system deferral. `LNG-B005` is closed by the bounded recovery evidence below. The implementation remains fail closed while `LNG-B004` is open.

## Privacy boundary and decision

The branch audit found no added ROM, SaveRAM, SQLite, APK, archive, keystore, signing, private-memory, or raw corpus artifacts. A separate path/key scan found and removed four temporary evidence paths from public documentation; the final scan found zero added private path or signing-key patterns. Raw reports, cache databases, source inputs, and retained private evidence remain outside Git.

`HOST_COMPLETE — PACKAGED_AVD_PENDING` — Stage 6 code, translations, browser acceptance, lint, host packaging, and final corpus persistence evidence are complete and pushed. Full Stage 6 acceptance is not claimed while `LNG-B004` remains open. Work pauses before the packaged Android emulator command as required.
