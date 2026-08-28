# Current Release Readiness

This is the canonical reviewer entry point for DualDex release readiness.

## Active marker

- **Latest published repository release marker:** `v1.1.0-rc.77`
- **Release notes:** [`release/RELEASE_NOTES_1.1.0-rc.77.md`](../release/RELEASE_NOTES_1.1.0-rc.77.md)
- **Machine-readable readiness marker:** [`release/v1-ready.json`](../release/v1-ready.json)
- **Current state:** blocked while project-wide QA blockers and referrals are remediated and Stages 7–8 remain open.

## Required final evidence

No final corpus or zero-gap closure evidence is currently tracked. The next release remains blocked until one stabilized source commit produces the canonical 334-input execution receipt and summary, every materialized catalog persists and reopens, and machine-readable Stage 7 and Stage 8 closure records both report zero blockers and zero referrals.

The release workflow requires the future `release/canonical-corpus.json`, `release/compatibility-evidence.json`, Stage 7 execution/summary, and Stage 7/8 closure records. It validates their source lineage, generator and raw-report digests, canonical denominator/digest, cache decision, exact closure state, protected tag rules, and both protected GitHub environments before signing can begin.

## Historical records

- **RC9 / v1.0 requirement matrix:** [`docs/archive/v1-requirement-matrix-rc9.md`](archive/v1-requirement-matrix-rc9.md)
- **Historical delivery ledger:** [`docs/v1-delivery-ledger.md`](v1-delivery-ledger.md)
- **Historical v1 release audit:** [`docs/v1-release-audit.md`](v1-release-audit.md)

Historical files describe the release state at the time they were written. They are evidence, not the current release decision.
