# Stage 4 FireRed item-name acceptance checkpoint

Status: **independently verified: two exact controls passed, zero failures/errors/skips**. This completes the Japanese FireRed ordinary item-name acceptance slice of Task 390 / Task 386 / `LNG-B002`, not Task 390 or Stage 4 as a whole.

## Scope and compiled authority

The generic producer published in `91470a13488ff6e7c0e494ca6c13dfe282fe3c20` already produced FireRed item names. This checkpoint adds mandatory independently sampled acceptance, not a new production fix or an artificial red-test claim. Only the real-control test and checkpoint documentation change. Parser revision remains **54**, storage schema **2**.

The exact Japanese FireRed witness independently establishes a complete u16 sanitizer, name-pointer getter, scalar-field boundary and ordinary copy-wrapper/copier relationship. Its geometry is **375 records, 40-byte stride, 10-byte name field**, distinct from Emerald's 377 records. The original selected published block nominates the root; it does not establish text semantics. All 15 root literals and 14 LDR getters are reconciled before decoding, with no root-plus-field literals. A bounded target-specific inventory finds 23 getter callers and one complete ordinary copy contract. The compiled dynamic exception is input **175**; dynamic descendants are not traversed or claimed as static authority.

The proof uses one exact 16-MiB file read for identity/root nomination, an additional 16-MiB BL nomination pass over retained bytes, 14,792 backward-reference bytes and **1,580 semantic bytes including header windows** within a 4-KiB semantic limit. It independently decodes the 127 referenced records and inspects three additional boundary records. A pinned public charmap is an independent text oracle, not a source-label, filename, identity or readability-based production selector. The coordinator verified the exact input hash, all retained header/code/record hashes and all publishable record text digests against the raw input and pinned charmap.

## Mandatory acceptance

`nativeOfficialJapaneseFireRedLeafGreen` now requires item-name acceptance alongside Emerald. The shared test helper retains Emerald's 104-record count, original five digests and boundaries, and adds:

- **127/127 FireRed referenced item names**, including legitimately referenced item zero.
- Six independent FireRed sample digests at materialization, actual SQLite write/close/read/close and API boundaries.
- Exact referenced-ID coverage, language-neutral shared records, and ball/POI projection parity.
- Original root nomination assertions, real zero and final row **374**, and unavailable dynamic **175** and invalid IDs **375/65535**. Invalid IDs do not acquire the game's row-zero fallback name.
- Cache-only API startup with `ROM_DEFAULT` and **zero reparses**.

The owner ran the two exact controls with `--rerun-tasks --no-parallel`: **2/2 passed**, zero failures/errors/skips, **7 minutes 27 seconds**, 40 tasks executed. The coordinator reviewed the single test-file diff, verified its frozen source/patch hashes and actual XML method inventory, then independently ran the same two-control gate: **2/2 passed**, zero failures/errors/skips, **7 minutes 9 seconds**, 40 tasks executed. The tested source hash remained unchanged.

The coordinator wrapper exited with code 1 only while printing the completed build log: the Windows stdout encoding could not represent Vite's checkmark. Gradle had returned zero, all test/cache/source assertions had passed, and the coordinator receipt was already persisted. A separate successful reconciliation reverified the full log, retained XML hash/exact two-method inventory, baseline and current source hash. The output-only failure is retained in an execution clarification; it is not hidden or represented as a failed test, and no redundant Gradle rerun was performed. Existing compiler/Gradle deprecation warnings remain.

## Independent persistence comparison

The coordinator reopened both owner and fresh-run FireRed databases read-only and compared all **127 names** with independently decoded records. In both, all **17 non-overlay sections**, every other overlay domain and every other localized capability state exactly match the revision-53 baseline. `POI_TEXT` coverage is unchanged. Integrity checks pass with zero foreign-key violations.

Both owner and fresh-run Emerald reopened catalogs are entirely equal to the previously accepted revision-54 Emerald catalog. This tests preservation of the complete catalog, not only the five samples. No production/schema change is needed for FireRed acceptance.

Private inputs, raw windows, oracle records, exact paths, source snapshots, command/log/XML receipts and SQLite files remain outside public assets. No ROM payload is published.

## Remaining Stage 4 work

This supersedes the Emerald checkpoint's historical observation that FireRed 127/127 was not yet independently ratified. That earlier statement remains accurate for its publication time.

Task 390 / `LNG-B002` remains open for Ruby item authority, Japanese Gen I/II packed and generated TM/HM names, Korean skipped-ID arithmetic and broader Western item coverage. Task 386 still owns remaining Local/region-label authority/applicability and reference-aware POI coverage. Tasks 373–375 require all 15 capability dispositions across the current 43-cell/44-control matrix, the final current-corpus run after executable changes stabilize, ledger audit and published `stage-04-closure.md`. No required cell is waived. Stage 4 remains open and Stage 5 blocked. No full corpus, devices/emulators/ADB, signing, release APK or cleanup is part of this checkpoint.
