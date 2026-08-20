# Loading, Knowledge Integrity, and Theme Contrast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the load screen name the module actually running, isolate Organic knowledge by validated save lineage, simplify Organic row status marks, and keep the clock dial legible over every ROM-derived theme.

**Architecture:** Preserve `CatalogMaterializationPhase` as the SQLite checkpoint contract and introduce a separate catalog work-progress stream for the user interface. Change durable knowledge from ROM-only documents to save-scoped schema-4 documents, sanitize restored ledgers against the active catalog, and keep pre-save observations session-local. Apply mode-driven row rendering and theme-independent clock contrast in the web layer.

**Tech Stack:** Kotlin/JVM, Android file persistence, Preact/TypeScript, CSS, JUnit 4, Vitest, Playwright, Gradle, GitHub Actions release workflow.

---

### Task 1: Report the current loading module independently of persistence checkpoints

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-web/src/App.tsx`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParserTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Test: `companion-web/src/App.production.test.tsx`

- [ ] **Step 1: Write failing parser and runtime tests**

Add assertions for a separate ordered work stream. The desired contract is:

```kotlin
enum class CatalogWorkModule {
    ROM_IDENTITY, FAMILY_AND_TABLES, CORE_RECORDS, SPECIES_MEDIA,
    EVOLUTIONS_AND_LEARNSETS, ENCOUNTERS, MOVE_DATA, ABILITY_DATA,
    MAPS, TRAINER_AND_THEME, CATALOG_STORAGE
}

data class CatalogWorkProgress(
    val module: CatalogWorkModule,
    val completedUnits: Int,
    val totalUnits: Int = CatalogWorkModule.entries.size,
)
```

The real parser fixture must observe all modules once in order. Existing checkpoint assertions must remain exactly `ESSENTIAL`, `SPECIES_MEDIA`, `RELATIONSHIPS`, `EXTENDED`, `COMPLETE`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
.\gradlew.bat :parser-core:test --tests '*CatalogParserTest*' :app:testDebugUnitTest --tests '*ProductionCompanionRuntimeTest*'
```

Expected: compilation fails because `CatalogWorkProgress` and the work callback do not exist.

- [ ] **Step 3: Implement the minimal work-progress seam**

Add an optional work callback to `CatalogParser.parse`, `parseCatching`, `ParserOrchestrator.analyzeForCatalog`, and `CatalogMaterializer.materialize`. Emit the module immediately before its operation. Callback failures must be isolated:

```kotlin
private fun reportWork(
    callback: ((CatalogWorkProgress) -> Unit)?,
    module: CatalogWorkModule,
) {
    runCatching { callback?.invoke(CatalogWorkProgress(module, module.ordinal)) }
}
```

Keep `onProgress` unchanged for SQLite checkpoints. Update the runtime injection seam to accept both callbacks; `publishCheckpoint` writes the catalog without changing the loading label, while `publishWork` changes `CatalogLoadingState.phase` and work units.

- [ ] **Step 4: Update UI module labels**

Map work modules directly in `loadingModuleLabel`:

```ts
ROM_IDENTITY: 'ROM identity',
FAMILY_AND_TABLES: 'parser family & table layouts',
CORE_RECORDS: 'species, moves, types & abilities',
SPECIES_MEDIA: 'sprites & Pokédex entries',
EVOLUTIONS_AND_LEARNSETS: 'evolutions & learnsets',
ENCOUNTERS: 'encounter areas',
MOVE_DATA: 'move descriptions & acquisition',
ABILITY_DATA: 'ability details',
MAPS: 'world & local maps',
TRAINER_AND_THEME: 'trainer assets & theme',
CATALOG_STORAGE: 'saved catalog',
```

- [ ] **Step 5: Run focused tests and commit**

Run the Task 1 command plus:

```powershell
Push-Location companion-web
npm.cmd test -- --run src/App.production.test.tsx
Pop-Location
```

Expected: all focused tests pass and checkpoint tests remain unchanged.

Commit:

```powershell
git add parser-core app companion-core companion-web
git commit -m "feat: report active catalog modules"
```

### Task 2: Store and validate knowledge by save lineage

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/knowledge/KnowledgeIntegrity.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepository.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepositoryTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/KnowledgeIntegrityTest.kt`

- [ ] **Step 1: Write failing save-isolation and sanitizer tests**

The repository contract becomes:

```kotlin
interface KnowledgeRepository {
    fun read(romIdentity: String, saveIdentity: String): KnowledgeLedger?
    fun write(romIdentity: String, saveIdentity: String, ledger: KnowledgeLedger)
}
```

Tests must prove:

- two save identities under the same ROM read different ledgers;
- a schema-3 `$rom.json` document is not returned by ordinary reads;
- schema 4 rejects mismatched embedded ROM/save identities;
- the sanitizer removes unknown species/moves/areas and makes `caughtSpecies` a subset of `seenSpecies`.

- [ ] **Step 2: Run knowledge tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*FileKnowledgeRepositoryTest*' --tests '*KnowledgeIntegrityTest*'
```

Expected: compilation fails on the new repository signatures and missing sanitizer.

- [ ] **Step 3: Implement schema 4 and catalog-domain sanitizer**

Use a filename derived from both normalized hashes:

```kotlin
private fun document(rom: String, save: String) = root.resolve("$rom-$save.json")
```

Persist `schema`, `romIdentity`, and `saveIdentity`. Do not delete or auto-import old `$rom.json` files.

Implement:

```kotlin
object KnowledgeIntegrity {
    fun sanitize(ledger: KnowledgeLedger, catalog: ParsedCatalog): KnowledgeLedger
}
```

Build valid species, move, and encounter-base domains from `ParsedCatalog`; filter every ledger field and restore consistency deterministically.

- [ ] **Step 4: Run knowledge tests and commit**

Run the Task 2 command. Expected: all tests pass.

Commit:

```powershell
git add app/src/main/java/com/darkaxt/dualdex/knowledge app/src/test/java/com/darkaxt/dualdex/knowledge
git commit -m "fix: scope knowledge to validated saves"
```

### Task 3: Fail closed until current save lineage is validated

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/SaveKnowledgeMapperTest.kt`

- [ ] **Step 1: Write failing lifecycle tests**

Tests must prove:

1. Catalog load and cache reopen expose an empty ledger even if a legacy ROM ledger exists.
2. Applying save A restores only save A's sanitized Organic observations, then overwrites seen/caught/owned/team from current SaveRAM.
3. Applying save B for the same ROM cannot see save A's observations.
4. An empty current Pokédex clears stale seen/caught flags.
5. Battle observations before a validated save remain visible for the current process but cause no repository write.

- [ ] **Step 2: Run runtime tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*ProductionCompanionRuntimeTest*' :companion-core:test --tests '*SaveKnowledgeMapperTest*'
```

Expected: save-scoped repository expectations fail against ROM-only restore/write behavior.

- [ ] **Step 3: Implement trusted lineage lifecycle**

Add `activeSaveIdentity: String?`. Reset it during catalog transition. Do not call `restoreKnowledge(romSha)` from `publishParsed` or `publishReopened`; publish an empty ledger.

When `applySaveSnapshot` accepts a matching ROM:

```kotlin
activeSaveIdentity = snapshot.saveIdentity
val restored = knowledgeRepository
    ?.read(current.romSha256, snapshot.saveIdentity)
    ?.let { KnowledgeIntegrity.sanitize(it, current) }
    ?: KnowledgeLedger()
gateway.dispatch(CompanionAction.ReplaceLedger(restored))
val merged = SaveKnowledgeMapper.merge(restored, current, snapshot)
```

Persist only when both catalog and `activeSaveIdentity` are present. Preserve same-lineage Organic observations; never merge a ROM-only ledger.

- [ ] **Step 4: Run lifecycle tests and commit**

Run the Task 3 command. Expected: all focused tests pass.

Commit:

```powershell
git add app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test companion-core/src/test
git commit -m "fix: trust knowledge only after save validation"
```

### Task 4: Simplify Organic status marks and improve clock contrast

**Files:**
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/styles.css`
- Test: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Test: `companion-web/src/GameClockIndicator.test.tsx`
- Test: `companion-web/e2e/theme-presentation.spec.ts`

- [ ] **Step 1: Write failing component tests**

Change `StatusMarks` to accept `mode`. Assert that Organic mode renders:

```tsx
<StatusMarks mode="ORGANIC" state={{ seen: true, caught: false, team: false, ballId: null }} ... />
```

with no `.eye-icon`, no `Not caught`, and no ball. A caught Organic row must render exactly one `Caught` mark. Discovered mode keeps its existing affirmative/negative status marks.

Add a clock assertion for a `.game-time-contrast-plate` wrapping the orbit and a CSS source assertion that the plate has a dark translucent background, outline, and stronger track opacity.

- [ ] **Step 2: Run web tests and verify RED**

Run:

```powershell
Push-Location companion-web
npm.cmd test -- --run src/pages/PokedexBrowse.test.tsx src/GameClockIndicator.test.tsx
Pop-Location
```

Expected: tests fail because status rendering is not mode-aware and the contrast plate is absent.

- [ ] **Step 3: Implement mode-aware marks and contrast plate**

Render Organic status as positive-only:

```tsx
if (mode === 'ORGANIC') {
  return caught ? <span class="status-marks">{caughtMark}</span> : null;
}
```

Wrap the dial in a semantic-free plate and use theme-independent contrast:

```css
.game-time-contrast-plate {
  background: #071e18cc;
  border: 1px solid #f4f0cf99;
  box-shadow: 0 1px 3px #0008, inset 0 0 0 1px #0004;
}
.game-time-orbit-track path { stroke: #f4f0cfcc; }
```

Keep exactly one sun or moon icon and preserve the current clock size and header collision contract.

- [ ] **Step 4: Run unit tests, production build, and browser evidence**

Run:

```powershell
Push-Location companion-web
npm.cmd test -- --run
npm.cmd run build
npm.cmd run test:e2e -- e2e/theme-presentation.spec.ts
Pop-Location
```

Expected: all web tests pass, production build succeeds, and the 1240x1080 browser control reports a visible clock plate without header overlap.

- [ ] **Step 5: Commit**

```powershell
git add companion-web
git commit -m "fix: clarify Organic status and clock contrast"
```

### Task 5: Integration verification and RC22 release

**Files:**
- Modify: `README.md`
- Modify: `release/v1-ready.json`
- Create: `release/RELEASE_NOTES_1.1.0-rc.22.md`
- Modify: `.github/workflows/release.yml`
- Modify: `tools/release/release-workflow.test.mjs`

- [ ] **Step 1: Run the complete affected verification**

Run:

```powershell
.\gradlew.bat :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest
Push-Location companion-web
npm.cmd test -- --run
npm.cmd run build
Pop-Location
node --test tools/release/release-workflow.test.mjs tools/release/release-metadata.test.mjs
git diff --check
```

Expected: every command exits 0; no device installation is part of this gate.

- [ ] **Step 2: Prepare RC22 metadata and verify release policy RED/GREEN**

Update version name/code to `1.1.0-rc.22` / `1010022`, record the three gates in `v1-ready.json`, add release notes, and update the workflow's exact tag/ref validation. Run release tests before and after the metadata change to demonstrate the expected stale-reference failure then GREEN.

- [ ] **Step 3: Commit release metadata**

```powershell
git add README.md release .github/workflows/release.yml tools/release
git commit -m "release: prepare DualDex 1.1 rc22"
```

- [ ] **Step 4: Publish and independently verify RC22**

Fast-forward `fork/master`, push the annotated `v1.1.0-rc.22` tag, wait for the protected release workflow, download the APK/checksums/provenance to `D:\Temp`, and verify package ID, version, signing certificate, and SHA-256. Do not install it on the device.

- [ ] **Step 5: Clean temporary artifacts**

After the remote commit/tag/release and local worktree are verified, remove only this task's exact `D:\Temp` worktree and downloaded verification directory. Preserve unrelated repositories, ROMs, map work, signing material, and the user's dirty primary checkout.
