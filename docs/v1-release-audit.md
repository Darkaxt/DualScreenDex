# DualDex 1.0.0 Stage 7 Release Audit

Audit date: 2026-08-10

This record covers the frozen debug candidate used to authorize GitHub-only production signing. It contains no ROM, SaveRAM, raw memory, private path, signing secret, or locally signed production APK.

## Automated and device gates

| Gate | Result |
| --- | --- |
| Complete Gradle run | 91/91 tasks executed; build, lint, and tests passed |
| JVM/Android unit tests | 291 passed; 0 failed, errors, or skipped |
| Browser unit/component tests | 15 files and 44 tests passed |
| Android instrumentation | 3 classes and 3 tests passed explicitly on `emulator-5556` |
| Private mainline-family ROM corpus | 14/14 selected; 14/14 SQLite reopen-equivalent; 0 ambiguous, no-family, errors, or applicable `N/F` cells |
| Live mapper evidence | Three labeled Modern Emerald snapshots each exported 262,144-byte EWRAM and 32,768-byte IWRAM regions; all decoded hashes verified; the sanitized field analysis is committed without raw memory |

The instrumentation classes were `MainActivityInstrumentedTest`, `AndroidCatalogDatabaseInstrumentedTest`, and `MemoryMapperIsolationInstrumentedTest`. No install or instrumentation command addressed the existing `emulator-5554`.

## Small-display audit

The packaged Android loopback UI was inspected at the Thor's 1080 x 1240 display size and at the 406 x 354 CSS reference viewport. Representative Browse, Entry, Stats, Moves, More, move detail, ability detail, Settings, Setup, and disabled Memory Mapper screens were checked at 100% and 135% font scale.

- The document body had no horizontal or vertical overflow.
- `.screen` now clips overflow explicitly; declared content/list regions own scrolling.
- Settings, Setup, and mapper controls remained reachable at the bottom of their scroll regions.
- Species and move detail navigation, including evolution shortcuts, remained usable.
- Production screenshots are stored under `docs/images/dualdex-v1-*.png`.

## Manifest, transport, and privacy audit

- Debug identity: `com.darkaxt.dualdex.debug`, version code 1, version `1.0.0-debug`, minimum API 30, target API 36.
- Production identity remains `com.darkaxt.dualdex` and has no local release-signing configuration.
- Declared functional permissions are Internet, Android All files access, display-over-other-apps, foreground service, and foreground-service special use. All files access is the user-approved primary ROM/config/SaveRAM discovery mode; ROM and SaveRAM paths are read-only, and only the exact public RetroArch config plus its verified recovery sibling are writable. The only additional merged permission is AndroidX's package-scoped, signature-level non-exported receiver permission.
- The sole app service is the non-exported floating companion service. No Accessibility, OCR, media-projection, screenshot, input-injection, or content-control service exists.
- Android backup is disabled for private catalogs, save-derived knowledge, and mapper sessions.
- Cleartext is denied by default and allowed only for `127.0.0.1` by the Android network-security policy.
- The app-owned HTTP listener was observed only at the IPv4-mapped loopback address. The WebView disables file/content access, rejects mixed content, and blocks navigation outside its exact local origin and approved native routes.
- Mapper capture begins disabled after process restart; the frozen default state was Organic, Game theme, Auto density/display targeting, Docked mode, zero mapper snapshots.

## All-files storage audit

- The dedicated AVD indexed 15 supported sources across shared storage without per-console folder grants. Protected `Android/data` and `Android/obb` trees are pruned by the direct indexer.
- Identical Modern Emerald ZIPs in two folders shared one valid SHA-256 and resolved deterministically; matches with different hashes remain ambiguous.
- The direct public config was synchronously patched/read back and reported verified after the required RetroArch restart. A later DualDex restart did not request a redundant RetroArch restart.
- `/storage/emulated/0/RetroArch/saves/mGBA/...srm` matched the active streamed ZIP and published all 12 supported save capabilities. RC7's 8 seen / 4 caught presentation was later rejected by the Thor's in-game 6 seen / 2 owned ground truth and corrected for RC8.
- Revoking broad access made Android restart the debug process. Relaunch degraded to `storageGrant=MISSING`/manual-SAF fallback while preserving the cached catalog and matched save snapshot.

## Dependency, source, and artifact audit

- The runtime dependency graph contains the project modules, Kotlin, AndroidX/Material, Gson, and their ordinary transitive support libraries. Google ML Kit/text recognition is absent; its stale unused version-catalog alias was removed.
- Source scans found no reachable OCR, Accessibility service, screenshot capture, MediaProjection, cheat, input injection, Cocoon dependency, or core-memory write API. The sole `WRITE_CORE_MEMORY` text is a negative architecture assertion in a test.
- The npm tree declared 215 MIT, 8 Apache-2.0, 9 BSD-2-Clause, 3 BSD-3-Clause, 9 ISC, 1 MIT-0, and 1 CC-BY-4.0 package, with zero undeclared licenses. The CC-BY package is build-time `caniuse-lite`; it is not a redistributed game-data source.
- Tracked files and Git history contained no `.gb`, `.gbc`, `.gba`, `.sav`, `.srm`, save state, database, memory dump, keystore, or private-key artifact.
- The APK contained no ROM/save/database/dump/keystore file. Its production web bundle contained only `index.html`, one JavaScript file, and one stylesheet; development simulator strings were absent.

## Frozen debug artifact

- File: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 14,106,117 bytes
- SHA-256: `95039828897A0139C3D442EB3131B1393F69466180E164E670E9ED45A47CB7FA`
- Deployment: installed only on the dedicated `DualDex_RA_API35` AVD (`emulator-5556`)
- Publication: local debug artifact only; not a GitHub release

Production signing remains exclusively owned by the protected GitHub `release-signing` environment. The debug hash above is evidence, not a release payload.

## GitHub-signed RC7 artifact

- Workflow: successful protected run [`31378249357`](https://github.com/Darkaxt/DualScreenDex/actions/runs/31378249357)
- Source: tag `v1.0.0-rc.7`, peeled to merged commit `8abee2e89e11ca0b98f43ac034de01594a9dd5d2`
- Release: public prerelease [`v1.0.0-rc.7`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.0.0-rc.7)
- File: `DualDex-v1.0.0-rc.7.apk`, 11,496,926 bytes
- Identity: package `com.darkaxt.dualdex`, version `1.0.0-rc.7`, code `1000007`
- SHA-256: `F26990AF356FB9B93CE42C156125DC5CEFCA8F0C1C3CEA79553D1FB7301C54FF`
- Signer SHA-256: `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA`

All six assets were downloaded anonymously from the public release. Every checksum-manifest row, the provenance tag/commit/run, APK identity, and pinned signer were independently verified. The exact public APK passed the storage/catalog discovery gates on `emulator-5556` and was then installed on the physical Thor `bfa98654`; no debug APK reached the Thor. The Thor cross-check exposed the Gen III expanded Pokédex-layout defect recorded as `V1-004`, so RC7 is superseded rather than accepted.
