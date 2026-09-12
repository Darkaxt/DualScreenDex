# Current Release Readiness

This is the canonical reviewer entry point for DualDex release readiness.

## Active marker

- **Next protected candidate:** `v1.2.0-rc.3`
- **Release notes:** [`release/RELEASE_NOTES_1.2.0-rc.3.md`](../release/RELEASE_NOTES_1.2.0-rc.3.md)
- **Machine-readable readiness marker:** [`release/v1-ready.json`](../release/v1-ready.json)
- **Current state:** project-wide QA Stages 7–8 remain closed with zero blockers and zero referrals; the six-stage localization system is complete with zero system blockers, and 1.2.0 RC3 supersedes the failed RC1 and RC2 candidates with the Web and desktop-server release gates aligned to the localized presentation contract.

## Final QA evidence

The canonical corpus remains 333 scanner-eligible inputs from the audited 334-file physical inventory, with one known spin-off outside scanner scope. The source-bound localization corpus selected 276 catalogs: 274 persisted and reopened in the consolidated run, and the two rows that exhausted the bounded six-GiB process heap each passed an exact source-bound, one-input recovery with matching logical digests. Combined evidence accounts for all 276 selected rows with zero parser, catalog, or unresolved persistence errors.

- [Canonical corpus contract](../release/canonical-corpus.json)
- [Release evidence manifest](../release/compatibility-evidence.json)
- [Localization corpus summary](reports/localization/stage-06-corpus-evidence.json)
- [Localization Stage 6 closure](reports/localization/stage-06-closure.md)
- [Full translation system closure](reports/localization/final-translation-system-closure.md)
- [QA Stage 7 closure](reports/qa-hardening/stage-07-closure.md)
- [QA Stage 8 integrated closure](reports/qa-hardening/stage-08-closure.md)

The release workflow validates source lineage, generator and raw-report digests, the canonical denominator/multiset digest, both exact bounded recoveries, the cache revision decision, QA and localization closure state, protected tag rules, and both protected GitHub environments before signing can begin. RC3 is a new non-replacing 1.2.0 candidate; it does not alter the already published stable `v1.1.0` or move the immutable failed `v1.2.0-rc.1` and `v1.2.0-rc.2` tags.

## Historical records

- **RC9 / v1.0 requirement matrix:** [`docs/archive/v1-requirement-matrix-rc9.md`](archive/v1-requirement-matrix-rc9.md)
- **Historical delivery ledger:** [`docs/v1-delivery-ledger.md`](v1-delivery-ledger.md)
- **Historical v1 release audit:** [`docs/v1-release-audit.md`](v1-release-audit.md)

Historical files describe the release state at the time they were written. They are evidence, not the current release decision.
