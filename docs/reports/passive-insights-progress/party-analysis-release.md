# RC68 Party Analysis publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.68` from commit `851fab2b63b76f3985cec0754781161eaf7ae4c7`. The separately downloaded signed APK matches the release checksum and provenance, exposes the exact package identity, and is signed by the pinned DualDex release certificate.

## Publication evidence

| Measurement | Verified value |
| --- | --- |
| Release | [`v1.1.0-rc.68`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.68), public prerelease, not a draft |
| Protected workflow | [`32995084127`](https://github.com/Darkaxt/DualScreenDex/actions/runs/32995084127), successful |
| Source | Annotated tag `v1.1.0-rc.68` resolves to `851fab2b63b76f3985cec0754781161eaf7ae4c7` |
| APK | `DualDex-v1.1.0-rc.68.apk`, 17,928,678 bytes |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.68`, code `1010068` |
| APK SHA-256 | `E1B85AAC54C7F0565743D4BA0CC49185436D446594BB682CD1840F415D464CDC` |
| Signer SHA-256 | `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Published assets | 32 assets, including the signed APK, provenance, certificate, checksums, Party Analysis audit, and exact compatibility report |

## Workflow gates

- Exact protected tag/source validation passed.
- Release-policy, compatibility-documentation, and Android deployment-safety checks passed.
- The complete web test and production bundle gates passed.
- All Android modules, lint, unsigned release assembly, and package-identity validation passed.
- The isolated signing job reconstructed the protected key, verified the pinned signer, signed and reverified the APK, generated provenance/checksums, and created a new non-replacing release.
- The downloaded APK hash matches both `SHA256SUMS.txt` and `provenance.json`; `aapt` and `apksigner` independently confirm the expected package, numeric version, and sole signer.

## Device boundary

No APK was installed or launched, and no ADB or emulator interaction was performed. Physical AYN Thor validation remains user-owned.
