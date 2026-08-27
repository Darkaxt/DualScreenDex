# Storage and Guide Load Hardening Audit

## Scope

This audit checks the implementation against
`docs/superpowers/specs/2026-08-27-storage-and-guide-load-hardening-design.md`.
It changes neither ROM parsing nor compatibility selection.

## Requirement cross-check

| Requirement | Implementation evidence | Verification evidence |
| --- | --- | --- |
| SG-01 | `StorageSetupStatusPolicy` derives `storageGrant` only from Android All Files Access and emits only `GRANTED` or `MISSING`. | `StorageSetupStatusPolicyTest` covers missing and granted states. |
| SG-02 | ROM discovery remains the separate `romGrant` projection with missing, indexing, available, and failed transitions. | Kotlin policy tests exercise all four transitions; `SetupPage.test.tsx` exercises their presentation. |
| SG-03 | Direct indexing begins and fails through `StorageSetupStatusPolicy` without mutating granted storage access. | Policy tests prove `GRANTED/INDEXING` and `GRANTED/FAILED`. |
| SG-04 | `SetupPage` renders the grant action only when `storageGrant === 'MISSING'`. | The focused Preact suite proves the link is absent while granted and present while missing. |
| SG-05 | Direct and SAF failures now use fixed player-facing discovery messages. Normal setup UI receives no raw exception message from those paths. | Preact assertions cover the displayed indexing/failure language. |
| SG-06 | A failed direct refresh retains the prior direct index; without one, the policy keeps an available SAF index selected. | `StorageSetupStatusPolicyTest` covers retained-direct and SAF-fallback outcomes. |
| GL-01 | Parser checkpoints are written only to the catalog repository. `catalog` and `catalogReady` change only in `publishParsed` or `publishReopened`. | `progressiveParseFailureNeverPublishesOrLeavesAPartialCatalogReady` proves a checkpoint followed by failure never becomes active. |
| GL-02 | `beginCatalogTransition` clears the active catalog before asynchronous work, and generation checks reject superseded publication. The repository exposes only complete transactions to cache lookup. | Runtime transition tests prove the previous identity is withheld and stale parses/restores cannot win; `CatalogStoreTest.incomplete phases remain unavailable until the complete transaction commits` covers persisted checkpoints. |
| GL-03 | ROM-source and parser boundaries catch `Exception` and `OutOfMemoryError` in distinct clauses. No `Throwable` or other `Error` catch was added. | Runtime tests inject parser and ROM-source `OutOfMemoryError` and observe recovery rather than escape. |
| GL-04 | `GuideLoadFailure` supplies fixed player text while the original failure reaches `PerformanceRecorder`. | `GuideLoadFailureTest` and runtime tests prove sanitized UI text plus `ROM_IDENTITY`/`ROM_SOURCE` and `OutOfMemoryError` Debug evidence. |
| GL-05 | `GuideActivationGate` retains the failed exact source and rejects another automatic `tryBegin` for it. | `GuideActivationGateTest.failure blocks automatic activation until explicit retry`. |
| GL-06 | Exact `dualdex://guide/retry` dispatches to `retryGuideLoad`; the Setup action appears only for a failed guide. Retry clears only the current source latch. | Native-route and Setup-page tests cover exact routing, malformed rejection, visibility, and absence outside failure. |
| GL-07 | The activation gate releases its sole loading owner through `finishSuccess` or `finishFailure`; every runtime callback/catch chooses exactly one path. | Gate tests prove success/failure release and single in-flight ownership; runtime tests cover success, ordinary failure, and memory failure callbacks. |

## Stage result

All 13 specification requirements have direct implementation and regression evidence. No requirement is deferred and no blocker is registered. Release-wide verification and public artifact proof are recorded below after the gates complete.

## Release verification

- `companion-web`: 29 test files and 215 tests passed; the TypeScript/Vite production build completed.
- Gradle: `:catalog-store:test`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleRelease` completed successfully in one gate (94 tasks, 55 executed and 39 up to date).
- After merging the one newer `master` parser commit, `:parser-core:test`, the real `UnifiedGameStateRealControlTest`, every hardening regression class, lint, and release assembly passed again against the exact candidate source (95 tasks, 25 executed and 70 up to date).
- Release policy: 18 metadata/workflow tests passed with no failure, cancellation, skip, or todo.
- Public RC71 artifact identity, checksum, certificate, provenance, and exact tag-commit evidence remain the publication step; no APK has been installed or launched.
