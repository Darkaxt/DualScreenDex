# Current Release Readiness

This is the canonical reviewer entry point for DualDex release readiness.

## Active marker

- **Final signed candidate marker:** `v1.1.0-rc.86`
- **Release notes:** [`release/RELEASE_NOTES_1.1.0-rc.86.md`](../release/RELEASE_NOTES_1.1.0-rc.86.md)
- **Machine-readable readiness marker:** [`release/v1-ready.json`](../release/v1-ready.json)
- **Current state:** project-wide QA Stages 7–8 are closed with zero blockers and zero referrals; RC86 completes the Thor lower-display usability pass and is the exact candidate source for protected signing, promotion, and stable `v1.1.0` authorization.

## Final QA evidence

The final corpus evaluated all 333 scanner-eligible mainline/hack inputs in the audited 334-file physical inventory; one known spin-off is intentionally outside scanner scope. The canonical schema-2 contract binds the 333-entry multiset, 331 unique byte identities, duplicate entries, and aggregate digest. All 278 selected catalogs persisted and reopened, and parser, compatibility, catalog, and persistence error counts are zero.

- [Canonical corpus contract](../release/canonical-corpus.json)
- [Release evidence manifest](../release/compatibility-evidence.json)
- [Stage 7 corpus summary](reports/qa-hardening/stage-07-corpus-evidence.md)
- [Stage 7 closure](reports/qa-hardening/stage-07-closure.md)
- [Stage 8 integrated closure](reports/qa-hardening/stage-08-closure.md)

The release workflow validates source lineage, generator and raw-report digests, the canonical denominator/multiset digest, cache decision, exact closure state, protected tag rules, and both protected GitHub environments before signing can begin. Stable `v1.1.0` may be authorized only from the exact promoted RC86 source, APK digest, signer, provenance, and immutable release asset set.

## Historical records

- **RC9 / v1.0 requirement matrix:** [`docs/archive/v1-requirement-matrix-rc9.md`](archive/v1-requirement-matrix-rc9.md)
- **Historical delivery ledger:** [`docs/v1-delivery-ledger.md`](v1-delivery-ledger.md)
- **Historical v1 release audit:** [`docs/v1-release-audit.md`](v1-release-audit.md)

Historical files describe the release state at the time they were written. They are evidence, not the current release decision.
