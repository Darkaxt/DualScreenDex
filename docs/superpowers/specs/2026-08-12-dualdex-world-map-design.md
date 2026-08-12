# DualDex World Map Design

## Goal

Add a ROM-authentic, zoomable world map to DualDex without weakening its structural-parser contract. The map renders only data that can be proven from the loaded ROM, reveals explored locations through Organic fog of war, and turns a resolved location into the existing Pokédex Area filter.

The approved interaction reference is Kanto Gear commit `e406ca5c2eb3ac3c44003d40e1da0edda253defe`. DualDex adopts its useful interaction ideas—an in-game map, location overlays, a current-location marker, zoom, and direct location-to-encounter navigation—but does not copy its host APIs, bundled assets, game-name tables, coordinates, or offsets.

## Product Contract

### One combined map view

DualDex uses one `WorldMapPage` rather than separate region and local-map pages.

- The initial view fits the ROM-rendered region map to the available screen.
- Pinch and the visible zoom control enlarge the same canvas.
- Drag pans after the map exceeds the viewport.
- A short tap selects a resolved location; movement beyond the tap threshold is a pan and must not activate a location.
- At overview zoom levels, the canvas shows the region map and its semantic location overlay.
- When local-map rendering is available in a later stage, zooming into a selected or current location transitions within the same canvas to its local map. Backing out returns to the prior viewport instead of navigating to a different page.
- A Gen II ROM with two structurally proven regions exposes an in-page region selector; it does not create a second map screen.

The map keeps DualDex's current dense, retro-instrument visual language: ROM pixels stay crisp, controls use the existing forest/acid/paper palette, and labels remain compact and legible. Map art is the memorable surface; the surrounding chrome stays restrained.

### Map First presentation contract

The region raster fills the page below the existing left-aligned two-line header. The first line is the ROM-derived region title and the second is the active current or selected place. The page must not replace that header with a centered or inline title, a floating location card, instructional copy, a permanent place rail, or a bottom toolbar.

- `CURRENT` appears beside the place subtitle only when the active place matches the validated live or offline current area. A separately selected place remains highlighted while the current-location ring stays visible at its own location.
- Visible zoom out, zoom in, recenter, and the global Pokédex shortcut remain fixed and unclipped at the upper right. Recenter targets the active selected place, falling back to current location and then fit-to-region.
- `Layers` and its compact legend remain fixed and unclipped at the upper left. Optional entity layer types and their controls are present only when structurally proven ROM data exists; unsupported trainers, items, or facilities are omitted rather than shown empty or inferred.
- The contextual Area Pokédex action uses the existing Pokédex icon directly below `Layers`, has an accessible `Open Area Pokédex` label and tooltip, and appears only when a revealed current or selected place is active. It is not a text button, toolbar, or card.
- The global Pokédex shortcut at upper right changes screens without changing Area state. The contextual Area Pokédex action validates the active semantic location, opens Pokédex with `AREA` active, and preserves map viewport and selection.
- Organic fog is solid black at every outer edge and falls away through a natural inward gradient around visited semantic cells. Unvisited art, labels, and hit targets are unavailable, not merely dimmed or made transparent. Discovered mode shows all structurally resolved locations.

These placements and absences are part of the user-facing contract. Centered or inline titles, bottom toolbars, floating location cards, instructional copy, clipped fixed controls, translucent outer fog, and controls for unproven entity layers are regressions.

### Area selection and the Pokédex

Every selectable map location carries one or more explicit encounter `baseAreaId` values. Tapping a revealed, resolved location:

1. validates the location key against the active catalog;
2. stores the selection as the active Area context;
3. opens the Pokédex;
4. activates the Area filter; and
5. lists only species that the current knowledge policy permits for that location.

The Pokédex browse toolbar shows an Area context chip whenever the Area filter is active. The chip contains a map-pin icon, the ROM-derived Route/Town/Area display name, and `CURRENT` only when the active selection is the live or valid offline current location. A selected non-current location shows its name without the marker. Clearing the selection returns Area to the current location.

Map and Pokédex expose a symmetric top-right shortcut using the existing compact icon-action language. Pokédex shows a map icon with an accessible `Open Map` label and tooltip; Map shows a Pokédex icon with an accessible `Open Pokédex` label and tooltip. These shortcuts change only the visible screen. They must not clear `selectedAreaId`, change the Area filter, replace the active/current base IDs, or reset the map viewport, so a map-selected Area survives Map → Pokédex → Map and Pokédex → Map → Pokédex round trips. The Map shortcut remains visibly disabled with an explanatory tooltip when no structurally resolved map is available, while the ordinary Pokédex Area experience remains usable.

Every location row in a Pokémon Detail Area tab also exposes an accessible `Show on Map` action. It sends only the catalog-owned semantic location key, preserves the open species as detail context, and opens the same `WorldMapPage` centered on and highlighting that location. Returning to the Pokédex restores the same species detail tab and Area context. Rows without a structurally proven location binding omit or disable the action rather than guessing from their label.

> Pending presentation approval: replace the location-row-only presentation with the same ROM-derived world map embedded in every Pokémon detail `AREA` tab. It would simultaneously highlight only base areas present in per-area observation knowledge for that species, apply Organic fog while treating an observation as proof that its area is revealed, preserve Pokémon context while an area is selected/centered, and let the contextual Pokédex icon open that selected Area filter. Caught state and inferred starter, gift, or trade provenance would never add a highlight. With no observed areas the map would remain visible with the exact copy `No known locations yet.` Potential parsed encounters in Discovered mode remain excluded unless separately approved. Do not implement this paragraph until that presentation approval is explicit.

Map availability is independent of the Area filter. A ROM whose map art cannot be resolved still gets the normal Area filter and Area context chip whenever its encounter/current-location data is valid.

### Area knowledge correctness

`seenSpeciesByArea[baseAreaId]` is the authority for species observed in a location. Global caught or owned state must never make a species appear in every Area result.

Consequently:

- caught but not locally observed: absent from the Area result;
- locally observed but not caught: present;
- both caught and locally observed: present because of the local observation.

This fixes the current Treecko path. Save mapping correctly marks party/owned Treecko as caught, but `ApiViewBuilder` currently unions every caught species into `currentAreaSpeciesIds`. Removing that union prevents Treecko from appearing in a starting-area result unless a Treecko observation was actually persisted for that base area.

This design does not silently change the Area policy in Discovered mode to the full parsed encounter table. Such a policy change requires separate approval because the established Area contract is locally observed species.

## Data Model

### Explicit encounter identity

`EncounterArea` gains a required `baseAreaId`, and `AreaView` exposes it to the web client.

The field is not reconstructed from `id`. Classic encounter rows currently use `baseAreaId * 10 + methodId`, while expansion rows use `baseAreaId * 100 + time * 10 + methodId`. Division by ten is therefore not a generic inverse and already selects the wrong location for expansion rows.

All parser materializers set `baseAreaId` at construction. Name resolution, current-area lookup, Area filtering, rarity lookup, tests, and future map joins use the explicit field. The catalog parser schema version advances so persisted rows without the field cannot be interpreted as base area zero.

### Active versus current Area context

The backend keeps the live/offline current area distinct from a user-selected area.

- `currentAreaBaseId`, `currentAreaName`, and `currentAreaIds` describe only the structurally validated live area, or a ROM-matched checksum-valid save area while RetroArch is disconnected.
- `selectedAreaId` continues to identify a selected encounter row during the Stage 0 bridge.
- Active Area fields describe the row set and observation set used by the filter. They resolve `selectedAreaId` through its explicit `baseAreaId`; without a valid selection, they fall back to current Area.
- `activeAreaIsCurrent` compares the active base ID with the current base ID. It is false for a different selected location and true for the current location, including a selected encounter method belonging to that same location.

Later world-map work replaces the single encounter-row bridge with a validated semantic location key that may map to several base IDs. The distinction between current and active fields remains unchanged.

### World map catalog

The future map section uses parser-normalized structures:

```kotlin
data class WorldMapCatalog(
    val regions: List<WorldMapRegion>,
    val assets: Map<String, RgbaSprite>,
)

data class WorldMapRegion(
    val key: String,
    val displayName: String?,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val imageAssetKey: String,
    val locations: List<WorldMapLocation>,
)

data class WorldMapLocation(
    val key: String,
    val displayName: String,
    val baseAreaIds: Set<Int>,
    val geometry: List<WorldMapCell>,
)

data class WorldMapCell(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
```

Keys are stable outputs of the resolved structure, not ROM title, SHA, filename, or absolute-offset tables. A location kind such as town or route is added only when a structural field proves it; names are never classified with English regular expressions.

Several map headers may legitimately share one semantic location. They are grouped only when the ROM's own section/landmark identity proves the relationship. If two distinct locations collide visually, the client presents an anchored chooser instead of silently unioning unrelated encounter sets.

### Catalog storage and assets

Overview metadata and each fully rendered ROM-derived raster are persisted in a required `world_maps` catalog section. A region may reference an `imageAssetKey` only when that key resolves to a nonempty raster with exactly the declared pixel dimensions. The API bootstrap contains normalized metadata and immutable asset URLs, not expanded pixel arrays. Region PNGs are served through `/api/maps/world/{regionKey}.png` with the catalog hash as an ETag, following the existing sprite endpoint pattern.

Local maps can be much larger than the overview. Their later stage stores rendered PNG assets in a BLOB-oriented catalog asset table rather than JSON-expanding every raster. The runtime does not retain raw ROM bytes after parsing, so every required asset is materialized during catalog parsing.

## Organic Fog of War

### Stage 1: semantic location cells

`KnowledgeLedger` gains ROM-scoped `visitedAreaBaseIds`.

- Every validated live area transition unions and persists its base ID, whether or not a battle occurs.
- A checksum-valid, ROM-matched save import records its current base ID.
- Migration may seed visits from `seenSpeciesByArea.keys` and the persisted current area because both prove that the player was there.
- `seenSpeciesByArea` itself is not the exploration ledger: towns and encounter-free routes may never record an opponent.

In Organic mode, only geometry owned by a visited base ID is revealed. Unvisited art, labels, and hit targets remain masked. This prevents a hidden label or invisible clickable region from leaking knowledge. Discovered mode reveals every structurally resolved location.

Without live coordinates, the current marker is a pulsing outline around the resolved current location cell or cells. DualDex does not fabricate a player dot inside the area.

### Later stage: local walk cells

True tile-by-tile fog and a precise player marker require structurally resolved live `(baseAreaId, x, y)` coordinates plus `visitedLocalCells`. Current runtime metadata publishes only map group/number, and area observations cannot reconstruct a walking path. No fixed RAM offset is permitted. Local-map fog remains unavailable until coordinates can be proven from ROM-derived runtime layouts.

## Structural ROM Resolution

### Gen III first vertical slice

Gen III is the first overview implementation because the existing `Gen3MapLocationResolver` already proves `gMapGroups`, map headers, section IDs, and the 8-byte region-location entries `{x, y, width, height, namePointer}`.

The resolver is expanded to retain base-area-to-section joins and overlay geometry. It locates a unique, co-referenced cluster containing:

- LZ-compressed 8bpp tile graphics;
- an LZ-compressed 16-bit tilemap whose indices and flip bits are valid for the decoded tile set; and
- a bounded, non-degenerate BGR555 palette set.

Candidate references come from the shared `RomAnalysisSession` / `GbaReferenceIndex`. Validation requires coherent decoded sizes, valid tile indices, palette bounds, overlay bounds, and one authoritative candidate cluster. A generic GBA 8bpp tile renderer and tilemap compositor create the final raster.

### Gen I overview

Gen I's ROM town map is structurally feasible through a dedicated resolver:

- 2bpp tile graphics;
- a zero-terminated nibble-RLE stream that expands to the expected 18 by 18 tilemap; and
- encounter-map-keyed three/four-byte coordinate/name records.

The resolver proves a unique table through valid coordinate nibbles, bank-local text pointers, encounter map-ID coverage, and loader/data references. Gen I coordinates prove anchor cells, not full route polygons; the overlay must not invent geometry the ROM does not contain.

### Gen II overview

Gen II is structurally feasible from:

- 48 compressed 2bpp tiles;
- per-region 19 by 19 tilemaps;
- four-byte landmark records `{pixelX, pixelY, namePointer}`; and
- map headers carrying landmark IDs.

Crystal may expose both Johto and Kanto in the same page. Landmark coordinates again prove point/cell anchors only.

### Local maps

Gen III local map headers already lead to layouts and primary/secondary tilesets. A later resolver validates layout dimensions, block grids, metatile layers, tile indices, palette banks, and both tileset partitions before rendering a static snapshot. Dynamic callbacks and animated door states are not guessed.

Gen I/II local maps follow only after equivalent header, block, tileset, palette, and bank validators exist. Overview support never implies local-map support.

## Capabilities and Failure Behavior

`RomCapability.WORLD_MAP` reports the map resolver independently of `AREA_ENCOUNTERS`.

- `AVAILABLE`: one fully validated overview and overlay set is materialized.
- `PARTIAL`: a validated subset such as one of multiple regions is materialized, with evidence explaining the missing subset.
- `AMBIGUOUS`: more than one authoritative asset/table candidate remains.
- `NOT_FOUND`: the structural chain cannot be resolved.
- `NOT_APPLICABLE`: the engine family has no applicable resolver stage.

Any absent, truncated, invalid, or non-unique dependency fails closed. The UI omits or disables the map entry and exposes the reason through the existing capability report. It never substitutes stock Kanto/Johto/Hoenn art, a ROM-name lookup, a SHA lookup, a filename heuristic, or a guessed coordinate table. Pokédex and Area behavior continue normally.

## Delivery Stages

### Stage 0: Area correctness and identity

- Remove the caught-species union from Area observations.
- Add the three backend regressions for caught-only, observed-only, and caught-plus-observed.
- Add required `baseAreaId` through parser, catalog cache, API, rarity, and web models.
- Replace location-sensitive `id / 10` paths.
- Add current/selected Area context fields and the Pokédex toolbar chip.
- Do not add map art or a map resolver.

### Stage 1: Gen III overview

- Add normalized world-map catalog models, capability evidence, cache section, PNG endpoint, and 8bpp compositor.
- Expand the Gen III structural resolver.
- Add `visitedAreaBaseIds` and Organic semantic-cell fog.
- Add the combined overview page, zoom/pan, current outline, tap-to-Area navigation, and symmetric Map/Pokédex shortcuts that preserve Area context.

### Stage 2: Gen I and Gen II overview

- Add their structurally validated codecs/resolvers.
- Reuse the same map catalog, asset API, fog, selection, and UI.
- Add Gen II's in-page multi-region control.

Stages 1 and 2 together form the minimum usable map delivery. Gen III-only support is not merge-ready: legitimate official Gen I and Gen II controls must each render real ROM-derived art, expose catalog-bound locations, complete both navigation directions, and apply the same Organic fog contract.

### Stage 3: local semantic zoom

- Materialize structurally validated local map assets.
- Transition to them inside the existing map canvas.
- Add structural live coordinate resolution and granular local fog only where proven.

## Verification Contract

Stage 0 requires backend and web red-green tests plus full affected builds.

Map stages require:

- decoder/compositor fixtures with exact pixels;
- resolver rejection tests for truncated, invalid, decoy, and ambiguous candidates;
- explicit base-area mapping tests, including expansion encounter IDs;
- catalog round-trip and parser-schema invalidation tests;
- knowledge persistence and area-transition-without-battle tests;
- API tests that prevent unvisited labels, species, or hit targets from leaking in Organic mode;
- UI tests for pinch/pan versus tap, current/selected labels, unavailable maps, symmetric Map/Pokédex navigation with preserved Area context, Pokémon Detail Area-row centering with preserved species context, and multi-region switching; and
- SHA-bound live controls on official Gen I, II, and III ROMs plus structurally compatible hacks. The SHA binds test evidence only; production resolution remains structural.

Before merge, install, or release, the resolver is run twice over the exact first 50 unique ROMs in manifest order, rehashing every input immediately before each run. A per-ROM `SUCCESS` requires all of the following, not merely a supported family or a nonempty metadata shell:

- a structurally resolved, nonempty ROM-derived world-map raster;
- validated raster dimensions and location geometry;
- at least one valid semantic location-to-`baseAreaId` binding;
- successful asset persistence and HTTP serving;
- usable Map ↔ Pokédex and Pokémon Detail Area → Map navigation;
- identical deterministic output on the repeat; and
- no `AMBIGUOUS`, budget, error, reference-index, parser, family, or exact first-33 behavior regression.

At least 25 of the 50 ROMs must be `SUCCESS`. Every one of the 50 receives an explicit status and reason. `NOT_FOUND`, `NOT_APPLICABLE`, partial metadata, blank placeholder art, unsupported-family scaffolding, or a failed interaction is not success. A total below 25 is a hard no-ship result.

Live acceptance verifies the user-facing flow on the target device: a route transition updates the current outline, the visit survives a cold restart, tapping a revealed location opens the correct Area filter, both top-right shortcuts preserve that Area context and map viewport, and Treecko remains absent from a location unless it was observed there. Pixel hashes and successful builds alone are not sufficient.
