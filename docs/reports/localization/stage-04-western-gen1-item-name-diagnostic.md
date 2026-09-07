# Stage 4 Western Gen I item-name diagnostic

Status: **focused synthetic verification and ten-control diagnostic passed; semantic acceptance remains open**. Task 392 is the diagnostic slice of Task 390 / `LNG-B002`, not item-name semantic acceptance. Production remains `5d65bf7256fb507c23be800174feabc599e7ef6c`, parser revision **59**, storage schema **2**. This checkpoint changes tests and documentation only.

## Exact scope and evidence boundaries

The capture selects the ten official English, French, German, Italian and Spanish Red/Blue-family and Yellow controls from the original 35-control Western manifest. Its SHA-256 is `ee408ad4a5d51da8656ff336ff7c139d92fc24b9d201f6817c47ce3ef194a749`. Every selected input is exactly 1,048,576 bytes; full language/family/hash identities must match the public matrix. Historical 0/62 item-name observations are not current reference certificates or expected names.

The separate `westernOfficialGen1BaselineTenControls` entrypoint requires the exact diagnostic opt-in. Metadata binding and fresh control directories precede ROM reads. Normal `analyzeForCatalog` wiring supplies the original session and frozen Gen I authority; materialization uses its ordinary callbacks without another parse or consumer search. All requested IDs remain in the producer/capability denominator. Sparse overlays contain only AVAILABLE, nonblank names.

The capture retains all fourteen `Gen1ItemReference` fields, including nulls. Visible witnesses cover map-bank/pointer/header/object-pointer binding, bounded variable-width object-list traversal and the seven-byte item row. Hidden witnesses cover the six-byte event row, nominated handler/continuation, coordinate traversal and three-byte coordinate record. Every witness is bounded and joined to the current numeric POI. The authority snapshot preserves both prefix roots independently, including equal-width branches, and the complete copy geometry. Its DTO has no compiled nomination/window inventory; that evidence remains a separate independent-proof obligation.

Gen I references have no quantity, coordinates or collection-flag field. Coordinates come from bounded raw records joined to numeric POIs; hidden `coordinateIndex` binds persisted `collectionFlagId`. API parity checks the exposed item fields and reopened name projection, not nonexistent quantity/flag fields. Shared numeric names remain null after localization extraction.

Every diagnostic retains separate capability, SQLite close/reopen, whole-catalog parity, section/integrity and cache-only API checks. A failed observation does not short-circuit unrelated later checks. Even complete observed names remain `semanticAcceptance=false` and `requiredSemanticCompletion=NOT_ACCEPTED`: production output is not its own semantic oracle.

## Focused implementation and verification

The test-only scope is two new Gen I capture/synthetic files plus the seven-line opt-in in the existing real-control class. The Gen II adapter and all production files remain unchanged.

The owner retained four distinct attempts:

| Attempt | Actual execution | Result | Elapsed |
|---|---|---|---:|
| RED | One new app synthetic | Expected assertion failure: 22 diagnostic checks expected, zero scaffold checks | 91.33 s |
| First GREEN | Four parser regressions; zero app methods | App compilation failed on unavailable `Files.writeString` / `Files.readString`; unexecuted app selectors are not passes or skips | 101.64 s |
| Byte-API correction | Four parser + eleven app methods | 15/15 passed, zero failures/errors/skips | 92.12 s |
| Nullability correction | Same fifteen methods | 15/15 passed, zero failures/errors/skips | 96.53 s |

Independent retained-evidence review checked all **93 root-index and 74 nested artifact hashes**, all four complete logs, six fresh XML files, exact command/method inventories, normalized patch and all **789 final source hashes**. Initial-to-final scope is exactly two added test files and the existing class's opt-in. These are synthetic results; no real-control method was selected.

Coordinator code review then found that two new synthetics incorrectly required `DUALDEX_TEST_TEMP_ROOT`, unlike neighboring tests. A fresh exact fifteen-method gate with that optional variable absent reproduced **13 passes and two failures**, zero errors/skips, in **95.73 seconds**, 43 tasks executed. Both failures were `Required value was null` at temporary-directory construction. Failed receipt SHA-256: `ffa2a2a2da99c90496ce5cfd782b2cc0615fc89325533589c3f04e1a6aa37799`.

The coordinator added the existing optional-root fallback convention in the new synthetic class only. The identical fresh selection then passed **15/15: four parser and eleven app**, zero failures/errors/skips, in **92.70 seconds**, 43 tasks executed. All **789 executable-source hashes** remained unchanged during the gate. Exact XML inventory and the complete log were checked; existing unrelated Kotlin/Gradle warnings remain. Receipt SHA-256: `923e3aeb1f510ac160e5843df001b512cfd08dafc45f65a1ad2dae6f0865ae70`.

## Single current diagnostic result

The coordinator executed the sole ten-control selector **once** on the successful synthetic freeze: exit **0**, **573.85 seconds**, **40 tasks executed**, one aggregate JUnit method and zero failures/errors/skips. All **789 executable-source hashes** and the original manifest/inventory pins remained unchanged. There was no timeout, retry or independent-proof execution. The complete log, fresh XML and wrapper receipt were inspected.

| Language | Red/Blue names / references | Yellow names / references | Required unavailable IDs per control |
|---|---:|---:|---|
| English | 62/62 / 156 | 62/62 / 161 | None observed |
| French | 60/62 / 156 | 60/62 / 161 | 46, 68 |
| German | 62/62 / 156 | 62/62 / 161 | None observed |
| Italian | 62/62 / 156 | 62/62 / 161 | None observed |
| Spanish | 62/62 / 156 | 62/62 / 161 | None observed |

Totals are **616/620 observed names**, **1,585 current typed references**, **2,825 control checks**, and **zero API reparses**. Each control performs one input read, original analysis and normal item-producer call. All ten diagnostic receipts finish `DIAGNOSTIC_CAPTURE_COMPLETE` with no failed checks. Original production authority reports `Available`; this is an observed resolver result, not an independent compiled proof. All fifteen capability inventories, sparse item-name denominators, raw-reference/numeric bindings, SQLite close/reopen parity and cache-only API checks pass their diagnostic contracts.

Both missing French IDs remain in `ITEM_NAMES.expectedRecords`; their absent labels are not waived by passing sparse-overlay assertions. The diagnostic alone does not establish their cause, nor does it certify the other 616 names. Every receipt retains `semanticAcceptance=false` and `requiredSemanticCompletion=NOT_ACCEPTED`. In particular, a Western Gen II token issue is not inferred for Gen I.

Receipt SHA-256: `59875af92c50baaea436d262769e9114f61a03c9e37d7dda7febafa85dffbcfd`. Complete log SHA-256: `36aa3d35b3138a8d6a0b6d85e49d7341cbdaea467175195cd010c1b766efc99d`. Exact source snapshots, original-input paths, reference witnesses, catalogs, SQLite databases and API captures remain outside public assets.

Independent retained-only reconciliation verified **146/146 diagnostic artifact hashes**, both 789-file source inventories against the current repository, exact fresh command/XML provenance, all ten receipt/batch pairs and all **2,825 passing checks**. All **1,585 typed references** match witness records: **1,060 visible and 525 hidden**. Requested IDs, producer fields and sparse overlays retain the same 616/620 observation. Numeric API parity covers 11,035 POIs; all 490 persistence and 390 API receipt checks pass, with archived integrity `ok`, empty foreign-key results and schemas 59/2. Witnesses remain `OBSERVED_NOT_PROVED`; no raw execution or semantic proof follows from reconciliation. The synthetic gate's fifteen indexed artifact hashes also match; its later one-shot diagnostic-pointer marker is an additional unindexed file, not a diagnostic-index coverage gap. The verifier performed no tests, ROM reads or artifact mutations.

## Independent source preparation, not a control oracle

A separate source-only preparation retained the [Generation I encoding reference, revision 4564128](https://bulbapedia.bulbagarden.net/w/index.php?title=Character_encoding_(Generation_I)&oldid=4564128), plus pinned English Red/Blue and Yellow and French/German/Spanish Red/Blue source. All 35 retained fetches and eleven local source pins were hash-verified. Archival wikitext SHA-256: `ff7d2748253731fba1593433c574d700a5d9c5aca4bdb4b8ab8c535a49d4df33`. This supplies candidate language-specific token domains, not evidence of which tokens the ten controls consume.

English source distinguishes the **20-byte ordinary copy** from the 13-byte item-name constant. Both generated branches copy **two-byte prefixes**, so a checker keyed only by prefix width would collapse HM/TM branches. Main-font Western glyph/digit declarations must not be replaced by Japanese aliases found elsewhere in a charmap. The candidate Italian repository was rejected because its inspected content still described German builds; localized Yellow/Italian consumer-source coverage remains unestablished. No binary behavior is inferred from that source-availability gap.

The Western Gen II ordinary `0x54` exception does not authorize Gen I substitutions. Any required token/prefix must receive separately justified Gen I authority, bound to the particular compiled input. No production token policy was changed. The approved reference-scoped static-label contract does not require a global item count, whole-table semantic domain, live MBC state, arbitrary RAM preservation or full text-renderer reconstruction.

## Remaining acceptance

Independent compiled/current-reference/token proof, resolution of both French missing-name gaps and every required name through materialization, actual SQLite close/reopen and cache-only API remain Task 390 obligations after this diagnostic. Western GBA item acceptance, Task 386's Local/region-label authority and reference-aware POI coverage, and Tasks 373–375's current **43-cell/44-control** matrix, all fifteen capability dispositions, native metadata-pin reconciliation, final current corpus after executable changes stabilize and published Stage 4 closure remain mandatory. No required cell is deferred. Stages 5–6 remain blocked. No corpus, native real-control gate, devices, signing, release APK or ROM publication is part of this checkpoint.
