# Gen I/II Map Parity Checkpoint B Audit

Specification: `docs/superpowers/specs/2026-08-24-gen1-gen2-local-map-parity-design.md`

**Result:** PRE-RELEASE PASS — implementation and automated integration gates pass; RC56 is eligible for protected publication.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Gen I POIs | Accepted map headers feed a bounded object/event adapter; Red/Blue/Yellow prove Pallet entrances and label, Viridian Forest visible items, and source-backed hidden items | PASS |
| Gen II POIs | Accepted attributes feed the counted event adapter; Gold/Silver/Crystal prove New Bark entrances/labels, Route 29 item ball, Azalea hidden item, Center, and Mart | PASS |
| Exact semantics | Gen I uses exact object type bits, text commands, hidden-event functions, and coordinate-table indexes; Gen II uses exact event kinds, item payloads, family-specific `jumptext`, and `jumpstd` roles | PASS |
| Bounds and isolation | Synthetic controls omit out-of-map events, ignore unrelated object pointers, and retain valid-map POIs when a sibling map is malformed | PASS |
| Local-map isolation | Both generation resolvers validate POIs under an independent `runCatching`; failure yields no POIs while retaining maps, scenes, Gen II indexed assets, lighting, and unrelated modules | PASS |
| Collection evidence | Checksum-valid Gen I main blocks expose the 14-byte hidden-item array; selected checksum-valid Gold/Silver and Crystal primary or backup blocks expose their 256-byte event flags | PASS |
| Shared knowledge/API/UI | Existing generic mapper, save-scoped ledger, Organic/Discovered filtering, preferences, zoom thresholds, decluttering, culling, and cards remain generation-neutral; Map-page regressions pass | PASS |
| Persistence | Parser schema 36 performs one intentional rebuild; static/indexed/timed maps, scenes, POIs, runtime metadata, and trainer assets round-trip through the existing Local-map section | PASS |
| Official controls | Red, Blue, Yellow, Gold, Silver, and Crystal retain exact map/raster/scene/lighting controls and pass selected compiled POI facts | PASS |
| Source-backed subset | Three SHA-verified Shin controls parsed twice: 3/3 deterministic, zero parser errors, zero strict failures, 678 maps/assets retained | PASS |
| Concurrent work | Checkpoint B rebased from `1ac0aa4f` onto fetched master `2e8909bd`; incoming RC55 performance/runtime paths were preserved and overlapping runtime tests passed | PASS |
| Signed artifact | Protected RC56 publication and anonymous artifact verification | PENDING |

## Collection authority

Gen I hidden-item catalog IDs are zero-based indexes in the structurally resolved `HiddenItemCoords` table. The supported SaveRAM ABI stores the corresponding 14-byte obtained-hidden-items array at `0x2b30`, inside the already validated main checksum block. Visible Gen I object records do not carry an authoritative collection mapping.

Gold/Silver and Crystal item event IDs are zero-based bits in their 256-byte event arrays. The supported save layouts place those arrays at primary offsets `0x261f` and `0x2600` respectively. `Gen2SaveReader` decodes only the already selected checksum-valid primary or reconstructed backup game-data copy. Invalid saves publish no snapshot and therefore no collection evidence.

No collection state is inferred from Bag contents, raster disappearance, proximity, or item identity.

## Automated evidence

- Focused official Red/Gold POI gate: `BUILD SUCCESSFUL`.
- All-six official gate identified Yellow's distinct inline hidden-event table and conditional item-exit ABI; the source-backed generic variant was implemented, and the focused Yellow plus synthetic isolation rerun passed.
- Save, companion, catalog, and Android unit gate completed; post-sync `ProductionCompanionRuntimeTest` plus `UnifiedGameStateDecoderTest` passed in 47 seconds.
- Shared web gate: 2 files, 36 tests passed; production Vite build succeeded.
- Focused source-backed matrix: 3 hashes verified, 3 selected, 3 deterministic, 0 parser errors, 0 strict failures.
- No ad hoc ADB/emulator interaction was performed, per the approved validation waiver until a reusable test bench exists.

## Deferral ledger

| ID | Missing behavior / affected family | Safe fallback | Target | Acceptance |
|---|---|---|---|---|
| GB-POI-001 | Collected state for Gen I visible object items; the six/seven/eight-byte object records prove item identity but no stable item-to-save-flag relationship | Item POI remains valid and visible; collection stays unknown | Future source-backed GB collection expansion | A generic compiled consumer proves the object-to-flag mapping and a checksum-valid SaveRAM or validated live-memory ABI publishes the same flag domain across official and affected source-backed controls |
| Task #153 | Local-map authority for 45 pre-existing unavailable GB/GBC rows | `LOCAL_MAP NOT_FOUND`; Atlas and unrelated capabilities remain available | Source-backed GB/GBC Local-map compatibility expansion | Each matching structural family resolves bounded Local maps without baseline capability/raster regressions and passes focused plus affected-corpus controls |

`GB-POI-001` is not a Checkpoint B blocker because the specification requires collection state only where the compiled mapping and integrity-checked state ABI are authoritative. No in-scope POI or known collection state is fabricated or suppressed.
