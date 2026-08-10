# DualDex 1.0.0 Requirement Matrix

This matrix maps the authoritative first-release design to implementation, automated evidence, and device evidence. `Implemented` means the code path exists; `Verified` means the named automated and/or device gate has passed; `Stage 8` means the requirement can only be completed with a GitHub-signed artifact or the physical Thor. The release remains blocked while any v1 ledger item is open.

## Specification sections

| Spec | Required outcome | Implementation | Automated evidence | Device/evidence record | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Passive local GB–GBA Pokédex; live targeting deferred; optional isolated mapper | `parser-core`, `save-core`, `companion-core`, `memory-mapper-lab` | Full module suites; mapper boundary tests | Delivery ledger Stages 1–6 | Verified |
| 2.1 | No per-ROM profiles or user-entered addresses | Parser-family competition and independent dataset validators | `ParserOrchestratorTest`, `DatasetResolversTest`, validator suites | Names-first corpus report | Verified |
| 2.2 | ROM-authoritative catalog with explicit capability states | Parsed catalog models/materializers; no bundled fallback database | Catalog/materializer/validator suites | Official and derived ROM AVD checks | Verified |
| 2.3 | Save-authoritative state; never modify SaveRAM | Gen I–III readers, save association/polling, immutable snapshots | Save-family, polling, corruption, and preferred-individual tests | Ledger Stages 4–5 | Verified |
| 2.4 | Local/passive operation and explicitly scoped config edits | All-files read gateway, SAF fallback, loopback host, exact-key public config editor | Storage/config/session tests | Nightly NCI and direct public-config evidence | Verified |
| 2.5 | Pokédex independent from memory mapping | Mapper in separate module/API/store; production runtime has no mapper dependency | `MapperIsolationBoundaryTest`, coordinator and instrumentation isolation tests | Mapper disabled/failure device checks | Verified |
| 3.1 | Search/filter/navigation, ROM sprites/balls/types, ROM identity, small display | Bundled Preact UI and ROM catalog endpoints | Browse/navigation/production UI tests | Exact 1080 x 1240 and 406 x 354 viewport audit | Verified |
| 3.2 | Entry/Stats/Moves/More behavior and IV/DV visualization | `PokedexDetail`, learnset normalization and rulesets | Detail/navigation/Organic-moves tests | 100%/135% font and focused-route visual audit | Verified |
| 3.3 | Focused move/ability pages; decoded mechanics; no raw identifiers or ability `#0` | Move/ability materializers and detail pages | Ability/move materializer and page tests | Modern Emerald visual checks | Verified |
| 3.4 | Best owned individual, innate tier, capture-ball semantics | Save normalization and knowledge mapping | Save reader, tier, tie-break, and policy tests | Gen III private saves and Gen I/II AVD saves | Verified |
| 3.5 | Cached/lazy activation and honest progress | SHA catalog cache and phased runtime loading state | Catalog store/runtime tests | Cold/reopen and `Loading... (N%)` evidence | Verified |
| 4 | Discovered/Organic/Hidden presentation only; captured is statically omniscient | `companion-core` policy and production views | Policy, detail, browse, and ruleset-switch tests | Red/Gold AVD browser gate | Verified |
| 5.1 | Mainline-family Gen I–III scope; spin-offs omitted | Family resolver scope and corpus scanner filters | Parser/CLI scanner tests | Compatibility report excludes named noise | Verified |
| 5.2 | Direct and streamed ZIP inputs; no permanent extraction | `RomImage`, Android `ContentResolver`, ZIP scanner | ROM image and scanner tests | Emerald direct and Modern Emerald ZIP checks | Verified |
| 5.3 | All validated rulesets resident; Auto/manual switch without reread | Catalog ruleset sections and settings action | Ruleset materializer/runtime tests | Modern Emerald two-ruleset check | Verified |
| 5.4 | Per-dataset Available/N/F/N/A plus evidence; score cannot mask failure | Capability/diagnostic model and report writer | Catalog model, validator, and report tests | Fresh 14-ROM names-first report | Verified |
| 6 | Focused module boundaries and Android loopback architecture | Gradle modules, Android server/WebView host | Unit tests, mapper import boundary scan, instrumentation | Loopback-only listener and recovery checks | Verified |
| 6.2 | Remove inherited OCR/Accessibility/screenshots/CSV/profile flow | Replaced Android source/manifest/dependencies | Final manifest/dependency/source audit | Stage 7 release audit | Verified |
| 6.3 | Bundled production UI, real data, blocked navigation, native recovery | Packaged Vite assets, `AndroidLoopbackServer`, `DualDexWebView` | Server/navigation/production asset tests | Final MainActivity instrumentation and APK audit | Verified |
| 7 | Primary All files access, SAF fallbacks, exact config edits, restart/effective-file verification, no PID/Cocoon dependency | Storage gateway/indexers, setup coordinator and `retroarch-session` | Storage policy/index/config/restart/session/route suites | Dedicated AVD grant, revocation and nightly NCI evidence | Verified |
| 8 | Status-based ROM resolution and SHA-keyed transactional cache | ROM session resolver and catalog store | Session/cache/migration tests | Direct/ZIP cold/reopen evidence | Verified |
| 9 | Direct plus SAF-fallback save discovery, heartbeat polling, Gen I–III parsing and gated filters | Direct/SAF save resolvers, monitor/readers and knowledge mapper | Direct refresh, save/checksum/corruption/association suites | Modern Emerald direct `RetroArch/saves/mGBA` match and named save reports | Verified |
| 10 | Disabled read-only mapper, labeled captures/diffs/export, isolated failures | `memory-mapper-lab` and Android coordinator/private store | Mapper unit, boundary, HTTP, native-route and device fake-transport tests | Live current-nightly mGBA capture closed ledger `V1-002` | Verified |
| 11 | Thor-first pages/settings, no bottom bar/simulator controls, Docked/Overlay | Production Preact UI, settings store, overlay service | Web production tests, sizer, display resolver, instrumentation | Exact-viewport/font audit and final AVD gate | Verified |
| 12.1 | Fixed production/debug application IDs | Android Gradle configuration | CI/release checks | Coexistence on dedicated AVD | Verified |
| 12.2 | One long-lived RSA signer; private key and credentials owned by the protected GitHub workflow | `signing/` public material plus GitHub environment secrets | Public-fingerprint and workflow checks | RC6 signer matches the pinned certificate | Verified |
| 12.3 | GitHub-only production signing, fail-closed workflow, safe assets | `.github/workflows/release.yml` | Workflow static validation and GitHub run `31354309237` | RC6 draft assets, provenance, checksums and certificate independently verified | Verified |
| 12.4 | Monotonic version/update with persistence | Gradle/workflow version gates and independent stores | Workflow checks; repository persistence tests | Signed RC5 to RC6 update retained catalog, save, grants and settings on the dedicated AVD | Verified |
| 13 | Dedicated AVD only; Thor reserved for signed live validation | Device-resolution script and explicit `adb -s` commands | Device selection checks | `emulator-5556` evidence; `5554` untouched | Verified; Thor is Stage 8 |
| 14 | Complete parser/save/catalog/setup/UI/release test strategy | Module, web, Playwright and instrumentation suites | 291 unit, 44 web, 13 release-policy, and 3 instrumentation tests plus Android lint | All-files debug candidate on dedicated AVD | Verified |
| 15 | Safe, non-destructive fallback behavior | Runtime/setup/save/cache/mapper recovery paths | Corruption, disconnect, recovery and isolation tests | Dedicated AVD recovery and frozen-default checks | Verified |
| 16 | Local-only, blocked navigation, no writes/telemetry/private assets, license compliance | Loopback/WebView/transport boundaries and repository policy | Final manifest/source/artifact/license audit | `docs/v1-release-audit.md` | Verified |
| 17 | All publication gates satisfied | This matrix plus delivery ledger | Complete convergence pipeline | GitHub-signed AVD and Thor validation | Stage 8 |
| 18 | Live battle features, day/night Area markers, and a gutter-aware resizable overlay remain after 1.0.0 | Explicit deferred list | Not part of the v1 gate | None required | Post-v1 |

## Acceptance criteria

| AC | Gate | Evidence | Current result |
| ---: | --- | --- | --- |
| 1 | Production/debug package IDs | `app/build.gradle.kts`; Stage 0 coexistence | Pass |
| 2 | GitHub-only pinned signing and signed in-place update | Release workflow, public fingerprint, signed RC5 to RC6 update | Pass |
| 3 | Existing AVD untouched; dedicated serial only | Resolver checks and ledger command record | Pass through Stage 6 |
| 4 | Fresh setup can grant broad storage once, index sibling folders, patch only the public config, restart when changed, and verify it | Storage/config/setup suites and dedicated-AVD grant/restart evidence | Pass |
| 5 | Active supported ROM resolution plus manual/cache fallback | Session resolver/runtime tests and nightly AVD evidence | Pass |
| 6 | Direct/ZIP catalog equivalence and SHA cache reuse | Parser/cache tests and Emerald/Modern Emerald evidence | Pass |
| 7 | All available catalog datasets render from ROM; no emoji/fallback Pokédex | Parser/materializer/UI suites and corpus report | Pass |
| 8 | Save refresh updates knowledge, filters, best individual, tier and ball | Save/knowledge suites and named save evidence | Pass |
| 9 | Invalid/partial save retains last good state | Save polling/corruption tests and AVD corruption drill | Pass |
| 10 | Information policies do not leak uncaught Organic data | Policy and focused page tests; Red/Gold visual gate | Pass |
| 11 | Focused navigation and small UI survive APK integration | Web/instrumentation tests and screenshots | Pass |
| 12 | NCI/save/memory failures never block general Pokédex | Runtime recovery and mapper isolation suites | Pass |
| 13 | Mapper disabled/read-only/isolated | Mapper suites, device fake transport, and verified live mGBA export | Pass |
| 14 | Forbidden OCR/screenshot/Accessibility/CSV/cheat/input/write paths absent | Manifest/dependency/source/artifact audit | Pass |
| 15 | Tests/corpus pass; signed candidate passes AVD and Thor | Full convergence run, GitHub RC, physical Thor | Signed AVD gate passed; physical Thor pending |
| 16 | Docked/Overlay behavior with ROM ball and RetroArch focus | Overlay tests and dedicated AVD evidence | Signed RC6 AVD gate passed; physical Thor pending |

## Remaining release blockers

1. Publish and independently verify the GitHub-signed RC7 storage correction, then install it on the physical Thor and complete live acceptance.
2. Build and smoke-check the final GitHub-signed `v1.0.0` artifact before publishing it.
