# DualDex Post-Hardening Project-Wide QA Detection Specification

**Status:** Audit complete; remediation not started

**Audited source:** `a00194fd36f2ce9cafecb6c46a8ac5f4fc837196`

**Worktree:** `D:\Temp\dualdex-qa-hardening`

**Scope:** The complete `2026-08-27-project-wide-qa-hardening-design.md` contract: Android setup and guide loading, runtime/session authority, parser/catalog/save persistence, companion web and servers, simulator/mapper behavior, CI, release evidence, privacy, and governance.

## 1. Purpose and stopping point

This document records the detections from the post-liveness-fix project-wide QA review. It is a remediation specification, not a closure claim. No detection in this document has been fixed as part of this review.

Per the selected delivery order:

1. Fix every blocker and close every tracked referral.
2. Stabilize and smart-sync the resulting source.
3. Run one completely fresh corpus from that final source across the 333 scanner-eligible inputs in the 334-file physical inventory.
4. Complete Stage 7 and Stage 8 closure with zero blockers and zero referrals.
5. Publish one official stable `1.1` or `1.2` release through protected GitHub signing.

The hours-long corpus was deliberately not run during this review. The 136 pre-fix receipts remain diagnostic-only and may not contribute to final evidence.

## 2. Review method and confidence

The review used six independent read-only discovery passes followed by five adversarial verification passes. Every delegated reviewer was prohibited from modifying files or spawning subagents. Findings were challenged against complete production call paths and existing tests; one proposed finding was refuted and several were narrowed or merged.

No emulator, ADB, browser E2E, external-service mutation, secret inspection, signing-material inspection, or full corpus run occurred. Focused owning regressions were used where useful. Sensitive local identifier values found by the privacy audit are intentionally not reproduced here.

**Surviving records:** 32 `BLOCKER`, 7 `TRACKED_REFERRAL`.

`S7-BLK-01` is expanded below rather than counted twice. The absence of `stage-07-closure.md` is not a separate detection: Stage 7 is still legitimately open.

## 3. Classification contract

- `BLOCKER`: Must be resolved and verified before the final corpus begins. Referring it away would defer a current invariant, required acceptance path, credible crash/data-loss/stale-authority path, or release-integrity failure.
- `TRACKED_REFERRAL`: Bounded or non-mainstream hardening work with an explicit target, dependency, and measurable acceptance. It still must reach a terminal resolution before Stage 8 and the stable release; it may not disappear from the ledger.

Every remediation must preserve the global rule that a failed optional module disables only that module and does not crash the APK, publish stale authority, invent empty authoritative data, or discard independently valid state.

## 4. Detection index

| ID | Class | Requirements | Target |
| --- | --- | --- | --- |
| `S7-BLK-01` | BLOCKER | INV-04, INV-06, CAT-08, CAT-14, REL-05 | Stage 7 evidence |
| `QA-BLK-EVID-02` | BLOCKER | INV-06, REL-05 | Stage 7 evidence |
| `QA-BLK-EVID-03` | BLOCKER | INV-06, REL-05 | Stage 7 evidence |
| `QA-BLK-SCHEMA-01` | BLOCKER | CAT-02, INV-06 | Stage 7 governance |
| `QA-BLK-REL-01` | BLOCKER | REL-02, INV-06 | Stage 3/8 release |
| `QA-BLK-REL-02` | BLOCKER | REL-02, REL-05, INV-06 | Stage 3/7 release |
| `QA-BLK-REL-03` | BLOCKER | REL-02, REL-09, INV-06 | Stage 7 governance |
| `QA-BLK-PRIV-01` | BLOCKER | REL-06 | Stage 7 privacy |
| `QA-BLK-PARSER-01` | BLOCKER | CAT-05, CAT-08, INV-01, INV-04 | Stage 7 parser |
| `QA-BLK-CACHE-01` | BLOCKER | CAT-10, CAT-12, INV-01, INV-04 | Stage 4 reopen |
| `QA-BLK-SNAPSHOT-01` | BLOCKER | CAT-01, INV-03 | Stage 2 reopen |
| `QA-BLK-GEN1-01` | BLOCKER | CAT-02, CAT-08, INV-06 | Stage 4/7 parser |
| `QA-BLK-STATE-01` | BLOCKER | AND-01, INV-05 | Stage 1 reopen |
| `QA-BLK-IDENTITY-01` | BLOCKER | RUN-01, INV-02 | Stage 2 reopen |
| `QA-BLK-EPOCH-01` | BLOCKER | RUN-02, INV-02 | Stage 2 reopen |
| `QA-BLK-KNOWLEDGE-01` | BLOCKER | RUN-02, INV-02, INV-03 | Stage 2 reopen |
| `QA-BLK-CONFIG-01` | BLOCKER | RUN-04, INV-01, INV-04 | Stage 5 reopen |
| `QA-BLK-MEMORY-01` | BLOCKER | RUN-05, RUN-08, INV-02, INV-04 | Stage 5 reopen |
| `QA-BLK-STORAGE-01` | BLOCKER | AND-05, INV-01, INV-04 | Stage 4/7 reopen |
| `QA-BLK-DIAG-01` | BLOCKER | INV-01, INV-05, REL-08 | Stage 7 diagnostics |
| `QA-BLK-PKG-01` | BLOCKER | AND-02, REL-03, INV-01, INV-05 | Stage 3 reopen |
| `QA-BLK-PICKER-01` | BLOCKER | AND-04 | Stage 7 UX |
| `QA-BLK-ARCH-01` | BLOCKER | REL-07 | Stage 7 architecture |
| `QA-BLK-DESKTOP-LOAD-01` | BLOCKER | AND-02, INV-01, INV-05 | Stage 1/8 desktop |
| `QA-BLK-WEB-STATE-01` | BLOCKER | WEB-02, WEB-04, INV-01 | Stage 6 reopen |
| `QA-BLK-MAP-URL-01` | BLOCKER | WEB-03 | Stage 6 reopen |
| `QA-BLK-ROUTE-01` | BLOCKER | WEB-02 | Stage 6 reopen |
| `QA-BLK-GUIDE-BUDGET-01` | BLOCKER | AND-08, INV-04 | Stage 4 reopen |
| `QA-BLK-HTTP-ANDROID-01` | BLOCKER | WEB-01, INV-04 | Stage 6 reopen |
| `QA-BLK-MAPPER-WEB-01` | BLOCKER | WEB-04, WEB-09, INV-05 | Stage 6 reopen |
| `QA-BLK-HTTP-DESKTOP-01` | BLOCKER | INV-04, WEB-09 | Stage 6 reopen |
| `QA-BLK-WEB-CACHE-01` | BLOCKER | WEB-04, WEB-09, INV-05 | Stage 6 reopen |
| `QA-REF-CACHE-HOL-01` | TRACKED_REFERRAL | CAT-03, CAT-04, CAT-05, CAT-14 | Stage 8 persistence |
| `QA-REF-CLI-RETENTION-01` | TRACKED_REFERRAL | CAT-14, INV-04 | Stage 8 corpus tooling |
| `QA-REF-7Z-01` | TRACKED_REFERRAL | CAT-09, CAT-14, INV-04 | Stage 8 archive tooling |
| `QA-REF-SNAPSHOT-RACE-01` | TRACKED_REFERRAL | CAT-04, CAT-12, INV-03 | Stage 8 persistence |
| `QA-REF-MAP-ERROR-01` | TRACKED_REFERRAL | INV-01, INV-05 | Stage 8 API parity |
| `QA-REF-SETTINGS-01` | TRACKED_REFERRAL | AND-06, INV-01 | Stage 8 setup UX |
| `QA-REF-LEDGER-01` | TRACKED_REFERRAL | Stage 1 referral governance | Stage 8 closure |

## 5. Release evidence, governance, and privacy blockers

### S7-BLK-01 — Fresh evidence and completeness validation

**Failure:** Final evidence is absent, as expected before the deferred corpus, but the validator would also accept a self-attested subset. It ignores `catalogError`, compatibility errors, missing terminal outcomes, and the canonical eligible-input denominator/digest. Readiness metadata can become signable after minimally shaped evidence appears without machine-checking Stage 7/8 closure.

**Evidence:**

- `docs/reports/qa-hardening/stage-07-parser-evidence-blocker.md:44-48`
- `tools/release/summarize-compatibility-evidence.mjs:18-47`
- `tools/release/validate-release-evidence.mjs:29-31,70-76`
- `tools/release/derive-release-metadata.mjs:76-82`
- `.github/workflows/release.yml:169-174`

**Correction boundary and dependency:** Canonical source-bound corpus inventory, corrected validator, all source remediation complete.

**Acceptance (corrected during evidence closure):** The physical inventory contains 334 supported-extension files, of which 333 are eligible under the already-tested mainline/hack scanner policy; one known spin-off is intentionally excluded. Reject any eligible count other than exactly 333, terminal totals not equal to 333, a unique-identity count inconsistent with the canonical multiset, any source/parser/catalog/compatibility/persistence error, extra or missing eligible input, digest drift, pre-fix receipt, or missing Stage 7/8 zero-gap closure. Generate evidence from one exact stabilized commit with every materialized catalog persisted and reopened. The canonical digest includes duplicate identities as separate inputs rather than falsely requiring every named input to have unique bytes.

### QA-BLK-EVID-02 — Raw corpus output has no trustworthy source lineage

**Failure:** Raw schema-12 output contains no source/build identity. The summarizer accepts a caller-provided commit and copies it into the summary, allowing stale output to be relabeled as current.

**Evidence:**

- `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriter.kt:24-30`
- `tools/release/summarize-compatibility-evidence.mjs:6-29,96-100`
- `tools/release/validate-release-evidence.mjs:65-69,117-125`

**Correction boundary and dependency:** Parser CLI execution receipt and trusted source/build identity.

**Acceptance:** Parser execution itself emits verifiable source/build identity plus raw-report/generator digest. The summarizer copies rather than invents identity. Relabeling an older report must fail even when schema and corpus digest otherwise match.

### QA-BLK-EVID-03 — NONPARSER_REUSE omits generator-affecting changes

**Failure:** Reuse classification excludes `parser-cli/build.gradle.kts` and evidence-producing corpus tooling. Generator dependency, build, or input-selection changes can therefore pass as nonparser reuse.

**Evidence:** `tools/release/validate-release-evidence.mjs:9-11,40-46` and `parser-cli/build.gradle.kts:10-15`.

**Correction boundary and dependency:** Repository change-scope policy.

**Acceptance (corrected during evidence closure):** Classify all `parser-cli/**`, parser/catalog modules, build logic/dependencies, Gradle wrapper/properties, and corpus input-selection/execution tooling as evidence-affecting. Downstream summarization and validation tools may process an immutable source-bound raw report under explicit `NONPARSER_REUSE`; they do not alter parser output, and requiring another hours-long parse after correcting a downstream denominator or bounded reader provides no additional scientific evidence. Mutation tests must reject reuse for every actual generator category and require explicit reuse scope for downstream policy changes.

### QA-BLK-SCHEMA-01 — Cache policy pins a number instead of requiring a decision

**Failure:** Release tests assert literal parser schema revision 45. A future output-changing parser edit can retain 45 and pass, leaving upgrading devices on stale output.

**Evidence:**

- `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt:3-6`
- `tools/release/release-workflow.test.mjs:225-227`
- `2026-08-27-project-wide-qa-hardening-design.md:157-165,511-524`

**Correction boundary and dependency:** Machine-readable per-change cache decision.

**Acceptance:** Every parser/catalog-affecting release declares exactly one of: `BUMP_REQUIRED`, with revision advance and seeded prior-version rejection/rebuild regression; or `OUTPUT_INVARIANT`, with bounded rationale and behavior test proving persisted output remains valid. Neither/mismatched decisions fail policy.

### QA-BLK-REL-01 — Stable release is not bound to validated candidate source

**Failure:** Stable authorization validates candidate tag/hash shape but not candidate source provenance or hash against the candidate release. The stable workflow can build from changed product source and publish a different APK using unrelated device authorization.

**Evidence:**

- `tools/release/derive-release-metadata.mjs:98-132`
- `.github/workflows/release.yml:32-54,335-367`
- `tools/release/release-metadata.test.mjs:142-158`

**Correction boundary and dependency:** Candidate provenance and stable transformation policy.

**Acceptance:** Bind authorization to the verified candidate source commit and provenance. The stable tag must have the same product tree except explicitly enumerated release-metadata changes, or it requires new candidate/device validation. Different product source must fail.

### QA-BLK-REL-02 — Promotion protects only the APK asset

**Failure:** Candidate promotion records and rechecks only the APK asset identity. Provenance, checksums, compatibility evidence, and other draft assets can be replaced before promotion.

**Evidence:**

- `.github/workflows/promote-candidate.yml:64-95,214-240`
- `tools/release/validate-candidate-promotion.mjs:64-70,163-172`

**Correction boundary and dependency:** Full immutable asset-set contract.

**Acceptance:** Record and recheck exact names, asset IDs, and SHA-256 digests for APK, provenance, checksum manifest, compatibility manifest, policy evidence, and every public evidence asset. Replacing, deleting, adding, or reuploading any asset must fail promotion.

### QA-BLK-REL-03 — Required environment authorization is not enforced

**Failure:** Repository policy accepts a signing environment with zero reviewers, and the promotion environment is referenced but not audited for reviewers, branch policy, or absence of signing secrets.

**Evidence:**

- `tools/release/verify-repository-policy.mjs:20-23,35-40`
- `.github/workflows/release.yml:79-107`
- `.github/workflows/promote-candidate.yml:19-25`

**Correction boundary and dependency:** Auditable GitHub environment-policy data.

**Acceptance:** Require at least one eligible reviewer and exact expected protection rules for both environments, self-review prevention where supported, default-branch/tag policy, and zero promotion signing secrets. Independent missing/wrong-policy fixtures must fail.

### QA-BLK-PRIV-01 — Privacy coverage is incomplete

**Failure:** Tracked IDE/tooling/documentation contains local device/workspace identifiers, release privacy scanning misses forward-slash absolute paths and Stage 7 assets, and persisted performance failures retain exact implementation exception class names rather than the required coarse category.

**Evidence:**

- `.idea/deploymentTargetSelector.xml:10` (value intentionally omitted)
- `.github/workflows/release.yml:233-280,564-565`
- `app/src/main/java/com/darkaxt/dualdex/performance/PerformanceRecorder.kt:73-83`
- `app/src/main/java/com/darkaxt/dualdex/performance/PrivacySafeDiagnostics.kt:23-29`

**Correction boundary and dependency:** Repository-wide and artifact-structural privacy policy.

**Acceptance:** Untrack local deployment state, sanitize retained local paths/identifiers, scan every published asset with cross-platform path/identifier patterns, reject unknown/private evidence fields, and serialize only coarse failure categories. Tests must prove path-bearing custom exceptions and local identifiers do not enter tracked artifacts, Logcat, or exports.

## 6. Parser, cache, snapshot, and corpus detections

### QA-BLK-PARSER-01 — Expensive fallback scans remain uncapped and uncancellable

**Failure:** Dense Gen II opcode data makes `RomImage.findAll` allocate millions of boxed offsets; move-description fallback scans and materializes candidate sets/lists without cancellation. Superseded ROM A can still occupy the single parser worker and block B.

**Evidence:**

- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt:66-84`
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2CompiledSpriteResolver.kt:16-27`
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializer.kt:77-131,164-210`
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt:73-76`
- `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt:159-183`

**Correction boundary and dependency:** Shared resolution budgets and end-to-end parser cancellation handoff.

**Acceptance:** Streaming visitors cap roots/matches/candidates/work and check cancellation at a defined operation interval. Max-size dense fixtures stay within measured heap, fail only the optional capability, and cancel soon enough for B to start. A regression must retain the production runtime adapter so replacing its token with `NONE` fails.

### QA-BLK-CACHE-01 — Persisted size checks occur after allocation

**Failure:** JDBC/Android retrieves complete digest/chunk BLOBs before Kotlin validates size. Snapshot JSON has no application byte limit before `getString` and Gson object-graph allocation. Corrupt databases can OOM outside `Exception` boundaries.

**Evidence:**

- `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt:83-107,128-134,450-463`
- `app/src/main/java/com/darkaxt/dualdex/catalog/AndroidCatalogDatabase.kt:55-59`
- `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/SaveSnapshotStore.kt:68-121`

**Correction boundary and dependency:** JDBC and Android database adapters with prefetch length checks/bounded reads.

**Acceptance:** Query and validate row/aggregate lengths before value retrieval; bound snapshot bytes and semantic collections. Oversized rows reject before blob/string access, remain within measured heap, quarantine only corrupt data, and permit valid live recovery.

### QA-BLK-SNAPSHOT-01 — Interrupted migration can hide the only valid legacy snapshot

**Failure:** Migration creates the destination database before inserting its row. Process death can leave a schema-only file; next launch skips the valid legacy row merely because the destination exists.

**Evidence:**

- `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/SaveSnapshotStore.kt:60-65,133-195`
- `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/SaveSnapshotSchema.kt:21-31`

**Correction boundary and dependency:** Atomic migration or validated incomplete-destination recovery.

**Acceptance:** Empty, corrupt, incompatible, and schema-only destination fixtures must not suppress a valid legacy row. Prefer temporary database plus atomic rename. Catalog cleanup must still leave the recovered snapshot readable.

### QA-BLK-GEN1-01 — Official Gen I description applicability denominator is wrong

**Failure:** Red, Blue, and Yellow decode 151 descriptions but report `PARTIAL 151/190`. Thirty-nine zero-Dex internal slots remain applicable because their Dex field is `AVAILABLE(0)`.

**Evidence:**

- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/profile/KnownProfiles.kt:218-244`
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt:142-159`
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt:320-365`
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/OfficialGen12CompletionLiveRomTest.kt:19-37,63-80,117-121`

**Correction boundary and dependency:** Correct Gen I Pokédex applicability semantics.

**Acceptance:** Deterministic non-live coverage plus official controls report `AVAILABLE 151/151`; zero-Dex internal slots are not applicable. Any output change advances the cache schema and is included in the final fresh corpus.

## 7. Android setup, runtime authority, and diagnostics blockers

### QA-BLK-STATE-01 — Recovery-only SaveRAM changes retain the old delivery revision

**Failure:** `resolvedRecoveryLedger()` directly replaces `saveRam` and invalidates the cache without advancing delivery version. A status-only transition can leave `/api/state?sinceVersion=N` returning 204 indefinitely.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt:340-344,808-820`
- `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt:319-329`

**Correction boundary and dependency:** One revision-advancing setter for every `SaveRamView` mutation.

**Acceptance:** With all game projections and save identity unchanged, change only recovery SaveRAM status/message and prove HTTP 200 with version greater than N and browser update. Repeating identical status returns 204.

### QA-BLK-IDENTITY-01 — Prior-epoch SHA authorizes reconnect without fresh verification

**Failure:** Session loss advances the epoch but retains `lastActivatedSha`. A same-CRC stale index can resolve active and return before reopening and SHA-hashing the source.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt:535-550,610-633`
- `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/RomSessionResolver.kt:35-65`

**Correction boundary and dependency:** Verification ownership bound to `SessionWorkToken`/epoch, not global SHA.

**Acceptance:** Verify A, lose session, reconnect through the same stale SHA/CRC while source bytes differ. Source verification must run again; resolution never becomes active and no battle/SaveRAM work starts.

### QA-BLK-EPOCH-01 — Session checks and commits are not atomic

**Failure:** Guide success checks the token once, then can publish activation fields after B/close. Save snapshot writes and checkpoint/journal/recovery work similarly commit after standalone checks.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt:668-685,739-755`
- `app/src/main/java/com/darkaxt/dualdex/setup/SessionEpochGate.kt:39-43`
- `app/src/main/java/com/darkaxt/dualdex/save/SavePollingMonitor.kt:160-165,212-218`
- `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinator.kt:17-58`

**Correction boundary and dependency:** Token-aware atomic commit across runtime publication, setup state, snapshots, checkpoints, and journal mutation.

**Acceptance:** Deterministically block A immediately before each publication/write and after its first current check; switch to B or close; release A. Assert zero A catalog/preference/activation/snapshot/checkpoint/journal/recovery/state mutation. The test must construct the production coordinator, not only isolated gates.

### QA-BLK-KNOWLEDGE-01 — Checkpoint failures are treated as absence or success

**Failure:** Checkpoint read failures become `null`, potentially resetting knowledge; write failures are swallowed while recovery reports acceptance. A valid sidecar can later be replaced by an empty ledger.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointStore.kt:26-45`
- `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinator.kt:17-58`
- `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt:320-339`

**Correction boundary and dependency:** Typed `Present/Absent/Corrupt/Unavailable` outcomes and durable write result.

**Acceptance:** Inject sidecar/fallback read and write failures. Retain prior knowledge, publish retryable stale/unavailable state, do not overwrite a valid sidecar, and recover after storage becomes valid.

### QA-BLK-CONFIG-01 — Config/recovery reads are unbounded and OOM leaves PATCHING active

**Failure:** Direct and SAF config/recovery documents use `readBytes()` without byte limits. OOM escapes `catch (Exception)` and the worker has no terminal outer boundary.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/storage/DocumentTreeAccess.kt:82-83`
- `app/src/main/java/com/darkaxt/dualdex/setup/FileRetroArchConfigStore.kt:19-36`
- `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/RetroArchConfigInstaller.kt:112-121`
- `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt:132-165`

**Correction boundary and dependency:** Shared bounded config/sidecar reader and module-local OOM boundary.

**Acceptance:** Oversized metadata, false/absent metadata, endless streams, and allocation failure terminate safely as sanitized `FAILED`, retain original/recovery bytes, keep the process alive, and leave retry reachable.

### QA-BLK-MEMORY-01 — Missing memory replies retry forever while old data remains LIVE

**Failure:** Healthy status/config traffic with absent or irrelevant-only memory replies leaves `CoreMemoryReadSession` in `Reading` forever. Each heartbeat resends and prior trainer/location/battle fields remain LIVE.

**Evidence:**

- `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReader.kt:44-48,99-147`
- `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt:174-229`

**Correction boundary and dependency:** Per-read monotonic deadline or missed-reply budget plus bounded recovery backoff.

**Acceptance:** Publish one live sample, then drop only memory replies while status stays fresh. Within the bound, prior fields cease being LIVE, mapper/battle loops stop or back off, safe unavailability is published, and later valid replies recover.

### QA-BLK-STORAGE-01 — Library traversal and initial SAF index persistence are nonterminal/unbounded

**Failure:** Direct/SAF ROM and SaveRAM discovery has no node/directory/file/result quotas and eagerly retains lists. Initial SAF index persistence failure escapes the success callback and leaves `romGrant=INDEXING`.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/storage/DirectRomLibraryIndexer.kt:53-72`
- `app/src/main/java/com/darkaxt/dualdex/storage/DocumentTreeAccess.kt:48-79`
- `app/src/main/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolver.kt:17-30`
- `app/src/main/java/com/darkaxt/dualdex/save/AndroidSaveDocumentResolver.kt:18-49`
- `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt:175-197`

**Correction boundary and dependency:** One bounded streaming traversal policy and one terminal index transaction.

**Acceptance:** Over-limit fake trees stop at measured quotas without OOM, retain prior valid index/snapshot, and publish sanitized terminal failure. Inject initial SAF `indexStore.write` failure and prove retry is available rather than permanent INDEXING.

### QA-BLK-DIAG-01 — Optional diagnostics can crash startup and report false durability

**Failure:** Diagnostic log construction precedes the guarded startup block. Directory failure can crash `Application.onCreate`. Append errors are swallowed but the previous-exit marker advances; export read failure becomes empty bytes and UI reports success.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt:139-165,301-355`
- `app/src/main/java/com/darkaxt/dualdex/performance/AndroidPerformanceLog.kt:16-50`
- `app/src/main/java/com/darkaxt/dualdex/performance/PreviousProcessExit.kt:58-64`
- `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt:191-201`

**Correction boundary and dependency:** Explicit append/export success contracts and optional-module startup containment.

**Acceptance:** Invalid/unwritable directory and append/export failures must not prevent app/loopback startup. Marker advances only after durable append; failed events retry next launch; UI reports export failure rather than successful empty output.

### QA-BLK-PKG-01 — Packaged retry test bypasses the production action

**Failure:** Packaged acceptance verifies route interception, then clears failure through a test-only method. Making MainActivity’s production retry branch a no-op still leaves the test green.

**Evidence:**

- `app/src/androidTest/java/com/darkaxt/dualdex/QaAndroidJUnitRunner.kt:27-39`
- `app/src/androidTest/java/com/darkaxt/dualdex/PackagedAcceptanceInstrumentedTest.kt:104-135`
- `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt:349-361`

**Correction boundary and dependency:** Deterministic production-owned failed activation source.

**Acceptance:** Without `clearGuideFailure()`, click retry and observe exactly one production invocation and `FAILED → LOADING → terminal`. Mutating MainActivity retry to a no-op must fail packaged acceptance.

### QA-BLK-PICKER-01 — Overlay picker wiring lacks end-to-end acceptance

**Failure:** Tests cover only helper parsing/consumption, not service extra creation, activity delivery, or one-shot launcher dispatch. Dropped/swapped extras or duplicate dispatch remain green.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/overlay/FloatingCompanionService.kt:273-313`
- `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt:316-332`
- `app/src/test/java/com/darkaxt/dualdex/setup/SetupPickerRequestTest.kt:7-25`

**Correction boundary and dependency:** Injectable activity/picker dispatch seam or Android integration fixture.

**Acceptance:** Both overlay routes foreground the activity and open the correct `OpenDocumentTree` exactly once through cold creation and `onNewIntent`; removing/swapping the extra or dispatching twice fails.

### QA-BLK-ARCH-01 — Direct-dependency architecture coverage is incomplete

**Failure:** The architecture test maps only parser-core and save-core while the app declares seven project dependencies. Accidental transitive imports from other modules can escape.

**Evidence:**

- `app/src/test/java/com/darkaxt/dualdex/architecture/DirectProjectDependencyTest.kt:8-30`
- `app/build.gradle.kts:86-93`

**Correction boundary and dependency:** Complete project package ownership map.

**Acceptance:** Derive ownership from every included module source root and require each app import’s owner as a direct dependency. Mutation-removing each current project dependency must fail.

## 8. Companion web, desktop, and HTTP blockers

### QA-BLK-DESKTOP-LOAD-01 — Desktop checkpoints become partial live authority

**Failure:** Desktop parser progress assigns incomplete catalogs and simulator state as active. OOM after ESSENTIAL is not caught, leaving partial authority and nonterminal loading; manual source OOM can also escape.

**Evidence:**

- `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexRuntime.kt:61-118,317-358`
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt:292-318`

**Correction boundary and dependency:** Desktop checkpoint/commit separation and common sanitized load-outcome boundary.

**Acceptance:** Inject ordinary failure and OOM before/after progress. Loading ends, no partial catalog/simulator is active, normal UI contains no raw detail, retry remains reachable, and a later valid load succeeds.

### QA-BLK-WEB-STATE-01 — Catalog refresh lacks immutable identity and request fencing

**Failure:** Refresh marker uses filename/progress only. Same-filename catalogs can share a final marker, and concurrent bootstraps allow an older response to overwrite newer catalog authority.

**Evidence:** `companion-web/src/App.tsx:57,142-185,419-422`.

**Correction boundary and dependency:** Catalog SHA/load generation in state and latest-request commit fence.

**Acceptance:** Same-filename A→B with only final event delivered must refresh B. Deliberately reverse concurrent bootstrap completion order; only the newest identity may commit state/catalog/routes.

### QA-BLK-MAP-URL-01 — Dynamic map URLs append a second question mark

**Failure:** Catalog-versioned URLs already contain `?catalog=...`; dynamic lighting adds another `?`, so lighting/hour parameters are lost or malformed.

**Evidence:**

- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt:1488-1495`
- `companion-web/src/pages/MapPage.tsx:425-429,799-801`

**Correction boundary and dependency:** Shared query composition helper.

**Acceptance:** Production catalog-versioned phase and timed URLs parse into distinct `catalog`, `lighting`, `hour`, and `minute` parameters and render distinct variants.

### QA-BLK-ROUTE-01 — Production specimen fallback keys exceed route limits

**Failure:** Real Gen I/II fallback keys are roughly 202–205 characters, but the route decoder rejects keys over 128. Live navigation works; refresh/transfer drops the route stack.

**Evidence:**

- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt:1234-1250`
- `save-core/src/main/kotlin/com/darkaxt/dualdex/save/SaveModels.kt:62-87`
- `companion-web/src/navigation.ts:20,79-90`

**Correction boundary and dependency:** Shared bounded specimen identity contract.

**Acceptance:** Use a fixed-size opaque key or compatible bound. Round-trip actual Gen I/II fallback keys through encode/decode, refresh, popstate, and Android display transfer at maximum stack depth.

### QA-BLK-GUIDE-BUDGET-01 — Area Guide output limit is post-allocation

**Failure:** The 65,536 retained-item limit is checked after complete construction. Inputs at existing individual caps can construct more than the limit and perform nested exit comparisons before failing.

**Evidence:**

- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/map/AreaGuideBuilder.kt:20-29,57-94,198-223`
- `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt:773-805`

**Correction boundary and dependency:** Budget-aware construction and precomputed scene adjacency.

**Acceptance:** Adversarial bounded input terminates before retained allocation exceeds the budget, marks only Area Guide unavailable, stays within measured heap/work units, and later valid projection recovers.

### QA-BLK-HTTP-ANDROID-01 — Chunk-size arithmetic overflows the body quota

**Failure:** `total + size <= maximumBytes` can overflow negative. A one-byte chunk followed by `Long.MAX_VALUE` bypasses the disk-write quota until the connection deadline.

**Evidence:** `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt:596-623`.

**Correction boundary and dependency:** Overflow-safe request quota accounting.

**Acceptance:** Validate with subtraction after proving total/size ranges. Boundary, cumulative-overflow, and `7fffffffffffffff` fixtures reject before writing the oversized chunk, leave no spool file, and do not prevent later bootstrap.

### QA-BLK-MAPPER-WEB-01 — Mapper is exposed without desktop support and polls unsafely

**Failure:** Desktop UI always exposes mapper capture but desktop has no mapper endpoints. It polls permanent 404 every 500 ms; requests can overlap, unmount does not abort, and structured errors render as `[object Object]`.

**Evidence:**

- `companion-web/src/pages/SettingsPage.tsx:28`
- `companion-web/src/pages/MemoryMapperPage.tsx:12-18`
- `companion-web/src/mapperGateway.ts:15-39`
- `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt:47-65`

**Correction boundary and dependency:** Bootstrap mapper capability/parity decision and shared bounded request utility.

**Acceptance:** Hide/disable unsupported mapper or implement desktop parity. Only one request may be active; unmount aborts; failures back off; recovery clears stale error; structured/malformed errors yield stable safe text. Parity tests cover availability.

### QA-BLK-HTTP-DESKTOP-01 — Desktop request capacity and action bodies are unbounded

**Failure:** JDK HttpServer uses `newCachedThreadPool`; slow action bodies retain unbounded workers. Action JSON has no byte/depth/read deadline. `/api/load` has downstream size limits but no request-read deadline.

**Evidence:** `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt:37-49,107-120,252-279`.

**Correction boundary and dependency:** Bounded executor/queue and request wrapper.

**Acceptance:** Fixed worker/queue cap with structured 503, endpoint byte/depth limits, and absolute/read deadlines. Excess partial requests remain bounded and time out; oversized actions reject before full parsing; later bootstrap succeeds.

### QA-BLK-WEB-CACHE-01 — Android 204 state responses omit no-store

**Failure:** Android unchanged-state response has no cache header while desktop sends `Cache-Control: no-store`. A cache can reuse 204 for the same `sinceVersion` URL after state changes.

**Evidence:**

- `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt:319-335,798-803`
- `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt:305-309`

**Correction boundary and dependency:** Shared parity assertion.

**Acceptance:** Both servers return identical 204, empty body, absent content type, and `Cache-Control: no-store`; deleting the header from either fails its owning test.

## 9. Tracked referrals

### QA-REF-CACHE-HOL-01 — CatalogCache serializes unrelated SHAs

**Failure:** Parser CLI workers share one `CatalogCache`; instance-wide synchronization makes unrelated SHA writes/reopens wait behind one slow persistence operation and can occupy every worker.

**Evidence:**

- `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/Main.kt:45`
- `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogCache.kt:46-102`

**Target:** Stage 8 persistence/liveness integration.

**Correction boundary and dependency:** Existing canonical-path coordinator plus cancellation-aware encoding.

**Acceptance:** Block SHA-A write; SHA-B write/reopen and later parsing still advance. Same-SHA writers serialize. Cancelled A emits no later chunks/publication.

### QA-REF-CLI-RETENTION-01 — Completed CLI results are retained without a total cap

**Failure:** Lazy discovery and in-flight work are bounded, but every result is retained, sorted, copied, and serialized. Millions of cheap error inputs can exhaust heap.

**Evidence:** `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/Main.kt:110-157`.

**Target:** Stage 8 corpus tooling.

**Correction boundary and dependency:** Explicit corpus cap or bounded spool/streaming reports.

**Acceptance:** Reject beyond a documented input cap before materialization, or prove heap remains independent of result count while preserving output order.

### QA-REF-7Z-01 — External 7-Zip work lacks aggregate/time/output bounds

**Failure:** Corpus validation captures complete 7-Zip output, waits without timeout, integrity-tests entire/solid archives, and extracts before enforcing actual resource limits.

**Evidence:** `tools/corpus/Invoke-DualDexCorpusValidation.ps1:122-160,273-298,339-424`.

**Target:** Stage 8 archive/corpus hardening.

**Correction boundary and dependency:** Shared archive policy and killable bounded subprocess wrapper.

**Acceptance:** Enforce entry, member, aggregate, staging, output, and time caps. Oversized/solid/1,025-entry/timeout fixtures terminate and clean their process tree/staging.

### QA-REF-SNAPSHOT-RACE-01 — Quarantine can delete a newer row across store instances

**Failure:** A corrupt read followed by a concurrent valid replacement can end with the reader’s unconditional delete removing the newer row. Normal production shares one instance, but the public store contract does not forbid multiple instances/process writers.

**Evidence:** `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/SaveSnapshotStore.kt:42-88`.

**Target:** Stage 8 persistence hardening.

**Correction boundary and dependency:** Canonical coordination and compare-and-delete identity/version.

**Acceptance:** Interleave corrupt read, valid write, and quarantine through canonical aliases; the valid replacement remains readable.

### QA-REF-MAP-ERROR-01 — Render failures collapse into genuine 404

**Failure:** Android converts map render exception/OOM/null to the same 404 as an absent key, hiding retryable module failure.

**Evidence:** `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt:399-412`.

**Target:** Stage 8 API parity.

**Correction boundary and dependency:** Typed `Found/Missing/Unavailable` map result.

**Acceptance:** Missing key remains 404; renderer exception/OOM returns the shared structured unavailable envelope with bounded diagnostics; other pages remain alive and later render recovers.

### QA-REF-SETTINGS-01 — Failed SAF fallback is reported as fallback success

**Failure:** When both settings routes and `openSafFallback` fail, the exception is discarded and result still says `SAF_FALLBACK`; callers surface nothing.

**Evidence:** `app/src/main/java/com/darkaxt/dualdex/storage/AllFilesSettingsLauncher.kt:20-25`.

**Target:** Stage 8 setup UX.

**Correction boundary and dependency:** Honest launcher outcome and visible final guidance.

**Acceptance:** Failure of package, global, and SAF launchers returns terminal failure, not fallback success, and presents folder-picker guidance plus retry.

### QA-REF-LEDGER-01 — Stage 1 referrals omit explicit dependencies

**Failure:** Stage 1 referral records omit the mandatory `Dependency` field, making the graph non-self-contained.

**Evidence:**

- `docs/superpowers/plans/2026-08-27-project-wide-qa-hardening.md:52-65`
- `docs/reports/qa-hardening/stage-01-closure.md:74-128`

**Target:** Stage 8 governance closure.

**Correction boundary and dependency:** Consolidated final verdict matrix.

**Acceptance:** Every referral records all mandatory fields, allowing explicit `None`; documentation lint rejects incomplete records.

## 10. Refuted, narrowed, and retained-good results

- **Refuted:** Absence of `stage-07-closure.md` is not an extra blocker while Stage 7 remains open under `S7-BLK-01`.
- **Narrowed:** Gen I descriptions are decoded correctly for 151 species; applicability/denominator is wrong.
- **Narrowed:** Area Guide’s confirmed defect is post-allocation budgeting/nested work, not an established unbounded combinatorial output on ordinary corpus inputs.
- **Narrowed:** SaveRAM revision failure affects recovery-derived direct assignment; the public `updateSaveRam()` setter advances revision correctly.
- **Narrowed:** Snapshot quarantine race requires multiple store instances/process/direct writers; normal production currently shares one instance.
- **Retained good:** The new detached Gen I resolver performs bounded indexed passes with cancellation propagated through probes and catalog sprite materialization.
- **Retained good:** Completion-driven parser CLI scheduling prevents an old-result head-of-line stall while preserving ordered output.
- **Retained good:** Separate SaveRAM snapshot databases survive catalog-cache cleanup and parser-schema invalidation outside the interrupted-migration/race cases above.
- **Retained good:** Basename-only identity, raw/ZIP source bounds, LZ77 allocation contracts, async cache-restore terminalization, Android main state polling, SSE conflation, static 404 behavior, sprite availability, simulator encounter ordinals, and the Stage 5 transport recreation/UDP ownership controls survived the review except where explicitly reopened above.
- **Retained good:** No additional high-confidence defect survived for AND-02/03/07 outside packaged retry coverage, RUN-03/06/07/09/10/11/12/13/14, REL-01/04/08 runtime export shape, or the current detached-record schema bump itself.

## 11. Remediation and closure rules

1. Do not start the final corpus until all 32 blockers and 7 referrals are resolved and focused owning regressions pass.
2. Any parser/catalog output correction must make and test a cache-schema decision before corpus evidence is generated.
3. Smart-sync each remediation checkpoint with `fork/master`; do not discard other-thread work.
4. Prefer focused tests during remediation. Run the broad integrated exit gate once after source stabilization.
5. The final corpus must be one fresh 333-eligible-input run over the audited 334-file physical inventory from the exact stabilized source and must satisfy the corrected evidence contract.
6. Stage 7 and Stage 8 closure documents must reread the complete specification and end with exactly zero blockers and zero referrals.
7. Do not publish another RC. The next publication is the authorized official stable `1.1` or `1.2`, after protected signing, exact artifact/evidence validation, and zero-gap closure.
