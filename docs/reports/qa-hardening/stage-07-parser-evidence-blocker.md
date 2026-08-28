# QA Hardening Stage 7 Parser Evidence Blocker

**ID:** `S7-BLK-01`

**Classification:** `BLOCKER`

**Requirement:** `INV-04`, `CAT-08`, `CAT-14`, `REL-05`

## Failure

The source-bound 334-input corpus gate cannot complete because `Let´s Go Pikachu (v6.0).gba` enters multiplicative full-ROM work in `Gen1DetachedSpeciesResolver`.

The first corpus execution completed 121 inputs before an external stop. A non-overlapping resume completed another 15 inputs, but this ROM occupied result slot 1. The parser CLI had started 16 inputs and completed the other 15; it did not submit input 17 because ordered collection waited for slot 1.

## Root cause

Two isolated thread dumps showed one runnable, CPU-bound parser thread rather than a lock or I/O deadlock:

- At 32.21 seconds elapsed, the main thread had consumed 30.69 seconds of CPU in `Gen1DetachedSpeciesResolver.hasFarCopyConsumer()`.
- At 55.06 seconds elapsed, it had consumed 53.02 seconds of CPU and remained in `Gen1DetachedSpeciesResolver.resolve()`.

`resolve()` scans the complete ROM for every missing Dex number. Every plausible detached record then calls `hasFarCopyConsumer()`, which scans the complete ROM again. A structurally plausible Gen I probe over this derivative therefore multiplies candidate discovery by repeated whole-ROM consumer scans.

`mapConcurrentlyOrdered()` compounds the impact by retaining a bounded 16-item in-flight window but waiting on the oldest future before submitting more work. One slow first result leaves completed worker capacity idle and prevents unrelated later inputs from advancing.

## Current disposition

All owned parser process trees were stopped after the thread dumps. No corpus job remains running. Machine-readable local receipts retain 136 non-overlapping completed outcomes; 198 inputs remain. No compatibility manifest or Stage 7 closure claim has been published.

## Target and acceptance

**Target:** Stage 7 evidence closure before `REL-05` can pass.

Acceptance requires:

1. Bound or pre-index the detached-record/far-copy scan so work cannot multiply into repeated complete-ROM scans.
2. Preserve identical detached-species results for official Gen I controls.
3. Make the isolated blocked ROM reach a terminal parser result within a deterministic operation/time bound.
4. Ensure one slow ordered result cannot prevent unrelated completed workers from advancing the corpus window.
5. Resume only the remaining 198 inputs, aggregate all non-overlapping receipts, and publish evidence only after all materialized catalogs persist and reopen with zero parser or persistence errors.
