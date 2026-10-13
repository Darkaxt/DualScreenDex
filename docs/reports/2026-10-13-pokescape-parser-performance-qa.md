# Pokescape parsing performance QA — 2026-10-13

## Verdict

**Suspected slowdown; substantial allocation/GC costs confirmed.** The fastest current parse took **75.572 s**, versus **43.280 s** before the Pokescape compatibility patches: **+74.6%**. The two-observation median increased **19.0%**. These are concerning observations, not an established universal regression percentage: the baseline itself varied from43.280 to94.981s, and the versions intentionally produce different supported data.

No production parser change or new tutor decoding was made during this review. Compatibility work remains paused; the recommended next work is the allocation optimization below, not another tutor entry.

## Method and scope

- Baseline: `5a29813807e7b1d84839180d0984e7124fe900f3`, parser86/SQL2, before the Pokescape patch series.
- Current: `a6995aada3acad9051ddbb3d96c6ca4636cc8233`, parser110/SQL2.
- One identical privately admitted regular32MiB input. Four fresh, separately MAIN-reviewed one-original/one-parser reservations; order **baseline, current, current, baseline**. No input copies, corpus run, Android/device/emulator work or signing.
- Same pinned Zulu21 executable; fresh JVM each time; `-Xms256m -Xmx3g -XX:ActiveProcessorCount=2`, jobs1, English/UTF-8, sanitized JVM-option environment and task-owned temp. JFR `profile` settings on every run. Fresh output/cache directories; OS filesystem caches were not flushed.
- CLI `durationMillis` times `CatalogParser.parseCatching`, excluding input loading, report generation and database persistence. Process wall/CPU/peak memory include the whole CLI and profiler.
- Windows process CPU and native peak working-set/commit counters, not instantaneous memory samples. CPU/allocation hotspots come from JFR execution/native/allocation samples; allocation weights are estimates of churn, not simultaneously live memory.
- Known host builds/queued supervisors were awaited by exact native process-exit events before every run. No Gradle mutex was acquired for benchmarks. Other processes can start later; this does not establish a universally idle host. Mean sampled machine CPU load was40.9–57.2%.
- Isolated Git source exports built only the JVM dependency closure: parser-core, parser-cli, catalog-store and save-core. Source/build/runtime pins are retained privately. The initial incomplete three-module build failed before any original read; its evidence remains historical. Corrected builds both acquired `Local\Darka.AndroidGradleBuildGate`: two workers/no parallel, observed Gradle3GiB/requested Kotlin3GiB, single-use daemon exit verified, no memory-related failure. No test tasks ran; generic test/native override verification gaps remain explicit.

## Measurements

| Run | Parser seconds | Whole CLI seconds | Whole CLI CPU seconds | Peak working set GiB | Database write/reopen seconds |
|---|---:|---:|---:|---:|---:|
| Baseline1 | 43.280 | 70.558 | 110.063 | 2.840 | 8.877 /1.724 |
| Current1 | 75.572 | 97.857 | 154.672 | 2.936 | 8.000 /1.274 |
| Current2 | 88.955 | 146.623 | 225.828 | 2.444 | 19.062 /4.006 |
| Baseline2 | 94.981 | 153.033 | 242.438 | 2.980 | 19.039 /3.453 |

Best whole-CLI CPU cost increased **40.5%**. Whole-CLI wall median increased9.3%; median peak commit decreased2.1%. Lower resident memory in one run is not proof of lower heap demand: OS working-set trimming can change it. Both versions approach3GiB of process memory.

JFR sampled **78.9–79.3GiB of allocation weight per complete CLI execution**, with200–241 collections,10.8–23.0s of accumulated collection duration and a largest recorded collection of1.8–5.5s. These include persistence/report work; they are not parser-only allocation totals or isolated stop-the-world pause sums.

## Ranked findings and next work

### 1. Type-chart candidate validation allocates full boxed matrices before rejection

`parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/TableValidators.kt:1052` constructs a boxed list for every candidate matrix, then makes additional validation/count/set passes. The referenced candidate search tries multiple table sizes for many offsets (`:924`).

- This method accounts for approximately **37.7–37.9GiB** of sampled allocation weight in **every** run: roughly48% of total CLI allocation weight.
- It is the leading project CPU leaf in all four recordings, with725/1458/1498/1600 samples respectively.
- Its function body is byte-identical between the baseline and current Git revisions. This is a **pre-existing bottleneck**, not evidence that the tutor patch introduced it.
- Recommended fix: bounded primitive single-pass validation with immediate rejection of out-of-range values; count neutral/common/distinct values without boxing a candidate matrix. Preserve every threshold, evidence field, ambiguity rule and cancellation/budget contract. Verify output equivalence before claiming a speedup.

### 2. Full-ROM title nomination creates per-position opcode arrays

`parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/CompiledGbaFieldMapTitle.kt:39` probes every aligned position; its legacy branch calls `CompiledGbaLegacyFieldMapTitle.layout` (`:25`). Two long vararg opcode lists are constructed even for immediately rejected positions.

- The legacy layout method contributes approximately **3.32–3.34GiB** of sampled allocation weight in each current run; it does not appear among the baseline's top20 allocation sites.
- The legacy source file itself is unchanged across revisions. The observation is an increased/newly sampled execution cost under the current resolution path, **not a proven introducing commit or exact attribution of the overall slowdown**.
- Recommended fix: allocation-free leading-opcode rejection and immutable-pattern matching without Kotlin spread-array copies. Preserve the complete connected-owner checks and malformed-contender/fail-closed behavior.

### 3. Canonical serialization and persistence remain significant secondary costs

`CatalogCanonicalJson`, `CatalogSectionCodec` and `CatalogLogicalDigest` contribute multiple GiB of sampled allocations in both versions. Database write/reopen together vary from9.274 to23.068s. They affect perceived completion time even though the CLI parser timer excludes them.

- Audit intermediate JSON trees, repeated canonical number conversion and duplicated canonical traversal after the two parser hotspots.
- Any optimization must preserve exact canonical digests and complete write/reopen equality. Do not skip persistence or weaken verification to make the benchmark faster.

The compiled tutor reader is not among the top20 project CPU leaf methods in any recording. This is not proof of zero tutor overhead; it does make the allocation hotspots above the better-supported first targets.

## Correctness and limitations

All four CLI processes exited0, reported exactly one input and reopened18 SQLite sections with equal pre/post logical digests. Each version reproduced its own digest across repeats. Both current runs exactly reproduce accepted parser110 digest `204e9665238d9277fe5f86898a73709fd7b4b4ca040468284b4bcf1e0c4fe642`.

The current parser correctly supports more data:1235 species descriptions/categories,828 move details/categories and298 ability names/descriptions, among other changes. The baseline lacks these and has different, historically unverified heuristic acquisition counts. Thus faster baseline output is not an equally correct substitute.

Only two observations per version were collected. There was no no-profiler measurement, Android timing, equal-feature ablation or all-ROM performance qualification. **The slowdown signal should be addressed; neither a reliable universal75% regression nor a clean bill of performance health is justified.** No optimization has been implemented or benchmarked yet.

Private source/runtime pins, immutable admissions/receipts, native process counters, sampled hotspot summaries and consumer code are retained outside Git. Oversized JSON profile-decoding failures were not parser failures and caused no extra original reads; streaming JFR consumption produced the final summaries without replaying any parser.
