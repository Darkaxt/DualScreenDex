# Stage 4 Japanese sign grammar and cache checkpoint

Date: 2026-09-09. Scope: Tasks 420/423, `LNG-B002`. This is a tested generic-parser/cache checkpoint, **not original-ROM Japanese sign acceptance or Stage 4 closure**.

## Implementation

- Bind the compiled direct-text template's distinct open/repeat/wait/close/end roles instead of fixed opcode values. Accept the positively bound inline and setup-call textbox envelopes; preserve the existing paired-token setup/farcall requirements.
- Distinguish scalar and lead-byte-pair token widths. Scalar grammar requires the complete bounded compiled dictionary and positive ordinary-character fallback. Competing declarations, aliases, missing selected roots, unsupported runtime/nested controls, record/bank boundaries and cancellation fail closed.
- Resolve only used ROM0 literal consumers, at most four distinct literals per record, without codec-substitution shortcuts. Compiled END rejects the record before DONE, LINE, dictionary or ordinary-codec interpretation. The retained END counterexamples previously yielded a whitespace or glyph where termination was required.
- Advance parser cache revision **63 → 64**; SQL schema remains **2**, codec versions remain **1**. Fresh readers and caches reject revision-63 catalogs and rewrite/reopen current catalogs without losing geometry, contextual maps, POI obligations or independent Japanese/Korean overlays. Historical stale-62/61 and older checks remain.
- Add an independent, test-only selected Japanese Crystal direct-sign assertion through producer, SQLite reopen and cache-only API. It checks the selected headline digest, numeric identity/geometry, actual SQLite metadata, full POI coverage denominator and read/parse counters. API obligation/visibility authority is explicitly `REOPENED_NUMERIC_KEY_JOIN`, not nonexistent API fields. Its receipt is emitted only after all same-capture NativeChecks pass. Existing Korean original-control assertions remain unchanged.

No production hash, filename, language-name or per-ROM profile routing was introduced. Retained patterns and synthetic fixtures do not establish whole-original uniqueness or semantic acceptance.

## Verification

The fresh parent source-bound host gate passed **78/78**, zero failures/errors/skips, Gradle exit 0, no watchdog timeout, in **258.347 seconds**:

| Module | Selected synthetic tests |
|---|---:|
| Parser scalar/paired sign grammar and POI regressions | 36 |
| Actual SQLite/cache invalidation and contextual/POI regressions | 28 |
| Selected-sign and contextual API assertion helpers | 14 |

All five actual JUnit XML files and all **800 recorded Kotlin/Gradle/configuration source pins** were independently rehashed after completion; the exact method inventory matched. These pins describe the focused host gate, not a complete final-matrix source inventory. Parent HEAD was `9fed34c6ebab7044381fbf4c82f96f278f8c4a94` plus this checkpoint's exact source delta.

The first combined launch failed before Gradle/JUnit because the Windows command line was too long: **zero tests**, exit 1. Its outcome remains unchanged. A new output reservation moved the same 78 exact selectors into the private Gradle filter configuration; no production change or selector waiver was used to obtain the successful run.

Earlier isolated evidence remains scoped:

- Captured-END regression: 37 cases, two genuine new failures before correction; 37 passed afterward, comprising 36 synthetic cases and one guarded retained-pattern diagnostic.
- App helper: seven genuine failures against the stub, then seven passes. Four independent retained/source oracle pins, 29 source charmap entries and the retained selected block-to-grid geometry were verified before integration.
- Cache regression: two cases at revision 63, one genuine failure because both the fresh reader and cache accepted the stale catalog; the same two cases passed after the revision-64 bump. A prior private runner import syntax failure launched no Gradle child and is not behavioral RED.

### Retained evidence digests

| Artifact | SHA-256 |
|---|---|
| Parent gate result | `e35632d9b27170acc7e94dbb520fc5b91b76897d9409cc72b4afe8f20ebe4638` |
| Parent source pins | `351dc5d6d4082a6f20cdee42654512d7902a4062e7de205d5cfe7ba50d2fe429` |
| Parent command and runner pins | `91b692694ecef181ed5c83d3dbae20bd65b9ff80d34ecec08c2521bd0efa07ae` |
| Parent Gradle log | `aabdc1792989c9e63283e013358b2d22a9d1ac40ad9eb212b11004cabcd764a6` |
| Independent parent verification | `cc7a826a8ab9aaa1777bbd7b8974211f6f66fb2d52e44f562a6c8f2b65673d97` |
| Preserved command-length failure result | `b71dfaa68d8c5378d0444d9b4cebd08d14b2957b663b54df210de3d2873fa92b` |

## Remaining acceptance

Task 423's stale-cache implementation is complete in this checkpoint. Task 420 / `LNG-B002` remains open until a separately scoped, reviewed, source-bound original Japanese Crystal producer/reopen/API gate confirms the independent selected headline and existing Japanese/Korean regressions. Consumed prior gates cannot be retried or rewritten.

GBA title/remaining content obligations, G0–G4 matrix exporters and independent oracles, the current 43-cell/44-control matrix with all 15 capability dispositions, and one final eligible corpus after executable changes stabilize remain mandatory. The published matrix assembler is tooling only (`acceptance=false`). **Zero final stable-source controls are accepted.** Stages 5–6 remain blocked on sequential published closure.

This checkpoint performed no original-ROM acquisition, corpus run, device/emulator/ADB work, APK/signing/release, ROM publication or cleanup deletion.
