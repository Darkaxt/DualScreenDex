# QA Hardening Convergence

## Purpose

The project-wide QA work is developed independently on `qa/project-wide-hardening`. A clean merge is not evidence that its parser, cache, persistence, recovery, map, and sprite hardening preserves the passive-insights suite. Each convergence result is therefore bound to the exact two commits tested.

## 2026-08-27 pre-merge control

- Passive-insights candidate: `e5c173fc` (`fix: surface guide recovery failures on welcome`), containing completed Stage 6 implementation and evidence.
- QA hardening tip: `26ea3ccf` (`fix: bound parser CLI corpus work`).
- Merge method: isolated disposable worktree, non-fast-forward merge stopped before commit.
- Merge result: clean, with no unresolved paths.
- QA delta at this checkpoint: 50 files, 2,950 insertions, 571 deletions.

### Results

- Browser production suite: 30 files and 227 tests passed.
- Browser production build: passed.
- Kotlin and Android gate: `:save-core:test`, `:battle-memory:test`, `:parser-core:test`, `:catalog-store:test`, `:companion-core:test`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleRelease` passed.
- Gradle result: 103 actionable tasks; 29 executed and 74 up to date; `BUILD SUCCESSFUL`.
- Packaged guide-recovery regression: the focused Welcome test passed with the authoritative native `RetroArchView` failure message and retry action.

The isolated Gradle run took 41 minutes 13 seconds while several unrelated JVM builds were active. This establishes correctness for the tested pair, not a performance baseline.

## Mandatory landing gate

This report does not approve later QA hardening commits. Before each remaining RC, fetch and record the current committed QA tip and rerun the combined-tree gate. When `qa/project-wide-hardening` reaches `master`, rerun the complete repository and specification matrices against the actual resulting `master` commit. Any failure is a release blocker; an earlier simulated-merge pass cannot be reused.

## 2026-08-27 landed-master control

The QA branch reached both `fork/master` and `fork/qa/project-wide-hardening` at `81182eb6` (`docs: close QA hardening stage 4`). This landing included three commits newer than the pre-merge control: bounded parser and guide cancellation, isolated optional-data cache invalidation, and the closing QA audit.

The Stage 6 guide-recovery fix and convergence record were rebased onto that exact landing. The complete gate was then repeated on the resulting authoritative tree:

- Browser production suite: 30 files and 227 tests passed.
- Browser production build: passed.
- Kotlin and Android gate: all required save, battle-memory, parser, catalog, companion, app unit, lint, and release-assembly tasks passed.
- Gradle result: 103 actionable tasks; 41 executed and 62 up to date; `BUILD SUCCESSFUL` in 52 minutes 12 seconds under concurrent JVM load.
- Merge state: no unresolved paths and no uncommitted QA work was read or modified.

This closes the QA-landing regression blocker for the tested `81182eb6` master commit. Any later hardening commit remains subject to the mandatory gate above.

## 2026-08-27 Stage 7 control

- Authoritative base: `fork/master` `d4ba6b3e` (`docs: close portable challenge RC76 delivery`).
- QA landing already present in that base: `81182eb6` (`docs: close QA hardening stage 4`).
- Current QA hardening tip: `ccffefb3` (`fix: harden runtime recovery and freshness`).
- Stage 7 implementation and evidence candidate: `f546818e` (`test: validate cross-feature UI conformance`).
- Ancestry result: `81182eb6` is present in the authoritative base; `ccffefb3` is one commit ahead of that base and was tested through an isolated non-committed merge into Stage 7.
- Merge result: clean, with no unresolved paths.

### Results

- Browser production suite: 30 files and 231 tests passed.
- Browser production build: passed.
- Real-ROM theme plus Stage 7 conformance: 30/30 Playwright tests passed, covering the theme control and all 756 route/theme/font rows.
- Feature/UI report validators: 16/16 passed.
- Kotlin and Android gate: all required save, battle-memory, parser, catalog, companion, app unit, lint, and release-assembly tasks passed.
- Gradle result: 103/103 tasks executed; `BUILD SUCCESSFUL` in 47 minutes 05 seconds.

This pre-landing result is bound to the then-current Stage 7 `f546818e` plus QA `ccffefb3`; it does not approve a later hardening commit.

### Landed-master result

QA subsequently reached both `fork/master` and `fork/qa/project-wide-hardening` at `1cd28586`. That commit contains tested runtime tip `ccffefb3` plus `docs: close QA hardening stage 5`. Stage 7 was rebased onto this authoritative history; its implementation/evidence commit became `5e066ab5`.

The complete gate was repeated on the actual landed history rather than reusing the simulated merge:

- Browser production suite: 30 files and 231 tests passed.
- Browser production build: passed.
- Real-ROM theme plus Stage 7 conformance: 30/30 Playwright tests passed, covering the theme control and all 756 route/theme/font rows.
- Feature/UI/release-policy tests: 49/49 passed.
- Kotlin and Android gate: all required save, battle-memory, parser, catalog, companion, app unit, lint, and release-assembly tasks passed.
- Gradle result: 103 actionable tasks; 32 executed and 71 up to date; `BUILD SUCCESSFUL` in 31 minutes 13 seconds.

This closes the QA landing regression blocker for the tested `1cd28586` master history plus Stage 7 `5e066ab5`. A later hardening or master commit still requires a fresh fetch and complete rerun before release.
