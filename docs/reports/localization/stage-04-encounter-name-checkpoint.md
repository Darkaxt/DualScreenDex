# Stage 4 encounter-name boundary checkpoint

Status: **verified bounded correction; not Stage 4 closure**.

## Corrected semantic boundary

`ENCOUNTER_AREA_NAMES` now contains only the unique ROM-native base-area label authorized by `AREA_NAMES` or an authorized static `LOCAL_MAP_NAMES` entry. Generated parser labels such as `Map 4-9 - morning grass`, and mixed labels such as `FAUSTAUHAVEN - water`, are not ROM-localized text and are no longer copied into language overlays.

Encounter method and time-window semantics remain in the shared `EncounterArea.methodId` and `EncounterArea.windows` fields. Translating interface labels such as grass, water, fishing, morning, day and night remains Stage 6 work; this checkpoint does not place that application copy in a ROM-language overlay.

Multiple candidate labels for one base-area ID must agree exactly. Missing or conflicting authority leaves that encounter name unavailable while preserving the encounter record and its numeric semantics. Persisted overlays are validated against the same resolver, so a malformed parser-73 overlay cannot reopen with a synthesized or suffix-contaminated encounter name.

Parser schema **73** invalidates parser-72 caches that may contain generated or mixed encounter labels. SQL schema remains **2** and codec version remains **1**.

## Verification

The extractor regressions first failed against generated encounter labels. A separate forged-overlay regression then failed with one assertion before persistence validation was added. The corrected focused gates passed:

- `CatalogLanguageOverlayTest`: 26/26 passed;
- `CatalogParserTest`: 28/28 passed;
- `CatalogStoreTest`: 69 passed, six existing optional tests skipped, zero failures/errors;
- `OfficialMatrixApiCaptureTest`: 23/23 passed.

Total: **146 passed, six existing optional skips, zero failures/errors** across 152 selected cases. The parser selection was freshly compiled and executed with `--rerun-tasks`; the store and API selections executed after the final production change. Existing compiler and Gradle deprecation warnings remain.

A read-only reconciliation of superseded official-matrix diagnostics found a single unambiguous native base label for every encounter record across all 44 controls, with zero missing and zero conflicting base labels. That result justified the bounded correction but is not current parser-73 matrix evidence and is not final semantic acceptance.

## Remaining work

Task 436's implementation checkpoint is complete, but Task 386 still owns the remaining independent all-capability semantic audit. Tasks 373–375 still require current 43-cell/44-control matrix ratification, all 660 independently authorized capability dispositions, the final source-bound corpus after executable changes stabilize, ledger audit and published Stage 4 closure. No final oracle is issued here.

Stage 4 remains open and Stages 5–6 remain blocked. No full corpus, final matrix capture, original-ROM read, device/emulator, ADB, APK, signing or release work was performed for this checkpoint.
