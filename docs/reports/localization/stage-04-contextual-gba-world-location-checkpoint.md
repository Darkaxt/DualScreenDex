# Stage 4 contextual GBA world-location checkpoint

Status: **verified bounded correction; not Stage 4 closure**.

## Corrected semantic boundary

Ruby/Sapphire and Emerald compile a dynamic region-map section as a placeholder. The region-map setup path first stores the current map header's section, compares that value with the dynamic section ID, and on the matching branch resolves another map header and overwrites the stored section from that header's byte at offset `0x14`. The same bounded setup envelope references the selected region-entry table. This is contextual control flow, not a static world location.

The parser now classifies a section as contextual only when one unique compiled envelope proves all of those relationships. The recognizer derives the section ID from the compare immediate, binds the replacement to the same state holder and field, requires the selected region-entry root in the bounded continuation, and knows no ROM identity, hash, language, title or fixed address. Missing, truncated, competing or wrong-root evidence publishes no contextual classification.

A contextual section remains in `sectionByBaseArea` and `entriesBySection`, preserving the numeric map-header join and decoded source record. It is excluded only from static `namesByBaseArea`, section-name projection and affine `WorldMapLocation` publication. The Japanese table text `とくしゅ` is therefore retained as decoded source evidence but is no longer exposed as a static location name or a synthetic `(0,0)` map target when compiled control flow proves the contextual replacement.

Parser schema **74** invalidates parser-73 caches that may persist the old static World-location projection. SQL schema remains **2** and codec version remains **1**.

## Independent semantic evidence

The Japanese Emerald control independently established the compiled data flow before implementation: its used `とくしゅ` payload belongs to the selected region-entry table; region-map initialization compares section `0x57`; the taken branch resolves the dynamic-warp map header; and that header's section byte replaces the stored section before table indexing and region positioning. Japanese Ruby uses the same semantic contract with a different state-field placement and instruction ordering. Neither control is admitted by identity: both pass the generic structural recognizer at their actual compiled locations.

The exact-control acceptance assertion requires both Japanese Ruby/Sapphire and Japanese Emerald to omit `section-87` and `とくしゅ` from static world-location publication while retaining an independently expected ordinary location name. Each control then completes the existing parse, selected overlay, SQLite close/reopen, whole-catalog equality, cache-only API, projection parity and zero-reparse gates.

## Verification

The regressions were established RED before `contextualSections` existed. Focused final gates passed:

- `Gen3MapLocationResolverTest`: 15/15 passed, covering relocation, both compiled state-field forms, wrong-root and truncated evidence, competing evidence, and cancellation;
- `Gen3WorldMapResolverTest`: 5/5 passed, including static publication exclusion with the numeric section/base-area inventory retained;
- `CatalogStoreTest`: 2/2 passed for parser-73 rejection/current rewrite and normalized map round-trip;
- `OfficialMatrixApiCaptureTest`: 23/23 passed;
- exact Japanese Ruby/Sapphire and Emerald production round trips: 2/2 passed, including SQLite and cache-only API boundaries.

Total: **47 passed, zero skips, failures or errors** across the selected final methods. The final parser and app selections were freshly executed with `--rerun-tasks`; the persistence selection was also freshly executed. Existing compiler and Gradle deprecation warnings remain.

## Remaining work

Task 437's bounded implementation and exact Japanese Ruby/Emerald verification are complete. Task 417 remains open for final all-control World-location disposition binding. Task 386 still owns the remaining independent all-capability semantic audit, including POI obligations and explicit exclusions. Tasks 373–375 still require all 660 final dispositions, the schema-current 43-cell/44-control matrix, one final source-bound corpus after executable changes stabilize, ledger audit and Stage 4 publication.

No final oracle or matrix is issued here. Stage 4 remains open and Stages 5–6 remain blocked. No full corpus, device/emulator, ADB, APK, signing or release work was performed.
