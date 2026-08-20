# Local-Raster Seamless Map Design

## Status

Stage 1 is implemented and lab-validated for official Gold, Silver, and Crystal. The next implementation target is Gen III dynamic Local-map support because that is the active live-testing path. Scene topology, slippy-map delivery, unified viewport work, and interactables remain separately scoped later stages.

## Goal

Use playable Local-map renders as the authoritative geography for both close and regional views. Provide a single Google Maps-style viewport that moves continuously from exact map detail to a connected regional overview without retaining a separate generated global raster or four daylight PNGs per map.

## Current State

DualScreenDex currently has two independent representations:

- `LocalMapCatalog` contains one playable-map raster per base-area ID.
- `WorldMapCatalog` contains a simplified official Town/World Map raster and location cells.
- `MapPage` switches between Local and Atlas modes.
- Official Gen I, Gen II, and selected Gen III Local-map controls are supported.
- The integrated release clock stack already publishes `AppSnapshot.gameTime`, projects Gen III day/night schedules, and renders `GameClockIndicator` on the Map page.
- Gen II Local maps are indexed rasters with four palette rows; the bounded runtime selector, lazy Android/desktop endpoints, ETag variants, phase-only clock presentation, and viewport-preserving web updates are connected and validated.

The separate Local/Atlas implementation remains the compatibility fallback while the seamless scene pipeline is incomplete.

## Design Principles

1. **Local maps own raster truth.** A generated regional scene references Local assets and never stores a second full-region image.
2. **Raw ROM bytes remain authoritative.** Public source code may describe structure, but production recognition and data extraction use compiled evidence.
3. **No ROM identity shortcuts.** Production selection must not use filenames, ROM hashes, fixed ROM offsets, per-ROM profiles, or hack-specific allowlists.
4. **Optional features fail independently.** Lighting, scene placement, tiled delivery, and interactables cannot crash catalog creation or disable otherwise usable maps.
5. **Memory stays bounded.** Assets remain individually compressed after catalog deserialization, and rendered output caches have hard byte limits.
6. **Geometry is proven, not invented.** Connection metadata may place maps; warps may link scenes but cannot imply physical adjacency.
7. **One clock state owns time presentation.** Gen II normalized lighting extends the existing `gameTime` projection; Stage 1 does not add a parallel clock snapshot, reducer field, or second widget.

## Architecture

The end state has three layers:

```text
LocalMapCatalog                 authoritative playable-map metadata and raster sources
       │
       ├── LocalRasterRenderer  renders a complete map or clipped rectangle
       │
MapSceneCatalog                 connection-derived placements referencing Local maps
       │
MapSceneTileService             composes visible 256×256 tiles at requested zoom/lighting
       │
Seamless Map UI                 one pan/zoom coordinate system with progressive tile loading
```

`WorldMapCatalog` remains available only as fallback until a generation's scene topology and tiled presentation pass their acceptance gates.

## Stage 1: Dynamic Gen II Raster Foundation

### Asset model

Introduce a generic Local raster abstraction that preserves existing Gen I/III assets while allowing Gen II indexed rasters:

```text
LocalRasterAsset
 ├── PngRasterAsset
 │    └── PNG bytes
 └── IndexedPaletteRasterAsset
      ├── pixel width and height
      ├── zlib-compressed pixel indices
      └── four 32-color ARGB palette tables
```

Each decompressed Gen II pixel is one byte in `0..31`:

```text
index = paletteSlot * 4 + colorIndex
paletteSlot: 0..7
colorIndex:  0..3
```

The index surface is time-independent. Morning, day, night, and dark each contribute only 32 colors. Roof overrides are applied while constructing each map's four palette tables, so the renderer does not need ROM-specific knowledge.

Each indexed surface is compressed independently before entering the catalog. The outer catalog-section compression is not considered sufficient because the catalog reader materializes the section in memory.

### Lighting policy

Each Local map carries one policy:

```text
AUTO | MORNING | DAY | NIGHT | DARK
```

Effective lighting follows this precedence:

1. An explicit map-header policy always wins.
2. `AUTO` uses the valid live game lighting value.
3. Missing, disconnected, or invalid live lighting falls back to `DAY`.

Runtime values normalize as:

```text
0 → MORNING
1 → DAY
2 → NIGHT
3 → DARK
```

The parser publishes a structurally resolved Gen II time-of-day WRAM offset through runtime metadata rather than selecting it by ROM identity. Official controls resolve Gold/Silver offset `0x1568` and Crystal offset `0x1841`. The battle-memory session adds one bounded byte region, normalizes it to the four existing palette modes, and publishes only changes into the shared game-clock state. It does not create a second live-lighting state machine.

### Renderer boundary

A shared `LocalRasterRenderer` accepts:

```text
asset + requested lighting + optional source rectangle → ARGB raster or PNG
```

For explicit map policies, requested lighting is ignored after effective-lighting resolution. Rectangle rendering must produce the same pixels as cropping a full render. This clipping contract is required by the later scene tile compositor.

The existing endpoint remains usable during Stage 1:

```text
GET /api/maps/<asset-key>.png?lighting=MORNING|DAY|NIGHT|DARK
```

Static PNG assets ignore the lighting query. Dynamic assets render lazily. A missing query is treated as `DAY`; an unknown value returns HTTP 400. The endpoint resolves the map's policy before rendering, and its ETag and cache key include the asset identity and effective lighting.

### Live state and UI

Stage 1 extends the existing clock path rather than introducing `liveMapLighting`, `LiveMapLightingChanged`, or `currentMapLighting` fields. The shared `GameClock` model supports two valid shapes:

```text
numeric clock: hours + minutes + optional DAY/NIGHT phase/progress
phase-only:    null hours/minutes + MORNING|DAY|NIGHT|DARK + null progress
```

Gen III keeps its current numeric clock extraction and DAY/NIGHT orbit behavior unchanged. A valid Gen II `wTimeOfDayPal` byte publishes a phase-only `gameTime`; disconnect, invalid data, or missing metadata publishes no game time. `GameClockIndicator` remains the only Map-page clock widget and renders phase-only Gen II state without inventing an `HH:mm` value.

For a dynamic Local map, `MapPage` appends `state.gameTime.phase`—when it is one of the four normalized map-lighting values—or `DAY` fallback to its image URL. The endpoint still applies the map's explicit or `AUTO` policy. Lighting changes replace the image without resetting pan, zoom, selected map, player position, or the existing clock widget.

### Stage 1 failure behavior

- Unavailable runtime lighting clears the phase-only Gen II `gameTime` and map rendering uses day; it is not a parser failure.
- Invalid runtime values are treated as unavailable.
- Existing Gen III numeric `gameTime`, schedule projection, and clock widget behavior are unchanged.
- A failed dynamic Local-map resolution disables `LOCAL_MAP` through the existing optional-capability path.
- Catalog and Atlas materialization continue.
- Static Gen I/III Local assets are unchanged.
- Render endpoint failures return map-not-available without terminating the companion runtime.

## Stage 2: Connection-Derived Scene Topology

### Scene model

A generated scene owns geometry but no raster bytes:

```text
MapSceneCatalog
 └── scenes
      ├── key and display name
      ├── native bounds
      └── placements
           ├── localMapKey
           ├── worldGridX and worldGridY
           └── layer
```

World coordinates use the existing 16-pixel Local-map grid. This keeps placement, live player coordinates, and future interactables in one coordinate system.

### Placement rules

1. Parse cardinal connection records and their signed offsets from compiled map data.
2. Build an undirected constraint graph for north, south, east, and west connections.
3. Choose a deterministic origin per connected component, propagate placement constraints, then translate the accepted component so its minimum X/Y is zero.
4. Require every repeated path to derive the same origin for a map.
5. Reject conflicting placement equations or unexplained occupied-area overlaps.
6. Treat warp links as navigation edges only.
7. Keep disconnected components as separate scenes rather than guessing their relative positions.
8. Represent interiors, caves, floors, and other warp-only spaces as separate scenes or layers.

A scene-placement failure cannot remove its Local maps. It disables only the generated scene, leaving Local view and the existing Atlas available.

Official Gold, Silver, and Crystal are the first topology controls. Gen I and Gen III follow in separately scoped work after the GBC scene model is validated.

## Stage 3: Slippy-Map Tile Service

### Tile contract

The server exposes scene tiles:

```text
GET /api/map-scenes/<scene>/<zoom>/<x>/<y>.png?lighting=<mode>
```

- Tiles are 256×256 pixels.
- The scene's native zoom renders one source pixel per output pixel.
- Each lower zoom halves both axes.
- A spatial index selects only placements intersecting the requested tile.
- The compositor requests clipped rectangles from `LocalRasterRenderer`.
- Lower zoom tiles downsample the same composed playable-map geometry.
- Empty edge space is transparent; the UI supplies the scene background.

The tile query carries the normalized live lighting. Each intersecting map independently applies its explicit or `AUTO` policy, so a tile may legitimately contain mixed effective modes. Tile cache identity includes catalog identity, scene identity, topology version, zoom, X/Y, and requested normalized live lighting. The Android/server rendered-output LRU has a hard 16 MiB byte cap. No unbounded map, palette, or tile cache is allowed.

The browser retains only visible tiles plus a small prefetch margin. A lighting change requests a new tile identity while preserving viewport state.

## Stage 4: Unified Seamless Viewport

For scenes with validated topology, `MapPage` presents one coordinate system:

- Close zoom shows exact playable-map detail.
- Zooming out reveals connected routes, towns, and adjacent outdoor maps.
- Far zoom shows the complete connected surface component.
- The live player marker converts local map/grid coordinates through the active placement.
- Existing pan, anchored zoom, and pointer interactions remain.
- CSS transforms provide continuous motion while discrete raster zoom levels load.
- Future markers can change visibility or clustering by zoom without changing coordinates.

The permanent target has no conceptual Local/Atlas split. During migration:

1. Use the seamless scene when available.
2. Fall back to the current individual Local map when scene placement is unavailable.
3. Keep the official Atlas as the final compatibility fallback.
4. Retire an official world raster only after that generation's scene coverage and UI behavior are validated.

## Stage 5: Interactables

Interactables are deliberately outside the first three implementation plans. A later specification may add hidden items, visible items, warps, signs, NPCs, trainers, and generic script events.

Those records will reference Local-map grid coordinates and therefore automatically inherit scene placement. Live proximity can compare the player and interactable positions without a second coordinate model. Unknown scripts fail closed at the individual marker level and are never executed.

## Persistence and Compatibility

- Increment the parser schema whenever serialized Local raster or scene types change.
- Cache round trips must preserve compressed bytes, palette tables, policies, scene placements, and deterministic rendering.
- Existing cached catalogs are invalidated through the schema version rather than migrated in place.
- API additions are nullable or capability-gated during rollout.
- Gen I/III PNG Local assets remain supported by the same renderer interface.

## Validation

### Stage 1

Validate only the directly affected GBC path:

- Official Gold, Silver, and Crystal deterministic parses.
- All four palette rows against compiled ROM data.
- Day output pixel-equivalent to the current accepted daytime render.
- Explicit morning/day/night/dark map-policy precedence.
- `AUTO` runtime transitions and day fallback.
- One-byte bounded memory reads and change-only publication through `AppSnapshot.gameTime`.
- Phase-only Gen II clock presentation without fabricated hours/minutes, while existing Gen III numeric clock/orbit tests remain unchanged.
- Catalog-store round trip of individually compressed indexed assets.
- Full-render versus clipped-render pixel equivalence.
- Android and server endpoint ETag separation by effective lighting.
- Web state update without viewport reset.
- Optional failure leaves Atlas and catalog startup usable.

No GBA investigation is part of Stage 1.

### Stage 2

- Every source-backed cardinal connection satisfies its placement equation.
- Placement is deterministic regardless of traversal order.
- Contradictory cycles fail the scene only.
- Warp-only and disconnected maps are not assigned invented positions.
- Scene bounds contain every accepted placement.
- Local maps remain available when scene generation fails.

### Stages 3 and 4

- Adjacent map seams match native full-scene composition at multiple zooms.
- Four neighboring tile edges have no gaps or duplicated pixels.
- Tile requests never decode nonintersecting placements.
- Render cache remains within its byte cap.
- Pan and zoom do not reset on map-boundary or lighting changes.
- Player position remains stable while crossing connected maps.
- Fallback Local/Atlas behavior remains available for unsupported ROMs.

## Delivery Sequence

1. **Dynamic Gen II raster foundation (complete)** — indexed asset, four palettes, runtime lighting, clipped renderer, current Local endpoint.
2. **Gen III live-testing expansion (next)** — adapt the dynamic raster contract to source-backed Gen III controls while preserving its existing numeric clock extraction and widget.
3. **Official scene topology** — connection parser, constraint solver, scene catalog, failure isolation, beginning with the best source-backed generation.
4. **Scene tile service** — spatial selection, clipped composition, zoom pyramid, bounded cache.
5. **Unified viewport** — visible-tile management, seamless zoom, player placement, compatibility fallbacks.
6. **Interactables** — separately designed event layers and live proximity.

Each numbered item ends in a working, independently testable checkpoint. Gen III dynamic Local-map support receives its own focused scope before implementation.
