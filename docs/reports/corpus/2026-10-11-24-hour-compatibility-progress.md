# DualDex compatibility progress — October11,2026

**Report window:** October10,21:29 UTC through October11,21:29 UTC (24 hours). **Parent goal and current ROM: IN_PROGRESS.** This updates the earlier13:42-window report; its historical version remains in Git. Focused/composite evidence only, not a new full-corpus evaluation or release.

## ROMs worked on during this window

**Pokescape v1.0.4 only.** No other ROM was a fresh implementation target or real regression input in this window; external-ROM host controls were guarded/skipped.

- Accepted the earlier native blank-name implementation: applicable Local482/482 and encounter201/201, while retaining two native-blank maps and one encounter structurally. Fresh acceptance is not a claim that this older source was implemented in the window.
- Implemented native Emerald braille/ordinary-spacing correction at `28d123e6`:43 changed direct POIs, **34 recovered and nine corrected**. POI1,769/2,149→1,803/2,149; Area Guide content POIs666→700. [Acceptance](2026-10-11-pokescape-native-braille-name-acceptance.md) records the actual unpublished parser103 package provenance, not clean-tree equivalence to the later implementation commit.
- Implemented generic all-path invariant first-headline recovery at `8f072dd6`: **21 more recovered POIs across19 scripts**. POI1,803/2,149→**1,824/2,149**; content POIs700→**721**. Every bounded branch independently reaches the same static first line; no arbitrary branch selection or weakened denominator.
- Latest guarded host integration:2,487 executed/244 external-input skips/zero failures or errors; all16 new tests execute. Exact clean published-source package, separate native21 event/source/headline joins,18 SQLite sections/integrity/reopen,95,087 references and complete parser97/96/map95/item94/ability93 reversals pass. [Acceptance](2026-10-11-pokescape-invariant-headline-acceptance.md).

**Window total:55 recovered POI labels plus nine corrected labels**, and applicable name closure. This is concrete field progress despite the unchanged weighted score. Broad host verification was reserved for functional integration checkpoints; no full parser corpus was rerun for the retained helpers. Failed historical runs/preflights remain preserved and are not relabeled as acceptance.

## Current overall progress

| Measure | Current composite observation |
|---|---:|
| Retained distinct ROM identities | 342 |
| Selected/materialized catalogs | 301 (88.01% of identities) |
| Persisted and exactly reopened catalogs | 301 |
| Ambiguous family | 2 |
| No family match | 39 |
| Recorded parser/catalog/persistence/reference errors | 0 |
| Resolved language manifests | 286 |
| Unknown language manifests | 15 |
| Weighted-score100% rows | 28 |
| Mean weighted score across301 selected rows | 88.55% |

**Neither301 recognized catalogs nor28 weighted-score100% rows is a full-compatibility count.** Optional/applicability/native/runtime gaps remain.341 unrelated row objects, all342 identities/aliases and historical evidence are preserved, not freshly reevaluated.

## Remaining work and release

Pokescape has **325 remaining POI obligations** against2,149:144 unresolved,100 preliminary contextual,75 preliminary no-text and six blank-map destinations. These roles are not applicability authority and cannot lower the denominator. Native fallback/secret-base/hidden/background, warp/scene/context/content/liveness validation, mechanics0/298, applicable machine/tutor and other parent requirements remain mandatory.

The **95.83% weighted score is not whole-ROM compatibility**. It stays unchanged because the newly verified text detail does not add another weighted top-level feature. Continue the same ROM's coherent remaining contract; do not stop at this field checkpoint.

**The requested public release is still pending current-ROM acceptance.** No release/tag/APK/signing/device/emulator/ADB action occurred. Source and accepted proof are preserved; cleanup is pending for specific baseline/current SQLite and package consumers, not claimed complete. The installed shared Gradle gate used two workers/no parallel, observed3 GiB Gradle/requested3 GiB Kotlin and configured512 MiB single fail-fast test forks without observed memory failure. No agents, Workflow, upstream interaction or fresh full-corpus run.
