# DualDex v1 Staged Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan stage-by-stage. Checkboxes track deliverable gates, not microtasks.

**Goal:** Ship DualDex 1.0.0 as a complete ROM- and SaveRAM-backed Pokédex, with an isolated optional Memory Mapper Lab and GitHub-only production signing.

**Architecture:** Deliver vertical slices in dependency order: establish identity/test isolation, put the real ROM Pokédex in an Android shell, add durable catalogs, automate RetroArch activation, add save-backed state by console family, then add the debug mapper. Every stage ends with a specification comparison and committed gap ledger; non-blocking gaps may move forward temporarily, but the final convergence gate closes every v1 requirement before signing.

**Tech Stack:** Kotlin/JVM 17, Android API 30–36, Gradle/AGP, SQLite, Android Storage Access Framework, RetroArch UDP Network Control Interface, Preact/TypeScript/Vite, loopback HTTP/WebView, JUnit, Vitest, Playwright, Android instrumentation, PowerShell/ADB, GitHub Actions.

---

## 1. Delivery rules

### 1.1 The 80/20 stage loop

Every stage uses the same loop:

- [ ] Build the smallest complete vertical slice that proves the stage's core outcome.
- [ ] Run focused unit/contract tests and the relevant regression suites.
- [ ] Exercise the deliverable on the dedicated DualDex emulator.
- [ ] Compare the result against the named specification sections and acceptance criteria.
- [ ] Record every discrepancy in `docs/v1-delivery-ledger.md` with evidence.
- [ ] Fix Stop issues before leaving the stage; carry Convergence and Tuning issues forward.
- [ ] Commit and push a working checkpoint with the ledger updated.

This favors visible, testable progress over early microoptimization. It does not permit dropping v1 requirements: every open Convergence or Tuning item tied to the v1 specification must close in Stage 7.

### 1.2 Gap classifications

| Class | Meaning | Stage behavior |
| --- | --- | --- |
| `STOP-SAFETY` | Wrong app identity/signature, data corruption, privacy leak, ROM/save/memory write, mapper contamination, or destructive behavior | Stop and fix immediately |
| `STOP-CORE` | The stage's advertised core outcome cannot be used, produces materially wrong data, or repeatedly crashes | Stop and fix immediately |
| `CONVERGENCE` | A v1 requirement is missing or imperfect but the current core feature remains honestly usable | Register with evidence and continue |
| `TUNING` | Performance, wording, spacing, secondary edge case, or other polish that does not mislead or block use | Register with evidence and continue |
| `POST-V1` | Explicitly listed in specification section 18 or newly proposed scope | Keep outside the v1 closure count |

`docs/v1-delivery-ledger.md` contains: ID, specification reference, stage found, class, observed result, expected result, reproduction/evidence, temporary disposition, fixing commit, and status. Release requires zero open `STOP-*`, `CONVERGENCE`, or `TUNING` entries tied to v1.

### 1.3 Build and device policy

- Android cannot install a literally unsigned APK. Emulator iterations use the ordinary disposable debug certificate and `com.darkaxt.dualdex.debug`.
- The existing `emulator-5554` is never used or modified.
- Every emulator command resolves the dedicated AVD by name and uses `adb -s <verified-serial>`.
- No debug-signed APK is installed on the AYN Thor for live testing.
- Thor/live testing uses only a GitHub-generated release-signed draft or prerelease APK.
- Production signing keys are never available to local Gradle builds.

## 2. Planned repository boundaries

| Path | Responsibility |
| --- | --- |
| `parser-core/` | Existing pure-Kotlin ROM identification, family competition, extraction, validation, and catalog models |
| `save-core/` | New pure-Kotlin Gen I–III SaveRAM competition, checksums, party/box decoding, current area, and normalized snapshots |
| `catalog-store/` | New Android SQLite catalog/save-association persistence keyed by ROM SHA-256 and schema version |
| `retroarch-session/` | New pure-Kotlin Network Command client, status/config protocol, session state, and read-only mapper transport interface |
| `companion-core/` | Knowledge policy, preferred-owned selection, settings, and presentation state independent of Android and raw addresses |
| `companion-server/` | Desktop POC adapter plus transport-neutral API routing; simulator remains development-only |
| `memory-mapper-lab/` | New optional read-only snapshot/diff/export subsystem with no production-state output |
| `companion-web/` | Production Preact UI and tests; simulator panel excluded from production bundles |
| `app/` | New Android setup wizard, SAF adapters, lifecycle, loopback host, WebView, display targeting, and settings shell |
| `tools/android/` | Dedicated AVD creation, identity resolution, debug deployment, evidence capture, and signed-candidate validation scripts |
| `.github/workflows/` | Non-secret CI and protected GitHub release/signing pipeline |
| `docs/v1-delivery-ledger.md` | Stage audit and temporary v1 gap register |

The inherited OCR/CSV/Accessibility implementation under `app/src/main/java/com/enrpau/dualscreendex` is removed after the replacement shell opens successfully. No new production code depends on those classes.

## Stage 0: Lock identity, delivery controls, and isolated test environment

**Core deliverable:** A reproducible debug build and dedicated RetroArch test AVD exist without touching the current emulator, while the permanent production identity and signer fingerprint are fixed.

**Primary files:**

- Modify `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.gitignore`.
- Create `docs/v1-delivery-ledger.md`.
- Create `tools/android/create-dualdex-avd.ps1`, `tools/android/resolve-dualdex-device.ps1`, `tools/android/install-debug.ps1`.
- Create `.github/workflows/ci.yml`, `.github/workflows/release.yml`.
- Create `signing/README.md`, `signing/dualdex-release-cert.pem`, `signing/dualdex-release-cert.sha256`.

**Delivery checklist:**

- [x] Set production `applicationId` to `com.darkaxt.dualdex` and debug suffix to `.debug`; retain one source tree rather than a forked debug app.
- [x] Generate the single long-lived release key once, export only its public certificate/fingerprint, create the protected GitHub `release-signing` environment secrets, and record a recovery bundle with explicit restoration context.
- [x] Configure local debug builds to use only the Android debug keystore; a local `assembleRelease` cannot silently produce a production-signed artifact.
- [x] Add CI for Kotlin tests, web tests/build, debug APK assembly, dependency/build validation, and `git diff --check`; CI receives no signing secrets.
- [x] Add a release workflow skeleton that fails closed until the protected secrets, tag/version, package ID, and expected fingerprint agree.
- [x] Create `DualDex_RA_API35` with persistent storage on `D:`, install a pinned RetroArch build, and capture its clean/configured AVD snapshots without symlinks.
- [x] Prove `resolve-dualdex-device.ps1` selects the new AVD while `emulator-5554` remains byte-for-byte outside all deployment commands.
- [x] Assemble/install the debug APK on the dedicated AVD and verify `com.darkaxt.dualdex.debug` can coexist with RetroArch.

**Gate evidence:** CI URL, signer fingerprint, AVD name/serial evidence, installed package list, and an unchanged-state check for `emulator-5554` recorded in the ledger.

**Specification audit:** Sections 12, 13, and acceptance criteria 1–3.

**Checkpoint commit:** `build: lock v1 identity and isolated test lane`

## Stage 1: Ship the real ROM Pokédex inside Android

**Core deliverable:** A debug APK on the dedicated AVD manually opens a user-selected ROM/ZIP and renders the real parsed Pokédex through the approved small-screen web UI. Save files and automatic RetroArch discovery are not required yet.

**Primary files:**

- Replace `app/src/main/AndroidManifest.xml` and `app/src/main/java/com/enrpau/dualscreendex/**` with focused sources under `app/src/main/java/com/darkaxt/dualdex/`.
- Create `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`, `DualDexApplication.kt`, `rom/RomDocumentPicker.kt`, `web/AndroidLoopbackServer.kt`, `web/ProductionCompanionRuntime.kt`, `web/DualDexWebView.kt`.
- Modify `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/ApiModels.kt` and `DualDexServer.kt` to separate transport-neutral routing from the desktop `com.sun.net.httpserver` adapter.
- Modify `companion-web/src/gateway.ts`, `App.tsx`, `models.ts`, `styles.css`, and Vite production configuration.
- Add Android host/unit/instrumentation tests under `app/src/test` and `app/src/androidTest`.

**Delivery checklist:**

- [x] Package the production Vite bundle into the APK and start an Android-compatible server bound only to `127.0.0.1`; show a native recovery page if it cannot start.
- [x] Use SAF to select a direct GB/GBC/GBA ROM or ZIP and pass its stream to the existing parser without creating a permanent extracted ROM.
- [x] Replace simulator data with a production runtime backed by `ParsedCatalog`; exclude generator, seed, attack-reference, and simulated-battle controls from production assets.
- [x] Render browse, species Entry/Stats/Moves/More, move detail, ability detail, evolution navigation, settings shell, ROM sprites, ball sprites, and ROM type colors.
- [x] Preserve the approved Thor layout: no global bottom bar, no emoji, no body overflow, and explicit Back behavior.
- [x] Remove ML Kit, Accessibility service, media-projection permissions, bundled CSV profiles, profile creation, screenshot capture, and inherited SQLite seeding.
- [x] Exercise one direct official ROM and the Modern Emerald ZIP on the dedicated AVD.

**Gate evidence:** Screen recording or screenshots of a real ROM catalog, loopback-only listener evidence, package permission dump, zero WebView console errors, and focused test results.

**Specification audit:** Sections 2.2, 2.4, 3.1–3.3, 6.2–6.3, 11.1, and acceptance criteria 7, 11, and 14.

**Expected temporary ledger items:** durable SQLite cache, progressive reopen, automatic active-ROM matching, and save-derived filters. These are planned stages, not grounds to polish Stage 1 indefinitely.

**Checkpoint commit:** `feat: run the ROM pokedex in Android`

## Stage 2: Complete and persist the ROM catalog

**Core deliverable:** Direct and ZIP ROMs across every supported mainline family create or reopen one complete SHA-256-keyed SQLite catalog with all validated rulesets and production navigation.

**Primary files:**

- Create `catalog-store/build.gradle.kts` and sources under `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/`.
- Create `CatalogDatabase.kt`, `CatalogSchema.kt`, `CatalogWriter.kt`, `CatalogReader.kt`, `CatalogCache.kt`, `CatalogMigration.kt`.
- Modify `parser-core` only where a normalized catalog field or progress contract is genuinely missing.
- Modify `app/.../rom/`, `ProductionCompanionRuntime.kt`, and `companion-web` loading/status pages.
- Modify `reports/dualdex-parser-compatibility.json` and `.md`.

**Delivery checklist:**

- [x] Persist catalog identity, capabilities, provenance, species/forms, sprites, types/charts, stats, entries, moves/mechanics, rulesets, acquisition, abilities, evolutions, encounters, and ball artwork in transactional SQLite.
- [x] Store SHA-256 as authority and CRC32/size/title/family as lookup evidence; never join caches by basename alone.
- [x] Publish phased loading state and make only committed catalog sections visible.
- [x] Reopen a completed cache without rereading the ROM; invalidate or migrate it when the parser schema changes.
- [x] Keep every ruleset in the same database and make Auto/manual switching instantaneous.
- [x] Index user-granted direct files and ZIP central-directory entries without permanent extraction.
- [x] Run the complete in-scope private ROM corpus and publish a names-first report with explicit Available/Not Found/Not Applicable states; exclude spin-offs and Mystery Dungeon.
- [x] Confirm all focused page links and parser diagnostics use persisted data, not the in-memory simulator.

**Gate evidence:** cold-parse versus cache-reopen timings, database identity/header dump, direct/ZIP equivalence tests, corpus report, and AVD UI capture.

**Specification audit:** Sections 2.1–2.2, 3.1–3.5, 5, 8, 14.1, and acceptance criteria 5–7.

**Checkpoint commit:** `feat: persist complete ROM catalogs`

## Stage 3: Automate RetroArch setup and active-content resolution

**Core deliverable:** The wizard obtains public-folder access, safely edits/verifies the public RetroArch configuration, detects an active ROM through Network Commands, and opens its catalog without depending on Cocoon or a PID. Settings also provides the user-requested Docked/Overlay mode so RetroArch can remain focused while the same companion is visible in a fixed 4:3 panel.

**Primary files:**

- Create `retroarch-session/build.gradle.kts` and sources under `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/`.
- Create `NetworkCommandClient.kt`, `RetroArchStatus.kt`, `SessionMonitor.kt`, `ConfigDocumentEditor.kt`, `RomSessionResolver.kt`.
- Create Android adapters under `app/src/main/java/com/darkaxt/dualdex/setup/` and `storage/`.
- Create `companion-web/src/pages/SetupPage.tsx` and update Settings/status models.

**Delivery checklist:**

- [x] Request persistent read/write access to the public RetroArch tree and read-only access to the smallest required ROM-library tree.
- [x] Patch only exact approved keys, preserve unrelated bytes/line endings, verify replacement, and clean the contextual recovery document.
- [x] Ask for a normal RetroArch restart and prove the candidate config is effective through NCI; a successful write alone is not success.
- [x] Implement lifecycle heartbeats without PID dependence or cancellation timeouts.
- [x] Resolve active content by system/basename/optional CRC32 against the granted direct/ZIP index, then verify SHA-256 before cache association.
- [x] Retain manual ROM selection and last-cache browsing when NCI is disabled, empty, or ambiguous.
- [x] Show exact setup state and manual RetroArch breadcrumbs when automatic verification fails.
- [x] Default to Docked; opt into a foreground-service overlay with a draggable ROM-derived Poké Ball, fixed 4:3 panel, and a working return-to-Docked path.
- [x] Validate independent launch without reading Cocoon or process state; Cocoon or another launcher may start both apps but is not a Stage 3 dependency.

**Gate evidence:** before/after config diff, runtime GET_STATUS/config evidence, automatic Modern Emerald activation on the AVD, manual fallback capture, failure-path tests, and a simultaneous RetroArch `PLAYING` plus DualDex `ACTIVE` overlay capture.

**Specification audit:** Sections 7–8.1, 14.4, 15, and acceptance criteria 4–6 and 12.

**Checkpoint commit:** `feat: activate catalogs from RetroArch sessions`

## Stage 4: Deliver the Generation III SaveRAM vertical slice

**Core deliverable:** Modern Emerald and the other private mGBA saves update seen/caught state, Team, Area, preferred owned individual, IV tier, and capture-ball artwork without affecting static catalog availability.

**Primary files:**

- Create `save-core/build.gradle.kts` and `save-core/src/main/kotlin/com/darkaxt/dualdex/save/`.
- Create `SaveModels.kt`, `SaveParser.kt`, `SaveCapability.kt`, `gen3/Gen3SaveReader.kt`, `gen3/Gen3PokemonCodec.kt`, `gen3/Gen3Checksums.kt`.
- Create Android `SaveDocumentResolver.kt`, `SavePollingMonitor.kt`, `SaveAssociationStore.kt`.
- Modify `companion-core` knowledge policy/preferred-individual models, API views, and Pokédex filters.

**Delivery checklist:**

- [x] Identify the newest complete checksum-valid Gen III save slot and decode sections without modifying the source file.
- [x] Decode seen/caught flags, party, boxes, current map, species/form, level, eggs, six IVs, and capture-ball ID as independent capabilities.
- [x] Match SaveRAM using effective RetroArch directory rules plus structural validation, not filename alone.
- [x] Poll on monitoring heartbeats; build a candidate snapshot off-state and atomically publish only after full validation.
- [x] Retain the last good snapshot across short/partial/corrupt reads and warn only when autosave is disabled/unverified, not merely because time passed.
- [x] Select the best owned individual deterministically and render its qualitative tier and ROM-derived ball marker.
- [x] Validate read-only against `Emerald Rogue`, `Modern Emerald 3.5`, and `Odyssey` samples in `H:\My Drive\Roms\Android\saves\mGBA`.
- [x] Use non-Pokémon saves only as negative family-detection fixtures and keep all private saves out of Git/artifacts.

**Gate evidence:** named-save parse summaries without personal/raw data, corruption/partial-write tests, Modern Emerald Organic-mode AVD capture, and source-file hashes proving no writes.

**Specification audit:** Sections 2.3, 3.4, 9, 14.2–14.3, and acceptance criteria 8–10.

**Checkpoint commit:** `feat: refresh Gen III knowledge from SaveRAM`

## Stage 5: Complete GB/GBC saves and all information policies

**Core deliverable:** Gen I and II SaveRAM readers join the existing ROM catalogs, and Discovered/Organic/Hidden plus All/Caught/Seen/Team/Area behave consistently across every supported generation.

**Primary files:**

- Create `save-core/.../gen1/Gen1SaveReader.kt`, `Gen1Checksums.kt`, `gen2/Gen2SaveReader.kt`, `Gen2Checksums.kt`.
- Expand `SaveParser.kt`, normalized models, capability tests, and Android save discovery.
- Modify `companion-core/.../KnowledgePolicy.kt`, `PreferredIndividualSelector.kt`, API models, and `companion-web` filters/detail gating.
- Add synthetic fixtures under `save-core/src/test/resources/` containing only original minimal test data.

**Delivery checklist:**

- [x] Decode each official Gen I/II region/version shape through structural competition and applicable checksums.
- [x] Produce seen/caught, party, boxes, current area, eggs where applicable, and the normalized five-value DV quality vector.
- [x] Mark original capture-ball identity Not Applicable and use the ROM's generic ball artwork without implying provenance.
- [x] Generate minimal real SaveRAM integration samples through normal play in the dedicated AVD where private samples are unavailable; do not download or commit third-party saves.
- [x] Make Organic list only seen/caught entries, make uncaught Moves empty for 1.0.0, disable uncaught Stats/More, and unlock complete static truth after capture.
- [x] Make Hidden list only captured entries and Discovered expose the complete validated ROM index.
- [x] Gate Team and Area independently and prove unsupported joins disable only their own filter.
- [x] Verify manual ruleset selection does not reread ROM/SaveRAM or duplicate the database.

**Gate evidence:** Gen I/II checksum fixtures, AVD-produced save summaries, all knowledge-policy matrix tests, and UI captures for each mode/generation.

**Specification audit:** Sections 3.1–3.4, 4, 9.3–9.5, 11.2, 14.2–14.5, and acceptance criteria 8–10.

**Checkpoint commit:** `feat: complete save-backed knowledge modes`

## Stage 6: Ship settings and the isolated Memory Mapper Lab

**Core deliverable:** Production settings are complete and the optional mapper can capture read-only labeled memory evidence without being able to influence the Pokédex.

**Primary files:**

- Create `memory-mapper-lab/build.gradle.kts` and sources under `memory-mapper-lab/src/main/kotlin/com/darkaxt/dualdex/mapper/`.
- Create `MapperSession.kt`, `MemoryDescriptor.kt`, `MemorySnapshot.kt`, `SnapshotDiff.kt`, `MapperExport.kt`.
- Create `app/.../settings/SettingsRepository.kt`, `display/DisplayTargetController.kt`, mapper Android adapters.
- Create `companion-web/src/pages/MemoryMapperPage.tsx`; complete `SettingsPage.tsx` and production status pages.
- Add module/dependency-boundary tests.

**Delivery checklist:**

- [x] Persist information policy, ruleset, font/density, theme, display target, folder grants, and verified RetroArch/save associations.
- [x] Expose scoped catalog-cache and mapper-session controls; no control claims to reset save-backed seen/caught state.
- [x] Keep the mapper toggle off on every app launch and use one confirmation when it is enabled for the session.
- [x] Issue no memory command while disabled; when enabled, permit only read commands through an API that cannot represent writes.
- [x] Capture labeled Overworld/Battle Start/Move Selected/Move Executed/Target Changed/Opponent Switched/Battle End snapshots and bounded diffs.
- [x] Store mapper data in a separate namespace; enabling the lab acknowledges raw export for that session and no raw data enters CI diagnostics.
- [x] Enforce that catalog, save, knowledge, ruleset, and production API modules cannot import mapper address/snapshot models.
- [x] Kill/fail/disable mapper sessions during tests and prove the Pokédex state/database hashes remain unchanged.

**Gate evidence:** no-read-while-disabled transport trace, read-only command API test, before/after production database hashes, mapper export sample held locally, and AVD isolation recording.

**Specification audit:** Sections 2.5, 10–11.3, 14.3, 15, 16, and acceptance criterion 13.

**Checkpoint commit:** `feat: add isolated read-only memory lab`

## Stage 7: Full v1 specification convergence

**Core deliverable:** Every v1 requirement and every temporary ledger entry is resolved before any production-signed candidate is requested.

**Primary files:** All modules affected by ledger findings; `docs/v1-delivery-ledger.md`, `README.md`, `reports/`, `docs/images/`, and release documentation.

**Delivery checklist:**

- [x] Build a requirement matrix mapping every first-release specification section and acceptance criterion to implementation, tests, and device evidence.
- [x] Re-run the complete ROM parser corpus and save-family suites; investigate every Not Found that the release claims should be Available.
- [x] Close all `STOP-*`, `CONVERGENCE`, and `TUNING` ledger items tied to v1; only specification section 18 items may remain Post-v1.
- [x] Run full Kotlin, Android, web, Playwright, instrumentation, config, save-corruption, cache-migration, policy, mapper-isolation, and update-preservation suites.
- [x] Audit small-screen behavior on the exact Thor viewport and large font scales; fix every overflow, malformed icon, inaccessible route, misleading label, or simulator artifact.
- [x] Audit permissions, localhost binding, WebView navigation, secret exposure, raw diagnostic handling, dependency licenses, and absence of ROM/save/memory assets.
- [x] Verify OCR, Accessibility, screenshot, CSV profile, cheat, input, ROM write, SaveRAM write, and memory-write paths are absent from the final manifest/dependency graph.
- [x] Update README architecture, setup, screenshots, feature table, limitations, compatibility report, signing fingerprint, and explicit memory-mapper boundary.
- [x] Produce a clean debug candidate on the dedicated AVD and freeze the exact commit for release-candidate signing.

**Gate evidence:** zero-open-v1 ledger, requirement matrix, complete test logs, final screenshots, manifest/dependency audit, corpus report, and frozen commit SHA.

**Specification audit:** Entire `2026-08-09-dualdex-first-release-design.md`, especially all 16 acceptance criteria.

**Checkpoint commit:** `feat: converge DualDex v1 specification`

## Stage 8: GitHub-signed release candidates and live validation

**Core deliverable:** GitHub produces the only production-signed artifacts; signed candidates pass update testing on the dedicated AVD and live testing on the Thor before v1.0.0 is published.

**Primary files:** `.github/workflows/release.yml`, `app/build.gradle.kts`, release notes, public certificate/fingerprint, and ledger evidence only if candidate validation finds a defect.

**Delivery checklist:**

- [ ] Trigger a protected `v1.0.0-rc.N` workflow from the frozen commit; run all non-secret tests before entering the signing environment.
- [ ] Reconstruct the keystore only on the ephemeral runner, verify the pinned fingerprint, sign, run `apksigner verify --print-certs`, and generate checksums/provenance.
- [ ] Attach the APK, checksum, public certificate, compatibility report, and release notes to a draft/prerelease; never attach secrets, ROMs, saves, corpus paths, or memory dumps.
- [ ] Download the GitHub asset and independently verify package ID, version, SHA-256, and signer before installation.
- [ ] Install/update the signed candidate on the dedicated AVD and validate setup, catalog cache, SaveRAM refresh, settings persistence, and mapper isolation.
- [ ] Install only that GitHub-signed candidate on the Thor, then validate public-folder setup, RetroArch/mGBA integration, lower-screen presentation, dual launch, lifecycle, and user acceptance.
- [ ] If validation finds a defect, classify it in the ledger, return to Stage 7, fix and re-run convergence, then create a new monotonically versioned signed RC. Never replace an existing signed asset in place.
- [ ] After a clean RC, tag `v1.0.0`, let GitHub rebuild/sign/verify the final artifact, repeat identity/checksum smoke validation, and publish the GitHub Release.
- [ ] Confirm the public release is visibly distinct from local deployment and document the exact released commit, APK hash, signer fingerprint, and validation devices.

**Release gate:** No public release exists until the signed GitHub artifact, not a local debug build, satisfies every acceptance criterion and the v1 ledger remains empty.

**Checkpoint commits/tags:** `release: prepare DualDex 1.0.0`, signed `v1.0.0-rc.N`, then public `v1.0.0`.

## 3. Stage execution commands

Each checkpoint runs the applicable subset during development and the complete set in Stage 7:

```powershell
.\gradlew.bat :parser-core:test :parser-cli:test :companion-core:test :companion-simulator:test :companion-server:test --rerun-tasks
Set-Location companion-web
npm test
npm run build
Set-Location ..
.\gradlew.bat :app:assembleDebug :app:lintDebug
git diff --check
```

As new modules land, add `:save-core:test`, `:catalog-store:test`, `:retroarch-session:test`, and `:memory-mapper-lab:test` to the complete command. Android instrumentation always names the dedicated AVD serial explicitly.

## 4. Definition of shipped v1

The plan is complete only when:

- all eight stages have evidence-backed checkpoint commits;
- the v1 ledger contains no unresolved in-scope item;
- the static Pokédex works without memory reads;
- all supported ROM/save families meet their honest capability contracts;
- the mapper is optional, read-only, and isolated;
- the existing emulator was never used;
- production signing occurred only in GitHub;
- only GitHub-signed candidates reached the Thor; and
- the published `v1.0.0` asset, hash, signer, commit, and device validation are recorded.
