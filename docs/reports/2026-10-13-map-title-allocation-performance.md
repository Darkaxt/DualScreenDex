# Legacy map-title allocation performance — 2026-10-13

## Measured result

Median parser time: **57.052s → 57.213s (+0.3%)**. Median whole-CLI CPU changes **+7.1%**, sampled allocation weight **-7.7%**, native peak working set **+0.1%**, and native peak commit **-1.4%**. These are two observations per package on a shared Windows host, not a universal/Android guarantee. Both packages retain the previous Q4.12 optimization.

**Verdict: allocation reduction only, not a demonstrated speed improvement.** Whole-CLI wall median also rose **3.8%**. The **7.1% CPU increase is an adverse signal**, not a result to dismiss as noise. With two runs per package, neither its cause nor CPU non-regression is established. Retain the narrow pattern-reuse change for its demonstrated allocation benefit; do not describe it as an overall performance win or as a memory-peak improvement.

A bounded generated rejection-classifier diagnostic used the frozen packages, four cold JVMs in the same counterbalanced order, 100,000 warmup calls and three one-million-call rounds each. It was inconclusive: later rounds eliminated candidate allocations in both packages, CPU counters were coarse, and timings varied between JVMs. Calling-thread CPU excludes GC workers and other CLI stages; fixed input and reflection do not reproduce real-ROM scanning. This diagnostic does not explain away or invalidate the measured whole-CLI CPU increase. It read no original and launched no catalog parser.

All four catalog executions exit0 and reproduce the exact previously accepted parser110 catalog with18-section SQLite write/reopen equality. No supported feature, opcode check or acceptance rule was removed; schema110/SQL2 are unchanged.

## Scope and correctness

Only the two legacy owner opcode-pattern arrays are reused instead of constructing vararg arrays per classifier invocation. Patterns remain private and are only read. Every word and complete range check remains; existing downstream selected-loader/literal/window/owner-flow authority, ambiguity, failure and cancellation behavior is unchanged. Other vararg checks, storage and serialization are not optimized.

Generated allocation RED: **1,409,560 bytes** for4,096 rejected candidates, with four classifier-correctness tests passing. GREEN: **98,840 bytes** for the same generated control; **35 focused tests** pass with zero failures/errors/skips, including1,674 original-classifier equivalence cases and the existing legacy title/authority/cancellation/budget controls. Generated tests read no originals.

## Package and launch provenance

Published baseline parent: `86fbbf59`. Candidate was measured as an explicitly qualified unpublished one-source title-only delta, not an unchanged published-source run. Both CLI manifests stamp that parent. Runtime/source pins, exact executable class changes and strictly debug-only other class differences are retained privately. Pinned JDK ASM profile2 reconstructs compared classes with debug attributes and only Kotlin's `SourceDebugExtension` debug annotation skipped; instructions, fields, signatures, other annotations and non-debug attributes are retained. Exactly the title classifier and Reader have executable changes; the other five changed classes are debug-only. Initial normalization did not strip the duplicate debug annotation and failed; its evidence is retained, and the prospective revised profile was verified before admissions. Other runtime jars are byte-identical.

Four fresh separately MAIN-reviewed immutable reservations, one regular32MiB original/one parser each, counterbalanced baseline1 → current1 → current2 → baseline2. Same pinned Zulu21 executable; `-Xms256m -Xmx3g -XX:ActiveProcessorCount=2`, jobs1, English/UTF-8, JFR profile, sanitized inherited JVM options, reviewed watchdog binding and fresh task-owned temp/cache. OS caches are not flushed. Known host Gradle owners/queue supervisors are awaited by identity-checked native exit events; benchmarks do not hold the Gradle mutex. External workloads may start later.

Builds use the installed shared gate: two workers/no parallel, single-use Gradle, requested Gradle/Kotlin3GiB, project-default Kotlin strategy, one512MiB test fork and native Gradle fail-fast. Inspect actual gate/profile and memory evidence in retained proof. Thread title was unavailable and not invented. Native/task-specific overrides are not a verified host-wide cap.

## Per-run measurements

| Run | Parser s | Whole CLI s | Total CPU s | Peak working set GiB | Sampled allocation GiB | Title classifier GiB |
|---|---:|---:|---:|---:|---:|---:|
| baseline-1 | 58.946 | 104.681 | 172.531 | 2.981 | 41.450 | 2.820566 |
| current-1 | 62.506 | 107.386 | 179.641 | 2.553 | 38.324 | 0.000000 |
| current-2 | 51.920 | 103.698 | 173.609 | 2.792 | 38.312 | 0.000000 |
| baseline-2 | 55.158 | 98.682 | 157.172 | 2.360 | 41.566 | 3.603290 |

Parser timing excludes ROM loading/report/database work; whole-process CPU/memory and JFR include them. Allocation weights estimate churn, not resident memory; zero attributed samples do not prove literal zero allocations. Peak working set is not exact peak Java heap. GC event duration is not necessarily an isolated stop-the-world pause. Streaming profile decoding reads generated recordings only with512MiB/two processors.

## Delivery boundary

Retain compact reopened/hash-verified private source/package/qualification/admission/consumption/test/profile proof. Publish the coherent implementation/tests/report to the existing fork feature branch, then apply only the exact reviewed scratch ticket. No original copy, corpus, new compatibility decoding, storage/serialization optimization, Android/device work, signing, release or upstream interaction is included. Compatibility expansion stays paused.
