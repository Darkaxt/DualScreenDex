# Stage 4 Ruby item-name checkpoint

Status: **independently verified: 193/193 tests passed, zero failures/errors/skips**. This completes the Japanese Ruby ordinary item-name slice of Task 390 / Task 386 / `LNG-B002`, not Task 390 or Stage 4 as a whole. Parser revision is **55**, storage schema remains **2**.

## Root cause and original authority

The revision-54 Ruby control had valid `gba-gen3-ja-ruby-sapphire` codec authority but **0/101** referenced item names. Its original names-only route did not invoke the published-header resolver. The default published Absent result prevented the item materializer from reaching compiled proof. This establishes a producer/dataflow gap, not physical absence of every possible Ruby header.

Pinned public `pret/pokeruby` source at `63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1` supplied a structural sanitizer/getter/scalar/copy hypothesis, but no justified item-header relationship. The implementation therefore does not enable Emerald/FireRed header selection for Ruby or import source record sizes, array counts or item labels as compiled authority.

The original identity phase now freezes one immutable terminal item-name authority, owned by the analysis session:

- Published routing not invoked is distinct from routing invoked with Absent/Ambiguous. Only the former may establish compiled-only authority before the terminal result is frozen.
- Existing published nomination is retained verbatim. A competing root cannot replace it; conflicting or incomplete relevant witnesses withhold item names.
- Shared-index structural hints are collected before reference-site truncation. Item-hint overflow does not invalidate independent numeric reference counts.
- Aggregate candidate/root/site/semantic limits, bounded batch reference recovery and one finite-target-set BL scan bound original proof. Memoization avoids repeating that work; cancellation precedes cached returns and publication.
- Full wrapper identity includes its entry/getter/copier/exclusion, not just its excluded item ID. Dynamic escape branches must avoid literal pools and the ordinary getter path.
- Layout aggregation transports scalar immutable authority. The materializer only decodes against it, with no discovery, new session, later-count retry or published-root fallback. Numeric root selection is unchanged.

## Exact compiled witness and oracle

The bounded Japanese Ruby proof establishes **349 records, 40-byte stride and a 10-byte name field** through a complete u16 sanitizer, pointer-return getter and 13 scalar consumers. The recorded inventory reconciles 14 literals/14 LDR sites, no root-plus-field references and 12 getter callers. The ordinary wrapper binds a terminator-aware copier; instruction-derived dynamic exception **175** remains unavailable for static naming. Invalid distinct IDs do not inherit the game's sanitizer-to-zero fallback.

The research used one 8-MiB identity/nomination read, one 8-MiB target-set BL pass over retained bytes, **1,730 semantic bytes** and one direct copier edge within its declared limits. A research assertion initially assumed the escape branch immediately preceded the getter; the actual wrapper has an intervening literal pool. The failed assertion and user-requested pause receipts are preserved. After explicit resumption, retained-window assertions were corrected without repeating nomination/BL scans; one additional 8-MiB hash-only integrity reread and **105 targeted records / 4,200 bytes** completed the proof.

All **101 unique referenced ball/POI item IDs** have independently reconstructed names. This is the referenced domain, not all 349 table rows. Neither zero nor dynamic 175 is referenced in this control. Additional boundaries establish legitimate zero/final row **348** punctuation, unavailable dynamic 175 and invalid 349/65535. Ten of the 12 caller BL pairs are not retained as complete replay windows; nomination completeness relies on the executed bounded scan evidence. The accepted wrapper/getter/copier path is replayable. No whole-program or arbitrary dynamic-descendant proof is claimed.

The actual records do not exercise Ruby-sensitive arrows/punctuation. A dedicated synthetic exact-codec regression covers those differences and rejects controls, substitutions, invalid extended controls and missing field-local termination. Its initial assumption that byte F0 was invalid was corrected: it is a colon. That test-oracle failure is retained, not described as a production codec defect.

## Regression and fresh verification

Retained executed regressions include:

- Both wrapper defects: **2 assertion failures**, then **11/11 passing**.
- Original-phase authority tests: five initial failures, then **18/18 passing**.
- Decode-only materializer: two initial failures; final materializer inventory **9/9 passing**.
- Real SQLite revision-54 cache invalidation: a formerly accepted complete stale cache now requires reparse; revision-55 write/reopen succeeds. This is a synthetic catalog in actual SQLite, not a separate old Ruby ROM run.
- Cached-site cancellation and hint value-equality failures, followed by a **46/46** focused gate including original-branch, batch-recovery and hidden-competing-hint regressions.

A bounded independent source/diff reviewer found no concrete blocker in the supported production path. This is narrow compiled-contract acceptance, not support for arbitrary item wrapper ABIs.

The owner and coordinator independently ran the frozen **193-method** gate with `--rerun-tasks --no-parallel`: **142 parser + 48 store + three exact native controls**, zero failures/errors/skips. The coordinator run completed in **4 minutes 11 seconds**, with **45 tasks executed**, Gradle exit zero and all 15 source hashes unchanged. Actual fresh XML method inventories, executed task markers, logs, source snapshots and artifact hashes were checked. Existing Kotlin/Gradle warnings remain. Optional live-corpus methods were explicitly excluded from this scoped gate, not counted as passes or skips.

Ruby, Emerald and FireRed each execute parse → materialize → actual SQLite write/close/reopen → cache-only API startup. Ruby mandates 101 names, five independent sample digests, exact referenced-ID coverage, original compiled-only provenance and zero/last/dynamic/invalid boundaries. Emerald retains 104 names and FireRed 127, including FireRed's referenced zero. Every API ball/POI item entry equals its reopened native name, with `ROM_DEFAULT` and **zero parser invocations**.

The coordinator separately reopened both owner and fresh databases read-only with bounded section/chunk/decompression checks, payload digests, SQLite integrity and foreign-key checks. All 101 Ruby names exactly match the independent oracle; all **17 non-overlay sections per control**, every unrelated overlay field and localized capability equal revision-54 baselines. Emerald and FireRed overlays are entirely unchanged. `POI_TEXT` coverage is unchanged, and shared numeric records remain language-neutral.

Private inputs, raw windows, exact paths, oracle records, source snapshots, command/log/XML receipts and SQLite files remain outside public assets. No ROM payload is published.

## Remaining Stage 4 work

This supersedes the previous Ruby 0/101 producer gap for the exact accepted control only. Task 390 remains open for Japanese Gen I/II packed ordinary/generated TM/HM names, Korean skipped-ID arithmetic and broader Western item support/acceptance. Task 386 still owns remaining Gen II Local labels, GBA region-label authority/applicability and reference-aware POI coverage.

Tasks 373–375 still require all 15 capability dispositions across the current **43-cell/44-control** official matrix, one final current-corpus run after executable changes stabilize, ledger audit and published `stage-04-closure.md`. No required cell is waived. Stage 4 remains open; Stages 5–6 have not begun. No full corpus, devices/emulators/ADB, signing, release APK or cleanup is part of this checkpoint.
