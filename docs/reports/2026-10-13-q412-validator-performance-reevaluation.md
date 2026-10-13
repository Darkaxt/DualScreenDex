# Pokescape Q4.12 validator performance reevaluation — 2026-10-13

## Result

The single validator optimization changes median parser time from **107.906s to 76.310s (-29.3%)** across two cold JVM observations per package. Median whole-CLI CPU changes **-18.8%**; median sampled allocation churn changes **-47.6%**. Every run reproduces the exact previously accepted parser 110 catalog, with 18-section SQLite write/reopen equality. No supported feature or acceptance threshold was removed.

**Peak memory did not improve:** median native peak working set rises **6.4%**, and peak commit rises **2.7%**. Reduced allocation churn does not imply a smaller peak footprint.

This is a same-output before/after comparison, unlike the earlier parser86-versus110 QA. The earlier slowdown signal remains valid historical evidence; this run does not establish which compatibility patch introduced it or promise a universal/Android speedup.

## Narrow change and preservation

Only `TableValidators.q412SquareTypeChart` candidate validation and its private common-value membership representation change: primitive traversal, early unsigned rejection, primitive counters and distinct cardinality capped at the existing three-value threshold. Complete extent/alignment validation still precedes traversal. All14 common multipliers, neutral/non-neutral/common/distinct thresholds, confidence, reason strings and evidence fields are preserved. Referenced-boundary discovery, u16 inverse tables, ambiguity/overlap policy and existing cancellation/budgets are unchanged. Parser schema110 and SQL schema2 remain unchanged.

- Generated allocation RED: **12,961,536 bytes** for1,024 early-invalid candidates on the original implementation; four correctness tests passed.
- GREEN: **145 focused tests**, zero failures/errors/skips; includes **3,103 exact-evidence oracle comparisons** over18–64 types, both widths, relocations, all common values, threshold boundaries, unsigned invalid values, structural guards and seeded randomized input. The same allocation guard passes below1MiB.
- Existing inverse, ambiguity, overlapping/interior-root, referenced-boundary, codec, projection and materialization controls pass.
- No originals are read by the generated tests.

## Method and package provenance

Baseline production is the published parent `a3597c1c`; candidate is its explicitly qualified unpublished Q4.12-only production delta. CLI manifests stamp that parent in both packages; the candidate is **not** represented as an unchanged published-source build. Frozen source/runtime pins bind one production source and one executable class change. A nested chart class changes source-line metadata only, independently verified by complete verbose bytecode comparison excluding archive locator/checksum/source line numbers. All other runtime jars are byte-identical.

The initial strict one-class package assertion failed because of that nested debug metadata. Its failure is retained; prospective profile revision2 qualifies the exact observed delta, without rewriting history or weakening feature preservation.

Four separately MAIN-reviewed immutable reservations, one32MiB regular original and one parser execution each; order **baseline1 → current1 → current2 → baseline2**. Same pinned Zulu21 Java executable; `-Xms256m -Xmx3g -XX:ActiveProcessorCount=2`, jobs1, English/UTF-8, JFR profile settings, sanitized inherited JVM options and task-owned temporary directories. Required watchdog binding is part of the reviewed launch profile. Fresh output/cache per run; OS filesystem caches are not flushed.

Native Windows process counters measure whole-CLI CPU and peak working set/commit. CLI parser duration measures `CatalogParser.parseCatching`, excluding ROM loading/report/database work. Whole-process metrics include those costs and profiling. Known host Gradle owners/queue supervisors are awaited through native identity-checked process-exit events before each run; benchmarks do **not** acquire the Gradle mutex. Other processes may start later, so no universal host-idle guarantee is claimed.

Both builds acquired `Local\Darka.AndroidGradleBuildGate`: two workers/no parallel, single-use Gradle, requested Gradle/Kotlin3GiB, project-default Kotlin strategy, one 512MiB test fork, Gradle test fail-fast. Gradle profile and daemon exit verified; no memory failure. Task-specific Kotlin/native settings are not a verified total-host cap. Conversation title was unavailable, so thread label remained Unknown.

## Timing and native memory

| Run | Parser s | Whole CLI s | Total CPU s | Peak working set GiB | DB write / reopen s |
|---|---:|---:|---:|---:|---:|
| baseline-1 | 108.135 | 172.900 | 236.578 | 2.529 | 23.635 / 4.583 |
| current-1 | 76.902 | 143.309 | 198.234 | 2.809 | 25.404 / 3.875 |
| current-2 | 75.717 | 135.860 | 187.438 | 2.778 | 22.703 / 4.041 |
| baseline-2 | 107.677 | 175.222 | 238.359 | 2.721 | 24.899 / 4.178 |

Median whole-CLI wall time changes **-19.8%**. Fastest parser time changes **-29.7%**. Peak working set is not allocation churn or exact peak Java heap.

## Streaming JFR observations

| Run | Sampled allocation weight GiB | Validator weight GiB | GC events | Summed GC event s | Largest GC event s |
|---|---:|---:|---:|---:|---:|
| baseline-1 | 79.192 | 37.776829 | 232 | 14.265 | 1.572 |
| current-1 | 41.471 | 0.000000 | 188 | 13.965 | 1.940 |
| current-2 | 41.475 | 0.000000 | 193 | 10.571 | 1.199 |
| baseline-2 | 79.145 | 37.425726 | 218 | 14.260 | 2.039 |

The validator is the largest project CPU leaf in both baseline profiles (2,098 / 1,994 samples). It is no longer the largest leaf afterward; 336 samples remain in current2's top methods. This shows reduced cost, not removal of validation. No further hotspot optimization is included.

Allocation weights estimate churn over the whole CLI, not live heap. Zero sampled validator weight, if present, means no attributed allocation sample—not a proof of literal zero allocations. Generated thread-allocation measurement supplies the separate deterministic early-rejection guard. GC event duration is not necessarily an isolated stop-the-world pause. Streaming decoding uses512MiB/two processors and reads only generated recordings, not originals.

## Scope and delivery

Retain compact private build/qualification/admission/consumption/package/profile proof; keep original locators, input hashes, raw recordings and machine-specific diagnostics out of public Git. Publish the coherent implementation/test/report checkpoint to the existing fork feature branch, then ticket the exact task-owned scratch root for cleanup. Compatibility expansion stays paused. No title-scanner or serialization optimization, tutor decoding, corpus execution, Android/device testing, signing or upstream interaction is included.
