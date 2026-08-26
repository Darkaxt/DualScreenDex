# RC67 Height Comparison publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.67` from commit `b9a650af587d137f905138592eaa9f2dd3d996ba`, and an unauthenticated public download matches every published checksum and GitHub asset digest.

## Publication evidence

| Measurement | Verified value |
| --- | --- |
| Release | [`v1.1.0-rc.67`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.67), public prerelease, not a draft |
| Protected workflow | [`32982975585`](https://github.com/Darkaxt/DualScreenDex/actions/runs/32982975585), successful |
| Source | Annotated tag `v1.1.0-rc.67` resolves to `b9a650af587d137f905138592eaa9f2dd3d996ba` |
| APK | `DualDex-v1.1.0-rc.67.apk`, 17,854,950 bytes |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.67`, code `1010067` |
| APK SHA-256 | `5BF19A22D7E48AC54E4318B2F56241C0CF754D19328EC970BE291E536946B655` |
| Signer SHA-256 | `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Published assets | 30/30 GitHub asset digests match; all 29 `SHA256SUMS.txt` entries match |

## Workflow gates

- Exact protected tag/source validation passed.
- Release-policy, compatibility-documentation, and Android deployment-safety checks passed.
- The complete web test and production bundle gates passed.
- All Android modules, lint, unsigned release assembly, and package-identity validation passed.
- The isolated signing job reconstructed the protected key, verified the pinned signer, signed and reverified the APK, generated provenance/checksums, and created a new non-replacing release.

## Device boundary

No APK was installed or launched, and no ADB or emulator interaction was performed. Physical AYN Thor validation remains user-owned.
