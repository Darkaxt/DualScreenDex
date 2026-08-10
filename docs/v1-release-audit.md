# DualDex 1.0.0 Stage 7 Release Audit

Audit date: 2026-08-10

This record covers the frozen debug candidate used to authorize GitHub-only production signing. It contains no ROM, SaveRAM, raw memory, private path, signing secret, or locally signed production APK.

## Automated and device gates

| Gate | Result |
| --- | --- |
| Complete Gradle run | 91/91 tasks executed; build, lint, and tests passed |
| JVM/Android unit tests | 275 passed; 0 failed, errors, or skipped |
| Browser unit/component tests | 15 files and 42 tests passed |
| Android instrumentation | 3 classes and 3 tests passed explicitly on `emulator-5556` |
| Private mainline-family ROM corpus | 14/14 selected; 14/14 SQLite reopen-equivalent; 0 ambiguous, no-family, errors, or applicable `N/F` cells |
| Live mapper evidence | Current nightly RetroArch + mGBA exported 262,144-byte EWRAM and 32,768-byte IWRAM regions; both decoded hashes verified; raw evidence then removed |

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
- Declared functional permissions are Internet, display-over-other-apps, foreground service, and foreground-service special use. The only additional merged permission is AndroidX's package-scoped, signature-level non-exported receiver permission.
- The sole app service is the non-exported floating companion service. No Accessibility, OCR, media-projection, screenshot, input-injection, or content-control service exists.
- Android backup is disabled for private catalogs, save-derived knowledge, and mapper sessions.
- Cleartext is denied by default and allowed only for `127.0.0.1` by the Android network-security policy.
- The app-owned HTTP listener was observed only at the IPv4-mapped loopback address. The WebView disables file/content access, rejects mixed content, and blocks navigation outside its exact local origin and approved native routes.
- Mapper capture begins disabled after process restart; the frozen default state was Organic, Game theme, Auto density/display targeting, Docked mode, zero mapper snapshots.

## Dependency, source, and artifact audit

- The runtime dependency graph contains the project modules, Kotlin, AndroidX/Material, Gson, and their ordinary transitive support libraries. Google ML Kit/text recognition is absent; its stale unused version-catalog alias was removed.
- Source scans found no reachable OCR, Accessibility service, screenshot capture, MediaProjection, cheat, input injection, Cocoon dependency, or core-memory write API. The sole `WRITE_CORE_MEMORY` text is a negative architecture assertion in a test.
- The npm tree declared 215 MIT, 8 Apache-2.0, 9 BSD-2-Clause, 3 BSD-3-Clause, 9 ISC, 1 MIT-0, and 1 CC-BY-4.0 package, with zero undeclared licenses. The CC-BY package is build-time `caniuse-lite`; it is not a redistributed game-data source.
- Tracked files and Git history contained no `.gb`, `.gbc`, `.gba`, `.sav`, `.srm`, save state, database, memory dump, keystore, or private-key artifact.
- The APK contained no ROM/save/database/dump/keystore file. Its production web bundle contained only `index.html`, one JavaScript file, and one stylesheet; development simulator strings were absent.

## Frozen debug artifact

- File: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 13,735,378 bytes
- SHA-256: `D00B66532B01525C8455082C3EC33E33CDC3BD8AC8C3B56C6E4D33FC5C287E22`
- Deployment: installed only on the dedicated `DualDex_RA_API35` AVD (`emulator-5556`)
- Publication: local debug artifact only; not a GitHub release

Production signing remains exclusively owned by the protected GitHub `release-signing` environment. Stage 8 must rebuild, sign, verify, and publish its own artifact from the frozen source commit; this debug hash is evidence, not a release payload.
