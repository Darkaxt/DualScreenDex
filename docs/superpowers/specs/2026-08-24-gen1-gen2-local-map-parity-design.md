# Gen I and Gen II Local-Map Parity Design

## Objective

Make Gen I and Gen II Local maps conform to the current generation-neutral RC53 map experience without creating generation-specific presentation paths. The RC53 baseline is commit `d07ce91acf73f7a4fa25b2b2134ec16fb5c52315` (`v1.1.0-rc.53`). Subsequent work from other threads must be reconciled from `fork/master` before every commit rather than overwritten.

Parity means that each generation supplies every structurally supportable input to the existing Local-first renderer: connected scenes, live player location, discovery-safe rasters, generation-appropriate POIs, save-scoped preferences and collection evidence where authoritative, and ROM-derived overworld player assets where resolvable. Gen I remains static; Gen II retains its four time-aware palettes. Atlas remains a compatibility fallback rather than a parallel destination.

This is Stage 1 of the requested work. Source-backed Gen III compatibility remains a separate Stage 2 and cannot begin until this design's checkpoints pass their validation and specification audits.

## Scope and delivery checkpoints

Stage 1 is split into two independently usable checkpoints. This controls regression risk and produces a signed validation APK before all optional POI semantics are complete.

### Checkpoint A: continuous Local navigation

Checkpoint A adds:

- Gen I cardinal-connection decoding and scene construction.
- Gen II cardinal-connection decoding and scene construction.
- Existing Gen I/II live area and X/Y projection into those scene coordinates.
- Existing RC53 initial framing, pan/zoom preservation, recentering, player following, Organic discovery, raster budgeting, and Atlas fallback behavior.
- Preservation of Gen II morning, day, night, and dark indexed-raster rendering for every mounted placement.
- Structurally resolved Gen I/II overworld walking frames when available, with the compact dot as the isolated fallback.
- Cache invalidation, API verification, Android integration tests, and web presentation tests required to expose the new generic catalog data.

Checkpoint A ends with a specification audit, blocker/deferral classification, full required validation, and a signed APK suitable for live testing.

### Checkpoint B: generation-appropriate POIs

Checkpoint B adds:

- Gen I and Gen II warp/entrance POIs.
- Structurally decodable signs and labels.
- Visible item balls and hidden items only where exact event or script semantics prove them.
- Service classification only where exact structural evidence proves the service.
- Checksum-valid save-backed collection state only where the collection-flag ABI is authoritative.
- Existing knowledge-mode filtering, proximity discovery, collection state, label decluttering, viewport culling, zoom thresholds, and save-scoped POI preferences through the shared contracts.

Checkpoint B ends with the same specification audit and validation gates. Stage 1 is complete only after its blockers are closed and any legitimate deferrals are recorded with an owner stage and acceptance condition.

## Architecture

### Shared renderer remains authoritative

`companion-web/src/pages/MapPage.tsx` remains the only Local-map presentation path. Gen I and Gen II do not receive dedicated pages, scene components, camera state, discovery modes, or POI preference models. The existing `LocalMapCatalog`, `LocalMapScene`, `LocalMapScenePlacement`, `LocalMapPoi`, runtime-position, and trainer-asset contracts remain generation-neutral.

Web changes are limited to compatibility defects demonstrated by Gen I/II tests. A generation check in the renderer is not an acceptable substitute for normalized parser data.

### Generation-specific structural decoders

Gen I and Gen II each receive a focused decoder for their compiled cardinal-connection ABI. A decoder:

1. Starts from a map header already accepted by the corresponding Local-map resolver.
2. Reads only relative fields defined by the structurally validated engine ABI.
3. Bounds-checks every record and target identity against accepted Local maps.
4. Converts a valid cardinal record into a generation-neutral placement constraint expressed in Local-map grid cells.
5. Rejects a malformed record without invalidating its source raster.

Absolute ROM addresses, ROM filenames, titles, project names, hashes, hack allowlists, and per-ROM profiles are prohibited. Relative field offsets belonging to a structurally identified ABI are permitted. Public source code may explain the ABI and guide test selection, but production acceptance is determined from compiled ROM bytes.

### Shared deterministic scene builder

The placement and partition behavior currently embedded in `Gen3MapSceneResolver` becomes a generation-neutral constraint solver with characterization coverage proving unchanged Gen III output. All three generation-specific decoders feed this solver.

The solver:

- Canonicalizes reciprocal constraints into one unordered map pair.
- Keeps a pair only when every accepted observation agrees on one displacement.
- Discards ambiguous pair evidence rather than guessing.
- Creates bidirectional adjacency from canonical constraints.
- Places maps deterministically in sorted identity order.
- Refuses placements that overlap an already accepted map.
- Partitions contradictory or anomalous components into additional safe scenes instead of rejecting every usable branch.
- Normalizes scene coordinates to non-negative grid positions.
- Rejects unbounded or oversized results.
- Emits only scenes with at least two placements.
- Assigns each Local map to at most one scene.

A map omitted from a scene retains its valid individual Local-map view. Scene construction is optional enrichment, never a prerequisite for retaining an accepted raster catalog.

### Player location and overworld assets

The existing Gen I one-byte area identity, Gen II group/map identity, and bounded X/Y runtime publication remain authoritative. The API projects a valid local tile into scene coordinates using the active placement. Invalid, unavailable, or sentinel coordinates omit the marker and do not move the camera.

The trainer-asset pipeline gains independent overworld-frame adapters:

- Gen I exposes its structurally resolved normal walking frame. Because the supported Gen I ABI has one player appearance, the API may select the sole available overworld asset when no gender field exists.
- Gen II exposes every structurally resolved player-gender frame. Engines with one appearance use the same sole-asset rule.
- Native ROM-derived dimensions continue to influence the existing marker-aware maximum zoom.
- Portraits, badges, and overworld frames remain independent roles.

Failure to resolve an overworld frame retains the compact dot and cannot suppress maps, scenes, portraits, badges, or other trainer assets.

### Gen II lighting preservation

A Gen II scene placement continues to reference its existing timed indexed asset. The server renders only the requested clipped map raster and applies the existing morning/day/night/dark palette selected by the structurally resolved game-clock byte. Scene assembly does not precompose or convert Gen II maps to static PNGs.

Every mounted placement retains its dynamic-lighting URL semantics. Unavailable or invalid clock evidence continues to use the existing safe palette behavior. Gen I and Gen III static PNG behavior is unchanged.

## POI normalization

### Event adapters

Each generation receives a per-map POI adapter over its compiled event/object structures. Resolution is deterministic and fail-closed:

- A valid warp becomes a `PLACE` with a destination area only when the destination identity is structurally valid.
- A valid sign becomes a named or unnamed `PLACE` only when its text/script form is decoded exactly.
- A sign and nearby entrance may be consolidated only when there is one unambiguous structural association. Ambiguous events remain separate rather than being guessed.
- An exact item-ball script/event form becomes `VISIBLE_ITEM`.
- An exact hidden-item form becomes `HIDDEN_ITEM`.
- Unsupported but valid events may become `UNKNOWN` when their position is authoritative.
- `SERVICE` and `MART`, `POKEMON_CENTER`, `GYM`, or `BUILDING` classification require explicit script, destination, or event semantics. Raster appearance and fuzzy name matching are forbidden.

POI coordinates must remain inside the owning Local map. Invalid POIs are omitted before catalog validation. One malformed map event table produces a diagnostic and omits only that map's POIs.

### Organic visibility and knowledge

The existing knowledge model remains authoritative:

- Entrances and signs use entrance-proximity visibility where appropriate.
- Visible item balls may be visible without prior identification.
- Hidden items use proximity silhouettes until identified.
- Discovered mode exposes all valid POIs.
- Hidden mode exposes none.
- Organic mode uses current/revealed geography, proximity, entry, identification, and collection evidence already persisted by the shared ledger.

POI preferences remain save-scoped. Gen I/II do not introduce new global settings or preference storage.

### Collection evidence

`LocalMapPoiItem.collectionFlagId` remains optional. A collection flag is emitted only when the compiled event structure proves the item-to-flag relationship. A flag is treated as set only from a supported SaveRAM or live-memory ABI whose containing data passes its existing integrity checks.

If the flag base, item mapping, SaveRAM checksum, or live section cannot be validated:

- the item POI may still exist,
- its identity may still become known through normal discovery,
- collection state remains unknown,
- no collected state is inferred from bag contents or raster disappearance.

Adding Gen I/II flag publication must not weaken existing save parsing. Collection evidence is optional enrichment and may not turn a previously usable save into an unsupported save.

## Data flow

```text
accepted Gen I/II Local-map headers
  -> existing raster resolver
  -> generation connection decoder
  -> shared constraint solver
  -> LocalMapCatalog.maps + scenes

accepted per-map event roots
  -> generation POI adapter
  -> validated LocalMapPoi records
  -> LocalMapCatalog.pois

validated live area + X/Y
  -> existing runtime publication
  -> active placement projection
  -> RC53 camera and player marker

checksum-valid save flags (when structurally supported)
  + catalog POI collectionFlagId
  -> existing POI knowledge mapper
  -> save-scoped collected state

normalized catalog + runtime snapshot
  -> existing companion API
  -> existing MapPage Local-first renderer
```

## Discovery and Atlas behavior

The current rendering contract remains unchanged:

- Organic mode creates image elements only for current or persisted-revealed placements.
- Undiscovered placements receive opaque black placeholders; their image URLs are not requested.
- Discovered mode renders all eligible placements within the existing decoded-raster budget.
- Empty scene gaps remain black.
- Atlas is never mounted beneath a valid Local scene.
- A valid Local map without a valid scene uses its individual Local view.
- Atlas appears only when no valid Local map can be presented.
- Crossing connected placements preserves the viewport.
- Recenter changes pan while retaining zoom and follows the valid live tile.

## Persistence and compatibility

No generation-specific serialized models are added. Existing generic scene, POI, timed-asset, preference, and trainer-asset fields remain the persistence boundary.

The catalog schema or parser capability version is incremented once so previously cached Gen I/II catalogs rebuild and receive the new optional fields. A cache produced after that increment must round-trip Gen I static assets, Gen II timed indexed assets, scenes, POIs, and overworld assets without changing their identities or raster bytes.

Knowledge-ledger sanitization continues removing keys that do not exist in the active catalog. Existing revealed areas and POI preferences that still reference valid normalized keys survive the rebuild.

## Failure isolation

Optional module failure behavior is explicit:

| Failure | Required behavior |
|---|---|
| Gen I/II connection root or record is invalid | Omit that evidence; retain the Local raster |
| Pair displacement is ambiguous | Discard that pair; do not guess |
| Component contains overlap or contradiction | Retain a safe deterministic partition; leave excluded maps standalone or in later scenes |
| Scene construction yields no valid scene | Use individual Local maps |
| One map event table is malformed | Omit that map's POIs and record a bounded diagnostic |
| Item collection mapping is unavailable | Keep the POI with unknown collection state |
| SaveRAM integrity fails | Publish no new collection evidence; retain prior safe app behavior |
| Overworld frame is unavailable | Use the compact dot |
| Live coordinates are invalid | Omit the marker and preserve the camera |
| Gen II clock evidence is unavailable | Use existing safe palette behavior |
| Local catalog is unavailable | Use Atlas compatibility fallback |

No optional failure may crash parsing, suppress an otherwise valid catalog, or remove an unrelated capability.

## Validation contract

### Characterization and unit tests

Before extracting the shared solver, freeze current Gen III scene results for existing official and hack controls. The extracted solver must produce identical scene membership, placement geometry, partitioning, and keys.

Generation-specific tests then prove:

- Every accepted connection record is bounds-checked.
- Cardinal direction and signed alignment produce the expected displacement.
- Reciprocal agreement canonicalizes to one constraint.
- Conflicting reciprocal evidence is discarded.
- Overlapping branches are excluded without losing unrelated valid placements.
- Partitioning is deterministic across input ordering.
- Every output scene is bounded and overlap-free.
- A map belongs to at most one scene.
- Malformed connections leave accepted rasters intact.
- POIs remain within map bounds and use only exact accepted event/script forms.
- Malformed POI data and missing flag ABIs fail independently.
- Sole-asset and gender-selected overworld resolution use the intended native dimensions.

### Official real-ROM controls

Strict compiled-ROM controls cover:

- Pokémon Red.
- Pokémon Blue.
- Pokémon Yellow.
- Pokémon Gold.
- Pokémon Silver.
- Pokémon Crystal.

They preserve all existing accepted map counts, dimensions, display names, raster hashes, and, for Gen II, morning/day/night/dark indexed palette hashes. Known route/town chains prove exact compiled connection geometry rather than merely checking that some scene exists. Selected event controls prove exact warp, sign, item, destination, and collection-flag bytes where supported.

Expected facts are derived with public disassemblies as structural references and then asserted against the compiled ROM. Production code does not consult those sources or identify the ROM by project metadata.

### Source-available hack and corpus controls

Every currently accepted Gen I/II ROM in the local regression corpus is reparsed to prove:

- no crash,
- no loss of an existing Local raster,
- no change to existing strict raster controls,
- valid catalog invariants,
- bounded diagnostics when optional modules do not resolve.

Where a local public source repository matches an available compiled hack, add strict scene or POI controls for that structural family. Raw compiled bytes remain authoritative. A source branch that differs from the tested binary cannot be used to force acceptance.

### API, persistence, and web tests

Tests prove:

- A Gen I static scene and Gen II timed scene serialize and project through the existing API.
- Gen II placement URLs retain dynamic-lighting parameters.
- The current Local placement receives the existing initial detail framing.
- Crossing a connected placement preserves pan and zoom.
- Recenter preserves zoom and follows the player cell.
- Hidden placement image URLs are absent in Organic mode.
- Opaque placeholders cover hidden placements.
- Atlas is absent whenever a valid Local map or scene exists.
- Atlas appears when Local resolution is unavailable.
- Mounted scene rasters stay within the existing 32 MiB decoded budget.
- POIs obey knowledge mode, proximity state, collection evidence, user preferences, zoom thresholds, and viewport culling.
- Missing overworld assets use the dot without changing camera behavior.
- A cache rebuild and round-trip retain Gen I/II normalized content.

A production web build, focused parser tests, persistence tests, companion/API tests, Android tests, and the relevant corpus suite must pass before a checkpoint is published.

### Live validation

Checkpoint A's signed APK must demonstrate on owned test devices or emulators:

- one official Gen I title with connected movement,
- one official Gen II title with connected movement and time-aware lighting,
- accurate current placement and player marker,
- retained zoom while crossing and recentering,
- Organic fog without hidden raster requests,
- correct Local-first/Atlas-fallback behavior.

Checkpoint B's signed APK additionally demonstrates representative Gen I and Gen II entrances, signs, visible items, hidden items, preferences, and any supported collection-state transitions. State-changing ADB work requires explicit thread-scoped device ownership, and current UI state must be recaptured before gestures.

## Concurrent-work reconciliation gate

Before every commit during planning or implementation:

1. Ensure the worktree contains only understood changes.
2. Fetch `fork/master` and the active remote feature branch.
3. Record local `HEAD`, fetched master, and merge base.
4. Inspect commits and changed paths added to master since the previous gate.
5. If master advanced without overlap, rebase or fast-forward the active work cleanly.
6. If changed paths or behavior overlap, inspect the three-way differences and deliberately preserve both threads' intended behavior.
7. Rerun focused tests for every reconciled subsystem plus the current task's tests.
8. Commit only after the integrated tree is coherent and validated.
9. Push without force. Integrate a validated checkpoint into `fork/master` only when it remains a fast-forward; otherwise reconcile again.

Hard reset, force push, blind conflict selection, and replacing master with an uninspected branch tip are prohibited.

## Post-stage specification audit

After every implementation-plan stage, compare the resulting code and evidence line by line against this specification. Record every missing, partial, or misimplemented requirement in a stage ledger with one of two classifications:

### Blocker

A gap is a blocker when it:

- violates the current checkpoint's stated scope or acceptance tests,
- regresses any previously accepted raster, lighting, map, discovery, camera, POI, or shared Gen III behavior,
- can crash or suppress an otherwise valid module,
- exposes hidden geography or requests hidden raster assets,
- relies on forbidden per-ROM identity or guessing,
- lacks required official control evidence.

Blockers are fixed and revalidated before the stage closes or a signed checkpoint is published.

### Deferral

A gap may be deferred only when it is outside the current checkpoint's success criteria or is optional evidence that cannot be established safely from the available compiled structure. Every deferral must record:

- a stable task or ledger ID,
- the exact missing behavior,
- the affected generation or structural family,
- why fail-closed behavior is safe,
- the named target stage,
- a concrete acceptance condition.

A deferral that contradicts Stage 1 parity cannot be used to declare Stage 1 complete or unblock Stage 2. Unrecorded omissions are not permitted.

## Out of scope

- A generation-specific map page or camera.
- Atlas underlays beneath valid Local maps or scenes.
- Raster-derived semantic POI inference.
- Filename-, title-, hash-, project-, or allowlist-based ROM behavior.
- Fixed absolute ROM offsets or per-ROM profiles.
- Inventing collection state from bag contents.
- Changing Gen III presentation behavior except characterization-preserving shared-solver extraction or separately proven compatibility fixes.
- General Gen III ROM-compatibility expansion; that is the next separately specified stage.
