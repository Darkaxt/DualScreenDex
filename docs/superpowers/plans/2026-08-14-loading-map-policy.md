# Loading and map-policy implementation plan

**Goal:** Publish real catalog phases, bind map fog to knowledge mode, and silently handle valid map locations without encounter data.

**Architecture:** Preserve the existing loading model, knowledge-mode setting, normalized world-map catalog, and MAP_AREA command. Change only phase publication, UI projection, and the valid-but-unmapped navigation boundary.

**Tech stack:** Kotlin/JVM runtime and reducer tests; React/TypeScript/Vitest; Vite; existing Playwright map contract.

---

### Task 1: Lock the loading contract RED

**Files:**
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [x] Assert the user-facing module label for known and unknown phase identifiers.
- [x] Assert parser progress dispatches the real phase, completed units, and total units.

### Task 2: Publish and render loading modules

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-web/src/App.tsx`

- [x] Dispatch `CatalogLoadingChanged` from each parser progress callback.
- [x] Render `Loading <module>` with the existing animation and accessible label.

### Task 3: Lock the map-policy contract RED

**Files:**
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [x] Assert no fog toggle exists.
- [x] Assert Discovered is clear and Organic/Hidden use fog.
- [x] Assert Area Dex is disabled for an unmapped location.
- [x] Assert a valid-but-unmapped MAP_AREA request is a silent no-op.

### Task 4: Implement the map policy

**Files:**
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`

- [x] Derive fog visibility from `knowledgeMode`.
- [x] Remove the manual fog state and button.
- [x] Derive whether the selected location has encounter areas and disable Area Dex otherwise.
- [x] Return unchanged state for a valid-but-unmapped MAP_AREA command.

### Task 5: Update the browser contract and verify

**Files:**
- Modify: `companion-web/e2e/map-presentation.spec.ts`

- [x] Drive clear/fog state through knowledge mode instead of a button.
- [x] Run focused Kotlin tests and web tests.
- [x] Run `npm run build`.
- [x] Run the sanitized Playwright map contract if the fixture is present.
- [x] Run `git diff --check`, review the diff, and commit the completed UI/runtime slice.
