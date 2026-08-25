# RC65 State Trace and Pokédex Controls Publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.65` from commit `b1c9855ab26df998107c5a6733dbac8455279f1c`, and an unauthenticated public download matches the workflow provenance and checksum manifest.

## Release evidence

| Gate | Result |
| --- | --- |
| Protected verify-and-build job | PASS in 7m25s: release policy, compatibility documents, deployment safety, web tests/build, all Gradle modules, Android lint, release assembly, and unsigned identity |
| Protected sign-and-publish job | PASS in 27s: pinned certificate, APK signature, package identity, provenance, checksums, and non-replacing publication |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.65`, code `1010065` |
| Public APK size | `17,813,990` bytes |
| APK SHA-256 | `C3722AF7C429CC54839C09A85BF2AF240B1AEC62BB8404ADAF4F78974B0379E7` |
| APK signature | v3, one signer, certificate SHA-256 `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Workflow provenance | Run `32903642279`, tag `v1.1.0-rc.65`, commit `b1c9855ab26df998107c5a6733dbac8455279f1c` |
| Anonymous public download | Hash matches both `provenance.json` and `SHA256SUMS.txt` |

- Release: <https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.65>
- Workflow: <https://github.com/Darkaxt/DualScreenDex/actions/runs/32903642279>
- Asset: `DualDex-v1.1.0-rc.65.apk`

No APK was installed or launched during publication or verification.
