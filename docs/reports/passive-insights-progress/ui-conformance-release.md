# Cross-feature UI Conformance Release Evidence

## Candidate identity

- Tag: `v1.1.0-rc.77`
- Source commit: `8076b5e9f1901dcd085b1c6a82eac38f13ff3b27`
- Protected workflow: [run 33118408017](https://github.com/Darkaxt/DualScreenDex/actions/runs/33118408017)
- Release state: signed draft prerelease; physical user acceptance remains pending.
- Android application ID: `com.darkaxt.dualdex`
- Version name: `1.1.0-rc.77`
- Version code: `1010077`

The immutable candidate source contains the complete Stage 7 implementation, measured evidence, suite audit, QA-hardening convergence report, release metadata, and release notes. This post-release evidence record is committed afterward on `master`; it does not move or replace the tag.

## Protected gates

- Exact protected tag/source binding: passed.
- Release policy, compatibility documentation, Android deployment-safety, and Stage 7 evidence validation: passed.
- Web production suite and build: passed.
- Complete JVM, Android unit, debug lint, release lint, and unsigned release assembly: passed.
- Installed APK and WebView managed-device acceptance: passed.
- Protected signing and independent in-workflow package/signature verification: passed.
- Non-replacing draft prerelease creation: passed.

## Stage 7 evidence

- Registered routes: `28`
- Required themes: `9`
- Font scales: `3`
- Matrix rows: `756/756`
- Matrix blockers: `0`
- Matrix errors: `0`
- Real-ROM theme plus conformance Playwright gate: `30/30`
- Browser production suite: `30` files and `231` tests
- Feature, UI, and release-policy validators: `49/49`
- Complete Android/Gradle gate: `103` actionable tasks passed on the authoritative QA-landed `master` history.

## Independent artifact verification

- APK: `DualDex-v1.1.0-rc.77.apk`
- APK SHA-256: `55043273BDE10411FA36887132373EFE1E1F39DF5C63CD76143E79FFF3758DC7`
- Signing certificate SHA-256: `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA`
- Provenance schema: `1`
- Provenance workflow run: `33118408017`
- Package identity: `com.darkaxt.dualdex`, `1.1.0-rc.77`, version code `1010077`
- Signature verification: APK Signature Scheme v3 passed.
- Checksum manifest: all `51` entries verified across `52` downloaded release files.
- Stage 7 route, font, computed-style, screenshot-manifest, summary, audit, and QA-convergence assets: present and checksum-verified.
- Installation, launch, or physical console access during independent validation: none.

The candidate remains a draft. No public promotion or physical user acceptance is claimed by this report.
