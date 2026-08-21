# Gen III Local Map POI Discovery Implementation Plan

> **Execution:** Implement this plan in order with focused RED/GREEN checks at each stage. Do not publish POI coordinates or identities in Organic mode before the corresponding discovery state is proven.

**Goal:** Add source-backed Gen III local-map places, services, visible items, hidden items, and unknown POIs to the existing Local map, with Organic discovery, ROM/save-scoped filters, and zoom thresholds that default to showing everything at the initial Local-map zoom.

**Architecture:** Extend the normalized `LocalMapCatalog` with immutable parser evidence and stable POI keys. Persist dynamic discovery in `KnowledgeLedger`, keep display preferences in the existing ROM-scoped settings document, project only knowledge-permitted POI state through the API, and render/cull POIs inside the existing connected Local-map scene.

**Tech stack:** Kotlin/JVM parser and companion modules, Gson catalog/ledger/settings persistence, Preact/TypeScript UI, Vitest, Gradle/JUnit.

---

## Stage 1: Normalized POI catalog and persistence

### Task 1: Define the normalized POI contract

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/LocalMapPoiCatalogTest.kt`

1. Add `LocalMapPoi`, `LocalMapPoiKind`, `LocalMapPoiService`, `LocalMapPoiItem`, and bounded tile-coordinate validation.
2. Give every POI a stable key derived from map identity plus structural event identity; never use display text as identity.
3. Add POIs to `LocalMapCatalog` and validate unique keys, known map/base-area ownership, in-bounds coordinates, legal discovery semantics, and referenced icon assets.
4. RED: duplicate, out-of-bounds, cross-map, and hidden-item identity-leak fixtures must reject.
5. GREEN: a mixed places/services/items catalog validates.

Run:
`./gradlew :parser-core:test --tests "*LocalMapPoiCatalogTest" --no-daemon --console=plain`

### Task 2: Persist POIs with the catalog

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Test: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

1. Advance the catalog schema only if required by the existing cache invalidation contract.
2. Round-trip every POI field and icon asset without changing existing local-map rasters/scenes.
3. Verify old cache input fails closed or migrates according to the current schema policy.

Run:
`./gradlew :catalog-store:test --tests "*CatalogStoreTest" --no-daemon --console=plain`

Commit: `feat: model local map points of interest`

## Stage 2: Source-backed Gen III POI extraction

### Task 3: Decode Gen III map-event records

**Files:**
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3LocalMapPoiResolver.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3LocalMapResolver.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3LocalMapPoiResolverRealControlTest.kt`

1. From each already selected map header, follow its typed `MapEvents` pointer.
2. Decode bounded object events, warps, coordinate events, and background events using the selected Gen III family ABI.
3. Classify entrances, visible field-item objects, hidden-item background events, and unresolved event/script records.
4. Resolve stable event identities and coordinates even when names, items, sprites, or scripts remain unavailable.
5. Run exact official Emerald and FireRed real-ROM controls first; assert counts, coordinates, kinds, and stable keys from source-built fixtures.
6. Malformed pointer/count/record failures skip only the affected POI/map and never invalidate rendered local maps.

Run:
`./gradlew :parser-core:test --tests "*Gen3LocalMapPoiResolverRealControlTest" --no-daemon --console=plain`

### Task 4: Resolve scripts, services, items, and icons

**Files:**
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3FieldScriptPoiResolver.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3FieldItemResolver.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3LocalMapPoiResolver.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3FieldScriptPoiResolverRealControlTest.kt`

1. Decode only source-proven bounded command streams and terminate on the selected engine's end/return opcodes.
2. Recognize Mart/shop inventory, Pokémon Center, Gym, building entrance, visible item, hidden item, and collection-flag roles through decoded operands and consumers.
3. Resolve item display names and item/object sprites from selected parser tables where available; retain a Poké Ball silhouette fallback without inventing identities.
4. Keep unclassified but structurally valid event records as `UNKNOWN`, never as a guessed category.
5. Add official Emerald/FRLG exact controls and then source-backed Modern Emerald, Unbound, and Odyssey controls where their supplied ROM/source pair proves the same structures.

Run:
`./gradlew :parser-core:test --tests "*Gen3FieldScriptPoiResolverRealControlTest" --tests "*Gen3LocalMapResolverRealControlTest" --no-daemon --console=plain`

Commit: `feat: extract Gen III local map POIs`

## Stage 3: Organic POI knowledge and live discovery

### Task 5: Persist discovery state per ROM and save

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepository.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerSanitizer.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepositoryTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerSanitizerTest.kt`

1. Store identified, proximity-revealed, entered, and collected POI key sets in the existing ROM-SHA plus save-identity ledger.
2. Migrate old ledgers with empty POI state.
3. Sanitize all keys against the active catalog so knowledge cannot cross ROMs or stale catalogs.

Run:
`./gradlew :app:testDebugUnitTest --tests "*FileKnowledgeRepositoryTest" --tests "*KnowledgeLedgerSanitizerTest" --no-daemon --console=plain`

### Task 6: Merge save flags and live proximity

**Files:**
- Modify: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/SaveModels.kt`
- Add: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3EventFlagSnapshot.kt`
- Modify: relevant Gen III save parser selected by `Gen3SaveRuntimeAbi`
- Add: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/LocalMapPoiKnowledgeMapper.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/LocalMapPoiKnowledgeMapperTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

1. Decode only event flags whose save ABI is structurally selected; otherwise leave completion unknown.
2. On each live position update, reveal hidden-item silhouettes when the player is at Chebyshev distance <= 1 on the same base area.
3. Reveal service category near its entrance; reveal exact place/shop identity and inventory after entering the mapped destination.
4. Mark visible/hidden items collected only from the proven collection flag or save state.
5. Preserve monotonic Organic discovery across area changes and reloads.

Run:
`./gradlew :companion-core:test --tests "*LocalMapPoiKnowledgeMapperTest" :app:testDebugUnitTest --tests "*ProductionCompanionRuntimeTest" --no-daemon --console=plain`

Commit: `feat: track organic local map discovery`

## Stage 4: Knowledge-safe API and ROM/save-scoped preferences

### Task 7: Project only permitted POI state

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-web/src/models.ts`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`

1. Add API POI views with `HIDDEN`, `SILHOUETTE`, `IDENTIFIED`, and `COLLECTED` states.
2. Organic mode omits hidden coordinates/identity entirely, returns only fallback silhouette fields after discovery, and reveals identity only after its rule is met.
3. Discovered mode projects every ROM-resolved POI immediately while preserving truthful collected state.
4. Hidden mode exposes no POIs.

Run:
`./gradlew :companion-core:test --tests "*ApiViewBuilderTest" --no-daemon --console=plain`

### Task 8: Persist category filters and zoom thresholds

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

1. Add five category toggles: Places, Services, Available items, Collected items, Unknown POIs.
2. Add normalized icon and label thresholds in 0..100%, enforce label threshold >= icon threshold.
3. Default all categories enabled and both thresholds to 0%, so the initial Local-map zoom shows every knowledge-permitted icon and label.
4. Store preferences in the existing ROM override plus save-specific POI preference document; do not leak preferences between saves.

Run:
`./gradlew :app:testDebugUnitTest --tests "*SettingsRepositoryTest" --tests "*ProductionCompanionRuntimeTest" --no-daemon --console=plain`

Commit: `feat: expose knowledge-safe local map POIs`

## Stage 5: Local-map POI interface

### Task 9: Add filters, thresholds, icons, labels, and cards

**Files:**
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/models.ts`
- Modify: relevant map/theme CSS files under `companion-web/src`
- Test: `companion-web/src/pages/MapPage.test.tsx`
- Test: `companion-web/src/pages/SettingsPage.production.test.tsx`

1. Add one compact POI filter button to the Local-map utility rail with the five category toggles.
2. Render icons in scene coordinates, offset by placement coordinates, with viewport culling.
3. At default 0% thresholds, render all knowledge-permitted icons and labels at the initial Local-map zoom.
4. Render visible-item sprite silhouettes where resolved, otherwise a black Poké Ball; reserve `?` for unclassified POIs.
5. Tap opens a compact card with only permitted name/category/item/inventory/collection information.
6. Add Settings sliders for icon and label thresholds; map current zoom to normalized 0..100% between the active view's supported minimum and maximum.
7. Keep Organic/Discovered/Hidden behavior exact and retain fog, player marker, gestures, recenter, local-map resolution, and themed surfaces.

Run:
`npm.cmd test -- --run src/pages/MapPage.test.tsx src/pages/SettingsPage.production.test.tsx`
`npm.cmd run build`

Commit: `feat: present local map POIs`

## Stage 6: Real-ROM vertical and release gate

### Task 10: Verify full persistence/API/browser behavior

**Files:**
- Add/modify focused tests in `parser-core`, `catalog-store`, `companion-core`, `app`, and `companion-web/e2e`
- Update: `docs/reports/` with source/ROM controls and exact supported behavior

1. Official Emerald and FireRed: parse -> catalog store -> reopen -> runtime -> API -> Local-map UI.
2. Modern Emerald, Unbound, Odyssey: run exact source-backed controls where current source/ROM inputs support the structure; report missing save-backed completion flags truthfully.
3. Browser assertions: default Local zoom shows all permitted POIs/labels, category filters work, zoom thresholds work, Organic hides undiscovered coordinates, proximity reveals silhouette, Discovered exposes static POIs, collected state persists, and fog/player/recenter remain unchanged.
4. Run focused affected suites, not unrelated corpus work.
5. Review diffs, commit evidence/docs, merge the completed feature to the active release branch, create the next RC, and publish the signed APK according to the existing release workflow. Do not install or launch it on the user's device.

Run:
`./gradlew :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain`
`npm.cmd test -- --run`
`npm.cmd run build`
`git diff --check`

