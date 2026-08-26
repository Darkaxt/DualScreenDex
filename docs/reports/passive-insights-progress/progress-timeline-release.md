# RC70 Trainer Progress and Save Timeline publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.70` from commit `128e8041b67d7d64f72c26d56fcb9526dbd7bd31`. The separately downloaded signed APK matches the release checksum and provenance, exposes the exact package identity, and is signed by the pinned DualDex release certificate.

## Publication evidence

| Measurement | Verified value |
| --- | --- |
| Release | [`v1.1.0-rc.70`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.70), public prerelease, not a draft |
| Protected workflow | [`33020578771`](https://github.com/Darkaxt/DualScreenDex/actions/runs/33020578771), successful |
| Source | Annotated tag `v1.1.0-rc.70` resolves to `128e8041b67d7d64f72c26d56fcb9526dbd7bd31`, identical to publication-time `master` |
| APK | `DualDex-v1.1.0-rc.70.apk`, 18,276,924 bytes |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.70`, code `1010070` |
| APK SHA-256 | `AD7D2BE150F4C906A1ED39BDA20AEA0E31E6151B29A911CB19432CE3DE946330` |
| Signer SHA-256 | `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Published assets | 36 assets, including the signed APK, provenance, certificate, checksums, Party Analysis and Area Guide evidence, and exact Progress/Timeline compatibility and audit reports |

## Workflow gates

- Exact protected tag/source validation passed after reconciling the upstream unified-species parser commit.
- Release-policy, compatibility-documentation, and Android deployment-safety checks passed.
- The complete web test and production bundle gates passed.
- All Android modules, lint, unsigned release assembly, and package-identity validation passed.
- The isolated signing job reconstructed the protected key, verified the pinned signer, signed and reverified the APK, generated provenance/checksums, and created a new non-replacing release.
- The downloaded APK hash matches both `SHA256SUMS.txt` and `provenance.json`; `aapt` and `apksigner` independently confirm the expected package, numeric version, and sole signer.

## Device boundary

No APK was installed or launched, and no ADB or emulator interaction was performed. Physical AYN Thor validation remains user-owned.
