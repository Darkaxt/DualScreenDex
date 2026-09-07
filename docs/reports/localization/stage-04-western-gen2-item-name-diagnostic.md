# Stage 4 Western Gen II item-name diagnostic

Status: **diagnostic evidence, not semantic acceptance**. Task 390 / `LNG-B002` remains open. Production is unchanged from `c0473667d16b019e9ba2415dc84d43e80d072b84`: parser revision **58**, storage schema **2**.

## Exact scope and observed gap

One diagnostic invocation exercised the ten official English, French, German, Italian and Spanish Gold/Silver-family and Crystal controls. Their identities come from the retained 35-control Western manifest, SHA-256 `ee408ad4a5d51da8656ff336ff7c139d92fc24b9d201f6817c47ce3ef194a749`; this is not a corpus run or a replacement input set.

| Family | Separately captured controls | Typed references per control | Requested item IDs | Available names | Missing required IDs |
|---|---:|---:|---:|---:|---|
| Gold/Silver | 5 | 225 | 62 | 61 | 5 |
| Crystal | 5 | 263 | 76 | 75 | 5 |

Every original-session compiled authority reports `Available`. Each control retains one input read, one original analysis and one item-producer call. These are production observations, not independent compiled-consumer or text oracles. The missing result is `NOT_FOUND`, with the reason “item name lacks exact tokens and bounded termination”. Its item ID and observed neighboring names do not establish the cause or expected text.

The required missing name remains in both the producer result domain and the localized capability denominator: `ITEM_NAMES` reports **PARTIAL 61/62** or **75/76**, with one incomplete record. An unavailable required name is not waived by otherwise correct diagnostic accounting.

## Actual baseline failure

The test adapter uses normal `analyzeForCatalog` wiring. A test-only observer accesses the item materializer's original session rather than parsing a second time. Each control captures current typed references, five raw witness roles per reference, original authority, item results, the catalog snapshot, actual SQLite close/reopen evidence and cache-only API output.

The first synthetic attempt failed compilation and produced **zero fresh methods**; 213 stale XML methods were excluded. After correcting test-only prefix-variable and Android-stub API mistakes, the retained synthetic gate passed **10/10 methods: nine parser synthetics and one original-session observer**, zero failures/errors/skips, with 43 tasks executed.

The subsequent **single** real-control diagnostic invocation took approximately **154.33 seconds** and executed 40 tasks. Gradle exited **1**: **one aggregate JUnit method failed**, with zero errors/skips and ten complete control receipts. It was not retried or timed out.

The adapter incorrectly demanded that requested IDs equal the language overlay's item-name keys. The overlay is intentionally sparse: only available, unambiguous names belong there. All requested IDs must instead remain in the producer/capability denominator. Exact requested-ID/overlay-key equality is valid only when every requested name is available.

The combined capability check failed at that incorrect assertion, short-circuiting its later assertions. Its original `BASELINE_INTEGRITY_BROKEN` receipts and failed XML are preserved. Later retained-data reconciliation does not turn that JUnit run into a pass.

## Independent retained-data reconciliation

The coordinator verified all **202 frozen artifact hashes**, the before/after **786 source hashes**, exact three-test-file baseline scope and actual fresh XML inventories, and read the complete synthetic and baseline Gradle logs.

A separate bounded, read-only verifier then reconciled all ten retained databases:

- Integrity and foreign keys; exactly 18 sections; contiguous chunks; compressed SHA-256 digests; bounded decompression; unchanged database hashes after close.
- Complete snapshot parity for the manifest, language overlay, numeric capabilities, diagnostics, capture balls and POIs. All 18 sections receive integrity checks; this is not a previous-version content comparison.
- All 15 localized capabilities and their exposed API fields, including the preserved item-name denominator.
- **2,440 typed references** joined to persisted numeric item identity, map/coordinates, visible/hidden kind and collection flags.
- Every API item name joined through the reopened language overlay, with exact language/codec and `ROM_DEFAULT`, and **zero retained API reparses**.
- **12,200 retained witness rows**, with complete unique role domains and bounded offsets/spans. These checks do not independently replay the bytes against ROMs.

The verifier initially assumed direct storage/model equality. Explicit storage list DTOs and derived capability fields, API category projection, and overlay-based names were then reconciled against the actual model/reader/projection code. These were verifier-contract corrections, not new production defects, test reruns or weakened semantic expectations. The final reconciliation completed with exit zero.

Receipt SHA-256: `36e04dc4f879791565cc03ba343e70a258f44ac301b259fbb2018691051583eb`. The receipt explicitly retains `originalJUnitStatus=FAILED`, `semanticAcceptance=false`, zero ROM reads and no independent compiled proof.

## Model and API evidence boundaries

The shared numeric `LocalMapPoiItem` persists item identity and collection flags, not quantity. Localized names remain in the language overlay. Typed references/raw evidence retain quantity; typed-reference/SQLite joins verify collection flags. `LocalMapPoiView` exposes neither quantity nor collection flags. It projects identified visible/hidden items to `AVAILABLE_ITEM`, rather than exposing their raw persisted kind. API parity must check the fields and projection actually exposed.

The Korean checkpoint wording is corrected to the same boundaries without changing its completed tests, cache/reference evidence or production API.

## Adapter correction and focused regressions

The test-only correction compares overlay keys and values against AVAILABLE, nonblank producer results. It retains independent producer, numeric-catalog, localized-capability and API denominators. Twenty-two item/capability observation checks now record failures separately; SQLite and API inventory/count/name checks are also separated. Diagnostic completion explicitly retains unavailable requested IDs and `requiredSemanticCompletion=NOT_ACCEPTED`.

A new valid-sparse synthetic first produced **one fresh failure solely at `items.overlay-sparse-keys`**. After the minimal assertion correction, the owner's focused gate passed **11/11 methods**, zero failures/errors/skips, in approximately **89.61 seconds**, 43 tasks executed. Coverage includes missing producer/capability denominators, continued checks after inventory failure, unexpected/ambiguous/unavailable/blank names, empty/all-unavailable domains, and the real extractor's sparse/conflicting-name behavior with complete numeric retention. The original native test bodies and four earlier Western synthetic methods remain unchanged.

The coordinator reviewed the full adapter/synthetic files and added app-test diff, verified all **50 frozen handoff artifacts** and **786 unchanged executable-source hashes**, and reconciled the actual RED/GREEN XML with their commands and complete logs. A worker connection interruption occurred after both test invocations had already terminated; their retained results were recovered without an owner rerun. This correction does not rerun or replace the original failed ten-control baseline.

A separate fresh coordinator gate then passed **11/11 methods: five parser synthetics and six app observer/contract tests**, zero failures/errors/skips, with `--rerun-tasks --continue --no-parallel --offline` in approximately **80.11 seconds**, 43 tasks executed. All 786 executable-source hashes remained unchanged; exact fresh XML methods and the complete log were checked. Existing Kotlin/Gradle warnings remain. Neither real-control nor native acceptance methods were selected. Fresh receipt SHA-256: `bed5d9d5e680e410e3d2a6e9e0049017e104d004765ed95ad882e96c0603e121`.

## Required next acceptance

Task 390 must establish an independent bounded compiled-consumer and consumed-token oracle, identify and correct any proven production defect, and require every applicable expected name through materialization, SQLite close/reopen and API in all ten controls. Frozen proof limits and acquired source references alone are not executable proof or semantic acceptance. Observed baseline labels cannot serve as independent expectations.

Western Gen I/GBA item acceptance, Task 386's Local/region-label authority and reference-aware POI coverage, and Tasks 373–375's complete current **43-cell/44-control** matrix, all 15 capability dispositions, final current corpus and Stage 4 closure remain mandatory. No required cell is deferred. Stages 5–6 have not begun. No devices, emulator/ADB testing, signing, release APK, cleanup or ROM publication is part of this diagnostic.
