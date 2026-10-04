# Current Release Readiness

This is the canonical reviewer entry point for DualDex release readiness.

## Active marker

- **Stable release:** `v1.2.0`
- **Next release:** `v1.2.1`; candidate preparation is in progress, with signing and promotion still pending.
- **Release notes:** [`release/RELEASE_NOTES_1.2.1.md`](../release/RELEASE_NOTES_1.2.1.md)
- **Machine-readable readiness marker:** [`release/v1-ready.json`](../release/v1-ready.json)
- **Current state:** project-wide QA Stages 7–8 remain closed with zero blockers and zero referrals; the six-stage localization system retains its closed baseline. The current compiled Gen I/II catalog batch has passed one full source-bound evaluation of all 342 distinct current-library identities, with zero parser, catalog, persistence or decoded-reference errors and no regressions against retained evidence.

## Current source-bound corpus evidence

The October 3 library snapshot contains **342 inputs and 342 distinct parser identities**. All retained source files and normalized identities were rehashed. The frozen parser/catalog executable at `f7214d1f644aa97f3bdfacddd3de8dcefdede9f5` selected 300 catalogs; every selected catalog persisted and reopened exactly with an unchanged logical digest. Two inputs remain ambiguous and 40 have no mainline-family match. Language authority is resolved for 285 catalogs and explicitly unknown for 15; no multilingual build is claimed.

- [Current corpus contract](../release/current-library-corpus.json)
- [Release evidence manifest](../release/compatibility-evidence.json)
- [Full342 release summary](reports/corpus/2026-10-03-full342-evidence.json)
- [Full342 execution receipt](reports/corpus/2026-10-03-full342-execution.json)
- [Full342 comparison and persistence gate](reports/corpus/2026-10-03-full342-verification.md)
- [Current compatibility matrix](reports/corpus/2026-10-03-library-compatibility.md)
- [QA Stage 7 closure](reports/qa-hardening/stage-07-closure.md)
- [QA Stage 8 integrated closure](reports/qa-hardening/stage-08-closure.md)

Parser cache revision **83** forces revision-82 catalogs to rebuild; SQL schema remains 2. The seeded prior-revision rejection/rebuild test passed. Optional capabilities remain bounded and fail closed; family selection does not mean every optional module is complete.

The release workflow binds source lineage, exact generator/report/input digests, current corpus totals, catalog accounting, cache revision, protected tags and the signing environment. It still requires the exact-source hosted packaged-Android acceptance gate before signing. Passive-catalog promotion requires five exact real-ROM cache/runtime/API/browser controls, successful exact-source CI, the pinned signer and the immutable signed candidate asset set. No physical-device or local-emulator validation is claimed.

## Historical records

The historical corpus remains 333 scanner-eligible inputs from the audited 334-file physical inventory, comprising 331 distinct identities. That contract and its localization evidence remain unchanged. The current summary explicitly identifies the earlier localization packaged-test proof's historical source; that retained proof is not relabelled as current candidate acceptance.

- [Historical canonical corpus](../release/canonical-corpus.json)
- [Historical localization corpus summary](reports/localization/stage-06-corpus-evidence.json)
- [Localization Stage 6 closure](reports/localization/stage-06-closure.md)
- [Full translation system closure](reports/localization/final-translation-system-closure.md)
- **RC9 / v1.0 requirement matrix:** [`docs/archive/v1-requirement-matrix-rc9.md`](archive/v1-requirement-matrix-rc9.md)
- **Historical delivery ledger:** [`docs/v1-delivery-ledger.md`](v1-delivery-ledger.md)
- **Historical v1 release audit:** [`docs/v1-release-audit.md`](v1-release-audit.md)

Historical files describe their own release checkpoints, not the current release decision.
