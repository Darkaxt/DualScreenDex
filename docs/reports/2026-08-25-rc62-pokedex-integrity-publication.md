# RC62 Gen III Pokédex Integrity Publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.62`, and the unauthenticated public APK matches the authenticated release asset and declared provenance.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Empty pre-party integrity | Positively empty live parties bypass heuristic flag scanning; unavailable live party evidence stays unavailable | PASS |
| Persisted recovery | Checksum-valid SaveRAM uses the explicit persisted-evidence path without weakening the live guard | PASS |
| Flag identity | Gen III National save flags translate to internal catalog species IDs independently of regional display numbering | PASS |
| Real-ROM control | Official Emerald verifies Hoenn display number 203 maps to National save flag 1, with 15 seen and 8 caught flags | PASS |
| Local verification | 1,803 Gradle/JUnit tests completed with 0 failures and 0 errors; 190 web tests and the production web build passed | PASS |
| Protected publication | Workflow `32794571643` completed both verification/build and signing/publication jobs successfully | PASS |
| Public identity | APK reports `com.darkaxt.dualdex`, version name `1.1.0-rc.62`, and version code `1010062` | PASS |
| Public integrity | Authenticated and unauthenticated downloads are both 17,785,318 bytes with SHA-256 `4E54925B11C2BFDC93CD7C74AE22B934B4FA8579F64C85476E3A7981F199AA71` | PASS |
| Signing authority | APK Signature Scheme v3 verifies with one signer and certificate SHA-256 `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` | PASS |
| Provenance | Annotated tag `v1.1.0-rc.62`, workflow provenance, and released source resolve to commit `6b48c52f8e04667c0c58e4feec54b00ad6f078a4` | PASS |
| Device boundary | No APK installation, launch, emulator use, or gameplay action was performed | PASS |

## Publication

- Release: <https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.62>
- Protected workflow: <https://github.com/Darkaxt/DualScreenDex/actions/runs/32794571643>
- Asset: `DualDex-v1.1.0-rc.62.apk`

RC61 had already been assigned to an independently published capture-ball expansion, so this correction advanced to RC62 rather than replacing an existing release.
