# Current Release Readiness

This is the canonical reviewer entry point for DualDex release readiness.

## Active marker

- **Latest public stable:** [`v1.2.3`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.2.3), published October 18, 2026.
- **Prepared next release:** `v1.2.3` preparation is closed by the verified stable publication below. The requested separate Unbound follow-up is now active; no newer release is prepared yet.
- **Machine-readable readiness marker:** [`release/v1-ready.json`](../release/v1-ready.json), source-bound build/signing readiness, not stable-promotion acceptance.
- **Current state:** **stablev1.2.3 is public and independently verified.** All68 downloaded assets,67 checksum entries, privacy checks and APK identity/signer/provenance pass. The final protected workflow passes all three jobs; its source-bound hosted Android JUnit has11 tests/zero failures/errors/skips. Physical validation was explicitly waived for this current release only, not claimed passed. Current-release cleanup removed568 exact superseded outputs/2,293,607,746 logical bytes, preserving all301 final catalogs, signed deliverables and private proof. Separate Unbound work remains in progress.
- **Current qualification:** [October18 release reconciliation](reports/corpus/2026-10-18-release-qualification.md).

## Verified public 1.2.3 release

[Protected final signing/publication39125119550](https://github.com/Darkaxt/DualScreenDex/actions/runs/39125119550) succeeded at `f6429bc82cf5f9d0ed6e3bf5cca42227aad60eb1`; releaseID416426695 is neither draft nor prerelease. All68 downloaded assets match GitHub digests and the complete checksum set, with62 evidence assets byte-identical to RC4. Package `com.darkaxt.dualdex`, version `1.2.3`, code `1020399` and the pinned production signer are independently verified. APK SHA256: `12520594C734122F1D0B86D53EFBB2885EE2C12E3A2A26EE7E19C13E9BA9E532`. [Provenance](https://github.com/Darkaxt/DualScreenDex/releases/download/v1.2.3/provenance.json) SHA256: `CCEA1E4E41EE461ADD42B83C0B1F1FB6E8E745F165BD1E80D60EF7C8465E7285`.

[Protected candidate promotion39124872680](https://github.com/Darkaxt/DualScreenDex/actions/runs/39124872680) published RC4 without replacing its release or any of its68 assets. The committed exact-candidate user waiver records the physical-check exception honestly; final product source is unchanged from RC4. No physical device or signed-APK execution pass is claimed. The separate requested Unbound follow-up retains its own acceptance requirements.

## Current source-bound corpus evidence

The retained library has **342 distinct identities**, canonical digest `733e36785fa9a5b71bc4121343a6b6d2cbd27e43e08b3fddc2e2870bac1a0561`. One fresh complete execution binds frozen source `36d6c2d4ee690e9069cc0af6d1ddb48f9ad4fc89` and its exact 13-jar runtime. All originals passed both hash/stat checks unchanged. Routing is **301 selected / 2 ambiguous / 39 unmatched**, with zero parser/catalog/persistence errors. All 301 persisted catalogs, 5,403 sections and 10,296,409 references are independently audited and reconciled; no new production regression remains open. Language manifests are **286 resolved / 15 explicitly unknown**, never backfilled from shared text.

- [Current corpus contract](../release/current-library-corpus.json)
- [Release evidence manifest](../release/compatibility-evidence.json)
- [Full342 source-bound localization summary](reports/corpus/2026-10-18-full342-localization-evidence.json)
- [Unmodified full342 execution receipt](reports/corpus/2026-10-18-full342-execution.json)
- [Initial aggregate summary, retained unchanged](reports/corpus/2026-10-18-full342-evidence.json)
- [Current release qualification and limits](reports/corpus/2026-10-18-release-qualification.md)
- [Exact-source hosted Android acceptance](https://github.com/Darkaxt/DualScreenDex/actions/runs/39115516225): all **11 tests** pass with zero failures/errors/skips; four required screenshots inspected. The original eight-case coverage is preserved and three newer guide-isolation/picker-delivery cases also pass. A disposable API35 AOSP runner emulator was used; no physical or shared local device was touched.

Parser cache revision **139** rejects stable v1.2.2 revision-83 caches; SQLite schema remains 2. The actual seeded83 rejection/miss/rewrite/exact-reopen regression passed. Partial compatibility, unsupported modules and fourteen Unbound budget-skipped maps remain explicit; family selection is not100% support. The separate requested Unbound follow-up release is now the active stage.

Historical Stage7/8 infrastructure closures retain their original source attribution. Current hosted package/localization proof is independently source-bound above, not relabeled historical evidence. Production signing remains exclusively in the protected GitHub environment, and final promotion still requires the matching verified signed candidate and actual acceptance evidence.

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
