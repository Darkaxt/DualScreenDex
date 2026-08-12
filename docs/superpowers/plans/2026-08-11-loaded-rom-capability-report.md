# Loaded-ROM Capability Report Implementation Plan

> **For Codex:** Execute this plan with test-driven development. Keep the capability page read-only and independent from the memory mapper.

**Goal:** Add a Settings > Debug capability report page that presents exact parser evidence for the loaded ROM, including partial coverage and manual-review state.

**Architecture:** Extend immutable parser capability evidence with optional structured coverage fields, expose those fields through the existing local `/api/diagnostics` response, and render the response in a dedicated Preact page opened from Settings. Preserve backward compatibility by adding only defaulted/nullable fields to persisted Kotlin models and by treating missing fields as unresolved rather than complete.

**Tech stack:** Kotlin/JVM parser and companion core, kotlinx serialization/storage adapters already used by the project, Preact + TypeScript, Vitest/Testing Library, Gradle.

---

## Task 1: Publish structured partial-coverage evidence

**Files:**

- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestratorTest.kt`
- Add: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/model/CapabilityEvidenceTest.kt`

### Step 1: Write failing evidence compatibility tests

Add tests proving that `CapabilityEvidence`:

- accepts `validRecords`, `totalRecords`, `elementSize`, and `reviewStatus` as nullable/defaulted trailing properties;
- labels a selected 7/10 table as partial/manual review without setting `compatible=false`;
- preserves legacy construction sites and unavailable capability completion.

Use an explicit model such as:

```kotlin
CapabilityEvidence(
    capability = RomCapability.LEARNSETS,
    compatible = true,
    confidence = 0.70,
    validRecords = 7,
    totalRecords = 10,
    reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
)
```

### Step 2: Run the focused tests and observe RED

Run:

```powershell
./gradlew :parser-core:test --tests '*CapabilityEvidenceTest' --tests '*ParserOrchestratorTest'
```

Expected: compilation or assertions fail because the structured fields do not exist.

### Step 3: Implement the evidence fields

Add a stable `CapabilityReviewStatus` enum (`NONE`, `MANUAL_REVIEW`) and defaulted trailing fields to `CapabilityEvidence`. Populate structured counts and element size from the selected layout/validation evidence in `FamilyParsers`; never infer them by parsing human-readable reasons. Preserve `AVAILABLE` for complete compatible evidence and emit explicit review metadata for usable incomplete evidence.

### Step 4: Run focused and parser tests

Run:

```powershell
./gradlew :parser-core:test --tests '*CapabilityEvidenceTest' --tests '*ParserOrchestratorTest' --tests '*FamilyParsers*'
./gradlew :parser-core:test
```

Expected: PASS.

### Step 5: Commit

```powershell
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestratorTest.kt parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/model/CapabilityEvidenceTest.kt
git commit -m "feat: expose structured parser coverage evidence"
```

## Task 2: Extend the local diagnostics contract

**Files:**

- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/gateway.ts`
- Modify: `companion-web/src/gateway.test.ts`

### Step 1: Write failing DTO and gateway tests

Add Kotlin assertions that a partial 7/10 capability produces:

```json
{
  "status": "PARTIAL",
  "validRecords": 7,
  "totalRecords": 10,
  "elementSize": 4,
  "reviewStatus": "MANUAL_REVIEW"
}
```

Add TypeScript tests proving `diagnostics()` returns a typed `DiagnosticView` and propagates HTTP errors without converting `N/F` to `N/A`.

### Step 2: Run focused tests and observe RED

Run:

```powershell
./gradlew :companion-core:test --tests '*ApiViewBuilderTest'
Set-Location companion-web
npm test -- --run src/gateway.test.ts
```

### Step 3: Extend the DTO without exposing private data

Add nullable `validRecords`, `totalRecords`, and `elementSize`, plus a stable `reviewStatus` string, to `DiagnosticCapabilityView`. Derive display status as:

- `PARTIAL` when compatible evidence is incomplete or marked `MANUAL_REVIEW`;
- the parser status for complete, ambiguous, not-found, or not-applicable evidence.

Add `DiagnosticView` and `DiagnosticCapability` interfaces to `models.ts`. Change `diagnostics()` from `Promise<unknown>` to `Promise<DiagnosticView>`. Do not add ROM bytes, paths, save data, memory contents, or knowledge history.

### Step 4: Run the focused suites

Run:

```powershell
./gradlew :companion-core:test --tests '*ApiViewBuilderTest'
Set-Location companion-web
npm test -- --run src/gateway.test.ts
```

Expected: PASS.

### Step 5: Commit

```powershell
git add companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt companion-web/src/models.ts companion-web/src/gateway.ts companion-web/src/gateway.test.ts
git commit -m "feat: publish loaded ROM diagnostics"
```

## Task 3: Build the dedicated capability page

**Files:**

- Add: `companion-web/src/pages/CapabilityReportPage.tsx`
- Add: `companion-web/src/pages/CapabilityReportPage.test.tsx`
- Modify: `companion-web/src/styles.css`
- Modify: `companion-web/src/layoutStyles.test.ts`

### Step 1: Write page behavior tests

Cover:

- ROM identity, family, platform, CRC32, abbreviated SHA-256, ruleset, and assumed marker;
- compact rows in capability enum order;
- `AVAILABLE`, 70% `PARTIAL`, `AMBIGUOUS`, `NOT FOUND`, and `N/A`;
- exact `7 / 10 records` and `70.0%` rendering;
- `N/F` for missing values and `N/A` only for fields that do not apply;
- expandable offset/record-size/element-size/confidence/reasons details;
- malformed capability isolation;
- loading/retry/error states;
- stable `COPY REPORT` JSON excluding raw bytes, save data, memory dumps, paths, and knowledge history.

### Step 2: Run the page test and observe RED

```powershell
Set-Location companion-web
npm test -- --run src/pages/CapabilityReportPage.test.tsx
```

### Step 3: Implement the compact handheld page

Use the existing `Header`, `paper-panel`, and scroll-region patterns. Fetch diagnostics on mount and whenever the active ROM hash/progress marker changes. Atomically replace the entire view; never merge capability rows from two hashes. Keep rows collapsed by default and limit copy feedback to a small inline status.

### Step 4: Add bounded responsive styling

Keep the page within the AYN Thor companion viewport: single-column cards, no fixed dual-battle gap, no bottom navigation, wrapped hashes/reasons, and touch targets consistent with existing detail pages.

### Step 5: Run page, layout, and complete web tests

```powershell
npm test -- --run src/pages/CapabilityReportPage.test.tsx src/layoutStyles.test.ts
npm test
npm run build
```

Expected: PASS and no viewport overflow assertion failures.

### Step 6: Commit

```powershell
git add companion-web/src/pages/CapabilityReportPage.tsx companion-web/src/pages/CapabilityReportPage.test.tsx companion-web/src/styles.css companion-web/src/layoutStyles.test.ts
git commit -m "feat: add loaded ROM capability page"
```

## Task 4: Add the Settings Debug row and navigation

**Files:**

- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/pages/SettingsPage.production.test.tsx`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/App.production.test.tsx`

### Step 1: Write failing navigation tests

Assert that the existing bottom group is labeled `DEBUG`, contains separate `CAPABILITY REPORT` and `CAPTURE MEMORY REPORT` buttons, and each opens only its own dedicated page. Assert `CAPABILITY REPORT` is disabled and reads `NO ROM LOADED` without a catalog. Assert the memory mapper confirmation/capture path is unchanged.

### Step 2: Run and observe RED

```powershell
Set-Location companion-web
npm test -- --run src/pages/SettingsPage.production.test.tsx src/App.production.test.tsx
```

### Step 3: Implement independent navigation state

Add `capabilityReportOpen` beside `mapperOpen`, pass `onOpenCapabilities` into `SettingsPage`, and render `CapabilityReportPage` before ordinary screen routing. Opening the capability page must not enable, poll, or navigate to the memory mapper.

### Step 4: Run focused and full web verification

```powershell
npm test -- --run src/pages/SettingsPage.production.test.tsx src/App.production.test.tsx src/pages/MemoryMapperPage.test.tsx
npm test
npm run build
```

Expected: PASS.

### Step 5: Commit

```powershell
git add companion-web/src/pages/SettingsPage.tsx companion-web/src/pages/SettingsPage.production.test.tsx companion-web/src/App.tsx companion-web/src/App.production.test.tsx
git commit -m "feat: link capability report from debug settings"
```

## Task 5: End-to-end verification and release evidence

**Files:**

- Modify if needed: `README.md`
- Modify if needed: `docs/compatibility/confirmed-offline-support.md`
- Add release evidence under the project's existing report location only; do not copy ROMs or memory dumps into Git.

### Step 1: Verify parser and companion modules

```powershell
./gradlew :parser-core:test :parser-cli:test :companion-core:test :app:testDebugUnitTest :app:assembleDebug
Set-Location companion-web
npm test
npm run build
```

### Step 2: Verify live diagnostic payloads

For one complete official ROM, one credible partial hack, and one unresolved/ambiguous fixture, confirm that `/api/diagnostics` and the page agree on status, exact counts, offsets, sizes, and review state. Confirm a 70% capability remains usable and only its invalid records are omitted.

### Step 3: Verify production UI behavior

Confirm on the compact viewport that:

- Settings > Debug opens the capability page;
- the report updates atomically when the loaded ROM changes;
- copy output contains no raw/private data;
- the memory report remains independently disabled until enabled;
- no page content escapes the device shell.

### Step 4: Update public documentation

Add a compact README screenshot/feature note only after the production page is verified. Update the compatibility report with confirmed parser evidence, keeping unfinished/partial features visible rather than reporting them as complete.

### Step 5: Commit and publish

```powershell
git add README.md docs/compatibility
git commit -m "docs: publish parser capability reporting"
git push
```

Generate the signed release only through the existing GitHub release workflow. Verify the published APK signer, version, SHA-256, and the Settings > Debug user flow before declaring the release complete.

## Task 6: Refresh the existing GAFT listing after release

**External repository:** `https://github.com/andreyvelsk/GAFT`

GAFT already contains an older `Pokemon game + DualScreenDex` listing. Update that entry instead of adding a duplicate.

### Step 1: Inspect the existing content slug after the release is public

Fork/refresh GAFT, locate the existing DualScreenDex `content/<slug>/index.md`, and create a narrowly scoped update branch. Do not start this step before the signed GitHub release passes runtime verification.

### Step 2: Replace stale project information

Update the existing frontmatter and body with:

- the current one-line companion description;
- category `companion`;
- direct URLs for verified current screenshots or video;
- the signed GitHub release/download link;
- the actual RetroArch and DualDex setup flow;
- AYN Thor docked/top-screen focus guidance;
- current supported generations and parser-capability behavior; and
- truthful known limitations from the release evidence.

Do not claim unverified ROM compatibility or live-memory behavior.

### Step 3: Preview and validate the GAFT contribution

Run GAFT's local preview/build commands, confirm the existing project page renders without broken media or duplicate slugs, and verify every public link resolves.

### Step 4: Commit, push, and open the GAFT pull request

Use the GAFT contribution contract: commit the existing content-page update, push the update branch, and open a PR explaining that it refreshes the already-listed DualScreenDex project for the new signed release. Publication remains controlled by the GAFT maintainer's review and deployment.
