# RC66 Gen III Live Pokédex Stability Publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.66` from commit `7f2207c02e9f967dc0ba7802472a6746f04a0c1f`, and an unauthenticated public download matches the workflow provenance and checksum manifest.

## Release evidence

| Gate | Result |
| --- | --- |
| Protected verify-and-build job | PASS in 6m54s: release policy, compatibility documents, deployment safety, web tests/build, all Gradle modules, Android lint, release assembly, and unsigned identity |
| Protected sign-and-publish job | PASS in 29s: pinned certificate, APK signature, package identity, provenance, checksums, and non-replacing publication |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.66`, code `1010066` |
| Public APK size | `17,822,182` bytes |
| APK SHA-256 | `1EA18FF5BD704B61B6F4C99461CE8CC1F65452A444395A4A6100F31BE7FEA0AA` |
| APK signature | v3, one signer, certificate SHA-256 `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Workflow provenance | Run `32913290247`, tag `v1.1.0-rc.66`, commit `7f2207c02e9f967dc0ba7802472a6746f04a0c1f` |
| Anonymous public download | Hash matches both `provenance.json` and `SHA256SUMS.txt` |

- Release: <https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.66>
- Workflow: <https://github.com/Darkaxt/DualScreenDex/actions/runs/32913290247>
- Asset: `DualDex-v1.1.0-rc.66.apk`

No APK was installed or launched during publication or verification.
