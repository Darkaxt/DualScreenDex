# RC69 Atlas Area Guide publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.69` from commit `e64ac3366e05814a379e36072703198c739ae052`. The separately downloaded signed APK matches the release checksum and provenance, exposes the exact package identity, and is signed by the pinned DualDex release certificate.

## Publication evidence

| Measurement | Verified value |
| --- | --- |
| Release | [`v1.1.0-rc.69`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.69), public prerelease, not a draft |
| Protected workflow | [`33004284158`](https://github.com/Darkaxt/DualScreenDex/actions/runs/33004284158), successful |
| Source | Annotated tag `v1.1.0-rc.69` resolves to `e64ac3366e05814a379e36072703198c739ae052` |
| APK | `DualDex-v1.1.0-rc.69.apk`, 17,990,118 bytes |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.69`, code `1010069` |
| APK SHA-256 | `E576BAE76D6F8093100F759794D6DC97E890CFE7E2C68DAED9E37CB51E4DE9C4` |
| Signer SHA-256 | `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Published assets | 34 assets, including the signed APK, provenance, certificate, checksums, Party Analysis evidence, Area Guide audit, and exact Area Guide compatibility report |

## Workflow gates

- Exact protected tag/source validation passed.
- Release-policy, compatibility-documentation, and Android deployment-safety checks passed.
- The complete web test and production bundle gates passed.
- All Android modules, lint, unsigned release assembly, and package-identity validation passed.
- The isolated signing job reconstructed the protected key, verified the pinned signer, signed and reverified the APK, generated provenance/checksums, and created a new non-replacing release.
- The downloaded APK hash matches both `SHA256SUMS.txt` and `provenance.json`; `aapt` and `apksigner` independently confirm the expected package, numeric version, and sole signer.

## Device boundary

No APK was installed or launched, and no ADB or emulator interaction was performed. Physical AYN Thor validation remains user-owned.
