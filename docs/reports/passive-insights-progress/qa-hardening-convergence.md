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
