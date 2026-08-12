# DualDex ROM-Scoped Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist most APK settings as sparse ROM-SHA-specific overrides while retaining durable global defaults and global-only device ownership fields.

**Architecture:** Upgrade `SettingsRepository` to a bounded schema-2 document with global defaults plus sparse per-ROM overrides. Thread ROM identity through `ProductionCompanionRuntime` settings reads/writes, preserving compatibility defaults for non-Android/test callers and keeping SaveRAM detection independent.

**Tech Stack:** Kotlin, Android `SharedPreferences`, Gson, JUnit 4, existing companion runtime and web settings UI.

---

### Task 1: Define and prove the schema-2 repository contract

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests**

Add tests using two distinct 64-character hashes. Assert `readForRom(hashA)` and `readForRom(hashB)` can return different `ruleset`, `knowledgeMode`, `theme`, and helper flags while `displayTarget`, `overlayScale`, and `thorTopScreenFocus` remain shared. Assert a sparse override inherits a later global default change.

- [ ] **Step 2: Verify RED**

Run:

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --tests com.darkaxt.dualdex.settings.SettingsRepositoryTest --console=plain
```

Expected: compilation or assertions fail because ROM-scoped APIs/schema do not exist.

- [ ] **Step 3: Implement the bounded schema-2 document**

Add synchronized APIs:

```kotlin
fun readGlobal(): CompanionSettings
fun readForRom(romSha256: String?): CompanionSettings
fun writeGlobal(settings: CompanionSettings)
fun writeForRom(romSha256: String?, settings: CompanionSettings)
fun migrateLegacyRuleset(lastRomSha256: String?)
```

Persist a complete global record and sparse string/boolean/number override records keyed by validated lowercase SHA-256. Compute overrides against current globals, remove empty records, and fail without mutation above 4096 records.

- [ ] **Step 4: Verify GREEN**

Run the focused command from Step 2. Expected: all repository tests pass.

### Task 2: Migrate schema 1 without guessing ownership

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing migration tests**

Load an existing schema-1 JSON document with a manual ruleset. Assert migration with a valid last-ROM hash stores only that ruleset under the ROM, resets the global ruleset to `AUTO`, retains all other values as global defaults, and is idempotent. Assert missing/invalid last hash leaves the legacy choice global until a valid migration is possible.

- [ ] **Step 2: Verify RED**

Run the focused repository test command. Expected: migration assertions fail.

- [ ] **Step 3: Implement migration**

Parse schema 1 through the existing independently-clamped field reader. Write schema 2 only after the entire replacement document is valid. Never infer ownership from a filename.

- [ ] **Step 4: Verify GREEN**

Run focused repository tests. Expected: all pass.

### Task 3: Make runtime settings ROM-aware

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`

- [ ] **Step 1: Write failing runtime tests**

Load catalog A, select a manual ruleset/theme, load catalog B and choose different values, then switch back. Assert each catalog publishes its own effective settings. Assert an unavailable stored ruleset yields `activeRulesetId=null` without erasing the stored ID. Assert global-only fields remain shared.

- [ ] **Step 2: Verify RED**

Run:

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --tests com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest --console=plain
```

Expected: catalog switches retain the old global settings and tests fail.

- [ ] **Step 3: Thread ROM identity through settings callbacks**

Add backward-compatible runtime callbacks:

```kotlin
settingsForRom: (String) -> CompanionSettings
onRomSettingsChanged: (String?, CompanionSettings) -> Unit
```

Apply settings for the catalog SHA before publishing ready/reopened state. Pass the active SHA on writes. In `DualDexApplication`, initialize from the last SHA, run legacy migration, and wire the repository functions. Keep SaveRAM detection/fingerprint application unchanged.

- [ ] **Step 4: Verify GREEN**

Run repository and runtime focused tests. Expected: all pass.

### Task 4: Tell the user what scope is being edited

**Files:**
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/pages/SettingsPage.production.test.tsx`

- [ ] **Step 1: Write a failing UI contract test**

Assert the settings page labels ROM-overridable settings as saved for the loaded ROM and device-owned settings as global. Keep the existing LEVEL_UP-only Auto/SaveRAM disclaimer.

- [ ] **Step 2: Verify RED**

Run:

```powershell
npm test -- --run src/pages/SettingsPage.production.test.tsx
```

from `companion-web`. Expected: copy assertion fails.

- [ ] **Step 3: Add concise scope copy**

Add a ROM-settings note near gameplay/ruleset controls and a global-device note near display ownership controls. Do not add a new navigation flow.

- [ ] **Step 4: Verify GREEN**

Run the focused web test and full web suite/build.

### Task 5: Verify persistence, real SaveRAM, and release compatibility

**Files:**
- Test only; no production file is required unless verification exposes a defect.

- [ ] **Step 1: Run complete affected suites**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest :catalog-store:test :save-core:test :companion-server:test --console=plain
```

- [ ] **Step 2: Reopen real Modern catalog and SaveRAM**

Copy the matching save from `H:\My Drive\Roms\Android\saves\mGBA` to `D:\Temp`, parse the copy, and verify:

- original hash unchanged;
- `AUTO` selects the fingerprint-bound detected ruleset;
- a manual ruleset override is stored only under Modern's ROM SHA;
- another ROM retains `AUTO` or its own manual choice;
- clearing/rebuilding catalog SQLite does not remove either profile.

- [ ] **Step 3: Run final formatting and diff checks**

```powershell
git diff --check
```

Expected: no whitespace errors; all task-created temporary files are removed after evidence aggregation.
