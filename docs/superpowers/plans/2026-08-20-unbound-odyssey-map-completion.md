# Unbound and Odyssey map completion implementation plan

> **Execution:** Follow this plan continuously through real-ROM verification and a separate map-stage commit. Do not stop for intermediate approval.

**Goal:** Resolve normalized world and local map catalogs for the exact Pokémon Unbound v2.1.1.1 and Pokémon Odyssey v4.1.1 controls, moving each parser from 20/23 to 22/23 without identity rules, fallback maps, or relaxed pixel limits.

**Architecture:** Extend the existing shared-session `Gen3WorldMapResolver`, `Gen3MapLocationResolver`, and `Gen3LocalMapResolver`. Raw ROM loaders, consumers, map groups, dimensions, palettes, tiles, semantic planes, and encounter joins are authoritative. Source repositories explain data roles only. Every published world/local map terminates in the existing normalized catalogs and must survive SQLite and API projection byte-for-byte.

**Controls:**

- Unbound v2.1.1.1 SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
- Odyssey v4.1.1 SHA-256 `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0`.
- Existing exact official FireRed/LeafGreen, Dark Violet, Clover, Dark Cry, Classic, Modern Emerald, and Emerald controls protect previously supported loader families.
- Source oracles: Dynamic-Pokemon-Expansion Unbound branch `fe058e0`, Complete-Fire-Red-Upgrade `b637a27`, and creator-authored Odyssey v4.1.1 workbook `31b1eff`. The available sources are partial semantic authorities, not exact whole-ROM link maps.

## Task 1: Freeze exact failure stages and deterministic real-ROM evidence

**Files:**

- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/UnboundOdysseyMapCompletionLiveRomTest.kt`
- Create: `docs/reports/2026-08-20-unbound-odyssey-map-completion.md`

- [ ] Parse each exact ROM twice and assert the same selected family, encounter base IDs, map capability stage, empty normalized catalogs, and no thrown error.
- [ ] Freeze Unbound's current world failure at `asset-loader` and local failure at `map-groups`.
- [ ] Freeze Odyssey's current world failure at `asset-loader` and local failure at `raster-pixels`, including the observed 105,010,432-pixel candidate total.
- [ ] Record the raw loader trace only as diagnosis; final tests assert structured resolver evidence, not console text or absolute addresses.

## Task 2: Prove and render each world-map loader

**Files:**

- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3WorldMapResolver.kt`
- Modify if required: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/GbaWorldMapCompositor.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/UnboundOdysseyMapCompletionLiveRomTest.kt`

- [ ] For Unbound, prove one complete compiled loader owner for the retained map plane, graphics, palette, destination, semantic plane, and region slot. Resolve the current multiple graphics candidate without choosing by address/order or visual plausibility.
- [ ] For Odyssey, prove the five-destination FRLG-style world-map role graph, separating the four world regions from the background plane and associating the correct graphics/palette call roles.
- [ ] Require every asset's complete compiled reference evidence and decoder success. Multiple surviving format/role contracts remain ambiguous.
- [ ] Compose exact normalized rasters, export PNGs under a task-owned `D:\Temp` evidence folder, inspect them visually, and freeze dimensions plus ARGB/PNG hashes.
- [ ] Join semantic locations through compiled map-header consumers and retain only encounter-backed base-area bindings. A raster without bindings remains 0% world-map support.
- [ ] Run two fresh parses and require identical region keys, rasters, geometry, and bindings.

## Task 3: Resolve Unbound's map-group authority and local maps

**Files:**

- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3MapLocationResolver.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3LocalMapResolver.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/UnboundOdysseyMapCompletionLiveRomTest.kt`

- [ ] Identify the compiled `group -> map -> MapHeader` consumer from Unbound's exact ROM and derive the relocated/sparse group authority from decoded pointer flow.
- [ ] Require all encounter base IDs to bind exactly once; reject incomplete, duplicate, or contradictory roots.
- [ ] Read map layout, dimensions, cell grid, primary/secondary tilesets, palettes, and metatiles only from the bound headers and source-family ABI.
- [ ] Render every eligible encounter map with bounded dimensions/assets and record any individually rejected map without publishing a false partial as complete support.
- [ ] Freeze exact local-map count, base-area set, dimensions, and PNG hashes across two parses.

## Task 4: Derive Odyssey's true local-map extents

**Files:**

- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3LocalMapResolver.kt`
- Modify if required: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3MapLocationResolver.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/UnboundOdysseyMapCompletionLiveRomTest.kt`

- [ ] Enumerate Odyssey's exact bound headers and explain the 105,010,432-pixel total per map before changing production.
- [ ] Prove the actual layout field widths/offsets and any chunked/expanded dimension representation from at least two independent compiled consumers or a complete selected typed parent structure.
- [ ] Keep the 2,000,000 per-map, 100,000,000 aggregate, and 64 MiB encoded-asset ceilings unchanged. Wrong extents must be corrected structurally, never admitted by raising limits.
- [ ] Render the bounded exact local maps, visually inspect representative PNGs, and freeze counts/base areas/dimensions/hashes across two parses.

## Task 5: Persist, serve, regress, and commit

**Files:**

- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/WorldMapCatalogApiRealControlTest.kt` or the current exact map API integration test.
- Complete: `docs/reports/2026-08-20-unbound-odyssey-map-completion.md`

- [ ] Parse each ROM twice; require `WORLD_MAP` and `LOCAL_MAP` available with non-empty normalized catalogs and identical semantic projections.
- [ ] Persist/reopen 14 SQLite sections, verify `quick_check=ok` and zero foreign-key violations, then request every world/local PNG through the runtime/API and compare exact bytes.
- [ ] Run the focused exact controls plus official and previously supported Gen III map controls. Investigate every regression from real ROM evidence.
- [ ] Run the affected parser/catalog/app map suites once after focused GREEN.
- [ ] Report numeric before/after: each exact ROM 20/23 to 22/23 parser capabilities; ability mechanics remains the sole applicable parser gap.
- [ ] Run `git diff --check`, verify no production ROM name/SHA/title/symbol/fixed-address selector, clean task-owned temporary diagnostics, and commit the map stage.

## Completion boundary

Map support is 100% for a ROM only when world raster, semantic geometry, encounter binding, local rasters, deterministic identity, persistence, and API-served bytes all pass. A decoded but unbound image, a safely empty catalog, or a raised resource ceiling counts as 0% for that map capability.
