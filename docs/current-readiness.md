# Current Release Readiness

This is the canonical reviewer entry point for DualDex release readiness.

## Active marker

- **Latest repository release marker:** `v1.1.0-rc.77`
- **Release notes:** [`release/RELEASE_NOTES_1.1.0-rc.77.md`](../release/RELEASE_NOTES_1.1.0-rc.77.md)
- **Machine-readable readiness marker:** [`release/v1-ready.json`](../release/v1-ready.json)
- **Current QA work:** project-wide hardening Stages 7–8; this work does not create or publish another candidate by itself.

## Current evidence

- **Source-bound compatibility manifest:** [`release/compatibility-evidence.json`](../release/compatibility-evidence.json)
- **Fresh corpus summary:** [`docs/reports/qa-hardening/stage-07-corpus-evidence.md`](reports/qa-hardening/stage-07-corpus-evidence.md)
- **QA closure reports:** [`docs/reports/qa-hardening/`](reports/qa-hardening/)
- **Release signing certificate and policy:** [`signing/README.md`](../signing/README.md)

A release workflow must validate the compatibility manifest against its exact source revision, audit the active `v1.*` tag ruleset and `release-signing` environment, and complete the protected signing job before publication.

## Historical records

- **RC9 / v1.0 requirement matrix:** [`docs/archive/v1-requirement-matrix-rc9.md`](archive/v1-requirement-matrix-rc9.md)
- **Historical delivery ledger:** [`docs/v1-delivery-ledger.md`](v1-delivery-ledger.md)
- **Historical v1 release audit:** [`docs/v1-release-audit.md`](v1-release-audit.md)

Historical files describe the release state at the time they were written. They are evidence, not the current release decision.
