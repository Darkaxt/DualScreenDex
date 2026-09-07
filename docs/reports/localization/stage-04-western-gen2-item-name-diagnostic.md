# Stage 4 Western Gen II item-name diagnostic

Status: **Western Gen II reference-scoped production semantic acceptance passed**. This checkpoint advances parser revision **58 → 59**, with storage schema **2** unchanged, from prior production `c0473667d16b019e9ba2415dc84d43e80d072b84`. Task 390 / `LNG-B002` and Stage 4 remain open for the remaining required work. The original failed diagnostic and first failed independent proof remain failed; the new proof and production acceptance are separate evidence.

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

## First independent proof attempt: terminal token boundary

After publishing the test/documentation checkpoint `c50e7a4e`, the coordinator reviewed the complete standalone source-authored checker/evaluator, its frozen contract, 31 source-file pins and all ten original-baseline input/reference nominations. A fresh independent synthetic run passed **18/18 methods**, including 510 in-memory instruction-chain evaluations. The original limits remained unchanged, with an additional 300-second process ceiling. Review receipt SHA-256: `a1d144cddc73bfb73bee97e7796cdc7165d12f7e1ec417ff92add7b832f8add5`.

The coordinator then separately authorized **one** read-only invocation of script SHA-256 `57efc3f0ac31b4746978b30bd9ed3f91bc88afd4bf570bd68effb86ac6f94c29` under contract SHA-256 `549111a6d1ff8d6b0d7507770e0a2a7795561f4b580525620bf43f9168ce9740`. It exited **2** in approximately **0.32 seconds**, without timeout or retry, and read only the first control: English Gold.

Before the stop, the checker matched nine compiled-code/directory windows containing 287 unique bytes, reconciled the original structural nomination and bound all 225 current typed references to raw witnesses. Evaluation reached required item ID **5**, whose complete 13-byte copied buffer begins with **`0x54`** and contains raw `0x50` termination at index 6. The frozen token oracle rejected `0x54` at index 0 as an unsupported control/substitution unit. These are retained diagnostic observations, not an accepted name oracle.

The terminal result is **`NOT_PROVED`**. All partial accepted labels were removed; all **690 requested-ID instances** remain represented. The other **nine controls were not read or evaluated**, so this observed token cannot yet be attributed to their missing names. No parser, materializer, SQLite/API, native acceptance or corpus rerun occurred. Production remains unchanged.

Proof receipt SHA-256: `f77be8498ceffef94ac516ea37a873592852796e8cb22065932dfe61291257d1`. Execution receipt SHA-256: `628436c7e6cd29c8f63a26c171bf482da3b3d04fec23773fbc784fd19915d753`. The frozen failed attempt is retained without modification. Source-only investigation of the observed token's static versus dynamic behavior precedes any revised proof contract or production change; neither automatic retry nor unsupported-token acceptance is authorized by this failure.

## Revised ordinary-name static-token proof

Source-only investigation distinguished `0x54` from dynamic substitutions. The pinned [Generation II encoding reference](https://bulbapedia.bulbagarden.net/w/index.php?title=Character_encoding_(Generation_II)&oldid=4564127) explicitly assigns the fixed text **`POKé`** to this byte in all five Western languages. Gold and Crystal's `PlacePOKe` handlers select a fixed `PlacePOKeText` literal; the French Crystal source corroborates this. Player/rival substitutions `0x52`/`0x53` and the distinct presentation token `0x24` are not covered by that authority. This is source-ratified codec interpretation, not proof of an exact binary's text-renderer chain.

A new private preparation preserved the original failed attempt and all original input, reference, compiled-consumer and budget contracts. Exactly three fixed-handler source files were added to the existing 31 pins. The original glyph allowlist remained unchanged; only ordinary-name `0x54` gained an explicit `StaticLiteralExpansion`: one encoded byte, four Unicode characters and five UTF-8 bytes. Each expansion character receives a charged cancellation/deadline check within the unchanged 128-byte output ceiling. Prefixes, generated labels and every other unsupported/disputed token remain terminal.

The coordinator reviewed the exact executable delta and structural comparison, independently checked all 34 source pins and original nominations, and passed **28/28 fresh synthetic methods**, including the 18 original methods unchanged. Review receipt SHA-256: `c2975048f24e312b938982c06926ed3c53da76c7d23cb48ddb9e444f28837745`. Script SHA-256: `47470d09fb2844416515b435a25376757e06129a5de7025f7a7992da327653d6`; contract SHA-256: `33bccbdac83b58b7c07362ffa8e19c99d82d37a832c8d880eb47ac0ad826c296`.

After a separate exact-invocation authorization under the approved host-work direction, this **new** contract ran once: exit **0**, approximately **14.19 seconds**, ten control reads, no timeout or retry. Its result is **`PROVED_REFERENCE_SCOPED_ONLY`**: all **690 requested names**, **2,440 current typed references**, **90 compiled windows** and **6,211 consumed tokens**. Every Gold/Silver control proves 62/62 requested names; every Crystal control proves 76/76. In each of the ten controls, required item ID **5** consumes the newly ratified static token. No other requested ID consumes it.

Independent retained-result verification reconciled exact identities and requested domains, all labels against source-authorized token units, field-local raw termination, complete 13-byte ordinary copies, reference nominations and witnesses, window hashes and budgets. A verifier-only assumption that payload witnesses were bare integers was corrected to their actual `{role, byte}` representation; neither ROMs nor the proof were rerun. The original failed proof remains hash-identical and **`NOT_PROVED`**.

Proof SHA-256: `4910fb487cb256cec0f0703f6301fe5167390ff121fb7cb3518acb5afbcb535b`. Execution receipt SHA-256: `5548126933d7a2fce9e669f2c40cb28b1394d61c20afe4bccb0ad0bdbca18c4f`. Accepted independent-oracle receipt SHA-256: `356d64dec41166d2d00e90adf9a79dec967e4c155e5eab11b517164cc45675c3`.

This independent acceptance supplies expectations for the requested item labels only. By itself, it does **not** establish production materialization, persistence/API acceptance, a whole-item-table domain, compiled text-renderer behavior or Stage 4 closure. The separate production correction and mandatory ten-control end-to-end gate are recorded below.

## Production correction and semantic adapter

The production patch registers one codec-owned, consumer-specific ratification: Western Gen II ordinary-name `0x54`, text `POKé`, encoded width one byte. The materializer opts in only for ordinary copies. `PokemonTextToken`, `DecodedText`, general decoding and its counters, codec IDs/versions, strict prefix/generated-label handling and original compiled/current-reference gates remain unchanged. The original strict codec constructor is retained, including trailing-SAM callers. Parser revision **59** invalidates revision-58 caches that may contain the missing label; storage schema remains **2**.

Read-only handoff verification reconciled all 148 artifact-index entries, the complete four Gradle logs, exact archived XML/command inventories and all 425 parser/store executable-source hashes. The two intentional REDs reproduce the unavailable ordinary label and revision-58 cache reuse. The first GREEN attempt failed parser compilation because a constructor extension broke trailing-SAM binding; only five store tests ran. Restoring the original strict constructor resolved source compatibility. The owner's final fresh gate passed **52/52 methods: 47 parser and five store**, zero failures/errors/skips, in approximately **82.51 seconds**. This evidence excludes the concurrent app changes and is not the production semantic gate.

The semantic adapter is separate from the diagnostic mode. Before any control ROM is read, it requires the exact reviewed proof hash, strict metadata parsing, all ten original identities and complete 690-name/2,440-reference domains. Every required name is checked against the independent oracle through producer, overlay, projected catalog, SQLite close/reopen and cache-only API; missing ID 5 remains fatal. The other fourteen capability status/count observations remain explicit regression controls, not semantic text expectations or Stage 4 waivers.

The first fresh coordinator combined gate compiled and executed all **69 methods**, with **68 passing and one failing**, in approximately **105.56 seconds**, 45 tasks executed. The failed synthetic attempted to construct an ambiguous `CatalogField` carrying a value; the model correctly rejected that invalid fixture before the intended boundary assertion. The correction explicitly checks constructor rejection, then uses a valid ambiguous field with no value for the required-name assertion. No production contract was weakened. Failed-run receipt SHA-256: `a0a742ba253334ed6791c579633bb7f9195eda563ae3b8e2ded0b2ed55ab7edb`.

After that fixture correction, the identical coordinator selection passed **69/69 fresh methods: 47 parser, five store and 17 app synthetics**, zero failures/errors/skips, in approximately **97.87 seconds**, 45 tasks executed. All **787 executable-source files**, including the new oracle helper, stayed unchanged. Exact fresh XML inventories and the complete log were checked. Receipt SHA-256: `d3c74c2749a3a38b2f0c9f990390ccce52876a2c8ada851cce6f3ac1c7f00bbd`. Neither real-control nor native methods were selected; existing Kotlin/Gradle warnings remain.

## Verified ten-control production semantic acceptance

The coordinator executed the separate `westernOfficialGen2SemanticTenControls` gate **once**, on the successful 69-method source freeze and original ten manifest identities. Its wrapper pinned the independent proof/contract/review, all **787 executable-source files**, exact sole JUnit selector and a 30-minute ceiling, with an exclusive one-shot marker and no automatic retry. Gradle exited **0** in approximately **170.34 seconds**, with **40 tasks executed** and one fresh aggregate JUnit method passing, zero failures/errors/skips. All source and evidence hashes stayed unchanged; there was no timeout or independent-proof rerun.

All ten receipts reach `SEMANTIC_ACCEPTANCE_COMPLETE`, `requiredSemanticCompletion=ACCEPTED` and `semanticCheckPathComplete=true`, with empty errors and all **1,450 control checks passing**. Each retains one input read, one original analysis and one original-session item-producer call. All **690 independently expected names** and **2,440 original typed references** pass producer, overlay, projection, actual SQLite close/reopen and cache-only API boundaries. All required IDs, including ID 5, are available; API reparses remain **zero**.

| Family | Accepted controls | Names per control | Typed references per control | Checks per control |
|---|---:|---:|---:|---:|
| Gold/Silver | 5 | 62/62 | 225 | 145 |
| Crystal | 5 | 76/76 | 263 | 145 |

Production-gate receipt SHA-256: `cb94e7026267a9c753b8e3d61d852e3f99d716736515220a76d6d30f49798473`. Full log SHA-256: `4b72c0632fc2c8298ffee9d21c9bc4f35a467d8e9124f8d70986cb2a3fb9620f`. This is reference-scoped static-label acceptance, not whole-table or compiled-renderer proof.

A separate read-only retained comparison verified the gate artifact index and independent proof, then reconciled every new name and all **2,440 API item occurrences**. Each overlay adds only ID **5**; the **680 previously available names**, all other overlay fields, the other **14 localized capability states**, current references and every captured shared snapshot field remain unchanged from the original diagnostic. SQLite integrity/foreign keys and metadata **59/2** pass, with database hashes unchanged after read-only close. In particular, item labels do not inflate `POI_TEXT`. This comparison covers the captured snapshot fields, not an all-18-section previous-version content comparison.

The comparator's first attempt stopped at an incorrect assumption that serialized `language` was an object. Inspection established its actual exact string representation; correcting only that assertion and rerunning the retained-data comparator completed all checks. No production code, proof or ROM gate was rerun. Comparison receipt SHA-256: `05243a72010b7f1f3509df82e30e8d113196848b53566ded6afb96ddcd6c98bb`, with zero ROM reads, parser runs and proof runs. The original diagnostic remains **FAILED**, and the first independent proof remains **NOT_PROVED**.

## Required next acceptance

This checkpoint completes Task 390's Western Gen II referenced-name slice only. Western Gen I/GBA item acceptance, Task 386's Local/region-label authority and reference-aware POI coverage, and Tasks 373–375's complete current **43-cell/44-control** matrix, all 15 capability dispositions, final current corpus and Stage 4 closure remain mandatory. Task 373 must reconcile native real-control revision-58 metadata literals with the final parser revision before its current gate; no native real methods ran here, and historical native acceptance is not relabeled as revision-59 evidence. No required cell is deferred. Stages 5–6 have not begun. No devices, emulator/ADB testing, signing, release APK, cleanup or ROM publication is part of this checkpoint.
