# DualDex compatibility progress — October11,2026

**Report window:** October10,13:42 UTC through October11,13:42 UTC (24 hours). **Parent goal and current ROM: IN_PROGRESS.** This is retained focused/composite evidence, not a new full-corpus evaluation or release.

## ROMs worked on during this window

**Pokescape v1.0.4 only.** No other ROM was used as a fresh implementation target or real regression input in this window.

- Independently accepted the previously implemented native blank-name contract at checkpoint `9f7ca99d`: applicable Local names482/482 and encounter names201/201. Two native-blank maps and one encounter remain structurally present. The source implementation predates this reporting window; its fresh acceptance does not imply it was newly implemented today.
- Implemented and published source `28d123e6`: separate Emerald braille transport/cell decoding and correct ordinary spacing-parameter handling. Independent native acceptance covers43 changed direct POIs: **34 recovered and nine corrected labels**,42 braille/one ordinary. POI_TEXT1,769/2,149→1,803/2,149; Area Guide content POIs666→700.
- Fresh guarded host QA:2,471 executed/244 guarded skips/zero failures/errors;25 focused tests and all128 selected controls executed. Exact source/profile/runtime binding, complete18-section SQLite/reopen/integrity,95,066references and exact parser96/map95/item94/ability93 reversals accepted.
- Retained-only remaining-script/background qualifications advance the next coherent batch but do not publish native applicability or new coverage.

[Acceptance and exact package-versus-implementation provenance](2026-10-11-pokescape-native-braille-name-acceptance.md). The actual accepted package names `0b00cd4d` plus exact unpublished candidate97 pins, not a clean-tree run at the later implementation commit. Consumed failures and failed generated preflights remain historical evidence.

## Current overall progress

| Measure | Current composite observation |
|---|---:|
| Retained distinct ROM identities | 342 |
| Selected/materialized catalogs | 301 (88.01% of identities) |
| Persisted and exactly reopened catalogs | 301 |
| Ambiguous family | 2 |
| No family match | 39 |
| Parser/catalog/persistence/reference errors | 0 |
| Resolved language manifests | 286 |
| Unknown language manifests | 15 |
| Weighted-score100% rows | 28 |
| Mean weighted score across301 selected rows | 88.55% |

**Neither301 recognized catalogs nor28 weighted-score100% rows is a full-compatibility count.** Optional/applicability/native/runtime gaps can remain.341 unrelated public row objects, all342 identities/aliases and historical source/evidence are preserved; no fresh all-library acceptance is claimed.

## Remaining work and release

Pokescape remains at the **95.83% weighted score**, not complete compatibility.346 POI obligations remain against2,149:165 unresolved,100 preliminary contextual,75 preliminary no-text and six destinations referencing blank maps. Native warp/scene/script/context/no-text/content validation, ability mechanics0/298, applicable machine/tutor and other parent requirements remain mandatory.

Continue the same ROM's retained standard4/5 and branch/call/context contracts, then actual fallback/secret-base/hidden/background applicability. Every new original/parser use still requires a separately scoped MAIN-reviewed one-use admission. Cleanup is pending for named next-batch baseline/package consumers; no deletion is claimed.

**The requested public release is still pending current-ROM acceptance.** No release/tag/APK/signing/device/emulator/ADB action occurred. Serial installed Gradle gate, two workers/no parallel, observed3 GiB Gradle/requested3 GiB Kotlin and configured512 MiB single test forks were used without observed memory failure. No agents, Workflow, upstream interaction or full-corpus rerun.
