# RC63 Unified Game State Publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.63`, and the unauthenticated public APK is byte-identical to the authenticated release asset and declared provenance.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Single transient authority | One `UnifiedGameStateDecoder` subscription publishes one immutable `ResolvedGameStateChanged` snapshot for trainer, Pokédex, party, battle, area, position, clock, bag, flags, and recovery provenance | PASS |
| Pokédex live replacement | Authoritative live empty/partial state replaces recovery instead of unioning stale app-side knowledge; one decoded bit maps to one base species | PASS |
| Recovery isolation | Checkpoints are written only after a validated same-playthrough `SaveObservationKind.CHANGED`; live, initial, unchanged, and recovery reads do not write checkpoints | PASS |
| Cross-feature consistency | Party, battle, overworld, organic knowledge, trainer card, and the server simulator consume the same atomic state projection | PASS |
| Structural route audit | Production modules contain no legacy section publishers or battle start/update/end routes that can bypass the unified authority | PASS |
| Local verification | 1,804 Gradle/JUnit tests completed with 0 failures and 0 errors; 191 web tests, production web build, 18 release-policy tests, Android deployment-safety tests, lint, and release assembly passed | PASS |
| Compatibility evidence | The published nine-field transient matrix contains 14 controls and 90/126 available field groups (71.43%), with zero retained raw bytes | PASS |
| Protected publication | Workflow `32837284886` completed verification/build and signing/publication successfully | PASS |
| Public identity | APK reports `com.darkaxt.dualdex`, version name `1.1.0-rc.63`, and version code `1010063` | PASS |
| Public integrity | Authenticated and unauthenticated downloads are both 17,793,510 bytes with SHA-256 `866BE8B4382E714D46CA6C306574CCF71F9AD779B22C2C78E3389A174B952E79` | PASS |
| Signing authority | APK Signature Scheme v3 verifies with one signer and certificate SHA-256 `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` | PASS |
| Provenance | Annotated tag `v1.1.0-rc.63`, workflow provenance, and released source resolve to commit `3ae07ef634a5b42b2f65be67259adc17322ba193` | PASS |
| Device boundary | No APK installation, launch, emulator use, ADB use, or gameplay action was performed | PASS |

## Publication

- Release: <https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.63>
- Protected workflow: <https://github.com/Darkaxt/DualScreenDex/actions/runs/32837284886>
- Asset: `DualDex-v1.1.0-rc.63.apk`
- Published audit: `dualdex-unified-game-state-final-audit.md`
- Published matrix: `dualdex-unified-game-state-compatibility.json`

The release tag remains immutable at the verified implementation and release-document commit. This publication report is a post-release evidence record on `master`; it does not move or replace the tag.
