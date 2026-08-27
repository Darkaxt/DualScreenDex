# Portable Challenge Expansion Release Evidence

## Candidate identity

- Tag: `v1.1.0-rc.76`
- Source commit: `5edfab5c3b35e6bc39712c5888700a4fb76e205e`
- Protected workflow: [run 33102469802](https://github.com/Darkaxt/DualScreenDex/actions/runs/33102469802)
- Release state: signed draft prerelease; physical user acceptance remains pending.
- Android application ID: `com.darkaxt.dualdex`
- Version name: `1.1.0-rc.76`
- Version code: `1010076`

RC75 retains its immutable source tag but has no GitHub release. Its installed-package gate exposed the missing Welcome failure message before signing. RC76 contains the repair and uses the next numeric identity without moving or replacing RC75.

## Protected gates

- Exact tag/source binding: passed.
- Release policy, compatibility documentation, and deployment-safety checks: passed.
- Web production suite and build: passed.
- Complete unsigned JVM, Android unit, lint, and release assembly: passed.
- Installed APK and WebView acceptance: passed, including the guide-failure message and reachable retry action.
- Protected signing and independent in-workflow signature verification: passed.
- Non-replacing draft release creation: passed.

## Independent artifact verification

- APK: `DualDex-v1.1.0-rc.76.apk`
- APK SHA-256: `90843C3E7CB8C1681D52F3D786660A9F24075BBC0FF14F1B4C54D8739B2ADCAE`
- Signing certificate SHA-256: `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA`
- Provenance schema: `1`
- Provenance workflow run: `33102469802`
- Checksum manifest: all 42 entries verified across 43 downloaded release files.
- Required Portable Challenge compatibility JSON and audit Markdown: present and checksum-verified.
- Installation or launch during independent validation: none.

The candidate remains a draft. No public promotion is claimed by this report.
