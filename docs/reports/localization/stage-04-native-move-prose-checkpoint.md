# Stage 4 native move-prose checkpoint

Status: **corrected implementation independently verified, 96/96 tests passed**. FireRed's required positive acceptance remains blocked.

## Defect and authority

The three Japanese GBA controls previously selected primary learnset pointer tables as move prose. Packed level/move pairs passed Japanese text-plausibility thresholds, producing false coverage counts of 318/354 for Ruby/Emerald and 317/354 for FireRed. Nonblank text and persistence equality did not establish semantic correctness.

Bounded compiled-consumer proof establishes direct **56-byte records indexed by move ID** for Japanese Ruby and Emerald. The physical table starts at ID 1; the biased address for ID 0 is not a description record. The supported summary-screen consumers share the move index with a helper bound to the selected 12-byte numeric move table, and bind the resulting direct description address to a recognized text wrapper. Literals and calls are relocatable; no production ROM hash, title, filename or fixed root selects this ABI. Unknown instruction shapes remain unavailable.

The native classic-table path now requires this compiled role evidence and decodes inside each proven record boundary. Conflicting supported roots are rejected before choosing by readability or coverage. No-authority, incomplete-reference and budget outcomes cannot enter the unproven native pointer fallback. Existing embedded move-description paths and supported Western pointer behavior remain covered separately.

## Review corrections

One bounded review identified two defects, both reproduced before correction:

1. `CatalogMaterializer` retried a resolver's null result with a fresh, reference-free materialization. It now uses standalone materialization only when the resolver itself is absent. Existing resolver absence/conflict/budget results remain terminal; cancellation propagates.
2. Missing reference sites could hide an unreadable competing native root because text plausibility decided whether incomplete evidence mattered. That filter was removed.

Conservative rejection of every incomplete target passed synthetic safety tests but made both exact Ruby/Emerald positive controls unavailable. The implementation therefore recovers **all incomplete targets together in one bounded halfword scan**, using the original reference index's literal-load inclusion rules. Every original site count, including nonconsumer sites, must reconcile before role selection. The shared supported-prefix test only limits retained sites; it does not establish authority. Full consumer proof still decides roles, including unreadable competing roots. Count mismatch, target/count incompleteness, scan/work/site exhaustion and cancellation cannot silently become positive results. No shared-index API expansion or per-root full scan was introduced.

## Cache and acceptance contracts

Parser revision **49 → 50** invalidates caches that may contain false prose. Storage schema remains **2**. A stale-revision-49 rejection test was observed failing before the revision change and passing afterward.

The Ruby/Emerald joined tests require 354/354 descriptions and independent normalized UTF-8 sample digests for move IDs 1, 11, 72, 253 and 354 at parse, actual SQLite close/reopen, and API boundaries. These digests come from independently decoded, source-pinned control evidence, not production-output baselines. Whole catalog/overlay parity, cache-only bootstrap, `ROM_DEFAULT`, zero reparses and existing forecast assertions remain required.

A separate FireRed negative test requires `NOT_FOUND` 0/354 and empty localized/API move prose. Its passing result prints `requiredPositive=BLOCKED`; it is not the required positive control. `nativeOfficialJapaneseFireRedLeafGreen` is not weakened into an absence acceptance test.

## Verification

Owner final results on the frozen corrected seven-file source:

| Suite | Passed |
|---|---:|
| `CatalogParserTest` | 27 |
| `MoveDescriptionMaterializerTest` | 40 |
| Scoped `CatalogStoreTest` | 14 |
| Ruby/Emerald positive and separate FireRed negative joined tests | 3 |
| **Total** | **84** |

Zero failures/errors/skips. Retained red evidence includes the initial learnset/consumer regressions, stale cache rejection, Western terminal fallback regressions, both review findings, and the conservative rule's two actual native failures. Recovery regressions cover unrelated and actual authority-site overflow together, unreadable competing authority, count mismatches including nonconsumer sites, bounded work and cancellation.

The coordinator inspected the caller presence branch, compared recovery alignment/count rules with the original index builder, and inspected the corrective tests. The fresh combined coordinator gate passed **96/96 tests, zero failures/errors/skips**, on the exact frozen source: the 84 cases above plus 12 forecast assembler regressions. Gradle completed in **4 minutes 21 seconds**, with 45 tasks executed using `--rerun-tasks --no-parallel`. All seven tested source hashes remained unchanged, and independent XML/logs/source snapshots were retained. Existing unrelated compiler and Gradle deprecation warnings remain. An earlier independent 62/62 result applies only to the pre-correction snapshot and is not substituted for this final acceptance.

Source snapshots, exact-input receipts, raw logs/XML and SQLite caches remain private. No ROM payloads are added to public assets.

## Remaining Stage 4 work

- **Task 388 / `LNG-B002`:** FireRed's actual move-prose root, layout and compiled consumer remain unproven. The earlier bounded locator pass found no candidate; this is not proof that descriptions are absent. Required positive parse/reopen/API acceptance remains open.
- **Task 385 / `LNG-B002`:** implement and verify description-only applicability for the proven native species slots, preserving public regional numbering and independent numeric records.
- **Task 389 / `LNG-B002`:** Korean direct-sign opcode and headline-control proof is complete for two signs in each exact control, independently checked by the coordinator with bounded raw-byte/source-algorithm assertions. This is not executed Kotlin acceptance. Generic implementation and two-control parse/reopen/API verification remain pending; an opcode-only fix is insufficient.
- **Tasks 386 and 373–375:** complete remaining capability producers/applicability decisions, current 43-cell/44-control matrix, final current corpus after executable changes stabilize, ledger audit and stage closure.

Stage 4 remains open and Stage 5 blocked. No full corpus, device/emulator/ADB test, signing, release APK or cleanup is part of this checkpoint.
