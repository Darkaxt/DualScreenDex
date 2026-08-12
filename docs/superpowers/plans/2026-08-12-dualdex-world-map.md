# DualDex World Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a ROM-authentic combined zoomable world map whose Organic fog reveals visited locations and whose cells open a correct Pokédex Area context.

**Architecture:** First establish explicit encounter base identity and an active-versus-current Area API. Then materialize normalized world-map metadata and immutable ROM-rendered assets behind generation-specific fail-closed resolvers, while one web canvas owns overview zoom, later local semantic zoom, fog, and Area navigation.

**Tech Stack:** Kotlin/JVM, parser-core structural resolvers, Gson/SQLite catalog cache, Preact/TypeScript, Vitest/Testing Library, Gradle, Vite.

---

## File Structure

- `parser-core/.../catalog/CatalogModels.kt`: explicit encounter base identity and normalized map catalog records.
- `parser-core/.../catalog/EncounterMaterializer.kt`: classic and expansion materialization with explicit base IDs.
- `parser-core/.../dataset/encounters/EncounterMaterializationProjection.kt`: structurally selected encounter-row projection.
- `parser-core/.../catalog/CatalogParser.kt`: area-name joins and future map materialization/capability publication.
- `parser-core/.../catalog/Gen3WorldMapResolver.kt`: Gen III overview structural chain.
- `parser-core/.../catalog/Gen1WorldMapResolver.kt`: Gen I 2bpp/RLE overview structural chain.
- `parser-core/.../catalog/Gen2WorldMapResolver.kt`: Gen II 2bpp/landmark overview structural chain.
- `parser-core/.../sprite/TileRenderer.kt`: generic 8bpp and tilemap composition primitives.
- `catalog-store/.../CatalogReader.kt`: typed `world_maps` section round trip.
- `catalog-store/.../CatalogSchema.kt`: parser-schema invalidation when persisted model meaning changes.
- `companion-core/.../api/ApiModels.kt`: current/selected/active Area projections and future map API metadata.
- `companion-core/.../battle/RarityEvaluator.kt`: explicit base-area encounter selection.
- `companion-core/.../model/AppModels.kt`: future semantic selection and visited-location knowledge.
- `app/.../knowledge/FileKnowledgeRepository.kt`: future visited-area persistence/migration.
- `app/.../web/AndroidLoopbackServer.kt`: future immutable map PNG endpoint.
- `companion-web/src/models.ts`: typed Area/map state.
- `companion-web/src/pages/PokedexBrowse.tsx`: Area context chip and active Area filtering.
- `companion-web/src/pages/PokemonDetail.tsx`: Area-row actions that preserve species context and center the map.
- `companion-web/src/pages/WorldMapPage.tsx`: future single semantic-zoom canvas.
- `companion-web/src/styles.css`: existing-theme Area chip and future map controls/fog.

## Stage 0: Area Correctness and Identity

### Task 1: Lock local-observation Area semantics

**Files:**
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`

- [ ] **Step 1: Replace the captured-override test with three failing regressions**

Create one shared fixture with species 1–4 and base area `0x0010`, then assert these independent behaviors:

```kotlin
@Test
fun caughtSpeciesWithoutALocalObservationIsAbsentFromAreaSpecies() {
    val state = ApiViewBuilder.state(
        snapshot(caught = setOf(4), observedHere = setOf(1, 2)),
        catalog(),
    )
    assertEquals(listOf(1, 2), state.activeAreaSpeciesIds)
}

@Test
fun locallyObservedSpeciesIsPresentWithoutBeingCaught() {
    val state = ApiViewBuilder.state(snapshot(observedHere = setOf(2)), catalog())
    assertEquals(listOf(2), state.activeAreaSpeciesIds)
}

@Test
fun caughtAndLocallyObservedSpeciesRemainsPresentBecauseOfTheObservation() {
    val state = ApiViewBuilder.state(
        snapshot(caught = setOf(2), observedHere = setOf(2)),
        catalog(),
    )
    assertEquals(listOf(2), state.activeAreaSpeciesIds)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :companion-core:test --tests "*ApiViewBuilderTest*"`

Expected: the caught-only assertion fails because species 4 is still unioned into the Area result; new active Area fields also fail to compile until Task 3 establishes them.

- [ ] **Step 3: Remove the caught union**

Project only locally observed, navigable species for the active base ID:

```kotlin
val activeAreaSpeciesIds = activeAreaBaseId?.let { baseId ->
    val navigableIds = catalog?.navigableSpecies()?.mapTo(mutableSetOf()) { it.id }.orEmpty()
    snapshot.ledger.seenSpeciesByArea[baseId].orEmpty()
        .filter { it in navigableIds }
        .sorted()
}.orEmpty()
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew :companion-core:test --tests "*ApiViewBuilderTest*"`

Expected: all three observation-authority regressions pass.

- [ ] **Step 5: Commit the behavior fix with Task 3's coherent API changes**

Do not commit a transient API that still calls the active set `currentAreaSpeciesIds`; complete Tasks 2–4 and commit the coherent Stage 0 slice.

### Task 2: Make base area identity explicit

**Files:**
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/EncounterMaterializerTest.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/encounters/EncounterMaterializationProjectionTest.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/ModernEmeraldEncounterLiveRomTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/EncounterMaterializer.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/encounters/EncounterMaterializationProjection.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/battle/RarityEvaluatorTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/battle/RarityEvaluator.kt`
- Modify: every test fixture constructing `EncounterArea`

- [ ] **Step 1: Write failing explicit-identity assertions**

Replace derived assertions such as:

```kotlin
assertEquals(listOf(0x201, 0x202, 0x203), result.areas.map { it.id / 10 })
```

with:

```kotlin
assertEquals(listOf(0x201, 0x202, 0x203), result.areas.map { it.baseAreaId })
```

Add an expansion assertion whose encoded encounter ID cannot be inverted by `/ 10`:

```kotlin
assertEquals(expectedBaseId, expansionArea.baseAreaId)
assertNotEquals(expectedBaseId, expansionArea.id / 10)
```

Add a rarity regression with an intentionally non-classic ID and matching `baseAreaId`; expect the matching area to apply.

- [ ] **Step 2: Run parser and rarity tests and verify RED**

Run: `./gradlew :parser-core:test :companion-core:test --tests "*EncounterMaterializerTest*" --tests "*EncounterMaterializationProjectionTest*" --tests "*RarityEvaluatorTest*"`

Expected: compilation fails because `baseAreaId` does not exist.

- [ ] **Step 3: Add the required catalog field and set it at every producer**

Use a required field with no compatibility default:

```kotlin
data class EncounterArea(
    val id: Int,
    val baseAreaId: Int,
    val name: CatalogField<String>,
    val methodId: Int,
    val slots: List<EncounterSlot>,
    val windows: Set<EncounterWindow> = setOf(EncounterWindow.ANY),
)
```

Every classic helper passes its `baseId`; expansion materialization passes the original group/map base ID before encoding time/method dimensions into `id`.

- [ ] **Step 4: Replace identity reconstruction in production paths**

Use the field directly:

```kotlin
rawEncounters.mapTo(linkedSetOf(), EncounterArea::baseAreaId)
namesByBaseId[area.baseAreaId]
encounterAreas.filter { area -> area.baseAreaId == baseId }
```

Apply this to catalog area-name resolution, API current/active lookups, and `RarityEvaluator`.

- [ ] **Step 5: Update fixtures explicitly**

Every `EncounterArea` fixture declares a base ID. Do not infer it with a shared test helper for expansion tests; the fixture must make encoded row ID versus base location visible.

- [ ] **Step 6: Run parser and rarity tests and verify GREEN**

Run: `./gradlew :parser-core:test :companion-core:test --tests "*EncounterMaterializerTest*" --tests "*EncounterMaterializationProjectionTest*" --tests "*RarityEvaluatorTest*"`

Expected: all selected tests pass and no production `area.id / 10` lookup remains.

### Task 3: Project current and selected Area contexts

**Files:**
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-server/src/test/kotlin/com/enrpau/dualscreendex/server/ApiViewBuilderTest.kt`

- [ ] **Step 1: Write failing current/selected context tests**

Cover a current base with two encounter methods and a selected row in a different base:

```kotlin
assertEquals(currentBaseId, state.currentAreaBaseId)
assertEquals("Route 101", state.currentAreaName)
assertEquals(selectedBaseId, state.activeAreaBaseId)
assertEquals("Oldale Town", state.activeAreaName)
assertEquals(selectedRowIds, state.activeAreaIds)
assertEquals(false, state.activeAreaIsCurrent)
```

Add a same-base selection and assert `activeAreaIsCurrent == true`. Add an invalid selection and assert fail-closed fallback to current.

- [ ] **Step 2: Run the focused API tests and verify RED**

Run: `./gradlew :companion-core:test :companion-server:test --tests "*ApiViewBuilderTest*"`

Expected: active Area fields are missing and selected rows do not affect the filter context.

- [ ] **Step 3: Add active Area fields**

Extend `StateView` with:

```kotlin
val activeAreaIds: List<Int>,
val activeAreaBaseId: Int?,
val activeAreaName: String?,
val activeAreaSpeciesIds: List<Int>,
val activeAreaIsCurrent: Boolean,
```

Resolve the selected row only from the active catalog. A missing row falls back to current. Resolve display name from `runtimeMetadata.areaNamesByBaseId`, then from the matching encounter rows after removing the parser's `" - method"` suffix only when the prefix is unambiguous.

- [ ] **Step 4: Preserve literal current fields**

Keep current fields tied to the validated live/offline source. Do not overwrite them with a selected location. This lets the toolbar mark the active context accurately and gives the later map a stable current outline.

- [ ] **Step 5: Run the focused API tests and verify GREEN**

Run: `./gradlew :companion-core:test :companion-server:test --tests "*ApiViewBuilderTest*"`

Expected: current, non-current selected, same-current selected, and invalid-selected cases pass.

### Task 4: Expose base identity and render the Area toolbar chip

**Files:**
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write failing catalog/API and UI tests**

Backend:

```kotlin
assertEquals(baseId, ApiViewBuilder.catalog(catalog).areas.single().baseAreaId)
```

Web current context:

```tsx
render(<PokedexBrowse catalog={catalog} state={{
  ...state,
  filter: 'AREA',
  activeAreaName: 'Route 101',
  activeAreaIsCurrent: true,
  activeAreaIds: [10],
  activeAreaSpeciesIds: [1],
}} send={vi.fn()} />)
expect(screen.getByText('Route 101')).toBeTruthy()
expect(screen.getByText('CURRENT')).toBeTruthy()
```

Web selected context asserts `Oldale Town` is rendered and `CURRENT` is absent. A normal Area state with no map metadata still renders the chip from Area state, proving no dependency on map art.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :companion-core:test --tests "*ApiViewBuilderTest*"`

Run: `npm test -- --run src/pages/PokedexBrowse.test.tsx` from `companion-web`.

Expected: backend `baseAreaId` and UI context elements are missing.

- [ ] **Step 3: Expose typed fields and switch Area filtering to active context**

Add `baseAreaId` to `AreaView` and `Catalog.areas`. Add active fields to the TypeScript `State`. Use active fields for Area enablement, local-observation filtering, and encounter-window icons, retaining a compatibility fallback to current fields while cached clients transition.

- [ ] **Step 4: Render an accessible existing-theme chip**

Inside `.browse-tools`, after the filter strip:

```tsx
{activeFilter === 'AREA' && activeAreaName && <div class="area-context" aria-label="Area filter location">
  <svg class="area-context-pin" aria-hidden="true" viewBox="0 0 24 24">
    <path d="M12 21s6-5.3 6-11a6 6 0 1 0-12 0c0 5.7 6 11 6 11Z" />
    <circle cx="12" cy="10" r="2.2" />
  </svg>
  <span><small>AREA</small><strong>{activeAreaName}</strong></span>
  {activeAreaIsCurrent && <b>CURRENT</b>}
</div>}
```

Style it as compact paper/forest instrument chrome. Do not add map controls, empty map placeholders, animation, or new fonts in Stage 0.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `./gradlew :companion-core:test --tests "*ApiViewBuilderTest*"`

Run: `npm test -- --run src/pages/PokedexBrowse.test.tsx` from `companion-web`.

Expected: API and current/selected/unsupported-map UI cases pass.

### Task 5: Invalidate incompatible catalog caches

**Files:**
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`

- [ ] **Step 1: Update the schema-version expectation first**

```kotlin
assertEquals(7, CatalogSchema.parserSchemaVersion)
```

- [ ] **Step 2: Run the focused store test and verify RED**

Run: `./gradlew :catalog-store:test --tests "*CatalogStoreTest*"`

Expected: version assertion reports 6 instead of 7.

- [ ] **Step 3: Advance parser schema version**

Set `CatalogSchema.parserSchemaVersion = 7`. Existing migration logic will discard parser databases carrying version 6, preventing absent `baseAreaId` values from becoming zero.

- [ ] **Step 4: Run store tests and verify GREEN**

Run: `./gradlew :catalog-store:test --tests "*CatalogStoreTest*"`

Expected: catalog round-trip and invalidation tests pass.

### Task 6: Verify and commit Stage 0

**Files:**
- Verify every Stage 0 source/test file above.

- [ ] **Step 1: Prove identity arithmetic is gone from relevant production paths**

Run: `rg -n "area\\.id / 10|it\\.id / 10" parser-core/src/main companion-core/src/main`

Expected: no matches.

- [ ] **Step 2: Run complete affected module tests**

Run: `./gradlew :parser-core:test :catalog-store:test :companion-core:test :companion-server:test :app:testDebugUnitTest`

Expected: all affected Kotlin/JVM and Android unit tests pass.

- [ ] **Step 3: Run the complete web suite and production build**

Run from `companion-web`: `npm test -- --run && npm run build`

Expected: all Vitest tests pass and Vite emits the production bundle.

- [ ] **Step 4: Build the Android debug artifact**

Run: `./gradlew :app:assembleDebug`

Expected: Gradle exits zero and produces the debug APK. Do not install or launch it in this stage.

- [ ] **Step 5: Review the diff against Stage 0 scope**

Run: `git diff --check && git status --short && git diff --stat HEAD`

Expected: no whitespace errors; only Stage 0 and its tests are present; no world-map raster/resolver source exists.

- [ ] **Step 6: Commit Stage 0**

```bash
git add parser-core catalog-store companion-core companion-server companion-web app
git commit -m "feat: establish correct area context"
```

## Stage 1: Gen III Region Overview

### Task 7: Add normalized map models and cache section

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] **Step 1: Write a failing round-trip test**

Construct two cells sharing one location plus a nonempty raster and assert region key, dimensions, asset key, display name, base IDs, geometry, and every RGBA pixel survive write/read exactly. Assert a dangling asset key and mismatched dimensions are rejected.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :catalog-store:test --tests "*CatalogStoreTest*"`

Expected: world-map types/section are missing.

- [ ] **Step 3: Add immutable normalized models and required `world_maps` section**

Use the structures in the design spec, add `worldMaps` and its immutable rendered assets to `ParsedCatalog`, add a typed codec entry, include it in required sections, validate each key and raster dimension, and advance the parser schema version.

- [ ] **Step 4: Run and verify GREEN**

Run: `./gradlew :catalog-store:test --tests "*CatalogStoreTest*"`

Expected: round trip and schema invalidation pass.

### Task 8: Add the Gen III 8bpp compositor

**Files:**
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/sprite/TileRendererTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/TileRenderer.kt`

- [ ] **Step 1: Write exact-pixel failing fixtures**

Cover palette index 0/255, horizontal/vertical flips, adjacent tiles, invalid indices, truncated tiles, and BGR555 alpha/color conversion.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :parser-core:test --tests "*TileRendererTest*"`

Expected: GBA 8bpp composition API is missing.

- [ ] **Step 3: Add only the validated generic primitives**

Decode one 64-byte 8bpp tile to palette indices and compose bounded 16-bit tilemap entries into `RgbaSprite`. Reject out-of-range tiles or palettes; do not clamp.

- [ ] **Step 4: Run and verify GREEN**

Run: `./gradlew :parser-core:test --tests "*TileRendererTest*"`

Expected: exact pixels and every rejection case pass.

### Task 9: Resolve the Gen III region map structurally

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3WorldMapResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3WorldMapResolverTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3MapLocationResolver.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`

- [ ] **Step 1: Write synthetic RED fixtures**

Build a minimal ROM image containing a proven map-header/section chain and one co-referenced gfx/tilemap/palette cluster. Add independent tests for truncated LZ, invalid tile index, palette overflow, overlay outside the grid, two authoritative clusters, and a decoy unreferenced cluster.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :parser-core:test --tests "*Gen3WorldMapResolverTest*"`

Expected: resolver and `WORLD_MAP` capability are missing.

- [ ] **Step 3: Implement the smallest structural chain**

Reuse the current `gMapGroups` and region-entry proof, retain base-to-section mappings, query the shared GBA reference index for bounded co-reference neighborhoods, validate one authoritative asset triple, render the raster, and emit normalized locations/cells.

- [ ] **Step 4: Publish fail-closed capability evidence**

Add `RomCapability.WORLD_MAP`. Available output includes structural reasons and counts; absent dependencies report `NOT_FOUND`; competing authoritative clusters report `AMBIGUOUS`; no fallback map is produced.

- [ ] **Step 5: Run and verify GREEN**

Run: `./gradlew :parser-core:test --tests "*Gen3WorldMapResolverTest*" --tests "*CatalogParserTest*"`

Expected: valid fixture materializes; all malformed/ambiguous fixtures fail closed.

### Task 10: Persist visited areas

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepository.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepositoryTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write RED persistence and transition tests**

Assert a validated `LiveAreaChanged` records a visit without a battle, repeated visits deduplicate, a ROM switch isolates visits, schema-1 knowledge seeds from current/observed bases, and a cold reload retains the set.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :companion-core:test :app:testDebugUnitTest --tests "*CompanionGatewayTest*" --tests "*FileKnowledgeRepositoryTest*" --tests "*ProductionCompanionRuntimeTest*"`

Expected: `visitedAreaBaseIds` is missing.

- [ ] **Step 3: Add ROM-scoped knowledge schema 2**

Add `visitedAreaBaseIds`, migrate proven visits only, and persist on validated current-area changes and matched save import. Do not derive visits from catalog encounter membership.

- [ ] **Step 4: Run and verify GREEN**

Run the Step 2 command.

Expected: transition, migration, isolation, and cold-reload tests pass.

### Task 11: Serve immutable map assets and metadata

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt`

- [ ] **Step 1: Write RED API/HTTP tests**

Assert metadata contains region/location/cell/base-ID data but no pixel arrays; valid asset keys return `image/png` and catalog ETag; invalid/traversal keys return 404; unavailable maps publish no URL.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :companion-core:test :app:testDebugUnitTest --tests "*ApiViewBuilderTest*" --tests "*AndroidLoopbackServerTest*"`

Expected: map views and route are missing.

- [ ] **Step 3: Add bounded metadata and asset route**

Project normalized catalog values and serve only catalog-owned asset keys. Reuse PNG encoding and route-response patterns from sprites.

- [ ] **Step 4: Run and verify GREEN**

Run the Step 2 command.

Expected: metadata, ETag, MIME, missing, and traversal cases pass.

### Task 12: Build the combined Gen III overview page

**Files:**
- Create: `companion-web/src/pages/WorldMapPage.tsx`
- Create: `companion-web/src/pages/WorldMapPage.test.tsx`
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/components.test.ts`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Modify: `companion-web/src/pages/PokemonDetail.tsx`
- Modify: `companion-web/src/pages/PokemonDetail.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write RED presentation and interaction tests**

Assert the full-screen ROM raster and existing left-aligned two-line `Region` / active-place header, including a `CURRENT` badge only when the active area matches validated current memory. Assert that centered or inline titles, a floating place card, instructional copy, a permanent place rail, and a bottom toolbar are absent. Assert fixed, visible, unclipped zoom out/in/recenter controls and the global Pokédex shortcut at upper right; `Layers`/legend at upper left; and the contextual existing-Pokédex-icon Area action directly below Layers only when a revealed current/selected place is active. Entity-layer controls must be absent without structurally proven entity data.

Assert fit-to-view, pinch scale, bounded pan, pan-not-tap discrimination, an independently highlighted selection plus persistent current-location ring, Discovered reveal, and unavailable-map fallback. Organic fog must be solid black at every outer edge with a natural inward gradient; unrevealed art, labels, and hit targets must not exist interactively. Assert location selection followed by the contextual Area Pokédex action dispatches a validated semantic key and activates Area. Assert an accessible top-right `Open Map` icon in Pokédex and global `Open Pokédex` icon in Map, including tooltips and a visibly disabled Map shortcut when no structural map is available. Assert every bound Pokémon Detail Area row offers `Show on Map`, centers/highlights the selected location, and retains the species and Area tab after a Map → Pokédex return.

- [ ] **Step 2: Run and verify RED**

Run: `npm test -- --run src/pages/WorldMapPage.test.tsx src/pages/PokemonDetail.test.tsx`

Expected: page and models are missing.

- [ ] **Step 3: Implement the one-canvas overview**

Use pointer events with one transform state, pixelated raster rendering, a semantic overlay layer, and an opaque Organic fog layer whose outer boundary remains fully black. A pointer sequence that pans cannot dispatch selection. Unrevealed locations render neither art nor labels nor interactive buttons. Keep fixed controls outside the transformed canvas and respect their measured bounds so zoom and pan cannot clip them.

- [ ] **Step 4: Wire map selection to Area**

Selecting a revealed place highlights and recenters it without hiding the independent current ring. The contextual Pokédex icon dispatches the catalog-owned location key, lets the backend validate/resolve it, then routes to Pokédex with Area active. Do not send arbitrary base-ID arrays from the browser. Omit the contextual action when no revealed current/selected semantic place exists.

- [ ] **Step 5: Add symmetric navigation without clearing context**

Extend the shared header end-action slot using the established icon-button styling. Map/Pokédex shortcut actions may change only `screen`; they must preserve `selectedAreaId`, `filter`, current/active Area fields, and the in-memory map viewport. Add round-trip tests for Map → Pokédex → Map and Pokédex → Map → Pokédex with a selected non-current Area, plus a current-Area control.

- [ ] **Step 6: Add Pokémon Detail Area-to-map navigation**

Resolve each Area row to a catalog-owned semantic location key on the backend. The row action stores a map focus request while retaining the open species/detail tab, opens Map, centers/highlights the location, and restores that species context through the reverse shortcut. Invalid or absent bindings fail closed.

Pending explicit presentation approval, a later replacement for this row-action presentation embeds the same ROM-derived map in every Pokémon detail `AREA` tab and highlights every location actually observed for that species. Before implementing it, add RED tests for simultaneous `seenSpeciesByArea` highlights, caught-only/starter/gift/trade exclusion, observation-implied reveal under Organic fog, selection/centering with preserved Pokémon context, contextual Area Pokédex handoff and back-state restoration, and the exact zero-result copy `No known locations yet.` Do not expose parsed potential encounters in Discovered mode without separate semantic approval.

- [ ] **Step 7: Run and verify GREEN**

Run: `npm test -- --run src/pages/WorldMapPage.test.tsx src/pages/PokemonDetail.test.tsx src/pages/PokedexBrowse.test.tsx src/App.production.test.tsx src/components.test.ts`

Expected: map interactions, Area handoff, accessible shortcuts, disabled fallback, and context-preserving round trips pass.

### Task 13: Verify Stage 1 live contract

**Files:**
- Modify: `docs/v1-delivery-ledger.md` only after live evidence exists.

- [ ] **Step 1: Run full automated verification**

Run the complete affected Gradle tests, `:app:assembleDebug`, complete Vitest suite, and Vite build.

- [ ] **Step 2: Run source-available ROM controls**

Compare parsed dimensions, cells, names, and raster pixels against symbol-backed official Gen III references, then run at least one structurally compatible hack without title/SHA exceptions.

- [ ] **Step 3: Validate the target-device flow**

Install only when explicitly authorized for this stage. Verify route transition outline, visit persistence after cold restart, Organic unrevealed cells, cell-to-Area navigation, both shortcut directions without Area-context loss, and Treecko absence without a local observation.

- [ ] **Step 4: Record exact evidence and commit Stage 1**

Document ROM class, parser outcome, device flow, and unsupported cases. Do not call an APK publicly released unless the release workflow actually published it.

## Stage 2: Gen I and Gen II Overviews

### Task 14: Add Gen I town-map resolution

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen1WorldMapResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen1WorldMapResolverTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`

- [ ] **Step 1: Write RED structural fixtures**

Cover exact nibble-RLE expansion to 18 by 18, 2bpp pixels, valid outdoor/indoor coordinate-name records, bad terminator, overflow, invalid bank pointer, inadequate map-ID coverage, and two authoritative tables.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :parser-core:test --tests "*Gen1WorldMapResolverTest*"`

Expected: resolver is missing.

- [ ] **Step 3: Implement anchor-cell output only**

Resolve the uniquely referenced asset/table chain and emit one-cell anchors. Do not synthesize route widths or polygons.

- [ ] **Step 4: Run and verify GREEN**

Run the Step 2 command.

Expected: valid fixture renders and every invalid/ambiguous fixture fails closed.

### Task 15: Add Gen II multi-region resolution

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen2WorldMapResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen2WorldMapResolverTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `companion-web/src/pages/WorldMapPage.tsx`
- Modify: `companion-web/src/pages/WorldMapPage.test.tsx`

- [ ] **Step 1: Write RED resolver and UI tests**

Cover 48-tile decompression, 19 by 19 maps, four-byte landmark records, header-to-landmark joins, invalid pointers/coordinates, unique Johto and Kanto output, region selector state, and current-region selection.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :parser-core:test --tests "*Gen2WorldMapResolverTest*"`

Run from `companion-web`: `npm test -- --run src/pages/WorldMapPage.test.tsx`

Expected: resolver and region selector are missing.

- [ ] **Step 3: Implement shared-model output and in-page region selection**

Emit point/cell anchors only, reuse the Stage 1 API/assets/fog, and preserve independent viewport state per region.

- [ ] **Step 4: Run and verify GREEN**

Run the Step 2 commands.

Expected: both regions resolve and switch without page navigation.

### Task 16: Run the mandatory exact 50-ROM gate

**Files:**
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/WorldMapCorpusTest.kt`
- Create: `docs/evidence/world-map-first-50.md`

- [ ] **Step 1: Freeze and verify the input set**

Read the exact first 50 unique ROMs in manifest order. Recompute every SHA-256 from file bytes immediately before parsing and record manifest ordinal, path-independent display identity, byte length, and digest. Never deduplicate by filename.

- [ ] **Step 2: Run every ROM twice through the production parser and asset pipeline**

For each pass validate a nonempty ROM-derived raster, exact dimensions, bounded location geometry, at least one valid `baseAreaId` binding, cache round trip, PNG serving, and the three navigation paths. Capture capability status and evidence. Require the second pass to produce byte-identical normalized metadata, raster pixels, and PNG bytes.

- [ ] **Step 3: Preserve the established parser contract**

Run the existing exact first-33 parser/family/reference controls unchanged. Fail the corpus gate on any new ambiguity, budget, error, family classification, reference-index, or parser regression even if the nominal map success total remains high.

- [ ] **Step 4: Report all 50 statuses and enforce the threshold**

Write one row per manifest ROM with exact digest, family, map result, pipeline checks, repeat result, and failure reason. Count `SUCCESS` only when every criterion passes. Require `SUCCESS >= 25`; otherwise stop with an explicit no-ship result.

- [ ] **Step 5: Run SHA-bound official controls**

For one legitimate official Gen I ROM, one legitimate official Gen II ROM, and Modern Emerald, bind acceptance evidence to the freshly computed SHA while keeping production resolution generic. Verify real ROM-derived raster/location data, cache/API round trip, Organic fog, map-to-Area, and Pokémon Area-to-map centering.

## Stage 3: Local Semantic Zoom

### Task 17: Materialize structurally validated Gen III local maps

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3LocalMapResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3LocalMapResolverTest.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogMigration.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogWriter.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`

- [ ] **Step 1: Write RED local-layout fixtures**

Cover layout dimensions, block grid, primary/secondary tilesets, metatile layers, palette banks, invalid block/metatile/tile indices, missing secondary data, pathological dimensions, and ambiguous layout pointers.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :parser-core:test :catalog-store:test --tests "*Gen3LocalMapResolverTest*" --tests "*CatalogStoreTest*"`

Expected: resolver and BLOB assets are missing.

- [ ] **Step 3: Render static proven state and persist BLOB assets**

Compose only validated static layers. Dynamic callback state remains absent rather than guessed. Store PNG bytes keyed by catalog-owned asset IDs.

- [ ] **Step 4: Run and verify GREEN**

Run the Step 2 command.

Expected: exact raster and asset round trip pass; malformed layouts fail closed per map.

### Task 18: Add same-canvas local semantic zoom

**Files:**
- Modify: `companion-web/src/pages/WorldMapPage.tsx`
- Modify: `companion-web/src/pages/WorldMapPage.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write RED semantic-transition tests**

Assert zooming over a selected/current location switches to its local asset in the same page, backing out restores the overview viewport, unavailable local assets retain overview pixel zoom, and region selection survives the transition.

- [ ] **Step 2: Run and verify RED**

Run: `npm test -- --run src/pages/WorldMapPage.test.tsx`

Expected: semantic zoom transition is missing.

- [ ] **Step 3: Add the local render layer without coordinates**

Keep one transform/gesture owner and swap normalized render layers at the semantic threshold. Do not draw a player dot or tile fog yet.

- [ ] **Step 4: Run and verify GREEN**

Run the Step 2 command.

Expected: all overview/local transitions and fallbacks pass.

### Task 19: Add structurally resolved live coordinates and granular fog

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/LiveCoordinateLayoutResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/LiveCoordinateLayoutResolverTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-web/src/pages/WorldMapPage.tsx`

- [ ] **Step 1: Write RED structural/runtime/persistence tests**

Assert coordinate layout uniqueness, bounded values, map-transition reset, per-map visited cells, persistence, Organic-only masking, and absence of a marker when coordinate evidence is unavailable.

- [ ] **Step 2: Run and verify RED**

Run the focused resolver, runtime, repository, and WorldMapPage tests.

Expected: coordinate and cell-visit models are missing.

- [ ] **Step 3: Resolve coordinates without fixed absolute offsets**

Publish coordinate layouts only from structural ROM evidence and read them through the existing memory transport. Invalid or ambiguous evidence disables granular behavior while retaining area-level fog.

- [ ] **Step 4: Persist visited local cells and render exact marker/fog**

Key cells by ROM, base area, and local map identity. Reveal only proven visited cells in Organic mode and draw the player marker only from a current valid sample.

- [ ] **Step 5: Run full automated and live verification**

Verify no fixed title/SHA/offset exceptions, cold-restart persistence, map transitions, coordinate loss fallback, and the actual same-canvas experience on target hardware.
