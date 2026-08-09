# DualDex Catalog Truth and UI Corrections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the browser POC expose the active ROM ruleset honestly and present complete, understandable species, encounter, evolution, ability, and move information on the Thor-sized companion surface.

**Architecture:** `parser-core` retains lossless raw records, independently validated level-up rulesets, move descriptions, acquisition methods, and provenance. `companion-server` exposes every detected variant while `CompanionSettings.ruleset` selects `AUTO` or a manual override for the simulator; the future memory mapper resolves `AUTO`. `companion-web` normalizes duplicate acquisition rows for presentation, uses one shared move-detail route, and exposes copyable diagnostics outside the production device frame.

**Tech Stack:** Kotlin/JVM 17, JUnit 4, Preact, TypeScript, Vite, Vitest/Testing Library, Playwright, loopback HTTP/SSE.

---

### Task 1: Lossless catalog types and presentation normalization

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/LearnsetNormalizer.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/LearnsetNormalizerTest.kt`

- [ ] **Step 1: Write failing normalization tests**

```kotlin
@Test fun groupsInitialAndLaterEntriesWithoutLosingEitherSource() {
    val result = LearnsetNormalizer.normalize(
        listOf(LearnsetEntry(1, 106), LearnsetEntry(7, 106)),
    )
    assertEquals(listOf(NormalizedLevelUpMove(106, initial = true, levels = listOf(7))), result)
}

@Test fun keepsDistinctLaterLevelsSortedAndUnique() {
    val result = LearnsetNormalizer.normalize(
        listOf(LearnsetEntry(20, 52), LearnsetEntry(7, 52), LearnsetEntry(7, 52)),
    )
    assertEquals(listOf(7, 20), result.single().levels)
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew :parser-core:test --tests '*LearnsetNormalizerTest'`

Expected: compilation fails because `LearnsetNormalizer` and `NormalizedLevelUpMove` do not exist.

- [ ] **Step 3: Add explicit catalog types**

Add `LearnsetRuleset`, `NormalizedLevelUpMove`, `MoveAcquisition`, and `MoveAcquisitionMethod`. Preserve `SpeciesRecord.learnset` as the parser-primary raw table and add catalog-level `learnsetRulesets` so existing official-ROM consumers remain source-compatible.

```kotlin
enum class MoveAcquisitionMethod { EGG, MACHINE, TUTOR }
data class MoveAcquisition(val moveId: Int, val method: MoveAcquisitionMethod, val sourceId: Int? = null)
data class NormalizedLevelUpMove(val moveId: Int, val initial: Boolean, val levels: List<Int>)
data class LearnsetRuleset(
    val id: String,
    val label: String,
    val sourceOffset: Int,
    val confidence: Double,
    val entriesBySpecies: Map<Int, List<LearnsetEntry>>,
)
```

- [ ] **Step 4: Implement stable grouping and run GREEN**

Normalization groups by `moveId`, treats level 1 as `initial`, retains every distinct level greater than 1, and preserves first-ROM-occurrence ordering. Run the focused test, then `./gradlew :parser-core:test`.

- [ ] **Step 5: Commit**

Commit: `feat: model catalog rulesets and move acquisition`

### Task 2: Detect multiple level-up rulesets and move descriptions

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/LearnsetRulesetMaterializer.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializer.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/LearnsetRulesetMaterializerTest.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializerTest.kt`

- [ ] **Step 1: Write a failing synthetic dual-ruleset test**

Build a synthetic GBA ROM with two aligned `speciesCount` pointer arrays. Both arrays terminate legally; the expanded table has an additional move. Assert that both stable ruleset IDs and their source offsets are returned and the parser-primary table is marked as the Auto fallback.

- [ ] **Step 2: Confirm the ruleset test fails for missing discovery**

Run: `./gradlew :parser-core:test --tests '*LearnsetRulesetMaterializerTest'`.

- [ ] **Step 3: Implement bounded structural discovery**

Scan aligned GBA pointer runs using the species-zero/species-one repeated-pointer anchor, validate all candidate learnsets against ROM bounds, terminators, move count, level bounds, monotonic levels, and a minimum valid-species ratio, and deduplicate identical pointer tables. Always include the selected layout table. Give variants neutral labels (`Base`, `Expanded 1`, and so on) derived from entry counts rather than source-specific names.

- [ ] **Step 4: Write and fail a move-description pointer-table test**

The synthetic ROM contains `moveCount - 1` pointers to terminated GBA strings. Assert that move 52 receives decoded text and that a pointer table with invalid strings is rejected.

- [ ] **Step 5: Materialize descriptions without guessing**

Detect a structurally valid pointer table, decode each terminated string through `PokemonTextCodec.gbaEnglish`, and populate `MoveRecord.effectText`. Gen I/II return explicit `NOT_APPLICABLE` or `NOT_FOUND` evidence when the family does not contain a compatible table.

- [ ] **Step 6: Integrate rulesets and descriptions and run GREEN**

Run the two focused suites and all parser-core tests.

- [ ] **Step 7: Commit**

Commit: `feat: discover ROM rulesets and move descriptions`

### Task 3: Extract independent move acquisition methods

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveAcquisitionMaterializer.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveAcquisitionMaterializerTest.kt`

- [ ] **Step 1: Write failing family-specific tests**

Cover Gen I/II embedded machine-compatibility bitfields, Gen III egg-list sentinels, and Gen III machine/tutor bitfields paired with a validated ROM move list. Assert that invalid candidates return no acquisitions and do not elevate the capability.

- [ ] **Step 2: Confirm RED**

Run: `./gradlew :parser-core:test --tests '*MoveAcquisitionMaterializerTest'`.

- [ ] **Step 3: Implement independent resolvers**

Materialize `EGG`, `MACHINE`, and `TUTOR` records only when both the compatibility structure and its move-ID list validate. Keep the methods independent so one missing table does not suppress the others. Never substitute a generation database when the ROM table cannot be resolved.

- [ ] **Step 4: Refine capability evidence**

Add independently reported capabilities for move descriptions and acquisition methods. Keep `MOVE_DETAILS` limited to numeric battle metadata so reports no longer imply that effect text or complete learnability is present.

- [ ] **Step 5: Run GREEN and corpus regression**

Run parser-core tests and regenerate the named corpus report. Confirm official profiles remain selected and unavailable acquisition methods appear as `N/F` or `N/A`, never as complete.

- [ ] **Step 6: Commit**

Commit: `feat: extract ROM move acquisition methods`

### Task 4: Expose complete catalog, ruleset settings, and diagnostics

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/ApiModels.kt`
- Modify: `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexRuntime.kt`
- Modify: `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt`
- Modify: `companion-simulator/src/main/kotlin/com/enrpau/dualscreendex/simulator/EncounterSimulator.kt`
- Test: `companion-server/src/test/kotlin/com/enrpau/dualscreendex/server/ServerContractTest.kt`
- Test: `companion-simulator/src/test/kotlin/com/enrpau/dualscreendex/simulator/EncounterSimulatorTest.kt`

- [ ] **Step 1: Write failing API contract tests**

Assert bootstrap includes ruleset metadata, every species' raw and normalized level-up groups by ruleset, abilities, decoded evolution labels, full encounter slots, move description/effect ID, and acquisition methods. Assert diagnostics include hash, family, capability offsets, raw and normalized selected-species records but no ROM bytes.

- [ ] **Step 2: Write the failing active-ruleset simulator test**

Create two rulesets where only the expanded variant contains move 2. Select it through `CompanionSettings.ruleset`, generate an eligible opponent, and assert its possible history may contain move 2 while Base never can.

- [ ] **Step 3: Confirm RED in server and simulator suites**

Run: `./gradlew :companion-server:test :companion-simulator:test`.

- [ ] **Step 4: Expand the view models**

Add `RulesetView`, `NormalizedMoveView`, `AbilityView`, `EvolutionView`, `EncounterSlotView`, `MoveAcquisitionView`, and `DiagnosticView`. Use capability status to omit unsupported sections rather than rendering implementation prose.

- [ ] **Step 5: Add the temporary Settings override**

`CompanionSettings.ruleset` defaults to `AUTO`. Runtime resolves Auto to the parser-primary ruleset and reports `rulesetAssumed=true`; a valid manual ID is used directly. Rebuild the simulator when selection changes. Invalid IDs return a clear 400 response and preserve the prior selection.

- [ ] **Step 6: Add an on-demand diagnostics endpoint**

Implement `GET /api/diagnostics?speciesId=<id>&moveId=<id>`. Return structured provenance and the selected record only. Do not return copyrighted bulk text, sprites, or raw ROM ranges.

- [ ] **Step 7: Run GREEN**

Run companion-core, simulator, and server tests.

- [ ] **Step 8: Commit**

Commit: `feat: expose catalog variants and diagnostics`

### Task 5: Implement the corrected Thor presentation

**Files:**
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/pages/PokedexDetail.tsx`
- Modify: `companion-web/src/pages/BattlePage.tsx`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/dev/SimulatorPanel.tsx`
- Create: `companion-web/src/pages/MoveDetail.tsx`
- Create: `companion-web/src/learnsets.ts`
- Modify: `companion-web/src/styles.css`
- Test: `companion-web/src/components.test.ts`
- Test: `companion-web/src/pages/PokedexDetail.test.tsx`
- Create: `companion-web/src/pages/MoveDetail.test.tsx`
- Create: `companion-web/src/learnsets.test.ts`

- [ ] **Step 1: Write failing UI tests**

Test an accessible SVG `Seen`/`Not seen` status, `BASE STATS` plus `BST 534`, grouped `Initial · Lv 7` labels, em dashes for zero power/accuracy, move-detail Back behavior, real More sections, and ruleset options with Auto selected by default.

- [ ] **Step 2: Confirm RED**

Run: `npm test` from `companion-web` and verify failures describe missing UI behavior.

- [ ] **Step 3: Replace status CSS geometry with SVG**

Use a 24×24 inline SVG almond outline, pupil, and a conventional eye-off slash. Retain `aria-label`; do not use emoji, icon fonts, or synthetic raster assets.

- [ ] **Step 4: Implement Stats, grouped Moves, and More**

Stats explains generation-aware base values and shows BST. Moves renders one button per normalized move with `Initial` and distinct levels. More renders abilities, evolution targets/conditions, and locations with method, range, and likelihood; unavailable sections are omitted or marked `N/A`/`N/F` by capability semantics.

- [ ] **Step 5: Add the shared move-detail page**

Lift species-detail tab state into `App`, retain battle tab state in the gateway, and route every known move row to `MoveDetail`. Show description, type, category, power, accuracy, PP, priority, and acquisition context using one compact scroll region.

- [ ] **Step 6: Add ruleset and diagnostics controls**

Settings lists Auto plus detected variants. The lab panel shows active/assumed state, fetches selected-record diagnostics, and provides a Clipboard API copy action with a visible copied/error status.

- [ ] **Step 7: Run GREEN and production build**

Run `npm test` and `npm run build`.

- [ ] **Step 8: Commit**

Commit: `feat: complete Thor catalog presentation`

### Task 6: Real-ROM regression and visual acceptance

**Files:**
- Modify: `reports/dualdex-parser-compatibility.json`
- Modify: `reports/dualdex-parser-compatibility.md`
- Modify: `README.md`
- Modify: `docs/images/*-poc.png`

- [ ] **Step 1: Run the complete automated suite**

Run:

```powershell
.\gradlew.bat :parser-core:test :parser-cli:test :companion-core:test :companion-simulator:test :companion-server:test --rerun-tasks
Set-Location companion-web
npm test
npm run build
Set-Location ..
.\gradlew.bat :companion-server:installDist
git diff --check
```

- [ ] **Step 2: Verify Modern Emerald provenance**

Load the existing Modern Emerald ZIP and assert at least two validated level-up variants when present in that binary. Confirm the base variant contains Charizard's original records, the expanded variant contains its extra records, and Kakuna's UI normalization produces one Harden row with Initial and Lv 7.

- [ ] **Step 3: Verify browser behavior at the Thor viewport**

Use Playwright against `http://127.0.0.1:47831`. Exercise Discovered browse, status icons, Stats, Moves, Move Detail, More, Auto/manual ruleset selection, diagnostics copy, single battle, and double battle. Assert no body overflow and zero console errors/warnings.

- [ ] **Step 4: Inspect and publish screenshots**

Capture updated browse, Stats, Moves, Move Detail, More, Settings, and battle screens. Inspect every image for clipping, artifacting, malformed icons, dense layouts, missing ROM sprites, and emoji.

- [ ] **Step 5: Refresh honest documentation and report wording**

Document the ruleset selector, refined capability definitions, diagnostics, and exact remaining memory-mapping boundary. Reports distinguish numeric move details from descriptions and acquisition methods.

- [ ] **Step 6: Commit and publish the draft update**

Commit as `feat: finish catalog truth correction pass`, push `codex/dualdex-parser-spec` to `fork`, and update the existing draft PR. Do not create a public APK release.
