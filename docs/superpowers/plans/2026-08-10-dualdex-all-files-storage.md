# DualDex All-Files Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make signed DualDex discover multi-folder GB/GBC/GBA ROM libraries and RetroArch SaveRAM through Android All files access.

**Architecture:** Add one Android permission gateway, direct-file ROM indexing/opening, and direct-file save discovery while retaining SAF adapters as fallback. Publish the grant and index state through the existing runtime API, then expose one primary setup action. Validate on the dedicated AVD before producing GitHub-signed RC7 for the Thor.

**Tech Stack:** Kotlin/JVM 17, Android API 30–36, `MANAGE_EXTERNAL_STORAGE`, Java `File`/URI I/O, Preact/TypeScript, JUnit, Vitest, ADB, GitHub Actions.

---

### Task 1: Pin the permission and route contract

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/NativeSetupRoute.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/NativeSetupRouteTest.kt`

- [x] Add a failing route test asserting `dualdex://grant/files` maps to `GRANT_ALL_FILES` and near-miss URLs remain rejected.
- [x] Run `./gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.web.NativeSetupRouteTest` and confirm the new assertion fails because the route does not exist.
- [x] Add `MANAGE_EXTERNAL_STORAGE`, the enum value, and the exact route mapping.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Index and open direct shared-storage ROMs

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/storage/SharedStorageGateway.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/storage/DirectRomLibraryIndexer.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/storage/RomSourceInput.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/storage/DirectRomLibraryIndexerTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`

- [x] Add tests with temporary sibling `GB`, `GBC`, and `GBA` directories proving all supported ROMs become `file:` entries, non-ROM ZIP/files are skipped, and duplicate canonical paths collapse.
- [x] Run the focused test and confirm it fails because the direct indexer is absent.
- [x] Implement mounted-root discovery, protected-directory pruning, parser-backed indexing, and direct/content source opening.
- [x] Make the coordinator prefer the all-files index and retain the SAF index when broad access is absent.
- [x] Re-run focused storage tests and existing ROM-index/session tests.

### Task 3: Read RetroArch SaveRAM directly

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolver.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolverTest.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/setup/FileRetroArchConfigStore.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/setup/FileRetroArchConfigStoreTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`

- [x] Add a failing test proving a matching `.srm` below an effective `savefile_directory` becomes a direct candidate and refreshes after the file changes.
- [x] Add a failing config-store test proving exact bytes, recovery write/read/delete, and save-setting reads work on a public file.
- [x] Implement direct save discovery for the effective save directory and active ROM parent, then fall back to SAF candidates when direct discovery finds none.
- [x] Implement the file config store and let autosave verification use it when broad access is granted.
- [x] Run save resolver, config installer, corruption, and association tests.

### Task 4: Make one permission the primary setup experience

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/SetupPage.tsx`
- Modify: `companion-web/src/pages/SetupPage.test.tsx`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`

- [x] Add failing web tests for the `ALL FILES ACCESS` action, multi-folder copy, and missing-access SaveRAM guidance.
- [x] Add a failing coordinator/gateway policy test for missing, granted/indexing, and revoked states.
- [x] Open `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for the exact package and refresh storage state on resume.
- [x] Publish `storageGrant` and render the single primary setup action with SAF fallback guidance.
- [x] Run the focused web and Android tests.

### Task 5: Converge, validate, and sign RC7

**Files:**
- Modify: `README.md`
- Modify: `docs/v1-delivery-ledger.md`
- Modify: `docs/v1-requirement-matrix.md`
- Modify: `docs/v1-release-audit.md`
- Modify: `docs/superpowers/specs/2026-08-09-dualdex-first-release-design.md`

- [x] Run all Gradle tests, all 42+ web tests, production web build, Android lint, release-policy tests, and Android deployment-safety tests.
- [x] Install the debug build only on `emulator-5556`, grant All files access, and prove multi-folder ROM indexing plus Modern Emerald SaveRAM matching.
- [x] Verify `emulator-5554` remains unchanged and no debug package reaches `bfa98654`.
- [ ] Record the Stage 8 defect and correction, commit, push, merge after CI, and tag a monotonically newer `v1.0.0-rc.7`.
- [ ] Let GitHub build/sign RC7, publish it as a prerelease, anonymously revalidate hash/package/version/signer, and install/update it on the Thor with `validate-signed-candidate.ps1 -Target Thor -Install`.
- [ ] Prove the Thor reports the existing Modern Emerald discoveries, catalog remains usable without mapper data, and complete the remaining live acceptance gates.
