# Current Release Readiness

This is the canonical reviewer entry point for DualDex release readiness.

## Active marker

- **Stable release:** [`v1.2.1`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.2.1)
- **Current release:** production-signed, publicly published and independently verified on October 4, 2026. The requested scan/release batch is complete; further compatibility work awaits a separate decision.
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

The release workflow binds source lineage, exact generator/report/input digests, current corpus totals, catalog accounting, cache revision, protected tags and the signing environment. It still requires the exact-source hosted packaged-Android acceptance gate before signing. No physical-device or local-emulator validation is claimed.

## Verified public 1.2.1 release

[Protected final signing/publication](https://github.com/Darkaxt/DualScreenDex/actions/runs/37171000662) passed at `f7b75f830269523e430482bc617aa090a4a88ed5`. The public release is neither a draft nor a prerelease. All 68 downloaded assets matched their GitHub digests and complete checksum manifest; 62 evidence assets are byte-identical to validated RC2. Public-asset privacy validation passed.

Static APK inspection confirmed `com.darkaxt.dualdex`, version `1.2.1`, code `1020199`. Cryptographic verification matched the pinned production signer `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA`. The final APK SHA-256 is `7A0DDBB27562E083F3BF10CE229FEDC899B7B3686B6C1F7046E89CC40BB601F2`. [Published provenance](https://github.com/Darkaxt/DualScreenDex/releases/download/v1.2.1/provenance.json) and [checksums](https://github.com/Darkaxt/DualScreenDex/releases/download/v1.2.1/SHA256SUMS.txt) bind the actual artifact. Exact-source ordinary CI and the final workflow's hosted packaged gate passed. No local emulator or physical-device acceptance is claimed.

## Validated 1.2.1 candidate

- Candidate: `v1.2.1-rc.2`, source `d2fedab79b01ed11e80dbc2363d7e31cf5ed417b`.
- [Exact-source CI](https://github.com/Darkaxt/DualScreenDex/actions/runs/37169527972) and [protected signing](https://github.com/Darkaxt/DualScreenDex/actions/runs/37169597497) passed. Both hosted packaged runs passed eight tests with no failures, errors or skips.
- All 68 immutable draft assets matched their GitHub IDs/digests and complete checksum set. Static APK inspection confirmed `com.darkaxt.dualdex`, version `1.2.1-rc.2`, code `1020102`; cryptographic verification matched the pinned production certificate.
- Five exact real-ROM cache-reopen, runtime/API and Chromium controls passed at the candidate source without reading ROM inputs or rerunning the parser. [Acceptance evidence](reports/candidate-promotions/v1.2.1-rc.2.json) and [immutable candidate record](../release/candidate-promotions/v1.2.1-rc.2.json) bind those outcomes to the actual signed APK.
- Hosted screenshots show a System UI nonresponse dialog. They are retained as diagnostic artifacts, not claimed as clean visual acceptance; browser presentation evidence comes from the five independent real-ROM controls. This release uses the authorized passive-catalog route, not a device-validation claim.
- RC1's ordinary CI failed while allocating its synthetic oversized-section fixture, before persistence. RC2 changed only that test fixture and release metadata: a shared 64 KiB string still exceeds the real 128 MiB section ceiling, and the test requires the exact limit exception. Production source and source-bound corpus evidence remain unchanged.

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
