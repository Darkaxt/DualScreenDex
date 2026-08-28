# QA Hardening Stage 7 Parser Evidence Blocker

**ID:** `S7-BLK-01`

**Classification:** `BLOCKER` — implementation corrected; fresh post-fix evidence still required

**Requirement:** `INV-04`, `INV-06`, `CAT-08`, `CAT-14`, `REL-05`

## Failure

The source-bound 334-input corpus gate could not complete because `Let´s Go Pikachu (v6.0).gba` entered multiplicative full-ROM work in `Gen1DetachedSpeciesResolver`.

The first corpus execution completed 121 inputs before an external stop. A non-overlapping resume completed another 15 inputs, but this ROM occupied result slot 1. The parser CLI had started 16 inputs and completed the other 15; it did not submit input 17 because ordered collection waited for slot 1.

## Root cause

Two isolated thread dumps showed one runnable, CPU-bound parser thread rather than a lock or I/O deadlock:

- At 32.21 seconds elapsed, the main thread had consumed 30.69 seconds of CPU in `Gen1DetachedSpeciesResolver.hasFarCopyConsumer()`.
- At 55.06 seconds elapsed, it had consumed 53.02 seconds of CPU and remained in `Gen1DetachedSpeciesResolver.resolve()`.

`resolve()` scanned the complete ROM for every missing Dex number. Every plausible detached record then called `hasFarCopyConsumer()`, which scanned the complete ROM again. A structurally plausible Gen I probe over this derivative therefore multiplied candidate discovery by repeated whole-ROM consumer scans.

`mapConcurrentlyOrdered()` compounded the impact by retaining a bounded 16-item in-flight window but waiting on the oldest future before submitting more work. One slow first result left completed worker capacity idle and prevented unrelated later inputs from advancing.

## Implemented correction

- `Gen1DetachedSpeciesResolver` now indexes far-copy consumers once and scans detached candidates once.
- Both complete-ROM passes check parser cancellation every 4 KiB, including when detached sprites are resolved during catalog materialization.
- Consumer and candidate collections fail closed beyond 4,096 structurally accepted entries.
- Gen I family probes and sprite materialization pass their session cancellation token into detached-record resolution.
- `mapConcurrentlyOrdered()` now consumes an `ExecutorCompletionService`, replenishes its bounded in-flight window after any completion, and restores source order only when returning results.
- Parser cache schema revision 45 invalidates revision-44 catalogs. The structural caps can intentionally disable optional detached-species evidence on pathological input, so treating this as output-invariant and retaining revision 44 would permit stale pre-bound output.

## Focused verification

- `Gen1DetachedSpeciesResolverTest` and both species-media cancellation regressions: passed, including resolution through one compiled consumer, cancellation during each complete-ROM pass, propagation through sprite materialization, and propagation from `CatalogMaterializer`. The caller-level regression was also verified red by removing the production token handoff, then green after restoring it.
- `ParallelMapOrderedTest`: passed, including a regression proving that a blocked first result does not prevent later inputs from starting while returned order remains stable.
- Revision-44 cache invalidation regression: failed before the schema bump and passed after revision 45.
- Release workflow parser-schema guard: 18 tests passed.
- The formerly blocking ROM reached terminal `NO_FAMILY_MATCH` in 280,858 ms. Its optional family resolution failed closed rather than crashing.
- Official Red, Blue, and Yellow live controls reached and passed the relevant detached base-stat, Mew sprite, navigable-species, species-catalog, base-stat, and sprite assertions. The three broader methods then failed on a Pokédex-description capability expectation (`PARTIAL` rather than `AVAILABLE`); this is outside the detached-record fix, is not being misreported as a complete test pass, and remains input for the project-wide QA review.

## Evidence disposition

No owned parser process remains running. The 136 pre-fix receipts are retained only as diagnostic evidence. Because parser-core, parser-cli, and parser cache-schema source changed, none of those receipts may contribute to release evidence or Stage 7 closure.

`S7-BLK-01` remains open only for the post-fix evidence gate. Acceptance requires a completely fresh run of all 334 inputs from the committed correction, with every materialized catalog persisted and reopened, zero parser or persistence errors, and release evidence bound to that exact source commit. A partial resume or aggregation with the 136 old receipts is prohibited.
