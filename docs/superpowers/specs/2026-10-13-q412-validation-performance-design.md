# Q4.12 candidate-validation performance optimization

**Status: IN_PROGRESS. Authorization: already_authorized.** User requested feature preservation and implementation/performance reevaluation after the [performance QA](../../reports/2026-10-13-pokescape-parser-performance-qa.md). One outcome: reduce Q4.12 validator allocation/cost without changing supported data. No tutor expansion, title-scanner optimization, corpus or device work.

| ID | Required acceptance |
|---|---|
| QP-01 | Replace only boxed candidate-matrix/count/set work with bounded primitive traversal. Preserve u16/u32 handling, complete extent/alignment checks, all14 common values, neutral/non-neutral/common/distinct thresholds, exact evidence fields/reasons/confidence, caller ambiguity/inverse-table logic and existing cancellation/budgets. No acceptance-rule or supported-domain change. |
| QP-02 | Generated oracle equivalence covers18–64 types,2/4-byte cells, relocation, every common multiplier, threshold boundaries, invalid unsigned cells, two-value matrices, alignment/truncation and randomized inputs. Observe a deterministic allocation-budget RED before implementation and GREEN afterward; run existing type-chart/validator projection/resolution controls. No original reads in tests. |
| QP-03 | Counterbalanced before/after performance comparison on the same singly admitted32MiB input: identical pinned JVM/JFR/jobs1/temp settings, separately MAIN-reviewed immutable reservations, no original copy or corpus. All runs reproduce the exact accepted parser110 logical catalog digest and18-section write/reopen equality. Keep parser schema110/SQL2 because behavior/data remain unchanged. Report measured time/CPU/memory/churn and limitations honestly; no guaranteed Android/universal improvement claim. |
| QP-04 | Inline review, installed serial host Gradle gate (two workers/no parallel, initial Gradle/Kotlin3GiB), preserved source/stash/other owners; retain compact private proof and ticket exact scratch cleanup. Smart-sync immediately before commits and push the coherent deliverable. No signing/device/upstream interaction. |

## Compact stage / reconciliation

One ACTIVE integrated stage covers QP-01–04: establish generated RED → implement primitive validator → focused equivalence/regression GREEN → pin before/after packages → reviewed performance/current-catalog acceptance → publish/report/cleanup. No component acceptance or unrelated optimization.

QP-01 implementation is present with unchanged guards, thresholds and evidence. QP-02: original implementation produced the expected allocation RED (12,961,536 bytes for1,024 rejected candidates; four correctness tests passed); primitive implementation passed all145 focused tests, including3,103 generated exact-evidence comparisons and the allocation guard below1MiB. Both builds acquired the shared gate with two workers/no parallel, requested Gradle/Kotlin3GiB, one512MiB test fork and no memory failure; Kotlin strategy stayed project-default.

Package qualification binds one production source and one executable class change. The nested `ReferencedQ412Chart` class also changes source-line metadata only: full verbose bytecode comparison, excluding archive locator/checksum/source line numbers, is equal. The initial strict one-class assertion failed and is retained; prospective profile revision2 explicitly qualifies this debug-only delta. All other runtime jars are byte-identical.

QP-03 satisfied: four separately MAIN-reviewed counterbalanced runs exit0, reproduce the exact accepted parser110 catalog and18-section write/reopen equality. Median parser107.906s→76.3095s (-29.3%); whole-CLI CPU -18.8%; sampled allocation churn -47.6%. Peak working set +6.4% and peak commit +2.7%: no peak-memory reduction claim. See the [fresh reevaluation](../../reports/2026-10-13-q412-validator-performance-reevaluation.md) for per-run evidence and limits.

QP-04 inline source/test/harness review found no remaining behavior or scope defect. Compact private proof:62 members/14,970,942 bytes, reopened and SHA-verified; originals were not copied. Source/stash and other owners remain preserved. Current blockers: coherent checkpoint publication and exact ticketed scratch cleanup/final delivery reconciliation remain pending. Tracked deferrals:0. Compatibility work stays paused; this stage remains ACTIVE through delivery.
